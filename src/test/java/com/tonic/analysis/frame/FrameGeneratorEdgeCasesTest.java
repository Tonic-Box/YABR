package com.tonic.analysis.frame;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.stack.FullFrame;
import com.tonic.parser.attribute.stack.StackMapFrame;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FrameGeneratorEdgeCasesTest {

    private ClassPool pool;
    private ClassFile classFile;
    private ConstPool constPool;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/FrameEdgeCases", access);
        constPool = classFile.getConstPool();
    }

    @Nested
    class BranchTargetEdgeCases {

        @Test
        void testFindFrameTargetsWithNegativeBranchOffset() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/NegativeBranch")
                    .publicStaticMethod("negativeBranch", "()V");
            BytecodeBuilder.Label backLabel = mb.newLabel();

            ClassFile cf = mb.label(backLabel)
                    .iconst(1)
                    .pop()
                    .iconst(0)
                    .ifne(backLabel)
                    .vreturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("negativeBranch"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void testTableSwitchWithNegativeDefaultOffset() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/TableSwitchNeg")
                    .publicStaticMethod("tableSwitchNeg", "(I)I");
            BytecodeBuilder.Label beforeSwitch = mb.newLabel();
            BytecodeBuilder.Label case0 = mb.newLabel();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(0, case0);

            ClassFile cf = mb.label(beforeSwitch)
                    .iload(0)
                    .tableswitch(0, 0, cases, beforeSwitch)
                    .label(case0)
                    .iconst(1)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("tableSwitchNeg"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void testLookupSwitchWithNegativeMatchOffset() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/LookupSwitchNeg")
                    .publicStaticMethod("lookupSwitchNeg", "(I)I");
            BytecodeBuilder.Label beforeSwitch = mb.newLabel();
            BytecodeBuilder.Label defaultCase = mb.newLabel();

            Map<Integer, BytecodeBuilder.Label> cases = new HashMap<>();
            cases.put(5, beforeSwitch);

            ClassFile cf = mb.label(beforeSwitch)
                    .iload(0)
                    .lookupswitch(cases, defaultCase)
                    .iconst(5)
                    .ireturn()
                    .label(defaultCase)
                    .iconst(0)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("lookupSwitchNeg"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }
    }

    @Nested
    class TypeStateComputationEdgeCases {

        @Test
        void testComputeTypeStatesWithMergingPaths() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/MergePaths")
                    .publicStaticMethod("mergePaths", "(I)I");
            BytecodeBuilder.Label path1 = mb.newLabel();
            BytecodeBuilder.Label path2 = mb.newLabel();
            BytecodeBuilder.Label merge = mb.newLabel();

            ClassFile cf = mb.iload(0)
                    .ifeq(path1)
                    .label(path2)
                    .iconst(1)
                    .goto_(merge)
                    .label(path1)
                    .iconst(2)
                    .label(merge)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("mergePaths"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
            assertTrue(frames.size() > 0);
        }

        @Test
        void testComputeTypeStatesWithBackwardBranch() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/BackwardBranch")
                    .publicStaticMethod("backwardBranch", "(I)I");
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
                    .filter(m -> m.getName().equals("backwardBranch"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
            assertTrue(frames.size() > 0);
        }

        @Test
        void testComputeTypeStatesWithUnreachableCode() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/Unreachable")
                    .publicStaticMethod("unreachable", "()I");

            ClassFile cf = mb.iconst(1)
                    .ireturn()
                    .iconst(2)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("unreachable"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void testComputeTypeStatesWithMultipleExceptionHandlers() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/MultipleHandlers")
                    .publicStaticMethod("multipleHandlers", "()V");
            BytecodeBuilder.Label tryStart = mb.newLabel();
            BytecodeBuilder.Label tryEnd = mb.newLabel();
            BytecodeBuilder.Label handler1 = mb.newLabel();
            BytecodeBuilder.Label handler2 = mb.newLabel();
            BytecodeBuilder.Label handler3 = mb.newLabel();
            BytecodeBuilder.Label afterAll = mb.newLabel();

            ClassFile cf = mb.label(tryStart)
                    .iconst(1)
                    .pop()
                    .label(tryEnd)
                    .goto_(afterAll)
                    .label(handler1)
                    .pop()
                    .goto_(afterAll)
                    .label(handler2)
                    .pop()
                    .goto_(afterAll)
                    .label(handler3)
                    .pop()
                    .label(afterAll)
                    .vreturn()
                    .tryCatch(tryStart, tryEnd, handler1, "java/lang/RuntimeException")
                    .tryCatch(tryStart, tryEnd, handler2, "java/io/IOException")
                    .tryCatch(tryStart, tryEnd, handler3, "java/lang/Exception")
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("multipleHandlers"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }
    }

    @Nested
    class WorklistAlgorithmEdgeCases {

        @Test
        void testWorklistWithConvergingPaths() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/Converge")
                    .publicStaticMethod("converge", "(I)I");
            BytecodeBuilder.Label path1 = mb.newLabel();
            BytecodeBuilder.Label path2 = mb.newLabel();
            BytecodeBuilder.Label merge = mb.newLabel();

            ClassFile cf = mb.iload(0)
                    .iconst(1)
                    .if_icmpeq(path1)
                    .iload(0)
                    .iconst(2)
                    .if_icmpeq(path2)
                    .iconst(3)
                    .goto_(merge)
                    .label(path1)
                    .iconst(1)
                    .goto_(merge)
                    .label(path2)
                    .iconst(2)
                    .label(merge)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("converge"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }

        @Test
        void testWorklistWithNestedLoops() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/NestedLoops")
                    .publicStaticMethod("nestedLoops", "(I)I");
            BytecodeBuilder.Label outerLoop = mb.newLabel();
            BytecodeBuilder.Label innerLoop = mb.newLabel();
            BytecodeBuilder.Label innerEnd = mb.newLabel();
            BytecodeBuilder.Label outerEnd = mb.newLabel();

            ClassFile cf = mb.iconst(0)
                    .istore(1)
                    .label(outerLoop)
                    .iload(1)
                    .iload(0)
                    .if_icmpge(outerEnd)
                    .iconst(0)
                    .istore(2)
                    .label(innerLoop)
                    .iload(2)
                    .iload(0)
                    .if_icmpge(innerEnd)
                    .iinc(2, 1)
                    .goto_(innerLoop)
                    .label(innerEnd)
                    .iinc(1, 1)
                    .goto_(outerLoop)
                    .label(outerEnd)
                    .iload(1)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("nestedLoops"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }
    }

    @Nested
    class OpcodeClassificationEdgeCases {

        @Test
        void testIsTerminatorForAllReturnTypes() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/AllReturns")
                    .publicStaticMethod("ireturn", "()I")
                        .iconst(1)
                        .ireturn()
                    .endMethod()
                    .publicStaticMethod("lreturn", "()J")
                        .lconst(1L)
                        .lreturn()
                    .endMethod()
                    .publicStaticMethod("freturn", "()F")
                        .fconst(1.0f)
                        .freturn()
                    .endMethod()
                    .publicStaticMethod("dreturn", "()D")
                        .dconst(1.0)
                        .dreturn()
                    .endMethod()
                    .publicStaticMethod("areturn", "()Ljava/lang/String;")
                        .aconst_null()
                        .areturn()
                    .endMethod()
                    .publicStaticMethod("vreturn", "()V")
                        .vreturn()
                    .endMethod()
                    .build();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());

            for (MethodEntry method : cf.getMethods()) {
                if (!method.getName().equals("<init>")) {
                    List<StackMapFrame> frames = generator.computeFrames(method);
                    assertNotNull(frames);
                }
            }
        }
    }

    @Nested
    class OffsetDeltaCalculationEdgeCases {

        @Test
        void testOffsetDeltaForFirstFrame() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/FirstFrame")
                    .publicStaticMethod("firstFrame", "(I)I");
            BytecodeBuilder.Label target = mb.newLabel();

            ClassFile cf = mb.iload(0)
                    .ifne(target)
                    .iconst(0)
                    .ireturn()
                    .label(target)
                    .iconst(1)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("firstFrame"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
            if (!frames.isEmpty()) {
                assertTrue(frames.get(0) instanceof FullFrame);
            }
        }

        @Test
        void testOffsetDeltaForConsecutiveFrames() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/ConsecutiveFrames")
                    .publicStaticMethod("consecutiveFrames", "(I)I");
            BytecodeBuilder.Label target1 = mb.newLabel();
            BytecodeBuilder.Label target2 = mb.newLabel();
            BytecodeBuilder.Label target3 = mb.newLabel();

            ClassFile cf = mb.iload(0)
                    .iconst(1)
                    .if_icmpeq(target1)
                    .iload(0)
                    .iconst(2)
                    .if_icmpeq(target2)
                    .iload(0)
                    .iconst(3)
                    .if_icmpeq(target3)
                    .iconst(0)
                    .ireturn()
                    .label(target1)
                    .iconst(1)
                    .ireturn()
                    .label(target2)
                    .iconst(2)
                    .ireturn()
                    .label(target3)
                    .iconst(3)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("consecutiveFrames"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            List<StackMapFrame> frames = generator.computeFrames(method);

            assertNotNull(frames);
        }
    }

    @Nested
    class UpdateStackMapTableEdgeCases {

        @Test
        void testUpdateStackMapTableWithExistingFrames() throws IOException {
            BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/ExistingFrames")
                    .publicStaticMethod("existingFrames", "(I)I");
            BytecodeBuilder.Label target = mb.newLabel();

            ClassFile cf = mb.iload(0)
                    .ifne(target)
                    .iconst(0)
                    .ireturn()
                    .label(target)
                    .iconst(1)
                    .ireturn()
                    .build();

            MethodEntry method = cf.getMethods().stream()
                    .filter(m -> m.getName().equals("existingFrames"))
                    .findFirst().orElseThrow();

            FrameGenerator generator = new FrameGenerator(cf.getConstPool());
            generator.updateStackMapTable(method);
            generator.updateStackMapTable(method);

            assertNotNull(method.getCodeAttribute());
        }
    }
}
