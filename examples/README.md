# Examples

| File | What it shows |
| ---- | ------------- |
| [payout/PayoutExample.java](payout/src/main/java/examples/payout/PayoutExample.java) | Estimate, execute, and poll a payout |
| [invoice/InvoiceExample.java](invoice/src/main/java/examples/invoice/InvoiceExample.java) | Create a fiat or crypto invoice and poll until paid |
| [webhook/WebhookExample.java](webhook/src/main/java/examples/webhook/WebhookExample.java) | JDK HTTP server verifying webhook signatures |

Each example reads credentials from the environment:

```
export CRYPTO_CHIEF_MERCHANT_ID=mer_...
export CRYPTO_CHIEF_API_KEY=sk_...
```
