package com.dispatchflow.archfixture.alpha.adapter.out.persistence;

import com.dispatchflow.archfixture.alpha.domain.entity.MappedRoot;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Rule: an adapter's interface never extends ListCrudRepository. {@code @NoRepositoryBean} keeps Spring Data from building it in the integration tests.
 */
@NoRepositoryBean
public interface ListCrudRepositoryOfRoots extends ListCrudRepository<MappedRoot, UUID> {}
