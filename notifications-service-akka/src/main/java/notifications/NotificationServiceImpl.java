/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package notifications;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;
import notifications.grpc.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class NotificationServiceImpl implements NotificationService {

    private final ActorSystem system;

    public NotificationServiceImpl(ActorSystem system) {
        this.system = system;
        ActorRef notificationSupervisor = system.actorOf(
                NotificationSupervisor.props(),
                "notification-supervisor");


        for (long i = 1; i <= 10; i++) {
            notificationSupervisor.tell(new NotificationSupervisor.CreateNotificationTarget(i), ActorRef.noSender());
        }
    }

    @Override
    public CompletionStage<NotifyResult> notify(NotifyRequest in) {
        system.actorSelection(pathFor(in.getRecipientId()))
                .tell(new NotificationTarget.Notify(in.getSenderId(), in.getMessage()), ActorRef.noSender());

        return CompletableFuture.completedFuture(NotifyResult.newBuilder().build());
    }

    @Override
    public Source<Notification, NotUsed> getNotifications(GetNotificationsRequest in) {
        return Source.<NotificationTarget.Notification>actorRef(10, OverflowStrategy.fail())
                .map(notification ->
                    Notification.newBuilder()
                        .setSenderId(notification.from)
                        .setWhen(notification.timestamp.getEpochSecond())
                        .setMessage(notification.message)
                        .build()
                ).mapMaterializedValue((actorRef) -> {
                    system.actorSelection(pathFor(in.getRecipientId())).tell(
                            NotificationTarget.SUBSCRIBE, actorRef
                    );
                    return NotUsed.getInstance();
                });
    }

    private String pathFor(long id) {
        return "/user/notification-supervisor/" + id;
    }
}
