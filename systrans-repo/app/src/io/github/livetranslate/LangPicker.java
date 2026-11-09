package io.github.livetranslate;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 常用语言下拉栏，同样做成玻璃。 */
final class LangPicker {

    private static final String[][] SOURCE = {
            {"自动", ""}, {"英语", "en"}, {"日语", "ja"}, {"韩语", "ko"}, {"中文", "zh"},
            {"粤语", "yue"}, {"俄语", "ru"}, {"法语", "fr"}, {"德语", "de"},
            {"西班牙语", "es"}, {"葡萄牙语", "pt"}, {"意大利语", "it"},
            {"泰语", "th"}, {"越南语", "vi"}, {"阿拉伯语", "ar"},
    };

    private static final String[] TARGET = {
            "简体中文", "繁體中文", "English", "日本語", "한국어",
            "Русский", "Français", "Deutsch", "Español", "ไทย", "Tiếng Việt",
    };

    interface Pick {
        void onPick(String value);
    }

    static void show(Activity a, View anchor, boolean source, String current, Pick cb) {
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        if (source) {
            for (String[] s : SOURCE) {
                labels.add(s[0]);
                values.add(s[1]);
            }
        } else {
            for (String s : TARGET) {
                labels.add(s);
                values.add(s);
            }
        }

        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (8 * d), (int) (10 * d), (int) (8 * d), (int) (10 * d));

        box.setBackgroundResource(R.drawable.bg_card);

        PopupWindow pw = new PopupWindow(box, (int) (188 * d),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        if (Build.VERSION.SDK_INT >= 21) pw.setElevation(18 * d);

        for (int i = 0; i < labels.size(); i++) {
            final String value = values.get(i);
            TextView row = new TextView(a);
            row.setText(labels.get(i));
            row.setTextSize(14);
            row.setTextColor(value.equals(current) ? 0xFF2B31E8 : 0xFF12100D);
            row.setPadding((int) (16 * d), (int) (12 * d), (int) (16 * d), (int) (12 * d));
            row.setOnClickListener(v -> {
                cb.onPick(value);
                pw.dismiss();
            });
            box.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        pw.showAsDropDown(anchor, 0, (int) (8 * d), Gravity.START);
    }
}
