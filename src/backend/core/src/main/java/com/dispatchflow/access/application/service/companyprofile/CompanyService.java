package com.dispatchflow.access.application.service.companyprofile;

import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.access.application.api.CompanyFacts;
import com.dispatchflow.access.application.port.out.CompanyProfileRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access's implementation of {@code Company}: the company's profile, which Customers &amp; Orders
 * reads a new customer's defaults from, and Messaging the name and language of its messages
 * (FR-125, FR-184, FR-349, FR-382, ADR-104).
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class CompanyService implements Company {

    private final CompanyProfileRepository companyProfiles;

    /**
     * Creates the service.
     *
     * @param companyProfiles where the company profile is kept
     */
    public CompanyService(CompanyProfileRepository companyProfiles) {
        this.companyProfiles = companyProfiles;
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public Optional<CompanyFacts> profile() {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
