package io.github.jessire.xiaocanpurify;

import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.HOME_SEARCH_BANNER;
import static io.github.jessire.xiaocanpurify.LayoutSettings.enabled;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/** Drops ad callbacks while enabled, but never replays placement updates or SDK preloads. */
final class HomeLiveSearchBanner {
    private static final WeakHashMap<View, State> BARS = new WeakHashMap<>();
    private static Method getSearchBinding;
    private static Field banner, image;

    private HomeLiveSearchBanner() {}

    static boolean install(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> bar = Class.forName("com.realtech.promotion.widget.HomeAppBarLayoutView", false, cl);
            getSearchBinding = bar.getMethod("getSearchBinding");
            Class<?> binding = getSearchBinding.getReturnType();
            banner = binding.getField("bannerSearchRight");
            image = binding.getField("ivSearchRight");
            for (Method m : bar.getDeclaredMethods()) {
                String name = m.getName();
                if ("updateSearchRightImgList".equals(name) || "renderSearchRightBanner".equals(name)) {
                    boolean update = "updateSearchRightImgList".equals(name);
                    xposed.hook(m).intercept(chain -> {
                        if (!HomeLiveViews.mainThread()) return chain.proceed();
                        View view = (View) chain.getThisObject();
                        State state = state(view);
                        if (enabled(HOME_SEARCH_BANNER)) {
                            // Any dropped data/render can make previously bound creative stale.
                            state.awaitingUpdate = true;
                            track(view);
                            return null;
                        }
                        if (!update && state.awaitingUpdate) return null;
                        if (update) state.awaitingUpdate = false;
                        track(view);
                        return chain.proceed();
                    });
                } else if ("updateTitleView".equals(name) || "updateTitleBar".equals(name)) {
                    xposed.hook(m).intercept(chain -> {
                        if (!HomeLiveViews.mainThread()) return chain.proceed();
                        View view = (View) chain.getThisObject();
                        try { return chain.proceed(); }
                        finally { track(view); }
                    });
                }
            }
            return true;
        } catch (Throwable t) {
            MainHook.log("Home live search banner unavailable: " + t);
            return false;
        }
    }

    private static State state(View view) {
        State value = BARS.get(view);
        if (value == null) {
            value = new State();
            BARS.put(view, value);
        }
        return value;
    }

    private static void track(View toolbar) {
        try {
            Object binding = getSearchBinding.invoke(toolbar);
            State state = state(toolbar);
            HomeLiveViews.track((View) banner.get(binding), state::hide, false, false);
            HomeLiveViews.track((View) image.get(binding), state::hide, false, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    private static final class State {
        boolean awaitingUpdate;
        boolean hide() { return awaitingUpdate || enabled(HOME_SEARCH_BANNER); }
    }

}
