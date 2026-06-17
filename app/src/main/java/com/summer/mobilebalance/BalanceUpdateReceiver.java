package com.summer.mobilebalance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 接收 Xposed 进程发来的事件，由 App 自己进程落盘。
 *
 * 这是绕过 SELinux 限制的最佳方案：
 *  - Xposed hook 跑在被hook的系统进程里(com.android.mms / com.android.providers.telephony等)，
 *    SELinux 不允许它们写 /data/local/tmp/ 或我们 App 的私有目录。
 *  - 改用 sendBroadcast() 通过 Binder IPC 把数据发到 App 进程，
 *    然后由 App 进程(有自己 data 目录的写权限)落盘。
 */
public class BalanceUpdateReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.summer.mobilebalance.UPDATE";

    public static final String EXTRA_TYPE = "type";          // "log" | "history" | "balance"
    public static final String EXTRA_LOG_TYPE = "log_type";
    public static final String EXTRA_LOG_MSG = "log_msg";
    public static final String EXTRA_SENDER = "sender";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_MATCHED = "matched";
    public static final String EXTRA_BALANCE = "balance";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_TIME = "time";
    public static final String EXTRA_TS = "ts";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String type = intent.getStringExtra(EXTRA_TYPE);
        if (type == null) return;

        try {
            // 确保存储目录已初始化
            LogStore.initLogDirs(context);
            SmsHistoryStore.initDirs(context);

            if ("log".equals(type)) {
                String t = intent.getStringExtra(EXTRA_LOG_TYPE);
                String m = intent.getStringExtra(EXTRA_LOG_MSG);
                if (t == null) t = "INFO";
                if (m == null) m = "";
                LogStore.append(t, m);
            } else if ("history".equals(type)) {
                String sender = intent.getStringExtra(EXTRA_SENDER);
                String body = intent.getStringExtra(EXTRA_BODY);
                boolean matched = intent.getBooleanExtra(EXTRA_MATCHED, false);
                String balance = intent.getStringExtra(EXTRA_BALANCE);
                String source = intent.getStringExtra(EXTRA_SOURCE);
                SmsHistoryStore.addRecord(sender, body, matched, balance, source);
            } else if ("balance".equals(type)) {
                String balance = intent.getStringExtra(EXTRA_BALANCE);
                String time = intent.getStringExtra(EXTRA_TIME);
                long ts = intent.getLongExtra(EXTRA_TS, System.currentTimeMillis());
                String sender = intent.getStringExtra(EXTRA_SENDER);
                String body = intent.getStringExtra(EXTRA_BODY);

                // 写到 App 私有 prefs (App进程有完整权限)
                SharedPreferences sp = context.getSharedPreferences(Config.PREF_NAME, Context.MODE_PRIVATE);
                sp.edit()
                  .putString(Config.KEY_LAST_BALANCE, balance == null ? "" : balance)
                  .putString(Config.KEY_LAST_BALANCE_TIME, time == null ? "" : time)
                  .putLong(Config.KEY_LAST_BALANCE_TS, ts)
                  .putString(Config.KEY_LAST_BALANCE_SENDER, sender == null ? "" : sender)
                  .putString(Config.KEY_LAST_BALANCE_BODY, body == null ? "" : body)
                  .apply();
            }
        } catch (Throwable t) {
            Log.e("BalanceUpdRecv", "onReceive failed", t);
        }
    }
}
