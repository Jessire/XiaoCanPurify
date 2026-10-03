package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

public final class PopupBlocker {
    private static final Map<Object, Boolean> TAOBAO_INSTALL_PROMPTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PopupBlocker() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        hookDialogUtils(xposed, classLoader);
        hookAppUpdateService(xposed, classLoader);
        hookMainViewModelPopups(xposed, classLoader);
        hookSpecificDialogClasses(xposed, classLoader);
        hookKuiklyDialogQueue(xposed, classLoader);
        hookTaobaoInstallPrompt(xposed, classLoader);
        EnrollmentJumpGuard.install(xposed, classLoader);
    }

    private static void hookTaobaoInstallPrompt(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> customDialog = Class.forName("com.kongzue.dialogx.dialogs.CustomDialog", false, cl);
            Class<?> redPackUtils = Class.forName("com.realtech.promotion.utils.RedPackUtils", false, cl);
            Object unit = Class.forName("kotlin.Unit", false, cl).getField("INSTANCE").get(null);
            Method configure = redPackUtils.getDeclaredMethod("toElemRedPack$lambda$1",
                    Activity.class, String.class, customDialog);
            int showHooks = 0;
            for (Method method : customDialog.getDeclaredMethods()) {
                if (!"show".equals(method.getName()) || Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != customDialog) continue;
                xposed.hook(method).intercept(chain -> {
                    Object dialog = chain.getThisObject();
                    if (!TAOBAO_INSTALL_PROMPTS.containsKey(dialog)) return chain.proceed();
                    MainHook.log("Blocked Taobao install coupon prompt");
                    return dialog;
                });
                showHooks++;
            }
            if (showHooks == 0) throw new NoSuchMethodException("CustomDialog.show");
            xposed.hook(configure).intercept(chain -> {
                Object dialog = chain.getArg(2);
                if (dialog == null) return chain.proceed();
                // Even dismissing this prompt launches a coupon mini-app. Do not attach its callbacks.
                TAOBAO_INSTALL_PROMPTS.put(dialog, true);
                return unit;
            });
            MainHook.log("Taobao install prompt guard installed");
        } catch (Throwable t) {
            MainHook.log("Taobao install prompt hook unavailable: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Hook DialogX CustomDialog to dismiss marketing/promo popups (e.g. "大额外卖券", OPS_POPUP)
     * while safely triggering onDismiss so coroutines/continuations resume without hanging.
     */
    private static void hookCustomDialogX(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> customDialogClass = Class.forName("com.kongzue.dialogx.dialogs.CustomDialog", false, cl);
            Method dismissMethod = customDialogClass.getMethod("dismiss");

            for (Method m : customDialogClass.getDeclaredMethods()) {
                if ("show".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object dialog = chain.getThisObject();
                        MainHook.log("Intercepted DialogX CustomDialog.show, auto-dismissing");
                        try {
                            dismissMethod.invoke(dialog);
                        } catch (Throwable t) {
                            MainHook.log("CustomDialog dismiss error: " + t);
                        }
                        return dialog;
                    });
                }
            }
            MainHook.log("Hooked DialogX CustomDialog.show");
        } catch (Throwable t) {
            MainHook.log("Failed to hook DialogX CustomDialog: " + t);
        }
    }

    private static void hookDialogUtils(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> dialogUtilsClass = Class.forName("com.realtech.common.dialog.DialogUtils", false, cl);
            for (Method m : dialogUtilsClass.getDeclaredMethods()) {
                if ("addDialog".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        String tag = null;
                        if (args != null) {
                            for (Object arg : args) {
                                if (arg instanceof String) {
                                    tag = (String) arg;
                                    break;
                                }
                            }
                        }
                        if (isMarketingTag(tag)) {
                            MainHook.log("Blocked DialogUtils dialog tag: " + tag);
                            return null;
                        }
                        return chain.proceed();
                    });
                    MainHook.log("Hooked DialogUtils.addDialog");
                    break;
                }
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook DialogUtils: " + t);
        }
    }

    private static void hookAppUpdateService(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> updateSvc = Class.forName("com.realtech.xiaocan.service.AppUpdateServiceImpl", false, cl);
            for (Method m : updateSvc.getDeclaredMethods()) {
                if ("checkUpdate".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        MainHook.log("Blocked AppUpdateServiceImpl.checkUpdate");
                        return null;
                    });
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void hookMainViewModelPopups(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> vmClass = Class.forName("com.realtech.xiaocan.MainViewModel", false, cl);
            String[] targetMethods = {
                    "checkUpdate",
                    "checkSyceeUpdateDialog",
                    "checkSchoolStarts",
                    "takeGiftReq"
            };
            for (Method m : vmClass.getDeclaredMethods()) {
                for (String tm : targetMethods) {
                    if (tm.equals(m.getName())) {
                        xposed.hook(m).intercept(chain -> {
                            MainHook.log("Blocked MainViewModel." + tm);
                            return null;
                        });
                        break;
                    }
                }
            }
            MainHook.log("Hooked MainViewModel popup triggers");
        } catch (Throwable t) {
            MainHook.log("Failed to hook MainViewModel popup triggers: " + t);
        }
    }

    private static void hookSpecificDialogClasses(XposedInterface xposed, ClassLoader cl) {
        String[] dialogClasses = {
                "com.realtech.promotion.dialog.SharerHomePopupDialog",
                "com.realtech.xiaocan.dialog.agency.FirstOrderFullAmountAgencyDialog",
                "com.realtech.xiaocan.dialog.DouyinMallBonusDialog",
                "com.realtech.common.ui.dialog.update.AppUpdateDialog",
                "com.realtech.promotion.dialog.OpenSchoolshareDialog",
                "com.realtech.promotion.dialog.details.AnnualReportShareDialog",
                "com.realtech.promotion.dialog.share.AnnualReportSecondShareDialog",
                "com.realtech.promotion.dialog.share.SecondShareDialog",
                "com.realtech.vip.dialog.SyceeUpdateNoticeDialog"
        };

        for (String cName : dialogClasses) {
            try {
                Class<?> dClass = Class.forName(cName, false, cl);
                for (Method m : dClass.getMethods()) {
                    if ("show".equals(m.getName())) {
                        xposed.hook(m).intercept(chain -> {
                            Object dialog = chain.getThisObject();
                            if (!dClass.isInstance(dialog)) return chain.proceed();
                            MainHook.log("Blocked " + cName + ".show");
                            return m.getReturnType().isInstance(dialog) ? dialog : null;
                        });
                    }
                }
                MainHook.log("Hooked dialog class: " + cName);
            } catch (Throwable ignored) {}
        }
    }

    private static void hookKuiklyDialogQueue(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> gdqClass = Class.forName("com.realtech.xiaocankuikly.utils.GlobalDialogQueue", false, cl);
            for (Method m : gdqClass.getDeclaredMethods()) {
                if ("show".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object dialogInfo = chain.getArg(0);
                        if (isMarketingDialogInfo(dialogInfo)) {
                            MainHook.log("Blocked GlobalDialogQueue marketing dialog");
                            return null;
                        }
                        return chain.proceed();
                    });
                    MainHook.log("Hooked GlobalDialogQueue.show");
                    break;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> dqmClass = Class.forName("com.realtech.xiaocankuikly.utils.DialogQueueManager", false, cl);
            for (Method m : dqmClass.getDeclaredMethods()) {
                if ("showDialog".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object dialogInfo = chain.getArg(0);
                        if (isMarketingDialogInfo(dialogInfo)) {
                            MainHook.log("Blocked DialogQueueManager.showDialog marketing dialog");
                            return null;
                        }
                        return chain.proceed();
                    });
                    MainHook.log("Hooked DialogQueueManager.showDialog");
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isMarketingTag(String tag) {
        if (tag == null) return false;
        String t = tag.toLowerCase(Locale.ROOT);
        return t.contains("maintomin")
                || t.startsWith("home_up_")
                || t.contains("openschoolshare")
                || t.contains("past_board_permission")
                || t.contains("timeerror")
                || t.contains("app_update")
                || t.contains("update")
                || t.contains("bonus")
                || t.contains("sycee")
                || t.contains("cake")
                || t.contains("popup")
                || t.contains("gift")
                || t.contains("red_packet")
                || t.contains("agency")
                || t.contains("first_order")
                || t.contains("share")
                || t.contains("annual")
                || t.contains("ops_popup")
                || t.contains("invite");
    }

    private static boolean isMarketingDialogInfo(Object dialogInfo) {
        if (dialogInfo == null) return false;
        String str = dialogInfo.toString().toLowerCase(Locale.ROOT);
        return str.contains("bonus")
                || str.contains("cake")
                || str.contains("invite")
                || str.contains("red_packet")
                || str.contains("redpacket")
                || str.contains("vip")
                || str.contains("sycee")
                || str.contains("update")
                || str.contains("popup")
                || str.contains("gift")
                || str.contains("share")
                || str.contains("ops")
                || str.contains("annual");
    }

    private static void dismissIfDialog(Object obj) {
        if (obj instanceof Dialog) {
            try {
                ((Dialog) obj).dismiss();
            } catch (Throwable ignored) {}
        }
    }
}
