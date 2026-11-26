package io.github.jessire.xiaocanpurify;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedInterface;

/** Additional, opt-in rule for the native homepage anniversary-coupon popup in 3.21.2. */
public final class ExtraMarketingPopupBlocker {
    private static final String TAG = "HomeAnniversaryBrandCouponDialog";
    private static final String TASK_CLASS = "com.realtech.promotion.dialog."
            + "HomeAnniversaryInviteCouponUtil$showHomeAnniversaryBrandCouponDialog$2$1$1";

    private ExtraMarketingPopupBlocker() {}

    public static void install(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> queue = Class.forName("com.realtech.common.dialog.DialogUtils", false, cl);
            Class<?> taskType = Class.forName("com.realtech.common.dialog.task.DialogTaskBuilder", false, cl);
            Class<?> targetTask = Class.forName(TASK_CLASS, false, cl);
            Method show = queue.getDeclaredMethod("show", String.class, taskType);
            if (!taskType.isAssignableFrom(targetTask) || show.getReturnType() != void.class
                    || Modifier.isStatic(show.getModifiers())) {
                throw new NoSuchMethodException("Unexpected DialogUtils.show signature");
            }
            xposed.hook(show).intercept(chain -> {
                Object task = chain.getArg(1);
                if (TAG.equals(chain.getArg(0)) && task != null && task.getClass() == targetTask) {
                    // Skip before queue insertion; do not dismiss an already-created dialog.
                    MainHook.log("Blocked homepage anniversary coupon popup");
                    return null;
                }
                return chain.proceed();
            });
            MainHook.log("Homepage anniversary coupon popup rule installed");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            MainHook.log("Homepage anniversary coupon popup rule unavailable: "
                    + e.getClass().getSimpleName());
        }
    }
}
