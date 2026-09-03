package com.cryptochief.processing.models;

/** Delivery statuses in {@link WebhookDelivery#status()}. */
public final class WebhookDeliveryStatus {
    /** Queued, not yet attempted (or waiting for a retry). */
    public static final String PENDING = "pending";
    /** A worker holds it right now. */
    public static final String IN_PROGRESS = "in_progress";
    /** Your endpoint answered 2xx. */
    public static final String DELIVERED = "delivered";
    /** Every attempt so far was refused or timed out. */
    public static final String FAILED = "failed";
    /** Superseded by a newer event before it was ever sent. */
    public static final String CANCELLED = "cancelled";

    private WebhookDeliveryStatus() {}
}
