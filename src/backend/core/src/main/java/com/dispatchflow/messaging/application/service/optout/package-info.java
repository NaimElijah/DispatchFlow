/**
 * The use cases of the opt-out list (DL §4.6, §17, FR-353): a dispatcher's record and removal of a
 * phone's opt-out, the screens' query of it, and the key rotation's step that re-hashes the
 * opt-outs whose phone is still stored (NFR-202). They sit beneath Recipient Messaging's use cases
 * (ADR-125).
 */
@NullMarked
package com.dispatchflow.messaging.application.service.optout;

import org.jspecify.annotations.NullMarked;
