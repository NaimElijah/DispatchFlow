/**
 * The {@link com.dispatchflow.messaging.domain.message.StopMessages} aggregate (DL §4.6): every
 * message to one stop's recipient, with its outgoing messages, snapshot and invariants, each
 * message's type and lifecycle state, the daily send cap, the recipient's phone, and the route and
 * stop progress that its triggers read.
 */
@NullMarked
package com.dispatchflow.messaging.domain.message;

import org.jspecify.annotations.NullMarked;
