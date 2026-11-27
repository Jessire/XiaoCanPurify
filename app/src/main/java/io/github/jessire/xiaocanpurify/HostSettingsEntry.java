package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

final class HostSettingsEntry {
    private static final String ACTIVITY = "com.realtech.xiaocan.flutter.MyFlutterActivity";
    private static final String FLUTTER_VIEW = "io.flutter.embedding.android.FlutterView";
    private static final String ROUTE = "/silkworm-options";

    private HostSettingsEntry() {}

    static void install(XposedInterface xposed, ClassLoader loader) {
        hookLynxSettingsRoute(xposed, loader);
        try {
            Class<?> host = Class.forName(ACTIVITY, false, loader);
            Class<?> flutter = Class.forName("io.flutter.embedding.android.FlutterActivity", false, loader);
            Class<?> engine = Class.forName("com.gtbluesky.fusion.engine.FusionEngineBinding", false, loader);
            Class<?> reply = Class.forName("io.flutter.plugin.common.BasicMessageChannel$Reply", false, loader);
            Class<?> stack = Class.forName("com.gtbluesky.fusion.container.FusionStackManager", false, loader);
            Field stackInstance = stack.getField("INSTANCE");
            Method findContainer = stack.getDeclaredMethod("findContainer", String.class);
            Method createView = flutter.getDeclaredMethod("createFlutterView");
            Method onCreate = host.getDeclaredMethod("onCreate", Bundle.class);
            Method newIntent = flutter.getDeclaredMethod("onNewIntent", Intent.class);
            Method sync = engine.getDeclaredMethod("attach$lambda$6", Object.class, reply);
            if (!flutter.isAssignableFrom(host) || createView.getReturnType() != View.class) {
                throw new NoSuchMethodException("Unexpected Flutter content factory");
            }

            xposed.hook(onCreate).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Activity activity = (Activity) chain.getThisObject();
                    FlutterSettingsRow content = findContent(activity);
                    if (content != null && !activity.isFinishing()) {
                        Bundle state = (Bundle) chain.getArg(0);
                        Object history = state == null ? null : state.get("history");
                        content.setRouteVisible(isSettingsIntent(activity.getIntent())
                                && isSettingsHistory(history, true));
                    }
                } catch (RuntimeException | LinkageError e) {
                    logFailure(e);
                }
                return result;
            });
            xposed.hook(newIntent).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    if (chain.getThisObject() instanceof Activity
                            && chain.getThisObject().getClass() == host) {
                        FlutterSettingsRow content = findContent((Activity) chain.getThisObject());
                        if (content != null && !isSettingsIntent((Intent) chain.getArg(0))) {
                            // Do not infer a new Fusion root from an Android reuse/deep-link intent.
                            content.disableEntry();
                        }
                    }
                } catch (RuntimeException | LinkageError e) {
                    logFailure(e);
                }
                return result;
            });
            xposed.hook(sync).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    // Fusion reports Dart route history for each container (in-place pushes/pops).
                    if (chain.getArg(0) instanceof Map<?, ?> payload
                            && payload.get("uniqueId") instanceof String id
                            && findContainer.invoke(stackInstance.get(null), id) instanceof Activity activity
                            && activity.getClass() == host) {
                        FlutterSettingsRow content = findContent(activity);
                        if (content != null) content.setRouteVisible(isSettingsHistory(payload.get("history"), false));
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    logFailure(e);
                }
                return result;
            });

            FlutterSettingsSemantics semantics = new FlutterSettingsSemantics(loader);
            semantics.install(xposed);

            // Register last: the other hooks are inert if content creation cannot be hooked.
            xposed.hook(createView).intercept(chain -> {
                Object original = chain.proceed();
                try {
                    if (chain.getThisObject() instanceof Activity activity && activity.getClass() == host
                            && original instanceof View view && FLUTTER_VIEW.equals(view.getClass().getName())
                            && view.getParent() == null && isSettingsIntent(activity.getIntent())
                            && isVerifiedHost(activity) && semantics.isTextureOnly(view)) {
                        // Wrap before FlutterActivity.setContentView: never detach/reparent an attached surface.
                        return new FlutterSettingsRow(activity, view, semantics);
                    }
                } catch (PackageManager.NameNotFoundException | RuntimeException | LinkageError e) {
                    logFailure(e);
                }
                return original;
            });
            MainHook.log("Hooked Flutter settings content (verified hosts only)");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailure(e);
        }
    }

    // 3.23.0 can open settings as a Lynx page; keep the Flutter page that carries the module row.
    private static void hookLynxSettingsRoute(XposedInterface xposed, ClassLoader loader) {
        try {
            Class<?> navigator = Class.forName("com.realtech.common.router.LynxSettingsNavigator", false, loader);
            Method open = navigator.getDeclaredMethod("open", boolean.class);
            xposed.hook(open).intercept(chain -> Boolean.TRUE.equals(chain.getArg(0))
                    ? chain.proceed(new Object[]{Boolean.FALSE}) : chain.proceed());
            MainHook.log("Hooked LynxSettingsNavigator.open -> Flutter settings");
        } catch (ClassNotFoundException ignored) {
            // Older hosts only have the Flutter settings route.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailure(e);
        }
    }

    private static boolean isSettingsIntent(Intent intent) {
        return intent != null && intent.getData() == null && ROUTE.equals(intent.getStringExtra("ROUTE_NAME"));
    }

    static boolean isSettingsHistory(Object history, boolean allowEmpty) {
        if (history == null) return allowEmpty;
        if (!(history instanceof List<?> entries)) return false;
        if (entries.isEmpty()) return allowEmpty;
        return entries.get(entries.size() - 1) instanceof Map<?, ?> top && ROUTE.equals(top.get("name"));
    }

    @SuppressWarnings("deprecation")
    private static boolean isVerifiedHost(Activity activity) throws PackageManager.NameNotFoundException {
        if (!MainHook.TARGET_PACKAGE.equals(activity.getPackageName())) return false;
        PackageInfo info = activity.getPackageManager().getPackageInfo(MainHook.TARGET_PACKAGE, 0);
        long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        return HostVersions.isSupported(info.versionName, code);
    }

    private static FlutterSettingsRow findContent(Activity activity) {
        View parent = activity.findViewById(android.R.id.content);
        if (parent instanceof ViewGroup group && group.getChildCount() == 1
                && group.getChildAt(0) instanceof FlutterSettingsRow content) return content;
        return null;
    }

    private static void logFailure(Throwable e) {
        MainHook.log("Flutter settings entry unavailable: " + e.getClass().getSimpleName());
    }

}
