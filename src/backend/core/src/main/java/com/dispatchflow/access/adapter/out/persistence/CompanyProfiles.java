package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.companyprofile.CompanyProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaCompanyProfileRepository}. */
interface CompanyProfiles extends Repository<CompanyProfile, UUID> {

    /**
     * Saves the profile.
     *
     * @param profile the profile
     * @return the managed profile
     */
    CompanyProfile save(CompanyProfile profile);

    /**
     * Loads the profile.
     *
     * @param key {@link CompanyProfile#KEY}
     * @return the profile, or empty before it was first stored
     */
    Optional<CompanyProfile> findById(UUID key);
}
