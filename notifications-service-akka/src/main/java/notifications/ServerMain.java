/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package notifications;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectWithHttps;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.grpc.internal.testing.TestUtils;
import notifications.grpc.NotificationService;
import notifications.grpc.NotificationServiceHandlerFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class ServerMain {

    public static void main(String[] args) throws Exception {
        // important to enable HTTP/2 in ActorSystem's config
        Config conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
                .withFallback(ConfigFactory.defaultApplication());


        final ActorSystem system = ActorSystem.create("grpc-service", conf);
        final Materializer materializer = ActorMaterializer.create(system);

        final NotificationService notificationService = new NotificationServiceImpl(system);

        Http.get(system).bindAndHandleAsync(
                NotificationServiceHandlerFactory.create(notificationService, materializer),
                ConnectWithHttps.toHostHttps("127.0.0.1", 8081).withCustomHttpsContext(serverHttpContext()),
                materializer)
                .thenAccept(binding -> {
                    System.out.println("gRPC server bound to: " + binding.localAddress() + " enter to kill server");
                });

        System.in.read();
        system.terminate();

    }

    // dummy certificate setup since HTTP2 requires encryption right now
    private static HttpsConnectionContext serverHttpContext() throws Exception {
        String keyEncoded = new String(Files.readAllBytes(Paths.get(TestUtils.loadCert("server1.key").getAbsolutePath())), "UTF-8")
                .replace("-----BEGIN PRIVATE KEY-----\n", "")
                .replace("-----END PRIVATE KEY-----\n", "")
                .replace("\n", "");

        byte[] decodedKey = Base64.getDecoder().decode(keyEncoded);

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);

        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(spec);

        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        FileInputStream is = new FileInputStream(TestUtils.loadCert("server1.pem"));
        Certificate cer = fact.generateCertificate(is);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null);
        ks.setKeyEntry("private", privateKey, new char[0], new Certificate[]{cer});

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(ks, null);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

        return ConnectionContext.https(context);
    }



}
