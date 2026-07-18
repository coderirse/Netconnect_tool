# Net-USTB

一个用于连接 USTB（北京科技大学）校园网的第三方 Android 原生 App。

校园网认证页面（`http://202.204.48.66`）使用的是 Dr.COM ePortal 4.x（哆点）系统，网页端体验较差：广告多、跳转乱、用量信息分散。本项目把网页上的关键信息搬到原生 App 里，一键登录、随时查看用量。

> 本项目为个人学习与日常使用而做，与学校官方无关。

## 功能特性

- **一键登录 / 注销** — 通过 ePortal JSONP 接口直接完成认证，无需打开浏览器
- **多运营商支持** — 默认 / 电信 / 联通（移动目前校园网未单独区分）
- **用量信息展示**
  - 认证账号
  - 当前余额
  - 已用时长
  - IPv4 / IPv6 已用流量
  - 登录时间、IPv4、IPv6 地址
- **校园看板** — 拉取校园公告条目
- **凭据加密存储** — 使用 `EncryptedSharedPreferences` 保存账号密码，下次免输入
- **会话失效自动跳转** — 检测到未登录状态时自动回到登录页
- **Material 3 UI** — 简洁卡片式布局

## 截图

> 后续补充

## 技术栈

| 领域 | 选型 |
| --- | --- |
| UI | Jetpack Compose + Material 3 |
| 架构 | 单 Activity + ViewModel + Navigation Compose |
| 网络 | OkHttp 4.12 + 自定义 CookieJar（共享实例） |
| HTML 解析 | Jsoup 1.18 + 正则兜底 + JS 变量提取 |
| 凭据存储 | androidx.security.crypto（EncryptedSharedPreferences） |
| 异步 | Kotlin Coroutines + Flow |
| 更新检查 | GitHub Releases API |
| 最低 SDK | 24 (Android 7.0) |
| 目标 SDK | 35 (Android 15) |

## 项目结构

```
app/src/main/java/com/example/netconnect_tool/
├── MainActivity.kt                  # NavHost 入口 + 共享 CampusNetworkClient
├── data/
│   ├── CampusNetworkClient.kt       # OkHttp：登录/注销/dashboard/重试
│   ├── DashboardParser.kt           # HTML + JS 变量 → Dashboard（6 级 V6 回退）
│   ├── CredentialStore.kt           # 加密凭据存储
│   ├── CachedDashboard.kt           # 内存缓存（AtomicReference）
│   ├── UpdateChecker.kt             # GitHub Releases 更新检查
│   └── model/
│       ├── Dashboard.kt
│       ├── Carrier.kt
│       └── BulletinItem.kt
└── ui/
    ├── LoginScreen.kt + LoginViewModel.kt
    └── DashboardScreen.kt + DashboardViewModel.kt
```

## 工作原理

### 登录流程

1. GET `http://202.204.48.66/` 检测是否已登录（解析页面里的账号字段）
2. 若未登录，从首页 URL 或 JS 变量中提取 `wlan_user_ip`
3. 调用 ePortal JSONP 接口：
   ```
   GET http://202.204.48.66:801/eportal/portal/login?
       callback=dr1003&login_method=1
       &user_account=<0|1>,<账号>
       &user_password=<密码>
       &wlan_user_ip=<IP>
       &jsVersion=4.1&terminal_type=1&lang=zh&v=<ts>
   ```
4. 解析 JSONP 响应中的 `result` 和 `msg`
5. 成功后再次拉取首页，解析出 Dashboard

不同部署可能使用 0-based 或 1-based 的运营商 ID，还会根据运营商额外尝试 `account@dx` / `account@lt` suffix 格式。

### Dashboard 解析

页面关键字段来自三类来源：
- **HTML 元素**：`#user_account`、`#user_usetime`、`#user_useflow`
- **JS 变量**：`uid`、`fee`、`flow`、`v6df`、`v6af`、`time`、`stime`、`v4ip`、`v6ip`
- **页面标签文本**：例如 "流量(V6)" 标签后紧跟的数值

实测 USTB 部署中 IPv6 流量计数器 `v6df` / `v6af` 单位为 256 字节/tick（除以 4 = KB），与 IPv4 的 `flow`（直读 KB）不同，解析时已自动区分。

### 注销

```
GET http://202.204.48.66:801/eportal/portal/logout?callback=dr1004&...
```

注销后通过外网连通性测试验证：访问外部站点，若未被重定向到 portal 则判定注销失败。

## 构建

需要 Android Studio Ladybug+ 或 JDK 21。

```bash
# Windows
gradlew.bat assembleDebug

# Unix-like
./gradlew assembleDebug
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

## 使用说明

1. 连接校园 WiFi（USTB-WLAN 等）
2. 打开 App
3. 输入校园网账号、密码，选择运营商（默认即可）
4. 点击登录
5. 登录成功后即可看到用量信息
6. 之后再次打开 App，账号密码会自动填入，且若会话仍有效会直接显示 Dashboard

## 已知限制

- 仅在 `202.204.48.66` 这一个 ePortal 部署上验证过，其他学校需要修改 `CampusNetworkClient.HOST`
- IPv6 流量计数器的单位因部署而异（USTB 实测 ÷4 = KB，其他学校可能是直读 KB），`DashboardParser` 已按优先级兼容
- 余额单位（元 / 分）依赖 JS 变量 `fee` 的具体含义
- 没有后台保活 / 自动重连

## 隐私说明

- 账号密码仅保存在本机 `EncryptedSharedPreferences` 中，不上传任何服务器
- 网络请求只发往 `202.204.48.66` 与触发重定向用的 `1.1.1.1` / `baidu.com`
- 没有埋点、没有统计、没有广告

## License

MIT
