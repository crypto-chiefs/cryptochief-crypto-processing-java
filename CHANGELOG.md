# Changelog

## [0.7.0] — 2026-09-02

Five things the platform has always answered and this SDK could not ask about, plus the one
sweep setting that decides where TRON energy is billed.

- `client.wallets().history()` — `POST /v1/wallets/history`, every pay-in that used one deposit address. A deposit wallet serves several orders over its lifetime, and this is the list of them: the answer is the same order and `meta` records `client.payIns().history()` returns, through the same types, so nothing new has to be learned to read it. The address is matched case-insensitively, so either spelling of an EVM address works, and an address your project does not own comes back as an empty page rather than an error — an empty result is not proof the address does not exist. `WalletHistoryQuery` adds the date window and paging
- `client.blockchain().blockchains()` — `POST /v1/blockchains/list`, the chains the platform's scanner is connected to and can read blocks from right now. Infrastructure, not your asset catalogue. It answers with a **bare JSON array**, not an `items` envelope, and `SupportedBlockchain.type()` is the scanner's own lower-case spelling of the protocol family (`evm`, `tron`, `solana`) rather than the upper-case `ChainFamily` used everywhere else
- `client.blockchain().contractsList()` — `POST /v1/blockchain/contracts/list`, every coin and token the platform supports on every network, for building a "which assets could we turn on" picker. Same item type as `contractsAvailable()`, which stays the list that actually governs orders, sweeps and payouts
- `AvailableContract` gained `chainFamily()` and `isTest()`. Both were on the wire on both catalogues all along and this SDK dropped them: `isTest()` is the one field that tells an asset on a test network from a real one, which matters most exactly when the platform picks the asset for you. `contract()` is an empty string for a native coin — there is no contract to name — and stays one rather than becoming null
- Sweep history filters `status` and `search`, on `history()` and `walletHistory()` alike, through `withStatus()` / `withSearch()` on the two query records. `status` takes one status; leaving it out includes every status, `skipped` among them — a sweep the platform decided against, a normal outcome rather than a failure and easy to be surprised by in a total. `search` matches the wallet address, the sweep or gas-pump transaction hash and the `task_id` on `history()`, and the hashes and `task_id` on the wallet variant. `SweepWalletHistoryQuery.forAddress()` and `sweeps().walletHistory(address)` save restating the nulls; the previous constructors are kept as delegating overloads
- `gas_source` on auto-sweep settings, read and write. It appears on all three layers of `settings()` and was dropped by the models entirely, so a caller reading `effective().gasSource()` silently got nothing. `SweepGasSource.NATIVE` has a TRON wallet burn its own TRX for energy; `SweepGasSource.RENTED` has the platform supply it. **Not setting it is not the same as setting `native`**: a wallet that never chose one gets the platform default, `rented`, so energy is supplied and billed to your API credits without anybody switching it on. `null` on an override layer means that layer does not decide — inherited, not switched off — while the effective layer is always concrete. `client.sweeps().updateGasSource()` writes just that field, and `SweepFieldWrite.inherit()` drops the override by naming `gas_source` in the `fields` mask with no value, which is the only way to clear one field and keep the others. `updateSettings()` gained a sixth `gasSource` argument; the previous four- and five-argument forms are kept as delegating overloads that leave the stored value alone
- `client.currencies().fiats()` and `client.currencies().cryptos()` — `POST /v1/currencies/fiats` and `/v1/currencies/cryptos`, the fiat codes the platform can price in and the crypto tickers it has a rate for, grouped by exchange. `fiats()` answers with a bare array. Rate availability only: a ticker listed there is one the platform can price, not one your project can be paid in
- An empty list endpoint answers with literal JSON `null`, not `[]`, and that is now an empty list rather than a `null` reference. The three new list endpoints — `blockchains()`, `currencies().fiats()`, `currencies().cryptos()` — are served by handlers that build their result from a nil slice, so "nothing to report" marshals as `null`; a caller writing the obvious for-loop got an NPE the first time the platform had nothing to say, which is exactly the case nobody tests against. A method that promises a `List` now keeps that promise: `blockchains()`, `fiats()`, `walletBalance()` and `transactionStatus()` return an empty list, and `cryptos()` returns `CryptoCurrencies.empty()`. `CryptoCurrencies` also normalizes the nulls it can carry one level down — a null `tickers`, a null `by_exchange`, and a null list under an exchange the platform names but has no tickers for, which stays a key rather than being dropped
- **`completedAt()` on a sweep is not proof it settled**, and every doc comment that implied otherwise is corrected. The sweeper stamps it at every *terminal* outcome, `failed` and `skipped` included — it is absent only while the sweep is in flight, which is what made "absent while in flight" read as "present, therefore settled" and let a failed sweep be booked as money received. The settlement signal is `sweepConfirmations()` above zero, or `confirmedAt()` on the `sweep.confirmed` webhook, which is a separate field for precisely this reason. `Sweep`, `SweepStatus` and the README say so now
- **The three sweep fee modes decide a gas *shortfall*, not the whole fee**, and `SweepFeeMode` described all three wrongly. A deposit wallet holding enough of the chain's native coin pays for its own transfer whatever the mode. Where it cannot: `CLIENT` takes the shortfall from **your own master wallet** (not from the swept wallet); `SERVICE` has the platform supply it and **bills the cost to your API credits** — the half that was missing, and the reason `service` is "billed elsewhere" rather than free; `MIX` is **the default** and tries `client` first, falling back to `service` when the master wallet cannot cover it — not, as the comment had it, the service wallet with the cost reclaimed from the sweep
- `ErrorCode` gained the codes the API reference lists and this SDK did not, among them `INVALID_SIGNATURE`, `UNKNOWN_FIELD`, `ENVIRONMENT_INVALID`, `TESTNET_NOT_ALLOWED`, `MASTER_WALLET_REQUIRED`, `MASTER_WALLET_AMBIGUOUS`, `NO_MASTER_WALLETS`, `FEE_LIMIT_EXCEEDED`, `WALLET_NOT_FOUND`, `SWEEP_SETTINGS_LOCKED` and the rest of the sweep-settings and sign/execute refusals. Codes that arrive in the envelope's `msg` under a generic `SERVICE_ERROR` sit beside the ones the gateway names in `error`, because `ApiException.code()` is the one field both reach

## [0.6.0] — 2026-09-02

Wallets can be named, re-wired and re-pointed after they exist. Until now a wallet's
webhook and its master were fixed at generation, and it had no name at all.

- Wallets carry a `label` — a name of at most 255 characters, stored and never interpreted. `GenerateWalletRequest.withLabel()` names a wallet as it is created, and `Wallet.label()` reads it back from generate, info, list and every endpoint that changes a wallet. An unnamed wallet reads as `null`, never an empty string. The previous four-argument `GenerateWalletRequest` constructor is kept as a delegating overload, so code written before labels existed still compiles
- `client.wallets().setLabel()` — `POST /v1/wallets/label`, names an existing wallet or renames it, and `clearLabel()` takes the name off. Every wallet type, master included: naming changes nothing about where funds go. An empty label is what clears the name rather than a request to leave it alone — the endpoint always writes the value it is given, so `null` is read the same way
- `client.wallets().setCallbackUrl()` — `POST /v1/wallets/callback-url`, moves the deposit webhook of a static wallet after the wallet was created; `clearCallbackUrl()` stops the announcements. Static wallets only, master and transit answer 400. A deposit already announced is not announced again to the new URL; the change applies from there on
- `client.wallets().rebindMaster()` — `POST /v1/wallets/rebind-master`, re-points a transit or static wallet at another master wallet of the same project. No money moves: what changes is where the NEXT sweep settles, including sweeps already queued and not yet sent. Anything already swept stays on the previous master, and getting it across is a separate transfer. Idempotent, and the new master has to be the project's own, of the same chain family, and not frozen
- `ErrorCode.LABEL_TOO_LONG` — the code behind the 400 a label longer than 255 characters answers with. It reaches `ApiException.code()` as that constant, with `"label is longer than 255 characters"` in `description()`. Nothing changed in the mapping to make that true: this SDK has always taken the code from the envelope's `error` field when the gateway named the refusal itself, and lifts `msg` into the code only when `error` is the generic `SERVICE_ERROR` of a relayed upstream refusal. Both shapes are now pinned by tests, and `ApiException.code()` documents the rule, so a caller matching on `ErrorCode` constants and one matching on upstream tokens like `wallet_not_found` are both switching on the same one field

## [0.4.0] — 2026-08-28

Same API surface as the Go SDK v0.4.0; the version numbers across the SDK family
line up again.

- Auto-sweep settings: `client.sweeps().settings()` and `client.sweeps().updateSettings()` — read and write the policy that decides when a deposit wallet is swept (on arrival, above a USD threshold, or never). The read returns three layers — effective, override and project default — because only the three together say whether a value is the wallet's own or inherited, and inheritance is per field
- `SweepFieldWrite.set()` writes a value and `SweepFieldWrite.inherit()` stops overriding a field; `null` leaves it alone. `null` could not carry both meanings
- Sweep records now carry what the platform has always sent and this SDK dropped on the floor: the trigger (`typeWork`), the fee breakdown (estimated and actual), the gas-pump transaction hash, and the new confirmation fields
- Sweep status tells a broadcast sweep from a settled one. `broadcasted` means the transaction is out and not yet confirmed; `completed` means the chain confirmed it, with the confirmation count and settlement time filled in. Earlier platform versions reported `completed` at broadcast, so a sweep could read as settled while its transaction was unconfirmed or dropped
- Pay-in create accepts `environment` (`mainnet` / `testnet`), which constrains the asset the platform picks in fiat mode and for `ANY` networks — the case where an unconstrained pick could put a real payment on a test chain
- Pay-in create and select-asset accept `master_wallet_address`, pinning the order's deposit wallet to one of the project's master wallets
- `BuildInfo.VERSION`, used in the User-Agent header, was still `0.1.0`. Corrected
- The README's dependency snippets pinned `0.1.0`, a full release behind. Corrected
- `CreatePayInRequest` gained two trailing components. The previous 16-argument constructor is kept as a delegating overload, so existing call sites still compile; `withMasterWallet` / `withEnvironment` avoid restating sixteen nulls

## [0.2.0] — 2026-08-18

- `CreditsService` (`client.credits().balance()`) — `POST /v1/credits/balance`, billing-exempt credits/USD balance check with postpaid and gas-operation gate info
- `client.credits().topup(CreditsTopupRequest)` — `POST /v1/credits/topup`, billing-exempt topup invoice (`USDT`/`USDC`) returning a hosted `payment_link`

## [0.1.0] — 2026-06-09

Initial release.

- `CryptoChiefClient` with synchronous service accessors
- Services: Payouts, Transactions, PayIns, Wallets, Sweeps, Withdrawals, StaticDeposits, Blockchain, Currencies
- Two-phase sign/execute on EVM, TRON, Solana, TON, XRP, UTXO; batch payouts
- High-level helpers: `signEvmCall`, `erc20Transfer`, `signAnchorCall` + `Borsh`, `signSolanaCall`, `signTonCall`, `jettonTransfer`, `nftTransfer`, `sendTonComment`
- TON cell + BoC encoder; TEP-74 / TEP-62 / op-0 message builders
- TON, TRON, Solana address parsing
- Self-contained Keccak-256 and EVM ABI encoder
- Webhook verification with typed event records
- RSA-OAEP / SHA-256 decryption with PKCS#1 and PKCS#8 PEM loaders
- Polling: `Polling.waitForPayout`, `waitForTransaction`, `waitForPayIn`
- Maven Central publishing via `com.vanniktech.maven.publish`
