package io.github.jessire.xiaocanpurify;

import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.DETAIL_AD;
import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.DETAIL_SHARE;
import static io.github.jessire.xiaocanpurify.LayoutSettings.enabled;

import android.view.View;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;

final class DetailPagePurifier {
    private DetailPagePurifier() {}

    static void install(XposedInterface xposed, ClassLoader cl) {
        OrderLiveViews.install();
        try {
            Class<?> loader = Class.forName("com.realtech.promotion.pages.detail.ext.PromotionDetailTobidAdViewExtKt", false, cl);
            for (Method method : loader.getDeclaredMethods()) {
                if (!"loadPromotionDetailBotAdA".equals(method.getName()) || method.getParameterCount() != 3
                        || !Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                        || !View.class.isAssignableFrom(method.getParameterTypes()[2])) continue;
                xposed.hook(method).intercept(chain -> {
                    View ad = OrderLiveViews.asView(chain.getArg(2));
                    if (enabled(DETAIL_AD)) {
                        OrderLiveViews.skipped(ad, () -> enabled(DETAIL_AD), OrderLiveViews.Size.VERTICAL);
                        OrderLiveViews.watchLayout(ad);
                        return null;
                    }
                    OrderLiveViews.beforeBind(ad);
                    Object result = chain.proceed();
                    trackAd(ad);
                    return result;
                });
            }
        } catch (Throwable t) {
            MainHook.log("Detail ad hook unavailable: " + t.getClass().getSimpleName());
        }
        String[] bindings = {"LayoutPromotionDetailContentBinding", "LayoutMtPromotionDetailContentBinding",
                "LayoutElmPromotionDetailContentBinding", "LayoutDpPromotionDetailContentBinding",
                "DialogQuickPromotionDetailBinding", "DialogMtQuickPromotionDetailBinding",
                "DialogElmQuickPromotionDetailBinding", "DialogDpQuickPromotionDetailBinding"};
        for (String name : bindings) {
            try {
                Class<?> binding = Class.forName("com.realtech.promotion.databinding." + name, false, cl);
                Method bind = binding.getDeclaredMethod("bind", View.class);
                xposed.hook(bind).intercept(chain -> {
                    Object result = chain.proceed();
                    cleanBinding(result);
                    return result;
                });
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                MainHook.log("Detail binding hook unavailable: " + name);
            }
        }
    }

    private static void cleanBinding(Object binding) {
        trackAd(OrderLiveViews.asView(OrderLiveViews.getField(binding, "flTobidContainer")));
        View share = OrderLiveViews.asView(OrderLiveViews.getField(binding, "ivShare"));
        OrderLiveViews.track(share, () -> enabled(DETAIL_SHARE), OrderLiveViews.Size.NONE);
        OrderLiveViews.watchLayout(share);
    }

    private static void trackAd(View view) {
        OrderLiveViews.track(view, () -> enabled(DETAIL_AD), OrderLiveViews.Size.VERTICAL);
        OrderLiveViews.watchLayout(view);
    }
}
