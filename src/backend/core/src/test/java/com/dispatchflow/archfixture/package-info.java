/**
 * Deliberate breaches of the domain purity rules, and a few clean controls, that the rules in
 * {@code com.dispatchflow.architecture} are evaluated against. It mirrors the production layout
 * one level down ({@code archfixture.<module>.domain}, {@code archfixture.shared}), so a rule
 * built for the root {@code com.dispatchflow.archfixture} sees it as a codebase of its own. These
 * classes live in the test tree, so the production import never includes them.
 */
package com.dispatchflow.archfixture;
