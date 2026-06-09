# Crypto Chief crypto-processing SDK for Java

[![Maven Central](https://img.shields.io/maven-central/v/com.crypto-chief/cryptochief-crypto-processing-java.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.crypto-chief/cryptochief-crypto-processing-java)
[![Java](https://img.shields.io/badge/java-17%2B-blue.svg)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Pure Java SDK for the [Crypto Chief](https://crypto-chief.com/processing/) crypto-processing API. No Kotlin runtime, no reactive bridges — straightforward synchronous API with records, builders, and OkHttp.

## Installation

### Maven

```xml
<dependency>
  <groupId>com.crypto-chief</groupId>
  <artifactId>cryptochief-crypto-processing-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.crypto-chief:cryptochief-crypto-processing-java:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.crypto-chief:cryptochief-crypto-processing-java:0.1.0'
}
```

Requires Java 17+.

## Quick start

Credentials come from the dashboard → Integration tab.

```java
import com.cryptochief.processing.Chain;
import com.cryptochief.processing.CryptoChiefClient;
import com.cryptochief.processing.models.EstimatePayoutRequest;
import com.cryptochief.processing.models.ExecutePayoutRequest;

public class App {
    public static void main(String[] args) {
        try (CryptoChiefClient client = CryptoChiefClient.create("mer_...", "sk_...")) {
            var estimate = client.payouts().estimate(
                EstimatePayoutRequest.of(Chain.ETH_SEPOLIA, "ETH", "0.0001", "0x..."));
            System.out.println("recipient gets " + estimate.amountToReceive());

            var payout = client.payouts().execute(new ExecutePayoutRequest(
                "order-42",
                "user-42",
                Chain.ETH_SEPOLIA,
                "ETH",
                "0.0001",
                "0x...",
                "https://your.app/webhooks/payout",
                null, false, false, null, null, null));
            System.out.println("payout: " + payout.uuid() + " → " + payout.status());
        }
    }
}
```

## Services

| Service | Endpoints |
| ------- | --------- |
| `client.payouts()` | estimate, execute, info, history, batchEstimate, batchExecute |
| `client.transactions()` | sign, execute, info, history + EVM/TRON/Solana/TON helpers |
| `client.payIns()` | create, info, history, cancel, selectAsset, resetAsset |
| `client.wallets()` | generate, list, info, freeze, decryptPrivateKey |
| `client.sweeps()` | force, history, walletHistory |
| `client.withdrawals()` | info, history |
| `client.staticDeposits()` | info, history |
| `client.blockchain()` | contractsAvailable, walletBalance, transactionStatus |
| `client.currencies()` | fiatToCrypto, cryptoToFiat |

## Invoices (PayIn)

FIAT mode — customer picks the coin at payment time:

```java
import com.cryptochief.processing.models.CreatePayInRequest;
import com.cryptochief.processing.models.PayInMode;

var invoice = client.payIns().create(new CreatePayInRequest(
    "order-42", "user-42", PayInMode.FIAT,
    null, 3600, "https://your.app/webhooks/invoice", null, null, null, null,
    "19.99", "USD", null, null,
    null, null));
System.out.println(invoice.paymentLink());
```

CRYPTO mode — fix the coin and amount up front:

```java
import com.cryptochief.processing.Asset;
import com.cryptochief.processing.Chain;

var invoice = client.payIns().create(new CreatePayInRequest(
    "order-42", "user-42", PayInMode.CRYPTO,
    null, null, "https://your.app/webhooks/invoice", null, null, null, null,
    null, null, null, null,
    "10", new Asset(Chain.TRON_MAINNET, "USDT")));
System.out.println("pay to " + invoice.toAddress());
```

## Contract calls

EVM / TRON:

```java
import com.cryptochief.processing.Amount;
import java.util.List;

var signed = client.transactions().erc20Transfer(
    Chain.ETH_MAINNET,
    "0x...",
    "0xdAC17F958D2ee523a2206206994597C13D831ec7",
    "0x...",
    Amount.toBase("12.50", 6));
client.transactions().execute(signed.uuid());
```

Custom EVM call:

```java
var signed = client.transactions().signEvmCall(
    Chain.ETH_SEPOLIA,
    "0x...",
    "0xUniswapV2Router",
    "swapExactTokensForTokens(uint256,uint256,address[],address,uint256)",
    List.of(amountIn, amountOutMin, path, "0xYou", deadline));
```

Solana Anchor:

```java
import com.cryptochief.processing.solana.Borsh;
import com.cryptochief.processing.models.SolanaAccount;
import java.util.List;

var signed = client.transactions().signAnchorCall(
    Chain.SOLANA_DEVNET,
    "YourWallet...",
    "ProgramId...",
    "transfer",
    List.of(Borsh.u64(1_000_000L)),
    List.of(new SolanaAccount("Acc1", true, true)),
    null);
```

TON Jetton:

```java
import com.cryptochief.processing.services.TransactionsService.JettonTransferRequest;

var signed = client.transactions().jettonTransfer(new JettonTransferRequest(
    Chain.TON_MAINNET,
    "EQ...",
    "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs",
    "EQ...",
    Amount.toBase("12.50", 6),
    null, null, null, null,
    "Order #4242",
    0L,
    null));
```

## Polling

```java
import com.cryptochief.processing.PollOptions;
import com.cryptochief.processing.poll.Polling;
import java.time.Duration;

var terminal = Polling.waitForPayout(client, payout.uuid(),
    new PollOptions(Duration.ofSeconds(5), Duration.ofMinutes(10)));
```

## Webhook handling

```java
import com.cryptochief.processing.webhook.PayoutWebhookEvent;
import com.cryptochief.processing.webhook.WebhookSignatureException;
import com.cryptochief.processing.webhook.WebhookVerifier;

try {
    var event = WebhookVerifier.parse(apiKey, rawBody,
        request.getHeader("Signature"), PayoutWebhookEvent.class);
    System.out.println("payout " + event.uuid() + " → " + event.status());
} catch (WebhookSignatureException e) {
    response.setStatus(401);
}
```

IP allowlist:

```java
if (!WebhookVerifier.SENDER_IPS.contains(request.getRemoteAddr())) {
    response.setStatus(403);
    return;
}
```

Typed events: `PayoutWebhookEvent`, `TransactionWebhookEvent`, `PayInWebhookEvent`, `StaticDepositWebhookEvent`.

## Wallet private key decryption

Upload an RSA public key in the dashboard (Project Settings → RSA Key), then
configure the client with the matching private key:

```java
import com.cryptochief.processing.Options;
import com.cryptochief.processing.rsa.RsaKeyLoader;

var client = new CryptoChiefClient(Options.builder()
    .merchantId("mer_...")
    .apiKey("sk_...")
    .rsaPrivateKey(RsaKeyLoader.loadPrivateKeyFromFile("/path/to/key.pem"))
    .build());

var wallet = client.wallets().generate(req);
String rawHex = client.wallets().decryptPrivateKey(wallet.privateKeyEncrypted());
```

PKCS#1 and PKCS#8 PEM both supported, JDK crypto only.

## Configuration

```java
import java.time.Duration;
import com.cryptochief.processing.Options;

var client = new CryptoChiefClient(Options.builder()
    .merchantId("...")
    .apiKey("...")
    .baseUrl("https://staging-api.crypto-chief.com")
    .requestTimeout(Duration.ofSeconds(30))
    .maxRetries(5)
    .initialRetryDelay(Duration.ofMillis(250))
    .maxRetryDelay(Duration.ofSeconds(10))
    .userAgent("my-app/1.2.3")
    .httpClient(myPreconfiguredOkHttpClient)
    .build());
```

A caller-supplied `httpClient` is not closed by the SDK.

## Errors

```java
import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.exceptions.ErrorCode;
import com.cryptochief.processing.exceptions.NetworkException;

try {
    client.payouts().execute(req);
} catch (ApiException e) {
    switch (e.code()) {
        case ErrorCode.INSUFFICIENT_FUNDS -> { /* top up the master wallet */ }
        case ErrorCode.ORDER_ALREADY_EXIST -> { /* idempotent retry */ }
        default -> throw e;
    }
} catch (NetworkException e) {
    // already retried up to options.maxRetries
}
```

5xx is retried with exponential backoff and full jitter. 4xx is not retried.

## Other SDKs

SDKs for other languages are listed at [docs-sdk.crypto-chief.com/processing/processing](https://docs-sdk.crypto-chief.com/processing/processing).

## License

[MIT](LICENSE) © 2026 Crypto Chief
