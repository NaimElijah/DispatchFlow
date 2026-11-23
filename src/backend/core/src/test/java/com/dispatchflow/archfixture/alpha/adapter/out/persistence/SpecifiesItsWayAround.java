package com.dispatchflow.archfixture.alpha.adapter.out.persistence;

import com.dispatchflow.archfixture.alpha.domain.entity.MappedRoot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Rule: a JpaSpecificationExecutor brings bulk updates and deletes that pass by @Version, @PostLoad
 * and the events. {@code @NoRepositoryBean} keeps Spring Data from building it in the integration
 * tests.
 */
@NoRepositoryBean
public interface SpecifiesItsWayAround extends Repository<MappedRoot, UUID>, JpaSpecificationExecutor<MappedRoot> {}
