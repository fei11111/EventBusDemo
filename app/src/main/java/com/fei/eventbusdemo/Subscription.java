package com.fei.eventbusdemo;

/**
 * @ClassName: Subscription
 * @Description: 描述
 * @Author: Fei
 * @CreateDate: 2021/2/25 15:25
 * @UpdateUser: Fei
 * @UpdateDate: 2021/2/25 15:25
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */
final class Subscription {
    final Object subscriber;
    final SubscriberMethod subscriberMethod;
    /**
     * Becomes false as soon as {@link EventBus#unregister(Object)} is called, which is checked by queued event delivery
     * {@link EventBus#invokeSubscriber(PendingPost)} to prevent race conditions.
     */
    volatile boolean active;

    Subscription(Object subscriber, SubscriberMethod subscriberMethod) {
        this.subscriber = subscriber;
        this.subscriberMethod = subscriberMethod;
        active = true;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Subscription) {
            Subscription otherSubscription = (Subscription) other;
            return subscriber == otherSubscription.subscriber
                    && subscriberMethod.equals(otherSubscription.subscriberMethod);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return subscriber.hashCode() + subscriberMethod.methodString.hashCode();
    }
}
