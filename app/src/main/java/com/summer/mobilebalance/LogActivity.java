package com.summer.mobilebalance;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 日志查看页。
 * 显示原短信内容、正则识别结果、POST 记录等。
 */
public class LogActivity extends Activity {

    private TextView tvLog;
    private ScrollView scroll;
    private Handler handler = new Handler();
    private boolean autoRefresh = true;
    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (!autoRefresh) return;
            loadLog();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0f1419"));

        // 标题栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(20, 20, 20, 20);
        bar.setBackgroundColor(Color.parseColor("#1a2332"));

        TextView title = new TextView(this);
        title.setText("📋 运行日志");
        title.setTextColor(Color.parseColor("#00d4ff"));
        title.setTextSize(16);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(title);

        Button btnRefresh = makeBtn("🔄 刷新");
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadLog(); }
        });
        bar.addView(btnRefresh);

        Button btnCopy = makeBtn("📋 复制");
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("log", tvLog.getText()));
                Toast.makeText(LogActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(btnCopy);

        Button btnClear = makeBtn("🗑 清空");
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (LogStore.clear()) {
                    Toast.makeText(LogActivity.this, "已清空", Toast.LENGTH_SHORT).show();
                    loadLog();
                } else {
                    Toast.makeText(LogActivity.this, "清空失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        bar.addView(btnClear);

        root.addView(bar);

        // 路径提示
        TextView pathHint = new TextView(this);
        pathHint.setText("文件: " + LogStore.resolveFile() + "  (每2秒自动刷新)");
        pathHint.setTextColor(Color.parseColor("#666666"));
        pathHint.setTextSize(10);
        pathHint.setPadding(20, 10, 20, 10);
        root.addView(pathHint);

        // 日志内容
        scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tvLog = new TextView(this);
        tvLog.setTextColor(Color.parseColor("#a0e0ff"));
        tvLog.setTextSize(11);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setPadding(20, 20, 20, 20);
        tvLog.setTextIsSelectable(true);
        scroll.addView(tvLog);
        root.addView(scroll);

        setContentView(root);
        loadLog();
    }

    private Button makeBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setTextColor(Color.parseColor("#0f1419"));
        b.setBackgroundColor(Color.parseColor("#00d4ff"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = 10;
        b.setLayoutParams(lp);
        b.setPadding(20, 10, 20, 10);
        return b;
    }

    private void loadLog() {
        String content = LogStore.readAll();
        tvLog.setText(content);
        // 滚到底部
        scroll.post(new Runnable() {
            @Override public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        autoRefresh = true;
        handler.postDelayed(refreshTask, 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoRefresh = false;
        handler.removeCallbacks(refreshTask);
    }
}
