package com.dispatchflow.messaging.application.service;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;

/**
 * The server step a key rotation runs: every opt-out whose phone is still stored is re-hashed under
 * the new key (ADR-069, NFR-202). For each such phone, {@link PhoneHasher#of} gives its hash under
 * every key version still in use, and a phone whose history has entries under older keys and none
 * under the current one gets its latest entry appended again under the current key, one phone per
 * transaction, with the audit record. A phone with an entry under the current key is left alone, so
 * an entry made since the rotation is never overtaken by an older one, and a second run re-hashes
 * nothing: the runbook may run the step again after a failure. An opt-out whose phone is no longer
 * stored stays under its older key, which is kept for lookups only: the accepted risk is the key's
 * continued existence, never a lost opt-out (ADR-069).
 *
 * <p>Three parts don't exist yet, and M12 adds them:
 *
 * <ul>
 *   <li>a read of every phone still stored, in Customers &amp; Orders' {@code api} and in
 *       Messaging's own repositories, which {@link RehashOptOutsChange}'s constructor then takes;
 *   <li>an {@code OptOutRecord} factory that copies an entry under a new hash, keeping its change,
 *       time and dispatcher (a domain gap);
 *   <li>the key version {@code PhoneHash} records (a domain gap).
 * </ul>
 *
 * <p>It opens no transaction of its own: it re-hashes the phones one by one, and {@link
 * RehashOptOutsChange} holds each phone's transaction (ADR-129).
 *
 * <p>Built in M12. Rows: NFR-202.
 */
public final class RehashOptOuts {

    private final RehashOptOutsChange change;

    /**
     * Creates the step.
     *
     * @param change the transaction of each phone's re-hash
     */
    public RehashOptOuts(RehashOptOutsChange change) {
        this.change = change;
    }

    /**
     * Re-hashes the opt-outs whose phone is still stored under the current key.
     *
     * <p>Built in M12.
     *
     * @param call the step's server context
     * @return how many phones' opt-outs it re-hashed
     */
    public int rehash(CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
