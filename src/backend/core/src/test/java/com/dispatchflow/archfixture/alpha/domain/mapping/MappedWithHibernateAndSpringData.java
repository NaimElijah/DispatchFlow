package com.dispatchflow.archfixture.alpha.domain.mapping;

import java.util.List;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OptimisticLock;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

/** Allowed: the two Hibernate and two Spring Data annotations ADR-126 lists. */
@Immutable
public class MappedWithHibernateAndSpringData {

    @OptimisticLock(excluded = true)
    private int contacts;

    @DomainEvents
    List<Object> events() {
        return List.of(contacts);
    }

    @AfterDomainEventPublication
    void clear() {
        contacts = 0;
    }
}
