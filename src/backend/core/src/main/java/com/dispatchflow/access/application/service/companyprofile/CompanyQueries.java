package com.dispatchflow.access.application.service.companyprofile;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.AccessViews;
import com.dispatchflow.access.application.port.out.CompanyView;
import java.util.Optional;

/**
 * The company settings page's query (FR-125, FR-184).
 *
 * <p>A Driver whose other roles allow no more reads only the company's name (the permission matrix,
 * "Read the company's settings"); how the view leaves out the rest is decided in M2.
 *
 * <p>Built in M2.
 */
public final class CompanyQueries {

    private final AccessViews views;

    /**
     * Creates the query class.
     *
     * @param views the read models Access's screens read
     */
    public CompanyQueries(AccessViews views) {
        this.views = views;
    }

    /**
     * The company's details. Before an Admin first saves the settings there is no profile, and the
     * answer is empty, so the settings page opens with no values for the Admin to fill in.
     *
     * <p>Built in M2.
     *
     * @param actor the signed-in user acting
     * @return the details, or empty while no profile is set up
     */
    public Optional<CompanyView> details(Actor actor) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
