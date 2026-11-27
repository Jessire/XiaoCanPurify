import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.libxposed.api.XposedInterface;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import com.realtech.common.entity.AppLocationEntity;
import com.realtech.location.LocationManager;
import com.realtech.location.utils.LocationUtils;
import com.realtech.promotion.pages.home.viewmodel.HomeVMFeedsKt;
import com.realtech.promotion.pages.home.viewmodel.HomeViewModel;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function0;

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

    private static final Map<java.lang.reflect.Executable, XposedInterface.Hooker> hooks = new HashMap<>();
    private static final ClassLoader loader = RegressionTests.class.getClassLoader();
    private static final XposedInterface xposed = (XposedInterface) Proxy.newProxyInstance(loader,
            new Class<?>[]{XposedInterface.class}, (p, m, a) -> {
                if (!m.getName().equals("hook")) return null;
                var target = (java.lang.reflect.Executable) a[0];
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
        return callWithOriginal(target, receiver, expectedProceed, () -> null, args);
    }

    @FunctionalInterface
    private interface Original { Object run() throws Throwable; }

    private static Object callWithOriginal(java.lang.reflect.Executable target, Object receiver, boolean expectedProceed,
                                           Original original, Object... args) throws Throwable {
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
                        case "proceed": proceeded[0] = true; return original.run();
                        default: throw new AssertionError(m.getName());
                    }
                });
        Object result = hook.intercept(chain);
        if (proceeded[0] != expectedProceed) throw new AssertionError("Unexpected original execution: " + target);
        return result;
    }

    private static Object[] vmArgs(boolean force, Continuation cont) {
        return new Object[]{force, true, cont};
    }

    private static void homeFreeOrderPopup() throws Throwable {
        var owner = com.realtech.promotion.pages.home.viewmodel.HomeVMOrderKt.class;
        var homeVm = com.realtech.promotion.pages.home.viewmodel.HomeViewModel.class;
        Method target = owner.getDeclaredMethod("checkFreeOrderDialog", homeVm);
        var before = new HashMap<>(hooks);
        install("HomeFreeOrderPopupBlocker", "install");
        var added = new HashMap<>(hooks);
        added.keySet().removeAll(before.keySet());
        if (!added.keySet().equals(java.util.Set.of(target)))
            throw new AssertionError("Only the exact popup producer should be hooked: " + added.keySet());

        Class<?> settings = Class.forName("io.github.jessire.xiaocanpurify.LayoutSettings");
        var active = settings.getDeclaredField("active");
        var verified = settings.getDeclaredField("verifiedHost3212");
        active.setAccessible(true);
        verified.setAccessible(true);
        Object previousActive = active.get(null);
        Object previousVerified = verified.get(null);
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Enum> items = (Class<? extends Enum>) Class.forName(settings.getName() + "$Item");
            @SuppressWarnings({"rawtypes", "unchecked"})
            var enabled = java.util.EnumSet.allOf((Class) items);
            Object toggle = enabled.stream().filter(item -> item.toString().equals("HOME_FREE_ORDER_POPUP"))
                    .findFirst().orElseThrow();
            var category = items.getDeclaredField("category");
            var key = items.getDeclaredField("key");
            category.setAccessible(true);
            key.setAccessible(true);
            if (!"HOME".equals(category.get(toggle).toString()) || !"home_free_order_popup".equals(key.get(toggle)))
                throw new AssertionError("Missing independent homepage registry entry");

            var vm = new com.realtech.promotion.pages.home.viewmodel.HomeViewModel();
            active.set(null, null);
            call(target, null, true, vm); // settings not loaded: fail open
            active.set(null, enabled);
            verified.setBoolean(null, false);
            call(target, null, true, vm); // unverified host
            verified.setBoolean(null, true);
            call(target, null, false, vm);
            var disabled = enabled.clone();
            disabled.remove(toggle);
            active.set(null, disabled);
            call(target, null, true, vm); // only this switch off
        } finally {
            active.set(null, previousActive);
            verified.set(null, previousVerified);
            hooks.clear();
            hooks.putAll(before);
        }
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
        Method openUrl = launch.getClass().getMethod("openUrl", String.class, String.class,
                boolean.class, String.class, Continuation.class);
        Object outcome = call(openUrl, launch, false, "imeituan://www.meituan.com/", "confirm", true, "store", null);
        if (!Boolean.FALSE.equals(outcome)) throw new AssertionError("Auto launch must settle as not opened");
        call(openUrl, launch, true, "alipays://platformapi/startapp", "payment", true, "pay", null);
        call(openUrl, launch, true, "https://example.com/login", "login", true, "auth", null);
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

    private static void foodDetective() throws Throwable {
        var enabled = new java.util.concurrent.atomic.AtomicBoolean(true);
        Class<?> guard = Class.forName("io.github.jessire.xiaocanpurify.FoodDetectiveAutoJump");
        Method install = guard.getDeclaredMethod("install", XposedInterface.class, ClassLoader.class,
                java.util.function.BooleanSupplier.class, java.util.function.BooleanSupplier.class);
        install.setAccessible(true);
        install.invoke(null, xposed, loader, (java.util.function.BooleanSupplier) enabled::get,
                (java.util.function.BooleanSupplier) () -> false);
        Class<?> nav = com.therouter.router.Navigator.class;
        Method route = nav.getDeclaredMethod("navigation$default", nav, android.content.Context.class,
                com.therouter.router.interceptor.NavigationCallback.class, int.class, Object.class);
        var detail = new com.realtech.promotion.pages.detail.PromotionDetailActivity();
        String target = "https://example.invalid/task?x=1&from_source=3&t=1740000000000";
        detail.onDestroy(foodRoute(route, "/web", target, 3, false));
        if (detail.exitWork != 2 || detail.dailyWrite != 1) throw new AssertionError("Skipped host exit work/check");
        detail.openManually(foodRoute(route, "/web", target, 3, true));
        detail.foodDetectiveCheck(foodRoute(route, "/web", target, 3, true));
        detail.onDestroy(foodRoute(route, "/web", "https://example.invalid/voucher", 3, true));
        detail.onDestroy(foodRoute(route, "/other", target, 3, true));
        detail.onDestroy(foodRoute(route, "/web", target, 1, true));
        detail.onDestroy(foodRoute(route, "/web", target + "x", 3, true));
        enabled.set(false);
        detail.onDestroy(foodRoute(route, "/web", target, 3, true));
        Class<?> rule = Class.forName("io.github.jessire.xiaocanpurify.FoodDetectiveJumpRule");
        Method exit = rule.getDeclaredMethod("isDetailExit", StackTraceElement[].class);
        exit.setAccessible(true);
        String detailPkg = "com.realtech.promotion.pages.detail.";
        for (String owner : List.of("dp.DpPromotionDetailActivity", "mt.MtPromotionDetailActivity",
                "quick.QuickPromotionDetailBottomSheet", "dp.DpQuickPromotionDetailBottomSheet",
                "mt.quick.MtQuickPromotionDetailBottomSheet")) {
            String name = detailPkg + owner;
            StackTraceElement check = new StackTraceElement(name, "foodDetectiveCheck", null, -1);
            String method = owner.equals("mt.MtPromotionDetailActivity") ? "onPause" : "onDestroy";
            if (owner.endsWith("BottomSheet")) {
                StackTraceElement observer = new StackTraceElement(name + "$createObserver$1", method, null, -1);
                StackTraceElement accessor = new StackTraceElement(name, "access$foodDetectiveCheck", null, -1);
                assertExit(exit, true, check, accessor, observer);
                assertExit(exit, true, check, observer);
            } else {
                assertExit(exit, true, check, new StackTraceElement(name, method, null, -1));
            }
            assertExit(exit, false, check, new StackTraceElement(name, "manual", null, -1));
            assertExit(exit, false, new StackTraceElement("com.realtech.Other", "route", null, -1), check,
                    new StackTraceElement(name, method, null, -1));
        }
    }

    private static Runnable foodRoute(Method route, String path, String link, int mask, boolean proceed) {
        return () -> {
            try {
                call(route, null, proceed, new com.therouter.router.Navigator(path, link), null, null, mask, null);
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        };
    }

    private static void assertExit(Method exit, boolean expected, StackTraceElement... frames) throws Exception {
        if (!Boolean.valueOf(expected).equals(exit.invoke(null, (Object) frames)))
            throw new AssertionError("Unexpected caller decision: " + java.util.Arrays.toString(frames));
    }

    private static void foodWebExit() throws Throwable {
        var hookClass = Class.forName("io.github.jessire.xiaocanpurify.FoodDetectiveWebExit");
        var method = hookClass.getDeclaredMethod("install", XposedInterface.class, ClassLoader.class,
                java.util.function.BooleanSupplier.class);
        method.setAccessible(true);
        var enabled = new java.util.concurrent.atomic.AtomicBoolean(true);
        method.invoke(null, xposed, loader, (java.util.function.BooleanSupplier) enabled::get);
        var page = new com.realtech.webpage.web.AgentWebFragment();
        var controller = new com.realtech.webpage.web.WebControllerImpl(page);
        var fragmentHook = page.getClass().getMethod("callJsRetention");
        var controllerHook = controller.getClass().getMethod("callJsRetention");
        String task = "https://gw.hzaiguojiang.com/static/act/singleActivity.html#/new-recommend";
        page.web.url = task + "?hide_title_bar=true&from=app&from_source=3&t=1";
        page.history = true;
        page.onBackClick(() -> { throw new AssertionError("History must take precedence"); });
        if (page.historyMoves != 1 || page.activity.finishes != 0) throw new AssertionError("History changed");
        page.history = false;
        page.canJsBack = true;
        page.onBackClick(() -> { throw new AssertionError("Direct host close must take precedence"); });
        if (page.activity.finishes != 1) throw new AssertionError("Direct close changed");
        page.canJsBack = false;
        page.activity.finishes = 0;
        page.otherCaller(retentionCall(fragmentHook, page, true));
        enabled.set(false);
        page.onBackClick(retentionCall(fragmentHook, page, true));
        enabled.set(true);
        for (String other : new String[]{null, "about:blank", "https://gw.hzaiguojiang.com/error",
                task + "-other?hide_title_bar=true", task.replace("https:", "http:"),
                task.replace(".com/", ".com.evil/"), task.replace(".com/", ".com@other.example/"),
                task.replace(".com/", ".com:444/"), task.replace("/static/act/", "/different/"),
                task.replace("/new-recommend", "/task-another")}) {
            page.web.url = other;
            page.onBackClick(retentionCall(fragmentHook, page, true));
            controller.closeWebView(retentionCall(controllerHook, controller, true));
        }
        page.web.url = task; // Manual entry URL: not an automatic navigation, but the same retention branch.
        page.activity = new android.app.Activity();
        page.onBackClick(retentionCall(fragmentHook, page, true));
        page.activity = new com.realtech.webpage.web.CommonWebActivity();
        page.activity.destroyed = true;
        page.onBackClick(retentionCall(fragmentHook, page, true));
        page.activity.destroyed = false;
        controller.agent = new Object();
        controller.closeWebView(retentionCall(controllerHook, controller, true));
        controller.agent = page.agent;
        if (page.activity.finishes != 0) throw new AssertionError("Forwarded cases closed activity");
        controller.closeWebView(retentionCall(controllerHook, controller, false));
        if (page.activity.finishes != 1) throw new AssertionError("Bridge close did not finish exactly once");
        page.activity.finishes = 0;
        controller.hostWeakRef.clear();
        controller.closeWebView(retentionCall(controllerHook, controller, true));
        page.onBackClick(retentionCall(fragmentHook, page, false));
        if (page.activity.finishes != 1) throw new AssertionError("Back did not finish exactly once");
        page.onBackClick(retentionCall(fragmentHook, page, true));
        if (page.activity.finishes != 1) throw new AssertionError("Already finishing closed twice");
    }

    private static Runnable retentionCall(Method hook, Object receiver, boolean proceed) {
        return () -> {
            try { call(hook, receiver, proceed); }
            catch (Throwable t) { throw new AssertionError(t); }
        };
    }

    private static void foodTaskPage() throws Throwable {
        var pageEnabled = new java.util.concurrent.atomic.AtomicBoolean(true);
        var autoEnabled = new java.util.concurrent.atomic.AtomicBoolean(false);
        var guard = Class.forName("io.github.jessire.xiaocanpurify.FoodDetectiveAutoJump");
        var install = guard.getDeclaredMethod("install", XposedInterface.class, ClassLoader.class,
                java.util.function.BooleanSupplier.class, java.util.function.BooleanSupplier.class);
        install.setAccessible(true);
        install.invoke(null, xposed, loader, (java.util.function.BooleanSupplier) autoEnabled::get,
                (java.util.function.BooleanSupplier) pageEnabled::get);
        var nav = com.therouter.router.Navigator.class;
        var route = nav.getDeclaredMethod("navigation$default", nav, android.content.Context.class,
                com.therouter.router.interceptor.NavigationCallback.class, int.class, Object.class);
        String target = "https://gw.hzaiguojiang.com/static/act/singleActivity.html#/new-recommend";
        String link = target + "?hide_title_bar=true&from=app";
        var detail = new com.realtech.promotion.pages.detail.PromotionDetailActivity();
        // Host's verified manual URL; the new switch does not depend on caller or source.
        detail.openManually(foodRoute(route, "/web", link + "&from_source=1&t=1", 3, false));
        foodRoute(route, "/web", link + "&from_source=2&t=2", 3, false).run();
        detail.onDestroy(foodRoute(route, "/web", link + "&from_source=3&t=3", 3, false));
        foodRoute(route, "/web", target, 3, false).run();
        if (detail.exitWork != 2 || detail.dailyWrite != 1) throw new AssertionError("Host exit work was skipped");
        for (String other : new String[]{null, "about:blank", "参数错误", "/order_voucher_submit_success",
                target.replace("https:", "http:"), target.replace(".com/", ".com.evil/"),
                target.replace(".com/", ".com@evil.example/"), target.replace(".com/", ".com:444/"),
                target.replace("/static/act/", "/elsewhere/"), target + "-other?from_source=2",
                target.replace("/new-recommend", "/task-other"), target.replace("#", "?"),
                "https://example.invalid/?next=" + target, target + "?broken=%zz"}) {
            foodRoute(route, "/web", other, 3, true).run();
        }
        foodRoute(route, "/order_voucher_submit_success", link, 3, true).run();
        foodRoute(route, "/web", link, 1, true).run();
        call(route, null, true, new com.therouter.router.Navigator("/web", link), null, null, 3, new Object());
        pageEnabled.set(false);
        autoEnabled.set(true);
        detail.openManually(foodRoute(route, "/web", link + "&from_source=1&t=1", 3, true));
        foodRoute(route, "/web", link + "&from_source=2&t=2", 3, true).run();
        detail.onDestroy(foodRoute(route, "/web", link + "&from_source=3&t=3", 3, false));
    }

    private static void benefitWebJump() throws Throwable {
        install("BenefitWebJumpGuard", "install");
        var launch = new com.realtech.common.ui.util.LaunchUrlUtils();
        Method url = launch.getClass().getMethod("openUrl", String.class, String.class,
                boolean.class, String.class, Continuation.class);
        Method mini = launch.getClass().getMethod("openMini", String.class, String.class,
                String.class, boolean.class, String.class, Continuation.class);
        String prompt = "跳转领取大额福利";
        var web = new com.realtech.webpage.web.CommonWebActivity();
        com.blankj.utilcode.util.ActivityUtils.top = web;
        try {
            if (call(url, launch, false, "test://ad", prompt, true, null, null) != Boolean.FALSE
                    || call(mini, launch, false, "ad", "/promo", prompt, true, null, null) != Boolean.FALSE) {
                throw new AssertionError("Marketing jump must report cancellation");
            }
            for (String other : new String[]{null, "是否允许打开微信？", "提现到账确认", prompt + "？"}) {
                call(url, launch, true, "test://normal", other, true, null, null);
                call(mini, launch, true, "normal", "/normal", other, true, null, null);
            }
            com.blankj.utilcode.util.ActivityUtils.top = new android.app.Activity();
            call(url, launch, true, "test://ad", prompt, true, null, null);
            call(mini, launch, true, "ad", "/promo", prompt, true, null, null);
            com.blankj.utilcode.util.ActivityUtils.top = null;
            call(url, launch, true, "test://ad", prompt, true, null, null);
            var wechat = new com.realtech.common.ui.mp.WeChatFuncs();
            Method dialog = wechat.getClass().getMethod("showLaunchMiniAppDialog",
                    android.content.Context.class, String.class, Continuation.class);
            if (!Integer.valueOf(0).equals(call(dialog, wechat, false, web, prompt, null))) {
                throw new AssertionError("WeChat prompt must report close without launch");
            }
            call(dialog, wechat, true, web, "提现到账确认", null);
            call(dialog, wechat, true, new android.app.Activity(), prompt, null);
            call(dialog, wechat, true, null, prompt, null);
        } finally {
            com.blankj.utilcode.util.ActivityUtils.top = null;
        }
    }

    public static void main(String[] args) throws Throwable {
        int failures = 0;
        for (String test : args.length == 0 ? new String[]{"refresh", "ads", "popup", "homeFreeOrderPopup", "policies", "withdraw", "vip", "detail", "enrollment", "taobaoPrompt", "foodDetective", "foodWebExit", "foodTaskPage", "benefitWebJump"} : args) {
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
