package io.github.jessire.xiaocanpurify;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.EnumMap;
import java.util.EnumSet;

final class LayoutSettings {
    enum Category {
        NAV("底部导航", "底栏标签与主界面浮动推广"),
        HOME("首页", "主题、搜索、横幅、信息流与下拉交互"),
        ORDER("订单", "订单列表、凭证广告、网页挽留与奖励推广"),
        DETAIL("活动详情", "详情广告、占位与分享推广"),
        PROFILE("我的", "个人中心广告、菜单、服务与会员推广"),
        WITHDRAW("提现", "提现成功后的公众号关注推广");

        final String title;
        final String description;

        Category(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    // UI categories and counts are derived from this registry. Item names are stored keys.
    enum Item {
        NAV_EXTRA(Category.NAV, "屏蔽底栏推广标签（购物返利等）"),
        NAV_HIDE_HOME(Category.NAV, "隐藏底栏「首页」"),
        NAV_HIDE_ORDER(Category.NAV, "隐藏底栏「订单」"),
        NAV_HIDE_VIP(Category.NAV, "隐藏底栏「会员」"),
        NAV_HIDE_TASK(Category.NAV, "隐藏底栏「福利」"),
        MAIN_FLOAT(Category.NAV, "隐藏主界面浮动推广"),
        HOME_THEME(Category.HOME, "净化状态栏与节日主题"),
        HOME_SEARCH_HINT(Category.HOME, "隐藏搜索框营销词"),
        HOME_SEARCH_BANNER(Category.HOME, "隐藏搜索框右侧横幅"),
        HOME_TOP(Category.HOME, "隐藏顶部横幅与推广背景"),
        HOME_MENUS(Category.HOME, "隐藏红包快捷菜单"),
        HOME_FEED(Category.HOME, "隐藏信息流推广"),
        HOME_BRAND_COUPON(Category.HOME, "屏蔽首页大牌券专享活动"),
        HOME_ORDER(Category.HOME, "隐藏顶部订单推广横幅"),
        HOME_FAB(Category.HOME, "隐藏红包／新手等浮动入口"),
        HOME_BOTTOM(Category.HOME, "隐藏底部推广与提示"),
        HOME_SECOND(Category.HOME, "屏蔽下拉第二层（保留刷新）"),
        HOME_FREE_ORDER_POPUP(Category.HOME, "隐藏「你有一单可免单」弹窗"),
        ORDER_CHALLENGE(Category.ORDER, "隐藏挑战横条与卡片"),
        ORDER_INVITE(Category.ORDER, "隐藏免单邀请"),
        ORDER_FEED(Category.ORDER, "隐藏列表推广图片"),
        ORDER_AD(Category.ORDER, "隐藏列表原生广告"),
        ORDER_VOUCHER_NATIVE_AD(Category.ORDER, "隐藏原生上传凭证页广告与占位"),
        ORDER_FOOD_DETECTIVE_PAGE(Category.ORDER, "屏蔽新店推荐任务网页（含手动打开）"),
        ORDER_TIP(Category.ORDER, "隐藏订单提示横幅"),
        ORDER_REWARD(Category.ORDER, "隐藏奖励类操作按钮"),
        DETAIL_AD(Category.DETAIL, "隐藏详情广告、占位和促销气泡"),
        DETAIL_FOOD_DETECTIVE_AUTO_JUMP(Category.DETAIL, "阻止详情离开时自动打开新店推荐任务"),
        DETAIL_FOOD_DETECTIVE_WEB_EXIT(Category.ORDER, "关闭新店推荐任务的网页退出挽留"),
        DETAIL_SHARE(Category.DETAIL, "隐藏「分享赚豆」入口"),
        USER_AD(Category.PROFILE, "隐藏百度广告位"),
        USER_COMMERCE(Category.PROFILE, "隐藏商业推广宫格"),
        USER_NOTICE(Category.PROFILE, "隐藏公告横幅"),
        USER_FLOAT(Category.PROFILE, "隐藏浮动推广入口"),
        USER_BONUS(Category.PROFILE, "隐藏底部奖励推广"),
        USER_NEW(Category.PROFILE, "隐藏新手提示"),
        USER_MENU(Category.PROFILE, "隐藏快捷菜单及其挑战、元宝入口"),
        USER_MERCHANT(Category.PROFILE, "隐藏用户商家入口"),
        USER_SERVICE(Category.PROFILE, "隐藏服务区"),
        USER_MATCH(Category.PROFILE, "隐藏个人中心图片推广"),
        USER_PROFIT(Category.PROFILE, "隐藏「赚钱」入口和待领徽标"),
        USER_VIP(Category.PROFILE, "隐藏提现旁的会员轮播"),
        VIP_HIDE_SVIP(Category.PROFILE, "屏蔽会员页「激活SVIP」标签"),
        WITHDRAW_FOLLOW(Category.WITHDRAW, "自动关闭提现成功页的公众号关注推广");

        final Category category;
        final String title;
        final String key;

        Item(Category category, String title) {
            this.category = category;
            this.title = title;
            key = name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static boolean defaultEnabled(Item item) {
        switch (item) {
            // 默认沿用旧版观感：底栏只留首页、订单、我的。
            case NAV_HIDE_VIP:
            case NAV_HIDE_TASK:
                return true;
            case NAV_HIDE_HOME:
            case NAV_HIDE_ORDER:
                return false;
            default:
                return true;
        }
    }

    /** 底栏标签是否要隐藏；「首页」在其余三项都被隐藏时保留，避免底栏只剩一项。 */
    static boolean tabHidden(Item item) {
        if (!enabled(item)) return false;
        if (item != Item.NAV_HIDE_HOME) return true;
        return !(enabled(Item.NAV_HIDE_ORDER) && enabled(Item.NAV_HIDE_VIP)
                && enabled(Item.NAV_HIDE_TASK));
    }

    private static final String FILE = "xiaocanpurify_layout_settings";
    // A cold-start snapshot keeps adapter data, dimensions and tab indices consistent.
    private static volatile EnumSet<Item> active;
    private static boolean verifiedHost3212;
    // Published as one immutable unit, independently of the cold-start settings.
    private static volatile HotRules liveRules;
    private static final CopyOnWriteArrayList<Runnable> LIVE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final EnumSet<Item> HOT = EnumSet.of(
            Item.NAV_HIDE_HOME, Item.NAV_HIDE_ORDER, Item.NAV_HIDE_VIP, Item.NAV_HIDE_TASK,
            Item.ORDER_FOOD_DETECTIVE_PAGE, Item.DETAIL_FOOD_DETECTIVE_AUTO_JUMP,
            Item.DETAIL_FOOD_DETECTIVE_WEB_EXIT, Item.HOME_FREE_ORDER_POPUP,
            Item.HOME_SEARCH_HINT, Item.HOME_SEARCH_BANNER, Item.HOME_MENUS,
            Item.HOME_ORDER, Item.HOME_FAB, Item.HOME_BOTTOM,
            Item.ORDER_CHALLENGE, Item.ORDER_INVITE, Item.ORDER_FEED, Item.ORDER_TIP,
            Item.ORDER_REWARD, Item.DETAIL_SHARE,
            Item.USER_AD, Item.USER_COMMERCE, Item.USER_NOTICE, Item.USER_FLOAT,
            Item.USER_BONUS, Item.USER_NEW, Item.USER_MENU, Item.USER_MERCHANT,
            Item.USER_SERVICE, Item.USER_MATCH, Item.USER_PROFIT, Item.USER_VIP,
            Item.VIP_HIDE_SVIP,
            Item.WITHDRAW_FOLLOW);

    static final class HotRules {
        private static final HotRules OFF = new HotRules(false, false, false, false);
        final boolean blockTaskPage;
        final boolean blockAutoJump;
        final boolean blockWebExit;
        final boolean blockFreeOrderPopup;
        private final EnumSet<Item> items;

        HotRules(boolean blockTaskPage, boolean blockAutoJump, boolean blockWebExit,
                 boolean blockFreeOrderPopup) {
            this(blockTaskPage, blockAutoJump, blockWebExit, blockFreeOrderPopup, EnumSet.noneOf(Item.class));
        }

        private HotRules(boolean blockTaskPage, boolean blockAutoJump, boolean blockWebExit,
                         boolean blockFreeOrderPopup, EnumSet<Item> items) {
            this.items = items;
            this.blockTaskPage = blockTaskPage;
            this.blockAutoJump = blockAutoJump;
            this.blockWebExit = blockWebExit;
            this.blockFreeOrderPopup = blockFreeOrderPopup;
        }

        private static HotRules from(EnumSet<Item> enabled) {
            EnumSet<Item> items = enabled.clone();
            items.retainAll(HOT);
            return new HotRules(enabled.contains(Item.ORDER_FOOD_DETECTIVE_PAGE),
                    enabled.contains(Item.DETAIL_FOOD_DETECTIVE_AUTO_JUMP),
                    enabled.contains(Item.DETAIL_FOOD_DETECTIVE_WEB_EXIT),
                    enabled.contains(Item.HOME_FREE_ORDER_POPUP), items);
        }
    }

    private LayoutSettings() {}

    static synchronized void initialize(Context context) {
        if (active != null || context == null || !MainHook.TARGET_PACKAGE.equals(context.getPackageName())) return;
        try {
            EnumSet<Item> enabled = EnumSet.noneOf(Item.class);
            EnumMap<Item, Boolean> saved = read(context);
            for (Item item : Item.values()) if (saved.get(item)) enabled.add(item);
            verifiedHost3212 = isVerifiedHost3212(context);
            liveRules = HotRules.from(enabled);
            active = enabled;
        } catch (RuntimeException e) {
            MainHook.log("Could not read layout settings: " + e.getClass().getSimpleName());
        }
    }

    static boolean enabled(Item item) {
        EnumSet<Item> snapshot = active;
        HotRules rules = liveRules;
        if (snapshot != null && verifiedHost3212 && rules != null && HOT.contains(item)) {
            return rules.items.contains(item);
        }
        return snapshot == null ? defaultEnabled(item) : snapshot.contains(item);
    }

    // Startup side effects must not react to a change of a live rule mid-process.
    static boolean coldEnabled(Item item) {
        EnumSet<Item> snapshot = active;
        return snapshot == null ? defaultEnabled(item) : snapshot.contains(item);
    }

    static boolean hasBaseline() { return active != null; }

    static boolean isVerifiedHost3212() {
        return active != null && verifiedHost3212;
    }

    static boolean isHot(Item item) {
        return verifiedHost3212 && HOT.contains(item);
    }

    /** Registry-only classification, valid before the host is verified (UI grouping). */
    static boolean hotCapable(Item item) {
        return HOT.contains(item);
    }

    /** How many structural (restart-only) rules differ from the running baseline. */
    static int restartPendingCount(EnumMap<Item, Boolean> values) {
        EnumSet<Item> snapshot = active;
        if (snapshot == null || values == null) return 0;
        int pending = 0;
        for (Item item : Item.values()) {
            if (hotCapable(item)) continue;
            if (snapshot.contains(item) != Boolean.TRUE.equals(values.get(item))) pending++;
        }
        return pending;
    }

    static boolean needsRestart(EnumMap<Item, Boolean> values) {
        EnumSet<Item> snapshot = active;
        if (snapshot == null) return false; // No cold baseline to compare yet.
        for (Item item : Item.values()) {
            if (isHot(item)) continue;
            boolean requested = Boolean.TRUE.equals(values.get(item));
            if (snapshot.contains(item) != requested) return true;
        }
        return false;
    }

    static HotRules hotRules() {
        EnumSet<Item> cold = active;
        if (cold == null || !verifiedHost3212) return HotRules.OFF;
        HotRules live = liveRules;
        // Preserve the cold-field fallback when only the cold snapshot is populated.
        return live != null ? live : HotRules.from(cold);
    }

    static boolean homeFreeOrderPopupEnabled() {
        return hotRules().blockFreeOrderPopup;
    }

    static boolean foodDetectiveAutoJumpEnabled() {
        return hotRules().blockAutoJump;
    }

    static boolean orderVoucherNativeAdEnabled() {
        EnumSet<Item> snapshot = active;
        return snapshot != null && snapshot.contains(Item.ORDER_VOUCHER_NATIVE_AD);
    }

    static boolean foodDetectivePageEnabled() {
        return hotRules().blockTaskPage;
    }

    static boolean foodDetectiveWebExitEnabled() {
        return hotRules().blockWebExit;
    }

    @SuppressWarnings("deprecation")
    private static boolean isVerifiedHost3212(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(MainHook.TARGET_PACKAGE, 0);
            return HostVersions.isSupported(info.versionName, info.versionCode);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    static EnumMap<Item, Boolean> read(Context context) {
        SharedPreferences prefs = preferences(context);
        EnumMap<Item, Boolean> result = new EnumMap<>(Item.class);
        for (Item item : Item.values()) result.put(item, prefs.getBoolean(item.key, defaultEnabled(item)));
        return result;
    }

    static synchronized boolean save(Context context, EnumMap<Item, Boolean> values) {
        if (context == null || !MainHook.TARGET_PACKAGE.equals(context.getPackageName())) return false;
        // Capture the running process's cold baseline before any write, never from the draft.
        initialize(context);
        if (active == null) return false;
        SharedPreferences.Editor editor = preferences(context).edit();
        EnumSet<Item> enabled = EnumSet.noneOf(Item.class);
        for (Item item : Item.values()) {
            boolean checked = values.get(item);
            editor.putBoolean(item.key, checked);
            if (checked) enabled.add(item);
        }
        HotRules next = HotRules.from(enabled);
        // A failed disk commit can still change SharedPreferences' in-memory map.
        // Hooks only see an explicitly published snapshot, never that map.
        if (!editor.commit()) return false;
        HotRules last = liveRules;
        liveRules = next;
        if (verifiedHost3212 && (last == null || !last.items.equals(next.items))) notifyLiveListeners();
        return true;
    }

    static void addLiveListener(Runnable listener) {
        LIVE_LISTENERS.addIfAbsent(listener);
    }

    private static void notifyLiveListeners() {
        if (LIVE_LISTENERS.isEmpty()) return;
        Runnable refresh = () -> {
            for (Runnable listener : LIVE_LISTENERS) {
                try { listener.run(); }
                catch (RuntimeException | LinkageError error) {
                    MainHook.log("Live refresh unavailable: " + error.getClass().getSimpleName());
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run();
        else new Handler(Looper.getMainLooper()).post(refresh);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
