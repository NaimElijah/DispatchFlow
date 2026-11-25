package com.dispatchflow.shared.adapter.in.web;

import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.PersonalData;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Turns an error record's components into a problem's {@code params} (ADR-043): IDs, numbers, enum
 * names, ISO-8601 dates and times, lists of these, and a nested refusal as {@code {code, params}}.
 * The shape rules keep text, objects, arrays and personal values out of an error's fields, and
 * {@link #isMappable} mirrors every rule below, so a test proves each production error maps before
 * one is ever answered.
 *
 * <p>Components are read by reflection: every production error is a record nested in a public
 * sealed interface, so its accessors are public.
 */
final class ErrorParams {

    private static final String KIND = "kind";
    private static final String CODE = "code";
    private static final String PARAMS = "params";
    private static final Set<Class<?>> AS_THEY_ARE =
            Set.of(int.class, long.class, boolean.class, Integer.class, Long.class, Boolean.class, String.class);
    private static final Set<Class<?>> TEMPORAL =
            Set.of(LocalDate.class, LocalTime.class, LocalDateTime.class, Instant.class);

    private ErrorParams() {}

    /**
     * The error's components, by name, in declaration order. An empty {@code Optional} leaves its
     * key out.
     *
     * @param error the error, a record
     * @return the params, possibly empty
     * @throws IllegalStateException when the error is no record, holds a personal value, or holds a
     *     value with no mapping: a programming error the shape rules and {@link #isMappable} catch
     *     first; the message names the type, never the value
     */
    static Map<String, Object> of(DomainError error) {
        if (!(error instanceof Record record)) {
            throw new IllegalStateException("An error must be a record to become a problem's params: "
                    + error.getClass().getName());
        }
        return components(record);
    }

    /**
     * Whether a record component of this type maps: the guarantee {@link #of} relies on.
     *
     * @param type a record component's generic type
     * @return {@code true} when every value of the type has a mapping
     */
    static boolean isMappable(Type type) {
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == Optional.class) {
            return isMappableValue(parameterized.getActualTypeArguments()[0]);
        }
        return isMappableValue(type);
    }

    private static boolean isMappableValue(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            return parameterized.getRawType() == List.class
                    && isMappableValue(parameterized.getActualTypeArguments()[0]);
        }
        if (!(type instanceof Class<?> raw) || PersonalData.class.isAssignableFrom(raw)) {
            return false;
        }
        if (DomainError.class.isAssignableFrom(raw)
                || raw.isEnum()
                || raw == UUID.class
                || TEMPORAL.contains(raw)
                || AS_THEY_ARE.contains(raw)) {
            return true;
        }
        if (raw.isRecord()) {
            RecordComponent[] components = raw.getRecordComponents();
            // A one-part value is unwrapped to its part, where an Optional has no mapping.
            if (components.length == 1 && !isASealedVariant(raw)) {
                return isMappableValue(components[0].getGenericType());
            }
            return Arrays.stream(components).allMatch(component -> isMappable(component.getGenericType()));
        }
        if (raw.isSealed()) {
            return Arrays.stream(raw.getPermittedSubclasses()).allMatch(ErrorParams::isMappableValue);
        }
        return false;
    }

    private static Map<String, Object> components(Record record) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            Object value = read(record, component);
            if (value instanceof Optional<?> optional) {
                optional.ifPresent(present -> params.put(component.getName(), value(present)));
            } else {
                params.put(component.getName(), value(value));
            }
        }
        return params;
    }

    private static Object value(Object value) {
        return switch (value) {
            case PersonalData personal ->
                throw new IllegalStateException("A personal value can't be a problem's param: "
                        + personal.getClass().getName());
            case DomainError nested -> nested(nested);
            case Record record when isASealedVariant(record.getClass()) -> variant(record);
            case Record record
            when record.getClass().getRecordComponents().length == 1 ->
                value(read(record, record.getClass().getRecordComponents()[0]));
            case Record record -> components(record);
            case Enum<?> constant -> constant.name();
            case UUID id -> id.toString();
            case LocalDate date -> DateTimeFormatter.ISO_LOCAL_DATE.format(date);
            case LocalTime time -> DateTimeFormatter.ISO_LOCAL_TIME.format(time);
            case LocalDateTime dateTime -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(dateTime);
            case Instant instant -> DateTimeFormatter.ISO_INSTANT.format(instant);
            case Integer number -> number;
            case Long number -> number;
            case Boolean flag -> flag;
            case String text -> text;
            case List<?> list -> list.stream().map(ErrorParams::value).toList();
            default ->
                throw new IllegalStateException("No mapping to a problem's param for "
                        + value.getClass().getName());
        };
    }

    private static Map<String, Object> nested(DomainError error) {
        Map<String, Object> refusal = new LinkedHashMap<>();
        refusal.put(CODE, error.code());
        refusal.put(PARAMS, of(error));
        return refusal;
    }

    /** A variant of a sealed type that is no error, such as Planning's {@code BasisChange}. */
    private static boolean isASealedVariant(Class<?> type) {
        return Arrays.stream(type.getInterfaces())
                .anyMatch(parent -> parent.isSealed() && !DomainError.class.isAssignableFrom(parent));
    }

    private static Map<String, Object> variant(Record record) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put(KIND, DomainError.codeOf(record.getClass()));
        variant.putAll(components(record));
        return variant;
    }

    private static Object read(Record record, RecordComponent component) {
        try {
            return component.getAccessor().invoke(record);
        } catch (IllegalAccessException | InvocationTargetException failed) {
            throw new IllegalStateException(
                    "Can't read " + record.getClass().getName() + "." + component.getName(), failed);
        }
    }
}
