package examples.webhook;

import com.cryptochief.processing.webhook.PayoutWebhookEvent;
import com.cryptochief.processing.webhook.WebhookSignatureException;
import com.cryptochief.processing.webhook.WebhookVerifier;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public final class WebhookExample {

    public static void main(String[] args) throws IOException {
        String apiKey = System.getenv("CRYPTO_CHIEF_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("set CRYPTO_CHIEF_API_KEY");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/webhook", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            String signature = exchange.getRequestHeaders().getFirst("Signature");
            try {
                PayoutWebhookEvent event = WebhookVerifier.parse(apiKey, body, signature,
                        PayoutWebhookEvent.class);
                System.out.println("payout webhook: uuid=" + event.uuid() + " status=" + event.status());
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write("ok".getBytes());
                exchange.close();
            } catch (WebhookSignatureException e) {
                System.err.println("rejected: " + e.getMessage());
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
            } catch (Exception e) {
                System.err.println("decode failed: " + e.getMessage());
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
            }
        });
        server.start();
        System.out.println("listening on http://localhost:8080/webhook");
    }

    private WebhookExample() {}
}
