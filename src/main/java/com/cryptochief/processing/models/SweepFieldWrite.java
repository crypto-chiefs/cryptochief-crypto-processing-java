package com.cryptochief.processing.models;

/**
 * A sweep-policy field being written.
 *
 * <p>{@link #set(String)} writes a value; {@link #inherit()} stops overriding the field and
 * goes back to inheriting it. The API expresses the second by naming the field with no
 * value, which {@code null} cannot say here because it already means "not supplied - leave
 * this field alone". The two are different instructions: one changes nothing, the other
 * resets a value.
 */
public final class SweepFieldWrite {

    private static final SweepFieldWrite INHERIT = new SweepFieldWrite(null);

    private final String value;

    private SweepFieldWrite(String value) {
        this.value = value;
    }

    /** Write this value. */
    public static SweepFieldWrite set(String value) {
        return new SweepFieldWrite(value);
    }

    /** Stop overriding the field; inherit it again. */
    public static SweepFieldWrite inherit() {
        return INHERIT;
    }

    /** The value being written, or null when the field is being reset to inherited. */
    public String value() {
        return value;
    }
}
