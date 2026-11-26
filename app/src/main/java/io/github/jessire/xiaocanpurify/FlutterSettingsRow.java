package io.github.jessire.xiaocanpurify;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Compose the same Flutter texture in two non-overlapping bands. Flutter is actually measured
 * shorter by the measured row height, so no host pixels (including the footer) are replaced.
 * Flutter still owns its viewport, scroll physics, IDs, and settings actions.
 */
final class FlutterSettingsRow extends ViewGroup {
    private static final int NONE = 0, ORIGINAL = 1, HOST = 2, ENTRY = 3, GAP = 4;
    private final View flutter;
    private final EntryView row;

    private static final class EntryView extends TextView {
        EntryView(Activity activity) { super(activity); }

        boolean routeHover(MotionEvent event) { return super.dispatchHoverEvent(event); }
    }
    private final FlutterSettingsSemantics semantics;
    private final AccessibilityManager accessibility;
    private final AccessibilityManager.AccessibilityStateChangeListener accessListener;
    private final ViewTreeObserver.OnPreDrawListener preDraw = this::prepareFrame;
    private final Runnable resizeTimeout = this::recoverMissingFrame;
    private final int gutterColor, slop;
    private boolean loggedDown, loggedClick, loggedObscured;
    private boolean allowed = true, route, reserve, dirty = true, rejectedResize, waitingForFirstLayout;
    private Object bridge, forced, viewport;
    private SettingsRowGeometry geometry, downGeometry;
    private int touch, offset;
    private boolean moved, allowUp;
    private float downX, downY;
    private int hover;

    FlutterSettingsRow(Activity activity, View flutter, FlutterSettingsSemantics semantics) {
        super(activity);
        this.flutter = flutter;
        this.semantics = semantics;
        accessibility = activity.getSystemService(AccessibilityManager.class);
        accessListener = enabled -> {
            // The bridge's own listener runs on the same main thread. Never disable system semantics.
            forced = null;
            semanticsChanged();
        };
        slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        setMotionEventSplittingEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // The verified Flutter options page paints its own light surfaces, even when the
        // surrounding Android theme is dark. Use the same colours, not the module dialog palette.
        gutterColor = 0xfff5f4f2;
        row = new EntryView(activity);
        row.setId(View.generateViewId());
        SettingsUi.disableForceDark(row);
        row.setText("模块设置");
        row.setTextSize(14);
        row.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        row.setTextColor(0xff333333);
        row.setIncludeFontPadding(false);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        int v = SettingsUi.dp(activity, 8);
        row.setPadding(0, v, 0, v);
        row.setMinHeight(SettingsUi.dp(activity, 45));
        row.setBackground(SettingsUi.ripple(activity, 0xffffffff, 0x14000000, 0));
        row.setFocusable(true);
        row.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        if (Build.VERSION.SDK_INT >= 28) row.setScreenReaderFocusable(true);
        // This button only opens the module menu. Respect the existing host view's
        // touch policy; do not introduce a separate restriction on this navigation row.
        row.setFilterTouchesWhenObscured(flutter.getFilterTouchesWhenObscured());
        row.setVisibility(INVISIBLE);
        row.setOnClickListener(view -> {
            if (!hasEntry(geometry) || activity.isFinishing() || activity.isDestroyed()) return;
            if (!loggedClick) {
                loggedClick = true;
                MainHook.log("Flutter settings row: click");
            }
            try {
                SettingsUi.open(activity, null);
            } catch (RuntimeException | LinkageError error) {
                MainHook.log("Flutter settings row: open failed");
            }
        });
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(view, info);
                info.setClassName(Button.class.getName());
                SettingsRowGeometry g = geometry;
                if (g != null && g.showsRow()) info.setTraversalAfter(flutter, g.heading());
            }
        });
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        // Last operation on construction: the unattached FlutterView keeps its original ID/context.
        LayoutParams old = flutter.getLayoutParams();
        try {
            addView(flutter, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } catch (RuntimeException | LinkageError error) {
            if (flutter.getParent() == this) removeView(flutter);
            flutter.setLayoutParams(old);
            throw error;
        }
    }

    void setRouteVisible(boolean visible) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post(() -> setRouteVisible(visible));
            return;
        }
        if (!route && visible) rejectedResize = false;
        route = allowed && visible;
        if (!route) reset();
        semanticsChanged();
    }

    void disableEntry() {
        allowed = false;
        setRouteVisible(false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        accessibility.addAccessibilityStateChangeListener(accessListener);
        getViewTreeObserver().addOnPreDrawListener(preDraw);
        semanticsChanged();
    }

    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(preDraw);
        accessibility.removeAccessibilityStateChangeListener(accessListener);
        reset();
        bridge = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (flutter == null) return;
        if (visibility != VISIBLE) {
            rejectedResize = false;
            reset();
        }
        else semanticsChanged();
    }

    void semanticsChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post(this::semanticsChanged);
            return;
        }
        dirty = true;
        if (route) postInvalidateOnAnimation();
    }

    private void reset() {
        try {
            if (forced != null && !accessibility.isEnabled() && semantics.bridge(flutter) == forced) {
                semantics.setEngineSemantics(forced, false);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // FlutterView also turns off engine semantics on engine detachment.
        } finally {
            forced = null;
            bridge = null;
            viewport = null;
            setGeometry(null);
            setReserve(false);
        }
    }

    private void fail(Throwable error) {
        if (allowed) MainHook.log("Flutter settings row disabled: " + error.getClass().getSimpleName());
        disableEntry();
    }

    private void recoverMissingFrame() {
        if (reserve && geometry == null) {
            rejectedResize = true;
            viewport = null;
            setReserve(false);
        }
    }

    private void setReserve(boolean value) {
        if (reserve != value) {
            reserve = value;
            waitingForFirstLayout = value;
            removeCallbacks(resizeTimeout);
            if (value) postDelayed(resizeTimeout, 900);
            requestLayout();
            invalidate();
        }
    }

    private void setGeometry(SettingsRowGeometry g) {
        if (geometry == null ? g == null : geometry.equals(g)) return;
        boolean wasVisible = geometry != null && geometry.showsRow();
        geometry = g;
        boolean visible = g != null && g.showsRow();
        if (g != null) removeCallbacks(resizeTimeout);
        row.setVisibility(visible ? VISIBLE : INVISIBLE);
        if (visible) layoutEntry(g);
        else {
            if (touch == ENTRY) touch = GAP;
            row.setPressed(false);
            row.layout(0, 0, 0, 0);
        }
        if (wasVisible != visible) {
            // Make the real Android button and Flutter provider siblings in the tree.
            setImportantForAccessibility(visible ? IMPORTANT_FOR_ACCESSIBILITY_YES
                    : IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (accessibility.isEnabled()) notifySubtreeAccessibilityStateChanged(
                    this, this, AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        }
        invalidate();
    }

    private void layoutEntry(SettingsRowGeometry g) {
        int right = getWidth() - g.right();
        if (row.getPaddingLeft() != g.left() || row.getPaddingRight() != right) {
            row.setPadding(g.left(), row.getPaddingTop(), right, row.getPaddingBottom());
        }
        row.layout(0, g.seam(), getWidth(), g.seam() + g.height());
    }

    private boolean hasEntry(SettingsRowGeometry g) {
        return route && isAttachedToWindow() && getWindowVisibility() == VISIBLE
                && g != null && g.showsRow() && row.isShown()
                && flutter.getHeight() + g.height() == getHeight();
    }

    @Override public void addChildrenForAccessibility(ArrayList<View> children) {
        super.addChildrenForAccessibility(children);
        if (hasEntry(geometry) && !children.contains(row)) children.add(row);
    }

    private boolean hitsEntry(SettingsRowGeometry g, float x, float y) {
        return hasEntry(g) && g.inBand(x, y, getWidth());
    }

    private boolean passesTouchSecurity(MotionEvent event) {
        if (super.onFilterTouchEventForSecurity(event) && flutter.onFilterTouchEventForSecurity(event)
                && row.onFilterTouchEventForSecurity(event)) return true;
        if (!loggedObscured) {
            loggedObscured = true;
            MainHook.log("Flutter settings row: obscured touch rejected");
        }
        return false;
    }

    private boolean prepareFrame() {
        if (!route || getWindowVisibility() != VISIBLE) return true;
        try {
            if (!isHardwareAccelerated() || !semantics.isTextureOnly(flutter)) {
                reset();
                return true;
            }
            Object current = semantics.bridge(flutter);
            if (current != bridge) {
                rejectedResize = false;
                viewport = null;
                bridge = current;
                forced = null;
                dirty = true;
                setGeometry(null);
            }
            if (current == null) {
                setReserve(false);
                return true;
            }
            if (!accessibility.isEnabled() && forced != current) {
                semantics.setEngineSemantics(current, true);
                forced = current;
                dirty = true;
            }
            if (!dirty) return true;
            dirty = false;
            int height = flutter.getHeight();
            int rowHeight = row.getMeasuredHeight();
            if (rowHeight <= 0 || height != getHeight() - (reserve ? rowHeight : 0)) return true;
            FlutterSettingsSemantics.Anchor anchor = semantics.read(current, viewport, flutter.getWidth(), height, rowHeight);
            if (anchor == null) {
                setGeometry(null);
                // If this is the first frame following a resize, wait for a matching semantics update.
                if (reserve && semantics.hasViewportSize(current, flutter.getWidth(), height)) {
                    rejectedResize |= waitingForFirstLayout;
                    viewport = null;
                    setReserve(false);
                } else if (reserve && !waitingForFirstLayout) {
                    removeCallbacks(resizeTimeout);
                    postDelayed(resizeTimeout, 900);
                }
                return true;
            }
            viewport = anchor.viewport();
            if (!reserve) {
                if (!rejectedResize && touch == NONE && anchor.geometry().showsRow()) setReserve(true);
            } else {
                waitingForFirstLayout = false;
                setGeometry(anchor.geometry());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
        }
        return true;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        row.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        int hostHeight = Math.max(0, height - (reserve ? row.getMeasuredHeight() : 0));
        flutter.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(hostHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        flutter.layout(0, 0, flutter.getMeasuredWidth(), flutter.getMeasuredHeight());
        SettingsRowGeometry g = geometry;
        if (g != null && (flutter.getHeight() + g.height() != getHeight() || !reserve)) {
            setGeometry(null);
        } else if (g != null && g.showsRow()) {
            layoutEntry(g);
        }
        dirty = true;
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        SettingsRowGeometry g = geometry;
        if (g == null || !canvas.isHardwareAccelerated() || getHeight() != flutter.getHeight() + g.height()) {
            // Draw only the host during a resize/unsupported surface, never a row on top of it.
            drawChild(canvas, flutter, getDrawingTime());
            return;
        }
        long time = getDrawingTime();
        int save = canvas.save();
        canvas.clipRect(0, 0, getWidth(), g.seam());
        drawChild(canvas, flutter, time);
        canvas.restoreToCount(save);
        save = canvas.save();
        canvas.clipRect(0, g.seam() + g.height(), getWidth(), getHeight());
        canvas.translate(0, g.height());
        drawChild(canvas, flutter, time);
        canvas.restoreToCount(save);
        save = canvas.save();
        canvas.clipRect(0, g.seam(), getWidth(), g.seam() + g.height());
        canvas.drawColor(gutterColor);
        if (g.showsRow()) {
            drawChild(canvas, row, time);
            int line = canvas.save();
            canvas.clipRect(g.left(), g.seam() + g.height() - 1, g.right(), g.seam() + g.height());
            canvas.drawColor(gutterColor);
            canvas.restoreToCount(line);
        }
        canvas.restoreToCount(save);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        SettingsRowGeometry g = geometry;
        if (action == MotionEvent.ACTION_DOWN) {
            downGeometry = g;
            downX = event.getX();
            downY = event.getY();
            moved = false;
            allowUp = true;
            touch = g == null ? ORIGINAL : hitsEntry(g, downX, downY) ? ENTRY : g.inGap(downY) ? GAP : HOST;
            offset = g == null ? 0 : g.offsetAt(downY);
            if (touch == ENTRY) {
                if (passesTouchSecurity(event)) {
                    row.setPressed(true);
                    if (!loggedDown) {
                        loggedDown = true;
                        MainHook.log("Flutter settings row: down");
                    }
                }
                else touch = GAP;
            }
        }
        boolean end = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        if (touch == ORIGINAL || touch == NONE) {
            boolean handled = super.dispatchTouchEvent(event);
            if (end || !handled) {
                touch = NONE;
                semanticsChanged();
            }
            return handled;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN || Math.abs(event.getX() - downX) > slop
                || Math.abs(event.getY() - downY) > slop) moved = true;
        if (touch == ENTRY) {
            if (!hasEntry(g) || !sameEntryBand(g) || !passesTouchSecurity(event)) {
                row.setPressed(false);
                touch = GAP;
            } else if (g.dragFromHeading() && g.inRow(downX, downY) && action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1 && moved
                    && Math.abs(event.getY() - downY) > slop) {
                // A vertical drag started in the inserted row scrolls Flutter from the non-actionable
                // heading side of the split. Never send an UP from this synthetic down as a tap.
                row.setPressed(false);
                offset = (int) downY - g.seam() + 1;
                MotionEvent start = MotionEvent.obtain(event.getDownTime(), event.getDownTime(),
                        MotionEvent.ACTION_DOWN, downX, downY, event.getMetaState());
                start.setSource(event.getSource());
                sendHost(start, offset, MotionEvent.ACTION_DOWN);
                start.recycle();
                touch = HOST;
                allowUp = false;
            } else if (moved) {
                row.setPressed(false);
            } else if (action == MotionEvent.ACTION_UP && hitsEntry(g, event.getX(), event.getY())) {
                row.performClick();
            }
        }
        if (touch == HOST) {
            // Keep one transform for the entire pointer stream (including history and extra fingers).
            // Cancel an uncommitted tap if the visual hit band changed. Never retarget it to another row.
            if (g == null || (!allowUp && event.getPointerCount() != 1)
                    || (allowUp && (!moved || action == MotionEvent.ACTION_UP) && !g.sameHitBand(event.getY(), offset))) {
                sendHost(event, offset, MotionEvent.ACTION_CANCEL);
                touch = GAP;
            } else {
                sendHost(event, offset, !allowUp && action == MotionEvent.ACTION_UP
                        ? MotionEvent.ACTION_CANCEL : event.getAction());
            }
        }
        if (end) {
            row.setPressed(false);
            touch = NONE;
            downGeometry = null;
            semanticsChanged();
        }
        return true;
    }

    private boolean sameEntryBand(SettingsRowGeometry g) {
        return downGeometry != null && g.seam() == downGeometry.seam()
                && g.height() == downGeometry.height();
    }

    private void sendHost(MotionEvent original, int offset, int action) {
        MotionEvent copy = MotionEvent.obtain(original);
        try {
            copy.offsetLocation(0, -offset);
            copy.setAction(action);
            flutter.dispatchTouchEvent(copy);
        } finally {
            copy.recycle();
        }
    }

    @Override protected boolean dispatchHoverEvent(MotionEvent event) {
        SettingsRowGeometry g = geometry;
        if (g == null && hover == NONE) return super.dispatchHoverEvent(event);
        int next = event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT ? NONE
                : g == null ? HOST : hitsEntry(g, event.getX(), event.getY()) ? ENTRY
                : g.inGap(event.getY()) ? GAP : HOST;
        if (next != hover && (hover == HOST || hover == ENTRY)) {
            dispatchPointer(event, g, hover, MotionEvent.ACTION_HOVER_EXIT);
        }
        int action = next != hover ? MotionEvent.ACTION_HOVER_ENTER : event.getAction();
        hover = next;
        return (next == HOST || next == ENTRY) && dispatchPointer(event, g, next, action);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        SettingsRowGeometry g = geometry;
        if (g != null && event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            if (g.inGap(event.getY()) && !g.showsRow()) return false;
            // Wheel/trackpad scroll from the inserted band goes to the list, not the fixed footer.
            return dispatchPointer(event, g, HOST, event.getAction());
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private boolean dispatchPointer(MotionEvent event, SettingsRowGeometry g, int target, int action) {
        MotionEvent copy = MotionEvent.obtain(event);
        try {
            copy.setAction(action);
            if (target == ENTRY) {
                copy.offsetLocation(-row.getLeft(), -row.getTop());
                return row.routeHover(copy);
            }
            if (g != null) copy.offsetLocation(0, g.inGap(copy.getY())
                    ? g.seam() - 1 - copy.getY() : -g.offsetAt(copy.getY()));
            return flutter.dispatchGenericMotionEvent(copy);
        } finally {
            copy.recycle();
        }
    }

    void adjustNode(int id, AccessibilityNodeInfo info) {
        SettingsRowGeometry g = geometry;
        if (g == null || bridge == null) return;
        try {
            semantics.adjust(bridge, viewport, flutter, row, g, id, info);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
        }
    }
}
