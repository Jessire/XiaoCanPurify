package io.github.jessire.xiaocanpurify;

import java.util.Map;

final class WithdrawAdPolicy {
    private WithdrawAdPolicy() {}

    static boolean isAdPlacement(String service, Object data) {
        if (service == null || (!service.contains("placement") && !service.contains("/g/pa"))) return false;
        if (!(data instanceof Map)) return false;
        boolean found = false;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) data).entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!key.equals("placement") && !key.equals("placement_id") && !key.equals("placementId")
                    && !key.equals("resource_slug") && !key.equals("resourceSlug") && !key.equals("slug")) continue;
            Object value = entry.getValue();
            if (!"user_withdraw_dialog_ad".equals(value) && !"user_withdraw_banner_ad".equals(value)
                    && !"withdrawal_success_popup".equals(value)) return false;
            found = true;
        }
        return found;
    }
}
