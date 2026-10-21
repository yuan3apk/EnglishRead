package com.englishread.app.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.englishread.app.App;
import com.englishread.app.R;
import com.englishread.app.data.Db;
import com.englishread.app.data.DictSource;
import com.englishread.app.data.ParagraphDao;
import com.englishread.app.data.ReadingPosDao;
import com.englishread.app.data.VocabDao;
import com.englishread.app.model.Chapter;
import com.englishread.app.model.Paragraph;
import com.englishread.app.model.VocabEntry;
import com.englishread.app.model.WordEntry;
import com.englishread.app.parse.Tokenizer;
import com.englishread.app.reader.ReaderAdapter;
import com.englishread.app.reader.ReaderPrefs;
import com.englishread.app.reader.TocAdapter;
import com.englishread.app.reader.TranslationPopup;
import com.englishread.app.trans.ApiConfig;
import com.englishread.app.trans.LlmClient;
import com.englishread.app.trans.TransQueue;
import com.englishread.app.tts.TtsController;
import com.englishread.app.tts.TtsService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

/**
 * 阅读器。正文铺满屏幕，菜单默认收起——点屏幕中间才滑入上下两条。
 *
 * <p>手势优先级（用户已定「词优先」）：
 * 点在单词上 → 查词；点在空白/标点/行尾 → 按左中右分区翻屏或呼菜单。
 */
public class ReaderActivity extends AppCompatActivity
        implements ReaderAdapter.Listener, TtsService.UiListener {

    public static final String EXTRA_BOOK_ID = "book_id";

    private RecyclerView list;
    private LinearLayoutManager layoutManager;
    private ReaderAdapter adapter;
    private TocAdapter tocAdapter;
    private ParagraphDao dao;
    private DictSource dict;
    private TranslationPopup popup;
    private ReaderPrefs prefs;
    private ApiConfig api;
    private TransQueue trans;
    /** 词卡对应的段与词：卡片上的「加生词本」要把语境一起存下来。 */
    private Paragraph cardPara;
    private String cardWord;

    private View topBar;
    private View bottomBar;
    private View settingsPanel;
    private View brightnessPanel;
    private TextView chapterTitle;
    private TextView bookSubtitle;
    private TextView statusView;
    private MaterialButton btnListen;
    /** 听书中才出现在右上角的「退出朗读」：菜单收着也够得着。 */
    private MaterialButton btnTtsExit;
    private androidx.drawerlayout.widget.DrawerLayout drawer;

    private long bookId;
    private List<Chapter> chapters = new ArrayList<>();
    private List<Paragraph> paragraphs = new ArrayList<>();
    private int currentIdx;
    private boolean menuShown;

    // ---- 听书（M4）----
    private TtsService ttsService;
    /** bindService 返回 true 才可 unbind：没绑上就解会抛 IllegalArgumentException。 */
    private boolean ttsBindRequested;
    private boolean ttsBound;
    private int ttsState = TtsController.STATE_IDLE;
    /** 最近一次快照：判断「在念的是不是这本书」、通知栏开关状态都靠它。 */
    private TtsController.Snapshot lastTts;
    /** 章节还没加载完时收到的朗读位置，等加载完再回填。 */
    private TtsController.Snapshot pendingTts;
    private ActivityResultLauncher<String> notifPermission;

    private final ServiceConnection ttsConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!ttsBindRequested) {
                return; // onStart/onStop 之间已经解绑，别把界面再挂到服务上
            }
            ttsService = ((TtsService.LocalBinder) binder).service();
            ttsBound = true;
            ttsService.setRate(prefs.speechRate());
            // 挂上就会立刻回一份当前状态：从书架回来/转屏重建时高亮与按钮靠它接上
            ttsService.setUiListener(ReaderActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            ttsBound = false;
            ttsService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = new ReaderPrefs(this);
        applyTheme();

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reader_root);

        // Android 13+ 通知要运行时授权。拒绝也照样能念（前台服务本身是合法的），
        // 只是锁屏/后台看不到控制，所以提示一句就继续，不拦着。
        notifPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (!granted) {
                        Toast.makeText(this, R.string.tts_no_notification, Toast.LENGTH_LONG).show();
                    }
                    startListening();
                });

        bookId = getIntent().getLongExtra(EXTRA_BOOK_ID, -1);
        if (bookId < 0) {
            finish();
            return;
        }

        dao = new ParagraphDao(Db.of(this));
        dict = new DictSource(this);
        api = new ApiConfig(this);
        trans = new TransQueue(dao, new LlmClient(api), ((App) getApplication()).io());

        bindViews();
        setupList();
        setupMenu();
        setupSettings();
        setupBrightness();
        setupPopup();
        applyWindowInsets();
        applyBrightness();

        loadChapters();
    }

    private void bindViews() {
        list = findViewById(R.id.para_list);
        topBar = findViewById(R.id.top_bar);
        bottomBar = findViewById(R.id.bottom_bar);
        settingsPanel = findViewById(R.id.settings_panel);
        brightnessPanel = findViewById(R.id.brightness_panel);
        chapterTitle = findViewById(R.id.chapter_title);
        bookSubtitle = findViewById(R.id.book_subtitle);
        statusView = findViewById(R.id.reader_status);
        btnListen = findViewById(R.id.btn_listen);
        btnTtsExit = findViewById(R.id.btn_tts_exit);
        drawer = findViewById(R.id.drawer_layout);
    }

    /**
     * 听书的服务和阅读器同生命周期地绑定/解绑。
     *
     * <p>解绑不停播：朗读归服务管，退回书架、锁屏都该继续——这正是 M4 要的后台播放。
     * 回来时 {@link TtsService#setUiListener} 会补一份状态，高亮与按钮接得上。
     */
    @Override
    protected void onStart() {
        super.onStart();
        ttsBindRequested = bindService(new Intent(this, TtsService.class), ttsConn,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        if (ttsService != null) {
            ttsService.setUiListener(null);
        }
        // 用「请求过绑定」判断，不用「已连上」：连接回调还没到就退出的情况下，
        // 只按 ttsBound 判断会漏掉 unbind，绑定与连接就永久挂在进程里了。
        if (ttsBindRequested) {
            try {
                unbindService(ttsConn);
            } catch (IllegalArgumentException ignored) {
                // 服务已被系统回收：没绑上就别再解一次
            }
            ttsBindRequested = false;
        }
        ttsBound = false;
        ttsService = null;
        super.onStop();
    }

    @Override
    protected void onPause() {
        // 离开页面就记断点：返回书架、进设置、切后台、被杀之前都会走到这里
        saveProgress();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // 退出阅读器就别再让飞行中的请求把译文写回界面
        if (trans != null) {
            trans.cancel();
        }
        super.onDestroy();
    }

    /**
     * 记「第一段可见的句子 + 它被滚上去多少」。不记整章滚动像素——字号、段距一改像素就作废，
     * 而「哪一句 + 句内偏移」换排版后依然对得上。
     */
    private void saveProgress() {
        if (chapters.isEmpty() || paragraphs.isEmpty()) {
            return; // 还没加载完就退出，别把进度冲成第 0 段
        }
        int pos = layoutManager.findFirstVisibleItemPosition();
        if (pos == RecyclerView.NO_POSITION || pos >= paragraphs.size()) {
            return;
        }
        View first = layoutManager.findViewByPosition(pos);
        int offsetPx = first == null ? 0 : first.getTop() - list.getPaddingTop();
        float pct = ReadingPosDao.progressPct(chapters, currentIdx, pos + 1);
        long chapterId = chapters.get(currentIdx).id;
        long paraId = paragraphs.get(pos).id;
        ((App) getApplication()).io().execute(() ->
                new ReadingPosDao(Db.of(getApplicationContext()))
                        .save(bookId, chapterId, paraId, offsetPx, pct));
    }

    /**
     * 音量键上下翻一屏。**菜单收起时才接管**——顶栏/底栏露出来就说明用户在操作界面
     * （听书时尤其要调音量），这时候把键还给系统，返回 false 让音量照常变。
     *
     * <p>只处理「短按」：长按（repeatCount &gt; 0）也交回系统，避免一手滑过整个音量条就滚过半章。
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyDown(keyCode, event);
        }
        if (menuShown) {
            return super.onKeyDown(keyCode, event); // 菜单开着：音量键调音量，不翻页
        }
        if (event.getRepeatCount() > 0) {
            return super.onKeyDown(keyCode, event); // 长按不连翻
        }
        if (popup != null && popup.isShowing()) {
            popup.dismiss(); // 词卡开着时，音量键先关卡片，别滚走
            return true;
        }
        scrollByScreen(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ? 1 : -1);
        return true;
    }

    /** 上下滚一屏（音量键专用）。到头就顺延到相邻章节。 */
    private void scrollByScreen(int dir) {
        if (!list.canScrollVertically(dir)) {
            int target = currentIdx + dir;
            if (target >= 0 && target < chapters.size()) {
                openChapter(target, dir > 0);
            }
            return;
        }
        int viewport = list.getHeight() - list.getPaddingTop() - list.getPaddingBottom();
        list.smoothScrollBy(0, dir * viewport);
    }

    private void setupList() {
        layoutManager = new LinearLayoutManager(this) {
            /**
             * 高度必须按 **UNSPECIFIED** 量，不能用 RecyclerView 默认的「最多一屏」。
             *
             * <p>默认那条路给 wrap_content 子 View 的是 {@code AT_MOST(一屏高)}：一段比一屏长时，
             * View 只有一屏高、后面的行直接被裁掉——正文看不见也滚不到。这里显式用 UNSPECIFIED，
             * 段落有多高就摆多高，超出部分由 RecyclerView 自己裁剪显示。
             *
             * <p>后面那句 `calculateItemDecorationsForChild` 不能省：默认实现顺带算好了装饰边距，
             * 省掉它 ItemDecoration 会**静默失效**；宽度也要扣掉 insets。
             */
            @Override
            public void measureChildWithMargins(@NonNull View child, int widthUsed, int heightUsed) {
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
                calculateItemDecorationsForChild(child, decorInsets);
                int w = getWidth() - getPaddingLeft() - getPaddingRight()
                        - lp.leftMargin - lp.rightMargin - widthUsed
                        - decorInsets.left - decorInsets.right;
                child.measure(
                        View.MeasureSpec.makeMeasureSpec(Math.max(0, w), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            }

            private final android.graphics.Rect decorInsets = new android.graphics.Rect();
        };
        adapter = new ReaderAdapter(this);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        applyDisplay();

        // 滚动时若菜单开着就收起来，别挡着正文
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy != 0 && menuShown) {
                    hideMenu();
                }
                updateStatus();
            }
        });

        installBlankTapHandler();
    }

    /**
     * 段落 TextView 只覆盖正文那几行，屏幕下半屏的空白没有任何 View 消费触摸，
     * 点上去会直接落空。这里在列表上再挂一层手势，专门接住「没打到正文」的点击——
     * 一句话一段之后，句与句之间那道缝也是明确「这里没有字」的地方，点它同样呼菜单。
     * 返回 false 保证滚动照旧。
     */
    private void installBlankTapHandler() {
        final android.view.GestureDetector g = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(android.view.MotionEvent e) {
                        onBlankTap();
                        return true;
                    }
                });
        list.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                // 只有没打在正文上才算空白；打在正文上的交给段落自己的手势
                blankTapCandidate = !hitContent(v, event);
            }
            if (blankTapCandidate) {
                g.onTouchEvent(event);
            }
            return false;
        });
    }

    private boolean blankTapCandidate;

    /**
     * 事件是否打在「有内容的 View」上：正文、译文、失败重试、「段」按钮。
     *
     * <p>**不能按整个 item 判**——item 是整屏宽的矩形，段内左右留白也算在它里面，
     * 那样点哪都是「点到内容」，菜单就永远呼不出来。只认真正装了东西的那几个子 View。
     */
    private static boolean hitContent(View list, android.view.MotionEvent e) {
        if (!(list instanceof android.view.ViewGroup)) {
            return false;
        }
        ViewGroup vg = (ViewGroup) list;
        float x = e.getX() + list.getScrollX();
        float y = e.getY() + list.getScrollY();
        View item = findItemAt(vg, x, y);
        if (item == null) {
            return false; // 屏幕下半屏那种连 item 都没有的空白
        }
        float ix = x - item.getLeft();
        float iy = y - item.getTop();
        int[] ids = {R.id.para_text, R.id.para_trans, R.id.para_retry,
                R.id.para_progress, R.id.btn_translate};
        for (int id : ids) {
            View c = item.findViewById(id);
            if (c != null && c.getVisibility() == View.VISIBLE
                    && ix >= c.getLeft() && ix < c.getRight()
                    && iy >= c.getTop() && iy < c.getBottom()) {
                return true;
            }
        }
        return false;
    }

    /** 坐标（RecyclerView 内容坐标系）落在哪个 item 上，没落在任何 item 上返回 null。 */
    private static View findItemAt(ViewGroup vg, float x, float y) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (x >= child.getLeft() && x < child.getRight()
                    && y >= child.getTop() && y < child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    private void setupMenu() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_toc).setOnClickListener(v -> openToc());
        findViewById(R.id.btn_chapter_prev).setOnClickListener(v -> stepChapter(-1));
        findViewById(R.id.btn_chapter_next).setOnClickListener(v -> stepChapter(1));
        btnListen.setOnClickListener(v -> onListenClick());
        btnTtsExit.setOnClickListener(v -> exitListening());
        findViewById(R.id.btn_settings).setOnClickListener(v -> toggleSettings());
        findViewById(R.id.btn_brightness).setOnClickListener(v -> toggleBrightness());
        // 翻译服务配置入口放在阅读器里：用户是在这里撞上「未配置」的，
        // 让他在同一个地方能改，而不是回书架再找一层菜单。
        findViewById(R.id.btn_api_settings).setOnClickListener(v ->
                startActivity(new Intent(this, TranslationSettingsActivity.class)));

        tocAdapter = new TocAdapter(index -> {
            drawer.closeDrawers();
            openChapter(index);
        });
        RecyclerView tocList = findViewById(R.id.toc_list);
        tocList.setLayoutManager(new LinearLayoutManager(this));
        tocList.setAdapter(tocAdapter);

        // 菜单初始在屏幕外
        hideMenuImmediate();
    }

    private void setupPopup() {
        popup = new TranslationPopup(this, new TranslationPopup.Listener() {
            @Override
            public void onSpeak(String word) {
                Toast.makeText(ReaderActivity.this, word, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAddVocab(WordEntry entry) {
                addToVocab(cardPara, cardWord, entry);
            }
        });
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.reader);
        View drawer = findViewById(R.id.toc_drawer);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 正文让出状态栏和导航栏，菜单自己再加一层内边距
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            // 目录抽屉是 DrawerLayout 的兄弟，不在 reader 的 padding 里。不单独垫的话，
            // 「目录」这个标题会直接压在状态栏的时间上（实测）。左右内边距保持原样。
            drawer.setPadding(drawer.getPaddingLeft(), bars.top,
                    drawer.getPaddingRight(), bars.bottom);
            return insets;
        });
    }

    // ---- 主题 / 亮度 / 显示设置 ----

    /**
     * 明暗只作用于阅读器。
     *
     * <p>这里**不能**用 {@code setDefaultNightMode()}：它是进程级的，会改掉整个 App 的默认模式，
     * 并把已创建的 Activity（书架）一起重建成夜间资源——表现出来就是「改完阅读器，回主页也黑了」。
     * local night mode 只改本 Activity 的 uiMode，主页仍按系统走。
     *
     * <p>必须在 {@code super.onCreate()} 之前调用，首次创建时才会按新模式解析资源。重建后这段会
     * 重跑，模式从 prefs 读回来，不依赖框架是否替我们记住上一次的 local 值。
     */
    private void applyTheme() {
        getDelegate().setLocalNightMode(nightMode(prefs.dark()));
    }

    /** prefs 的三态 → AppCompat 的模式常量。 */
    private static int nightMode(Boolean dark) {
        return dark == null ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                : (dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void applyBrightness() {
        if (prefs.followSystemBrightness()) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(lp);
        } else {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = prefs.brightness();
            getWindow().setAttributes(lp);
        }
    }

    private void applyDisplay() {
        adapter.applyDisplay(prefs);
    }

    // ---- 章节 ----

    /** 首次打开书时的断点，只消费一次（回填完就置空）。 */
    private ReadingPosDao.Pos pendingRestore;

    private void loadChapters() {
        ((App) getApplication()).io().execute(() -> {
            List<Chapter> cs = dao.chapters(bookId);
            // 同一次 IO 里把断点也读出来，省一次线程切换
            ReadingPosDao.Pos pos = new ReadingPosDao(Db.of(this)).load(bookId);
            runOnUiThread(() -> {
                if (cs.isEmpty()) {
                    finish();
                    return;
                }
                chapters = cs;
                tocAdapter.submit(chapters, 0);
                int idx = indexOfChapter(pos);
                pendingRestore = idx < 0 ? null : pos;
                // 正在朗读（可能是切后台后回来、也可能是转屏重建）：跟着朗读走，别回断点章节
                if (pendingTts != null && pendingTts.state != TtsController.STATE_IDLE
                        && pendingTts.bookId == bookId) {
                    idx = pendingTts.chapterIdx;
                    pendingRestore = null;
                }
                openChapter(Math.max(0, idx));
            });
        });
    }

    /** 断点落在第几章；没读过、或书被删了重导过（章节对不上）返回 -1 从头读。 */
    private int indexOfChapter(ReadingPosDao.Pos pos) {
        if (pos == null) {
            return -1;
        }
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).id == pos.chapterId) {
                return i;
            }
        }
        return -1;
    }

    private void openChapter(int idx) {
        openChapter(idx, true);
    }

    /**
     * @param toStart 从章首开始读。往后翻进下一章为 true，往回翻进上一章为 false（落章尾）。
     */
    private void openChapter(int idx, final boolean toStart) {
        if (chapters == null || idx < 0 || idx >= chapters.size()) {
            return;
        }
        currentIdx = idx;
        Chapter ch = chapters.get(idx);
        chapterTitle.setText(ch.title);
        // 副标题只说「第几章 / 共几章」；位置由左下角状态显示，两处各司其职
        bookSubtitle.setText(getString(R.string.reader_chapter_position,
                idx + 1, chapters.size()));

        ((App) getApplication()).io().execute(() -> {
            List<Paragraph> ps = dao.paragraphs(ch.id);
            runOnUiThread(() -> {
                paragraphs = ps;
                adapter.submit(ps);
                if (pendingRestore != null && pendingRestore.chapterId == ch.id) {
                    restore(pendingRestore);
                    pendingRestore = null;
                } else {
                    // 往回翻时落在章尾，接着往下读的手感才连贯
                    list.scrollToPosition(toStart ? 0 : Math.max(0, ps.size() - 1));
                }
                tocAdapter.setCurrent(idx);
                updateStatus();
                applyPendingTts();
            });
        });
    }

    /** 落回上次那一段那一行。段找不到（书换过/段落被删）就退回章首，不猜。 */
    private void restore(ReadingPosDao.Pos pos) {
        for (int i = 0; i < paragraphs.size(); i++) {
            if (paragraphs.get(i).id == pos.paraId) {
                // 用 offset 而不是 scrollToPosition：只有它能把「读到某句中间」也还原回来
                layoutManager.scrollToPositionWithOffset(i, pos.offsetPx);
                // 布局要下一帧才生效，状态栏里的「第几句」得等它落位再刷新
                list.post(this::updateStatus);
                return;
            }
        }
        list.scrollToPosition(0);
    }

    private void stepChapter(int delta) {
        int target = currentIdx + delta;
        if (target >= 0 && target < chapters.size()) {
            // 下一章从章首读，上一章落在章尾——和后一章的衔接才顺
            openChapter(target, delta > 0);
        }
    }

    /** 章节切换按钮的可用态由当前章决定。 */
    private void fixChapterButtons() {
        findViewById(R.id.btn_chapter_prev).setEnabled(currentIdx > 0);
        findViewById(R.id.btn_chapter_next).setEnabled(currentIdx < chapters.size() - 1);
    }

    private void updateStatus() {
        if (chapters.isEmpty()) {
            return;
        }
        // 没有「屏」的概念，用「第几句/总句数」定位（一句话就是一段）
        int first = layoutManager.findFirstVisibleItemPosition();
        int pos = first == RecyclerView.NO_POSITION ? 1 : first + 1;
        int total = paragraphs.size();
        String text = getString(R.string.reader_progress, currentIdx + 1, pos, total);
        if (ttsState == TtsController.STATE_PLAYING && isThisBookSpeaking()) {
            text = "▶ " + text;
        }
        statusView.setText(text);
        fixChapterButtons();
    }

    // ---- 菜单 ----

    private void showMenu() {
        if (menuShown) {
            return;
        }
        menuShown = true;
        animateIn(topBar, -1f);
        animateIn(bottomBar, 1f);
    }

    private void hideMenu() {
        if (!menuShown) {
            return;
        }
        menuShown = false;
        animateOut(topBar, -1f);
        animateOut(bottomBar, 1f);
        settingsPanel.setVisibility(View.GONE);
        brightnessPanel.setVisibility(View.GONE);
    }

    private void hideMenuImmediate() {
        topBar.setTranslationY(-topBar.getHeight() > 0 ? -topBar.getHeight() : -400f);
        bottomBar.setTranslationY(400f);
        topBar.setVisibility(View.INVISIBLE);
        bottomBar.setVisibility(View.INVISIBLE);
        menuShown = false;
    }

    private void animateIn(View v, float dir) {
        v.setVisibility(View.VISIBLE);
        v.post(() -> v.animate()
                .translationY(0f)
                .setDuration(180)
                .start());
    }

    private void animateOut(View v, float dir) {
        v.post(() -> v.animate()
                .translationY(dir * v.getHeight())
                .setDuration(180)
                .withEndAction(() -> v.setVisibility(View.INVISIBLE))
                .start());
    }

    private void toggleSettings() {
        boolean show = settingsPanel.getVisibility() != View.VISIBLE;
        if (show) {
            // 面板内容比屏幕还高，全展开会把正文整块盖掉。限到屏幕的 55%（且不超过 560dp），
            // 其余交给面板内部的 ScrollView。给固定高度而不是 wrap_content：一次算完，不用等布局。
            float density = getResources().getDisplayMetrics().density;
            int cap = (int) Math.min(getResources().getDisplayMetrics().heightPixels * 0.55f, 560 * density);
            settingsPanel.getLayoutParams().height = cap;
        }
        settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        brightnessPanel.setVisibility(View.GONE);
        bottomBar.post(this::resetBottomBar);
    }

    private void resetBottomBar() {
        bottomBar.setTranslationY(0f);
    }

    private void toggleBrightness() {
        boolean show = brightnessPanel.getVisibility() != View.VISIBLE;
        brightnessPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            // 亮度浮层跟设置面板二选一：两个都开着会互相挡
            settingsPanel.setVisibility(View.GONE);
            syncBrightnessUi();
        }
    }

    private void openToc() {
        drawer.openDrawer(findViewById(R.id.toc_drawer));
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(findViewById(R.id.toc_drawer))) {
            drawer.closeDrawers();
            return;
        }
        if (menuShown) {
            hideMenu();
            return;
        }
        super.onBackPressed();
    }

    // ---- 设置面板 ----

    private void setupSettings() {
        View p = settingsPanel;

        p.findViewById(R.id.btn_font_smaller).setOnClickListener(v -> bumpTextSize(-1));
        p.findViewById(R.id.btn_font_larger).setOnClickListener(v -> bumpTextSize(1));
        p.findViewById(R.id.btn_spacing_smaller).setOnClickListener(v -> bumpSpacing(-1));
        p.findViewById(R.id.btn_spacing_larger).setOnClickListener(v -> bumpSpacing(1));
        p.findViewById(R.id.btn_para_spacing_smaller).setOnClickListener(v -> bumpParaSpacing(-1));
        p.findViewById(R.id.btn_para_spacing_larger).setOnClickListener(v -> bumpParaSpacing(1));
        p.findViewById(R.id.btn_letter_spacing_smaller).setOnClickListener(v -> bumpLetterSpacing(-1));
        p.findViewById(R.id.btn_letter_spacing_larger).setOnClickListener(v -> bumpLetterSpacing(1));
        p.findViewById(R.id.btn_indent_smaller).setOnClickListener(v -> bumpIndent(-1));
        p.findViewById(R.id.btn_indent_larger).setOnClickListener(v -> bumpIndent(1));

        MaterialSwitch marker = p.findViewById(R.id.switch_marker);
        marker.setChecked(prefs.showMarker());
        marker.setOnCheckedChangeListener((b, checked) -> {
            prefs.setShowMarker(checked);
            applyDisplay();
        });

        p.findViewById(R.id.btn_rate_smaller).setOnClickListener(v -> bumpSpeechRate(-1));
        p.findViewById(R.id.btn_rate_larger).setOnClickListener(v -> bumpSpeechRate(1));
        MaterialSwitch tapPause = p.findViewById(R.id.switch_tap_pause);
        tapPause.setChecked(prefs.tapToPause());
        tapPause.setOnCheckedChangeListener((b, checked) -> prefs.setTapToPause(checked));

        com.google.android.material.button.MaterialButtonToggleGroup themeGroup =
                p.findViewById(R.id.theme_group);
        Boolean dark = prefs.dark();
        themeGroup.check(dark == null ? R.id.theme_system
                : (dark ? R.id.theme_dark : R.id.theme_light));
        themeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            Boolean target = checkedId == R.id.theme_dark ? Boolean.TRUE
                    : checkedId == R.id.theme_light ? Boolean.FALSE : null;
            if (target == null ? prefs.dark() == null : target.equals(prefs.dark())) {
                return;
            }
            prefs.setDark(target);
            // 只重建阅读器自己，书架不受影响；重建后菜单收起，正文保持满屏
            getDelegate().setLocalNightMode(nightMode(target));
        });

        syncSettingLabels();
    }

    private void bumpTextSize(int delta) {
        int[] sizes = ReaderPrefs.TEXT_SIZES;
        int i = Math.max(0, Math.min(sizes.length - 1, prefs.textSizeIndex() + delta));
        prefs.setTextSize(sizes[i]);
        applyDisplay();
        syncSettingLabels();
    }

    private void bumpSpacing(int delta) {
        float[] all = ReaderPrefs.LINE_SPACINGS;
        int i = Math.max(0, Math.min(all.length - 1, prefs.lineSpacingIndex() + delta));
        prefs.setLineSpacing(all[i]);
        applyDisplay();
        syncSettingLabels();
    }

    private void bumpParaSpacing(int delta) {
        int[] all = ReaderPrefs.PARA_SPACINGS;
        int i = Math.max(0, Math.min(all.length - 1, prefs.paraSpacingIndex() + delta));
        prefs.setParaSpacing(all[i]);
        applyDisplay();
        syncSettingLabels();
    }

    private void bumpLetterSpacing(int delta) {
        float[] all = ReaderPrefs.LETTER_SPACINGS;
        int i = Math.max(0, Math.min(all.length - 1, prefs.letterSpacingIndex() + delta));
        prefs.setLetterSpacing(all[i]);
        applyDisplay();
        syncSettingLabels();
    }

    private void bumpIndent(int delta) {
        int[] all = ReaderPrefs.INDENTS;
        int i = Math.max(0, Math.min(all.length - 1, prefs.indentIndex() + delta));
        prefs.setIndent(all[i]);
        applyDisplay();
        syncSettingLabels();
    }

    /** 语速只影响之后念的句子（引擎没有中途变速的能力），所以只推给服务，不重绑列表。 */
    private void bumpSpeechRate(int delta) {
        float[] all = ReaderPrefs.SPEECH_RATES;
        int i = Math.max(0, Math.min(all.length - 1, prefs.speechRateIndex() + delta));
        prefs.setSpeechRate(all[i]);
        if (ttsBound && ttsService != null) {
            ttsService.setRate(all[i]);
        }
        syncSettingLabels();
    }

    /** 把当前档位显示在标签上——不然用户只能靠感觉猜现在多大。 */
    private void syncSettingLabels() {
        TextView size = settingsPanel.findViewById(R.id.label_text_size);
        size.setText(ReaderPrefs.textSizeLabel(this, prefs.textSizeIndex()));
        ((TextView) settingsPanel.findViewById(R.id.label_line_spacing))
                .setText(ReaderPrefs.lineSpacingLabel(prefs.lineSpacingIndex()));
        ((TextView) settingsPanel.findViewById(R.id.label_para_spacing))
                .setText(ReaderPrefs.paraSpacingLabel(this, prefs.paraSpacingIndex()));
        ((TextView) settingsPanel.findViewById(R.id.label_letter_spacing))
                .setText(ReaderPrefs.letterSpacingLabel(this, prefs.letterSpacingIndex()));
        ((TextView) settingsPanel.findViewById(R.id.label_indent))
                .setText(ReaderPrefs.indentLabel(this, prefs.indentIndex()));
        ((TextView) settingsPanel.findViewById(R.id.label_speech_rate))
                .setText(ReaderPrefs.speechRateLabel(this, prefs.speechRateIndex()));
    }

    // ---- 亮度面板 ----

    private void setupBrightness() {
        SeekBar seek = brightnessPanel.findViewById(R.id.brightness_seek);
        MaterialSwitch follow = brightnessPanel.findViewById(R.id.brightness_follow);

        follow.setChecked(prefs.followSystemBrightness());
        follow.setOnCheckedChangeListener((b, checked) -> {
            prefs.setFollowSystemBrightness(checked);
            seek.setEnabled(!checked);
            applyBrightness();
        });

        seek.setEnabled(!prefs.followSystemBrightness());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                // 拉到 0 会黑屏看不到东西，抬一手到 0.05
                prefs.setBrightness(Math.max(0.05f, progress / 100f));
                applyBrightness();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        syncBrightnessUi();
    }

    private void syncBrightnessUi() {
        SeekBar seek = brightnessPanel.findViewById(R.id.brightness_seek);
        MaterialSwitch follow = brightnessPanel.findViewById(R.id.brightness_follow);
        boolean f = prefs.followSystemBrightness();
        follow.setChecked(f);
        seek.setEnabled(!f);
        seek.setProgress(Math.round(prefs.brightness() * 100));
    }

    // ---- 听书（M4）----

    /** 在念的是不是手上这本书。念别的书时不能跟着滚，也不能把按钮显示成「暂停」。 */
    private boolean isThisBookSpeaking() {
        return ttsBound && ttsService != null && lastTts != null
                && lastTts.bookId == bookId && lastTts.state != TtsController.STATE_IDLE;
    }

    private void onListenClick() {
        if (!ttsBound || ttsService == null) {
            Toast.makeText(this, R.string.tts_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isThisBookSpeaking()) {
            if (ttsState == TtsController.STATE_PLAYING) {
                ttsService.pause();
            } else {
                ttsService.resume();
            }
            return;
        }
        if (chapters.isEmpty() || paragraphs.isEmpty()) {
            Toast.makeText(this, R.string.tts_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        startListening();
    }

    /** 退出朗读。按钮与高亮靠回来的 IDLE 快照收起，这里只管发命令。 */
    private void exitListening() {
        if (ttsService != null) {
            ttsService.stop();
        }
    }

    /** 从当前可见的那一句开始念——用户点「听书」时眼睛就在那里。 */
    private void startListening() {
        if (!ttsBound || ttsService == null || paragraphs.isEmpty() || chapters.isEmpty()) {
            return;
        }
        int pos = layoutManager.findFirstVisibleItemPosition();
        if (pos == RecyclerView.NO_POSITION || pos >= paragraphs.size()) {
            pos = 0;
        }
        // 先起前台服务再下命令：onStartCommand 里立刻 startForeground，不会超 5 秒
        ContextCompat.startForegroundService(this, new Intent(this, TtsService.class));
        ttsService.setRate(prefs.speechRate());
        ttsService.play(bookId, currentIdx, pos);
    }

    @Override
    public void onTtsSnapshot(TtsController.Snapshot s) {
        lastTts = s;
        ttsState = s.state;
        syncListenButton();
        updateStatus();

        if (s.state == TtsController.STATE_IDLE || s.bookId != bookId) {
            // 停了，或者念的是另一本书：这本书不该再留着高亮
            adapter.clearHighlight();
            return;
        }
        if (chapters.isEmpty()) {
            pendingTts = s; // 章节还没读回来，等 openChapter 完再补
            return;
        }
        if (s.chapterIdx != currentIdx) {
            pendingTts = s;
            openChapter(s.chapterIdx, true);
            return;
        }
        showTtsPosition(s);
    }

    private void applyPendingTts() {
        if (pendingTts == null) {
            return;
        }
        TtsController.Snapshot s = pendingTts;
        pendingTts = null;
        if (s.state != TtsController.STATE_IDLE && s.bookId == bookId && s.chapterIdx == currentIdx) {
            showTtsPosition(s);
        }
    }

    private void showTtsPosition(TtsController.Snapshot s) {
        if (s.end > s.start) {
            adapter.setHighlight(s.paraId, s.start, s.end);
            scrollToParagraph(s.paraIdx);
        }
    }

    /** 朗读跟读：只在目标句看不见时才滚，用户正拖着列表时不抢。 */
    private void scrollToParagraph(int pos) {
        if (pos < 0 || pos >= adapter.getItemCount()) {
            return;
        }
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        if (first != RecyclerView.NO_POSITION && pos >= first && pos <= last) {
            return;
        }
        if (list.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
            return;
        }
        layoutManager.scrollToPositionWithOffset(pos, Math.max(0, list.getHeight() / 4));
    }

    @Override
    public void onTtsHighlight(long paraId, int start, int end) {
        if (!isThisBookSpeaking() || ttsState != TtsController.STATE_PLAYING) {
            return;
        }
        adapter.setHighlight(paraId, start, end);
        int pos = adapter.positionOf(paraId);
        if (pos >= 0) {
            scrollToParagraph(pos);
        }
    }

    @Override
    public void onTtsError(String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tts_error_title)
                .setMessage(message)
                .setPositiveButton(R.string.tts_open_settings, (d, w) -> openTtsSettings())
                .setNegativeButton(R.string.tts_error_close, null)
                .show();
    }

    @Override
    public void onTtsFinished() {
        if (!isFinishing() && !isDestroyed()) {
            Toast.makeText(this, R.string.tts_finished, Toast.LENGTH_SHORT).show();
        }
    }

    private void openTtsSettings() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (Exception e) {
            // 各家 ROM 未必有这个页面；至少给条能自己找的路
            Toast.makeText(this, R.string.tts_no_settings_page, Toast.LENGTH_LONG).show();
        }
    }

    private void syncListenButton() {
        boolean mine = lastTts != null && lastTts.bookId == bookId;
        // 「退出朗读」只在本书真有朗读会话时出现：念着或暂停着都算，IDLE 才收起
        boolean active = mine && ttsState != TtsController.STATE_IDLE;
        btnTtsExit.setVisibility(active ? View.VISIBLE : View.GONE);
        if (mine && ttsState == TtsController.STATE_PLAYING) {
            btnListen.setText(R.string.reader_listen_pause);
        } else if (mine && ttsState == TtsController.STATE_PAUSED) {
            btnListen.setText(R.string.reader_listen_resume);
        } else {
            btnListen.setText(R.string.reader_listen);
        }
    }

    // ---- 点词 / 段按钮 ----

    @Override
    public void onWordTap(Paragraph p, String word, int start, int end) {
        // 「点正文暂停」是可选手感，默认关：点词查词是核心动作（D2 词优先），不能被听书抢走
        if (prefs.tapToPause() && isThisBookSpeaking()) {
            ttsService.toggle();
            return;
        }
        showWordCard(p, word, start, end);
    }

    @Override
    public void onWordLongPress(Paragraph p, String word, int start, int end) {
        // 长按 = 一键加生词本。M3 补丁 3 修好事件分发后，长按才是真长按；
        // 单击查词仍是主路径（D2 词优先），长按只是省一步「先弹卡再点加生词本」。
        addToVocab(p, word, null);
    }

    /**
     * 加入生词本。两条入口（长按、词卡上的「加生词本」）共用这一条路：
     * 查词（本地词典，不联网）在 IO 上做，落库与提示只有 {@link #storeVocab} 一个出口。
     *
     * @param entry 词卡已经查过词时直接传进来，省一次词典查询
     */
    private void addToVocab(Paragraph p, String word, WordEntry entry) {
        if (p == null || word == null) {
            return;
        }
        // 章节 id 在主线程取好再进 IO：进去之后 currentIdx 可能已经被翻章改掉
        final long chapterId = chapters.isEmpty() ? 0 : chapters.get(currentIdx).id;
        final Paragraph para = p;
        final String w = word;
        ((App) getApplication()).io().execute(() -> {
            WordEntry e = entry != null ? entry : dict.lookup(Tokenizer.lookupKey(w));
            storeVocab(vocabOf(para, w, e, chapterId));
        });
    }

    /** 落库 + 提示。**只在 IO 线程调用**（要写库）。 */
    private void storeVocab(VocabEntry v) {
        long id = new VocabDao(Db.of(getApplicationContext())).add(v);
        runOnUiThread(() -> Toast.makeText(this,
                getString(id < 0 ? R.string.vocab_exists_word : R.string.vocab_added_word, v.word),
                Toast.LENGTH_SHORT).show());
    }

    /**
     * 生词本一行。语境就是这一段的原文——正文一句话一段（M2 补丁 5），
     * 所以「命中处的原句」不用再从段里切一次。
     */
    private VocabEntry vocabOf(Paragraph p, String word, WordEntry entry, long chapterId) {
        VocabEntry v = new VocabEntry();
        v.word = word;
        String key = Tokenizer.lookupKey(word);
        v.lemma = entry != null && entry.lemma != null && !entry.lemma.isEmpty()
                ? entry.lemma : key;
        v.phon = entry == null ? null : entry.phonetic;
        v.translation = entry == null ? null : entry.translation;
        v.sentence = p.text;
        v.bookId = bookId;
        v.chapterId = chapterId;
        v.paraId = p.id;
        return v; // createdAt 留 0，DAO 落库时补当前时间
    }

    /** 查词走 IO 线程，弹窗回主线程；两条手势共用这一条路径。 */
    private void showWordCard(Paragraph p, String word, int start, int end) {
        int[] anchor = wordRect(p, start, end);
        if (anchor == null) {
            return; // 段落还没画出来，宁可不弹
        }
        cardPara = p;
        cardWord = word;
        String key = Tokenizer.lookupKey(word);
        ((App) getApplication()).io().execute(() -> {
            WordEntry entry = dict.lookup(key);
            runOnUiThread(() -> popup.show(findViewById(R.id.reader), anchor, word, entry));
        });
    }

    /**
     * 词的矩形：[x, y, w, h]，屏幕坐标。
     * getPrimaryHorizontal 返回 Layout 坐标，要加回 TextView 内边距才对得上。
     */
    private int[] wordRect(Paragraph p, int start, int end) {
        int pos = paragraphs.indexOf(p);
        if (pos < 0) {
            return null;
        }
        RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(pos);
        if (!(vh instanceof ReaderAdapter.VH)) {
            return null;
        }
        TextView tv = ((ReaderAdapter.VH) vh).text;
        android.text.Layout layout = tv.getLayout();
        if (layout == null) {
            return null;
        }
        int line = layout.getLineForOffset(start);
        int left = (int) layout.getPrimaryHorizontal(start) + tv.getTotalPaddingLeft();
        int right = (int) layout.getPrimaryHorizontal(end) + tv.getTotalPaddingLeft();
        int top = layout.getLineTop(line) + tv.getTotalPaddingTop();
        int bottom = layout.getLineBottom(line) + tv.getTotalPaddingTop();

        int[] loc = new int[2];
        tv.getLocationOnScreen(loc);
        return new int[]{loc[0] + left, loc[1] + top, right - left, bottom - top};
    }

    @Override
    public void onTranslateToggle(Paragraph p) {
        // 已有译文：只是显隐，绝不重复请求（控成本的命门，HANDOFF 2.2）
        if (p.transStatus == Paragraph.STATUS_DONE && p.trans != null) {
            adapter.toggleExpand(p);
            return;
        }
        if (!api.isConfigured()) {
            Toast.makeText(this, R.string.reader_need_api_key, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, TranslationSettingsActivity.class));
            return;
        }
        translateOne(p);
    }

    @Override
    public void onTranslateRetry(Paragraph p) {
        if (!api.isConfigured()) {
            Toast.makeText(this, R.string.reader_need_api_key, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, TranslationSettingsActivity.class));
            return;
        }
        // 不在这里写库（会是主线程写库）：成功后 TransQueue 会覆盖成 DONE，
        // 再失败又会落 FAILED，中间态本来就不需要持久化
        p.transStatus = Paragraph.STATUS_NONE;
        adapter.updateParagraph(p);
        translateOne(p);
    }

    /**
     * 只译被点的那一句。
     *
     * <p>以前从这个段起往后捎带最多 5 段——省一次往返，但代价是点一段会**同时弹出下面几段的译文**，
     * 用户看到的不是自己点的那一句。所以批量只留 {@link TransQueue} 内部的协议切批，
     * 入口这里永远只送一段。
     */
    private void translateOne(Paragraph p) {
        List<Paragraph> batch = java.util.Collections.singletonList(p);
        adapter.setPending(batch, true);
        trans.translate(batch, (done, error) -> onTranslated(batch, done, error));
    }

    private void onTranslated(List<Paragraph> batch, List<Paragraph> done, String error) {
        // 只收这一批的「翻译中」。按批清而不是整表清，才不会抹掉另一批还在飞的提示
        adapter.setPending(batch, false);
        for (Paragraph p : batch) {
            adapter.updateParagraph(p);
        }
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 点在空白上（正文外、句与句的缝里都算）。
     *
     * <p>只有一个意思：呼出菜单（开着就收起）。**不再做左中右分区**——
     * 只剩上下滚动，点两侧再去翻页既没意义又会误触，而菜单是这里唯一常驻的功能。
     */
    @Override
    public void onBlankTap() {
        if (menuShown) {
            hideMenu();
        } else {
            showMenu();
        }
    }

}
