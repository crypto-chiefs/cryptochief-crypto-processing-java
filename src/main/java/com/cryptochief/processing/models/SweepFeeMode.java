package com.cryptochief.processing.models;

/**
 * Who covers a <em>shortfall</em> of native coin when a deposit wallet is swept.
 *
 * <p>A deposit wallet that already holds enough of the chain's native coin pays for its own
 * transfer, <strong>whatever the mode</strong>. These three only decide where the missing
 * gas comes from when it does not:
 *
 * <ul>
 *   <li>{@link #CLIENT} - from your own <strong>master wallet</strong>. The platform
 *       transfers what the transfer needs and the cost is yours.</li>
 *   <li>{@link #SERVICE} - the platform supplies it, and <strong>the cost is billed to your
 *       API credits</strong>. The master wallet is not touched for gas.</li>
 *   <li>{@link #MIX} - <strong>the default.</strong> Tries {@link #CLIENT} first and falls
 *       back to {@link #SERVICE} when the master wallet cannot cover it.</li>
 * </ul>
 *
 * <p>Nothing here is free: {@link #SERVICE}, and the {@link #MIX} sweeps that fall back to
 * it, spend API credits rather than on-chain balance, which is a cost no fee field on the
 * sweep announces as a wallet movement.
 *
 * <p>Independent of {@link SweepGasSource}, which answers <em>what is bought</em> on TRON.
 * Rented energy is billed to your API credits in every fee mode, this one included.
 */
public final class SweepFeeMode {

    /** The shortfall comes from your own master wallet. */
    public static final String CLIENT = "client";

    /** The platform covers the shortfall and bills it to your API credits. */
    public static final String SERVICE = "service";

    /** The default: {@link #CLIENT} first, {@link #SERVICE} when the master cannot cover it. */
    public static final String MIX = "mix";

    private SweepFeeMode() {}
}
