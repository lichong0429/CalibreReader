# Calibre 阅读（CalibreReader）

自建 Calibre 服务的 Android 客户端。**calibre-server（官方内容服务器）与 Calibre-Web 双兼容**，全部走 OPDS 1.2 协议，不硬编码任何服务端路由；内置 Readium 阅读器，不再依赖第三方阅读 App。

## 已实现（v0.3.0）

| 模块 | 说明 |
|---|---|
| 服务器管理 | 多服务器保存、地址自动补全、OPDS 路径自动探测（`/opds/`、`/opds`、`/opds/v1.2/catalog`）、服务类型识别 |
| 认证 | HTTP Basic；密码用 AndroidKeyStore AES-GCM 加密后落盘 |
| 网络兼容 | 明文 HTTP、自签证书开关、连接超时友好提示 |
| 书库浏览 | OPDS 目录导航栈、面包屑返回、分页自动加载、搜索（OpenSearch 模板） |
| 封面 | 带认证加载 + 内存 LRU + OkHttp 磁盘缓存，无封面显示书名占位 |
| 书籍详情 | 元数据（作者/出版社/语言/日期/标签）、简介、多格式列表与文件大小 |
| 下载 | 64KB 流式下载、`.part` 临时文件、进度回调、完成后入库 |
| 书架 | Room 持久化、进度条、文件丢失检测、删除确认 |
| **内置阅读器** | Readium 3.1.2 渲染 EPUB / KEPUB；左右翻页或滚动模式；目录跳转；字号、行距、衬线/无衬线、白底/护眼/夜间主题实时生效 |
| **进度同步** | Calibre-Web Kobo 协议双向同步（拉取 + 推送），calibre-server 自动降级为本地保存 |
| **检查更新** | GitHub Releases 版本检查（启动静默 + 手动）、APK 下载、拉起系统安装器、未知来源授权跳转与返回续装 |

## 应用内更新

版本来源于本仓库的 GitHub Releases。启动时静默检查一次（12 小时节流），服务器页底部「关于」卡片可随时手动检查。

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
./gradlew :app:testDebugUnitTest    # 10 个单元测试：OPDS 解析 + Kobo 同步能力
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
    ├── AppRoot.kt             底部导航（书库/书架/服务器）
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
- 未做真机验证（开发环境无连接设备）
- 自更新依赖 GitHub 可达；GitHub API 匿名限流 60 次/小时（12h 节流远低于此）
- Debug 签名，仅自用。若换正式签名，必须先在设备上卸载旧版，否则安装器会拒绝覆盖
