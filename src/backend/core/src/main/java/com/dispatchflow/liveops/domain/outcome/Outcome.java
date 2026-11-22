package com.dispatchflow.liveops.domain.outcome;

import com.dispatchflow.liveops.domain.proof.Proof;
import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;
import java.util.Optional;

/**
 * A stop's outcome: delivered, with its proof and whether it was left at the door, or failed with a
 * reason (DL §3.3, FR-29). A proof's note and a reason's text are personal data, so every variant
 * prints redacted.
 */
public sealed interface Outcome extends PersonalData {

    /**
     * Delivered or failed.
     *
     * @return the kind
     */
    OutcomeKind kind();

    /**
     * Whether another outcome says the same thing, so a report repeating it is refused without
     * telling the dispatcher (BR-88, ADR-107): the same kind, and for a failure the same reason.
     * An *other* reason's text, a proof and *left at the door* are not compared.
     *
     * @param other the outcome to compare with
     * @return {@code true} when the two say the same thing
     */
    boolean isSameAs(Outcome other);

    /**
     * Whether another outcome states exactly this one, so a correction to it would change nothing
     * (FR-333, ADR-114): the same kind, the same *left at the door*, and for a failure the same
     * reason with the same text. A proof is not compared, because a dispatcher's outcome never
     * carries one (FR-367). A reason whose text retention erased states nothing exact any more, so
     * it matches no reason: two corrected texts that are both erased later still differ at rest.
     *
     * @param other the outcome to compare with
     * @return {@code true} when the two state exactly the same outcome
     */
    boolean statesExactly(Outcome other);

    /**
     * Delivered (FR-29, FR-366).
     *
     * @param proof the proof; a driver's report needs one (FR-367), a dispatcher's record none
     * @param leftAtDoor whether the goods were left at the door (FR-366)
     */
    record Delivered(Optional<Proof> proof, boolean leftAtDoor) implements Outcome {

        /** Checks the proof is present, possibly empty. */
        public Delivered {
            Objects.requireNonNull(proof, "proof");
        }

        /**
         * Delivered with a proof, as a driver reports it.
         *
         * @param proof the proof
         * @param leftAtDoor whether the goods were left at the door
         * @return the outcome
         */
        public static Delivered withProof(Proof proof, boolean leftAtDoor) {
            return new Delivered(Optional.of(Objects.requireNonNull(proof, "proof")), leftAtDoor);
        }

        /**
         * Delivered with no proof, as a dispatcher records or corrects it (FR-367).
         *
         * @param leftAtDoor whether the goods were left at the door (FR-368)
         * @return the outcome
         */
        public static Delivered withoutProof(boolean leftAtDoor) {
            return new Delivered(Optional.empty(), leftAtDoor);
        }

        @Override
        public OutcomeKind kind() {
            return OutcomeKind.DELIVERED;
        }

        @Override
        public boolean isSameAs(Outcome other) {
            return other instanceof Delivered;
        }

        @Override
        public boolean statesExactly(Outcome other) {
            return other instanceof Delivered delivered && delivered.leftAtDoor == leftAtDoor;
        }

        /**
         * A form that leaves out the proof's note, because it is personal data.
         *
         * @return the outcome, redacted
         */
        @Override
        public String toString() {
            return "Delivered[proof=" + proof + ", leftAtDoor=" + leftAtDoor + "]";
        }
    }

    /**
     * Failed, with its reason (FR-242, BR-109).
     *
     * @param reason the reason
     */
    record Failed(FailureReason reason) implements Outcome {

        /** Checks the reason is present. */
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public OutcomeKind kind() {
            return OutcomeKind.FAILED;
        }

        @Override
        public boolean isSameAs(Outcome other) {
            return other instanceof Failed(FailureReason otherReason) && otherReason.kind() == reason.kind();
        }

        @Override
        public boolean statesExactly(Outcome other) {
            return other instanceof Failed(FailureReason otherReason)
                    && !reason.isErased()
                    && !otherReason.isErased()
                    && otherReason.equals(reason);
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
