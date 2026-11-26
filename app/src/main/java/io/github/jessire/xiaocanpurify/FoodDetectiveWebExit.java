package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.webkit.WebView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.function.BooleanSupplier;
import io.github.libxposed.api.XposedInterface;

/** Replaces only the last, marketing-specific branch of the task page's normal exit. */
final class FoodDetectiveWebExit {
    private static final String WEB = "com.realtech.webpage.web.";
    private static final String FRAGMENT = WEB + "AgentWebFragment";
    private static final String CONTROLLER = WEB + "WebControllerImpl";

    private FoodDetectiveWebExit() {}

    static void install(XposedInterface xposed, ClassLoader loader) {
        install(xposed, loader, LayoutSettings::foodDetectiveWebExitEnabled);
    }

    static void install(XposedInterface xposed, ClassLoader loader, BooleanSupplier enabled) {
        try {
            Class<?> fragment = Class.forName(FRAGMENT, false, loader);
            Class<?> container = Class.forName(WEB + "CommonWebActivity", false, loader);
            Method activity = fragment.getMethod("getActivity");
            Method webView = fragment.getMethod("getWebView");
            Method agent = fragment.getMethod("getAgentWeb");
            if (!Activity.class.isAssignableFrom(container)
                    || !Activity.class.isAssignableFrom(activity.getReturnType())
                    || webView.getReturnType() != WebView.class) return;
            installBranch(xposed, fragment, "onBackClick", enabled, fragment, container,
                    activity, webView, agent, null, null);

            Class<?> controller = Class.forName(CONTROLLER, false, loader);
            Field host = controller.getField("hostWeakRef");
            Method controllerAgent = controller.getMethod("getAgentWeb");
            if (host.getType() != WeakReference.class || controllerAgent.getReturnType() != agent.getReturnType()) return;
            installBranch(xposed, controller, "closeWebView", enabled, fragment, container,
                    activity, webView, agent, host, controllerAgent);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            MainHook.log("Food detective web exit hook unavailable: " + e.getClass().getSimpleName());
        }
    }

    private static void installBranch(XposedInterface xposed, Class<?> owner, String caller,
                                      BooleanSupplier enabled, Class<?> fragment, Class<?> container,
                                      Method activity, Method webView, Method agent, Field host,
                                      Method controllerAgent) throws ReflectiveOperationException {
        Method retention = owner.getDeclaredMethod("callJsRetention");
        if (Modifier.isStatic(retention.getModifiers()) || retention.getReturnType() != void.class) return;
        xposed.hook(retention).intercept(chain -> {
            Activity target = null;
            try {
                Object receiver = chain.getThisObject();
                if (enabled.getAsBoolean() && receiver != null && receiver.getClass() == owner
                        && hasExitCaller(new Throwable().getStackTrace(), owner.getName(), caller)) {
                    Object page = host == null ? receiver : ((WeakReference<?>) host.get(receiver)).get();
                    if (page != null && page.getClass() == fragment) {
                        Object parent = activity.invoke(page);
                        Object pageAgent = agent.invoke(page);
                        if (parent != null && parent.getClass() == container && pageAgent != null
                                && (controllerAgent == null || controllerAgent.invoke(receiver) == pageAgent)) {
                            Activity candidate = (Activity) parent;
                            WebView web = (WebView) webView.invoke(page);
                            if (!candidate.isFinishing() && !candidate.isDestroyed() && web != null
                                    && isTaskUrl(web.getUrl())) target = candidate;
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Fail open: no replacement if host state cannot be verified.
            }
            if (target == null) return chain.proceed();
            // Back/history and canJsBack checks have already executed. Match the normal
            // close branch; do not leave the user stuck by just dropping the JS click.
            target.finish();
            MainHook.log("Skipped food detective web exit retention");
            return null;
        });
    }

    private static boolean hasExitCaller(StackTraceElement[] stack, String owner, String caller) {
        for (StackTraceElement frame : stack) {
            if (!frame.getClassName().startsWith("com.realtech.")) continue;
            if (owner.equals(frame.getClassName()) && "callJsRetention".equals(frame.getMethodName())) continue;
            return owner.equals(frame.getClassName()) && caller.equals(frame.getMethodName());
        }
        return false;
    }

    static boolean isTaskUrl(String value) {
        if (value == null) return false;
        try {
            URI url = URI.create(value);
            if (!"https".equals(url.getScheme()) || !"gw.hzaiguojiang.com".equals(url.getRawAuthority())
                    || !"/static/act/singleActivity.html".equals(url.getRawPath())) return false;
            String route = url.getRawFragment();
            return route != null && (route.equals("/new-recommend") || route.startsWith("/new-recommend?"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
