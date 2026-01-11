package com.tonic.analysis.source.parser;

import lombok.Getter;

@Getter
public enum Precedence {
    NONE(0),
    ASSIGNMENT(1),
    TERNARY(2),
    OR(3),
    AND(4),
    BIT_OR(5),
    BIT_XOR(6),
    BIT_AND(7),
    EQUALITY(8),
    COMPARISON(9),
    SHIFT(10),
    ADDITIVE(11),
    MULTIPLICATIVE(12),
    UNARY(13),
    POSTFIX(14),
    PRIMARY(15);

    private final int level;

    Precedence(int level) {
        this.level = level;
    }

    public static Precedence of(TokenType type) {
        switch (type) {
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

    public boolean isRightAssociative() {
        return this == ASSIGNMENT || this == TERNARY;
    }
}
