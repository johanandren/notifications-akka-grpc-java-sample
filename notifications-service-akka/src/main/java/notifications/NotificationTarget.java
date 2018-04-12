/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package notifications;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;

import java.time.Instant;
import java.util.*;

public class NotificationTarget extends AbstractLoggingActor {

    // messages
    public static final class Notify {
        final long from;
        final String message;
        public Notify(long from, String message) {
            this.from = from;
            this.message = message;
        }
    }
    public static final class Subscribe {
        private Subscribe() {
        }
    }
    public static final Subscribe SUBSCRIBE = new Subscribe();

    public static final class Notification {
        public final String id;
        public final Instant timestamp;
        public final long from;
        public final String message;
        public Notification(String id, Instant timestamp, long from, String message) {
            this.id = id;
            this.timestamp = timestamp;
            this.from = from;
            this.message = message;
        }
    }

    public static Props props() {
        return Props.create(
                NotificationTarget.class,
                NotificationTarget::new);
    }


    private final List<Notification> unseenNotifications = new ArrayList<>();
    private final Set<ActorRef> currentSubscriptions = new HashSet<>();


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Notify.class, this::onNotify)
                .matchEquals(SUBSCRIBE, __ -> onSubscribe())
                .match(Terminated.class, this::onTerminated)
                .build();
    }

    private void onNotify(Notify notify) {
        log().info("Notification from " + notify.from + ": " + notify.message);
        final Notification notification = new Notification(nextId(), Instant.now(), notify.from, notify.message);
        if (currentSubscriptions.isEmpty()) {
            unseenNotifications.add(notification);
        } else {
            currentSubscriptions.forEach(subscriber ->
                    subscriber.tell(notification, getSelf())
                );
        }
    }

    private void onSubscribe() {
        log().info("Subscriber added");
        final ActorRef newSubscriber = sender();
        getContext().watch(newSubscriber);
        currentSubscriptions.add(newSubscriber);
        if (!unseenNotifications.isEmpty()) {
            unseenNotifications.forEach(notification ->
                    newSubscriber.tell(notification, getSelf())
                );
            unseenNotifications.clear();
        }
    }


    private void onTerminated(Terminated terminated) {
        currentSubscriptions.remove(terminated.actor());
    }

    private String nextId() {
        return UUID.randomUUID().toString();
    }
}
