package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class LambdaLoweringTest {

    private ASTLowerer lowerer;
    private JavaParser parser;

    @BeforeEach
    void setUp() throws IOException {
        com.tonic.analysis.ssa.cfg.IRBlock.resetIdCounter();
        com.tonic.analysis.ssa.value.SSAValue.resetIdCounter();
        ConstPool constPool = new ConstPool();
        ClassPool classPool = new ClassPool();
        lowerer = new ASTLowerer(constPool, classPool);
        parser = JavaParser.create();
    }

    @Nested
    class SimpleLambdaTests {

        @Test
        void testNoCaptureLambdaExpression() {
            String source = "package test;\n" +
                "import java.util.function.Supplier;\n" +
                "public class Test {\n" +
                "    public Supplier<Integer> test() {\n" +
                "        return () -> 42;\n" +
                "    }\n" +
                "}\n";

            CompilationUnit cu = parser.parse(source);
            ClassDecl classDecl = cu.getClasses().get(0);
            MethodDecl method = classDecl.getMethods().get(0);

            lowerer.setCurrentClassDecl(classDecl);
            lowerer.setImports(cu.getImports());
            IRMethod ir = lowerer.lower(method, "test/Test");

            assertNotNull(ir);
            assertTrue(hasInvokeDynamic(ir), "Should contain invokedynamic instruction");
        }

        @Test
        void testSingleParamLambda() {
            String source = "package test;\n" +
                "import java.util.function.Function;\n" +
                "public class Test {\n" +
                "    public Function<Integer, Integer> test() {\n" +
                "        return x -> x * 2;\n" +
                "    }\n" +
                "}\n";

            CompilationUnit cu = parser.parse(source);
            ClassDecl classDecl = cu.getClasses().get(0);
            MethodDecl method = classDecl.getMethods().get(0);

            lowerer.setCurrentClassDecl(classDecl);
            lowerer.setImports(cu.getImports());
            IRMethod ir = lowerer.lower(method, "test/Test");

            assertNotNull(ir);
            assertTrue(hasInvokeDynamic(ir), "Should contain invokedynamic instruction");
        }

        @Test
        void testMultiParamLambda() {
            String source = "package test;\n" +
                "import java.util.function.BiFunction;\n" +
                "public class Test {\n" +
                "    public BiFunction<Integer, Integer, Integer> test() {\n" +
                "        return (a, b) -> a + b;\n" +
                "    }\n" +
                "}\n";

            CompilationUnit cu = parser.parse(source);
            ClassDecl classDecl = cu.getClasses().get(0);
            MethodDecl method = classDecl.getMethods().get(0);

            lowerer.setCurrentClassDecl(classDecl);
            lowerer.setImports(cu.getImports());
            IRMethod ir = lowerer.lower(method, "test/Test");

            assertNotNull(ir);
            assertTrue(hasInvokeDynamic(ir), "Should contain invokedynamic instruction");
        }
    }

    @Nested
    class BlockBodyLambdaTests {

        @Test
        void testBlockBodyLambda() {
            String source = "package test;\n" +
                "import java.util.function.Function;\n" +
                "public class Test {\n" +
                "    public Function<Integer, Integer> test() {\n" +
                "        return x -> {\n" +
                "            int y = x * 2;\n" +
                "            return y;\n" +
                "        };\n" +
                "    }\n" +
                "}\n";

            CompilationUnit cu = parser.parse(source);
            ClassDecl classDecl = cu.getClasses().get(0);
            MethodDecl method = classDecl.getMethods().get(0);

            lowerer.setCurrentClassDecl(classDecl);
            lowerer.setImports(cu.getImports());
            IRMethod ir = lowerer.lower(method, "test/Test");

            assertNotNull(ir);
            assertTrue(hasInvokeDynamic(ir), "Should contain invokedynamic instruction");
        }
    }

    @Nested
    class CapturingLambdaTests {

        @Test
        void testCapturingLocalVariable() {
            String source = "package test;\n" +
                "import java.util.function.Supplier;\n" +
                "public class Test {\n" +
                "    public Supplier<Integer> test() {\n" +
                "        int x = 5;\n" +
                "        return () -> x;\n" +
                "    }\n" +
                "}\n";

            CompilationUnit cu = parser.parse(source);
            ClassDecl classDecl = cu.getClasses().get(0);
            MethodDecl method = classDecl.getMethods().get(0);

            lowerer.setCurrentClassDecl(classDecl);
            lowerer.setImports(cu.getImports());
            IRMethod ir = lowerer.lower(method, "test/Test");

            assertNotNull(ir);
            assertTrue(hasInvokeDynamic(ir), "Should contain invokedynamic instruction");

            InvokeInstruction indy = findInvokeDynamic(ir);
            assertNotNull(indy);
            assertFalse(indy.getArguments().isEmpty(), "Should have captured variable as argument");
        }
    }

    @Nested
    class MethodReferenceTests {

        @Test
        void testStaticMethodReference() {
            String source = "package test;\n" +
                "import java.util.function.Function;\n" +
                "public class Test {\n" +
                "    public Function<String, Integer> test() {\n" +
                "        return Integer::parseInt;\n" +
                "    }\n" +
                "}\n";

            CompilationUnit cu = parser.parse(source);
            ClassDecl classDecl = cu.getClasses().get(0);
            MethodDecl method = classDecl.getMethods().get(0);

            lowerer.setCurrentClassDecl(classDecl);
            lowerer.setImports(cu.getImports());
            IRMethod ir = lowerer.lower(method, "test/Test");

            assertNotNull(ir);
            assertTrue(hasInvokeDynamic(ir), "Should contain invokedynamic instruction");
        }

        @Test
        void testInstanceMethodReference() {
            String source = "package test;\n" +
                "import java.util.function.Function;\n" +
                "public class Test {\n" +
                "    public Function<String, Integer> test() {\n" +
                "        return String::length;\n" +
                "    }\n" +
                "}\n";

            CompilationUnit cu = parser.parse(source);
            ClassDecl classDecl = cu.getClasses().get(0);
            MethodDecl method = classDecl.getMethods().get(0);

            lowerer.setCurrentClassDecl(classDecl);
            lowerer.setImports(cu.getImports());
            IRMethod ir = lowerer.lower(method, "test/Test");

            assertNotNull(ir);
            assertTrue(hasInvokeDynamic(ir), "Should contain invokedynamic instruction");
        }

        @Test
        void testConstructorReference() {
            String source = "package test;\n" +
                "import java.util.function.Supplier;\n" +
                "import java.util.ArrayList;\n" +
                "public class Test {\n" +
                "    public Supplier<ArrayList> test() {\n" +
                "        return ArrayList::new;\n" +
                "    }\n" +
                "}\n";

            CompilationUnit cu = parser.parse(source);
            ClassDecl classDecl = cu.getClasses().get(0);
            MethodDecl method = classDecl.getMethods().get(0);

            lowerer.setCurrentClassDecl(classDecl);
            lowerer.setImports(cu.getImports());
            IRMethod ir = lowerer.lower(method, "test/Test");

            assertNotNull(ir);
            assertTrue(hasInvokeDynamic(ir), "Should contain invokedynamic instruction");
        }
    }

    private boolean hasInvokeDynamic(IRMethod ir) {
        return findInvokeDynamic(ir) != null;
    }

    private InvokeInstruction findInvokeDynamic(IRMethod ir) {
        for (var block : ir.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    if (invoke.getInvokeType() == InvokeType.DYNAMIC) {
                        return invoke;
                    }
                }
            }
        }
        return null;
    }
}
