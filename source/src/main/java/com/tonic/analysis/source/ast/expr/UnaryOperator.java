package com.tonic.analysis.source.ast.expr;

/**
 * Unary operators for expressions.
 */
public enum UnaryOperator
{
    /**
     * Arithmetic negation, {@code -}, applied to a single numeric operand.
     */
    NEG("-", true),
    /**
     * Unary plus, {@code +}, which leaves the value alone but still promotes a
     * narrower operand to int.
     */
    POS("+", true),

    /**
     * Bitwise complement, {@code ~}, flipping every bit of an integral operand.
     */
    BNOT("~", true),

    /**
     * Logical negation, {@code !}, applying to a boolean operand.
     */
    NOT("!", true),

    /**
     * Prefix increment, {@code ++x}, which yields the already-incremented
     * value.
     */
    PRE_INC("++", true),
    /**
     * Prefix decrement, {@code --x}, which yields the already-decremented
     * value.
     */
    PRE_DEC("--", true),
    /**
     * Postfix increment, {@code x++}, which yields the operand's value from
     * before the increment.
     */
    POST_INC("++", false),
    /**
     * Postfix decrement, {@code x--}, which yields the operand's value from
     * before the decrement.
     */
    POST_DEC("--", false);

    private final String symbol;
    private final boolean prefix;

    UnaryOperator(String symbol, boolean prefix)
    {
        this.symbol = symbol;
        this.prefix = prefix;
    }

    /**
     * @return the symbol
     */
    public String getSymbol()
    {
        return symbol;
    }

    /**
     * @return true if the symbol is written before its operand
     */
    public boolean isPrefix()
    {
        return prefix;
    }

    /**
     * @return true if the symbol is written after its operand
     */
    public boolean isPostfix()
    {
        return !prefix;
    }

    /**
     * @return true for the four increment and decrement operators
     */
    public boolean isIncDec()
    {
        return this == PRE_INC || this == PRE_DEC ||
               this == POST_INC || this == POST_DEC;
    }

    @Override
    public String toString()
    {
        return symbol;
    }
}
