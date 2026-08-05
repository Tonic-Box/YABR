package com.tonic.analysis.source.ast.expr;

/**
 * Binary operators for expressions.
 */
public enum BinaryOperator
{
    /**
     * Addition, {@code +}; also string concatenation when either operand is a
     * string.
     */
    ADD("+", 12, true),
    /**
     * Subtraction, {@code -}.
     */
    SUB("-", 12, true),
    /**
     * Multiplication, {@code *}.
     */
    MUL("*", 13, true),
    /**
     * Division, {@code /}, truncating toward zero on integral operands.
     */
    DIV("/", 13, true),
    /**
     * Remainder, {@code %}, which takes the sign of the dividend.
     */
    MOD("%", 13, true),

    /**
     * Bitwise and, {@code &}; on boolean operands a logical and that always
     * evaluates both sides.
     */
    BAND("&", 8, true),
    /**
     * Bitwise or, {@code |}; on boolean operands a logical or that always
     * evaluates both sides.
     */
    BOR("|", 6, true),
    /**
     * Bitwise exclusive or, {@code ^}; logical xor on boolean operands.
     */
    BXOR("^", 7, true),
    /**
     * Left shift, {@code <<}.
     */
    SHL("<<", 11, true),
    /**
     * Signed right shift, {@code >>}, which preserves the sign bit.
     */
    SHR(">>", 11, true),
    /**
     * Unsigned right shift, {@code >>>}, which fills the vacated high bits with
     * zeroes regardless of sign.
     */
    USHR(">>>", 11, true),

    /**
     * Equality test, {@code ==}; reference identity when the operands are
     * references.
     */
    EQ("==", 9, true),
    /**
     * Inequality test, {@code !=}; reference identity when the operands are
     * references.
     */
    NE("!=", 9, true),
    /**
     * Less-than relational test, {@code <}.
     */
    LT("<", 10, true),
    /**
     * Less-than-or-equal relational test, {@code <=}.
     */
    LE("<=", 10, true),
    /**
     * Greater-than relational test, {@code >}.
     */
    GT(">", 10, true),
    /**
     * Greater-than-or-equal relational test, {@code >=}.
     */
    GE(">=", 10, true),

    /**
     * Short-circuiting logical and, {@code &&}; the right operand is skipped
     * when the left is false.
     */
    AND("&&", 5, true),
    /**
     * Short-circuiting logical or, {@code ||}; the right operand is skipped when
     * the left is true.
     */
    OR("||", 4, true),

    /**
     * Plain assignment, {@code =}; the lowest-precedence operator and the only
     * non-compound one that groups right to left.
     */
    ASSIGN("=", 1, false),
    /**
     * Add and assign, {@code +=}; also string append-assign.
     */
    ADD_ASSIGN("+=", 1, false),
    /**
     * Subtract and assign, {@code -=}.
     */
    SUB_ASSIGN("-=", 1, false),
    /**
     * Multiply and assign, {@code *=}.
     */
    MUL_ASSIGN("*=", 1, false),
    /**
     * Divide and assign, {@code /=}.
     */
    DIV_ASSIGN("/=", 1, false),
    /**
     * Remainder and assign, {@code %=}.
     */
    MOD_ASSIGN("%=", 1, false),
    /**
     * Bitwise and and assign, {@code &=}; also non-short-circuiting and-assign
     * on booleans.
     */
    BAND_ASSIGN("&=", 1, false),
    /**
     * Bitwise or and assign, {@code |=}; also non-short-circuiting or-assign on
     * booleans.
     */
    BOR_ASSIGN("|=", 1, false),
    /**
     * Bitwise exclusive-or and assign, {@code ^=}; also logical xor-assign on
     * booleans.
     */
    BXOR_ASSIGN("^=", 1, false),
    /**
     * Left shift and assign, {@code <<=}.
     */
    SHL_ASSIGN("<<=", 1, false),
    /**
     * Signed right shift and assign, {@code >>=}.
     */
    SHR_ASSIGN(">>=", 1, false),
    /**
     * Unsigned right shift and assign, {@code >>>=}.
     */
    USHR_ASSIGN(">>>=", 1, false);

    private final String symbol;
    private final int precedence;
    private final boolean leftAssociative;

    BinaryOperator(String symbol, int precedence, boolean leftAssociative)
    {
        this.symbol = symbol;
        this.precedence = precedence;
        this.leftAssociative = leftAssociative;
    }

    /**
     * @return the symbol
     */
    public String getSymbol()
    {
        return symbol;
    }

    /**
     * @return the binding strength, where a higher value binds tighter
     */
    public int getPrecedence()
    {
        return precedence;
    }

    /**
     * @return true if operands group left to right
     */
    public boolean isLeftAssociative()
    {
        return leftAssociative;
    }

    /**
     * @return true for plain assignment and every compound assignment
     */
    public boolean isAssignment()
    {
        return this == ASSIGN || name().endsWith("_ASSIGN");
    }

    /**
     * @return true for the equality and relational operators
     */
    public boolean isComparison()
    {
        return this == EQ || this == NE || this == LT || this == LE ||
               this == GT || this == GE;
    }

    /**
     * @return true for the short-circuiting &amp;&amp; and || operators
     */
    public boolean isLogical()
    {
        return this == AND || this == OR;
    }

    /**
     * @return true for the and, or, xor and shift operators
     */
    public boolean isBitwise()
    {
        return this == BAND || this == BOR || this == BXOR ||
               this == SHL || this == SHR || this == USHR;
    }

    @Override
    public String toString()
    {
        return symbol;
    }
}
