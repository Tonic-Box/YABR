package com.tonic.analysis.source.parser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    @Nested
    class KeywordTests {
        @Test
        void recognizesClassKeyword() {
            Lexer lexer = new Lexer("class");
            Token token = lexer.nextToken();
            assertEquals(TokenType.CLASS, token.getType());
            assertEquals("class", token.getText());
        }

        @Test
        void recognizesAllKeywords() {
            String[] keywords = {
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                "class", "const", "continue", "default", "do", "double", "else", "enum",
                "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native", "new",
                "package", "private", "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
                "transient", "try", "var", "void", "volatile", "while"
            };

            for (String keyword : keywords) {
                Lexer lexer = new Lexer(keyword);
                Token token = lexer.nextToken();
                assertTrue(token.isKeyword(), "Expected keyword: " + keyword);
            }
        }
    }

    @Nested
    class OperatorTests {
        @Test
        void recognizesSingleCharOperators() {
            Lexer lexer = new Lexer("+ - * / % = < > ! & | ^ ~ ? :");
            assertEquals(TokenType.PLUS, lexer.nextToken().getType());
            assertEquals(TokenType.MINUS, lexer.nextToken().getType());
            assertEquals(TokenType.STAR, lexer.nextToken().getType());
            assertEquals(TokenType.SLASH, lexer.nextToken().getType());
            assertEquals(TokenType.PERCENT, lexer.nextToken().getType());
            assertEquals(TokenType.EQ, lexer.nextToken().getType());
            assertEquals(TokenType.LT, lexer.nextToken().getType());
            assertEquals(TokenType.GT, lexer.nextToken().getType());
            assertEquals(TokenType.BANG, lexer.nextToken().getType());
            assertEquals(TokenType.AMP, lexer.nextToken().getType());
            assertEquals(TokenType.PIPE, lexer.nextToken().getType());
            assertEquals(TokenType.CARET, lexer.nextToken().getType());
            assertEquals(TokenType.TILDE, lexer.nextToken().getType());
            assertEquals(TokenType.QUESTION, lexer.nextToken().getType());
            assertEquals(TokenType.COLON, lexer.nextToken().getType());
        }

        @Test
        void recognizesDoubleCharOperators() {
            Lexer lexer = new Lexer("++ -- && || == != <= >= << >> -> ::");
            assertEquals(TokenType.PLUS_PLUS, lexer.nextToken().getType());
            assertEquals(TokenType.MINUS_MINUS, lexer.nextToken().getType());
            assertEquals(TokenType.AMP_AMP, lexer.nextToken().getType());
            assertEquals(TokenType.PIPE_PIPE, lexer.nextToken().getType());
            assertEquals(TokenType.EQ_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.BANG_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.LT_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.GT_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.LT_LT, lexer.nextToken().getType());
            assertEquals(TokenType.GT_GT, lexer.nextToken().getType());
            assertEquals(TokenType.ARROW, lexer.nextToken().getType());
            assertEquals(TokenType.DOUBLE_COLON, lexer.nextToken().getType());
        }

        @Test
        void recognizesTripleCharOperators() {
            Lexer lexer = new Lexer(">>> <<= >>= >>>= += -= *= /= %= &= |= ^=");
            assertEquals(TokenType.GT_GT_GT, lexer.nextToken().getType());
            assertEquals(TokenType.LT_LT_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.GT_GT_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.GT_GT_GT_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.PLUS_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.MINUS_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.STAR_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.SLASH_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.PERCENT_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.AMP_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.PIPE_EQ, lexer.nextToken().getType());
            assertEquals(TokenType.CARET_EQ, lexer.nextToken().getType());
        }
    }

    @Nested
    class LiteralTests {
        @Test
        void recognizesIntegerLiterals() {
            Lexer lexer = new Lexer("42 0 123456");
            assertEquals(42, lexer.nextToken().getValue());
            assertEquals(0, lexer.nextToken().getValue());
            assertEquals(123456, lexer.nextToken().getValue());
        }

        @Test
        void recognizesHexLiterals() {
            Lexer lexer = new Lexer("0xFF 0x10 0xCAFEBABE");
            assertEquals(0xFF, lexer.nextToken().getValue());
            assertEquals(0x10, lexer.nextToken().getValue());
            assertEquals(0xCAFEBABE, lexer.nextToken().getValue());
        }

        @Test
        void recognizesBinaryLiterals() {
            Lexer lexer = new Lexer("0b1010 0B11111111");
            assertEquals(0b1010, lexer.nextToken().getValue());
            assertEquals(0B11111111, lexer.nextToken().getValue());
        }

        @Test
        void recognizesOctalLiterals() {
            Lexer lexer = new Lexer("077 0755");
            assertEquals(63, lexer.nextToken().getValue());
            assertEquals(493, lexer.nextToken().getValue());
        }

        @Test
        void recognizesUnderscoresInNumbers() {
            Lexer lexer = new Lexer("1_000_000 0xFF_FF");
            assertEquals(1_000_000, lexer.nextToken().getValue());
            assertEquals(0xFF_FF, lexer.nextToken().getValue());
        }

        @Test
        void recognizesLongLiterals() {
            Lexer lexer = new Lexer("42L 123456789012345L");
            Token t1 = lexer.nextToken();
            assertEquals(TokenType.LONG_LITERAL, t1.getType());
            assertEquals(42L, t1.getValue());
            Token t2 = lexer.nextToken();
            assertEquals(TokenType.LONG_LITERAL, t2.getType());
            assertEquals(123456789012345L, t2.getValue());
        }

        @Test
        void recognizesFloatLiterals() {
            Lexer lexer = new Lexer("3.14f 2.5F .5f");
            Token t1 = lexer.nextToken();
            assertEquals(TokenType.FLOAT_LITERAL, t1.getType());
            Token t2 = lexer.nextToken();
            assertEquals(TokenType.FLOAT_LITERAL, t2.getType());
            Token t3 = lexer.nextToken();
            assertEquals(TokenType.FLOAT_LITERAL, t3.getType());
        }

        @Test
        void recognizesDoubleLiterals() {
            Lexer lexer = new Lexer("3.14 2.5d 1e10 1.5e-3");
            Token t1 = lexer.nextToken();
            assertEquals(TokenType.DOUBLE_LITERAL, t1.getType());
            assertEquals(3.14, t1.getValue());
            Token t2 = lexer.nextToken();
            assertEquals(TokenType.DOUBLE_LITERAL, t2.getType());
            Token t3 = lexer.nextToken();
            assertEquals(TokenType.DOUBLE_LITERAL, t3.getType());
            Token t4 = lexer.nextToken();
            assertEquals(TokenType.DOUBLE_LITERAL, t4.getType());
        }

        @Test
        void recognizesStringLiterals() {
            Lexer lexer = new Lexer("\"hello\" \"world\"");
            Token t1 = lexer.nextToken();
            assertEquals(TokenType.STRING_LITERAL, t1.getType());
            assertEquals("hello", t1.getValue());
            Token t2 = lexer.nextToken();
            assertEquals(TokenType.STRING_LITERAL, t2.getType());
            assertEquals("world", t2.getValue());
        }

        @Test
        void recognizesStringEscapes() {
            Lexer lexer = new Lexer("\"hello\\nworld\" \"tab\\there\"");
            assertEquals("hello\nworld", lexer.nextToken().getValue());
            assertEquals("tab\there", lexer.nextToken().getValue());
        }

        @Test
        void recognizesCharLiterals() {
            Lexer lexer = new Lexer("'a' 'Z' '\\n' '\\t'");
            assertEquals('a', lexer.nextToken().getValue());
            assertEquals('Z', lexer.nextToken().getValue());
            assertEquals('\n', lexer.nextToken().getValue());
            assertEquals('\t', lexer.nextToken().getValue());
        }

        @Test
        void recognizesBooleanLiterals() {
            Lexer lexer = new Lexer("true false");
            assertEquals(TokenType.TRUE, lexer.nextToken().getType());
            assertEquals(TokenType.FALSE, lexer.nextToken().getType());
        }

        @Test
        void recognizesNullLiteral() {
            Lexer lexer = new Lexer("null");
            assertEquals(TokenType.NULL, lexer.nextToken().getType());
        }
    }

    @Nested
    class IdentifierTests {
        @Test
        void recognizesSimpleIdentifiers() {
            Lexer lexer = new Lexer("foo bar baz");
            assertEquals("foo", lexer.nextToken().getText());
            assertEquals("bar", lexer.nextToken().getText());
            assertEquals("baz", lexer.nextToken().getText());
        }

        @Test
        void recognizesIdentifiersWithNumbers() {
            Lexer lexer = new Lexer("foo1 bar2 baz123");
            assertEquals("foo1", lexer.nextToken().getText());
            assertEquals("bar2", lexer.nextToken().getText());
            assertEquals("baz123", lexer.nextToken().getText());
        }

        @Test
        void recognizesIdentifiersWithUnderscore() {
            Lexer lexer = new Lexer("_foo foo_bar __private");
            assertEquals("_foo", lexer.nextToken().getText());
            assertEquals("foo_bar", lexer.nextToken().getText());
            assertEquals("__private", lexer.nextToken().getText());
        }

        @Test
        void recognizesIdentifiersWithDollar() {
            Lexer lexer = new Lexer("$inner Outer$Inner");
            assertEquals("$inner", lexer.nextToken().getText());
            assertEquals("Outer$Inner", lexer.nextToken().getText());
        }
    }

    @Nested
    class DelimiterTests {
        @Test
        void recognizesDelimiters() {
            Lexer lexer = new Lexer("( ) { } [ ] ; , . @");
            assertEquals(TokenType.LPAREN, lexer.nextToken().getType());
            assertEquals(TokenType.RPAREN, lexer.nextToken().getType());
            assertEquals(TokenType.LBRACE, lexer.nextToken().getType());
            assertEquals(TokenType.RBRACE, lexer.nextToken().getType());
            assertEquals(TokenType.LBRACKET, lexer.nextToken().getType());
            assertEquals(TokenType.RBRACKET, lexer.nextToken().getType());
            assertEquals(TokenType.SEMICOLON, lexer.nextToken().getType());
            assertEquals(TokenType.COMMA, lexer.nextToken().getType());
            assertEquals(TokenType.DOT, lexer.nextToken().getType());
            assertEquals(TokenType.AT, lexer.nextToken().getType());
        }

        @Test
        void recognizesEllipsis() {
            Lexer lexer = new Lexer("...");
            assertEquals(TokenType.ELLIPSIS, lexer.nextToken().getType());
        }
    }

    @Nested
    class CommentTests {
        @Test
        void skipsLineComments() {
            Lexer lexer = new Lexer("foo // this is a comment\nbar");
            assertEquals("foo", lexer.nextToken().getText());
            assertEquals("bar", lexer.nextToken().getText());
        }

        @Test
        void skipsBlockComments() {
            Lexer lexer = new Lexer("foo /* block comment */ bar");
            assertEquals("foo", lexer.nextToken().getText());
            assertEquals("bar", lexer.nextToken().getText());
        }

        @Test
        void skipsMultiLineBlockComments() {
            Lexer lexer = new Lexer("foo /* \n * block \n * comment \n */ bar");
            assertEquals("foo", lexer.nextToken().getText());
            assertEquals("bar", lexer.nextToken().getText());
        }
    }

    @Nested
    class PositionTrackingTests {
        @Test
        void tracksLineNumbers() {
            Lexer lexer = new Lexer("a\nb\nc");
            Token t1 = lexer.nextToken();
            assertEquals(1, t1.getLine());
            Token t2 = lexer.nextToken();
            assertEquals(2, t2.getLine());
            Token t3 = lexer.nextToken();
            assertEquals(3, t3.getLine());
        }

        @Test
        void tracksColumnNumbers() {
            Lexer lexer = new Lexer("a b c");
            Token t1 = lexer.nextToken();
            assertEquals(1, t1.getColumn());
            Token t2 = lexer.nextToken();
            assertEquals(3, t2.getColumn());
            Token t3 = lexer.nextToken();
            assertEquals(5, t3.getColumn());
        }
    }

    @Nested
    class PeekTests {
        @Test
        void peekDoesNotAdvance() {
            Lexer lexer = new Lexer("a b c");
            Token peeked = lexer.peek();
            Token next = lexer.nextToken();
            assertEquals(peeked.getText(), next.getText());
        }

        @Test
        void multiplePeeksReturnSameToken() {
            Lexer lexer = new Lexer("a b");
            Token peek1 = lexer.peek();
            Token peek2 = lexer.peek();
            assertEquals(peek1.getText(), peek2.getText());
        }
    }

    @Nested
    class EOFTests {
        @Test
        void returnsEOFAtEnd() {
            Lexer lexer = new Lexer("a");
            lexer.nextToken();
            assertEquals(TokenType.EOF, lexer.nextToken().getType());
        }

        @Test
        void returnsEOFForEmptyInput() {
            Lexer lexer = new Lexer("");
            assertEquals(TokenType.EOF, lexer.nextToken().getType());
        }

        @Test
        void returnsEOFRepeatedly() {
            Lexer lexer = new Lexer("a");
            lexer.nextToken();
            assertEquals(TokenType.EOF, lexer.nextToken().getType());
            assertEquals(TokenType.EOF, lexer.nextToken().getType());
        }
    }
}
