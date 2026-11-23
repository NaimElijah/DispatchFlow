package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.delivery.history.ArrivalRecord;
import com.dispatchflow.liveops.domain.delivery.history.HeldReport;
import com.dispatchflow.liveops.domain.delivery.history.OutcomeRecord;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import java.util.List;
import java.util.Optional;

/**
 * What a test sees of a {@link Delivery}, read through its getters: a value that compares by every
 * part. Tests compare a delivery before and after a refused command, or after a load with what was
 * added, through it. It leaves the version out, which tests check on their own.
 *
 * @param stop the stop, which keys the delivery
 * @param order the stop's order
 * @param attempt the stop's attempt
 * @param arrival the driver's arrival, if any
 * @param outcomes the original outcome first, then each correction
 * @param heldReports every held report, in the order held
 */
public record DeliveryDescription(
        StopId stop,
        OrderId order,
        Attempt attempt,
        Optional<ArrivalRecord> arrival,
        List<OutcomeRecord> outcomes,
        List<HeldReport> heldReports) {

    /**
     * Describes a delivery.
     *
     * @param delivery the delivery
     * @return what its getters give
     */
    public static DeliveryDescription of(Delivery delivery) {
        return new DeliveryDescription(
                delivery.stop(),
                delivery.order(),
                delivery.attempt(),
                delivery.arrival(),
                delivery.outcomes(),
                delivery.heldReports());
    }
}
