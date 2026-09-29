package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

public final class PopupBlocker {
    private PopupBlocker() {}

    public static void install(XposedInterface xposed, ClassLoader classLoader) {
        hookDialogUtils(xposed, classLoader);
        hookAppUpdateService(xposed, classLoader);
        hookMainViewModelPopups(xposed, classLoader);
        hookSpecificDialogClasses(xposed, classLoader);
        hookKuiklyDialogQueue(xposed, classLoader);
        hookMiniAppJump(xposed, classLoader);
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
                for (Method m : dClass.getDeclaredMethods()) {
                    if ("show".equals(m.getName())) {
                        xposed.hook(m).intercept(chain -> {
                            MainHook.log("Blocked " + cName + ".show");
                            dismissIfDialog(chain.getThisObject());
                            return null;
                        });
                    } else if ("onCreate".equals(m.getName()) && m.getParameterCount() == 1) {
                        xposed.hook(m).intercept(chain -> {
                            MainHook.log("Intercepted " + cName + ".onCreate");
                            dismissIfDialog(chain.getThisObject());
                            return null;
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

    /**
     * The app launches Meituan/JD/Eleme via WeChat mini-apps after enrollment
     * or store navigation. Block every openMiniApp call at the common entry.
     */
    private static void hookMiniAppJump(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> funcs = Class.forName("com.realtech.common.ui.mp.WeChatFuncs", false, cl);
            Class<?> unit = Class.forName("kotlin.Unit", false, cl);
            Object unitInstance = unit.getField("INSTANCE").get(null);
            int hooked = 0;
            for (Method m : funcs.getDeclaredMethods()) {
                if (!m.getName().startsWith("openMiniApp")) continue;
                xposed.hook(m).intercept(chain -> {
                    MainHook.log("Blocked WeChatFuncs mini-app open");
                    return unitInstance;
                });
                hooked++;
            }
            MainHook.log("Hooked WeChatFuncs openMiniApp x" + hooked);
        } catch (Throwable t) {
            MainHook.log("Failed to hook WeChatFuncs: " + t);
        }
        try {
            Class<?> wxApi = Class.forName("com.tencent.mm.opensdk.openapi.WXApiImplV10", false, cl);
            for (Method m : wxApi.getMethods()) {
                if (!"sendReq".equals(m.getName())) continue;
                xposed.hook(m).intercept(chain -> {
                    Object req = chain.getArgs().size() > 0 ? chain.getArg(0) : null;
                    if (req == null) return chain.proceed();
                    String type = req.getClass().getSimpleName();
                    if (type.contains("LaunchMiniProgram")) {
                        MainHook.log("Blocked WXLaunchMiniProgram via IWXAPI");
                        return Boolean.TRUE;
                    }
                    return chain.proceed();
                });
                MainHook.log("Hooked IWXAPI.sendReq");
                break;
            }
        } catch (Throwable t) {
            MainHook.log("Failed to hook IWXAPI: " + t);
        }
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
