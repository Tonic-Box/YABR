package com.tonic.analysis.source.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Lexer {
    private final String source;
    private int pos;
    private int line;
    private int column;
    private int tokenStart;
    private int tokenStartLine;
    private int tokenStartColumn;

    private Token current;
    private final List<Token> lookaheadBuffer = new ArrayList<>();

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("abstract", TokenType.ABSTRACT);
        KEYWORDS.put("assert", TokenType.ASSERT);
        KEYWORDS.put("boolean", TokenType.BOOLEAN);
        KEYWORDS.put("break", TokenType.BREAK);
        KEYWORDS.put("byte", TokenType.BYTE);
        KEYWORDS.put("case", TokenType.CASE);
        KEYWORDS.put("catch", TokenType.CATCH);
        KEYWORDS.put("char", TokenType.CHAR);
        KEYWORDS.put("class", TokenType.CLASS);
        KEYWORDS.put("const", TokenType.CONST);
        KEYWORDS.put("continue", TokenType.CONTINUE);
        KEYWORDS.put("default", TokenType.DEFAULT);
        KEYWORDS.put("do", TokenType.DO);
        KEYWORDS.put("double", TokenType.DOUBLE);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("enum", TokenType.ENUM);
        KEYWORDS.put("extends", TokenType.EXTENDS);
        KEYWORDS.put("final", TokenType.FINAL);
        KEYWORDS.put("finally", TokenType.FINALLY);
        KEYWORDS.put("float", TokenType.FLOAT);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("goto", TokenType.GOTO);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("implements", TokenType.IMPLEMENTS);
        KEYWORDS.put("import", TokenType.IMPORT);
        KEYWORDS.put("instanceof", TokenType.INSTANCEOF);
        KEYWORDS.put("int", TokenType.INT);
        KEYWORDS.put("interface", TokenType.INTERFACE);
        KEYWORDS.put("long", TokenType.LONG);
        KEYWORDS.put("native", TokenType.NATIVE);
        KEYWORDS.put("new", TokenType.NEW);
        KEYWORDS.put("package", TokenType.PACKAGE);
        KEYWORDS.put("private", TokenType.PRIVATE);
        KEYWORDS.put("protected", TokenType.PROTECTED);
        KEYWORDS.put("public", TokenType.PUBLIC);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("short", TokenType.SHORT);
        KEYWORDS.put("static", TokenType.STATIC);
        KEYWORDS.put("strictfp", TokenType.STRICTFP);
        KEYWORDS.put("super", TokenType.SUPER);
        KEYWORDS.put("switch", TokenType.SWITCH);
        KEYWORDS.put("synchronized", TokenType.SYNCHRONIZED);
        KEYWORDS.put("this", TokenType.THIS);
        KEYWORDS.put("throw", TokenType.THROW);
        KEYWORDS.put("throws", TokenType.THROWS);
        KEYWORDS.put("transient", TokenType.TRANSIENT);
        KEYWORDS.put("try", TokenType.TRY);
        KEYWORDS.put("var", TokenType.VAR);
        KEYWORDS.put("void", TokenType.VOID);
        KEYWORDS.put("volatile", TokenType.VOLATILE);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("false", TokenType.FALSE);
        KEYWORDS.put("null", TokenType.NULL);
    }

    public Lexer(String source) {
        this.source = source;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
    }

    public Token nextToken() {
        if (!lookaheadBuffer.isEmpty()) {
            current = lookaheadBuffer.remove(0);
            return current;
        }
        current = scanToken();
        return current;
    }

    public Token peek() {
        return peekAhead(0);
    }

    public Token peekAhead(int offset) {
        while (lookaheadBuffer.size() <= offset) {
            lookaheadBuffer.add(scanToken());
        }
        return lookaheadBuffer.get(offset);
    }

    public Token current() {
        return current;
    }

    public SourcePosition currentPosition() {
        return SourcePosition.of(line, column, pos);
    }

    private Token scanToken() {
        skipWhitespaceAndComments();

        if (isAtEnd()) {
            return makeToken(TokenType.EOF, "");
        }

        markTokenStart();
        char c = advance();

        if (isDigit(c)) {
            return scanNumber();
        }

        if (isIdentifierStart(c)) {
            return scanIdentifierOrKeyword();
        }

        switch (c) {
            case '(': return makeToken(TokenType.LPAREN);
            case ')': return makeToken(TokenType.RPAREN);
            case '{': return makeToken(TokenType.LBRACE);
            case '}': return makeToken(TokenType.RBRACE);
            case '[': return makeToken(TokenType.LBRACKET);
            case ']': return makeToken(TokenType.RBRACKET);
            case ';': return makeToken(TokenType.SEMICOLON);
            case ',': return makeToken(TokenType.COMMA);
            case '~': return makeToken(TokenType.TILDE);
            case '?': return makeToken(TokenType.QUESTION);
            case '@': return makeToken(TokenType.AT);

            case '.':
                if (match('.') && match('.')) {
                    return makeToken(TokenType.ELLIPSIS);
                }
                if (isDigit(peek(0))) {
                    return scanNumber();
                }
                return makeToken(TokenType.DOT);

            case ':':
                if (match(':')) {
                    return makeToken(TokenType.DOUBLE_COLON);
                }
                return makeToken(TokenType.COLON);

            case '+':
                if (match('+')) return makeToken(TokenType.PLUS_PLUS);
                if (match('=')) return makeToken(TokenType.PLUS_EQ);
                return makeToken(TokenType.PLUS);

            case '-':
                if (match('-')) return makeToken(TokenType.MINUS_MINUS);
                if (match('=')) return makeToken(TokenType.MINUS_EQ);
                if (match('>')) return makeToken(TokenType.ARROW);
                return makeToken(TokenType.MINUS);

            case '*':
                if (match('=')) return makeToken(TokenType.STAR_EQ);
                return makeToken(TokenType.STAR);

            case '/':
                if (match('=')) return makeToken(TokenType.SLASH_EQ);
                return makeToken(TokenType.SLASH);

            case '%':
                if (match('=')) return makeToken(TokenType.PERCENT_EQ);
                return makeToken(TokenType.PERCENT);

            case '&':
                if (match('&')) return makeToken(TokenType.AMP_AMP);
                if (match('=')) return makeToken(TokenType.AMP_EQ);
                return makeToken(TokenType.AMP);

            case '|':
                if (match('|')) return makeToken(TokenType.PIPE_PIPE);
                if (match('=')) return makeToken(TokenType.PIPE_EQ);
                return makeToken(TokenType.PIPE);

            case '^':
                if (match('=')) return makeToken(TokenType.CARET_EQ);
                return makeToken(TokenType.CARET);

            case '!':
                if (match('=')) return makeToken(TokenType.BANG_EQ);
                return makeToken(TokenType.BANG);

            case '=':
                if (match('=')) return makeToken(TokenType.EQ_EQ);
                return makeToken(TokenType.EQ);

            case '<':
                if (match('<')) {
                    if (match('=')) return makeToken(TokenType.LT_LT_EQ);
                    return makeToken(TokenType.LT_LT);
                }
                if (match('=')) return makeToken(TokenType.LT_EQ);
                return makeToken(TokenType.LT);

            case '>':
                if (match('>')) {
                    if (match('>')) {
                        if (match('=')) return makeToken(TokenType.GT_GT_GT_EQ);
                        return makeToken(TokenType.GT_GT_GT);
                    }
                    if (match('=')) return makeToken(TokenType.GT_GT_EQ);
                    return makeToken(TokenType.GT_GT);
                }
                if (match('=')) return makeToken(TokenType.GT_EQ);
                return makeToken(TokenType.GT);

            case '\'': return scanCharLiteral();
            case '"': return scanStringLiteral();

            default:
                return makeErrorToken("Unexpected character: '" + c + "'");
        }
    }

    private Token scanNumber() {
        boolean isFloat = false;
        boolean isLong = false;
        boolean isHex = false;
        boolean isBinary = false;
        boolean isOctal = false;

        char first = source.charAt(tokenStart);

        if (first == '0' && pos < source.length()) {
            char second = peek(0);
            if (second == 'x' || second == 'X') {
                advance();
                isHex = true;
                scanHexDigits();
            } else if (second == 'b' || second == 'B') {
                advance();
                isBinary = true;
                scanBinaryDigits();
            } else if (isDigit(second)) {
                isOctal = true;
                scanOctalDigits();
            }
        }

        if (!isHex && !isBinary && !isOctal) {
            scanDecimalDigits();

            if (peek(0) == '.' && isDigit(peek(1))) {
                isFloat = true;
                advance();
                scanDecimalDigits();
            }

            if (peek(0) == 'e' || peek(0) == 'E') {
                isFloat = true;
                advance();
                if (peek(0) == '+' || peek(0) == '-') {
                    advance();
                }
                scanDecimalDigits();
            }
        }

        char suffix = peek(0);
        if (suffix == 'l' || suffix == 'L') {
            isLong = true;
            advance();
        } else if (suffix == 'f' || suffix == 'F') {
            isFloat = true;
            advance();
            return makeNumberToken(TokenType.FLOAT_LITERAL);
        } else if (suffix == 'd' || suffix == 'D') {
            isFloat = true;
            advance();
            return makeNumberToken(TokenType.DOUBLE_LITERAL);
        }

        if (isFloat) {
            return makeNumberToken(TokenType.DOUBLE_LITERAL);
        } else if (isLong) {
            return makeNumberToken(TokenType.LONG_LITERAL);
        } else {
            return makeNumberToken(TokenType.INTEGER_LITERAL);
        }
    }

    private void scanDecimalDigits() {
        while (isDigit(peek(0)) || peek(0) == '_') {
            advance();
        }
    }

    private void scanHexDigits() {
        while (isHexDigit(peek(0)) || peek(0) == '_') {
            advance();
        }
    }

    private void scanBinaryDigits() {
        while (peek(0) == '0' || peek(0) == '1' || peek(0) == '_') {
            advance();
        }
    }

    private void scanOctalDigits() {
        while (isOctalDigit(peek(0)) || peek(0) == '_') {
            advance();
        }
    }

    private Token makeNumberToken(TokenType type) {
        String text = currentTokenText();
        String cleaned = text.replace("_", "");

        Object value;
        try {
            switch (type) {
                case INTEGER_LITERAL:
                    value = parseInteger(cleaned);
                    break;
                case LONG_LITERAL:
                    value = parseLong(cleaned);
                    break;
                case FLOAT_LITERAL:
                    value = Float.parseFloat(cleaned.replaceAll("[fF]$", ""));
                    break;
                case DOUBLE_LITERAL:
                    value = Double.parseDouble(cleaned.replaceAll("[dD]$", ""));
                    break;
                default:
                    value = null;
            }
        } catch (NumberFormatException e) {
            return makeErrorToken("Invalid number format: " + text);
        }

        return new Token(type, text, value, tokenStartPosition());
    }

    private int parseInteger(String s) {
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Integer.parseUnsignedInt(s.substring(2), 16);
        } else if (s.startsWith("0b") || s.startsWith("0B")) {
            return Integer.parseUnsignedInt(s.substring(2), 2);
        } else if (s.length() > 1 && s.startsWith("0") && !s.contains(".")) {
            return Integer.parseUnsignedInt(s.substring(1), 8);
        }
        return Integer.parseInt(s);
    }

    private long parseLong(String s) {
        s = s.replaceAll("[lL]$", "");
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Long.parseUnsignedLong(s.substring(2), 16);
        } else if (s.startsWith("0b") || s.startsWith("0B")) {
            return Long.parseUnsignedLong(s.substring(2), 2);
        } else if (s.length() > 1 && s.startsWith("0") && !s.contains(".")) {
            return Long.parseUnsignedLong(s.substring(1), 8);
        }
        return Long.parseLong(s);
    }

    private Token scanIdentifierOrKeyword() {
        while (isIdentifierPart(peek(0))) {
            advance();
        }

        String text = currentTokenText();
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);

        if (type == TokenType.TRUE) {
            return new Token(type, text, Boolean.TRUE, tokenStartPosition());
        } else if (type == TokenType.FALSE) {
            return new Token(type, text, Boolean.FALSE, tokenStartPosition());
        } else if (type == TokenType.NULL) {
            return new Token(type, text, null, tokenStartPosition());
        }

        return makeToken(type);
    }

    private Token scanCharLiteral() {
        if (isAtEnd()) {
            return makeErrorToken("Unterminated character literal");
        }

        char value;
        if (peek(0) == '\\') {
            advance();
            value = scanEscapeSequence();
        } else {
            value = advance();
        }

        if (!match('\'')) {
            return makeErrorToken("Unterminated character literal");
        }

        return new Token(TokenType.CHAR_LITERAL, currentTokenText(), value, tokenStartPosition());
    }

    private Token scanStringLiteral() {
        StringBuilder sb = new StringBuilder();

        while (!isAtEnd() && peek(0) != '"') {
            if (peek(0) == '\n') {
                return makeErrorToken("Unterminated string literal");
            }
            if (peek(0) == '\\') {
                advance();
                sb.append(scanEscapeSequence());
            } else {
                sb.append(advance());
            }
        }

        if (isAtEnd()) {
            return makeErrorToken("Unterminated string literal");
        }

        advance();
        return new Token(TokenType.STRING_LITERAL, currentTokenText(), sb.toString(), tokenStartPosition());
    }

    private char scanEscapeSequence() {
        if (isAtEnd()) return '\0';

        char c = advance();
        switch (c) {
            case 'b': return '\b';
            case 't': return '\t';
            case 'n': return '\n';
            case 'f': return '\f';
            case 'r': return '\r';
            case '"': return '"';
            case '\'': return '\'';
            case '\\': return '\\';
            case 'u':
                return scanUnicodeEscape();
            case '0': case '1': case '2': case '3':
            case '4': case '5': case '6': case '7':
                return scanOctalEscape(c);
            default:
                return c;
        }
    }

    private char scanUnicodeEscape() {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 4 && !isAtEnd(); i++) {
            if (isHexDigit(peek(0))) {
                hex.append(advance());
            } else {
                break;
            }
        }
        if (hex.length() == 4) {
            return (char) Integer.parseInt(hex.toString(), 16);
        }
        return '\0';
    }

    private char scanOctalEscape(char first) {
        StringBuilder octal = new StringBuilder();
        octal.append(first);
        for (int i = 0; i < 2 && !isAtEnd() && isOctalDigit(peek(0)); i++) {
            octal.append(advance());
        }
        return (char) Integer.parseInt(octal.toString(), 8);
    }

    private void skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            char c = peek(0);

            switch (c) {
                case ' ':
                case '\t':
                case '\r':
                    advance();
                    break;
                case '\n':
                    advance();
                    line++;
                    column = 1;
                    break;
                case '/':
                    if (peek(1) == '/') {
                        skipLineComment();
                    } else if (peek(1) == '*') {
                        skipBlockComment();
                    } else {
                        return;
                    }
                    break;
                default:
                    return;
            }
        }
    }

    private void skipLineComment() {
        while (!isAtEnd() && peek(0) != '\n') {
            advance();
        }
    }

    private void skipBlockComment() {
        advance();
        advance();

        while (!isAtEnd()) {
            if (peek(0) == '*' && peek(1) == '/') {
                advance();
                advance();
                return;
            }
            if (peek(0) == '\n') {
                line++;
                column = 1;
                advance();
            } else {
                advance();
            }
        }
    }

    private boolean isAtEnd() {
        return pos >= source.length();
    }

    private char advance() {
        char c = source.charAt(pos);
        pos++;
        column++;
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(pos) != expected) {
            return false;
        }
        pos++;
        column++;
        return true;
    }

    private char peek(int offset) {
        int index = pos + offset;
        if (index >= source.length()) {
            return '\0';
        }
        return source.charAt(index);
    }

    private void markTokenStart() {
        tokenStart = pos;
        tokenStartLine = line;
        tokenStartColumn = column;
    }

    private String currentTokenText() {
        return source.substring(tokenStart, pos);
    }

    private SourcePosition tokenStartPosition() {
        return SourcePosition.of(tokenStartLine, tokenStartColumn, tokenStart);
    }

    private Token makeToken(TokenType type) {
        return new Token(type, currentTokenText(), tokenStartPosition());
    }

    private Token makeToken(TokenType type, String text) {
        return new Token(type, text, SourcePosition.of(line, column, pos));
    }

    private Token makeErrorToken(String message) {
        return new Token(TokenType.ERROR, message, tokenStartPosition());
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isOctalDigit(char c) {
        return c >= '0' && c <= '7';
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }
}
