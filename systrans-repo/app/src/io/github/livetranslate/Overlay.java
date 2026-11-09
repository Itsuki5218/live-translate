package io.github.livetranslate;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮歌词：每行字自己带一个底框（荧光笔效果），整块区域完全透明。
 * 多行累积，每行停留一段时间后淡出。
 */
public final class Overlay {

    private static Overlay inst;

    private final Context app;
    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private WindowManager.LayoutParams lp;
    private final List<View> lines = new ArrayList<>();
    private boolean added;

    private int textSize = 18;
    private int bgAlpha = 90;
    private int holdSeconds = 10;
    private int textColor = 0xFF12100D;
    private int highlight = 0xFFF2EDE3;
    private static final int MAX_LINES = 4;

    private Overlay(Context c) {
        app = c.getApplicationContext();
        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized Overlay get(Context c) {
        if (inst == null) inst = new Overlay(c);
        return inst;
    }

    public void configure(int textSizeSp, int bgAlphaPercent, int holdSec,
                          int color, int highlightColor) {
        textSize = textSizeSp;
        bgAlpha = bgAlphaPercent;
        holdSeconds = Math.max(2, holdSec);
        textColor = color;
        highlight = highlightColor;
    }

    void show() {
        ui.post(() -> {
            if (added) return;
            root = new LinearLayout(app);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(6), dp(6), dp(6), dp(6));
            root.setClipChildren(false);
            root.setBackground(null);
            root.setMinimumHeight(dp(12));

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            // 固定窗口宽度：否则所有行都消失后窗口会塌成 0，再加内容也显示不出来
            lp = new WindowManager.LayoutParams(
                    app.getResources().getDisplayMetrics().widthPixels - dp(24),
                    WindowManager.LayoutParams.WRAP_CONTENT, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = dp(12);
            lp.y = dp(110);

            final int[] startY = new int[1];
            final float[] downY = new float[1];
            root.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY[0] = lp.y;
                        downY[0] = ev.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.y = startY[0] + (int) (ev.getRawY() - downY[0]);
                        if (added) {
                            try {
                                wm.updateViewLayout(root, lp);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    default:
                        return false;
                }
            });

            try {
                wm.addView(root, lp);
                added = true;
            } catch (Exception e) {
                added = false;
            }
        });
    }

    void hide() {
        ui.post(() -> {
            if (!added) return;
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
            added = false;
            root = null;
            lines.clear();
        });
    }

    void push(final String source, final String translation) {
        ui.post(() -> {
            if (!added || root == null) {
                show();
                ui.postDelayed(() -> addLine(translation, source, textColor), 150);
                return;
            }
            addLine(translation, source, textColor);
        });
    }

    void pushOriginal(final String text) {
        ui.post(() -> {
            if (!added || root == null) {
                show();
                ui.postDelayed(() -> addLine(text, null, textColor), 150);
                return;
            }
            addLine(text, null, textColor);
        });
    }

    void status(String text) {
        ui.post(() -> addLine(text, null, 0xFFB8BFC9));
    }

    private void addLine(String main, String sub, int color) {
        if (!added || root == null) return;
        if (main == null || main.trim().isEmpty()) return;

        LinearLayout item = new LinearLayout(app);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(10), dp(3), dp(10), dp(5));

        // 荧光笔底框：只包裹这一行文字
        if (bgAlpha > 0) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(withAlpha(highlight, Math.min(255, (int) (bgAlpha * 2.55f))));
            g.setStroke(dp(1), 0xFF12100D);
            g.setCornerRadius(0);
            item.setBackground(g);
        }

        if (sub != null && !sub.trim().isEmpty() && !sub.equals(main)) {
            TextView s = new TextView(app);
            s.setText(sub);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(11, textSize - 5));
            s.setTextColor(withAlpha(color, 170));
            s.setGravity(Gravity.CENTER);
            s.setMaxLines(2);
            s.setEllipsize(TextUtils.TruncateAt.END);
            s.setShadowLayer(dp(5), 0, dp(1), 0x99000000);
            item.addView(s);
        }

        TextView t = new TextView(app);
        t.setText(main);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(dp(12), 0, dp(2), 0xCC000000);
        t.setMaxLines(3);
        t.setEllipsize(TextUtils.TruncateAt.END);

        int maxW = app.getResources().getDisplayMetrics().widthPixels - dp(64);
        t.setMaxWidth(maxW);
        item.addView(t);

        LinearLayout.LayoutParams lpItem = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpItem.topMargin = dp(4);
        item.setLayoutParams(lpItem);

        item.setAlpha(0f);
        item.setTranslationY(dp(10));
        root.addView(item);
        lines.add(item);

        // 强制让窗口重新测量一次，避免残留 0 尺寸
        if (added && root != null) {
            try {
                wm.updateViewLayout(root, lp);
            } catch (Exception ignored) {
            }
        }

        item.animate().alpha(1f).translationY(0).setDuration(220).start();

        while (lines.size() > MAX_LINES) {
            removeLine(lines.get(0), 0);
        }

        final long life = holdSeconds * 1000L;
        item.postDelayed(() -> removeLine(item, 320), life);
    }

    private void removeLine(View item, long duration) {
        if (item == null || root == null) return;
        lines.remove(item);
        if (duration <= 0) {
            try {
                root.removeView(item);
            } catch (Exception ignored) {
            }
            return;
        }
        item.animate().alpha(0f).translationY(-dp(6)).setDuration(duration)
                .withEndAction(() -> {
                    try {
                        root.removeView(item);
                    } catch (Exception ignored) {
                    }
                }).start();
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private int dp(float v) {
        return (int) (v * app.getResources().getDisplayMetrics().density + 0.5f);
    }
}
