package com.cryptochief.processing.models;

/**
 * What is bought to move a sweep on TRON: {@link #NATIVE} burns the wallet's own TRX for
 * energy, {@link #RENTED} has the platform supply the energy so nothing is burnt. Carried
 * and ignored on every other chain.
 *
 * <p>This answers <em>what is bought</em> where {@link SweepFeeMode} answers <em>who covers
 * the network fees</em>, and the two are independent: energy can be supplied under any fee
 * mode, and it is billed to your API credits whatever the fee mode says.
 *
 * <p><strong>Not setting it is not the same as setting {@link #NATIVE}.</strong> A wallet
 * that never chose one gets the platform default, which is {@link #RENTED} - so energy is
 * supplied, and billed to your credits, without anybody having switched it on. To have the
 * wallet burn its own TRX, write {@link #NATIVE} explicitly.
 *
 * <p>Read {@code settings().effective().gasSource()} to see what will actually happen; it is
 * always a concrete value. A {@code null} on the override layer means only that this layer
 * does not decide - the value is inherited, not switched off.
 */
public final class SweepGasSource {

    /** The wallet burns its own TRX for energy. */
    public static final String NATIVE = "native";

    /** The platform supplies the energy, billed to your API credits. The default. */
    public static final String RENTED = "rented";

    private SweepGasSource() {}
}
