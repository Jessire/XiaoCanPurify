package io.github.jessire.xiaocanpurify;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

public final class OrderPagePurifier {
    private OrderPagePurifier() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        hookOrderTypeFragment(xposed, classLoader);
        hookOrderListFragment(xposed, classLoader);
        hookOrderHolders(xposed, classLoader);
    }

    /**
     * Purge top banner/challenge and promo entry in OrderTypeFragment.
     */
    private static void hookOrderTypeFragment(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> typeFragClass = Class.forName("com.realtech.my_order.ui.OrderTypeFragment", false, cl);

            for (Method m : typeFragClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("lazyInitView".equals(mName)) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        purgeOrderTypeViews(chain.getThisObject());
                        return result;
                    });
                    MainHook.log("Hooked OrderTypeFragment.lazyInitView");
                } else if ("renderStickyBar".equals(mName)) {
                    xposed.hook(m).intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        if (!args.isEmpty() && args.get(0) != null) {
                            hideField(args.get(0), "clMachallenge");
                        }
                        return null;
                    });
                    MainHook.log("Hooked OrderTypeFragment.renderStickyBar");
                } else if ("initFreeOrderInviteView".equals(mName)) {
                    xposed.hook(m).intercept(chain -> {
                        purgeOrderTypeViews(chain.getThisObject());
                        return null;
                    });
                    MainHook.log("Hooked OrderTypeFragment.initFreeOrderInviteView");
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook OrderTypeFragment: " + t);
        }
    }

    private static void purgeOrderTypeViews(Object fragment) {
        if (fragment == null) return;
        try {
            Method getBinding = fragment.getClass().getMethod("getBinding");
            Object binding = getBinding.invoke(fragment);
            if (binding != null) {
                hideField(binding, "clMachallenge");
                hideField(binding, "llFreeOrder");
                hideField(binding, "ivTips");
                hideField(binding, "ivFreeOrderTag");
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Prevent feed images, native ads, and challenge popups in OrderListFragment.
     */
    private static void hookOrderListFragment(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> listFragClass = Class.forName("com.realtech.my_order.ui.OrderListFragment", false, cl);

            for (Method m : listFragClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("renderFeedImage".equals(mName)
                        || "renderNativeAd".equals(mName)
                        || "renderChallenge".equals(mName)
                        || "showMaChallengePopUp".equals(mName)
                        || "renderOrderTip".equals(mName)) {
                    xposed.hook(m).intercept(chain -> null);
                    MainHook.log("Hooked OrderListFragment." + mName + " -> blocked");
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook OrderListFragment: " + t);
        }
    }

    /**
     * Purge order list ad holders and promo buttons ("领1k元宝").
     */
    private static void hookOrderHolders(XposedInterface xposed, ClassLoader cl) {
        // 1. OrderBannerHolder
        try {
            Class<?> bannerHolderClass = Class.forName("com.realtech.my_order.adapter.holder.OrderBannerHolder", false, cl);
            for (Method m : bannerHolderClass.getDeclaredMethods()) {
                if ("bind".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        collapseHolderView(chain.getThisObject());
                        return null;
                    });
                    MainHook.log("Hooked OrderBannerHolder.bind");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook OrderBannerHolder: " + t);
        }

        // 2. OrderNativeadHolder
        try {
            Class<?> nativeadHolderClass = Class.forName("com.realtech.my_order.adapter.holder.OrderNativeadHolder", false, cl);
            for (Method m : nativeadHolderClass.getDeclaredMethods()) {
                if ("bind".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        collapseHolderView(chain.getThisObject());
                        return null;
                    });
                    MainHook.log("Hooked OrderNativeadHolder.bind");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook OrderNativeadHolder: " + t);
        }

        // 3. OrderNormalHolder action buttons (filter out "领1k元宝") and tip banner
        try {
            Class<?> normalHolderClass = Class.forName("com.realtech.my_order.adapter.holder.OrderNormalHolder", false, cl);
            for (Method m : normalHolderClass.getDeclaredMethods()) {
                if ("bindActionButtons".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object container = chain.getArg(0);
                        List<?> actions = (List<?>) chain.getArg(1);
                        if (actions != null) {
                            List<Object> filtered = new ArrayList<>();
                            for (Object act : actions) {
                                if (act != null) {
                                    String title = "";
                                    try {
                                        Method getTitle = act.getClass().getMethod("getTitle");
                                        Object t = getTitle.invoke(act);
                                        if (t != null) title = t.toString();
                                    } catch (Throwable ignored) {}
                                    if (title.contains("元宝") || title.contains("奖励") || title.contains("1k") || title.contains("红包")) {
                                        MainHook.log("Purged order action button: " + title);
                                        continue;
                                    }
                                    filtered.add(act);
                                }
                            }
                            return chain.proceed(new Object[]{ container, filtered });
                        }
                        return chain.proceed();
                    });
                    MainHook.log("Hooked OrderNormalHolder.bindActionButtons");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook OrderNormalHolder: " + t);
        }
    }

    private static void collapseHolderView(Object holder) {
        if (holder == null) return;
        try {
            Method getBinding = holder.getClass().getMethod("getBinding");
            Object binding = getBinding.invoke(holder);
            if (binding != null) {
                Method getRoot = binding.getClass().getMethod("getRoot");
                View root = (View) getRoot.invoke(binding);
                if (root != null) {
                    root.setVisibility(View.GONE);
                    ViewGroup.LayoutParams lp = root.getLayoutParams();
                    if (lp != null) {
                        lp.height = 0;
                        lp.width = 0;
                        if (lp instanceof ViewGroup.MarginLayoutParams) {
                            ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, 0, 0);
                        }
                        root.setLayoutParams(lp);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void hideField(Object binding, String fieldName) {
        try {
            Field field = binding.getClass().getField(fieldName);
            View view = (View) field.get(binding);
            if (view != null) {
                view.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, 0, 0);
                    view.setLayoutParams(lp);
                }
            }
        } catch (Throwable ignored) {}
    }
}
