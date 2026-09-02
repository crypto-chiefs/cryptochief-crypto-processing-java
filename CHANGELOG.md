# Changelog

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
