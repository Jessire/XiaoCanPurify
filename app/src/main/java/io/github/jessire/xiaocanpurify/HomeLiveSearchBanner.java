package io.github.jessire.xiaocanpurify;

import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.HOME_SEARCH_BANNER;
import static io.github.jessire.xiaocanpurify.LayoutSettings.enabled;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/** Drops ad callbacks while enabled, but never replays placement updates or SDK preloads. */
final class HomeLiveSearchBanner {
    private static final WeakHashMap<View, State> BARS = new WeakHashMap<>();
    private static final WeakHashMap<View, Button> BUTTONS = new WeakHashMap<>();
    private static Method getSearchBinding;
    private static Field banner, image, button;

    private HomeLiveSearchBanner() {}

    static boolean install(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> bar = Class.forName("com.realtech.promotion.widget.HomeAppBarLayoutView", false, cl);
            getSearchBinding = bar.getMethod("getSearchBinding");
            Class<?> binding = getSearchBinding.getReturnType();
            banner = binding.getField("bannerSearchRight");
            image = binding.getField("ivSearchRight");
            button = binding.getField("tvSearch");
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
                        View target = button(view);
                        Button overlay = target != null ? BUTTONS.get(target) : null;
                        if (overlay != null) overlay.apply(target, false);
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

    private static View button(View toolbar) {
        try { return (View) button.get(getSearchBinding.invoke(toolbar)); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
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
            View target = (View) button.get(binding);
            if (target != null) buttonState(target).apply(target, enabled(HOME_SEARCH_BANNER));
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    private static Button buttonState(View view) {
        return BUTTONS.computeIfAbsent(view, target -> {
            target.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    Button overlay = BUTTONS.get(v);
                    if (overlay != null) overlay.apply(v, enabled(HOME_SEARCH_BANNER));
                }
                @Override public void onViewDetachedFromWindow(View v) {}
            });
            return new Button();
        });
    }

    static void refresh() {
        for (View view : new ArrayList<>(BUTTONS.keySet())) {
            Button overlay = BUTTONS.get(view);
            if (view != null && overlay != null && view.isAttachedToWindow()) overlay.apply(view, enabled(HOME_SEARCH_BANNER));
        }
    }

    private static final class State {
        boolean awaitingUpdate;
        boolean hide() { return awaitingUpdate || enabled(HOME_SEARCH_BANNER); }
    }

    private static final class Button {
        // Do not mutate the RTextView helper's colors or stroke: its original drawable is
        // restored before host title callbacks, so they always update the real host state.
        java.lang.ref.WeakReference<Drawable> overlay;
        Drawable host;
        void apply(View button, boolean hide) {
            Drawable now = button.getBackground();
            Drawable owned = overlay == null ? null : overlay.get();
            if (hide && (owned == null || now != owned)) {
                host = now;
                ColorDrawable transparent = new ColorDrawable(Color.TRANSPARENT);
                overlay = new java.lang.ref.WeakReference<>(transparent);
                button.setBackground(transparent);
            } else if (!hide && overlay != null) {
                if (owned != null && now == owned) button.setBackground(host);
                overlay = null;
                host = null;
            }
        }
    }
}
