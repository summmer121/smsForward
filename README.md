# 📱 homeassistant移动话费查询 (MobileBalance)

> Xposed/LSPosed 模块 — 监听 10086 短信识别话费余额 + 定时查询 + Webhook 推送 + 历史记录

## ✨ 功能特性

- 📩 **短信监听** — 4 路 Hook 兜底（PDU + ContentResolver + DisplayBody + Telephony Provider）
- 🔍 **正则识别** — 自定义发件人正则 + 余额提取正则，默认适配中国移动 10086
- 🔁 **跨进程通信** — Xposed 进程通过 Broadcast 把事件转发给 App 进程落盘，绕开 SELinux 写文件限制
- 🧹 **5 秒短信去重** — 同 sender+body 5 秒窗口内只处理一次，干掉多 hook 点 + 长短信 PDU 分片造成的重复
- 💰 **余额卡片** — 主界面显示「上次成功获取余额」+ 时间 + 多久前
- 📨 **历史短信** — 主界面 📨 历史 按钮查看最近 100 条监听到的短信（含未匹配的）
- ⏰ **定时发送** — 每天定时发「查余额」到 10086，开机自动恢复
- 🌐 **Webhook 推送** — 识别到余额自动 POST 到指定 URL，支持自定义 Body 模板
- 🔔 **弹窗通知** — 识别成功 / 失败 / 发送结果均弹窗
- 📋 **完整日志** — 原始短信、正则匹配、发送记录、Webhook 响应一网打尽，默认显示最新行

## 🔧 技术栈

- 纯 Java Android 项目（无 Gradle）
- aapt2 + javac + d8 命令行编译
- Xposed API (`de.robv.android.xposed`)
- 支持 Android 5.0 ~ 14 (API 21-34)

## 📦 安装使用

1. 安装 APK 到已 Root 手机
2. 打开 App → 点击 🔑 **权限** 按钮，授予所有权限
3. LSPosed → 模块 → 启用「移动话费查询」
4. 作用域勾选：
   - `com.android.mms`（短信 App）
   - `com.android.providers.telephony`（短信存储 Provider）
   - 你的第三方短信 App（如有）
5. **重启手机**
6. 打开 App 配置 Webhook URL 和正则（默认 webhook 是占位符 `http://YOUR_SERVER:8123/api/webhook/YOUR_WEBHOOK_ID`，必须自己改）
7. 发送测试短信或等待真实短信

## 📋 日志说明

| 标签 | 含义 |
|------|------|
| `APP` | App 启动日志 |
| `INIT` | Xposed Hook 初始化进度 |
| `SMS_RAW` | 监听到的原始短信（发件人 / 来源 / 内容） |
| `SMS_PDU` | createFromPdu 截获 |
| `SMS_INSERT` | ContentResolver 截获（最可靠） |
| `SMS_DISPLAY` | getDisplayMessageBody 截获 |
| `MATCH` | 余额正则匹配成功 + 提取结果 |
| `SAVE` | 保存最新余额到主界面 |
| `POST/RESP` | Webhook 请求和响应 |
| `SUMMARY` | 📊 每条短信处理结果总结 |
| `SKIP` | 发件人不匹配 / 正则未命中 |
| `DEDUP` | 5 秒窗口内重复短信跳过 |

## 🏗 架构亮点：跨进程 Broadcast

LSPosed 模块运行在系统进程（`com.android.mms` / `com.android.providers.telephony`），SELinux 策略禁止它们写任意路径。本项目方案：

```
[Xposed Hook] (sys process)              [App] (own process)
       │                                       │
       │  收到短信 → processSmsContent          │
       │  ├─ 提取余额                           │
       │  └─ sendBroadcast(UPDATE)  ───────────►│  BalanceUpdateReceiver
       │                                       │  ├─ 写日志
       │                                       │  ├─ 写历史
       │                                       │  └─ 写余额到 prefs
```

App 进程用自己的权限落盘到私有目录，彻底绕开 SELinux 限制。

## 📁 项目结构

```
MobileBalance/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/summer/mobilebalance/
│   │   ├── Config.java                 # 配置常量
│   │   ├── MainActivity.java           # 主界面（含余额卡片）
│   │   ├── LogActivity.java            # 日志查看
│   │   ├── LogStore.java               # 日志存储
│   │   ├── SmsHistoryActivity.java     # 历史短信查看
│   │   ├── SmsHistoryStore.java        # 历史 JSON 持久化
│   │   ├── BalanceUpdateReceiver.java  # 跨进程事件接收器
│   │   ├── SmsHooker.java              # Xposed Hook 核心
│   │   ├── SmsScheduleActivity.java    # 定时发送设置
│   │   ├── SmsAlarmReceiver.java       # 定时任务接收器
│   │   └── BootReceiver.java           # 开机自启
│   └── res/
│       ├── values/arrays.xml           # Xposed 作用域提示
│       └── values/strings.xml
├── libs/xposed-api.jar
├── assets/xposed_init
├── build.sh                            # 一键编译脚本
└── .gitignore
```

## 🛠 编译

```bash
# 需要 JDK 17 + Android SDK (platform-34, build-tools-34)
export ANDROID_HOME=/opt/android-sdk
bash build.sh
# 输出: build/MobileBalance.apk
```

## 📜 版本历史

- **v1.8** — 5 秒短信去重 / App 启动日志只写一次
- **v1.7** — 跨进程 Broadcast 修复 SELinux 写文件被拦
- **v1.6** — 余额卡片 + 历史短信按钮
- **v1.5** — 历史短信存储
- **v1.4** — 初版发布

## ⚠️ 免责声明

本软件仅供个人学习和研究使用。请遵守当地法律法规，不得用于非法用途。
默认 Webhook 地址为占位符，使用者需自行配置自己的服务地址，作者不收集、不存储任何用户数据。

---

*by 小小飞 ✈️ for 主人summer*
