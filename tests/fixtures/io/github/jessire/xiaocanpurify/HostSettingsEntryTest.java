package io.github.jessire.xiaocanpurify;

import java.util.List;
import java.util.Map;

/** Route-history policy regression, independent of Android view inflation. */
public final class HostSettingsEntryTest {
    public static void main(String[] args) {
        var settings = Map.of("name", "/silkworm-options");
        var child = Map.of("name", "/withdraw");
        require(true, null, true, "fresh launch");
        require(true, List.of(), true, "empty saved history takes the verified intent route");
        require(true, List.of(settings), true, "restored settings");
        require(false, List.of(settings, child), true, "restore while another route is on top");
        require(false, List.of(settings, child), false, "same-container push away");
        require(true, List.of(settings), false, "same-container pop back");
        require(false, List.of(), false, "empty runtime sync cannot prove the top route");
        require(false, null, false, "missing runtime history");
        require(false, settings, false, "malformed history");
        require(false, List.of(42), false, "malformed route item");
        require(false, List.of(Map.of("name", "/silkworm-options?source=profile")), false, "not a prefix match");
        require(false, List.of(Map.of("route", "/silkworm-options")), false, "unknown schema");
        System.out.println("PASS: HostSettingsEntry route-history policy");
    }

    private static void require(boolean expected, Object history, boolean allowEmpty, String scenario) {
        if (HostSettingsEntry.isSettingsHistory(history, allowEmpty) != expected) {
            throw new AssertionError(scenario);
        }
    }
}
