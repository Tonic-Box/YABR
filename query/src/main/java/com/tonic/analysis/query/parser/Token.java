package com.tonic.analysis.query.parser;

import java.util.Objects;

/**
 * Token produced by QueryLexer.
 */
public final class Token
{

    /**
     * The lexical kinds of the query language.
     */
    public enum TokenType
    {
        /**
         * The word {@code find}, opening a query that returns matching
         * entities with their evidence.
         */
        FIND,
        /**
         * The word {@code show}, opening a query that lists matching entities
         * without evidence.
         */
        SHOW,
        /**
         * The word {@code where}, introducing the condition expression, and
         * also the body of a quantifier or count.
         */
        WHERE,
        /**
         * The word {@code in}, introducing a static scope, and doubling as the
         * set membership operator inside a condition.
         */
        IN,
        /**
         * The word {@code during}, introducing an execution-time scope rather
         * than a static one.
         */
        DURING,
        /**
         * The word {@code and}, conjoining condition terms.
         */
        AND,
        /**
         * The word {@code or}, disjoining condition terms.
         */
        OR,
        /**
         * The word {@code not}, negating the condition that follows it.
         */
        NOT,
        /**
         * The word {@code before}, reserved for temporal ordering between
         * events; no production consumes it yet.
         */
        BEFORE,
        /**
         * The word {@code after}, reserved for temporal ordering between
         * events; no production consumes it yet.
         */
        AFTER,
        /**
         * The word {@code with}, introducing the run spec key/value settings
         * that configure execution.
         */
        WITH,
        /**
         * The word {@code limit}, capping the number of results; followed by a
         * number.
         */
        LIMIT,
        /**
         * The word {@code order}, the first half of {@code order by}.
         */
        ORDER,
        /**
         * The word {@code by}, completing {@code order by} ahead of the sort
         * key.
         */
        BY,
        /**
         * The word {@code asc}, an ascending sort direction; also the
         * direction assumed when neither is written.
         */
        ASC,
        /**
         * The word {@code desc}, a descending sort direction.
         */
        DESC,
        /**
         * The word {@code of}, tying a {@code <clinit>} scope to the classes
         * it should be taken over.
         */
        OF,
        /**
         * The word {@code all}, either an unrestricted scope or filler ahead of
         * a target word, as in {@code show all strings}.
         */
        ALL,
        /**
         * The word {@code class}, introducing a class-name pattern for a
         * scope.
         */
        CLASS,
        /**
         * The word {@code method}, introducing a method-name pattern for a
         * static or execution-time scope.
         */
        METHOD,
        /**
         * The word {@code clinit} or the literal {@code <clinit>}, scoping a
         * query to static initializer execution.
         */
        CLINIT,
        /**
         * The word {@code becomes}, reserved for a value-transition predicate;
         * no production consumes it yet.
         */
        BECOMES,
        /**
         * The hyphenated word {@code non-null}, a nullness state; the hyphen
         * keeps it out of the identifier space.
         */
        NON_NULL,
        /**
         * The word {@code null}, the null literal or the null state.
         */
        NULL,

        /**
         * The target word {@code methods}, making methods the result rows.
         */
        METHODS,
        /**
         * The target word {@code classes}, making classes the result rows.
         */
        CLASSES,
        /**
         * The target word {@code paths}, making execution paths the result
         * rows.
         */
        PATHS,
        /**
         * The target word {@code events}, making recorded runtime events the
         * result rows.
         */
        EVENTS,
        /**
         * The target word {@code strings}, making string values the result
         * rows.
         */
        STRINGS,
        /**
         * The target word {@code objects}, making allocated objects the result
         * rows.
         */
        OBJECTS,

        /**
         * The attribute word {@code calls}, naming the invocations made by an
         * entity.
         */
        CALLS,
        /**
         * The attribute word {@code alloccount} or {@code alloc_count}, naming
         * a count of allocations.
         */
        ALLOC_COUNT,
        /**
         * The attribute word {@code writesfield} or {@code writes_field},
         * naming the fields an entity stores to.
         */
        WRITES_FIELD,
        /**
         * The attribute word {@code readsfield} or {@code reads_field}, naming
         * the fields an entity loads from.
         */
        READS_FIELD,
        /**
         * The attribute word {@code field}, naming a field reference.
         */
        FIELD,
        /**
         * The attribute word {@code containsstring} or
         * {@code contains_string}, naming a string constant an entity carries.
         */
        CONTAINS_STRING,
        /**
         * The attribute word {@code throws}, naming the exceptions an entity
         * can raise.
         */
        THROWS,
        /**
         * The attribute word {@code instructioncount} or
         * {@code instruction_count}, naming a count of instructions.
         */
        INSTRUCTION_COUNT,
        /**
         * The attribute word {@code coverage}, naming how much of an entity
         * was reached.
         */
        COVERAGE,

        /**
         * The argument word {@code any}, matching an argument of any kind.
         */
        ARG_ANY,
        /**
         * The argument word {@code literal}, matching an argument that is a
         * compile-time constant.
         */
        ARG_LITERAL,
        /**
         * The argument word {@code dynamic} or {@code dynamicarg}, matching an
         * argument only known at run time.
         */
        ARG_DYNAMIC,
        /**
         * The argument word {@code fieldarg}, matching an argument that came
         * from a field read.
         */
        ARG_FIELD,
        /**
         * The argument word {@code localarg}, matching an argument that came
         * from a local variable.
         */
        ARG_LOCAL,
        /**
         * The argument word {@code callarg}, matching an argument that came
         * from another call's result.
         */
        ARG_CALL,

        /**
         * A name-like word that matched no keyword, left for the attribute
         * registry to resolve.
         */
        IDENTIFIER,
        /**
         * A quoted literal; the value carries the text with escapes already
         * resolved and quotes stripped.
         */
        STRING,
        /**
         * A slash-delimited pattern; trailing flag letters are folded into the
         * value as an inline {@code (?flags)} prefix.
         */
        REGEX,
        /**
         * A numeric literal, possibly signed or fractional; underscore
         * separators are stripped from the value.
         */
        NUMBER,

        /**
         * The character {@code (}, opening a group, a quantifier body, or an
         * accessor index.
         */
        LPAREN,
        /**
         * The character {@code )}, closing what an {@code LPAREN} opened.
         */
        RPAREN,
        /**
         * The character {@code [}, opening a set literal or a sequence.
         */
        LBRACKET,
        /**
         * The character {@code ]}, closing what an {@code LBRACKET} opened.
         */
        RBRACKET,
        /**
         * The character <code>{</code>, opening a repetition count on a
         * sequence element.
         */
        LBRACE,
        /**
         * The character <code>}</code>, closing a repetition count.
         */
        RBRACE,
        /**
         * The character {@code *}, repeating a sequence element zero or more
         * times.
         */
        STAR,
        /**
         * The character {@code +}, repeating a sequence element one or more
         * times.
         */
        PLUS,
        /**
         * The character {@code ,}, separating set members, sequence elements
         * and repetition bounds.
         */
        COMMA,
        /**
         * The character {@code .}, separating accessor steps; a doubled pair
         * forms the sequence gap.
         */
        DOT,
        /**
         * The character {@code :}, separating a run spec key from its value.
         */
        COLON,
        /**
         * The operator {@code >}.
         */
        GT,
        /**
         * The operator {@code >=}.
         */
        GTE,
        /**
         * The operator {@code <}, distinguished from the start of
         * {@code <clinit>} by lookahead.
         */
        LT,
        /**
         * The operator {@code <=}.
         */
        LTE,
        /**
         * The operator {@code ==}; a single {@code =} is not accepted.
         */
        EQ,
        /**
         * The operator {@code !=}; a bare {@code !} is a parse error.
         */
        NEQ,

        /**
         * The end-of-input marker appended after the last real token.
         */
        EOF
    }

    private final TokenType type;
    private final String value;
    private final int position;

    /**
     * Creates a token.
     *
     * @param type the token kind
     * @param value the matched text, or null if the kind carries none
     * @param position the character offset where the token starts
     */
    public Token(TokenType type, String value, int position)
    {
        this.type = type;
        this.value = value;
        this.position = position;
    }

    /**
     * @return the token kind
     */
    public TokenType type()
    {
        return type;
    }

    /**
     * @return the matched text, or null for tokens that carry none
     */
    public String value()
    {
        return value;
    }

    /**
     * @return the character offset where the token starts
     */
    public int position()
    {
        return position;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;
        Token token = (Token) o;
        return position == token.position &&
               type == token.type &&
               Objects.equals(value, token.value);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type, value, position);
    }

    @Override
    public String toString()
    {
        return type + (value != null ? "(" + value + ")" : "") + "@" + position;
    }
}
