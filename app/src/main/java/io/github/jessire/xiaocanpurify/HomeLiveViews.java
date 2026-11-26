package io.github.jessire.xiaocanpurify;

import static io.github.jessire.xiaocanpurify.LayoutSettings.enabled;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

import io.github.libxposed.api.XposedInterface;

/** Main-thread-only overlays. Values in the map never retain the view (or its owner). */
final class HomeLiveViews {
    private static final WeakHashMap<View, State> VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, Padding> PADDINGS = new WeakHashMap<>();
    private static boolean installed;

    private HomeLiveViews() {}

    static boolean install(XposedInterface xposed) {
        if (installed) return true;
        try {
            // Host writes of GONE while already masked still matter. A layout listener cannot
            // distinguish one from our own GONE; intercept only writes to exact registered views.
            xposed.hook(View.class.getDeclaredMethod("setVisibility", int.class)).intercept(chain -> {
                if (mainThread() && !VIEWS.isEmpty()) {
                    State state = VIEWS.get(chain.getThisObject());
                    if (state != null && !state.writing) {
                        int requested = (Integer) chain.getArg(0);
                        if (state.visibility.hostWrite(requested, state.hide.getAsBoolean())) {
                            return chain.proceed(new Object[]{View.GONE});
                        }
                    }
                }
                return chain.proceed();
            });
            installed = true;
        } catch (Throwable t) {
            MainHook.log("Home live visibility unavailable: " + t);
        }
        return installed;
    }

    static boolean mainThread() { return Looper.myLooper() == Looper.getMainLooper(); }

    static void track(View view, LayoutSettings.Item item) {
        track(view, () -> enabled(item), false, false);
    }

    static void track(View view, BooleanSupplier hide, boolean zeroHeight, boolean zeroMinimum) {
        if (view == null || !mainThread()) return;
        State state = VIEWS.get(view);
        if (state == null) {
            state = new State(view, hide, zeroHeight, zeroMinimum);
            VIEWS.put(view, state);
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    State current = VIEWS.get(v);
                    if (current != null) current.apply(v);
                }
                @Override public void onViewDetachedFromWindow(View v) {}
            });
        }
        state.apply(view);
    }

    static void refresh() {
        for (View view : new ArrayList<>(VIEWS.keySet())) {
            State state = VIEWS.get(view);
            if (view != null && state != null && view.isAttachedToWindow()) state.apply(view);
        }
        if (!HomePagePurifier.collapseWholeHeader()) {
            for (View view : new ArrayList<>(PADDINGS.keySet())) restorePadding(view);
        } else {
            // Recompute the gap from current measured coordinates on the next layout.
            for (View view : new ArrayList<>(PADDINGS.keySet())) if (view != null) view.requestLayout();
        }
    }

    // The compatibility pass adjusts just top padding and clipToPadding, never the other edges.
    static void pinPadding(View rv, int top) {
        Padding p = PADDINGS.get(rv);
        if (p == null) {
            p = new Padding(rv.getPaddingTop(), rv instanceof ViewGroup && ((ViewGroup) rv).getClipToPadding());
            PADDINGS.put(rv, p);
        } else if (rv.getPaddingTop() != p.appliedTop) {
            p.hostTop = rv.getPaddingTop();
        }
        p.appliedTop = top;
        setTopPadding(rv, top);
        if (rv instanceof ViewGroup && !((ViewGroup) rv).getClipToPadding()) {
            p.hostClip = false;
            ((ViewGroup) rv).setClipToPadding(true);
        }
    }

    static void restorePadding(View rv) {
        if (rv == null) return;
        Padding p = PADDINGS.remove(rv);
        if (p == null) return;
        if (rv.getPaddingTop() == p.appliedTop) setTopPadding(rv, p.hostTop);
        if (rv instanceof ViewGroup && ((ViewGroup) rv).getClipToPadding()) {
            ((ViewGroup) rv).setClipToPadding(p.hostClip);
        }
    }

    private static void setTopPadding(View view, int value) {
        if (view.getPaddingTop() == value) return;
        if (view.isPaddingRelative()) {
            view.setPaddingRelative(view.getPaddingStart(), value, view.getPaddingEnd(), view.getPaddingBottom());
        } else {
            view.setPadding(view.getPaddingLeft(), value, view.getPaddingRight(), view.getPaddingBottom());
        }
    }

    private static final class Padding {
        int hostTop, appliedTop;
        boolean hostClip;
        Padding(int top, boolean clip) { hostTop = appliedTop = top; hostClip = clip; }
    }

    private static final class State {
        final BooleanSupplier hide;
        final HomeLiveVisibility visibility;
        final boolean zeroHeight, zeroMinimum;
        boolean writing, heightOwned, minimumOwned;
        int hostHeight, hostMinimum;
        ViewGroup.LayoutParams lastParams;

        State(View view, BooleanSupplier hide, boolean zeroHeight, boolean zeroMinimum) {
            this.hide = hide;
            this.visibility = new HomeLiveVisibility(view.getVisibility(), View.GONE);
            this.zeroHeight = zeroHeight;
            this.zeroMinimum = zeroMinimum;
        }

        void apply(View view) {
            boolean hidden = hide.getAsBoolean();
            int desired = visibility.apply(view.getVisibility(), hidden);
            if (desired != view.getVisibility()) {
                writing = true;
                try { view.setVisibility(desired); }
                finally { writing = false; }
            }
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (zeroHeight && params != null) {
                if (hidden) {
                    if (!heightOwned || lastParams != params || params.height != 0) {
                        hostHeight = params.height;
                        lastParams = params;
                        heightOwned = true;
                    }
                    if (params.height != 0) { params.height = 0; view.setLayoutParams(params); }
                } else if (heightOwned && lastParams == params && params.height == 0 && hostHeight != 0) {
                    params.height = hostHeight;
                    view.setLayoutParams(params);
                }
            }
            if (!hidden) { heightOwned = false; lastParams = null; }
            if (zeroMinimum) {
                int value = view.getMinimumHeight();
                if (hidden) {
                    if (!minimumOwned || value != 0) hostMinimum = value;
                    minimumOwned = true;
                    if (value != 0) view.setMinimumHeight(0);
                } else if (minimumOwned) {
                    if (value == 0 && hostMinimum != 0) view.setMinimumHeight(hostMinimum);
                    minimumOwned = false;
                }
            }
        }
    }
}
