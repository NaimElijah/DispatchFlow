package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.PersonalData;
import java.util.Objects;
import java.util.Optional;

/**
 * What an opt-out word does (FR-363): the entry to append, and whether the one confirmation is due.
 *
 * @param entry the opt-out to append; empty when the phone had already opted out
 * @param confirmationDue whether the phone gets the one confirmation
 */
public record ReplyOptOut(Optional<OptOutRecord> entry, boolean confirmationDue) implements PersonalData {

    /** Checks the entry is given. */
    public ReplyOptOut {
        Objects.requireNonNull(entry, "entry");
    }

    /**
     * A form that hides the phone.
     *
     * @return the result without the entry's phone
     */
    @Override
    public String toString() {
        return "ReplyOptOut[entry=" + entry.map(OptOutRecord::toString).orElse("none") + ", confirmationDue="
                + confirmationDue + "]";
    }
}
