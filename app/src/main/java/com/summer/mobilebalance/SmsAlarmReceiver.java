package com.summer.mobilebalance;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class SmsAlarmReceiver extends android.content.BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.summer.mobilebalance.SEND_SMS_ALARM".equals(intent.getAction())) {
            String phone = intent.getStringExtra("sms_phone");
            String msg = intent.getStringExtra("sms_message");
            if (phone == null) phone = "10086";
            if (msg == null) msg = "查余额";

            LogStore.append("ALARM", "定时任务触发: 发送短信到 " + phone + " 内容=" + msg);

            try {
                android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
                smsManager.sendTextMessage(phone, null, msg, null, null);
                LogStore.append("SMS_SEND", "✓ 短信已发送: " + phone + " -> " + msg);
            } catch (Throwable t) {
                LogStore.append("SMS_SEND", "✗ 短信发送失败: " + t.getMessage());
            }

            // 如果是重复任务，重新设置下一天
            boolean repeat = intent.getBooleanExtra("sms_repeat", false);
            if (repeat) {
                int hour = intent.getIntExtra("sms_hour", 8);
                int minute = intent.getIntExtra("sms_minute", 0);
                setAlarm(context, phone, msg, hour, minute, true);
            }
        }
    }

    public static void setAlarm(Context context, String phone, String msg, int hour, int minute, boolean repeat) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, SmsAlarmReceiver.class);
        i.setAction("com.summer.mobilebalance.SEND_SMS_ALARM");
        i.putExtra("sms_phone", phone);
        i.putExtra("sms_message", msg);
        i.putExtra("sms_repeat", repeat);
        i.putExtra("sms_hour", hour);
        i.putExtra("sms_minute", minute);

        int requestCode = 9991;
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
        cal.set(java.util.Calendar.MINUTE, minute);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        // 如果时间已过，推到明天
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else if (Build.VERSION.SDK_INT >= 19) {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }

        LogStore.append("ALARM", "定时任务已设置: " + hour + ":" + String.format("%02d", minute)
            + " 发送到 " + phone + " 内容=" + msg + " 重复=" + repeat);
    }

    public static void cancelAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, SmsAlarmReceiver.class);
        i.setAction("com.summer.mobilebalance.SEND_SMS_ALARM");
        PendingIntent pi = PendingIntent.getBroadcast(context, 9991, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        LogStore.append("ALARM", "定时任务已取消");
    }
}
