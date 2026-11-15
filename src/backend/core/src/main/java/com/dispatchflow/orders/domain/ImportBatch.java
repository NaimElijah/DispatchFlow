package com.dispatchflow.orders.domain;

import com.dispatchflow.orders.domain.OrdersError.AmbiguousAddress;
import com.dispatchflow.orders.domain.OrdersError.DateFull;
import com.dispatchflow.orders.domain.OrdersError.DuplicateOrderReference;
import com.dispatchflow.orders.domain.OrdersError.EncodingNotConfirmed;
import com.dispatchflow.orders.domain.OrdersError.ImportAlreadyConfirmed;
import com.dispatchflow.orders.domain.OrdersError.ImportDiscarded;
import com.dispatchflow.orders.domain.OrdersError.ImportExpired;
import com.dispatchflow.orders.domain.OrdersError.ImportHasErrors;
import com.dispatchflow.orders.domain.OrdersError.ImportNotYetExpired;
import com.dispatchflow.orders.domain.OrdersError.NewCustomerNeedsNameAndPhone;
import com.dispatchflow.orders.domain.OrdersError.RowRefused;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.RuleViolations;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One uploaded order file, previewed and then confirmed all or nothing (DL §4.3, §9.4, ADR-086). It
 * owns the rules of an import: every row is checked and every error listed at once (FR-203), a file
 * with any error imports nothing (FR-204), a file read as Windows-1255 is confirmed only when the
 * dispatcher confirms its encoding (FR-313), and a preview is confirmed once, within the hour after it
 * was made (FR-399). Once confirmed, discarded or expired, a batch keeps none of its rows, which hold
 * personal data (NFR-31, ADR-086).
 *
 * <p>Each row's checks are an order's ({@link Order#place}'s, minus the first-refusal rule) plus the
 * rules across rows: the address names one customer or creates one (FR-310 to FR-312), a reference
 * is unique on its date within the file and among the date's orders (FR-211), and a date's orders,
 * existing and imported, stay within the limit (BR-101, BR-113).
 *
 * <p>Its rows hold names, phones, addresses and entrance codes, which are personal data.
 */
public final class ImportBatch extends AggregateRoot implements PersonalData {

    /** How long a preview may wait for confirmation (FR-399, ADR-086). */
    public static final Duration PREVIEW_LIFETIME = Duration.ofHours(1);

    /** Where the batch is in its lifecycle (DL §9.4). */
    public sealed interface State {

        /** Uploaded and checked, waiting for the dispatcher. */
        record Previewed() implements State {}

        /**
         * Confirmed: its customers and orders were created.
         *
         * @param at when it was confirmed
         */
        record Confirmed(Instant at) implements State {

            /** Checks the time is given. */
            public Confirmed {
                Objects.requireNonNull(at, "at");
            }
        }

        /**
         * Discarded by the dispatcher; its rows are gone (FR-399).
         *
         * @param at when it was discarded
         */
        record Discarded(Instant at) implements State {

            /** Checks the time is given. */
            public Discarded {
                Objects.requireNonNull(at, "at");
            }
        }

        /**
         * Not confirmed within its hour; its rows are gone (FR-399).
         *
         * @param at when the sweep expired it
         */
        record Expired(Instant at) implements State {

            /** Checks the time is given. */
            public Expired {
                Objects.requireNonNull(at, "at");
            }
        }
    }

    private final ImportBatchId id;
    private final FileHash hash;
    private final FileEncoding encoding;
    private List<PreviewRow> rows;
    private final Instant previewedAt;
    private State state;

    private ImportBatch(ImportBatchSnapshot snapshot) {
        super(snapshot.version());
        this.id = snapshot.id();
        this.hash = snapshot.hash();
        this.encoding = snapshot.encoding();
        this.rows = snapshot.rows();
        this.previewedAt = snapshot.previewedAt();
        this.state = snapshot.state();
    }

    /**
     * Previews an uploaded file: every row is checked, and every error is kept with its row to be
     * shown (FR-203, ADR-086). A preview is never refused; its errors refuse the confirmation.
     *
     * @param id the new batch's ID
     * @param hash the file's hash
     * @param encoding how the reader decoded it
     * @param rows every row the reader parsed
     * @param context the matches, date snapshots and company defaults the rows are checked against
     * @param today today in {@code Asia/Jerusalem}
     * @param now the time of the preview, from which its hour counts
     * @return the batch, previewed, at {@link Version#NEW}, with its rows in row order
     * @throws java.util.NoSuchElementException when the context lacks a row's address or date, a
     *     programming error
     * @throws StoredStateInvalid when two rows share a number: the reader numbers rows, so this is a
     *     reader bug, refused here so a stored batch never fails {@link #restore}
     */
    public static ImportBatch preview(
            ImportBatchId id,
            FileHash hash,
            FileEncoding encoding,
            List<ImportRow> rows,
            ImportContext context,
            LocalDate today,
            Instant now) {
        List<ImportRow> ordered =
                rows.stream().sorted(Comparator.comparing(ImportRow::number)).toList();
        if (ordered.stream().map(ImportRow::number).distinct().count() != ordered.size()) {
            throw new StoredStateInvalid(ImportBatch.class, "two rows share a number");
        }
        List<List<DomainError>> errors = check(ordered, context, today);
        List<PreviewRow> previewed = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            previewed.add(new PreviewRow(ordered.get(i), errors.get(i)));
        }
        return new ImportBatch(
                new ImportBatchSnapshot(id, hash, encoding, previewed, now, new State.Previewed(), Version.NEW));
    }

    /**
     * Rebuilds a stored batch, checking the rules that hold at rest. Only the persistence adapter
     * calls it (DL §3.5).
     *
     * @param snapshot the stored batch
     * @return the batch, at the stored version
     * @throws StoredStateInvalid when a confirmed, discarded or expired batch keeps a row, the row
     *     numbers are not unique and ascending, its state is older than its preview, or it was
     *     confirmed after its hour
     */
    public static ImportBatch restore(ImportBatchSnapshot snapshot) {
        if (!(snapshot.state() instanceof State.Previewed) && !snapshot.rows().isEmpty()) {
            throw new StoredStateInvalid(ImportBatch.class, "a confirmed, discarded or expired import keeps a row");
        }
        for (int i = 1; i < snapshot.rows().size(); i++) {
            RowNumber previous = snapshot.rows().get(i - 1).row().number();
            if (snapshot.rows().get(i).row().number().compareTo(previous) <= 0) {
                throw new StoredStateInvalid(ImportBatch.class, "the row numbers are not unique and ascending");
            }
        }
        Optional<Instant> changedAt = switch (snapshot.state()) {
            case State.Previewed previewed -> Optional.empty();
            case State.Confirmed confirmed -> Optional.of(confirmed.at());
            case State.Discarded discarded -> Optional.of(discarded.at());
            case State.Expired expired -> Optional.of(expired.at());
        };
        if (changedAt.filter(at -> at.isBefore(snapshot.previewedAt())).isPresent()) {
            throw new StoredStateInvalid(ImportBatch.class, "the import's state is older than its preview");
        }
        if (snapshot.state() instanceof State.Confirmed confirmed
                && !confirmed.at().isBefore(snapshot.previewedAt().plus(PREVIEW_LIFETIME))) {
            throw new StoredStateInvalid(ImportBatch.class, "the import was confirmed after its hour");
        }
        return new ImportBatch(snapshot);
    }

    /**
     * Confirms the import (ADR-086). Every row is checked again against the current facts, and the
     * new customers and orders are returned for the use case to save with the batch (DL §6.1). The
     * batch then keeps no rows: the orders and customers hold what the dispatcher imported.
     *
     * <p>The new IDs are drawn in row order: for each row, first its new customer's, when the row
     * creates one, then its order's. Rows whose addresses normalize alike share one new customer,
     * made from the first of them (ADR-102). A row that gives no recipient, and whose name or phone
     * differs from the customer it matches or shares, makes them its order's recipient (ADR-115).
     *
     * @param context the matches, date snapshots and company defaults, read again
     * @param encodingConfirmed whether the dispatcher confirmed the decoded preview (FR-313)
     * @param ids new IDs: one per row, plus one per new customer
     * @param today today in {@code Asia/Jerusalem}
     * @param now the time of the confirmation; a time before the preview, from a clock that stepped
     *     back, is recorded as the preview's, so the batch still restores (ADR-115)
     * @return the new customers and orders
     * @throws RuleViolation with {@link ImportAlreadyConfirmed}, {@link ImportDiscarded} or {@link
     *     ImportExpired}, for a batch no longer previewed or past its hour, then with {@link
     *     EncodingNotConfirmed}
     * @throws RuleViolations with the summary {@link ImportHasErrors} and a {@link RowRefused} for
     *     each error of each row, in row order (FR-203, FR-204)
     * @throws java.util.NoSuchElementException when the context lacks a row's address or date, or
     *     {@code ids} runs out: programming errors
     */
    public ImportResult confirm(
            ImportContext context, boolean encodingConfirmed, FreshIds ids, LocalDate today, Instant now) {
        refuseUnlessPreviewed();
        if (isOverdue(now)) {
            throw new RuleViolation(new ImportExpired());
        }
        if (encoding == FileEncoding.WINDOWS_1255 && !encodingConfirmed) {
            throw new RuleViolation(new EncodingNotConfirmed());
        }
        List<ImportRow> imported = rows.stream().map(PreviewRow::row).toList();
        List<List<DomainError>> errors = check(imported, context, today);
        List<RowRefused> refusals = new ArrayList<>();
        int rejectedRows = 0;
        for (int i = 0; i < imported.size(); i++) {
            RowNumber number = imported.get(i).number();
            errors.get(i).forEach(error -> refusals.add(new RowRefused(number, error)));
            rejectedRows += errors.get(i).isEmpty() ? 0 : 1;
        }
        RuleViolations.throwIfAny(new ImportHasErrors(rejectedRows), refusals);

        ImportResult result = create(imported, context, ids, now);
        rows = List.of();
        state = new State.Confirmed(notBeforePreview(now));
        return result;
    }

    /**
     * Discards the preview at the dispatcher's request; its rows are deleted at once (FR-399).
     *
     * @param now the time of discarding; a time before the preview, from a clock that stepped back,
     *     is recorded as the preview's, so the batch still restores (ADR-115)
     * @throws RuleViolation with {@link ImportAlreadyConfirmed}, {@link ImportDiscarded} or {@link
     *     ImportExpired}
     */
    public void discard(Instant now) {
        refuseUnlessPreviewed();
        rows = List.of();
        state = new State.Discarded(notBeforePreview(now));
    }

    /**
     * Expires a preview whose hour has passed; its rows are deleted (FR-399). The hourly sweep calls
     * it (DL §10.3).
     *
     * @param now the time of the sweep, which is never before the preview: an earlier one is refused
     *     as not yet expired
     * @throws RuleViolation with {@link ImportAlreadyConfirmed}, {@link ImportDiscarded} or {@link
     *     ImportExpired}, then with {@link ImportNotYetExpired} before the hour has passed
     */
    public void expire(Instant now) {
        refuseUnlessPreviewed();
        if (!isOverdue(now)) {
            throw new RuleViolation(new ImportNotYetExpired());
        }
        rows = List.of();
        state = new State.Expired(now);
    }

    /**
     * The time a state change is recorded at: {@code now}, or the preview's time when a clock that
     * stepped back gives an earlier one, which {@link #restore} would refuse. Expiring needs no such
     * guard, because it is refused before the hour has passed.
     */
    private Instant notBeforePreview(Instant now) {
        return now.isBefore(previewedAt) ? previewedAt : now;
    }

    private boolean isOverdue(Instant now) {
        return !now.isBefore(previewedAt.plus(PREVIEW_LIFETIME));
    }

    private void refuseUnlessPreviewed() {
        switch (state) {
            case State.Previewed previewed -> {}
            case State.Confirmed confirmed -> throw new RuleViolation(new ImportAlreadyConfirmed());
            case State.Discarded discarded -> throw new RuleViolation(new ImportDiscarded());
            case State.Expired expired -> throw new RuleViolation(new ImportExpired());
        }
    }

    /**
     * Every row's errors, in row order; a row's list is empty when it imports. Each row costs a
     * constant number of lookups, so a file of any length is checked in one pass.
     */
    private static List<List<DomainError>> check(List<ImportRow> rows, ImportContext context, LocalDate today) {
        // Only looked up, never iterated, so their order never shows.
        Map<DeliveryDate, Integer> joining = new HashMap<>();
        Map<DeliveryDate, Set<ExternalReference>> references = new HashMap<>();
        List<List<DomainError>> all = new ArrayList<>();
        for (ImportRow row : rows) {
            all.add(
                    switch (row) {
                        case ImportRow.Unreadable unreadable -> unreadable.fieldErrors();
                        case ImportRow.Parsed parsed -> checkLine(parsed.line(), context, today, joining, references);
                    });
        }
        return all;
    }

    private static List<DomainError> checkLine(
            ImportLine line,
            ImportContext context,
            LocalDate today,
            Map<DeliveryDate, Integer> joining,
            Map<DeliveryDate, Set<ExternalReference>> references) {
        List<DomainError> errors = new ArrayList<>();
        Optional<OrderDefaults> defaults = switch (context.matchFor(NormalizedAddress.of(line.address()))) {
            case CustomerMatch.NoCustomer none -> {
                if (line.name().isEmpty() || line.phone().isEmpty()) {
                    errors.add(new NewCustomerNeedsNameAndPhone());
                }
                yield Optional.of(context.companyDefaults());
            }
            case CustomerMatch.OneCustomer one -> Optional.of(one.candidate().defaults());
            case CustomerMatch.SeveralCustomers several -> {
                errors.add(new AmbiguousAddress(several.customers()));
                yield Optional.empty();
            }
        };
        DeliveryDate date = line.date();
        errors.addAll(Order.problemsPlacing(line.order(), defaults, date, today));

        DateCapacity capacity = context.capacityFor(date);
        Set<ExternalReference> taken = references.computeIfAbsent(date, unused -> new HashSet<>());
        line.order().reference().ifPresent(reference -> {
            if (capacity.referenceTakenByAnother(reference, Optional.empty()) || !taken.add(reference)) {
                errors.add(new DuplicateOrderReference(date));
            }
        });
        int alreadyJoining = joining.getOrDefault(date, 0);
        if (capacity.count() + alreadyJoining >= DateCapacity.LIMIT) {
            errors.add(new DateFull(date, DateCapacity.LIMIT));
        } else {
            joining.put(date, alreadyJoining + 1);
        }
        return List.copyOf(errors);
    }

    /**
     * Builds the customers and orders of rows that were all checked and found clean. Rows whose
     * addresses normalize alike share one new customer, made from the first of them, and a later row
     * naming someone else gives its order that person as the recipient.
     */
    private static ImportResult create(List<ImportRow> rows, ImportContext context, FreshIds ids, Instant now) {
        // Only looked up, never iterated: the new customers keep their first row's order in the list.
        Map<NormalizedAddress, CustomerMatch.Candidate> createdFor = new HashMap<>();
        List<Customer> created = new ArrayList<>();
        List<Order> orders = new ArrayList<>();
        for (ImportRow row : rows) {
            // Every row is parsed here: an unreadable row always carries an error, refused above.
            ImportLine line = ((ImportRow.Parsed) row).line();
            NormalizedAddress address = NormalizedAddress.of(line.address());
            CustomerMatch.Candidate candidate = context.matchFor(address)
                    .customer()
                    .orElseGet(() -> createdFor.computeIfAbsent(address, unused -> {
                        ContactName name = line.name().orElseThrow();
                        PhoneNumber phone = line.phone().orElseThrow();
                        Customer customer = Customer.registerFromImport(
                                CustomerId.of(ids.next()), name, phone, line.address(), context.companyDefaults(), now);
                        created.add(customer);
                        return new CustomerMatch.Candidate(customer.id(), name, phone, customer.orderDefaults());
                    }));
            OrderId order = OrderId.of(ids.next());
            OrderDetails details = line.order().withRecipient(recipientOf(line, candidate));
            orders.add(Order.checked(order, candidate.id(), line.date(), details, candidate.defaults(), now));
        }
        return new ImportResult(created, orders);
    }

    /**
     * The order's recipient (FR-87, ADR-115). A row whose recipient columns are filled keeps them. A
     * row that gives none, and whose name or phone differs from its customer's, names someone else at
     * the address, such as a neighbour in another flat: its name and phone become the recipient, so
     * that person's messages reach them and not the customer (FR-348).
     */
    private static Recipient recipientOf(ImportLine line, CustomerMatch.Candidate customer) {
        Recipient given = line.order().recipient();
        if (!given.equals(Recipient.NONE)) {
            return given;
        }
        boolean otherName =
                line.name().filter(name -> !name.equals(customer.name())).isPresent();
        boolean otherPhone =
                line.phone().filter(phone -> !phone.equals(customer.phone())).isPresent();
        return otherName || otherPhone ? new Recipient(line.name(), line.phone()) : Recipient.NONE;
    }

    /**
     * The batch's ID.
     *
     * @return the ID
     */
    public ImportBatchId id() {
        return id;
    }

    /**
     * Where the batch is in its lifecycle.
     *
     * @return the state
     */
    public State state() {
        return state;
    }

    /**
     * Its rows with their errors, for the preview screen (FR-203).
     *
     * @return the rows in row order; empty once confirmed, discarded or expired
     */
    public List<PreviewRow> rows() {
        return rows;
    }

    /**
     * Everything the batch holds, for the persistence adapter.
     *
     * @return the snapshot, at the loaded version
     */
    public ImportBatchSnapshot snapshot() {
        return new ImportBatchSnapshot(id, hash, encoding, rows, previewedAt, state, version());
    }

    /**
     * A form that hides the batch, because it holds personal data.
     *
     * @return {@code ImportBatch[redacted]}
     */
    @Override
    public String toString() {
        return "ImportBatch[redacted]";
    }
}
