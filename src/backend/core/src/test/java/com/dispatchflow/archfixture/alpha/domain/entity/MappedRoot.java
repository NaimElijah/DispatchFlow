package com.dispatchflow.archfixture.alpha.domain.entity;

import com.dispatchflow.shared.AggregateRoot;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Allowed: a root mapped as ADR-126 asks, with enums stored by name and its children in order. */
@Entity
@Table(schema = "alpha")
public class MappedRoot extends AggregateRoot {

    @Id
    private UUID id = new UUID(0, 1);

    @Enumerated(EnumType.STRING)
    private EntityKind kind = EntityKind.FIRST;

    @ElementCollection
    @CollectionTable(schema = "alpha")
    @Enumerated(EnumType.STRING)
    @OrderColumn
    private List<EntityKind> history = new ArrayList<>();

    @ElementCollection
    @CollectionTable(schema = "alpha")
    @MapKeyEnumerated(EnumType.STRING)
    private Map<EntityKind, String> labels = new HashMap<>();

    @OneToMany(mappedBy = "root", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    private List<MappedChild> children = new ArrayList<>();

    protected MappedRoot() {}

    @PostLoad
    void checkStored() {}
}
