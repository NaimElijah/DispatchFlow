package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.domain.delivery.Delivery;
import com.dispatchflow.liveops.domain.delivery.RetentionPeriods;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps each stop's {@link Delivery} in {@code liveops.delivery}, its outcome records in {@code
 * liveops.delivery_outcome} and its held reports in {@code liveops.held_report}.
 *
 * <p>{@link #withExpiredData} binds the cutoffs {@link RetentionPeriods} computes, the ones {@code
 * Delivery.eraseExpired} compares with, so the job loads exactly the deliveries that erasure changes.
 */
@Component
@Transactional
class JpaDeliveryRepository implements DeliveryRepository {

    private final Deliveries deliveries;
    private final VersionedSave versionedSave;

    /**
     * Creates the adapter.
     *
     * @param deliveries the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     */
    JpaDeliveryRepository(Deliveries deliveries, VersionedSave versionedSave) {
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
    }

    @Override
    public Optional<Delivery> get(StopId stop) {
        return load(stop.value());
    }

    /**
     * {@inheritDoc}
     *
     * <p>It goes through the shared save, which turns a first save that loses the race for the
     * stop's key into {@code StaleVersion}.
     */
    @Override
    public void add(Delivery delivery) {
        versionedSave.save(delivery, deliveries::save);
    }

    @Override
    public void save(Delivery delivery) {
        versionedSave.save(delivery, deliveries::save);
    }

    @Override
    public List<Delivery> ofStops(List<StopId> stops) {
        if (stops.isEmpty()) {
            return List.of();
        }
        List<UUID> keys = stops.stream().map(StopId::value).toList();
        List<Delivery> found = deliveries.findByIdIn(keys);
        // Fills the held reports of the instances just loaded; the list it answers is the same.
        deliveries.withHeldReportsIn(keys);
        return found;
    }

    /**
     * {@inheritDoc}
     *
     * @throws org.springframework.dao.IncorrectResultSizeDataAccessException when two stops of the
     *     attempt both have a delivery, which the port leaves undecided
     */
    @Override
    public Optional<Delivery> ofAttempt(OrderId order, Attempt attempt) {
        return deliveries.stopOf(order, attempt).flatMap(this::load);
    }

    @Override
    public List<StopId> withExpiredData(Instant now) {
        return deliveries
                .withPersonalDataRecordedBy(RetentionPeriods.proofCutoff(now), RetentionPeriods.reasonCutoff(now))
                .stream()
                .map(StopId::of)
                .toList();
    }

    /** Loads a delivery whole: its outcome records with it, then its held reports into it. */
    private Optional<Delivery> load(UUID stop) {
        Optional<Delivery> delivery = deliveries.findById(stop);
        delivery.ifPresent(found -> deliveries.withHeldReports(stop));
        return delivery;
    }
}
