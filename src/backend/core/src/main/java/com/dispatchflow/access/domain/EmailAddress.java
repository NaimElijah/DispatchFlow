package com.dispatchflow.access.domain;

import com.dispatchflow.access.domain.AccessError.InvalidEmail;
import com.dispatchflow.shared.RuleViolation;
import java.net.IDN;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * An email address in the canonical form one account is keyed by (BR-45, DL §3.3): trimmed, with
 * ASCII letters folded to lower case and nothing else folded, and the domain in its ASCII
 * (Punycode) form. So {@code " Dana@Example.COM"} and {@code "dana@example.com"} are one address.
 *
 * <p>The local part is a dot-atom (RFC 5322 §3.2.3), with non-ASCII letters allowed as well; a
 * quoted local part is refused. The domain has at least two labels, and holds none of the four
 * characters IDNA2003 and IDNA2008 treat differently, so the JDK's IDNA2003 conversion can't map
 * one address onto another's mailbox.
 *
 * <p>It is a class rather than a record so the only way in is {@link #parse}, which builds the
 * canonical form. An address is personal data: {@link #toString()} hides it, and a refusal names
 * no address, so neither can carry it into a log (NFR-94).
 */
public final class EmailAddress {

    private static final char AT = '@';
    // RFC 5321 §4.5.3.1.1 and §4.5.3.1.3: the longest local part, and the longest path less its
    // angle brackets.
    private static final int MAX_LOCAL_PART = 64;
    private static final int MAX_ADDRESS = 254;
    private static final String DOT = ".";
    private static final int MIN_LABELS = 2;
    private static final int ASCII_LIMIT = 0x80;
    private static final String ATEXT_SYMBOLS = "!#$%&'*+-/=?^_`{|}~";
    // ß, final sigma, zero-width non-joiner and zero-width joiner (UTS #46 "deviation" characters).
    private static final Set<Integer> IDNA_DEVIATIONS = Set.of(0x00DF, 0x03C2, 0x200C, 0x200D);
    private static final String LABEL_SEPARATOR = "\\.";

    private final String value;

    private EmailAddress(String value) {
        this.value = value;
    }

    /**
     * Reads an address as typed (BR-45).
     *
     * @param text the address, with any surrounding spaces
     * @return the address in canonical form
     * @throws RuleViolation with {@link InvalidEmail} when the text has no single {@code @} with
     *     text on both sides; the local part is over 64 characters or not a dot-atom; the domain
     *     is not a valid host name of two or more labels, or holds an IDNA deviation character; or
     *     the address is over 254 characters in canonical form, its domain in Punycode
     */
    public static EmailAddress parse(String text) {
        String trimmed = Objects.requireNonNull(text, "text").strip();
        int at = trimmed.indexOf(AT);
        if (at <= 0 || at != trimmed.lastIndexOf(AT) || at == trimmed.length() - 1) {
            throw invalid();
        }
        String localPart = trimmed.substring(0, at);
        if (localPart.length() > MAX_LOCAL_PART || !isDotAtom(localPart)) {
            throw invalid();
        }
        String domain = trimmed.substring(at + 1);
        if (domain.codePoints().anyMatch(IDNA_DEVIATIONS::contains)) {
            throw invalid();
        }
        String canonical = foldAscii(localPart) + AT + asciiDomain(domain);
        if (canonical.length() > MAX_ADDRESS) {
            throw invalid();
        }
        return new EmailAddress(canonical);
    }

    private static String asciiDomain(String domain) {
        String ascii;
        try {
            ascii = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException notAHostName) {
            // The cause is dropped on purpose: its message may quote the domain, which is personal
            // data.
            throw invalid();
        }
        // IDN accepts a trailing dot, which would make a second spelling of the same domain.
        String[] labels = ascii.split(LABEL_SEPARATOR, -1);
        if (labels.length < MIN_LABELS) {
            throw invalid();
        }
        for (String label : labels) {
            if (label.isEmpty()) {
                throw invalid();
            }
        }
        return foldAscii(ascii);
    }

    private static boolean isDotAtom(String localPart) {
        if (localPart.startsWith(DOT) || localPart.endsWith(DOT) || localPart.contains(DOT + DOT)) {
            return false;
        }
        return localPart.codePoints().allMatch(point -> point == '.' || isAtext(point));
    }

    /** RFC 5322 atext, widened to non-ASCII letters for internationalized local parts (RFC 6531). */
    private static boolean isAtext(int point) {
        if (point >= ASCII_LIMIT) {
            return Character.isLetter(point);
        }
        return (point >= 'a' && point <= 'z')
                || (point >= 'A' && point <= 'Z')
                || (point >= '0' && point <= '9')
                || ATEXT_SYMBOLS.indexOf(point) >= 0;
    }

    /** BR-45 folds ASCII letters only, so a non-ASCII letter keeps its case. */
    private static String foldAscii(String text) {
        StringBuilder folded = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            folded.append(current >= 'A' && current <= 'Z' ? (char) (current + ('a' - 'A')) : current);
        }
        return folded.toString();
    }

    private static RuleViolation invalid() {
        return new RuleViolation(new InvalidEmail());
    }

    /**
     * The canonical form, which the account is keyed by and every link and notice goes to
     * (BR-45, BR-125).
     *
     * @return the address, such as {@code dana@example.com}
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof EmailAddress that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * A form that hides the address, because an address is personal data.
     *
     * @return {@code EmailAddress[redacted]}
     */
    @Override
    public String toString() {
        return "EmailAddress[redacted]";
    }
}
