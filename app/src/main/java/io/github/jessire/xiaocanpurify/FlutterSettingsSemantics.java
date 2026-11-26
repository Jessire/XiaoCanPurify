package io.github.jessire.xiaocanpurify;

import android.graphics.Rect;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

/** Read-only adapter for the exact Flutter embedding bundled in host 3.21.2. */
final class FlutterSettingsSemantics {
    private static final int CONTROL_ACTIONS = 1 | 2 | 64 | 128 | 131072 | 262144;
    private static final int VERTICAL_SCROLL = 16 | 32;
    private static final long HIDDEN = 8192, SLIDER = 8388608, TEXT_FIELD = 16;
    private final Field bridge, root, tree, channel, rect, label, parent, flags, actions, platform, scrollPosition;
    private final Method update, createNode, enable, disable;

    FlutterSettingsSemantics(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> view = Class.forName("io.flutter.embedding.android.FlutterView", false, loader);
        Class<?> b = Class.forName("io.flutter.view.AccessibilityBridge", false, loader);
        Class<?> n = Class.forName("io.flutter.view.AccessibilityBridge$SemanticsNode", false, loader);
        bridge = field(view, "accessibilityBridge");
        root = field(b, "rootAccessibilityView");
        tree = field(b, "flutterSemanticsTree");
        channel = field(b, "accessibilityChannel");
        rect = field(n, "globalRect");
        label = field(n, "label");
        parent = field(n, "parent");
        flags = field(n, "flags");
        actions = field(n, "actions");
        platform = field(n, "platformViewId");
        scrollPosition = field(n, "scrollPosition");
        update = b.getDeclaredMethod("updateSemantics", ByteBuffer.class, String[].class, ByteBuffer[].class);
        createNode = b.getDeclaredMethod("createAccessibilityNodeInfo", int.class);
        enable = channel.getType().getMethod("onAndroidAccessibilityEnabled");
        disable = channel.getType().getMethod("onAndroidAccessibilityDisabled");
    }

    void install(XposedInterface xposed) {
        xposed.hook(update).intercept(chain -> {
            Object result = chain.proceed();
            FlutterSettingsRow view = owner(chain.getThisObject());
            if (view != null) view.semanticsChanged();
            return result;
        });
        xposed.hook(createNode).intercept(chain -> {
            Object result = chain.proceed();
            FlutterSettingsRow view = owner(chain.getThisObject());
            if (view != null && result instanceof AccessibilityNodeInfo info) {
                view.adjustNode((int) chain.getArg(0), info);
            }
            return result;
        });
    }

    private FlutterSettingsRow owner(Object b) {
        try {
            if (root.get(b) instanceof View view && view.getParent() instanceof FlutterSettingsRow owner
                    && bridge.get(view) == b) return owner;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // The host return value must remain intact.
        }
        return null;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    boolean isTextureOnly(View view) {
        return view instanceof ViewGroup group && group.getChildCount() == 1
                && group.getChildAt(0) instanceof TextureView;
    }

    Object bridge(View view) throws ReflectiveOperationException { return bridge.get(view); }

    void setEngineSemantics(Object b, boolean enabled) throws ReflectiveOperationException {
        (enabled ? enable : disable).invoke(channel.get(b));
    }

    private boolean isLabel(Object node, String expected) throws ReflectiveOperationException {
        return label.get(node) instanceof String text
                && (expected.equals(text) || text.startsWith(expected + "\n"));
    }

    private boolean visible(Object node) throws ReflectiveOperationException {
        int depth = 0;
        for (; node != null && depth < 80; node = parent.get(node), depth++) {
            if ((flags.getLong(node) & HIDDEN) != 0) return false;
        }
        return node == null;
    }

    private boolean inside(Object node, Object ancestor) throws ReflectiveOperationException {
        for (int i = 0; node != null && i < 80; node = parent.get(node), i++) {
            if (node == ancestor) return true;
        }
        return false;
    }

    private Object scrollAncestor(Object node) throws ReflectiveOperationException {
        for (int i = 0; node != null && i < 80; node = parent.get(node), i++) {
            if ((actions.getInt(node) & VERTICAL_SCROLL) != 0 && Float.isFinite(scrollPosition.getFloat(node))) return node;
        }
        return null;
    }

    private boolean actionable(Object node) throws ReflectiveOperationException {
        return (actions.getInt(node) & CONTROL_ACTIONS) != 0
                || (flags.getLong(node) & (SLIDER | TEXT_FIELD)) != 0;
    }

    private Rect bounds(Object node) throws ReflectiveOperationException {
        return node == null ? null : (Rect) rect.get(node);
    }

    boolean hasViewportSize(Object b, int width, int height) throws ReflectiveOperationException {
        Rect page = bounds(((Map<?, ?>) tree.get(b)).get(0));
        return page != null && Math.abs(page.height() - height) <= 2 && Math.abs(page.width() - width) <= 2;
    }

    record Anchor(Object viewport, SettingsRowGeometry geometry) {}

    Anchor read(Object b, Object knownViewport, int width, int height, int rowHeight)
            throws ReflectiveOperationException {
        Map<?, ?> nodes = (Map<?, ?>) tree.get(b);
        if (nodes.size() > 1024) return null;
        Rect page = bounds(nodes.get(0));
        if (page == null || Math.abs(page.height() - height) > 2 || Math.abs(page.width() - width) > 2) return null;
        Object heading = null, phone = null, title = null, logout = null;
        int headingId = -1, phoneId = -1;
        for (Map.Entry<?, ?> e : nodes.entrySet()) {
            Object n = e.getValue();
            Rect r = bounds(n);
            if (r == null || r.isEmpty() || !visible(n)) continue;
            if (platform.getInt(n) != -1) return null; // no independently composited Android children
            if (isLabel(n, "基础设置")) {
                if (heading != null) return null;
                heading = n;
                headingId = (int) e.getKey();
            } else if (isLabel(n, "手机号码")) {
                if (phone != null) return null;
                phone = n;
                phoneId = (int) e.getKey();
            } else if (isLabel(n, "设置")) {
                if (title != null) return null;
                title = n;
            } else if (isLabel(n, "退出登录")) {
                if (logout != null) return null;
                logout = n;
            }
        }
        if (title == null || logout == null) return null;
        Object viewport = heading != null ? scrollAncestor(heading)
                : phone != null ? scrollAncestor(phone) : knownViewport;
        Rect area = bounds(viewport);
        if (area == null || !visible(viewport) || inside(logout, viewport) || inside(title, viewport)
                || !nodes.containsValue(viewport) || area.top < 0 || area.bottom > height
                || area.height() < rowHeight || bounds(title).bottom > area.top
                || bounds(logout).top < area.bottom) return null;

        int seam = -1, left = 0, right = 0;
        if (heading != null && phone != null && inside(phone, viewport)) {
            Object hit = phone;
            while (hit != null && hit != viewport && !actionable(hit)) hit = parent.get(hit);
            Rect first = hit == viewport ? null : bounds(hit);
            Rect header = bounds(heading);
            if (first != null) {
                seam = SettingsRowGeometry.findSeam(header.bottom, first.top, area.top, area.bottom);
                if (seam >= 0 && header.top >= area.top) {
                    // Preserve all the section heading's background/padding above the entry.
                    seam = first.top;
                    left = Math.max(Math.max(0, first.left), area.left);
                    right = Math.min(Math.min(width, first.right), area.right);
                }
            }
        }
        if (left >= right) {
            // When the heading scrolls out, put the unused reserve after the list, before logout.
            // Do not invent an entry from another node or from geometry alone.
            if (knownViewport != viewport) return null;
            seam = area.bottom;
            left = right = 0;
        }
        boolean dragFromHeading = left < right;
        for (Object n : nodes.values()) {
            if (!actionable(n) || !visible(n)) continue;
            Rect r = bounds(n);
            if (r == null || r.isEmpty()) continue;
            int top = r.top, bottom = r.bottom;
            if (inside(n, viewport) && n != viewport) {
                top = Math.max(top, area.top);
                bottom = Math.min(bottom, area.bottom);
            }
            // Modal barriers and merged control bounds can straddle the seam. Fail closed.
            if (top < seam && seam < bottom) return null;
            if (r.left < right && left < r.right && top <= seam - 1 && seam - 1 < bottom) {
                dragFromHeading = false;
            }
        }
        return new Anchor(viewport, new SettingsRowGeometry(seam, rowHeight, left, right,
                headingId, phoneId, dragFromHeading));
    }

    void adjust(Object b, Object viewport, View view, View row, SettingsRowGeometry g, int id, AccessibilityNodeInfo info)
            throws ReflectiveOperationException {
        if (id < 0) return;
        Object node = ((Map<?, ?>) tree.get(b)).get(id);
        Rect r = bounds(node);
        if (r == null || r.isEmpty()) return;
        Rect area = bounds(viewport);
        if (node != viewport && area != null && inside(node, viewport)) {
            r = new Rect(r);
            if (!r.intersect(area)) {
                info.setVisibleToUser(false);
                return;
            }
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        info.setBoundsInScreen(new Rect(r.left + location[0], g.mapTop(r.top) + location[1],
                r.right + location[0], g.mapBottom(r.bottom) + location[1]));
        Rect p = bounds(parent.get(node));
        int x = p == null ? 0 : p.left, y = p == null ? 0 : g.mapTop(p.top);
        info.setBoundsInParent(new Rect(r.left - x, g.mapTop(r.top) - y, r.right - x, g.mapBottom(r.bottom) - y));
        if (g.showsRow() && id == g.phone()) info.setTraversalAfter(row);
    }
}
