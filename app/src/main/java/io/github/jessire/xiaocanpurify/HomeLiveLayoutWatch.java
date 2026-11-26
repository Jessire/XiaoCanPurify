package io.github.jessire.xiaocanpurify;

import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Scope the old home layout pass to the attached fragment view, not the Activity window. */
final class HomeLiveLayoutWatch implements View.OnAttachStateChangeListener,
        ViewTreeObserver.OnGlobalLayoutListener {
    private static final WeakHashMap<View, HomeLiveLayoutWatch> ROOTS = new WeakHashMap<>();

    private final WeakReference<Object> binding;
    private final Consumer<Object> layout;
    private WeakReference<ViewTreeObserver> observer;

    private HomeLiveLayoutWatch(Object binding, Consumer<Object> layout) {
        this.binding = new WeakReference<>(binding);
        this.layout = layout;
    }

    static void attach(View root, Object binding, Consumer<Object> layout) {
        HomeLiveLayoutWatch watch = new HomeLiveLayoutWatch(binding, layout);
        ROOTS.put(root, watch);
        root.addOnAttachStateChangeListener(watch);
        if (root.isAttachedToWindow()) watch.onViewAttachedToWindow(root);
    }

    static void invalidateLayouts() {
        for (View root : new java.util.ArrayList<>(ROOTS.keySet())) {
            if (root != null && root.isAttachedToWindow()) root.requestLayout();
        }
    }

    @Override public void onViewAttachedToWindow(View view) {
        onViewDetachedFromWindow(view);
        ViewTreeObserver tree = view.getViewTreeObserver();
        observer = new WeakReference<>(tree);
        tree.addOnGlobalLayoutListener(this);
        onGlobalLayout();
    }

    @Override public void onViewDetachedFromWindow(View view) {
        ViewTreeObserver tree = observer == null ? null : observer.get();
        if (tree != null && tree.isAlive()) tree.removeOnGlobalLayoutListener(this);
        observer = null;
    }

    @Override public void onGlobalLayout() {
        Object value = binding.get();
        if (value != null) layout.accept(value);
    }
}
