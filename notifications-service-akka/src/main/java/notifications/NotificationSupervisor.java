/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package notifications;

import akka.actor.AbstractActor;
import akka.actor.Props;

public final class NotificationSupervisor extends AbstractActor {

    // messages
    public static final class CreateNotificationTarget {
        final long id;
        public CreateNotificationTarget(long id) {
            this.id = id;
        }
    }

    public static Props props() {
        return Props.create(
                NotificationSupervisor.class,
                NotificationSupervisor::new);
    }


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(CreateNotificationTarget.class, this::onCreateNotificationTarget)
                .build();
    }

    private void onCreateNotificationTarget(CreateNotificationTarget message) {
        getContext().actorOf(NotificationTarget.props(), Long.toString(message.id));
    }
}
