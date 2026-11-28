package io.github.jessire.xiaocanpurify;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.jessire.xiaocanpurify.LayoutSettings.Item;

/** Presentation-only grouping. LayoutSettings.Item remains the preference registry. */
final class SettingsGroups {
    enum State { OFF, MIXED, ON }

    /** Scene follows the host screen the rule applies to. */
    enum Scene {
        HOME("首页"), ORDER("订单"), PROFILE("我的"), GLOBAL("全局");

        final String title;

        Scene(String title) {
            this.title = title;
        }

        java.util.List<Item> items(boolean hot) {
            java.util.List<Item> out = new java.util.ArrayList<>();
            for (Item item : Item.values()) {
                if (of(item) == this && LayoutSettings.hotCapable(item) == hot) out.add(item);
            }
            return out;
        }

        int total() {
            int n = 0;
            for (Item item : Item.values()) if (of(item) == this) n++;
            return n;
        }

        static Scene of(Item item) {
            switch (item) {
                case ORDER_CHALLENGE:
                case ORDER_INVITE:
                case ORDER_FEED:
                case ORDER_AD:
                case ORDER_VOUCHER_NATIVE_AD:
                case ORDER_FOOD_DETECTIVE_PAGE:
                case ORDER_TIP:
                case ORDER_REWARD:
                case DETAIL_FOOD_DETECTIVE_WEB_EXIT:
                case DETAIL_AD:
                case DETAIL_FOOD_DETECTIVE_AUTO_JUMP:
                case DETAIL_SHARE:
                    return ORDER;
                case USER_AD:
                case USER_COMMERCE:
                case USER_NOTICE:
                case USER_FLOAT:
                case USER_BONUS:
                case USER_NEW:
                case USER_MENU:
                case USER_MERCHANT:
                case USER_SERVICE:
                case USER_MATCH:
                case USER_PROFIT:
                case USER_VIP:
                case VIP_HIDE_SVIP:
                case WITHDRAW_FOLLOW:
                    return PROFILE;
                case NAV_EXTRA:
                case NAV_HIDE_HOME:
                case NAV_HIDE_ORDER:
                case NAV_HIDE_VIP:
                case NAV_HIDE_TASK:
                case MAIN_FLOAT:
                    return GLOBAL;
                default:
                    return HOME;
            }
        }
    }

    enum Section {
        MAIN("首页与导航"), ORDER("订单与活动"), PROFILE("个人中心与提现");

        final String title;

        Section(String title) {
            this.title = title;
        }
    }

    enum Group {
        NAV(Section.MAIN, "底栏与主界面推广", "精简底栏，隐藏主界面浮层",
                Item.NAV_EXTRA, Item.NAV_HIDE_HOME, Item.NAV_HIDE_ORDER, Item.NAV_HIDE_VIP,
                Item.NAV_HIDE_TASK, Item.MAIN_FLOAT),
        HOME_PROMOTIONS(Section.MAIN, "首页推广", "搜索营销、横幅、红包与启动免单",
                Item.HOME_SEARCH_HINT, Item.HOME_SEARCH_BANNER, Item.HOME_TOP, Item.HOME_MENUS,
                Item.HOME_FEED, Item.HOME_BRAND_COUPON, Item.HOME_ORDER, Item.HOME_FAB, Item.HOME_BOTTOM,
                Item.HOME_FREE_ORDER_POPUP),
        HOME_INTERACTION(Section.MAIN, "首页主题与交互", "去节日主题，禁第二层，保留刷新",
                Item.HOME_THEME, Item.HOME_SECOND),
        ORDER_PROMOTIONS(Section.ORDER, "订单推广", "列表、奖励与上传凭证页广告",
                Item.ORDER_CHALLENGE, Item.ORDER_INVITE, Item.ORDER_FEED, Item.ORDER_AD,
                Item.ORDER_VOUCHER_NATIVE_AD, Item.ORDER_TIP, Item.ORDER_REWARD),
        NEW_STORE(Section.ORDER, "新店任务跳转", "屏蔽手动／自动打开与退出挽留",
                Item.ORDER_FOOD_DETECTIVE_PAGE, Item.DETAIL_FOOD_DETECTIVE_AUTO_JUMP,
                Item.DETAIL_FOOD_DETECTIVE_WEB_EXIT),
        DETAIL(Section.ORDER, "活动详情推广", "隐藏广告占位与分享赚豆入口",
                Item.DETAIL_AD, Item.DETAIL_SHARE),
        PROFILE_PROMOTIONS(Section.PROFILE, "我的推广", "广告、公告、奖励与会员轮播",
                Item.USER_AD, Item.USER_COMMERCE, Item.USER_NOTICE, Item.USER_FLOAT,
                Item.USER_BONUS, Item.USER_NEW, Item.USER_MATCH, Item.USER_PROFIT, Item.USER_VIP,
                Item.VIP_HIDE_SVIP),
        PROFILE_SHORTCUTS(Section.PROFILE, "我的菜单与服务", "隐藏快捷菜单、商家入口和服务区",
                Item.USER_MENU, Item.USER_MERCHANT, Item.USER_SERVICE),
        WITHDRAW(Section.PROFILE, "提现关注提示", "关闭提现成功后的公众号推广",
                Item.WITHDRAW_FOLLOW);

        final Section section;
        final String title;
        final String summary;
        final List<Item> items;

        Group(Section section, String title, String summary, Item... items) {
            this.section = section;
            this.title = title;
            this.summary = summary;
            this.items = Collections.unmodifiableList(Arrays.asList(items));
        }

        int enabledCount(Map<Item, Boolean> values) {
            int count = 0;
            for (Item item : items) if (Boolean.TRUE.equals(values.get(item))) count++;
            return count;
        }

        State state(Map<Item, Boolean> values) {
            int count = enabledCount(values);
            if (count == 0) return State.OFF;
            return count == items.size() ? State.ON : State.MIXED;
        }

        void setEnabled(Map<Item, Boolean> draft, boolean enabled) {
            for (Item item : items) draft.put(item, enabled);
        }
    }

    private SettingsGroups() {}

    /** Preserve untouched groups from the latest read; a deliberately edited group applies in full. */
    static EnumMap<Item, Boolean> mergeEdits(EnumMap<Item, Boolean> original,
                                           EnumMap<Item, Boolean> draft,
                                           EnumMap<Item, Boolean> latest) {
        EnumMap<Item, Boolean> result = new EnumMap<>(latest);
        for (Group group : Group.values()) {
            for (Item item : group.items) {
                if (!Objects.equals(original.get(item), draft.get(item))) {
                    for (Item member : group.items) result.put(member, draft.get(member));
                    break;
                }
            }
        }
        return result;
    }
}
