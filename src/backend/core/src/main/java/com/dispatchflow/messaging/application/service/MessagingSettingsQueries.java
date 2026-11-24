package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.GrantLimitExceeded;
import com.dispatchflow.messaging.application.port.out.DispatchPhoneView;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsView;
import com.dispatchflow.messaging.application.port.out.MessagingViews;
import org.springframework.transaction.annotation.Transactional;

/**
 * The messaging part of the company settings page, which an Admin or a Dispatcher reads (FR-125,
 * FR-350, FR-375, placement P7), and the dispatch phone a driver calls with one tap (FR-199).
 *
 * <p>The permission matrix's <em>Read the company's settings</em> row grants a Driver only the
 * company's name and dispatch phone. {@link #dispatchPhone} serves the dispatch phone only; the
 * company's name comes from Access's {@code CompanyQueries}. That grant is limited, not empty, so the
 * web adapter's role check lets a Driver through (ADR-068), and {@link #settings} refuses a Driver
 * itself with {@link GrantLimitExceeded}.
 *
 * <p>To Be Implemented. Rows: FR-199, FR-350, FR-375.
 */
@Transactional(readOnly = true)
public class MessagingSettingsQueries {

    private final MessagingViews views;

    /**
     * Creates the queries.
     *
     * @param views Messaging's read port
     */
    public MessagingSettingsQueries(MessagingViews views) {
        this.views = views;
    }

    /**
     * The messaging settings; before an Admin first set them, messages and the rating question are
     * off and there is no dispatch phone (FR-350, FR-375).
     *
     * <p>To Be Implemented.
     *
     * @param actor the Admin or Dispatcher
     * @return the settings, with the version a change sends back
     * @throws GrantLimitExceeded when the actor's roles give only the Driver's limited grant, which
     *     covers the company's name and dispatch phone and not these settings
     */
    public MessagingSettingsView settings(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * The dispatch phone alone.
     *
     * <p>To Be Implemented.
     *
     * @param actor any signed-in user, a driver included
     * @return the dispatch phone, if an Admin set one
     */
    public DispatchPhoneView dispatchPhone(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
