/**
 * Recipient Messaging's outbound ports (DL §4.6, §13, TS §6.4): the repositories of {@code
 * StopMessages}, {@code RatingRequest} and {@code MessagingSettings}; the opt-out history and the
 * reply log, which are records (DL §3.2); the SMS provider behind {@link
 * com.dispatchflow.messaging.application.port.out.SmsGateway}; the keyed phone hash behind {@link
 * com.dispatchflow.messaging.application.port.out.PhoneHasher} (NFR-202); and the read port {@link
 * com.dispatchflow.messaging.application.port.out.MessagingViews} with the view records the screens
 * show. The adapters implement them.
 *
 * <p>A record here that holds a phone, a reply or a comment is personal data and prints redacted
 * (NFR-19, ADR-106).
 */
@NullMarked
package com.dispatchflow.messaging.application.port.out;

import org.jspecify.annotations.NullMarked;
