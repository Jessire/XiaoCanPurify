package io.github.jessire.xiaocanpurify;

import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.MAIN_FLOAT;
import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.NAV_EXTRA;
import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.NAV_HIDE_HOME;
import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.NAV_HIDE_ORDER;
import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.NAV_HIDE_TASK;
import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.NAV_HIDE_VIP;
import static io.github.jessire.xiaocanpurify.LayoutSettings.enabled;
import static io.github.jessire.xiaocanpurify.LayoutSettings.tabHidden;

import android.app.Activity;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * 底栏按「首页 / 订单 / 会员 / 福利 / 我的」逐个独立显隐。
 *
 * <p>小蚕的五个标签是同一条 LinearLayout 里等宽（weight=1）的 BottomBarItem。把某一位设为
 * GONE，其余标签会自动重新均分宽度，而 navView 与 viewPager2 的下标完全不变，所以不需要
 * 移除视图、也不需要裁剪适配器列表。这样宿主内部按位置跳转的逻辑（订单、会员、元宝）继续
 * 成立，开关也能即时生效。
 *
 * <p>「我的」不提供开关：设置入口（模块设置）就挂在那一页，隐藏后用户无法自查。
 * 若四项都打开，「首页」会被保留，避免底栏只剩一项。
 */
public final class BottomBarPurifier {
    /** 底栏位置：0 首页、1 订单、2 会员、3 福利、4 我的。 */
    private static final String[] TAB_FIELDS = {"nbiHome", "nbiTd", "nbiVip", "nbiBenefit", "nbiUser"};
    private static final LayoutSettings.Item[] TAB_RULES = {
            NAV_HIDE_HOME, NAV_HIDE_ORDER, NAV_HIDE_VIP, NAV_HIDE_TASK, null};

    private BottomBarPurifier() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        hookShoppingRebateTab(xposed, classLoader);
        hookSecondTabPlacement(xposed, classLoader);
        hookMainActivitySetupPages(xposed, classLoader);
        hookApplyBottomTabTheme(xposed, classLoader);
        hookChangeTabIndex(xposed, classLoader);
    }

    /** 3.23.0 swaps tab 1 for a "购物返利" page through a remote feature switch. */
    private static void hookShoppingRebateTab(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> registry = Class.forName("com.realtech.xiaocan.MainTabDynamicRegistryKt", false, cl);
            Method enabledMethod = registry.getDeclaredMethod("isShoppingRebateTabEnabled");
            xposed.hook(enabledMethod).intercept(chain -> enabled(NAV_EXTRA) ? Boolean.FALSE : chain.proceed());
            MainHook.log("Hooked isShoppingRebateTabEnabled -> keep Order tab");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Not present before 3.23.0.
        } catch (Throwable t) {
            MainHook.log("Shopping rebate tab hook unavailable: " + t.getClass().getSimpleName());
        }
    }

    /** Prevent dynamic replacement of Tab 1 (Order) with marketing placements such as "抖音补贴". */
    private static void hookSecondTabPlacement(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> mainActivityClass = Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            for (Method m : mainActivityClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("getSecondTabPlacementContent".equals(mName) || "getSecondTabPlacementMatchValue".equals(mName)) {
                    xposed.hook(m).intercept(chain -> {
                        if (!enabled(NAV_EXTRA)) return chain.proceed();
                        MainHook.log("Intercepted " + mName + ", returning null to keep pure Order tab");
                        return null;
                    });
                } else if ("isSecondTabPlacementAction".equals(mName)) {
                    xposed.hook(m).intercept(chain -> enabled(NAV_EXTRA) ? false : chain.proceed());
                }
            }
            MainHook.log("Hooked second tab placement overrides");
        } catch (Throwable t) {
            MainHook.log("Failed to hook second tab placement: " + t);
        }
    }

    private static void hookMainActivitySetupPages(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> mainActivityClass = Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            for (Method m : mainActivityClass.getDeclaredMethods()) {
                if ("setUpPages".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        bindTabs((Activity) chain.getThisObject());
                        return result;
                    });
                    MainHook.log("Hooked MainActivity.setUpPages");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook MainActivity.setUpPages: " + t);
        }
    }

    private static void hookApplyBottomTabTheme(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> mainActivityClass = Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            for (Method m : mainActivityClass.getDeclaredMethods()) {
                if ("applyBottomTabTheme".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        bindTabs((Activity) chain.getThisObject());
                        return result;
                    });
                    MainHook.log("Hooked MainActivity.applyBottomTabTheme");
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 把四个可隐藏标签绑到各自的开关键上；已绑定过的视图会在开关变化时自动刷新。 */
    private static void bindTabs(Activity activity) {
        if (activity == null) return;
        try {
            Object binding = activity.getClass().getMethod("getBinding").invoke(activity);
            if (binding == null) return;
            for (int i = 0; i < TAB_FIELDS.length; i++) {
                View tab = readView(binding, TAB_FIELDS[i]);
                if (tab == null) continue;
                LayoutSettings.Item rule = TAB_RULES[i];
                if (rule == null) {
                    // 「我的」始终保留，它是设置与订单的兜底入口。
                    tab.setVisibility(View.VISIBLE);
                    continue;
                }
                HomeLiveViews.track(tab, () -> tabHidden(rule), false, false);
            }
            if (enabled(MAIN_FLOAT)) hide(readView(binding, "ivFloatingWindow"));
            if (tabHidden(NAV_HIDE_VIP)) {
                hide(readView(binding, "lyActiveVip"));
                hide(readView(binding, "tvVipExpireTag"));
            }
            if (!tabHidden(NAV_HIDE_ORDER)) keepOrderKey(binding);
            moveOffHiddenTab(binding);
            MainHook.log("Bottom bar tabs bound");
        } catch (Throwable t) {
            MainHook.log("Failed to bind bottom tabs: " + t.getClass().getSimpleName());
        }
    }

    /** 关掉「购物返利」后，第二格必须仍然是订单，否则点进去是别的页面。 */
    private static void keepOrderKey(Object binding) {
        View second = readView(binding, "nbiTd");
        if (second == null) return;
        try {
            second.getClass().getMethod("setItemKey", String.class).invoke(second, "order");
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    /** 当前停在被隐藏的标签上时，切到第一个仍可见的位置。 */
    private static void moveOffHiddenTab(Object binding) {
        try {
            Object pager = read(binding, "viewPager2");
            if (pager == null) return;
            int current = (int) pager.getClass().getMethod("getCurrentItem").invoke(pager);
            if (!tabHiddenAt(current)) return;
            for (int i = 0; i < TAB_RULES.length; i++) {
                if (tabHiddenAt(i)) continue;
                pager.getClass().getMethod("setCurrentItem", int.class, boolean.class)
                        .invoke(pager, i, false);
                Object nav = read(binding, "navView");
                if (nav != null) {
                    nav.getClass().getMethod("updateTabState", int.class).invoke(nav, i);
                }
                MainHook.log("Moved off hidden tab to position " + i);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    private static boolean tabHiddenAt(int position) {
        if (position < 0 || position >= TAB_RULES.length) return false;
        LayoutSettings.Item rule = TAB_RULES[position];
        return rule != null && tabHidden(rule);
    }

    /**
     * 宿主按原始位置跳转（订单详情回订单页、元宝入口进会员页等）。目标被隐藏时不再静默失败：
     * 「会员」改用独立页面打开元宝乐园，其余位置直接忽略，避免跳到用户已经关掉的页面。
     */
    private static void hookChangeTabIndex(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> mainActivityClass = Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            for (Method m : mainActivityClass.getDeclaredMethods()) {
                if (!"changeTabIndex".equals(m.getName()) || m.getParameterCount() != 1) continue;
                xposed.hook(m).intercept(chain -> {
                    int index = (int) chain.getArg(0);
                    if (index < 0 || index >= TAB_RULES.length) return chain.proceed();
                    if (!tabHiddenAt(index)) return chain.proceed();
                    if (index == 2) {
                        Activity activity = (Activity) chain.getThisObject();
                        if (VipEntryFallback.openPointsPark(activity)) {
                            MainHook.log("Opened points park for the hidden Vip tab");
                        }
                    }
                    MainHook.log("Ignored changeTabIndex for hidden tab: " + index);
                    return null;
                });
                MainHook.log("Hooked MainActivity.changeTabIndex");
                break;
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook MainActivity.changeTabIndex: " + t);
        }
    }

    private static Object read(Object binding, String name) {
        if (binding == null) return null;
        try {
            return binding.getClass().getField(name).get(binding);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static View readView(Object binding, String name) {
        Object value = read(binding, name);
        return value instanceof View ? (View) value : null;
    }

    private static void hide(View view) {
        if (view != null) view.setVisibility(View.GONE);
    }
}
