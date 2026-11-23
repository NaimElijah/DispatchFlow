package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Rule: EnumType.ORDINAL stores an enum by its position. */
@Entity
@Table(schema = "alpha")
public class StoresAnEnumAsAnOrdinal {

    @Id
    private UUID id = new UUID(0, 7);

    @Enumerated(EnumType.ORDINAL)
    private EntityKind kind = EntityKind.FIRST;

    protected StoresAnEnumAsAnOrdinal() {}
}
