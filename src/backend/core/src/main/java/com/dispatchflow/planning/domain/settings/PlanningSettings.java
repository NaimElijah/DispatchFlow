package com.dispatchflow.planning.domain.settings;

import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The company's planning settings, which Admins change (FR-183, FR-21, FR-67, FR-227, FR-321, DL
 * §4.4). There is one, with no ID. Each value checks itself, so a change can only be refused by its
 * value; each change is audited by its use case (FR-186). No change raises an event.
 *
 * <p>It is mapped for JPA (ADR-126). Having no ID, it is stored under the constant {@link #KEY}
 * (ADR-127 decision 5), in one row: the travel buckets in seven columns, each other value in one,
 * and the sealed fixed vehicle cost as a kind beside the cost an enabled one has.
 */
@Entity
@Table(schema = "planning", name = "planning_settings")
public class PlanningSettings extends AggregateRoot {

    /**
     * The one stored row's key. The settings have no ID, but every root keys on a UUID (ADR-126
     * decision 10), so they key on this constant, which the table allows as its only key.
     */
    public static final UUID KEY = UUID.fromString("416e88e3-1d2b-4859-bc69-3a80ed927c46");

    /** Which {@link FixedVehicleCost} the settings hold. */
    private enum FixedCostKind {
        OFF,
        ON
    }

    @Id
    private UUID id;

    @Embedded
    @AttributeOverride(name = "morningPeak.start", column = @Column(name = "morning_peak_start"))
    @AttributeOverride(name = "morningPeak.end", column = @Column(name = "morning_peak_end"))
    @AttributeOverride(name = "morningPeak.factor", column = @Column(name = "morning_peak_factor"))
    @AttributeOverride(name = "eveningPeak.start", column = @Column(name = "evening_peak_start"))
    @AttributeOverride(name = "eveningPeak.end", column = @Column(name = "evening_peak_end"))
    @AttributeOverride(name = "eveningPeak.factor", column = @Column(name = "evening_peak_factor"))
    @AttributeOverride(name = "offPeak", column = @Column(name = "off_peak_factor"))
    private TravelBuckets travelBuckets;

    @Column(name = "buffer_percent")
    private Buffer buffer;

    private SolveLimit defaultSolveLimit;

    @Enumerated(EnumType.STRING)
    private FixedCostKind fixedVehicleCost;

    private @Nullable Duration fixedVehicleCostPerVehicle;

    @Column(name = "departure_allowance_minutes")
    private DepartureAllowance departureAllowance;

    /** For Hibernate, which sets the fields from the stored row and then runs {@link #checkStored}. */
    protected PlanningSettings() {}

    private PlanningSettings(
            TravelBuckets travelBuckets,
            Buffer buffer,
            SolveLimit defaultSolveLimit,
            FixedVehicleCost fixedVehicleCost,
            DepartureAllowance departureAllowance) {
        super();
        this.id = KEY;
        this.travelBuckets = travelBuckets;
        this.buffer = buffer;
        this.defaultSolveLimit = defaultSolveLimit;
        storeFixedVehicleCost(fixedVehicleCost);
        this.departureAllowance = departureAllowance;
    }

    /**
     * The settings before an Admin changes any: FR-321's buckets, a 10% buffer (FR-227), a 45-second
     * daily limit (FR-19), no fixed vehicle cost (FR-21), and no departure allowance (ADR-101).
     *
     * @return the new settings, never saved
     */
    public static PlanningSettings startingValues() {
        return new PlanningSettings(
                TravelBuckets.STARTING,
                Buffer.STARTING,
                SolveLimit.DEFAULT_DAILY,
                new FixedVehicleCost.Off(),
                DepartureAllowance.STARTING);
    }

    /**
     * Checks the settings as Hibernate loaded them (ADR-126 decision 8). Every other value checked
     * itself when its converter or instantiator rebuilt it, and no rule ties them together, so only
     * the fixed vehicle cost, which this class builds from its kind and its duration, is rebuilt here.
     *
     * @throws StoredStateInvalid when an enabled cost is zero or less
     */
    @PostLoad
    void checkStored() {
        StoredStateInvalid.whileRestoring(FixedVehicleCost.class, this::fixedVehicleCost);
    }

    /**
     * Changes the travel-time buckets (FR-321).
     *
     * @param buckets the new buckets
     */
    public void changeTravelBuckets(TravelBuckets buckets) {
        travelBuckets = Objects.requireNonNull(buckets, "buckets");
    }

    /**
     * Changes the travel-time buffer (FR-227).
     *
     * @param newBuffer the new buffer
     */
    public void changeBuffer(Buffer newBuffer) {
        buffer = Objects.requireNonNull(newBuffer, "newBuffer");
    }

    /**
     * Changes the default daily solve limit, which stays within 5 to 60 seconds (FR-183, NFR-06).
     *
     * @param limit the new default
     */
    public void changeDefaultSolveLimit(SolveLimit limit) {
        defaultSolveLimit = Objects.requireNonNull(limit, "limit");
    }

    /**
     * Turns the fixed vehicle cost on or off, or changes it (FR-21).
     *
     * @param cost the new cost
     */
    public void changeFixedVehicleCost(FixedVehicleCost cost) {
        storeFixedVehicleCost(Objects.requireNonNull(cost, "cost"));
    }

    /**
     * Changes the departure allowance (FR-67).
     *
     * @param allowance the new allowance
     */
    public void changeDepartureAllowance(DepartureAllowance allowance) {
        departureAllowance = Objects.requireNonNull(allowance, "allowance");
    }

    /**
     * The travel-time buckets (FR-321).
     *
     * @return the morning and evening peaks and the off-peak factor
     */
    public TravelBuckets travelBuckets() {
        return travelBuckets;
    }

    /**
     * The travel-time buffer (FR-227).
     *
     * @return the buffer
     */
    public Buffer buffer() {
        return buffer;
    }

    /**
     * The default daily solve limit (FR-19, FR-183).
     *
     * @return the limit
     */
    public SolveLimit defaultSolveLimit() {
        return defaultSolveLimit;
    }

    /**
     * The fixed cost per vehicle used (FR-21).
     *
     * @return off, or on with its cost
     */
    public FixedVehicleCost fixedVehicleCost() {
        return switch (fixedVehicleCost) {
            case OFF -> new FixedVehicleCost.Off();
            case ON ->
                new FixedVehicleCost.On(
                        Objects.requireNonNull(fixedVehicleCostPerVehicle, "an enabled cost has its duration"));
        };
    }

    /**
     * The minutes between a shift's start and leaving the depot (FR-67).
     *
     * @return the allowance
     */
    public DepartureAllowance departureAllowance() {
        return departureAllowance;
    }

    /** Sets the kind and the duration from a cost, which checked itself when it was built. */
    private void storeFixedVehicleCost(FixedVehicleCost cost) {
        switch (cost) {
            case FixedVehicleCost.Off off -> {
                fixedVehicleCost = FixedCostKind.OFF;
                fixedVehicleCostPerVehicle = null;
            }
            case FixedVehicleCost.On on -> {
                fixedVehicleCost = FixedCostKind.ON;
                fixedVehicleCostPerVehicle = on.cost();
            }
        }
    }
}
