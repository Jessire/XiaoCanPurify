package io.github.jessire.xiaocanpurify;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedInterface;

/** Suppresses only the homepage order-backed free-order invitation in host 3.21.2. */
final class HomeFreeOrderPopupBlocker {
    private static final String HOME_VM = "com.realtech.promotion.pages.home.viewmodel.HomeViewModel";
    private static final String HOME_VM_ORDER = "com.realtech.promotion.pages.home.viewmodel.HomeVMOrderKt";

    private HomeFreeOrderPopupBlocker() {}

    static void install(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> owner = Class.forName(HOME_VM_ORDER, false, cl);
            Class<?> homeVm = Class.forName(HOME_VM, false, cl);
            Method check = owner.getDeclaredMethod("checkFreeOrderDialog", homeVm);
            if (!Modifier.isStatic(check.getModifiers()) || check.getReturnType() != void.class) {
                throw new NoSuchMethodException("Unexpected HomeVMOrderKt.checkFreeOrderDialog signature");
            }
            xposed.hook(check).intercept(chain -> {
                if (!LayoutSettings.homeFreeOrderPopupEnabled()) return chain.proceed();
                // The producer sets isHomeInviteFreeOrderShowing before enqueueing;
                // its task clears it after dismissal. Skip the producer, not the queued task.
                MainHook.log("Blocked homepage free-order invitation popup");
                return null;
            });
            MainHook.log("Hooked homepage free-order invitation producer (3.21.2 only)");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            MainHook.log("Homepage free-order invitation rule unavailable: " + e.getClass().getSimpleName());
        }
    }
}
