package com.englishread.app;

import android.app.Application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 进程级入口：业务库单例与共享 IO 线程（解析、入库都在这上面跑）。
 *
 * <p>单线程够用：导入是用户一次点一个的，并发反而要处理写库争锁。
 */
public class App extends Application {

    private ExecutorService io;

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
