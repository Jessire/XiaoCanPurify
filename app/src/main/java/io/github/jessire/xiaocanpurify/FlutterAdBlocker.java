package io.github.jessire.xiaocanpurify;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import io.github.libxposed.api.XposedInterface;

final class FlutterAdBlocker {
    private FlutterAdBlocker() {}

    static void install(XposedInterface xposed, ClassLoader cl) {
        String[] handlers = {
                "com.windmill.windmill_ad_plugin.WindmillAdPluginDelegate",
                "com.windmill.windmill_ad_plugin.interstitial.InterstitialAd",
                "com.windmill.windmill_ad_plugin.reward.RewardVideoAd",
                "com.windmill.windmill_ad_plugin.splashAd.SplashAd",
                "com.windmill.windmill_ad_plugin.banner.BannerAd",
                "com.windmill.windmill_ad_plugin.feedAd.NativeAd",
                "com.realtech.xiaocan.flutter.NativeAdBridge"
        };
        for (String name : handlers) {
            try {
                Class<?> handler = Class.forName(name, false, cl);
                Class<?> callType = Class.forName("io.flutter.plugin.common.MethodCall", false, cl);
                Class<?> resultType = Class.forName("io.flutter.plugin.common.MethodChannel$Result", false, cl);
                Field methodField = callType.getField("method");
                Method success = resultType.getMethod("success", Object.class);
                Method error = resultType.getMethod("error", String.class, String.class, Object.class);
                String entry = name.endsWith("NativeAdBridge") ? "createBridge$lambda$3" : "onMethodCall";
                Method target = handler.getDeclaredMethod(entry, callType, resultType);
                xposed.hook(target).intercept(chain -> {
                    String method = (String) methodField.get(chain.getArg(0));
                    if (!isAdMethod(method)) return chain.proceed();
                    Object reply = chain.getArg(1);
                    if ("isReady".equals(method) || "show_dyds".equals(method)) {
                        success.invoke(reply, Boolean.FALSE);
                    } else if ("getCacheAdInfoList".equals(method)) {
                        success.invoke(reply, Collections.emptyList());
                    } else if ("load".equals(method) || "showAd".equals(method)) {
                        // Resolve the Future as unavailable, never as watched/rewarded.
                        error.invoke(reply, "AD_BLOCKED", "Ad unavailable", null);
                    } else {
                        success.invoke(reply, (Object) null);
                    }
                    MainHook.log("Blocked Flutter ad method: " + method);
                    return null;
                });
                MainHook.log("Hooked ad channel: " + name);
            } catch (Throwable t) {
                MainHook.log("Ad channel hook unavailable: " + name + " (" + t.getClass().getSimpleName() + ")");
            }
        }
    }

    private static boolean isAdMethod(String method) {
        if (method == null) return false;
        switch (method) {
            case "initRequest":
            case "load":
            case "showAd":
            case "isReady":
            case "getAdInfo":
            case "getCacheAdInfoList":
            case "destroy":
            case "init_interstitial":
            case "load_interstitial":
            case "pre_load_interstitial":
            case "init_with_id":
            case "load_with_id":
            case "pre_load_with_id":
            case "show_dyds":
                return true;
            default:
                return false;
        }
    }
}
