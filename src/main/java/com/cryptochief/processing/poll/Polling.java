package com.cryptochief.processing.poll;

import com.cryptochief.processing.CryptoChiefClient;
import com.cryptochief.processing.PollOptions;
import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.models.PayIn;
import com.cryptochief.processing.models.PayoutInfo;
import com.cryptochief.processing.models.TransactionInfo;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Blocking poll helpers for payouts, transactions, and pay-ins. */
public final class Polling {

    private Polling() {}

    public static PayoutInfo waitForPayout(CryptoChiefClient client, String uuid, PollOptions options) {
        return pollUntilTerminal(options,
                () -> client.payouts().info(uuid),
                PayoutInfo::isTerminal);
    }

    public static PayoutInfo waitForPayout(CryptoChiefClient client, String uuid) {
        return waitForPayout(client, uuid, PollOptions.defaults());
    }

    public static TransactionInfo waitForTransaction(CryptoChiefClient client, String uuid, PollOptions options) {
        return pollUntilTerminal(options,
                () -> client.transactions().info(uuid),
                TransactionInfo::isTerminal);
    }

    public static TransactionInfo waitForTransaction(CryptoChiefClient client, String uuid) {
        return waitForTransaction(client, uuid, PollOptions.defaults());
    }

    public static PayIn waitForPayIn(CryptoChiefClient client, String uuid, PollOptions options) {
        return pollUntilTerminal(options,
                () -> client.payIns().info(uuid),
                PayIn::isTerminal);
    }

    public static PayIn waitForPayIn(CryptoChiefClient client, String uuid) {
        return waitForPayIn(client, uuid, PollOptions.defaults());
    }

    private static <T> T pollUntilTerminal(
            PollOptions options, Supplier<T> fetch, Predicate<T> isTerminal) {
        Duration timeout = options.timeout();
        Duration interval = options.interval();
        long deadline = System.nanoTime() + timeout.toNanos();
        T last = null;
        while (System.nanoTime() < deadline) {
            try {
                T obj = fetch.get();
                last = obj;
                if (isTerminal.test(obj)) return obj;
            } catch (ApiException e) {
                if (!e.retryable()) throw e;
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (last != null) return last;
                throw new RuntimeException("polling interrupted", e);
            }
        }
        if (last != null) return last;
        throw new RuntimeException("cryptochief: poll did not reach terminal in " + timeout);
    }
}
