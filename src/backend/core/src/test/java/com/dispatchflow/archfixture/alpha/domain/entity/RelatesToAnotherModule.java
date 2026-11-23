package com.dispatchflow.archfixture.alpha.domain.entity;

import com.dispatchflow.archfixture.beta.domain.BetaLine;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Rule: an entity refers to another module's classes only by their IDs. */
@Entity
@Table(schema = "alpha")
public class RelatesToAnotherModule {

    @Id
    private UUID id = new UUID(0, 4);

    @OneToMany
    private List<BetaLine> lines = new ArrayList<>();

    protected RelatesToAnotherModule() {}
}
