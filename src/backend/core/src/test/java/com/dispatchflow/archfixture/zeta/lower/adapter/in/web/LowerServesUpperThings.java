package com.dispatchflow.archfixture.zeta.lower.adapter.in.web;

import com.dispatchflow.archfixture.zeta.shared.clientapi.api.UpperThingsApi;
import com.dispatchflow.archfixture.zeta.shared.clientapi.model.ThingDto;

/** Rule 22: lower's controller implements upper's interface, beside upper's own controller. */
public final class LowerServesUpperThings implements UpperThingsApi {

    @Override
    public ThingDto thing(String name) {
        return new ThingDto(name);
    }
}
