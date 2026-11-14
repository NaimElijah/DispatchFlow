/**
 * The Recipient Messaging context's domain (DL §4.6): the messages to one stop's recipient and the
 * rules on which go and how often, the rating requests that read the recipients' replies, the
 * opt-out history of each phone, and the company's messaging settings.
 *
 * <p>It is pure (DL §3.9): it sends no SMS and hashes no phone. The provider adapter reports what
 * happened to a send, and the hashing adapter hands it a {@link
 * com.dispatchflow.messaging.domain.PhoneHash}. The decisions no document settled are in ADR-108.
 */
@NullMarked
package com.dispatchflow.messaging.domain;

import org.jspecify.annotations.NullMarked;
