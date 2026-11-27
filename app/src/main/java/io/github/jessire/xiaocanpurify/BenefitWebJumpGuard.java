package io.github.jessire.xiaocanpurify;

import android.content.Context;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

final class BenefitWebJumpGuard {
    private static final String PROMPT = "跳转领取大额福利";
    private static final String WEB_ACTIVITY = "com.realtech.webpage.web.CommonWebActivity";

    private BenefitWebJumpGuard() {}

    static void install(XposedInterface xposed, ClassLoader loader) {
        try {
            Class<?> continuation = Class.forName("kotlin.coroutines.Continuation", false, loader);
            Class<?> launch = Class.forName("com.realtech.common.ui.util.LaunchUrlUtils", false, loader);
            Method topActivity = Class.forName("com.blankj.utilcode.util.ActivityUtils", false, loader)
                    .getMethod("getTopActivity");
            hook(xposed, launch.getDeclaredMethod("openUrl", String.class, String.class,
                    boolean.class, String.class, continuation), 1, topActivity, Boolean.FALSE);
            hook(xposed, launch.getDeclaredMethod("openMini", String.class, String.class,
                    String.class, boolean.class, String.class, continuation), 2, topActivity, Boolean.FALSE);
            MainHook.log("Benefit web jump guard installed");
        } catch (Throwable t) {
            MainHook.log("Benefit web jump hooks unavailable: " + t.getClass().getSimpleName());
        }
        try {
            Class<?> continuation = Class.forName("kotlin.coroutines.Continuation", false, loader);
            Class<?> wechat = Class.forName("com.realtech.common.ui.mp.WeChatFuncs", false, loader);
            // This suspend method returns an Integer; 0 is the host's close-without-launch result.
            hook(xposed, wechat.getDeclaredMethod("showLaunchMiniAppDialog", Context.class,
                    String.class, continuation), 1, null, Integer.valueOf(0));
            MainHook.log("Benefit WeChat prompt guard installed");
        } catch (Throwable t) {
            MainHook.log("Benefit WeChat prompt hook unavailable: " + t.getClass().getSimpleName());
        }
    }

    private static void hook(XposedInterface xposed, Method method, int messageIndex,
                             Method topActivity, Object cancelled) throws Throwable {
        if (method.getReturnType() != Object.class) throw new NoSuchMethodException(method.getName());
        xposed.hook(method).intercept(chain -> {
            if (!PROMPT.equals(chain.getArg(messageIndex))) return chain.proceed();
            Object activity;
            try {
                activity = topActivity == null ? chain.getArg(0) : topActivity.invoke(null);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return chain.proceed();
            }
            if (activity == null || !WEB_ACTIVITY.equals(activity.getClass().getName())) return chain.proceed();
            MainHook.log("Cancelled benefit web jump via " + method.getName());
            return cancelled;
        });
    }
}
