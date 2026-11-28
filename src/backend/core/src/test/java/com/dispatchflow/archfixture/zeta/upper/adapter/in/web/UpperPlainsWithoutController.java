package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import com.dispatchflow.archfixture.zeta.shared.clientapi.api.UpperPlainsApi;

/**
 * Rule 22: the one implementation of upper's interface sits at home but is no controller, so Spring
 * would map nothing and both of its operations would answer 404.
 */
public final class UpperPlainsWithoutController implements UpperPlainsApi {

    @Override
    public String plain(String field) {
        return field;
    }
}
