package com.derekjass.sts.weightedpaths.creative;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * AI 异步调用的共享线程池：单线程、daemon、串行。
 *
 * <p>为什么单线程：AI 调用天然串行（一次卡奖一个请求），单线程既消除并发写共享状态的竞态，
 * 也避免每处调用 new Thread 造成线程泄漏；daemon 线程不阻塞游戏退出。
 */
public final class AiExecutor {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "run-advisor-ai");
            t.setDaemon(true);
            return t;
        }
    });

    private AiExecutor() {
    }

    /** 提交 AI 任务（串行执行；失败由任务内兜底处理）。 */
    public static void submit(Runnable task) {
        if (task != null) {
            EXECUTOR.submit(task);
        }
    }
}