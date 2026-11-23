package com.dispatchflow.orders.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.orders.application.port.out.ImportBatchRepository;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.domain.AccessDetails;
import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.orders.domain.CustomerMatch;
import com.dispatchflow.orders.domain.DailyWindow;
import com.dispatchflow.orders.domain.DriverNote;
import com.dispatchflow.orders.domain.ExternalReference;
import com.dispatchflow.orders.domain.FileEncoding;
import com.dispatchflow.orders.domain.FileHash;
import com.dispatchflow.orders.domain.ImportBatch;
import com.dispatchflow.orders.domain.ImportBatchDescription;
import com.dispatchflow.orders.domain.ImportContext;
import com.dispatchflow.orders.domain.ImportLine;
import com.dispatchflow.orders.domain.ImportRow;
import com.dispatchflow.orders.domain.NormalizedAddress;
import com.dispatchflow.orders.domain.OrderDefaults;
import com.dispatchflow.orders.domain.OrderDetails;
import com.dispatchflow.orders.domain.OrdersError.AmbiguousAddress;
import com.dispatchflow.orders.domain.OrdersError.DateInPast;
import com.dispatchflow.orders.domain.OrdersError.NewCustomerNeedsNameAndPhone;
import com.dispatchflow.orders.domain.PreviewRow;
import com.dispatchflow.orders.domain.Recipient;
import com.dispatchflow.orders.domain.RowNumber;
import com.dispatchflow.orders.domain.ServiceTime;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.SharedKernelError.Measure;
import com.dispatchflow.shared.SharedKernelError.QuantityOutOfRange;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredLists;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import com.dispatchflow.shared.application.StaleVersion;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
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
 * {@link ImportBatchRepository} against a real PostgreSQL (ADR-126, ADR-127): what an import and
 * its rows store and load as, the rows a batch that leaves its preview deletes, the version, the
 * open previews oldest first, and the stored states the load check and the schema refuse.
 *
 * <p>The database is shared and never cleaned, and the sweep's lookup reads every import. So test
 * {@code n} keys its own imports ({@code batch(n)}, {@code batch(100 + n)}, …), previews them at its
 * own time, {@code previewedAt(n)}, and reads only its own imports out of the lookup's answer.
 */
@IntegrationTest
class ImportBatchPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_696d_7074L;
    private static final Instant FIRST_PREVIEW = Instant.parse("2033-01-02T08:00:00Z");
    private static final FileHash HASH =
            new FileHash("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    private static final OrderDefaults COMPANY_DEFAULTS = new OrderDefaults(
            DailyWindow.of(LocalTime.of(9, 0), LocalTime.of(17, 0)), Optional.of(ServiceTime.ofSeconds(300)));
    private static final ContactName NAME = ContactName.of("רות ישראלי");
    private static final PhoneNumber PHONE = PhoneNumber.ofStored("+972523456780");
    private static final Duration HALF_AN_HOUR = Duration.ofMinutes(30);

    @Autowired
    private ImportBatchRepository repository;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    // --- round trips (ADR-127 decision 1)

    /** The states an import stores and loads in, reached through commands. */
    enum Scenario {
        PREVIEWED_CLEAN,
        PREVIEWED_WITH_ERRORS,
        READ_AS_WINDOWS_1255,
        CONFIRMED,
        DISCARDED,
        EXPIRED
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void GivenAnImport_WhenAddedAndLoadedInAnotherTransaction_ThenEveryPartComesBackAsAdded(Scenario scenario) {
        int n = 1 + scenario.ordinal();
        ImportBatch added = transactions.inTransaction(() -> {
            ImportBatch batch = switch (scenario) {
                case PREVIEWED_CLEAN -> previewed(n, cleanRows(n, 3), FileEncoding.UTF_8);
                case PREVIEWED_WITH_ERRORS -> previewed(n, rowsWithErrors(n), FileEncoding.UTF_8);
                case READ_AS_WINDOWS_1255 -> previewed(n, cleanRows(n, 1), FileEncoding.WINDOWS_1255);
                case CONFIRMED -> confirmed(n);
                case DISCARDED -> discarded(n);
                case EXPIRED -> expired(n);
            };
            repository.add(batch);
            return batch;
        });

        ImportBatch loaded =
                transactions.inTransaction(() -> repository.get(batch(n)).orElseThrow());

        assertThat(ImportBatchDescription.of(loaded)).isEqualTo(ImportBatchDescription.of(added));
        assertThat(loaded.id()).isEqualTo(batch(n));
        assertThat(loaded.state()).isEqualTo(added.state());
        assertThat(loaded.rows()).isEqualTo(added.rows());
        assertThat(loaded.version()).isEqualTo(Version.of(0));
        assertThat(loaded.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAPreviewWithRowsInError_WhenLoaded_ThenEachRowKeepsItsOwnErrorsInTheOrderFound() {
        int n = 7;
        transactions.inTransaction(() -> repository.add(previewed(n, rowsWithErrors(n), FileEncoding.UTF_8)));

        List<List<DomainError>> errors =
                transactions.inTransaction(() -> repository.get(batch(n)).orElseThrow().rows().stream()
                        .map(PreviewRow::errors)
                        .toList());

        assertThat(errors)
                .containsExactly(
                        List.of(),
                        List.of(
                                new NewCustomerNeedsNameAndPhone(),
                                new DateInPast(DeliveryDate.of(today(n).minusDays(1)))),
                        List.of(new AmbiguousAddress(List.of(customer(n, 1), customer(n, 2)))),
                        unreadableErrors());
    }

    @Test
    void GivenNoImportWithAnId_WhenItIsLoaded_ThenThereIsNone() {
        assertThat(transactions.inTransaction(() -> repository.get(batch(8)))).isEmpty();
    }

    // --- a batch that leaves its preview keeps no rows (NFR-31)

    /** The ways a preview ends. */
    enum Ending {
        CONFIRMED,
        DISCARDED,
        EXPIRED
    }

    @ParameterizedTest
    @EnumSource(Ending.class)
    void GivenAStoredPreviewWithRows_WhenItEndsAndIsSaved_ThenItsRowsAreDeleted(Ending ending) {
        int n = 10 + ending.ordinal();
        transactions.inTransaction(() -> repository.add(previewed(n, cleanRows(n, 3), FileEncoding.UTF_8)));
        long before = storedRows(n);

        transactions.inTransaction(() -> {
            ImportBatch batch = repository.get(batch(n)).orElseThrow();
            switch (ending) {
                case CONFIRMED -> batch.confirm(context(n, cleanRows(n, 3)), true, ids(n, 6), today(n), confirmTime(n));
                case DISCARDED -> batch.discard(previewedAt(n).plus(HALF_AN_HOUR));
                case EXPIRED -> batch.expire(previewedAt(n).plus(ImportBatch.PREVIEW_LIFETIME));
            }
            repository.save(batch);
        });

        assertThat(before).isEqualTo(3);
        assertThat(storedRows(n)).isZero();
        assertThat(transactions.inTransaction(
                        () -> repository.get(batch(n)).orElseThrow().rows()))
                .isEmpty();
    }

    // --- versions and conflicts

    @Test
    void GivenAnAddedPreview_WhenSavedUnchangedAndThenDiscarded_ThenTheVersionIsZeroThenOneThenTwo() {
        int n = 20;
        transactions.inTransaction(() -> repository.add(previewed(n, cleanRows(n, 2), FileEncoding.UTF_8)));
        long added = storedVersion(n);

        transactions.inTransaction(
                () -> repository.save(repository.get(batch(n)).orElseThrow()));
        long saved = storedVersion(n);
        transactions.inTransaction(() -> {
            ImportBatch batch = repository.get(batch(n)).orElseThrow();
            batch.discard(previewedAt(n).plus(HALF_AN_HOUR));
            repository.save(batch);
        });

        assertThat(List.of(added, saved, storedVersion(n))).containsExactly(0L, 1L, 2L);
    }

    @Test
    void GivenTwoTransactionsOnOneImport_WhenTheLaterOneSaves_ThenStaleVersionAndTheFirstChangeStands() {
        int n = 21;
        transactions.inTransaction(() -> repository.add(previewed(n, cleanRows(n, 2), FileEncoding.UTF_8)));
        Instant theirTime = previewedAt(n).plus(HALF_AN_HOUR);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    ImportBatch mine = repository.get(batch(n)).orElseThrow();
                    transactions.inANewTransaction(() -> {
                        ImportBatch theirs = repository.get(batch(n)).orElseThrow();
                        theirs.discard(theirTime);
                        repository.save(theirs);
                    });
                    mine.discard(theirTime.plusSeconds(60));
                    repository.save(mine);
                }))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("ImportBatch");
                    assertThat(stale.id()).isEqualTo(batch(n).value());
                });

        ImportBatch stored =
                transactions.inTransaction(() -> repository.get(batch(n)).orElseThrow());
        assertThat(stored.version()).isEqualTo(Version.of(1));
        assertThat(stored.state()).isEqualTo(new ImportBatch.State.Discarded(theirTime));
    }

    // --- the open previews, oldest first (FR-399)

    @Test
    void
            GivenPreviewsOfDifferentAgesAndAnEndedOne_WhenThoseOlderThanACutoffAreRead_ThenOnlyOpenOlderOnesComeOldestFirst() {
        int n = 30;
        Instant cutoff = previewedAt(n);
        // Keys that don't sort as their previews do, and two previews made at once.
        transactions.inTransaction(() -> {
            repository.add(openPreview(105, cutoff.minus(Duration.ofHours(2))));
            repository.add(openPreview(101, cutoff.minusSeconds(1)));
            repository.add(openPreview(104, cutoff.minus(Duration.ofHours(3))));
            repository.add(openPreview(103, cutoff.minus(Duration.ofHours(3))));
            repository.add(openPreview(102, cutoff));
            ImportBatch ended = openPreview(106, cutoff.minus(Duration.ofHours(4)));
            ended.discard(cutoff);
            repository.add(ended);
            repository.add(openPreview(107, cutoff.plusSeconds(1)));
        });
        Set<ImportBatchId> ours =
                Set.of(batch(101), batch(102), batch(103), batch(104), batch(105), batch(106), batch(107));

        List<ImportBatchId> older = transactions.inTransaction(() -> repository.previewsOlderThan(cutoff));

        assertThat(older).filteredOn(ours::contains).containsExactly(batch(103), batch(104), batch(105), batch(101));
    }

    // --- the load check (ADR-127 decision 1): the damaged stored states it refuses

    private static final String KEEPS_A_ROW = "a confirmed, discarded or expired import keeps a row";
    private static final String NOT_ASCENDING = "the row numbers are not unique and ascending";

    static Stream<Arguments> damagedRows() {
        return Stream.of(
                Arguments.of(
                        "a confirmed import with a row",
                        1_001,
                        Base.PREVIEWED,
                        (Damage) rows -> rows.updateBatch(
                                "state = 'CONFIRMED', state_since = previewed_at + interval '30 minutes'"),
                        "ImportBatch",
                        KEEPS_A_ROW),
                Arguments.of(
                        "a discarded import with a row",
                        1_002,
                        Base.PREVIEWED,
                        (Damage) rows -> rows.updateBatch(
                                "state = 'DISCARDED', state_since = previewed_at + interval '30 minutes'"),
                        "ImportBatch",
                        KEEPS_A_ROW),
                Arguments.of(
                        "an expired import with a row",
                        1_003,
                        Base.PREVIEWED,
                        (Damage) rows -> rows.updateBatch(
                                "state = 'EXPIRED', state_since = previewed_at + interval '60 minutes'"),
                        "ImportBatch",
                        KEEPS_A_ROW),
                Arguments.of(
                        "rows 2, then 2",
                        1_004,
                        Base.PREVIEWED,
                        (Damage) rows -> rows.numbered(2, 2),
                        "ImportBatch",
                        NOT_ASCENDING),
                Arguments.of(
                        "rows 3, then 1",
                        1_005,
                        Base.PREVIEWED,
                        (Damage) rows -> rows.numbered(3, 1),
                        "ImportBatch",
                        NOT_ASCENDING),
                Arguments.of(
                        "a state older than the preview",
                        1_006,
                        Base.DISCARDED,
                        (Damage) rows -> rows.updateBatch("state_since = previewed_at - interval '1 second'"),
                        "ImportBatch",
                        "the import's state is older than its preview"),
                Arguments.of(
                        "a confirmation after the hour",
                        1_007,
                        Base.CONFIRMED,
                        (Damage) rows -> rows.updateBatch("state_since = previewed_at + interval '60 minutes'"),
                        "ImportBatch",
                        "the import was confirmed after its hour"),
                Arguments.of(
                        "a row numbered 0",
                        1_008,
                        Base.PREVIEWED,
                        (Damage) rows -> rows.numbered(0, 2),
                        "RowNumber",
                        "a row number is 1 or more"),
                Arguments.of(
                        "an unreadable row with no refused field",
                        1_009,
                        Base.PREVIEWED_WITH_ERRORS,
                        (Damage) rows -> rows.updateRow(3, "field_errors = '[]'"),
                        "ImportRow",
                        "an unreadable row names no refused field"),
                Arguments.of(
                        "rows whose positions start past the first",
                        1_011,
                        Base.PREVIEWED,
                        (Damage) Rows::shiftRowPositions,
                        "ImportBatch",
                        StoredLists.GAP));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damagedRows")
    void GivenAStoredImportBreakingAnInvariantAtRest_WhenLoaded_ThenStoredStateInvalid(
            String broken, int n, Base base, Damage damage, String subject, String detail) {
        transactions.inTransaction(() -> repository.add(build(base, n)));

        // The damage and the load share a transaction, which the refused load rolls back, so the
        // previews other tests read never include a damaged row.
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    damage.apply(new Rows(jdbc, n));
                    return repository.get(batch(n));
                }))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo(subject);
                    assertThat(invalid.detail()).isEqualTo(detail);
                });
        assertThat(transactions.inTransaction(() -> repository.get(batch(n)))).isPresent();
    }

    @Test
    void GivenStoredErrorsNamingAClassOutsideTheAllowList_WhenLoaded_ThenTheConvertersStoredStateInvalidIsTheCause() {
        int n = 1_010;
        transactions.inTransaction(() -> repository.add(build(Base.PREVIEWED, n)));

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
                    new Rows(jdbc, n).updateRow(0, "errors = '[{\"type\":\"java.lang.ProcessBuilder\",\"value\":{}}]'");
                    return repository.get(batch(n));
                }))
                .satisfies(thrown -> assertThat(StoredStates.invalidIn(thrown)).hasValueSatisfying(invalid -> {
                    assertThat(invalid.subject()).isEqualTo("RowErrors");
                    assertThat(invalid.detail())
                            .isEqualTo("a stored error names a type that is not a DomainError record");
                }));
    }

    static Stream<Arguments> damageTheSchemaForbids() {
        return Stream.of(
                Arguments.of("a previewed import with a state time", 1_101, Base.PREVIEWED, (Damage)
                        rows -> rows.updateBatch("state_since = previewed_at")),
                Arguments.of("a discarded import with no state time", 1_102, Base.DISCARDED, (Damage)
                        rows -> rows.updateBatch("state_since = NULL")),
                Arguments.of("an unknown encoding", 1_103, Base.PREVIEWED, (Damage)
                        rows -> rows.updateBatch("encoding = 'LATIN_1'")),
                Arguments.of("a parsed row with no address", 1_104, Base.PREVIEWED, (Damage)
                        rows -> rows.updateRow(0, "address = NULL")),
                Arguments.of("a parsed row with refused fields", 1_105, Base.PREVIEWED, (Damage)
                        rows -> rows.updateRow(0, "field_errors = '[]'")),
                Arguments.of("an unreadable row with an address", 1_106, Base.PREVIEWED_WITH_ERRORS, (Damage)
                        rows -> rows.updateRow(3, "address = 'הרצל 5'")),
                Arguments.of(
                        "half a window", 1_107, Base.PREVIEWED, (Damage) rows -> rows.updateRow(0, "ends_at = NULL")),
                Arguments.of("an unknown row kind", 1_108, Base.PREVIEWED, (Damage)
                        rows -> rows.updateRow(0, "kind = 'BLANK'")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("damageTheSchemaForbids")
    void GivenAStoredImport_WhenARowIsDamagedAsTheSchemaForbids_ThenTheDatabaseRefusesTheWrite(
            String broken, int n, Base base, Damage damage) {
        transactions.inTransaction(() -> repository.add(build(base, n)));

        assertThatThrownBy(() -> damage.apply(new Rows(jdbc, n))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transactions.inTransaction(() -> repository.get(batch(n)))).isPresent();
    }

    /** The valid states the damage cases start from. */
    enum Base {
        PREVIEWED,
        PREVIEWED_WITH_ERRORS,
        CONFIRMED,
        DISCARDED
    }

    /** One damage to import {@code n}'s stored rows. */
    @FunctionalInterface
    interface Damage {
        void apply(Rows rows);
    }

    /** Writes to import {@code n}'s rows; the SQL is the test's own constant text. */
    record Rows(JdbcClient jdbc, int n) {

        void updateBatch(String assignments) {
            jdbc.sql("UPDATE orders.import_batch SET " + assignments + " WHERE id = ?")
                    .param(batch(n).value())
                    .update();
        }

        void updateRow(int position, String assignments) {
            jdbc.sql("UPDATE orders.import_row SET " + assignments + " WHERE import_batch_id = ? AND position = ?")
                    .params(batch(n).value(), position)
                    .update();
        }

        /** Moves every row one position on, so no row holds the first. */
        void shiftRowPositions() {
            jdbc.sql("UPDATE orders.import_row SET position = position + 1 WHERE import_batch_id = ?")
                    .param(batch(n).value())
                    .update();
        }

        /** Gives the first two rows the numbers given. */
        void numbered(int first, int second) {
            updateRow(0, "row_number = " + first);
            updateRow(1, "row_number = " + second);
        }
    }

    // --- states, reached through the domain's commands

    private ImportBatch build(Base base, int n) {
        return switch (base) {
            case PREVIEWED -> previewed(n, cleanRows(n, 2), FileEncoding.UTF_8);
            case PREVIEWED_WITH_ERRORS -> previewed(n, rowsWithErrors(n), FileEncoding.UTF_8);
            case CONFIRMED -> confirmed(n);
            case DISCARDED -> discarded(n);
        };
    }

    private ImportBatch previewed(int n, List<ImportRow> rows, FileEncoding encoding) {
        return ImportBatch.preview(batch(n), HASH, encoding, rows, context(n, rows), today(n), previewedAt(n));
    }

    /** Import {@code b}, one clean row of test {@code b}'s, previewed at a time given. */
    private ImportBatch openPreview(int b, Instant at) {
        List<ImportRow> rows = cleanRows(b, 1);
        LocalDate today = IsraelDateTime.at(at).date().value();
        return ImportBatch.preview(batch(b), HASH, FileEncoding.UTF_8, rows, context(b, rows), today, at);
    }

    /** Two clean rows, confirmed half an hour after the preview. */
    private ImportBatch confirmed(int n) {
        List<ImportRow> rows = cleanRows(n, 2);
        ImportBatch batch = previewed(n, rows, FileEncoding.UTF_8);
        batch.confirm(context(n, rows), true, ids(n, 4), today(n), confirmTime(n));
        return batch;
    }

    private ImportBatch discarded(int n) {
        ImportBatch batch = previewed(n, cleanRows(n, 2), FileEncoding.UTF_8);
        batch.discard(previewedAt(n).plus(HALF_AN_HOUR));
        return batch;
    }

    private ImportBatch expired(int n) {
        ImportBatch batch = previewed(n, cleanRows(n, 2), FileEncoding.UTF_8);
        batch.expire(previewedAt(n).plus(ImportBatch.PREVIEW_LIFETIME));
        return batch;
    }

    /**
     * {@code count} clean rows for new customers tomorrow, each at its own address; the first gives
     * every optional part, the others none.
     */
    private static List<ImportRow> cleanRows(int n, int count) {
        DeliveryDate tomorrow = DeliveryDate.of(today(n).plusDays(1));
        return IntStream.rangeClosed(1, count)
                .mapToObj(r -> (ImportRow) new ImportRow.Parsed(
                        RowNumber.of(r),
                        new ImportLine(
                                address(n, r),
                                Optional.of(NAME),
                                Optional.of(PHONE),
                                tomorrow,
                                r == 1 ? full(tomorrow) : bare())))
                .toList();
    }

    /**
     * A clean row; a row for a new customer with no name and phone, dated yesterday; a row whose
     * address two customers share; and a row the reader could not read.
     */
    private static List<ImportRow> rowsWithErrors(int n) {
        DeliveryDate tomorrow = DeliveryDate.of(today(n).plusDays(1));
        DeliveryDate yesterday = DeliveryDate.of(today(n).minusDays(1));
        return List.of(
                cleanRows(n, 1).get(0),
                new ImportRow.Parsed(
                        RowNumber.of(2),
                        new ImportLine(address(n, 2), Optional.empty(), Optional.empty(), yesterday, bare())),
                new ImportRow.Parsed(
                        RowNumber.of(3),
                        new ImportLine(sharedAddress(n), Optional.of(NAME), Optional.of(PHONE), tomorrow, bare())),
                new ImportRow.Unreadable(RowNumber.of(4), unreadableErrors()));
    }

    private static List<DomainError> unreadableErrors() {
        return List.of(new QuantityOutOfRange(Measure.WEIGHT), new TextTooLong(TextKind.NAME, 100));
    }

    /**
     * The facts the rows are checked against: no customer at a row's own address, two at the shared
     * one, and each date's capacity read under its lock.
     */
    private ImportContext context(int n, List<ImportRow> rows) {
        List<ImportContext.AddressMatch> matches = new ArrayList<>();
        List<DeliveryDate> dates = new ArrayList<>();
        for (ImportRow row : rows) {
            if (row instanceof ImportRow.Parsed parsed) {
                Address address = parsed.line().address();
                CustomerMatch match = address.equals(sharedAddress(n))
                        ? new CustomerMatch.SeveralCustomers(List.of(customer(n, 2), customer(n, 1)))
                        : new CustomerMatch.NoCustomer();
                matches.add(new ImportContext.AddressMatch(NormalizedAddress.of(address), match));
                if (!dates.contains(parsed.line().date())) {
                    dates.add(parsed.line().date());
                }
            }
        }
        return ImportContext.of(
                matches, dates.stream().map(orders::capacityUnderLock).toList(), COMPANY_DEFAULTS);
    }

    private static OrderDetails full(DeliveryDate date) {
        return new OrderDetails(
                Optional.of(TimeWindow.on(date, LocalTime.of(10, 0), LocalTime.of(12, 0))),
                Weight.kilograms(new BigDecimal("12.5")),
                Volume.litres(new BigDecimal("40")),
                Optional.of(ServiceTime.ofSeconds(180)),
                Priority.of(2),
                true,
                ExternalReference.of("SHOP-1001"),
                new Recipient(
                        Optional.of(ContactName.of("דנה לוי")), Optional.of(PhoneNumber.ofStored("+972523456789"))),
                DriverNote.of("לא לצלצל"),
                AccessDetails.of("3", "12", "1234#"));
    }

    private static OrderDetails bare() {
        return new OrderDetails(
                Optional.empty(),
                Weight.kilograms(BigDecimal.ONE),
                Volume.litres(BigDecimal.ONE),
                Optional.empty(),
                Priority.byDefault(),
                false,
                Optional.empty(),
                Recipient.NONE,
                Optional.empty(),
                AccessDetails.NONE);
    }

    // --- keys, times and stored rows

    private static ImportBatchId batch(int b) {
        return ImportBatchId.of(new UUID(KEY_PREFIX, b));
    }

    private static CustomerId customer(int n, int c) {
        return CustomerId.of(new UUID(KEY_PREFIX + 1, 100L * n + c));
    }

    /** New IDs for a confirmation: a customer's and an order's for each row. */
    private static FreshIds ids(int n, int count) {
        return FreshIds.of(IntStream.rangeClosed(1, count)
                .mapToObj(i -> new UUID(KEY_PREFIX + 2, 100L * n + i))
                .toList());
    }

    private static Address address(int n, int r) {
        return Address.of("הנביאים " + (100 * n + r) + ", ירושלים");
    }

    private static Address sharedAddress(int n) {
        return Address.of("שד' בן גוריון " + n + ", חיפה");
    }

    /** Test {@code n}'s preview time, a day apart from every other test's. */
    private static Instant previewedAt(int n) {
        return FIRST_PREVIEW.plus(Duration.ofDays(n));
    }

    private static LocalDate today(int n) {
        return IsraelDateTime.at(previewedAt(n)).date().value();
    }

    private static Instant confirmTime(int n) {
        return previewedAt(n).plus(HALF_AN_HOUR);
    }

    private long storedVersion(int n) {
        return jdbc.sql("SELECT version FROM orders.import_batch WHERE id = ?")
                .param(batch(n).value())
                .query(Long.class)
                .single();
    }

    private long storedRows(int n) {
        return jdbc.sql("SELECT count(*) FROM orders.import_row WHERE import_batch_id = ?")
                .param(batch(n).value())
                .query(Long.class)
                .single();
    }
}
