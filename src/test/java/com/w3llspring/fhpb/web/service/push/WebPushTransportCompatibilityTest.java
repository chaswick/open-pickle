package com.w3llspring.fhpb.web.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.concurrent.atomic.AtomicReference;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jose4j.jws.JsonWebSignature;

class WebPushTransportCompatibilityTest {

  @Test
  @Timeout(15)
  void sendsAnEncryptedVapidNotificationWithTheRetainedApacheTransport() throws Exception {
    if (Security.getProvider("BC") == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair sender = generator.generateKeyPair();
    KeyPair recipient = generator.generateKeyPair();
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> encoding = new AtomicReference<>();
    AtomicReference<byte[]> received = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/push", exchange -> {
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      encoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
      received.set(exchange.getRequestBody().readAllBytes());
      exchange.sendResponseHeaders(201, -1);
      exchange.close();
    });
    server.start();
    try {
      String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/push";
      byte[] payload = "Local compatibility check".getBytes(StandardCharsets.UTF_8);
      Notification notification = new Notification(endpoint, recipient.getPublic(), new byte[16], payload);
      PushService service = new PushService(sender, "mailto:test@example.invalid");

      var response = service.send(notification);

      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(201);
      assertThat(authorization.get()).startsWith("WebPush ");
      JsonWebSignature signature = new JsonWebSignature();
      signature.setCompactSerialization(authorization.get().substring("WebPush ".length()));
      signature.setKey(sender.getPublic());
      assertThat(signature.verifySignature()).isTrue();
      assertThat(encoding.get()).isEqualTo("aesgcm");
      assertThat(received.get()).hasSizeGreaterThan(payload.length);
      assertThat(new String(received.get(), StandardCharsets.UTF_8))
          .doesNotContain("Local compatibility check");
    } finally {
      server.stop(0);
    }
  }
}
