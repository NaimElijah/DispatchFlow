package com.dispatchflow.liveops.domain.outcome;

import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * An outcome a dispatcher records on a driver's behalf or corrects to (FR-73, FR-333). It can't
 * carry a proof: only a driver's report has one (FR-367), and a proof's retention runs from its
 * report's receipt (NFR-184). A failure's reason may hold text, so every variant prints redacted.
 */
public sealed interface DispatcherOutcome extends PersonalData {

    /**
     * The outcome the stop's records hold.
     *
     * @return the outcome, with no proof
     */
    Outcome toOutcome();

    /**
     * Delivered, perhaps left at the door, which a dispatcher records when the driver has no camera
     * (FR-368).
     *
     * @param leftAtDoor whether the goods were left at the door (FR-366)
     */
    record Delivered(boolean leftAtDoor) implements DispatcherOutcome {

        @Override
        public Outcome toOutcome() {
            return Outcome.Delivered.withoutProof(leftAtDoor);
        }

        /**
         * A form with nothing personal to hide; declared because the type is marked.
         *
         * @return the outcome
         */
        @Override
        public String toString() {
            return "Delivered[leftAtDoor=" + leftAtDoor + "]";
        }
    }

    /**
     * Failed, with its reason (FR-73, BR-109).
     *
     * @param reason the reason
     */
    record Failed(FailureReason reason) implements DispatcherOutcome {

        /** Checks the reason is present. */
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public Outcome toOutcome() {
            return new Outcome.Failed(reason);
        }

        /**
         * A form that hides the reason's text, because it is personal data.
         *
         * @return the outcome, redacted
         */
        @Override
        public String toString() {
            return "Failed[reason=" + reason + "]";
        }
    }
}
