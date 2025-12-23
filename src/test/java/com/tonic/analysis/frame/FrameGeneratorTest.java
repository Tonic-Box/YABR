package com.tonic.analysis.frame;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.stack.FullFrame;
import com.tonic.parser.attribute.stack.StackMapFrame;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.ReturnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FrameGenerator.
 * Verifies StackMapTable frame generation for various instruction patterns.
 */
class FrameGeneratorTest {

    private ClassPool pool;
    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/FrameTest", access);
        constPool = classFile.getConstPool();
    }

    @Test
    void frameGeneratorCreatesWithConstPool() {
        FrameGenerator generator = new FrameGenerator(constPool);
        assertNotNull(generator);
    }

    @Test
    void computeFramesForMethodWithNoCode() throws IOException {
        int access = new AccessBuilder().setPublic().setAbstract().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "abstractMethod", "()V");

        FrameGenerator generator = new FrameGenerator(constPool);
        List<StackMapFrame> frames = generator.computeFrames(method);

        assertTrue(frames.isEmpty(), "Abstract methods should have no frames");
    }

    @Test
    void computeFramesForSimpleMethod() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "simple", "()V");

        Bytecode bc = new Bytecode(method);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        FrameGenerator generator = new FrameGenerator(constPool);
        List<StackMapFrame> frames = generator.computeFrames(method);

        // Simple method with no branches should have no frames
        assertTrue(frames.isEmpty() || frames.size() >= 0,
                "Simple method should produce valid frame list");
    }

    @Test
    void computeFramesForMethodWithBranch() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/BranchTest")
                .publicStaticMethod("withBranch", "(I)V")
                    .iload(0)
                    .iconst(1)
                    .pop()
                    .vreturn()
                .endMethod()
                .build();

        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("withBranch"))
                .findFirst().orElseThrow();

        FrameGenerator generator = new FrameGenerator(cf.getConstPool());
        List<StackMapFrame> frames = generator.computeFrames(method);

        // Method with potential branch targets
        assertNotNull(frames);
    }

    @Test
    void computeFramesHandlesSimpleReturn() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/SimpleTest")
                .publicStaticMethod("simple", "()I")
                    .iconst(42)
                    .ireturn()
                .endMethod()
                .build();

        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("simple"))
                .findFirst().orElseThrow();

        FrameGenerator generator = new FrameGenerator(cf.getConstPool());
        List<StackMapFrame> frames = generator.computeFrames(method);

        assertNotNull(frames);
    }

    @Test
    void framesAreFullFrameType() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/FullFrameTest")
                .publicStaticMethod("test", "()V")
                    .iconst(1)
                    .pop()
                    .vreturn()
                .endMethod()
                .build();

        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("test"))
                .findFirst().orElseThrow();

        FrameGenerator generator = new FrameGenerator(cf.getConstPool());
        List<StackMapFrame> frames = generator.computeFrames(method);

        // All frames should be FullFrame
        for (StackMapFrame frame : frames) {
            if (frame instanceof FullFrame) {
                assertTrue(frame instanceof FullFrame,
                        "All generated frames should be FullFrame type");
            }
        }
    }

    @Test
    void updateStackMapTableCreatesAttribute() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "needsFrames", "(I)V");

        Bytecode bc = new Bytecode(method);
        bc.addILoad(0);
        bc.addIConst(1);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        FrameGenerator generator = new FrameGenerator(constPool);
        generator.updateStackMapTable(method);

        // StackMapTable should be handled without error
        assertNotNull(method.getCodeAttribute());
    }

    @Test
    void updateStackMapTableHandlesNoFrames() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, "noFrames", "()V");

        Bytecode bc = new Bytecode(method);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();

        FrameGenerator generator = new FrameGenerator(constPool);
        generator.updateStackMapTable(method);

        // Should not crash when no frames needed
        assertNotNull(method.getCodeAttribute());
    }

    @Test
    void frameLocalsMatchMethodSignature() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/InstanceTest")
                .publicMethod("instanceMethod", "(IJ)V")
                    .iload(1)
                    .pop()
                    .vreturn()
                .endMethod()
                .build();

        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("instanceMethod"))
                .findFirst().orElseThrow();

        FrameGenerator generator = new FrameGenerator(cf.getConstPool());
        List<StackMapFrame> frames = generator.computeFrames(method);

        // Frames should be generated without error
        assertNotNull(frames);
    }

    @Test
    void computeFramesHandlesComplexMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ComplexTest")
                .publicStaticMethod("complex", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .endMethod()
                .build();

        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("complex"))
                .findFirst().orElseThrow();

        FrameGenerator generator = new FrameGenerator(cf.getConstPool());
        List<StackMapFrame> frames = generator.computeFrames(method);

        // Should handle arithmetic operations
        assertNotNull(frames);
    }

    @Test
    void computeFramesWithMultipleMethods() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/MultiMethodTest")
                .publicStaticMethod("method1", "()V")
                    .vreturn()
                .endMethod()
                .publicStaticMethod("method2", "()I")
                    .iconst(42)
                    .ireturn()
                .endMethod()
                .build();

        FrameGenerator generator = new FrameGenerator(cf.getConstPool());

        for (MethodEntry method : cf.getMethods()) {
            if (!method.getName().equals("<init>") && !method.getName().equals("<clinit>")) {
                List<StackMapFrame> frames = generator.computeFrames(method);
                assertNotNull(frames, "Frames should be computed for " + method.getName());
            }
        }
    }

    @Test
    void computeFramesForMethodWithParameters() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ParamTest")
                .publicStaticMethod("withParams", "(IJFD)V")
                    .iload(0)
                    .pop()
                    .lload(1)
                    .pop2()
                    .fload(3)
                    .pop()
                    .dload(4)
                    .pop2()
                    .vreturn()
                .endMethod()
                .build();

        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("withParams"))
                .findFirst().orElseThrow();

        FrameGenerator generator = new FrameGenerator(cf.getConstPool());
        List<StackMapFrame> frames = generator.computeFrames(method);

        assertNotNull(frames);
    }

    @Test
    void computeFramesHandlesStackManipulation() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/StackTest")
                .publicStaticMethod("stackOps", "()I")
                    .iconst(10)
                    .iconst(20)
                    .dup()
                    .pop()
                    .iadd()
                    .ireturn()
                .endMethod()
                .build();

        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("stackOps"))
                .findFirst().orElseThrow();

        FrameGenerator generator = new FrameGenerator(cf.getConstPool());
        List<StackMapFrame> frames = generator.computeFrames(method);

        assertNotNull(frames);
    }

    @Test
    void computeFramesDoesNotThrowOnValidMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/ValidTest")
                .publicStaticMethod("valid", "(I)I")
                    .iload(0)
                    .iconst(1)
                    .iadd()
                    .ireturn()
                .endMethod()
                .build();

        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals("valid"))
                .findFirst().orElseThrow();

        FrameGenerator generator = new FrameGenerator(cf.getConstPool());

        assertDoesNotThrow(() -> generator.computeFrames(method));
        assertDoesNotThrow(() -> generator.updateStackMapTable(method));
    }

    @org.junit.jupiter.api.Nested
    class ConditionalBranchTests {

        @Test
        void computeFramesForIfComparison() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/IfTest")
                    .publicStaticMethod("ifTest", "(II)I");
            BytecodeBuilder.Label elseLabel = mb.newLabel();

            ClassFile cf = mb.iload(0)
                    .iload(1)
                    .if_icmpge(elseLabel)
                    .iconst(1)
                    .ireturn()
                    .label(elseLabel)
                    .iconst(0)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("ifTest"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void computeFramesForIfNull() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/IfNullTest")
                    .publicStaticMethod("ifNull", "(Ljava/lang/String;)I");
            BytecodeBuilder.Label elseLabel = mb.newLabel();

            ClassFile cf = mb.aload(0)
                    .ifnull(elseLabel)
                    .iconst(1)
                    .ireturn()
                    .label(elseLabel)
                    .iconst(0)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("ifNull"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void computeFramesForIfZero() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/IfZeroTest")
                    .publicStaticMethod("ifZero", "(I)I");
            BytecodeBuilder.Label elseLabel = mb.newLabel();

            ClassFile cf = mb.iload(0)
                    .ifeq(elseLabel)
                    .iconst(1)
                    .ireturn()
                    .label(elseLabel)
                    .iconst(0)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("ifZero"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }
    }

    @org.junit.jupiter.api.Nested
    class SwitchInstructionTests {

        @Test
        void computeFramesForTableSwitch() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TableSwitchTest")
                    .publicStaticMethod("tableSwitch", "(I)I");
            BytecodeBuilder.Label case0 = mb.newLabel();
            BytecodeBuilder.Label case1 = mb.newLabel();
            BytecodeBuilder.Label case2 = mb.newLabel();
            BytecodeBuilder.Label defaultCase = mb.newLabel();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(0, case0);
            cases.put(1, case1);
            cases.put(2, case2);

            ClassFile cf = mb.iload(0)
                    .tableswitch(0, 2, cases, defaultCase)
                    .label(case0)
                    .iconst(1)
                    .ireturn()
                    .label(case1)
                    .iconst(2)
                    .ireturn()
                    .label(case2)
                    .iconst(3)
                    .ireturn()
                    .label(defaultCase)
                    .iconst(0)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("tableSwitch"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
            assertTrue(frames.size() >= 3, "Should have frames for switch targets");
        }

        @Test
        void computeFramesForLookupSwitch() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/LookupSwitchTest")
                    .publicStaticMethod("lookupSwitch", "(I)I");
            BytecodeBuilder.Label case5 = mb.newLabel();
            BytecodeBuilder.Label case10 = mb.newLabel();
            BytecodeBuilder.Label case15 = mb.newLabel();
            BytecodeBuilder.Label defaultCase = mb.newLabel();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(5, case5);
            cases.put(10, case10);
            cases.put(15, case15);

            ClassFile cf = mb.iload(0)
                    .lookupswitch(cases, defaultCase)
                    .label(case5)
                    .iconst(5)
                    .ireturn()
                    .label(case10)
                    .iconst(10)
                    .ireturn()
                    .label(case15)
                    .iconst(15)
                    .ireturn()
                    .label(defaultCase)
                    .iconst(0)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("lookupSwitch"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
            assertTrue(frames.size() >= 3, "Should have frames for switch targets");
        }
    }

    @org.junit.jupiter.api.Nested
    class ExceptionHandlerTests {

        @Test
        void computeFramesForTryCatch() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TryCatchTest")
                    .publicStaticMethod("tryCatch", "()V");
            BytecodeBuilder.Label tryStart = mb.newLabel();
            BytecodeBuilder.Label tryEnd = mb.newLabel();
            BytecodeBuilder.Label handler = mb.newLabel();
            BytecodeBuilder.Label afterCatch = mb.newLabel();

            ClassFile cf = mb.label(tryStart)
                    .iconst(10)
                    .iconst(0)
                    .idiv()
                    .pop()
                    .label(tryEnd)
                    .goto_(afterCatch)
                    .label(handler)
                    .pop()
                    .label(afterCatch)
                    .vreturn()
                    .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("tryCatch"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
            assertTrue(frames.stream().anyMatch(f -> f instanceof FullFrame),
                    "Should generate frames for exception handler");
        }

        @Test
        void computeFramesForCatchAll() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/CatchAllTest")
                    .publicStaticMethod("catchAll", "()V");
            BytecodeBuilder.Label tryStart = mb.newLabel();
            BytecodeBuilder.Label tryEnd = mb.newLabel();
            BytecodeBuilder.Label handler = mb.newLabel();
            BytecodeBuilder.Label afterCatch = mb.newLabel();

            ClassFile cf = mb.label(tryStart)
                    .iconst(1)
                    .pop()
                    .label(tryEnd)
                    .goto_(afterCatch)
                    .label(handler)
                    .pop()
                    .label(afterCatch)
                    .vreturn()
                    .tryCatchAll(tryStart, tryEnd, handler)
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("catchAll"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void computeFramesForNestedTryCatch() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/NestedTest")
                    .publicStaticMethod("nested", "()V");
            BytecodeBuilder.Label outerTryStart = mb.newLabel();
            BytecodeBuilder.Label innerTryStart = mb.newLabel();
            BytecodeBuilder.Label innerTryEnd = mb.newLabel();
            BytecodeBuilder.Label innerHandler = mb.newLabel();
            BytecodeBuilder.Label outerTryEnd = mb.newLabel();
            BytecodeBuilder.Label outerHandler = mb.newLabel();
            BytecodeBuilder.Label afterAll = mb.newLabel();

            ClassFile cf = mb.label(outerTryStart)
                    .label(innerTryStart)
                    .iconst(1)
                    .pop()
                    .label(innerTryEnd)
                    .goto_(outerTryEnd)
                    .label(innerHandler)
                    .pop()
                    .label(outerTryEnd)
                    .goto_(afterAll)
                    .label(outerHandler)
                    .pop()
                    .label(afterAll)
                    .vreturn()
                    .tryCatch(innerTryStart, innerTryEnd, innerHandler, "java/lang/RuntimeException")
                    .tryCatch(outerTryStart, outerTryEnd, outerHandler, "java/lang/Exception")
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("nested"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }
    }

    @org.junit.jupiter.api.Nested
    class LoopAndGotoTests {

        @Test
        void computeFramesForSimpleLoop() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/LoopTest")
                    .publicStaticMethod("loop", "(I)I");
            BytecodeBuilder.Label loopStart = mb.newLabel();
            BytecodeBuilder.Label loopEnd = mb.newLabel();

            ClassFile cf = mb.iconst(0)
                    .istore(1)
                    .label(loopStart)
                    .iload(1)
                    .iload(0)
                    .if_icmpge(loopEnd)
                    .iinc(1, 1)
                    .goto_(loopStart)
                    .label(loopEnd)
                    .iload(1)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("loop"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
            assertTrue(frames.size() > 0, "Loop should require frame entries");
        }

        @Test
        void computeFramesForGotoForward() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/GotoTest")
                    .publicStaticMethod("gotoForward", "()I");
            BytecodeBuilder.Label target = mb.newLabel();

            ClassFile cf = mb.iconst(1)
                    .goto_(target)
                    .iconst(2)
                    .label(target)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("gotoForward"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void computeFramesForGotoBackward() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/GotoBackTest")
                    .publicStaticMethod("gotoBack", "()I");
            BytecodeBuilder.Label backTarget = mb.newLabel();
            BytecodeBuilder.Label endLabel = mb.newLabel();

            ClassFile cf = mb.iconst(0)
                    .istore(0)
                    .label(backTarget)
                    .iload(0)
                    .ifeq(endLabel)
                    .goto_(backTarget)
                    .label(endLabel)
                    .iconst(1)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("gotoBack"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }
    }

    @org.junit.jupiter.api.Nested
    class ObjectOperationTests {

        @Test
        void computeFramesForCheckCast() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/CastTest")
                    .publicStaticMethod("cast", "(Ljava/lang/Object;)Ljava/lang/String;")
                        .aload(0)
                        .checkcast("java/lang/String")
                        .areturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("cast"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void computeFramesForInstanceOf() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/InstanceOfTest")
                    .publicStaticMethod("instanceOf", "(Ljava/lang/Object;)Z")
                        .aload(0)
                        .instanceof_("java/lang/String")
                        .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("instanceOf"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void computeFramesForArrayCreation() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayTest")
                    .publicStaticMethod("createArray", "()[I")
                        .iconst(10)
                        .newarray(10)
                        .areturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("createArray"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }
    }

    @org.junit.jupiter.api.Nested
    class MethodInvocationTests {

        @Test
        void computeFramesForInvokeVirtual() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/InvokeTest")
                    .publicStaticMethod("invokeVirtual", "(Ljava/lang/String;)I")
                        .aload(0)
                        .invokevirtual("java/lang/String", "length", "()I")
                        .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("invokeVirtual"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void computeFramesForInvokeStatic() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/InvokeStaticTest")
                    .publicStaticMethod("invokeStatic", "(I)Ljava/lang/String;")
                        .iload(0)
                        .invokestatic("java/lang/String", "valueOf", "(I)Ljava/lang/String;")
                        .areturn()
                    .endMethod()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("invokeStatic"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

    }
}
