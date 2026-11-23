package com.dispatchflow.shared.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.TestPersistenceConfiguration.RecordedEvents;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Weight;
import com.dispatchflow.shared.adapter.out.persistence.Ledger.EntryAdded;
import com.dispatchflow.shared.adapter.out.persistence.Ledger.EntryReweighed;
import com.dispatchflow.shared.adapter.out.persistence.Ledger.LedgerOpened;
import com.dispatchflow.shared.adapter.out.persistence.Ledger.LedgerRenamed;
import com.dispatchflow.shared.application.StaleVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The version helper against a real PostgreSQL (ADR-126 decisions 2, 3 and 5), on the test-only
 * {@link Ledger}. Every save moves the root's version by exactly 1, a conflict becomes {@link
 * StaleVersion}, and the events leave with the save and never outlive one that fails. Each test
 * keys its own ledgers, so none depends on another's rows.
 */
@IntegrationTest
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
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordedEvents events;

    @BeforeEach
    void forgetEarlierEvents() {
        events.clear();
    }

    @Test
    void GivenANewLedger_WhenSaved_ThenItIsStoredAtVersionZeroAndItsEventCarriesZero() {
        UUID id = key(1);

        Ledger saved = inTransaction(() -> save(Ledger.open(id, "Ledger", DATE, MORNING, DEPOT, NOW)));

        assertThat(storedVersion(id)).isEqualTo(FIRST_SAVE.value());
        assertThat(saved.version()).isEqualTo(FIRST_SAVE);
        assertThat(events.committed())
                .containsExactly(new LedgerOpened(new EventId("LedgerOpened", id, FIRST_SAVE, 0), NOW));
    }

    @Test
    void GivenASavedLedger_WhenOnlyItsOwnColumnChanges_ThenItsVersionMovesByOne() {
        UUID id = key(2);
        created(id);

        inTransaction(() -> {
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

        inTransaction(() -> {
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

        inTransaction(() -> {
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

        inTransaction(() -> {
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

        inTransaction(() -> {
            save(load(id));
        });

        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
    }

    @Test
    void GivenTwoTransactionsOnOneLedger_WhenTheLaterSaveChangesTheRoot_ThenStaleVersionAndTheFirstChangeStands() {
        UUID id = key(7);
        created(id);

        assertThatThrownBy(() -> inTransaction(() -> {
                    Ledger mine = load(id);
                    inANewTransaction(() -> {
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
                    assertThat(stale.getCause()).isNotNull();
                });

        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
        assertThat(storedName(id)).isEqualTo("Theirs");
    }

    @Test
    void GivenTwoTransactionsOnOneLedger_WhenTheLaterSaveChangesAnEntryOnly_ThenStaleVersionAndTheEntryIsUnchanged() {
        UUID id = key(8);
        UUID entry = key(80);
        createdWithAnEntry(id, entry);

        assertThatThrownBy(() -> inTransaction(() -> {
                    Ledger mine = load(id);
                    inANewTransaction(() -> {
                        Ledger theirs = load(id);
                        theirs.rename("Theirs", NOW);
                        save(theirs);
                    });
                    mine.reweigh(entry, TWELVE_AND_A_HALF, NOW);
                    save(mine);
                }))
                .isInstanceOf(StaleVersion.class);

        assertThat(storedVersion(id)).isEqualTo(SECOND_SAVE.value());
        assertThat(storedAmount(entry)).isEqualByComparingTo(TEN.kilograms());
    }

    @Test
    void GivenASaveThatLosesAConflict_WhenItsTransactionRollsBack_ThenItsEventsWerePublishedButNeverCommitted() {
        UUID id = key(9);
        created(id);
        LedgerRenamed theirs = new LedgerRenamed(new EventId("LedgerRenamed", id, SECOND_SAVE, 0), NOW);
        LedgerRenamed mine = new LedgerRenamed(new EventId("LedgerRenamed", id, SECOND_SAVE, 0), LATER);

        assertThatThrownBy(() -> inTransaction(() -> {
                    Ledger ledger = load(id);
                    inANewTransaction(() -> {
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
        createdWithAnEntry(id, entry);

        Ledger loaded = inTransaction(() -> load(id));

        assertThat(loaded.name()).isEqualTo("Ledger");
        assertThat(loaded.status()).isEqualTo(Ledger.Status.OPEN);
        assertThat(loaded.bookedFor()).isEqualTo(DATE);
        assertThat(loaded.window()).isEqualTo(MORNING);
        assertThat(loaded.depot()).contains(DEPOT);
        assertThat(loaded.entries()).singleElement().satisfies(stored -> {
            assertThat(stored.id()).isEqualTo(entry);
            assertThat(stored.amount()).isEqualTo(TEN);
        });
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
        inTransaction(() -> save(Ledger.open(id, "Ledger", DATE, MORNING, null, NOW)));

        assertThat(inTransaction(() -> load(id)).depot()).isEmpty();
    }

    @Test
    void GivenAStoredLedgerWithABlankName_WhenLoaded_ThenItsPostLoadCheckRefusesTheRow() {
        UUID id = key(12);
        insertLedger(id, " ", LocalDateTime.of(2027, 7, 13, 8, 0), LocalDateTime.of(2027, 7, 13, 12, 0));

        assertThatThrownBy(() -> inTransaction(() -> load(id)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("Ledger"));
    }

    @Test
    void GivenAStoredWindowThatEndsBeforeItStarts_WhenLoaded_ThenTheRegisteredInstantiatorRefusesTheRow() {
        UUID id = key(13);
        insertLedger(id, "Ledger", LocalDateTime.of(2027, 7, 13, 12, 0), LocalDateTime.of(2027, 7, 13, 8, 0));

        // Hibernate's own record instantiator would throw TimeWindow's RuleViolation instead.
        assertThatThrownBy(() -> inTransaction(() -> load(id)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("TimeWindow"));
    }

    private void insertLedger(UUID id, String name, LocalDateTime windowStart, LocalDateTime windowEnd) {
        jdbc.sql("""
                        INSERT INTO persistence_fixture.ledger
                            (id, version, name, status, booked_for, window_start, window_end)
                        VALUES (?, 0, ?, 'OPEN', ?, ?, ?)
                        """).params(id, name, DATE.value(), windowStart, windowEnd).update();
    }

    private Ledger created(UUID id) {
        Ledger ledger = inTransaction(() -> save(Ledger.open(id, "Ledger", DATE, MORNING, DEPOT, NOW)));
        events.clear();
        return ledger;
    }

    private void createdWithAnEntry(UUID id, UUID entry) {
        inTransaction(() -> {
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

    private <T> T inTransaction(Supplier<T> work) {
        T result = new TransactionTemplate(transactionManager).execute(status -> work.get());
        if (result == null) {
            throw new AssertionError("the transaction returned nothing");
        }
        return result;
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private void inANewTransaction(Runnable work) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> work.run());
    }

    private static UUID key(int number) {
        return new UUID(KEY_PREFIX, number);
    }
}
