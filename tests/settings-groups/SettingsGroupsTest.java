package io.github.jessire.xiaocanpurify;

import java.util.EnumMap;
import java.util.EnumSet;

import io.github.jessire.xiaocanpurify.LayoutSettings.Item;
import io.github.jessire.xiaocanpurify.SettingsGroups.Group;
import io.github.jessire.xiaocanpurify.SettingsGroups.State;

public final class SettingsGroupsTest {
    public static void main(String[] args) {
        coversEveryPreferenceExactlyOnce();
        mixedGroupsRequireAnExplicitChoice();
        mixedGroupsSurviveUnrelatedChanges();
        savesOnlyGroupsWithNetEdits();
        System.out.println("SettingsGroupsTest passed");
    }

    private static void coversEveryPreferenceExactlyOnce() {
        EnumSet<Item> covered = EnumSet.noneOf(Item.class);
        for (Group group : Group.values()) {
            check(!group.items.isEmpty(), group + " is empty");
            for (Item item : group.items) check(covered.add(item), "duplicate: " + item);
        }
        check(covered.equals(EnumSet.allOf(Item.class)), "some original preferences are missing");
        check(Group.WITHDRAW.items.size() == 1 && Group.WITHDRAW.items.contains(Item.WITHDRAW_FOLLOW),
                "withdraw should remain independent");
        check(Group.HOME_PROMOTIONS.items.contains(Item.HOME_FREE_ORDER_POPUP), "startup popup group");
        check(Group.HOME_INTERACTION.items.contains(Item.HOME_SECOND), "home interaction group");
        check(Group.ORDER_PROMOTIONS.items.contains(Item.ORDER_VOUCHER_NATIVE_AD), "voucher promotion group");
        check(Group.NEW_STORE.items.equals(java.util.List.of(Item.ORDER_FOOD_DETECTIVE_PAGE,
                Item.DETAIL_FOOD_DETECTIVE_AUTO_JUMP, Item.DETAIL_FOOD_DETECTIVE_WEB_EXIT)), "new store group");
    }

    private static void mixedGroupsRequireAnExplicitChoice() {
        for (Group group : Group.values()) {
            EnumMap<Item, Boolean> original = all(true);
            check(group.state(original) == State.ON, "all enabled: " + group);
            check(group.state(all(false)) == State.OFF, "all disabled: " + group);
            if (group.items.size() < 2) continue;
            original.put(group.items.get(0), false);
            EnumMap<Item, Boolean> draft = new EnumMap<>(original);
            check(group.state(draft) == State.MIXED, "legacy mix: " + group);
            check(group.enabledCount(draft) == group.items.size() - 1, "mixed count: " + group);
            check(draft.equals(original), "reading mixed state modified the draft");
            for (boolean enabled : new boolean[] {true, false}) {
                group.setEnabled(draft, enabled);
                EnumMap<Item, Boolean> result = SettingsGroups.mergeEdits(original, draft, original);
                check(group.state(result) == (enabled ? State.ON : State.OFF), "explicit choice: " + group);
                for (Item item : Item.values()) {
                    boolean expected = group.items.contains(item) ? enabled : original.get(item);
                    check(result.get(item) == expected, "unintended preference change: " + item);
                }
            }
            check(group.state(original) == State.MIXED, "discarding draft must preserve original: " + group);
        }
    }

    private static void mixedGroupsSurviveUnrelatedChanges() {
        EnumMap<Item, Boolean> original = all(true);
        original.put(Item.HOME_FAB, false);
        original.put(Item.ORDER_FOOD_DETECTIVE_PAGE, false);
        EnumMap<Item, Boolean> draft = new EnumMap<>(original);
        Group.WITHDRAW.setEnabled(draft, false);
        EnumMap<Item, Boolean> result = SettingsGroups.mergeEdits(original, draft, original);
        check(result.equals(draft), "save rewrote untouched mixed items");
        check(Group.HOME_PROMOTIONS.state(result) == State.MIXED, "home mix lost");
        check(Group.NEW_STORE.state(result) == State.MIXED, "new store mix lost");
        check(Group.WITHDRAW.state(result) == State.OFF, "withdraw not changed");
    }

    private static void savesOnlyGroupsWithNetEdits() {
        EnumMap<Item, Boolean> original = all(true);
        original.put(Item.HOME_FAB, false);
        EnumMap<Item, Boolean> draft = new EnumMap<>(original);
        EnumMap<Item, Boolean> latest = new EnumMap<>(original);
        latest.put(Item.HOME_FEED, false);
        latest.put(Item.ORDER_AD, false);
        Group.ORDER_PROMOTIONS.setEnabled(draft, false);
        Group.ORDER_PROMOTIONS.setEnabled(draft, true); // No net edit.
        check(SettingsGroups.mergeEdits(original, draft, latest).equals(latest), "no edits changed latest values");
        Group.WITHDRAW.setEnabled(draft, false);
        EnumMap<Item, Boolean> result = SettingsGroups.mergeEdits(original, draft, latest);
        EnumMap<Item, Boolean> expected = new EnumMap<>(latest);
        expected.put(Item.WITHDRAW_FOLLOW, false);
        check(result.equals(expected), "undo/untouched groups must keep the latest values");
        Group.HOME_PROMOTIONS.setEnabled(draft, true);
        result = SettingsGroups.mergeEdits(original, draft, latest);
        check(Group.HOME_PROMOTIONS.state(result) == State.ON, "explicit choice should apply to entire group");
        check(!result.get(Item.ORDER_AD), "other edit was lost");
    }

    private static EnumMap<Item, Boolean> all(boolean enabled) {
        EnumMap<Item, Boolean> values = new EnumMap<>(Item.class);
        for (Item item : Item.values()) values.put(item, enabled);
        return values;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
