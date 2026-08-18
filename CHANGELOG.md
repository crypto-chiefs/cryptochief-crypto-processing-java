# Changelog

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
