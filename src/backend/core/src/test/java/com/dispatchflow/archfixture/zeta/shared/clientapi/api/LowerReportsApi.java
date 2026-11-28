package com.dispatchflow.archfixture.zeta.shared.clientapi.api;

/** Rule 22: a generated interface of lower's tag, implemented once, by upper's controller. */
public interface LowerReportsApi {

    /** An operation of the tag. */
    String report(String day);
}
