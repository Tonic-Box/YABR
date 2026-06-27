package com.tonic.analysis.source;

import com.tonic.analysis.source.ast.decl.*;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SourceToBytecodeIntegrationTest {

    private JavaParser parser;
    private ClassPool pool;
    private int classCounter = 0;

    @BeforeEach
    void setUp() {
        parser = JavaParser.create();
        pool = TestUtils.emptyPool();
        TestUtils.resetSSACounters();
    }

    private String uniqueClassName() {
        return "com/test/Gen" + (classCounter++);
    }

    private ClassFile createClassWithMethod(String className, String methodName, String descriptor,
                                            boolean isStatic) throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass(className, classAccess);
        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        if (!isStatic) {
            methodAccess = new AccessBuilder().setPublic().build();
        }
        cf.createNewMethodWithDescriptor(methodAccess, methodName, descriptor);
        return cf;
    }

    private Class<?> compileAndLoad(BlockStmt body, String methodName, String ownerClass,
                                    boolean isStatic, List<SourceType> params, SourceType returnType)
            throws Exception {

        String descriptor = buildDescriptor(params, returnType);
        ClassFile cf = createClassWithMethod(ownerClass, methodName, descriptor, isStatic);
        MethodEntry method = findMethod(cf, methodName);

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        IRMethod ir = lowerer.lower(body, methodName, ownerClass, isStatic, params, returnType);

        SSA ssa = new SSA(cf.getConstPool());
        ssa.lower(ir, method);

        return TestUtils.loadAndVerify(cf);
    }

    private Class<?> compileAndLoad(MethodDecl methodDecl, String ownerClass) throws Exception {
        List<SourceType> params = new ArrayList<>();
        for (ParameterDecl p : methodDecl.getParameters()) {
            params.add(p.getType());
        }
        SourceType returnType = methodDecl.getReturnType();
        String methodName = methodDecl.getName();
        boolean isStatic = methodDecl.isStatic();

        String descriptor = buildDescriptor(params, returnType);
        ClassFile cf = createClassWithMethod(ownerClass, methodName, descriptor, isStatic);
        MethodEntry method = findMethod(cf, methodName);

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        IRMethod ir = lowerer.lower(methodDecl, ownerClass);

        SSA ssa = new SSA(cf.getConstPool());
        ssa.lower(ir, method);

        return TestUtils.loadAndVerify(cf);
    }

    /**
     * Parses a full class declaration, materializes its fields and methods onto a fresh
     * ClassFile, lowers every method body (with the parsed class declaration available for
     * field resolution) and loads the result. Used to exercise unqualified own-class field
     * references, which the decompiler emits as bare names.
     */
    private Class<?> compileClassWithFields(String source, String ownerClass) throws Exception {
        CompilationUnit cu = parser.parse(source);
        ClassDecl cls = (ClassDecl) cu.getTypes().get(0);

        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass(ownerClass, classAccess);

        for (FieldDecl field : cls.getFields()) {
            int fieldAccess = field.isStatic()
                    ? new AccessBuilder().setPublic().setStatic().build()
                    : new AccessBuilder().setPublic().build();
            cf.createNewField(fieldAccess, field.getName(),
                    field.getType().toIRType().getDescriptor(), new ArrayList<>());
        }

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(cls);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(cf.getConstPool());

        for (MethodDecl method : cls.getMethods()) {
            if (method.getBody() == null) {
                continue;
            }
            List<SourceType> params = new ArrayList<>();
            for (ParameterDecl p : method.getParameters()) {
                params.add(p.getType());
            }
            int methodAccess = method.isStatic()
                    ? new AccessBuilder().setPublic().setStatic().build()
                    : new AccessBuilder().setPublic().build();
            cf.createNewMethodWithDescriptor(methodAccess, method.getName(),
                    buildDescriptor(params, method.getReturnType()));
            MethodEntry entry = findMethod(cf, method.getName());
            IRMethod ir = lowerer.lower(method, ownerClass);
            ssa.lower(ir, entry);
        }

        return TestUtils.loadAndVerify(cf);
    }

    private String buildDescriptor(List<SourceType> params, SourceType returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (SourceType p : params) {
            sb.append(p.toIRType().getDescriptor());
        }
        sb.append(")");
        sb.append(returnType.toIRType().getDescriptor());
        return sb.toString();
    }

    private static MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Method not found: " + name);
    }

    @Nested
    class BasicExpressionTests {

        @Test
        void returnsIntegerLiteral() throws Exception {
            String source = "class Test { static int test() { return 42; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), PrimitiveSourceType.INT);

            Method m = clazz.getMethod("test");
            assertEquals(42, (int) m.invoke(null));
        }

        @Test
        void returnsLongLiteral() throws Exception {
            String source = "class Test { static long test() { return 123456789012345L; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), PrimitiveSourceType.LONG);

            Method m = clazz.getMethod("test");
            assertEquals(123456789012345L, (long) m.invoke(null));
        }

        @Test
        void returnsDoubleLiteral() throws Exception {
            String source = "class Test { static double test() { return 3.14159; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), PrimitiveSourceType.DOUBLE);

            Method m = clazz.getMethod("test");
            assertEquals(3.14159, (double) m.invoke(null), 0.00001);
        }

        @Test
        void returnsBooleanTrue() throws Exception {
            String source = "class Test { static boolean test() { return true; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), PrimitiveSourceType.BOOLEAN);

            Method m = clazz.getMethod("test");
            assertTrue((boolean) m.invoke(null));
        }

        @Test
        void returnsBooleanFalse() throws Exception {
            String source = "class Test { static boolean test() { return false; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), PrimitiveSourceType.BOOLEAN);

            Method m = clazz.getMethod("test");
            assertFalse((boolean) m.invoke(null));
        }
    }

    @Nested
    class ArithmeticTests {

        @Test
        void addsIntegers() throws Exception {
            String source = "class Test { static int add(int a, int b) { return a + b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("add", int.class, int.class);
            assertEquals(30, (int) m.invoke(null, 10, 20));
            assertEquals(0, (int) m.invoke(null, -5, 5));
            assertEquals(-10, (int) m.invoke(null, -3, -7));
        }

        @Test
        void subtractsIntegers() throws Exception {
            String source = "class Test { static int sub(int a, int b) { return a - b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("sub", int.class, int.class);
            assertEquals(5, (int) m.invoke(null, 15, 10));
            assertEquals(-10, (int) m.invoke(null, 5, 15));
        }

        @Test
        void multipliesIntegers() throws Exception {
            String source = "class Test { static int mul(int a, int b) { return a * b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("mul", int.class, int.class);
            assertEquals(200, (int) m.invoke(null, 10, 20));
            assertEquals(0, (int) m.invoke(null, 0, 100));
            assertEquals(-50, (int) m.invoke(null, -5, 10));
        }

        @Test
        void dividesIntegers() throws Exception {
            String source = "class Test { static int div(int a, int b) { return a / b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("div", int.class, int.class);
            assertEquals(5, (int) m.invoke(null, 20, 4));
            assertEquals(3, (int) m.invoke(null, 10, 3));
        }

        @Test
        void moduloIntegers() throws Exception {
            String source = "class Test { static int mod(int a, int b) { return a % b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("mod", int.class, int.class);
            assertEquals(1, (int) m.invoke(null, 10, 3));
            assertEquals(0, (int) m.invoke(null, 15, 5));
        }

        @Test
        void complexArithmeticExpression() throws Exception {
            String source = "class Test { static int calc(int a, int b, int c) { return a + b * c; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("calc", int.class, int.class, int.class);
            assertEquals(26, (int) m.invoke(null, 2, 3, 8));
        }

        @Test
        void negatesInteger() throws Exception {
            String source = "class Test { static int neg(int a) { return -a; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("neg", int.class);
            assertEquals(-42, (int) m.invoke(null, 42));
            assertEquals(42, (int) m.invoke(null, -42));
        }
    }

    @Nested
    class BitwiseOperationTests {

        @Test
        void bitwiseAnd() throws Exception {
            String source = "class Test { static int and(int a, int b) { return a & b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("and", int.class, int.class);
            assertEquals(0xFF00, (int) m.invoke(null, 0xFF00, 0xFFFF));
            assertEquals(0, (int) m.invoke(null, 0xAAAA, 0x5555));
        }

        @Test
        void bitwiseOr() throws Exception {
            String source = "class Test { static int or(int a, int b) { return a | b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("or", int.class, int.class);
            assertEquals(0xFFFF, (int) m.invoke(null, 0xFF00, 0x00FF));
        }

        @Test
        void bitwiseXor() throws Exception {
            String source = "class Test { static int xor(int a, int b) { return a ^ b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("xor", int.class, int.class);
            assertEquals(0, (int) m.invoke(null, 0xFF, 0xFF));
            assertEquals(0xFF, (int) m.invoke(null, 0xFF, 0x00));
        }

        @Test
        void bitwiseComplement() throws Exception {
            String source = "class Test { static int not(int a) { return ~a; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("not", int.class);
            assertEquals(-1, (int) m.invoke(null, 0));
            assertEquals(-43, (int) m.invoke(null, 42));
        }

        @Test
        void leftShift() throws Exception {
            String source = "class Test { static int shl(int a, int b) { return a << b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("shl", int.class, int.class);
            assertEquals(8, (int) m.invoke(null, 1, 3));
            assertEquals(80, (int) m.invoke(null, 5, 4));
        }

        @Test
        void rightShift() throws Exception {
            String source = "class Test { static int shr(int a, int b) { return a >> b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("shr", int.class, int.class);
            assertEquals(2, (int) m.invoke(null, 16, 3));
            assertEquals(-1, (int) m.invoke(null, -8, 3));
        }

        @Test
        void unsignedRightShift() throws Exception {
            String source = "class Test { static int ushr(int a, int b) { return a >>> b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("ushr", int.class, int.class);
            assertEquals(536870911, (int) m.invoke(null, -8, 3));
        }
    }

    @Nested
    class ComparisonTests {

        @Test
        void lessThan() throws Exception {
            String source = "class Test { static boolean lt(int a, int b) { return a < b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("lt", int.class, int.class);
            assertTrue((boolean) m.invoke(null, 5, 10));
            assertFalse((boolean) m.invoke(null, 10, 5));
            assertFalse((boolean) m.invoke(null, 5, 5));
        }

        @Test
        void lessThanOrEqual() throws Exception {
            String source = "class Test { static boolean le(int a, int b) { return a <= b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("le", int.class, int.class);
            assertTrue((boolean) m.invoke(null, 5, 10));
            assertFalse((boolean) m.invoke(null, 10, 5));
            assertTrue((boolean) m.invoke(null, 5, 5));
        }

        @Test
        void greaterThan() throws Exception {
            String source = "class Test { static boolean gt(int a, int b) { return a > b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("gt", int.class, int.class);
            assertFalse((boolean) m.invoke(null, 5, 10));
            assertTrue((boolean) m.invoke(null, 10, 5));
            assertFalse((boolean) m.invoke(null, 5, 5));
        }

        @Test
        void greaterThanOrEqual() throws Exception {
            String source = "class Test { static boolean ge(int a, int b) { return a >= b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("ge", int.class, int.class);
            assertFalse((boolean) m.invoke(null, 5, 10));
            assertTrue((boolean) m.invoke(null, 10, 5));
            assertTrue((boolean) m.invoke(null, 5, 5));
        }

        @Test
        void equalTo() throws Exception {
            String source = "class Test { static boolean eq(int a, int b) { return a == b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("eq", int.class, int.class);
            assertFalse((boolean) m.invoke(null, 5, 10));
            assertTrue((boolean) m.invoke(null, 5, 5));
        }

        @Test
        void notEqualTo() throws Exception {
            String source = "class Test { static boolean ne(int a, int b) { return a != b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("ne", int.class, int.class);
            assertTrue((boolean) m.invoke(null, 5, 10));
            assertFalse((boolean) m.invoke(null, 5, 5));
        }
    }

    @Nested
    class LogicalOperationTests {

        @Test
        void logicalNot() throws Exception {
            String source = "class Test { static boolean not(boolean a) { return !a; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("not", boolean.class);
            assertFalse((boolean) m.invoke(null, true));
            assertTrue((boolean) m.invoke(null, false));
        }
    }

    @Nested
    class LocalVariableTests {

        @Test
        void simpleVariableDeclaration() throws Exception {
            String source = "class Test { static int test() { int x = 42; return x; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), PrimitiveSourceType.INT);

            Method m = clazz.getMethod("test");
            assertEquals(42, (int) m.invoke(null));
        }

        @Test
        void multipleVariables() throws Exception {
            String source = "class Test { static int test() { int x = 10; int y = 20; return x + y; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), PrimitiveSourceType.INT);

            Method m = clazz.getMethod("test");
            assertEquals(30, (int) m.invoke(null));
        }

        @Test
        void variableReassignment() throws Exception {
            String source = "class Test { static int test() { int x = 10; x = 20; return x; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), PrimitiveSourceType.INT);

            Method m = clazz.getMethod("test");
            assertEquals(20, (int) m.invoke(null));
        }

        @Test
        void multipleVariablesInOneDeclaration() throws Exception {
            String source = "class Test { static int test() { int a = 3, b = 4, c = a + b; return c; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), PrimitiveSourceType.INT);

            assertEquals(7, (int) clazz.getMethod("test").invoke(null));
        }

        @Test
        void compoundAssignment() throws Exception {
            String source = "class Test { static int test() { int x = 10; x += 5; return x; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), PrimitiveSourceType.INT);

            Method m = clazz.getMethod("test");
            assertEquals(15, (int) m.invoke(null));
        }
    }

    @Nested
    class VoidMethodTests {

        @Test
        void emptyVoidMethod() throws Exception {
            String source = "class Test { static void test() { } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), VoidSourceType.INSTANCE);

            Method m = clazz.getMethod("test");
            m.invoke(null);
        }

        @Test
        void voidMethodWithExplicitReturn() throws Exception {
            String source = "class Test { static void test() { return; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);
            BlockStmt body = method.getBody();

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(body, "test", className, true, List.of(), VoidSourceType.INSTANCE);

            Method m = clazz.getMethod("test");
            m.invoke(null);
        }
    }

    @Nested
    class ParameterTests {

        @Test
        void singleParameter() throws Exception {
            String source = "class Test { static int identity(int x) { return x; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("identity", int.class);
            assertEquals(42, (int) m.invoke(null, 42));
            assertEquals(-100, (int) m.invoke(null, -100));
        }

        @Test
        void multipleParameters() throws Exception {
            String source = "class Test { static int sum3(int a, int b, int c) { return a + b + c; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("sum3", int.class, int.class, int.class);
            assertEquals(60, (int) m.invoke(null, 10, 20, 30));
        }

        @Test
        void longParameter() throws Exception {
            String source = "class Test { static long identity(long x) { return x; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("identity", long.class);
            assertEquals(123456789012345L, (long) m.invoke(null, 123456789012345L));
        }

        @Test
        void doubleParameter() throws Exception {
            String source = "class Test { static double identity(double x) { return x; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("identity", double.class);
            assertEquals(3.14159, (double) m.invoke(null, 3.14159), 0.00001);
        }

        @Test
        void mixedTypeParameters() throws Exception {
            String source = "class Test { static long mixed(int a, long b) { return a + b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("mixed", int.class, long.class);
            assertEquals(42L, (long) m.invoke(null, 10, 32L));
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void integerOverflow() throws Exception {
            String source = "class Test { static int overflow() { return 2147483647 + 1; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("overflow");
            assertEquals(Integer.MIN_VALUE, (int) m.invoke(null));
        }

        @Test
        void negativeZero() throws Exception {
            String source = "class Test { static int negzero() { return -0; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("negzero");
            assertEquals(0, (int) m.invoke(null));
        }

        @Test
        void deeplyNestedExpression() throws Exception {
            String source = "class Test { static int nested(int a) { return ((((a + 1) * 2) - 3) / 2); } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("nested", int.class);
            assertEquals(4, (int) m.invoke(null, 5));
        }

        @Test
        void longConstant() throws Exception {
            String source = "class Test { static long test() { return 9223372036854775807L; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test");
            assertEquals(Long.MAX_VALUE, (long) m.invoke(null));
        }
    }

    @Nested
    class ControlFlowTests {

        @Test
        void ifThenElseTrue() throws Exception {
            String source = "class Test { static int test(int x) { if (x > 0) { return 1; } else { return -1; } } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(1, (int) m.invoke(null, 5));
            assertEquals(-1, (int) m.invoke(null, -5));
            assertEquals(-1, (int) m.invoke(null, 0));
        }

        @Test
        void ifWithoutElse() throws Exception {
            String source = "class Test { static int test(int x) { if (x > 0) { return 1; } return 0; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(1, (int) m.invoke(null, 5));
            assertEquals(0, (int) m.invoke(null, -5));
        }

        @Test
        void ifElseChain() throws Exception {
            String source = "class Test { static int test(int x) { if (x > 10) { return 2; } else if (x > 0) { return 1; } else { return 0; } } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(2, (int) m.invoke(null, 15));
            assertEquals(1, (int) m.invoke(null, 5));
            assertEquals(0, (int) m.invoke(null, -5));
        }

        @Test
        void multipleReturnsInBranches() throws Exception {
            String source = "class Test { static int test(int x, int y) { if (x > y) { return x; } else { return y; } } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class, int.class);
            assertEquals(10, (int) m.invoke(null, 10, 5));
            assertEquals(10, (int) m.invoke(null, 5, 10));
        }

        @Test
        void nestedIf() throws Exception {
            String source = "class Test { static int test(int x, int y) { if (x > 0) { if (y > 0) { return 1; } else { return 2; } } else { return 3; } } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class, int.class);
            assertEquals(1, (int) m.invoke(null, 5, 5));
            assertEquals(2, (int) m.invoke(null, 5, -5));
            assertEquals(3, (int) m.invoke(null, -5, 5));
        }
    }

    @Nested
    class TernaryExpressionTests {

        @Test
        void simpleTernary() throws Exception {
            String source = "class Test { static int test(int x) { return x > 0 ? 1 : -1; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(1, (int) m.invoke(null, 5));
            assertEquals(-1, (int) m.invoke(null, -5));
        }

        @Test
        void ternaryWithExpressions() throws Exception {
            String source = "class Test { static int test(int a, int b) { return a > b ? a - b : b - a; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class, int.class);
            assertEquals(5, (int) m.invoke(null, 10, 5));
            assertEquals(5, (int) m.invoke(null, 5, 10));
        }

        @Test
        void nestedTernary() throws Exception {
            String source = "class Test { static int test(int x) { return x > 0 ? 1 : (x < 0 ? -1 : 0); } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(1, (int) m.invoke(null, 5));
            assertEquals(-1, (int) m.invoke(null, -5));
            assertEquals(0, (int) m.invoke(null, 0));
        }
    }

    @Nested
    class ShortCircuitTests {

        @Test
        void logicalAnd() throws Exception {
            String source = "class Test { static boolean test(boolean a, boolean b) { return a && b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", boolean.class, boolean.class);
            assertTrue((boolean) m.invoke(null, true, true));
            assertFalse((boolean) m.invoke(null, true, false));
            assertFalse((boolean) m.invoke(null, false, true));
            assertFalse((boolean) m.invoke(null, false, false));
        }

        @Test
        void logicalOr() throws Exception {
            String source = "class Test { static boolean test(boolean a, boolean b) { return a || b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", boolean.class, boolean.class);
            assertTrue((boolean) m.invoke(null, true, true));
            assertTrue((boolean) m.invoke(null, true, false));
            assertTrue((boolean) m.invoke(null, false, true));
            assertFalse((boolean) m.invoke(null, false, false));
        }
    }

    @Nested
    class BooleanComparisonTests {

        @Test
        void returnGreaterThan() throws Exception {
            String source = "class Test { static boolean test(int a, int b) { return a > b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class, int.class);
            assertTrue((boolean) m.invoke(null, 5, 3));
            assertFalse((boolean) m.invoke(null, 3, 5));
            assertFalse((boolean) m.invoke(null, 4, 4));
        }

        @Test
        void returnLessThan() throws Exception {
            String source = "class Test { static boolean test(int a, int b) { return a < b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class, int.class);
            assertFalse((boolean) m.invoke(null, 5, 3));
            assertTrue((boolean) m.invoke(null, 3, 5));
            assertFalse((boolean) m.invoke(null, 4, 4));
        }

        @Test
        void returnEquals() throws Exception {
            String source = "class Test { static boolean test(int a, int b) { return a == b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class, int.class);
            assertFalse((boolean) m.invoke(null, 5, 3));
            assertFalse((boolean) m.invoke(null, 3, 5));
            assertTrue((boolean) m.invoke(null, 4, 4));
        }

        @Test
        void returnNotEquals() throws Exception {
            String source = "class Test { static boolean test(int a, int b) { return a != b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class, int.class);
            assertTrue((boolean) m.invoke(null, 5, 3));
            assertTrue((boolean) m.invoke(null, 3, 5));
            assertFalse((boolean) m.invoke(null, 4, 4));
        }
    }

    @Nested
    class IncrementDecrementTests {

        @Test
        void preIncrement() throws Exception {
            String source = "class Test { static int test(int x) { return ++x; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(6, (int) m.invoke(null, 5));
        }

        @Test
        void postIncrement() throws Exception {
            String source = "class Test { static int test() { int x = 5; int y = x++; return y; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test");
            assertEquals(5, (int) m.invoke(null));
        }

        @Test
        void preDecrement() throws Exception {
            String source = "class Test { static int test(int x) { return --x; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(4, (int) m.invoke(null, 5));
        }

        @Test
        void postDecrement() throws Exception {
            String source = "class Test { static int test() { int x = 5; int y = x--; return y; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test");
            assertEquals(5, (int) m.invoke(null));
        }
    }

    @Nested
    class LoopTests {

        @Test
        void simpleWhileLoop() throws Exception {
            String source = "class Test { static int test(int n) { int count = 0; while (count < n) { count = count + 1; } return count; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(0, (int) m.invoke(null, 0));
            assertEquals(5, (int) m.invoke(null, 5));
        }

        @Test
        void whileLoopWithCounter() throws Exception {
            String source = "class Test { static int test(int n) { int sum = 0; int i = 0; while (i < n) { sum = sum + i; i = i + 1; } return sum; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(0, (int) m.invoke(null, 0));
            assertEquals(0, (int) m.invoke(null, 1));
            assertEquals(1, (int) m.invoke(null, 2));
            assertEquals(10, (int) m.invoke(null, 5));
        }

        @Test
        void forLoopSum() throws Exception {
            String source = "class Test { static int test(int n) { int sum = 0; for (int i = 1; i <= n; i = i + 1) { sum = sum + i; } return sum; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(0, (int) m.invoke(null, 0));
            assertEquals(1, (int) m.invoke(null, 1));
            assertEquals(3, (int) m.invoke(null, 2));
            assertEquals(15, (int) m.invoke(null, 5));
        }

        @Test
        void nestedLoops() throws Exception {
            String source = "class Test { static int test(int n, int m) { int count = 0; for (int i = 0; i < n; i = i + 1) { for (int j = 0; j < m; j = j + 1) { count = count + 1; } } return count; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class, int.class);
            assertEquals(0, (int) m.invoke(null, 0, 5));
            assertEquals(0, (int) m.invoke(null, 5, 0));
            assertEquals(6, (int) m.invoke(null, 2, 3));
            assertEquals(20, (int) m.invoke(null, 4, 5));
        }

        @Test
        void loopWithSwapDecompilesItsBody() throws Exception {
            // Regression: a loop whose body copies between locals (a = b; b = temp) must not have
            // its body dropped nor its variables conflated on the decompile round-trip. The phi for
            // the loop variable must be attributed to its own slot, not a slot that copies it.
            String source = "class Test { static int fib(int n) { "
                + "if (n <= 1) { return n; } int a = 0; int b = 1; "
                + "for (int i = 2; i <= n; i = i + 1) { int t = a + b; a = b; b = t; } return b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            ClassFile cf = createClassWithMethod(className, "fib", "(I)I", true);
            ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
            IRMethod ir = lowerer.lower(method, className);
            new SSA(cf.getConstPool()).lower(ir, findMethod(cf, "fib"));

            String decompiled = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);
            int forIdx = decompiled.indexOf("for ");
            assertTrue(forIdx >= 0, "should recover a for loop");
            int bodyOpen = decompiled.indexOf('{', forIdx);
            int bodyClose = decompiled.indexOf('}', bodyOpen);
            String loopBody = decompiled.substring(bodyOpen + 1, bodyClose);
            assertFalse(loopBody.trim().isEmpty(), "loop body must not be dropped: " + decompiled);
            assertTrue(loopBody.contains("+"), "loop body must keep the a + b computation: " + decompiled);
            assertTrue(decompiled.contains("<= 1"),
                "single-use constant must be inlined into the condition, not materialized to a slot: " + decompiled);
        }

        @Test
        void incrementDoesNotEmitDeadFieldLoad() throws Exception {
            // Regression: lowerUnary eagerly lowered the operand for every unary, then inc/dec
            // lowered it again in lowerIncDec, leaving a dead getstatic/load. `counter++` as a
            // statement must read the field exactly once.
            String source = "class Test { static int counter; static void tick() { counter++; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            String ownerClass = uniqueClassName();

            int classAccess = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass(ownerClass, classAccess);
            for (FieldDecl field : cls.getFields()) {
                cf.createNewField(new AccessBuilder().setPublic().setStatic().build(), field.getName(),
                        field.getType().toIRType().getDescriptor(), new ArrayList<>());
            }
            ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
            lowerer.setCurrentClassDecl(cls);
            lowerer.setImports(cu.getImports());
            cf.createNewMethodWithDescriptor(new AccessBuilder().setPublic().setStatic().build(),
                    "tick", "()V");
            MethodEntry entry = findMethod(cf, "tick");
            IRMethod ir = lowerer.lower(cls.getMethods().get(0), ownerClass);
            new SSA(cf.getConstPool()).lower(ir, entry);

            String code = com.tonic.analysis.CodePrinter.prettyPrintCode(
                entry.getCodeAttribute().getCode(), cf.getConstPool());
            int getstatics = code.split("getstatic", -1).length - 1;
            assertEquals(1, getstatics, "counter++ must read the field exactly once: " + code);
        }

        @Test
        void loopConditionWithComputedValueIsNotSpilledToSlot() throws Exception {
            // Regression: a single-use computed value feeding a comparison (i * i <= n, where the
            // second operand is the parameter n) must stay on the operand stack, not be spilled to a
            // scratch local. When spilled, the slot was reused for the body's n % i, and the
            // decompiler conflated the two, losing the multiplication and emitting an undeclared
            // variable in the condition (uncompilable). The result must round-trip to i * i <= n.
            String source = "class Test { static boolean isPrime(int n) { "
                + "for (int i = 2; i * i <= n; i = i + 1) { if (n % i == 0) { return false; } } return true; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            ClassFile cf = createClassWithMethod(className, "isPrime", "(I)Z", true);
            ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
            IRMethod ir = lowerer.lower(method, className);
            new SSA(cf.getConstPool()).lower(ir, findMethod(cf, "isPrime"));

            String decompiled = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);
            int forIdx = decompiled.indexOf("for ");
            assertTrue(forIdx >= 0, "should recover a for loop: " + decompiled);
            String condition = decompiled.substring(decompiled.indexOf(';', forIdx) + 1,
                decompiled.indexOf(';', decompiled.indexOf(';', forIdx) + 1));
            assertTrue(condition.contains("*") && condition.contains("<="),
                "loop condition must recover i * i <= n, not a spilled scratch reference: " + decompiled);
            assertTrue(decompiled.contains("% i == 0") || decompiled.replaceAll("\\s", "").contains("%i==0"),
                "body must recover n % i == 0: " + decompiled);
            assertFalse(decompiled.contains("local2"),
                "no scratch slot variable should be materialized: " + decompiled);
        }

        @Test
        void loopWithVariableSwap() throws Exception {
            // Iterative fibonacci: the loop swap (a = b; b = temp) makes the two loop-carried phis
            // simultaneously live, so they must not be coalesced into one register.
            String source = "class Test { static int fib(int n) { "
                + "if (n <= 1) { return n; } int a = 0; int b = 1; "
                + "for (int i = 2; i <= n; i = i + 1) { int t = a + b; a = b; b = t; } return b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("fib", int.class);
            assertEquals(0, (int) m.invoke(null, 0));
            assertEquals(1, (int) m.invoke(null, 2));
            assertEquals(5, (int) m.invoke(null, 5));
            assertEquals(55, (int) m.invoke(null, 10));
        }

        @Test
        void doWhileLoop() throws Exception {
            String source = "class Test { static int test(int n) { int count = 0; do { count = count + 1; } while (count < n); return count; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class);
            assertEquals(1, (int) m.invoke(null, 0));
            assertEquals(1, (int) m.invoke(null, 1));
            assertEquals(5, (int) m.invoke(null, 5));
        }
    }

    @Nested
    class FloatDoubleTests {

        @Test
        void floatArithmetic() throws Exception {
            String source = "class Test { static float test(float a, float b) { return a + b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", float.class, float.class);
            assertEquals(5.5f, (float) m.invoke(null, 2.5f, 3.0f), 0.001f);
        }

        @Test
        void doubleArithmetic() throws Exception {
            String source = "class Test { static double test(double a, double b) { return a * b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", double.class, double.class);
            assertEquals(7.5, (double) m.invoke(null, 2.5, 3.0), 0.001);
        }

        @Test
        void floatToDoubleWidening() throws Exception {
            String source = "class Test { static double test(float a, double b) { return a + b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", float.class, double.class);
            assertEquals(5.5, (double) m.invoke(null, 2.5f, 3.0), 0.001);
        }

        @Test
        void intToDoubleWidening() throws Exception {
            String source = "class Test { static double test(int a, double b) { return a + b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", int.class, double.class);
            assertEquals(5.5, (double) m.invoke(null, 2, 3.5), 0.001);
        }
    }

    @Nested
    class LongArithmeticTests {

        @Test
        void longAddition() throws Exception {
            String source = "class Test { static long test(long a, long b) { return a + b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", long.class, long.class);
            assertEquals(5000000000L, (long) m.invoke(null, 2000000000L, 3000000000L));
        }

        @Test
        void longMultiplication() throws Exception {
            String source = "class Test { static long test(long a, long b) { return a * b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", long.class, long.class);
            assertEquals(6000000000000000000L, (long) m.invoke(null, 2000000000L, 3000000000L));
        }

        @Test
        void longDivision() throws Exception {
            String source = "class Test { static long test(long a, long b) { return a / b; } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("test", long.class, long.class);
            assertEquals(2L, (long) m.invoke(null, 6000000000L, 3000000000L));
        }
    }

    @Nested
    class FieldAccessTests {

        @Test
        void staticFieldReadWrite() throws Exception {
            String source = "class T { static int counter; "
                    + "static int test() { counter = 41; counter = counter + 1; return counter; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(42, (int) clazz.getMethod("test").invoke(null));
        }

        @Test
        void staticFieldCompoundAssignment() throws Exception {
            String source = "class T { static int counter; "
                    + "static int test() { counter = 10; counter += 5; return counter; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(15, (int) clazz.getMethod("test").invoke(null));
        }

        @Test
        void staticFieldIncrement() throws Exception {
            String source = "class T { static int counter; "
                    + "static int test() { counter = 5; counter++; return counter; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(6, (int) clazz.getMethod("test").invoke(null));
        }

        @Test
        void staticArrayFieldAccess() throws Exception {
            String source = "class T { static int[] arr; "
                    + "static int test() { arr = new int[3]; arr[1] = 99; return arr[1]; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(99, (int) clazz.getMethod("test").invoke(null));
        }

        @Test
        void staticFieldDecrement() throws Exception {
            String source = "class T { static int counter; "
                    + "static int test() { counter = 5; counter--; return counter; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(4, (int) clazz.getMethod("test").invoke(null));
        }
    }

    @Nested
    class UnqualifiedSelfCallTests {

        @Test
        void recursiveStaticCall() throws Exception {
            String source = "class T { static int fib(int n) { "
                    + "if (n < 2) { return n; } return fib(n - 1) + fib(n - 2); } }";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            Class<?> clazz = compileAndLoad(cls.getMethods().get(0), uniqueClassName());

            Method m = clazz.getMethod("fib", int.class);
            assertEquals(0, (int) m.invoke(null, 0));
            assertEquals(1, (int) m.invoke(null, 1));
            assertEquals(55, (int) m.invoke(null, 10));
        }

        @Test
        void unqualifiedStaticHelperCall() throws Exception {
            String source = "class T { static int dbl(int x) { return x * 2; } "
                    + "static int use(int x) { return dbl(x) + 1; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(11, (int) clazz.getMethod("use", int.class).invoke(null, 5));
        }
    }

    @Nested
    class TextBlockTests {

        @Test
        void textBlockLowersToNormalizedStringConstant() throws Exception {
            // The front-end lexer normalizes the text block (incidental whitespace stripped, LF line
            // terminators). YABR emits a plain String constant in a v55 class, so it loads in-process.
            String source = "class T { static String tb() { return \"\"\"\n"
                    + "        Hello\n"
                    + "        World\n"
                    + "        \"\"\"; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals("Hello\nWorld\n", clazz.getMethod("tb").invoke(null));
        }

        @Test
        void textBlockEscapesAndSpaceEscape() throws Exception {
            // \s preserves a trailing space; \" stays literal; line-continuation backslash joins lines.
            String source = "class T { static String tb() { return \"\"\"\n"
                    + "        a\\sb\n"
                    + "        c\\\n"
                    + "        d\\\"\n"
                    + "        \"\"\"; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals("a b\ncd\"\n", clazz.getMethod("tb").invoke(null));
        }

        @Test
        void textBlockRoundTripMatchesJavac() throws Exception {
            // javac's text block and YABR's must produce the same String value: compile the same
            // source with corretto javac, run it, and compare to YABR's in-process result.
            assumeTrue(com.tonic.testutil.ModernJdk.available(17), "JDK 17 not installed");
            String body = "return \"\"\"\n            Line1\n              indented\n            Line3\n            \"\"\";";
            String yabrSource = "class T { static String tb() { " + body + " } }";
            String expected = (String) compileClassWithFields(yabrSource, uniqueClassName())
                    .getMethod("tb").invoke(null);

            String javacSource = "public class TbRef { public static String tb() { " + body + " }\n"
                    + "  public static void main(String[] a) { System.out.print(tb()); } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(17, "TbRef", javacSource);
            String javacValue = com.tonic.testutil.ModernJdk.runVerified(17, classes, "TbRef");
            assertEquals(javacValue, expected, "YABR text-block value must match javac's");
        }
    }

    @Nested
    class PatternInstanceOfTests {

        @Test
        void recompilePositivePatternBinding() throws Exception {
            String source = "class T { static String describe(Object o) { "
                    + "if (o instanceof String s) { return \"str:\" + s.length(); } return \"other\"; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals("str:5", clazz.getMethod("describe", Object.class).invoke(null, "hello"));
            assertEquals("other", clazz.getMethod("describe", Object.class).invoke(null, 42));
        }

        @Test
        void recompileNegatedGuardPatternBinding() throws Exception {
            String source = "class T { static int len(Object o) { "
                    + "if (!(o instanceof String s)) { return -1; } return s.length(); } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(5, clazz.getMethod("len", Object.class).invoke(null, "hello"));
            assertEquals(-1, clazz.getMethod("len", Object.class).invoke(null, new Object()));
        }

        @Test
        void decompileReconstructsPatternBinding() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(17), "JDK 17 not installed");
            String javac = "public class Pat { public static String describe(Object o) {"
                    + " if (o instanceof String s) { return \"str:\" + s.length(); } return \"other\"; } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(17, "Pat", javac);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("Pat")));
            String decompiled = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);
            assertTrue(decompiled.matches("(?s).*instanceof\\s+String\\s+\\w+.*"),
                    "must reconstruct a pattern binding `instanceof String <var>`:\n" + decompiled);
        }

        @Test
        void roundTripJavacToYabrAndBack() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(17), "JDK 17 not installed");
            // javac source -> bytecode -> YABR decompile -> re-parse with YABR front-end -> run.
            String javac = "public class Pat { public static String describe(Object o) {"
                    + " if (o instanceof String s) { return \"str:\" + s.length(); } return \"other\"; } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(17, "Pat", javac);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("Pat")));
            String decompiled = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);

            // Strip the package/class wrapper down to the method and recompile via YABR.
            String body = decompiled.substring(decompiled.indexOf("public static String describe"));
            body = body.substring(0, body.indexOf("\n\t}") + 3);
            String reSource = "class T { static " + body.substring(body.indexOf("String")) + " }";
            Class<?> clazz = compileClassWithFields(reSource, uniqueClassName());
            assertEquals("str:5", clazz.getMethod("describe", Object.class).invoke(null, "hello"));
            assertEquals("other", clazz.getMethod("describe", Object.class).invoke(null, 42));
        }
    }

    @Nested
    class SealedClassTests {

        @Test
        void decompileRendersSealedAndPermits() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(17), "JDK 17 not installed");
            java.util.Map<String, String> src = new java.util.LinkedHashMap<>();
            src.put("Shape", "public sealed interface Shape permits Circle, Square { double area(); }");
            src.put("Circle", "public final class Circle implements Shape { public double area(){return 1.0;} }");
            src.put("Square", "public final class Square implements Shape { public double area(){return 2.0;} }");
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(17, src);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("Shape")));
            String decompiled = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);
            assertTrue(decompiled.contains("sealed interface Shape"),
                    "must render the sealed modifier:\n" + decompiled);
            assertTrue(decompiled.matches("(?s).*permits\\s+Circle,\\s*Square.*"),
                    "must render the permits clause:\n" + decompiled);
        }

        @Test
        void parserToleratesSealedAndPermitsAndNonSealed() {
            // Re-parsing decompiled modern source must not error on the sealed header.
            parser.parse("public sealed interface Shape permits Circle, Square { double area(); }");
            parser.parse("public sealed class Base permits A, B { }");
            parser.parse("public non-sealed class A extends Base { }");
        }
    }

    @Nested
    class RecordTests {

        @Test
        void decompileReconstructsRecordHeaderAndSuppressesGenerated() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(17), "JDK 17 not installed");
            String javac = "public record Point(int x, int y) { public int sum() { return x + y; } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(17, "Point", javac);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("Point")));
            String d = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);

            assertTrue(d.contains("record Point(int x, int y)"), "must render record header:\n" + d);
            assertTrue(d.contains("public int sum()"), "must keep the user method:\n" + d);
            assertFalse(d.contains("class Point"), "must not render as a class:\n" + d);
            assertFalse(d.contains("extends Record"), "must not render the implicit Record supertype:\n" + d);
            assertFalse(d.contains("int x()") || d.contains("int y()"), "accessors must be suppressed:\n" + d);
            assertFalse(d.contains("ObjectMethods"), "ObjectMethods equals/hashCode/toString must be suppressed:\n" + d);
            assertFalse(d.contains("private final int x"), "component fields must be suppressed:\n" + d);
        }

        @Test
        void parserReParsesDecompiledRecord() {
            // Decompiled record source must re-parse without error, materializing component fields.
            CompilationUnit cu = parser.parse(
                    "public record Point(int x, int y) { public int sum() { return this.x + this.y; } }");
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            assertEquals(2, cls.getFields().size(), "components should be materialized as fields");
            assertEquals(1, cls.getMethods().size(), "user method should be parsed");
        }
    }

    @Nested
    class SwitchExpressionTests {

        @Test
        void decompileReconstructsReturnSwitchExpression() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(17), "JDK 17 not installed");
            String javac = "public class Sw { public static int classify(int n) {"
                    + " return switch (n) { case 1 -> 10; case 2 -> 20; default -> 0; }; } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(17, "Sw", javac);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("Sw")));
            String d = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);
            assertTrue(d.matches("(?s).*return switch \\(arg0\\) \\{.*-> 10;.*-> 20;.*default -> 0;.*"),
                    "must reconstruct a return switch expression:\n" + d);
        }

        @Test
        void decompileReconstructsAssignmentSwitchExpression() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(17), "JDK 17 not installed");
            String javac = "public class Sw { public static String name(int d) {"
                    + " String s = switch (d) { case 1 -> \"one\"; case 2 -> \"two\"; default -> \"?\"; }; return s; } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(17, "Sw", javac);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("Sw")));
            String d = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);
            assertTrue(d.matches("(?s).*=\\s*switch \\(arg0\\) \\{.*-> \"one\";.*default -> \"\\?\";.*"),
                    "must reconstruct an assignment switch expression:\n" + d);
        }

        @Test
        void recompileReturnSwitchExpression() throws Exception {
            String source = "class T { static int classify(int n) { "
                    + "return switch (n) { case 1 -> 10; case 2 -> 20; default -> 0; }; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(10, clazz.getMethod("classify", int.class).invoke(null, 1));
            assertEquals(20, clazz.getMethod("classify", int.class).invoke(null, 2));
            assertEquals(0, clazz.getMethod("classify", int.class).invoke(null, 99));
        }

        @Test
        void recompileAssignmentSwitchExpression() throws Exception {
            String source = "class T { static String name(int d) { "
                    + "String s = switch (d) { case 1 -> \"one\"; case 2 -> \"two\"; default -> \"?\"; }; "
                    + "return s; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals("one", clazz.getMethod("name", int.class).invoke(null, 1));
            assertEquals("two", clazz.getMethod("name", int.class).invoke(null, 2));
            assertEquals("?", clazz.getMethod("name", int.class).invoke(null, 9));
        }

        @Test
        void fullRoundTripJavacToYabr() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(17), "JDK 17 not installed");
            String javac = "public class Sw { public static int classify(int n) {"
                    + " return switch (n) { case 1 -> 10; case 2 -> 20; default -> 0; }; } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(17, "Sw", javac);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("Sw")));
            String dec = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);
            String body = dec.substring(dec.indexOf("public static int classify"));
            body = body.substring(0, body.indexOf("\n\t}") + 3);
            String reSource = "class T { static " + body.substring(body.indexOf("int")) + " }";
            Class<?> clazz = compileClassWithFields(reSource, uniqueClassName());
            assertEquals(10, clazz.getMethod("classify", int.class).invoke(null, 1));
            assertEquals(0, clazz.getMethod("classify", int.class).invoke(null, 7));
        }
    }

    @Nested
    class BranchMergeRecompileTests {

        // Regression: variables assigned across if/switch branches and read afterward must merge via
        // a phi on recompile. Previously SSA construction ran only for loops, so the post-branch read
        // wrongly took the last branch's value.

        @Test
        void ifElseVariableMerge() throws Exception {
            String source = "class T { static int f(boolean c) { int r; if (c) { r = 1; } else { r = 2; } return r; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(1, clazz.getMethod("f", boolean.class).invoke(null, true));
            assertEquals(2, clazz.getMethod("f", boolean.class).invoke(null, false));
        }

        @Test
        void switchStatementVariableMerge() throws Exception {
            String source = "class T { static int f(int n) { int r = 0; "
                    + "switch (n) { case 1: r = 10; break; case 2: r = 20; break; default: r = 99; break; } "
                    + "return r; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(10, clazz.getMethod("f", int.class).invoke(null, 1));
            assertEquals(20, clazz.getMethod("f", int.class).invoke(null, 2));
            assertEquals(99, clazz.getMethod("f", int.class).invoke(null, 5));
        }

        @Test
        void ifWithoutElseMerge() throws Exception {
            String source = "class T { static int f(boolean c) { int r = 7; if (c) { r = 1; } return r; } }";
            Class<?> clazz = compileClassWithFields(source, uniqueClassName());
            assertEquals(1, clazz.getMethod("f", boolean.class).invoke(null, true));
            assertEquals(7, clazz.getMethod("f", boolean.class).invoke(null, false));
        }
    }

    @Nested
    class PatternSwitchTests {

        @Test
        void decompileReconstructsTypePatternSwitch() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(21), "JDK 21 not installed");
            String javac = "public class Pat21 { static String describe(Object o) {"
                    + " return switch (o) {"
                    + "   case Integer i -> \"int:\" + i;"
                    + "   case String s -> \"str:\" + s.length();"
                    + "   default -> \"other\"; }; } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(21, "Pat21", javac);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("Pat21")));
            String d = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);

            // Reconstructed as a pattern switch with type-pattern arms; no dispatch-loop artifacts.
            assertFalse(d.contains("$pc$"), "must not leave a dispatch loop:\n" + d);
            assertFalse(d.contains("typeSwitch"), "must not leave a raw typeSwitch invokedynamic:\n" + d);
            assertTrue(d.matches("(?s).*return switch \\(arg0\\) \\{.*"), "expected a return switch:\n" + d);
            assertTrue(d.matches("(?s).*case Integer \\w+ ->.*"), "expected `case Integer <b> ->`:\n" + d);
            assertTrue(d.matches("(?s).*case String \\w+ ->.*"), "expected `case String <b> ->`:\n" + d);
            assertTrue(d.matches("(?s).*default -> \"other\";.*"), "expected default arm:\n" + d);
        }

        @Test
        void decompileReconstructsAssignmentTypePatternSwitch() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(21), "JDK 21 not installed");
            String javac = "public class PatA { static int describe(Object o) {"
                    + " String r = switch (o) {"
                    + "   case Integer i -> \"int:\" + i;"
                    + "   case String s -> \"S\";"
                    + "   default -> \"other\"; };"
                    + " return r.length(); } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(21, "PatA", javac);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("PatA")));
            String d = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);
            assertFalse(d.contains("$pc$"), "no dispatch loop:\n" + d);
            assertFalse(d.contains("typeSwitch"), "no raw typeSwitch:\n" + d);
            assertTrue(d.matches("(?s).*=\\s*switch \\(arg0\\) \\{.*"), "expected assignment switch:\n" + d);
            assertTrue(d.matches("(?s).*case Integer \\w+ -> \"int:\".*"), "expected Integer arm:\n" + d);
            // Unused String binding must still be a valid type-pattern binding, not `case String ->`.
            assertTrue(d.matches("(?s).*case String \\w+ -> \"S\";.*"), "expected `case String <b> ->`:\n" + d);
        }

        @Test
        void decompileReconstructsRecordDeconstructionPattern() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(21), "JDK 21 not installed");
            String javac = "public class PatRec {"
                    + " record Point(int x, int y) {}"
                    + " static int rec(Object o) {"
                    + "   return switch (o) {"
                    + "     case Point(int x, int y) -> x + y;"
                    + "     default -> -1; }; } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(21, "PatRec", javac);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("PatRec")));
            String d = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);

            // The MatchException machinery is stripped and the accessor sequence folds into a
            // record-deconstruction pattern; no dispatch-loop / raw-typeSwitch / MatchException leakage.
            assertFalse(d.contains("$pc$"), "no dispatch loop:\n" + d);
            assertFalse(d.contains("typeSwitch"), "no raw typeSwitch:\n" + d);
            assertFalse(d.contains("MatchException"), "MatchException handler must be elided:\n" + d);
            assertTrue(d.matches("(?s).*return switch \\(arg0\\) \\{.*"), "expected a return switch:\n" + d);
            assertTrue(d.matches("(?s).*case PatRec\\.Point\\(int \\w+, int \\w+\\) -> \\w+ \\+ \\w+;.*"),
                    "expected `case PatRec.Point(int a, int b) -> a + b`:\n" + d);
            assertTrue(d.matches("(?s).*default -> -1;.*"), "expected default arm:\n" + d);
        }

        @Test
        void decompileReconstructsGuardedPattern() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(21), "JDK 21 not installed");
            String javac = "public class PatGuard { static String guard(Object o) {"
                    + " return switch (o) {"
                    + "   case Integer i when i > 0 -> \"pos\";"
                    + "   case Integer i -> \"nonpos\";"
                    + "   default -> \"other\"; }; } }";
            java.util.Map<String, byte[]> classes = com.tonic.testutil.ModernJdk.compile(21, "PatGuard", javac);
            ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(classes.get("PatGuard")));
            String d = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);

            // The guard restart loop is recovered (via the $pc$ dispatch form) and folded into a `when`.
            assertFalse(d.contains("$pc$"), "no dispatch loop:\n" + d);
            assertFalse(d.contains("typeSwitch"), "no raw typeSwitch:\n" + d);
            assertTrue(d.matches("(?s).*return switch \\(arg0\\) \\{.*"), "expected a return switch:\n" + d);
            assertTrue(d.matches("(?s).*case Integer \\w+ when [^>]*> 0 -> \"pos\";.*"),
                    "expected guarded `case Integer i when ... -> \"pos\"`:\n" + d);
            assertTrue(d.matches("(?s).*case Integer \\w+ -> \"nonpos\";.*"),
                    "expected unguarded `case Integer i -> \"nonpos\"`:\n" + d);
            assertTrue(d.matches("(?s).*default -> \"other\";.*"), "expected default arm:\n" + d);
        }
    }

    @Nested
    class PatternSwitchRecompileTests {

        /**
         * Lowers every method of a parsed class to bytecode and returns the class bytes. The given
         * platform classes are pre-loaded into the pool so library calls resolve.
         */
        private byte[] recompileToBytes(String source, String ownerClass, String... platformClasses)
                throws Exception {
            return recompileToBytes(source, ownerClass, java.util.Collections.emptyMap(), platformClasses);
        }

        private byte[] recompileToBytes(String source, String ownerClass,
                                        java.util.Map<String, byte[]> preloaded, String... platformClasses)
                throws Exception {
            ClassPool localPool = TestUtils.emptyPool();
            for (String cn : platformClasses) {
                localPool.loadPlatformClass(cn + ".class");
            }
            for (byte[] classBytes : preloaded.values()) {
                localPool.loadClass(new java.io.ByteArrayInputStream(classBytes));
            }
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            ClassFile cf = localPool.createNewClass(ownerClass, new AccessBuilder().setPublic().build());
            ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), localPool);
            lowerer.setCurrentClassDecl(cls);
            lowerer.setImports(cu.getImports());
            SSA ssa = new SSA(cf.getConstPool());
            for (MethodDecl method : cls.getMethods()) {
                if (method.getBody() == null) {
                    continue;
                }
                List<SourceType> params = new ArrayList<>();
                for (ParameterDecl p : method.getParameters()) {
                    params.add(p.getType());
                }
                cf.createNewMethodWithDescriptor(new AccessBuilder().setPublic().setStatic().build(),
                        method.getName(), buildDescriptor(params, method.getReturnType()));
                MethodEntry entry = findMethod(cf, method.getName());
                ssa.lower(lowerer.lower(method, ownerClass), entry);
            }
            return cf.write();
        }

        @Test
        void recompilesTypePatternSwitchAndRunsOnJdk21() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(21), "JDK 21 not installed");
            // main throws (exit != 0) on any wrong arm; runVerified passes iff -Xverify:all and all
            // arms behave. Exercises a typeSwitch indy + integer dispatch + per-arm casts.
            String src = "public class PReType {"
                    + " public static void main(String[] a) {"
                    + "   Object o1 = \"x\";"
                    + "   Object o2 = Integer.valueOf(5);"
                    + "   Object o3 = Double.valueOf(1.0);"
                    + "   int r1 = switch (o1) { case Integer i -> 1; case String s -> 2; default -> 0; };"
                    + "   int r2 = switch (o2) { case Integer i -> 1; case String s -> 2; default -> 0; };"
                    + "   int r3 = switch (o3) { case Integer i -> 1; case String s -> 2; default -> 0; };"
                    + "   if (r1 != 2 || r2 != 1 || r3 != 0) throw new RuntimeException(); } }";
            byte[] bytes = recompileToBytes(src, "PReType",
                    "java/lang/Object", "java/lang/Integer", "java/lang/Double",
                    "java/lang/String", "java/lang/Number", "java/lang/RuntimeException");
            java.util.Map<String, byte[]> classes = new java.util.LinkedHashMap<>();
            classes.put("PReType", bytes);
            com.tonic.testutil.ModernJdk.runVerified(21, classes, "PReType");
        }

        @Test
        void recompilesGuardedPatternSwitchAndRunsOnJdk21() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(21), "JDK 21 not installed");
            // x1: guard passes -> 1; x2: guard fails -> restart re-dispatch -> unguarded Integer arm -> 2;
            // x3: String selector -> default -> 0. Exercises the typeSwitch restart loop.
            String src = "public class PReGuard {"
                    + " public static void main(String[] a) {"
                    + "   int n = 0;"
                    + "   Object g1 = Integer.valueOf(5);"
                    + "   Object g2 = Integer.valueOf(-3);"
                    + "   Object g3 = \"z\";"
                    + "   int x1 = switch (g1) { case Integer i when n == 0 -> 1; case Integer i -> 2; default -> 0; };"
                    + "   int x2 = switch (g2) { case Integer i when n > 0 -> 1; case Integer i -> 2; default -> 0; };"
                    + "   int x3 = switch (g3) { case Integer i when n == 0 -> 1; case Integer i -> 2; default -> 0; };"
                    + "   if (x1 != 1 || x2 != 2 || x3 != 0) throw new RuntimeException(); } }";
            byte[] bytes = recompileToBytes(src, "PReGuard",
                    "java/lang/Object", "java/lang/Integer", "java/lang/String",
                    "java/lang/Number", "java/lang/RuntimeException");
            java.util.Map<String, byte[]> classes = new java.util.LinkedHashMap<>();
            classes.put("PReGuard", bytes);
            com.tonic.testutil.ModernJdk.runVerified(21, classes, "PReGuard");
        }

        @Test
        void recompilesRecordDeconstructionSwitchAndRunsOnJdk21() throws Exception {
            assumeTrue(com.tonic.testutil.ModernJdk.available(21), "JDK 21 not installed");
            // The record Pt is compiled by javac so its RecordAttribute is available to resolve the
            // component accessors; YABR then recompiles a deconstruction switch against it.
            java.util.Map<String, byte[]> rec =
                    com.tonic.testutil.ModernJdk.compile(21, "Pt", "public record Pt(int x, int y) {}");
            String src = "public class PReDec {"
                    + " public static void main(String[] a) {"
                    + "   Object o = new Pt(3, 4);"
                    + "   int r = switch (o) { case Pt(int x, int y) -> x + y; default -> -1; };"
                    + "   Object o2 = Integer.valueOf(9);"
                    + "   int r2 = switch (o2) { case Pt(int x, int y) -> x + y; default -> -1; };"
                    + "   if (r != 7 || r2 != -1) throw new RuntimeException(); } }";
            byte[] bytes = recompileToBytes(src, "PReDec", rec,
                    "java/lang/Object", "java/lang/Integer", "java/lang/String",
                    "java/lang/Number", "java/lang/RuntimeException");
            java.util.Map<String, byte[]> classes = new java.util.LinkedHashMap<>();
            classes.put("Pt", rec.get("Pt"));
            classes.put("PReDec", bytes);
            com.tonic.testutil.ModernJdk.runVerified(21, classes, "PReDec");
        }
    }
}
