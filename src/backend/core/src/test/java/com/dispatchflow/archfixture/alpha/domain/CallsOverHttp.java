package com.dispatchflow.archfixture.alpha.domain;

import java.net.http.HttpClient;

/** Rule 4: java.net.http. */
public final class CallsOverHttp {

    static HttpClient client() {
        return HttpClient.newHttpClient();
    }
}
