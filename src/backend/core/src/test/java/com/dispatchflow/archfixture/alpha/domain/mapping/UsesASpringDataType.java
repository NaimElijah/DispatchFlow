package com.dispatchflow.archfixture.alpha.domain.mapping;

import org.springframework.data.domain.Pageable;

/** Rule 1: of Spring Data, a domain may carry only its two event annotations (ADR-126). */
public final class UsesASpringDataType {

    static int size(Pageable page) {
        return page.getPageSize();
    }
}
