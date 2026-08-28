package com.cryptochief.processing.models;

/**
 * The two environments an order can belong to.
 *
 * <p>A project may be allowed one or both; asking for testnet on a project that does not
 * permit it is refused with {@code TESTNET_NOT_ALLOWED} rather than quietly served on
 * mainnet, and a value that is neither is {@code ENVIRONMENT_INVALID} rather than a silent
 * fallback.
 */
public final class Environment {

    public static final String MAINNET = "mainnet";
    public static final String TESTNET = "testnet";

    private Environment() {}
}
