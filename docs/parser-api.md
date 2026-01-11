[<- Back to README](../README.md) | [AST Guide](ast-guide.md) | [Class Files ->](class-files.md)

# Java Source Parser API

Parse Java source code into the AST for analysis and transformation.

## Quick Start

```java
// Parse a full compilation unit
JavaParser parser = JavaParser.create();
CompilationUnit cu = parser.parse("public class Foo { void bar() {} }");

// Access the parsed structure
ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
MethodDecl method = cls.getMethods().get(0);

// Parse individual fragments
Expression expr = parser.parseExpression("x + y * 2");
Statement stmt = parser.parseStatement("if (x > 0) return x;");
SourceType type = parser.parseType("List<String>");
```

## API Reference

### JavaParser

The main facade for parsing Java source code.

```java
public final class JavaParser {
    // Create a parser with default (throwing) error listener
    static JavaParser create();

    // Create with custom error handling
    static JavaParser withErrorListener(ParseErrorListener listener);

    // Parse full compilation units
    CompilationUnit parse(String source);
    CompilationUnit parse(Reader reader) throws IOException;
    CompilationUnit parseFile(Path path) throws IOException;
    CompilationUnit parseFile(File file) throws IOException;

    // Parse fragments
    Expression parseExpression(String source);
    Statement parseStatement(String source);
    BlockStmt parseBlock(String source);
    SourceType parseType(String source);
}
```

### Error Handling

```java
// Collect all errors instead of throwing
List<ParseException> errors = new ArrayList<>();
JavaParser parser = JavaParser.withErrorListener(ParseErrorListener.collecting(errors));

// Log errors
JavaParser parser = JavaParser.withErrorListener(
    ParseErrorListener.logging(System.err::println));

// Custom handling
JavaParser parser = JavaParser.withErrorListener(error -> {
    logger.warn("Parse error at {}:{}: {}",
        error.getLine(), error.getColumn(), error.getMessage());
});
```

### ParseException

Thrown when parsing fails. Includes location and formatted error message.

```java
try {
    parser.parse("class { }");  // Missing class name
} catch (ParseException e) {
    int line = e.getLine();
    int column = e.getColumn();
    String message = e.getMessage();
    String formatted = e.getFormattedMessage();
    // formatted includes source context with caret:
    // ParseException: Expected identifier
    //   at 1:7
    //    |
    //  1 | class { }
    //    |       ^
}
```

## Declaration Nodes

### CompilationUnit

Root node for a parsed Java file.

```java
CompilationUnit cu = parser.parse(source);

String pkg = cu.getPackageName();                    // nullable
NodeList<ImportDecl> imports = cu.getImports();
NodeList<TypeDecl> types = cu.getTypes();

// Convenience methods
List<ClassDecl> classes = cu.getClasses();
List<InterfaceDecl> interfaces = cu.getInterfaces();
List<EnumDecl> enums = cu.getEnums();
TypeDecl type = cu.getType("Foo");
```

### ClassDecl

```java
ClassDecl cls = (ClassDecl) cu.getTypes().get(0);

String name = cls.getName();
Set<Modifier> mods = cls.getModifiers();
SourceType superclass = cls.getSuperclass();         // nullable
NodeList<SourceType> interfaces = cls.getInterfaces();
NodeList<SourceType> typeParams = cls.getTypeParameters();
NodeList<FieldDecl> fields = cls.getFields();
NodeList<MethodDecl> methods = cls.getMethods();
NodeList<ConstructorDecl> constructors = cls.getConstructors();
NodeList<TypeDecl> innerTypes = cls.getInnerTypes();

// Queries
boolean isAbstract = cls.isAbstract();
boolean isFinal = cls.isFinal();
MethodDecl method = cls.getMethod("bar");
FieldDecl field = cls.getField("x");
```

### InterfaceDecl

```java
InterfaceDecl iface = (InterfaceDecl) cu.getTypes().get(0);

String name = iface.getName();
NodeList<SourceType> extended = iface.getExtendedInterfaces();
NodeList<MethodDecl> methods = iface.getMethods();
NodeList<FieldDecl> constants = iface.getFields();  // implicitly public static final
```

### EnumDecl

```java
EnumDecl enm = (EnumDecl) cu.getTypes().get(0);

NodeList<EnumConstantDecl> constants = enm.getConstants();
NodeList<MethodDecl> methods = enm.getMethods();
NodeList<FieldDecl> fields = enm.getFields();
NodeList<ConstructorDecl> constructors = enm.getConstructors();
```

### MethodDecl

```java
MethodDecl method = cls.getMethods().get(0);

String name = method.getName();
SourceType returnType = method.getReturnType();
NodeList<ParameterDecl> params = method.getParameters();
NodeList<SourceType> typeParams = method.getTypeParameters();
NodeList<SourceType> throwsTypes = method.getThrowsTypes();
BlockStmt body = method.getBody();                   // null for abstract

boolean isAbstract = method.isAbstract();
boolean isStatic = method.isStatic();
boolean isDefault = method.isDefault();              // interface default
String signature = method.getSignature();            // name(Type1,Type2)
```

### FieldDecl

```java
FieldDecl field = cls.getFields().get(0);

String name = field.getName();
SourceType type = field.getType();
Expression initializer = field.getInitializer();     // nullable

boolean isFinal = field.isFinal();
boolean isStatic = field.isStatic();
```

### ParameterDecl

```java
ParameterDecl param = method.getParameters().get(0);

String name = param.getName();
SourceType type = param.getType();
boolean isFinal = param.isFinal();
boolean isVarArgs = param.isVarArgs();
NodeList<AnnotationExpr> annotations = param.getAnnotations();
```

### AnnotationExpr

```java
AnnotationExpr ann = cls.getAnnotations().get(0);

SourceType type = ann.getAnnotationType();
NodeList<AnnotationValue> values = ann.getValues();

boolean isMarker = ann.isMarker();           // @Ann
boolean isSingleValue = ann.isSingleValue(); // @Ann(value)
boolean isNormal = ann.isNormal();           // @Ann(key=value)
```

### ImportDecl

```java
ImportDecl imp = cu.getImports().get(0);

String name = imp.getName();             // e.g., "java.util.List"
boolean isStatic = imp.isStatic();
boolean isWildcard = imp.isWildcard();   // "java.util.*"
```

## Expression Parsing

The parser supports all Java 11 expression forms with correct operator precedence.

```java
// Literals
parser.parseExpression("42");           // INTEGER_LITERAL
parser.parseExpression("3.14");         // DOUBLE_LITERAL
parser.parseExpression("\"hello\"");    // STRING_LITERAL
parser.parseExpression("'a'");          // CHAR_LITERAL
parser.parseExpression("true");         // BOOLEAN_LITERAL
parser.parseExpression("null");         // NULL_LITERAL

// Binary operations (correct precedence)
parser.parseExpression("a + b * c");    // a + (b * c)
parser.parseExpression("a || b && c");  // a || (b && c)

// Unary operations
parser.parseExpression("-x");
parser.parseExpression("!flag");
parser.parseExpression("++i");
parser.parseExpression("i++");

// Method calls
parser.parseExpression("foo()");
parser.parseExpression("obj.method(a, b)");

// Object creation
parser.parseExpression("new Foo()");
parser.parseExpression("new int[10]");
parser.parseExpression("new int[] {1, 2, 3}");

// Lambdas and method references
parser.parseExpression("x -> x * 2");
parser.parseExpression("(a, b) -> a + b");
parser.parseExpression("String::valueOf");
parser.parseExpression("System.out::println");

// Ternary and instanceof
parser.parseExpression("x > 0 ? x : -x");
parser.parseExpression("obj instanceof String");

// Casts and array access
parser.parseExpression("(String) obj");
parser.parseExpression("arr[i]");
```

## Statement Parsing

```java
// Control flow
parser.parseStatement("if (x) y();");
parser.parseStatement("if (x) y(); else z();");
parser.parseStatement("while (x) y();");
parser.parseStatement("do x(); while (y);");
parser.parseStatement("for (int i = 0; i < 10; i++) x();");
parser.parseStatement("for (int x : list) foo();");

// Switch
parser.parseStatement("switch (x) { case 1: break; default: return; }");

// Try-catch
parser.parseStatement("try { x(); } catch (Exception e) { handle(); }");
parser.parseStatement("try { x(); } finally { cleanup(); }");
parser.parseStatement("try (var r = open()) { use(r); }");

// Other
parser.parseStatement("return x;");
parser.parseStatement("throw e;");
parser.parseStatement("break;");
parser.parseStatement("continue;");
parser.parseStatement("synchronized (lock) { work(); }");
parser.parseStatement("int x = 42;");
```

## Type Parsing

```java
// Primitives
parser.parseType("int");
parser.parseType("boolean");
parser.parseType("void");

// References
parser.parseType("String");
parser.parseType("java.util.List");

// Arrays
parser.parseType("int[]");
parser.parseType("String[][]");

// Generics
parser.parseType("List<String>");
parser.parseType("Map<String, Integer>");
parser.parseType("Class<?>");
parser.parseType("List<? extends Number>");
parser.parseType("Consumer<? super T>");
```

## Integration with SourceEmitter

Parse and re-emit source code:

```java
JavaParser parser = JavaParser.create();
CompilationUnit cu = parser.parse(source);

// Modify the AST
ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
cls.addField(new FieldDecl("newField", PrimitiveSourceType.INT, null));

// Emit back to source
String output = SourceEmitter.emit(cu);
```

## Java 11 Features

| Feature | Support |
|---------|---------|
| Lambdas | Full |
| Method references | Full |
| Try-with-resources | Full |
| Diamond operator | Full |
| Generics with wildcards | Full |
| Annotations | Full |
| `var` keyword | Full |
| Multi-catch | Full |
| Underscores in numbers | Full |
| Binary literals | Full |

## Package Structure

```
com.tonic.analysis.source.parser/
├── JavaParser.java           # Main facade
├── Parser.java               # Recursive descent parser
├── Lexer.java                # Tokenizer
├── Token.java                # Token with location
├── TokenType.java            # Token type enum
├── Precedence.java           # Operator precedence
├── ParseException.java       # Error with location
├── ParseErrorListener.java   # Error callback
└── SourcePosition.java       # Line/column tracking

com.tonic.analysis.source.ast.decl/
├── CompilationUnit.java      # Root node
├── ImportDecl.java           # Import statement
├── TypeDecl.java             # Base interface
├── ClassDecl.java            # Class declaration
├── InterfaceDecl.java        # Interface declaration
├── EnumDecl.java             # Enum declaration
├── EnumConstantDecl.java     # Enum constant
├── MethodDecl.java           # Method declaration
├── ConstructorDecl.java      # Constructor declaration
├── FieldDecl.java            # Field declaration
├── ParameterDecl.java        # Parameter declaration
├── AnnotationExpr.java       # @Annotation
└── Modifier.java             # Access modifiers
```
