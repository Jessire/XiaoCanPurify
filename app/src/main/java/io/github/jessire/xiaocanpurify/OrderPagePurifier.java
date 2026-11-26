package io.github.jessire.xiaocanpurify;

import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.*;
import static io.github.jessire.xiaocanpurify.LayoutSettings.enabled;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

public final class OrderPagePurifier {
    private static final ThreadLocal<Boolean> RENDERING_HEADER = new ThreadLocal<>();
    private static final LayoutSettings.Item[] HEADER_RULES = {
            ORDER_CHALLENGE, ORDER_CHALLENGE, ORDER_INVITE, ORDER_INVITE, ORDER_TIP
    };

    private OrderPagePurifier() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        OrderLiveViews.install();
        hookOrderTypeFragment(xposed, classLoader);
        hookOrderListFragment(xposed, classLoader);
        hookHolder(xposed, classLoader, "OrderBannerHolder", ORDER_FEED, 6);
        hookHolder(xposed, classLoader, "OrderNativeadHolder", ORDER_AD, 2);
        hookRewardButtons(xposed, classLoader);
    }

    private static void hookOrderTypeFragment(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> fragment = Class.forName("com.realtech.my_order.ui.OrderTypeFragment", false, cl);
            for (Method method : fragment.getDeclaredMethods()) {
                String name = method.getName();
                int count = method.getParameterCount();
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                boolean sticky = !isStatic && name.equals("renderStickyBar") && count == 2;
                boolean invite = !isStatic && name.equals("initFreeOrderInviteView") && count == 1;
                boolean tipsObserver = isStatic && name.equals("initData$lambda$4") && count == 2;
                boolean observesHeader = !isStatic && ((name.equals("lazyInitView") && count == 0)
                        || (name.equals("onViewCreated") && count == 2)
                        || (name.equals("restoreOrderTips") && count == 0)
                        || (name.equals("bindOrderCompetitorChallenge") && count == 1)
                        || (name.equals("applyOrderStickyMutex") && count == 0));
                if (!sticky && !invite && !tipsObserver && !observesHeader) continue;
                if ((sticky || invite) && method.getReturnType() != void.class) continue;
                xposed.hook(method).intercept(chain -> {
                    Object receiver = tipsObserver ? chain.getArg(0) : chain.getThisObject();
                    View[] views = headerViews(sticky ? chain.getArg(0)
                            : OrderLiveViews.callGetter(receiver, "getBinding"));
                    if (sticky && enabled(ORDER_CHALLENGE)) {
                        OrderLiveViews.skipped(views[0], () -> enabled(ORDER_CHALLENGE), OrderLiveViews.Size.NONE);
                        return null;
                    }
                    if (invite && enabled(ORDER_INVITE)) {
                        for (int i = 2; i <= 3; i++) {
                            OrderLiveViews.skipped(views[i], () -> enabled(ORDER_INVITE), OrderLiveViews.Size.NONE);
                        }
                        return null;
                    }
                    // The stock header checks other views' visibility to choose its own.
                    // Suspend our overlays during the normal render, including nested callbacks.
                    boolean outer = RENDERING_HEADER.get() == null;
                    if (outer) {
                        RENDERING_HEADER.set(true);
                        for (View view : views) OrderLiveViews.suspend(view);
                    }
                    if (sticky) OrderLiveViews.beforeBind(views[0]);
                    if (invite) {
                        OrderLiveViews.beforeBind(views[2]);
                        OrderLiveViews.beforeBind(views[3]);
                    }
                    try {
                        return chain.proceed();
                    } finally {
                        if (outer) {
                            RENDERING_HEADER.remove();
                            for (int i = 0; i < views.length; i++) {
                                LayoutSettings.Item rule = HEADER_RULES[i];
                                OrderLiveViews.track(views[i], () -> enabled(rule), OrderLiveViews.Size.NONE);
                            }
                        }
                    }
                });
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook OrderTypeFragment: " + t);
        }
    }

    private static View[] headerViews(Object binding) {
        Object mini = OrderLiveViews.getField(binding, "layoutChallengeCardOrder");
        return new View[]{fieldView(binding, "clMachallenge"),
                OrderLiveViews.asView(OrderLiveViews.callGetter(mini, "getRoot")),
                fieldView(binding, "llFreeOrder"), fieldView(binding, "ivFreeOrderTag"),
                fieldView(binding, "ivTips")};
    }

    private static View fieldView(Object binding, String name) {
        return OrderLiveViews.asView(OrderLiveViews.getField(binding, name));
    }

    private static void hookOrderListFragment(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> fragment = Class.forName("com.realtech.my_order.ui.OrderListFragment", false, cl);
            for (Method method : fragment.getDeclaredMethods()) {
                if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())) continue;
                LayoutSettings.Item item;
                switch (method.getName()) {
                    case "renderFeedImage": item = ORDER_FEED; break;
                    case "renderNativeAd": item = ORDER_AD; break;
                    case "renderChallenge": item = ORDER_CHALLENGE; break;
                    case "renderOrderTip": item = ORDER_TIP; break;
                    case "showMaChallengePopUp": // Existing popup policy is not a layout option.
                        xposed.hook(method).intercept(chain -> null);
                        continue;
                    default: continue;
                }
                xposed.hook(method).intercept(chain -> enabled(item) ? null : chain.proceed());
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook OrderListFragment: " + t);
        }
    }

    private static void hookHolder(XposedInterface xposed, ClassLoader cl, String name,
                                   LayoutSettings.Item rule, int parameterCount) {
        try {
            Class<?> holder = Class.forName("com.realtech.my_order.adapter.holder." + name, false, cl);
            for (Method method : holder.getDeclaredMethods()) {
                if (!method.getName().equals("bind") || method.isBridge() || Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != void.class || method.getParameterCount() != parameterCount) continue;
                xposed.hook(method).intercept(chain -> {
                    Object binding = OrderLiveViews.callGetter(chain.getThisObject(), "getBinding");
                    View root = OrderLiveViews.asView(OrderLiveViews.callGetter(binding, "getRoot"));
                    if (enabled(rule)) {
                        OrderLiveViews.skipped(root, () -> enabled(rule), OrderLiveViews.Size.ROW);
                        return null;
                    }
                    OrderLiveViews.beforeBind(root);
                    Object result = chain.proceed();
                    OrderLiveViews.track(root, () -> enabled(rule), OrderLiveViews.Size.ROW);
                    return result;
                });
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook " + name + ": " + t);
        }
    }

    private static void hookRewardButtons(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> holder = Class.forName("com.realtech.my_order.adapter.holder.OrderNormalHolder", false, cl);
            for (Method method : holder.getDeclaredMethods()) {
                if (!method.getName().equals("bindActionButtons") || method.getParameterCount() != 2
                        || method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())
                        || !ViewGroup.class.isAssignableFrom(method.getParameterTypes()[0])
                        || method.getParameterTypes()[1] != List.class) continue;
                xposed.hook(method).intercept(chain -> {
                    if (!(chain.getArg(0) instanceof ViewGroup container)
                            || !(chain.getArg(1) instanceof List<?> actions)) return chain.proceed();
                    if (enabled(ORDER_REWARD)) {
                        List<Object> filtered = new ArrayList<>();
                        for (Object action : actions) {
                            if (!isReward(action)) filtered.add(action);
                        }
                        return filtered.size() == actions.size() ? chain.proceed()
                                : chain.proceed(new Object[]{container, filtered});
                    }
                    Object result = chain.proceed();
                    // The verified renderer replaces the children, one root per action in order.
                    if (container.getChildCount() == actions.size()) {
                        for (int i = 0; i < actions.size(); i++) {
                            if (isReward(actions.get(i))) {
                                OrderLiveViews.track(container.getChildAt(i), () -> enabled(ORDER_REWARD),
                                        OrderLiveViews.Size.NONE);
                            }
                        }
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook OrderNormalHolder: " + t);
        }
    }

    private static boolean isReward(Object action) {
        Object value = OrderLiveViews.callGetter(action, "getTitle");
        if (!(value instanceof CharSequence)) return false;
        String title = value.toString();
        return title.contains("元宝") || title.contains("奖励") || title.contains("1k") || title.contains("红包");
    }
}
