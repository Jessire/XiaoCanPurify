import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.libxposed.api.XposedInterface;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public final class RegressionTests {
    public static final class Refresh {
        public boolean enabled = true;
        public Refresh setEnableRefresh(boolean value) { enabled = value; return this; }
        public Refresh setEnableOverScrollDrag(boolean value) { return this; }
        public Refresh setEnableOverScrollBounce(boolean value) { return this; }
    }

    public static final class HomeBinding {
        public final Refresh refresh = new Refresh();
    }

    private static final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();
    private static final ClassLoader loader = RegressionTests.class.getClassLoader();
    private static final XposedInterface xposed = (XposedInterface) Proxy.newProxyInstance(loader,
            new Class<?>[]{XposedInterface.class}, (p, m, a) -> {
                if (!m.getName().equals("hook")) return null;
                Method target = (Method) a[0];
                return Proxy.newProxyInstance(loader, new Class<?>[]{XposedInterface.HookBuilder.class},
                        (bp, bm, ba) -> {
                            if (bm.getName().equals("intercept")) {
                                hooks.put(target, (XposedInterface.Hooker) ba[0]);
                                return null;
                            }
                            return bp;
                        });
            });

    private static final class Reply implements MethodChannel.Result {
        int replies;
        Object value;
        String error;
        public void success(Object value) { replies++; this.value = value; }
        public void error(String code, String message, Object details) { replies++; error = code; }
        public void notImplemented() { replies++; error = "notImplemented"; }
    }

    private static void install(String owner, String name) throws Exception {
        Method m = Class.forName("io.github.jessire.xiaocanpurify." + owner)
                .getDeclaredMethod(name, XposedInterface.class, ClassLoader.class);
        m.setAccessible(true);
        m.invoke(null, xposed, loader);
    }

    private static Object call(Method target, Object receiver, boolean expectedProceed, Object... args) throws Throwable {
        XposedInterface.Hooker hook = hooks.get(target);
        if (hook == null) throw new AssertionError("Missing hook: " + target);
        boolean[] proceeded = {false};
        XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(loader,
                new Class<?>[]{XposedInterface.Chain.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "getThisObject": return receiver;
                        case "getExecutable": return target;
                        case "getArg": return args[(Integer) a[0]];
                        case "getArgs": return java.util.Arrays.asList(args);
                        case "proceed": proceeded[0] = true; return null;
                        default: throw new AssertionError(m.getName());
                    }
                });
        Object result = hook.intercept(chain);
        if (proceeded[0] != expectedProceed) throw new AssertionError("Unexpected original execution: " + target);
        return result;
    }

    private static void refresh() throws Throwable {
        HomeBinding binding = new HomeBinding();
        Class<?> home = Class.forName("io.github.jessire.xiaocanpurify.HomePagePurifier");
        Method enforce = home.getDeclaredMethod("enforceHome", Object.class);
        enforce.setAccessible(true);
        enforce.invoke(null, binding);
        if (!binding.refresh.enabled) throw new AssertionError("Home cleanup disabled ordinary pull-to-refresh");
        binding.refresh.enabled = false;
        enforce.invoke(null, binding);
        if (binding.refresh.enabled) throw new AssertionError("Overrode a host-owned temporary refresh lock");
        install("HomePagePurifier", "hookRefreshLayout");
        Method gate = com.realtech.promotion.pages.home.viewmodel.HomeViewModel.class.getMethod("canUseHomeSecondFloor");
        if (!Boolean.FALSE.equals(call(gate, new com.realtech.promotion.pages.home.viewmodel.HomeViewModel(), false)))
            throw new AssertionError("Second floor still enabled");
    }

    private static void ads() throws Throwable {
        install("AdBlocker", "hookFlutterAdPlugins");
        Class<?> ad = com.windmill.windmill_ad_plugin.interstitial.InterstitialAd.class;
        Method m = ad.getMethod("onMethodCall", MethodCall.class, MethodChannel.Result.class);
        for (String method : List.of("load", "showAd", "isReady", "initRequest", "getCacheAdInfoList")) {
            Reply reply = new Reply();
            call(m, ad.getConstructor().newInstance(), false, new MethodCall(method, Map.of()), reply);
            if (reply.replies != 1) throw new AssertionError("Unresolved/duplicate ad reply: " + method);
            if (method.equals("isReady") && !Boolean.FALSE.equals(reply.value)) throw new AssertionError("Ad readiness must be false");
            if (Boolean.TRUE.equals(reply.value)) throw new AssertionError("Must not simulate ad success");
        }
        Class<?> delegate = com.windmill.windmill_ad_plugin.WindmillAdPluginDelegate.class;
        call(delegate.getMethod("onMethodCall", MethodCall.class, MethodChannel.Result.class),
                delegate.getConstructor().newInstance(), true, new MethodCall("getSdkVersion", Map.of()), new Reply());
    }

    private static void popup() throws Throwable {
        Method marketing = Class.forName("io.github.jessire.xiaocanpurify.PopupBlocker").getDeclaredMethod("isMarketingTag", String.class);
        marketing.setAccessible(true);
        if (!Boolean.TRUE.equals(marketing.invoke(null, "home_up_7743"))) throw new AssertionError("Observed HomeUp campaign still enters popup queue");
        if (!Boolean.FALSE.equals(marketing.invoke(null, "payment_confirmation"))) throw new AssertionError("Payment prompt must remain available");
        install("PopupBlocker", "hookSpecificDialogClasses");
        Method show = com.realtech.promotion.dialog.BasePopup.class.getMethod("show");
        Object popup = new com.realtech.promotion.dialog.SharerHomePopupDialog();
        if (call(show, popup, false) != popup) throw new AssertionError("Fluent popup return contract changed");
        call(show, new com.realtech.promotion.dialog.BasePopup(), true);
    }

    private static void policies() { io.github.jessire.xiaocanpurify.PolicyTests.run(); }

    private static void taobaoPrompt() throws Throwable {
        install("PopupBlocker", "hookTaobaoInstallPrompt");
        var dialog = new com.kongzue.dialogx.dialogs.CustomDialog();
        Method configure = com.realtech.promotion.utils.RedPackUtils.class.getMethod("toElemRedPack$lambda$1",
                android.app.Activity.class, String.class, dialog.getClass());
        if (call(configure, null, false, null, "unused-local-test", dialog) != kotlin.Unit.INSTANCE)
            throw new AssertionError("Popup builder return contract changed");
        Method show = dialog.getClass().getMethod("show", android.app.Activity.class);
        if (call(show, dialog, false, (Object) null) != dialog)
            throw new AssertionError("Blocked popup must preserve fluent return");
        call(dialog.getClass().getMethod("show"), dialog, false);
        if (dialog.dismissCalls != 0) throw new AssertionError("Dismiss would trigger unwanted coupon navigation");
        call(show, new com.kongzue.dialogx.dialogs.CustomDialog(), true, (Object) null);
    }

    private static void enrollment() throws Throwable {
        install("EnrollmentJumpGuard", "hookSubmissionStart");
        install("EnrollmentJumpGuard", "hookLaunchDialogs");
        var vm = new com.realtech.promotion.pages.detail.PromotionDetailViewModel();
        call(vm.getClass().getMethod("submitOrder", boolean.class, boolean.class), vm, true, false, true);
        var launch = new com.realtech.common.ui.util.LaunchUrlUtils();
        Object outcome = call(launch.getClass().getMethod("openUrl", String.class, String.class, boolean.class, String.class, Object.class),
                launch, false, "imeituan://www.meituan.com/", "confirm", true, "store", new Object());
        if (!Boolean.FALSE.equals(outcome)) throw new AssertionError("Auto launch must settle as not opened");
        call(launch.getClass().getMethod("openUrl", String.class, String.class, boolean.class, String.class, Object.class),
                launch, true, "alipays://platformapi/startapp", "payment", true, "pay", new Object());
        call(launch.getClass().getMethod("openUrl", String.class, String.class, boolean.class, String.class, Object.class),
                launch, true, "https://example.com/login", "login", true, "auth", new Object());
    }

    private static void vip() throws Throwable {
        install("UserPagePurifier", "hookVipCarousel");
        var module = new com.realtech.user.pages.userv2.UserVipModule();
        call(module.getClass().getMethod("render", Object.class), module, false, new Object());
        if (module.binding.bannerVipCard.getVisibility() != 8 || module.binding.bannerIndicator.getVisibility() != 8)
            throw new AssertionError("VIP carousel still visible");
        if (!module.binding.bannerVipCard.stopped) throw new AssertionError("VIP autoplay still running");
        if (module.binding.tvWithdrawal.getVisibility() != 0 || module.binding.cardSilkEarn.getVisibility() != 0)
            throw new AssertionError("Withdraw card was hidden");
        var params = (com.realtech.user.pages.userv2.UserVipModule.Params) module.binding.cardSilkEarn.getLayoutParams();
        if (params.matchConstraintPercentWidth != 1.0f || params.endToStart != -1) throw new AssertionError("VIP gap still reserved");
    }

    private static void detail() throws Throwable {
        install("DetailPagePurifier", "install");
        var ad = new android.view.View();
        Method load = com.realtech.promotion.pages.detail.ext.PromotionDetailTobidAdViewExtKt.class
                .getMethod("loadPromotionDetailBotAdA", Object.class, String.class, android.view.View.class);
        call(load, null, false, new Object(), "ad-slot", ad);
        if (ad.getVisibility() != 8 || ad.getLayoutParams().height != 0) throw new AssertionError("Detail ad remains visible or reserves space");
        var binding = new com.realtech.promotion.databinding.LayoutPromotionDetailContentBinding();
        Method clean = Class.forName("io.github.jessire.xiaocanpurify.DetailPagePurifier").getDeclaredMethod("cleanBinding", Object.class);
        clean.setAccessible(true);
        clean.invoke(null, binding);
        if (binding.ivShare.getVisibility() != 8 || binding.flTobidContainer.getVisibility() != 8) throw new AssertionError("Share-earn/ad still visible");
        if (binding.merchantContent.getVisibility() != 0) throw new AssertionError("Merchant content hidden");
    }

    private static void withdraw() throws Throwable {
        install("NetworkAdInterceptor", "hookNativeHttpBridge");
        Method bridge = com.realtech.xiaocan.flutter.NativeHttpBridge.class.getMethod("createBridge$lambda$2", MethodCall.class, MethodChannel.Result.class);
        Reply reply = new Reply();
        call(bridge, null, false, new MethodCall("postService", Map.of("service", "MatchPlacement", "data",
                Map.of("resource_slug", "user_withdraw_dialog_ad"))), reply);
        if (reply.replies != 1 || reply.value != null) throw new AssertionError("Withdraw ad callback must settle as no data");
        Reply replySuccessPopup = new Reply();
        call(bridge, null, false, new MethodCall("postService", Map.of("service", "MatchPlacement", "data",
                Map.of("resource_slug", "WITHDRAWAL_SUCCESS_POPUP", "placement_id", "12345"))), replySuccessPopup);
        if (replySuccessPopup.replies != 1 || replySuccessPopup.value != null) throw new AssertionError("Withdrawal success popup callback must settle as no data");
        call(bridge, null, true, new MethodCall("postService", Map.of("service", "Withdraw", "data", Map.of("amount", 1))), new Reply());
        call(bridge, null, true, new MethodCall("postService", Map.of("service", "GetClientWithdrawList")), new Reply());
    }

    public static void main(String[] args) throws Throwable {
        int failures = 0;
        for (String test : args.length == 0 ? new String[]{"refresh", "ads", "popup", "policies", "withdraw", "vip", "detail", "enrollment", "taobaoPrompt"} : args) {
            try {
                RegressionTests.class.getDeclaredMethod(test).invoke(null);
                System.out.println("PASS: " + test);
            } catch (java.lang.reflect.InvocationTargetException e) {
                failures++;
                System.out.println("FAIL: " + test + ": " + e.getCause());
            }
        }
        if (failures != 0) throw new AssertionError(failures + " regression group(s) failed");
    }
}
