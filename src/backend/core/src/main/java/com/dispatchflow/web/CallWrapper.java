package com.dispatchflow.web;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.CurrentActor;
import com.dispatchflow.audit.application.api.CurrentCall;
import com.dispatchflow.web.security.Caller;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod;

/**
 * Wraps every handler's invocation in {@link CallRetry}, through the handler adapter Spring Boot
 * takes from this registration (ADR-140 decision 6). Spring resolves and validates the arguments
 * once, and the retry invokes the handler again with them, because a request's body can be read
 * only once. No handler takes a streaming parameter ({@code ApplicationRules} rule 16), so every
 * argument a retry reuses is one a converter or a resolver already read in full, under a size
 * limit. The rule reads raw parameter types, so a stream inside a type argument, as in {@code
 * HttpEntity<InputStream>}, is left to review. A retry passes the very same argument objects, not
 * copies, so a handler must never mutate its arguments, or the next try would see the change;
 * review checks this.
 *
 * <p>It also binds the call's holders around the handler, its retries included (ADR-140 decision
 * 4): {@code CurrentCall} for every call to a handler under {@code /api/v1}, and {@code
 * CurrentActor} on top when its caller is signed in, from the {@link Caller} that {@code
 * web.security}'s access interceptor kept for the request. A handler outside {@code /api/v1} gets
 * neither. It is the one class that binds them ({@code ApplicationRules} rule 18). The exception
 * handlers run after the binding has ended, so none reads them.
 *
 * <p>Spring Boot takes this bean with {@code getIfUnique}, so a second {@code WebMvcRegistrations}
 * would silently switch both off: a later concern extends this class instead of adding one.
 * Spring's MVC configuration still configures the adapter returned here, its converters, argument
 * resolvers and body advice included, as it does its own.
 */
@Component
class CallWrapper implements WebMvcRegistrations {

    private final CallRetry retry;

    CallWrapper(CallRetry retry) {
        this.retry = retry;
    }

    @Override
    public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
        return new RetryingAdapter(retry);
    }

    /** Spring MVC's handler adapter, whose handler methods retry. */
    static final class RetryingAdapter extends RequestMappingHandlerAdapter {

        private final CallRetry retry;

        RetryingAdapter(CallRetry retry) {
            this.retry = retry;
        }

        @Override
        protected ServletInvocableHandlerMethod createInvocableHandlerMethod(HandlerMethod handlerMethod) {
            return new RetryingHandlerMethod(handlerMethod, retry);
        }
    }

    /** One request's invocation of its handler; a new instance serves each request. */
    static final class RetryingHandlerMethod extends ServletInvocableHandlerMethod {

        private final CallRetry retry;
        private @Nullable HttpServletRequest request;

        RetryingHandlerMethod(HandlerMethod handlerMethod, CallRetry retry) {
            super(handlerMethod);
            this.retry = retry;
        }

        @Override
        public void invokeAndHandle(
                ServletWebRequest webRequest, ModelAndViewContainer mavContainer, Object... providedArgs)
                throws Exception {
            request = webRequest.getRequest();
            super.invokeAndHandle(webRequest, mavContainer, providedArgs);
        }

        @Override
        protected @Nullable Object doInvoke(Object... args) throws Exception {
            HttpServletRequest current =
                    Objects.requireNonNull(request, "invokeAndHandle sets the request before any invocation");
            CallRetry.Call retried = () -> retry.run(current, getMethod().getName(), () -> super.doInvoke(args));
            // Only the request's own dispatch: an error dispatch keeps the request's attributes, but
            // its handler is the error endpoint, which acts for no caller.
            Optional<Caller> caller =
                    current.getDispatcherType() == DispatcherType.REQUEST ? Caller.of(current) : Optional.empty();
            if (caller.isEmpty()) {
                return retried.call();
            }
            Optional<Actor> actor = caller.get().actor();
            return CurrentCall.callWith(
                    caller.get().call(),
                    () -> actor.isPresent() ? CurrentActor.callWith(actor.get(), retried::call) : retried.call());
        }
    }
}
