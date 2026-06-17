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
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 日志查看页。显示原短信内容、正则识别结果、POST 记录等。
 * v1.5 改进:
 *  - 仅在内容变化时刷新，避免每2秒"重置滚动位置后再滚到底部"的循环抖动。
 *  - 默认锚定在最新行(底部)，新增日志后自动跟随；用户上滑后停留在用户位置，不强制下拉。
 *  - 切回页面 / 进入页面时立刻定位到底部最新行。
 */
public class LogActivity extends Activity {

    private TextView tvLog;
    private ScrollView scroll;
    private Handler handler = new Handler();
    private boolean autoRefresh = true;

    /** 上一次显示的内容,用于比较是否需要刷新. */
    private String lastContent = "";
    /** 用户是否已经主动滑动到非底部(此时不强制吸附). */
    private boolean userScrolledUp = false;
    /** 距离底部多少px以内仍视为"在底部",会自动跟随. */
    private static final int STICK_BOTTOM_THRESHOLD = 80;

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
            @Override public void onClick(View v) {
                // 手动刷新强制定位到底部最新行
                userScrolledUp = false;
                lastContent = "";  // 强制重新setText
                loadLog();
            }
        });
        bar.addView(btnRefresh);

        Button btnBottom = makeBtn("⬇ 最新");
        btnBottom.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                userScrolledUp = false;
                scrollToBottom();
            }
        });
        bar.addView(btnBottom);

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
                    lastContent = "";
                    userScrolledUp = false;
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
        pathHint.setText("文件: " + LogStore.resolveFile() + "  (默认显示最新, 上滑可查看历史)");
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

        // 监听滚动: 用户上滑离开底部后,不再强制吸附;再次回到底部自动跟随
        scroll.getViewTreeObserver().addOnScrollChangedListener(
            new ViewTreeObserver.OnScrollChangedListener() {
                @Override
                public void onScrollChanged() {
                    int diff = (tvLog.getBottom() - (scroll.getHeight() + scroll.getScrollY()));
                    userScrolledUp = diff > STICK_BOTTOM_THRESHOLD;
                }
            }
        );

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

    /**
     * 加载日志.
     * 关键改进: 仅当内容真正变化时才 setText, 避免重新setText导致ScrollView滚动位置被重置
     * (ScrollView.setText会让textview重测,继而触发滚动到顶,然后再fullScroll到底,出现"上下抖动").
     */
    private void loadLog() {
        String content = LogStore.readAll();
        if (content == null) content = "";

        if (content.equals(lastContent)) {
            // 内容没变化,什么都不做
            return;
        }
        lastContent = content;

        tvLog.setText(content);
        // 只有"用户没有主动上滑去看历史"时才自动跟到底部
        if (!userScrolledUp) {
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
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
        // 每次回到页面: 清空缓存内容强制刷新 + 定位到底部最新行
        lastContent = "";
        userScrolledUp = false;
        loadLog();
        handler.postDelayed(refreshTask, 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoRefresh = false;
        handler.removeCallbacks(refreshTask);
    }
}
