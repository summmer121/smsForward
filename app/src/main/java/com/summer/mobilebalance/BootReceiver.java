package com.summer.mobilebalance;

import android.content.Context;
import android.content.Intent;

public class BootReceiver extends android.content.BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            LogStore.append("BOOT", "开机启动, 恢复定时任务");
            try {
                @SuppressWarnings("deprecation")
                android.content.SharedPreferences prefs = context.getSharedPreferences(
                    Config.PREF_NAME, Context.MODE_WORLD_READABLE);
                boolean scheduled = prefs.getBoolean(Config.KEY_SCHEDULE_ENABLED, false);
                if (scheduled) {
                    String phone = prefs.getString(Config.KEY_SCHEDULE_PHONE, "10086");
                    String msg = prefs.getString(Config.KEY_SCHEDULE_MESSAGE, "查余额");
                    int hour = Integer.parseInt(prefs.getString(Config.KEY_SCHEDULE_HOUR, "08"));
                    int minute = Integer.parseInt(prefs.getString(Config.KEY_SCHEDULE_MINUTE, "00"));
                    SmsAlarmReceiver.setAlarm(context, phone, msg, hour, minute, true);
                    LogStore.append("BOOT", "✓ 定时任务已恢复: " + hour + ":" + minute);
                }
            } catch (Throwable t) {
                LogStore.append("BOOT", "恢复定时任务失败: " + t.getMessage());
            }
        }
    }
}
