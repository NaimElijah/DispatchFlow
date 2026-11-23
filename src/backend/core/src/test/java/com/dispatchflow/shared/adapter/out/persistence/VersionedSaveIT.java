package com.dispatchflow.shared.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.TestPersistenceConfiguration.RecordedEvents;
import com.dispatchflow.Transactions;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import com.dispatchflow.shared.adapter.out.persistence.Ledger.EntryAdded;
import com.dispatchflow.shared.adapter.out.persistence.Ledger.EntryReweighed;
import com.dispatchflow.shared.adapter.out.persistence.Ledger.LedgerOpened;
import com.dispatchflow.shared.adapter.out.persistence.Ledger.LedgerRenamed;
import com.dispatchflow.shared.application.StaleVersion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The version helper against a real PostgreSQL (ADR-126 decisions 2, 3 and 5), on the test-only
 * {@link Ledger}. Every save moves the root's version by exactly 1, a conflict becomes {@link
 * StaleVersion}, and the events leave with the save and never outlive one that fails. Each test
 * keys its own ledgers, so none depends on another's rows.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class VersionedSaveIT {

    private static final Instant NOW = Instant.parse("2027-07-13T05:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);
    private static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2027, 7, 13));
    private static final TimeWindow MORNING = TimeWindow.on(DATE, LocalTime.of(8, 0), LocalTime.of(12, 0));
    private static final GeoPoint DEPOT = GeoPoint.of(32.08, 34.78);
    private static final Weight TEN = Weight.kilograms(new BigDecimal("10"));
    private static final Weight TWELVE_AND_A_HALF = Weight.kilograms(new BigDecimal("12.5"));
    private static final Version FIRST_SAVE = Version.of(0);
    private static final Version SECOND_SAVE = Version.of(1);
    private static final long KEY_PREFIX = 0x0190a5e2_7c3b_7defL;

    @Autowired
    private Ledgers ledgers;

    @Autowired
    private VersionedSave versionedSave;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordedEvents events;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void forgetEarlierEvents() {
        events.clear();
    }

    @Test
    void GivenANewLedger_WhenSaved_ThenItIsStoredAtVersionZeroAndItsEventCarriesZero() {
        UUID id = key(1);

        Ledger saved = transactions.inTransaction(() -> save(Ledger.open(id, "Ledger", DATE, MORNING, DEPOT, NOW)));

        assertThat(storedVersion(id)).isEqualTo(FIRST_SAVE.value());
        assertThat(saved.version()).isEqualTo(FIRST_SAVE);
        assertThat(saved.pendingEvents()).isEmpty();
        assertThat(events.committed())
                .containsExactly(new LedgerOpened(new EventId("LedgerOpened", id, FIRST_SAVE, 0), NOW));
    }

    @Test
    void GivenASavedLedger_WhenOnlyItsOwnColumnChanges_ThenItsVersionMovesByOne() {
        UUID id = key(2);
        created(id);

        transactions.inTransaction(() -> {
            Ledger ledger = load(id);
            ledger.rename("Renamed", NOW);
            save(ledger);
        });

        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
        assertThat(events.committed())
                .containsExactly(new LedgerRenamed(new EventId("LedgerRenamed", id, SECOND_SAVE, 0), NOW));
    }

    @Test
    void GivenASavedLedger_WhenOnlyAnEntrysColumnChanges_ThenTheSaveForcesItsVersionUpByOne() {
        UUID id = key(3);
        UUID entry = key(30);
        createdWithAnEntry(id, entry);

        transactions.inTransaction(() -> {
            Ledger ledger = load(id);
            ledger.reweigh(entry, TWELVE_AND_A_HALF, NOW);
            save(ledger);
        });

        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
        assertThat(storedAmount(entry)).isEqualByComparingTo(TWELVE_AND_A_HALF.kilograms());
        assertThat(events.committed())
                .containsExactly(new EntryReweighed(new EventId("EntryReweighed", id, SECOND_SAVE, 0), NOW));
    }

    @Test
    void GivenASavedLedger_WhenAnEntryIsAdded_ThenItsVersionMovesByOne() {
        UUID id = key(4);
        created(id);

        transactions.inTransaction(() -> {
            Ledger ledger = load(id);
            ledger.addEntry(key(40), TEN, NOW);
            save(ledger);
        });

        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
        assertThat(events.committed())
                .containsExactly(new EntryAdded(new EventId("EntryAdded", id, SECOND_SAVE, 0), NOW));
    }

    @Test
    void GivenASavedLedger_WhenItsOwnColumnAndAnEntryChange_ThenItsVersionMovesByOne() {
        UUID id = key(5);
        UUID entry = key(50);
        createdWithAnEntry(id, entry);

        transactions.inTransaction(() -> {
            Ledger ledger = load(id);
            ledger.rename("Renamed", NOW);
            ledger.reweigh(entry, TWELVE_AND_A_HALF, NOW);
            save(ledger);
        });

        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
        assertThat(events.committed())
                .extracting(DomainEvent::eventId)
                .containsExactly(
                        new EventId("LedgerRenamed", id, SECOND_SAVE, 0),
                        new EventId("EntryReweighed", id, SECOND_SAVE, 1));
    }

    @Test
    void GivenASavedLedger_WhenSavedAgainWithNothingChanged_ThenItsVersionStillMovesByOne() {
        UUID id = key(6);
        created(id);

        transactions.inTransaction(() -> {
            save(load(id));
        });

        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
    }

    @Test
    void GivenTwoTransactionsOnOneLedger_WhenTheLaterSaveChangesTheRoot_ThenStaleVersionAndTheFirstChangeStands() {
        UUID id = key(7);
        created(id);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Ledger mine = load(id);
                    transactions.inANewTransaction(() -> {
                        Ledger theirs = load(id);
                        theirs.rename("Theirs", NOW);
                        save(theirs);
                    });
                    mine.rename("Mine", NOW);
                    save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Ledger");
                    assertThat(stale.id()).isEqualTo(id);
                    assertThat(stale.getCause()).isInstanceOf(OptimisticLockException.class);
                });

        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
        assertThat(storedName(id)).isEqualTo("Theirs");
    }

    @Test
    void GivenTwoTransactionsOnOneLedger_WhenTheLaterSaveChangesAnEntryOnly_ThenStaleVersionAndTheEntryIsUnchanged() {
        UUID id = key(8);
        UUID entry = key(80);
        createdWithAnEntry(id, entry);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Ledger mine = load(id);
                    transactions.inANewTransaction(() -> {
                        Ledger theirs = load(id);
                        theirs.rename("Theirs", NOW);
                        save(theirs);
                    });
                    mine.reweigh(entry, TWELVE_AND_A_HALF, NOW);
                    save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Ledger");
                    assertThat(stale.id()).isEqualTo(id);
                    assertThat(stale.getCause()).isInstanceOf(OptimisticLockException.class);
                });

        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
        assertThat(storedAmount(entry)).isEqualByComparingTo(TEN.kilograms());
    }

    @Test
    void GivenASaveThatLosesAConflict_WhenItsTransactionRollsBack_ThenItsEventsWerePublishedButNeverCommitted() {
        UUID id = key(9);
        created(id);
        LedgerRenamed theirs = new LedgerRenamed(new EventId("LedgerRenamed", id, SECOND_SAVE, 0), NOW);
        LedgerRenamed mine = new LedgerRenamed(new EventId("LedgerRenamed", id, SECOND_SAVE, 0), LATER);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Ledger ledger = load(id);
                    transactions.inANewTransaction(() -> {
                        Ledger other = load(id);
                        other.rename("Theirs", NOW);
                        save(other);
                    });
                    ledger.rename("Mine", LATER);
                    save(ledger);
                }))
                .isInstanceOf(StaleVersion.class);

        assertThat(events.published()).containsExactly(theirs, mine);
        assertThat(events.committed()).containsExactly(theirs);
    }

    @Test
    void GivenALedgerWithKernelValues_WhenSavedAndLoadedAgain_ThenEveryValueComesBackAsSaved() {
        UUID id = key(10);
        UUID entry = key(100);
        UUID detailed = key(101);
        PlanId plan = PlanId.of(key(102));
        PhoneNumber phone = PhoneNumber.ofStored("+972523456789");
        EventId cause = new EventId("PlanOpened", plan.value(), Version.of(0), 0);
        DaySeconds due = DaySeconds.of(DATE, 36_000);
        TimeWindow afternoon = TimeWindow.on(DATE, LocalTime.of(13, 0), LocalTime.of(17, 0));
        Volume volume = Volume.litres(new BigDecimal("40"));
        transactions.inTransaction(() -> {
            Ledger ledger = Ledger.open(id, "Ledger", DATE, MORNING, DEPOT, NOW);
            ledger.addEntry(entry, TEN, NOW);
            ledger.describe(Priority.of(Priority.HIGHEST), plan, phone, cause);
            ledger.addDetailedEntry(detailed, TWELVE_AND_A_HALF, Attempt.of(2), volume, due, afternoon, NOW);
            return save(ledger);
        });

        Ledger loaded = transactions.inTransaction(() -> load(id));

        assertThat(loaded.name()).isEqualTo("Ledger");
        assertThat(loaded.status()).isEqualTo(Ledger.Status.OPEN);
        assertThat(loaded.bookedFor()).isEqualTo(DATE);
        assertThat(loaded.window()).isEqualTo(MORNING);
        assertThat(loaded.depot()).contains(DEPOT);
        assertThat(loaded.priority()).isEqualTo(Priority.of(Priority.HIGHEST));
        assertThat(loaded.plan()).contains(plan);
        assertThat(loaded.phone()).contains(phone);
        assertThat(loaded.cause()).contains(cause);
        assertThat(loaded.entries()).hasSize(2);
        assertThat(loaded.entries().get(0)).satisfies(stored -> {
            assertThat(stored.id()).isEqualTo(entry);
            assertThat(stored.amount()).isEqualTo(TEN);
            assertThat(stored.attempt()).isEqualTo(Attempt.FIRST);
            assertThat(stored.volume()).isEmpty();
            assertThat(stored.due()).isEmpty();
            assertThat(stored.slot()).isEmpty();
        });
        assertThat(loaded.entries().get(1)).satisfies(stored -> {
            assertThat(stored.id()).isEqualTo(detailed);
            assertThat(stored.amount()).isEqualTo(TWELVE_AND_A_HALF);
            assertThat(stored.attempt()).isEqualTo(Attempt.of(2));
            assertThat(stored.volume()).contains(volume);
            assertThat(stored.due()).contains(due);
            assertThat(stored.slot()).contains(afternoon);
        });
        assertThat(jdbc.sql("SELECT starts_at FROM persistence_fixture.ledger_entry WHERE id = ?")
                        .param(detailed)
                        .query(LocalDateTime.class)
                        .single())
                .isEqualTo(LocalDateTime.of(2027, 7, 13, 13, 0));
        assertThat(jdbc.sql("SELECT window_start FROM persistence_fixture.ledger WHERE id = ?")
                        .param(id)
                        .query(LocalDateTime.class)
                        .single())
                .isEqualTo(LocalDateTime.of(2027, 7, 13, 8, 0));
        assertThat(jdbc.sql("SELECT status FROM persistence_fixture.ledger WHERE id = ?")
                        .param(id)
                        .query(String.class)
                        .single())
                .isEqualTo("OPEN");
    }

    @Test
    void GivenALedgerWithNoDepot_WhenLoadedAgain_ThenItHasNone() {
        UUID id = key(11);
        transactions.inTransaction(() -> save(Ledger.open(id, "Ledger", DATE, MORNING, null, NOW)));

        assertThat(transactions.inTransaction(() -> load(id)).depot()).isEmpty();
    }

    @Test
    void GivenAStoredLedgerWithABlankName_WhenLoaded_ThenItsPostLoadCheckRefusesTheRow() {
        UUID id = key(12);
        insertLedger(id, " ", LocalDateTime.of(2027, 7, 13, 8, 0), LocalDateTime.of(2027, 7, 13, 12, 0));

        assertThatThrownBy(() -> transactions.inTransaction(() -> load(id)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("Ledger"));
    }

    @Test
    void GivenAStoredWindowThatEndsBeforeItStarts_WhenLoaded_ThenTheRegisteredInstantiatorRefusesTheRow() {
        UUID id = key(13);
        insertLedger(id, "Ledger", LocalDateTime.of(2027, 7, 13, 12, 0), LocalDateTime.of(2027, 7, 13, 8, 0));

        // Hibernate's own record instantiator would throw TimeWindow's RuleViolation instead.
        assertThatThrownBy(() -> transactions.inTransaction(() -> load(id)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("TimeWindow"));
    }

    private void insertLedger(UUID id, String name, LocalDateTime windowStart, LocalDateTime windowEnd) {
        jdbc.sql("""
                        INSERT INTO persistence_fixture.ledger
                            (id, version, name, status, booked_for, window_start, window_end, priority)
                        VALUES (?, 0, ?, 'OPEN', ?, ?, ?, 3)
                        """).params(id, name, DATE.value(), windowStart, windowEnd).update();
    }

    @Test
    void GivenARowTheDatabaseRefuses_WhenSaved_ThenNeitherTheErrorNorTheLogQuotesItsValues(CapturedOutput output) {
        UUID id = key(14);

        Throwable refused = catchThrowable(() -> transactions.inTransaction(
                () -> save(Ledger.open(id, "Refused by the database", DATE, MORNING, DEPOT, NOW))));

        assertThat(refused).isNotNull().isNotInstanceOf(StaleVersion.class);
        for (Throwable cause = refused; cause != null; cause = cause.getCause()) {
            assertThat(String.valueOf(cause.getMessage()))
                    .doesNotContain("Failing row")
                    .doesNotContain(String.valueOf(DEPOT.latitude()));
        }
        assertThat(output.getAll()).doesNotContain("Failing row").doesNotContain(String.valueOf(DEPOT.latitude()));
    }

    @Test
    void
            GivenALedgerSavedOnceInATransaction_WhenAnEntryOnlyChangeIsSavedAgain_ThenTheSaveFailsLoudlyAndNothingIsStored() {
        UUID id = key(15);
        UUID entry = key(150);
        createdWithAnEntry(id, entry);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Ledger ledger = load(id);
                    ledger.rename("Renamed", NOW);
                    save(ledger);
                    ledger.reweigh(entry, TWELVE_AND_A_HALF, NOW);
                    save(ledger);
                }))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("once per transaction");

        assertThat(storedVersion(id)).isEqualTo(FIRST_SAVE.value());
        assertThat(storedName(id)).isEqualTo("Ledger");
        assertThat(events.committed()).isEmpty();
    }

    @Test
    void GivenAQueryThatFlushesBetweenTwoChanges_WhenTheLedgerIsSaved_ThenTheSaveFailsLoudlyAndNothingIsStored() {
        UUID id = key(16);
        UUID entry = key(160);
        createdWithAnEntry(id, entry);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Ledger ledger = load(id);
                    ledger.rename("Renamed", NOW);
                    entityManager
                            .createQuery("select count(l) from Ledger l", Long.class)
                            .getSingleResult();
                    ledger.rename("Renamed again", NOW);
                    save(ledger);
                }))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("between two of its changes");

        assertThat(storedVersion(id)).isEqualTo(FIRST_SAVE.value());
        assertThat(events.committed()).isEmpty();
    }

    @Test
    void GivenATransactionHoldingTheLedgersRow_WhenAnotherSaveTimesOutWaitingForIt_ThenStaleVersion() {
        UUID id = key(17);
        created(id);

        transactions.inTransaction(() -> {
            Ledger holder = load(id);
            holder.rename("Held", NOW);
            save(holder);
            assertThatThrownBy(() -> transactions.inANewTransaction(() -> {
                        jdbc.sql("SET LOCAL lock_timeout = '200ms'").update();
                        Ledger waiting = load(id);
                        waiting.rename("Waiting", NOW);
                        save(waiting);
                    }))
                    .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                        assertThat(stale.kind()).isEqualTo("Ledger");
                        assertThat(stale.id()).isEqualTo(id);
                    });
        });

        assertThat(storedName(id)).isEqualTo("Held");
        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
    }

    @Test
    void GivenTwoTransactionsCreatingTheSameLedger_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstStands() {
        UUID id = key(18);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    Ledger mine = Ledger.open(id, "Mine", DATE, MORNING, DEPOT, NOW);
                    transactions.inANewTransaction(() -> {
                        save(Ledger.open(id, "Theirs", DATE, MORNING, DEPOT, NOW));
                    });
                    save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Ledger");
                    assertThat(stale.id()).isEqualTo(id);
                });

        assertThat(storedName(id)).isEqualTo("Theirs");
        assertThat(storedVersion(id)).isEqualTo(FIRST_SAVE.value());
    }

    @Test
    void GivenALedgerLoadedInAnEarlierTransaction_WhenSavedAfterAnotherChangeCommitted_ThenStaleVersion() {
        UUID id = key(19);
        created(id);
        Ledger detached = transactions.inTransaction(() -> load(id));
        transactions.inTransaction(() -> {
            Ledger ledger = load(id);
            ledger.rename("Theirs", NOW);
            save(ledger);
        });

        detached.rename("Mine", NOW);

        assertThatThrownBy(() -> transactions.inTransaction(() -> save(detached)))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Ledger");
                    assertThat(stale.id()).isEqualTo(id);
                    assertThat(stale.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
                });
        assertThat(storedName(id)).isEqualTo("Theirs");
    }

    @Test
    void GivenAStoredEntryWhoseDueSecondsFallBeforeTheDay_WhenLoaded_ThenTheRegisteredInstantiatorRefusesTheRow() {
        UUID id = key(20);
        insertLedger(id, "Ledger", LocalDateTime.of(2027, 7, 13, 8, 0), LocalDateTime.of(2027, 7, 13, 12, 0));
        jdbc.sql("""
                        INSERT INTO persistence_fixture.ledger_entry
                            (id, ledger_id, entry_index, amount, attempt, due_date, due_seconds)
                        VALUES (?, ?, 0, 10, 1, ?, -1)
                        """).params(key(200), id, DATE.value()).update();

        // Hibernate's own record instantiator would throw DaySeconds' RuleViolation instead.
        assertThatThrownBy(() -> transactions.inTransaction(() -> load(id)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("DaySeconds"));
    }

    private Ledger created(UUID id) {
        Ledger ledger = transactions.inTransaction(() -> save(Ledger.open(id, "Ledger", DATE, MORNING, DEPOT, NOW)));
        events.clear();
        return ledger;
    }

    private void createdWithAnEntry(UUID id, UUID entry) {
        transactions.inTransaction(() -> {
            Ledger ledger = Ledger.open(id, "Ledger", DATE, MORNING, DEPOT, NOW);
            ledger.addEntry(entry, TEN, NOW);
            return save(ledger);
        });
        events.clear();
    }

    private Ledger load(UUID id) {
        return ledgers.findById(id).orElseThrow();
    }

    private Ledger save(Ledger ledger) {
        return versionedSave.save(ledger, ledgers::save);
    }

    private long storedVersion(UUID id) {
        return jdbc.sql("SELECT version FROM persistence_fixture.ledger WHERE id = ?")
                .param(id)
                .query(Long.class)
                .single();
    }

    private String storedName(UUID id) {
        return jdbc.sql("SELECT name FROM persistence_fixture.ledger WHERE id = ?")
                .param(id)
                .query(String.class)
                .single();
    }

    private BigDecimal storedAmount(UUID entry) {
        return jdbc.sql("SELECT amount FROM persistence_fixture.ledger_entry WHERE id = ?")
                .param(entry)
                .query(BigDecimal.class)
                .single();
    }

    private static UUID key(int number) {
        return new UUID(KEY_PREFIX, number);
    }
}
