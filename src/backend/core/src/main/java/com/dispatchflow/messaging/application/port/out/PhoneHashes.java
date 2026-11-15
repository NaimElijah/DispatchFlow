package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.PhoneHash;
import com.dispatchflow.shared.PersonalData;
import java.util.List;
import java.util.Objects;

/**
 * One phone's keyed hashes, one under each key version still in use (ADR-069, NFR-202). A new
 * opt-out entry is hashed under the current key; a phone's history is read under every key, because
 * an opt-out whose phone is no longer stored is never re-hashed and stays under an older key, which
 * is kept for lookups only. Each hash still singles out one person's phone, so it prints redacted.
 *
 * <p>{@code PhoneHash} records no key version yet, so the hashes can't say which key made each: M12
 * adds the version to {@code PhoneHash} (a domain gap).
 *
 * <p>Built in M12.
 *
 * @param current the hash under the current key, which a new entry is appended under
 * @param older the hashes under the older key versions still in use, the newest first; empty when
 *     no rotation left an opt-out under an older key
 */
public record PhoneHashes(PhoneHash current, List<PhoneHash> older) implements PersonalData {

    /** Checks the current hash is present, and copies the older ones. */
    public PhoneHashes {
        Objects.requireNonNull(current, "current");
        older = List.copyOf(older);
    }

    /**
     * A form that hides the hashes.
     *
     * @return the number of older hashes, redacted
     */
    @Override
    public String toString() {
        return "PhoneHashes[older=" + older.size() + ", redacted]";
    }
}
