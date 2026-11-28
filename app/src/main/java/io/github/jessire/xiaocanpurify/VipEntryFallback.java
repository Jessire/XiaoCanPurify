package io.github.jessire.xiaocanpurify;

import android.app.Activity;

import java.lang.reflect.Method;

/**
 * 「会员」标签隐藏后的兜底入口。
 *
 * <p>元宝乐园原本是挂在会员标签里的 Lynx 子页，用户点「领元宝」时宿主只是切到那个标签。
 * 标签被隐藏后这条路径就断了，所以改成用宿主自己的 {@code action://open_lynx} 在独立页面
 * 打开同一个 Lynx 地址，领取流程不受影响。
 */
final class VipEntryFallback {
    /** 与宿主 VipFragmentExtKt 里的默认元宝乐园地址保持一致。 */
    private static final String POINTS_PARK =
            "https://gw.djtaoke.cn/static/silk/nauth.html#/points-park?lynx=true&bundle_name=ext"
                    + "&lynx_v3=true&platform=[1,2,3]&version=2745";

    private VipEntryFallback() {}

    static boolean openPointsPark(Activity activity) {
        if (activity == null) return false;
        return openLynx(activity, POINTS_PARK);
    }

    static boolean openLynx(Activity activity, String url) {
        try {
            ClassLoader loader = activity.getClassLoader();
            Class<?> router = Class.forName("com.therouter.TheRouter", false, loader);
            Class<?> navigatorType = Class.forName("com.therouter.router.Navigator", false, loader);
            Object navigator = router.getMethod("build", String.class).invoke(null, "action://open_lynx");
            Method withString = navigatorType.getMethod("withString", String.class, String.class);
            withString.invoke(navigator, "url", url);
            navigatorType.getMethod("action").invoke(navigator);
            return true;
        } catch (Throwable t) {
            MainHook.log("Points park fallback unavailable: " + t.getClass().getSimpleName());
            return false;
        }
    }
}
