package com.dispatchflow.orders;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Makes Orders' use cases beans, TS §2.1's configuration layer (ADR-140 decision 10). A use case
 * and its {@code Change} join this {@code @Import} list in the pull request that builds its body,
 * so a throwing body is never wired (ADR-119 decision 4). {@code @Import} lets Spring call each
 * constructor, so none is written twice: no {@code @Bean} method and no scan. It stays
 * package-private and lists only this module's {@code application.service} classes ({@code
 * ApplicationRules} rule 15).
 */
@Configuration(proxyBeanMethods = false)
@Import({})
class OrdersConfiguration {}
