package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.liveops.domain.outcome.FailureReason;
import com.dispatchflow.liveops.domain.proof.Proof;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.Objects;
import java.util.Optional;

/**
 * What a driver reports at a stop of a route: arrived, delivered or failed (FR-29, FR-242, BR-87).
 * The web adapter reads it from the request body and checks it against the contract; its time comes
 * beside it, as a {@link com.dispatchflow.liveops.application.service.DeviceClock}.
 *
 * <p>Built in M5.
 */
public sealed interface StopReport {

    /**
     * The route the report was sent for.
     *
     * <p>Built in M5.
     *
     * @return the route
     */
    RouteId route();

    /**
     * The reported stop.
     *
     * <p>Built in M5.
     *
     * @return the stop
     */
    StopId stop();

    /**
     * The driver arrived at the stop (FR-29).
     *
     * <p>Built in M5.
     *
     * @param route the route the report was sent for
     * @param stop the stop
     */
    record Arrived(RouteId route, StopId stop) implements StopReport {

        /** Checks both parts are present. */
        public Arrived {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(stop, "stop");
        }
    }

    /**
     * The stop was delivered (FR-29, FR-30, FR-366). Its proof names the file the phone uploaded
     * first under its outbox row's ID (ADR-087); it holds the driver's note, so it prints redacted.
     *
     * <p>Built in M5.
     *
     * @param route the route the report was sent for
     * @param stop the stop
     * @param proof the proof of delivery, if one was sent; {@code Delivery.deliver} refuses its
     *     absence (FR-367)
     * @param leftAtDoor whether the goods were left at the door (FR-366)
     */
    record Delivered(RouteId route, StopId stop, Optional<Proof> proof, boolean leftAtDoor)
            implements StopReport, PersonalData {

        /** Checks every part is present, possibly empty. */
        public Delivered {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(proof, "proof");
        }

        /**
         * A form that names the stop and hides the proof's note.
         *
         * @return the route and the stop, redacted
         */
        @Override
        public String toString() {
            return "Delivered[route=" + route + ", stop=" + stop + ", redacted]";
        }
    }

    /**
     * The stop failed, for one of BR-109's reasons (FR-242). The reason's text can name a person, so
     * it prints redacted.
     *
     * <p>Built in M5.
     *
     * @param route the route the report was sent for
     * @param stop the stop
     * @param reason why it failed
     */
    record Failed(RouteId route, StopId stop, FailureReason reason) implements StopReport, PersonalData {

        /** Checks every part is present. */
        public Failed {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(reason, "reason");
        }

        /**
         * A form that names the stop and hides the reason.
         *
         * @return the route and the stop, redacted
         */
        @Override
        public String toString() {
            return "Failed[route=" + route + ", stop=" + stop + ", redacted]";
        }
    }
}
