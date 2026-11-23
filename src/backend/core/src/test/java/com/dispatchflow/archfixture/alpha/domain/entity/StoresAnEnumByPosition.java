package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Rule: an enum with no @Enumerated is stored by its position. */
@Entity
@Table(schema = "alpha")
public class StoresAnEnumByPosition {

    @Id
    private UUID id = new UUID(0, 6);

    private EntityKind kind = EntityKind.FIRST;

    protected StoresAnEnumByPosition() {}
}
