package com.tonic.analysis.source.ast.expr;

/**
 * Unary operators for expressions.
 */
public enum UnaryOperator {
    // Arithmetic
    NEG("-", true),
    POS("+", true),

    // Bitwise
    BNOT("~", true),

    // Logical
    NOT("!", true),

    // Increment/Decrement
    PRE_INC("++", true),
    PRE_DEC("--", true),
    POST_INC("++", false),
    POST_DEC("--", false);

    private final String symbol;
    private final boolean prefix;

    UnaryOperator(String symbol, boolean prefix) {
        this.symbol = symbol;
        this.prefix = prefix;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Checks if this is a prefix operator.
     */
    public boolean isPrefix() {
        return prefix;
    }

    /**
     * Checks if this is a postfix operator.
     */
    public boolean isPostfix() {
        return !prefix;
    }

    /**
     * Checks if this is an increment or decrement operator.
     */
    public boolean isIncDec() {
        return this == PRE_INC || this == PRE_DEC ||
               this == POST_INC || this == POST_DEC;
    }

    @Override
    public String toString() {
        return symbol;
    }
}
