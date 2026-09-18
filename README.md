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
  <version>0.12.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.crypto-chief:cryptochief-crypto-processing-java:0.12.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.crypto-chief:cryptochief-crypto-processing-java:0.12.0'
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
| `client.transactions()` | estimate, sign, execute, info, history + EVM/TRON/Solana/TON helpers |
| `client.payIns()` | create, info, history, cancel, selectAsset, resetAsset |
| `client.wallets()` | generate, list, info, history, freeze, rebindMaster, setCallbackUrl, clearCallbackUrl, setLabel, clearLabel, decryptPrivateKey |
| `client.sweeps()` | force, history, walletHistory, settings, updateSettings, updateGasSource |
| `client.withdrawals()` | info, history (read-only, see [Withdrawals](#withdrawals)) |
| `client.staticDeposits()` | info, history |
| `client.blockchain()` | contractsAvailable, contractsList, blockchains, walletBalance, transactionStatus |
| `client.currencies()` | fiatToCrypto, cryptoToFiat, fiats, cryptos |
| `client.credits()` | balance, topup |
| `client.energy()` | quote, rent, order (TRON energy rental, billed to API credits) |
| `client.nativeCoin()` | quote, buy, order (native coin purchase, billed to API credits) |

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

## Wallets

`generate` takes an optional `label` — a name of your own for the wallet, at most 255
characters, stored and never interpreted. It applies to every wallet type, a master wallet
as much as a static one. Leave it null and the wallet stays unnamed; the field then does not
go on the wire at all.

```java
import com.cryptochief.processing.ChainFamily;
import com.cryptochief.processing.models.GenerateWalletRequest;
import com.cryptochief.processing.models.WalletType;

var wallet = client.wallets().generate(new GenerateWalletRequest(
    WalletType.STATIC, ChainFamily.EVM, masterAddress,
    "https://your.app/webhooks/deposit",
    "Acme Corp — EU customers"));
```

The name comes back on every response that describes a wallet — generation, `info`, the
list, and the updates below — as `label()`, which reads `null` when the wallet has no name.

Three things can still be changed once the wallet exists.

`rebindMaster` re-points a transit or static wallet at another master wallet of the project:

```java
var moved = client.wallets().rebindMaster(depositAddress, otherMasterAddress);
System.out.println(moved.masterWalletAddress());
```

It moves no money. It decides where the *next* sweep settles — including sweeps already
queued but not yet sent — and everything swept before stays on the previous master. Calling
it again with the same master returns 200 and changes nothing. Master wallets cannot be
re-pointed, and the new master must be the same chain family and not frozen.

`setCallbackUrl` sets or clears the deposit webhook of a static wallet after creation:

```java
client.wallets().setCallbackUrl(depositAddress, "https://your.app/webhooks/deposit");
client.wallets().clearCallbackUrl(depositAddress);   // sends "", stops the announcements
```

Static wallets only — master and transit answer 400. A deposit that was already announced
is not announced again to the new URL.

`setLabel` renames a wallet, or takes the name off it:

```java
client.wallets().setLabel(depositAddress, "Acme Corp — EU customers");
client.wallets().clearLabel(depositAddress);   // sends "", the wallet goes back to unnamed
```

Every wallet type, unlike the callback URL — a master wallet is named the same way a static
one is. Over 255 characters answers 400 with `LABEL_TOO_LONG`.

All three calls return the wallet as it stands afterwards, so the new binding, URL or name
is visible without a second request. `masterWalletAddress()`, `callbackUrl()` and `label()`
read as `null` when the wallet has none.

`history` lists every pay-in that used one deposit address — useful when a payer says they
sent funds and you have the address but not the order, since a deposit wallet can serve
several orders over its lifetime:

```java
import com.cryptochief.processing.models.WalletHistoryQuery;

var page = client.wallets().history(depositAddress);
for (var order : page.items()) {
    System.out.println(order.orderId() + " → " + order.status());
}

var window = client.wallets().history(new WalletHistoryQuery(
    depositAddress, "2026-08-01T00:00:00+00:00", "2026-08-31T23:59:59+00:00", 1, 50));
```

The same order and `meta` records as `client.payIns().history()` — this is the same list,
narrowed to one wallet. The address is matched case-insensitively, so either spelling of an
EVM address works, and an address your project does not own yields an **empty page rather
than an error**: an empty result is not proof the address does not exist.

## Auto-sweep settings

A deposit wallet is swept to your master wallet on a policy: as soon as funds arrive, once
the balance reaches an amount, or never on its own (a force sweep still works).

```java
import com.cryptochief.processing.models.SweepFieldWrite;
import com.cryptochief.processing.models.SweepPolicyMode;
import com.cryptochief.processing.models.SweepSettingsQuery;

var s = client.sweeps().updateSettings(depositAddress,
    SweepFieldWrite.set(SweepPolicyMode.THRESHOLD),
    SweepFieldWrite.set("250"),
    null);

System.out.println(s.effective().typeWork());  // what will actually happen
System.out.println(s.effective().source());    // which layer decided it
```

The read (`client.sweeps().settings(SweepSettingsQuery.forAddress(address))`) comes back in
three layers — `effective` (what will happen), `override` (what this wallet decides for
itself) and `projectDefault` (what it falls back to) — because only the three together say
whether a value is yours or inherited.

Inheritance is per field: writing the mode leaves the fee mode inherited. A `null` argument
leaves a field alone; `SweepFieldWrite.inherit()` stops overriding it.

### `fee_mode` — who covers a gas shortfall

A deposit wallet that already holds enough of the chain's native coin pays for its own
transfer, **whatever the mode**. `fee_mode` only decides where the missing gas comes from
when it does not:

| Value | Where the shortfall comes from |
| ----- | ------------------------------ |
| `SweepFeeMode.CLIENT` | Your own **master wallet**. |
| `SweepFeeMode.SERVICE` | The platform — and the cost is **billed to your API credits**. |
| `SweepFeeMode.MIX` | **The default.** Tries `client` first, falls back to `service` when the master wallet cannot cover it. |

So `service`, and every `mix` sweep that falls back to it, spends API credits rather than
on-chain balance — a cost that shows up on the credits ledger and nowhere in the wallet.

### `gas_source` — who buys the energy on TRON

`gas_source` decides *what is bought* to move a sweep on TRON, where `fee_mode` decides *who
covers a gas shortfall*. The two are independent, and the energy is billed to your API
credits whatever the fee mode says.

| Value | What happens |
| ----- | ------------ |
| `SweepGasSource.NATIVE` | The wallet burns its own TRX for energy. |
| `SweepGasSource.RENTED` | The platform supplies the energy, billed to your API credits. **The default.** |

> **Not setting it is not the same as setting `native`.** A wallet that never chose one gets
> the platform default, which is `rented` — so energy is supplied and billed to your credits
> without anyone having switched it on. To have the wallet burn its own TRX, send `native`
> explicitly.

```java
import com.cryptochief.processing.models.SweepGasSource;

client.sweeps().updateGasSource(tronAddress,
    SweepFieldWrite.set(SweepGasSource.NATIVE));   // burn the wallet's own TRX

client.sweeps().updateGasSource(tronAddress,
    SweepFieldWrite.inherit());                    // drop the override and inherit again
```

`inherit()` names `gas_source` in the `fields` mask with no value, which is the only way to
clear one field while keeping the others — and it inherits back to `rented`, not to "off".
The mask accepts `type_work`, `threshold_amount_usd`, `fee_mode` and `gas_source`.

Read it back with `effective().gasSource()`, which is always a concrete value. A `null` in
`override().gasSource()` means only that this layer does not decide — inherited, **not**
switched off.

Carried and ignored on every chain other than TRON.

### Sweep history

While a sweep is `SweepStatus.BROADCASTED`, `sweepConfirmations()` grows. When it reaches
`requiredConfirmations()` the sweep becomes `SweepStatus.COMPLETED` and `sweep.confirmed` is
sent. Settled: status `completed` and `sweepConfirmations()` above zero. On older records
`completed` can have `0` and is then not settled. A count above zero alone is not enough: a
`broadcasted` sweep has one too.

```java
for (var s : client.sweeps().history().items()) {
    boolean settled = SweepStatus.COMPLETED.equals(s.status())
        && s.sweepConfirmations() != null && s.sweepConfirmations() > 0;
    System.out.println(s.taskId() + " " + s.status() + " "
        + s.sweepConfirmations() + "/" + s.requiredConfirmations()
        + (settled ? " settled" : ""));
}
```

`completedAt()` is when the sweep transaction was sent (for `waiting_gas`, `failed` and
`skipped`, when that status was recorded). It is not a settlement signal.

Both history endpoints filter on `status` and `search` as well as `mode`:

```java
import com.cryptochief.processing.models.SweepHistoryQuery;
import com.cryptochief.processing.models.SweepStatus;
import com.cryptochief.processing.models.SweepWalletHistoryQuery;

var failed = client.sweeps().history(SweepHistoryQuery.empty()
    .withStatus(SweepStatus.FAILED)
    .withSearch("0x77EDde3213b70c9dd224C874c28f41B23B070f65"));

var one = client.sweeps().walletHistory(SweepWalletHistoryQuery.forAddress(depositAddress)
    .withStatus(SweepStatus.COMPLETED));
```

`status` takes one status. Leave it out and **every** status comes back, `skipped` among
them — a skipped sweep is a balance the platform decided against moving, a normal outcome
rather than a failure, and easy to be surprised by in a total. `search` is a substring match:
on `history` it matches the wallet address, the sweep or gas-pump transaction hash and the
`task_id`; on `walletHistory` the hashes and the `task_id`, since the address is already the
question.

## Withdrawals

Withdrawals are read-only: `info(uuid)` returns `Withdrawal`, `history()` returns a page of them
in `items()`. There are no withdrawal webhooks. `status()` is one of the `WithdrawalStatus` values:

| Status | Meaning |
| ------ | ------- |
| `queue` | Accepted, waiting to be processed |
| `refueling` | The source wallet is being topped up with native coin for gas |
| `refuel_confirmed` | Gas is in place or was not needed |
| `sending` | The transaction is being built, signed and sent |
| `broadcasting` | EVM: queued for broadcast |
| `in_mempool` | Bitcoin family: in the mempool, not yet in a block |
| `confirm_check` | Sent, waiting for `requiredConfirmations()` |
| `completed` | Reached `requiredConfirmations()`. Terminal |
| `failed` | Did not go through; `errorReason()` says why. Terminal |

`WithdrawalStatus.CANCELLED` is not produced by the API.

`confirmations()` is optional, absent until the transaction is in a block.
`requiredConfirmations()` is always present.

```java
import com.cryptochief.processing.models.WithdrawalStatus;

var w = client.withdrawals().info(withdrawalUuid);
if (w.succeeded()) {
    System.out.println("settled at " + w.completedAt() + " tx " + w.txHash());
} else if (WithdrawalStatus.CONFIRM_CHECK.equals(w.status())) {
    System.out.println(w.confirmations() == null
        ? "sent, waiting for its first block"
        : "settling: " + w.confirmations() + " of " + w.requiredConfirmations());
} else if (w.isTerminal()) {
    System.out.println(w.status() + ": " + w.errorReason());
}
```

`history()` applies only page, page size and `date_from` / `date_to` (RFC 3339); `status`,
`coin` and `network` on `HistoryQuery` are ignored.

## Blockchain data

`contractsAvailable` is the project's own asset catalogue — what it can be paid in right now,
and the list that governs orders, sweeps and payouts. `contractsList` is the platform-wide
one: every coin and token the platform supports anywhere, for building a "which assets could
we turn on" picker. Same item shape:

```java
for (var asset : client.blockchain().contractsList().items()) {
    System.out.println(asset.network() + " " + asset.coin()
        + " family=" + asset.chainFamily()
        + " test=" + asset.isTest()
        + " decimals=" + asset.decimals());
}
```

`contract()` is an **empty string** for a native coin, not null — there is no contract to
name. `isTest()` marks an asset on a test network, which is what tells a worthless payment
from a real one when the platform picks the asset for you.

`blockchains` is a different question: which chains the scanner is connected to and can read
blocks from right now. Infrastructure, not your catalogue. It answers with a bare JSON array,
so there is no envelope to unwrap:

```java
for (var chain : client.blockchain().blockchains()) {
    System.out.println(chain.name() + " read as " + chain.type());   // ETH_MAINNET read as evm
}
```

`type()` is the scanner's own lower-case spelling of the protocol family (`evm`, `tron`,
`solana`), not the upper-case `ChainFamily` used elsewhere in the API.

## Currency lists

What can be quoted, for building a currency picker:

```java
for (var fiat : client.currencies().fiats()) {
    System.out.println(fiat.code() + " — " + fiat.name());   // SEK — Swedish Krona
}

var cryptos = client.currencies().cryptos();
System.out.println(cryptos.count() + " tickers against " + cryptos.quote());
System.out.println(cryptos.byExchange().get("binance"));
```

`fiats()` are the codes `fiatToCrypto` and a pay-in's `currency` accept. `cryptos()` is rate
availability only — a ticker listed there is one the platform can price, which does not mean
your project can be paid in it. For that, use `client.blockchain().contractsAvailable()`.

## Fee estimation

`estimate` asks what a transaction would cost without signing or broadcasting anything. It
takes the transfer fields of a sign request (`native` or `token` — no `url_callback`) and
answers with the network fee and the balance the from-wallet must hold:

```java
import com.cryptochief.processing.models.EstimateTransactionRequest;

var fee = client.transactions().estimate(EstimateTransactionRequest.ofToken(
    Chain.TRON_MAINNET,
    "TFrom...",
    "TTo...",
    Amount.toBase("12.50", 6).toString(),
    "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"));
System.out.println("network fee " + fee.estimatedFee() + " (" + fee.estimatedFeeFiat() + " USD)");
System.out.println("the wallet must hold " + fee.required());
```

`estimatedFee()` is the network fee in the native coin, human-readable. `required()` is what
the from-wallet must hold of the native coin — `fee + value` for a `native` transfer, just the
fee for a `token` one. `estimatedFeeFiat()` and `requiredFiat()` are the same amounts in USD
and read as an empty string when no rate is available. A `contract` call cannot be estimated —
the API answers 400 `CONTRACT_ESTIMATE_UNSUPPORTED`.

On TRON the answer also carries a breakdown (`null` everywhere else): `energyFee()`,
`bandwidthFee()` and `activationFee()` add up to `estimatedFee()` as the gross cost,
`energy()` is the energy the transfer needs, `feeLimit()` is the on-chain cap that would be
written into the transaction, and `feeExpected()` is what the transfer is expected to cost
given the wallet's current energy pool (staked, delegated or rented) — an expectation, not a
guarantee, since the pool may be spent before the transaction is broadcast. `activationFee()`
is present only for a `native` transfer to an address that does not exist yet.

## TRON energy rental

`client.energy()` rents TRON energy for the address that will send the transfer, billed to the
same API credits as everything else. `quote` is free and prices the rental; `rent` buys it —
synchronously, so by the time it answers the energy has been delegated or the refusal reason is
known; `order` reads an order back by its idempotency key.

```java
import com.cryptochief.processing.models.EnergyOrderStatus;
import com.cryptochief.processing.models.EnergyQuoteRequest;
import com.cryptochief.processing.models.EnergyRentRequest;

var quote = client.energy().quote(EnergyQuoteRequest.of("TSender..."));
System.out.println(quote.energy() + " energy for " + quote.priceTrx() + " TRX"
        + " — burning would cost " + quote.burnPriceTrx());

// The idempotency key is what makes a retry after a timeout safe instead of a
// double purchase; rent without one is refused before any request goes out.
var order = client.withIdempotencyKey("rent-2026-09-18-0001").energy()
        .rent(EnergyRentRequest.ofQuote(quote.ref()));
if (EnergyOrderStatus.DELIVERED.equals(order.status())) {
    System.out.println("delegated " + order.deliveredEnergy() + " energy to " + order.receiveAddress());
}

// Later, from any client:
var same = client.energy().order("rent-2026-09-18-0001");
```

`rent` returns the order even when the HTTP status is not 200: a `refused` order (502 — or 402
when the credits balance did not cover it) and an `unresolved` one (409, `needsAttention()` set)
arrive with the order itself as the body and come back as the order, not as an exception — branch
on `status()` and `needsAttention()`, and on `errorCode()` for the machine reason (`error()` is
the sanitised human sentence, for logs only). Only a failure with no order to report (409
`NOT_WORTH_RENTING` / `QUOTE_EXPIRED`, a gateway error page, ...) throws `ApiException`.

A `refused` order was never charged, so its `priceUsd()`, `credits()` and `trxUsd()` are `null`
rather than zero. An `unresolved` order means the energy may already be delegated, so it must
**not** be retried — poll `order(key)` until it settles; a `refused` one may be retried with a
**new** idempotency key (after a top-up, when the refusal was 402 `INSUFFICIENT_CREDITS`).

## Native coin purchase

`client.nativeCoin()` (`native` is a Java keyword) buys the native coin of a network — TRX, ETH,
BNB, SOL, TON, ... — out of the platform's liquidity, delivered to any address you name (the
merchant pays the transfer) and billed to the same API credits as everything else. The price is
the coins at the current market rate plus the platform's transfer fee, already included; the quote
breaks it down field by field — `total_usd` is the full price and `credits` the exact amount the
buy will take from the credits balance. `quote` is free; `buy` is synchronous,
so by the time it answers the coins have been sent or the refusal reason is known; `order` reads
an order back by its idempotency key.

```java
import com.cryptochief.processing.models.NativeBuyRequest;
import com.cryptochief.processing.models.NativeOrderStatus;
import com.cryptochief.processing.models.NativeQuoteRequest;

var quote = client.nativeCoin().quote(
        NativeQuoteRequest.of(Chain.TRON_MAINNET, "TRecipient...", "0.05"));
System.out.println(quote.amount() + " TRX for " + quote.totalUsd() + " USD ("
        + quote.credits() + " credits), transfer fee included");

// The idempotency key is what makes a retry after a timeout safe instead of a
// double purchase; buy without one is refused before any request goes out.
var order = client.withIdempotencyKey("buy-2026-09-18-0001").nativeCoin()
        .buy(NativeBuyRequest.ofQuote(quote.ref()));
if (NativeOrderStatus.DELIVERED.equals(order.status())) {
    System.out.println("sent " + order.amount() + " to " + order.receiveAddress()
            + " — tx " + order.txHash());
}

// Later, from any client:
var same = client.nativeCoin().order("buy-2026-09-18-0001");
```

`buy` returns the order even when the HTTP status is not 200: a `refused` order (502 — or 402
when the credits balance did not cover it) and an `unresolved` one (409, `needsAttention()` set)
arrive with the order itself as the body and come back as the order, not as an exception — branch
on `status()` and `needsAttention()`, and on `errorCode()` for the machine reason (`error()` is
the sanitised human sentence, for logs only). Only a failure with no order to report (409
`QUOTE_EXPIRED` / `QUOTE_ALREADY_USED`, a 502 `INSUFFICIENT_LIQUIDITY` envelope, a gateway error
page, ...) throws `ApiException`.

A `refused` order was never sent or charged, so its `txHash()`, `totalUsd()` and `credits()` are
`null` rather than zero; `transferFee()`, `transferFeeUsd()`, `coinPriceUsd()` and `coinUsd()`
are always on the wire and arrive as `""` / `"0.00"` on a refusal that never got priced. An
`unresolved` order means the coins may already be sent, so it must **not** be retried — poll
`order(key)` until it settles; a `refused` one may be retried with a **new** idempotency key
(after a top-up, when the refusal was 402 `INSUFFICIENT_CREDITS`). A 409 `QUOTE_EXPIRED` or
`QUOTE_ALREADY_USED` means the quote has to be requested again — each quote lives about 90 seconds
and is single-use.

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

> **This snippet shows the encoder, not a complete swap.** Uniswap's router
> moves your input token with `transferFrom`, so it needs an ERC-20
> `approve(address,uint256)` on that token first, confirmed before the swap is
> signed — without it the swap reverts and burns the gas. And an `amountOutMin`
> of `0` accepts whatever the pool returns, which on a public mempool hands the
> trade to the first sandwich bot that sees it. Sign and confirm the approve as a
> separate transaction before signing the swap.

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
    new PollOptions(Duration.ofSeconds(5), Duration.ofMinutes(90)));
```

| Helper | Default timeout |
| ------ | --------------- |
| `waitForPayout` | 90 min. The payout stays `confirm_check` until every source reaches `requiredConfirmations()` |
| `waitForTransaction`, `waitForPayIn` | 10 min |

On timeout a helper returns the last answer, or throws if it got none.

### Confirmation counts

A sign/execute transaction always has `confirmations()` and `requiredConfirmations()`, in
execute, info, history and `transaction.*` webhooks. `confirmations()` is `0` until the
transaction is in a block and grows while it is `broadcasted`. At `requiredConfirmations()`
the transaction becomes `confirmed`. `transaction.*` webhooks are sent for final statuses only.

A payout has `confirmations()`, the lowest count among its sources, and
`requiredConfirmations()`. Each `PayoutSource` and `PayoutServiceOperation` has its own
`confirmations()`. All of them are optional. The payout stays `confirm_check` until every
source reaches `requiredConfirmations()`, then becomes `paid` and `payout.paid` is sent.

Settlement is the status: `confirmed` for a transaction, `paid` for a payout.

```java
import com.cryptochief.processing.models.TxStatus;

// On timeout waitForTransaction returns the last answer.
var tx = Polling.waitForTransaction(client, signed.uuid());
if (tx.succeeded()) {
    System.out.println("confirmed at " + tx.confirmations() + " of " + tx.requiredConfirmations());
} else if (TxStatus.BROADCASTED.equals(tx.status())) {
    System.out.println("in the network: " + tx.confirmations() + " of " + tx.requiredConfirmations());
}

var p = client.payouts().info(payout.uuid());
if (!p.succeeded() && p.confirmations() != null && p.requiredConfirmations() != null) {
    System.out.println("settling: least-confirmed source at "
        + p.confirmations() + " of " + p.requiredConfirmations());
}
```

## Webhook handling

```java
import com.cryptochief.processing.webhook.PayoutWebhookEvent;
import com.cryptochief.processing.webhook.WebhookVerificationException;
import com.cryptochief.processing.webhook.WebhookVerifier;

byte[] rawBody = request.getInputStream().readAllBytes(); // before any JSON parsing

try {
    var event = WebhookVerifier.parse(apiKey, rawBody, request::getHeader, PayoutWebhookEvent.class);
    System.out.println("payout " + event.uuid() + " → " + event.status()
        + " confirmations=" + event.confirmations()
        + " required=" + event.requiredConfirmations());
} catch (WebhookVerificationException e) {
    response.setStatus(401);
}
```

Headers:

| Header | Value |
|---|---|
| `X-Webhook-Delivery` | delivery id, 1–128 characters `[A-Za-z0-9_-]`; the same on every attempt and resend of one delivery |
| `X-CC-Timestamp` | Unix time of the attempt, seconds; decimal digits without a leading zero |
| `X-CC-Signature` | `v1=<64 hex>` |

String to sign, lines joined with `\n`, no trailing newline:

```
CC-HMAC-SHA256-WEBHOOK-V1
<X-CC-Timestamp>
<X-Webhook-Delivery>
<lowercase hex SHA-256 of the raw body>
```

`X-CC-Signature = "v1=" + lowercase hex HMAC-SHA256(key = api_key, message = string to sign)`

`verify` and `parse` take the raw body bytes and either a header lookup (`Function<String, String>`, e.g. `request::getHeader`) or a header map (`Map<String, ?>` with `String`, `String[]` or `Collection<String>` values, e.g. `com.sun.net.httpserver.Headers` or Spring `HttpHeaders`). Header names are case-insensitive in ASCII letters (in a header map U+212A also matches `k` and U+017F matches `s`, other non-ASCII characters match nothing); spaces and tabs around values are ignored. A repeated header is detected only through a header map.

| Exception | Reason |
|---|---|
| `WebhookHeadersException` | a header is missing, repeated or malformed |
| `WebhookTimestampException` | `X-CC-Timestamp` differs from the current time by more than the tolerance (300 s) |
| `WebhookSignatureException` | signature mismatch, compared in constant time |

All three extend `WebhookVerificationException`; answer 401. An empty `apiKey` throws `IllegalArgumentException`. `parse` throws `DecodeException` when a verified body does not decode into the event type.

```java
import java.time.Clock;
import java.time.Duration;
import com.cryptochief.processing.webhook.WebhookOptions;

WebhookVerifier.verify(apiKey, rawBody, headers, WebhookOptions.defaults()
    .withTolerance(Duration.ofSeconds(600))
    .withClock(Clock.systemUTC()));
```

A redelivery arrives with the same `X-Webhook-Delivery` and a new `X-CC-Timestamp`; deduplicate by `X-Webhook-Delivery`.

`RequestSigner.signWebhookV1(apiKey, timestamp, deliveryId, body)` and `RequestSigner.webhookV1StringToSign(timestamp, deliveryId, body)` produce the signature, e.g. for tests.

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
    .baseUrl("https://api-processing.crypto-chief.com") // the default; override for a white-label installation
    .requestTimeout(Duration.ofSeconds(30))
    .maxRetries(5)
    .initialRetryDelay(Duration.ofMillis(250))
    .maxRetryDelay(Duration.ofSeconds(10))
    .userAgent("my-app/1.2.3")
    .httpClient(myPreconfiguredOkHttpClient)
    .build());
```

A caller-supplied `httpClient` is not closed by the SDK. Its interceptors run after the request is
signed, so one that changes the URL, the body, `Merchant` or `Idempotency-Key` makes the signature
wrong and the request is refused with `INVALID_SIGNATURE`.

## Request signing

Every request is signed with HMAC-SHA256 v1.

| Header | Value |
|---|---|
| `Merchant` | merchant id |
| `X-CC-Timestamp` | Unix time, seconds |
| `X-CC-Nonce` | 32 hex chars, new per attempt |
| `X-CC-Signature` | `v1=<64 hex>` |

String to sign, lines joined with `\n`, no trailing newline:

```
CC-HMAC-SHA256-REQ-V1
<X-CC-Timestamp>
<X-CC-Nonce>
<METHOD>
<path>
<query without "?", or empty>
<Merchant>
<Idempotency-Key, or empty>
<lowercase hex SHA-256 of the body bytes>
```

`X-CC-Signature = "v1=" + lowercase hex HMAC-SHA256(key = api_key, message = string to sign)`

`path` is the route (`/v1/payout/execute`) without the base URL prefix, percent-decoded as the server reads it: `/v1/orders/payout%2F8814` is sent escaped and signed as `/v1/orders/payout/8814`. `query` is the raw string the URL carries. The body is the exact bytes sent: the request serialised with Jackson, without `null` properties and `null` map values, integers and decimals written exactly. Timestamp, nonce and signature are computed on every attempt. On `SIGNATURE_TIMESTAMP_OUT_OF_RANGE` the client sets its clock offset from `server_time` and repeats the request once.

`METHOD` has its `a-z` upper-cased; every other byte goes in as it is. An `apiKey` that is empty or
only spaces and tabs is no key: signing and verifying both refuse it.

`client.withIdempotencyKey(key)` returns a client that sends `Idempotency-Key` on every call it
makes, inside the signature; the value must be printable ASCII with no space at either edge. Payout
idempotency is `ExecutePayoutRequest.orderId()` — the header only labels the billing record.

`client.request(method, path, body, type)` sends a signed request with the method spelled out, for a
route the SDK has no method for; a `GET` takes a query on `path` and no body.

```java
record Balance(String credits) {}
var balance = client.request("GET", "/v1/balance", null, Balance.class);

client.withIdempotencyKey("payout-2026-09-16-0001").payouts().execute(req);
```

To sign a request the SDK does not send itself:

```java
import com.cryptochief.processing.http.RequestSigner;
import java.time.Instant;

// byte[] body: the exact bytes sent
String timestamp = Long.toString(Instant.now().getEpochSecond());
String nonce = RequestSigner.newNonce();
String sig = RequestSigner.signHmacV1(apiKey, timestamp, nonce, "POST",
    "/v1/payout/info", "", merchantId, "", body);
// POST <base URL>/v1/payout/info with body and headers:
//   Merchant: merchantId
//   X-CC-Timestamp: timestamp
//   X-CC-Nonce: nonce
//   X-CC-Signature: sig
//   Content-Type: application/json
```

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
        case ErrorCode.INSUFFICIENT_CREDITS -> { /* top up API credits */ }
        default -> throw e;
    }
} catch (NetworkException e) {
    // already retried up to options.maxRetries
}
```

`code()` is read from both error formats:

| Format | Code | `description()` |
|---|---|---|
| Gateway: `{"ok":false,"error":"<CODE>","msg":"..."}` | `error`; `msg` when `error` is `SERVICE_ERROR` | `msg` |
| White-label: `{"data":null,"error":{"status":...,"name":...,"message":"...","details":{"code":"<CODE>"}}}` | `error.details.code`, else `error.name` | `error.message` |

Without a code, `code()` is `HTTP_<status>`.

5xx is retried with exponential backoff and full jitter. 4xx is not retried. The exception is
one repeat after `SIGNATURE_TIMESTAMP_OUT_OF_RANGE`, with the clock offset taken from
`server_time`.

## Other SDKs

SDKs for other languages are listed at [docs-sdk.crypto-chief.com/processing/processing](https://docs-sdk.crypto-chief.com/processing/processing).

## License

[MIT](LICENSE) © 2026 Crypto Chief
