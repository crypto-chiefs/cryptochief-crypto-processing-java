package examples.webhook;

import com.cryptochief.processing.webhook.PayoutWebhookEvent;
import com.cryptochief.processing.webhook.SweepWebhookEvent;
import com.cryptochief.processing.webhook.WebhookVerificationException;
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
            // Raw bytes: the signature covers the body exactly as sent.
            byte[] body = exchange.getRequestBody().readAllBytes();
            String deliveryId = exchange.getRequestHeaders().getFirst(WebhookVerifier.DELIVERY_HEADER);
            try {
                // Checks X-CC-Timestamp, X-Webhook-Delivery and X-CC-Signature.
                PayoutWebhookEvent event = WebhookVerifier.parse(apiKey, body, exchange.getRequestHeaders(),
                        PayoutWebhookEvent.class);
                // payout.paid is sent once every source reaches requiredConfirmations.
                System.out.println("payout webhook " + deliveryId + ": uuid=" + event.uuid()
                        + " status=" + event.status()
                        + " confirmations=" + event.confirmations()
                        + " required=" + event.requiredConfirmations());
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write("ok".getBytes());
                exchange.close();
            } catch (WebhookVerificationException e) {
                System.err.println("rejected " + deliveryId + ": " + e.getMessage());
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
            } catch (Exception e) {
                System.err.println("decode failed: " + e.getMessage());
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
            }
        });
        // Sweep - your money finishing its move into your own custody.
        //
        // A static_deposit.paid told you a customer paid. THIS says the funds
        // have been swept off the deposit address and the sweep reached
        // requiredConfirmations. Until it fires the balance still sits on the deposit
        // wallet, so treasury reporting and "available to pay out" should key off
        // this, not the deposit. Sweeps run on static deposit wallets and on per-order
        // transit wallets alike; both arrive here.
        server.createContext("/webhook/sweep", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            try {
                SweepWebhookEvent event = WebhookVerifier.parse(apiKey, body, exchange.getRequestHeaders(),
                        SweepWebhookEvent.class);
                System.out.println("sweep " + event.taskId() + ": "
                        + event.amountHuman() + " " + event.assetSymbol() + " "
                        + event.walletAddress() + " -> " + event.toAddress()
                        + " tx=" + event.sweepTxHash()
                        + " confirmations=" + event.sweepConfirmations()
                        + "/" + event.requiredConfirmations()
                        + " trigger=" + event.typeWork()
                        + " fee_usd=" + event.totalFeeUsd());

                // taskId is the idempotency key: one sweep settles once. Seeing
                // it twice means a redelivery - acknowledge and stop.
                // if (treasury.alreadyRecorded(event.taskId())) { ... }

                // The event arrives only once the sweep is settled.
                // treasury.recordSettled(event.taskId(), event.assetSymbol(),
                //         event.amountHuman(), event.sweepTxHash());
                // costs.record(event.taskId(), event.totalFeeUsd());  // sweeps are not free

                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write("ok".getBytes());
                exchange.close();
            } catch (WebhookVerificationException e) {
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
        System.out.println("sweep events on http://localhost:8080/webhook/sweep");
    }

    private WebhookExample() {}
}
