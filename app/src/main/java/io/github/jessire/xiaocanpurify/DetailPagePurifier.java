package io.github.jessire.xiaocanpurify;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;

final class DetailPagePurifier {
    private static final WeakHashMap<Object, Boolean> WATCHED = new WeakHashMap<>();

    private DetailPagePurifier() {}

    static void install(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> loader = Class.forName("com.realtech.promotion.pages.detail.ext.PromotionDetailTobidAdViewExtKt", false, cl);
            for (Method method : loader.getDeclaredMethods()) {
                if (!"loadPromotionDetailBotAdA".equals(method.getName()) || method.getParameterCount() != 3) continue;
                xposed.hook(method).intercept(chain -> {
                    collapse(chain.getArg(2));
                    MainHook.log("Blocked detail ToBid ad load");
                    return null;
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
                    if (result != null && WATCHED.put(result, true) == null) {
                        View root = (View) binding.getMethod("getRoot").invoke(result);
                        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> cleanBinding(result));
                    }
                    return result;
                });
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                MainHook.log("Detail binding hook unavailable: " + name);
            }
        }
    }

    private static void cleanBinding(Object binding) {
        if (binding == null) return;
        for (String name : new String[]{"flTobidContainer", "ivShare"}) {
            try { collapse(binding.getClass().getField(name).get(binding)); }
            catch (ReflectiveOperationException ignored) {}
        }
    }

    private static void collapse(Object object) {
        if (!(object instanceof View)) return;
        View view = (View) object;
        if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height != 0) {
            params.height = 0;
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) params).topMargin = 0;
                ((ViewGroup.MarginLayoutParams) params).bottomMargin = 0;
            }
            view.setLayoutParams(params);
        }
    }
}
