package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.delivery.Delivery;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where each stop's {@link Delivery} is kept, keyed by its stop (DL §3.6, §4.5). A stop with no
 * delivery is pending; its first fact creates one (DL §6.2). Adding or saving a delivery publishes
 * the events it raised, in the caller's transaction (ADR-064).
 *
 * <p>Built in M5.
 */
public interface DeliveryRepository {

    /**
     * One stop's delivery.
     *
     * <p>Built in M5.
     *
     * @param stop the stop
     * @return the delivery, or empty when nothing is recorded for the stop
     */
    Optional<Delivery> get(StopId stop);

    /**
     * Stores a stop's first delivery, in the caller's transaction.
     *
     * <p>Built in M5.
     *
     * @param delivery the delivery, at {@code Version.NEW}
     * @throws StaleVersion when another writer stored the stop's delivery first; the caller retries
     *     the command as a change (DL §11)
     */
    void add(Delivery delivery);

    /**
     * Stores a changed delivery, checking the version it was loaded at (BR-76, ADR-057).
     *
     * <p>Built in M5.
     *
     * @param delivery the delivery
     * @throws StaleVersion when another writer saved it since it was loaded
     */
    void save(Delivery delivery);

    /**
     * The deliveries of several stops in one read, for projections, figures and the answers Live
     * Operations gives Planning and Customers &amp; Orders.
     *
     * <p>Built in M5.
     *
     * @param stops the stops, each once
     * @return the stops' deliveries, each once; a pending stop has none, so it is left out
     */
    List<Delivery> ofStops(List<StopId> stops);

    /**
     * The delivery of one attempt of an order, which Customers &amp; Orders' {@code StopProgress}
     * asks about (BR-92, FR-258).
     *
     * <p>Built in M5.
     *
     * @param order the order
     * @param attempt the attempt
     * @return the attempt's delivery, or empty when nothing is recorded for its stop
     */
    Optional<Delivery> ofAttempt(OrderId order, Attempt attempt);

    /**
     * The stops whose delivery holds personal data past its retention: a proof's file or note, or
     * the text of an <em>other</em> reason or a correction reason, recorded 90 days or more ago
     * (NFR-31, NFR-184, ADR-107).
     *
     * <p>Built in M8.
     *
     * @param now when the retention job runs
     * @return the stops, each once, for the job to erase one delivery at a time
     */
    List<StopId> withExpiredData(Instant now);
}
