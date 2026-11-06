# Calibre 阅读（CalibreReader）

自建 Calibre 服务的 Android 客户端。**calibre-server（官方内容服务器）与 Calibre-Web 双兼容**，全部走 OPDS 1.2 协议，不硬编码任何服务端路由；内置 EPUB/KEPUB/PDF 阅读器，不再依赖第三方阅读 App。

## 已实现（v0.10.0）

| 模块 | 说明 |
|---|---|
| 服务器管理 | 多服务器保存、地址自动补全、OPDS 路径自动探测（`/opds/`、`/opds`、`/opds/v1.2/catalog`）、服务类型识别 |
| 认证 | HTTP Basic；密码用 AndroidKeyStore AES-GCM 加密后落盘 |
| 网络兼容 | 明文 HTTP、自签证书开关、连接超时友好提示 |
| 书库浏览 | **兼容 Calibre-Web 无 `rel` 的导航链接**、打开即见书（自动下钻至书籍 feed，最多两层）、OPDS 导航栈、快捷分类、分页自动加载、全库搜索（OpenSearch 模板优先取含 `{searchTerms}` 的链接） |
| 一键阅读 | 已下载的书直接打开；未下载自动优先取 EPUB/KEPUB，其次 PDF，**全部在 App 内打开** |
| **书库分组** | 按标签 / 作者 / 系列 / 出版社 / 语言分组显示，可切维度、点分组筛选；「全部分类」一键回到服务端自己的分类树（书架 / 作者 / 系列 / 类别） |
| 封面 | 带认证加载 + 内存 LRU + OkHttp 磁盘缓存，无封面显示书名占位 |
| 书籍详情 | 元数据（作者/出版社/语言/日期/标签）、简介、多格式列表与文件大小 |
| 下载 | 64KB 流式下载、`.part` 临时文件、进度回调、完成后入库 |
| 书架 | Room 持久化、进度条、文件丢失检测、删除确认 |
| **内置阅读器** | Readium 3.1.2 渲染 EPUB / KEPUB；系统 `PdfRenderer` 渲染 PDF（翻页、缩放、拖动、页码跳转）。EPUB 支持左右翻页/滚动、目录跳转、字号、行距、字体与主题；两种格式都不跳外部 App |
| **进度同步** | Calibre-Web Kobo 协议双向同步（拉取 + 推送），calibre-server 自动降级为本地保存 |
| **检查更新** | GitHub Releases 版本检查（后台静默 + 手动）、APK 下载；**全程手动**：不弹窗、不自动安装、不自动跳系统设置页，每一步都在设置页「关于」里点按钮 |
| **外观** | 墨紫（weknora-mobile）/ 晴蓝（AnyWords）两套配色 × 跟随系统 / 浅色 / 深色，设置页实时切换；统一大圆角、字体排版与卡片层次 |
| **界面中文化** | Calibre-Web 官方模板 30 条分类文案 + calibre-server 常见导航 + 二级索引，客户端展示层翻译；未收录文案原样回退 |

## v0.10.0：PDF 内置阅读 + 书库分组

### PDF 不再跳到其它软件

之前 `isReadableInApp` 只认 EPUB/KEPUB，PDF 下载完走 `ACTION_VIEW` 交给系统选择器。这一版新增独立的 `PdfReaderActivity`：

- 用 Android 自带的 `android.graphics.pdf.PdfRenderer` 按页渲染，**不引入第三方 PDF 引擎、不增加 APK 体积**；渲染限制在单线程 dispatcher，位图宽度封顶 1440px 防 OOM。
- 翻页：左右滑动、点屏幕左右两侧、上一页/下一页按钮、页码滑块；缩放按 `100% → 150% → 200% → 300%` 循环，放大后可拖动。
- 进度按页保存在 Room（`{"pdfPage": n}`），Kobo 支持时按百分比推送；返回时自动续读。
- 一键阅读优先级 `EPUB/KEPUB > PDF > 其它`，可读格式一律进内置阅读器；MOBI 等仍可交外部应用。

### 书库按「文件夹」大概分个类

Calibre 的数据模型里没有磁盘文件夹字段，OPDS 也不返回路径；**最接近文件夹的是标签**（Calibre-Web 官方 `feed.xml` 会为每本书输出 `<category term="标签" label="标签"/>`），其次是服务端自己的书架/分类目录。v0.8.0 为了「打开即见书」加了自动下钻，把分类树整页跳过了，所以书看起来全堆在一起。

| 能力 | 说明 |
|---|---|
| 默认分组 | 有标签时默认「按标签」，否则依次退到作者 / 系列 / 出版社 / 语言 |
| 维度切换 | 书库顶部「分类」下拉只列出真正能分成 ≥2 组的维度 |
| 分组筛选 | 每组一个 chip（带数量），点一下只看该组；「全部」恢复 |
| 分节网格 | 每组一个标题条 + 该组书籍，未分类永远排在最后 |
| 全部分类 | 一键回到服务端根导航（书架 / 作者 / 类别 / 系列），不受自动直达影响 |
| 系列识别 | Calibre-Web 把系列写在 `<content>` 的 `SERIES: 名称 [序号]` 行里，单独解析 |

分组是纯展示层计算（`BookGroups.kt`），不改 `OpdsEntry`，不影响 `pickAllBooksShortcut()` 与点击链。新增 `BookGroupsTest.kt` 7 项单测（共 42 项）。

> 如果你在 Calibre 里用的是「自定义列」当文件夹，或想严格按磁盘目录分，OPDS 拿不到这些信息 —— 走「全部分类」看服务端的书架导航，或告诉我具体字段名，我再补一维映射。

## v0.9.0：书库分类菜单中文化

服务端 OPDS 返回的分类标题是英文（`Alphabetical Books` / `Hot Books` / `Categories` ...），手机上看不友好。这一版在客户端做展示层翻译：

| 覆盖 | 内容 |
|---|---|
| Calibre-Web | 官方 `cps/templates/index.xml` 的 **30 条文案**（15 个分类标题 + 15 条描述）全部有中文 |
| calibre-server | `By Newest` / `By Title` / `By Author` / `By Series` / `By Tag` 等常见导航 |
| 二级索引 | `All` → 全部、`All Books` → 全部书籍；A–Z 单字母保留 |
| 出现位置 | 分类列表、快捷入口 chips、混合 feed 的目录行、顶栏标题与面包屑、分类描述、封面占位文字 |

设计边界：

- **只改展示，不改数据**：`OpdsEntry.title` 保留服务端原文，`ServerProbe.detectKind()` 与 `pickAllBooksShortcut()` 仍靠英文标题判断；映射只发生在 Composable 里。
- **不翻译内容数据**：书名、作者、简介、标签、用户自定义分类原样显示；未收录文案原样回退（服务端本来就是中文时不受影响）。
- 归一化匹配：忽略大小写、多余空白与句末标点。

另兼容部分版本省略 `from this catalog` 的描述变体，以及 `Search Results` 等 feed 标题。新增 `data/opds/OpdsLabels.kt` 纯函数 + `OpdsLabelsTest.kt` 7 项单测（共 35 项）。

## v0.8.0：真正修好「书库点不开」——OPDS 导航链接识别

拿到真机截图后才定位到真正的根因，前三轮（v0.4.0 / v0.5.0 / v0.7.0）都在查导航状态与自动弹窗，方向不对。截图显示：`v0.7.0` 已经装上了，画面其实是**书库的 OPDS 根分类目录**（一列带 `>` 的条目，看起来像 Android 设置菜单），底部 Toast 是「该条目没有可打开的内容」。

| 事实 | 说明 |
|---|---|
| Calibre-Web `cps/templates/index.xml` | 根导航 entry 写作 `<link href="/opds/books" type="application/atom+xml;profile=opds-catalog"/>` —— **没有 `rel` 属性** |
| 旧实现 `navLinks` | 只认 `rel == "subsection"`，于是根节点所有分类的 `navLinks` 恒为空 |
| 后果 | 点任何分类都弹「该条目没有可打开的内容」；`pickAllBooksShortcut` 也依赖 `navLinks`，自动直达同样失效 → 永远卡在分类页，看不到书 |

> 旧单测里「Calibre-Web 结构解析」自己臆造了 `rel="subsection"`，所以一直没暴露这个差异。这次按官方模板原文补了回归用例。

修复：

- `OpdsLink` 增加 `isCatalogFeed` / `isAcquisition` / `isImage` / `isFeedControl`；`navLinks` 兼容显式 `subsection`/`collection` 与「按 MIME type 识别的 Atom/OPDS 目录链接」；新增 `navigationHref` 兜底，杜绝「点不开」的死路。
- 自动直达改为**最多连续下钻两层直到见到书籍 feed**（只在自动链上生效，用户手动点分类不会被强行跳走）；优先级兼顾 calibre-server 的「By Title」（全量）与 Calibre-Web 的「Recently added Books」（直接返回书籍 feed）—— 后者的「Alphabetical Books」其实是 A–Z 字母索引，直达它仍然看不到书。
- 搜索模板：Calibre-Web 根 feed 有两个 `rel="search"`，第一个是 OpenSearch 描述文档、第二个才带 `{searchTerms}`；改为优先取含占位符的链接。
- 界面消歧：根目录顶栏标题明确写「书库」，左上角齿轮移到右上角；分类页顶部加「这是书库的分类，不是设置」说明；无链接的条目标红显示「无链接」而不是画一个假箭头。
- 本版在 **Android 模拟器 + 本地模拟 Calibre-Web OPDS 服务**上完成实测（见交付说明）；`mock_opds_server.py` 保留在工作区可复现。

## v0.7.0：拆掉「自动挡路」，并让版本可自检

第三次收到「书库还是设置页、所有条目都点不了」。这轮把范围从导航代码扩大到**所有会抢占界面的自动行为**，才找到前两轮漏掉的一整条链：

| 之前的行为 | 造成的现象 |
|---|---|
| 启动静默检查发现新版 → 自动弹 `AlertDialog` | 模态层吃掉整屏点击，底下的条目「全都点不了」 |
| 点「立即更新」→ 下载完自动拉起系统安装器 | 用户一回到 App 就被弹走 |
| 缺「安装未知应用」权限 → 自动跳 **Android 系统的设置页** | 看起来就是「App 卡在设置界面」 |
| `ON_RESUME` 自动 `retryInstall()` | 每次回前台重复上面两步，形成循环，用户根本回不到书库，也就一直没能完成升级 |

修复：**应用内更新全部改成纯手动**——不弹窗、不自动安装、不自动跳设置页；下载完只停在「安装包已就绪」，由用户点「安装」；需要授权时给「去授权」按钮而不是自动跳转；删除 `ON_RESUME` 自动重试。另加 `BackHandler`：任何非书库页按返回都回书库。

版本自检（避免再出现「改了三轮但其实装的是旧包」）：
- **版本变化时一次性 Toast** `CalibreReader v0.7.0 已启动 · 书库就绪`，Toast 不受任何 UI 遮挡影响
- **底部导航栏上方常驻版本标识** `Calibre 阅读 v0.7.0 · 当前：书库`，三个页面都能看到
- 书库引导页、书库顶栏、设置页顶栏继续显示版本号
- 不依赖 App 的终极确认：手机「设置 → 应用管理 → Calibre 阅读 → 版本号」

另核对了 v0.1.0 ~ v0.6.0 六个 APK 的签名证书，SHA-256 全部为 `bac23182…58d8c67`，**排除签名冲突导致覆盖安装失败**。

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
./gradlew :app:testDebugUnitTest    # 42 个单元测试：OPDS 解析 + Calibre-Web 官方模板回归 + 导航文案中文化 + 书库分组 + PDF 格式判定 + Kobo 同步 + 版本比较 + 目录直达启发式
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
- **v0.8.0 已在 Android 模拟器（AVD wk34 / Android 34 x86_64）实测**：安装启动、书库为默认页、保存服务器后自动直达书封面网格、返回分类页、点分类进入下一级、第二层再进书列表，全程无「该条目没有可打开的内容」。测试用的本地模拟服务见工作区 `mock_opds_server.py`
- 实体手机仍未验证；`AndroidView` 承载 Readium Fragment、内置阅读器打开 EPUB、未知来源授权返回续装等仍待实机确认
- 「自动直达书籍列表」靠 OPDS 根导航条目标题匹配（中英文关键字）；服务端若用了完全不同的命名，会停在分类页，可用快捷入口手动进入
- 自更新依赖 GitHub 可达；GitHub API 匿名限流 60 次/小时（12h 节流远低于此）
- Debug 签名，仅自用。若换正式签名，必须先在设备上卸载旧版，否则安装器会拒绝覆盖
