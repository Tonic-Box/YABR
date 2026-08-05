package com.tonic.analysis.source.parser;

import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.type.SourceType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point for parsing Java source into the source AST, holding the error
 * listener that decides whether parse errors throw or are collected.
 */
public final class JavaParser
{

    private final ParseErrorListener errorListener;

    private JavaParser(ParseErrorListener errorListener)
    {
        this.errorListener = errorListener;
    }

    /**
     * @return the error listener
     */
    public ParseErrorListener getErrorListener()
    {
        return errorListener;
    }

    /**
     * @return a parser that throws on the first parse error
     */
    public static JavaParser create()
    {
        return new JavaParser(ParseErrorListener.throwing());
    }

    /**
     * Creates a parser that routes errors to a listener.
     *
     * @param listener the error sink
     * @return the parser
     */
    public static JavaParser withErrorListener(ParseErrorListener listener)
    {
        return new JavaParser(listener);
    }

    /**
     * Parses a whole source file.
     *
     * @param source the file text
     * @return the compilation unit
     * @throws ParseException if the text is malformed and the listener throws
     */
    public CompilationUnit parse(String source)
    {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer, source, errorListener);
        return parser.parseCompilationUnit();
    }

    /**
     * Reads a stream fully and parses it as a source file.
     *
     * @param reader the source text
     * @return the compilation unit
     * @throws IOException if reading fails
     * @throws ParseException if the text is malformed and the listener throws
     */
    public CompilationUnit parse(Reader reader) throws IOException
    {
        String source = readAll(reader);
        return parse(source);
    }

    /**
     * Reads a UTF-8 file and parses it as a source file.
     *
     * @param path the file to read
     * @return the compilation unit
     * @throws IOException if the file cannot be read
     * @throws ParseException if the text is malformed and the listener throws
     */
    public CompilationUnit parseFile(Path path) throws IOException
    {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        return parse(source);
    }

    /**
     * Reads a UTF-8 file and parses it as a source file.
     *
     * @param file the file to read
     * @return the compilation unit
     * @throws IOException if the file cannot be read
     * @throws ParseException if the text is malformed and the listener throws
     */
    public CompilationUnit parseFile(File file) throws IOException
    {
        return parseFile(file.toPath());
    }

    /**
     * Parses a single expression.
     *
     * @param source the expression text
     * @return the expression node
     * @throws ParseException if the text is malformed and the listener throws
     */
    public Expression parseExpression(String source)
    {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer, source, errorListener);
        return parser.parseExpression();
    }

    /**
     * Parses a single statement.
     *
     * @param source the statement text
     * @return the statement node
     * @throws ParseException if the text is malformed and the listener throws
     */
    public Statement parseStatement(String source)
    {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer, source, errorListener);
        return parser.parseStatement();
    }

    /**
     * Parses a block, wrapping the text in braces first if it has none.
     *
     * @param source the block text, with or without enclosing braces
     * @return the block node
     * @throws ParseException if the text is malformed, or parses to something
     *                        other than a block
     */
    public BlockStmt parseBlock(String source)
    {
        String wrapped = source;
        if (!source.trim().startsWith("{"))
        {
            wrapped = "{ " + source + " }";
        }
        Lexer lexer = new Lexer(wrapped);
        Parser parser = new Parser(lexer, wrapped, errorListener);
        Statement stmt = parser.parseStatement();
        if (stmt instanceof BlockStmt)
        {
            return (BlockStmt) stmt;
        }
        throw new ParseException("Expected block statement", SourcePosition.of(1, 1), wrapped);
    }

    /**
     * Parses a type reference.
     *
     * @param source the type text
     * @return the parsed type
     * @throws ParseException if the text is malformed and the listener throws
     */
    public SourceType parseType(String source)
    {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer, source, errorListener);
        return parser.parseType();
    }

    /**
     * Derives a parser using a different error sink.
     *
     * @param listener the error sink for the new parser
     * @return the derived parser
     */
    public JavaParser withListener(ParseErrorListener listener)
    {
        return new JavaParser(listener);
    }

    private static String readAll(Reader reader) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1)
        {
            sb.append(buffer, 0, read);
        }
        return sb.toString();
    }
}
