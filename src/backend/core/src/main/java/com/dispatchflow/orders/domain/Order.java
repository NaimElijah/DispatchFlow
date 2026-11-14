package com.dispatchflow.orders.domain;

import com.dispatchflow.orders.domain.OrdersError.AlreadyOnThatDate;
import com.dispatchflow.orders.domain.OrdersError.DateFull;
import com.dispatchflow.orders.domain.OrdersError.DateInPast;
import com.dispatchflow.orders.domain.OrdersError.DuplicateOrderReference;
import com.dispatchflow.orders.domain.OrdersError.NoServiceTime;
import com.dispatchflow.orders.domain.OrdersError.OnlyFailedOrDroppedOrdersArePutBack;
import com.dispatchflow.orders.domain.OrdersError.OrderAlreadyCancelled;
import com.dispatchflow.orders.domain.OrdersError.OrderAnonymized;
import com.dispatchflow.orders.domain.OrdersError.RetentionNotOver;
import com.dispatchflow.orders.domain.OrdersError.StopAlreadyHasFact;
import com.dispatchflow.orders.domain.OrdersError.WindowNotOnDate;
import com.dispatchflow.orders.events.OrderCancelled;
import com.dispatchflow.orders.events.OrderRescheduled;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Goods to deliver to a customer on a date (DL §4.3, §17). It owns the rules about one order: its
 * date isn't past (BR-116), its window lies on its date (BR-35), it has a service time (BR-124), it
 * can't change once its stop has a fact (BR-92), a cancelled order stays cancelled, and only a failed
 * or dropped order is put back, starting the next attempt (FR-75, FR-258). With a {@link
 * DateCapacity} it also keeps a date's orders under the limit (BR-101, BR-113) and their references
 * unique (FR-211, ADR-102).
 *
 * <p>Whether it is planned, dropped, delivered or failed is not stored here: {@link
 * OrderStatus#derive} works it out from its three owners (DL §9.6).
 *
 * <p>Its recipient, delivery note, access details and outside reference are personal data, removed
 * 90 days after its date (NFR-257).
 */
public final class Order extends AggregateRoot implements PersonalData {

    /** Days after its date that an order's personal fields are kept (NFR-257). */
    public static final int RETENTION_DAYS = 90;

    /** Whether the order is active or cancelled (DL §4.3). A cancelled order stays cancelled. */
    public sealed interface State {

        /** The order may still be served. */
        record Active() implements State {}

        /**
         * The order was cancelled (FR-58); late reports are judged against the time (BR-88,
         * BR-130).
         *
         * @param at when it was cancelled
         */
        record Cancelled(Instant at) implements State {

            /** Checks the time is given. */
            public Cancelled {
                Objects.requireNonNull(at, "at");
            }
        }
    }

    private final OrderId id;
    private final CustomerId customer;
    private DeliveryDate date;
    private TimeWindow window;
    private Weight weight;
    private Volume volume;
    private ServiceTime serviceTime;
    private Priority priority;
    private boolean leaveAtDoor;
    private @Nullable ExternalReference reference;
    private Recipient recipient;
    private @Nullable DriverNote note;
    private AccessDetails access;
    private Attempt attempt;
    private State state;
    private boolean anonymized;
    private Instant lastChangedAt;

    private Order(OrderSnapshot snapshot) {
        super(snapshot.version());
        this.id = snapshot.id();
        this.customer = snapshot.customer();
        this.date = snapshot.date();
        this.window = snapshot.window();
        this.weight = snapshot.weight();
        this.volume = snapshot.volume();
        this.serviceTime = snapshot.serviceTime();
        this.priority = snapshot.priority();
        this.leaveAtDoor = snapshot.leaveAtDoor();
        this.reference = snapshot.reference().orElse(null);
        this.recipient = snapshot.recipient();
        this.note = snapshot.note().orElse(null);
        this.access = snapshot.access();
        this.attempt = snapshot.attempt();
        this.state = snapshot.state();
        this.anonymized = snapshot.anonymized();
        this.lastChangedAt = snapshot.lastChangedAt();
    }

    /**
     * Places a new order on the capacity's date (FR-09, FR-11).
     *
     * @param id the new order's ID
     * @param customer its customer
     * @param details what it is placed with
     * @param defaults its customer's defaults, for a window or service time not given (BR-49,
     *     BR-124)
     * @param capacity the date's uncancelled orders, read under the date's lock; the order takes its
     *     date
     * @param today today in {@code Asia/Jerusalem}
     * @param now the time of placing, its first change time
     * @return the order, at {@link Version#NEW}, with no event
     * @throws RuleViolation with the first of, in this order: {@link DateInPast} (BR-116); {@link
     *     NonexistentLocalTime} when the default window falls in the hour the clock skips on the date
     *     (BR-111), or {@link WindowNotOnDate} (BR-35); {@link NoServiceTime} (BR-124); {@link
     *     DuplicateOrderReference} (FR-211); {@link DateFull} (BR-101)
     */
    public static Order place(
            OrderId id,
            CustomerId customer,
            OrderDetails details,
            OrderDefaults defaults,
            DateCapacity capacity,
            LocalDate today,
            Instant now) {
        List<DomainError> problems =
                new ArrayList<>(problemsPlacing(details, Optional.of(defaults), capacity.date(), today));
        problems.addAll(problemsJoining(capacity, Optional.of(id), details.reference()));
        if (!problems.isEmpty()) {
            throw new RuleViolation(problems.get(0));
        }
        return checked(id, customer, capacity.date(), details, defaults, now);
    }

    /**
     * The rules of a new order that need neither the date's other orders nor the clock beyond
     * {@code today}, every one of them, so an import row lists them all (ADR-086). Without defaults,
     * as for a row whose address matches several customers, the window is checked only when given
     * and the service time not at all.
     */
    static List<DomainError> problemsPlacing(
            OrderDetails details, Optional<OrderDefaults> defaults, DeliveryDate date, LocalDate today) {
        List<DomainError> problems = new ArrayList<>();
        if (date.isBefore(today)) {
            problems.add(new DateInPast(date));
        }
        try {
            Optional<TimeWindow> window = details.window()
                    .or(() -> defaults.map(known -> known.window().on(date)));
            window.filter(given -> !given.date().equals(date))
                    .ifPresent(offDate -> problems.add(new WindowNotOnDate(offDate, date)));
        } catch (RuleViolation skippedHour) {
            // The default window can't exist on this date: listed beside the row's other refusals.
            problems.add(skippedHour.error());
        }
        defaults.filter(known -> details.serviceTime().or(known::serviceTime).isEmpty())
                .ifPresent(known -> problems.add(new NoServiceTime()));
        return List.copyOf(problems);
    }

    /**
     * The rules of an order joining a date through its snapshot: its reference is free there
     * (FR-211), and the date has room, unless the order is already one of its orders (BR-113).
     */
    static List<OrdersError> problemsJoining(
            DateCapacity capacity, Optional<OrderId> self, Optional<ExternalReference> reference) {
        List<OrdersError> problems = new ArrayList<>();
        if (reference.filter(ref -> capacity.referenceTakenByAnother(ref, self)).isPresent()) {
            problems.add(new DuplicateOrderReference(capacity.date()));
        }
        boolean alreadyCounted = self.filter(capacity::holds).isPresent();
        if (!alreadyCounted && capacity.count() >= DateCapacity.LIMIT) {
            problems.add(new DateFull(capacity.date(), DateCapacity.LIMIT));
        }
        return List.copyOf(problems);
    }

    /** Builds an order whose rules were checked, by {@link #place} or an import's confirmation. */
    static Order checked(
            OrderId id,
            CustomerId customer,
            DeliveryDate date,
            OrderDetails details,
            OrderDefaults defaults,
            Instant now) {
        ServiceTime serviceTime = details.serviceTime()
                .or(defaults::serviceTime)
                .orElseThrow(() -> new RuleViolation(new NoServiceTime()));
        return new Order(new OrderSnapshot(
                id,
                customer,
                date,
                windowOf(details, defaults, date),
                details.weight(),
                details.volume(),
                serviceTime,
                details.priority(),
                details.leaveAtDoor(),
                details.reference(),
                details.recipient(),
                details.note(),
                details.access(),
                Attempt.FIRST,
                new State.Active(),
                false,
                now,
                Version.NEW));
    }

    private static TimeWindow windowOf(OrderDetails details, OrderDefaults defaults, DeliveryDate date) {
        return details.window().orElseGet(() -> defaults.window().on(date));
    }

    /**
     * Rebuilds a stored order, checking the rules that hold at rest. Only the persistence adapter
     * calls it (DL §3.5).
     *
     * @param snapshot the stored order
     * @return the order, at the stored version
     * @throws StoredStateInvalid when the window lies off the order's date, an anonymized order still
     *     holds a personal field, or the cancellation is later than the last change
     */
    public static Order restore(OrderSnapshot snapshot) {
        if (!snapshot.window().date().equals(snapshot.date())) {
            throw new StoredStateInvalid(Order.class, "the window lies off the order's date");
        }
        boolean holdsPersonalFields = !snapshot.recipient().equals(Recipient.NONE)
                || snapshot.note().isPresent()
                || snapshot.access().isGiven()
                || snapshot.reference().isPresent();
        if (snapshot.anonymized() && holdsPersonalFields) {
            throw new StoredStateInvalid(Order.class, "an anonymized order holds a personal field");
        }
        if (snapshot.state() instanceof State.Cancelled cancelled
                && cancelled.at().isAfter(snapshot.lastChangedAt())) {
            throw new StoredStateInvalid(Order.class, "the order was cancelled after its last change");
        }
        return new Order(snapshot);
    }

    /**
     * Changes the order's fields other than its date and reference (FR-57). On a published plan the
     * change reaches the route through a re-optimization (FR-206); the last change time tells
     * publishing and re-optimization it changed (FR-397, BR-82).
     *
     * @param change the new values
     * @param progress what has happened at its stop, from Live Operations
     * @param now the time of the change
     * @throws RuleViolation with {@link OrderAnonymized}, {@link OrderAlreadyCancelled}, {@link
     *     StopAlreadyHasFact} (BR-92) or {@link WindowNotOnDate} (BR-35), in that order
     */
    public void change(OrderChange change, StopState progress, Instant now) {
        refuseUnlessOpen(progress);
        if (!change.window().date().equals(date)) {
            throw new RuleViolation(new WindowNotOnDate(change.window(), date));
        }
        window = change.window();
        weight = change.weight();
        volume = change.volume();
        serviceTime = change.serviceTime();
        priority = change.priority();
        leaveAtDoor = change.leaveAtDoor();
        recipient = change.recipient();
        note = change.note().orElse(null);
        access = change.access();
        lastChangedAt = now;
    }

    /**
     * Moves the order to another date, with a window on that date (FR-57, FR-315). Its reference is
     * checked there exactly as a new order's (ADR-102), and it needs room there (BR-113).
     *
     * @param window the window on the new date
     * @param target the new date's uncancelled orders, read under its lock
     * @param progress what has happened at its stop, from Live Operations
     * @param today today in {@code Asia/Jerusalem}
     * @param now the time of the move
     * @throws RuleViolation with the first of, in this order: {@link OrderAnonymized}, {@link
     *     OrderAlreadyCancelled}, {@link StopAlreadyHasFact} (BR-92), {@link AlreadyOnThatDate},
     *     {@link DateInPast} (BR-116), {@link WindowNotOnDate} (BR-35), {@link
     *     DuplicateOrderReference} (FR-211), {@link DateFull} (BR-101)
     */
    public void reschedule(TimeWindow window, DateCapacity target, StopState progress, LocalDate today, Instant now) {
        refuseUnlessOpen(progress);
        DeliveryDate to = target.date();
        if (to.equals(date)) {
            throw new RuleViolation(new AlreadyOnThatDate(date));
        }
        refuseJoining(window, target, today);
        DeliveryDate from = date;
        date = to;
        this.window = window;
        lastChangedAt = now;
        raise(
                OrderRescheduled.class,
                id.value(),
                eventId -> new OrderRescheduled(eventId, now, id, from, to, attempt.number()));
    }

    /**
     * Cancels the order (FR-58). Planning takes its stop off at once (FR-207, FR-316).
     *
     * @param progress what has happened at its stop, from Live Operations
     * @param now the time of the cancellation
     * @throws RuleViolation with {@link OrderAnonymized}, {@link OrderAlreadyCancelled} or {@link
     *     StopAlreadyHasFact} (BR-92), in that order
     */
    public void cancel(StopState progress, Instant now) {
        refuseUnlessOpen(progress);
        state = new State.Cancelled(now);
        lastChangedAt = now;
        raise(
                OrderCancelled.class,
                id.value(),
                eventId -> new OrderCancelled(eventId, now, id, date, attempt.number()));
    }

    /**
     * Puts a failed or dropped order back, for today's re-optimization or on another date, as its
     * next attempt (FR-75, FR-258, BR-94, BR-95). The earlier attempt's outcome stays as recorded.
     * On its own date it doesn't count again toward the limit (BR-113); on another date its
     * reference is checked as a new order's (ADR-102).
     *
     * @param placement where Planning holds the current attempt
     * @param progress what has happened at its stop, from Live Operations
     * @param window the window on the target date
     * @param target the target date's uncancelled orders, read under its lock
     * @param today today in {@code Asia/Jerusalem}
     * @param now the time of the put-back
     * @throws RuleViolation with the first of, in this order: {@link OrderAnonymized}, {@link
     *     OrderAlreadyCancelled}, {@link OnlyFailedOrDroppedOrdersArePutBack}, {@link DateInPast}
     *     (BR-116), {@link WindowNotOnDate} (BR-35), {@link DuplicateOrderReference} (FR-211),
     *     {@link DateFull} (BR-101)
     */
    public void putBack(
            Placement placement,
            StopState progress,
            TimeWindow window,
            DateCapacity target,
            LocalDate today,
            Instant now) {
        refuseUnlessUncancelled();
        OrderStatus status = OrderStatus.derive(this, placement, progress);
        if (status != OrderStatus.FAILED && status != OrderStatus.DROPPED) {
            throw new RuleViolation(new OnlyFailedOrDroppedOrdersArePutBack(status));
        }
        refuseJoining(window, target, today);
        date = target.date();
        this.window = window;
        attempt = attempt.next();
        lastChangedAt = now;
    }

    /**
     * Removes the order's personal fields once 90 days have passed since its date (NFR-257,
     * ADR-075): its recipient, delivery note, access details and outside reference, which an outside
     * system may build from a customer's data (ADR-102). The order and its figures stay.
     *
     * @param today today in {@code Asia/Jerusalem}
     * @throws RuleViolation with {@link OrderAnonymized} when already done, or with {@link
     *     RetentionNotOver} before the 90th day after its date
     */
    public void anonymize(LocalDate today) {
        if (anonymized) {
            throw new RuleViolation(new OrderAnonymized(id));
        }
        if (today.isBefore(date.value().plusDays(RETENTION_DAYS))) {
            throw new RuleViolation(new RetentionNotOver());
        }
        recipient = Recipient.NONE;
        note = null;
        access = AccessDetails.NONE;
        reference = null;
        anonymized = true;
    }

    private void refuseUnlessUncancelled() {
        if (anonymized) {
            throw new RuleViolation(new OrderAnonymized(id));
        }
        if (state instanceof State.Cancelled) {
            throw new RuleViolation(new OrderAlreadyCancelled(id));
        }
    }

    private void refuseUnlessOpen(StopState progress) {
        refuseUnlessUncancelled();
        if (progress.hasFactFor(attempt)) {
            throw new RuleViolation(new StopAlreadyHasFact(id));
        }
    }

    private void refuseJoining(TimeWindow newWindow, DateCapacity target, LocalDate today) {
        DeliveryDate to = target.date();
        if (to.isBefore(today)) {
            throw new RuleViolation(new DateInPast(to));
        }
        if (!newWindow.date().equals(to)) {
            throw new RuleViolation(new WindowNotOnDate(newWindow, to));
        }
        List<OrdersError> problems = problemsJoining(target, Optional.of(id), Optional.ofNullable(reference));
        if (!problems.isEmpty()) {
            throw new RuleViolation(problems.get(0));
        }
    }

    /**
     * The order's ID.
     *
     * @return the ID
     */
    public OrderId id() {
        return id;
    }

    /**
     * The customer the order is for.
     *
     * @return the customer's ID
     */
    public CustomerId customer() {
        return customer;
    }

    /**
     * The order's delivery date.
     *
     * @return the date
     */
    public DeliveryDate date() {
        return date;
    }

    /**
     * The order's time window, on its date.
     *
     * @return the window
     */
    public TimeWindow window() {
        return window;
    }

    /**
     * The order's current attempt (FR-258).
     *
     * @return the attempt
     */
    public Attempt attempt() {
        return attempt;
    }

    /**
     * Whether the order is cancelled, which is shown beside an outcome too (BR-33).
     *
     * @return {@code true} once cancelled
     */
    public boolean isCancelled() {
        return state instanceof State.Cancelled;
    }

    /**
     * The order's outside reference.
     *
     * @return the reference, or empty
     */
    public Optional<ExternalReference> reference() {
        return Optional.ofNullable(reference);
    }

    /**
     * When the order was last placed, changed, moved, cancelled or put back (FR-397, BR-82).
     *
     * @return the time
     */
    public Instant lastChangedAt() {
        return lastChangedAt;
    }

    /**
     * Everything the order holds, for the persistence adapter and the read side.
     *
     * @return the snapshot, at the loaded version
     */
    public OrderSnapshot snapshot() {
        return new OrderSnapshot(
                id,
                customer,
                date,
                window,
                weight,
                volume,
                serviceTime,
                priority,
                leaveAtDoor,
                Optional.ofNullable(reference),
                recipient,
                Optional.ofNullable(note),
                access,
                attempt,
                state,
                anonymized,
                lastChangedAt,
                version());
    }

    /**
     * A form that hides the order, because it holds personal data.
     *
     * @return {@code Order[redacted]}
     */
    @Override
    public String toString() {
        return "Order[redacted]";
    }
}
