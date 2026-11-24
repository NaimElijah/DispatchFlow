/**
 * Access's startup trigger (ADR-133): when the active profiles are exactly {@code dev} or exactly
 * {@code test}, it calls the use case that stores an account for each entry on the shared list of
 * seeded accounts. The trigger holds no transaction and no port.
 */
@NullMarked
package com.dispatchflow.access.adapter.in.job;

import org.jspecify.annotations.NullMarked;
