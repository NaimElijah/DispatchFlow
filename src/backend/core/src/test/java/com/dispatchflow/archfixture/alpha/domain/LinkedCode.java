package com.dispatchflow.archfixture.alpha.domain;

/** A value that refers to its own type, so a rule that looks inside field types must stop. */
public record LinkedCode(RuleCode code, LinkedCode next) {}
