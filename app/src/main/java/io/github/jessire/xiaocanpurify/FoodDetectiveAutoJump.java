package io.github.jessire.xiaocanpurify;

import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import io.github.libxposed.api.XposedInterface;

final class FoodDetectiveAutoJump {
    private FoodDetectiveAutoJump() {}

    static void install(XposedInterface xposed, ClassLoader loader) {
        install(xposed, loader, LayoutSettings::hotRules);
    }

    static void install(XposedInterface xposed, ClassLoader loader, BooleanSupplier enabled,
                        BooleanSupplier blockTaskPage) {
        install(xposed, loader, () -> new LayoutSettings.HotRules(
                blockTaskPage.getAsBoolean(), enabled.getAsBoolean(), false, false));
    }

    private static void install(XposedInterface xposed, ClassLoader loader,
                                Supplier<LayoutSettings.HotRules> rulesSupplier) {
        try {
            Class<?> navigator = Class.forName("com.therouter.router.Navigator", false, loader);
            Class<?> callback = Class.forName("com.therouter.router.interceptor.NavigationCallback", false, loader);
            Method navigation = navigator.getDeclaredMethod("navigation$default", navigator, Context.class,
                    callback, int.class, Object.class);
            Method originalUrl = navigator.getMethod("getOriginalUrl");
            Method extras = navigator.getMethod("getExtras");
            if (!Modifier.isStatic(navigation.getModifiers()) || navigation.getReturnType() != void.class
                    || originalUrl.getReturnType() != String.class || extras.getReturnType() != Bundle.class) return;
            xposed.hook(navigation).intercept(chain -> {
                String diagnostic = null;
                try {
                    LayoutSettings.HotRules rules = rulesSupplier.get();
                    boolean blockPage = rules.blockTaskPage;
                    boolean blockAuto = rules.blockAutoJump;
                    if ((blockPage || blockAuto) && chain.getArg(1) == null && chain.getArg(2) == null
                            && Integer.valueOf(3).equals(chain.getArg(3)) && chain.getArg(4) == null) {
                        Object receiver = chain.getArg(0);
                        if ("/web".equals(originalUrl.invoke(receiver))) {
                            Bundle args = (Bundle) extras.invoke(receiver);
                            String link = args == null ? null : args.getString("link");
                            if (blockPage && FoodDetectiveWebExit.isTaskUrl(link)) {
                                diagnostic = "Blocked food detective task web navigation";
                            } else if (blockAuto && FoodDetectiveJumpRule.hasAutoSuffix(link)
                                    && FoodDetectiveJumpRule.isDetailExit(new Throwable().getStackTrace())) {
                                diagnostic = "Blocked detail-exit food detective navigation";
                            }
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    // Signature/data mismatch: leave the host call untouched.
                }
                if (diagnostic == null) return chain.proceed();
                MainHook.log(diagnostic);
                return null; // This overload returns void; no exit/result callback is suppressed.
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            MainHook.log("Food detective navigation hook unavailable: " + e.getClass().getSimpleName());
        }
    }
}
