package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import com.dispatchflow.archfixture.zeta.shared.clientapi.api.UpperThingsApi;
import com.dispatchflow.archfixture.zeta.shared.clientapi.model.ThingDto;

/** Rule 22: upper's controller implements its own module's interface and uses its model. */
public final class ThingsController implements UpperThingsApi {

    @Override
    public ThingDto thing(String name) {
        return new ThingDto(name);
    }
}
