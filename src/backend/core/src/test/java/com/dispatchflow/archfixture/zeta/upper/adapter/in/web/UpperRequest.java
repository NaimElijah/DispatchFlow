package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

/** A request the web adapter of the module upper reads, which no other adapter may use. */
public record UpperRequest(String body) {}
