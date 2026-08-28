package com.cryptochief.processing.services;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.ForceSweepRequest;
import com.cryptochief.processing.models.ForceSweepResponse;
import com.cryptochief.processing.models.SweepFieldWrite;
import com.cryptochief.processing.models.SweepHistoryQuery;
import com.cryptochief.processing.models.SweepHistoryResponse;
import com.cryptochief.processing.models.SweepSettings;
import com.cryptochief.processing.models.SweepSettingsQuery;
import com.cryptochief.processing.models.SweepSettingsUpdateRequest;
import com.cryptochief.processing.models.SweepWalletHistoryQuery;

import java.util.ArrayList;
import java.util.List;

/** Transit → master sweep endpoints. */
public final class SweepsService {

    private final HttpTransport transport;

    public SweepsService(HttpTransport transport) {
        this.transport = transport;
    }

    public ForceSweepResponse force(String address, Chain network) {
        return transport.send("/v1/sweeps/force",
                new ForceSweepRequest(address, network), ForceSweepResponse.class);
    }

    public SweepHistoryResponse history(SweepHistoryQuery query) {
        return transport.send("/v1/sweeps/history", query, SweepHistoryResponse.class);
    }

    public SweepHistoryResponse history() {
        return history(SweepHistoryQuery.empty());
    }

    public SweepHistoryResponse walletHistory(SweepWalletHistoryQuery query) {
        return transport.send("/v1/sweeps/wallet/history", query, SweepHistoryResponse.class);
    }

    /**
     * The auto-sweep policy in force for one wallet, together with what it overrides and
     * what it inherits.
     *
     * <p>Scoped to the caller's own wallets: an address that is not the project's answers
     * {@code WALLET_NOT_FOUND}.
     */
    public SweepSettings settings(SweepSettingsQuery query) {
        return transport.send("/v1/sweeps/settings", query, SweepSettings.class);
    }

    /** The project's own default policy, with no wallet in the question. */
    public SweepSettings settings() {
        return settings(SweepSettingsQuery.projectDefault());
    }

    /**
     * Write a wallet's auto-sweep policy. Returns the settings as they stand afterwards,
     * so the caller sees what the write resolved to without asking again.
     *
     * <p>A {@code null} argument leaves that field alone; {@link SweepFieldWrite#inherit()}
     * stops overriding it. Inheritance is per field, so writing the mode leaves the fee
     * mode as it was.
     *
     * <p>Refusals are named: {@code TYPE_WORK_INVALID}, {@code FEE_MODE_INVALID},
     * {@code THRESHOLD_INVALID}, {@code THRESHOLD_MUST_BE_POSITIVE},
     * {@code THRESHOLD_REQUIRED_FOR_THRESHOLD_MODE}, and {@code SWEEP_SETTINGS_LOCKED} when
     * an operator has pinned the policy.
     */
    public SweepSettings updateSettings(
            String address,
            Chain networkCode,
            SweepFieldWrite typeWork,
            SweepFieldWrite thresholdAmountUsd,
            SweepFieldWrite feeMode) {

        List<String> fields = new ArrayList<>();
        if (typeWork != null) {
            fields.add("type_work");
        }
        if (thresholdAmountUsd != null) {
            fields.add("threshold_amount_usd");
        }
        if (feeMode != null) {
            fields.add("fee_mode");
        }

        var body = new SweepSettingsUpdateRequest(
                address,
                networkCode,
                fields.isEmpty() ? null : fields,
                typeWork == null ? null : typeWork.value(),
                thresholdAmountUsd == null ? null : thresholdAmountUsd.value(),
                feeMode == null ? null : feeMode.value());

        return transport.send("/v1/sweeps/settings/update", body, SweepSettings.class);
    }

    /** As {@link #updateSettings(String, Chain, SweepFieldWrite, SweepFieldWrite, SweepFieldWrite)}, for an address on every network. */
    public SweepSettings updateSettings(
            String address,
            SweepFieldWrite typeWork,
            SweepFieldWrite thresholdAmountUsd,
            SweepFieldWrite feeMode) {
        return updateSettings(address, null, typeWork, thresholdAmountUsd, feeMode);
    }
}
