package io.github.jessire.xiaocanpurify;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

final class WithdrawAdPolicy {
    private WithdrawAdPolicy() {}

    static boolean isAdPlacement(String service, Object data) {
        if (service == null) return false;
        String sLower = service.toLowerCase(Locale.ROOT);
        if (!sLower.contains("placement") && !sLower.contains("/g/pa")) return false;

        ScanResult result = new ScanResult();
        scan(data, result);
        return result.foundAd && !result.foundBusiness;
    }

    private static class ScanResult {
        boolean foundAd = false;
        boolean foundBusiness = false;
    }

    private static void scan(Object data, ScanResult result) {
        if (data instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) data).entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                Object val = entry.getValue();
                if (isSlotKey(key)) {
                    inspectValue(val, result);
                } else if (val instanceof Map || val instanceof Collection) {
                    scan(val, result);
                }
            }
        } else if (data instanceof Collection) {
            for (Object item : (Collection<?>) data) {
                if (item instanceof Map || item instanceof Collection) {
                    scan(item, result);
                } else {
                    inspectValue(item, result);
                }
            }
        }
    }

    private static void inspectValue(Object val, ScanResult result) {
        if (val instanceof Collection) {
            for (Object item : (Collection<?>) val) {
                inspectValue(item, result);
            }
            return;
        }
        if (isWithdrawAdValue(val)) {
            result.foundAd = true;
        } else if (isBusinessPlacementValue(val)) {
            result.foundBusiness = true;
        }
    }

    private static boolean isSlotKey(String key) {
        if (key == null) return false;
        return key.equals("placement") || key.equals("placement_id") || key.equals("placementid")
                || key.equals("resource_slug") || key.equals("resourceslug") || key.equals("slug")
                || key.equals("slotid") || key.equals("slot_id") || key.equals("type")
                || key.equals("scene") || key.equals("position");
    }

    private static boolean isWithdrawAdValue(Object val) {
        if (val == null) return false;
        String v = String.valueOf(val).toLowerCase(Locale.ROOT).trim();
        if (v.equals("user_withdraw_dialog_ad") || v.equals("user_withdraw_banner_ad")
                || v.equals("withdrawal_success_popup") || v.equals("withdrawalpage_popup")
                || v.equals("withdraw_pop_up") || v.equals("withdraw_placement")
                || v.equals("withdraw_placement_dialog") || v.equals("withdraw_flow")
                || v.equals("withdraw_up_activity_dialog") || v.equals("8876")) {
            return true;
        }
        return (v.contains("withdraw") || v.contains("withdrawal"))
                && (v.contains("dialog") || v.contains("popup") || v.contains("banner")
                || v.contains("placement") || v.contains("success") || v.contains("flow")
                || v.contains("ad"));
    }

    private static boolean isBusinessPlacementValue(Object val) {
        if (val == null) return false;
        String v = String.valueOf(val).toLowerCase(Locale.ROOT).trim();
        if (isWithdrawAdValue(val)) return false;
        if (v.length() < 3 || v.matches("\\d+")) return false;
        return true;
    }
}