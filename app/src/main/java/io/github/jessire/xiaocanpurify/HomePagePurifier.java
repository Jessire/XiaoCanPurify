package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

public final class HomePagePurifier {
    private HomePagePurifier() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        hookStatusBarAndWindow(xposed, classLoader);
        hookSearchHint(xposed, classLoader);
        hookSearchRightBanner(xposed, classLoader);
        hookTopBanner(xposed, classLoader);
        hookFeedBanners(xposed, classLoader);
        hookOrderBanner(xposed, classLoader);
        hookFloatingRedPacket(xposed, classLoader);
        hookBottomPlacement(xposed, classLoader);
        hookRefreshLayout(xposed, classLoader);
        hookMenusRow(xposed, classLoader);
        hookHomeBottomInit(xposed, classLoader);
        hookHideHeadAdapter(xposed, classLoader);
    }

    /**
     * Aggressively disable pull-to-refresh AND second floor on home page.
     */
    private static void hookRefreshLayout(XposedInterface xposed, ClassLoader cl) {
        // Configured on the HomeFragment instance only; never hook global setters recursively.
    }

    /**
     * Fix status bar color: force clean white status bar with dark icons.
     */
    private static void hookStatusBarAndWindow(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> mainActivityClass = Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            for (Method m : mainActivityClass.getDeclaredMethods()) {
                if ("onCreate".equals(m.getName()) || "onResume".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        Activity activity = (Activity) chain.getThisObject();
                        if (activity != null && activity.getWindow() != null) {
                            activity.getWindow().getDecorView().setBackgroundColor(Color.WHITE);
                            activity.getWindow().setStatusBarColor(Color.WHITE);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                View decor = activity.getWindow().getDecorView();
                                int flags = decor.getSystemUiVisibility();
                                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                                decor.setSystemUiVisibility(flags);
                            }
                        }
                        return result;
                    });
                }
            }
            MainHook.log("Hooked MainActivity window status bar -> pure white + dark icons");
        } catch (Throwable t) {
            MainHook.log("Failed to hook MainActivity status bar: " + t);
        }

        try {
            Class<?> fragClass = Class.forName("com.realtech.promotion.pages.home.HomeFragment", false, cl);
            for (Method m : fragClass.getDeclaredMethods()) {
                if ("applyStatusBarThemeColor".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object binding = chain.getArg(0);
                        if (binding != null) {
                            try {
                                Field sbField = binding.getClass().getField("statusBar");
                                View statusBar = (View) sbField.get(binding);
                                if (statusBar != null) {
                                    statusBar.setBackgroundColor(Color.WHITE);
                                }
                            } catch (Throwable ignored) {}
                        }
                        return null;
                    });
                    MainHook.log("Hooked HomeFragment.applyStatusBarThemeColor -> white");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook applyStatusBarThemeColor: " + t);
        }

        try {
            Class<?> barLayoutClass = Class.forName("com.realtech.promotion.widget.HomeAppBarLayoutView", false, cl);
            for (Method m : barLayoutClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("setSpringFestivalState".equals(mName) || "set5stState".equals(mName)) {
                    xposed.hook(m).intercept(chain -> null);
                    MainHook.log("Hooked HomeAppBarLayoutView." + mName + " -> blocked festive bg");
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Eradicate marketing/ad hint words from the search box and reset placeholder to standard.
     */
    private static void hookSearchHint(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> autoScrollClass = Class.forName("com.realtech.promotion.widget.AutoScrollTextView", false, cl);
            for (Method m : autoScrollClass.getDeclaredMethods()) {
                if ("setTextList".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object autoScroll = chain.getThisObject();
                        if (autoScroll != null) {
                            try {
                                Method stopScroll = autoScroll.getClass().getMethod("stopScroll");
                                stopScroll.invoke(autoScroll);
                                Method getCurrentTextView = autoScroll.getClass().getMethod("getCurrentTextView");
                                TextView tv = (TextView) getCurrentTextView.invoke(autoScroll);
                                if (tv != null) {
                                    tv.setText("搜索商家或套餐");
                                    tv.setTextColor(0xFF999999);
                                }
                            } catch (Throwable ignored) {}
                        }
                        return null;
                    });
                    MainHook.log("Hooked AutoScrollTextView.setTextList");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook AutoScrollTextView: " + t);
        }

        try {
            Class<?> vmClass = Class.forName("com.realtech.promotion.pages.home.viewmodel.HomeViewModel", false, cl);
            for (Method m : vmClass.getDeclaredMethods()) {
                if ("refreshSearchWord".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> null);
                    MainHook.log("Hooked HomeViewModel.refreshSearchWord");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeViewModel.refreshSearchWord: " + t);
        }
    }

    /**
     * Fix search button color to orange and hide search right banner.
     */
    private static void hookSearchRightBanner(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> barLayoutClass = Class.forName("com.realtech.promotion.widget.HomeAppBarLayoutView", false, cl);
            for (Method m : barLayoutClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("renderSearchRightBanner".equals(mName) || "updateSearchRightImgList".equals(mName)) {
                    xposed.hook(m).intercept(chain -> {
                        Object barLayout = chain.getThisObject();
                        if (barLayout != null) {
                            try {
                                Field bindingField = barLayout.getClass().getDeclaredField("binding");
                                bindingField.setAccessible(true);
                                Object binding = bindingField.get(barLayout);
                                Field searchViewField = binding.getClass().getField("searchView");
                                Object searchView = searchViewField.get(binding);
                                Field bannerField = searchView.getClass().getField("bannerSearchRight");
                                View banner = (View) bannerField.get(searchView);
                                if (banner != null) {
                                    banner.setVisibility(View.GONE);
                                }
                                Field ivField = searchView.getClass().getField("ivSearchRight");
                                View iv = (View) ivField.get(searchView);
                                if (iv != null) {
                                    iv.setVisibility(View.GONE);
                                }
                                // Force search button to orange
                                if (searchView != null) {
                                    Method setBg = searchView.getClass().getMethod("setButtonBackgroundColor", int.class);
                                    setBg.invoke(searchView, 0xFFFF6600);
                                }
                            } catch (Throwable ignored) {}
                        }
                        return null;
                    });
                    MainHook.log("Hooked HomeAppBarLayoutView." + mName);
                }
            }

            for (Method m : barLayoutClass.getDeclaredMethods()) {
                if (!"updateTitleView".equals(m.getName()) && !"updateTitleBar".equals(m.getName())) continue;
                xposed.hook(m).intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        Field f = barLayoutClass.getDeclaredField("binding");
                        f.setAccessible(true);
                        Object search = field(f.get(chain.getThisObject()), "searchView");
                        View button = asView(field(search, "tvSearch"));
                        if (button != null) {
                            Object helper = button.getClass().getMethod("getHelper").invoke(button);
                            helper.getClass().getMethod("setBackgroundColorNormal", int.class).invoke(helper, Color.TRANSPARENT);
                            helper.getClass().getMethod("setStrokeColorNormal", int.class).invoke(helper, Color.TRANSPARENT);
                            button.setBackgroundColor(Color.TRANSPARENT);
                        }
                    } catch (Throwable ignored) {}
                    return result;
                });
            }

        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeSearchView: " + t);
        }
    }

    /**
     * Eradicate top banner ("请学生免费喝10000杯奶茶") and top red/orange background.
     */
    private static void hookTopBanner(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> bannerExtClass = Class.forName("com.realtech.promotion.pages.home.ext.HomeFragmentBannerExtKt", false, cl);
            for (Method m : bannerExtClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("applyTrafficExpBBanner".equals(mName)
                        || "applyTrafficExpBNoBannerTopArea".equals(mName)
                        || "applyTrafficExpBImmersiveBannerLayout".equals(mName)
                        || "initBanner".equals(mName)) {
                    xposed.hook(m).intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        for (Object arg : args) {
                            if (arg != null) {
                                String cn = arg.getClass().getName();
                                if (cn.contains("ViewHeadHomeBinding")) {
                                    collapseTopBanner(arg);
                                } else if (cn.contains("FragmentHomeBinding")) {
                                    try {
                                        Field sbField = arg.getClass().getField("statusBar");
                                        View sb = (View) sbField.get(arg);
                                        if (sb != null) sb.setBackgroundColor(Color.WHITE);
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                        return null;
                    });
                    MainHook.log("Hooked HomeFragmentBannerExtKt." + mName);
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeFragmentBannerExtKt: " + t);
        }

        try {
            Class<?> headViewExtClass = Class.forName("com.realtech.promotion.pages.home.ext.HeadViewExtKt", false, cl);
            for (Method m : headViewExtClass.getDeclaredMethods()) {
                if ("updateHeadBannerView".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        for (Object arg : args) {
                            if (arg != null) {
                                String cn = arg.getClass().getName();
                                if (cn.contains("ViewHeadHomeBinding")) {
                                    collapseTopBanner(arg);
                                } else if (cn.contains("FragmentHomeBinding")) {
                                    try {
                                        Field sbField = arg.getClass().getField("statusBar");
                                        View sb = (View) sbField.get(arg);
                                        if (sb != null) sb.setBackgroundColor(Color.WHITE);
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                        return null;
                    });
                    MainHook.log("Hooked HeadViewExtKt.updateHeadBannerView");
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HeadViewExtKt: " + t);
        }


    }

    private static void collapseTopBanner(Object headBinding) {
        try {
            Field flBannerField = headBinding.getClass().getField("flBanner");
            View flBanner = (View) flBannerField.get(headBinding);
            if (flBanner != null) {
                flBanner.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = flBanner.getLayoutParams();
                if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
                    lp.height = 0;
                    flBanner.setLayoutParams(lp);
                }
            }

            Field bgViewField = headBinding.getClass().getField("trafficExpTopBackground");
            View bgView = (View) bgViewField.get(headBinding);
            if (bgView != null) {
                bgView.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = bgView.getLayoutParams();
                if (lp != null) {
                    lp.height = 0;
                    bgView.setLayoutParams(lp);
                }
            }

            Field bannerField = headBinding.getClass().getField("banner");
            View banner = (View) bannerField.get(headBinding);
            if (banner != null) banner.setVisibility(View.GONE);

            Field trafficExpBannerField = headBinding.getClass().getField("trafficExpBanner");
            View trafficExpBanner = (View) trafficExpBannerField.get(headBinding);
            if (trafficExpBanner != null) trafficExpBanner.setVisibility(View.GONE);

            Field conHelicopterField = headBinding.getClass().getField("conHelicopterchallenge");
            View conHelicopter = (View) conHelicopterField.get(headBinding);
            if (conHelicopter != null) conHelicopter.setVisibility(View.GONE);
        } catch (Throwable ignored) {}
    }

    /**
     * Block HomeBottomKt.initHomeBottomView from setting bottom placement visible
     */
    private static void hookHomeBottomInit(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> bottomKtClass = Class.forName("com.realtech.promotion.pages.home.ext.HomeBottomKt", false, cl);
            for (Method m : bottomKtClass.getDeclaredMethods()) {
                if ("initHomeBottomView".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> null);
                    MainHook.log("Hooked HomeBottomKt.initHomeBottomView -> blocked");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeBottomKt: " + t);
        }
    }

    /**
     * Collapse the home header menus row (美团红包/闪购红包 etc.)
     */
    private static void hookMenusRow(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> menusBindingClass = Class.forName("com.realtech.promotion.databinding.LayoutHomeMenus2Binding", false, cl);
            for (Method m : menusBindingClass.getDeclaredMethods()) {
                if ("bind".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        if (result != null) {
                            try {
                                Method getRoot = result.getClass().getMethod("getRoot");
                                View root = (View) getRoot.invoke(result);
                                if (root != null) {
                                    root.setVisibility(View.GONE);
                                    ViewGroup.LayoutParams lp = root.getLayoutParams();
                                    if (lp != null) {
                                        lp.height = 0;
                                        root.setLayoutParams(lp);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                        return result;
                    });
                    MainHook.log("Hooked LayoutHomeMenus2Binding.bind");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook LayoutHomeMenus2Binding: " + t);
        }
    }

    /**
     * Hide RecyclerView header content (banner/menus) but keep header functional.
     * Only collapse ad/promo elements, keep search + filter in header.
     */
    private static void hookHideHeadAdapter(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> type = Class.forName("com.realtech.promotion.adapters.HomeHeadAdapter", false, cl);
            for (Method m : type.getDeclaredMethods()) {
                if (!"bindAll".equals(m.getName())) continue;
                xposed.hook(m).intercept(chain -> {
                    Object result = chain.proceed();
                    if (!chain.getArgs().isEmpty()) collapseHeader(asView(chain.getArg(0)));
                    return result;
                });
            }
        } catch (Throwable t) { MainHook.log("Header hook: " + t); }
    }

    private static void collapseHeader(View root) {
        if (root == null) return;
        collapseView(root);
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp != null && lp.height != 0) { lp.height = 0; root.setLayoutParams(lp); }
        if (root.getMinimumHeight() != 0) root.setMinimumHeight(0);
    }

    private static View asView(Object value) {
        if (value instanceof View) return (View) value;
        if (value == null) return null;
        try { return (View) value.getClass().getMethod("getRoot").invoke(value); }
        catch (Throwable ignored) { return null; }
    }

    private static Object field(Object obj, String name) {
        if (obj == null) return null;
        try { return obj.getClass().getField(name).get(obj); }
        catch (Throwable ignored) { return null; }
    }

    /**
     * Filter ad items (picbanner/pic/group) from HomePromotionAdapter data list
     * so they never appear in the RecyclerView at all.
     */
    private static void hookFeedBanners(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> type = Class.forName("com.realtech.promotion.adapters.HomePromotionAdapter", false, cl);
            for (Method m : type.getSuperclass().getDeclaredMethods()) {
                String name = m.getName();
                boolean bulk = "submitList".equals(name) || "setItems".equals(name) || "addAll".equals(name);
                if (!bulk && !"add".equals(name) && !"set".equals(name)) continue;
                xposed.hook(m).intercept(chain -> {
                    if (!type.isInstance(chain.getThisObject())) return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    if (bulk) {
                        for (int i = 0; i < args.length; i++) {
                            if (!(args[i] instanceof java.util.Collection)) continue;
                            java.util.ArrayList<Object> clean = new java.util.ArrayList<>();
                            for (Object item : (java.util.Collection<?>) args[i])
                                if (!isAdFeedItem(item)) clean.add(item);
                            args[i] = clean;
                        }
                    } else if (args.length > 0 && isAdFeedItem(args[args.length - 1])) {
                        if ("set".equals(name)) {
                            type.getSuperclass().getMethod("removeAt", int.class)
                                .invoke(chain.getThisObject(), args[0]);
                        }
                        return null;
                    }
                    return chain.proceed(args);
                });
                MainHook.log("Home-only feed filter: " + m.getName());
            }
        } catch (Throwable t) { MainHook.log("Feed hook: " + t); }
    }

    private static boolean isAdFeedItem(Object item) {
        if (item == null) return false;
        try {
            // Check the item's "type" field (HomeType enum)
            Method getType = item.getClass().getMethod("getType");
            Object type = getType.invoke(item);
            if (type != null) {
                String typeName = type instanceof Enum ? ((Enum<?>) type).name() : type.toString();
                return "pic".equals(typeName) || "picbanner".equals(typeName) || "group".equals(typeName);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void collapseViewHolder(Object holder) {
        if (holder == null) return;
        try {
            Method getBinding = holder.getClass().getMethod("getBinding");
            Object binding = getBinding.invoke(holder);
            if (binding != null) {
                Method getRoot = binding.getClass().getMethod("getRoot");
                View root = (View) getRoot.invoke(binding);
                collapseView(root);
                root.requestLayout();
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Eradicate "开心收下" order banner bar from home header.
     */
    private static void hookOrderBanner(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> extClass = Class.forName("com.realtech.promotion.pages.home.ext.HomeTrafficExpCOrderExtKt", false, cl);

            for (Method m : extClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("buildTrafficExpCOrderBannerItems".equals(mName)) {
                    xposed.hook(m).intercept(chain -> Collections.emptyList());
                    MainHook.log("Hooked HomeTrafficExpCOrderExtKt.buildTrafficExpCOrderBannerItems");
                } else if ("updateTrafficExpCOrderBanner".equals(mName)) {
                    xposed.hook(m).intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        for (Object arg : args) {
                            if (arg != null && arg.getClass().getName().contains("ViewHeadHomeBinding")) {
                                try {
                                    Field layoutBannerField = arg.getClass().getField("layoutTrafficExpCOrderBanner");
                                    Object layoutBanner = layoutBannerField.get(arg);
                                    if (layoutBanner != null) {
                                        Method getRoot = layoutBanner.getClass().getMethod("getRoot");
                                        View root = (View) getRoot.invoke(layoutBanner);
                                        collapseView(root);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                        return null;
                    });
                    MainHook.log("Hooked HomeTrafficExpCOrderExtKt.updateTrafficExpCOrderBanner");
                } else if ("bindTrafficExpCOrderBanner".equals(mName)) {
                    xposed.hook(m).intercept(chain -> null);
                    MainHook.log("Hooked HomeTrafficExpCOrderExtKt.bindTrafficExpCOrderBanner");
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeTrafficExpCOrderExtKt: " + t);
        }
    }

    /**
     * Eradicate floating red packet ("¥18 领红包") at the bottom of the home screen.
     * This is app:id/layout_bottom_placement & app:id/ivBottomImg in LayoutHomeBottomBinding!
     */
    private static void hookBottomPlacement(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> bottomPlacementBindingClass = Class.forName("com.realtech.promotion.databinding.LayoutHomeBottomPlacementBinding", false, cl);
            for (Method m : bottomPlacementBindingClass.getDeclaredMethods()) {
                if ("bind".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        MainHook.log("LayoutHomeBottomPlacementBinding.bind INVOKED");
                        Object result = chain.proceed();
                        if (result != null) {
                            try {
                                Method getRoot = result.getClass().getMethod("getRoot");
                                View root = (View) getRoot.invoke(result);
                                if (root != null) {
                                    root.setVisibility(View.GONE);
                                    MainHook.log("LayoutHomeBottomPlacementBinding root GONE, view=" + root.getClass().getSimpleName());
                                }
                            } catch (Throwable t) {
                                MainHook.log("LayoutHomeBottomPlacementBinding root hide error: " + t);
                            }
                        }
                        return result;
                    });
                    MainHook.log("Hooked LayoutHomeBottomPlacementBinding.bind");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook LayoutHomeBottomPlacementBinding: " + t);
        }

        try {
            Class<?> bottomBindingClass = Class.forName("com.realtech.promotion.databinding.LayoutHomeBottomBinding", false, cl);
            for (Method m : bottomBindingClass.getDeclaredMethods()) {
                if ("bind".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        if (result != null) {
                            try {
                                Field placementField = result.getClass().getField("layoutBottomPlacement");
                                Object placement = placementField.get(result);
                                if (placement != null) {
                                    Method getRoot = placement.getClass().getMethod("getRoot");
                                    View root = (View) getRoot.invoke(placement);
                                    collapseView(root);
                                }
                                Field tipsField = result.getClass().getField("bottomTips");
                                View tips = (View) tipsField.get(result);
                                if (tips != null) {
                                    tips.setVisibility(View.GONE);
                                }
                            } catch (Throwable ignored) {}
                        }
                        return result;
                    });
                    MainHook.log("Hooked LayoutHomeBottomBinding.bind");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook LayoutHomeBottomBinding: " + t);
        }
    }

    /**
     * Eradicate floating red packet rain and all floating bubbles on home.
     */
    private static void hookFloatingRedPacket(XposedInterface xposed, ClassLoader cl) {
        // 1. Hook LayoutHomeFabBinding.bind -> collapse layout_fab immediately
        try {
            Class<?> fabBindingClass = Class.forName("com.realtech.promotion.databinding.LayoutHomeFabBinding", false, cl);
            for (Method m : fabBindingClass.getDeclaredMethods()) {
                if ("bind".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        if (result != null) {
                            try {
                                Method getRoot = result.getClass().getMethod("getRoot");
                                View root = (View) getRoot.invoke(result);
                                collapseView(root);
                            } catch (Throwable ignored) {}
                        }
                        return result;
                    });
                    MainHook.log("Hooked LayoutHomeFabBinding.bind");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook LayoutHomeFabBinding.bind: " + t);
        }

        // 2. Hook HomeRedPackRainFabView
        try {
            Class<?> rainClass = Class.forName("com.realtech.promotion.widget.home.HomeRedPackRainFabView", false, cl);
            for (Method m : rainClass.getDeclaredMethods()) {
                if ("checkShowRain".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> false);
                    MainHook.log("Hooked HomeRedPackRainFabView.checkShowRain -> false");
                } else if ("updateViews".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        View view = (View) chain.getThisObject();
                        collapseView(view);
                        return null;
                    });
                    MainHook.log("Hooked HomeRedPackRainFabView.updateViews");
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeRedPackRainFabView: " + t);
        }

        // 3. Hook modern HomeFabKt & HomeFabOrderKt
        try {
            Class<?> fabKtClass = Class.forName("com.realtech.promotion.pages.home.ext.fab.HomeFabKt", false, cl);
            for (Method m : fabKtClass.getDeclaredMethods()) {
                if ("initHomeFab".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object binding = chain.getArg(0);
                        if (binding != null) {
                            try {
                                Method getRoot = binding.getClass().getMethod("getRoot");
                                View root = (View) getRoot.invoke(binding);
                                collapseView(root);
                            } catch (Throwable ignored) {}
                        }
                        return null;
                    });
                    MainHook.log("Hooked HomeFabKt.initHomeFab");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeFabKt: " + t);
        }

        try {
            Class<?> fabOrderKtClass = Class.forName("com.realtech.promotion.pages.home.ext.fab.HomeFabOrderKt", false, cl);
            for (Method m : fabOrderKtClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("initFabOrder".equals(mName) || "updateOrderFabView".equals(mName)) {
                    xposed.hook(m).intercept(chain -> null);
                    MainHook.log("Hooked HomeFabOrderKt." + mName);
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeFabOrderKt: " + t);
        }

        // 4. Hook legacy HomeFabExtKt
        try {
            Class<?> fabExtClass = Class.forName("com.realtech.promotion.pages.home.ext.HomeFabExtKt", false, cl);
            for (Method m : fabExtClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("initFabView".equals(mName)
                        || "updateOrderFabView".equals(mName)
                        || "initNewUserFabView".equals(mName)
                        || "updateNewUserFabView".equals(mName)) {
                    xposed.hook(m).intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        if (!args.isEmpty() && args.get(0) != null) {
                            hideHomeLayoutFab(args.get(0));
                        }
                        return null;
                    });
                    MainHook.log("Hooked HomeFabExtKt." + mName);
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeFabExtKt: " + t);
        }

        // 5. Hook HomeFabOrderBannerAdapter
        try {
            Class<?> adapterClass = Class.forName("com.realtech.promotion.adapters.HomeFabOrderBannerAdapter", false, cl);
            for (Method m : adapterClass.getDeclaredMethods()) {
                if ("onBindView".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> null);
                    MainHook.log("Hooked HomeFabOrderBannerAdapter.onBindView");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeFabOrderBannerAdapter: " + t);
        }

        // 6. Hook HomeFragment lazyInitView (called when fragment becomes visible)
        try {
            Class<?> fragClass = Class.forName("com.realtech.promotion.pages.home.HomeFragment", false, cl);
            for (Method m : fragClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("lazyInitView".equals(mName) || "lazyLoadData".equals(mName) || "onResume".equals(mName)) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        Object fragment = chain.getThisObject();
                        if (fragment != null) {
                            forceHideFloatingViews(fragment);
                        }
                        return result;
                    });
                    MainHook.log("Hooked HomeFragment." + mName);
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeFragment lifecycle: " + t);
        }
    }

    private static final java.util.WeakHashMap<View, Boolean> watchedHomes = new java.util.WeakHashMap<>();

    private static void forceHideFloatingViews(Object fragment) {
        try {
            Object binding = fragment.getClass().getMethod("getBinding").invoke(fragment);
            View root = asView(binding);
            if (root == null) return;
            enforceHome(binding);
            if (watchedHomes.put(root, Boolean.TRUE) == null) {
                root.getViewTreeObserver().addOnGlobalLayoutListener(() -> enforceHome(binding));
                MainHook.log("Home persistent layout guard attached");
            }
        } catch (Throwable t) { MainHook.log("Home guard: " + t); }
    }

    private static void enforceHome(Object binding) {
        for (String name : new String[]{"layoutFab", "flSceondFloor", "lySceondFloorGuide", "layoutSecondHeader"})
            collapseView(asView(field(binding, name)));
        collapseView(asView(field(field(binding, "layoutBottom"), "layoutBottomPlacement")));
        Object refresh = field(binding, "refresh");
        if (refresh != null) {
            for (String name : new String[]{"setEnableRefresh", "setEnableOverScrollDrag", "setEnableOverScrollBounce"}) {
                try { refresh.getClass().getMethod(name, boolean.class).invoke(refresh, false); }
                catch (Throwable ignored) {}
            }
        }
        View rv = asView(field(binding, "rv"));
        View toolbar = asView(field(binding, "toolBar"));
        if (rv != null && toolbar != null && toolbar.getHeight() > 0) {
            // The title bar is taller than the search pill, and that spare band is transparent,
            // so the gray page background shows through. Pin the list to the pill itself.
            View search = findViewByName(toolbar, "search_view");
            if (search == null || search.getHeight() <= 0) search = findViewByName(toolbar, "searchView");
            View anchor = search != null && search.getHeight() > 0 ? search : toolbar;
            int[] anchorLocation = new int[2];
            int[] listLocation = new int[2];
            anchor.getLocationInWindow(anchorLocation);
            rv.getLocationInWindow(listLocation);
            int searchBottom = anchorLocation[1] + anchor.getHeight();
            int top = Math.max(0, searchBottom - listLocation[1]);
            if (!rv.canScrollVertically(-1)) {
                View row = topmostContent(rv);
                if (row != null) {
                    int[] rowLocation = new int[2];
                    row.getLocationInWindow(rowLocation);
                    int gap = rowLocation[1] - searchBottom;
                    if (gap > 1) top = Math.max(0, rv.getPaddingTop() - gap);
                }
            }
            if (rv.getPaddingTop() != top) rv.setPadding(rv.getPaddingLeft(), top, rv.getPaddingRight(), rv.getPaddingBottom());
            if (rv instanceof ViewGroup) ((ViewGroup) rv).setClipToPadding(true);
        }
        View root = asView(binding);
        if (root instanceof ViewGroup) cleanHomeTree((ViewGroup) root);
    }

    private static View topmostContent(View rv) {
        if (!(rv instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) rv;
        View best = null;
        int bestTop = Integer.MAX_VALUE;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || child.getHeight() <= 2) continue;
            int[] location = new int[2];
            child.getLocationInWindow(location);
            if (location[1] < bestTop) {
                bestTop = location[1];
                best = child;
            }
        }
        return best;
    }

    private static View findViewByName(View root, String name) {
        if (name.equals(getViewIdName(root))) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findViewByName(group.getChildAt(i), name);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static void cleanHomeTree(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            String id = getViewIdName(child);
            if ("fl_banner".equals(id)) {
                // Header root owns the promotional blocks, not the merchant filter adapter.
                if (child.getParent() instanceof View) collapseHeader((View) child.getParent());
            }
            if ("layout_traffic_exp_c_order_banner".equals(id) || "layout_bottom_placement".equals(id)
                    || "layout_fab".equals(id) || "fl_sceond_floor".equals(id)
                    || "ly_sceond_floor_guide".equals(id) || "layout_second_header".equals(id)) collapseView(child);
            if (child instanceof ViewGroup) cleanHomeTree((ViewGroup) child);
        }
    }

    private static void collapseChildrenByIdName(android.view.ViewGroup vg, String idName) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            String name = getViewIdName(child);
            if (idName.equals(name)) {
                child.setVisibility(View.GONE);
                child.getLayoutParams().height = 0;
            }
            if (child instanceof android.view.ViewGroup) {
                collapseChildrenByIdName((android.view.ViewGroup) child, idName);
            }
        }
    }

    private static String getViewIdName(View v) {
        if (v == null || v.getId() == View.NO_ID) return "";
        try {
            return v.getResources().getResourceEntryName(v.getId());
        } catch (Throwable e) {
            return "";
        }
    }

    private static void hideHomeLayoutFab(Object fragment) {
        try {
            Method getBinding = fragment.getClass().getMethod("getBinding");
            Object binding = getBinding.invoke(fragment);
            if (binding != null) {
                hideBindingLayoutFab(binding);
            }
        } catch (Throwable ignored) {}
    }

    private static void hideBindingLayoutFab(Object binding) {
        try {
            Field layoutFabField = binding.getClass().getField("layoutFab");
            Object layoutFab = layoutFabField.get(binding);
            if (layoutFab != null) {
                Method getRoot = layoutFab.getClass().getMethod("getRoot");
                View root = (View) getRoot.invoke(layoutFab);
                collapseView(root);
            }
        } catch (Throwable ignored) {}
    }

    private static void collapseView(View view) {
        if (view == null) return;
        view.setVisibility(View.GONE);
    }
}
