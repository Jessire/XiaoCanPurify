package io.github.jessire.xiaocanpurify;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

public final class UserPagePurifier {
    private UserPagePurifier() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        hookUserV2Fragment(xposed, classLoader);
        hookUserModules(xposed, classLoader);
        hookUserInfoModule(xposed, classLoader);
    }

    private static void hookUserV2Fragment(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> userFragClass = Class.forName("com.realtech.user.pages.UserV2Fragment", false, cl);

            for (Method m : userFragClass.getDeclaredMethods()) {
                if ("lazyInitView".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        Object fragment = chain.getThisObject();
                        purgeUserViews(fragment);
                        return result;
                    });
                    MainHook.log("Hooked UserV2Fragment.lazyInitView");
                    break;
                }
            }

            for (Method m : userFragClass.getDeclaredMethods()) {
                if ("renderState".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object state = chain.getArg(0);
                        if (state != null) {
                            String sName = state.getClass().getSimpleName();
                            if (sName.contains("Menu")
                                    || sName.contains("Service")
                                    || sName.contains("BWC")
                                    || sName.contains("Notice")
                                    || sName.contains("NewUserTip")
                                    || sName.contains("Commerce")
                                    || sName.contains("BaiduAd")
                                    || sName.contains("MatchPlacement")
                                    || sName.contains("Challenge")
                                    || sName.contains("YuanBao")) {
                                MainHook.log("Filtered UserCenterViewState: " + sName);
                                return null;
                            }
                        }
                        return chain.proceed();
                    });
                    MainHook.log("Hooked UserV2Fragment.renderState");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook UserV2Fragment: " + t);
        }
    }

    private static void purgeUserViews(Object fragment) {
        try {
            Field bindingField = fragment.getClass().getField("binding");
            Object binding = bindingField.get(fragment);
            if (binding == null) return;

            hideBindingField(binding, "adContainer");
            hideBindingField(binding, "bqtContainer");
            hideBindingField(binding, "commerceGridContainer");
            hideBindingField(binding, "bannerNotice");
            hideBindingField(binding, "ivFloatingWindow");
            hideBindingField(binding, "frameBottomBonus");
            hideBindingLayout(binding, "layoutNewUserTip");
            hideBindingLayout(binding, "layoutMenu");
            hideBindingLayout(binding, "layoutUserMerchant");
            hideBindingLayout(binding, "layoutServer");

            // Hide "赚钱" button and "待领蚕豆" badge next to 提现
            try {
                Field silkField = binding.getClass().getField("layoutSilkEarn");
                Object silkBinding = silkField.get(binding);
                if (silkBinding != null) {
                    hideBindingField(silkBinding, "tvMakeProfit");
                    hideBindingField(silkBinding, "tvMakeProfitClaimBadge");
                }
            } catch (Throwable ignored) {}

            MainHook.log("Successfully purged unwanted user page views");
        } catch (Throwable t) {
            MainHook.log("Error in purgeUserViews: " + t);
        }
    }

    private static void hookUserInfoModule(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> moduleClass = Class.forName("com.realtech.user.pages.userv2.UserInfoModule", false, cl);
            for (Method m : moduleClass.getDeclaredMethods()) {
                if ("render".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        Object module = chain.getThisObject();
                        if (module != null) {
                            try {
                                Field silkField = module.getClass().getField("silkBinding");
                                Object silkBinding = silkField.get(module);
                                if (silkBinding != null) {
                                    hideBindingField(silkBinding, "tvMakeProfit");
                                    hideBindingField(silkBinding, "tvMakeProfitClaimBadge");
                                }
                            } catch (Throwable ignored) {}
                        }
                        return result;
                    });
                    MainHook.log("Hooked UserInfoModule.render (hidden tvMakeProfit & badge)");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook UserInfoModule: " + t);
        }
    }

    private static void hideBindingField(Object binding, String fieldName) {
        try {
            Field field = binding.getClass().getField(fieldName);
            View view = (View) field.get(binding);
            if (view != null) {
                view.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {}
    }

    private static void hideBindingLayout(Object binding, String fieldName) {
        try {
            Field field = binding.getClass().getField(fieldName);
            Object layoutBinding = field.get(binding);
            if (layoutBinding != null) {
                Method getRoot = layoutBinding.getClass().getMethod("getRoot");
                View root = (View) getRoot.invoke(layoutBinding);
                if (root != null) {
                    root.setVisibility(View.GONE);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void hookUserModules(XposedInterface xposed, ClassLoader cl) {
        hookModuleRender(xposed, cl, "com.realtech.user.pages.userv2.UserMenuModule");
        hookModuleRender(xposed, cl, "com.realtech.user.pages.userv2.UserServiceModule");
        hookModuleRender(xposed, cl, "com.realtech.user.pages.userv2.UserBWCModule");
        hookModuleRender(xposed, cl, "com.realtech.user.pages.userv2.UserNoticeModule");
        hookModuleRender(xposed, cl, "com.realtech.user.pages.userv2.UserCommerceGridModule");
        hookModuleRender(xposed, cl, "com.realtech.user.pages.userv2.UserOtherMatchModule");
        hookModuleRender(xposed, cl, "com.realtech.user.pages.userv2.UserBqtModule");
        hookModuleRender(xposed, cl, "com.realtech.user.pages.userv2.NewUserTipModule");
    }

    private static void hookModuleRender(XposedInterface xposed, ClassLoader cl, String className) {
        try {
            Class<?> moduleClass = Class.forName(className, false, cl);
            for (Method m : moduleClass.getDeclaredMethods()) {
                String mName = m.getName();
                if ("render".equals(mName) || "load".equals(mName) || "initView".equals(mName)) {
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
                    MainHook.log("Hooked " + className + "." + mName + " -> silenced");
                }
            }
        } catch (Throwable ignored) {}
    }
}
