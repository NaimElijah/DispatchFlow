package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import com.dispatchflow.archfixture.zeta.web.ZetaWebEndpoint;

/** Rule 2: a module's controller uses a class of web, on which nothing may depend (ADR-140). */
public record ControllerReachesTheWeb(ZetaWebEndpoint endpoint) {}
