package com.dispatchflow.archfixture.beta.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** An entity of another module, which an alpha entity must not relate to. */
@Entity
@Table(schema = "beta")
public class BetaLine {

    @Id
    private UUID id = new UUID(0, 13);

    protected BetaLine() {}
}
