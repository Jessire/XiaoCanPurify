package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

final class EnrollmentJumpGuard {
    private static final AutoJumpWindow WINDOW = new AutoJumpWindow();
    private static WeakReference<Activity> current = new WeakReference<>(null);

    private EnrollmentJumpGuard() {}

    static void install(XposedInterface xposed, ClassLoader cl) {
        hookSubmissionStart(xposed, cl);
        hookLaunchDialogs(xposed, cl);
        hookManualStoreRequest(xposed, cl);
        String[] observers = {
                "com.realtech.promotion.pages.detail.ext.PromotionDetailsCreateObserverKt$initEffect$1",
                "com.realtech.promotion.pages.detail.mt.ext.MtCreateObserverKt$initEffect$1",
                "com.realtech.promotion.pages.detail.elm.ext.ElmCreateObserverKt$initEffect$1",
                "com.realtech.promotion.pages.detail.dp.ext.DpPromotionDetailsCreateObserverKt$initEffect$1"
        };
        try {
            Method top = Class.forName("com.blankj.utilcode.util.ActivityUtils", false, cl).getMethod("getTopActivity");
            for (String name : observers) {
                try {
                    Class<?> observer = Class.forName(name, false, cl);
                    for (Method m : observer.getDeclaredMethods()) {
                        if (!"emit".equals(m.getName()) || m.isBridge() || m.getParameterCount() != 2) continue;
                        xposed.hook(m).intercept(chain -> {
                            Object effect = chain.getArg(0);
                            String event = effect == null ? "" : effect.getClass().getSimpleName();
                            if ("NavToOrderVoucher".equals(event) || "ShowNewUserFirstOrderSignupSuccessDialog".equals(event)) {
                                Activity activity = (Activity) top.invoke(null);
                                current = new WeakReference<>(activity);
                                WINDOW.arm(SystemClock.uptimeMillis());
                                MainHook.log("Enrollment auto-jump guard armed");
                            }
                            return chain.proceed();
                        });
                    }
                    MainHook.log("Hooked enrollment result: " + name);
                } catch (Throwable t) {
                    MainHook.log("Enrollment observer unavailable: " + name);
                }
            }
            xposed.hook(Activity.class.getDeclaredMethod("onResume")).intercept(chain -> {
                current = new WeakReference<>((Activity) chain.getThisObject());
                return chain.proceed();
            });
            for (Method m : Instrumentation.class.getDeclaredMethods()) {
                if (!"execStartActivity".equals(m.getName())) continue;
                xposed.hook(m).intercept(chain -> {
                    if (!WINDOW.isActive(SystemClock.uptimeMillis())) return chain.proceed();
                    Intent intent = null;
                    Activity owner = current.get();
                    for (Object arg : chain.getArgs()) {
                        if (arg instanceof Intent) intent = (Intent) arg;
                        if (arg instanceof Activity) owner = (Activity) arg;
                    }
                    if (owner != null && intent != null && isExternal(owner, intent)) {
                        MainHook.log("Blocked automatic external launch after enrollment");
                        return null;
                    }
                    return chain.proceed();
                });
            }
            Class<?> wx = Class.forName("com.tencent.mm.opensdk.openapi.WXApiImplV10", false, cl);
            for (Method m : wx.getMethods()) {
                if (!"sendReq".equals(m.getName()) || m.getParameterCount() != 1) continue;
                xposed.hook(m).intercept(chain -> {
                    Object request = chain.getArg(0);
                    if (WINDOW.isActive(SystemClock.uptimeMillis()) && request != null
                            && request.getClass().getName().contains("WXLaunchMiniProgram")) {
                        MainHook.log("Blocked automatic mini-app launch after enrollment");
                        return false;
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable t) {
            MainHook.log("Enrollment guard setup failed: " + t.getClass().getSimpleName());
        }
    }

    private static void hookSubmissionStart(XposedInterface xposed, ClassLoader cl) {
        String[] classes = {
                "com.realtech.promotion.pages.detail.PromotionDetailViewModel",
                "com.realtech.promotion.pages.detail.dp.DpPromotionDetailViewModel",
                "com.realtech.promotion.pages.detail.mt.vm.MtPromotionSubmitKt",
                "com.realtech.promotion.pages.detail.elm.vm.ElmPromotionSubmitKt"
        };
        for (String name : classes) {
            try {
                for (Method method : Class.forName(name, false, cl).getDeclaredMethods()) {
                    if (!"submitOrder".equals(method.getName())) continue;
                    xposed.hook(method).intercept(chain -> {
                        WINDOW.arm(SystemClock.uptimeMillis());
                        MainHook.log("Enrollment submission guard armed");
                        return chain.proceed();
                    });
                    MainHook.log("Hooked enrollment submission: " + name);
                }
            } catch (Throwable t) {
                MainHook.log("Enrollment submission hook unavailable: " + name);
            }
        }
    }

    private static void hookLaunchDialogs(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> launch = Class.forName("com.realtech.common.ui.util.LaunchUrlUtils", false, cl);
            for (Method method : launch.getDeclaredMethods()) {
                if (!"openUrl".equals(method.getName()) && !"openMini".equals(method.getName())) continue;
                xposed.hook(method).intercept(chain -> {
                    boolean storeTarget = "openMini".equals(method.getName())
                            || isStoreUrl(chain.getArg(0));
                    if (storeTarget && WINDOW.isActive(SystemClock.uptimeMillis())) {
                        MainHook.log("Blocked enrollment launch prompt: " + method.getName());
                        return Boolean.FALSE;
                    }
                    return chain.proceed();
                });
            }
            MainHook.log("Enrollment launch-dialog guard installed");
        } catch (Throwable t) {
            MainHook.log("Enrollment launch-dialog hook unavailable: " + t.getClass().getSimpleName());
        }
    }

    private static boolean isStoreUrl(Object value) {
        if (!(value instanceof String)) return false;
        try {
            java.net.URI uri = java.net.URI.create((String) value);
            String scheme = uri.getScheme();
            if (scheme == null) return false;
            switch (scheme.toLowerCase(java.util.Locale.ROOT)) {
                case "imeituan": case "meituanwaimai": case "meituan":
                case "eleme": case "tbopen": case "taobao":
                case "openapp.jdmobile": case "dianping": return true;
                case "https": case "http":
                    String host = uri.getHost();
                    if (host == null) return false;
                    host = host.toLowerCase(java.util.Locale.ROOT);
                    for (String domain : new String[]{"meituan.com", "ele.me", "jd.com", "taobao.com", "dianping.com"})
                        if (host.equals(domain) || host.endsWith("." + domain)) return true;
                    return false;
                default: return false;
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void hookManualStoreRequest(XposedInterface xposed, ClassLoader cl) {
        String[] classes = {
                "com.realtech.promotion.pages.detail.ext.PromotionDetailsDialogExtKt",
                "com.realtech.promotion.pages.detail.mt.ext.MtDetailDialogExtKt",
                "com.realtech.promotion.pages.detail.elm.ext.ElmDetailDialogExtKt"
        };
        for (String name : classes) {
            try {
                for (Method method : Class.forName(name, false, cl).getDeclaredMethods()) {
                    if (!"showPopToStoreWaySelect".equals(method.getName())) continue;
                    xposed.hook(method).intercept(chain -> {
                        for (Object arg : chain.getArgs()) {
                            if (arg instanceof Enum) {
                                String source = ((Enum<?>) arg).name();
                                if ("PROMOTION_DETAIL_HEADER".equals(source) || "PROMOTION_DETAIL_BOTTOM".equals(source)
                                        || "ORDER_LIST".equals(source)) WINDOW.onManualStoreRequest();
                            }
                        }
                        return chain.proceed();
                    });
                }
            } catch (Throwable ignored) {}
        }
    }

    private static boolean isExternal(Activity owner, Intent intent) {
        String target = intent.getComponent() != null ? intent.getComponent().getPackageName() : intent.getPackage();
        if (target == null) {
            ResolveInfo resolved = owner.getPackageManager().resolveActivity(intent, 0);
            if (resolved == null || resolved.activityInfo == null) return false;
            target = resolved.activityInfo.packageName;
        }
        // Auth/payment targets are not store launches and must remain usable.
        return "com.sankuai.meituan".equals(target) || "com.sankuai.meituan.takeoutnew".equals(target)
                || "me.ele".equals(target) || "com.jingdong.app.mall".equals(target)
                || "com.taobao.taobao".equals(target) || "com.dianping.v1".equals(target);
    }
}
