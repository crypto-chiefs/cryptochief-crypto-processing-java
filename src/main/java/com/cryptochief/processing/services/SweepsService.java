package com.cryptochief.processing.services;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.ForceSweepRequest;
import com.cryptochief.processing.models.ForceSweepResponse;
import com.cryptochief.processing.models.SweepFieldWrite;
import com.cryptochief.processing.models.SweepGasSource;
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

    /** As {@link #walletHistory(SweepWalletHistoryQuery)}, unfiltered. */
    public SweepHistoryResponse walletHistory(String address) {
        return walletHistory(SweepWalletHistoryQuery.forAddress(address));
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
     * <p>{@code gasSource} is a {@link SweepGasSource} value and applies to TRON, where it
     * decides whether the wallet burns its own TRX for energy or the platform supplies it
     * and bills your API credits. Leaving it null leaves the stored value alone - which is
     * <strong>not</strong> the same as {@link SweepGasSource#NATIVE}: a wallet that never
     * chose one gets the platform default, {@link SweepGasSource#RENTED}.
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
            SweepFieldWrite feeMode,
            SweepFieldWrite gasSource) {

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
        if (gasSource != null) {
            fields.add("gas_source");
        }

        var body = new SweepSettingsUpdateRequest(
                address,
                networkCode,
                fields.isEmpty() ? null : fields,
                typeWork == null ? null : typeWork.value(),
                thresholdAmountUsd == null ? null : thresholdAmountUsd.value(),
                feeMode == null ? null : feeMode.value(),
                gasSource == null ? null : gasSource.value());

        return transport.send("/v1/sweeps/settings/update", body, SweepSettings.class);
    }

    /**
     * As {@link #updateSettings(String, Chain, SweepFieldWrite, SweepFieldWrite, SweepFieldWrite, SweepFieldWrite)},
     * leaving the wallet's gas source alone. Kept so code written before it existed still
     * compiles - and note that leaving it alone is not the same as choosing
     * {@link SweepGasSource#NATIVE}.
     */
    public SweepSettings updateSettings(
            String address,
            Chain networkCode,
            SweepFieldWrite typeWork,
            SweepFieldWrite thresholdAmountUsd,
            SweepFieldWrite feeMode) {
        return updateSettings(address, networkCode, typeWork, thresholdAmountUsd, feeMode, null);
    }

    /**
     * As {@link #updateSettings(String, Chain, SweepFieldWrite, SweepFieldWrite, SweepFieldWrite)},
     * for an address on every network.
     */
    public SweepSettings updateSettings(
            String address,
            SweepFieldWrite typeWork,
            SweepFieldWrite thresholdAmountUsd,
            SweepFieldWrite feeMode) {
        return updateSettings(address, null, typeWork, thresholdAmountUsd, feeMode, null);
    }

    /**
     * Write only the wallet's gas source, on every network the address exists on, leaving
     * its mode, threshold and fee mode as they are.
     *
     * <p>{@code SweepFieldWrite.set(SweepGasSource.NATIVE)} has the wallet burn its own TRX;
     * {@link SweepFieldWrite#inherit()} drops the override so the wallet inherits again -
     * which returns it to the platform default {@link SweepGasSource#RENTED}, not to "off".
     */
    public SweepSettings updateGasSource(String address, SweepFieldWrite gasSource) {
        return updateSettings(address, null, null, null, null, gasSource);
    }

    /** As {@link #updateGasSource(String, SweepFieldWrite)}, for one network only. */
    public SweepSettings updateGasSource(String address, Chain networkCode, SweepFieldWrite gasSource) {
        return updateSettings(address, networkCode, null, null, null, gasSource);
    }
}
