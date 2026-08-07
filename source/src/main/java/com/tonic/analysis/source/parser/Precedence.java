package com.tonic.analysis.source.parser;

/**
 * Java operator binding strengths, ordered from loosest (NONE) to tightest (PRIMARY).
 */
public enum Precedence
{
    /**
     * No binding at all; the level reported for a token that is not an
     * operator, which stops the parser from extending an expression.
     */
    NONE(0),
    /**
     * The loosest operator level, covering plain assignment and every compound
     * assignment; right-associative.
     */
    ASSIGNMENT(1),
    /**
     * The conditional {@code ?:} level, reached from the {@code ?} token and
     * right-associative like assignment.
     */
    TERNARY(2),
    /**
     * The level of the short-circuiting {@code ||}, the loosest binary
     * operator.
     */
    OR(3),
    /**
     * The level of the short-circuiting {@code &&}, binding tighter than
     * {@code ||}.
     */
    AND(4),
    /**
     * The level of the single {@code |}, the loosest of the three bitwise
     * levels.
     */
    BIT_OR(5),
    /**
     * The level of {@code ^}, between bitwise or and bitwise and.
     */
    BIT_XOR(6),
    /**
     * The level of the single {@code &}, the tightest of the three bitwise
     * levels.
     */
    BIT_AND(7),
    /**
     * The level of {@code ==} and {@code !=}, binding looser than the ordering
     * comparisons.
     */
    EQUALITY(8),
    /**
     * The relational level, covering the four ordering operators and
     * {@code instanceof}.
     */
    COMPARISON(9),
    /**
     * The level of the three shift operators.
     */
    SHIFT(10),
    /**
     * The level of binary {@code +} and {@code -}.
     */
    ADDITIVE(11),
    /**
     * The level of {@code *}, {@code /}, and {@code %}.
     */
    MULTIPLICATIVE(12),
    /**
     * The prefix operator level, covering negation, complement, prefix
     * increment and decrement, and casts.
     */
    UNARY(13),
    /**
     * The postfix level, covering {@code ++} and {@code --} as well as member
     * access, array indexing, and call parentheses.
     */
    POSTFIX(14),
    /**
     * The tightest level, above every operator; literals, names, and
     * parenthesized expressions sit here.
     */
    PRIMARY(15);

    private final int level;

    Precedence(int level)
    {
        this.level = level;
    }

    /**
     * @return the level
     */
    public int getLevel()
    {
        return level;
    }

    /**
     * Maps an operator token to the precedence at which it binds.
     * @param type the token to classify
     * @return the matching precedence, or NONE when the token is not an operator
     */
    public static Precedence of(TokenType type)
    {
        switch (type)
        {
            case EQ:
            case PLUS_EQ:
            case MINUS_EQ:
            case STAR_EQ:
            case SLASH_EQ:
            case PERCENT_EQ:
            case AMP_EQ:
            case PIPE_EQ:
            case CARET_EQ:
            case LT_LT_EQ:
            case GT_GT_EQ:
            case GT_GT_GT_EQ:
                return ASSIGNMENT;

            case QUESTION:
                return TERNARY;

            case PIPE_PIPE:
                return OR;

            case AMP_AMP:
                return AND;

            case PIPE:
                return BIT_OR;

            case CARET:
                return BIT_XOR;

            case AMP:
                return BIT_AND;

            case EQ_EQ:
            case BANG_EQ:
                return EQUALITY;

            case LT:
            case GT:
            case LT_EQ:
            case GT_EQ:
            case INSTANCEOF:
                return COMPARISON;

            case LT_LT:
            case GT_GT:
            case GT_GT_GT:
                return SHIFT;

            case PLUS:
            case MINUS:
                return ADDITIVE;

            case STAR:
            case SLASH:
            case PERCENT:
                return MULTIPLICATIVE;

            case PLUS_PLUS:
            case MINUS_MINUS:
            case DOT:
            case LBRACKET:
            case LPAREN:
                return POSTFIX;

            default:
                return NONE;
        }
    }

    /**
     * @return true for assignment and ternary, the two right-associative levels
     */
    public boolean isRightAssociative()
    {
        return this == ASSIGNMENT || this == TERNARY;
    }
}
