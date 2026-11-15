package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.archfixture.alpha.events.AlphaEventKind;
import com.dispatchflow.archfixture.shared.KernelValue;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.net.IDN;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * A clean domain type: the JDK (with java.net.IDN, and case, formats and date patterns in a fixed
 * locale), JSpecify, the kernel, its own events and libphonenumber.
 */
public record AlphaValue(@Nullable KernelValue kernel, AlphaEventKind kind, List<Instant> times) {

    static PhoneNumberUtil phoneNumbers() {
        return PhoneNumberUtil.getInstance();
    }

    static String domainOf(String host) {
        return IDN.toASCII(host);
    }

    static String upperCase(String text) {
        return text.toUpperCase(Locale.ROOT);
    }

    static String lowerCase(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    static String format(double kilometres) {
        return String.format(Locale.ROOT, "%.1f km", kilometres);
    }

    static DateTimeFormatter dayAndMonth() {
        return DateTimeFormatter.ofPattern("d MMM", Locale.ROOT);
    }
}
