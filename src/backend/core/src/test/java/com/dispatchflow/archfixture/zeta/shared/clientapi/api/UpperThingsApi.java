package com.dispatchflow.archfixture.zeta.shared.clientapi.api;

import com.dispatchflow.archfixture.zeta.shared.clientapi.model.ThingDto;

/**
 * Rule 22: a generated interface of upper's tag, implemented by upper's controller and by lower's
 * too, so it has two implementations.
 */
public interface UpperThingsApi {

    /** An operation of the tag. */
    ThingDto thing(String name);
}
