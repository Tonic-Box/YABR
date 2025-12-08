package com.tonic.analysis.source.ast.expr;

/**
 * Binary operators for expressions.
 */
public enum BinaryOperator {
    // Arithmetic
    ADD("+", 12, true),
    SUB("-", 12, true),
    MUL("*", 13, true),
    DIV("/", 13, true),
    MOD("%", 13, true),

    // Bitwise
    BAND("&", 8, true),
    BOR("|", 6, true),
    BXOR("^", 7, true),
    SHL("<<", 11, true),
    SHR(">>", 11, true),
    USHR(">>>", 11, true),

    // Comparison
    EQ("==", 9, true),
    NE("!=", 9, true),
    LT("<", 10, true),
    LE("<=", 10, true),
    GT(">", 10, true),
    GE(">=", 10, true),

    // Logical (short-circuit)
    AND("&&", 5, true),
    OR("||", 4, true),

    // Assignment operators (for compound assignments)
    ASSIGN("=", 1, false),
    ADD_ASSIGN("+=", 1, false),
    SUB_ASSIGN("-=", 1, false),
    MUL_ASSIGN("*=", 1, false),
    DIV_ASSIGN("/=", 1, false),
    MOD_ASSIGN("%=", 1, false),
    BAND_ASSIGN("&=", 1, false),
    BOR_ASSIGN("|=", 1, false),
    BXOR_ASSIGN("^=", 1, false),
    SHL_ASSIGN("<<=", 1, false),
    SHR_ASSIGN(">>=", 1, false),
    USHR_ASSIGN(">>>=", 1, false);

    private final String symbol;
    private final int precedence;
    private final boolean leftAssociative;

    BinaryOperator(String symbol, int precedence, boolean leftAssociative) {
        this.symbol = symbol;
        this.precedence = precedence;
        this.leftAssociative = leftAssociative;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Gets the precedence level (higher = binds tighter).
     */
    public int getPrecedence() {
        return precedence;
    }

    /**
     * Checks if this operator is left-associative.
     */
    public boolean isLeftAssociative() {
        return leftAssociative;
    }

    /**
     * Checks if this is an assignment operator.
     */
    public boolean isAssignment() {
        return this == ASSIGN || name().endsWith("_ASSIGN");
    }

    /**
     * Checks if this is a comparison operator.
     */
    public boolean isComparison() {
        return this == EQ || this == NE || this == LT || this == LE ||
               this == GT || this == GE;
    }

    /**
     * Checks if this is a logical operator (short-circuit).
     */
    public boolean isLogical() {
        return this == AND || this == OR;
    }

    /**
     * Checks if this is a bitwise operator.
     */
    public boolean isBitwise() {
        return this == BAND || this == BOR || this == BXOR ||
               this == SHL || this == SHR || this == USHR;
    }

    @Override
    public String toString() {
        return symbol;
    }
}
