package com.dispatchflow.planning.adapter.out.persistence;

import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.D1;
import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.V1;
import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.V3;
import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.bareReoptimization;
import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.emptySolution;
import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.everyKindOfReoptimization;
import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.everyKindOfSolution;
import static com.dispatchflow.planning.adapter.out.persistence.StoredSolutions.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.planning.application.port.out.proposal.ProposalRepository;
import com.dispatchflow.planning.domain.plan.ProposalDescription;
import com.dispatchflow.planning.domain.plan.QueuedJobs;
import com.dispatchflow.planning.domain.plan.ReoptimizationProposal;
import com.dispatchflow.planning.domain.plan.SolveJob;
import com.dispatchflow.planning.events.Lane;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ProposalRepository} against a real PostgreSQL (ADR-126, ADR-127): what a proposal stores and
 * loads as, ready and declined, the version, and the stored states the load check refuses.
 *
 * <p>The database is shared and never cleaned, and no query here reads more than one proposal, so
 * test {@code n} keys its own proposal, job and plan.
 */
@IntegrationTest
class ProposalPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_7072_6f70L;
    private static final Instant REQUESTED = Instant.parse("2027-08-10T09:00:00Z");
    private static final Instant ENDED = REQUESTED.plusSeconds(40);
    private static final Instant DECLINED_AT = REQUESTED.plusSeconds(300);
    private static final AccountId DISPATCHER = AccountId.of(new UUID(KEY_PREFIX, 99_999));

    @Autowired
    private ProposalRepository repository;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    // --- round trips (ADR-127 decision 1)

    /** The states a proposal stores and loads in, reached through its job's success and its decline. */
    enum Scenario {
        READY_WITH_EVERY_LIST,
        READY_WITH_EMPTY_LISTS,
        DECLINED;

        ReoptimizationProposal build(int n) {
            SolveJob running = QueuedJobs.queue(job(n), plan(n), Lane.REOPTIMIZATION, REQUESTED);
            running.claim(REQUESTED.plusSeconds(5));
            running.searchStarted(REQUESTED.plusSeconds(10));
            ReoptimizationProposal proposal = running.succeed(
                    proposal(n),
                    this == READY_WITH_EMPTY_LISTS
                            ? bareReoptimization(emptySolution(), REQUESTED)
                            : everyKindOfReoptimization(everyKindOfSolution(), REQUESTED),
                    ENDED);
            if (this == DECLINED) {
                proposal.decline(DISPATCHER, DECLINED_AT);
            }
            return proposal;
        }
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenAProposal_WhenAddedAndLoadedInAnotherTransaction_ThenItsWholeStateComesBackAsAdded(Scenario scenario) {
        int n = 1 + scenario.ordinal();
        ProposalDescription added = transactions.inTransaction(() -> {
            ReoptimizationProposal proposal = scenario.build(n);
            ProposalDescription before = ProposalDescription.of(proposal);
            repository.add(proposal);
            return before;
        });

        ReoptimizationProposal loaded =
                transactions.inTransaction(() -> repository.get(proposal(n)).orElseThrow());

        // Read outside the transaction, so a collection the load left unfetched fails here.
        assertThat(ProposalDescription.of(loaded)).isEqualTo(added.at(Version.of(0)));
        assertThat(loaded.id()).isEqualTo(proposal(n));
        assertThat(loaded.plan()).isEqualTo(plan(n));
        assertThat(loaded.basis()).isEqualTo(added.basis());
        assertThat(loaded.state()).isEqualTo(added.state());
        assertThat(loaded.routes()).isEqualTo(added.routes());
        assertThat(loaded.dropped()).isEqualTo(added.dropped());
        assertThat(loaded.excluded()).isEqualTo(added.excluded());
        assertThat(loaded.withdrawn()).isEqualTo(added.withdrawn());
        assertThat(loaded.withdraws(V3)).isEqualTo(scenario != Scenario.READY_WITH_EMPTY_LISTS);
        assertThat(loaded.withdraws(V1)).isFalse();
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenNoProposalWithAnId_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(proposal(9))))
                .isEmpty();
    }

    // --- versions and conflicts

    @Test
    void GivenAnAddedProposal_WhenItIsDeclinedAndSaved_ThenTheVersionMovesFromZeroToOneAndItStaysDeclined() {
        int n = 10;
        transactions.inTransaction(() -> repository.add(Scenario.READY_WITH_EVERY_LIST.build(n)));
        long added = storedVersion(n);

        transactions.inTransaction(() -> {
            ReoptimizationProposal proposal = repository.get(proposal(n)).orElseThrow();
            proposal.decline(DISPATCHER, DECLINED_AT);
            repository.save(proposal);
        });

        assertThat(List.of(added, storedVersion(n))).containsExactly(0L, 1L);
        assertThat(transactions.inTransaction(
                        () -> repository.get(proposal(n)).orElseThrow().state()))
                .isEqualTo(new ReoptimizationProposal.State.Declined(DISPATCHER, DECLINED_AT));
    }

    @Test
    void GivenTwoTransactionsOnOneProposal_WhenTheLaterDeclineIsSaved_ThenStaleVersionAndTheFirstStands() {
        int n = 11;
        AccountId other = AccountId.of(new UUID(KEY_PREFIX, 99_998));
        transactions.inTransaction(() -> repository.add(Scenario.READY_WITH_EVERY_LIST.build(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    ReoptimizationProposal mine = repository.get(proposal(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        ReoptimizationProposal theirs =
                                repository.get(proposal(n)).orElseThrow();
                        theirs.decline(other, DECLINED_AT);
                        repository.save(theirs);
                    });
                    mine.decline(DISPATCHER, DECLINED_AT.plusSeconds(1));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("ReoptimizationProposal");
                    assertThat(stale.id()).isEqualTo(proposal(n).value());
                });

        assertThat(storedVersion(n)).isEqualTo(1L);
        assertThat(transactions.inTransaction(
                        () -> repository.get(proposal(n)).orElseThrow().state()))
                .isEqualTo(new ReoptimizationProposal.State.Declined(other, DECLINED_AT));
    }

    @Test
    void GivenAStoredProposal_WhenAnotherWithItsIdIsAdded_ThenStaleVersionAndTheFirstStands() {
        int n = 12;

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    ReoptimizationProposal mine = Scenario.READY_WITH_EMPTY_LISTS.build(n);
                    transactions.inANewTransaction(() -> repository.add(Scenario.READY_WITH_EVERY_LIST.build(n)));
                    repository.add(mine);
                }))
                .isInstanceOf(StaleVersion.class);

        assertThat(transactions.inTransaction(
                        () -> repository.get(proposal(n)).orElseThrow().routes()))
                .hasSize(3);
    }

    // --- the load check (ADR-127 decision 1), one case for each damaged state the domain tests used to build

    static Stream<Arguments> damagedRows() {
        String once = "an order and attempt is served or dropped at most once";
        String oneRoute = "a vehicle or driver drives at most one route";
        return Stream.of(
                Arguments.of(
                        "an order served twice",
                        101,
                        Scenario.READY_WITH_EVERY_LIST,
                        (Damage) rows -> rows.update(
                                "reoptimization_proposal_stop",
                                "order_id = '" + order(1).value() + "', attempt = 1",
                                "route_position = 2"),
                        "ReoptimizationProposal",
                        once),
                Arguments.of(
                        "an order served and dropped",
                        102,
                        Scenario.READY_WITH_EVERY_LIST,
                        (Damage) rows -> rows.update(
                                "reoptimization_proposal_drop",
                                "order_id = '" + order(1).value() + "'",
                                "reason = 'DISPLACED'"),
                        "ReoptimizationProposal",
                        once),
                Arguments.of(
                        "a vehicle with two routes",
                        103,
                        Scenario.READY_WITH_EVERY_LIST,
                        (Damage) rows -> rows.update(
                                "reoptimization_proposal_route", "vehicle_id = '" + V1.value() + "'", "position = 1"),
                        "ReoptimizationProposal",
                        oneRoute),
                Arguments.of(
                        "a withdrawn vehicle with two routes",
                        104,
                        Scenario.READY_WITH_EVERY_LIST,
                        (Damage) rows -> rows.update(
                                "reoptimization_proposal_route", "vehicle_id = '" + V3.value() + "'", "position = 0"),
                        "ReoptimizationProposal",
                        oneRoute),
                Arguments.of(
                        "a driver on two routes",
                        105,
                        Scenario.READY_WITH_EVERY_LIST,
                        (Damage) rows -> rows.update(
                                "reoptimization_proposal_route", "driver_id = '" + D1.value() + "'", "position = 1"),
                        "ReoptimizationProposal",
                        oneRoute),
                Arguments.of(
                        "a decline before the proposal was created",
                        106,
                        Scenario.DECLINED,
                        (Damage) rows -> rows.update(
                                "reoptimization_proposal", "declined_at = created_at - interval '1 second'"),
                        "ReoptimizationProposal",
                        "a proposal is declined no earlier than it is created"),
                Arguments.of(
                        "a changed order of no driver change",
                        107,
                        Scenario.READY_WITH_EVERY_LIST,
                        (Damage) rows -> rows.update("reoptimization_proposal_changed_order", "driver_change = 2"),
                        "DriverChange",
                        "a changed order belongs to one of its proposal's driver changes"),
                Arguments.of(
                        "a stop on no route",
                        108,
                        Scenario.READY_WITH_EVERY_LIST,
                        (Damage) rows ->
                                rows.update("reoptimization_proposal_stop", "route_position = 3", "route_position = 2"),
                        "SolvedRoute",
                        "a solved stop is on one of its solution's routes"),
                Arguments.of(
                        "late stops whose positions start past the first",
                        109,
                        Scenario.READY_WITH_EVERY_LIST,
                        (Damage) rows -> rows.update("reoptimization_proposal_late_stop", "position = position + 1"),
                        "ReoptimizationProposal",
                        StoredLists.GAP));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredProposalBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Scenario scenario, Damage damage, String subject, String detail) {
        transactions.inTransaction(() -> repository.add(scenario.build(n)));

        damage.apply(new Rows(jdbc, proposal(n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> repository.get(proposal(n))))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo(subject);
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
    }

    @Test
    void GivenAStoredBasisRevisionBelowOne_WhenTheProposalIsLoaded_ThenItsConverterRaisesStoredStateInvalid() {
        int n = 201;
        transactions.inTransaction(() -> repository.add(Scenario.READY_WITH_EMPTY_LISTS.build(n)));

        new Rows(jdbc, proposal(n)).update("reoptimization_proposal", "basis_revision = 0");

        // Hibernate wraps a converter's exception, and Spring wraps Hibernate's.
        Throwable thrown = catchThrowable(() -> transactions.inTransaction(() -> repository.get(proposal(n))));
        assertThat(Stream.iterate(thrown, cause -> cause != null, Throwable::getCause))
                .anyMatch(cause -> cause instanceof StoredStateInvalid invalid
                        && invalid.subject().equals("RevisionNo"));
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("a state no proposal has", 301, (Damage)
                        rows -> rows.update("reoptimization_proposal", "state = 'ACCEPTED'")),
                Arguments.of("a declined proposal without who declined it", 302, (Damage)
                        rows -> rows.update("reoptimization_proposal", "declined_by = NULL")),
                Arguments.of("a declined proposal without when", 303, (Damage)
                        rows -> rows.update("reoptimization_proposal", "declined_at = NULL")),
                Arguments.of("a ready proposal with a decline", 304, (Damage)
                        rows -> rows.update("reoptimization_proposal", "state = 'READY'")),
                Arguments.of("a cause no withdrawal has", 305, (Damage)
                        rows -> rows.update("reoptimization_proposal_withdrawal", "cause = 'STOLEN'")),
                Arguments.of("a list no changed order is in", 306, (Damage)
                        rows -> rows.update("reoptimization_proposal_changed_order", "change = 'KEPT'")),
                Arguments.of("a pin drop without its target", 307, (Damage) rows -> rows.update(
                        "reoptimization_proposal_drop",
                        "pin_vehicle_id = NULL, pin_driver_id = NULL",
                        "reason = 'PIN_NOT_HONOURED'")),
                Arguments.of("a route plan missing its date", 308, (Damage)
                        rows -> rows.update("reoptimization_proposal_route", "times_date = NULL", "position = 0")),
                Arguments.of("a late stop without its lateness", 309, (Damage)
                        rows -> rows.update("reoptimization_proposal_late_stop", "late = NULL")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredProposal_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Damage damage) {
        transactions.inTransaction(() -> repository.add(Scenario.DECLINED.build(n)));
        Rows rows = new Rows(jdbc, proposal(n));

        assertThatThrownBy(() -> damage.apply(rows)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(proposal(n))))
                .isPresent();
    }

    /** One damage done with SQL to a stored proposal's rows. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** The SQL the damage cases run on one proposal's rows; every statement is the test's own constant. */
    record Rows(JdbcClient jdbc, ProposalId proposal) {

        /** Sets columns of the proposal's rows in one of its tables. */
        void update(String table, String assignments) {
            update(table, assignments, "TRUE");
        }

        /** Sets columns of those of the proposal's rows in one of its tables that meet a condition. */
        void update(String table, String assignments, String condition) {
            String key = table.equals("reoptimization_proposal") ? "id" : "proposal_id";
            jdbc.sql("UPDATE planning." + table + " SET " + assignments + " WHERE " + key + " = ? AND (" + condition
                            + ")")
                    .param(proposal.value())
                    .update();
        }
    }

    // --- keys and stored values

    private long storedVersion(int n) {
        return jdbc.sql("SELECT version FROM planning.reoptimization_proposal WHERE id = ?")
                .param(proposal(n).value())
                .query(Long.class)
                .single();
    }

    private static ProposalId proposal(int n) {
        return ProposalId.of(new UUID(KEY_PREFIX, n));
    }

    private static JobId job(int n) {
        return JobId.of(new UUID(KEY_PREFIX, 10_000 + n));
    }

    private static PlanId plan(int n) {
        return PlanId.of(new UUID(KEY_PREFIX, 20_000 + n));
    }
}
