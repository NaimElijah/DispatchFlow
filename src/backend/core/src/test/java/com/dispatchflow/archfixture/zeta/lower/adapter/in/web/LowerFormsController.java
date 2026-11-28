package com.dispatchflow.archfixture.zeta.lower.adapter.in.web;

import com.dispatchflow.archfixture.zeta.shared.clientapi.api.LowerFormsApi;

/** Rule 22: lower's controller is the one implementation of its own module's interface. */
public final class LowerFormsController implements LowerFormsApi {

    @Override
    public String form(String field) {
        return field;
    }
}
