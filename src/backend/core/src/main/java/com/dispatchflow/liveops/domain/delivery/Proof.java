package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StoredStateInvalid;
import java.util.Objects;
import java.util.Optional;

/**
 * A proof of delivery: a signature or a photo, the uploaded file it names, and an optional note
 * (FR-30, FR-367). The file and the note are removed 90 days after the report arrived, and the kind
 * stays, so the record still shows what proof there was (NFR-184, ADR-107). The note is personal
 * data, so {@link #toString()} hides it.
 *
 * @param kind a signature or a photo
 * @param file the uploaded file, until retention removes it
 * @param note the driver's note, until retention removes it
 */
public record Proof(ProofKind kind, Optional<ProofId> file, Optional<ProofNote> note) implements PersonalData {

    /**
     * Checks the parts.
     *
     * @throws StoredStateInvalid when a note outlives its file, which {@link #erased()} never leaves
     */
    public Proof {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(note, "note");
        if (note.isPresent() && file.isEmpty()) {
            throw new StoredStateInvalid(Proof.class, "a proof's note is removed with its file");
        }
    }

    /**
     * A proof the phone just uploaded.
     *
     * @param kind a signature or a photo
     * @param file the uploaded file
     * @param note the driver's note, if any
     * @return the proof
     */
    public static Proof of(ProofKind kind, ProofId file, Optional<ProofNote> note) {
        return new Proof(kind, Optional.of(Objects.requireNonNull(file, "file")), note);
    }

    /**
     * This proof once retention has removed its file and note (NFR-184).
     *
     * @return the proof with its kind only
     */
    public Proof erased() {
        return new Proof(kind, Optional.empty(), Optional.empty());
    }

    /**
     * Whether retention has removed the file.
     *
     * @return {@code true} when only the kind is left
     */
    public boolean isErased() {
        return file.isEmpty();
    }

    /**
     * A form that names the kind and the file and hides the note, because it is personal data.
     *
     * @return the proof without its note
     */
    @Override
    public String toString() {
        return "Proof[kind=" + kind + ", file=" + file + ", note=" + (note.isPresent() ? "redacted" : "none") + "]";
    }
}
