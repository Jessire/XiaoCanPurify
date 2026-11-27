package io.github.jessire.xiaocanpurify;

import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.HOME_SEARCH_HINT;
import static io.github.jessire.xiaocanpurify.LayoutSettings.enabled;

import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/** Rebinds the view's last local list, never HomeViewModel.refreshSearchWord. */
final class HomeLiveSearchHint {
    private static final WeakHashMap<View, State> VIEWS = new WeakHashMap<>();
    private static Method setter;
    private static Field animating, next, current;
    private static List<?> placeholder;

    private HomeLiveSearchHint() {}

    static boolean install(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> type = Class.forName("com.realtech.promotion.widget.AutoScrollTextView", false, cl);
            Class<?> word = Class.forName("com.realtech.data.placement.content.PlacementSearchWord", false, cl);
            placeholder = Collections.singletonList(placeholderWord(word, cl));
            animating = declared(type, "isAnimating");
            current = declared(type, "currentTextView");
            next = declared(type, "nextTextView");
            setter = type.getDeclaredMethod("setTextList", List.class);
            xposed.hook(setter).intercept(chain -> {
                if (!HomeLiveViews.mainThread()) return chain.proceed();
                View view = (View) chain.getThisObject();
                State state = state(view);
                if (state.rebinding || !(chain.getArg(0) instanceof List<?>)) return chain.proceed();
                state.words = new ArrayList<>((List<?>) chain.getArg(0));
                boolean hide = enabled(HOME_SEARCH_HINT);
                Object result = hide ? chain.proceed(new Object[]{placeholder}) : chain.proceed();
                finishBind(view, state, hide);
                return result;
            });
            return true;
        } catch (Throwable t) {
            MainHook.log("Home search hint live hook unavailable: " + t);
            setter = null;
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object placeholderWord(Class<?> word, ClassLoader cl) throws ReflectiveOperationException {
        try {
            return word.getConstructor(String.class, String.class).newInstance("搜索商家或套餐", "#999999");
        } catch (NoSuchMethodException e) {
            // 3.23.0: (word, color, tag, storeId, SearchWordType)
            Class<? extends Enum> type = (Class<? extends Enum>) Class.forName(
                    "com.realtech.data.placement.content.SearchWordType", false, cl);
            return word.getConstructor(String.class, String.class, String.class, Long.class, type)
                    .newInstance("搜索商家或套餐", "#999999", null, null, Enum.valueOf(type, "OTHER"));
        }
    }

    private static Field declared(Class<?> type, String name) throws ReflectiveOperationException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static State state(View view) {
        State state = VIEWS.get(view);
        if (state == null) {
            state = new State();
            try { state.emptyText = ((TextView) current.get(view)).getText(); }
            catch (ReflectiveOperationException | RuntimeException ignored) {}
            VIEWS.put(view, state);
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { apply(v); }
                @Override public void onViewDetachedFromWindow(View v) {}
            });
        }
        return state;
    }

    static void track(Object value) {
        if (setter == null || !setter.getDeclaringClass().isInstance(value) || !HomeLiveViews.mainThread()) return;
        View view = (View) value;
        state(view);
        apply(view);
    }

    static void refresh() {
        for (View view : new ArrayList<>(VIEWS.keySet())) {
            if (view != null && view.isAttachedToWindow()) apply(view);
        }
    }

    private static void apply(View view) {
        State state = VIEWS.get(view);
        if (state == null) return;
        boolean hide = enabled(HOME_SEARCH_HINT);
        if (hide == state.hidden) return;
        try {
            // A detached AutoScrollTextView relies on its own attach callback to start timers.
            if (!view.isAttachedToWindow()) return;
            state.rebinding = true;
            setter.invoke(view, hide ? placeholder : state.words);
            finishBind(view, state, hide);
        } catch (ReflectiveOperationException | RuntimeException e) {
            MainHook.log("Home search hint local rebind failed: " + e.getClass().getSimpleName());
        } finally {
            state.rebinding = false;
        }
    }

    private static void finishBind(View view, State state, boolean hide) {
        try {
            TextView front = (TextView) current.get(view);
            // setTextList(empty) retains the last text. Do not leave the module's placeholder.
            if (!hide && state.hidden && state.words.isEmpty()) front.setText(state.emptyText);
            if (!hide) state.emptyText = front.getText();
            // An animation started before the bind copies nextTextView into currentTextView on
            // completion. Update both faces to prevent the old hint (or placeholder) resurfacing.
            if (hide || animating.getBoolean(view)) ((TextView) next.get(view)).setText(front.getText());
            state.hidden = hide;
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    private static final class State {
        List<?> words = Collections.emptyList();
        CharSequence emptyText = "";
        boolean hidden, rebinding;
    }
}
