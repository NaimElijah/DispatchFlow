package com.dispatchflow.archfixture.alpha.domain.io.process;

import java.util.prefs.Preferences;

/** Rule 4: java.util.prefs reads and writes the user's or the system's stored preferences. */
public final class UsesPreferences {

    static String region() {
        return Preferences.userRoot().get("region", "IL");
    }
}
