package com.englishread.app;

import android.app.Application;

import com.englishread.app.ui.UiTheme;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 进程级入口：业务库单例与共享 IO 线程（解析、入库都在这上面跑）。
 *
 * <p>单线程够用：导入是用户一次点一个的，并发反而要处理写库争锁。
 */
public class App extends Application {

    private ExecutorService io;

    @Override
    public void onCreate() {
        super.onCreate();
        // 界面主题（阅读器之外）必须在任何 Activity 创建之前落到进程上：
        // 晚一步首屏就会先按系统主题画一遍、再闪一下切过去。
        // 阅读器不受影响——它自己在 onCreate 里 setLocalNightMode，local 优先于这里。
        UiTheme.apply(this);
    }

    public ExecutorService io() {
        if (io == null) {
            io = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "englishread-io");
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
        }
        return io;
    }
}
