package com.tonic.analysis.source.parser;

public enum TokenType {
    // Literals
    INTEGER_LITERAL,
    LONG_LITERAL,
    FLOAT_LITERAL,
    DOUBLE_LITERAL,
    CHAR_LITERAL,
    STRING_LITERAL,
    TRUE,
    FALSE,
    NULL,

    // Identifier
    IDENTIFIER,

    // Keywords (Java 11)
    ABSTRACT,
    ASSERT,
    BOOLEAN,
    BREAK,
    BYTE,
    CASE,
    CATCH,
    CHAR,
    CLASS,
    CONST,
    CONTINUE,
    DEFAULT,
    DO,
    DOUBLE,
    ELSE,
    ENUM,
    EXTENDS,
    FINAL,
    FINALLY,
    FLOAT,
    FOR,
    GOTO,
    IF,
    IMPLEMENTS,
    IMPORT,
    INSTANCEOF,
    INT,
    INTERFACE,
    LONG,
    NATIVE,
    NEW,
    PACKAGE,
    PRIVATE,
    PROTECTED,
    PUBLIC,
    RETURN,
    SHORT,
    STATIC,
    STRICTFP,
    SUPER,
    SWITCH,
    SYNCHRONIZED,
    THIS,
    THROW,
    THROWS,
    TRANSIENT,
    TRY,
    VAR,
    VOID,
    VOLATILE,
    WHILE,

    // Operators
    PLUS,           // +
    MINUS,          // -
    STAR,           // *
    SLASH,          // /
    PERCENT,        // %
    AMP,            // &
    PIPE,           // |
    CARET,          // ^
    TILDE,          // ~
    BANG,           // !
    EQ,             // =
    LT,             // <
    GT,             // >
    QUESTION,       // ?
    COLON,          // :
    ARROW,          // ->
    DOUBLE_COLON,   // ::

    // Increment/Decrement
    PLUS_PLUS,      // ++
    MINUS_MINUS,    // --

    // Logical
    AMP_AMP,        // &&
    PIPE_PIPE,      // ||

    // Comparison
    EQ_EQ,          // ==
    BANG_EQ,        // !=
    LT_EQ,          // <=
    GT_EQ,          // >=

    // Shift
    LT_LT,          // <<
    GT_GT,          // >>
    GT_GT_GT,       // >>>

    // Compound Assignment
    PLUS_EQ,        // +=
    MINUS_EQ,       // -=
    STAR_EQ,        // *=
    SLASH_EQ,       // /=
    PERCENT_EQ,     // %=
    AMP_EQ,         // &=
    PIPE_EQ,        // |=
    CARET_EQ,       // ^=
    LT_LT_EQ,       // <<=
    GT_GT_EQ,       // >>=
    GT_GT_GT_EQ,    // >>>=

    // Delimiters
    LPAREN,         // (
    RPAREN,         // )
    LBRACE,         // {
    RBRACE,         // }
    LBRACKET,       // [
    RBRACKET,       // ]
    SEMICOLON,      // ;
    COMMA,          // ,
    DOT,            // .
    AT,             // @
    ELLIPSIS,       // ...

    // Special
    EOF,
    ERROR;

    public boolean isKeyword() {
        return ordinal() >= ABSTRACT.ordinal() && ordinal() <= WHILE.ordinal();
    }

    public boolean isLiteral() {
        return ordinal() <= NULL.ordinal();
    }

    public boolean isAssignmentOperator() {
        return this == EQ || (ordinal() >= PLUS_EQ.ordinal() && ordinal() <= GT_GT_GT_EQ.ordinal());
    }

    public boolean isComparisonOperator() {
        return this == EQ_EQ || this == BANG_EQ || this == LT || this == GT ||
               this == LT_EQ || this == GT_EQ || this == INSTANCEOF;
    }

    public boolean isPrimitiveType() {
        return this == BOOLEAN || this == BYTE || this == CHAR || this == SHORT ||
               this == INT || this == LONG || this == FLOAT || this == DOUBLE;
    }

    public boolean isModifier() {
        return this == PUBLIC || this == PROTECTED || this == PRIVATE ||
               this == STATIC || this == FINAL || this == ABSTRACT ||
               this == SYNCHRONIZED || this == NATIVE || this == STRICTFP ||
               this == TRANSIENT || this == VOLATILE || this == DEFAULT;
    }
}
