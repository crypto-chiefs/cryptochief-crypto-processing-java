package examples.invoice;

import com.cryptochief.processing.Asset;
import com.cryptochief.processing.Chain;
import com.cryptochief.processing.CryptoChiefClient;
import com.cryptochief.processing.PollOptions;
import com.cryptochief.processing.models.CreatePayInRequest;
import com.cryptochief.processing.models.PayIn;
import com.cryptochief.processing.models.PayInMode;
import com.cryptochief.processing.poll.Polling;

import java.time.Duration;
import java.util.UUID;

public final class InvoiceExample {

    public static void main(String[] args) {
        String merchantId = require("CRYPTO_CHIEF_MERCHANT_ID");
        String apiKey = require("CRYPTO_CHIEF_API_KEY");
        String mode = args.length > 0 ? args[0] : "fiat";

        try (CryptoChiefClient client = CryptoChiefClient.create(merchantId, apiKey)) {
            PayIn invoice = switch (mode) {
                case "fiat" -> client.payIns().create(new CreatePayInRequest(
                        "order-" + UUID.randomUUID(),
                        "user-42",
                        PayInMode.FIAT,
                        null, 3600,
                        "https://example.com/webhooks/invoice",
                        "https://example.com/checkout/success",
                        "https://example.com/checkout/failed",
                        null, null,
                        "19.99", "USD", null, null,
                        null, null));
                case "crypto" -> client.payIns().create(new CreatePayInRequest(
                        "order-" + UUID.randomUUID(),
                        "user-42",
                        PayInMode.CRYPTO,
                        null, 3600,
                        "https://example.com/webhooks/invoice",
                        null, null, null, null,
                        null, null, null, null,
                        "10", new Asset(Chain.TRON_MAINNET, "USDT")));
                default -> throw new IllegalArgumentException("usage: InvoiceExample [fiat|crypto]");
            };

            System.out.println("invoice: uuid=" + invoice.uuid() + " status=" + invoice.status());
            if (invoice.paymentLink() != null) {
                System.out.println("payment link: " + invoice.paymentLink());
            }
            if (invoice.toAddress() != null) {
                System.out.println("pay to: " + invoice.toAddress()
                        + " (" + invoice.paymentCoin() + " on " + invoice.paymentNetwork() + ")");
            }

            PayIn terminal = Polling.waitForPayIn(client, invoice.uuid(),
                    new PollOptions(Duration.ofSeconds(10), Duration.ofMinutes(15)));
            System.out.println("final: status=" + terminal.status());
        }
    }

    private static String require(String name) {
        String v = System.getenv(name);
        if (v == null || v.isEmpty()) throw new IllegalStateException("set " + name);
        return v;
    }

    private InvoiceExample() {}
}
