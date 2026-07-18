# Netconnect 发布工作流（v1.0.7 → v1.0.10）

> 喂 AI 用：本文档记录了此项目的完整发布流程，包括构建命令、git 操作、GitHub Release 发布等。

---

## 1. 项目概况

- **项目名称**：Net-USTB（Netconnect_tool）
- **用途**：北京科技大学 Dr.COM ePortal 4.x 校园网第三方 Android 客户端
- **语言/框架**：Kotlin + Jetpack Compose + Material 3
- **最低 SDK**：24 (Android 7.0)
- **GitHub**：`coderirse/Netconnect_tool`

---

## 2. 环境配置

### 构建环境

- **JDK**：Android Studio 自带的 JBR，路径 `/d/Android-studio_/jbr`
- **Gradle**：项目自带的 `gradlew.bat`（Windows）
- **Android SDK**：`~/AppData/Local/Android/Sdk/platform-tools/`

### 代理配置

Git push 和 GitHub Release 都需要走代理：

```bash
# git
git -c http.proxy=http://127.0.0.1:7897 -c https.proxy=http://127.0.0.1:7897 push

# gh CLI
HTTPS_PROXY=http://127.0.0.1:7897 HTTP_PROXY=http://127.0.0.1:7897 gh release create ...
```

---

## 3. 版本号规则

| 文件 | 字段 | 说明 |
|------|------|------|
| `app/build.gradle.kts` | `versionCode` | 整数，每次 +1 |
| `app/build.gradle.kts` | `versionName` | 字符串，如 `"1.0.10"` |

| 版本 | versionCode | 版本 | versionCode |
|------|------------|------|------------|
| v1.0.7 | 8 | v1.0.9 | 10 |
| v1.0.8 | 9 | v1.0.10 | 11 |

---

## 4. 完整发布流程

### 4.1 改版本号

编辑 `app/build.gradle.kts`：

```kotlin
versionCode = 11      // +1
versionName = "1.0.10" // 对应版本名
```

### 4.2 构建 APK

```bash
cd "d:/Android-project/Netconnect_tool"
JAVA_HOME="/d/Android-studio_/jbr" ./gradlew assembleDebug --no-daemon
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

### 4.3 Git 提交

```bash
git add <改动的文件...>
# 例如：
# git add app/build.gradle.kts app/src/main/java/com/example/netconnect_tool/data/DashboardParser.kt

git commit -m "修复描述 (v1.0.x)"
```

提交信息格式：简短描述 + 版本号标签。

### 4.4 推送

```bash
git -c http.proxy=http://127.0.0.1:7897 \
    -c https.proxy=http://127.0.0.1:7897 \
    -c http.postBuffer=524288000 \
    push origin main
```

### 4.5 创建 GitHub Release

```bash
# 复制 APK 为带版本号的文件名
cp app/build/outputs/apk/debug/app-debug.apk Net-USTB-v1.0.x-debug.apk

# 创建 release
HTTPS_PROXY=http://127.0.0.1:7897 \
HTTP_PROXY=http://127.0.0.1:7897 \
gh release create v1.0.x \
  Net-USTB-v1.0.x-debug.apk \
  --title "v1.0.x — 简短标题" \
  --notes "详细 release notes..."

# 清理
rm Net-USTB-v1.0.x-debug.apk
```

---

## 5. 代码架构速查

```
app/src/main/java/com/example/netconnect_tool/
├── MainActivity.kt               # NavHost 入口，创建共享 CampusNetworkClient
├── data/
│   ├── CampusNetworkClient.kt    # OkHttp：登录/注销/dashboard 拉取
│   ├── DashboardParser.kt        # HTML + JS 变量 → Dashboard 数据模型
│   ├── CredentialStore.kt        # EncryptedSharedPreferences 凭据存储
│   ├── CachedDashboard.kt        # 内存缓存（AtomicReference）
│   ├── UpdateChecker.kt          # GitHub API 检查更新
│   └── model/
│       ├── Dashboard.kt          # 数据模型
│       ├── Carrier.kt            # 运营商枚举（@dx/@lt）
│       └── BulletinItem.kt
└── ui/
    ├── LoginScreen.kt
    ├── LoginViewModel.kt
    ├── DashboardScreen.kt
    └── DashboardViewModel.kt
```

### 关键设计点

- `MainActivity` 创建**共享的** `CampusNetworkClient` 实例，传给 LoginViewModel 和 DashboardViewModel。不能各自 new，否则 cookie 不一致导致注销失效。
- `CampusNetworkClient` 内部用一个 `InMemoryCookieJar` 维护 session。
- `CachedDashboard` 用 `AtomicReference` 在登录成功和 Dashboard 页面间传递数据，避免重复网络请求。

---

## 6. 调试方法

### 6.1 ADB 日志

```bash
# 确认设备连接
~/AppData/Local/Android/Sdk/platform-tools/adb devices

# 清旧日志 + 抓 DashboardParser 和 Netconnect 标签
~/AppData/Local/Android/Sdk/platform-tools/adb logcat -c
~/AppData/Local/Android/Sdk/platform-tools/adb logcat -s DashboardParser Netconnect
```

手机需要开启 **开发者选项 → USB 调试**。

### 6.2 日志标签

| TAG | 用途 |
|-----|------|
| `DashboardParser` | HTML 解析、JS 变量提取、各级回退诊断 |
| `Netconnect` | 网络请求（登录/注销/dashboard API） |

---

## 7. DashboardParser 的 V6 流量解析

### 回退链路（优先级从高到低）

| 级别 | 方法 | 说明 |
|------|------|------|
| 1a | Jsoup `#user_useflowV6 p` | HTML 元素（最优先） |
| 1b | Jsoup `#user_useflowV6` | 整体 div 文本 |
| 2 | 正则 `id="user_useflowV6"...<p>` | HTML 字符串正则 |
| 3 | "流量(V6)" 标签 | 搜索标签后最近的数值 |
| 4a | **`v6df / 4` → KB** | ✅ 当前 USTB 部署匹配 |
| 4b | `v6af / 4` → KB | 回退 |
| 4c | `v6df` 直读 KB | 向后兼容 |
| 4d | `v6af` 直读 KB | 向后兼容 |
| 5 | 其他变量名 | flowV6, v6flow 等 |

### USTB 具体发现

- HTML 中**完全没有** `#user_useflowV6` 元素，页面完全由 JS 变量渲染
- `v6df=27901752` 是 IPv6 下行流量，单位是 **256 字节/tick**（除以 4 = KB）
- `v6af=29649892` 是 IPv6 总流量（上下行合计），同单位
- `v6df / 4 / 1024 = 6811.95 MB`，与网站显示完全吻合
- V4 流量来自 `flow` 变量，单位是 **KB**（不需要除 4）

---

## 8. ePortal JS 变量速查

登录后 dashboard 页面的关键 JS 变量（USTB 实测）：

| 变量 | 示例值 | 含义 | 单位 |
|------|--------|------|------|
| `uid` | `U2024xxxxxx` | 学号/账号 | — |
| `flow` | `78131959` | IPv4 流量 | **KB** |
| `v6df` | `27901752` | IPv6 下行流量 | **÷4 = KB** |
| `v6af` | `29649892` | IPv6 总流量 | **÷4 = KB** |
| `fee` | `179300` | 余额（÷10000=元） | 分 |
| `time` | `10542` | 已用时长 | 分钟 |
| `stime` | `2026-07-11 15:20:10` | 登录时间 | — |
| `v4ip` | `10.38.32.110` | IPv4 地址 | — |
| `v6ip` | `::` | IPv6 地址 | — |
| `olflow` | `78333952` | 累计流量（?） | — |
| `oltime` | `4294967295` | 时长上限（=无限制） | — |
| `pwd` | `''` | 密码（已置空） | — |

---

## 9. 已知问题和修复历史

| 版本 | 问题 | 修复 |
|------|------|------|
| v1.0.7 | 注销失效 | 共享 `CampusNetworkClient` 实例，修复 cookie 丢失 |
| v1.0.7 | 注销验证虚假通过 | 改为外网连通性测试（访问 baidu 看是否被重定向） |
| v1.0.7 | 登录失败 | 登录前清理旧 cookie + carrier suffix 格式 + 增加 IP 兜底 |
| v1.0.8 | V6 流量诊断 | 6 级回退逐级日志 |
| v1.0.9 | V6 根因定位 | 全部 69 个 JS 变量 dump + 流量数值扫描 |
| v1.0.10 | V6 流量错误 | 改用 `v6df/4` 代替 `v6af×1` |

---

## 10. 提交信息参考

```
Shared CampusNetworkClient + logout verify via internet test + login enhancements (v1.0.7)
V6 traffic diagnostic logging: track every fallback level (v1.0.8)
Full JS variable dump + all traffic values for V6 root-cause (v1.0.9)
Fix V6 traffic: use v6df/4 instead of v6af (v1.0.10)
```

格式：`简短描述 (v1.0.x)`，必要时正文另起一段。
