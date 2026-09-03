package com.cryptochief.processing.services;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.StaticDepositResendResult;
import com.cryptochief.processing.models.UuidRequest;
import com.cryptochief.processing.models.WebhookDelivery;
import com.cryptochief.processing.models.WebhookResendResult;

/**
 * Reads and re-fires the platform's OUTBOUND webhooks - the deliveries made to your
 * endpoint. (Verifying INCOMING webhooks is {@code com.cryptochief.processing.webhook}.)
 *
 * <p>A delivery is named by the uuid the platform put on it in the {@code X-Webhook-Delivery}
 * header ({@link com.cryptochief.processing.webhook.WebhookVerifier#DELIVERY_HEADER}). It is
 * the same across every attempt and resend of that delivery - the natural idempotency key for
 * your receiver - and it is the only handle there is: the API has no listing of deliveries,
 * and the payload names the order, not the delivery. Keep it when you log an incoming webhook.
 */
public final class WebhooksService {

    private final HttpTransport transport;

    public WebhooksService(HttpTransport transport) {
        this.transport = transport;
    }

    /**
     * One delivery by the uuid from its {@code X-Webhook-Delivery} header. A delivery that is
     * not this project's is {@code NOT_FOUND}, the same as one that does not exist.
     */
    public WebhookDelivery info(String deliveryUuid) {
        return transport.send("/v1/webhooks/info", new UuidRequest(deliveryUuid), WebhookDelivery.class);
    }

    /**
     * Send one delivery to your endpoint again, right now.
     *
     * <p>Refused with an {@link com.cryptochief.processing.exceptions.ApiException} whose code is:
     * <ul>
     *   <li>{@code DELIVERY_SUPERSEDED} (409) - a newer event exists for the same object.
     *   Re-sending {@code invoice.in_mempool} after {@code invoice.paid} would tell your system
     *   the order went backwards, so only the latest event may be resent. Permanent; the newer
     *   event's name is in the message.</li>
     *   <li>{@code DELIVERY_IN_FLIGHT} (409) - a worker is delivering it right now, or it is
     *   already scheduled for an automatic retry. Try again in a moment.</li>
     *   <li>{@code RESEND_TOO_SOON} (429) - resent under a minute ago; {@code Retry-After} is set.</li>
     * </ul>
     * A successful manual delivery is billed as {@code /v1/webhook/resend}; a refused one is not.
     */
    public WebhookResendResult resend(String deliveryUuid) {
        return transport.send("/v1/webhooks/resend", new UuidRequest(deliveryUuid), WebhookResendResult.class);
    }

    /**
     * Re-fire the NEWEST webhook of one static deposit, named by the deposit's own uuid - for
     * when you have the deposit and not the delivery. Older events of the deposit are
     * superseded and are not resent.
     *
     * <p>Refused with {@code NO_DELIVERIES} (409) when the deposit is yours but no webhook was
     * ever queued for it: it arrived on a static wallet with no {@code callback_url}. The
     * per-delivery refusals of {@link #resend(String)} apply as well.
     */
    public StaticDepositResendResult resendStaticDeposit(String depositUuid) {
        return transport.send("/v1/static-deposits/resend", new UuidRequest(depositUuid), StaticDepositResendResult.class);
    }
}
