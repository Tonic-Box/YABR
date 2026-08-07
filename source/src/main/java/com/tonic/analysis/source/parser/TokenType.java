package com.tonic.analysis.source.parser;

/**
 * The lexical token kinds of Java 11, declared in category order so the classification predicates
 * can test ordinal ranges.
 */
public enum TokenType
{
    // Literals
    /**
     * An unsuffixed whole-number literal in any radix; one too large to fit an int is retyped as
     * LONG_LITERAL instead of being rejected.
     */
    INTEGER_LITERAL,
    /**
     * A whole-number literal carrying an l or L suffix.
     */
    LONG_LITERAL,
    /**
     * A numeric literal carrying an f or F suffix.
     */
    FLOAT_LITERAL,
    /**
     * A numeric literal with a decimal point, an exponent, or a d suffix.
     */
    DOUBLE_LITERAL,
    /**
     * A single-quoted character literal; the token also carries the decoded character value.
     */
    CHAR_LITERAL,
    /**
     * A quoted string or text block; the token also carries the decoded contents, with escapes
     * resolved and text-block indentation stripped.
     */
    STRING_LITERAL,
    /**
     * The true boolean literal, grouped with the literals rather than the keywords, so isLiteral
     * accepts it and isKeyword does not.
     */
    TRUE,
    /**
     * The false boolean literal, grouped with the literals rather than the keywords.
     */
    FALSE,
    /**
     * The null reference literal, and the last kind isLiteral accepts.
     */
    NULL,

    // Identifier
    /**
     * Any name that is not a reserved word; it sits between the literals and the keywords so
     * neither isLiteral nor isKeyword accepts it.
     */
    IDENTIFIER,

    // Keywords (Java 11)
    /**
     * Modifier for a type or method declared without an implementation.
     */
    ABSTRACT,
    /**
     * Introduces an assert statement, whose check is inert unless assertions are enabled at run
     * time.
     */
    ASSERT,
    /**
     * Primitive type keyword for a true/false value.
     */
    BOOLEAN,
    /**
     * Leaves the innermost loop or switch, or the labeled statement it names.
     */
    BREAK,
    /**
     * Primitive type keyword for a signed 8-bit integer.
     */
    BYTE,
    /**
     * Introduces a switch label, in either colon or arrow form.
     */
    CASE,
    /**
     * Introduces a handler clause of a try statement.
     */
    CATCH,
    /**
     * Primitive type keyword for an unsigned 16-bit UTF-16 code unit.
     */
    CHAR,
    /**
     * Introduces a class declaration, and forms a class literal when it follows a type name.
     */
    CLASS,
    /**
     * Reserved by the language but unused, so it can only ever appear as a syntax error.
     */
    CONST,
    /**
     * Skips to the next iteration of the innermost loop, or of the loop its label names.
     */
    CONTINUE,
    /**
     * The fall-through label of a switch, the modifier for an interface method carrying a body,
     * and the marker before an annotation element's default value.
     */
    DEFAULT,
    /**
     * Introduces a do-while loop, whose body runs once before the condition is first tested.
     */
    DO,
    /**
     * Primitive type keyword for a 64-bit IEEE 754 floating point value.
     */
    DOUBLE,
    /**
     * The alternative branch of an if statement.
     */
    ELSE,
    /**
     * Introduces an enum declaration.
     */
    ENUM,
    /**
     * Names a superclass, an interface's superinterfaces, or the upper bound of a type parameter
     * or wildcard.
     */
    EXTENDS,
    /**
     * Modifier forbidding reassignment, overriding, or subclassing.
     */
    FINAL,
    /**
     * Introduces the clause of a try that runs on every exit path, normal or exceptional.
     */
    FINALLY,
    /**
     * Primitive type keyword for a 32-bit IEEE 754 floating point value.
     */
    FLOAT,
    /**
     * Introduces either a classic three-clause loop or a for-each loop.
     */
    FOR,
    /**
     * Reserved by the language but unused, so it can only ever appear as a syntax error.
     */
    GOTO,
    /**
     * Introduces a conditional statement.
     */
    IF,
    /**
     * Names the interfaces a class declares itself to satisfy.
     */
    IMPLEMENTS,
    /**
     * Introduces an import declaration - single type, on demand, or static.
     */
    IMPORT,
    /**
     * Run-time type test; it is a keyword by position but isComparisonOperator counts it as a
     * comparison.
     */
    INSTANCEOF,
    /**
     * Primitive type keyword for a signed 32-bit integer.
     */
    INT,
    /**
     * Introduces an interface declaration, and follows AT in an annotation type declaration.
     */
    INTERFACE,
    /**
     * Primitive type keyword for a signed 64-bit integer.
     */
    LONG,
    /**
     * Modifier for a method whose implementation lives outside the JVM.
     */
    NATIVE,
    /**
     * Introduces an instance creation, an array creation, or an anonymous class body.
     */
    NEW,
    /**
     * Introduces the package declaration at the head of a compilation unit.
     */
    PACKAGE,
    /**
     * Access modifier limiting visibility to the declaring class.
     */
    PRIVATE,
    /**
     * Access modifier granting visibility to subclasses and to the same package.
     */
    PROTECTED,
    /**
     * Access modifier granting unrestricted visibility.
     */
    PUBLIC,
    /**
     * Leaves the enclosing method or constructor, with a value unless it is void.
     */
    RETURN,
    /**
     * Primitive type keyword for a signed 16-bit integer.
     */
    SHORT,
    /**
     * Modifier binding a member to the type rather than to an instance; also appears in static
     * imports and in initializer blocks.
     */
    STATIC,
    /**
     * Modifier pinning floating point evaluation to strict IEEE 754 semantics.
     */
    STRICTFP,
    /**
     * Refers to the superclass - as a qualifier, as a constructor call, or as the lower bound of
     * a wildcard.
     */
    SUPER,
    /**
     * Introduces a switch statement or switch expression.
     */
    SWITCH,
    /**
     * Introduces a monitor-guarded block, or marks a method as guarded by its receiver's monitor.
     */
    SYNCHRONIZED,
    /**
     * Refers to the current instance, and heads a delegating constructor call to the same class.
     */
    THIS,
    /**
     * Raises an exception, unwinding until a handler matches.
     */
    THROW,
    /**
     * Introduces the checked exception list of a method or constructor.
     */
    THROWS,
    /**
     * Modifier excluding a field from default serialization.
     */
    TRANSIENT,
    /**
     * Introduces a try statement, with or without a resource list.
     */
    TRY,
    /**
     * Requests an inferred local variable type; the lexer treats it as a reserved word even
     * though the language allows it as an identifier.
     */
    VAR,
    /**
     * Marks a method as producing no value, and names the void pseudo-type in a class literal.
     */
    VOID,
    /**
     * Modifier making a field's reads and writes immediately visible across threads.
     */
    VOLATILE,
    /**
     * Introduces a while loop, and closes a do-while.
     */
    WHILE,

    // Operators
    /**
     * Addition, string concatenation, or unary plus.
     */
    PLUS,           // +
    /**
     * Subtraction or unary negation.
     */
    MINUS,          // -
    /**
     * Multiplication, and the on-demand wildcard of an import.
     */
    STAR,           // *
    /**
     * Division.
     */
    SLASH,          // /
    /**
     * Remainder of a division.
     */
    PERCENT,        // %
    /**
     * Bitwise or non-short-circuiting logical AND, and the separator between the bounds of an
     * intersection type.
     */
    AMP,            // &
    /**
     * Bitwise or non-short-circuiting logical OR, and the separator between the alternatives of a
     * multi-catch clause.
     */
    PIPE,           // |
    /**
     * Bitwise or logical exclusive OR.
     */
    CARET,          // ^
    /**
     * Bitwise complement.
     */
    TILDE,          // ~
    /**
     * Logical negation.
     */
    BANG,           // !
    /**
     * Plain assignment; the only assignment operator outside the compound range, so
     * isAssignmentOperator tests it separately.
     */
    EQ,             // =
    /**
     * Less-than test, and the opener of a type argument or type parameter list.
     */
    LT,             // <
    /**
     * Greater-than test, and the closer of a type argument or type parameter list.
     */
    GT,             // >
    /**
     * The condition separator of a conditional expression, and an unbounded wildcard type
     * argument.
     */
    QUESTION,       // ?
    /**
     * Separates the arms of a conditional expression, terminates a colon-form switch label, and
     * separates the parts of a for-each header or a statement label.
     */
    COLON,          // :
    /**
     * Separates a lambda's parameters from its body, and an arrow-form switch label from its
     * result.
     */
    ARROW,          // ->
    /**
     * Introduces a method or constructor reference.
     */
    DOUBLE_COLON,   // ::

    // Increment/Decrement
    /**
     * Increment by one, in either prefix or postfix position.
     */
    PLUS_PLUS,      // ++
    /**
     * Decrement by one, in either prefix or postfix position.
     */
    MINUS_MINUS,    // --

    // Logical
    /**
     * Conditional AND, which does not evaluate its right operand once the left is false.
     */
    AMP_AMP,        // &&
    /**
     * Conditional OR, which does not evaluate its right operand once the left is true.
     */
    PIPE_PIPE,      // ||

    // Comparison
    /**
     * Equality test, which on reference operands compares identity rather than contents.
     */
    EQ_EQ,          // ==
    /**
     * Inequality test, the negation of EQ_EQ.
     */
    BANG_EQ,        // !=
    /**
     * Numeric less-than-or-equal test.
     */
    LT_EQ,          // <=
    /**
     * Numeric greater-than-or-equal test.
     */
    GT_EQ,          // >=

    // Shift
    /**
     * Left shift.
     */
    LT_LT,          // <<
    /**
     * Right shift that preserves the sign bit; the parser also splits this token back apart when
     * it closes two nested type argument lists.
     */
    GT_GT,          // >>
    /**
     * Right shift that fills with zeroes regardless of sign.
     */
    GT_GT_GT,       // >>>

    // Compound Assignment
    /**
     * Adds to the target in place; also the in-place string append.
     */
    PLUS_EQ,        // +=
    /**
     * Subtracts from the target in place.
     */
    MINUS_EQ,       // -=
    /**
     * Multiplies the target in place.
     */
    STAR_EQ,        // *=
    /**
     * Divides the target in place.
     */
    SLASH_EQ,       // /=
    /**
     * Replaces the target with the remainder of dividing it.
     */
    PERCENT_EQ,     // %=
    /**
     * Bitwise or logical AND into the target.
     */
    AMP_EQ,         // &=
    /**
     * Bitwise or logical OR into the target.
     */
    PIPE_EQ,        // |=
    /**
     * Bitwise exclusive OR into the target.
     */
    CARET_EQ,       // ^=
    /**
     * Left-shifts the target in place.
     */
    LT_LT_EQ,       // <<=
    /**
     * Sign-extending right-shifts the target in place.
     */
    GT_GT_EQ,       // >>=
    /**
     * Zero-filling right-shifts the target in place.
     */
    GT_GT_GT_EQ,    // >>>=

    // Delimiters
    /**
     * Opens a parenthesized expression, a parameter or argument list, or a cast.
     */
    LPAREN,         // (
    /**
     * Closes the construct opened by the matching LPAREN.
     */
    RPAREN,         // )
    /**
     * Opens a block, a type body, or an array initializer.
     */
    LBRACE,         // {
    /**
     * Closes the construct opened by the matching LBRACE.
     */
    RBRACE,         // }
    /**
     * Opens an array index, or a dimension in an array type or creation.
     */
    LBRACKET,       // [
    /**
     * Closes the construct opened by the matching LBRACKET.
     */
    RBRACKET,       // ]
    /**
     * Terminates a statement or declaration, and separates the clauses of a classic for loop.
     */
    SEMICOLON,      // ;
    /**
     * Separates list elements such as parameters, arguments, declarators and type arguments.
     */
    COMMA,          // ,
    /**
     * Member access, and the separator inside qualified names and imports.
     */
    DOT,            // .
    /**
     * Introduces an annotation, and precedes the interface keyword in an annotation type
     * declaration.
     */
    AT,             // @
    /**
     * Marks the last parameter of a method as varargs.
     */
    ELLIPSIS,       // ...

    // Special
    /**
     * End of input; emitted once when the source is exhausted so the parser always has a
     * terminator to stop on.
     */
    EOF,
    /**
     * A sequence the lexer could not scan; the token's text is the diagnostic message rather
     * than the offending source.
     */
    ERROR;

    /**
     * @return true for a reserved word, ABSTRACT through WHILE
     */
    public boolean isKeyword()
    {
        return ordinal() >= ABSTRACT.ordinal() && ordinal() <= WHILE.ordinal();
    }

    /**
     * @return true for a literal, including the true, false and null keywords
     */
    public boolean isLiteral()
    {
        return ordinal() <= NULL.ordinal();
    }

    /**
     * @return true for plain assignment or any compound assignment operator
     */
    public boolean isAssignmentOperator()
    {
        return this == EQ || (ordinal() >= PLUS_EQ.ordinal() && ordinal() <= GT_GT_GT_EQ.ordinal());
    }

    /**
     * @return true for an equality or relational operator, counting instanceof
     */
    public boolean isComparisonOperator()
    {
        return this == EQ_EQ || this == BANG_EQ || this == LT || this == GT ||
               this == LT_EQ || this == GT_EQ || this == INSTANCEOF;
    }

    /**
     * @return true for one of the eight primitive type keywords
     */
    public boolean isPrimitiveType()
    {
        return this == BOOLEAN || this == BYTE || this == CHAR || this == SHORT ||
               this == INT || this == LONG || this == FLOAT || this == DOUBLE;
    }

    /**
     * @return true for a declaration modifier keyword
     */
    public boolean isModifier()
    {
        return this == PUBLIC || this == PROTECTED || this == PRIVATE ||
               this == STATIC || this == FINAL || this == ABSTRACT ||
               this == SYNCHRONIZED || this == NATIVE || this == STRICTFP ||
               this == TRANSIENT || this == VOLATILE || this == DEFAULT;
    }
}
