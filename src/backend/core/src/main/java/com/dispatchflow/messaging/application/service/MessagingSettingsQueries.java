package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.messaging.application.port.out.DispatchPhoneView;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsView;
import com.dispatchflow.messaging.application.port.out.MessagingViews;

/**
 * The messaging part of the company settings page, which an Admin reads (FR-125, FR-350, FR-375,
 * placement P7), and the dispatch phone a driver calls with one tap (FR-199), the one part a driver
 * may read (the permission matrix's <em>Read the company's settings</em> row).
 *
 * <p>Built in M12. Rows: FR-199, FR-350, FR-375.
 */
public final class MessagingSettingsQueries {

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
     * <p>Built in M12.
     *
     * @param actor the Admin
     * @return the settings, with the version a change sends back
     */
    public MessagingSettingsView settings(Actor actor) {
        throw new UnsupportedOperationException("Built in M12");
    }

    /**
     * The dispatch phone alone.
     *
     * <p>Built in M12.
     *
     * @param actor any signed-in user, a driver included
     * @return the dispatch phone, if an Admin set one
     */
    public DispatchPhoneView dispatchPhone(Actor actor) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
