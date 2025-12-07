package com.tonic.utill;

/**
 * Builder class for constructing access flag integers using method chaining.
 * Utilizes the {@code Modifiers} class to set specific access flags.
 * <p>
 * The {@code AccessBuilder} class provides a fluent API to set various access modifiers
 * based on the JVM specification. Each method corresponds to a specific access flag
 * and modifies the internal state accordingly.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>{@code
 * int accessFlags = new AccessBuilder()
 *                         .setPublic()
 *                         .setStatic()
 *                         .setFinal()
 *                         .build();
 * }</pre>
 */
public final class AccessBuilder {

    private int accessFlags;

    /**
     * Constructs a new {@code AccessBuilder} instance with no access flags set.
     */
    public AccessBuilder() {
        this.accessFlags = 0;
    }

    /**
     * Sets the {@code public} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setPublic() {
        this.accessFlags = Modifiers.setPublic(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code private} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setPrivate() {
        this.accessFlags = Modifiers.setPrivate(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code protected} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setProtected() {
        this.accessFlags = Modifiers.setProtected(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code static} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setStatic() {
        this.accessFlags = Modifiers.setStatic(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code final} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setFinal() {
        this.accessFlags = Modifiers.setFinal(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code synchronized} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setSynchronized() {
        this.accessFlags = Modifiers.setSynchronized(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code bridge} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setBridge() {
        this.accessFlags = Modifiers.setBridge(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code varargs} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setVarArgs() {
        this.accessFlags = Modifiers.setVarArgs(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code native} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setNative() {
        this.accessFlags = Modifiers.setNative(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code abstract} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setAbstract() {
        this.accessFlags = Modifiers.setAbstract(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code strictfp} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setStrictfp() {
        this.accessFlags = Modifiers.setStrict(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code synthetic} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setSynthetic() {
        this.accessFlags = Modifiers.setSynthetic(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code annotation} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setAnnotation() {
        this.accessFlags = Modifiers.setAnnotation(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code enum} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setEnum() {
        this.accessFlags = Modifiers.setEnum(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code volatile} access flag.
     * <p>
     * Note: Typically used for fields.
     * </p>
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setVolatile() {
        this.accessFlags = Modifiers.setVolatile(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code transient} access flag.
     * <p>
     * Note: Typically used for fields.
     * </p>
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setTransient() {
        this.accessFlags = Modifiers.setTransient(this.accessFlags);
        return this;
    }

    /**
     * Sets the {@code interface} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setInterface() {
        this.accessFlags = Modifiers.setInterface(this.accessFlags);
        return this;
    }

    /**
     * Builds and returns the final access flags integer.
     *
     * @return the combined access flags as an integer
     */
    public int build() {
        return this.accessFlags;
    }

    /**
     * Resets the builder to its initial state, clearing all set access flags.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder reset() {
        this.accessFlags = 0;
        return this;
    }

    /**
     * Sets the access flags to a specific value.
     * <p>
     * This method allows initializing the builder with existing access flags.
     * </p>
     *
     * @param accessFlags the access flags integer to set
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setAccessFlags(int accessFlags) {
        this.accessFlags = accessFlags;
        return this;
    }
}
