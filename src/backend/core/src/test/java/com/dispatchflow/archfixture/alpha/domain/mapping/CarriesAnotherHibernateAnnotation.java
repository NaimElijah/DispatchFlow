package com.dispatchflow.archfixture.alpha.domain.mapping;

import org.hibernate.annotations.Formula;

/** Rule 1: of Hibernate, a domain may carry only @OptimisticLock and @Immutable (ADR-126). */
public class CarriesAnotherHibernateAnnotation {

    @Formula("1")
    private int computed;

    int computed() {
        return computed;
    }
}
