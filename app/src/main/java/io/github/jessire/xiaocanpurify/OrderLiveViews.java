package io.github.jessire.xiaocanpurify;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Main-thread UI overlays. No host binder, callback or request is replayed on save. */
final class OrderLiveViews {
    enum Size { NONE, VERTICAL, ROW }

    private static final WeakHashMap<View, State> VIEWS = new WeakHashMap<>();
    private static final Runnable REFRESH = OrderLiveViews::refresh;
    private static boolean listening;

    private OrderLiveViews() {}

    static synchronized void install() {
        if (listening) return;
        try {
            // Optional on the old settings baseline; the merged settings register this once.
            Method method = LayoutSettings.class.getDeclaredMethod("addLiveListener", Runnable.class);
            method.invoke(null, REFRESH);
            listening = true;
        } catch (NoSuchMethodException ignored) {
            // Existing hooks still read the setting at their normal entry points.
        } catch (ReflectiveOperationException e) {
            MainHook.log("Order live listener unavailable: " + e.getClass().getSimpleName());
        }
    }

    static View asView(Object object) {
        return object instanceof View ? (View) object : null;
    }

    static Object getField(Object object, String name) {
        if (object == null) return null;
        try { return object.getClass().getField(name).get(object); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    static Object callGetter(Object object, String name) {
        if (object == null) return null;
        try { return object.getClass().getMethod(name).invoke(object); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    static void track(View view, BooleanSupplier hide, Size size) {
        State state = state(view, hide, size);
        if (state != null) state.apply(view);
    }

    static void skipped(View view, BooleanSupplier hide, Size size) {
        State state = state(view, hide, size);
        if (state == null) return;
        // The view may contain old content or an empty XML placeholder. Do not expose
        // it after unblocking until a real bind has supplied current content.
        state.awaitingBind = true;
        state.apply(view);
    }

    static void beforeBind(View view) {
        State state = VIEWS.get(view);
        if (state == null) return;
        state.awaitingBind = false;
        state.restore(view);
    }

    static void suspend(View view) {
        State state = VIEWS.get(view);
        if (state != null && !state.awaitingBind) state.restore(view);
    }

    static void watchLayout(View view) {
        State state = VIEWS.get(view);
        if (state == null || state.watcher != null) return;
        try {
            state.watcher = new LayoutWatcher(view);
            view.addOnAttachStateChangeListener(state.watcher);
            if (view.isAttachedToWindow()) state.watcher.onViewAttachedToWindow(view);
        } catch (NoClassDefFoundError | NoSuchMethodError ignored) {
            // A reduced View stub may not expose attach APIs; save and bind still apply.
        }
    }

    private static State state(View view, BooleanSupplier hide, Size size) {
        if (view == null) return null;
        State state = VIEWS.get(view);
        if (state == null) {
            state = new State(hide, size);
            VIEWS.put(view, state);
        }
        return state;
    }

    private static void refresh() {
        // A View can cause layout callbacks when it changes. Don't iterate the live map.
        for (View view : new ArrayList<>(VIEWS.keySet())) {
            State state = VIEWS.get(view);
            if (state != null) state.apply(view);
        }
    }

    private static final class LayoutWatcher implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnGlobalLayoutListener {
        private final WeakReference<View> view;
        private WeakReference<ViewTreeObserver> observer;

        LayoutWatcher(View view) { this.view = new WeakReference<>(view); }

        @Override public void onViewAttachedToWindow(View attached) {
            onViewDetachedFromWindow(attached);
            ViewTreeObserver tree = attached.getViewTreeObserver();
            observer = new WeakReference<>(tree);
            tree.addOnGlobalLayoutListener(this);
            onGlobalLayout();
        }

        @Override public void onViewDetachedFromWindow(View detached) {
            ViewTreeObserver tree = observer == null ? null : observer.get();
            if (tree != null && tree.isAlive()) tree.removeOnGlobalLayoutListener(this);
            observer = null;
        }

        @Override public void onGlobalLayout() {
            View target = view.get();
            if (target == null) return;
            State state = VIEWS.get(target);
            if (state != null) state.apply(target);
        }
    }

    private static final class State {
        final BooleanSupplier hide;
        final Size size;
        boolean awaitingBind;
        int visibility = View.GONE;
        Geometry geometry;
        LayoutWatcher watcher;

        State(BooleanSupplier hide, Size size) {
            this.hide = hide;
            this.size = size;
        }

        void apply(View view) {
            if (!awaitingBind && !hide.getAsBoolean()) {
                restore(view);
                return;
            }
            int current = view.getVisibility();
            if (current != View.GONE) {
                visibility = current;
                view.setVisibility(View.GONE);
            }
            if (size == Size.NONE) return;
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params == null) {
                geometry = null;
                return;
            }
            if (geometry == null || geometry.params.get() != params) geometry = new Geometry(params);
            geometry.update(view, params, size, false);
        }

        void restore(View view) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (geometry != null && params != null && geometry.params.get() == params) {
                geometry.update(view, params, size, true);
            }
            geometry = null;
            if (visibility != View.GONE && view.getVisibility() == View.GONE) view.setVisibility(visibility);
            visibility = View.GONE;
        }
    }

    private static final class Geometry {
        final WeakReference<ViewGroup.LayoutParams> params;
        int width, height, left, top, right, bottom;

        Geometry(ViewGroup.LayoutParams params) { this.params = new WeakReference<>(params); }

        void update(View view, ViewGroup.LayoutParams lp, Size size, boolean restore) {
            boolean changed = false;
            if (restore ? lp.height == 0 && height != 0 : lp.height != 0) {
                if (!restore) height = lp.height;
                lp.height = restore ? height : 0;
                changed = true;
            }
            if (size == Size.ROW && (restore ? lp.width == 0 && width != 0 : lp.width != 0)) {
                if (!restore) width = lp.width;
                lp.width = restore ? width : 0;
                changed = true;
            }
            if (lp instanceof ViewGroup.MarginLayoutParams p) {
                if (restore ? p.topMargin == 0 && top != 0 : p.topMargin != 0) {
                    if (!restore) top = p.topMargin;
                    p.topMargin = restore ? top : 0;
                    changed = true;
                }
                if (restore ? p.bottomMargin == 0 && bottom != 0 : p.bottomMargin != 0) {
                    if (!restore) bottom = p.bottomMargin;
                    p.bottomMargin = restore ? bottom : 0;
                    changed = true;
                }
                if (size == Size.ROW) {
                    if (restore ? p.leftMargin == 0 && left != 0 : p.leftMargin != 0) {
                        if (!restore) left = p.leftMargin;
                        p.leftMargin = restore ? left : 0;
                        changed = true;
                    }
                    if (restore ? p.rightMargin == 0 && right != 0 : p.rightMargin != 0) {
                        if (!restore) right = p.rightMargin;
                        p.rightMargin = restore ? right : 0;
                        changed = true;
                    }
                }
            }
            if (changed) view.setLayoutParams(lp);
        }
    }
}
