package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Rule: an element collection of enums is stored by name too. */
@Entity
@Table(schema = "alpha")
public class StoresEnumsByPositionInAList {

    @Id
    private UUID id = new UUID(0, 8);

    @ElementCollection
    @CollectionTable(schema = "alpha")
    @OrderColumn
    private List<EntityKind> kinds = new ArrayList<>();

    protected StoresEnumsByPositionInAList() {}
}
