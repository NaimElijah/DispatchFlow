package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The columns one {@link PreviewRow} is stored in, a row of its {@link ImportBatch}'s row table
 * (ADR-127 decision 3). A preview row has no ID of its own, and its {@link ImportRow} is sealed and
 * full of optional parts, so it is stored as this embeddable: a kind, the row's number and errors,
 * the refused fields of an unreadable row, and the nullable columns of a parsed row's line. {@link
 * #row()} builds the public record every caller sees. Only {@code ImportBatch} makes one.
 *
 * <p>Both error lists are {@link RowErrors}, each one JSON column (ADR-127 decision 7).
 */
@Embeddable
class StoredPreviewRow implements PersonalData {

    /** Which {@link ImportRow} the row holds. */
    private enum RowKind {
        PARSED,
        UNREADABLE
    }

    private int rowNumber;

    @Enumerated(EnumType.STRING)
    private RowKind kind;

    private RowErrors errors;

    private @Nullable RowErrors fieldErrors;

    private @Nullable Address address;

    private @Nullable ContactName name;

    private @Nullable PhoneNumber phone;

    private @Nullable DeliveryDate date;

    @Embedded
    private @Nullable TimeWindow window;

    @Column(name = "weight_kg")
    private @Nullable Weight weight;

    @Column(name = "volume_l")
    private @Nullable Volume volume;

    @Column(name = "service_time_seconds")
    private @Nullable ServiceTime serviceTime;

    private @Nullable Priority priority;

    private @Nullable Boolean leaveAtDoor;

    private @Nullable ExternalReference reference;

    private @Nullable ContactName recipientName;

    private @Nullable PhoneNumber recipientPhone;

    private @Nullable DriverNote note;

    private @Nullable Floor floor;

    private @Nullable Apartment apartment;

    private @Nullable EntranceCode entranceCode;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredPreviewRow() {}

    /**
     * Stores a preview row.
     *
     * @param previewed the row with its errors
     */
    StoredPreviewRow(PreviewRow previewed) {
        this.rowNumber = previewed.row().number().value();
        this.errors = new RowErrors(previewed.errors());
        switch (previewed.row()) {
            case ImportRow.Unreadable unreadable -> {
                this.kind = RowKind.UNREADABLE;
                this.fieldErrors = new RowErrors(unreadable.fieldErrors());
            }
            case ImportRow.Parsed parsed -> {
                this.kind = RowKind.PARSED;
                storeLine(parsed.line());
            }
        }
    }

    private void storeLine(ImportLine line) {
        this.address = line.address();
        this.name = line.name().orElse(null);
        this.phone = line.phone().orElse(null);
        this.date = line.date();
        OrderDetails order = line.order();
        this.window = order.window().orElse(null);
        this.weight = order.weight();
        this.volume = order.volume();
        this.serviceTime = order.serviceTime().orElse(null);
        this.priority = order.priority();
        this.leaveAtDoor = order.leaveAtDoor();
        this.reference = order.reference().orElse(null);
        this.recipientName = order.recipient().name().orElse(null);
        this.recipientPhone = order.recipient().phone().orElse(null);
        this.note = order.note().orElse(null);
        this.floor = order.access().floor().orElse(null);
        this.apartment = order.access().apartment().orElse(null);
        this.entranceCode = order.access().entranceCode().orElse(null);
    }

    /**
     * The preview row these columns store.
     *
     * @return the row with its errors
     * @throws com.dispatchflow.shared.StoredStateInvalid when the stored row number is below 1, or an
     *     unreadable row names no refused field
     * @throws NullPointerException when a column its kind needs is null, which the migration's
     *     checks forbid
     */
    PreviewRow row() {
        RowNumber number = RowNumber.of(rowNumber);
        ImportRow row = switch (kind) {
            case UNREADABLE ->
                new ImportRow.Unreadable(
                        number,
                        Objects.requireNonNull(fieldErrors, "an unreadable row has its refused fields")
                                .errors());
            case PARSED -> new ImportRow.Parsed(number, line());
        };
        return new PreviewRow(row, errors.errors());
    }

    private ImportLine line() {
        OrderDetails order = new OrderDetails(
                Optional.ofNullable(window),
                Objects.requireNonNull(weight, "a parsed row has a weight"),
                Objects.requireNonNull(volume, "a parsed row has a volume"),
                Optional.ofNullable(serviceTime),
                Objects.requireNonNull(priority, "a parsed row has a priority"),
                Objects.requireNonNull(leaveAtDoor, "a parsed row says whether to leave at the door"),
                Optional.ofNullable(reference),
                new Recipient(Optional.ofNullable(recipientName), Optional.ofNullable(recipientPhone)),
                Optional.ofNullable(note),
                new AccessDetails(
                        Optional.ofNullable(floor), Optional.ofNullable(apartment), Optional.ofNullable(entranceCode)));
        return new ImportLine(
                Objects.requireNonNull(address, "a parsed row has an address"),
                Optional.ofNullable(name),
                Optional.ofNullable(phone),
                Objects.requireNonNull(date, "a parsed row has a date"),
                order);
    }

    /**
     * A form that hides the row, because it holds personal data.
     *
     * @return {@code StoredPreviewRow[redacted]}
     */
    @Override
    public String toString() {
        return "StoredPreviewRow[redacted]";
    }
}
