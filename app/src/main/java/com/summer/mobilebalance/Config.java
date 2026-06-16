package com.summer.mobilebalance;

public class Config {
    public static final String PREF_NAME = "mobile_balance_config";

    // Webhook
    public static final String KEY_WEBHOOK_URL = "webhook_url";
    public static final String DEFAULT_WEBHOOK_URL = "http://YOUR_SERVER:8123/api/webhook/YOUR_WEBHOOK_ID";
    public static final String KEY_WEBHOOK_METHOD = "webhook_method";
    public static final String DEFAULT_WEBHOOK_METHOD = "POST";
    public static final String KEY_WEBHOOK_BODY = "webhook_body";
    public static final String DEFAULT_WEBHOOK_BODY = "message=余额为{balance}元";
    public static final String KEY_WEBHOOK_HEADERS = "webhook_headers";
    public static final String DEFAULT_WEBHOOK_HEADERS =
        "Content-Type:application/x-www-form-urlencoded; charset=UTF-8";

    // 正则
    public static final String KEY_SENDER_REGEX = "sender_regex";
    public static final String DEFAULT_SENDER_REGEX = "^(10086|10086\\d*|\\+?8610086.*)$";
    public static final String KEY_BALANCE_REGEX = "balance_regex";
    public static final String DEFAULT_BALANCE_REGEX =
        "(?:话费?余额|可用余额|账户余额|当前余额|余额)[为是:：\\s]*(-?\\d+(?:\\.\\d+)?)\\s*元?";

    // 开关
    public static final String KEY_ENABLED = "enabled";
    public static final boolean DEFAULT_ENABLED = true;
    public static final String KEY_LOG_ENABLED = "log_enabled";
    public static final boolean DEFAULT_LOG_ENABLED = true;
    public static final String KEY_POPUP_ENABLED = "popup_enabled";
    public static final boolean DEFAULT_POPUP_ENABLED = true;

    // 发送短信 (立即发送 + 定时)
    public static final String KEY_SMS_NUMBER = "sms_number";
    public static final String DEFAULT_SMS_NUMBER = "10086";
    public static final String KEY_SMS_CONTENT = "sms_content";
    public static final String DEFAULT_SMS_CONTENT = "查余额";
    public static final String KEY_SMS_SCHEDULED = "sms_scheduled";
    public static final boolean DEFAULT_SMS_SCHEDULED = false;
    public static final String KEY_SMS_PHONE = "sms_phone";
    public static final String DEFAULT_SMS_PHONE = "10086";
    public static final String KEY_SMS_MESSAGE = "sms_message";
    public static final String DEFAULT_SMS_MESSAGE = "查余额";

    // 定时发送
    public static final String KEY_SMS_ENABLED = "sms_enabled";
    public static final boolean DEFAULT_SMS_ENABLED = false;
    public static final String KEY_SMS_TARGET = "sms_target";
    public static final String DEFAULT_SMS_TARGET = "10086";
    public static final String KEY_SMS_HOUR = "sms_hour";
    public static final int DEFAULT_SMS_HOUR = 9;
    public static final String KEY_SMS_MINUTE = "sms_minute";
    public static final int DEFAULT_SMS_MINUTE = 0;

    // Schedule页专用
    public static final String KEY_SCHEDULE_ENABLED = "schedule_enabled";
    public static final boolean DEFAULT_SCHEDULE_ENABLED = false;
    public static final String KEY_SCHEDULE_PHONE = "schedule_phone";
    public static final String DEFAULT_SCHEDULE_PHONE = "10086";
    public static final String KEY_SCHEDULE_MESSAGE = "schedule_message";
    public static final String DEFAULT_SCHEDULE_MESSAGE = "查余额";
    public static final String KEY_SCHEDULE_HOUR = "schedule_hour";
    public static final String DEFAULT_SCHEDULE_HOUR = "08";
    public static final String KEY_SCHEDULE_MINUTE = "schedule_minute";
    public static final String DEFAULT_SCHEDULE_MINUTE = "00";
    public static final String KEY_SCHEDULE_REPEAT = "schedule_repeat";
    public static final boolean DEFAULT_SCHEDULE_REPEAT = true;

    // 日志文件
    public static final String LOG_FILE = "/data/local/tmp/mobile_balance.log";
    public static final String LOG_FILE_FALLBACK = "/sdcard/MobileBalance/sms.log";
    public static final String LOG_FILE_FALLBACK2 = "/sdcard/Android/data/com.summer.mobilebalance/files/mobile_balance.log";
    public static final int MAX_LOG_FILE_BYTES = 512 * 1024;
}
