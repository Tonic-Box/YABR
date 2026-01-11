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
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool());
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

        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool());
        IRMethod ir = lowerer.lower(methodDecl, ownerClass);

        SSA ssa = new SSA(cf.getConstPool());
        ssa.lower(ir, method);

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
            assertEquals(true, (boolean) m.invoke(null));
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
            assertEquals(false, (boolean) m.invoke(null));
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
            assertEquals(true, (boolean) m.invoke(null, 5, 10));
            assertEquals(false, (boolean) m.invoke(null, 10, 5));
            assertEquals(false, (boolean) m.invoke(null, 5, 5));
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
            assertEquals(true, (boolean) m.invoke(null, 5, 10));
            assertEquals(false, (boolean) m.invoke(null, 10, 5));
            assertEquals(true, (boolean) m.invoke(null, 5, 5));
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
            assertEquals(false, (boolean) m.invoke(null, 5, 10));
            assertEquals(true, (boolean) m.invoke(null, 10, 5));
            assertEquals(false, (boolean) m.invoke(null, 5, 5));
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
            assertEquals(false, (boolean) m.invoke(null, 5, 10));
            assertEquals(true, (boolean) m.invoke(null, 10, 5));
            assertEquals(true, (boolean) m.invoke(null, 5, 5));
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
            assertEquals(false, (boolean) m.invoke(null, 5, 10));
            assertEquals(true, (boolean) m.invoke(null, 5, 5));
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
            assertEquals(true, (boolean) m.invoke(null, 5, 10));
            assertEquals(false, (boolean) m.invoke(null, 5, 5));
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
            assertEquals(false, (boolean) m.invoke(null, true));
            assertEquals(true, (boolean) m.invoke(null, false));
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
            assertEquals(true, (boolean) m.invoke(null, true, true));
            assertEquals(false, (boolean) m.invoke(null, true, false));
            assertEquals(false, (boolean) m.invoke(null, false, true));
            assertEquals(false, (boolean) m.invoke(null, false, false));
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
            assertEquals(true, (boolean) m.invoke(null, true, true));
            assertEquals(true, (boolean) m.invoke(null, true, false));
            assertEquals(true, (boolean) m.invoke(null, false, true));
            assertEquals(false, (boolean) m.invoke(null, false, false));
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
            assertEquals(true, (boolean) m.invoke(null, 5, 3));
            assertEquals(false, (boolean) m.invoke(null, 3, 5));
            assertEquals(false, (boolean) m.invoke(null, 4, 4));
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
            assertEquals(false, (boolean) m.invoke(null, 5, 3));
            assertEquals(true, (boolean) m.invoke(null, 3, 5));
            assertEquals(false, (boolean) m.invoke(null, 4, 4));
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
            assertEquals(false, (boolean) m.invoke(null, 5, 3));
            assertEquals(false, (boolean) m.invoke(null, 3, 5));
            assertEquals(true, (boolean) m.invoke(null, 4, 4));
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
            assertEquals(true, (boolean) m.invoke(null, 5, 3));
            assertEquals(true, (boolean) m.invoke(null, 3, 5));
            assertEquals(false, (boolean) m.invoke(null, 4, 4));
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
}
