package com.dispatchflow.reporting.domain;

import com.dispatchflow.shared.VehicleId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * How the baseline's nearest-neighbour solve treats the vehicles withdrawn mid-route, as FR-271
 * says: it leaves out a vehicle marked unavailable or left without a driver, and gives a vehicle
 * whose shift ran out its whole shift. The listener asks for that solve, and {@link Baseline}
 * names the withdrawals it covered (ADR-063, ADR-109).
 *
 * @param leftOut the vehicles the solve leaves out, in the order first withdrawn
 * @param wholeShift the vehicles the solve gives their whole shift, in the order first withdrawn
 */
public record WithdrawnFleet(List<VehicleId> leftOut, List<VehicleId> wholeShift) {

    /** Copies the lists. */
    public WithdrawnFleet {
        leftOut = List.copyOf(leftOut);
        wholeShift = List.copyOf(wholeShift);
    }

    /**
     * Splits withdrawals by FR-271. A vehicle named more than once counts once, with the cause it
     * was first named with, because a withdrawn route never returns (FR-268).
     *
     * @param withdrawals the withdrawals, in the order they happened
     * @return the vehicles to leave out and those to give their whole shift
     */
    public static WithdrawnFleet of(List<Withdrawal> withdrawals) {
        Objects.requireNonNull(withdrawals, "withdrawals");
        List<VehicleId> seen = new ArrayList<>();
        List<VehicleId> leftOut = new ArrayList<>();
        List<VehicleId> wholeShift = new ArrayList<>();
        for (Withdrawal withdrawal : withdrawals) {
            if (seen.contains(withdrawal.vehicle())) {
                continue;
            }
            seen.add(withdrawal.vehicle());
            switch (withdrawal.cause()) {
                case VEHICLE_UNAVAILABLE, NO_DRIVER -> leftOut.add(withdrawal.vehicle());
                case SHIFT_RAN_OUT -> wholeShift.add(withdrawal.vehicle());
            }
        }
        return new WithdrawnFleet(leftOut, wholeShift);
    }
}
