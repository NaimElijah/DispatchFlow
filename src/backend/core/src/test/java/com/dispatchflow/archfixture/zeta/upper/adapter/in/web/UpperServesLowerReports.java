package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import com.dispatchflow.archfixture.zeta.shared.clientapi.api.LowerReportsApi;

/** Rule 22: upper's controller implements another module's interface, its only implementation. */
public final class UpperServesLowerReports implements LowerReportsApi {

    @Override
    public String report(String day) {
        return day;
    }
}
