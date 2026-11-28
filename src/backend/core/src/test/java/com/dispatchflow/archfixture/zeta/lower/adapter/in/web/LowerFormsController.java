package com.dispatchflow.archfixture.zeta.lower.adapter.in.web;

import com.dispatchflow.archfixture.zeta.shared.clientapi.api.LowerFormsApi;
import org.springframework.web.bind.annotation.RestController;

/** Rule 22: lower's controller is the one implementation of its own module's interface. * Its interface maps no path, so the contexts that scan the test tree serve nothing from it.
 */
@RestController
public final class LowerFormsController implements LowerFormsApi {

    @Override
    public String form(String field) {
        return field;
    }
}
