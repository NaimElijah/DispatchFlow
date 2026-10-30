package com.dispatchflow.example.adapter.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Example: Spring Data's access to the {@code example_widget} table. Only {@link WidgetPersistenceAdapter} uses it. */
interface WidgetJpaRepository extends JpaRepository<WidgetEntity, UUID> {}
