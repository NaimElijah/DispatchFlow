package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.outcome.FailureReason;
import com.dispatchflow.liveops.domain.outcome.FailureReasonKind;
import com.dispatchflow.liveops.domain.outcome.Outcome;
import com.dispatchflow.liveops.domain.proof.Proof;
import com.dispatchflow.liveops.domain.proof.ProofId;
import com.dispatchflow.liveops.domain.proof.ProofKind;
import com.dispatchflow.liveops.domain.proof.ProofNote;
import com.dispatchflow.liveops.events.OutcomeKind;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The columns one {@link Outcome} is stored in, inside a stored outcome record or a stored held
 * report (ADR-127 decision 3). An outcome is sealed and its proof optional, so it is stored as its
 * kind plus the nullable columns each kind needs; {@link #outcome()} builds the public value every
 * caller sees. Only this package makes one.
 */
@Embeddable
class StoredOutcome {

    @Enumerated(EnumType.STRING)
    private OutcomeKind outcome;

    private @Nullable Boolean leftAtDoor;

    @Enumerated(EnumType.STRING)
    private @Nullable ProofKind proofKind;

    private @Nullable ProofId proofFile;

    private @Nullable ProofNote proofNote;

    @Enumerated(EnumType.STRING)
    private @Nullable FailureReasonKind failureReason;

    private @Nullable String failureText;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredOutcome() {}

    /**
     * Stores an outcome.
     *
     * @param stated the outcome
     */
    StoredOutcome(Outcome stated) {
        this.outcome = stated.kind();
        switch (stated) {
            case Outcome.Delivered delivered -> {
                this.leftAtDoor = delivered.leftAtDoor();
                delivered.proof().ifPresent(proof -> {
                    this.proofKind = proof.kind();
                    this.proofFile = proof.file().orElse(null);
                    this.proofNote = proof.note().orElse(null);
                });
            }
            case Outcome.Failed failed -> {
                this.failureReason = failed.reason().kind();
                this.failureText = failed.reason().text().orElse(null);
            }
        }
    }

    /**
     * The outcome these columns store.
     *
     * @return the outcome
     * @throws StoredStateInvalid when the stored reason or proof is refused by its own rules
     */
    Outcome outcome() {
        return switch (outcome) {
            case DELIVERED ->
                new Outcome.Delivered(
                        Optional.ofNullable(proofKind)
                                .map(kind -> new Proof(
                                        kind, Optional.ofNullable(proofFile), Optional.ofNullable(proofNote))),
                        Objects.requireNonNull(leftAtDoor, "a delivered outcome says whether it was left at the door"));
            case FAILED ->
                new Outcome.Failed(FailureReason.ofStored(
                        Objects.requireNonNull(failureReason, "a failed outcome has its reason"),
                        Optional.ofNullable(failureText)));
        };
    }
}
