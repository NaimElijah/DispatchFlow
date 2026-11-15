package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.TimeWindow;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * The refusals of the Customers &amp; Orders context (DL §4.3). Their fields become the response's
 * parameters (ADR-043), so none carries an address, a name, a phone, a note, a location or an
 * order's reference text: a row number, a date or an ID says which thing was refused.
 */
public sealed interface OrdersError extends DomainError {

    /**
     * An order placed, rescheduled or put back on a date before today (BR-116). Other modules
     * declare the same error with the same meaning.
     *
     * @param date the refused date
     */
    record DateInPast(DeliveryDate date) implements OrdersError {}

    /**
     * An order's time window that lies on another date than the order's (BR-35).
     *
     * @param window the refused window
     * @param date the order's date
     */
    record WindowNotOnDate(TimeWindow window, DeliveryDate date) implements OrdersError {}

    /**
     * A service time of zero or fewer seconds (BR-35, BR-124).
     *
     * @param seconds the refused duration
     */
    record NonPositiveServiceTime(long seconds) implements OrdersError {}

    /** An order given no service time whose customer has no default one either (BR-124). */
    record NoServiceTime() implements OrdersError {}

    /**
     * An order whose reference another uncancelled order on its date already carries (FR-211). The
     * reference itself is not named; the row number or the form says which one it is.
     *
     * @param date the date the reference is taken on
     */
    record DuplicateOrderReference(DeliveryDate date) implements OrdersError {}

    /**
     * A date that already holds its limit of uncancelled orders (BR-101, BR-113, NFR-05). The
     * message names the limit.
     *
     * @param date the full date
     * @param limit the most uncancelled orders a date holds
     */
    record DateFull(DeliveryDate date, int limit) implements OrdersError {}

    /**
     * A change, reschedule or cancellation of an order whose current stop is marked arrived or has
     * an outcome (BR-92).
     *
     * @param order the refused order
     */
    record StopAlreadyHasFact(OrderId order) implements OrdersError {}

    /**
     * A command on an order that is cancelled, which stays cancelled (DL §4.3).
     *
     * @param order the cancelled order
     */
    record OrderAlreadyCancelled(OrderId order) implements OrdersError {}

    /**
     * A put-back of an order that is neither failed nor dropped (FR-75, BR-94, BR-95).
     *
     * @param status the order's derived status
     */
    record OnlyFailedOrDroppedOrdersArePutBack(OrderStatus status) implements OrdersError {}

    /**
     * A reschedule onto the date the order is already on: moving an order means another date
     * (FR-315, DL §17).
     *
     * @param date the order's current date
     */
    record AlreadyOnThatDate(DeliveryDate date) implements OrdersError {}

    /**
     * A command on an order whose personal fields were removed after retention (NFR-257).
     *
     * @param order the anonymized order
     */
    record OrderAnonymized(OrderId order) implements OrdersError {}

    /** An address with no text but whitespace (FR-08, FR-310). */
    record BlankAddress() implements OrdersError {}

    /** A customer's or a recipient's name with no text but whitespace (FR-59, FR-87). */
    record BlankContactName() implements OrdersError {}

    /**
     * A customer's default window whose start is not before its end (FR-08, BR-35).
     *
     * @param start the refused opening time
     * @param end the refused closing time
     */
    record InvalidDailyWindow(LocalTime start, LocalTime end) implements OrdersError {}

    /**
     * A normalized address that several active customers share, so it can't name one (FR-312).
     *
     * @param customers the matching customers, ordered by ID
     */
    record AmbiguousAddress(List<CustomerId> customers) implements OrdersError {

        /** Copies the customers in a fixed order, so the response lists them the same way. */
        public AmbiguousAddress {
            customers = customers.stream()
                    .sorted(Comparator.comparing(CustomerId::value))
                    .toList();
        }
    }

    /** A hand-placed location outside the company's service area (FR-320). */
    record OutsideServiceArea() implements OrdersError {}

    /**
     * Retiring a customer that a plan not yet completed uses (BR-43).
     *
     * @param customer the refused customer
     * @param plans the plans that use it, ordered by ID
     */
    record CustomerInUse(CustomerId customer, List<PlanId> plans) implements OrdersError {

        /** Copies the plans in a fixed order. */
        public CustomerInUse {
            plans = plans.stream().sorted(Comparator.comparing(PlanId::value)).toList();
        }
    }

    /**
     * A change to a retired customer, or retiring it again (BR-43, FR-56).
     *
     * @param customer the retired customer
     */
    record CustomerRetired(CustomerId customer) implements OrdersError {}

    /**
     * A command on a customer whose personal data was removed (BR-98, NFR-257).
     *
     * @param customer the anonymized customer
     */
    record CustomerAnonymized(CustomerId customer) implements OrdersError {}

    /**
     * Anonymizing a customer or an order before its retention period is over (NFR-257, ADR-075).
     * Other modules declare the same error with the same meaning.
     */
    record RetentionNotOver() implements OrdersError {}

    /** An import row that matches no customer and lacks the name or the phone to create one (FR-311). */
    record NewCustomerNeedsNameAndPhone() implements OrdersError {}

    /**
     * The summary of an import refused because rows have errors (FR-203, FR-204). Each row's error
     * follows it as a {@link RowRefused}.
     *
     * @param rejectedRows how many rows have at least one error
     */
    record ImportHasErrors(int rejectedRows) implements OrdersError {}

    /**
     * One refusal of one import row, under an {@link ImportHasErrors} (FR-203). A row with several
     * errors has one of these for each.
     *
     * @param row the row's number in the file
     * @param reason why the row is refused: a field's refusal as the reader found it, such as the
     *     kernel's {@code QuantityOutOfRange}, or a rule of this context
     */
    record RowRefused(RowNumber row, DomainError reason) implements OrdersError {}

    /** Confirming an import read as Windows-1255 without the dispatcher's explicit confirmation (FR-313). */
    record EncodingNotConfirmed() implements OrdersError {}

    /** Confirming, discarding or expiring an import that is already confirmed (DL §9.4). */
    record ImportAlreadyConfirmed() implements OrdersError {}

    /** Confirming, discarding or expiring an import the dispatcher discarded (DL §9.4). */
    record ImportDiscarded() implements OrdersError {}

    /** Confirming, discarding or expiring an import whose hour has passed (FR-399, ADR-086). */
    record ImportExpired() implements OrdersError {}

    /** Expiring an import preview before its hour has passed (FR-399, ADR-086). */
    record ImportNotYetExpired() implements OrdersError {}
}
