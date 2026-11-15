package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.companyprofile.CompanyProfile;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * Where the company's one profile is kept (DL §3.6, §4.1). It has no ID, because there is one.
 *
 * <p>Built in M2.
 */
public interface CompanyProfileRepository {

    /**
     * The company's profile.
     *
     * <p>Built in M2.
     *
     * @return the profile, or empty before it is created
     */
    Optional<CompanyProfile> get();

    /**
     * Stores the profile when the system is deployed. Whether the first Admin's setup creates it is
     * decided in M6.
     *
     * <p>Built in M6.
     *
     * @param profile the new profile, at {@code Version.NEW}
     */
    void add(CompanyProfile profile);

    /**
     * Stores the changed profile with a version check (DL §3.6, BR-76).
     *
     * <p>Built in M2.
     *
     * @param profile the changed profile
     * @throws StaleVersion when the stored profile is no longer at the version it was loaded at
     */
    void save(CompanyProfile profile);
}
