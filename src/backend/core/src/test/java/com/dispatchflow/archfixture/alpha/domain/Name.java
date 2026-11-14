package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.PersonalData;

/** A personal value that the wrappers below hold in an array or behind a sealed interface. */
public record Name(String value) implements PersonalData {

    @Override
    public String toString() {
        return "Name[redacted]";
    }
}
