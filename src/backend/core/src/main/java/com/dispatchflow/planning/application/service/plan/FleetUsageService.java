package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.fleet.application.spi.PlanUseFact;
import com.dispatchflow.fleet.application.spi.RecordUsage;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.VehicleId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planning's implementation of Fleet's {@link RecordUsage}, which Fleet asks before it retires a
 * depot, a vehicle or a driver (FR-305, DL §2.1's dashed arrow, placement P9). It reads the plans
 * dated today or later through {@link PlanningViews}; Fleet's {@code PlanUses} then decides that only
 * an uncancelled one blocks.
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class FleetUsageService implements RecordUsage {

    private final PlanningViews views;

    /**
     * Creates the service.
     *
     * @param views Planning's read side
     */
    public FleetUsageService(PlanningViews views) {
        this.views = views;
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public List<PlanUseFact> usesOfVehicle(VehicleId vehicle, LocalDate today) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public List<PlanUseFact> usesOfDepot(DepotId depot, LocalDate today) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public List<PlanUseFact> usesOfDriver(AccountId driver, LocalDate today) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
