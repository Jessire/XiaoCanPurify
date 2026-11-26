package io.github.jessire.xiaocanpurify;

import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/** Uses Flutter's existing in-process semantics only. Does not enable or alter accessibility. */
final class WithdrawFollowPopup {
    private static final int LIMIT = 700;
    private static final String ACTIVITY = "com.realtech.xiaocan.flutter.MyFlutterActivity";
    private WithdrawFollowPopup() {}

    static void install(XposedInterface xposed, ClassLoader loader) {
        try {
            Class<?> bridge = Class.forName("io.flutter.view.AccessibilityBridge", false, loader);
            Reader reader = new Reader(bridge, loader);
            Method update = bridge.getDeclaredMethod("updateSemantics", ByteBuffer.class, String[].class, ByteBuffer[].class);
            xposed.hook(update).intercept(chain -> {
                Object result = chain.proceed();
                if (Looper.myLooper() == Looper.getMainLooper() && enabled()) {
                    try { reader.afterUpdate(chain.getThisObject()); } catch (Exception ignored) {}
                }
                return result;
            });
            LayoutSettings.addLiveListener(reader::onSettingsSaved);
            MainHook.log("Withdraw follow popup semantic guard installed");
        } catch (ReflectiveOperationException | RuntimeException e) {
            MainHook.log("Withdraw follow popup guard unavailable: " + e.getClass().getSimpleName());
        }
    }

    private static boolean enabled() {
        return LayoutSettings.enabled(LayoutSettings.Item.WITHDRAW_FOLLOW);
    }

    private static final class State {
        long postedGeneration = -1;
        WeakReference<Object> attempted = new WeakReference<>(null);
    }

    private static final class Reader {
        final Field rootView, tree, stack, children, label, tooltip, platformId;
        final Method hasFlag, hasAction, getActivity;
        final Object hidden, routeScope, tap;
        final WeakHashMap<Object, State> states = new WeakHashMap<>();

        Reader(Class<?> bridge, ClassLoader loader) throws ReflectiveOperationException {
            String name = bridge.getName();
            Class<?> node = Class.forName(name + "$SemanticsNode", false, loader);
            Class<?> flag = Class.forName(name + "$Flag", false, loader);
            Class<?> action = Class.forName(name + "$Action", false, loader);
            rootView = field(bridge, "rootAccessibilityView");
            tree = field(bridge, "flutterSemanticsTree");
            stack = field(bridge, "flutterNavigationStack");
            children = field(node, "childrenInTraversalOrder");
            label = field(node, "label");
            tooltip = field(node, "tooltip");
            platformId = field(node, "platformViewId");
            hasFlag = node.getDeclaredMethod("hasFlag", flag);
            hasFlag.setAccessible(true);
            hasAction = node.getDeclaredMethod("hasAction", action);
            hasAction.setAccessible(true);
            hidden = flag.getField("IS_HIDDEN").get(null);
            routeScope = flag.getField("SCOPES_ROUTE").get(null);
            tap = action.getField("TAP").get(null);
            Class<?> utils = Class.forName("io.flutter.util.ViewUtils", false, loader);
            getActivity = utils.getMethod("getActivity", android.content.Context.class);
        }

        private long generation;

        void onSettingsSaved() {
            // An off→on cycle must not revive a click posted before the disable.
            if (!enabled()) generation++;
        }

        void afterUpdate(Object bridge) throws ReflectiveOperationException {
            View view = (View) rootView.get(bridge);
            if (!enabled() || !eligible(view)) return;
            State state = states.computeIfAbsent(bridge, key -> new State());
            long queued = generation;
            if (state.postedGeneration == queued) return;
            WeakReference<View> pendingView = new WeakReference<>(view);
            WeakReference<Object> pendingBridge = new WeakReference<>(bridge);
            // Run after the host's semantics frame and re-read before dispatching.
            state.postedGeneration = queued;
            if (!view.post(() -> {
                if (state.postedGeneration == queued) state.postedGeneration = -1;
                if (!enabled() || generation != queued) return;
                try {
                    View currentView = pendingView.get();
                    Object currentBridge = pendingBridge.get();
                    if (currentBridge == null || !eligible(currentView)
                            || currentView.getAccessibilityNodeProvider() != currentBridge) return;
                    Map<?, ?> map = (Map<?, ?>) tree.get(currentBridge);
                    List<?> routes = (List<?>) stack.get(currentBridge);
                    if (map.size() > LIMIT || routes.size() < 2) return;
                    Object top = routes.get(routes.size() - 1);
                    Object previous = routes.get(routes.size() - 2);
                    if (!(top instanceof Integer) || !(previous instanceof Integer)) return;
                    List<WithdrawFollowPolicy.Node> out = new ArrayList<>();
                    IdentityHashMap<Object, Integer> ids = new IdentityHashMap<>();
                    for (var e : map.entrySet()) {
                        if (!(e.getKey() instanceof Integer)) return;
                        ids.put(e.getValue(), (Integer) e.getKey());
                    }
                    if (!walk(map.get(0), -1, 0, ids, new IdentityHashMap<>(), out)) return;
                    int closeId = WithdrawFollowPolicy.closeId(out, (Integer) top, (Integer) previous);
                    if (closeId <= 0 || closeId >= 65536) return;
                    Object close = map.get(closeId);
                    if (close == null || state.attempted.get() == close) return;
                    if (!enabled() || generation != queued) return;
                    state.attempted = new WeakReference<>(close);
                    if (((AccessibilityNodeProvider) currentBridge).performAction(closeId, AccessibilityNodeInfo.ACTION_CLICK, (Bundle) null)) {
                        MainHook.log("Closed withdraw-success official-account promotion via semantic close button");
                    }
                } catch (Exception ignored) { /* Unknown embedding or incomplete semantics: keep the popup. */ }
            }) && state.postedGeneration == queued) state.postedGeneration = -1;
        }

        private boolean eligible(View view) throws ReflectiveOperationException {
            if (view == null || !view.isAttachedToWindow() || !view.isShown() || !view.hasWindowFocus()) return false;
            Object activity = getActivity.invoke(null, view.getContext());
            return activity instanceof android.app.Activity a && ACTIVITY.equals(a.getClass().getName())
                    && !a.isFinishing() && !a.isDestroyed();
        }

        private boolean walk(Object node, int route, int depth, IdentityHashMap<Object, Integer> ids,
                             IdentityHashMap<Object, Boolean> seen, List<WithdrawFollowPolicy.Node> out)
                throws ReflectiveOperationException {
            if (node == null || depth > 64 || seen.put(node, true) != null) return false;
            if ((Boolean) hasFlag.invoke(node, hidden)) return true;
            Integer id = ids.get(node);
            if (id == null || id < 0 || id >= 65536) return false;
            if (platformId.getInt(node) != -1) return true;
            if ((Boolean) hasFlag.invoke(node, routeScope)) route = id;
            out.add(new WithdrawFollowPolicy.Node(id, route, (String) label.get(node), (String) tooltip.get(node),
                    (Boolean) hasAction.invoke(node, tap)));
            for (Object child : (List<?>) children.get(node)) {
                if (!walk(child, route, depth + 1, ids, seen, out)) return false;
            }
            return true;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
