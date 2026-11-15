package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.PhoneHash;
import com.dispatchflow.shared.PhoneNumber;

/**
 * Hashes a phone with a secret key, which is all an opt-out keeps of the phone (NFR-202, NFR-03, TS
 * §6.4). The adapter hashes the E.164 form, so two spellings of one number hash the same (FR-371,
 * FR-377); the secret comes from the environment and never reaches a log.
 *
 * <p>Built in M12.
 */
public interface PhoneHasher {

    /**
     * The phone's keyed hash.
     *
     * <p>Built in M12.
     *
     * @param phone the phone
     * @return its hash, the same for every spelling of the number
     */
    PhoneHash of(PhoneNumber phone);
}
