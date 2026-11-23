package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.RowErrors;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * Stores an import row's {@link RowErrors} as JSON text in one column (ADR-127 decision 7): a list
 * whose every element is {@code {"type": <binary class name>, "value": {<record components>}}}.
 * Jackson, which the web starter already brings, writes and reads each error record by its
 * components.
 *
 * <p>The stored type names the class to build, so reading admits only a record that implements
 * {@link DomainError} and lives under {@code com.dispatchflow}: a damaged or forged column can't
 * make it load or build any other class. It checks the package before it loads the class, and loads
 * it without initializing it. Anything else, and any text not in this form, is {@link
 * StoredStateInvalid}. An error its record now refuses is too, through {@code
 * StoredStateInvalid.whileRestoring}, which the base class runs.
 *
 * <p>Writing refuses with {@link AssertionError} an error the reader would refuse, so no row is
 * stored that can't be loaded again. It checks the type first, then reads its own text back: a
 * record of an admitted type can still hold a component Jackson can't rebuild, such as one typed by
 * an interface, which it writes as its fields with no type to read them back by.
 */
@Converter(autoApply = true)
public final class RowErrorsConverter extends StoredValueConverter<RowErrors, String> {

    private static final String TYPE = "type";
    private static final String VALUE = "value";
    private static final String ADMITTED_PACKAGE = "com.dispatchflow.";
    private static final String TYPE_REFUSED = "a stored error names a type that is not a DomainError record";
    private static final String NOT_WELL_FORMED = "a stored error list is not well formed";

    // Strict, so a stored error with a missing, extra or null component is damaged, not defaulted.
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    /** Creates the converter, which Hibernate does once. */
    public RowErrorsConverter() {
        super(RowErrors.class, RowErrorsConverter::write, RowErrorsConverter::read);
    }

    private static String write(RowErrors errors) {
        ArrayNode stored = JSON.createArrayNode();
        for (DomainError error : errors.errors()) {
            // Every error a preview keeps is a module's or the kernel's record, so another type here
            // is the caller's bug, never a refusal.
            if (admitted(error.getClass().getName()).isEmpty()) {
                throw new AssertionError(
                        "an import row's error must be a DomainError record under com.dispatchflow, so it can be read back");
            }
            stored.addObject().put(TYPE, error.getClass().getName()).set(VALUE, JSON.valueToTree(error));
        }
        String text = JSON.writeValueAsString(stored);
        if (!readsBackAs(text, errors)) {
            throw new AssertionError("an import row's errors must read back as they were written");
        }
        return text;
    }

    private static boolean readsBackAs(String text, RowErrors written) {
        try {
            return read(text).equals(written);
        } catch (StoredStateInvalid | RuleViolation unreadable) {
            // The reader's refusal is the answer: this text could never be loaded again.
            return false;
        }
    }

    private static RowErrors read(String stored) {
        JsonNode list = parse(stored);
        if (!list.isArray()) {
            throw new StoredStateInvalid(RowErrors.class, NOT_WELL_FORMED);
        }
        List<DomainError> errors = new ArrayList<>();
        for (JsonNode element : list) {
            errors.add(readError(element));
        }
        return new RowErrors(errors);
    }

    private static DomainError readError(JsonNode element) {
        JsonNode type = element.get(TYPE);
        JsonNode value = element.get(VALUE);
        if (!element.isObject() || element.size() != 2 || type == null || !type.isString() || value == null) {
            throw new StoredStateInvalid(RowErrors.class, NOT_WELL_FORMED);
        }
        Class<? extends DomainError> errorType =
                admitted(type.stringValue()).orElseThrow(() -> new StoredStateInvalid(RowErrors.class, TYPE_REFUSED));
        try {
            return JSON.treeToValue(value, errorType);
        } catch (JacksonException unreadable) {
            // A record's own rule refused the stored components: rethrown for whileRestoring, which
            // names the rule. Jackson's message quotes the stored text, so no other cause is kept.
            throw ruleViolationIn(unreadable)
                    .orElseThrow(() -> new StoredStateInvalid(RowErrors.class, NOT_WELL_FORMED));
        }
    }

    private static JsonNode parse(String stored) {
        try {
            return JSON.readTree(stored);
        } catch (JacksonException malformed) {
            // Jackson's message quotes the stored text, which a log must not carry, so it is dropped.
            throw new StoredStateInvalid(RowErrors.class, NOT_WELL_FORMED);
        }
    }

    /**
     * The type validator: the class a stored name names, only when it is a record that implements
     * {@link DomainError} and lives under {@code com.dispatchflow}. The package is checked before
     * the class is loaded, and the class is loaded without running its initializers.
     */
    private static Optional<Class<? extends DomainError>> admitted(String name) {
        if (!name.startsWith(ADMITTED_PACKAGE)) {
            return Optional.empty();
        }
        Class<?> loaded;
        try {
            loaded = Class.forName(name, false, RowErrorsConverter.class.getClassLoader());
        } catch (ClassNotFoundException unknown) {
            // No such type: the stored name is damaged, and the caller refuses it as such.
            return Optional.empty();
        }
        if (!loaded.isRecord() || !DomainError.class.isAssignableFrom(loaded)) {
            return Optional.empty();
        }
        return Optional.of(loaded.asSubclass(DomainError.class));
    }

    private static Optional<RuleViolation> ruleViolationIn(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof RuleViolation refused) {
                return Optional.of(refused);
            }
        }
        return Optional.empty();
    }
}
