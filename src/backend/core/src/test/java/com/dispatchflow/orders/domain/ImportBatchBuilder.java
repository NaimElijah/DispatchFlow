package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.OrdersTestData.DEFAULTS;
import static com.dispatchflow.orders.domain.OrdersTestData.NOW;
import static com.dispatchflow.orders.domain.OrdersTestData.TODAY;
import static com.dispatchflow.orders.domain.OrdersTestData.TOMORROW;
import static com.dispatchflow.orders.domain.OrdersTestData.YESTERDAY;
import static com.dispatchflow.orders.domain.OrdersTestData.bareDetails;
import static com.dispatchflow.orders.domain.OrdersTestData.customerId;
import static com.dispatchflow.orders.domain.OrdersTestData.details;
import static com.dispatchflow.orders.domain.OrdersTestData.emptyDate;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The one builder of {@link ImportBatch}es for tests: {@link #previewed()} goes through {@link
 * ImportBatch#preview}, and {@link #restored()} through {@link ImportBatch#restore} for a confirmed,
 * discarded or expired batch. It also builds rows and the context they are checked against.
 */
final class ImportBatchBuilder {

    static final ImportBatchId BATCH = ImportBatchId.of(UUID.fromString("0190a5e2-0000-7000-8000-0000000000ba"));
    static final FileHash HASH = new FileHash("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");

    /** An address no active customer has. */
    static final Address NEW_ADDRESS = Address.of("הנביאים 10, ירושלים");

    /** An address one active customer has, {@link #KNOWN_CUSTOMER}. */
    static final Address KNOWN_ADDRESS = Address.of("רח' הרצל 5, תל אביב");

    /** An address two active customers share. */
    static final Address SHARED_ADDRESS = Address.of("שד' בן גוריון 1, חיפה");

    /** The name and phone every row built here gives, and {@link #KNOWN_CUSTOMER}'s own. */
    static final ContactName NAME = ContactName.of("רות ישראלי");

    static final PhoneNumber PHONE = PhoneNumber.parse("052-345-6780");

    static final CustomerMatch.Candidate KNOWN_CUSTOMER =
            new CustomerMatch.Candidate(customerId(1), NAME, PHONE, OrdersTestData.NO_DEFAULT_SERVICE_TIME);

    private FileEncoding encoding = FileEncoding.UTF_8;
    private final List<ImportRow> rows = new ArrayList<>();
    private ImportBatch.State state = new ImportBatch.State.Previewed();

    private ImportBatchBuilder() {}

    static ImportBatchBuilder anImportBatch() {
        return new ImportBatchBuilder();
    }

    /** A clean row for a new customer tomorrow, with no reference. */
    static ImportRow cleanRow(int number) {
        return new ImportRow.Parsed(
                RowNumber.of(number),
                new ImportLine(NEW_ADDRESS, Optional.of(NAME), Optional.of(PHONE), TOMORROW, bareDetails()));
    }

    /** A row with the given address, date and details, and a name and phone. */
    static ImportRow row(int number, Address address, DeliveryDate date, OrderDetails order) {
        return new ImportRow.Parsed(
                RowNumber.of(number), new ImportLine(address, Optional.of(NAME), Optional.of(PHONE), date, order));
    }

    /** A row for a known customer tomorrow, with its own service time and the given reference. */
    static ImportRow rowWithReference(int number, ExternalReference reference) {
        return rowWithReferenceOn(number, reference, TOMORROW);
    }

    /** A row for a known customer on a date, with its own service time and the given reference. */
    static ImportRow rowWithReferenceOn(int number, ExternalReference reference, DeliveryDate date) {
        OrderDetails base = details(date);
        OrderDetails withReference = new OrderDetails(
                base.window(),
                base.weight(),
                base.volume(),
                base.serviceTime(),
                base.priority(),
                base.leaveAtDoor(),
                Optional.of(reference),
                base.recipient(),
                base.note(),
                base.access());
        return row(number, KNOWN_ADDRESS, date, withReference);
    }

    /**
     * The context where the three addresses match none, one and two customers; with no capacity
     * given, tomorrow and yesterday hold no order.
     */
    static ImportContext context(DateCapacity... capacities) {
        List<DateCapacity> dates =
                capacities.length == 0 ? List.of(emptyDate(TOMORROW), emptyDate(YESTERDAY)) : List.of(capacities);
        return ImportContext.of(
                List.of(
                        new ImportContext.AddressMatch(
                                NormalizedAddress.of(NEW_ADDRESS), new CustomerMatch.NoCustomer()),
                        new ImportContext.AddressMatch(
                                NormalizedAddress.of(KNOWN_ADDRESS), new CustomerMatch.OneCustomer(KNOWN_CUSTOMER)),
                        new ImportContext.AddressMatch(
                                NormalizedAddress.of(SHARED_ADDRESS),
                                new CustomerMatch.SeveralCustomers(List.of(customerId(3), customerId(2))))),
                dates,
                DEFAULTS);
    }

    ImportBatchBuilder withRows(ImportRow... more) {
        rows.addAll(List.of(more));
        return this;
    }

    ImportBatchBuilder readAsWindows1255() {
        this.encoding = FileEncoding.WINDOWS_1255;
        return this;
    }

    ImportBatchBuilder inState(ImportBatch.State inState) {
        this.state = inState;
        return this;
    }

    ImportBatch previewed() {
        return previewedWith(context());
    }

    ImportBatch previewedWith(ImportContext context) {
        return ImportBatch.preview(BATCH, HASH, encoding, rowsOrOne(), context, TODAY, NOW);
    }

    ImportBatchSnapshot snapshot() {
        // Only a preview keeps rows: a confirmed, discarded or expired batch keeps none.
        List<PreviewRow> preview = !(state instanceof ImportBatch.State.Previewed)
                ? List.of()
                : rowsOrOne().stream()
                        .map(row -> new PreviewRow(row, List.of()))
                        .toList();
        return new ImportBatchSnapshot(BATCH, HASH, encoding, preview, NOW, state, Version.of(1));
    }

    ImportBatch restored() {
        return ImportBatch.restore(snapshot());
    }

    private List<ImportRow> rowsOrOne() {
        return rows.isEmpty() ? List.of(cleanRow(1)) : List.copyOf(rows);
    }

    static Instant inMinutes(long minutes) {
        return NOW.plusSeconds(minutes * 60);
    }
}
