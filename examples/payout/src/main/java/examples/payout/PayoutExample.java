package examples.payout;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.CryptoChiefClient;
import com.cryptochief.processing.PollOptions;
import com.cryptochief.processing.models.EstimatePayoutRequest;
import com.cryptochief.processing.models.ExecutePayoutRequest;
import com.cryptochief.processing.models.PayoutInfo;
import com.cryptochief.processing.poll.Polling;

import java.time.Duration;
import java.util.UUID;

public final class PayoutExample {

    public static void main(String[] args) {
        String merchantId = require("CRYPTO_CHIEF_MERCHANT_ID");
        String apiKey = require("CRYPTO_CHIEF_API_KEY");
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: PayoutExample <to_address>");
        }
        String toAddress = args[0];

        try (CryptoChiefClient client = CryptoChiefClient.create(merchantId, apiKey)) {
            var estimate = client.payouts().estimate(
                    EstimatePayoutRequest.of(Chain.ETH_SEPOLIA, "ETH", "0.0001", toAddress));
            System.out.println("estimate: receive=" + estimate.amountToReceive()
                    + " fee=" + (estimate.feeInfo() == null ? null : estimate.feeInfo().estimatedFiat()));

            var payout = client.payouts().execute(new ExecutePayoutRequest(
                    "order-" + UUID.randomUUID(),
                    "user-42",
                    Chain.ETH_SEPOLIA,
                    "ETH",
                    "0.0001",
                    toAddress,
                    "https://example.com/webhooks/payout",
                    null, false, false, null, null, null));
            System.out.println("created: uuid=" + payout.uuid() + " status=" + payout.status());

            PayoutInfo terminal = Polling.waitForPayout(client, payout.uuid(),
                    new PollOptions(Duration.ofSeconds(5), Duration.ofMinutes(5)));
            System.out.println("final:   status=" + terminal.status()
                    + " txid=" + (terminal.txid() == null ? "" : terminal.txid()));
        }
    }

    private static String require(String name) {
        String v = System.getenv(name);
        if (v == null || v.isEmpty()) throw new IllegalStateException("set " + name);
        return v;
    }

    private PayoutExample() {}
}
