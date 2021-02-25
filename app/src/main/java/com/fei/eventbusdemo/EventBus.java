package com.fei.eventbusdemo;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @ClassName: EventBus
 * @Description: 描述
 * @Author: Fei
 * @CreateDate: 2021/2/25 15:14
 * @UpdateUser: Fei
 * @UpdateDate: 2021/2/25 15:14
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */
public class EventBus {

    static volatile EventBus defaultInstance;
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;
    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
    //缓存方法
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();
    //缓存参数类型-所有方法
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    //缓存类-参数类型
    private final Map<Object, List<Class<?>>> typesBySubscriber;

    private EventBus() {
        subscriptionsByEventType = new HashMap<>();
        typesBySubscriber = new HashMap<>();
    }

    public static EventBus getDefault() {
        EventBus instance = defaultInstance;
        if (instance == null) {
            synchronized (EventBus.class) {
                instance = EventBus.defaultInstance;
                if (instance == null) {
                    instance = EventBus.defaultInstance = new EventBus();
                }
            }
        }
        return instance;
    }

    /**
     * 事件发送
     *
     * @param event
     */
    public void post(Object event) {
        Class<?> eventType = event.getClass();
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        boolean isMainThread = Looper.getMainLooper() == Looper.myLooper();
        if (subscriptions != null) {
            for (Subscription subscription : subscriptions) {
                postSingleEvent(subscription, isMainThread, event);
            }

        }
    }

    private void postSingleEvent(final Subscription subscription, boolean isMainThread, final Object event) {
        switch (subscription.subscriberMethod.threadMode) {
            case MAIN:
            case MAIN_ORDERED:
                if (!isMainThread) {
                    postOnMainThread(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case POSTING:
                invokeSubscriber(subscription, event);
                break;
            case ASYNC:
                AsyncPoster.enqueue(subscription, event);
                break;
            case BACKGROUND:
                if (isMainThread) {
                    AsyncPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            // temporary: technically not correct as poster not decoupled from subscriber
        }
    }

    private void postOnMainThread(final Subscription subscription, final Object event) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                invokeSubscriber(subscription, event);
            }
        });
    }

    /**
     * 反射回调
     *
     * @param subscription
     * @param event
     */
    private void invokeSubscriber(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    /**
     * 注册
     *
     * @param subscriber
     */
    public void register(Object subscriber) {
        //遍历类，获取所有@Subscribe注解的方法
        List<SubscriberMethod> subscriberMethods = findMethod(subscriber.getClass());
        synchronized (this) {
            if (subscriberMethods != null) {
                for (SubscriberMethod subscriberMethod : subscriberMethods) {
                    //遍历方法，生成两个map
                    subscribe(subscriber, subscriberMethod);
                }
            }
        }
    }

    /**
     * 获取所有@Subscribe注解的方法
     *
     * @param subscriberClass
     * @return
     */
    private List<SubscriberMethod> findMethod(Class<?> subscriberClass) {
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        if (subscriberMethods != null) {
            return subscriberMethods;
        }
        subscriberMethods = new ArrayList<>();
        //获取所有方法
        Method[] declaredMethods = subscriberClass.getDeclaredMethods();
        for (Method declaredMethod : declaredMethods) {
            Subscribe subscribe = declaredMethod.getAnnotation(Subscribe.class);
            if (subscribe != null) {
                int modifiers = declaredMethod.getModifiers();
                //访问权限必须是public
                if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                    Class<?>[] eventType = declaredMethod.getParameterTypes();
                    //参数只有一个
                    if (eventType.length == 1) {
                        SubscriberMethod subscriberMethod = new SubscriberMethod(declaredMethod, eventType[0],
                                subscribe.threadMode(), subscribe.priority(), subscribe.sticky());
                        //加入集合
                        subscriberMethods.add(subscriberMethod);
                    } else {
                        String methodName = declaredMethod.getDeclaringClass().getName() + "." + declaredMethod.getName();
                        throw new IllegalArgumentException("@Subscribe method " + methodName +
                                "must have exactly 1 parameter but has " + eventType.length);
                    }
                } else {
                    String methodName = declaredMethod.getDeclaringClass().getName() + "." + declaredMethod.getName();
                    throw new IllegalArgumentException(methodName +
                            " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
                }
            }
        }
        if (subscriberMethods.isEmpty()) {
            throw new IllegalArgumentException("Subscriber " + subscriberClass
                    + " and its super classes have no public methods with the @Subscribe annotation");
        } else {
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
            return subscriberMethods;
        }
    }

    /**
     * 生成两个map
     *
     * @param subscriber
     * @param subscriberMethod
     */
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        Class<?> eventType = subscriberMethod.eventType;
        //缓存参数类型-所有方法
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        }

        ////////粘性、权限大小就没有判断了///////////////////

        Subscription subscription = new Subscription(subscriber, subscriberMethod);
        subscriptions.add(subscription);

        //缓存类-参数类型
        List<Class<?>> eventTypes = typesBySubscriber.get(subscriber);
        if (eventTypes == null) {
            eventTypes = new ArrayList<>();
            typesBySubscriber.put(subscriber, eventTypes);
        }

        if (!eventTypes.contains(eventType)) {
            eventTypes.add(eventType);
        }

    }

    /**
     * 注销
     *
     * @param subscriber
     */
    public void unregister(Object subscriber) {
        if (subscriber == null) return;

        if (METHOD_CACHE.containsKey(subscriber)) {
            List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriber);
            if (subscriberMethods != null) {
                subscriberMethods.clear();
                subscriberMethods = null;
            }
        }

        List<Class<?>> eventTypes = typesBySubscriber.get(subscriber);
        if (eventTypes == null) return;
        for (Class<?> eventType : eventTypes) {
            unsubscribeByEventType(subscriber, eventType);
        }

        typesBySubscriber.remove(subscriber);
    }

    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                Subscription subscription = subscriptions.get(i);
                if (subscription.subscriber == subscriber) {
                    subscriptions.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

}
