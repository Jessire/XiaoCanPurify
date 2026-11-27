package io.github.jessire.xiaocanpurify;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import io.github.jessire.xiaocanpurify.LayoutSettings.Item;
import io.github.jessire.xiaocanpurify.SettingsGroups.Scene;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;

/**
 * Purification console: apply on tap, scene tabs, hot vs structural rules,
 * and a non-blocking restart capsule. No in-host resources or images.
 */
final class SettingsUi {
    private SettingsUi() {}

    static View profileEntry(Context host) {
        Palette colors = new Palette(host);
        LinearLayout row = horizontal(host);
        pad(row, 16, 10, 16, 10);
        row.setMinimumHeight(dp(host, 52));
        row.setBackground(ripple(host, colors.card, colors.ripple, 14));
        disableForceDark(row);

        TextView title = text(host, "小蚕净化设置", 16, colors.text);
        row.addView(title, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        TextView arrow = text(host, "›", 22, colors.subtext);
        arrow.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        row.addView(arrow);
        hideAccessibilityChildren(row);
        row.setContentDescription(title.getText());
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(view, info);
                info.setClassName(Button.class.getName());
            }
        });
        row.setFocusable(true);
        row.setOnClickListener(v -> open(host, null));
        return row;
    }

    static String enabledSummary(Context host) {
        try {
            EnumMap<Item, Boolean> values = LayoutSettings.read(host);
            return countEnabled(values) + "/" + Item.values().length + " 已开";
        } catch (RuntimeException e) {
            return "设置";
        }
    }

    static void open(Context host, Runnable onClosed) {
        EnumMap<Item, Boolean> values;
        try {
            values = LayoutSettings.read(host);
        } catch (RuntimeException e) {
            toast(host, "无法读取净化设置，请稍后重试");
            return;
        }
        ConsoleDialog dialog = new ConsoleDialog(host, new Palette(host), values);
        if (onClosed != null) dialog.setOnDismissListener(d -> onClosed.run());
        dialog.show();
    }

    private static int countEnabled(EnumMap<Item, Boolean> values) {
        int count = 0;
        for (Item item : Item.values()) {
            if (Boolean.TRUE.equals(values.get(item))) count++;
        }
        return count;
    }

    private static final class ConsoleDialog extends Dialog {
        private final Context host;
        private final Palette colors;
        private final EnumMap<Item, Boolean> values;
        private final LinearLayout list;
        private final LinearLayout hero;
        private final TextView heroCount;
        private final TextView heroOn;
        private final TextView heroOff;
        private final LinearLayout capsule;
        private final TextView capsuleText;
        private final List<TabButton> tabs = new ArrayList<>();
        private Scene scene = Scene.HOME;
        private ScrollView scroll;
        private boolean applying;

        ConsoleDialog(Context host, Palette colors, EnumMap<Item, Boolean> values) {
            super(host, colors.dark ? android.R.style.Theme_DeviceDefault_NoActionBar
                    : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
            this.host = host;
            this.colors = colors;
            this.values = values;

            Context ctx = getContext();
            LinearLayout root = vertical(ctx);
            root.setBackgroundColor(colors.bg);

            // ---- Top bar: back, title, overflow ----
            LinearLayout bar = horizontal(ctx);
            bar.setMinimumHeight(dp(ctx, 56));
            pad(bar, 8, 6, 12, 6);
            bar.setBackgroundColor(colors.bg);

            Glyph back = new Glyph(ctx, Glyph.BACK, colors.text, 2.0f);
            back.setContentDescription("返回");
            back.setBackground(ripple(ctx, Color.TRANSPARENT, colors.ripple, 22));
            back.setOnClickListener(v -> dismiss());
            LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40));
            bar.addView(back, backParams);

            TextView title = text(ctx, "小蚕净化", 21, colors.text);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            markHeading(title);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
            titleParams.setMarginStart(dp(ctx, 8));
            bar.addView(title, titleParams);

            Glyph more = new Glyph(ctx, Glyph.DOTS, colors.text, 0f);
            more.setContentDescription("更多");
            more.setBackground(ripple(ctx, colors.card, colors.ripple, 22));
            more.setOnClickListener(this::showOverflow);
            bar.addView(more, new LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)));
            root.addView(bar, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            // ---- Scrollable body ----
            scroll = new ScrollView(ctx);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            LinearLayout body = vertical(ctx);
            pad(body, 16, 8, 16, 116);

            // Hero: live status + count + batch pills
            hero = vertical(ctx);
            hero.setBackground(surface(ctx, colors.card, colors.border, 20));
            pad(hero, 18, 16, 16, 16);

            LinearLayout statusRow = horizontal(ctx);
            View dot = new View(ctx);
            dot.setBackground(rounded(ctx, colors.jade, 4));
            statusRow.addView(dot, new LinearLayout.LayoutParams(dp(ctx, 7), dp(ctx, 7)));
            addGap(statusRow, dp(ctx, 8));
            TextView status = text(ctx, "实时净化中", 11.5f, colors.jadeDark);
            status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            statusRow.addView(status);
            hero.addView(statusRow, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            LinearLayout countRow = horizontal(ctx);
            countRow.setGravity(Gravity.CENTER_VERTICAL);
            countRow.setPadding(0, dp(ctx, 6), 0, 0);

            LinearLayout countLabels = horizontal(ctx);
            countLabels.setGravity(Gravity.BOTTOM);
            heroCount = text(ctx, "0", 28, colors.text);
            heroCount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            heroCount.setIncludeFontPadding(false);
            countLabels.addView(heroCount);
            TextView suffix = text(ctx, " / " + Item.values().length + " 项已启用", 12.5f, colors.subtext);
            suffix.setIncludeFontPadding(false);
            LinearLayout.LayoutParams suffixParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            suffixParams.setMarginStart(dp(ctx, 4));
            suffixParams.bottomMargin = dp(ctx, 2);
            countLabels.addView(suffix, suffixParams);
            countRow.addView(countLabels, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

            heroOn = pill(ctx, "全开", colors.primaryFill, colors.primaryText);
            heroOn.setOnClickListener(v -> applyAll(true));
            heroOff = pill(ctx, "全关", colors.surfaceMuted, colors.subtext);
            heroOff.setOnClickListener(v -> applyAll(false));
            LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(dp(ctx, 68), dp(ctx, 38));
            pillParams.setMarginStart(dp(ctx, 8));
            countRow.addView(heroOn, pillParams);
            LinearLayout.LayoutParams offParams = new LinearLayout.LayoutParams(dp(ctx, 68), dp(ctx, 38));
            offParams.setMarginStart(dp(ctx, 8));
            countRow.addView(heroOff, offParams);

            hero.addView(countRow, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            body.addView(hero, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            // Scene segmented control
            LinearLayout segment = horizontal(ctx);
            segment.setBackground(rounded(ctx, colors.surfaceMuted, 24));
            pad(segment, 4, 4, 4, 4);
            LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            segmentParams.topMargin = dp(ctx, 14);
            for (Scene value : Scene.values()) {
                TabButton tab = new TabButton(ctx, value);
                tabs.add(tab);
                segment.addView(tab, new LinearLayout.LayoutParams(0, dp(ctx, 38), 1f));
            }
            body.addView(segment, segmentParams);

            list = vertical(ctx);
            LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            listParams.topMargin = dp(ctx, 14);
            body.addView(list, listParams);

            scroll.addView(body, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f));

            // ---- Floating restart capsule ----
            capsule = horizontal(ctx);
            capsule.setBackground(surface(ctx, colors.capsuleFill, colors.capsuleBorder, 26));
            capsule.setElevation(dp(ctx, 8));
            pad(capsule, 18, 8, 8, 8);
            capsule.setVisibility(GONE);

            Glyph icon = new Glyph(ctx, Glyph.RESTART, colors.capsuleIcon, 2.0f);
            capsule.addView(icon, new LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)));
            addGap(capsule, dp(ctx, 10));

            capsuleText = text(ctx, "", 13, colors.capsuleText);
            capsuleText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            capsule.addView(capsuleText, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

            TextView restartButton = pill(ctx, "立即重启", colors.restartFill, colors.restartText);
            restartButton.setOnClickListener(v -> restart(host));
            capsule.addView(restartButton, new LinearLayout.LayoutParams(dp(ctx, 88), dp(ctx, 36)));

            FrameLayout overlay = new FrameLayout(ctx);
            disableForceDark(overlay);
            overlay.addView(root, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            FrameLayout.LayoutParams capsuleParams = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            capsuleParams.gravity = Gravity.BOTTOM;
            capsuleParams.setMargins(dp(ctx, 16), 0, dp(ctx, 16), dp(ctx, 22));
            overlay.addView(capsule, capsuleParams);

            applyInsets(bar, scroll, capsuleParams, overlay);
            setContentView(overlay);

            Window window = getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(colors.bg));
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setStatusBarColor(colors.bg);
                window.setNavigationBarColor(colors.bg);
                int flags = window.getDecorView().getSystemUiVisibility();
                int lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                window.getDecorView().setSystemUiVisibility(colors.dark ? flags & ~lightBars : flags | lightBars);
            }

            select(Scene.HOME);
            refreshSummary();
        }

        @SuppressWarnings("deprecation")
        private void applyInsets(View bar, View scroll, FrameLayout.LayoutParams capsuleParams, View overlay) {
            bar.setOnApplyWindowInsetsListener((v, insets) -> {
                int top = insets.getSystemWindowInsetTop();
                int left = insets.getSystemWindowInsetLeft();
                int right = insets.getSystemWindowInsetRight();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                    left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
                    right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
                }
                v.setPadding(dp(getContext(), 8) + left, dp(getContext(), 6) + top,
                        dp(getContext(), 12) + right, dp(getContext(), 6));
                return insets;
            });
            overlay.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = insets.getSystemWindowInsetBottom();
                capsuleParams.bottomMargin = dp(getContext(), 22) + Math.max(0, bottom - dp(getContext(), 16));
                capsule.setLayoutParams(capsuleParams);
                scroll.setPadding(0, 0, 0, bottom);
                return insets;
            });
        }

        private final class TabButton extends LinearLayout {
            private final Scene value;
            private final TextView label;
            private final TextView count;

            TabButton(Context ctx, Scene value) {
                super(ctx);
                this.value = value;
                setOrientation(HORIZONTAL);
                setGravity(Gravity.CENTER);
                setFocusable(true);

                label = text(ctx, value.title, 13, colors.subtext);
                count = text(ctx, String.valueOf(value.total()), 10.5f, colors.muted);

                addView(label);
                LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                countParams.setMarginStart(dp(ctx, 4));
                addView(count, countParams);

                setOnClickListener(v -> select(value));
                setBackground(ripple(ctx, Color.TRANSPARENT, colors.ripple, 20));
            }

            void setActive(boolean active) {
                setBackground(active
                        ? surface(getContext(), colors.card, colors.border, 20)
                        : ripple(getContext(), Color.TRANSPARENT, colors.ripple, 20));
                label.setTextColor(active ? colors.text : colors.subtext);
                label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
                count.setTextColor(active ? colors.jadeDark : colors.muted);
                count.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
                setContentDescription(value.title + "，共 " + value.total() + " 项" + (active ? "，已选中" : ""));
                setSelected(active);
            }
        }

        private void select(Scene value) {
            scene = value;
            for (TabButton tab : tabs) tab.setActive(tab.value == value);
            render();
        }

        private void render() {
            Context ctx = getContext();
            list.removeAllViews();

            addSection(ctx, "即时生效", "点按即刻应用，无需重启", colors.jadeDark,
                    scene.items(true), true);
            addSection(ctx, "结构与初始化", "改动后需重启小蚕生效", colors.muted,
                    scene.items(false), false);
        }

        private void addSection(Context ctx, String title, String hint, int hintColor,
                                List<Item> items, boolean hot) {
            if (items.isEmpty()) return;

            LinearLayout header = horizontal(ctx);
            TextView heading = text(ctx, title, 12.5f, colors.subtext);
            heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            markHeading(heading);
            header.addView(heading, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
            TextView trailing = text(ctx, hint, 11.5f, hintColor);
            header.addView(trailing);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            headerParams.setMargins(dp(ctx, 4), list.getChildCount() == 0 ? dp(ctx, 0) : dp(ctx, 20), dp(ctx, 4), dp(ctx, 10));
            list.addView(header, headerParams);

            LinearLayout card = vertical(ctx);
            card.setBackground(surface(ctx, colors.card, colors.border, 18));
            card.setClipToOutline(true);
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) card.addView(hairline(ctx, colors.hairline, dp(ctx, 18), dp(ctx, 18)));
                card.addView(new RuleRow(ctx, items.get(i)), new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            }
            list.addView(card, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }

        private final class RuleRow extends LinearLayout {
            private final Item item;
            private final ToggleSwitch toggle;

            RuleRow(Context ctx, Item item) {
                super(ctx);
                this.item = item;
                setOrientation(HORIZONTAL);
                setGravity(Gravity.CENTER_VERTICAL);
                pad(this, 18, 6, 16, 6);
                setMinimumHeight(dp(ctx, 48));
                setFocusable(true);
                setBackground(ripple(ctx, Color.TRANSPARENT, colors.ripple, 0));

                TextView label = text(ctx, item.title, 14, colors.text);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
                labelParams.setMarginEnd(dp(ctx, 12));
                addView(label, labelParams);

                toggle = new ToggleSwitch(ctx, colors);
                addView(toggle, new LinearLayout.LayoutParams(dp(ctx, 42), dp(ctx, 24)));

                hideAccessibilityChildren(this);
                setOnClickListener(v -> toggleItem());
                refresh();
            }

            void refresh() {
                boolean on = Boolean.TRUE.equals(values.get(item));
                toggle.setChecked(on, false);
                setContentDescription(item.title + "，" + (on ? "已开启" : "已关闭")
                        + (LayoutSettings.hotCapable(item) ? "" : "，需重启小蚕生效"));
            }

            private void toggleItem() {
                if (applying) return;
                applying = true;
                try {
                    EnumMap<Item, Boolean> latest = LayoutSettings.read(host);
                    latest.put(item, !Boolean.TRUE.equals(latest.get(item)));
                    if (LayoutSettings.save(host, latest)) {
                        values.clear();
                        values.putAll(latest);
                        refresh();
                        refreshSummary();
                    } else {
                        showSaveFailure();
                    }
                } catch (RuntimeException e) {
                    showSaveFailure();
                } finally {
                    applying = false;
                }
            }
        }

        private void showSaveFailure() {
            toast(host, "保存失败，请重试");
            render();
        }

        private void applyAll(boolean enabled) {
            applyAll(enabled, false);
        }

        private void applyAll(boolean enabled, boolean restoreDefaults) {
            if (applying) return;
            applying = true;
            try {
                EnumMap<Item, Boolean> latest = LayoutSettings.read(host);
                for (Item item : Item.values()) {
                    latest.put(item, restoreDefaults ? LayoutSettings.defaultEnabled(item) : enabled);
                }
                if (LayoutSettings.save(host, latest)) {
                    values.clear();
                    values.putAll(latest);
                    render();
                    refreshSummary();
                } else {
                    showSaveFailure();
                }
            } catch (RuntimeException e) {
                showSaveFailure();
            } finally {
                applying = false;
            }
        }

        private void refreshSummary() {
            heroCount.setText(String.valueOf(countEnabled(values)));
            int pending = LayoutSettings.restartPendingCount(values);
            if (pending > 0) {
                capsuleText.setText(pending + " 项结构改动待重启");
                if (capsule.getVisibility() != VISIBLE) {
                    capsule.setVisibility(VISIBLE);
                    capsule.setAlpha(0f);
                    capsule.animate().alpha(1f).setDuration(140).start();
                }
            } else if (capsule.getVisibility() == VISIBLE) {
                capsule.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                    capsule.setVisibility(GONE);
                    capsule.setAlpha(1f);
                }).start();
            }
        }

        private void showOverflow(View anchor) {
            PopupMenu menu = new PopupMenu(getContext(), anchor);
            menu.getMenu().add(0, 1, 0, "全部恢复默认");
            menu.getMenu().add(0, 2, 1, "关闭全部");
            menu.getMenu().add(0, 3, 2, "关于小蚕净化");
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1 -> applyAll(true, true);
                    case 2 -> applyAll(false);
                    case 3 -> showAbout();
                    default -> { }
                }
                return true;
            });
            menu.show();
        }

        private void showAbout() {
            String version;
            try {
                PackageInfo info = host.getPackageManager()
                        .getPackageInfo(MainHook.TARGET_PACKAGE, 0);
                long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
                version = "适配小蚕 " + info.versionName + "（" + code + "）";
            } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                version = "适配小蚕 3.21.2（2985）、3.23.0（3070）";
            }
            int hot = 0;
            for (Item item : Item.values()) if (LayoutSettings.hotCapable(item)) hot++;
            new AlertDialog.Builder(getContext())
                    .setTitle("小蚕净化")
                    .setMessage(version + "\n" + hot + " 项即时生效，" + (Item.values().length - hot) + " 项结构性规则重启后生效。")
                    .setPositiveButton("知道了", null)
                    .show();
        }
    }

    // ---------------------------------------------------------------- widgets

    /** Animated pill switch: jade track when on, neutral when off. */
    private static final class ToggleSwitch extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Palette colors;
        private boolean checked;
        private float progress;
        private ValueAnimator animator;

        ToggleSwitch(Context ctx, Palette colors) {
            super(ctx);
            this.colors = colors;
        }

        void setChecked(boolean value, boolean animate) {
            float target = value ? 1f : 0f;
            if (checked == value && progress == target) return;
            checked = value;
            if (animator != null) animator.cancel();
            if (!animate || !isAttachedToWindow()) {
                progress = target;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(140);
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight();
            float w = getWidth();
            float radius = h / 2f;
            paint.setColor(progress > 0f ? blend(colors.trackOff, colors.jade, progress) : colors.trackOff);
            canvas.drawRoundRect(0, 0, w, h, radius, radius, paint);

            float inset = dp(getContext(), 3);
            float thumb = radius - inset;
            float travel = w - 2 * radius;
            float cx = radius + travel * progress;
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, radius, thumb, paint);
        }

        private static int blend(int from, int to, float t) {
            int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
            int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
            int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
            int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
            return Color.argb(a, r, g, b);
        }

        @Override public CharSequence getAccessibilityClassName() {
            return Switch.class.getName();
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setCheckable(true);
            info.setChecked(checked);
        }
    }

    /** Vector glyphs drawn with Canvas: no drawable resources needed. */
    private static final class Glyph extends View {
        static final int BACK = 0, DOTS = 1, RESTART = 2;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int kind;
        private final int color;
        private final float stroke;

        Glyph(Context ctx, int kind, int color, float stroke) {
            super(ctx);
            this.kind = kind;
            this.color = color;
            this.stroke = stroke;
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float unit = Math.min(w, h) / 2f;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(stroke <= 0 ? unit * 0.18f : dp(getContext(), stroke));
            path.reset();
            switch (kind) {
                case BACK -> {
                    float cx = w / 2f, cy = h / 2f, r = unit * 0.52f;
                    path.moveTo(cx + r * 0.55f, cy - r);
                    path.lineTo(cx - r * 0.45f, cy);
                    path.lineTo(cx + r * 0.55f, cy + r);
                    path.moveTo(cx - r, cy);
                    path.lineTo(cx + r, cy);
                }
                case RESTART -> {
                    // Rotate-cw in a centered 24-unit viewport.
                    float scale = Math.min(w, h) / 24f;
                    if (scale <= 0f) return;
                    canvas.save();
                    canvas.translate((w - 24f * scale) / 2f, (h - 24f * scale) / 2f);
                    canvas.scale(scale, scale);
                    paint.setStrokeWidth(2.25f);
                    path.moveTo(21f, 7f);
                    path.arcTo(3f, 2f, 21f, 20f, -45f, -315f, false);
                    path.moveTo(21f, 2f);
                    path.lineTo(21f, 7f);
                    path.lineTo(16f, 7f);
                    canvas.drawPath(path, paint);
                    canvas.restore();
                    return;
                }
                default -> {
                    paint.setStyle(Paint.Style.FILL);
                    float r = unit * 0.10f;
                    for (int i = -1; i <= 1; i++) {
                        canvas.drawCircle(w / 2f + i * unit * 0.34f, h / 2f, r, paint);
                    }
                    return;
                }
            }
            canvas.drawPath(path, paint);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static LinearLayout horizontal(Context ctx) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private static LinearLayout vertical(Context ctx) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(VERTICAL);
        return layout;
    }

    private static TextView text(Context ctx, String label, float sp, int color) {
        TextView view = new TextView(ctx);
        view.setText(label);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private static TextView pill(Context ctx, String label, int fill, int labelColor) {
        TextView view = text(ctx, label, 12.5f, labelColor);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(ctx, 34));
        view.setBackground(surface(ctx, fill, fill, 18));
        view.setFocusable(true);
        return view;
    }

    private static View hairline(Context ctx, int color, int marginStart, int marginEnd) {
        View divider = new View(ctx);
        divider.setBackgroundColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, Math.max(1, dp(ctx, 0.13f)));
        params.setMarginStart(marginStart);
        params.setMarginEnd(marginEnd);
        divider.setLayoutParams(params);
        return divider;
    }

    private static void addGap(LinearLayout row, int width) {
        View gap = new View(row.getContext());
        row.addView(gap, new LinearLayout.LayoutParams(width, 1));
    }

    private static void markHeading(TextView view) {
        if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
    }

    private static void hideAccessibilityChildren(LinearLayout group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        group.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    private static void pad(View view, int start, int top, int end, int bottom) {
        Context ctx = view.getContext();
        view.setPaddingRelative(dp(ctx, start), dp(ctx, top), dp(ctx, end), dp(ctx, bottom));
    }

    static GradientDrawable rounded(Context ctx, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(ctx, radius));
        return drawable;
    }

    private static GradientDrawable surface(Context ctx, int fill, int border, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(ctx, radius));
        drawable.setStroke(Math.max(1, dp(ctx, 0.5f)), border);
        return drawable;
    }

    static RippleDrawable ripple(Context ctx, int fill, int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(color), rounded(ctx, fill, radius),
                rounded(ctx, Color.WHITE, radius));
    }

    static void disableForceDark(View view) {
        if (Build.VERSION.SDK_INT >= 29) view.setForceDarkAllowed(false);
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void restart(Context host) {
        Intent launch = host.getPackageManager().getLaunchIntentForPackage(host.getPackageName());
        if (launch == null) {
            toast(host, "无法自动重启，请手动打开小蚕");
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            // The alarm lives in the system server, so it still relaunches after this process dies.
            PendingIntent pending = PendingIntent.getActivity(host, 0x5850, launch,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarms = (AlarmManager) host.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) throw new IllegalStateException("no alarm service");
            alarms.set(AlarmManager.RTC, System.currentTimeMillis() + 900, pending);
        } catch (RuntimeException e) {
            toast(host, "无法自动重启，请手动打开小蚕");
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> android.os.Process.killProcess(android.os.Process.myPid()), 150);
    }

    private static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    /** Warm stone light theme and a matching dark theme. */
    static final class Palette {
        final boolean dark;
        final int bg, card, surfaceMuted, border, hairline;
        final int text, subtext, muted;
        final int jade, jadeDark, trackOff, ripple;
        final int primaryFill, primaryText;
        final int capsuleFill, capsuleBorder, capsuleText, capsuleIcon;
        final int restartFill, restartText;

        Palette(Context ctx) {
            this((ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES);
        }

        Palette(boolean dark) {
            this.dark = dark;
            if (dark) {
                bg = 0xFF121311;
                card = 0xFF1C1E1B;
                surfaceMuted = 0xFF262825;
                border = 0xFF2C2F2A;
                hairline = 0xFF252823;
                text = 0xFFF2F3EF;
                subtext = 0xFFBDC2BA;
                muted = 0xFFA3A99F;
                primaryFill = 0xFF67DAB9;
                primaryText = 0xFF12231C;
                jade = 0xFF2BC49B;
                jadeDark = 0xFF4FD6B0;
                trackOff = 0xFF3A3D38;
                ripple = 0x30FFFFFF;
                capsuleFill = 0xFF24312B;
                capsuleBorder = 0xFF435B50;
                capsuleText = 0xFFF2F6F1;
                capsuleIcon = 0xFF67DAB9;
                restartFill = primaryFill;
                restartText = primaryText;
            } else {
                bg = 0xFFF6F6F4;
                card = 0xFFFFFFFF;
                surfaceMuted = 0xFFEBEBE7;
                border = 0xFFE6E6E1;
                hairline = 0xFFF0F0EC;
                text = 0xFF15171C;
                subtext = 0xFF5C6169;
                muted = 0xFF646972;
                primaryFill = 0xFF15171C;
                primaryText = 0xFFFFFFFF;
                jade = 0xFF0E9F7E;
                jadeDark = 0xFF097A60;
                trackOff = 0xFFD5D6D2;
                ripple = 0x18000000;
                capsuleFill = primaryFill;
                capsuleBorder = capsuleFill;
                capsuleText = 0xFFFFFFFF;
                capsuleIcon = 0xFF34D399;
                restartFill = primaryText;
                restartText = primaryFill;
            }
        }
    }
}
