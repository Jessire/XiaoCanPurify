package io.github.jessire.xiaocanpurify;

/** Plain-JVM test for the compositor's split and inverse pointer map. */
public final class SettingsRowGeometryTest {
    public static void main(String[] args) {
        int seam = SettingsRowGeometry.findSeam(121, 132, 83, 757);
        eq(121, seam, "insert below heading (not at first row center)");
        eq(-1, SettingsRowGeometry.findSeam(120, 119, 83, 757), "adjacent row already overlaps");
        eq(-1, SettingsRowGeometry.findSeam(83, 132, 83, 757), "scrolled-out heading");
        eq(-1, SettingsRowGeometry.findSeam(121, 757, 83, 757), "first row outside viewport");
        SettingsRowGeometry g = new SettingsRowGeometry(seam, 52, 16, 376, 8, 9, true);
        check(g.inRow(16, 121) && !g.inRow(376, 121) && !g.inRow(16, 173), "exclusive right/bottom edges");
        check(g.inGap(140) && !g.inRow(3, 140), "horizontal gutter is not clickable");
        check(g.inBand(0, 140, 400) && g.inBand(399, 140, 400) && !g.inBand(400, 140, 400)
                && !g.inBand(100, 173, 400), "visible row hit area fills only its entire allocated band");
        eq(121, g.mapBottom(121), "heading bottom stays before reserve");
        eq(173, g.mapTop(121), "native row starting at seam is not covered");
        eq(809, g.mapTop(757), "fixed footer keeps its allocated space");
        check(!g.cuts(85, 121) && g.cuts(120, 122) && !g.cuts(121, 150), "detect split control");
        for (int y = 0; y < 820; y++) {
            int shown = g.mapTop(y);
            check(!g.inGap(shown), "no native pixel can land in gap");
            eq(y, shown - g.offsetAt(shown), "round trip at y=" + y);
        }
        check(g.sameHitBand(180, 52) && !g.sameHitBand(140, 52) && !g.sameHitBand(115, 52),
                "a pending native tap cannot end in another band");
        SettingsRowGeometry offscreen = new SettingsRowGeometry(757, 52, 0, 0, -1, -1, false);
        check(!offscreen.showsRow() && !offscreen.inRow(100, 770), "offscreen reserve has no button");
        eq(121, offscreen.mapTop(121), "do not shift visible list when heading is offscreen");
        eq(809, offscreen.mapTop(757), "footer remains below the reserve");
        System.out.println("PASS: SettingsRowGeometry (anchor, bands, touch round-trip, offscreen)");
    }

    private static void eq(int expected, int actual, String what) {
        check(expected == actual, what + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean value, String what) {
        if (!value) throw new AssertionError(what);
    }
}
