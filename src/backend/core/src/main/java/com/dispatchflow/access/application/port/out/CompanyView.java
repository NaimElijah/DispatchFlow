package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.companyprofile.CompanyName;
import com.dispatchflow.access.domain.companyprofile.DefaultServiceTime;
import com.dispatchflow.access.domain.companyprofile.DefaultWindow;
import com.dispatchflow.shared.Version;
import java.util.Objects;
import java.util.Optional;

/**
 * The company's details as the settings page shows them (FR-125, FR-184): its name, its default
 * language, and what a new customer starts with. The dispatch phone is Messaging's setting
 * (placement P7).
 *
 * <p>A view exists only once the profile does. Before an Admin first saves the settings there is no
 * view, so there is no version to send: that first save sends {@code If-None-Match: *}, which the
 * web adapter passes to {@code ChangeCompanyProfile} as an empty expected version, "no profile
 * yet".
 * Every later save sends {@code If-Match} with this view's version.
 *
 * <p>To Be Implemented.
 *
 * @param name the company's name (FR-382)
 * @param language the language a new user starts with (FR-182)
 * @param defaultWindow the time window a new customer starts with (FR-125)
 * @param defaultServiceTime the service duration a new customer starts with; empty until an Admin
 *     sets one (FR-184)
 * @param version the profile's version, which every save after the first sends back as {@code
 *     If-Match} (BR-76)
 */
public record CompanyView(
        CompanyName name,
        Language language,
        DefaultWindow defaultWindow,
        Optional<DefaultServiceTime> defaultServiceTime,
        Version version) {

    /** Checks every part is present, possibly empty. */
    public CompanyView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(defaultWindow, "defaultWindow");
        Objects.requireNonNull(defaultServiceTime, "defaultServiceTime");
        Objects.requireNonNull(version, "version");
    }
}
