package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.DispatchPhone;
import java.util.Objects;
import java.util.Optional;

/**
 * The company's dispatch phone, which a driver calls with one tap from either app (FR-199). It is
 * the one part of the messaging settings a driver may read (the permission matrix's <em>Read the
 * company's settings</em> row).
 *
 * <p>To Be Implemented.
 *
 * @param dispatchPhone the dispatch phone, or empty before an Admin set one
 */
public record DispatchPhoneView(Optional<DispatchPhone> dispatchPhone) {

    /** Checks the phone is present, possibly empty. */
    public DispatchPhoneView {
        Objects.requireNonNull(dispatchPhone, "dispatchPhone");
    }
}
