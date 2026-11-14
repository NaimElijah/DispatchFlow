package com.dispatchflow.audit.domain;

import com.dispatchflow.audit.domain.AuditError.InvalidAppVersion;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version a client declares in every call's {@code X-App-Version} (NFR-93, ADR-082): a
 * semantic version's three numbers (TS §5, Releases). It holds numbers only, so no text a client
 * sends reaches an audit record (NFR-94, ADR-110).
 *
 * @param major the major version, 0 or more
 * @param minor the minor version, 0 or more
 * @param patch the patch version, 0 or more
 */
public record AppVersion(int major, int minor, int patch) {

    // Ten digits at most, so the value fits a long and is then checked against int.
    private static final String PART = "(0|[1-9][0-9]{0,9})";
    private static final Pattern VERSION = Pattern.compile(PART + "[.]" + PART + "[.]" + PART);
    private static final int MAJOR = 1;
    private static final int MINOR = 2;
    private static final int PATCH = 3;

    /**
     * Checks the version.
     *
     * @throws RuleViolation with {@link InvalidAppVersion} when a part is negative
     */
    public AppVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new RuleViolation(new InvalidAppVersion());
        }
    }

    /**
     * Reads the version a client declared.
     *
     * @param text the declared version, such as {@code 1.4.2}
     * @return the version
     * @throws RuleViolation with {@link InvalidAppVersion} when the text is not three
     *     dot-separated ASCII numbers without leading zeros, each within an {@code int}; a
     *     pre-release or build suffix, a {@code v} prefix and surrounding whitespace are refused
     */
    public static AppVersion parse(String text) {
        Objects.requireNonNull(text, "text");
        Matcher matcher = VERSION.matcher(text);
        if (!matcher.matches()) {
            throw new RuleViolation(new InvalidAppVersion());
        }
        return new AppVersion(part(matcher, MAJOR), part(matcher, MINOR), part(matcher, PATCH));
    }

    /**
     * The version as a client writes it.
     *
     * @return the three numbers joined by dots, such as {@code 1.4.2}
     */
    public String text() {
        return major + "." + minor + "." + patch;
    }

    private static int part(Matcher matcher, int group) {
        long value = Long.parseLong(matcher.group(group));
        if (value > Integer.MAX_VALUE) {
            throw new RuleViolation(new InvalidAppVersion());
        }
        return (int) value;
    }
}
