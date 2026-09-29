package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

public final class BottomBarPurifier {
    private BottomBarPurifier() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        hookTabActionList(xposed, classLoader);
        hookMainActivitySetupPages(xposed, classLoader);
        hookOnItemSelectedListener(xposed, classLoader);
        hookChangeTabIndex(xposed, classLoader);
        hookApplyBottomTabTheme(xposed, classLoader);
        hookSecondTabPlacement(xposed, classLoader);
    }

    /**
     * Hook MainActivity.getTabActionList() to return only HOME, ORDER, and USER.
     */
    private static void hookTabActionList(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> mainActivityClass = Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            Class<?> tabActionHome = Class.forName("com.realtech.xiaocan.MainTabAction$HOME", false, cl);
            Class<?> tabActionOrder = Class.forName("com.realtech.xiaocan.MainTabAction$ORDER", false, cl);
            Class<?> tabActionUser = Class.forName("com.realtech.xiaocan.MainTabAction$USER", false, cl);

            Object homeInstance = tabActionHome.getField("INSTANCE").get(null);
            Object orderInstance = null;
            try {
                orderInstance = tabActionOrder.getField("INSTANCE").get(null);
            } catch (Throwable ignored) {
                orderInstance = tabActionOrder.getField("ORDER").get(null);
            }
            Object userInstance = tabActionUser.getField("INSTANCE").get(null);

            final Object fHome = homeInstance;
            final Object fOrder = orderInstance;
            final Object fUser = userInstance;

            for (Method m : mainActivityClass.getDeclaredMethods()) {
                if ("getTabActionList".equals(m.getName()) || "tabActionList_delegate$lambda$0".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        List<Object> threeTabs = new ArrayList<>(3);
                        threeTabs.add(fHome);
                        threeTabs.add(fOrder);
                        threeTabs.add(fUser);
                        return threeTabs;
                    });
                    MainHook.log("Hooked " + m.getName() + " to 3 tabs [HOME, ORDER, USER]");
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook tabActionList: " + t);
        }
    }

    /**
     * Prevent dynamic replacement of Tab 1 (Order) with marketing placements such as "抖音补贴".
     */
    private static void hookSecondTabPlacement(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> mainActivityClass = Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            for (Method m : mainActivityClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("getSecondTabPlacementContent".equals(mName) || "getSecondTabPlacementMatchValue".equals(mName)) {
                    xposed.hook(m).intercept(chain -> {
                        MainHook.log("Intercepted " + mName + ", returning null to keep pure Order tab");
                        return null;
                    });
                } else if ("isSecondTabPlacementAction".equals(mName)) {
                    xposed.hook(m).intercept(chain -> false);
                }
            }
            MainHook.log("Hooked second tab placement overrides");
        } catch (Throwable t) {
            MainHook.log("Failed to hook second tab placement: " + t);
        }
    }

    /**
     * Hook MainActivity.setUpPages to remove VIP and Benefit tabs AFTER ViewBinding has completed.
     */
    private static void hookMainActivitySetupPages(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> mainActivityClass = Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            for (Method m : mainActivityClass.getDeclaredMethods()) {
                if ("setUpPages".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        Activity activity = (Activity) chain.getThisObject();
                        purgeExtraTabs(activity);
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
                        Activity activity = (Activity) chain.getThisObject();
                        purgeExtraTabs(activity);
                        return result;
                    });
                    MainHook.log("Hooked MainActivity.applyBottomTabTheme");
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void purgeExtraTabs(Activity activity) {
        try {
            Method getBinding = activity.getClass().getMethod("getBinding");
            Object binding = getBinding.invoke(activity);
            if (binding == null) return;

            Field navViewField = binding.getClass().getField("navView");
            ViewGroup navView = (ViewGroup) navViewField.get(binding);

            Field nbiVipField = binding.getClass().getField("nbiVip");
            View nbiVip = (View) nbiVipField.get(binding);

            Field nbiBenefitField = binding.getClass().getField("nbiBenefit");
            View nbiBenefit = (View) nbiBenefitField.get(binding);

            Field nbiTdField = binding.getClass().getField("nbiTd");
            View nbiTd = (View) nbiTdField.get(binding);

            // Hide VIP decorations / badges
            try {
                Field lyActiveVip = binding.getClass().getField("lyActiveVip");
                View v = (View) lyActiveVip.get(binding);
                if (v != null) v.setVisibility(View.GONE);
            } catch (Throwable ignored) {}

            try {
                Field tvVipExpire = binding.getClass().getField("tvVipExpireTag");
                View v = (View) tvVipExpire.get(binding);
                if (v != null) v.setVisibility(View.GONE);
            } catch (Throwable ignored) {}

            // Hide floating red packet icon on main activity
            try {
                Field ivFloatingWindowField = binding.getClass().getField("ivFloatingWindow");
                View v = (View) ivFloatingWindowField.get(binding);
                if (v != null) v.setVisibility(View.GONE);
            } catch (Throwable ignored) {}

            if (navView != null) {
                if (nbiVip != null) {
                    nbiVip.setVisibility(View.GONE);
                    navView.removeView(nbiVip);
                }
                if (nbiBenefit != null) {
                    nbiBenefit.setVisibility(View.GONE);
                    navView.removeView(nbiBenefit);
                }
                if (nbiTd != null) {
                    try {
                        Method setItemKey = nbiTd.getClass().getMethod("setItemKey", String.class);
                        setItemKey.invoke(nbiTd, "order");
                    } catch (Throwable ignored) {}
                }
                Method initMethod = navView.getClass().getMethod("init");
                initMethod.invoke(navView);
                MainHook.log("navView purged and re-initialized with 3 items");
            }
        } catch (Throwable t) {
            MainHook.log("Failed to purge extra tabs: " + t);
        }
    }

    /**
     * Hook MainActivity$setUpPages$2.onItemSelected to handle tab clicks cleanly:
     * 0 -> Home (viewPager2 item 0)
     * 1 -> Order (viewPager2 item 1)
     * 2 -> User ("我的", viewPager2 item 2)
     */
    private static void hookOnItemSelectedListener(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> listenerClass = Class.forName("com.realtech.xiaocan.MainActivity$setUpPages$2", false, cl);
            for (Method m : listenerClass.getDeclaredMethods()) {
                if ("onItemSelected".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        int currentPos = (int) chain.getArg(2);
                        Field this$0Field = listenerClass.getDeclaredField("this$0");
                        this$0Field.setAccessible(true);
                        Activity mainActivity = (Activity) this$0Field.get(chain.getThisObject());
                        if (mainActivity != null) {
                            Method getBinding = mainActivity.getClass().getMethod("getBinding");
                            Object binding = getBinding.invoke(mainActivity);
                            if (binding != null) {
                                Field vpField = binding.getClass().getField("viewPager2");
                                Object viewPager2 = vpField.get(binding);
                                Method setCurrentItem = viewPager2.getClass().getMethod("setCurrentItem", int.class, boolean.class);

                                Field navViewField = binding.getClass().getField("navView");
                                Object navView = navViewField.get(binding);
                                Method updateTabState = navView.getClass().getMethod("updateTabState", int.class);

                                if (currentPos == 0) {
                                    setCurrentItem.invoke(viewPager2, 0, false);
                                    updateTabState.invoke(navView, 0);
                                    MainHook.log("Switched to Home tab (position 0)");
                                    return false;
                                } else if (currentPos == 1) {
                                    setCurrentItem.invoke(viewPager2, 1, false);
                                    updateTabState.invoke(navView, 1);
                                    MainHook.log("Switched to Order tab (position 1)");
                                    return false;
                                } else if (currentPos == 2) {
                                    switchToUserTab(mainActivity);
                                    updateTabState.invoke(navView, 2);
                                    MainHook.log("Switched to User tab (position 2)");
                                    return false;
                                }
                            }
                        }
                        return chain.proceed();
                    });
                    MainHook.log("Hooked MainActivity$setUpPages$2.onItemSelected");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook MainActivity$setUpPages$2.onItemSelected: " + t);
        }
    }

    private static void switchToUserTab(Activity mainActivity) {
        try {
            // Track source
            try {
                Class<?> trackClass = Class.forName("com.realtech.track.Track", false, mainActivity.getClassLoader());
                Object trackInstance = trackClass.getField("INSTANCE").get(null);
                Method setFromSource = trackClass.getMethod("setFromSource", String.class);
                setFromSource.invoke(trackInstance, "我的");
            } catch (Throwable ignored) {}

            // fetchKeFuUnread
            try {
                Method getMViewModel = mainActivity.getClass().getMethod("getMViewModel");
                Object vm = getMViewModel.invoke(mainActivity);
                if (vm != null) {
                    Method fetchKeFu = vm.getClass().getMethod("fetchKeFuUnread");
                    fetchKeFu.invoke(vm);
                }
            } catch (Throwable ignored) {}

            // viewPager2.setCurrentItem(2, false)
            Method getBinding = mainActivity.getClass().getMethod("getBinding");
            Object binding = getBinding.invoke(mainActivity);
            if (binding != null) {
                Field vpField = binding.getClass().getField("viewPager2");
                Object viewPager2 = vpField.get(binding);
                if (viewPager2 != null) {
                    Method setCurrentItem = viewPager2.getClass().getMethod("setCurrentItem", int.class, boolean.class);
                    setCurrentItem.invoke(viewPager2, 2, false);
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to switch to user tab: " + t);
        }
    }

    /**
     * Hook MainActivity.changeTabIndex to map position 4 ("我的" in old layout) to 2.
     */
    private static void hookChangeTabIndex(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> mainActivityClass = Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            for (Method m : mainActivityClass.getDeclaredMethods()) {
                if ("changeTabIndex".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        int index = (int) chain.getArg(0);
                        if (index == 4) {
                            MainHook.log("Mapped changeTabIndex from 4 to 2");
                            return chain.proceed(new Object[]{ 2 });
                        } else if (index == 2 || index == 3) {
                            MainHook.log("Ignored changeTabIndex for removed tab: " + index);
                            return null;
                        }
                        return chain.proceed();
                    });
                    MainHook.log("Hooked MainActivity.changeTabIndex");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook MainActivity.changeTabIndex: " + t);
        }
    }
}
