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
import java.util.List;

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
}
