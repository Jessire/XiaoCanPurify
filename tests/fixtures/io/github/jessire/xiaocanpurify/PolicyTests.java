package io.github.jessire.xiaocanpurify;
import java.util.Map;
public final class PolicyTests {
    public static void run() {
        AutoJumpWindow guard = new AutoJumpWindow();
        check(!guard.isActive(0), "idle navigation must remain available");
        guard.arm(1000);
        check(guard.isActive(1000) && guard.isActive(3100), "post-enrollment auto launch must be blocked");
        guard.onUserInput();
        check(guard.isActive(3100), "confirmation touches must not disable enrollment protection");
        guard.onManualStoreRequest();
        check(!guard.isActive(3100), "manual navigation must remain available");
        guard.arm(4000);
        check(!guard.isActive(34000), "stale enrollment guard must expire");
        check(WithdrawAdPolicy.isAdPlacement("matchplacement", Map.of("resource_slug", "user_withdraw_dialog_ad")), "withdraw ad selector");
        check(!WithdrawAdPolicy.isAdPlacement("withdraw", Map.of("resource_slug", "user_withdraw_dialog_ad")), "withdraw transaction must not be blocked");
        check(!WithdrawAdPolicy.isAdPlacement("matchplacement", Map.of("resource_slug", "order_content")), "business placement must remain available");
        check(!WithdrawAdPolicy.isAdPlacement("matchplacement", Map.of("resource_slug", "user_withdraw_dialog_ad", "placement", "order_content")), "mixed batch must not be dropped");
        check(WithdrawAdPolicy.isAdPlacement("matchplacement", Map.of("resource_slug", "WITHDRAWAL_SUCCESS_POPUP")), "withdraw success popup selector");
        check(WithdrawAdPolicy.isAdPlacement("matchplacement", Map.of("resource_slug", "WITHDRAWALPAGE_POPUP")), "withdraw page popup selector");
        check(WithdrawAdPolicy.isAdPlacement("matchplacement", Map.of("resource_slug", "WITHDRAWAL_SUCCESS_POPUP", "placement_id", "12345")), "withdraw popup with numeric id selector");
        check(WithdrawAdPolicy.isAdPlacement("PlacementMatchService", Map.of(
                "resource_slug", java.util.List.of("WITHDRAWAL_SUCCESS_POPUP"),
                "silk_id", "123",
                "city_code", "010"
        )), "real withdraw success popup payload with list resource_slug");
        check(WithdrawAdPolicy.isAdPlacement("PlacementMatchService", Map.of(
                "placement_id", "8876",
                "resource_id", "999"
        )), "real withdraw placement id 8876");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
