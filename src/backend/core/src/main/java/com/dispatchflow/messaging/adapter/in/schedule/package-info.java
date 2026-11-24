/**
 * Recipient Messaging's timed triggers (ADR-130): {@code ProjectArrivals} every minute, the sender
 * {@code SendDueMessages} every 5 seconds, and the nightly retention step {@code
 * EraseExpiredMessages}.
 */
@NullMarked
package com.dispatchflow.messaging.adapter.in.schedule;

import org.jspecify.annotations.NullMarked;
