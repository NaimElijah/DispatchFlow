package com.dispatchflow.archfixture.alpha.domain.entity;

import com.dispatchflow.shared.AggregateRoot;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Rule: every entity root declares a @PostLoad check. */
@Entity
@Table(schema = "alpha")
public class RootWithoutALoadCheck extends AggregateRoot {

    @Id
    private UUID id = new UUID(0, 9);

    protected RootWithoutALoadCheck() {}
}
