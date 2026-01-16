package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.*;
import com.tonic.analysis.source.ast.type.SourceType;
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

public class ExpressionLoweringE2ETest {

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
        return "com/test/E2E" + (classCounter++);
    }

    private ClassFile createClassWithMethod(String className, String methodName, String descriptor,
                                            boolean isStatic) throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass(className, classAccess);
        int methodAccess = isStatic
                ? new AccessBuilder().setPublic().setStatic().build()
                : new AccessBuilder().setPublic().build();
        cf.createNewMethodWithDescriptor(methodAccess, methodName, descriptor);
        return cf;
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
    class MainMethodTests {

        @Test
        void mainMethodWithPrintln() throws Exception {
            String source = "class Test { " +
                    "static int main() { " +
                    "    int result = 42; " +
                    "    return result; " +
                    "} " +
                    "}";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("main");
            assertEquals(42, (int) m.invoke(null));
        }

        @Test
        void mainMethodWithArithmetic() throws Exception {
            String source = "class Test { " +
                    "static int main() { " +
                    "    int a = 10; " +
                    "    int b = 20; " +
                    "    int c = a + b * 2; " +
                    "    return c; " +
                    "} " +
                    "}";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("main");
            assertEquals(50, (int) m.invoke(null));
        }

        @Test
        void mainMethodWithControlFlow() throws Exception {
            String source = "class Test { " +
                    "static int main() { " +
                    "    int x = 5; " +
                    "    if (x > 3) { " +
                    "        return x * 2; " +
                    "    } else { " +
                    "        return x; " +
                    "    } " +
                    "} " +
                    "}";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("main");
            assertEquals(10, (int) m.invoke(null));
        }

        @Test
        void mainMethodWithLoop() throws Exception {
            String source = "class Test { " +
                    "static int main() { " +
                    "    int sum = 0; " +
                    "    for (int i = 1; i <= 10; i = i + 1) { " +
                    "        sum = sum + i; " +
                    "    } " +
                    "    return sum; " +
                    "} " +
                    "}";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("main");
            assertEquals(55, (int) m.invoke(null));
        }
    }

    @Nested
    class ComprehensiveExpressionTests {

        @Test
        void allBasicExpressionTypes() throws Exception {
            String source = "class Test { " +
                    "static int compute(int a, int b) { " +
                    "    int sum = a + b; " +
                    "    int diff = a - b; " +
                    "    int prod = a * b; " +
                    "    return sum + diff + prod; " +
                    "} " +
                    "}";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("compute", int.class, int.class);
            int result = (int) m.invoke(null, 10, 3);
            assertEquals(50, result);
        }

        @Test
        void ternaryAndComparisons() throws Exception {
            String source = "class Test { " +
                    "static int max3(int a, int b, int c) { " +
                    "    int maxAB = a > b ? a : b; " +
                    "    return maxAB > c ? maxAB : c; " +
                    "} " +
                    "}";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("max3", int.class, int.class, int.class);
            assertEquals(30, (int) m.invoke(null, 10, 30, 20));
            assertEquals(30, (int) m.invoke(null, 30, 10, 20));
            assertEquals(30, (int) m.invoke(null, 10, 20, 30));
        }

        @Test
        void bitwiseOperations() throws Exception {
            String source = "class Test { " +
                    "static int bitwise(int a, int b) { " +
                    "    int and = a & b; " +
                    "    int or = a | b; " +
                    "    int xor = a ^ b; " +
                    "    int not = ~a; " +
                    "    return and + or + xor + not; " +
                    "} " +
                    "}";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("bitwise", int.class, int.class);
            int result = (int) m.invoke(null, 0xFF, 0x0F);
            int expected = (0xFF & 0x0F) + (0xFF | 0x0F) + (0xFF ^ 0x0F) + (~0xFF);
            assertEquals(expected, result);
        }

        @Test
        void incrementDecrement() throws Exception {
            String source = "class Test { " +
                    "static int incdec() { " +
                    "    int x = 10; " +
                    "    int a = ++x; " +
                    "    return a; " +
                    "} " +
                    "}";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Method m = clazz.getMethod("incdec");
            assertEquals(11, (int) m.invoke(null));
        }
    }

    @Nested
    class SuperExprTests {

        @Test
        void superHashCode() throws Exception {
            String source = "class Test { " +
                    "int getSuperHash() { " +
                    "    return super.hashCode(); " +
                    "} " +
                    "}";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method m = clazz.getMethod("getSuperHash");
            int result = (int) m.invoke(instance);
            assertEquals(System.identityHashCode(instance), result);
        }

        @Test
        void superToString() throws Exception {
            String source = "class Test { " +
                    "String getSuperString() { " +
                    "    return super.toString(); " +
                    "} " +
                    "}";
            CompilationUnit cu = parser.parse(source);
            ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
            MethodDecl method = cls.getMethods().get(0);

            String className = uniqueClassName();
            Class<?> clazz = compileAndLoad(method, className);

            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method m = clazz.getMethod("getSuperString");
            String result = (String) m.invoke(instance);
            assertTrue(result.contains("@"), "Should return Object's toString format");
        }
    }
}
