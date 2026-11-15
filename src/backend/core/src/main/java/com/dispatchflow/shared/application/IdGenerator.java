package com.dispatchflow.shared.application;

import com.dispatchflow.shared.FreshIds;
import java.util.UUID;

/**
 * New IDs, made before anything is stored (TS §2.1 rule 3, TS §6.4): the domain never makes one
 * (DL §3.9 rule 3), so a factory takes its new ID, and a method that creates many children takes a
 * {@link FreshIds}. The adapter makes UUID version 7, which the kernel's typed IDs carry.
 *
 * <p>Built in M0.
 */
public interface IdGenerator {

    /**
     * One new ID, for the caller to wrap in its typed ID.
     *
     * <p>Built in M0.
     *
     * @return a UUID no other record has
     */
    UUID next();

    /**
     * A supply of new IDs for a method that creates several children, such as a solve result's
     * routes and stops (DL §3.9 rule 3).
     *
     * <p>Built in M0.
     *
     * @param count how many IDs the supply holds, 0 or more
     * @return a supply of {@code count} new UUIDs
     */
    FreshIds fresh(int count);
}
