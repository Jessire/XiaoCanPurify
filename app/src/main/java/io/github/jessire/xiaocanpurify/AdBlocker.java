package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

public final class AdBlocker {
    private AdBlocker() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        hookSplashAdFactory(xposed, classLoader);
        hookSplashAdUtils(xposed, classLoader);
        hookSilentTaskEngine(xposed, classLoader);
        hookFeedAndBannerAds(xposed, classLoader);
        hookUserCommerceGrid(xposed, classLoader);
        hookFloatingAds(xposed, classLoader);
        hookSearchRightBanner(xposed, classLoader);
        hookFlutterAdPlugins(xposed, classLoader);
        hookAdActivities(xposed);
    }

    private static void hookSplashAdFactory(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> factoryClass = Class.forName("com.realtech.xiaocan.ad.splash.SplashAdSourceFactory", false, cl);
            Class<?> sourceInterface = Class.forName("com.realtech.xiaocan.ad.splash.SplashAdSource", false, cl);
            Class<?> listenerInterface = Class.forName("com.realtech.xiaocan.ad.splash.SplashAdSourceListener", false, cl);

            for (Method m : factoryClass.getDeclaredMethods()) {
                if ("create".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object listener = chain.getArg(2);
                        Object proxy = Proxy.newProxyInstance(
                                cl,
                                new Class<?>[]{sourceInterface},
                                new InvocationHandler() {
                                    @Override
                                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                        String mName = method.getName();
                                        if ("load".equals(mName)) {
                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                try {
                                                    Method onClosed = listenerInterface.getMethod("onAdClosed");
                                                    onClosed.invoke(listener);
                                                } catch (Throwable t) {
                                                    try {
                                                        Method onError = listenerInterface.getMethod("onAdError");
                                                        onError.invoke(listener);
                                                    } catch (Throwable ignored) {}
                                                }
                                            });
                                            return null;
                                        }
                                        if ("isAdReady".equals(mName)) return false;
                                        if ("destroy".equals(mName)) return null;
                                        if ("equals".equals(mName)) return proxy == (args != null && args.length > 0 ? args[0] : null);
                                        if ("hashCode".equals(mName)) return System.identityHashCode(proxy);
                                        if ("toString".equals(mName)) return "PurifiedSplashAdSource";
                                        return null;
                                    }
                                }
                        );
                        return proxy;
                    });
                    MainHook.log("Hooked SplashAdSourceFactory.create");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook SplashAdSourceFactory: " + t);
        }
    }

    private static void hookSplashAdUtils(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> splashUtilsClass = Class.forName("com.realtech.xiaocan.util.SplashAdUtils", false, cl);
            for (Method m : splashUtilsClass.getDeclaredMethods()) {
                if ("initAd".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> null);
                    break;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> placementManager = Class.forName("com.realtech.xiaocan.ad.splash.SplashAdPlacementManager", false, cl);
            Method refreshAsync = placementManager.getDeclaredMethod("refreshAsync");
            xposed.hook(refreshAsync).intercept(chain -> null);
            MainHook.log("Hooked SplashAdPlacementManager.refreshAsync");
        } catch (Throwable ignored) {}
    }

    private static void hookSilentTaskEngine(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> engineClass = Class.forName("com.realtech.xiaocan.ad.silent.SilentTaskEngine", false, cl);
            for (Method m : engineClass.getDeclaredMethods()) {
                if ("start".equals(m.getName()) || "init".equals(m.getName()) || "scheduleNext".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> null);
                }
            }
            MainHook.log("Hooked SilentTaskEngine");
        } catch (Throwable ignored) {}
    }

    private static void hookFeedAndBannerAds(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> holderClass = Class.forName("com.realtech.promotion.adapters.holder.HomeFeedAdItemHolder", false, cl);
            for (Method m : holderClass.getDeclaredMethods()) {
                if ("bind".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object holder = chain.getThisObject();
                        if (holder != null) {
                            try {
                                Method getBinding = holder.getClass().getMethod("getBinding");
                                Object binding = getBinding.invoke(holder);
                                Method getRoot = binding.getClass().getMethod("getRoot");
                                View root = (View) getRoot.invoke(binding);
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
                        return null;
                    });
                    MainHook.log("Hooked HomeFeedAdItemHolder.bind");
                    break;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> bannerHolderClass = Class.forName("com.realtech.promotion.adapters.holder.HomeHeadBannerHolder", false, cl);
            for (Method m : bannerHolderClass.getDeclaredMethods()) {
                if ("bind".equals(m.getName()) || "renderBanner".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object holder = chain.getThisObject();
                        if (holder != null) {
                            try {
                                Method getBinding = holder.getClass().getMethod("getBinding");
                                Object binding = getBinding.invoke(holder);
                                Method getRoot = binding.getClass().getMethod("getRoot");
                                View root = (View) getRoot.invoke(binding);
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
                        return null;
                    });
                    MainHook.log("Hooked HomeHeadBannerHolder");
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void hookUserCommerceGrid(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> gridModuleClass = Class.forName("com.realtech.user.pages.userv2.UserCommerceGridModule", false, cl);
            for (Method m : gridModuleClass.getDeclaredMethods()) {
                if ("render".equals(m.getName()) || "load".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object module = chain.getThisObject();
                        if (module != null) {
                            try {
                                Field bf = module.getClass().getField("binding");
                                Object binding = bf.get(module);
                                if (binding != null) {
                                    Method getRoot = binding.getClass().getMethod("getRoot");
                                    View root = (View) getRoot.invoke(binding);
                                    if (root != null) {
                                        root.setVisibility(View.GONE);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                        return null;
                    });
                }
            }
            MainHook.log("Hooked UserCommerceGridModule");
        } catch (Throwable ignored) {}
    }

    private static void hookFloatingAds(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> floatingClass = Class.forName("com.realtech.xiaocan.ad.floating.FloatingAdManager", false, cl);
            for (Method m : floatingClass.getDeclaredMethods()) {
                if ("show".equals(m.getName()) || "load".equals(m.getName()) || "attach".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> null);
                }
            }
            MainHook.log("Hooked FloatingAdManager");
        } catch (Throwable ignored) {}
    }

    private static void hookSearchRightBanner(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> barLayoutClass = Class.forName("com.realtech.promotion.widget.HomeAppBarLayoutView", false, cl);
            for (Method m : barLayoutClass.getDeclaredMethods()) {
                if ("renderSearchRightBanner".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object barLayout = chain.getThisObject();
                        try {
                            Field searchViewField = barLayout.getClass().getField("searchView");
                            Object searchView = searchViewField.get(barLayout);
                            if (searchView != null) {
                                Field bannerField = searchView.getClass().getField("searchRightBanner");
                                Object banner = bannerField.get(searchView);
                                if (banner instanceof View) {
                                    ((View) banner).setVisibility(View.GONE);
                                }
                                Field ivField = searchView.getClass().getField("ivSearchRight");
                                Object iv = ivField.get(searchView);
                                if (iv instanceof View) {
                                    ((View) iv).setVisibility(View.GONE);
                                }
                                MainHook.log("SearchRightBanner hidden");
                            }
                        } catch (Throwable t) {
                            MainHook.log("Failed to hide SearchRightBanner: " + t);
                        }
                        return null;
                    });
                    MainHook.log("Hooked HomeAppBarLayoutView.renderSearchRightBanner");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook HomeAppBarLayoutView: " + t);
        }
    }

    private static void hookFlutterAdPlugins(XposedInterface xposed, ClassLoader cl) {
        // AmSdkPlugin
        try {
            Class<?> amPlugin = Class.forName("com.realtech.am_sdk_plugin.AmSdkPlugin", false, cl);
            for (Method m : amPlugin.getDeclaredMethods()) {
                if ("initSdk".equals(m.getName()) || "registerInterstitial".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> null);
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> amHandler = Class.forName("com.realtech.am_sdk_plugin.AmInterstitialAdHandler", false, cl);
            for (Method m : amHandler.getDeclaredMethods()) {
                if ("loadAd".equals(m.getName()) || "show".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> null);
                }
            }
        } catch (Throwable ignored) {}

        // WindmillAdPluginDelegate
        try {
            Class<?> wmDelegate = Class.forName("com.windmill.windmill_ad_plugin.WindmillAdPluginDelegate", false, cl);
            for (Method m : wmDelegate.getDeclaredMethods()) {
                if ("onMethodCall".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object call = chain.getArg(0);
                        Object result = chain.getArg(1);
                        if (call != null && result != null) {
                            try {
                                Method getMethod = call.getClass().getMethod("getMethod");
                                String methodName = (String) getMethod.invoke(call);
                                if (methodName != null && (methodName.contains("load") || methodName.contains("show") || methodName.contains("Ready"))) {
                                    Method success = result.getClass().getMethod("success", Object.class);
                                    success.invoke(result, (Object) null);
                                    return null;
                                }
                            } catch (Throwable ignored) {}
                        }
                        return chain.proceed();
                    });
                    break;
                }
            }
        } catch (Throwable ignored) {}

        // WindMillNativeAdViewFactory -> Return empty invisible PlatformView for any Windmill ad in Flutter!
        try {
            Class<?> factoryClass = Class.forName("com.windmill.windmill_ad_plugin.WindMillNativeAdViewFactory", false, cl);
            Class<?> platformViewInterface = Class.forName("io.flutter.plugin.platform.PlatformView", false, cl);

            for (Method m : factoryClass.getDeclaredMethods()) {
                if ("create".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Context ctx = (Context) chain.getArg(0);
                        MainHook.log("WindMillNativeAdViewFactory.create intercepted, returning dummy empty view");
                        return Proxy.newProxyInstance(
                                cl,
                                new Class<?>[]{platformViewInterface},
                                (proxy, method, args) -> {
                                    if ("getView".equals(method.getName())) {
                                        View v = new View(ctx);
                                        v.setVisibility(View.GONE);
                                        v.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
                                        return v;
                                    }
                                    if ("dispose".equals(method.getName())) {
                                        return null;
                                    }
                                    return null;
                                }
                        );
                    });
                    MainHook.log("Hooked WindMillNativeAdViewFactory.create");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook WindMillNativeAdViewFactory: " + t);
        }

        // Windmill BannerAd & NativeAd
        try {
            Class<?> bannerClass = Class.forName("com.windmill.windmill_ad_plugin.banner.BannerAd", false, cl);
            for (Method m : bannerClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("load".equals(mName) || "showAd".equals(mName)) {
                    xposed.hook(m).intercept(chain -> null);
                } else if ("isReady".equals(mName)) {
                    xposed.hook(m).intercept(chain -> Boolean.FALSE);
                }
            }
            MainHook.log("Hooked BannerAd");
        } catch (Throwable ignored) {}

        try {
            Class<?> nativeClass = Class.forName("com.windmill.windmill_ad_plugin.feedAd.NativeAd", false, cl);
            for (Method m : nativeClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("load".equals(mName) || "showAd".equals(mName)) {
                    xposed.hook(m).intercept(chain -> null);
                } else if ("isReady".equals(mName)) {
                    xposed.hook(m).intercept(chain -> Boolean.FALSE);
                }
            }
            MainHook.log("Hooked NativeAd");
        } catch (Throwable ignored) {}

        // WMBannerView
        try {
            Class<?> wmBannerView = Class.forName("com.windmill.sdk.banner.WMBannerView", false, cl);
            for (Method m : wmBannerView.getDeclaredMethods()) {
                if ("loadAd".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> null);
                }
            }
            MainHook.log("Hooked WMBannerView.loadAd");
        } catch (Throwable ignored) {}

        // NativeAdBridge
        try {
            Class<?> adBridge = Class.forName("com.realtech.xiaocan.flutter.NativeAdBridge", false, cl);
            for (Method m : adBridge.getDeclaredMethods()) {
                if ("createBridge$lambda$3".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.getArg(1);
                        if (result != null) {
                            try {
                                Method success = result.getClass().getMethod("success", Object.class);
                                success.invoke(result, (Object) null);
                            } catch (Throwable ignored) {}
                        }
                        return null;
                    });
                    MainHook.log("Hooked NativeAdBridge.createBridge$lambda$3");
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void hookAdActivities(XposedInterface xposed) {
        try {
            Method actOnCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            xposed.hook(actOnCreate).intercept(chain -> {
                Activity activity = (Activity) chain.getThisObject();
                if (activity != null) {
                    String name = activity.getClass().getName().toLowerCase(Locale.ROOT);
                    if (isAdActivityName(name)) {
                        MainHook.log("Auto finishing ad activity: " + activity.getClass().getName());
                        activity.finish();
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {}
    }

    private static boolean isAdActivityName(String lowerName) {
        return lowerName.contains("ttfulladactivity")
                || lowerName.contains("tobidrewardvideoactivity")
                || lowerName.contains("douyindramaactivity")
                || lowerName.contains("openadsdk")
                || lowerName.contains("kwad.sdk")
                || lowerName.contains("sigmob")
                || lowerName.contains("beizi")
                || lowerName.contains("qq.e.ads")
                || lowerName.contains("mcto.sspsdk")
                || lowerName.contains("aggmoread")
                || lowerName.contains("gameley.template")
                || lowerName.contains("ubix.ssp")
                || lowerName.contains("wj.mobads")
                || lowerName.contains("alliance.ssp");
    }
}
