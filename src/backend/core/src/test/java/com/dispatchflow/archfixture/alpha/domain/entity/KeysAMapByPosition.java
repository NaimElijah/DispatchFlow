package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rule: a map keyed by an enum stores the key by position unless @MapKeyEnumerated says otherwise. */
@Entity
@Table(schema = "alpha")
public class KeysAMapByPosition {

    @Id
    private UUID id = new UUID(0, 20);

    @ElementCollection
    @CollectionTable(schema = "alpha")
    @Enumerated(EnumType.STRING)
    private Map<EntityKind, EntityKind> next = new HashMap<>();

    protected KeysAMapByPosition() {}
}
