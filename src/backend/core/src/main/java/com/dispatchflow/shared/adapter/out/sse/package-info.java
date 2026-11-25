/**
 * The registry of open event streams (ADR-140): {@code web.stream} registers each stream it opens
 * here, and Planning's and Live Operations' {@code sse} adapters send their live updates through it
 * (#193). It sits beside the kernel, below {@code web}, so those adapters reach it without
 * depending on {@code web}.
 *
 * <p>It holds kernel types only, such as {@code AccountId} and {@code SessionId}, never a module's,
 * because rule 11 keeps it off the modules. So each module's {@code sse} adapter picks the
 * recipients of an event, through Access's {@code api}, before it sends (ADR-140).
 */
@NullMarked
package com.dispatchflow.shared.adapter.out.sse;

import org.jspecify.annotations.NullMarked;
