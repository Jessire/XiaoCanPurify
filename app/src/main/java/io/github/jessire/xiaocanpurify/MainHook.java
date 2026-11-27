package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class MainHook extends XposedModule {
    public static final String TARGET_PACKAGE = "com.realtech.xiaocan";
    public static final String LOG_TAG = "XiaoCanPurify";
    private static final AtomicBoolean HOOKED = new AtomicBoolean(false);
    private static volatile MainHook instance;
    private static volatile boolean targetMainProcess;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        targetMainProcess = TARGET_PACKAGE.equals(param.getProcessName());
        instance = this;
        log("XiaoCanPurify API 102 module loaded.");
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        instance = this;
        log("Target package ready: " + param.getPackageName());

        hookLifecycle(this);
        ClassLoader initialLoader = param.getClassLoader();
        if (LayoutSettings.hasBaseline() && canLoadTargetClasses(initialLoader)) {
            installAll(this, initialLoader);
        }
        // The lifecycle hooks also handle late-unpacked host classes.
    }

    private static boolean canLoadTargetClasses(ClassLoader cl) {
        if (cl == null) return false;
        try {
            Class.forName("com.realtech.xiaocan.MainActivity", false, cl);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void hookLifecycle(XposedInterface xposed) {
        try {
            Method attachBaseContext = android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            xposed.hook(attachBaseContext).intercept(chain -> {
                if (!(chain.getThisObject() instanceof Application)) return chain.proceed();
                Context context = (Context) chain.getArg(0);
                LayoutSettings.initialize(context);
                Object result = chain.proceed();
                if (context != null) {
                    ClassLoader cl = context.getClassLoader();
                    if (LayoutSettings.hasBaseline() && canLoadTargetClasses(cl)) {
                        installAll(xposed, cl);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            log("Hook Application.attachBaseContext failed: " + t);
        }

        try {
            Method onCreate = Application.class.getDeclaredMethod("onCreate");
            xposed.hook(onCreate).intercept(chain -> {
                Object result = chain.proceed();
                Application app = (Application) chain.getThisObject();
                if (app != null) {
                    LayoutSettings.initialize(app);
                    ClassLoader cl = app.getClassLoader();
                    if (LayoutSettings.hasBaseline() && canLoadTargetClasses(cl)) {
                        installAll(xposed, cl);
                    }
                }
                return result;
            });
        } catch (Throwable t) {
            log("Hook Application.onCreate failed: " + t);
        }

        try {
            Method actOnCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            xposed.hook(actOnCreate).intercept(chain -> {
                Activity activity = (Activity) chain.getThisObject();
                if (activity != null) {
                    LayoutSettings.initialize(activity);
                    ClassLoader cl = activity.getClassLoader();
                    if (LayoutSettings.hasBaseline() && canLoadTargetClasses(cl)) {
                        installAll(xposed, cl);
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log("Hook Activity.onCreate failed: " + t);
        }
    }

    public static synchronized void installAll(XposedInterface xposed, ClassLoader classLoader) {
        if (!HOOKED.compareAndSet(false, true)) {
            return;
        }
        log("Target classes available, installing purifier hooks with ClassLoader: " + classLoader);

        try {
            AdBlocker.install(xposed, classLoader);
            log("AdBlocker installed successfully.");
        } catch (Throwable t) {
            log("AdBlocker install error: " + t);
        }

        try {
            PopupBlocker.install(xposed, classLoader);
            log("PopupBlocker installed successfully.");
        } catch (Throwable t) {
            log("PopupBlocker install error: " + t);
        }

        if (targetMainProcess) {
            HostSettingsEntry.install(xposed, classLoader);
            MatchPlacementGuard.install(xposed, classLoader);
            BenefitWebJumpGuard.install(xposed, classLoader);

            try {
                ExtraMarketingPopupBlocker.install(xposed, classLoader);
            } catch (Throwable t) {
                log("ExtraMarketingPopupBlocker install error: " + t);
            }
            HomeFreeOrderPopupBlocker.install(xposed, classLoader);
            WithdrawFollowPopup.install(xposed, classLoader);
            FoodDetectiveAutoJump.install(xposed, classLoader);
            FoodDetectiveWebExit.install(xposed, classLoader);
            PlaceOrderAdPurifier.install(xposed, classLoader);
        }

        try {
            BottomBarPurifier.install(xposed, classLoader);
            log("BottomBarPurifier installed successfully.");
        } catch (Throwable t) {
            log("BottomBarPurifier install error: " + t);
        }

        try {
            HomePagePurifier.install(xposed, classLoader);
            log("HomePagePurifier installed successfully.");
        } catch (Throwable t) {
            log("HomePagePurifier install error: " + t);
        }

        try {
            OrderPagePurifier.install(xposed, classLoader);
            log("OrderPagePurifier installed successfully.");
        } catch (Throwable t) {
            log("OrderPagePurifier install error: " + t);
        }

        try {
            DetailPagePurifier.install(xposed, classLoader);
            log("DetailPagePurifier installed successfully.");
        } catch (Throwable t) {
            log("DetailPagePurifier install error: " + t);
        }

        try {
            UserPagePurifier.install(xposed, classLoader);
            log("UserPagePurifier installed successfully.");
        } catch (Throwable t) {
            log("UserPagePurifier install error: " + t);
        }

        try {
            NetworkAdInterceptor.install(xposed, classLoader);
            log("NetworkAdInterceptor installed successfully.");
        } catch (Throwable t) {
            log("NetworkAdInterceptor install error: " + t);
        }


        try {
            FlutterPageGuard.install(xposed, classLoader);
            log("FlutterPageGuard installed successfully.");
        } catch (Throwable t) {
            log("FlutterPageGuard install error: " + t);
        }

        log("All XiaoCanPurify hooks successfully initialized!");
    }

    public static void log(String message) {
        MainHook hook = instance;
        if (hook != null) {
            try {
                hook.log(Log.INFO, LOG_TAG, message);
                return;
            } catch (Throwable ignored) {
            }
        }
        Log.i(LOG_TAG, message);
    }
}
