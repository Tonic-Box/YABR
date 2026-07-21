package com.tonic.analysis.source.parser;

import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.decl.*;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;

import java.util.*;

public class Parser {

    private final Lexer lexer;
    private final String source;
    private final ParseErrorListener errorListener;
    private Token current;
    private final Deque<Map<String, SourceType>> scopes = new ArrayDeque<>();

    private static final Map<String, String> JAVA_LANG_TYPES = Map.ofEntries(
        Map.entry("Object", "java/lang/Object"),
        Map.entry("String", "java/lang/String"),
        Map.entry("Class", "java/lang/Class"),
        Map.entry("Integer", "java/lang/Integer"),
        Map.entry("Long", "java/lang/Long"),
        Map.entry("Double", "java/lang/Double"),
        Map.entry("Float", "java/lang/Float"),
        Map.entry("Boolean", "java/lang/Boolean"),
        Map.entry("Byte", "java/lang/Byte"),
        Map.entry("Short", "java/lang/Short"),
        Map.entry("Character", "java/lang/Character"),
        Map.entry("Number", "java/lang/Number"),
        Map.entry("Void", "java/lang/Void"),
        Map.entry("Throwable", "java/lang/Throwable"),
        Map.entry("Exception", "java/lang/Exception"),
        Map.entry("RuntimeException", "java/lang/RuntimeException"),
        Map.entry("Error", "java/lang/Error"),
        Map.entry("Thread", "java/lang/Thread"),
        Map.entry("Runnable", "java/lang/Runnable"),
        Map.entry("StringBuilder", "java/lang/StringBuilder"),
        Map.entry("StringBuffer", "java/lang/StringBuffer"),
        Map.entry("System", "java/lang/System"),
        Map.entry("Math", "java/lang/Math"),
        Map.entry("Comparable", "java/lang/Comparable"),
        Map.entry("Iterable", "java/lang/Iterable"),
        Map.entry("Enum", "java/lang/Enum"),
        Map.entry("Override", "java/lang/Override"),
        Map.entry("Deprecated", "java/lang/Deprecated"),
        Map.entry("SuppressWarnings", "java/lang/SuppressWarnings")
    );

    public Parser(Lexer lexer, String source, ParseErrorListener errorListener) {
        this.lexer = lexer;
        this.source = source;
        this.errorListener = errorListener != null ? errorListener : ParseErrorListener.throwing();
        scopes.push(new HashMap<>());
        advance();
    }

    private void pushScope() {
        scopes.push(new HashMap<>());
    }

    private void popScope() {
        if (scopes.size() > 1) {
            scopes.pop();
        }
    }

    private void defineVariable(String name, SourceType type) {
        if(scopes.peek() == null) {
            throw new IllegalStateException("No scope available to define variable: " + name);
        }
        scopes.peek().put(name, type);
    }

    private SourceType lookupVariable(String name) {
        for (Map<String, SourceType> scope : scopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return ReferenceSourceType.OBJECT;
    }

    public Parser(Lexer lexer, String source) {
        this(lexer, source, ParseErrorListener.throwing());
    }

    public CompilationUnit parseCompilationUnit() {
        CompilationUnit cu = new CompilationUnit(currentLocation());

        if (check(TokenType.PACKAGE)) {
            advance();
            cu.setPackageName(parseQualifiedName());
            consume(TokenType.SEMICOLON, "Expected ';' after package declaration");
        }

        while (check(TokenType.IMPORT)) {
            cu.addImport(parseImport());
        }

        while (!isAtEnd()) {
            TypeDecl type = parseTypeDeclaration();
            if (type != null) {
                cu.addType(type);
            }
        }

        return cu;
    }

    public Expression parseExpression() {
        return parseExpressionWithPrecedence(Precedence.ASSIGNMENT.getLevel());
    }

    public Statement parseStatement() {
        return parseBlockStatement();
    }

    public SourceType parseType() {
        return parseTypeReference();
    }

    private ImportDecl parseImport() {
        SourceLocation loc = currentLocation();
        consume(TokenType.IMPORT, "Expected 'import'");

        boolean isStatic = match(TokenType.STATIC);
        String name = parseQualifiedName();
        boolean isWildcard = false;

        if (match(TokenType.DOT)) {
            consume(TokenType.STAR, "Expected '*' after '.'");
            isWildcard = true;
        }

        consume(TokenType.SEMICOLON, "Expected ';' after import");
        return new ImportDecl(name, isStatic, isWildcard, loc);
    }

    private TypeDecl parseTypeDeclaration() {
        List<AnnotationExpr> annotations = new ArrayList<>();
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);

        while (check(TokenType.AT) || current.isModifier() || isSealedContextual()) {
            if (check(TokenType.AT)) {
                annotations.add(parseAnnotation());
            } else if (isSealedContextual()) {
                consumeSealedContextual();
            } else {
                modifiers.add(parseModifier());
            }
        }

        if (isRecordDeclAhead()) {
            return parseRecord(modifiers, annotations);
        } else if (check(TokenType.CLASS)) {
            return parseClass(modifiers, annotations);
        } else if (check(TokenType.INTERFACE)) {
            return parseInterface(modifiers, annotations);
        } else if (check(TokenType.ENUM)) {
            return parseEnum(modifiers, annotations);
        } else if (check(TokenType.AT) && checkNext(TokenType.INTERFACE)) {
            return parseAnnotationTypeDecl(modifiers, annotations);
        } else if (!isAtEnd()) {
            error("Expected class, interface, or enum declaration");
            advance();
        }

        return null;
    }

    private ClassDecl parseClass(Set<Modifier> modifiers, List<AnnotationExpr> annotations) {
        SourceLocation loc = currentLocation();
        consume(TokenType.CLASS, "Expected 'class'");

        String name = consume(TokenType.IDENTIFIER, "Expected class name").getText();
        ClassDecl cls = new ClassDecl(name, loc);
        cls.withModifiers(modifiers);
        for (AnnotationExpr ann : annotations) {
            cls.addAnnotation(ann);
        }

        if (match(TokenType.LT)) {
            parseTypeParameters(cls.getTypeParameters());
        }

        if (match(TokenType.EXTENDS)) {
            cls.withSuperclass(parseTypeReference());
        }

        if (match(TokenType.IMPLEMENTS)) {
            do {
                cls.addInterface(parseTypeReference());
            } while (match(TokenType.COMMA));
        }

        skipPermitsClause();

        parseClassBody(cls);
        return cls;
    }

    /**
     * True if a record declaration begins here: the contextual {@code record} keyword, a name, then
     * a component list {@code (} (or type parameters {@code <}). The trailing {@code (}/{@code <}
     * disambiguates from a field/variable whose type is literally named {@code record} in legacy code.
     */
    private boolean isRecordDeclAhead() {
        if (!checkIdentifier("record") || lexer.peekAhead(0).getType() != TokenType.IDENTIFIER) {
            return false;
        }
        TokenType afterName = lexer.peekAhead(1).getType();
        return afterName == TokenType.LPAREN || afterName == TokenType.LT;
    }

    /**
     * Parses a record declaration (Java 16). The implicit component backing fields are materialized
     * so body references resolve; the canonical constructor / accessors / ObjectMethods members are
     * compiler-synthesized and not represented here (recompilation edits an existing class, which
     * already carries them).
     */
    private ClassDecl parseRecord(Set<Modifier> modifiers, List<AnnotationExpr> annotations) {
        SourceLocation loc = currentLocation();
        advance(); // contextual 'record'
        String name = consume(TokenType.IDENTIFIER, "Expected record name").getText();
        ClassDecl cls = new ClassDecl(name, loc);
        cls.withModifiers(modifiers);
        for (AnnotationExpr ann : annotations) {
            cls.addAnnotation(ann);
        }
        if (match(TokenType.LT)) {
            parseTypeParameters(cls.getTypeParameters());
        }
        consume(TokenType.LPAREN, "Expected '(' for record components");
        if (!check(TokenType.RPAREN)) {
            do {
                while (check(TokenType.AT)) {
                    parseAnnotation();
                }
                SourceType type = parseTypeReference();
                String compName = consume(TokenType.IDENTIFIER, "Expected record component name").getText();
                cls.addField(new FieldDecl(compName, type, loc));
                defineVariable(compName, type);
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after record components");
        if (match(TokenType.IMPLEMENTS)) {
            do {
                cls.addInterface(parseTypeReference());
            } while (match(TokenType.COMMA));
        }
        skipPermitsClause();
        parseClassBody(cls);
        return cls;
    }

    private InterfaceDecl parseInterface(Set<Modifier> modifiers, List<AnnotationExpr> annotations) {
        SourceLocation loc = currentLocation();
        consume(TokenType.INTERFACE, "Expected 'interface'");

        String name = consume(TokenType.IDENTIFIER, "Expected interface name").getText();
        InterfaceDecl iface = new InterfaceDecl(name, loc);
        iface.withModifiers(modifiers);
        for (AnnotationExpr ann : annotations) {
            iface.addAnnotation(ann);
        }

        if (match(TokenType.LT)) {
            parseTypeParameters(iface.getTypeParameters());
        }

        if (match(TokenType.EXTENDS)) {
            do {
                iface.addExtendedInterface(parseTypeReference());
            } while (match(TokenType.COMMA));
        }

        skipPermitsClause();

        parseInterfaceBody(iface);
        return iface;
    }

    private EnumDecl parseEnum(Set<Modifier> modifiers, List<AnnotationExpr> annotations) {
        SourceLocation loc = currentLocation();
        consume(TokenType.ENUM, "Expected 'enum'");

        String name = consume(TokenType.IDENTIFIER, "Expected enum name").getText();
        EnumDecl enumDecl = new EnumDecl(name, loc);
        enumDecl.withModifiers(modifiers);
        for (AnnotationExpr ann : annotations) {
            enumDecl.addAnnotation(ann);
        }

        if (match(TokenType.IMPLEMENTS)) {
            do {
                enumDecl.addInterface(parseTypeReference());
            } while (match(TokenType.COMMA));
        }

        parseEnumBody(enumDecl);
        return enumDecl;
    }

    private TypeDecl parseAnnotationTypeDecl(Set<Modifier> modifiers, List<AnnotationExpr> annotations) {
        consume(TokenType.AT, "Expected '@'");
        consume(TokenType.INTERFACE, "Expected 'interface'");
        String name = consume(TokenType.IDENTIFIER, "Expected annotation type name").getText();
        InterfaceDecl annType = new InterfaceDecl(name, currentLocation());
        annType.withModifiers(modifiers);
        for (AnnotationExpr ann : annotations) {
            annType.addAnnotation(ann);
        }
        consume(TokenType.LBRACE, "Expected '{'");
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            parseMemberDeclaration(annType);
        }
        consume(TokenType.RBRACE, "Expected '}'");
        return annType;
    }

    private void parseClassBody(ClassDecl cls) {
        consume(TokenType.LBRACE, "Expected '{' before class body");

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            parseMemberDeclaration(cls);
        }

        consume(TokenType.RBRACE, "Expected '}' after class body");
    }

    private void parseInterfaceBody(InterfaceDecl iface) {
        consume(TokenType.LBRACE, "Expected '{' before interface body");

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            parseMemberDeclaration(iface);
        }

        consume(TokenType.RBRACE, "Expected '}' after interface body");
    }

    private void parseEnumBody(EnumDecl enumDecl) {
        consume(TokenType.LBRACE, "Expected '{' before enum body");

        if (!check(TokenType.SEMICOLON) && !check(TokenType.RBRACE)) {
            do {
                if (check(TokenType.RBRACE) || check(TokenType.SEMICOLON)) break;
                enumDecl.addConstant(parseEnumConstant());
            } while (match(TokenType.COMMA));
        }

        if (match(TokenType.SEMICOLON)) {
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                parseMemberDeclaration(enumDecl);
            }
        }

        consume(TokenType.RBRACE, "Expected '}' after enum body");
    }

    private EnumConstantDecl parseEnumConstant() {
        SourceLocation loc = currentLocation();
        List<AnnotationExpr> annotations = new ArrayList<>();
        while (check(TokenType.AT)) {
            annotations.add(parseAnnotation());
        }

        String name = consume(TokenType.IDENTIFIER, "Expected enum constant name").getText();
        EnumConstantDecl constant = new EnumConstantDecl(name, loc);
        for (AnnotationExpr ann : annotations) {
            constant.addAnnotation(ann);
        }

        if (match(TokenType.LPAREN)) {
            if (!check(TokenType.RPAREN)) {
                do {
                    constant.addArgument(parseExpression());
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "Expected ')' after arguments");
        }

        if (check(TokenType.LBRACE)) {
            advance();
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                List<AnnotationExpr> memberAnns = new ArrayList<>();
                Set<Modifier> memberMods = EnumSet.noneOf(Modifier.class);
                while (check(TokenType.AT) || current.isModifier()) {
                    if (check(TokenType.AT)) {
                        memberAnns.add(parseAnnotation());
                    } else {
                        memberMods.add(parseModifier());
                    }
                }
                SourceType type = parseTypeReference();
                String memberName = consume(TokenType.IDENTIFIER, "Expected member name").getText();
                if (check(TokenType.LPAREN)) {
                    MethodDecl method = parseMethod(memberMods, memberAnns, type, memberName);
                    constant.addMethod(method);
                } else {
                    for (FieldDecl field : parseField(memberMods, memberAnns, type, memberName)) {
                        constant.addField(field);
                    }
                }
            }
            consume(TokenType.RBRACE, "Expected '}'");
        }

        return constant;
    }

    private void parseMemberDeclaration(TypeDecl owner) {
        if (match(TokenType.SEMICOLON)) return;

        if (check(TokenType.LBRACE)) {
            BlockStmt block = parseInitializerBlock();
            if (owner instanceof ClassDecl) {
                ((ClassDecl) owner).addInstanceInitializer(block);
            }
            return;
        }

        if (check(TokenType.STATIC) && checkNext(TokenType.LBRACE)) {
            advance();
            BlockStmt block = parseInitializerBlock();
            if (owner instanceof ClassDecl) {
                ((ClassDecl) owner).addStaticInitializer(block);
            }
            return;
        }

        List<AnnotationExpr> annotations = new ArrayList<>();
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);

        while (check(TokenType.AT) || current.isModifier() || isSealedContextual()) {
            if (check(TokenType.AT)) {
                annotations.add(parseAnnotation());
            } else if (isSealedContextual()) {
                consumeSealedContextual();
            } else {
                modifiers.add(parseModifier());
            }
        }

        if (isRecordDeclAhead()) {
            owner.getInnerTypes().add(parseRecord(modifiers, annotations));
            return;
        }
        if (check(TokenType.CLASS)) {
            TypeDecl inner = parseClass(modifiers, annotations);
            owner.getInnerTypes().add(inner);
            return;
        }
        if (check(TokenType.INTERFACE)) {
            TypeDecl inner = parseInterface(modifiers, annotations);
            owner.getInnerTypes().add(inner);
            return;
        }
        if (check(TokenType.ENUM)) {
            TypeDecl inner = parseEnum(modifiers, annotations);
            owner.getInnerTypes().add(inner);
            return;
        }

        if (check(TokenType.IDENTIFIER) && current.getText().equals(owner.getName()) && checkNext(TokenType.LPAREN)) {
            ConstructorDecl ctor = parseConstructor(modifiers, annotations, owner.getName());
            if (owner instanceof ClassDecl) {
                ((ClassDecl) owner).addConstructor(ctor);
            } else if (owner instanceof EnumDecl) {
                ((EnumDecl) owner).addConstructor(ctor);
            }
            return;
        }

        if (check(TokenType.LT)) {
            List<SourceType> typeParams = new ArrayList<>();
            advance();
            parseTypeParameters(typeParams);

            if (check(TokenType.IDENTIFIER) && current.getText().equals(owner.getName()) && checkNext(TokenType.LPAREN)) {
                ConstructorDecl ctor = parseConstructor(modifiers, annotations, owner.getName());
                for (SourceType tp : typeParams) {
                    ctor.addTypeParameter(tp);
                }
                if (owner instanceof ClassDecl) {
                    ((ClassDecl) owner).addConstructor(ctor);
                }
                return;
            }

            SourceType returnType = parseTypeReference();
            String name = consume(TokenType.IDENTIFIER, "Expected method name").getText();
            MethodDecl method = parseMethod(modifiers, annotations, returnType, name);
            for (SourceType tp : typeParams) {
                method.addTypeParameter(tp);
            }
            owner.getMethods().add(method);
            return;
        }

        SourceType type = parseTypeReference();
        String name = consume(TokenType.IDENTIFIER, "Expected member name").getText();

        if (check(TokenType.LPAREN)) {
            MethodDecl method = parseMethod(modifiers, annotations, type, name);
            owner.getMethods().add(method);
        } else {
            owner.getFields().addAll(parseField(modifiers, annotations, type, name));
        }
    }

    private BlockStmt parseInitializerBlock() {
        return parseBlock();
    }

    private MethodDecl parseMethod(Set<Modifier> modifiers, List<AnnotationExpr> annotations,
                                    SourceType returnType, String name) {
        SourceLocation loc = currentLocation();
        MethodDecl method = new MethodDecl(name, returnType, loc);
        method.withModifiers(modifiers);
        for (AnnotationExpr ann : annotations) {
            method.addAnnotation(ann);
        }

        pushScope();

        consume(TokenType.LPAREN, "Expected '(' after method name");
        if (!check(TokenType.RPAREN)) {
            do {
                ParameterDecl param = parseParameter();
                method.addParameter(param);
                defineVariable(param.getName(), param.getType());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after parameters");

        if (match(TokenType.THROWS)) {
            do {
                method.addThrowsType(parseTypeReference());
            } while (match(TokenType.COMMA));
        }

        if (check(TokenType.LBRACE)) {
            method.withBody(parseBlock());
        } else {
            consume(TokenType.SEMICOLON, "Expected ';' or method body");
        }

        popScope();
        return method;
    }

    private ConstructorDecl parseConstructor(Set<Modifier> modifiers, List<AnnotationExpr> annotations,
                                              String name) {
        SourceLocation loc = currentLocation();
        advance();
        ConstructorDecl ctor = new ConstructorDecl(name, loc);
        ctor.withModifiers(modifiers);
        for (AnnotationExpr ann : annotations) {
            ctor.addAnnotation(ann);
        }

        pushScope();

        consume(TokenType.LPAREN, "Expected '(' after constructor name");
        if (!check(TokenType.RPAREN)) {
            do {
                ParameterDecl param = parseParameter();
                ctor.addParameter(param);
                defineVariable(param.getName(), param.getType());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after parameters");

        if (match(TokenType.THROWS)) {
            do {
                ctor.addThrowsType(parseTypeReference());
            } while (match(TokenType.COMMA));
        }

        ctor.withBody(parseBlock());
        popScope();
        return ctor;
    }

    private List<FieldDecl> parseField(Set<Modifier> modifiers, List<AnnotationExpr> annotations,
                                       SourceType type, String name) {
        List<FieldDecl> fields = new ArrayList<>();
        fields.add(parseFieldDeclarator(modifiers, annotations, type, name));
        while (match(TokenType.COMMA)) {
            String nextName = consume(TokenType.IDENTIFIER, "Expected field name").getText();
            fields.add(parseFieldDeclarator(modifiers, annotations, type, nextName));
        }
        consume(TokenType.SEMICOLON, "Expected ';' after field declaration");
        return fields;
    }

    /**
     * Builds a single field declarator (optional C-style {@code []} dimensions and initializer)
     * against a shared base type. The field name has already been consumed.
     */
    private FieldDecl parseFieldDeclarator(Set<Modifier> modifiers, List<AnnotationExpr> annotations,
                                           SourceType baseType, String name) {
        SourceLocation loc = currentLocation();
        SourceType type = baseType;
        while (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET)) {
            advance();
            advance();
            type = new ArraySourceType(type);
        }

        FieldDecl field = new FieldDecl(name, type, loc);
        field.withModifiers(modifiers);
        for (AnnotationExpr ann : annotations) {
            field.addAnnotation(ann);
        }
        if (match(TokenType.EQ)) {
            field.withInitializer(parseExpression());
        }
        return field;
    }

    private ParameterDecl parseParameter() {
        SourceLocation loc = currentLocation();
        List<AnnotationExpr> annotations = new ArrayList<>();
        boolean isFinal = false;

        while (check(TokenType.AT) || check(TokenType.FINAL)) {
            if (check(TokenType.AT)) {
                annotations.add(parseAnnotation());
            } else {
                advance();
                isFinal = true;
            }
        }

        SourceType type = parseTypeReference();
        boolean isVarArgs = match(TokenType.ELLIPSIS);
        String name = consume(TokenType.IDENTIFIER, "Expected parameter name").getText();

        ParameterDecl param = new ParameterDecl(name, type, loc);
        param.withFinal(isFinal);
        param.withVarArgs(isVarArgs);
        for (AnnotationExpr ann : annotations) {
            param.addAnnotation(ann);
        }

        return param;
    }

    private void parseTypeParameters(List<SourceType> typeParams) {
        do {
            String name = consume(TokenType.IDENTIFIER, "Expected type parameter name").getText();
            List<SourceType> bounds = new ArrayList<>();

            if (match(TokenType.EXTENDS)) {
                bounds.add(parseTypeReference());
                while (match(TokenType.AMP)) {
                    bounds.add(parseTypeReference());
                }
            }

            ReferenceSourceType param = new ReferenceSourceType(name, bounds);
            typeParams.add(param);
        } while (match(TokenType.COMMA));

        consume(TokenType.GT, "Expected '>' after type parameters");
    }

    private AnnotationExpr parseAnnotation() {
        SourceLocation loc = currentLocation();
        consume(TokenType.AT, "Expected '@'");

        SourceType type = parseTypeReference();
        AnnotationExpr ann = new AnnotationExpr(type, loc);

        if (match(TokenType.LPAREN)) {
            if (!check(TokenType.RPAREN)) {
                if (check(TokenType.IDENTIFIER) && checkNext(TokenType.EQ)) {
                    do {
                        String name = consume(TokenType.IDENTIFIER, "Expected attribute name").getText();
                        consume(TokenType.EQ, "Expected '='");
                        Expression value = parseAnnotationValue();
                        ann.addValue(name, value);
                    } while (match(TokenType.COMMA));
                } else {
                    Expression value = parseAnnotationValue();
                    ann.addValue("value", value);
                }
            }
            consume(TokenType.RPAREN, "Expected ')' after annotation");
        }

        return ann;
    }

    private Expression parseAnnotationValue() {
        if (check(TokenType.AT)) {
            return parseAnnotation();
        }
        if (check(TokenType.LBRACE)) {
            return parseArrayInit(new ArraySourceType(ReferenceSourceType.OBJECT));
        }
        return parseExpression();
    }

    private Modifier parseModifier() {
        Token token = current;
        advance();
        switch (token.getType()) {
            case PUBLIC: return Modifier.PUBLIC;
            case PROTECTED: return Modifier.PROTECTED;
            case PRIVATE: return Modifier.PRIVATE;
            case STATIC: return Modifier.STATIC;
            case FINAL: return Modifier.FINAL;
            case ABSTRACT: return Modifier.ABSTRACT;
            case SYNCHRONIZED: return Modifier.SYNCHRONIZED;
            case NATIVE: return Modifier.NATIVE;
            case STRICTFP: return Modifier.STRICTFP;
            case TRANSIENT: return Modifier.TRANSIENT;
            case VOLATILE: return Modifier.VOLATILE;
            case DEFAULT: return Modifier.DEFAULT;
            default:
                throw error("Unexpected modifier: " + token.getText());
        }
    }

    /** True if the current token begins a contextual {@code sealed} or {@code non-sealed} modifier. */
    private boolean isSealedContextual() {
        if (checkIdentifier("sealed")) {
            return true;
        }
        return checkIdentifier("non") && checkNext(TokenType.MINUS);
    }

    /** Consumes a {@code sealed} or {@code non-sealed} contextual modifier (tolerated, not modeled). */
    private void consumeSealedContextual() {
        if (checkIdentifier("sealed")) {
            advance();
            return;
        }
        // non - sealed
        advance();
        advance();
        if (checkIdentifier("sealed")) {
            advance();
        }
    }

    /** Skips an optional {@code permits A, B, ...} clause on a sealed type declaration. */
    private void skipPermitsClause() {
        if (checkIdentifier("permits")) {
            advance();
            do {
                parseTypeReference();
            } while (match(TokenType.COMMA));
        }
    }

    private boolean checkIdentifier(String text) {
        return current.getType() == TokenType.IDENTIFIER && text.equals(current.getText());
    }

    private BlockStmt parseBlock() {
        SourceLocation loc = currentLocation();
        consume(TokenType.LBRACE, "Expected '{'");

        List<Statement> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            if (isLocalVariableDeclaration()) {
                statements.addAll(parseLocalVariableDeclarations());
                continue;
            }
            Statement stmt = parseBlockStatement();
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        consume(TokenType.RBRACE, "Expected '}'");
        return new BlockStmt(statements, loc);
    }

    private Statement parseBlockStatement() {
        if (check(TokenType.LBRACE)) {
            return parseBlock();
        }
        if (check(TokenType.IF)) {
            return parseIf();
        }
        if (check(TokenType.WHILE)) {
            return parseWhile();
        }
        if (check(TokenType.DO)) {
            return parseDoWhile();
        }
        if (check(TokenType.FOR)) {
            return parseFor();
        }
        if (check(TokenType.SWITCH)) {
            return parseSwitch();
        }
        if (check(TokenType.TRY)) {
            return parseTry();
        }
        if (check(TokenType.RETURN)) {
            return parseReturn();
        }
        if (check(TokenType.THROW)) {
            return parseThrow();
        }
        if (check(TokenType.BREAK)) {
            return parseBreak();
        }
        if (check(TokenType.CONTINUE)) {
            return parseContinue();
        }
        if (check(TokenType.SYNCHRONIZED)) {
            return parseSynchronized();
        }
        if (check(TokenType.ASSERT)) {
            return parseAssert();
        }
        if (check(TokenType.SEMICOLON)) {
            advance();
            return new BlockStmt(List.of(), currentLocation());
        }

        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.COLON)) {
            return parseLabeled();
        }

        if (isLocalVariableDeclaration()) {
            return parseLocalVariable();
        }

        return parseExpressionStatement();
    }

    private boolean isLocalVariableDeclaration() {
        if (check(TokenType.FINAL)) return true;
        if (check(TokenType.VAR)) return true;
        if (current.isPrimitiveType()) return true;

        if (check(TokenType.IDENTIFIER)) {
            return looksLikeTypeDeclaration();
        }
        return false;
    }

    private boolean looksLikeTypeDeclaration() {
        int offset = skipTypeTokens(0);
        return offset >= 0 && lexer.peekAhead(offset).getType() == TokenType.IDENTIFIER;
    }

    /**
     * Assuming {@code current} is the first identifier of a type, returns the peek-offset of the token
     * immediately after the (possibly qualified, generic, or array) type, or -1 on a malformed array.
     */
    private int skipTypeTokens(int offset) {
        if (lexer.peekAhead(offset).getType() == TokenType.DOT) {
            offset++;
            while (lexer.peekAhead(offset).getType() == TokenType.IDENTIFIER) {
                offset++;
                if (lexer.peekAhead(offset).getType() != TokenType.DOT) break;
                offset++;
            }
        }

        if (lexer.peekAhead(offset).getType() == TokenType.LT) {
            int depth = 1;
            offset++;
            while (lexer.peekAhead(offset).getType() != TokenType.EOF) {
                TokenType t = lexer.peekAhead(offset).getType();
                if (t == TokenType.LT) depth++;
                else if (t == TokenType.GT) depth--;
                else if (t == TokenType.GT_GT) depth -= 2;
                else if (t == TokenType.GT_GT_GT) depth -= 3;
                offset++;
                if (depth <= 0) break;
            }
        }

        while (lexer.peekAhead(offset).getType() == TokenType.LBRACKET) {
            offset++;
            if (lexer.peekAhead(offset).getType() == TokenType.RBRACKET) {
                offset++;
            } else {
                return -1;
            }
        }

        return offset;
    }

    /** A switch case-label type pattern: {@code Type binding} ({@code current} is the type's first identifier). */
    private boolean looksLikeTypePattern() {
        if (!check(TokenType.IDENTIFIER)) return false;
        int offset = skipTypeTokens(0);
        return offset >= 0 && lexer.peekAhead(offset).getType() == TokenType.IDENTIFIER;
    }

    /** A switch case-label record-deconstruction pattern: {@code Type(...)}. */
    private boolean looksLikeRecordPattern() {
        if (!check(TokenType.IDENTIFIER)) return false;
        int offset = skipTypeTokens(0);
        return offset >= 0 && lexer.peekAhead(offset).getType() == TokenType.LPAREN;
    }

    private IfStmt parseIf() {
        SourceLocation loc = currentLocation();
        consume(TokenType.IF, "Expected 'if'");
        consume(TokenType.LPAREN, "Expected '(' after 'if'");
        Expression condition = parseExpression();
        consume(TokenType.RPAREN, "Expected ')' after condition");

        Statement thenBranch = parseBlockStatement();
        Statement elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = parseBlockStatement();
        }

        return new IfStmt(condition, thenBranch, elseBranch, loc);
    }

    private WhileStmt parseWhile() {
        SourceLocation loc = currentLocation();
        consume(TokenType.WHILE, "Expected 'while'");
        consume(TokenType.LPAREN, "Expected '(' after 'while'");
        Expression condition = parseExpression();
        consume(TokenType.RPAREN, "Expected ')' after condition");
        Statement body = parseBlockStatement();

        return new WhileStmt(condition, body, null, loc);
    }

    private DoWhileStmt parseDoWhile() {
        SourceLocation loc = currentLocation();
        consume(TokenType.DO, "Expected 'do'");
        Statement body = parseBlockStatement();
        consume(TokenType.WHILE, "Expected 'while' after do body");
        consume(TokenType.LPAREN, "Expected '(' after 'while'");
        Expression condition = parseExpression();
        consume(TokenType.RPAREN, "Expected ')' after condition");
        consume(TokenType.SEMICOLON, "Expected ';' after do-while");

        return new DoWhileStmt(body, condition, null, loc);
    }

    private Statement parseFor() {
        SourceLocation loc = currentLocation();
        consume(TokenType.FOR, "Expected 'for'");
        consume(TokenType.LPAREN, "Expected '(' after 'for'");

        if (isEnhancedFor()) {
            return parseForEach(loc);
        }

        List<Statement> init = new ArrayList<>();
        if (!check(TokenType.SEMICOLON)) {
            if (isLocalVariableDeclaration()) {
                init.addAll(parseLocalVariableDeclaratorsNoSemi());
            } else {
                init.add(new ExprStmt(parseExpression(), loc));
                while (match(TokenType.COMMA)) {
                    init.add(new ExprStmt(parseExpression(), loc));
                }
            }
        }
        consume(TokenType.SEMICOLON, "Expected ';' after for init");

        Expression condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = parseExpression();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after for condition");

        List<Expression> update = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                update.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after for clauses");

        Statement body = parseBlockStatement();
        return new ForStmt(init, condition, update, body, null, loc);
    }

    private boolean isEnhancedFor() {
        int depth = 0;
        Lexer tempLexer = new Lexer(source.substring(lexer.currentPosition().getOffset() - current.getText().length()));
        Token t = tempLexer.nextToken();

        while (t.getType() != TokenType.EOF && t.getType() != TokenType.SEMICOLON) {
            if (t.getType() == TokenType.LPAREN) depth++;
            if (t.getType() == TokenType.RPAREN) {
                depth--;
                if (depth < 0) break;
            }
            if (t.getType() == TokenType.COLON && depth == 0) {
                return true;
            }
            t = tempLexer.nextToken();
        }
        return false;
    }

    private ForEachStmt parseForEach(SourceLocation loc) {
        boolean isFinal = match(TokenType.FINAL);
        SourceType type = parseTypeReference();
        String varName = consume(TokenType.IDENTIFIER, "Expected variable name").getText();
        consume(TokenType.COLON, "Expected ':' in enhanced for");
        Expression iterable = parseExpression();
        consume(TokenType.RPAREN, "Expected ')' after enhanced for");
        Statement body = parseBlockStatement();

        VarDeclStmt varDecl = new VarDeclStmt(type, varName, null, false, isFinal, loc);
        defineVariable(varName, type);
        return new ForEachStmt(varDecl, iterable, body, null, loc);
    }

    private SwitchStmt parseSwitch() {
        SourceLocation loc = currentLocation();
        consume(TokenType.SWITCH, "Expected 'switch'");
        consume(TokenType.LPAREN, "Expected '(' after 'switch'");
        Expression selector = parseExpression();
        consume(TokenType.RPAREN, "Expected ')' after switch selector");
        consume(TokenType.LBRACE, "Expected '{' before switch body");

        List<SwitchCase> cases = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            cases.add(parseSwitchCase());
        }

        consume(TokenType.RBRACE, "Expected '}' after switch body");
        return new SwitchStmt(selector, cases, loc);
    }

    /**
     * Parses a switch expression (Java 14) in arrow form:
     * {@code switch (sel) { case L1, L2 -> expr; default -> expr; }}.
     */
    private com.tonic.analysis.source.ast.expr.SwitchExpr parseSwitchExpr() {
        SourceLocation loc = currentLocation();
        consume(TokenType.SWITCH, "Expected 'switch'");
        consume(TokenType.LPAREN, "Expected '(' after 'switch'");
        Expression selector = parseExpression();
        consume(TokenType.RPAREN, "Expected ')' after switch selector");
        consume(TokenType.LBRACE, "Expected '{' before switch body");

        List<com.tonic.analysis.source.ast.expr.SwitchExpr.Arm> arms = new ArrayList<>();
        SourceType type = null;
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            com.tonic.analysis.source.ast.expr.SwitchExpr.Arm arm = parseSwitchExprArm();
            arms.add(arm);
            if (type == null && arm.getResult() != null && arm.getResult().getType() != null) {
                type = arm.getResult().getType();
            }
        }
        consume(TokenType.RBRACE, "Expected '}' after switch expression body");
        return new com.tonic.analysis.source.ast.expr.SwitchExpr(selector, arms, type, loc);
    }

    /**
     * Parses one switch-expression arm: a constant arm {@code case L1, L2 -> e}, a type-pattern arm
     * {@code case T b [when g] -> e}, a record-deconstruction arm {@code case T(C0 b0, ...) [when g] -> e},
     * or {@code default -> e} (Java 21 pattern switch).
     */
    private com.tonic.analysis.source.ast.expr.SwitchExpr.Arm parseSwitchExprArm() {
        List<Expression> labels = new ArrayList<>();
        SourceType patternType = null;
        String patternBinding = null;
        List<com.tonic.analysis.source.ast.expr.SwitchExpr.Component> components = null;
        Expression guard = null;
        boolean isDefault = false;

        if (match(TokenType.CASE)) {
            if (looksLikeRecordPattern()) {
                patternType = parseType();
                components = parseDeconstructionComponents();
            } else if (looksLikeTypePattern()) {
                patternType = parseType();
                patternBinding = consume(TokenType.IDENTIFIER, "Expected pattern binding name").getText();
            } else {
                do {
                    labels.add(parseExpression());
                } while (match(TokenType.COMMA));
            }
            if (patternType != null && checkIdentifier("when")) {
                advance();
                guard = parseExpression();
            }
        } else if (match(TokenType.DEFAULT)) {
            isDefault = true;
        } else {
            throw error("Expected 'case' or 'default' in switch expression");
        }

        consume(TokenType.ARROW, "Expected '->' in switch expression arm");
        Expression result = parseExpression();
        consume(TokenType.SEMICOLON, "Expected ';' after switch expression arm");

        if (patternType != null) {
            return new com.tonic.analysis.source.ast.expr.SwitchExpr.Arm(
                    new ArrayList<>(), false, patternType, patternBinding, components, guard, result);
        }
        return new com.tonic.analysis.source.ast.expr.SwitchExpr.Arm(labels, isDefault, result);
    }

    /** Parses {@code (C0 b0, C1 b1, ...)} of a record-deconstruction pattern (flat components). */
    private List<com.tonic.analysis.source.ast.expr.SwitchExpr.Component> parseDeconstructionComponents() {
        consume(TokenType.LPAREN, "Expected '(' in record-deconstruction pattern");
        List<com.tonic.analysis.source.ast.expr.SwitchExpr.Component> components = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                SourceType compType = parseType();
                String compBinding = consume(TokenType.IDENTIFIER, "Expected component binding name").getText();
                components.add(new com.tonic.analysis.source.ast.expr.SwitchExpr.Component(compType, compBinding));
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after record-deconstruction components");
        return components;
    }

    private SwitchCase parseSwitchCase() {
        List<Expression> labels = new ArrayList<>();
        boolean isDefault = false;

        if (match(TokenType.CASE)) {
            do {
                labels.add(parseExpression());
            } while (match(TokenType.COMMA));
        } else if (match(TokenType.DEFAULT)) {
            isDefault = true;
        } else {
            throw error("Expected 'case' or 'default'");
        }

        consume(TokenType.COLON, "Expected ':' after case label");

        List<Statement> statements = new ArrayList<>();
        while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE) && !isAtEnd()) {
            if (isLocalVariableDeclaration()) {
                statements.addAll(parseLocalVariableDeclarations());
                continue;
            }
            Statement stmt = parseBlockStatement();
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        if (isDefault) {
            return SwitchCase.defaultCase(statements);
        }
        return SwitchCase.ofExpressions(labels, statements);
    }

    private TryCatchStmt parseTry() {
        SourceLocation loc = currentLocation();
        consume(TokenType.TRY, "Expected 'try'");

        List<Expression> resources = new ArrayList<>();
        if (match(TokenType.LPAREN)) {
            do {
                if (check(TokenType.RPAREN)) break;
                if (check(TokenType.FINAL) || (check(TokenType.IDENTIFIER) && looksLikeTypeDeclaration())) {
                    VarDeclStmt decl = parseResource();
                    if (decl.getInitializer() != null) {
                        resources.add(decl.getInitializer());
                    }
                } else {
                    // A concise (Java 9+) resource: an existing effectively-final variable or field access used
                    // directly as the resource, e.g. `try (in)`, rather than a declaration `try (Type in = init)`.
                    resources.add(parseExpression());
                }
            } while (match(TokenType.SEMICOLON));
            consume(TokenType.RPAREN, "Expected ')' after resources");
        }

        BlockStmt tryBlock = parseBlock();

        List<CatchClause> catchClauses = new ArrayList<>();
        while (check(TokenType.CATCH)) {
            catchClauses.add(parseCatch());
        }

        BlockStmt finallyBlock = null;
        if (match(TokenType.FINALLY)) {
            finallyBlock = parseBlock();
        }

        return new TryCatchStmt(tryBlock, catchClauses, finallyBlock, resources, loc);
    }

    private VarDeclStmt parseResource() {
        SourceLocation loc = currentLocation();
        boolean isFinal = match(TokenType.FINAL);
        SourceType type = parseTypeReference();
        String name = consume(TokenType.IDENTIFIER, "Expected resource name").getText();
        consume(TokenType.EQ, "Expected '=' in resource declaration");
        Expression init = parseExpression();

        defineVariable(name, type);
        return new VarDeclStmt(type, name, init, false, isFinal, loc);
    }

    private CatchClause parseCatch() {
        consume(TokenType.CATCH, "Expected 'catch'");
        consume(TokenType.LPAREN, "Expected '(' after 'catch'");

        match(TokenType.FINAL);
        List<SourceType> exceptionTypes = new ArrayList<>();
        exceptionTypes.add(parseTypeReference());
        while (match(TokenType.PIPE)) {
            exceptionTypes.add(parseTypeReference());
        }

        String varName = consume(TokenType.IDENTIFIER, "Expected exception variable name").getText();
        consume(TokenType.RPAREN, "Expected ')' after catch parameter");
        BlockStmt body = parseBlock();

        return new CatchClause(exceptionTypes, varName, body);
    }

    private ReturnStmt parseReturn() {
        SourceLocation loc = currentLocation();
        consume(TokenType.RETURN, "Expected 'return'");
        Expression value = null;
        if (!check(TokenType.SEMICOLON)) {
            value = parseExpression();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after return");
        return new ReturnStmt(value, loc);
    }

    private ThrowStmt parseThrow() {
        SourceLocation loc = currentLocation();
        consume(TokenType.THROW, "Expected 'throw'");
        Expression value = parseExpression();
        consume(TokenType.SEMICOLON, "Expected ';' after throw");
        return new ThrowStmt(value, loc);
    }

    private BreakStmt parseBreak() {
        SourceLocation loc = currentLocation();
        consume(TokenType.BREAK, "Expected 'break'");
        String label = null;
        if (check(TokenType.IDENTIFIER)) {
            label = advance().getText();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after break");
        return new BreakStmt(label, loc);
    }

    private ContinueStmt parseContinue() {
        SourceLocation loc = currentLocation();
        consume(TokenType.CONTINUE, "Expected 'continue'");
        String label = null;
        if (check(TokenType.IDENTIFIER)) {
            label = advance().getText();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after continue");
        return new ContinueStmt(label, loc);
    }

    private SynchronizedStmt parseSynchronized() {
        SourceLocation loc = currentLocation();
        consume(TokenType.SYNCHRONIZED, "Expected 'synchronized'");
        consume(TokenType.LPAREN, "Expected '(' after 'synchronized'");
        Expression lock = parseExpression();
        consume(TokenType.RPAREN, "Expected ')' after lock expression");
        BlockStmt body = parseBlock();
        return new SynchronizedStmt(lock, body, loc);
    }

    private Statement parseAssert() {
        SourceLocation loc = currentLocation();
        consume(TokenType.ASSERT, "Expected 'assert'");
        Expression condition = parseExpression();
        consume(TokenType.SEMICOLON, "Expected ';' after assert");

        return new ExprStmt(condition, loc);
    }

    private LabeledStmt parseLabeled() {
        SourceLocation loc = currentLocation();
        String label = consume(TokenType.IDENTIFIER, "Expected label").getText();
        consume(TokenType.COLON, "Expected ':'");
        Statement body = parseBlockStatement();
        return new LabeledStmt(label, body, loc);
    }

    private VarDeclStmt parseLocalVariable() {
        VarDeclStmt decl = parseLocalVariableNoSemi();
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration");
        return decl;
    }

    private VarDeclStmt parseLocalVariableNoSemi() {
        boolean isFinal = match(TokenType.FINAL);
        TypePrefix prefix = parseVarTypePrefix();
        return parseDeclarator(prefix.type, prefix.useVar, isFinal);
    }

    /**
     * Parses a local variable declaration with one or more comma-separated declarators sharing
     * the base type (e.g. {@code int a = 0, b = 1;}), consuming the trailing semicolon, and
     * returns one VarDeclStmt per declarator.
     */
    private List<VarDeclStmt> parseLocalVariableDeclarations() {
        List<VarDeclStmt> decls = parseLocalVariableDeclaratorsNoSemi();
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration");
        return decls;
    }

    /**
     * Parses comma-separated declarators of a local variable declaration without the trailing
     * semicolon (used by for-loop initializers, e.g. {@code for (int i = 0, j = n; ...)}).
     */
    private List<VarDeclStmt> parseLocalVariableDeclaratorsNoSemi() {
        boolean isFinal = match(TokenType.FINAL);
        TypePrefix prefix = parseVarTypePrefix();
        List<VarDeclStmt> decls = new ArrayList<>();
        do {
            decls.add(parseDeclarator(prefix.type, prefix.useVar, isFinal));
        } while (match(TokenType.COMMA));
        return decls;
    }

    private TypePrefix parseVarTypePrefix() {
        if (match(TokenType.VAR)) {
            return new TypePrefix(ReferenceSourceType.OBJECT, true);
        }
        return new TypePrefix(parseTypeReference(), false);
    }

    /**
     * Parses a single declarator (name, optional C-style {@code []} dimensions, optional
     * initializer) against a shared base type, and registers the variable.
     */
    private VarDeclStmt parseDeclarator(SourceType baseType, boolean useVar, boolean isFinal) {
        SourceLocation loc = currentLocation();
        String name = consume(TokenType.IDENTIFIER, "Expected variable name").getText();

        SourceType type = baseType;
        while (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET)) {
            advance();
            advance();
            type = new ArraySourceType(type);
        }

        Expression init = null;
        if (match(TokenType.EQ)) {
            init = parseExpression();
        }
        if (useVar && init != null) {
            type = init.getType();
        }
        defineVariable(name, type);
        return new VarDeclStmt(type, name, init, false, isFinal, loc);
    }

    private static final class TypePrefix {
        final SourceType type;
        final boolean useVar;

        TypePrefix(SourceType type, boolean useVar) {
            this.type = type;
            this.useVar = useVar;
        }
    }

    private ExprStmt parseExpressionStatement() {
        SourceLocation loc = currentLocation();
        Expression expr = parseExpression();
        consume(TokenType.SEMICOLON, "Expected ';' after expression");
        return new ExprStmt(expr, loc);
    }

    private Expression parseExpressionWithPrecedence(int minPrecedence) {
        Expression left = parsePrefixExpression();

        while (!isAtEnd()) {
            Precedence prec = Precedence.of(current.getType());
            if (prec.getLevel() < minPrecedence) {
                break;
            }

            int nextMinPrec = prec.isRightAssociative() ? prec.getLevel() : prec.getLevel() + 1;
            left = parseInfixExpression(left, nextMinPrec);
        }

        return left;
    }

    private Expression parsePrefixExpression() {
        SourceLocation loc = currentLocation();

        if (match(TokenType.BANG)) {
            Expression operand = parsePrefixExpression();
            return new UnaryExpr(UnaryOperator.NOT, operand, PrimitiveSourceType.BOOLEAN, loc);
        }
        if (match(TokenType.TILDE)) {
            Expression operand = parsePrefixExpression();
            return new UnaryExpr(UnaryOperator.BNOT, operand, PrimitiveSourceType.INT, loc);
        }
        if (match(TokenType.MINUS)) {
            Expression operand = parsePrefixExpression();
            if (operand instanceof LiteralExpr) {
                LiteralExpr lit = (LiteralExpr) operand;
                Object val = lit.getValue();
                if (val instanceof Long) {
                    long lval = (Long) val;
                    // An explicit `long` literal (an `L` suffix, so type LONG) stays long, whatever its
                    // magnitude: `-1L` is long -1, not int -1. Only a decimal INT literal whose positive
                    // form overflowed int (e.g. 2147483648, lexed as a Long with INT type) narrows back to
                    // int on negation - its value is then in int range (down to Integer.MIN_VALUE).
                    if (lit.getType() == PrimitiveSourceType.LONG) {
                        return new LiteralExpr(-lval, PrimitiveSourceType.LONG, loc);
                    }
                    if (lval == 2147483648L) {
                        return new LiteralExpr(Integer.MIN_VALUE, PrimitiveSourceType.INT, loc);
                    }
                    long negated = -lval;
                    if (negated >= Integer.MIN_VALUE && negated <= Integer.MAX_VALUE) {
                        return new LiteralExpr((int) negated, PrimitiveSourceType.INT, loc);
                    }
                    return new LiteralExpr(negated, PrimitiveSourceType.LONG, loc);
                }
                if (val instanceof Integer) {
                    return new LiteralExpr(-((Integer) val), PrimitiveSourceType.INT, loc);
                }
                if (val instanceof Double) {
                    return new LiteralExpr(-((Double) val), PrimitiveSourceType.DOUBLE, loc);
                }
                if (val instanceof Float) {
                    return new LiteralExpr(-((Float) val), PrimitiveSourceType.FLOAT, loc);
                }
            }
            return new UnaryExpr(UnaryOperator.NEG, operand, operand.getType(), loc);
        }
        if (match(TokenType.PLUS)) {
            Expression operand = parsePrefixExpression();
            return new UnaryExpr(UnaryOperator.POS, operand, operand.getType(), loc);
        }
        if (match(TokenType.PLUS_PLUS)) {
            Expression operand = parsePrefixExpression();
            return new UnaryExpr(UnaryOperator.PRE_INC, operand, operand.getType(), loc);
        }
        if (match(TokenType.MINUS_MINUS)) {
            Expression operand = parsePrefixExpression();
            return new UnaryExpr(UnaryOperator.PRE_DEC, operand, operand.getType(), loc);
        }

        if (check(TokenType.LPAREN) && isCastExpression()) {
            return parseCast();
        }

        return parsePostfixExpression();
    }

    private boolean isCastExpression() {
        if (!check(TokenType.LPAREN)) return false;

        Lexer tempLexer = new Lexer(source.substring(lexer.currentPosition().getOffset() - current.getText().length()));
        Token t = tempLexer.nextToken();
        if (t.getType() != TokenType.LPAREN) return false;

        t = tempLexer.nextToken();
        if (!t.isPrimitiveType() && t.getType() != TokenType.IDENTIFIER) {
            return false;
        }

        int depth = 1;
        while (depth > 0 && t.getType() != TokenType.EOF) {
            t = tempLexer.nextToken();
            if (t.getType() == TokenType.LPAREN) depth++;
            if (t.getType() == TokenType.RPAREN) depth--;
        }

        if (depth != 0) return false;

        t = tempLexer.nextToken();
        return t.getType() == TokenType.IDENTIFIER ||
               t.getType() == TokenType.LPAREN ||
               t.getType() == TokenType.NEW ||
               t.getType() == TokenType.THIS ||
               t.getType() == TokenType.SUPER ||
               t.isLiteral() ||
               t.getType() == TokenType.BANG ||
               t.getType() == TokenType.TILDE ||
               t.getType() == TokenType.PLUS_PLUS ||
               t.getType() == TokenType.MINUS_MINUS;
    }

    private CastExpr parseCast() {
        SourceLocation loc = currentLocation();
        consume(TokenType.LPAREN, "Expected '('");
        SourceType type = parseTypeReference();
        consume(TokenType.RPAREN, "Expected ')'");
        Expression operand = parsePrefixExpression();
        return new CastExpr(type, operand, loc);
    }

    private Expression parsePostfixExpression() {
        Expression expr = parsePrimaryExpression();

        while (true) {
            SourceLocation loc = currentLocation();

            if (match(TokenType.DOT)) {
                if (check(TokenType.CLASS)) {
                    advance();
                    SourceType classType = getTypeFromExpression(expr);
                    expr = new ClassExpr(classType, loc);
                } else if (check(TokenType.THIS)) {
                    advance();
                    SourceType qualType = getTypeFromExpression(expr);
                    expr = new ThisExpr(qualType, loc);
                } else if (check(TokenType.SUPER)) {
                    advance();
                    SourceType qualType = getTypeFromExpression(expr);
                    expr = new SuperExpr(qualType, loc);
                } else if (check(TokenType.NEW)) {
                    advance();
                    expr = parseInnerClassCreation(expr, loc);
                } else {
                    String name = consume(TokenType.IDENTIFIER, "Expected field or method name").getText();
                    if (check(TokenType.LPAREN)) {
                        expr = parseMethodCall(expr, name, loc);
                    } else {
                        String ownerClass = deriveOwnerClass(expr);
                        expr = new FieldAccessExpr(expr, name, ownerClass, false, ReferenceSourceType.OBJECT, loc);
                    }
                }
            } else if (match(TokenType.LBRACKET)) {
                Expression index = parseExpression();
                consume(TokenType.RBRACKET, "Expected ']'");
                SourceType elementType = ReferenceSourceType.OBJECT;
                SourceType arrayType = expr.getType();
                if (arrayType instanceof ArraySourceType) {
                    elementType = ((ArraySourceType) arrayType).getElementType();
                }
                expr = new ArrayAccessExpr(expr, index, elementType, loc);
            } else if (match(TokenType.PLUS_PLUS)) {
                expr = new UnaryExpr(UnaryOperator.POST_INC, expr, expr.getType(), loc);
            } else if (match(TokenType.MINUS_MINUS)) {
                expr = new UnaryExpr(UnaryOperator.POST_DEC, expr, expr.getType(), loc);
            } else if (match(TokenType.DOUBLE_COLON)) {
                expr = parseMethodReference(expr, loc);
            } else if (check(TokenType.LPAREN) && (expr instanceof SuperExpr || expr instanceof ThisExpr)) {
                expr = parseMethodCall(expr, "<init>", loc);
            } else {
                break;
            }
        }

        return expr;
    }

    private SourceType getTypeFromExpression(Expression expr) {
        if (expr instanceof VarRefExpr) {
            return new ReferenceSourceType(((VarRefExpr) expr).getName());
        }
        if (expr instanceof FieldAccessExpr) {
            return new ReferenceSourceType(((FieldAccessExpr) expr).getFieldName());
        }
        return ReferenceSourceType.OBJECT;
    }

    private String deriveOwnerClass(Expression expr) {
        if (expr == null) {
            return "java/lang/Object";
        }
        SourceType type = expr.getType();
        if (type instanceof ReferenceSourceType) {
            return ((ReferenceSourceType) type).getInternalName();
        }
        if (type instanceof GenericSourceType) {
            return ((GenericSourceType) type).getRawType().getInternalName();
        }
        return "java/lang/Object";
    }

    private Expression parsePrimaryExpression() {
        SourceLocation loc = currentLocation();

        if (check(TokenType.SWITCH)) {
            return parseSwitchExpr();
        }
        if (match(TokenType.TRUE)) {
            return LiteralExpr.ofBoolean(true);
        }
        if (match(TokenType.FALSE)) {
            return LiteralExpr.ofBoolean(false);
        }
        if (match(TokenType.NULL)) {
            return LiteralExpr.ofNull();
        }
        if (check(TokenType.INTEGER_LITERAL)) {
            Token t = advance();
            return new LiteralExpr(t.getValue(), PrimitiveSourceType.INT, loc);
        }
        if (check(TokenType.LONG_LITERAL)) {
            Token t = advance();
            return new LiteralExpr(t.getValue(), PrimitiveSourceType.LONG, loc);
        }
        if (check(TokenType.FLOAT_LITERAL)) {
            Token t = advance();
            return new LiteralExpr(t.getValue(), PrimitiveSourceType.FLOAT, loc);
        }
        if (check(TokenType.DOUBLE_LITERAL)) {
            Token t = advance();
            return new LiteralExpr(t.getValue(), PrimitiveSourceType.DOUBLE, loc);
        }
        if (check(TokenType.CHAR_LITERAL)) {
            Token t = advance();
            return new LiteralExpr(t.getValue(), PrimitiveSourceType.CHAR, loc);
        }
        if (check(TokenType.STRING_LITERAL)) {
            Token t = advance();
            return new LiteralExpr(t.getValue(), ReferenceSourceType.STRING, loc);
        }

        if (match(TokenType.THIS)) {
            return new ThisExpr(ReferenceSourceType.OBJECT, loc);
        }
        if (match(TokenType.SUPER)) {
            return new SuperExpr(ReferenceSourceType.OBJECT, loc);
        }

        if (check(TokenType.NEW)) {
            return parseNewExpression();
        }

        if (check(TokenType.LPAREN)) {
            if (isLambdaExpression()) {
                return parseLambda();
            }
            advance();
            Expression expr = parseExpression();
            consume(TokenType.RPAREN, "Expected ')'");
            return expr;
        }

        if (check(TokenType.LBRACE)) {
            return parseArrayInit(new ArraySourceType(ReferenceSourceType.OBJECT));
        }

        if (current.isPrimitiveType()) {
            SourceType type = parsePrimitiveType();
            if (match(TokenType.DOT)) {
                consume(TokenType.CLASS, "Expected 'class'");
                return new ClassExpr(type, loc);
            }
            throw error("Unexpected primitive type");
        }

        if (check(TokenType.IDENTIFIER)) {
            String name = advance().getText();

            if (check(TokenType.ARROW)) {
                return parseLambdaWithSingleParam(name, loc);
            }

            if (check(TokenType.LPAREN)) {
                return parseMethodCall(null, name, loc);
            }

            if (check(TokenType.DOUBLE_COLON)) {
                advance();
                return parseMethodReferenceFromType(name, loc);
            }

            return new VarRefExpr(name, lookupVariable(name));
        }

        throw error("Expected expression");
    }

    private boolean isLambdaExpression() {
        if (!check(TokenType.LPAREN)) return false;

        Lexer tempLexer = new Lexer(source.substring(lexer.currentPosition().getOffset() - current.getText().length()));
        Token t = tempLexer.nextToken();

        int depth = 1;
        while (depth > 0 && t.getType() != TokenType.EOF) {
            t = tempLexer.nextToken();
            if (t.getType() == TokenType.LPAREN) depth++;
            if (t.getType() == TokenType.RPAREN) depth--;
        }

        t = tempLexer.nextToken();
        return t.getType() == TokenType.ARROW;
    }

    private Expression parseLambda() {
        SourceLocation loc = currentLocation();
        consume(TokenType.LPAREN, "Expected '('");

        List<LambdaParameter> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                SourceType type;
                String name;

                if (check(TokenType.IDENTIFIER) && (checkNext(TokenType.COMMA) || checkNext(TokenType.RPAREN))) {
                    name = advance().getText();
                    params.add(LambdaParameter.implicit(name, ReferenceSourceType.OBJECT));
                } else {
                    type = parseTypeReference();
                    name = consume(TokenType.IDENTIFIER, "Expected parameter name").getText();
                    params.add(LambdaParameter.explicit(type, name));
                }
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')'");
        consume(TokenType.ARROW, "Expected '->'");

        if (check(TokenType.LBRACE)) {
            BlockStmt body = parseBlock();
            return new LambdaExpr(params, body, ReferenceSourceType.OBJECT, loc);
        } else {
            Expression body = parseExpression();
            return new LambdaExpr(params, body, ReferenceSourceType.OBJECT, loc);
        }
    }

    private Expression parseLambdaWithSingleParam(String paramName, SourceLocation loc) {
        consume(TokenType.ARROW, "Expected '->'");
        List<LambdaParameter> params = List.of(LambdaParameter.implicit(paramName, ReferenceSourceType.OBJECT));

        if (check(TokenType.LBRACE)) {
            BlockStmt body = parseBlock();
            return new LambdaExpr(params, body, ReferenceSourceType.OBJECT, loc);
        } else {
            Expression body = parseExpression();
            return new LambdaExpr(params, body, ReferenceSourceType.OBJECT, loc);
        }
    }

    private Expression parseMethodReference(Expression qualifier, SourceLocation loc) {
        String methodName;
        if (match(TokenType.NEW)) {
            methodName = "new";
        } else {
            methodName = consume(TokenType.IDENTIFIER, "Expected method name").getText();
        }

        String ownerClass = "java/lang/Object";
        if (qualifier instanceof VarRefExpr) {
            ownerClass = ((VarRefExpr) qualifier).getName();
        }

        MethodRefKind kind = "new".equals(methodName) ? MethodRefKind.CONSTRUCTOR : MethodRefKind.BOUND;
        return new MethodRefExpr(qualifier, methodName, ownerClass, kind, ReferenceSourceType.OBJECT, loc);
    }

    private Expression parseMethodReferenceFromType(String typeName, SourceLocation loc) {
        String methodName;
        if (match(TokenType.NEW)) {
            methodName = "new";
        } else {
            methodName = consume(TokenType.IDENTIFIER, "Expected method name").getText();
        }

        MethodRefKind kind = "new".equals(methodName) ? MethodRefKind.CONSTRUCTOR : MethodRefKind.STATIC;
        return new MethodRefExpr(null, methodName, typeName, kind, ReferenceSourceType.OBJECT, loc);
    }

    private Expression parseNewExpression() {
        SourceLocation loc = currentLocation();
        consume(TokenType.NEW, "Expected 'new'");

        SourceType type = parseNewType();

        if (check(TokenType.LBRACKET)) {
            return parseNewArray(type, loc);
        }

        if (type instanceof ArraySourceType && check(TokenType.LBRACE)) {
            ArraySourceType arrayType = (ArraySourceType) type;
            ArrayInitExpr initializer = parseArrayInit(arrayType);
            return new NewArrayExpr(arrayType.getElementType(), List.of(), initializer, null, loc);
        }

        consume(TokenType.LPAREN, "Expected '(' after type");
        List<Expression> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after arguments");

        String className = typeToClassName(type);

        if (check(TokenType.LBRACE)) {
            parseAnonymousClassBody();
        }

        return new NewExpr(className, args, type, loc);
    }

    private SourceType parseNewType() {
        SourceType type;
        if (current.isPrimitiveType()) {
            type = parsePrimitiveType();
        } else {
            type = parseReferenceType();
        }

        while (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET)) {
            advance();
            advance();
            type = new ArraySourceType(type);
        }

        return type;
    }

    private String typeToClassName(SourceType type) {
        if (type instanceof ReferenceSourceType) {
            return ((ReferenceSourceType) type).getInternalName();
        }
        if (type instanceof GenericSourceType) {
            return ((GenericSourceType) type).getRawType().getInternalName();
        }
        return type.toJavaSource();
    }

    private Expression parseNewArray(SourceType elementType, SourceLocation loc) {
        List<Expression> dimensions = new ArrayList<>();

        while (match(TokenType.LBRACKET)) {
            if (check(TokenType.RBRACKET)) {
                advance();
                dimensions.add(null);
            } else {
                dimensions.add(parseExpression());
                consume(TokenType.RBRACKET, "Expected ']'");
            }
        }

        ArrayInitExpr initializer = null;
        if (check(TokenType.LBRACE)) {
            initializer = parseArrayInit(new ArraySourceType(elementType));
        }

        return new NewArrayExpr(elementType, dimensions, initializer, null, loc);
    }

    private ArrayInitExpr parseArrayInit(SourceType arrayType) {
        SourceLocation loc = currentLocation();
        consume(TokenType.LBRACE, "Expected '{'");

        SourceType elementType = arrayType instanceof ArraySourceType
            ? ((ArraySourceType) arrayType).getElementType()
            : ReferenceSourceType.OBJECT;

        List<Expression> elements = new ArrayList<>();
        if (!check(TokenType.RBRACE)) {
            do {
                if (check(TokenType.RBRACE)) break;
                if (check(TokenType.LBRACE)) {
                    elements.add(parseArrayInit(elementType instanceof ArraySourceType ? elementType : new ArraySourceType(elementType)));
                } else {
                    elements.add(parseExpression());
                }
            } while (match(TokenType.COMMA));
        }

        consume(TokenType.RBRACE, "Expected '}'");
        return new ArrayInitExpr(elements, arrayType, loc);
    }

    private void parseAnonymousClassBody() {
        consume(TokenType.LBRACE, "Expected '{'");
        int depth = 1;
        while (!isAtEnd() && depth > 0) {
            if (check(TokenType.LBRACE)) depth++;
            if (check(TokenType.RBRACE)) depth--;
            advance();
        }
    }

    private Expression parseInnerClassCreation(Expression outer, SourceLocation loc) {
        SourceType type = parseTypeReference();
        consume(TokenType.LPAREN, "Expected '('");
        List<Expression> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')'");

        String className = typeToClassName(type);

        if (check(TokenType.LBRACE)) {
            parseAnonymousClassBody();
        }

        return new NewExpr(outer, className, args, type, loc);
    }

    private MethodCallExpr parseMethodCall(Expression receiver, String name, SourceLocation loc) {
        consume(TokenType.LPAREN, "Expected '('");
        List<Expression> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')'");
        return new MethodCallExpr(receiver, name, "", args, false, ReferenceSourceType.OBJECT, loc);
    }

    private Expression parseInfixExpression(Expression left, int nextMinPrec) {
        SourceLocation loc = currentLocation();
        Token op = advance();

        if (op.getType() == TokenType.QUESTION) {
            Expression thenExpr = parseExpressionWithPrecedence(Precedence.ASSIGNMENT.getLevel());
            consume(TokenType.COLON, "Expected ':' in ternary");
            Expression elseExpr = parseExpressionWithPrecedence(Precedence.TERNARY.getLevel());
            SourceType resultType = inferTernaryResultType(thenExpr, elseExpr);
            return new TernaryExpr(left, thenExpr, elseExpr, resultType, loc);
        }

        if (op.getType() == TokenType.INSTANCEOF) {
            SourceType type = parseTypeReference();
            // Optional Java 16 pattern binding: `x instanceof T t`. Register the binding in scope so
            // later references resolve to T (flow scoping is approximated as method scope here).
            String binding = null;
            if (check(TokenType.IDENTIFIER)) {
                binding = consume(TokenType.IDENTIFIER, "Expected pattern variable name").getText();
                defineVariable(binding, type);
            }
            return new InstanceOfExpr(left, type, binding, loc);
        }

        Expression right = parseExpressionWithPrecedence(nextMinPrec);
        BinaryOperator binaryOp = tokenToBinaryOperator(op.getType());

        if (binaryOp != null) {
            SourceType resultType = inferBinaryResultType(binaryOp, left, right);
            return new BinaryExpr(binaryOp, left, right, resultType, loc);
        }

        throw error("Unknown binary operator: " + op.getText());
    }

    private BinaryOperator tokenToBinaryOperator(TokenType type) {
        switch (type) {
            case PLUS: return BinaryOperator.ADD;
            case MINUS: return BinaryOperator.SUB;
            case STAR: return BinaryOperator.MUL;
            case SLASH: return BinaryOperator.DIV;
            case PERCENT: return BinaryOperator.MOD;
            case AMP: return BinaryOperator.BAND;
            case PIPE: return BinaryOperator.BOR;
            case CARET: return BinaryOperator.BXOR;
            case LT_LT: return BinaryOperator.SHL;
            case GT_GT: return BinaryOperator.SHR;
            case GT_GT_GT: return BinaryOperator.USHR;
            case AMP_AMP: return BinaryOperator.AND;
            case PIPE_PIPE: return BinaryOperator.OR;
            case EQ_EQ: return BinaryOperator.EQ;
            case BANG_EQ: return BinaryOperator.NE;
            case LT: return BinaryOperator.LT;
            case GT: return BinaryOperator.GT;
            case LT_EQ: return BinaryOperator.LE;
            case GT_EQ: return BinaryOperator.GE;
            case EQ: return BinaryOperator.ASSIGN;
            case PLUS_EQ: return BinaryOperator.ADD_ASSIGN;
            case MINUS_EQ: return BinaryOperator.SUB_ASSIGN;
            case STAR_EQ: return BinaryOperator.MUL_ASSIGN;
            case SLASH_EQ: return BinaryOperator.DIV_ASSIGN;
            case PERCENT_EQ: return BinaryOperator.MOD_ASSIGN;
            case AMP_EQ: return BinaryOperator.BAND_ASSIGN;
            case PIPE_EQ: return BinaryOperator.BOR_ASSIGN;
            case CARET_EQ: return BinaryOperator.BXOR_ASSIGN;
            case LT_LT_EQ: return BinaryOperator.SHL_ASSIGN;
            case GT_GT_EQ: return BinaryOperator.SHR_ASSIGN;
            case GT_GT_GT_EQ: return BinaryOperator.USHR_ASSIGN;
            default: return null;
        }
    }

    private SourceType inferBinaryResultType(BinaryOperator op, Expression left, Expression right) {
        if (op.isComparison() || op == BinaryOperator.AND || op == BinaryOperator.OR) {
            return PrimitiveSourceType.BOOLEAN;
        }
        if (op.isAssignment()) {
            return left.getType();
        }
        SourceType leftType = left.getType();
        SourceType rightType = right.getType();
        if (op == BinaryOperator.ADD) {
            if (isString(leftType) || isString(rightType)) {
                return ReferenceSourceType.STRING;
            }
        }
        if (leftType instanceof PrimitiveSourceType && rightType instanceof PrimitiveSourceType) {
            PrimitiveSourceType lp = (PrimitiveSourceType) leftType;
            PrimitiveSourceType rp = (PrimitiveSourceType) rightType;
            return promoteNumericTypes(lp, rp);
        }
        if (leftType instanceof PrimitiveSourceType) {
            return leftType;
        }
        if (rightType instanceof PrimitiveSourceType) {
            return rightType;
        }
        return leftType;
    }

    private SourceType inferTernaryResultType(Expression thenExpr, Expression elseExpr) {
        SourceType thenType = thenExpr.getType();
        SourceType elseType = elseExpr.getType();
        if (thenType == null) {
            return elseType != null ? elseType : ReferenceSourceType.OBJECT;
        }
        if (elseType == null) {
            return thenType;
        }
        if (thenType.equals(elseType)) {
            return thenType;
        }
        if (thenType instanceof PrimitiveSourceType && elseType instanceof PrimitiveSourceType) {
            return promoteNumericTypes((PrimitiveSourceType) thenType, (PrimitiveSourceType) elseType);
        }
        if (thenType instanceof PrimitiveSourceType) {
            return thenType;
        }
        if (elseType instanceof PrimitiveSourceType) {
            return elseType;
        }
        return thenType;
    }

    private PrimitiveSourceType promoteNumericTypes(PrimitiveSourceType left, PrimitiveSourceType right) {
        if (left == PrimitiveSourceType.DOUBLE || right == PrimitiveSourceType.DOUBLE) {
            return PrimitiveSourceType.DOUBLE;
        }
        if (left == PrimitiveSourceType.FLOAT || right == PrimitiveSourceType.FLOAT) {
            return PrimitiveSourceType.FLOAT;
        }
        if (left == PrimitiveSourceType.LONG || right == PrimitiveSourceType.LONG) {
            return PrimitiveSourceType.LONG;
        }
        return PrimitiveSourceType.INT;
    }

    private boolean isString(SourceType type) {
        if (type instanceof ReferenceSourceType) {
            ReferenceSourceType ref = (ReferenceSourceType) type;
            String name = ref.getInternalName();
            return "java/lang/String".equals(name) || "String".equals(name) || "java.lang.String".equals(name);
        }
        return false;
    }

    private SourceType parseTypeReference() {
        SourceType type;

        if (current.isPrimitiveType()) {
            type = parsePrimitiveType();
        } else if (check(TokenType.VOID)) {
            advance();
            type = VoidSourceType.INSTANCE;
        } else {
            type = parseReferenceType();
        }

        while (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET)) {
            advance();
            advance();
            type = new ArraySourceType(type);
        }

        return type;
    }

    private SourceType parsePrimitiveType() {
        Token t = advance();
        switch (t.getType()) {
            case BOOLEAN: return PrimitiveSourceType.BOOLEAN;
            case BYTE: return PrimitiveSourceType.BYTE;
            case CHAR: return PrimitiveSourceType.CHAR;
            case SHORT: return PrimitiveSourceType.SHORT;
            case INT: return PrimitiveSourceType.INT;
            case LONG: return PrimitiveSourceType.LONG;
            case FLOAT: return PrimitiveSourceType.FLOAT;
            case DOUBLE: return PrimitiveSourceType.DOUBLE;
            case VOID: return VoidSourceType.INSTANCE;
            default:
                throw error("Expected primitive type");
        }
    }

    private SourceType parseReferenceType() {
        StringBuilder sb = new StringBuilder();
        sb.append(consume(TokenType.IDENTIFIER, "Expected type name").getText());

        while (check(TokenType.DOT) && checkNext(TokenType.IDENTIFIER)) {
            advance();
            sb.append("/").append(advance().getText());
        }

        String name = sb.toString();

        if (!name.contains("/")) {
            String resolved = JAVA_LANG_TYPES.get(name);
            if (resolved != null) {
                name = resolved;
            }
        }

        if (match(TokenType.LT)) {
            List<SourceType> typeArgs = parseTypeArguments();
            return new GenericSourceType(name, typeArgs);
        }

        return new ReferenceSourceType(name);
    }

    private List<SourceType> parseTypeArguments() {
        List<SourceType> typeArgs = new ArrayList<>();

        if (check(TokenType.GT)) {
            advance();
            return typeArgs;
        }

        do {
            if (check(TokenType.QUESTION)) {
                advance();
                if (match(TokenType.EXTENDS)) {
                    SourceType bound = parseTypeReference();
                    typeArgs.add(WildcardSourceType.extendsType(bound));
                } else if (match(TokenType.SUPER)) {
                    SourceType bound = parseTypeReference();
                    typeArgs.add(WildcardSourceType.superType(bound));
                } else {
                    typeArgs.add(WildcardSourceType.unbounded());
                }
            } else {
                typeArgs.add(parseTypeReference());
            }
        } while (match(TokenType.COMMA));

        consume(TokenType.GT, "Expected '>' after type arguments");
        return typeArgs;
    }

    private String parseQualifiedName() {
        StringBuilder sb = new StringBuilder();
        sb.append(consume(TokenType.IDENTIFIER, "Expected identifier").getText());
        while (check(TokenType.DOT) && checkNext(TokenType.IDENTIFIER)) {
            advance();
            sb.append(".").append(consume(TokenType.IDENTIFIER, "Expected identifier").getText());
        }
        return sb.toString();
    }

    private Token advance() {
        Token previous = current;
        current = lexer.nextToken();
        return previous;
    }

    private Token consume(TokenType expected, String message) {
        if (check(expected)) {
            return advance();
        }
        throw error(message + " (got " + current.getType() + ")");
    }

    private boolean check(TokenType type) {
        return current.getType() == type;
    }

    private boolean checkNext(TokenType type) {
        return lexer.peek().getType() == type;
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean isAtEnd() {
        return current.getType() == TokenType.EOF;
    }

    private SourceLocation currentLocation() {
        return SourceLocation.fromLine(current.getLine());
    }

    private ParseException error(String message) {
        ParseException ex = new ParseException(message, current, source);
        errorListener.onError(ex);
        return ex;
    }
}
