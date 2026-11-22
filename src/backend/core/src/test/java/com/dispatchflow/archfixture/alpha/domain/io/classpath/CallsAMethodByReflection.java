package com.dispatchflow.archfixture.alpha.domain.io.classpath;

import java.lang.reflect.Method;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Rule 4: java.lang.reflect reaches a member by name, past every other rule. */
public final class CallsAMethodByReflection {

    static @Nullable Object now() throws ReflectiveOperationException {
        Method now = Instant.class.getMethod("now");
        return now.invoke(null);
    }
}
