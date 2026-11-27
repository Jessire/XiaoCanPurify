package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import io.github.libxposed.api.XposedInterface;

/** Removes only the native ad row from the verified hosts' order voucher screen. */
final class PlaceOrderAdPurifier {
    private static final String PACKAGE = "com.realtech.xiaocan";
    private static final String ACTIVITY = "com.realtech.place.order.ui.PlaceOrderActivity";
    private static final String ITEM = "com.realtech.place.order.model.PlaceOrderItem";
    private static final String AD_ITEM = ITEM + "$PlaceOrderADItem";
    private static final String AD_WIDGET = "com.realtech.place.order.widget.PlaceOrderADWidget";

    private PlaceOrderAdPurifier() {}

    static void install(XposedInterface xposed, ClassLoader loader) {
        install(xposed, loader, LayoutSettings::orderVoucherNativeAdEnabled);
        hookExistingAdRow(xposed, loader);
    }

    static void install(XposedInterface xposed, ClassLoader loader, BooleanSupplier enabled) {
        try {
            Class<?> activity = Class.forName(ACTIVITY, false, loader);
            Class<?> item = Class.forName(ITEM, false, loader);
            Class<?> adItem = Class.forName(AD_ITEM, false, loader);
            Method target = activity.getDeclaredMethod("createLayout", List.class);
            if (!Activity.class.isAssignableFrom(activity) || adItem.getSuperclass() != item
                    || Modifier.isStatic(target.getModifiers()) || target.getReturnType() != void.class) {
                return;
            }
            xposed.hook(target).intercept(chain -> enabled.getAsBoolean() ? intercept(chain, adItem) : chain.proceed());
            MainHook.log("Hooked PlaceOrderActivity.createLayout for native voucher ad (verified hosts only)");
        } catch (Exception | LinkageError e) {
            MainHook.log("Native voucher ad hook unavailable: " + e.getClass().getSimpleName());
        }
    }

    private static void hookExistingAdRow(XposedInterface xposed, ClassLoader loader) {
        try {
            Class<?> page = Class.forName(ACTIVITY, false, loader);
            Class<?> widget = Class.forName(AD_WIDGET, false, loader);
            Method getView = widget.getDeclaredMethod("getView");
            if (Modifier.isStatic(getView.getModifiers()) || getView.getReturnType() != View.class) return;
            OrderLiveViews.install();
            xposed.hook(getView).intercept(chain -> {
                Object result = chain.proceed();
                if (result instanceof View row && verifiedPage(row.getContext(), page)) {
                    // This is the widget root, a direct child of the voucher LinearLayout.
                    // GONE preserves the original params and removes the row and its margins.
                    OrderLiveViews.track(row, LayoutSettings::orderVoucherNativeAdEnabled,
                            OrderLiveViews.Size.NONE);
                }
                return result;
            });
        } catch (Exception | LinkageError e) {
            MainHook.log("Native voucher row tracking unavailable: " + e.getClass().getSimpleName());
        }
    }

    private static boolean verifiedPage(Context context, Class<?> page) {
        for (int depth = 0; depth < 8 && context != null; depth++) {
            if (context instanceof Activity activity) return page.isInstance(activity) && isVerifiedHost(activity);
            if (!(context instanceof ContextWrapper wrapper)) return false;
            context = wrapper.getBaseContext();
        }
        return false;
    }

    static Object intercept(XposedInterface.Chain chain, Class<?> adItem) throws Throwable {
        Object arg = chain.getArg(0);
        Object receiver = chain.getThisObject();
        if (arg instanceof List<?> list && receiver instanceof Activity activity) {
            List<?> filtered = withoutAd(list, adItem);
            if (filtered != list && isVerifiedHost(activity)) {
                return chain.proceed(new Object[]{filtered});
            }
        }
        return chain.proceed();
    }

    static List<?> withoutAd(List<?> list, Class<?> adItem) {
        // Copy only if there is a match. Preserve business objects, their order and the source list.
        ArrayList<Object> filtered = null;
        int index = 0;
        for (Object item : list) {
            if (item != null && item.getClass() == adItem) {
                if (filtered == null) filtered = new ArrayList<>(list.subList(0, index));
            } else if (filtered != null) {
                filtered.add(item);
            }
            index++;
        }
        return filtered == null ? list : filtered;
    }

    @SuppressWarnings("deprecation")
    private static boolean isVerifiedHost(Activity activity) {
        try {
            if (!PACKAGE.equals(activity.getPackageName())) return false;
            PackageInfo info = activity.getPackageManager().getPackageInfo(PACKAGE, 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return HostVersions.isSupported(info.versionName, code);
        } catch (Exception | LinkageError e) {
            return false;
        }
    }
}
