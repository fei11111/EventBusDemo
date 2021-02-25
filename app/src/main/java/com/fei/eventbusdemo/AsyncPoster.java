package com.fei.eventbusdemo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @ClassName: AsyncPoster
 * @Description: 描述
 * @Author: Fei
 * @CreateDate: 2021/2/25 15:26
 * @UpdateUser: Fei
 * @UpdateDate: 2021/2/25 15:26
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */
class AsyncPoster implements Runnable {

    private final static ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    private Subscription subscription;
    private Object event;

    private AsyncPoster(Subscription subscription, Object event) {
        this.subscription = subscription;
        this.event = event;
    }

    public static void enqueue(Subscription subscription, Object event) {
        AsyncPoster asyncPoster = new AsyncPoster(subscription,event);
        DEFAULT_EXECUTOR_SERVICE.execute(asyncPoster);
    }

    @Override
    public void run() {

    }

}
