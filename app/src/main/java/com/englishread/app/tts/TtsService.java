package com.englishread.app.tts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.englishread.app.R;
import com.englishread.app.ui.ReaderActivity;

/**
 * 听书的前台服务：持有 {@link TtsController}，负责通知栏与「退出阅读器后继续念」。
 *
 * <p>为什么要服务：锁屏/切后台还能控制就得有常驻通知，而常驻通知必须由前台服务发。
 * 朗读状态全在 controller 里，这里只做三件事：绑定的界面 ↔ controller 转发、
 * 通知栏按钮 → controller 命令、controller 状态 → 通知内容。
 *
 * <p>**没做 MediaSession**（要引 androidx.media）：通知栏只能给按钮，
 * 蓝牙耳机/车载上的播放键收不到。等有需要再加依赖，现在这层不值得。
 */
public class TtsService extends Service implements TtsController.Listener {

    public static final String ACTION_TOGGLE = "com.englishread.app.action.TTS_TOGGLE";
    public static final String ACTION_PREV = "com.englishread.app.action.TTS_PREV";
    public static final String ACTION_NEXT = "com.englishread.app.action.TTS_NEXT";
    public static final String ACTION_STOP = "com.englishread.app.action.TTS_STOP";

    /** 阅读器要用的一组回调，全部在主线程。 */
    public interface UiListener {
        void onTtsSnapshot(TtsController.Snapshot snapshot);

        void onTtsHighlight(long paraId, int start, int end);

        void onTtsError(String message);

        void onTtsFinished();
    }

    public class LocalBinder extends Binder {
        public TtsService service() {
            return TtsService.this;
        }
    }

    private static final String CHANNEL_ID = "tts_playback";
    private static final int NOTE_ID = 1001;
    private static final int REQ_OPEN = 10;
    private static final int REQ_PREV = 11;
    private static final int REQ_TOGGLE = 12;
    private static final int REQ_NEXT = 13;
    private static final int REQ_STOP = 14;

    private final IBinder binder = new LocalBinder();
    private TtsController controller;
    private UiListener ui;
    private NotificationManager notifications;
    private boolean foreground;

    @Override
    public void onCreate() {
        super.onCreate();
        notifications = getSystemService(NotificationManager.class);
        createChannel();
        controller = new TtsController(this, this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 从 startForegroundService 进来必须尽快 startForeground（系统只等 5 秒）
        ensureForeground();

        String action = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(action)) {
            toggle();
        } else if (ACTION_PREV.equals(action)) {
            controller.prev();
        } else if (ACTION_NEXT.equals(action)) {
            controller.next();
        } else if (ACTION_STOP.equals(action)) {
            controller.stop();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (controller != null) {
            controller.release();
        }
        super.onDestroy();
    }

    /** 从最近任务里划掉 App 就停下：没人看着的朗读只是耗电。 */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        controller.stop();
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    // ---- 给界面用的命令 ----

    public void setUiListener(@Nullable UiListener listener) {
        ui = listener;
        if (listener != null) {
            // 界面重建/切后台回来：先给一份当前状态，高亮与按钮才接得上
            listener.onTtsSnapshot(controller.snapshot());
        }
    }

    public void play(long bookId, int chapterIdx, int paraIdx) {
        controller.start(bookId, chapterIdx, paraIdx);
    }

    public void pause() {
        controller.pause();
    }

    public void resume() {
        controller.resume();
    }

    /** 退出朗读：停声、撤通知、收高亮，全在 {@code onState(STATE_IDLE)} 里收尾。 */
    public void stop() {
        controller.stop();
    }

    public void toggle() {
        TtsController.Snapshot s = controller.snapshot();
        if (s.state == TtsController.STATE_PLAYING) {
            controller.pause();
        } else if (s.state == TtsController.STATE_PAUSED) {
            controller.resume();
        }
    }

    public void setRate(float rate) {
        controller.setRate(rate);
    }

    // ---- controller 回调（主线程） ----

    @Override
    public void onState(int state) {
        if (state == TtsController.STATE_IDLE) {
            dropForeground();
            pushSnapshot();
            stopSelf();
            return;
        }
        ensureForeground();
        updateNotification();
        pushSnapshot();
    }

    @Override
    public void onMoved() {
        updateNotification();
        pushSnapshot();
    }

    @Override
    public void onHighlight(long paraId, int start, int end) {
        if (ui != null) {
            ui.onTtsHighlight(paraId, start, end);
        }
    }

    @Override
    public void onError(int code) {
        // 连引擎都没起来时状态一直是 IDLE，不会走 onState，这里得自己把前台撤掉，
        // 否则会挂着一条「准备朗读」的通知再也不消失。
        if (controller.snapshot().state == TtsController.STATE_IDLE) {
            dropForeground();
            stopSelf();
        }
        if (ui != null) {
            ui.onTtsError(TtsController.errorText(this, code));
        }
    }

    @Override
    public void onFinished() {
        if (ui != null) {
            ui.onTtsFinished();
        }
    }

    private void pushSnapshot() {
        if (ui != null) {
            ui.onTtsSnapshot(controller.snapshot());
        }
    }

    // ---- 通知 ----

    private void ensureForeground() {
        if (foreground) {
            return;
        }
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTE_ID, n);
        }
        foreground = true;
    }

    private void dropForeground() {
        if (!foreground) {
            return;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
    }

    private void updateNotification() {
        if (!foreground) {
            return;
        }
        notifications.notify(NOTE_ID, buildNotification());
    }

    private Notification buildNotification() {
        TtsController.Snapshot s = controller.snapshot();
        String title = s.chapterTitle == null || s.chapterTitle.isEmpty()
                ? getString(R.string.app_name) : s.chapterTitle;
        String text = s.preview == null || s.preview.isEmpty()
                ? getString(R.string.tts_preparing) : s.preview;
        boolean playing = s.state == TtsController.STATE_PLAYING;

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tts_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openIntent(s.bookId))
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                // 系统最多同时摆 3 个按钮（第 4 个直接不渲染，API 36 实测），
                // 所以「停止」进不了按钮栏——改成「划掉通知就是停止」：
                // 不设 ongoing + deleteIntent 打 ACTION_STOP，这也是常见播放器的关法。
                .setOngoing(false)
                .setDeleteIntent(actionIntent(ACTION_STOP, REQ_STOP))
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_media_previous, getString(R.string.tts_prev),
                        actionIntent(ACTION_PREV, REQ_PREV))
                .addAction(android.R.drawable.ic_media_play,
                        getString(playing ? R.string.tts_pause : R.string.tts_resume),
                        actionIntent(ACTION_TOGGLE, REQ_TOGGLE))
                .addAction(android.R.drawable.ic_media_next, getString(R.string.tts_next),
                        actionIntent(ACTION_NEXT, REQ_NEXT));
        return b.build();
    }

    private PendingIntent openIntent(long bookId) {
        Intent i = new Intent(this, ReaderActivity.class)
                .putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, REQ_OPEN, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent i = new Intent(this, TtsService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                getString(R.string.tts_channel), NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(getString(R.string.tts_channel_desc));
        ch.setShowBadge(false);
        notifications.createNotificationChannel(ch);
    }
}
