package com.tonic.analysis.source.parser;

import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.type.SourceType;
import lombok.Getter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
public final class JavaParser {

    private final ParseErrorListener errorListener;

    private JavaParser(ParseErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    public static JavaParser create() {
        return new JavaParser(ParseErrorListener.throwing());
    }

    public static JavaParser withErrorListener(ParseErrorListener listener) {
        return new JavaParser(listener);
    }

    public CompilationUnit parse(String source) {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer, source, errorListener);
        return parser.parseCompilationUnit();
    }

    public CompilationUnit parse(Reader reader) throws IOException {
        String source = readAll(reader);
        return parse(source);
    }

    public CompilationUnit parseFile(Path path) throws IOException {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        return parse(source);
    }

    public CompilationUnit parseFile(File file) throws IOException {
        return parseFile(file.toPath());
    }

    public Expression parseExpression(String source) {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer, source, errorListener);
        return parser.parseExpression();
    }

    public Statement parseStatement(String source) {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer, source, errorListener);
        return parser.parseStatement();
    }

    public BlockStmt parseBlock(String source) {
        String wrapped = source;
        if (!source.trim().startsWith("{")) {
            wrapped = "{ " + source + " }";
        }
        Lexer lexer = new Lexer(wrapped);
        Parser parser = new Parser(lexer, wrapped, errorListener);
        Statement stmt = parser.parseStatement();
        if (stmt instanceof BlockStmt) {
            return (BlockStmt) stmt;
        }
        throw new ParseException("Expected block statement", SourcePosition.of(1, 1), wrapped);
    }

    public SourceType parseType(String source) {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer, source, errorListener);
        return parser.parseType();
    }

    public JavaParser withListener(ParseErrorListener listener) {
        return new JavaParser(listener);
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, read);
        }
        return sb.toString();
    }
}
