package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.domain.proof.ProofNote;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link ProofNote} as its text. A stored text that is blank or not in the kernel's normal
 * form is refused by the note itself with {@code StoredStateInvalid}.
 */
@Converter(autoApply = true)
public final class ProofNoteConverter extends StoredValueConverter<ProofNote, String> {

    /** Creates the converter, which Hibernate does once. */
    public ProofNoteConverter() {
        super(ProofNote.class, ProofNote::text, ProofNote::new);
    }
}
