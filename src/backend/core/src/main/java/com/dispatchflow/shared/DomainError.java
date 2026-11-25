package com.dispatchflow.shared;

/**
 * A refusal in business language (DL §3.8). Each module declares one sealed interface extending
 * this one, with a nested record per error, and the shared kernel's own errors are {@link
 * SharedKernelError}.
 *
 * <p>An error's fields become the parameters of the RFC 9457 response, so they never carry
 * personal data.
 */
public interface DomainError {

    /**
     * The stable code the web adapter sends and both apps translate (NFR-69, NFR-144).
     *
     * @return the code derived from this error's simple name by {@link #codeOf(Class)}
     */
    default String code() {
        return codeOf(getClass());
    }

    /**
     * Derives an error code from a type's simple name, so a code never drifts from its type: {@code
     * NonexistentLocalTime} becomes {@code NONEXISTENT_LOCAL_TIME}, and a run of capitals stays one
     * word ({@code SMSGatewayDown} becomes {@code SMS_GATEWAY_DOWN}).
     *
     * <p>It takes any type, so a problem's {@code params} can name the kind of a sealed variant
     * that is no error, such as Planning's {@code BasisChange}, by the same rule.
     *
     * @param type the error type, or another named (not anonymous) class
     * @return the simple name in upper snake case
     */
    static String codeOf(Class<?> type) {
        String name = type.getSimpleName();
        StringBuilder code = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
            if (i > 0 && Character.isUpperCase(current) && startsAWord(name, i)) {
                code.append('_');
            }
            code.append(Character.toUpperCase(current));
        }
        return code.toString();
    }

    private static boolean startsAWord(String name, int index) {
        char previous = name.charAt(index - 1);
        boolean nextIsLower = index + 1 < name.length() && Character.isLowerCase(name.charAt(index + 1));
        return Character.isLowerCase(previous)
                || Character.isDigit(previous)
                || (Character.isUpperCase(previous) && nextIsLower);
    }
}
