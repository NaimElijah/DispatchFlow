package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.application.api.OrderChanges;
import com.dispatchflow.orders.application.api.OrderFact;
import com.dispatchflow.orders.application.api.RecipientContactFact;
import com.dispatchflow.orders.application.api.StopOrderFact;
import com.dispatchflow.orders.domain.NormalizedAddress;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Customers &amp; Orders' read port (DL §3.6, CQRS-lite): what its screens and its published API
 * read, straight from its own schema, without loading aggregates. A repository never grows a query
 * for a screen; this port holds them instead. Every answer is read now.
 *
 * <p>Built in M2, M5, M7, M9 and M12, method by method.
 */
public interface OrdersViews {

    /**
     * Every uncancelled order of a date, with its customer's location, its outside-service-area
     * flag and defaults applied (FR-15, FR-320, BR-22, BR-49, BR-124). Each order's {@code placedAt}
     * has no column to read yet: M2 adds the time the order joined its date, set by {@code place},
     * {@code reschedule} and a put-back to another date (BR-71).
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @return the orders, each once; empty for a date with none
     */
    List<OrderFact> uncancelledOn(DeliveryDate date);

    /**
     * What changed among a date's orders since an instant, from each order's last change time
     * (BR-25, BR-82, ADR-056).
     *
     * <p>Built in M9.
     *
     * @param date the date
     * @param since the instant the changes count from, excluded
     * @return the cancelled and the changed orders
     */
    OrderChanges changesSince(DeliveryDate date, Instant since);

    /**
     * Orders' door flags and the fields a driver sees at their stops, in one read (FR-86, FR-87,
     * FR-135, FR-364).
     *
     * <p>Built in M5.
     *
     * @param orders the orders, each once
     * @return one answer per order found, in any order; an ID that names no order has none
     */
    List<StopOrderFact> stopOrders(List<OrderId> orders);

    /**
     * Orders' recipient phones and their customers' contact phones, in one read (FR-348).
     *
     * <p>Built in M12.
     *
     * @param orders the orders, each once
     * @return one answer per order found, in any order, each with both phones where they exist; an
     *     ID that names no order has none
     */
    List<RecipientContactFact> recipientContacts(List<OrderId> orders);

    /**
     * The customers whose name, phone or address contains a text, retired ones included and
     * anonymized ones never (FR-134, FR-259).
     *
     * <p>Built in M2.
     *
     * @param text what the dispatcher typed, matched against the name, the phone in any form the
     *     dispatcher types it, and the normalized address (FR-398)
     * @param limit the most customers to answer, 1 or more
     * @return the first {@code limit} customers found, each once, ordered by name
     */
    List<CustomerSummary> customersMatching(String text, int limit);

    /**
     * One customer, with its orders from today on.
     *
     * <p>Built in M2.
     *
     * @param customer the customer
     * @param today today in {@code Asia/Jerusalem}, from which its orders are listed
     * @return the customer, or empty when none has the ID
     */
    Optional<CustomerView> customer(CustomerId customer, DeliveryDate today);

    /**
     * The active customers likely to duplicate one being saved: the same phone, or a matching
     * normalized address (FR-212).
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param phone the phone being saved
     * @param address the normalized address being saved
     * @param except the customer being changed, which is not its own duplicate; empty for a new one
     * @return the likely duplicates, each once, ordered by name
     */
    List<CustomerSummary> likelyDuplicatesOf(PhoneNumber phone, NormalizedAddress address, Optional<CustomerId> except);

    /**
     * Every active customer whose address carries a flag (FR-63, FR-149).
     *
     * <p>Built in M7.
     *
     * @return the flagged customers, each once, ordered by name
     */
    List<FlaggedCustomer> flaggedCustomers();

    /**
     * The geocoding outbox's backlog on one Israel day: how many requests wait, and whether the
     * day's tries reached {@value GeocodeRequests#DAILY_LOOKUPS} (NFR-135, FR-64).
     *
     * <p>Built in M7.
     *
     * @param today today in {@code Asia/Jerusalem}, whose tries the cap counts
     * @return the backlog
     */
    GeocodingBacklog geocodingBacklog(LocalDate today);

    /**
     * Every order of a date, cancelled ones included, without their statuses, which the query
     * derives (FR-76, DL §9.6).
     *
     * <p>Built in M2.
     *
     * @param date the date
     * @return the orders, each once, ordered by window
     */
    List<OrderRow> ordersOn(DeliveryDate date);

    /**
     * Everything one order holds, without its status, which the query derives (DL §9.6).
     *
     * <p>Built in M2.
     *
     * @param order the order
     * @return the order, or empty when none has the ID
     */
    Optional<OrderDetail> order(OrderId order);

    /**
     * An import's preview, while it is still previewed (FR-203, ADR-086).
     *
     * <p>Built in M2.
     *
     * @param batch the import
     * @return the preview, or empty when no import has the ID or it is no longer previewed, so its
     *     rows are gone (NFR-31)
     */
    Optional<ImportPreviewView> importPreview(ImportBatchId batch);
}
