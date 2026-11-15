package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.shared.PhoneNumber;

/**
 * Hashes a phone with a secret key, which is all an opt-out keeps of the phone (NFR-202, NFR-03, TS
 * §6.4). The adapter hashes the E.164 form, so two spellings of one number hash the same (FR-371,
 * FR-377); the secret comes from the environment and never reaches a log.
 *
 * <p>The keys rotate by version (ADR-069). New hashes use the current key, and an older key stays,
 * for lookups only, while any opt-out remains under it, so a rotation never loses an opt-out
 * (NFR-202). {@code RehashOptOuts} copies the opt-outs whose phone is still stored to the new key.
 *
 * <p>Built in M12.
 */
public interface PhoneHasher {

    /**
     * The phone's keyed hashes under every key version still in use.
     *
     * <p>Built in M12.
     *
     * @param phone the phone
     * @return its hash under the current key and under each older key still in use, each the same
     *     for every spelling of the number
     */
    PhoneHashes of(PhoneNumber phone);
}
