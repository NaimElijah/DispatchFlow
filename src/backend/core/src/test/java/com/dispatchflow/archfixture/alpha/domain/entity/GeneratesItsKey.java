package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Rule: no key is generated; IdGenerator gives every ID. */
@Entity
@Table(schema = "alpha")
public class GeneratesItsKey {

    @Id
    @GeneratedValue
    private UUID id = new UUID(0, 10);

    protected GeneratesItsKey() {}
}
