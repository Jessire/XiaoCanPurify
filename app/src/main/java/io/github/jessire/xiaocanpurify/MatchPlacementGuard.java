package io.github.jessire.xiaocanpurify;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;

import io.github.libxposed.api.XposedInterface;

/**
 * Folds an empty placement match list into the null result the host already understands.
 *
 * <p>{@code MatchPlacementRepository.obtainMatchPlacement} returns null when the placement
 * request fails or times out, but returns the raw list when the request succeeds. That list
 * can be empty when the server has no match values for the requested placement. Its callers
 * only check for null: {@code MenuUseCase} then calls {@code List.first()} on the non-null
 * list and aborts the whole user-center menu build with
 * {@code NoSuchElementException: List is empty}. Feeding the empty case into the existing
 * null path keeps the host on a branch it already handles.
 *
 * <p>The hook replaces the caller continuation for the duration of the call, so the value is
 * rewritten before the host coroutine resumes. Nothing in the host state is mutated and no
 * request is retried or fabricated.
 */
public final class MatchPlacementGuard {
    private static final String REPOSITORY =
            "com.realtech.logic.placement.repository.MatchPlacementRepository";
    private static final String METHOD = "obtainMatchPlacement";
    private static final String CONTINUATION = "kotlin.coroutines.Continuation";

    private MatchPlacementGuard() {}

    /** Visible for the JVM regression test; never touches host classes. */
    static Object nullIfEmpty(Object value) {
        if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
            return null;
        }
        return value;
    }

    public static void install(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> repository = Class.forName(REPOSITORY, false, cl);
            Class<?> continuation = Class.forName(CONTINUATION, false, cl);
            Method obtain = repository.getDeclaredMethod(METHOD, String.class, continuation);
            xposed.hook(obtain).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                int slot = continuationSlot(args, continuation);
                if (slot < 0) {
                    return chain.proceed();
                }
                args[slot] = wrap(args[slot], continuation);
                return chain.proceed(args);
            });
            MainHook.log("Placement match list guard installed");
        } catch (Throwable t) {
            MainHook.log("Placement match list guard unavailable: " + t.getClass().getSimpleName());
        }
    }

    private static int continuationSlot(Object[] args, Class<?> continuation) {
        for (int i = 0; i < args.length; i++) {
            if (continuation.isInstance(args[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Visible for the JVM regression test; the guard only needs an interface with resumeWith. */
    static Object wrap(Object original, Class<?> continuation) {
        ClassLoader loader = continuation.getClassLoader();
        if (loader == null) {
            loader = MatchPlacementGuard.class.getClassLoader();
        }
        return Proxy.newProxyInstance(loader, new Class<?>[]{continuation},
                new ResumeRewriter(original));
    }

    private static final class ResumeRewriter implements InvocationHandler {
        private final Object delegate;

        ResumeRewriter(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("resumeWith".equals(method.getName()) && args != null && args.length == 1) {
                Object value = nullIfEmpty(args[0]);
                if (value != args[0]) {
                    // Same path the host takes when the placement request fails.
                    MainHook.log("Folded empty placement match list into null");
                    return method.invoke(delegate, new Object[]{null});
                }
            }
            return method.invoke(delegate, args);
        }
    }
}
