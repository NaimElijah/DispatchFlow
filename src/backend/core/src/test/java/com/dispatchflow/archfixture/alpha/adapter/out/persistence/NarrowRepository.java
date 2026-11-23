package com.dispatchflow.archfixture.alpha.adapter.out.persistence;

import com.dispatchflow.archfixture.alpha.domain.entity.MappedRoot;
import java.util.UUID;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Allowed: a plain Repository, which declares only what its port needs. {@code @NoRepositoryBean} keeps Spring Data from building it in the integration tests.
 */
@NoRepositoryBean
public interface NarrowRepository extends Repository<MappedRoot, UUID> {}
