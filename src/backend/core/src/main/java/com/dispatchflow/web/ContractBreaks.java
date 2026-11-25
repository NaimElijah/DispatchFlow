package com.dispatchflow.web;

import com.dispatchflow.shared.adapter.in.web.ProblemItem;
import com.dispatchflow.shared.adapter.in.web.ProblemItem.Place;
import jakarta.validation.ConstraintViolation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * The failing items of a request that breaks the contract (ADR-140 decisions 5 and 9). Each says
 * where it failed, by a pointer into the body or a parameter's name and place, and never the
 * value: an item's {@code params} carry only a constraint's own bounds.
 */
final class ContractBreaks {

    private static final String WHOLE_BODY = "";
    private static final String POINTER_STEP = "/";
    // Jackson 3 reports a duplicate key as a plain StreamReadException, with no subtype of its own,
    // so only its message tells it apart. ContractErrorHandlerTest pins the text.
    private static final String DUPLICATE_KEY_MESSAGE = "Duplicate Object property ";
    // The constraint attributes an item's params may carry: bounds the contract states, never the
    // refused value.
    private static final Set<String> CONSTRAINT_BOUNDS = Set.of("min", "max", "value");
    private static final Map<String, String> CODE_BY_CONSTRAINT = Map.ofEntries(
            Map.entry("NotNull", WebCodes.REQUIRED),
            Map.entry("NotEmpty", WebCodes.REQUIRED),
            Map.entry("NotBlank", WebCodes.REQUIRED),
            Map.entry("Size", WebCodes.WRONG_SIZE),
            Map.entry("Min", WebCodes.TOO_SMALL),
            Map.entry("DecimalMin", WebCodes.TOO_SMALL),
            Map.entry("Max", WebCodes.TOO_LARGE),
            Map.entry("DecimalMax", WebCodes.TOO_LARGE),
            Map.entry("Pattern", WebCodes.WRONG_FORMAT),
            Map.entry("Email", WebCodes.WRONG_FORMAT));

    private ContractBreaks() {}

    /**
     * The one item of a body that can't be read: the first problem Jackson found, alone.
     *
     * @param unreadable Spring's exception, whose cause is Jackson's, if any
     * @return one item: an unknown property, a duplicate key or a wrong type, at its pointer; or an
     *     unreadable body, at the whole body, for anything else, a missing body included
     */
    static List<ProblemItem> unreadable(HttpMessageNotReadableException unreadable) {
        return List.of(firstJacksonCause(unreadable)
                .map(ContractBreaks::itemOf)
                .orElseGet(() -> ProblemItem.pointer(WebCodes.UNREADABLE_BODY, WHOLE_BODY)));
    }

    /**
     * Whether a body over its limit caused this, at any depth: the one place causes are walked, for
     * {@code web}'s own marker, which a converter wraps.
     *
     * @param thrown the exception
     * @return {@code true} when {@link BodySizeLimit.BodyTooLarge} is in its cause chain
     */
    static boolean isBodyTooLarge(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof BodySizeLimit.BodyTooLarge) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every failing field of a validated body, at once.
     *
     * @param errors the validation's errors
     * @return one item per error, at its field's pointer, or at the whole body for an object's own
     *     error
     */
    static List<ProblemItem> invalidFields(Errors errors) {
        return invalidFields(WHOLE_BODY, errors);
    }

    /**
     * Every failing field of one validated element, each pointer under the element's own: Spring's
     * method validation reports each element of a list or map body apart, with its index or key.
     */
    private static List<ProblemItem> invalidFields(String element, Errors errors) {
        return errors.getAllErrors().stream()
                .map(error -> itemOf(element, error))
                .toList();
    }

    /**
     * Every failing parameter of a validated method, at once, each with its place.
     *
     * @param invalid Spring's method validation result
     * @return one item per error
     */
    static List<ProblemItem> invalidParameters(HandlerMethodValidationException invalid) {
        List<ProblemItem> items = new ArrayList<>();
        invalid.visitResults(new HandlerMethodValidationException.Visitor() {
            @Override
            public void cookieValue(CookieValue cookieValue, ParameterValidationResult result) {
                addParameter(items, nameOf(cookieValue.name(), cookieValue.value(), result), Place.COOKIE, result);
            }

            @Override
            public void matrixVariable(MatrixVariable matrixVariable, ParameterValidationResult result) {
                addParameter(items, nameOf(matrixVariable.name(), matrixVariable.value(), result), Place.PATH, result);
            }

            @Override
            public void modelAttribute(@Nullable ModelAttribute modelAttribute, ParameterErrors errors) {
                items.addAll(invalidFields(containerPointer(errors), errors));
            }

            @Override
            public void pathVariable(PathVariable pathVariable, ParameterValidationResult result) {
                addParameter(items, nameOf(pathVariable.name(), pathVariable.value(), result), Place.PATH, result);
            }

            @Override
            public void requestBody(RequestBody requestBody, ParameterErrors errors) {
                items.addAll(invalidFields(containerPointer(errors), errors));
            }

            @Override
            public void requestBodyValidationResult(RequestBody requestBody, ParameterValidationResult result) {
                result.getResolvableErrors()
                        .forEach(error -> items.add(ProblemItem.pointer(
                                codeOf(error, result), containerPointer(result), boundsOf(error, result))));
            }

            @Override
            public void requestHeader(RequestHeader requestHeader, ParameterValidationResult result) {
                addParameter(items, nameOf(requestHeader.name(), requestHeader.value(), result), Place.HEADER, result);
            }

            @Override
            public void requestParam(@Nullable RequestParam requestParam, ParameterValidationResult result) {
                String name = requestParam == null
                        ? parameterName(result.getMethodParameter())
                        : nameOf(requestParam.name(), requestParam.value(), result);
                addParameter(items, name, Place.QUERY, result);
            }

            @Override
            public void requestPart(RequestPart requestPart, ParameterErrors errors) {
                items.addAll(invalidFields(containerPointer(errors), errors));
            }

            @Override
            public void other(ParameterValidationResult result) {
                addParameter(items, parameterName(result.getMethodParameter()), Place.QUERY, result);
            }
        });
        return items;
    }

    /**
     * The item of a required multipart part the request lacks.
     *
     * @param partName the part's name, as the operation declares it
     * @return a required item, at the part's pointer
     */
    static ProblemItem missingPart(String partName) {
        return ProblemItem.pointer(WebCodes.REQUIRED, POINTER_STEP + escape(partName));
    }

    /**
     * The item of a 400 whose exception names no field or parameter, such as a parameter condition
     * the request doesn't meet, so that every 400 lists at least one item, as the contract requires.
     *
     * @return an invalid-value item for the whole request, with neither a pointer nor a parameter
     */
    static ProblemItem wholeRequest() {
        return ProblemItem.request(WebCodes.INVALID_VALUE);
    }

    /**
     * The item of a parameter that doesn't convert to its type.
     *
     * @param mismatch Spring's exception
     * @return a wrong-type item, with the parameter's name and the place its annotation names
     */
    static ProblemItem wrongType(TypeMismatchException mismatch) {
        if (mismatch instanceof MethodArgumentTypeMismatchException argument) {
            return ProblemItem.parameter(WebCodes.WRONG_TYPE, argument.getName(), placeOf(argument.getParameter()));
        }
        String property = mismatch.getPropertyName();
        return ProblemItem.parameter(WebCodes.WRONG_TYPE, property == null ? WHOLE_BODY : property, Place.QUERY);
    }

    private static Optional<JacksonException> firstJacksonCause(Throwable thrown) {
        for (Throwable cause = thrown.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException jackson) {
                return Optional.of(jackson);
            }
        }
        return Optional.empty();
    }

    private static ProblemItem itemOf(JacksonException jackson) {
        return switch (jackson) {
            case UnrecognizedPropertyException unknown ->
                ProblemItem.pointer(WebCodes.UNKNOWN_PROPERTY, pointerOf(unknown.getPath()));
            case StreamReadException read
            when isDuplicateKey(read) -> ProblemItem.pointer(WebCodes.DUPLICATE_PROPERTY, duplicatePointer(read));
            case MismatchedInputException mismatched ->
                ProblemItem.pointer(WebCodes.WRONG_TYPE, pointerOf(mismatched.getPath()));
            default -> ProblemItem.pointer(WebCodes.UNREADABLE_BODY, WHOLE_BODY);
        };
    }

    private static boolean isDuplicateKey(StreamReadException read) {
        String message = read.getOriginalMessage();
        return message != null && message.startsWith(DUPLICATE_KEY_MESSAGE);
    }

    /** Where the duplicate is: the parser stopped on the repeated key, in the object holding it. */
    private static String duplicatePointer(StreamReadException read) {
        JsonParser parser = read.processor();
        return parser == null
                ? pointerOf(read.getPath())
                : parser.streamReadContext().pathAsPointer().toString();
    }

    private static String pointerOf(List<JacksonException.Reference> path) {
        return path.stream()
                .map(step -> {
                    String name = step.getPropertyName();
                    return POINTER_STEP + (name == null ? String.valueOf(step.getIndex()) : escape(name));
                })
                .collect(Collectors.joining());
    }

    private static ProblemItem itemOf(String element, ObjectError error) {
        String pointer = element + (error instanceof FieldError field ? fieldPointer(field.getField()) : WHOLE_BODY);
        if (error instanceof FieldError field && field.isBindingFailure()) {
            return ProblemItem.pointer(WebCodes.WRONG_TYPE, pointer);
        }
        Map<String, Object> bounds = error.contains(ConstraintViolation.class)
                ? boundsOf(error.unwrap(ConstraintViolation.class))
                : Map.of();
        return ProblemItem.pointer(codeOf(error.getCode()), pointer, bounds);
    }

    private static void addParameter(
            List<ProblemItem> items, String name, Place place, ParameterValidationResult result) {
        result.getResolvableErrors()
                .forEach(error ->
                        items.add(ProblemItem.parameter(codeOf(error, result), name, place, boundsOf(error, result))));
    }

    private static String codeOf(MessageSourceResolvable error, ParameterValidationResult result) {
        return codeOf(constraintName(result.unwrap(error, ConstraintViolation.class)));
    }

    private static Map<String, Object> boundsOf(MessageSourceResolvable error, ParameterValidationResult result) {
        return boundsOf(result.unwrap(error, ConstraintViolation.class));
    }

    private static String codeOf(@Nullable String constraint) {
        return constraint == null
                ? WebCodes.INVALID_VALUE
                : CODE_BY_CONSTRAINT.getOrDefault(constraint, WebCodes.INVALID_VALUE);
    }

    private static String constraintName(ConstraintViolation<?> violation) {
        return violation
                .getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName();
    }

    private static Map<String, Object> boundsOf(ConstraintViolation<?> violation) {
        Map<String, Object> bounds = new LinkedHashMap<>();
        violation.getConstraintDescriptor().getAttributes().forEach((name, value) -> {
            if (CONSTRAINT_BOUNDS.contains(name)) {
                bounds.put(name, value);
            }
        });
        return bounds;
    }

    private static String containerPointer(ParameterValidationResult result) {
        Integer index = result.getContainerIndex();
        Object key = result.getContainerKey();
        if (index != null) {
            return POINTER_STEP + index;
        }
        return key == null ? WHOLE_BODY : POINTER_STEP + escape(key.toString());
    }

    /** Spring's field path, such as {@code stops[0].name}, as a pointer: {@code /stops/0/name}. */
    private static String fieldPointer(String field) {
        if (field.isEmpty()) {
            return WHOLE_BODY;
        }
        return Arrays.stream(field.replace("[", ".").replace("]", "").split("\\."))
                .filter(step -> !step.isEmpty())
                .map(step -> POINTER_STEP + escape(step))
                .collect(Collectors.joining());
    }

    /** RFC 6901: {@code ~} first, then {@code /}. */
    private static String escape(String step) {
        return step.replace("~", "~0").replace("/", "~1");
    }

    private static Place placeOf(MethodParameter parameter) {
        if (parameter.hasParameterAnnotation(PathVariable.class)
                || parameter.hasParameterAnnotation(MatrixVariable.class)) {
            return Place.PATH;
        }
        if (parameter.hasParameterAnnotation(RequestHeader.class)) {
            return Place.HEADER;
        }
        if (parameter.hasParameterAnnotation(CookieValue.class)) {
            return Place.COOKIE;
        }
        return Place.QUERY;
    }

    private static String nameOf(String name, String value, ParameterValidationResult result) {
        if (!name.isEmpty()) {
            return name;
        }
        return value.isEmpty() ? parameterName(result.getMethodParameter()) : value;
    }

    private static String parameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        return name == null ? WHOLE_BODY : name;
    }
}
