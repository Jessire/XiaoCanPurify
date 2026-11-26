package io.github.jessire.xiaocanpurify;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Main-thread registry. Neither keys' owners nor bindings/layout instances are retained. */
final class ProfileLiveViews {
    private final WeakHashMap<View, Entry> entries = new WeakHashMap<>();

    Entry track(View view, LayoutSettings.Item item) {
        if (view == null) return null;
        Entry entry = entries.get(view);
        if (entry == null) {
            entry = new Entry(view, item);
            entries.put(view, entry);
        }
        return entry;
    }

    Entry find(View view) { return entries.get(view); }

    void refresh() {
        for (Entry entry : new ArrayList<>(entries.values())) entry.refresh();
    }

    static final class Entry {
        private final WeakReference<View> view;
        private final LayoutSettings.Item item;
        private int depth;
        private int hostVisibility;
        private boolean masked;
        private boolean carousel;
        private ProfileLiveVipLayout vipLayout;

        Entry(View view, LayoutSettings.Item item) {
            this.view = new WeakReference<>(view);
            this.item = item;
        }

        void carousel() { carousel = true; }

        boolean blocksCarouselStart() { return carousel && LayoutSettings.enabled(item); }

        void vipLayout() { if (vipLayout == null) vipLayout = new ProfileLiveVipLayout(); }

        // Give a normal host render its own visibility, even for renderers that read it.
        // Nesting is needed for e.g. a module render that synchronously calls hide().
        void begin() {
            if (depth++ == 0 && masked) {
                View target = view.get();
                if (target != null && target.getVisibility() == View.GONE)
                    target.setVisibility(hostVisibility);
            }
        }

        void end() {
            if (--depth != 0) return;
            boolean wasMasked = masked;
            masked = false;
            apply(wasMasked);
        }

        void refresh() { if (depth == 0) apply(masked); }

        private void apply(boolean wasMasked) {
            View target = view.get();
            if (target == null) return;
            boolean enabled = LayoutSettings.enabled(item);
            if (vipLayout != null) {
                vipLayout.apply(target, enabled);
                return;
            }
            if (enabled) {
                int current = target.getVisibility();
                if (!masked || current != View.GONE) hostVisibility = current;
                masked = true;
                if (current != View.GONE) target.setVisibility(View.GONE);
                if (carousel && !wasMasked) invoke(target, "stop");
            } else {
                if (masked && target.getVisibility() == View.GONE) target.setVisibility(hostVisibility);
                masked = false;
                if (carousel && wasMasked && target.getVisibility() == View.VISIBLE && target.isAttachedToWindow())
                    invoke(target, "start");
            }
        }

        private static void invoke(View target, String name) {
            try { target.getClass().getMethod(name).invoke(target); }
            catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
    }
}
