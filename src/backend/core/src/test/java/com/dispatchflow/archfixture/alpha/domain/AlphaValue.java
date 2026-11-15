package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.archfixture.alpha.events.AlphaEventKind;
import com.dispatchflow.archfixture.shared.KernelValue;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.net.IDN;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * A clean domain type: the JDK (with java.net.IDN, java.text's Normalizer, case, formats and date
 * patterns in a fixed locale, a sequential stream and a TimeUnit conversion), JSpecify, the
 * kernel, its own events and libphonenumber.
 */
public record AlphaValue(@Nullable KernelValue kernel, AlphaEventKind kind, List<Instant> times) {

    static PhoneNumberUtil phoneNumbers() {
        return PhoneNumberUtil.getInstance();
    }

    static String domainOf(String host) {
        return IDN.toASCII(host);
    }

    static String composed(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC);
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

    static String number(double kilometres) {
        return NumberFormat.getInstance(Locale.ROOT).format(kilometres);
    }

    static String decimal(double kilometres) {
        return new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(kilometres);
    }

    static String stops(int count) {
        return new MessageFormat("{0} stops", Locale.ROOT).format(new Object[] {count});
    }

    static DateTimeFormatter dayAndMonth() {
        return DateTimeFormatter.ofPattern("d MMM", Locale.ROOT);
    }

    static DateTimeFormatter builtDayAndMonth() {
        return new DateTimeFormatterBuilder().appendPattern("d MMM").toFormatter(Locale.ROOT);
    }

    static long blanks(List<String> texts) {
        return texts.stream().filter(String::isBlank).count();
    }

    static long millis(long seconds) {
        return TimeUnit.SECONDS.toMillis(seconds);
    }
}
