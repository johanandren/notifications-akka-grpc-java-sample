/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.StringUnmarshallers;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.internal.testing.TestUtils;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import notifications.grpc.GetNotificationsRequest;
import notifications.grpc.NotificationServiceClient;
import notifications.grpc.NotifyRequest;
import notifications.grpc.NotifyResult;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;
import static akka.http.javadsl.server.PathMatchers.*;
import static akka.http.javadsl.server.PathMatchers.pathEnd;


public class Main {

  public static void main(String[] args) throws IOException {
    final ActorSystem system = ActorSystem.create();
    final ActorMaterializer materializer = ActorMaterializer.create(system);

    final NotificationServiceClient client = createClient("127.0.0.1", 8081, system, materializer);


    Route route = route(
        path(segment("view").slash(longSegment()), id ->
            get(() ->
                route(
                    handleWebSocketMessages(
                        Flow.fromSinkAndSource(
                            Sink.ignore(),
                            client.getNotifications(
                                GetNotificationsRequest.newBuilder()
                                    .setRecipientId(id)
                                    .build()
                            ).map(streamNotification ->
                                TextMessage.create(streamNotification.getMessage())
                            )
                        )
                    ),
                    getFromResource("view.html")
                )
            )
        ),
        path(pathEnd(), () ->
            get(() ->
                getFromResource("index.html")
            )
        ),
        path(segment("notify"), () ->
            post(() ->
                formField(StringUnmarshallers.LONG, "targetId", (id) ->
                    formField("notification", notification -> {
                      CompletionStage<NotifyResult> done = client.notify(NotifyRequest.newBuilder()
                          .setRecipientId(id)
                          .setMessage(notification)
                          .setSenderId(1L) // FIXME we don't actually have this, remove from sample?
                          .build());
                      return onSuccess(done, completed -> redirect(Uri.create("/"), StatusCodes.SEE_OTHER));
                    })
                )
            )
        )

    );

    Http.get(system).bindAndHandle(
        route.flow(system, materializer),
        ConnectHttp.toHost("127.0.0.1", 8080),
        materializer
    ).whenComplete((binding, error) -> {
      if (binding != null) system.log().info("Server bound to http://{}:{} Press that any key to kill server",
          binding.localAddress().getHostName(), binding.localAddress().getPort());
      else throw new RuntimeException(error);
    });


    System.in.read();
    system.terminate();
  }


  private static NotificationServiceClient createClient(String host, int port, ActorSystem actorSystem, ActorMaterializer materializer) {
    final SslContext sslContext;

    try {
      sslContext = GrpcSslContexts.forClient().trustManager(TestUtils.loadCert("ca.pem")).build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }


    NettyChannelBuilder channelBuilder =
        NettyChannelBuilder
            .forAddress(host, port)
            .flowControlWindow(65 * 1024)
            .negotiationType(NegotiationType.TLS)
            .sslContext(sslContext);

    // we're ok with a fake server cert in this sample
    String serverHostOverride = "foo.test.google.fr";
    channelBuilder.overrideAuthority(serverHostOverride);

    ManagedChannel channel = channelBuilder.build();

    CallOptions callOptions = CallOptions.DEFAULT;

    return NotificationServiceClient.create(channel, callOptions, materializer, actorSystem.dispatcher());
  }
}
