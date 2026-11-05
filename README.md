# Calibre 阅读（CalibreReader）

自建 Calibre 服务的 Android 客户端。**calibre-server（官方内容服务器）与 Calibre-Web 双兼容**，全部走 OPDS 1.2 协议，不硬编码任何服务端路由；内置 Readium 阅读器，不再依赖第三方阅读 App。

## 已实现（v0.6.0）

| 模块 | 说明 |
|---|---|
| 服务器管理 | 多服务器保存、地址自动补全、OPDS 路径自动探测（`/opds/`、`/opds`、`/opds/v1.2/catalog`）、服务类型识别 |
| 认证 | HTTP Basic；密码用 AndroidKeyStore AES-GCM 加密后落盘 |
| 网络兼容 | 明文 HTTP、自签证书开关、连接超时友好提示 |
| 书库浏览 | 打开即见书（自动进入全库目录）、OPDS 导航栈、快捷分类、分页自动加载、全库搜索（OpenSearch 模板沿用根目录） |
| 一键阅读 | 已下载的书直接打开；未下载自动取 EPUB 下载并拉起内置阅读器 |
| 封面 | 带认证加载 + 内存 LRU + OkHttp 磁盘缓存，无封面显示书名占位 |
| 书籍详情 | 元数据（作者/出版社/语言/日期/标签）、简介、多格式列表与文件大小 |
| 下载 | 64KB 流式下载、`.part` 临时文件、进度回调、完成后入库 |
| 书架 | Room 持久化、进度条、文件丢失检测、删除确认 |
| **内置阅读器** | Readium 3.1.2 渲染 EPUB / KEPUB；左右翻页或滚动模式；目录跳转；字号、行距、衬线/无衬线、白底/护眼/夜间主题实时生效 |
| **进度同步** | Calibre-Web Kobo 协议双向同步（拉取 + 推送），calibre-server 自动降级为本地保存 |
| **检查更新** | GitHub Releases 版本检查（启动静默 + 手动）、APK 下载、拉起系统安装器、未知来源授权跳转与返回续装 |
| **外观** | 墨紫（weknora-mobile）/ 晴蓝（AnyWords）两套配色 × 跟随系统 / 浅色 / 深色，设置页实时切换；统一大圆角、字体排版与卡片层次 |

## v0.6.0：视觉重做，对齐其他软件的设计语言

参考的是用户自己项目里的真实 token，不是凭感觉配色：

| 来源 | 取用的设计 token |
|---|---|
| `weknora-mobile/tailwind.config.js` | `brand` 紫色阶（`#6D5CF5` / `#5B5BD6`）、`surface`（`#F6F7F9` / `#F0F1F4`）、`ink`（`#1A1D26` / `#5B5F66`）、`line`、`accent`、18~24px 大圆角、`0 4px 12px rgba(17,17,26,.06)` 轻阴影、Inter / Noto Sans SC |
| `weknora-mobile/src/index.css` `.dark` | 深色表面 `#121317` / `#1C1E24` / `#26282F`，文字 `#ECEEF3` / `#B4B9C3` |
| `AnyWords/frontend/src/index.css` | 苹果风 `--accent #0071E3` / `#2997FF`、`--bg #F5F5F7`、`--text #1D1D1F`、`--border #D2D2D7`、克制留白 |

| 改动 | 效果 |
|---|---|
| 关掉 `dynamicColor` | 之前 Android 12+ 会按壁纸生成 Material You 配色，把自定义品牌色冲掉，同一台机器观感都不一致 —— 这是「杂」的主因。现在颜色完全由配色方案决定 |
| 四套 ColorScheme | 墨紫 / 晴蓝 × 浅色 / 深色，`Color.kt` 集中管理 |
| Design Token | `Type.kt` 收紧字距、调整行高；`Shape.kt` 统一 8~28dp 圆角，全 App 卡片/按钮/弹层一致 |
| 设置页「外观」卡片 | 两套配色 + 跟随系统 / 浅色 / 深色，`ThemePrefs` 持久化，切换立即生效（MainActivity 与阅读器同步重组） |
| 书库网格 | 封面 14dp 圆角 + 3dp 阴影、渐变书脊占位（不再是平涂色块）、网格间距与书名/作者排版重排 |
| 引导页 | 品牌渐变插画替代默认图标、白卡片层次、52dp 主按钮 |
| 其它 | 底部导航细分隔线 + 纯 surface 底、详情弹层 24dp 顶部圆角、书架进度条圆角、空态图标圆形容器、启动窗口背景与 launcher 底色跟随新配色 |

## v0.5.0：修掉「升级后还是设置页、按钮全点不了」

v0.4.0 改完仍有用户反馈打开就是设置页、所有按钮点不动。定位到两个真问题：

| 根因 | 修复 |
|---|---|
| `rememberSaveable` 把旧版本保存的「停留在设置 tab」状态恢复到新版本，冷启动直接落在设置页 | tab / 弹窗状态改用 `remember`，**每次冷启动强制回书库**，不再读旧 savedInstanceState |
| 「添加服务器」对话框的探测协程一旦抛异常，`busy` 永远停在 `true` → 三个按钮全禁用、`Dialog` 连返回键都关不掉，看起来就是「整屏点不了」 | `try/finally` 保证 busy 复位、25 秒总超时、可取消的 Job、busy 时仍可点「取消连接」关闭 |
| DataStore 首帧异常时永远停在加载圈 | 3 秒超时 + 异常兜底，超时按「无服务器」渲染书库引导页，后续仍会收到真实配置 |
| 无法判断手机上到底装的是哪个包 | 书库引导页、书库顶栏、设置页顶栏**都显示当前版本号**；设置页新增「回书库」按钮 |

## v0.4.0：书库优先

之前只要没配置服务器，底部导航会被强制改成服务器设置页，点「书库」没有反应 —— 等于把「手机方便访问书库」这个主目的挡在门外。这一版把书库做成真正的主界面：

| 改动 | 效果 |
|---|---|
| 去掉 tab 强制跳转 | 书库 / 书架 / 设置三个 tab 始终可切换，书库是默认首页 |
| 首帧就绪判定 | DataStore 首帧空列表不再闪设置页，改为「正在准备书库…」 |
| 书库内连接引导 | 没连服务器时，书库页直接弹「添加服务器」，不用先跳设置页 |
| 自动直达书籍列表 | OPDS 根只是分类导航时，自动进入「全部书籍 / By Title / 最新」类入口；返回键可回到分类页 |
| 快捷入口 | 分类页顶部一排 chip，少点一层目录 |
| 全局搜索 | 根 feed 的 OpenSearch 模板沿用到子目录，任何位置都能搜全库 |
| 一键阅读 | 书籍详情主按钮：已下载直接打开，未下载自动下载首选格式并拉起阅读器 |
| 服务器切换 | 多台服务器时，书库顶栏「切换 ▾」直接换库 |
| 混合目录 | 目录 + 书籍混合的 feed 分区展示，目录不再被画成书封面 |
| 错误页兜底 | 连不上时除「重试」外可直接「检查服务器设置」 |

首次使用：打开 App → 书库页「连接 Calibre 服务器」→ 填 `http://<内网IP>:8083`（calibre-server 一般是 8080）→ 保存，自动探测 OPDS 并回到书库 → 点书 → 一键阅读。

**排查提示**：书库引导页右上角、书库顶栏副标题、设置页顶栏都会显示当前版本号。升级后如果这里不是 `v0.5.0`，说明新 APK 没有真正装上（常见于点了「立即更新」但没在系统安装器里确认，或提示「应用未安装」）。v0.4.0 / v0.5.0 的 debug 签名证书一致（`SHA-256 bac23182…58d8c67`），可直接覆盖安装；若仍提示签名冲突，先卸载旧版再装（服务器配置需重填）。

## 应用内更新

版本来源于本仓库的 GitHub Releases。启动时静默检查一次（12 小时节流），设置页底部「关于」卡片可随时手动检查。

```
GitHub Releases (latest)
   ↓ tag_name 与 BuildConfig.VERSION_NAME 做语义化比较
有新版 → 下载 APK 到 cacheDir/update（.part 保护）
   ↓ FileProvider（cache-path）
系统安装器 → 覆盖安装
```

发版流程：改 `app/build.gradle.kts` 的 `versionCode` / `versionName` → 构建 → 上传 APK 到新 tag 的 Release。**不发布 Release，客户端就永远认为已是最新。**

> 检查更新使用独立的 `OkHttpClient`（`AppContainer.updateClient`），不带任何认证拦截器。切勿改成复用 `opdsClient` —— 会把 Calibre 服务器账号密码作为 Basic 头发给 `api.github.com`。


## 阅读器

EPUB / KEPUB 下载完成后，从书架或下载完成提示直接进入内置阅读器：

- **位置恢复**：Readium `Locator` JSON 存 Room，精确到章节内偏移（不是只记百分比）
- **目录**：解析 `manifest.tableOfContents`，最多 5 级缩进
- **设置**：字号 70%–220%、行距 1.0–2.2、主题/字体即时生效并全局记忆
- **节流保存**：本地每 3 秒、远端每 45 秒、退出前台时强制保存

PDF / MOBI / AZW3 / CBZ 仍走系统阅读器（Readium 的 PDF navigator 需要额外引入 pdfium，暂未打包）。

## 进度同步配置（Calibre-Web）

1. Calibre-Web → 右上角用户名 → **用户设置** → 页面下方 **Kobo 授权令牌**（`Generate Kobo Auth Token`）
2. 复制形如 `9f3c1a...`（32 位 hex）的令牌
3. 本 App → 服务器 → 编辑 → **Kobo 令牌** 填入并保存

实现依据 Calibre-Web 源码（`cps/kobo.py`）：

| 行为 | 请求 |
|---|---|
| 拉取 | `GET  <base>/kobo/<token>/v1/library/<uuid>/state` |
| 推送 | `PUT  <base>/kobo/<token>/v1/library/<uuid>/state` |

- `<uuid>` 直接从 OPDS entry 的 `<id>urn:uuid:xxx</id>` 里取，无需额外同步目录
- 进度写入 `CurrentBookmark.ProgressPercent`（0–100），位置写入 `Location.Value`，`Location.Type` 用自定义标记 `CalibreReaderLocator` 区分 Kobo 设备写的 `KoboSpan`
- 打开书时若服务端进度明显领先（>1%），自动以服务端位置续读；否则本地优先，避免覆盖本机最新进度
- `calibre-server` 没有该接口，`isSupported()` 返回 false，进度仅存本机

> 不使用 `/ajax/bookmark` 通道：它依赖 Calibre-Web 的登录会话与 CSRF token，反向代理和版本差异下太脆，收益也不如标准 Kobo 接口。

## 构建

```bash
# 环境：JDK 17+、Android SDK 35、Gradle 8.14.2
./gradlew :app:assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # 23 个单元测试：OPDS 解析 + Kobo 同步 + 版本比较 + 目录直达启发式
```

`local.properties` 需指向本机 SDK：

```properties
sdk.dir=C\:\\Users\\<you>\\Android\\Sdk
```

## 服务端配置要点

| 服务端 | 启动方式 | 客户端填写 |
|---|---|---|
| calibre-server | `calibre-server --listen-on 0.0.0.0 --port 8080 ~/Calibre\ 书库` | `http://<内网IP>:8080` |
| Calibre-Web | 默认 8083 端口 | `http://<内网IP>:8083` + 用户名密码（可选 Kobo 令牌） |

> 只监听 `127.0.0.1` 时手机无法访问，必须 `--listen-on 0.0.0.0`。
> 反向代理若开启登录墙会拦截 `/opds`，需放行该路径。

## 技术栈

Kotlin 2.1.21 · Jetpack Compose (Material 3) · Room 2.7.2 · OkHttp 4.12 · DataStore · Readium Kotlin Toolkit 3.1.2 · AGP 8.10.1 / Gradle 8.14.2 · minSdk 26 / targetSdk 35

## 结构

```
app/src/main/java/com/lichong/calibrereader/
├── CalibreApp.kt              手写 DI 容器
├── MainActivity.kt
├── data/
│   ├── db/                    Room 实体/DAO/仓库（含 1→2 迁移）
│   ├── download/              流式下载器、文件打开器
│   ├── image/                 封面加载器（认证 + 双层缓存）
│   ├── model/                 ServerConfig
│   ├── net/                   OPDS 客户端、服务端探测
│   ├── opds/                  OPDS 模型 + Atom 解析器（含 koboUuid 提取）
│   ├── prefs/                 DataStore + KeyStore 加密
│   ├── reader/                Readium Publication 加载
│   └── sync/                  Kobo 协议进度同步
└── ui/
    ├── AppRoot.kt             底部导航（书库/书架/设置）+ 书库首页连接引导
    ├── browse/                浏览 + 详情 + 下载
    ├── library/               书架
    ├── reader/                内置阅读器（Activity + Compose + Fragment 宿主）
    ├── server/                服务器管理 + 探测对话框
    ├── components/            带认证封面组件
    └── theme/                 Material 3 主题
```

## 已知限制

- 内置阅读器只覆盖 EPUB/KEPUB；PDF 需另加 `io.legere:pdfiumandroid`
- 进度同步仅 Calibre-Web（需 Kobo 令牌），不支持官方 calibre-server
- 阅读偏好为全局共享，不按书单独记忆
- 未做真机验证（开发环境无连接设备）；`AndroidView` 承载 Readium Fragment、未知来源授权返回续装等仍待实机确认
- 「自动直达书籍列表」靠 OPDS 根导航条目标题匹配（中英文关键字）；服务端若用了完全不同的命名，会停在分类页，可用快捷入口手动进入
- 自更新依赖 GitHub 可达；GitHub API 匿名限流 60 次/小时（12h 节流远低于此）
- Debug 签名，仅自用。若换正式签名，必须先在设备上卸载旧版，否则安装器会拒绝覆盖
