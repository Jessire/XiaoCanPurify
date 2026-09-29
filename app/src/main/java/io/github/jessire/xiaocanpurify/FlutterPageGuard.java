package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * Force the withdraw Flutter page into texture mode and briefly cover the
 * bottom join card with a normal child view until the network blocker has
 * removed it. The page itself stays visible from the first frame.
 */
public final class FlutterPageGuard {
    private static final String TAG = "XiaoCanPurify";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, View> COVER = new WeakHashMap<>();
    private static volatile long withdrawRouteAt;
    private static volatile Object renderModeTexture;
    private static volatile boolean textureApplied;

    private FlutterPageGuard() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        hookRoute(xposed, classLoader, "com.gtbluesky.fusion.navigator.FusionNavigator", "push");
        hookRoute(xposed, classLoader, "com.realtech.common.router.RouterUtils", "goFlutter");
        try {
            Class<?> renderMode = Class.forName("io.flutter.embedding.android.RenderMode", false, classLoader);
            for (Object mode : renderMode.getEnumConstants()) {
                if ("texture".equals(((Enum<?>) mode).name())) {
                    renderModeTexture = mode;
                    break;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "Withdraw texture resolve failed: " + t);
        }
        try {
            Class<?> flutterActivity = Class.forName("io.flutter.embedding.android.FlutterActivity", false, classLoader);
            Method getRenderMode = flutterActivity.getDeclaredMethod("getRenderMode");
            getRenderMode.setAccessible(true);
            xposed.hook(getRenderMode).intercept(chain -> {
                if (isWithdrawRouteWindow() && !textureApplied && renderModeTexture != null) {
                    textureApplied = true;
                    withdrawRouteAt = 0;
                    Log.i(TAG, "Withdraw forced to texture mode");
                    return renderModeTexture;
                }
                return chain.proceed();
            });
            Log.i(TAG, "Withdraw texture hook installed");
        } catch (Throwable t) {
            Log.i(TAG, "Withdraw texture hook failed: " + t);
        }
        try {
            Method onAttached = View.class.getDeclaredMethod("onAttachedToWindow");
            xposed.hook(onAttached).intercept(chain -> {
                Object result = chain.proceed();
                Object self = chain.getThisObject();
                if (self instanceof SurfaceView && isFlutterSurface((SurfaceView) self)) {
                    try {
                        ((SurfaceView) self).setZOrderOnTop(false);
                        Log.i(TAG, "Withdraw surface moved behind window");
                    } catch (Throwable t) {
                        Log.i(TAG, "setZOrderedOnTop failed: " + t);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            Log.i(TAG, "Surface z-order hook failed: " + t);
        }
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            xposed.hook(onResume).intercept(chain -> {
                Object result = chain.proceed();
                Object self = chain.getThisObject();
                if (self instanceof Activity) holdIfWithdraw((Activity) self);
                return result;
            });
            Log.i(TAG, "Withdraw guard installed");
        } catch (Throwable t) {
            Log.i(TAG, "Withdraw guard failed: " + t);
        }
    }

    public static void onWithdrawResponse() {
        // The timed cover is simpler than frame-copy feedback.
    }

    private static void hookRoute(XposedInterface xposed, ClassLoader classLoader, String className, String prefix) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().startsWith(prefix) || method.getParameterCount() == 0) continue;
                xposed.hook(method).intercept(chain -> {
                    for (Object arg : chain.getArgs()) {
                        if (arg instanceof String && isWithdrawPath((String) arg)) {
                            withdrawRouteAt = SystemClock.uptimeMillis();
                            textureApplied = false;
                            break;
                        }
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable t) {
            Log.i(TAG, "Route hook failed: " + className);
        }
    }

    private static void holdIfWithdraw(Activity activity) {
        if (activity.isFinishing() || COVER.containsKey(activity) || !isFlutterHost(activity)) return;
        if (!isWithdrawEntry(activity)) return;
        withdrawRouteAt = 0;
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            View cover = new View(activity);
            cover.setBackgroundColor(0xFFF4F4F4);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 200), Gravity.BOTTOM);
            ((ViewGroup) content).addView(cover, params);
            COVER.put(activity, cover);
            MAIN.postDelayed(() -> removeCover(activity), 1800);
            Log.i(TAG, "Withdraw bottom cover added");
        }
    }

    private static void removeCover(Activity activity) {
        View cover = COVER.remove(activity);
        if (cover != null) {
            ViewGroup parent = (ViewGroup) cover.getParent();
            if (parent != null) parent.removeView(cover);
            Log.i(TAG, "Withdraw bottom cover removed");
        }
    }

    private static boolean isWithdrawRouteWindow() {
        long marked = withdrawRouteAt;
        return marked != 0 && SystemClock.uptimeMillis() - marked < 5000;
    }

    private static boolean isFlutterHost(Activity activity) {
        String name = activity.getClass().getName();
        return name.endsWith("MyFlutterActivity")
                || name.endsWith("FusionActivity")
                || name.endsWith("FusionFragmentActivity");
    }

    private static boolean isWithdrawEntry(Activity activity) {
        if (isWithdrawRouteWindow()) return true;
        Intent intent = activity.getIntent();
        if (intent == null) return false;
        if (isWithdrawPath(intent.getDataString()) || isWithdrawPath(intent.getAction())) return true;
        Bundle extras = intent.getExtras();
        if (extras == null) return false;
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            if (value != null && isWithdrawPath(String.valueOf(value))) return true;
        }
        return false;
    }

    private static boolean isWithdrawPath(String value) {
        if (value == null) return false;
        return value.contains("/withdraw")
                && !value.contains("withdraw_rule")
                && !value.contains("withdrawal");
    }

    private static boolean isFlutterSurface(SurfaceView view) {
        String name = view.getClass().getName();
        return name.contains("FlutterSurface") || name.contains("FlutterView");
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
