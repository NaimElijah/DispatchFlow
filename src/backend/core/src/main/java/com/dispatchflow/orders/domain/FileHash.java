package com.dispatchflow.orders.domain;

import java.util.Objects;

/**
 * The hash of an uploaded import file's bytes, which the upload adapter computes and the batch keeps
 * with its rows (DL §4.3). No rule of this context reads it; it identifies the file a batch came
 * from.
 *
 * @param value the hash, as the adapter writes it
 */
public record FileHash(String value) {

    /** Checks the hash is given. */
    public FileHash {
        Objects.requireNonNull(value, "value");
    }
}
