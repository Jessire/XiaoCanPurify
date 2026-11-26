package io.github.jessire.xiaocanpurify;

import static io.github.jessire.xiaocanpurify.LayoutSettings.Item.*;
import static io.github.jessire.xiaocanpurify.LayoutSettings.enabled;

import android.os.Looper;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import io.github.libxposed.api.XposedInterface;

public final class UserPagePurifier {
    private static final String MODULE = "com.realtech.user.pages.userv2.";
    private static final ProfileLiveViews VIEWS = new ProfileLiveViews();

    private UserPagePurifier() {}

    public static void install(XposedInterface xposed, ClassLoader cl) {
        // No setting reads at install time. Register views even when currently unmasked.
        LayoutSettings.addLiveListener(VIEWS::refresh);
        hookUserV2Fragment(xposed, cl);
        hookVipCarousel(xposed, cl);
        hookModule(xposed, cl, "UserInfoModule", "silkBinding", UserPagePurifier::profit, null, "render");
        hookModule(xposed, cl, "UserOtherMatchModule", "binding", UserPagePurifier::placements, USER_MATCH, "render");
        hookBottomBonusDismiss(xposed, cl);
        hookModule(xposed, cl, "UserNoticeModule", "binding", UserPagePurifier::notice, null, "renderInternal");
        hookNoticeRatio(xposed, cl);
        // These adapters size items in posted callbacks. Do not bind fresh rows while
        // their roots are GONE; the next normal render sizes them when visible.
        hookModule(xposed, cl, "UserMenuModule", "binding", b -> root(b, USER_MENU), USER_MENU, "render");
        hookModule(xposed, cl, "UserServiceModule", "binding", b -> root(b, USER_SERVICE), USER_SERVICE, "render");
        hookModule(xposed, cl, "UserBWCModule", "binding", b -> root(b, USER_MERCHANT), null, "render");
        hookModule(xposed, cl, "NewUserTipModule", "binding", b -> root(b, USER_NEW), null, "render");

        // Preserve request suppression for these two SDK-backed placements. In-flight
        // updates and host resets still update the underlying visibility while masked.
        hookModule(xposed, cl, "UserBqtModule", "binding", UserPagePurifier::baidu, USER_AD, "render", "loadAd");
        hookModule(xposed, cl, "UserBqtModule", "binding", UserPagePurifier::baidu, null, "hideAd");
        hookBaiduCallback(xposed, cl);
        hookModule(xposed, cl, "UserCommerceGridModule", "binding", UserPagePurifier::commerce, USER_COMMERCE, "render", "load");
        hookModule(xposed, cl, "UserCommerceGridModule", "binding", UserPagePurifier::commerce, null, "renderColumns", "hide");
    }

    private static boolean mainThread() { return Looper.myLooper() == Looper.getMainLooper(); }

    private static void hookUserV2Fragment(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> fragment = Class.forName("com.realtech.user.pages.UserV2Fragment", false, cl);
            Method init = fragment.getDeclaredMethod("lazyInitView", View.class);
            xposed.hook(init).intercept(chain -> {
                Object result = chain.proceed();
                if (mainThread()) {
                    Object binding = field(chain.getThisObject(), "binding");
                    refresh(placements(binding));
                    refresh(notice(binding));
                    refresh(baidu(binding));
                    refresh(commerce(binding));
                    refresh(root(field(binding, "layoutNewUserTip"), USER_NEW));
                    refresh(root(field(binding, "layoutMenu"), USER_MENU));
                    refresh(root(field(binding, "layoutUserMerchant"), USER_MERCHANT));
                    refresh(root(field(binding, "layoutServer"), USER_SERVICE));
                    Object silk = field(binding, "layoutSilkEarn");
                    refresh(profit(silk));
                    refresh(vip(silk));
                }
                return result;
            });
        } catch (Throwable t) {
            MainHook.log("User page init hook unavailable: " + t.getClass().getSimpleName());
        }
    }

    private static void hookVipCarousel(XposedInterface xposed, ClassLoader cl) {
        hookModule(xposed, cl, "UserVipModule", "binding", UserPagePurifier::vip, USER_VIP, "render");
        try {
            Class<?> binding = Class.forName("com.realtech.user.databinding.UserLayoutUserV2SilkBinding", false, cl);
            xposed.hook(binding.getDeclaredMethod("bind", View.class)).intercept(chain -> {
                Object result = chain.proceed();
                if (mainThread()) refresh(vip(result));
                return result;
            });
            // Banner can start itself on attach. Suppress only tracked profile banners,
            // not any unrelated carousel. Banner.start() returns this for chaining.
            Class<?> banner = binding.getField("bannerVipCard").getType();
            Method start = banner.getMethod("start");
            xposed.hook(start).intercept(chain -> {
                if (mainThread() && chain.getThisObject() instanceof View view) {
                    ProfileLiveViews.Entry entry = VIEWS.find(view);
                    if (entry != null && entry.blocksCarouselStart()) return view;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            MainHook.log("VIP carousel bind/start hook unavailable: " + t.getClass().getSimpleName());
        }
    }

    private interface Targets { ProfileLiveViews.Entry[] get(Object binding); }

    private static void hookModule(XposedInterface xposed, ClassLoader cl, String name, String bindingName,
                                   Targets targets, LayoutSettings.Item skip, String... methods) {
        try {
            Class<?> module = Class.forName(MODULE + name, false, cl);
            Field binding = module.getField(bindingName);
            for (Method method : module.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                        || !Arrays.asList(methods).contains(method.getName())) continue;
                xposed.hook(method).intercept(chain -> {
                    if (!mainThread()) return chain.proceed();
                    ProfileLiveViews.Entry[] entries = targets.get(field(binding, chain.getThisObject()));
                    if (shouldSkip(skip) && hasTarget(entries)) {
                        refresh(entries);
                        return null;
                    }
                    return render(chain, entries);
                });
            }
        } catch (Throwable t) {
            MainHook.log("User module hook unavailable: " + name + " (" + t.getClass().getSimpleName() + ")");
        }
    }

    private static void hookBottomBonusDismiss(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> module = Class.forName(MODULE + "UserOtherMatchModule", false, cl);
            Method dismiss = module.getDeclaredMethod("_init_$lambda$0", module, View.class);
            xposed.hook(dismiss).intercept(chain -> mainThread()
                    ? render(chain, new ProfileLiveViews.Entry[]{track(field(chain.getArg(0), "binding"),
                            "frameBottomBonus", USER_BONUS)}) : chain.proceed());
        } catch (Throwable t) {
            MainHook.log("User bottom bonus dismiss hook unavailable: " + t.getClass().getSimpleName());
        }
    }

    private static void hookNoticeRatio(XposedInterface xposed, ClassLoader cl) {
        // The posted ConstraintSet operation clones and reapplies visibility as well as ratio.
        try {
            Class<?> module = Class.forName(MODULE + "UserNoticeModule", false, cl);
            for (Method method : module.getDeclaredMethods()) {
                if (!method.getName().startsWith("applyBannerRatio$lambda$") || !Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 2 || method.getParameterTypes()[0] != module) continue;
                xposed.hook(method).intercept(chain -> mainThread()
                        ? render(chain, notice(field(chain.getArg(0), "binding"))) : chain.proceed());
            }
        } catch (Throwable t) {
            MainHook.log("User notice ratio hook unavailable: " + t.getClass().getSimpleName());
        }
    }

    private static void hookBaiduCallback(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> module = Class.forName(MODULE + "UserBqtModule", false, cl);
            Class<?> callback = Class.forName(module.getName() + "$loadAd$1", false, cl);
            for (Field owner : callback.getDeclaredFields()) {
                if (owner.getType() != module || Modifier.isStatic(owner.getModifiers())) continue;
                owner.setAccessible(true);
                xposed.hook(callback.getDeclaredMethod("onAdLoadedFinish", String.class)).intercept(chain -> mainThread()
                        ? render(chain, baidu(field(field(owner, chain.getThisObject()), "binding"))) : chain.proceed());
                return;
            }
        } catch (Throwable t) {
            MainHook.log("User Baidu completion hook unavailable: " + t.getClass().getSimpleName());
        }
    }

    private static Object render(XposedInterface.Chain chain, ProfileLiveViews.Entry[] entries) throws Throwable {
        for (ProfileLiveViews.Entry entry : entries) if (entry != null) entry.begin();
        try { return chain.proceed(); }
        finally { for (ProfileLiveViews.Entry entry : entries) if (entry != null) entry.end(); }
    }

    private static boolean shouldSkip(LayoutSettings.Item item) {
        if (item == null || !enabled(item)) return false;
        // The host shares one state for three placements. Only skip its render
        // (including exposure tracking) when none of the three is wanted.
        return item != USER_MATCH || (enabled(USER_FLOAT) && enabled(USER_BONUS));
    }

    private static boolean hasTarget(ProfileLiveViews.Entry[] entries) {
        for (ProfileLiveViews.Entry entry : entries) if (entry != null) return true;
        return false;
    }

    private static void refresh(ProfileLiveViews.Entry[] entries) {
        for (ProfileLiveViews.Entry entry : entries) if (entry != null) entry.refresh();
    }

    private static ProfileLiveViews.Entry[] vip(Object binding) {
        ProfileLiveViews.Entry banner = track(binding, "bannerVipCard", USER_VIP);
        ProfileLiveViews.Entry card = track(binding, "cardSilkEarn", USER_VIP);
        if (banner != null) banner.carousel();
        if (card != null) card.vipLayout();
        return new ProfileLiveViews.Entry[]{banner, track(binding, "bannerIndicator", USER_VIP), card};
    }

    private static ProfileLiveViews.Entry[] profit(Object binding) {
        return new ProfileLiveViews.Entry[]{track(binding, "tvMakeProfit", USER_PROFIT),
                track(binding, "tvMakeProfitClaimBadge", USER_PROFIT)};
    }

    private static ProfileLiveViews.Entry[] placements(Object binding) {
        return new ProfileLiveViews.Entry[]{track(binding, "adContainer", USER_MATCH),
                track(binding, "ivFloatingWindow", USER_FLOAT), track(binding, "frameBottomBonus", USER_BONUS)};
    }

    private static ProfileLiveViews.Entry[] baidu(Object binding) {
        return new ProfileLiveViews.Entry[]{track(binding, "bqtContainer", USER_AD)};
    }

    private static ProfileLiveViews.Entry[] commerce(Object binding) {
        return new ProfileLiveViews.Entry[]{track(binding, "commerceGridContainer", USER_COMMERCE)};
    }

    private static ProfileLiveViews.Entry[] notice(Object binding) {
        ProfileLiveViews.Entry banner = track(binding, "bannerNotice", USER_NOTICE);
        if (banner != null) banner.carousel();
        return new ProfileLiveViews.Entry[]{banner};
    }

    private static ProfileLiveViews.Entry[] root(Object binding, LayoutSettings.Item item) {
        View view = null;
        try { if (binding != null) view = (View) binding.getClass().getMethod("getRoot").invoke(binding); }
        catch (ReflectiveOperationException | RuntimeException ignored) {}
        return new ProfileLiveViews.Entry[]{VIEWS.track(view, item)};
    }

    private static ProfileLiveViews.Entry track(Object binding, String name, LayoutSettings.Item item) {
        Object value = field(binding, name);
        return value instanceof View view ? VIEWS.track(view, item) : null;
    }

    private static Object field(Object owner, String name) {
        try { return owner == null ? null : owner.getClass().getField(name).get(owner); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static Object field(Field field, Object owner) {
        try { return field.get(owner); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }
}
