package com.dispatchflow.liveops.application.port.out;

import java.util.Objects;
import java.util.UUID;

/**
 * A phone request's {@code Idempotency-Key}: the ID of the outbox row that sends it, made when the
 * driver acts (ADR-087). Scoped to the signed-in user, it makes a retried report or position batch
 * take effect once (BR-28, NFR-65, NFR-285).
 *
 * <p>Built in M8.
 *
 * @param value the UUID the phone sent
 */
public record IdempotencyKey(UUID value) {

    /** Checks the UUID is present. */
    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
    }
}
