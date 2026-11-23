package com.dispatchflow.archfixture.alpha.adapter.out.persistence;

import com.dispatchflow.archfixture.alpha.domain.entity.MappedRoot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Rule: an adapter's interface never extends JpaRepository. {@code @NoRepositoryBean} keeps Spring Data from building it in the integration tests.
 */
@NoRepositoryBean
public interface WideRepository extends JpaRepository<MappedRoot, UUID> {}
