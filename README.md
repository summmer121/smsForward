# 📱 移动话费查询 (MobileBalance)

> Xposed/LSPosed 模块 — 监听短信识别话费余额 + 定时发送查余额 + Webhook推送

## ✨ 功能特性

- 📩 **短信监听** — 4路Hook确保抓到所有短信（PDU + ContentResolver + DisplayBody）
- 🔍 **正则识别** — 自定义发件人正则 + 余额提取正则，默认匹配中国移动10086
- 📤 **定时发送** — 每天定时发"查余额"到10086，开机自动恢复
- 🔔 **弹窗通知** — 识别成功/失败/发送结果均弹窗通知
- 🌐 **Webhook推送** — 识别到余额自动POST到指定URL，支持自定义Body模板
- 📋 **完整日志** — 记录原始短信、正则匹配、发送记录，App内查看

## 🔧 技术栈

- 纯Java Android项目（无Gradle）
- aapt2 + javac + d8 命令行编译
- Xposed API (de.robv.android.xposed)
- 支持 Android 5.0 ~ 14 (API 21-34)

## 📦 安装使用

1. 安装APK到已Root手机
2. 打开App → 点击🔑**权限**按钮，授予所有权限
3. LSPosed → 模块 → 启用「移动话费查询」
4. 作用域勾选：**Android系统**、**电话和短信**、**你的短信App**
5. **重启手机**
6. 打开App配置Webhook URL和正则
7. 发送测试短信或等待真实短信

## 📋 日志说明

日志记录5类事件：

| 标签 | 含义 |
|------|------|
| `SMS_PDU` | 从createFromPdu截获的原始短信 |
| `SMS_INSERT` | 从ContentResolver截获的入库短信（最可靠） |
| `MATCH_SENDER` | 发件人正则匹配成功 |
| `MATCH` | 余额正则匹配成功 + 提取结果 |
| `POST/RESP` | Webhook请求和响应 |

## ⏰ 定时发送

- 支持自定义发送号码、内容、时间
- 每天重复或仅一次
- 开机自启恢复定时任务
- 也可手动「立即发送」

## 🔔 弹窗通知

- ✅ 识别成功 → 显示余额 + 匹配内容
- ❌ Webhook发送失败 → 显示错误
- 📩 收到短信但未匹配 → 提示方便调试正则

## 📁 项目结构

```
MobileBalance/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/summer/mobilebalance/
│   │   ├── Config.java            # 配置常量
│   │   ├── MainActivity.java      # 主界面
│   │   ├── LogActivity.java       # 日志查看
│   │   ├── LogStore.java          # 日志存储
│   │   ├── SmsHooker.java         # Xposed Hook核心
│   │   ├── SmsScheduleActivity.java  # 定时发送设置
│   │   ├── SmsAlarmReceiver.java  # 定时任务接收器
│   │   └── BootReceiver.java      # 开机自启
│   └── res/
│       ├── values/arrays.xml      # Xposed作用域
│       └── values/strings.xml
├── libs/xposed-api.jar
├── assets/xposed_init
└── build.sh                       # 一键编译脚本
```

## 🛠 编译

```bash
# 需要JDK17 + Android SDK (platform-34, build-tools-34)
export ANDROID_HOME=/opt/android-sdk
bash build.sh
# 输出: build/MobileBalance.apk
```

## ⚠️ 免责声明

本软件仅供个人学习和研究使用。请遵守当地法律法规，不得用于非法用途。

---

*by 小小飞 ✈️ for 主人summer*
