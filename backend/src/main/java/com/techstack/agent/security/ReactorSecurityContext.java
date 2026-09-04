package com.techstack.agent.security;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Schedulers;

/**
 * Bridges Servlet SecurityContextHolder state into Reactor Context and restores it
 * around operators after scheduler switches.
 */
public final class ReactorSecurityContext {

    private static final String ACCESSOR_KEY = ReactorSecurityContext.class.getName();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private ReactorSecurityContext() {
    }

    public static <T> Flux<T> onBlockingScheduler(Flux<T> source) {
        initialize();
        return source
                .subscribeOn(Schedulers.boundedElastic(), false)
                .contextCapture();
    }

    static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        ContextRegistry.getInstance().registerThreadLocalAccessor(new SecurityContextAccessor());
        Hooks.enableAutomaticContextPropagation();
    }

    private static final class SecurityContextAccessor implements ThreadLocalAccessor<SecurityContext> {
        @Override
        public Object key() {
            return ACCESSOR_KEY;
        }

        @Override
        public SecurityContext getValue() {
            SecurityContext context = SecurityContextHolder.getContext();
            return context.getAuthentication() == null ? null : context;
        }

        @Override
        public void setValue(SecurityContext value) {
            SecurityContextHolder.setContext(value);
        }

        @Override
        public void setValue() {
            SecurityContextHolder.clearContext();
        }
    }
}
