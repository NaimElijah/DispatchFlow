package com.dispatchflow.audit.domain;

import com.dispatchflow.audit.domain.AuditError.InvalidAppVersion;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;
import java.util.Optional;
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
        return parseIfValid(text).orElseThrow(() -> new RuleViolation(new InvalidAppVersion()));
    }

    /**
     * Reads the version a client declared, when it is one, so that an audit record can leave out a
     * version that isn't (ADR-110).
     *
     * @param text the declared version, such as {@code 1.4.2}
     * @return the version, or empty when the text is not three dot-separated ASCII numbers without
     *     leading zeros, each within an {@code int}
     * @throws NullPointerException when {@code text} is null
     */
    public static Optional<AppVersion> parseIfValid(String text) {
        Objects.requireNonNull(text, "text");
        Matcher matcher = VERSION.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long major = Long.parseLong(matcher.group(MAJOR));
        long minor = Long.parseLong(matcher.group(MINOR));
        long patch = Long.parseLong(matcher.group(PATCH));
        if (major > Integer.MAX_VALUE || minor > Integer.MAX_VALUE || patch > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        return Optional.of(new AppVersion((int) major, (int) minor, (int) patch));
    }

    /**
     * The version as a client writes it.
     *
     * @return the three numbers joined by dots, such as {@code 1.4.2}
     */
    public String text() {
        return major + "." + minor + "." + patch;
    }
}
