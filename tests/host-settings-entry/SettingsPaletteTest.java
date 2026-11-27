package io.github.jessire.xiaocanpurify;

public final class SettingsPaletteTest {
    public static void main(String[] args) {
        for (boolean dark : new boolean[]{false, true}) {
            SettingsUi.Palette p = new SettingsUi.Palette(dark);
            check("All on", p.primaryText, p.primaryFill, 4.5);
            check("All off", p.subtext, p.surfaceMuted, 4.5);
            check("Rule labels", p.text, p.card, 4.5);
            check("Inactive tab counts", p.muted, p.surfaceMuted, 4.5);
            check("Restart required hint", p.muted, p.bg, 4.5);
            check("Live hint", p.jadeDark, p.bg, 4.5);
            check("Selected tab count", p.jadeDark, p.card, 4.5);
            check("Restart message", p.capsuleText, p.capsuleFill, 4.5);
            check("Restart icon", p.capsuleIcon, p.capsuleFill, 3.0);
            check("Restart button", p.restartText, p.restartFill, 4.5);
        }
        System.out.println("PASS: light and dark settings palette contrast");
    }

    private static void check(String label, int foreground, int background, double min) {
        double a = luminance(foreground);
        double b = luminance(background);
        double contrast = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
        if (contrast < min) throw new AssertionError(label + ": contrast " + contrast + " < " + min);
    }

    private static double luminance(int rgb) {
        return 0.2126 * linear(rgb >> 16) + 0.7152 * linear(rgb >> 8) + 0.0722 * linear(rgb);
    }

    private static double linear(int channel) {
        double srgb = (channel & 0xFF) / 255.0;
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }
}

