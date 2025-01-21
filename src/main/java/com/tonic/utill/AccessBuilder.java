package com.tonic.utill;

public final class AccessBuilder {

    // Internal integer to hold the access flags
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
        this.accessFlags |= Access.ACC_PUBLIC;
        return this;
    }

    /**
     * Sets the {@code private} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setPrivate() {
        this.accessFlags |= Access.ACC_PRIVATE;
        return this;
    }

    /**
     * Sets the {@code protected} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setProtected() {
        this.accessFlags |= Access.ACC_PROTECTED;
        return this;
    }

    /**
     * Sets the {@code static} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setStatic() {
        this.accessFlags |= Access.ACC_STATIC;
        return this;
    }

    /**
     * Sets the {@code final} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setFinal() {
        this.accessFlags |= Access.ACC_FINAL;
        return this;
    }

    /**
     * Sets the {@code synchronized} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setSynchronized() {
        this.accessFlags |= Access.ACC_SYNCHRONIZED;
        return this;
    }

    /**
     * Sets the {@code bridge} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setBridge() {
        this.accessFlags |= Access.ACC_BRIDGE;
        return this;
    }

    /**
     * Sets the {@code varargs} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setVarArgs() {
        this.accessFlags |= Access.ACC_VARARGS;
        return this;
    }

    /**
     * Sets the {@code native} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setNative() {
        this.accessFlags |= Access.ACC_NATIVE;
        return this;
    }

    /**
     * Sets the {@code abstract} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setAbstract() {
        this.accessFlags |= Access.ACC_ABSTRACT;
        return this;
    }

    /**
     * Sets the {@code strictfp} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setStrictfp() {
        this.accessFlags |= Access.ACC_STRICT;
        return this;
    }

    /**
     * Sets the {@code synthetic} access flag.
     *
     * @return the current {@code AccessBuilder} instance for chaining
     */
    public AccessBuilder setSynthetic() {
        this.accessFlags |= Access.ACC_SYNTHETIC;
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
