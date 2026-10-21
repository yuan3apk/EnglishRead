# EnglishRead 交接文档

> 英语阅读学习 App：导入英文书 → 点词看释义 → 点「段」按钮看整段译文 → 听书。
> 本文件是项目的唯一入口文档：现状、决策、数据模型、实施步骤、验收标准、风险。接手先读完第 0 节。

最后更新：**M4 已完成（听书：系统 TTS + 前台服务 + 逐词高亮 + 通知栏控制）；M3 已完成（LLM 段落翻译 + 设置页）；M2 只上下滚动 + 一句话一段；阅读进度已落地；M5 生词本未开始**

---

## 0. 现状快照（实测，不是推测）

| 项 | 值 |
|---|---|
| 形态 | 纯 Android 原生单模块工程，`:app`，语言 **Java** |
| 包名 / applicationId | **`com.englishread.app`**（M0 已从 `com.example.englishread` 迁走） |
| AGP / Gradle | 8.13.1 / Gradle 8.14（wrapper，走腾讯云镜像） |
| compileSdk / targetSdk / minSdk | 36 / 36 / **28** |
| Java 编译级别 | source/target = 17 |
| 现有依赖 | appcompat、material、activity、constraintlayout、**recyclerview**、**drawerlayout**、junit、espresso |
| 本机 JDK | **`C:\Users\y\.jdks\corretto-17-jdk`（Corretto 17.0.20.1）** |
| SDK | `C:\Users\y\AppData\Local\Android\Sdk`，platforms：android-36；build-tools：35.0.0、36.1.0 |
| 可用 AVD | `Pixel_9_Pro`（android-36.1，Google Play x86_64）、`Medium_Phone` |

### M0 已完成清单

- [x] 包名迁移：`com.example.englishread` → **`com.englishread.app`**，目录与 `namespace`/`applicationId` 同步，旧的 `com.example` 源目录已删除
- [x] 声明确已在本地缓存中的 `androidx.recyclerview:1.3.2`
- [x] Java 编译级别 11 → **17**（与 JDK 17 对齐）
- [x] `App.java` 创建并在 Manifest 注册 `android:name=".App"`
- [x] 包结构骨架：`ui/`、`model/`、`parse/`
- [x] 书架页：`MaterialToolbar` + `RecyclerView`（`LibraryAdapter` + `item_book.xml`）+ 空态 + 导入 FAB
- [x] 主题 token：品牌色、米白/深色页面底色、三档文字样式、间距与阅读器排版尺寸
- [x] 单测 `TokenizerTest`（3 例）+ 仪器测试 `PackageIdTest` 全绿

### M2 已完成清单

- [x] `model/{Chapter,Paragraph,WordEntry}.java`：`paragraph.text` 只含原文的字段约定写进类注释
- [x] `data/ParagraphDao.java`：章节列表、整章段落、译文回写、未译计数
- [x] `data/DictSource.java`：`form` 反查（ran→run）→ `dict` 取释义 → 业务库 `word_cache` 兜底；assets 库先拷到 `databases/`
- [x] `reader/ReaderHitTest.java`：纯函数，只有 `word` / `none` 两种结果，无 `gutterPx`、无 marker
- [x] `reader/ParagraphGestures.java`：`GestureDetector` + `onTouch` 返回 false，滚动不受影响
- [x] `reader/ReaderAdapter.java`：正文纯原文 + 独立小号「段」按钮（48dp `TouchDelegate`）+ 内联译文
- [x] `reader/TranslationPopup.java` + `popup_word.xml`：词卡锚定词矩形，下方放不下翻到上方
- [x] `ui/ReaderActivity.java`：阅读器主界面
- [x] `ui/LibraryAdapter`：补 `OnBookClick`，书架点击进阅读器；Manifest 注册 `ReaderActivity`
- [x] `tools/build_dict.py`：ECDICT → 8 万词裁剪（10.9 MB）+ 5.6 万变形反查
- [x] `app/src/main/assets/dict.db`：内置词典已生成并入库
- [x] 测试：`ReaderHitTestTest`（6 例）、`TokenizerTest`（+2 例）、`ReaderDataTest`（4 例，仪器）

### M3 已完成清单（本次）

- [x] `trans/TransProtocol.java`：线协议纯函数——拼「编号|原文」、宽松切回复、"base_url"→完整地址、https 校验
- [x] `trans/ProviderTemplates.java`：7 家厂商的 base_url + model 模板，设置页 chip 一键填入（**绝不含 Key**）
- [x] `trans/ApiConfig.java`：base_url / key / model 三件套存私有 SP
- [x] `trans/LlmClient.java`：`HttpURLConnection` + `org.json` 打 chat/completions，超时 10s/20s，只对超时·断网·5xx·408·429 重试 1 次
- [x] `trans/TransQueue.java`：批量（自动按 `MAX_BATCH=5` 切）、同段去重、漏段只补发缺的、失败落 `STATUS_FAILED`、可取消
- [x] `ui/SettingsActivity.java` + `activity_settings.xml`：三件套 + 隐私提示 + https 校验
- [x] 阅读器设置面板加「翻译服务设置」入口（撞上「未配置」的地方就能就地改）
- [x] `item_paragraph.xml` 加 `para_progress`：翻译中 / 失败重试 / 正常三态互斥
- [x] `ReaderAdapter`：`toggleExpand`（已译只显隐不发请求）、`setPending`（翻译中禁用「段」）
- [x] `ReaderActivity.onTranslateToggle` / `onTranslateRetry` 接真队列；`onDestroy` 取消
- [x] Manifest 加 `INTERNET`；`app/src/debug/AndroidManifest.xml` 放行明文（仅 debug）
- [x] `tools/fake_llm.py`：本地假 LLM，支持 `--drop N`（漏段）与 `--delay S`（慢响应）
- [x] 测试：`TransProtocolTest`（15 例）、`TransQueuePendingTest`（4 例）、`LlmResponseParseTest`（5 例，仪器）

**验收结果（真机模拟器逐项实测）**：单测 **50/50**、仪器测试 **17/17** 全绿。
`deepseek-chat`/`paras=2`/`Bearer` 三件套在服务端日志里逐字确认；
译文内联展开在正文下方；**第二次展开日志无任何请求**（控成本命门成立）；
断网点「段」→ 10s 超时后重试 1 次 → 落 `trans_status=3` + 界面「翻译失败，点击重试」，不崩溃；
恢复网络点重试 → 成功；
`--drop 1` 下补发只发缺的 1 段（服务端日志 `paras=1`），已译的那段保持 `2` 未被重发；
连点「段」5 次只产生 **1 次**请求；
请求在飞时按返回退出 → 库里两段仍是 `trans_status=0`，迟到的译文没有写回。

**M3 期间踩到并修掉的坑（后人别再踩）**：
- **`readAll` 绝不能截断响应体**（这是真实线上 bug，用户用 DeepSeek 时踩到）：
  原本为了「别把 HTML 错误页塞进 toast」而把读到的东西截到 300 字符，结果**成功响应也被截了**。
  真实厂商的 JSON 有 id/model/usage 等字段，动辄 400+ 字符，切成半个 JSON 就报
  「回复不是合法 JSON」。修法：`readAll` 原样读完，只给错误提示用的 `clip()` 负责截短。
  教训：**截断只能作用在「给人看的错误信息」上，不能作用在要解析的数据上**。
- **假服务响应太短会掩盖 bug**：原来的 `fake_llm.py` 只回几十字节的迷你 JSON，
  所以「客户端只读前 300 字节」这种 bug 一直测不出来。现在默认用 padding 撑到 1500+ 字节，
  按真实响应形状（id/object/created/model/choices/usage/system_fingerprint）返回。
  **改假服务时别把响应体又缩回去**，否则这个 bug 会静默复活。
- **测试要测在出错的那一层**：第一版回归用例直接调 `LlmClient.content(raw)`，
  可截断发生在 `readAll`（读流），所以 bug 明明还在、用例全绿。
  必须走 `readAll` + `content` 的完整路径（见 `longResponseSurvivesReadThenParse`）。
  验证测试有效性的办法：把 bug 临时加回去，确认它变红。
- **`org.json` 的 `optString(k, null)` 遇到 JSON `null` 返回字符串 `"null"`**，不是 Java null。
  判断「字段缺失/为 null」必须用 `isNull(k)`，否则 `content: null` 会被当成正常译文
  `"null"` 写进 `paragraph.trans`，永久缓存一个假译文。
- **DeepSeek 新模型默认开启思考模式**（`deepseek-flash` / `deepseek-v4-pro`）：
  会先输出一大段 `reasoning_content`，翻译这种活又慢又贵，而且思考模式下 `temperature` 无效。
  现在对 DeepSeek 的地址下发 `{"thinking":{"type":"disabled"}}`；
  **别的厂商不发**——它们不认这个字段，严格校验参数的服务商可能因此 400。
- **模型名会过期**：写 M3 时 DeepSeek 的正式模型名已经从 `deepseek-chat` 换成了 `deepseek-flash`
  （[官方定价页](https://api-docs.deepseek.com/quick_start/pricing)）。所以配置模板化，
  且界面必须允许手改 model——别把模板当硬约束，也别在代码里硬编码某一家的模型名当默认值。
- `TransProtocol.endpoint` **不能只判 `/v1`**：智谱的 base_url 是 `https://open.bigmodel.cn/api/paas/v4`，
  只认 `/v1` 会拼成 `/v4/v1/chat/completions` → 404。改成认任意版本段 `/v\d+$`。
  实测确认：拼对的地址返回 **401**（鉴权失败），拼错的返回 404。
- 模板里**绝不能带 Key**。填完仍需用户自己贴，「没 Key 就发不出去」这条防线不能被模板绕过。
- `setOnApplyWindowInsetsListener` 在 View 已经 attach 之后再挂，**永远等不到那次分发**。
  设置页标题压在状态栏上就是这么来的；挂完必须补一次 `ViewCompat.requestApplyInsets(v)`。
- AGP 8 **默认不生成 `BuildConfig`**，用 `BuildConfig.DEBUG` 要在 `build.gradle.kts` 里
  显式开 `buildFeatures { buildConfig = true }`，否则编译报「找不到 BuildConfig」。
- https 的强制**别写进 `LlmClient`**：那样本地假服务（http）做不了端到端验收。
  规则属于「用户能输入什么」→ 放 `SettingsActivity`；客户端只管把配置的地址打出去。
  明文放行只在 debug（`src/debug/AndroidManifest.xml` + `BuildConfig.DEBUG`），release 永远拒绝。
- `TransQueue.runBatch` 里判断「还有没有缺的」**不能用 `done.size()`**：`done` 是跨批累积的，
  第二批一开始就已经等于第一批的数量，判断会永远为假、漏段补发形同虚设。要按本批自己数。
- 「翻译中」不能只看 `STATUS_QUEUED`：真在飞的那段在内存里由 adapter 的 `pending` 集合记，
  数据库根本没来得及写（也不该为一个中间态写库）。两处都要认。

### M3 补丁 2：只译被点的那一句，译文默认收起（2026-10-21）

**症状**：点某一句的「段」，下面四五句的译文也一起弹出来。

**根因（两层，缺一不可）**：

1. `ReaderActivity.onTranslateToggle` 走 `batchFrom(p)`，从这一段起往后取最多 `MAX_BATCH=5` 段
   ——省一次往返，代价是多译 4 段（多花钱）而且**把这 4 段的译文一起显示**。
2. `ReaderAdapter.submit` 把所有「已译」段落都塞进 `expanded`，所以**重进章节就是满屏译文**，
   不是「默认关闭」。

**改法（三处，删的比加的多）**：

- `onTranslateToggle` / `onTranslateRetry` 统一走 `translateOne(p)`：`singletonList(p)`，永远只送一段。
  `batchFrom` 整个删掉（连 `TransProtocol` 的 import 一起删）。
- `ReaderAdapter.submit` 只清 `expanded`，不再自动摊开已译段落 → **默认收起**。
- `updateParagraph` 保留「译文回来就展开」——现在流进来的只有被点的那一段，
  这正是「只显示当前段」；改了它反而会变成点完什么都不显示。

**为什么不再多带几段**：省下的那一次往返，换来的是「多花 4 次钱 + 弹出一堆不是我要的译文」，
在「读哪译哪」的场景里这笔账是亏的。`TransQueue` 的批量能力留着（协议仍是 `编号|原文` 多行、
`MAX_BATCH` 照样切批），只是入口不再用它。

**实测（模拟器 Pixel_9_Pro，`sample` 书 2 句）**：点第 1 句「段」→ 只有第 1 句下方出现译文，
第 2 句没有；假服务日志 `paras=1`（一次只送一段）；重进章节后两句译文都收着；
再点已译的第 1 句只显隐、日志零新增（控成本命门未破）。单测 45/45。

### M3 补丁 3：滑动不再被当成点词（2026-10-21）

**症状**：上下滑动翻页时，如果手指最后停在一个单词上，抬手后莫名其妙弹出词卡，打断阅读。

**根因（不在手势判定里，在事件分发里）**：`ParagraphGestures.onTouch` 返回 false，
而段落 `TextView` 又不是 clickable —— **它根本当不上触摸目标**。于是 RecyclerView 接管了整个手势，
正文只收到一个 `ACTION_DOWN`，**永远等不到 `ACTION_UP`，也等不到 `ACTION_CANCEL`**。
而 `GestureDetector` 的长按计时器是**按下时就启动**的：既然没有 MOVE 把它取消、也没有 UP 结束它，
**500ms 到点必然触发 `onLongPress`** → 弹词卡。滑得越快、越顺手，越容易撞上。

这解释了两件旧事，别再当成两个 bug：
- 旧闻「adb `input tap` 在模拟器上必被判成长按」——其实就是这个：真长按从未发生，
  发生的是计时器到点。单击与长按共用 `handle()`，所以卡照样弹，看不出区别。
- 也解释了为什么「点词」一直"能用"：它一直是靠 500ms 后的长按弹出来的，单击路径其实从没跑通过。

**改法（一行）**：`ParagraphGestures` 构造里 `view.setClickable(true)`，让正文成为触摸目标，
拿到完整的 DOWN/MOVE/UP/CANCEL 事件流。之后：抬手是 `onSingleTapUp`；滑动时 RecyclerView 拦截 MOVE、
正文收到 `CANCEL`，计时器随之取消；手指按住不动满 500ms 才是长按。
**顺序不能反**：`setTextIsSelectable(false)` 内部会把 clickable 置回 false，必须写在它后面。

**副作用（可接受）**：正文里那点「行尾/短句右侧」的留白现在也归正文处理 → 走 `onBlankTap()` 呼菜单，
与点真的空白同一个效果（以前这种位置点了没反应）。

**实测（模拟器 Pixel_9_Pro，日志打 `onTouch` 的 action）**：
单击 → `act=0`(DOWN) → `act=1`(UP) → 一条词卡、仅此一次；
拖动 → `act=0` → `act=3`(CANCEL) → 无词卡，章节位置 1/147 → 2/147 正常滚动；
按住 900ms 不动 → 弹词卡（长按仍是长按）；点下方空白 → 菜单照旧一开一关。单测 45/45。

### 书架导入：本地文件 / URL 直链（2026-10-21）

**需求**：导入按钮点下去先问「本地文件 / 从 URL 导入」，URL 那条直接下载 EPUB/TXT 入库。

**改法（三个文件 + 一个布局，没加依赖）**：

- `ui/MainActivity.showImportSource()`：`MaterialAlertDialogBuilder.setItems` 两项，
  第一项照旧 `openDoc.launch(ImportHelper.mimeTypes())`，第二项 `askForUrl()`。
- `ui/MainActivity.askForUrl()`：`dialog_import_url.xml` 一个输入框 + 一句提示。
  **正按钮的监听必须在 `show()` 之后挂**——先 SetPositiveButton 再覆盖，
  才能做到「地址不合法时不关对话框」。校验只认 `http://` / `https://` 前缀。
- `ui/ImportHelper.importUrl()`：`HttpURLConnection`（连接 15s / 读取 30s、跟随重定向、
  带 UA）下载到 `filesDir/books/{uuid}.{ext}`，然后**复用跟本地导入完全相同的**
  `save()`（解析 + 入库）。两条路径只有一个出口，别为 URL 另写一套入库。
- 顺手把 `importUri` 里「解析 + 入库 + 失败删半成品」抽成 `save()` / `writeTo()` / `fail()`，
  两个入口共用；回调也提成 `onImportDone` 一个字段，别复制粘贴两份。

**格式判定（`pickName`，纯函数，单测 `UrlImportNameTest` 5 例）**：

1. URL 最后一段（`new URL().getPath()` 已去 query/fragment，再 `URLDecoder` 解码）
   以 `.epub` / `.txt` 结尾 → 用它；
2. 否则看响应 `Content-Type`：含 `epub` → `{host}.epub`，`text/*` → `{host}.txt`；
3. **`text/html` 与认不出的一律拒绝**，错误信息提示「请用以 .epub / .txt 结尾的直链」。

第 3 条不是洁癖：网页当纯文本入库只会得到一锅标签，用户还以为书坏了。

**顺手补的两道防线**：下载/拷贝都走 `writeTo()`，上限 **100 MB**（超了报错并删半成品）；
失败时半成品文件在 `writeTo()` 里就删掉，`importUri` 原本「拷贝中途失败会留半个文件」的漏也一并堵上。

**实测（模拟器 Pixel_9_Pro）**：`python -m http.server 8799` 服务 `samples/`，
URL 填 `http://10.0.2.2:8799/sample.txt`（**仅 debug 包放行明文**）→ 文件落到 `files/books/`、
书架多一条 `sample`、点进去就是 `It was the best of times.` / `It was the worst of times.` 两句，
与本地导入结果逐字一致，服务端日志出现该 GET；
换成 `http://10.0.2.2:8799/`（目录页 = `text/html`）→ 提示拒绝，`files/books/` 文件数不变、
书架不变、不崩溃。单测 50/50（新增 5 例）。

**别忘**：「取消」用 `@string/action_cancel` 而不是 `android.R.string.cancel`——
后者在英文系统上显示 `Cancel`，跟全中文界面混搭（删除确认框也一起改了）。

### 阅读进度：断点续读 + 书架百分比（2026-10-21）

**症状**：每次重开书都回到第一句；书架卡片永远 `0%`。

**根因**：`reading_pos` 表建了（M1），但**没有任何 DAO、没有任何读写**；
`BookDao.listAll` 照样查 `progress_pct` / `last_opened_at`，于是永远是 0 与 NULL；
`ReaderActivity.loadChapters` 里写死 `openChapter(0)`。

**改法（新增 `data/ReadingPosDao.java` + 阅读器三处，没动表结构）**：

- `ReadingPosDao.load/save`：`reading_pos` 存断点，`book` 表同一次写入里跟新
  `progress_pct` 与 `last_opened_at`（后者本来就是 `listAll` 的排序键，一直没人写）。
  两个 DAO 分开的话会出现「有断点没百分比」，书架又显示 0%，所以放一起。
- 断点内容 = **第一段可见的句子 id + 它被滚上去多少像素**，不是滚动像素总量：
  字号/段距一改总像素就作废，而「哪一句 + 句内偏移」换排版后依然对得上。
- `ReaderActivity.onPause()` 存断点（返回书架、进设置、切后台、被杀之前都走这条）；
  `loadChapters` 在同一次 IO 里读断点，`openChapter` 第一次加载完就
  `scrollToPositionWithOffset(段, offset)` 还原，然后 `pendingRestore` 置空——**只消费一次**，
  之后翻章不受影响。
- 断点里的 `chapter_id` 找不到（书被删了重导入）→ 从第一章读，不猜。
- 百分比 = `ReadingPosDao.progressPct(章列表, 当前章, 章内已读段数)`，
  **「读到第一句」算读了 1 段**，否则最后一章永远差一句到 100%。纯函数，`ProgressPctTest` 5 例。

**实测（模拟器 Pixel_9_Pro，`pp` 书 62 章 / 317 段的第 1 章）**：
读到 `45/317` → 返回书架显示 **1%** → 重进落回 `45/317`；
`am force-stop` 杀进程后再进仍是 `45/317`（真落库，不是内存态）；
目录跳到 `CHAPTER V.` 读几屏（`第 6 章 · 15/71`）→ 返回书架 **9%** → 重进落回同一句、
首行还是被截半行（说明句内偏移也还原了）。
**顺带修好**：切主题会重建 Activity，以前重建就回章首，现在因为 `onPause` 已存断点、重建立刻读回，位置不丢。

### M4 已完成清单：听书（2026-10-21）

- [x] `reader/SentenceIndex.java`：段落 → 朗读片段，**返回原文区间而不是子串**（逐词高亮换算只差一个 base）
- [x] `tts/TtsController.java`：引擎初始化 / 语言检查 / **一次只塞一句**的队列 / 状态机 / `onRangeStart` / 章尾顺延 / 全书读完
- [x] `tts/TtsService.java`：`mediaPlayback` 前台服务 + 通知栏（上一句 / 暂停 / 下一句）+ binder + `UiListener`
- [x] `ReaderAdapter`：逐词高亮（**直接改 Spannable 的 span**，不重绑 item）
- [x] `ReaderActivity`：听书按钮、绑定服务、跟读滚动、`onServiceConnected` 回填快照、无引擎提示
- [x] `ReaderPrefs`：语速 5 档（0.7–1.5）+「听书时点正文暂停」开关（**默认关**）
- [x] 依赖：**0 个新增**（通知用 appcompat 已带的 `androidx.core` `NotificationCompat`；**没引 MediaSession / MediaStyle**）

**验收结果（模拟器 Pixel_9_Pro 逐项实测）**：

- 单测 **63/63**、仪器测试 **19/19** 全绿。
- 点「听书」→ 从**当前可见那句**开始念；`onRangeStart` 逐词高亮（截图里 `everybody` / `score` / `vain` 依次亮起）。
- 念到章尾自动进下一章（日志 `load book=3 ch=1`），全书读完 → `book finished` + toast「本书已读完」。
- 通知栏：`isForeground=true foregroundId=1001 types=0x2`，标题=章名、正文=当前句；**上一句 / 暂停 / 下一句真能控**（暂停 → `state -> 2`、按钮变「继续」）；
  **划掉通知 = 停止**（`state -> 0`、通知消失、没有残留前台）。
- 回桌面 6 秒后通知正文仍在推进（后台在念）；从最近任务回到 App：章节 / 高亮 / 按钮自动接上，不用手动点。
- 语速显示 `1.00×` 且落盘（`shared_prefs/reader_prefs.xml`）。
- **无引擎**：`pm disable-user com.google.android.tts` 后点「听书」→ 弹「无法朗读 / 这台设备上找不到可用的语音引擎…」+「知道了 · 去系统设置」，**不崩、不留通知**；
  重新 enable 后**不重启 App** 直接再点「听书」就能念（初始化失败会重建引擎再试）。
- 「点正文暂停」开关（默认关）打开后，点单词 → `state -> 2` 且**不弹词卡**；再点 → `state -> 1`。
- **引擎初始化看门狗**（8 秒 + 重试一次）：把常量临时改成 300ms 实测过
  `creating → 超时 → retry → 超时 → 抛错弹窗`，迟到的两个客户端都被 `shutdown()` 掉
  （日志里成对出现 `Disconnected from TTS engine`），进程不崩、不再有
  `stop failed: not bound to TTS engine` 的误导性日志。
- **模拟器没有音频设备**（启动参数 `-no-audio`），句子几乎瞬间完成，所以「连续播放 5 分钟不中断」
  是用「连续跑完整章（71 句）+ 自动进下一章 + 期间通知正文持续推进」代替的；
  **真机要再听一遍**——这轮验的是队列、回调、跨章链路不中断，不是听感与功耗。

**关键设计（改之前先读）**：

1. **一次只往引擎塞一句**（`QUEUE_FLUSH` + `onDone` 前进）。不是图省事：队列里积压 5 句时，
   `stop()` 与「当前句」的对应关系立刻变模糊，暂停/上一句都会错位。句间空隙 20–80ms，听感反而更像人。
2. **朗读单元 = 一句**。正文导入时已经一句话一段（M2 补丁 5），`SentenceIndex` 只是兜底：
   老书没切过的整段、以及超长句（引擎单次输入约 4000 字符上限，太长会直接失败不出声）。
3. **高亮区间是段内偏移**：`onRangeStart` 给的是「片段内偏移」，加 `chunkStart` 才是段内偏移；
   `ReaderAdapter.setHighlight(paraId, start, end)` 只认段内偏移。
4. **高亮直接改 span，不 `notifyItemChanged`**：逐词回调每秒 4–5 次，整项重绑会把每次高亮都变成一次 measure/layout。
5. **高亮配色必须用主题对**：底色 `colorPrimaryContainer` + 前景 `colorOnPrimaryContainer`。
   **别用 `highlight_word`**（#F3D98C 亮黄）：深色主题下亮底浅字根本读不清——这是实测截图才发现的。
6. **状态只在主线程改**；读库走 controller 自己的单线程（`tts-db`）。**不能借 `App.io()`**：
   它被翻译请求占着时有 20 秒网络超时，章节切换会跟着卡半分钟。
7. **离开阅读器不停播**：朗读归前台服务，返回书架 / 锁屏继续；回到 App 时 `setUiListener` 立刻回一份
   `Snapshot`，章节、高亮、按钮都靠它接上（转屏重建走的也是这条路径）。
8. **从最近任务划掉 App 就停**（`onTaskRemoved`）：没人看着的朗读只是耗电。

**这次踩到并修掉的坑（别再踩）**：

- **通知最多摆 3 个按钮**（API 36 实测，第 4 个直接不渲染）。原本放了「停止」，界面上根本没有 →
  改成 `setOngoing(false)` + `setDeleteIntent(ACTION_STOP)`：**划掉通知即停止**。
  想加第 4 个按钮得自己画 RemoteViews 或用 MediaStyle，对一个阅读器不值当。
- **没做 MediaSession**（要引 `androidx.media`）：蓝牙耳机 / 车机的播放键收不到，通知栏也不是媒体卡样式。
  这是**已知缺口，不是 bug**，见 R14。
- **引擎初始化失败别在打开阅读器时就弹框**：服务是 `BIND_AUTO_CREATE` 起来的，用户只是看书、没点朗读。
  失败只记 code，等真有播放请求再报（`reportPendingError`）。
- **init 失败后要能重试**：用户可能刚装完引擎回来。`start()` 里把 init 置回 FRESH 并 `shutdownEngine()` 重建，
  别一条道走到「永远失败」。
- **连引擎都没起来时状态一直是 IDLE**，不会走 `onState` → 前台通知得在 `onError` 里撤掉（`dropForeground`），
  否则会挂一条「准备朗读」永不消失。
- **迟到的 `onDone` 必须用 utteranceId 过滤**：`stop()` / `pause()` 之后引擎仍可能补一个回调，
  不过滤就会「已经暂停了，队列却自己往前跳一句」。
- **解绑要按「请求过绑定」判断，不能按「已连上」**：`bindService` 返回 true、连接回调还没到就 `onStop` 的话，
  只按「已连上」判断会漏掉 `unbindService`，绑定与 ServiceConnection 就永久挂在进程里。
- **`stop()` / `shutdown()` 在客户端没连上时会各打一条 `... failed: not bound to TTS engine` 的 W**，
  这不是故障，是收尾噪音（`TextToSpeech` 源码里只有 `mServiceConnection == null` 才走到那两句）。
  `shutdown()` 对「还在连」的连接有单独分支（静默 disconnect 后 return），所以只在
  READY（`stop()`+`shutdown()`）和 CREATING（只 `shutdown()`）时调；FAILED / FRESH 调了只会刷误导日志。
- **引擎初始化必须有看门狗 + 重试一次**：ColorOS 会把 TTS 引擎进程冻住
  （实测日志 `Async freezing 23854 com.oplus.ttsaccessibilityengine` → `am_freeze`），
  bind 可能迟迟不回 `onInit`；没有超时的话界面永远停在「准备朗读」还不报错。
- **重试时旧的 `onInit` 会迟到**：取消旧连接后框架仍会回调那个监听器。用
  `TextToSpeech[] holder` 抓住「这一轮」的实例，回调里 `engine != tts` 就丢弃并顺手
  `engine.shutdown()`——框架的异步连接可能事后才建立（实测 API 36 会打
  `Connected to TTS engine`），不关就每超时一次漏一条 binder 连接，还会把新引擎标成失败。
- **日志里的 `default=null`** 表示系统没有默认 TTS 引擎（`TextToSpeech.getDefaultEngine()` 为空），
  通常就是「这台机器没装/没启用任何引擎」；`engine ready, pkg=… lang=1` 则说明引擎与英文都对上了。
- **国产 ROM 的默认引擎常常只有中文**（OPPO 的 `com.oplus.ttsaccessibilityengine` 实测）：
  `setLanguage(Locale.US)` 返回 `LANG_MISSING_DATA(-1)` / `LANG_NOT_SUPPORTED(-2)` 时
  报「没有英语语音包」，跟「没有引擎」是两条不同的提示。日志里 `engine ready, pkg=… lang=N`
  能一眼看出用的是哪家、支不支持英文。
- **逐词偏移要夹**：个别引擎给出的 `start/end` 会越界，回写前 `Math.min(text.length())` 夹一下。
- **跟读自动滚动会改掉断点**：滚到听到的那一句本来就是想要的，但**用户正拖着列表时不能抢**
  （`SCROLL_STATE_DRAGGING` 直接跳过）。

**已知取舍（要改先看这里）**：

- 暂停 / 上一句 / 下一句都从**当前句开头**重念——TTS 引擎没有「句内 seek」，想要词级续播得自己切片重排。
- 语速只对**之后念的句子**生效，当前这句不会中途变速（引擎没这个能力）。
- 没有音频焦点管理（来电 / 其他播放器不会自动让路），`setAudioAttributes(USAGE_MEDIA)` 至少保证走媒体音量。

### M2 体验升级（目录 / 设置 / 翻页 / 亮度 / 日夜）

> **注意（2026-10-21）**：左右翻页整套已经删掉，只保留上下滚动。
> 下面清单里 `TapZone` / `PageSwipe` / `PageTurn` / `PageIndex` / `ParagraphGap` 五个文件**已删除**，
> 保留这段是为了记住当时为什么那么设计（以及踩过什么坑）。现状见「M2 补丁 5」。

- [x] `reader/ReaderPrefs.java`：字号档位、行距、**段距**、**字距**、**首行缩进**、主题、亮度、段按钮开关，全存私有 SP
- [x] `reader/TapZone.java`：左/中/右三分区纯函数（**词优先**：命中单词先查词，空白才分区）
- [x] `reader/PageSwipe.java`：横滑翻页的纯函数判定（横向够长且压过纵向才算）
- [x] `reader/PageTurn.java`：左右翻页的两层位移计算（保证新旧两页首尾相接）
- [x] **音量键翻页**：`VOLUME_DOWN` = 下一页、`VOLUME_UP` = 上一页（两种翻页模式都生效）
- [x] `reader/PageIndex.java`：按屏翻页的页码计算——整章摊平成绝对高度轴，每屏高切一刀
- [x] `reader/TocAdapter.java` + `item_toc.xml`：目录抽屉，当前章青色高亮
- [x] `activity_reader_root.xml`：`DrawerLayout` 包阅读页，左侧目录抽屉
- [x] `activity_reader.xml`：正文铺满；顶部栏 + 底部栏默认在屏幕外，点中间才滑入
- [x] `view_reader_settings.xml`：字号 / 行距 / **段距** / **字距** / **首行缩进** / 主题 / 翻页 / 段按钮 / 亮度入口，整块可滚动
- [x] 沉浸式：菜单默认隐藏，滚动或点空白自动收起；切主题后重建仍保持满屏
- [x] 亮度：`WindowManager.LayoutParams.screenBrightness` + 跟随系统开关（不申请权限）
- [x] 依赖：新增 `androidx.drawerlayout:1.1.1`（本地缓存已有，仍可离线构建）
- [x] `reader/PageSwipe.java`：横滑翻页的纯函数判定（横向够长且压过纵向才算）
- [x] 测试：`TapZoneTest`（4 例）、`PageIndexTest`（11 例）、`PageSwipeTest`（6 例）

**验收结果**：单测 31/31、仪器测试 11/11 全绿；模拟器逐项实测：
菜单默认看不见 → 点屏幕中间滑出顶部栏（返回/标题/章节位置）与底部栏（目录/上一章/下一章/设置）；
点「目录」左侧抽屉列出全部章节、当前章青色高亮；
「按屏翻页」下点右侧从第 1 章进第 2 章、点左侧退回第 1 章，左下角显示「第 N 章 · x/y」；
点在单词 `It` 上仍弹词卡（`It /ɪt/ pron. 它`），**不翻页**——「词优先」成立；
字号 18sp → 20sp 实时生效、行距档位同步显示；主题切深色后整页变黑、菜单自动收起；
亮度浮层有滑杆与「跟随系统亮度」开关。

**体验升级期间踩到并修掉的坑（后人别再踩）**：
- 段落的 `TextView` 只覆盖正文那几行，屏幕下半屏空白**没有任何 View 消费触摸**，
  点上去直接落空、菜单呼不出来。要在 `RecyclerView` 上另挂一层手势接住「没打到子 View」的点击
  （`hitChild` 自己算），并且 `onTouch` 仍返回 false 保滚动。
- 菜单开着时点空白**必须先收菜单再考虑翻页**。一开始只在连续滚动模式做这个判断，
  翻页模式下就成了「想关菜单却翻走一页」。
- `PageIndex` 第一版用「已用高度」跨迭代累加，短段被整屏跳过、长段跨屏偏移错。
  改成把整章摊平成**绝对高度轴**、每屏切一刀再二分定位，状态没了，bug 也没了。
  单测里 `overflowsToSecondScreen` 一开始是我期望值写错（不是代码错），
  断言要按「刀口落在哪一段的段内偏移」来写。
- Material3 的 `OutlinedButton` 最小宽度与内边距会把 48dp 方框里的 `A-`/`+` 挤没，
  必须显式给 `minWidth=0dp` + `paddingHorizontal=0dp` + 足够宽度。
- 设置面板内容一多就被屏幕底部裁掉（亮度入口看不见），套一层 `ScrollView` 即可。
- **不要用 PowerShell 正则批量改 XML**：`Set-Content` 会把文件写成 GBK，之后 read/write 工具报
  `invalid UTF-8 text`，只能删掉重建。改 XML 用 edit 工具。
- 仪器测试在 `--offline` 下可能缺 UTP 的传递依赖（如 `j2objc-annotations`），
  去掉 `--offline` 让它走阿里云镜像补一次即可。

### M2 补丁 5：删掉左右翻页，改成「只上下滚动 + 一句话一段」（2026-10-21）

**决定**：左右翻页（按屏翻页 / 点两侧 / 横滑 / 音量键翻页）整套删掉，只留上下滚动。
同时**一句话一个段落**——原来的整段按 `.!?` 切开，每句独立成段。

**删掉的东西**（约 1000 行，删比写省心）：

| 删 | 原因 |
|---|---|
| `reader/PageIndex.java` + `PageIndexTest` | 分页算法的全部意义就是「按屏切」，没有屏了就没用了 |
| `reader/PageTurn.java` + 测试 | 左右翻页的两层位图位移 |
| `reader/PageSwipe.java` + 测试 | 横滑翻页判定 |
| `reader/ParagraphGap.java` + 测试 | 原本只为「点缝不放去翻页」而存在；现在点缝和点空白一样只呼菜单 |
| `reader/TapZone.java` + 测试 | 左中右三分区只为翻页，留着反而误触 |
| `activity_reader.xml` 的 `page_snapshot` | 旧页快照只在左右翻页时用 |
| `view_reader_settings.xml` 的「翻页」开关 + 3 条 strings | 设置里不再有翻页选项 |
| `ReaderPrefs` 的 `pageMode/MODE_PAGE` | 一个值都不留，避免留下永假的开关 |

留下的是 `ReaderActivity.scrollByScreen(dir)`：音量键和「点空白」共用，滚一屏；到头顺延到相邻章节。

**一句话一段**：新增 `parse/Sentences.java`（纯函数），在 `BookParser.parse` 里对**两种格式统一**切
（TXT/EPUB 各自不用管），`Sentences.splitBook(book)` 把每章段落列表换成句子列表。

- 断句规则：`. ! ?` 之后**必须跟空白**才算（避开 `3.14`、`www.example.com`）；中文 `。！？` 不需要空白。
- **代价（已知，接受）**：`Mr. Smith` 会被切成两句。要修得挂缩写表，对一个阅读器不值当；
  真要修在 `Sentences.BOUNDARY` 前面加负向断言即可，调用方一行都不用动。
- 「段」按钮、译文、词偏移全部跟着段走，所以**翻译粒度自动变成一句一译**，代码零改动。
- **旧书不受影响**：拆分发生在导入时，已在库里的书仍是整段。要看效果得删掉重导入。

**顺手确认**：`measureChildWithMargins` 的 UNSPECIFIED 覆写**必须留着**——
它修的是「长段落被 RecyclerView 压成一屏高、后面看不见」，跟翻页无关，滚动模式下同样会犯。
`calculateItemDecorationsForChild` 也一并留着（省了它 ItemDecoration 会静默失效）。

**实测（模拟器 Pixel_9_Pro）**：
空白处点击只开关菜单（点左侧不再翻页）、句间缝隙同样呼菜单；
音量键上下各滚一屏（147 段的书 1→8→19）；点词仍弹词卡（`Ruskin / n. 罗斯金（姓氏）`）；
重新导入 `samples/sample.txt` 后得到两段 `It was the best of times.` / `It was the worst of times.`；
设置面板只剩字号/行距/段距/字距/缩进/主题/段按钮/亮度/翻译服务。单测 45/45。

### M2 补丁 3：按屏翻页改成真·左右翻页 + 音量键翻页（2026-10-20）

**症状**：两种翻页模式其实都是纵向滚动——「按屏翻页」只是用
`scrollToPositionWithOffset` 瞬间跳一屏，视觉上和连续滚动没区别，用户要的是横向翻页。

**改法**：不把整章切成 N 个 View（那要把点词偏移、每段「段」按钮、内联译文全部重映射，
HANDOFF D2 早就算过这笔账，代价远大于收益）。改用**两层位图位移**：

1. `activity_reader.xml` 加一个 `page_snapshot` 的 `ImageView`，与列表完全同尺寸；
2. 翻页时先把当前画面 `list.draw(canvas)` 拍成位图盖上去（旧页），列表瞬间跳到目标屏（新页）；
3. 两页各自 `translationX` 滑到终点，`PageTurn` 纯函数保证**始终首尾相接**。

于是 `p.text`、词偏移、内联译文、按钮全都留在原来那个 `RecyclerView` 里，**一行都不用重映射**。

**新增音量键翻页**：`onKeyDown` 收 `VOLUME_UP/PREV`、`VOLUME_DOWN/NEXT`，
`repeatCount > 0` 不连翻（长按不滑过整章），词卡开着时先关卡片。返回 `true` 吃掉事件。

**按屏翻页模式下禁止纵向滚动**：页是按「一屏」切出来的，手指一拖就停在半屏，
页码与屏表立刻对不上，左右翻页也会错位。做法是在 `LinearLayoutManager` 上覆写
`canScrollVertically()`，翻页模式直接返回 `false` —— RecyclerView 的拖动与惯性滑动
都会先问这一句，而 `scrollToPositionWithOffset` 走布局、不经过这里，所以翻页定位照旧。
切模式时要 `list.stopScroll()` + `requestLayout()`，否则还在飞的那记惯性滑动与
旧的滚动能力会残留。

**这次踩到的坑（都在 `ReaderActivity` 里，以后别重犯）**：

- **`currentSlot()` 与 `scrollToPositionWithOffset` 的坐标约定写反了**，这是最花时间的一个。
  定位写的是 `-offsetPx - paddingTop`（即 `getTop() = paddingTop - offsetPx`），
  读回却写成 `getTop() - paddingTop`，再被 `Math.max(0, …)` 钳成 0 →
  **永远算出第 0 屏，翻页后页码不动，再翻又回原地**。三处（`currentSlot`、`updateStatus`、
  `turnTo`）现在统一成同一套约定，并写进 `currentSlot` 的注释里。
- `snapshotPage()` **必须先 `canvas.drawColor(底色)` 再 `list.draw()`**。列表背景是透明的，
  只画列表得到的位图字间是透明的，翻页时透出下层、闪鬼影。
- 别加 `bringToFront` 调 Z 序：`PageTurn` 已保证两页**永不重叠**（单元测试
  `theTwoLayersStayAdjacent` 逐 0.1 步长验证），层级无关紧要，加了反而触发多余重排。
- 跨章方向：往后翻过章尾进下一章要落**章首**，往前翻过章首进上一章要落**章尾**
  （`openChapter(idx, toStart)`），否则往回翻时总是跳到章首，读不下去。

**实测（模拟器，按屏翻页模式）**：音量下 1→2→3、音量上 3→2；
点右侧空白 2→3、点左侧 3→2；右向左滑 2→3、左向右滑 3→2；
动画日志确认两页真的是横向错开（旧页 0→-719、新页 1280→561，首尾相接）而非瞬跳；
切回连续滚动后音量键走纵向滚动、不产生横向动画。单测 70/70（新增 `PageTurnTest` 6 例）。

**纵向拖动的实测**（翻页模式）：从段间空白起手向上拖 1800px、向下拖 1400px，
页码与首段 `getTop()` 都**一字未变**（1/49、228）；切回连续滚动模式同样拖动，
首段 `getTop()` 228→156，滚动正常。两种模式互不干扰。

### M2 补丁 4：修「长段落时翻页错乱 / 一页露不全」（2026-10-21）

**症状**：按屏翻页时，只有**长段落（一段占满整屏）**的章节会错——页码跳号、
翻到一半就跳走；正文底部被裁掉看不到。

**根因（两处，缺一不可）**：

1. **RecyclerView 默认把子 View 高度量成「最多一屏」**：`LinearLayoutManager`
   用 `getChildMeasureSpec(...)`，`wrap_content` 的 item 拿到的是 `AT_MOST(一屏高)`。
   于是比一屏长的段落**只摆一屏高，后面的行被直接裁掉、屏幕上看不见**，
   而屏表是按「真实段落高度」算的 —— 两边对不上，翻到「长段落中间那一页」时
   目标位置超过了那个被压扁的 View，`scrollToPositionWithOffset` 只能落到下一段，
   页码就跳过去了。修法：覆写 `LinearLayoutManager.measureChildWithMargins`，
   高度显式传 `UNSPECIFIED`。
2. **屏表用的是估算高度**：`rebuildSlots` 只量「已经画出来的 View」，屏幕外的段落
   一律按 `viewport/3` 估，而且翻页后从不重测。长段落一多，估得越离谱。
   修法：`ReaderAdapter.measureHeights` 用**同一套 bind 逻辑**在屏幕外把每段量一遍
   （`inflate` 一个离屏 item → `bindContent` → `measure(EXACTLY 宽, UNSPECIFIED 高)`），
   再把实测高度喂给 `PageIndex.paginate`。

顺带把分页改成**按段落对齐**：一屏装得下就整段整段装，只有单段高过一屏时才段内切
（不切的话那一段后半截在翻页模式下永远看不到）。

**其它两处必须一起改，否则现象仍在**：

- **末屏要垫一屏底部留白**（`ItemDecoration`，只在按屏翻页模式生效）。
  正文末尾不足一屏时，最大滚动量不够，最后一屏的起点顶不到屏幕顶部 —— 翻过去页码不动。
- **读回位置的偏移必须是 `-getTop()`**，不是 `paddingTop - getTop()`。
  `scrollToPositionWithOffset(pos, o)` 把 item 顶边摆在 `paddingTop + o`，
  而 `turnTo` 传的是 `-offsetPx - paddingTop`，所以顶边落在 `-offsetPx`。
  写成 `paddingTop - getTop()` 会多算一个 paddingTop，翻到段落中间那几屏时页码往前跳一格。

**覆写 `measureChildWithMargins` 时别忘了 `calculateItemDecorationsForChild(child, insets)`**：
默认实现顺带算好了装饰边距，省掉它 `ItemDecoration` 会**静默失效**（末屏留白不生效，
而且没有任何报错）。宽度也要扣掉 `insets.left/right`。

**实测（模拟器 Pixel_9_Pro，按屏翻页，第 37 章 16 屏）**：
逐页音量下 1→2→…→16 严格 +1 不跳号；每屏内容都落在视口内（`top=156 bot=2784`）；
长段落那几屏 `getTop()` 分别是 0 / -2484（正是段内偏移，`measuredH=actualH=4029`）；
16/16 翻下一屏进第 38 章 1/10；回翻 15→14→13 对称；单测 71/71。

**以后别再踩**：`viewport/3` 这种「估高」一旦喂给分页，长内容下必然错位；
段落高度必须实测，且量高与上屏必须共用同一套绑定代码（`bindContent`），否则样式一改就又对不上。

### M2 补丁 2：设置里加「段距 / 字距 / 首行缩进」（2026-10-20）

三项都走已有的「档位表 + `bumpXxx(±1)` + `syncSettingLabels`」模式，没有新机制：

| 项 | 档位 | 落地方式 |
|---|---|---|
| 段距 | `PARA_SPACINGS = {8,12,16,24,32}` dp | `itemView` 的 `layout_marginBottom`，运行时覆盖布局里的 16dp |
| 字距 | `LETTER_SPACINGS = {0, .02, .04, .08}` em | `TextView.setLetterSpacing`（0 = 关） |
| 首行缩进 | `INDENTS = {0,1,2}` 字 | `LeadingMarginSpan.Standard(chars × 字宽)`，**不改 `p.text`** |

三条必须记住的约束：

1. **`PARA_SPACINGS` 最小档不能是 0**。段距就是「段与段之间那道缝」，也就是长段落
   唯一能呼出菜单的点击区（见上一节补丁）。归零 = 功能消失且不会报错。
   `ReaderPrefsLadderTest.paraSpacingNeverZero` 钉住了它。
2. **缩进必须用 span，绝不许往 `p.text` 里塞空格/全角空格**。那会同时污染
   词偏移表、TTS 朗读和复制，正是 D1 要防的。实测缩进 2 字后点词仍命中（点了 `is`，弹 `is`）。
3. **段距要同步给 `PageIndex`**。`rebuildSlots` 原来读的是 `R.dimen.reader_para_spacing`，
   改成读 `prefs.paraSpacing()`——否则改段距后按屏翻页的屏高还是老值，翻页会越切越偏。

实测（模拟器）：面板五行排版正常，标签显示 `18 sp / 1.5 / 16 dp / 关 / 关`；
缩进 +2 → 首行明显缩进且值变 `2 字`；段距降到 `8 dp` → item 间距由 48px 变 24px，
**点这条更窄的缝仍能呼出菜单**；字距 +2 → `letter_spacing=0.04` 落盘，正文变松。
单测 64/64 全绿（新增 `ReaderPrefsLadderTest` 3 例）。

### M2 补丁：段与段之间留一道缝当「菜单键」（2026-10-20）

**症状**：段落很长时（一段占满整屏），正文里每一点几乎都压在一个单词上，
按「词优先」全被判成点词，**没有任何位置能呼出菜单**。用户原话：「要划半天才找得到一个空白」。

**根因**：`item_paragraph.xml` 里**根本没有段间距**——`reader_para_spacing`(16dp) 只被
`ReaderActivity.rebuildSlots` 拿去算屏高，布局上从来没真用上，所以 item 是首尾相接的。
`hitContent` 按子 View 判空白是对的，但子 View 铺满整屏时它也找不到空隙。

**改法**（三处，最少改动）：
1. `item_paragraph.xml` 根布局加 `android:layout_marginBottom="@dimen/reader_para_spacing"`——
   这条缝不属于任何一段，`layout_margin` 不被子 View 命中。
2. `reader/ParagraphGap.isGap(tops, bottoms, y)`：纯函数，纵坐标落在两段之间且不属于任何一段才算缝。
   **不能用「没打中正文」代替**：段内左右留白也不在正文上，那是留给分区翻页的。
3. `ReaderActivity.inParagraphGap` 调它；`ACTION_DOWN` 时记 `gapTap`，抬手走 `onGapTap()`——
   **只开关菜单，不参与左中右分区**（否则点缝的左边会翻走一页，想找菜单反而翻丢位置）；
   同时 `gapTap` 时跳过横滑翻页判定。

**验证**（模拟器 Pixel_9_Pro 实测）：点缝（含左侧 1/3 的 x=100）→ 上下菜单滑出；
再点同一条缝 → 收起；点缝期间页面滚动位置不变（item bounds 前后一致）；
点正文里的词仍弹词卡（`eBook` → 词典无此词），没被这条缝抢走。
单测 61/61 全绿（新增 `ParagraphGapTest` 5 例）。

**别退回去**：这条缝若被挪进子 View 或改成 `padding`，`hitContent` 就会把它算成内容，
bug 原样复活。`padding` 尤其不行——它在 item 矩形**内部**。

**M2 第一轮踩到并修掉的坑（同样别再踩）**：- `LlmClient`/`TransQueue` 不存在时，「段」按钮不能静默：现在给「未配置」提示，M3 换掉即可。
- `DictSource` 里 `word_cache` 在**业务库** `Db`，不在 `assets/dict.db`（后者只有 `dict`/`form`）。
  一开始写错，`no such table: word_cache` 在仪器测试里当场暴露。
- ECDICT 的 `frq`/`bnc` 只覆盖约 5.7 万词，`well-known` 这类词的 `frq`/`bnc` 都是 0；
  只按词频裁剪会把它们随机截掉。必须把 `tag`/`oxford`/`collins` 一起纳入排序
  （`well-known` 靠 `tag='cet4 cet6'` 进入 8 万边界）。
- `getOffsetForHorizontal` 返回「最近的字符边界」而不是「点到了哪个字符」：
  点 `t` 的右半边会返回它后面那个位置，导致点在词内部也判成空白。
  要用 `getPrimaryHorizontal(offset) > x` 往左纠正一格；空格/标点仍不命中。
- `GestureDetector` 长按后抬手还会补一个 `onSingleTapUp`，必须用一个标志位抑制，
  否则长按加生词本会连带弹一次词卡。
- `RecyclerView.ViewHolder` 里的回调匿名类若改成 `static`，就访问不到外部类的
  `listener`/`items`，要把 adapter 实例显式传进去。
- ~~**adb `input tap` 在模拟器上必被判为 `onLongPress`**~~：这条观察是对的，但归因错了 ——
  真凶是 M3 补丁 3 那个「正文当不上触摸目标、只收到 DOWN」的事件分发 bug，2026-10-21 已修。
  现在 adb 注入的单击与手动点走同一条路径。
- `adb exec-out screencap` 经 PowerShell 重定向会毁掉 PNG（老坑的另一个面孔）；
  用 `adb shell screencap -p /sdcard/x.png` + `adb pull` 才稳。

### M1 已完成清单

- [x] `data/Db.java`：6 张表建表，单例，`onUpgrade` 留好递增位
- [x] `data/BookDao.java`：整本事务入库（预编译语句批量插段落）、书架列表、**删书连带删私有目录文件**
- [x] `parse/ParsedBook.java`：解析层中间结构（纯 Java，可在 JVM 单测里构造）
- [x] `parse/TxtParser.java`：BOM→严格 UTF-8→GB18030 探测、`Chapter N`/`第N章`/分隔线切章、空行切段
- [x] `parse/XhtmlBlockParser.java`：文本级清洗（命名实体→数字实体、裸 `br`/`img` 补自闭合）→ `XmlPullParser` 抽块；失败降级剥标签
- [x] `parse/EpubParser.java`：container.xml → OPF → manifest/spine 顺序 → 章节；EPUB2 `meta name="cover"` 与 EPUB3 `properties="cover-image"` 两种封面都认
- [x] `parse/BookParser.java`：按扩展名分派
- [x] `ui/ImportHelper.java`：SAF → 拷贝 `filesDir/books/{uuid}.ext` → 解析 → 入库，失败删半成品文件
- [x] `ui/MainActivity.java`：接 `BookDao`、导入/长按删除、空态切换
- [x] `ui/LibraryAdapter.java`：`replace()` 刷列表、封面缩略图、**无作者时收起该行**
- [x] `App.java`：共享单线程 IO 线程池
- [x] 样本 `samples/{sample.txt, sample_epub2.epub, sample_epub3.epub}`（EPUB3 里故意塞了一章不合法 XHTML）
- [x] 测试：`TxtParserTest`（5 例）、`ImportParsersTest`（3 例，跑真样本）、`BookDaoTest`（3 例）


### 已解决的构建问题

1. **JDK**：本机只有 JDK 24（`D:\tools\java`），但 `C:\Users\y\.jdks\corretto-17-jdk` 已有 Corretto 17。**构建前必须设 `JAVA_HOME` 指向它**，否则走 JDK 24 未经验证。
2. **Gradle home 可写性**：早期在受限沙箱下报 `gradle-8.14-bin.zip.lck 拒绝访问`，授权放开后已恢复正常，构建无需额外处理。
3. **本地依赖缓存里没有** room、okhttp、gson、jsoup、media3、work、datastore、security。
   → 首次联网才拉得到。**本方案刻意做到 v1 新增 0 个第三方依赖**（目前只多声明了本地已有的 recyclerview），可离线构建。

---

## 1. 技术选型与决策

原则：能用平台自带的就不引依赖。下面每条都写明「为什么不是更常见的那个方案」。

| 领域 | 选择 | 理由 / 被否决的方案 |
|---|---|---|
| 语言 + UI | **Java + View 体系** | 现有工程就是这套，零迁移成本。否决 Kotlin+Compose：重写骨架对 MVP 没收益。 |
| 包名 | **`com.englishread.app`** | M0 已定，勿改。 |
| 导入 | SAF `ACTION_OPEN_DOCUMENT` | 免 `READ_EXTERNAL_STORAGE` 权限、天然兼容分区存储。 |
| EPUB 解包 | `java.util.zip` + `XmlPullParser` | 平台自带。否决 jsoup（未缓存，且 Epub 是 well-formed XML 为主）。 |
| 阅读渲染 | RecyclerView + **每段一个 TextView** | 段落级译文要内联展开，一段一 View 最直接。否决整章一整块大 TextView：无法增量翻译与复用。 |
| 点击命中 | **自己算**（`Layout.getOffsetForHorizontal`） | 用 `ClickableSpan + LinkMovementMethod` 会吞掉滚动/长按。自建约 40 行，一次到位。 |
| 翻译入口 | 段落下沿一个**独立的小号「段」按钮**（非文本），48dp `TouchDelegate` | 否决「点行尾留白 = 翻译」：靠 `gutterPx` 阈值猜意图，误触多、还要维护魔法像素值。否决「把段字拼进 Spannable」：污染 `paragraph.text`，TTS 会念出来、复制会带上，还要额外维护字符区间排除逻辑。独立 View 让正文永远是纯原文。 |
| 段落文本纯净度 | **`paragraph.text` 只含原文**，UI 装饰一律走独立 View | 一旦把「段」拼进文本，TTS 朗读、复制本段、句子偏移表三处都要记得减掉它——漏一处就是线上 bug。 |
| 分页 | v1 **连续滚动**，不做仿真翻页 | 翻页 + 跨页 span + 弹窗定位三者叠加是复杂度黑洞。v2 再说。 |
| 单词释义 | **ECDICT 裁剪版 SQLite**（assets 内置）+ 预生成 `变形→原形` 反查表 | 离线、零成本、毫秒级。否决「点词走网络」：延迟 + 成本 + 断网即废。 |
| 段落翻译 | **OpenAI 兼容 `/v1/chat/completions`**，`HttpURLConnection` + `org.json` | 平台自带 HTTP 和 JSON，不需要 okhttp/gson。译文质量是段落级的关键，本地 ML Kit 做不到「像人话」。国内多家（DeepSeek / 千问 / GLM / Kimi / 硅基流动）都兼容这个协议，换厂商只改三件套。 |
| 段落译文缓存 | 直接存 `paragraph` 表字段 | 一次生成永久复用，这是控成本的命门。 |
| 听书 | 系统 `TextToSpeech` + `UtteranceProgressListener.onRangeStart` | `onRangeStart` 提供**词级字符区间**，白送的逐词高亮，不用自己做时间轴。minSdk 28 ✓。实测 Google TTS 正常回调；不实现的引擎退化为整句高亮。 |
| 后台播放 | `ForegroundService`（`mediaPlayback` 类型）+ 自带通知 | Android 8+ 必须有通知才不会被杀。**没做 MediaSession / MediaStyle**（要引 androidx.media），代价是蓝牙/车机播放键收不到，见 R14。 |
| 持久化 | **裸 SQLite（`SQLiteOpenHelper`）** + 手写 DAO | 只有 6 张表。否决 Room：需加 kapt 插件 + 2 个依赖，本地还没缓存，收益不抵成本。 |
| 密钥存储 | 私有 `SharedPreferences` | 私有目录本身受系统沙箱保护。`androidx.security` 未缓存，v1 不引。 |
| ~~PDF~~ | 不做 | 无文字层就无法重排、无法点词。 |
| ~~ML Kit 翻译~~ | 不做 | 段落译文生硬，与「辅助学习」定位冲突。 |

**本方案的依赖账单：0 个新第三方依赖**（M1 只用到已在缓存里的 `androidx.recyclerview`；M2 不再声明 `viewpager2`——
v1 是连续滚动，不需要翻页容器）。

---

## 2. 三个核心机制（难点都在这）

### 2.1 点词命中 vs 「段」按钮（整段译文入口）

**翻译入口不靠「猜段落末尾」，也绝不写进段落文字**：每段正文下方右对齐放一个小号「段」按钮，点它展开整段译文。

- **段是独立 View，不是字符串**。段落 `TextView` 的 `text` 里永远只有原文本身——
  TTS 不会念出「段」、复制本段不会带上它、句子切分与偏移表也不需要减字符。
  这是硬约束：`paragraph.text` 从入库到显示全程不被污染。
- 视觉：`11sp` 左右（明显小于正文）、次要色（`text_secondary`）、右对齐、段落下沿 `space_xs` 间距；
  带 `ripple` 背景，`contentDescription` = 「翻译本段」。
  小到像页面上的装订标记，**不会和章节标题混淆**——这就是「与正常章节区分开」的要求。
- 触摸区用 `TouchDelegate` 扩到 **48dp**，视觉小但好点（无障碍最小点击尺寸）。
- 只给**正文段落**加；章节标题行不加。设置里可整体隐藏（隐藏后就没有翻译入口，不是「点空白」）。
- 展开的译文是段落下方一个默认 `GONE` 的 `TextView`（内联，不是弹窗）；
  再点一次收起。同一段的译文只请求一次，之后开关只是显隐。

这样手势互不干扰：

| 操作 | 结果 |
|---|---|
| 点段落文字里的词 | 查词卡（`ReaderHitTest`） |
| 点「段」按钮 | 展开/收起该段译文（按钮自己的 `OnClickListener`） |
| 点段落里的空白/标点/行尾 | 什么都不做 |

`onLongPress` 仍只做「加入生词本 + 朗读该词」，与「段」无关。

- 手势识别用 `GestureDetector`，`onTouch` **返回 false**，滚动仍交给 RecyclerView。
- `onSingleTapUp` → 命中测试，**只判词**：

```java
// 只有两种结果：命中某个词，或什么都不做
static Hit resolve(Layout layout, float x, float y, int[] tokenStarts, int[] tokenEnds) {
    if (y < 0 || y > layout.getHeight()) return Hit.none();
    int line = layout.getLineForVertical((int) y);
    int off = layout.getOffsetForHorizontal(line, (int) x);
    int i = binarySearchFloor(tokenStarts, off);      // 含 off 的 token
    if (i >= 0 && off < tokenEnds[i]) return Hit.word(i);
    return Hit.none();                                // 空白/标点/行尾：不猜
}
```

> 相比最初方案，**`gutterPx` 与「行尾右侧留白 → 段落翻译」整条分支删掉了**；
> 相比「把段字拼进 Spannable」的方案，**`markerStart/markerEnd` 区间判定也不用写了**。
> 命中逻辑退回「只判词」，是这三个版本里最小的一个。

段落 TextView 必须 `setMovementMethod(null)`、`setTextIsSelectable(false)`，否则它自己的滚动和选区会抢事件。

- 单词卡：`PopupWindow` 锚定到词的矩形
  （`getLineForOffset` + `getPrimaryHorizontal` + `getLineTop/Bottom`，再加 TextView 在屏幕上的位置），
  下方放不下时翻到上方。内容：词 + 音标 + 释义 + 朗读 + 加生词本。

### 2.2 段落译文的生成与控成本

不预先生成整本书（贵且慢），采用「**读哪译哪**」。**没有做后台预取**——预取等于用户没点也在花钱。

1. 点某段的「段」按钮 → **只把这一句**送进队列（单线程 IO 池，复用 `App.io()`）。
   不捎带后面几段：多译的段会连带把下面几句的译文一起弹出来，用户看到的不是自己点的那一句，
   而且等于替没点的句子付钱（见「M3 补丁 2」）。
2. 批量翻译：`TransProtocol.buildUser` 拼「编号|原文」逐行，一次请求 3–5 段，
   用固定分隔符协议返回，省 token 也省一次 JSON 解析失败的机会。
3. 结果按编号切回：命中写 `paragraph.trans` 并置 `trans_status=2`；
   **再次点击永不重复请求**（`TransQueue.needsWork` 先判缓存，缓存的段连队列都不进）。
4. 模型漏段 → 只补发缺的那几段（不是整批重发，整批重发等于付两次钱）；
   仍失败则整批尝试各自重试 1 次后置 `trans_status=3`，界面展示「翻译失败，点击重试」。
5. 提示词要求：只输出译文、保留编号、人名/术语一致、不解释、不扩写。
6. 退出阅读器 → `TransQueue.cancel()`，飞行中的请求结果不再写回。

```text
你是英译中翻译器。把下面编号的段落翻译成简体中文。
要求：保留原段落编号；只输出「编号|译文」行；术语与专有名词前后一致；不添加解释，不扩写，不合并段落。
1|It was the best of times, it was the worst of times.
2|...
```

**宽松解析**（`TransProtocol.parseReply`）认三种情况，都别当成 bug 改严：编号乱序 / 全角竖线「｜」、
模型加的寒暄行（无编号，忽略）、译文里的续行（接到上一段后面）。漏段留 `null`——
用空串冒充译文会永久缓存一个空框，那段再也不会重试。

进程被杀就停，下次打开章节重新点就续——不做 WorkManager（见风险 R5）。

### 2.3 听书逐词高亮

以**句子**为发音单元（不是段落，否则 `onRangeStart` 太粗、暂停粒度太差）：

```java
// 每句一个 utteranceId，如 "c12:p34:s2"
tts.speak(sentence, TextToSpeech.QUEUE_ADD, params, uid);
// 引擎回调该句内字符区间 → 加上句子在段落中的偏移 → 段落 TextView 上打 BackgroundColorSpan
@Override public void onRangeStart(String uid, int start, int end, int frame) {
    int base = sentenceStartOffset(uid);   // 从缓存查
    highlight(base + start, base + end);
}
```

- 高亮后用 `scrollToPosition` 保证当前句可见。
- **兜底**：少数引擎不实现 `onRangeStart`，此时退化为整句高亮，功能不残。
- 初始化时检查 `isLanguageAvailable(Locale.US)`，不可用则引导用户装 TTS 引擎（Google TTS / 讯飞等），别静默失败。
- 后台播放靠 `TtsService`（前台服务 + 媒体通知：播放/暂停/上一句/下一句），`onDestroy` 里务必 `tts.stop(); tts.shutdown();`。

---

## 3. 数据模型（SQLite）

```sql
CREATE TABLE book (
  id INTEGER PRIMARY KEY, title TEXT, author TEXT,
  format TEXT,              -- 'epub' | 'txt'
  src_path TEXT,            -- 拷贝到 filesDir/books/ 后的本地路径
  cover_path TEXT, chapter_count INTEGER,
  added_at INTEGER, last_opened_at INTEGER, progress_pct REAL DEFAULT 0
);

CREATE TABLE chapter (
  id INTEGER PRIMARY KEY, book_id INTEGER, idx INTEGER,   -- idx 从 0 起，spine 顺序
  title TEXT, href TEXT, para_count INTEGER
);
CREATE INDEX idx_chapter_book ON chapter(book_id, idx);

CREATE TABLE paragraph (
  id INTEGER PRIMARY KEY, chapter_id INTEGER, idx INTEGER,
  text TEXT,
  trans TEXT,               -- 整段译文；NULL = 未译
  trans_status INTEGER DEFAULT 0, -- 0未译 1排队 2已译 3失败
  trans_engine TEXT, trans_at INTEGER
);
CREATE INDEX idx_para_chapter ON paragraph(chapter_id, idx);

CREATE TABLE word_cache (   -- 词典查不到网络补译时的兜底也存这
  word TEXT PRIMARY KEY, lemma TEXT, phonetic TEXT, translation TEXT,
  source TEXT, updated_at INTEGER
);

CREATE TABLE vocab (
  id INTEGER PRIMARY KEY, word TEXT, lemma TEXT, phon TEXT, translation TEXT,
  sentence TEXT,            -- 命中处的原句，复习时给语境
  book_id INTEGER, chapter_id INTEGER, para_id INTEGER,
  created_at INTEGER, mastered INTEGER DEFAULT 0
);
CREATE UNIQUE INDEX idx_vocab_word ON vocab(lemma);

CREATE TABLE reading_pos (
  book_id INTEGER PRIMARY KEY, chapter_id INTEGER, para_id INTEGER,
  in_para_offset INTEGER, updated_at INTEGER
);
```

内置词典 `assets/dict.db`（只读，独立于上面的业务库）：

```sql
CREATE TABLE dict (word TEXT PRIMARY KEY, lemma TEXT, phonetic TEXT,
                   translation TEXT, freq INTEGER, tag TEXT);
CREATE TABLE form (   -- 变形反查：ran → run，由 ECDICT 的 exchange 字段预生成
  form TEXT PRIMARY KEY, lemma TEXT);
CREATE INDEX idx_form_lemma ON form(lemma);
```

查词流程：`form` 表查原形 → `dict` 取释义；都没有再走 `word_cache` → 最后才考虑网络。

---

## 4. 代码结构（目标形态）

```
app/src/main/java/com/englishread/app/
  App.java                      Application：共享单线程 IO 线程池              ← M0/M1 已建
  ui/MainActivity.java          书架（首页）                                  ← M0/M1 已建（接真数据）
  ui/LibraryAdapter.java        书架列表项（封面/无作者收起/长按删除）         ← M0/M1 已建
  ui/ImportHelper.java          SAF 选文件 / URL 直链 → 拷贝 → 解析入库（含失败清理） ← M1 已建，URL 导入后加
  ui/ReaderActivity.java        阅读器主界面（沉浸菜单、目录、翻页、亮度、主题）     ← M2 已建
  ui/SettingsActivity.java      翻译服务三件套配置 + 隐私提示                      ← M3 已建
  ui/VocabActivity.java         生词本
  ui/SettingsActivity.java      全局设置（阅读器内的设置已在底部面板里）
  reader/ReaderAdapter.java     段落列表：段落 TextView（纯原文）+ 独立「段」按钮 + 内联译文 ← M2 已建
  reader/ReaderHitTest.java     纯函数命中测试（只判词，可单测）                ← M2 已建
  reader/ParagraphGestures.java 段落点击/长按识别（onTouch 返回 false）         ← M2 已建
  reader/ReaderPrefs.java       字号/行距/段距/字距/缩进/主题/亮度持久化          ← M2 已建
  reader/TocAdapter.java        目录列表                                       ← M2 已建
  reader/TranslationPopup.java  单词卡 PopupWindow                              ← M2 已建
  reader/SentenceIndex.java     段落 → 朗读片段（返回原文区间，纯函数，7 例单测）  ← M4 已建
  model/{Book,Chapter,Paragraph,WordEntry,Token,VocabEntry}.java  ← Book/Chapter/Paragraph/WordEntry 已建
  data/Db.java                  建表 + 版本迁移                                 ← M1 已建
  data/BookDao.java             整本事务入库 / 列表 / 删书连带删文件             ← M1 已建
  data/ParagraphDao.java        章节列表 / 整章段落 / 译文回写                   ← M2 已建
  data/{VocabDao,ReadingPosDao}.java
  data/DictSource.java          assets/dict.db 查询 + form→lemma + word_cache   ← M2 已建
  data/ReadingPosDao.java       断点续读 + 书架百分比/最后打开时间              ← 2026-10-21 已建
  parse/BookParser.java         按格式分派                                       ← M1 已建
  parse/ParsedBook.java         解析中间结构（纯 Java）                           ← M1 已建
  parse/TxtParser.java          编码探测(BOM→UTF-8→GB18030) + 切章 + 空行切段     ← M1 已建
  parse/EpubParser.java         container.xml → OPF(manifest/spine) → 章节        ← M1 已建
  parse/XhtmlBlockParser.java   XmlPullParser 抽块级元素 + 清洗 + 剥标签降级       ← M1 已建
  parse/Tokenizer.java          正则分词 + 句子切分                               ← M0 已建（分词）
  parse/Sentences.java          切句：一句话一个段落（BookParser 统一调用）         ← M2 补丁 5 已建
  trans/LlmClient.java          HttpURLConnection 打 chat/completions             ← M3 已建
  trans/TransQueue.java         批量 + 去重 + 漏段补发 + 缓存 + 失败落库            ← M3 已建
  trans/TransProtocol.java      线协议纯函数（拼请求/切回复/认地址）                ← M3 已建
  trans/ProviderTemplates.java  7 家厂商 base_url + model 模板                    ← M3 已建
  trans/ApiConfig.java          base_url / key / model 私有 SP                     ← M3 已建
  tts/TtsController.java        引擎 / 一次一句的队列 / 高亮 / 语速 / 状态机       ← M4 已建
  tts/TtsService.java           前台服务 + 通知栏控制 + binder                    ← M4 已建

app/src/main/assets/dict.db     内置词典（由 tools/build_dict.py 生成）           ← M2 已建
tools/build_dict.py             ECDICT → 裁剪 → dict.db（8 万词 / 10.9 MB）      ← M2 已建
tools/fake_llm.py               本地假 LLM，端到端验收翻译链路（--drop/--delay）  ← M3 已建
app/src/debug/AndroidManifest.xml  仅 debug 放行明文，供假服务验收              ← M3 已建
samples/{sample.txt,sample_epub2.epub,sample_epub3.epub}  导入验收样本            ← M1 已建
```

M0 已落地的资源 token（后续直接复用，不要再造一套）：
`colors.xml`（brand / brand_deep / page_light / page_dark / highlight_word / highlight_sentence）、
`dimens.xml`（space_xs…l / reader_text_size / reader_line_spacing_mult / reader_para_spacing / reader_side_margin）、
`themes.xml`（`TextAppearance.EnglishRead.{Title,Body,Caption}`、`Widget.EnglishRead.BookCard`）。

分词正则（词 + 缩写 + 连字符）：

```java
Pattern WORD = Pattern.compile("[A-Za-z]+(?:['\u2019\\-][A-Za-z]+)*");
```

---

## 5. 实施步骤

每步都给了「验收」，不通过不进下一步。工时为单人估。

### M0 工程基线（0.5 天）—— ✅ 已完成

1. ✅ JDK 17：用 `C:\Users\y\.jdks\corretto-17-jdk`，`JAVA_HOME` 指向它。
2. ✅ `applicationId` / `namespace` → `com.englishread.app`。
3. ✅ `libs.versions.toml` 补 `androidx.recyclerview:1.3.2`。
4. ✅ `App.java`（含 `android:name` 注册）、`ui/model/parse` 包结构。
5. ✅ `themes.xml` 基础色板与字号 token（含 `values-night`）。

**验收结果**：`assembleDebug` 通过；在 Pixel_9_Pro（Android 16）上安装启动无崩溃；
浅色/深色主题截图核对通过；`testDebugUnitTest` 3/3 通过；`connectedDebugAndroidTest` 1/1 通过。

**M0 期间踩到并修掉的坑（后人别再踩）**：
- `MaterialToolbar` 的标题必须写 `app:title`，写 `android:title` 不显示（实测工具栏空白）。
- 深色主题下 FAB 白字对 `brand`(#2F6F62) 只有约 4.5:1，压线；已改用 `brand_deep`(#23574D) 提到约 6:1。
- 布局里用 `tools:text` 就必须在根节点声明 `xmlns:tools`，否则 aapt2 直接 `AttributePrefixUnbound` 编译失败。
- 空态文案不要写「点右下角」，改为动作名「导入书籍」，换布局也不会过期。
- 模拟器 adb 可能先是 `unauthorized`，`adb kill-server && adb start-server` 可解。

### M1 书架 + 导入 + 解析（2–3 天）—— ✅ 已完成

1. ✅ `ImportHelper`：`ACTION_OPEN_DOCUMENT`（`application/epub+zip` + `text/plain` + `application/octet-stream`），
   流式拷贝到 `filesDir/books/{uuid}.{ext}`。
   **没做** `takePersistableUriPermission`：文件已拷进私有目录，没有后续读取原 URI 的需求，不需要这个授权。
2. ✅ `TxtParser`：编码探测（BOM→UTF-8 严格解码失败→GB18030），空行切段，按 `Chapter N`/`第N章`/分隔线切章。
3. ✅ `EpubParser`：`META-INF/container.xml` → OPF → `manifest`/`spine` 顺序 → 逐章 XHTML 抽块。
   XHTML 预清洗：去 DOCTYPE、命名实体换数字实体、补自闭合 `br`/`img`。
4. ✅ 入库（book/chapter/paragraph），封面取 `properties="cover-image"`（EPUB3）或
   `<meta name="cover">`（EPUB2）。
5. ✅ 书架 UI：卡片（封面/标题/进度）+ 导入按钮 + 长按删除。

**验收结果**：`samples/` 里 TXT、EPUB2、EPUB3 各导入一次，落库行数与样本一致
（2 章/2 段、2 章/4 段、3 章/5 段）；EPUB3 里那一章故意写成不合法 XHTML，靠清洗救回而非整本失败；
`TxtParserTest` 5/5、`ImportParsersTest` 3/3、`BookDaoTest` 3/3、`TokenizerTest` 3/3 全绿。

**M1 期间踩到并修掉的坑（后人别再踩）**：
- `Chapter 1` 这种首字母大写的章名，正则写成 `chapter|CHAPTER` 两边都不匹配 —— 用 `(?i)`，别枚举大小写。
- EPUB 的 `<h1>` 既是章节名又是第一个 `<p>` 级别的块：直接全收会在阅读器里重复显示标题，入库前要把与章节名相同的那一条剥掉。
- 删书只删数据库行会留一堆孤儿文件在 `filesDir/books/`，删得越多磁盘越满；删文件要在事务提交**之后**。
- 没有作者的书（TXT 常见）如果仍占着 `author` 那一行，卡片会凭空高出一截；`View.GONE` 掉。
- `sourceSets` 里给 androidTest 加 assets，相对路径 `../../samples` 解析不到（merge 出空目录）；用 `rootProject.file("samples")`。
- `adb shell cat` 拉二进制文件会被 CRLF 破坏，`adb exec-out` 又会被 PowerShell 写成 UTF-16；最稳的是先 `run-as ... > /data/local/tmp/x`，再 `cp` 到 `/sdcard`，最后 `adb pull`。

### M3 段落翻译（2–3 天）—— ✅ 已完成

1. `LlmClient`：`base_url` + `key` + `model` 三件套可在设置页配；`HttpURLConnection` + `org.json`；
   超时 10s/20s、失败重试 1 次、可取消。
2. `TransQueue`：批量 3–5 段、命中缓存即返回、写回 `paragraph`。
3. 段落下方内联译文（默认折叠，**点「段」按钮展开**；设置里可整体隐藏该按钮）。
   M2 的 `ReaderActivity.onTranslateToggle` 已从「未配置」占位换成真实队列。
4. 设置页明示：**段落文本会发送到所配置的第三方服务**（隐私政策必写）。

**验收（已达成）**：断网 → 点「段」字出「失败 + 重试」而非崩溃；
同一段第二次展开时日志**无网络请求**；一本书读 3 章后 `paragraph.trans_status=2` 的段落数稳定不回退。

实现与本节原计划的差异（后续别当成 bug 改回去）：
- 超时定的是 **10s 连接 / 20s 读取**，不是统一的 20s——连接超时那么长只会在断网时白等。
- **没做后台预取**（原计划「顺延预取后续 10 段」）。预取等于用户没点也在花钱，
  而 M3 的验收只要求「读哪译哪」；要做请先把「本月调用次数」的展示一起补上。
- 点一次「段」**只译被点的那一句**（曾经的「顺手往后多译 5 段」已删，见「M3 补丁 2」）。

**配置模板（`trans/ProviderTemplates.java`，设置页点 chip 一键填）**

模板只填 base_url 与 model，**绝不含 Key**（用户仍需自己贴，这条防线不能被模板绕过）。
模型名与地址都会过期，界面允许手改；下表是写这份代码时（2026-10）核过官方文档的值：

| 厂商 | base_url | model | 备注 |
|---|---|---|---|
| DeepSeek | `https://api.deepseek.com` | `deepseek-flash` | 便宜，段落翻译够用。**旧名 `deepseek-chat` 已废弃** |
| 阿里云百炼 | `https://dashscope.aliyuncs.com/compatible-mode` | `qwen-plus` | 新域名要 `{WorkspaceId}` 前缀，VPC 专属域名更好但需用户填自己的 ID |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-5.3` | **地址以 `/v4` 结尾**，是 `endpoint()` 必须认任意版本段的原因 |
| Kimi | `https://api.moonshot.cn/v1` | `kimi-k2.6` | 长上下文 |
| 硅基流动 | `https://api.siliconflow.cn/v1` | `Qwen/Qwen3-32B` | 开源模型聚合，部分免费 |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | 需海外网络 |
| 本机 Ollama | `http://127.0.0.1:11434/v1` | `qwen2.5:7b` | 原文不出网；**仅 debug 包可用**，release 拒绝明文 |

模板正确性已实测：五个 https 地址用无效 Key 打过去全部返回 **401**（说明路径与协议对，只差 Key），
智谱那条在修复前会因 `/v4/v1` 叠层返回 404。OpenAI 因网络环境超时，未验。

### M4 听书（2–3 天）—— ✅ 已完成

1. ✅ `TtsController`：初始化 / `setLanguage(Locale.US)` 可用性检查 / 一次一句的队列 / `onRangeStart` 逐词高亮 / 语速。
2. ✅ `TtsService`：`mediaPlayback` 前台服务 + 通知栏（上一句 / 暂停 / 下一句，**划掉即停止**）；锁屏与切后台继续。
3. ✅ 高亮跟随自动滚动；点正文时暂停/继续（设置里可开，**默认关**，保护「词优先」查词）。

**验收（已达成）**：连续播放不中断；高亮逐词跟随且不卡顿（直接改 span，不整项重绑）；
后台/锁屏可控（通知栏三键）；播完自动进下一章、全书读完有提示；
无 TTS 引擎的机器弹「去系统设置」的可操作提示，而不是静默。

实现与本节原计划的差异（后续别当成 bug 改回去）：

- **没做 MediaSession / MediaStyle**：要引 `androidx.media`，收益只是蓝牙键与媒体卡样式，v1 不值（R14）。
- **发音单元是「句」不是「段」**：正文本来就是一句话一段，句子就是段落；`SentenceIndex` 只兜底老书的长段与超长句。
- **暂停后从当前句开头重念**：TTS 没有 seek，想要词级续播得自己切片重排。
- 通知第 4 个按钮（停止）被系统丢弃 → 改成划掉通知，见 M4 的坑。

### M5 生词本 + 进度 + 设置（2 天）

1. 生词本列表（带原句语境）、标记已掌握、按书筛选。
2. 阅读进度：`onPause` 存 `reading_pos`，进入时恢复到段落偏移。**← 已提前做完（见「阅读进度」一节）**
3. 设置聚合：字号、行距、主题、译文语言、API 配置、词典/数据来源声明。

**验收**：杀进程后重新打开，落回原段落原位置 ±1 屏。

### M6 打磨与发布（1–2 天）

1. 性能：10 万字单章滚动不卡（`RecyclerView` 复用 + 文本预分词缓存）、内存峰值检查。
2. 空态/错误态/加载态补齐；RTL 与超长单词不撑破布局。
3. release 签名、`minifyEnabled=true`、APK 体积核对（词典是大头）。
4. 隐私政策 + 第三方数据来源声明（ECDICT、LLM 服务）。

**验收**：release 包在 minSdk 28 与 targetSdk 36 设备各跑一遍主流程无崩溃。

**合计约 12–17 人日。**

---

## 6. 构建与自测命令

```powershell
$env:JAVA_HOME = 'C:\Users\y\.jdks\corretto-17-jdk'   # 必须：本机默认 java 是 JDK 24
.\gradlew.bat :app:assembleDebug                      # 首次联网，之后可加 --offline
.\gradlew.bat :app:testDebugUnitTest                  # 63 例（TransProtocol 15 / Sentences 7 / SentenceIndex 7 / ReaderHitTest 6 / Tokenizer 5 / TxtParser 5 / UrlImportName 5 / ProgressPct 5 / ReaderPrefsLadder 4 / TransQueuePending 4）
.\gradlew.bat :app:installDebug
.\gradlew.bat :app:connectedDebugAndroidTest           # 需先起模拟器（19 例：BookDao 3 + 导入解析 3 + LLM 回复解析 6 + 包名 1 + 阅读器数据 4 + 阅读器主题 2）

# 听书的手工验收（模拟器自带 Google TTS，不需要联网）
adb logcat -s TtsController:*                          # 看 engine ready / load / state / book finished
adb shell dumpsys activity services com.englishread.app | findstr isForeground   # 前台服务与通知类型
adb shell dumpsys notification --noredact | findstr "android.text"              # 通知正文＝当前句，能看出是否在推进
adb shell cmd statusbar expand-notifications           # 通知栏三键：上一句/暂停/下一句；划掉通知＝停止
# 无引擎路径：
adb shell pm disable-user --user 0 com.google.android.tts   # → 点「听书」应弹可操作提示
adb shell pm enable com.google.android.tts                  # 装回后不重启 App 再点「听书」应能直接念

# 段译文的端到端验收：本地假 LLM（不连外网、不花钱）
python tools/fake_llm.py 8787                          # 基础
python tools/fake_llm.py 8787 --drop 1                 # 故意漏第 1 段，验证只补发缺的
python tools/fake_llm.py 8787 --delay 6                # 慢响应，验证「翻译中」与取消
# 设置页填 http://10.0.2.2:8787 + 任意 key + 任意 model 即可（仅 debug 放行明文）
# 验收时数假服务打印的 REQ 行数：第二次展开同一段应该是 0 行新增

# 起模拟器（后台）
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel_9_Pro -no-audio -gpu swiftshader_indirect
# adb 若显示 unauthorized：
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" kill-server; & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" start-server
```

单测只覆盖纯逻辑（分词、编码探测、TXT 切章、线协议、补发筛选），不引 Robolectric——
这几处正是最容易悄悄坏掉的地方，一个 JUnit 就够。**解析器吃 byte[]/String**，所以能直接单测。

`BookDao` 与 EPUB 解析走真 SQLite / 真 zip，放在仪器测试里（`connectedDebugAndroidTest`），需要先起模拟器。

`LlmClient` / `TransQueue` 的网络路径不写单测（要 mock 掉 `HttpURLConnection` 得不偿失），
靠 `tools/fake_llm.py` 做手工端到端验收，见上面的命令。

---

## 7. 风险与未决项

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| R1 | ECDICT 数据许可 | 发布合规 | 仓库 README 与其 releases 页面写的是 MIT 风格授权，但**发布前必须自己核对一遍**并做署名声明；拿不准就只内置裁剪子集 + 署名。 |
| R2 | ECDICT 体积 | APK 过大 | 按词频裁剪到 6–10 万词，预计 20–30MB；再大就改成首次启动下载词典包。 |
| R3 | EPUB 千奇百怪（XHTML 不合法、老 EPUB2 编码） | 解析失败 | 先清洗再解析；解析失败的章节降级为「纯文本抽取」而不是整本失败；失败可见可上报。 |
| R4 | LLM 成本与延迟 | 用户流失 | 段落级缓存 + 批量请求 + 只译可见区；设置页显示本月调用次数。 |
| R5 | 后台预取进程被杀 | 预取中断 | 可接受，下次打开章节续上。真要保证就上 WorkManager（新增依赖，v1 不做）。 |
| R6 | TTS 引擎差异（部分不实现 `onRangeStart`） | 高亮缺词级 | 已实现整句兜底：开始念就把整句区间给高亮，range 回调只是把它缩到词。 |
| R14 | 没有 MediaSession / MediaStyle（要引 `androidx.media`） | 蓝牙耳机、车机的播放键收不到；通知栏不是媒体卡样式 | 接受（v1）。真要接：引依赖后在 `TtsService` 里加 `MediaSessionCompat`，命令仍转给同一个 `TtsController`。 |
| R15 | 通知只有 3 个按钮，且**划掉通知＝停止** | 误划会结束朗读；「停止」没法做成按钮 | 接受。3 个按钮是系统上限（API 36 实测，第 4 个直接不渲染），自定义 RemoteViews 不值当。 |
| R7 | 大书打开慢（整章入库） | 首开等待 | 解析放 IO 线程 + 进度条；入库按章增量写，先出第一章再继续。 |
| R8 | 密钥明文存 SharedPreferences | 中低 | 私有目录已受系统保护；`ponytail:` 明确留了升级路径到 `EncryptedSharedPreferences`。 |
| R9 | 「段」按钮被误读成正文/装饰 | 用户不知道能点 | 小号 + 次要色 + `contentDescription` + ripple；48dp `TouchDelegate` 保证可点；设置页有开关说明。 |
| R10 | 明文接口地址（http） | 段落原文可被中间人读取 | 设置页强制 https；只有 debug 构建放行本机回环（`src/debug/AndroidManifest.xml` + `BuildConfig.DEBUG`），release 包永远拒绝明文。 |
| R11 | 模型漏段 / 答非所问 | 少一段译文或多花钱 | 回复按编号切槽，缺的留 null；只补发缺的那几段（不是整批重发）；仍缺则落 `STATUS_FAILED` 给用户重试。 |
| R12 | API Key 明文存 SP | 中低 | 同 R8，私有目录已受系统保护；升级路径是 `EncryptedSharedPreferences`。 |
| R13 | 无「本月调用次数」显示 | 用户不知道花了多少 | M3 没做。设置页目前只有隐私提示；要补请在 `TransQueue` 里累计成功请求数并展示。 |
| R16 | 没有音频焦点管理 | 来电 / 其他播放器不会自动让路，可能叠音 | `setAudioAttributes(USAGE_MEDIA)` 只保证走媒体音量；要接就 `AudioManager.requestAudioFocus`，拿到 `LOSS` 时暂停、`GAIN` 时不自动续（听书场景手动续更安全）。 |

### 决策记录 D1：整段翻译入口 = 段落下方的独立小号「段」按钮（2026-10-20 定，同日修订）

**改之前**：点「段落末尾/行尾留白」触发翻译，靠 `gutterPx` 阈值判断。

**第一版修订**：改成段末小号「段」字，但**拼进 Spannable**。问题：段字成了段落文本的一部分，
`TTS` 会把它念出来、复制本段会带上它、句子切分与偏移表都要记得排除它。三处都得同步，漏一处就是 bug。

**最终版**：段是**独立 View**（`item_paragraph.xml` 里的 `MaterialButton`/`TextView` + `OnClickListener`），
不是字符串。因此：

- `paragraph.text` 从入库到显示全程只含原文，TTS / 复制 / 句子切分**零改动**。
- `ReaderHitTest.resolve` 退回「只判词」两种结果（**没有** `Hit.marker()`，也**没有** `gutterPx`）。
- 手势天然不冲突：按钮吃掉自己的点击，`onTouch` 的 `false` 只影响段落文字区域。

**代价（已知，接受）**：多一个 View 层级、每段多一个 `OnClickListener`；回收池里每项常驻一个隐藏按钮。
可忽略——RecyclerView 复用下 View 数量与可见段落数同阶。

**为什么选它**：三个版本里它的代码最少、且是唯一不污染数据模型的一个。

**如果将来要改回去**：只需改 `ReaderHitTest.resolve` 的判定顺序，别的地方不依赖这个方案。

**待你决定（不影响开工，M3 前定即可）**
- LLM 供应商与模型（默认 OpenAI 兼容；国内可换 DeepSeek / 通义，代码里只认 `base_url`）。
- 词典是内置还是首次启动下载。
- 是否要「点词自动发声」。

### 决策记录 D2：沉浸式菜单 + 左中右分区翻页，且**词优先**（2026-10-20 定）

**交互模型**（参考主流的 legado / 微信读书类阅读器）：
正文满屏、菜单不可见；点屏幕**中间**才滑出顶部栏（返回/章节/位置）与底部栏（目录/上一章/下一章/设置）。

**分区与点词的冲突**：同一块区域既要能查词又要能翻页。定了**词优先**：
- 手指落在**单词**上 → 查词卡（这是本 App 的核心学习动作，不能被翻页抢走）；
- 落在**空白 / 标点 / 行尾 / 段外留白** → 才按左 1/3 回退、中 1/3 呼菜单、右 1/3 前进。

否决的替代方案：
- 「分区优先、查词改长按」：翻页手感纯正，但查词每次多一步，学习场景不划算。
- 「单击分区 + 双击查词」：双击不可靠，还容易误触翻页。

**翻页做了两种**（设置里可切）：
- **连续滚动**（默认）：左右分区 = 上/下一屏平滑滚动，章末自动进下一章；可自由上下拖动。
- **按屏翻页**：`PageIndex` 把整章摊平成绝对高度轴，每屏切一刀，
  分区点按/横滑/音量键都是**真正的左右翻页**（旧页滑出、新页滑入），
  左下角显示「第 N 章 · x/y」。**此模式下纵向拖动被禁用**——页是一屏一屏的，
  允许拖到半屏会让页码与屏表对不上。
- 仿真翻页（页边卷曲那种）**没做**，但横移动画已经到位；要加卷曲只需换掉
  `PageTurn` 的位移公式，两页的素材与层级不用动。
- **音量键翻页**在两种模式下都生效：`VOLUME_DOWN` 下一页、`VOLUME_UP` 上一页。

**其余设置**：字号 7 档、行距 5 档、段距 5 档、字距 4 档、首行缩进 3 档、
主题（跟随系统/浅色/深色）、段按钮开关、亮度滑杆。
亮度用 `screenBrightness` 而不是调系统亮度——**不申请任何权限**，且「跟随系统」默认为开。

---

## 8. 接手第一小时

1. 设 `JAVA_HOME=C:\Users\y\.jdks\corretto-17-jdk`，跑通 `.\gradlew.bat :app:assembleDebug`（基线是绿的）。
2. 样本已就位：`samples/sample.txt`、`samples/sample_epub2.epub`、`samples/sample_epub3.epub`。
   它们通过 `sourceSets` 注入 androidTest 的 assets，**不会**被打进 APK。
3. **直接从 M5（生词本）开始**——M0–M4 都已就位。不要再动包名，也不要改「段按钮是独立 View」这个决策，
   更不要把 `paragraph.text` 拼上任何 UI 装饰（TTS、复制、句子偏移表全依赖它只含原文）。
   M5 的接入点是 `ReaderActivity.onWordLongPress`（现在只弹词卡）与 `vocab` 表（表已建、DAO 未建）；
   听书要加控制就动 `tts/TtsService` + `tts/TtsController`，**别在 Activity 里另起一个 TextToSpeech**。
4. 词典已内置：`app/src/main/assets/dict.db`（8 万词 / 5.6 万变形 / 10.9 MB）。
   要重新生成：`python tools/build_dict.py <ecdict-sqlite 解出的 stardict.db> app/src/main/assets/dict.db`。
5. 段译文要跑端到端：`python tools/fake_llm.py 8787`，设置页填 `http://10.0.2.2:8787`
   （**仅 debug 包放行明文**，release 会拒绝并提示必须 https）。见第 6 节。
6. 模拟器上测单击请用 `adb shell "input motionevent DOWN x y"` + `... UP x y" 两次
   （2026-10-21 之前 `input tap` 看起来"必被判成长按"，其实是 M3 补丁 3 那个事件分发 bug，已修）。
   「段」按钮是普通 `OnClickListener`，`input tap` 对它一直有效，M3 的验收就是这么做的；
   听书按钮、通知栏按钮同样走 `OnClickListener` / `PendingIntent`，`input tap` 也有效。

**当前代码状态：M0、M1、M2、M3、M4 已完成，M5 未开始。** 书架点击已接进阅读器，
阅读器支持：沉浸式上下菜单（**点正文外任意空白、以及句子之间的缝都能唤出**）、目录抽屉、
**只有上下滚动（左右翻页已删，见 M2 补丁 5）**、音量键上下滚一屏、
字号行距段距字距缩进主题亮度设置、点词查本地词典、
**点「段」看这一句的中文译文（LLM，带缓存与失败重试）**、
**从上次读到的那一句续读（书架卡片也会显示百分比）**、
**听书（系统 TTS，逐词高亮、章尾自动顺延、锁屏/后台用通知栏控制）**；生词本还没有。

**正文单位是「一句话」**：导入时 `Sentences` 把每段按 `.!?` 切开，一句一段。
所以「段」按钮、译文缓存、词偏移、进度计数全都是按句走的。
