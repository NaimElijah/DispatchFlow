/**
 * Recipient Messaging's use cases (DL §4.6, §6.2, §13, TS §6.2), one class each: the Admin's
 * settings, a dispatcher's opt-out record or removal, the provider's reply callback, the listeners
 * that trigger messages and raise the low-rating alert, the minute and five-second jobs that project
 * arrivals and send, the retention job, the restore step that turns messages off, the key
 * rotation's re-hash step, and the screens' queries. The {@code StopMessages} aggregate's triggers,
 * projection job and sender sit in their own package, {@code message}, and the opt-out list's use
 * cases in {@code optout} (ADR-125); this package holds the rest.
 *
 * <p>Every trigger builds the {@code SendingConditions} it is judged by from Access's {@code
 * Company} (the name and language), the {@code MessagingSettings}, and the phone's {@code
 * OptOutStatus}, and the {@code RecipientPhone} from Customers &amp; Orders' {@code
 * RecipientContacts} through {@code RecipientPhone.choose} (FR-348, FR-349). Settings no Admin has
 * set yet mean messages are off, so a trigger adds nothing (FR-350); so does a company with no
 * profile yet, which gives no sender name.
 */
@NullMarked
package com.dispatchflow.messaging.application.service;

import org.jspecify.annotations.NullMarked;
