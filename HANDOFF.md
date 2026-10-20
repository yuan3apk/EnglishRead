# EnglishRead 交接文档

> 英语阅读学习 App：导入英文书 → 点词看释义 → 点「段」按钮看整段译文 → 听书。
> 本文件是项目的唯一入口文档：现状、决策、数据模型、实施步骤、验收标准、风险。接手先读完第 0 节。

最后更新：**M2 已完成（阅读器 + 点词 + 段按钮 + 内置词典），M3 未开始**（M0/M1 此前已完成；M2 已在 Android 16 模拟器实测导入、点词、段按钮、章节切换）

---

## 0. 现状快照（实测，不是推测）

| 项 | 值 |
|---|---|
| 形态 | 纯 Android 原生单模块工程，`:app`，语言 **Java** |
| 包名 / applicationId | **`com.englishread.app`**（M0 已从 `com.example.englishread` 迁走） |
| AGP / Gradle | 8.13.1 / Gradle 8.14（wrapper，走腾讯云镜像） |
| compileSdk / targetSdk / minSdk | 36 / 36 / **28** |
| Java 编译级别 | source/target = 17 |
| 现有依赖 | appcompat、material、activity、constraintlayout、**recyclerview**、junit、espresso |
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

### M2 已完成清单（本次）

- [x] `model/{Chapter,Paragraph,WordEntry}.java`：`paragraph.text` 只含原文的字段约定写进类注释
- [x] `data/ParagraphDao.java`：章节列表、整章段落、译文回写、未译计数
- [x] `data/DictSource.java`：`form` 反查（ran→run）→ `dict` 取释义 → 业务库 `word_cache` 兜底；assets 库先拷到 `databases/`
- [x] `reader/ReaderHitTest.java`：纯函数，只有 `word` / `none` 两种结果，无 `gutterPx`、无 marker
- [x] `reader/ParagraphGestures.java`：`GestureDetector` + `onTouch` 返回 false，滚动不受影响
- [x] `reader/ReaderAdapter.java`：正文纯原文 + 独立小号「段」按钮（48dp `TouchDelegate`）+ 内联译文
- [x] `reader/TranslationPopup.java` + `popup_word.xml`：词卡锚定词矩形，下方放不下翻到上方
- [x] `ui/ReaderActivity.java`：单章连续滚动、上一章/下一章、点词查词、段按钮、长按
- [x] `ui/LibraryAdapter`：补 `OnBookClick`，书架点击进阅读器；Manifest 注册 `ReaderActivity`
- [x] `tools/build_dict.py`：ECDICT → 8 万词裁剪（10.9 MB）+ 5.6 万变形反查
- [x] `app/src/main/assets/dict.db`：内置词典已生成并入库
- [x] 测试：`ReaderHitTestTest`（6 例）、`TokenizerTest`（+2 例）、`ReaderDataTest`（4 例，仪器）

**验收结果**：`assembleDebug` 通过；单测 16/16、仪器测试 11/11 全绿；
模拟器实测导入样本 → 点词弹出 `It /ɪt/ pron. 它` + 朗读 + 加生词本；
「段」按钮弹出「未配置翻译服务」（M3 才接 LLM，不假装在翻译）；
「段」为约正文一半大小的浅灰右对齐小字，明显弱于正文、不像章节标题；
`paragraph.text` 与入库原文逐字相同（`ReaderDataTest.paragraphTextIsStoredVerbatim` 守这条）。

**M2 期间踩到并修掉的坑（后人别再踩）**：
- `LlmClient`/`TransQueue` 不存在时，「段」按钮不能静默：现在给「未配置」提示，M3 换掉即可。
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
- **adb `input tap` 在模拟器上必被判为 `onLongPress`**（注入的事件时长超阈值），
  所以用 `input tap` 测不了单击路径。单击与长按共用 `handle()`，命中计算本身已用日志验证；
  要测单击请直接手动点，不要靠 adb 断言。
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
| 段落翻译 | **OpenAI 兼容 `/v1/chat/completions`**，`HttpURLConnection` + `org.json` | 平台自带 HTTP 和 JSON，不需要 okhttp/gson。译文质量是段落级的关键，本地 ML Kit 做不到「像人话」。 |
| 段落译文缓存 | 直接存 `paragraph` 表字段 | 一次生成永久复用，这是控成本的命门。 |
| 听书 | 系统 `TextToSpeech` + `UtteranceProgressListener.onRangeStart` | `onRangeStart` 提供**词级字符区间**，白送的逐词高亮，不用自己做时间轴。minSdk 28 ✓。 |
| 后台播放 | `ForegroundService`（`mediaPlayback` 类型）+ 媒体通知 | Android 8+ 必须有通知才不会被杀。 |
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

不预先生成整本书（贵且慢），采用「**读哪译哪 + 后台预取**」：

1. 打开章节 → 当前可见段落进队列（单线程 `ExecutorService`）。
2. 批量翻译：一次请求 3–5 段，用固定分隔符协议返回，省 token 也省往返。
3. 结果写 `paragraph.trans` 并置 `trans_status=2`；再次点击**永不重复请求**。
4. 后台顺延预取本章后续 10 段；失败重试 1 次后置 `trans_status=3`，展示「重试」。
5. 提示词要求：只输出译文、保留人名/术语一致、不解释、不扩写。

```text
你是英译中翻译器。把下面编号的段落翻译成简体中文。
要求：保留原段落编号；只输出「编号\|译文」行；术语与专有名词前后一致；不添加解释。
1|It was the best of times, it was the worst of times.
2|...
```

进程被杀就停，下次打开章节自动续——不做 WorkManager（见风险 R5）。

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
  ui/ImportHelper.java          SAF 选文件 → 拷贝 → 解析入库（含失败清理）    ← M1 已建
  ui/ReaderActivity.java        阅读器主界面（章节切换、点词、段按钮）          ← M2 已建
  ui/VocabActivity.java         生词本
  ui/SettingsActivity.java      字号/行距/主题/翻译语言/API 配置/词典来源声明
  reader/ReaderAdapter.java     段落列表：段落 TextView（纯原文）+ 独立「段」按钮 + 内联译文 ← M2 已建
  reader/ReaderHitTest.java     纯函数命中测试（只判词，可单测）                ← M2 已建
  reader/ParagraphGestures.java 段落点击/长按识别（onTouch 返回 false）         ← M2 已建
  reader/TranslationPopup.java  单词卡 PopupWindow                              ← M2 已建
  reader/SentenceIndex.java     段落内句子切分与偏移表（TTS 用）
  model/{Book,Chapter,Paragraph,WordEntry,Token,VocabEntry}.java  ← Book/Chapter/Paragraph/WordEntry 已建
  data/Db.java                  建表 + 版本迁移                                 ← M1 已建
  data/BookDao.java             整本事务入库 / 列表 / 删书连带删文件             ← M1 已建
  data/ParagraphDao.java        章节列表 / 整章段落 / 译文回写                   ← M2 已建
  data/{VocabDao,ReadingPosDao}.java
  data/DictSource.java          assets/dict.db 查询 + form→lemma + word_cache   ← M2 已建
  parse/BookParser.java         按格式分派                                       ← M1 已建
  parse/ParsedBook.java         解析中间结构（纯 Java）                           ← M1 已建
  parse/TxtParser.java          编码探测(BOM→UTF-8→GB18030) + 切章 + 空行切段     ← M1 已建
  parse/EpubParser.java         container.xml → OPF(manifest/spine) → 章节        ← M1 已建
  parse/XhtmlBlockParser.java   XmlPullParser 抽块级元素 + 清洗 + 剥标签降级       ← M1 已建
  parse/Tokenizer.java          正则分词 + 句子切分                               ← M0 已建（分词）
  trans/LlmClient.java          HttpURLConnection 打 chat/completions
  trans/TransQueue.java         预取队列 + 批量 + 缓存 + 重试
  tts/TtsController.java        句队列、高亮、语速、状态机
  tts/TtsService.java           前台服务 + 媒体通知

app/src/main/assets/dict.db     内置词典（由 tools/build_dict.py 生成）           ← M2 已建
tools/build_dict.py             ECDICT → 裁剪 → dict.db（8 万词 / 10.9 MB）      ← M2 已建
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

### M3 段落翻译（2–3 天）

1. `LlmClient`：`base_url` + `key` + `model` 三件套可在设置页配；`HttpURLConnection` + `org.json`；
   超时 20s、失败重试 1 次、可取消。
2. `TransQueue`：批量 3–5 段、命中缓存即返回、写回 `paragraph`。
3. 段落下方内联译文（默认折叠，**点「段」按钮展开**；设置里可整体隐藏该按钮）。
   M2 的 `ReaderActivity.onTranslateToggle` 现在只弹「未配置」，这里替换成真实队列。
4. 设置页明示：**段落文本会发送到所配置的第三方服务**（隐私政策必写）。

**验收**：断网 → 点「段」字出「未配置/失败 + 重试」而非崩溃；
同一段第二次展开时日志**无网络请求**；一本书读 3 章后 `paragraph.trans_status=2` 的段落数稳定不回退。

### M4 听书（2–3 天）

1. `TtsController`：初始化/语言可用性检查/句子队列/`onRangeStart` 逐词高亮/语速。
2. `TtsService`：前台服务 + 媒体通知（播放、暂停、上一句、下一句），锁屏与切后台继续。
3. 高亮跟随自动滚动；点击正文时暂停或继续（可配）。

**验收**：连续播放 5 分钟不中断；高亮逐词跟随且不卡顿；后台/锁屏可控；播完自动进下一章；
无 TTS 引擎的机器给出可操作的提示而非静默。

### M5 生词本 + 进度 + 设置（2 天）

1. 生词本列表（带原句语境）、标记已掌握、按书筛选。
2. 阅读进度：`onPause` 存 `reading_pos`，进入时恢复到段落偏移。
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
.\gradlew.bat :app:testDebugUnitTest                  # ReaderHitTest / Tokenizer / TxtParser（16 例）
.\gradlew.bat :app:installDebug
.\gradlew.bat :app:connectedDebugAndroidTest           # 需先起模拟器（11 例：阅读器数据 4 + 解析 3 + DAO 3 + 包名 1）

# 起模拟器（后台）
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel_9_Pro -no-audio -gpu swiftshader_indirect
# adb 若显示 unauthorized：
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" kill-server; & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" start-server
```

单测只覆盖纯逻辑（分词、编码探测、TXT 切章），不引 Robolectric——
这几处正是最容易悄悄坏掉的地方，一个 JUnit 就够。**解析器吃 byte[]/String**，所以能直接单测。

`BookDao` 与 EPUB 解析走真 SQLite / 真 zip，放在仪器测试里（`connectedDebugAndroidTest`），需要先起模拟器。

---

## 7. 风险与未决项

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| R1 | ECDICT 数据许可 | 发布合规 | 仓库 README 与其 releases 页面写的是 MIT 风格授权，但**发布前必须自己核对一遍**并做署名声明；拿不准就只内置裁剪子集 + 署名。 |
| R2 | ECDICT 体积 | APK 过大 | 按词频裁剪到 6–10 万词，预计 20–30MB；再大就改成首次启动下载词典包。 |
| R3 | EPUB 千奇百怪（XHTML 不合法、老 EPUB2 编码） | 解析失败 | 先清洗再解析；解析失败的章节降级为「纯文本抽取」而不是整本失败；失败可见可上报。 |
| R4 | LLM 成本与延迟 | 用户流失 | 段落级缓存 + 批量请求 + 只译可见区；设置页显示本月调用次数。 |
| R5 | 后台预取进程被杀 | 预取中断 | 可接受，下次打开章节续上。真要保证就上 WorkManager（新增依赖，v1 不做）。 |
| R6 | TTS 引擎差异（部分不实现 `onRangeStart`） | 高亮缺词级 | 已设计整句兜底。 |
| R7 | 大书打开慢（整章入库） | 首开等待 | 解析放 IO 线程 + 进度条；入库按章增量写，先出第一章再继续。 |
| R8 | 密钥明文存 SharedPreferences | 中低 | 私有目录已受系统保护；`ponytail:` 明确留了升级路径到 `EncryptedSharedPreferences`。 |
| R9 | 「段」按钮被误读成正文/装饰 | 用户不知道能点 | 小号 + 次要色 + `contentDescription` + ripple；48dp `TouchDelegate` 保证可点；设置页有开关说明。 |

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

---

## 8. 接手第一小时

1. 设 `JAVA_HOME=C:\Users\y\.jdks\corretto-17-jdk`，跑通 `.\gradlew.bat :app:assembleDebug`（基线是绿的）。
2. 样本已就位：`samples/sample.txt`、`samples/sample_epub2.epub`、`samples/sample_epub3.epub`。
   它们通过 `sourceSets` 注入 androidTest 的 assets，**不会**被打进 APK。
3. **直接从 M3 开始**——M0/M1/M2 都已就位，不要再动包名，也不要改「段按钮是独立 View」这个决策。
   M3 的接入点是 `ReaderActivity.onTranslateToggle`：现在它只弹「未配置」，换成 `TransQueue` 即可；
   `ParagraphDao.saveTranslation` 与 `Paragraph.STATUS_*` 已经是译文回写要用的全部接口。
4. 词典已内置：`app/src/main/assets/dict.db`（8 万词 / 5.6 万变形 / 10.9 MB）。
   要重新生成：`python tools/build_dict.py <ecdict-sqlite 解出的 stardict.db> app/src/main/assets/dict.db`。
5. adb `input tap` 在模拟器上必被判成长按，**测不了单击路径**；测点词请手动点，或看 `ReaderDataTest`。

**当前代码状态：M0、M1、M2 已完成，M3 未开始。** 书架点击已接进阅读器，
阅读器能读段落、点词查本地词典、点「段」给「未配置」提示；段落译文的生成（LLM）还没有。
