package com.tonic.builder;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeBuilderTest {

    @Nested
    class LoadAndStoreInstructions {

        @Test
        void iloadAndIstore() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .istore(1)
                .iload(1)
                .ireturn()
            );

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(insns.size() >= 4);
        }

        @Test
        void aloadAndAstore() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .astore(1)
                .aload(1)
                .areturn()
            , "(Ljava/lang/Object;)Ljava/lang/Object;");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(insns.size() >= 4);
        }

        @Test
        void lloadAndLstore() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lstore(2)
                .lload(2)
                .lreturn()
            , "(J)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(insns.size() >= 4);
        }
    }

    @Nested
    class ArithmeticInstructions {

        @Test
        void integerArithmetic() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .iadd()
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void integerSubtraction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .isub()
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void integerMultiplication() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .imul()
                .ireturn()
            , "(II)I");

            assertNotNull(cf);
        }
    }

    @Nested
    class StackManipulationInstructions {

        @Test
        void dupInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(5)
                .dup()
                .iadd()
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, DupInstruction.class));
        }

        @Test
        void popInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(5)
                .pop()
                .iconst(10)
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, PopInstruction.class));
        }

        @Test
        void pop2Instruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lconst(5)
                .pop2()
                .iconst(10)
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, Pop2Instruction.class));
        }

        @Test
        void swapInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(1)
                .iconst(2)
                .swap()
                .isub()
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, SwapInstruction.class));
        }

        @Test
        void dup_x1Instruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(1)
                .iconst(2)
                .dup_x1()
                .pop()
                .iadd()
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, DupInstruction.class));
        }

        @Test
        void dup_x2Instruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(1)
                .iconst(2)
                .iconst(3)
                .dup_x2()
                .pop()
                .iadd()
                .iadd()
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, DupInstruction.class));
        }

        @Test
        void dup2Instruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lconst(5)
                .dup2()
                .ladd()
                .lreturn()
            , "()J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, DupInstruction.class));
        }

        @Test
        void dup2_x1Instruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(1)
                .lconst(2)
                .dup2_x1()
                .pop2()
                .pop()
                .lreturn()
            , "()J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, DupInstruction.class));
        }

        @Test
        void dup2_x2Instruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lconst(1)
                .lconst(2)
                .dup2_x2()
                .pop2()
                .ladd()
                .lreturn()
            , "()J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, DupInstruction.class));
        }
    }

    @Nested
    class BranchInstructions {

        @Test
        void gotoInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .goto_("end")
                .iconst(0)
                .label("end")
                .iconst(1)
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, GotoInstruction.class));
        }

        @Test
        void ifeqInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .ifeq("zero")
                .iconst(1)
                .ireturn()
                .label("zero")
                .iconst(0)
                .ireturn()
            , "(I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void ifneInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .ifne("nonzero")
                .iconst(0)
                .ireturn()
                .label("nonzero")
                .iconst(1)
                .ireturn()
            , "(I)I");

            assertNotNull(cf);
        }

        @Test
        void ifltInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iflt("negative")
                .iconst(0)
                .ireturn()
                .label("negative")
                .iconst(1)
                .ireturn()
            , "(I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void ifgeInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .ifge("nonnegative")
                .iconst(0)
                .ireturn()
                .label("nonnegative")
                .iconst(1)
                .ireturn()
            , "(I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void ifgtInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .ifgt("positive")
                .iconst(0)
                .ireturn()
                .label("positive")
                .iconst(1)
                .ireturn()
            , "(I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void ifleInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .ifle("nonpositive")
                .iconst(0)
                .ireturn()
                .label("nonpositive")
                .iconst(1)
                .ireturn()
            , "(I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void if_icmpltInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .if_icmplt("lessthan")
                .iconst(0)
                .ireturn()
                .label("lessthan")
                .iconst(1)
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void if_icmpgeInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .if_icmpge("greaterequal")
                .iconst(0)
                .ireturn()
                .label("greaterequal")
                .iconst(1)
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void if_icmpgtInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .if_icmpgt("greater")
                .iconst(0)
                .ireturn()
                .label("greater")
                .iconst(1)
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void if_icmpleInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .if_icmple("lessequal")
                .iconst(0)
                .ireturn()
                .label("lessequal")
                .iconst(1)
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void if_icmpeqInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .if_icmpeq("equal")
                .iconst(0)
                .ireturn()
                .label("equal")
                .iconst(1)
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void if_icmpneInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .if_icmpne("notequal")
                .iconst(0)
                .ireturn()
                .label("notequal")
                .iconst(1)
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void if_acmpeqInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .aload(1)
                .if_acmpeq("same")
                .iconst(0)
                .ireturn()
                .label("same")
                .iconst(1)
                .ireturn()
            , "(Ljava/lang/Object;Ljava/lang/Object;)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void if_acmpneInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .aload(1)
                .if_acmpne("different")
                .iconst(0)
                .ireturn()
                .label("different")
                .iconst(1)
                .ireturn()
            , "(Ljava/lang/Object;Ljava/lang/Object;)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void ifnullInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .ifnull("isnull")
                .iconst(0)
                .ireturn()
                .label("isnull")
                .iconst(1)
                .ireturn()
            , "(Ljava/lang/Object;)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }

        @Test
        void ifnonnullInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .ifnonnull("notnull")
                .iconst(0)
                .ireturn()
                .label("notnull")
                .iconst(1)
                .ireturn()
            , "(Ljava/lang/Object;)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConditionalBranchInstruction.class));
        }
    }

    @Nested
    class MethodInvocationInstructions {

        @Test
        void invokestaticInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .invokestatic("java/lang/Math", "abs", "(I)I")
                .ireturn()
            , "(I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, InvokeStaticInstruction.class));
        }

        @Test
        void invokevirtualInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .invokevirtual("java/lang/Object", "hashCode", "()I")
                .ireturn()
            , "(Ljava/lang/Object;)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, InvokeVirtualInstruction.class));
        }

        @Test
        void invokespecialInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .invokespecial("java/lang/Object", "<init>", "()V")
                .vreturn()
            , "(Ljava/lang/Object;)V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, InvokeSpecialInstruction.class));
        }

        @Test
        void invokeinterfaceInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .invokeinterface("java/util/List", "size", "()I")
                .ireturn()
            , "(Ljava/util/List;)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, InvokeInterfaceInstruction.class));
        }
    }

    @Nested
    class FieldAccessInstructions {

        @Test
        void getstaticInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
                .areturn()
            , "()Ljava/io/PrintStream;");

            assertNotNull(cf);
        }

        @Test
        void getfieldInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .getfield("com/test/TestClass", "value", "I")
                .ireturn()
            , "(Lcom/test/TestClass;)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, GetFieldInstruction.class));
        }

        @Test
        void putfieldInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(42)
                .putfield("com/test/TestClass", "value", "I")
                .vreturn()
            , "(Lcom/test/TestClass;)V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, PutFieldInstruction.class));
        }

        @Test
        void putstaticInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(100)
                .putstatic("com/test/TestClass", "staticValue", "I")
                .vreturn()
            , "()V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, PutFieldInstruction.class));
        }
    }

    @Nested
    class ArrayInstructions {

        @Test
        void newarrayInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(10)
                .newarray(AccessFlags.T_INT)
                .areturn()
            , "()[I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, NewArrayInstruction.class));
        }
    }

    @Nested
    class ConstantInstructions {

        @Test
        void iconstInstructions() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(0)
                .iconst(5)
                .iadd()
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, IConstInstruction.class));
        }

        @Test
        void bipushInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .bipush(100)
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, BipushInstruction.class));
        }

        @Test
        void sipushInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .sipush(1000)
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, SipushInstruction.class));
        }

        @Test
        void aconstNullInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aconst_null()
                .areturn()
            , "()Ljava/lang/Object;");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, AConstNullInstruction.class));
        }
    }

    @Nested
    class ReturnInstructions {

        @Test
        void vreturnInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb.vreturn(), "()V");
            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ReturnInstruction.class));
        }

        @Test
        void ireturnInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb.iconst(0).ireturn(), "()I");
            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ReturnInstruction.class));
        }

        @Test
        void areturnInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb.aconst_null().areturn(), "()Ljava/lang/Object;");
            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ReturnInstruction.class));
        }
    }

    @Nested
    class LongOperations {

        @Test
        void lconstInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lconst(0)
                .lreturn()
            , "()J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, LConstInstruction.class));
        }

        @Test
        void lloadAndLstoreInstructions() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lstore(2)
                .lload(2)
                .lreturn()
            , "(J)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(insns.size() >= 4);
        }

        @Test
        void laddInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lload(2)
                .ladd()
                .lreturn()
            , "(JJ)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void lsubInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lload(2)
                .lsub()
                .lreturn()
            , "(JJ)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void lmulInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lload(2)
                .lmul()
                .lreturn()
            , "(JJ)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void ldivInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lload(2)
                .ldiv()
                .lreturn()
            , "(JJ)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void lremInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lload(2)
                .lrem()
                .lreturn()
            , "(JJ)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void lnegInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lneg()
                .lreturn()
            , "(J)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, LNegInstruction.class));
        }

        @Test
        void lreturnInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lconst(0)
                .lreturn()
            , "()J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ReturnInstruction.class));
        }
    }

    @Nested
    class FloatOperations {

        @Test
        void fconstInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fconst(0)
                .freturn()
            , "()F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, FConstInstruction.class));
        }

        @Test
        void floadAndFstoreInstructions() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .fstore(1)
                .fload(1)
                .freturn()
            , "(F)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(insns.size() >= 4);
        }

        @Test
        void faddInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .fload(1)
                .fadd()
                .freturn()
            , "(FF)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void fsubInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .fload(1)
                .fsub()
                .freturn()
            , "(FF)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void fmulInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .fload(1)
                .fmul()
                .freturn()
            , "(FF)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void fdivInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .fload(1)
                .fdiv()
                .freturn()
            , "(FF)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void fremInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .fload(1)
                .frem()
                .freturn()
            , "(FF)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void fnegInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .fneg()
                .freturn()
            , "(F)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, FNegInstruction.class));
        }

        @Test
        void freturnInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fconst(0)
                .freturn()
            , "()F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ReturnInstruction.class));
        }
    }

    @Nested
    class DoubleOperations {

        @Test
        void dconstInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dconst(0)
                .dreturn()
            , "()D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, DConstInstruction.class));
        }

        @Test
        void dloadAndDstoreInstructions() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .dstore(2)
                .dload(2)
                .dreturn()
            , "(D)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(insns.size() >= 4);
        }

        @Test
        void daddInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .dload(2)
                .dadd()
                .dreturn()
            , "(DD)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void dsubInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .dload(2)
                .dsub()
                .dreturn()
            , "(DD)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void dmulInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .dload(2)
                .dmul()
                .dreturn()
            , "(DD)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void ddivInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .dload(2)
                .ddiv()
                .dreturn()
            , "(DD)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void dremInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .dload(2)
                .drem()
                .dreturn()
            , "(DD)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticInstruction.class));
        }

        @Test
        void dnegInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .dneg()
                .dreturn()
            , "(D)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, DNegInstruction.class));
        }

        @Test
        void dreturnInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dconst(0)
                .dreturn()
            , "()D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ReturnInstruction.class));
        }
    }

    @Nested
    class ArrayOperations {

        @Test
        void ialoadInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .iaload()
                .ireturn()
            , "([I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, IALoadInstruction.class));
        }

        @Test
        void laloadInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .laload()
                .lreturn()
            , "([J)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, LALoadInstruction.class));
        }

        @Test
        void faloadInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .faload()
                .freturn()
            , "([F)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, FALoadInstruction.class));
        }

        @Test
        void daloadInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .daload()
                .dreturn()
            , "([D)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, DALoadInstruction.class));
        }

        @Test
        void aaloadInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .aaload()
                .areturn()
            , "([Ljava/lang/Object;)Ljava/lang/Object;");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, AALoadInstruction.class));
        }

        @Test
        void baloadInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .baload()
                .ireturn()
            , "([B)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, BALOADInstruction.class));
        }

        @Test
        void caloadInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .caload()
                .ireturn()
            , "([C)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, CALoadInstruction.class));
        }

        @Test
        void saloadInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .saload()
                .ireturn()
            , "([S)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, SALoadInstruction.class));
        }

        @Test
        void iastoreInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .iconst(42)
                .iastore()
                .vreturn()
            , "([I)V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, IAStoreInstruction.class));
        }

        @Test
        void lastoreInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .lconst(42)
                .lastore()
                .vreturn()
            , "([J)V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, LAStoreInstruction.class));
        }

        @Test
        void fastoreInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .fconst(1)
                .fastore()
                .vreturn()
            , "([F)V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, FAStoreInstruction.class));
        }

        @Test
        void dastoreInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .dconst(1)
                .dastore()
                .vreturn()
            , "([D)V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, DAStoreInstruction.class));
        }

        @Test
        void aastoreInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .aconst_null()
                .aastore()
                .vreturn()
            , "([Ljava/lang/Object;)V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, AAStoreInstruction.class));
        }

        @Test
        void bastoreInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .iconst(1)
                .bastore()
                .vreturn()
            , "([B)V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, BAStoreInstruction.class));
        }

        @Test
        void castoreInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .iconst(65)
                .castore()
                .vreturn()
            , "([C)V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, CAStoreInstruction.class));
        }

        @Test
        void sastoreInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .iconst(0)
                .iconst(1)
                .sastore()
                .vreturn()
            , "([S)V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, SAStoreInstruction.class));
        }

        @Test
        void arraylengthInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .arraylength()
                .ireturn()
            , "([I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArrayLengthInstruction.class));
        }
    }

    @Nested
    class BitwiseOperations {

        @Test
        void iandInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .iand()
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, IAndInstruction.class));
        }

        @Test
        void iorInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .ior()
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, IOrInstruction.class));
        }

        @Test
        void ixorInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .ixor()
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, IXorInstruction.class));
        }

        @Test
        void ishlInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .ishl()
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticShiftInstruction.class));
        }

        @Test
        void ishrInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .ishr()
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticShiftInstruction.class));
        }

        @Test
        void iushrInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .iload(1)
                .iushr()
                .ireturn()
            , "(II)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticShiftInstruction.class));
        }

        @Test
        void landInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lload(2)
                .land()
                .lreturn()
            , "(JJ)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, IAndInstruction.class));
        }

        @Test
        void lorInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lload(2)
                .lor()
                .lreturn()
            , "(JJ)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, IOrInstruction.class));
        }

        @Test
        void lxorInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lload(2)
                .lxor()
                .lreturn()
            , "(JJ)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, IXorInstruction.class));
        }

        @Test
        void lshlInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .iload(2)
                .lshl()
                .lreturn()
            , "(JI)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticShiftInstruction.class));
        }

        @Test
        void lshrInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .iload(2)
                .lshr()
                .lreturn()
            , "(JI)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticShiftInstruction.class));
        }

        @Test
        void lushrInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .iload(2)
                .lushr()
                .lreturn()
            , "(JI)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ArithmeticShiftInstruction.class));
        }
    }

    @Nested
    class TypeConversions {

        @Test
        void i2lInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .i2l()
                .lreturn()
            , "(I)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, I2LInstruction.class));
        }

        @Test
        void i2fInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .i2f()
                .freturn()
            , "(I)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }

        @Test
        void i2dInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .i2d()
                .dreturn()
            , "(I)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }

        @Test
        void i2bInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .i2b()
                .ireturn()
            , "(I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, NarrowingConversionInstruction.class));
        }

        @Test
        void i2cInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .i2c()
                .ireturn()
            , "(I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, NarrowingConversionInstruction.class));
        }

        @Test
        void i2sInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .i2s()
                .ireturn()
            , "(I)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, NarrowingConversionInstruction.class));
        }

        @Test
        void l2iInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .l2i()
                .ireturn()
            , "(J)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }

        @Test
        void l2fInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .l2f()
                .freturn()
            , "(J)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }

        @Test
        void l2dInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .l2d()
                .dreturn()
            , "(J)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }

        @Test
        void f2iInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .f2i()
                .ireturn()
            , "(F)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }

        @Test
        void f2lInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .f2l()
                .lreturn()
            , "(F)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }

        @Test
        void f2dInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .f2d()
                .dreturn()
            , "(F)D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }

        @Test
        void d2iInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .d2i()
                .ireturn()
            , "(D)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }

        @Test
        void d2lInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .d2l()
                .lreturn()
            , "(D)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }

        @Test
        void d2fInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .d2f()
                .freturn()
            , "(D)F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ConversionInstruction.class));
        }
    }

    @Nested
    class ComparisonOperations {

        @Test
        void lcmpInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .lload(0)
                .lload(2)
                .lcmp()
                .ireturn()
            , "(JJ)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, CompareInstruction.class));
        }

        @Test
        void fcmplInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .fload(1)
                .fcmpl()
                .ireturn()
            , "(FF)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, CompareInstruction.class));
        }

        @Test
        void fcmpgInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .fload(0)
                .fload(1)
                .fcmpg()
                .ireturn()
            , "(FF)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, CompareInstruction.class));
        }

        @Test
        void dcmplInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .dload(2)
                .dcmpl()
                .ireturn()
            , "(DD)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, CompareInstruction.class));
        }

        @Test
        void dcmpgInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .dload(0)
                .dload(2)
                .dcmpg()
                .ireturn()
            , "(DD)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, CompareInstruction.class));
        }
    }

    @Nested
    class TypeConversionInstructions {

        @Test
        void i2lInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iload(0)
                .i2l()
                .lreturn()
            , "(I)J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, I2LInstruction.class));
        }
    }

    @Nested
    class ObjectOperations {

        @Test
        void newInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .new_("java/lang/Object")
                .dup()
                .invokespecial("java/lang/Object", "<init>", "()V")
                .areturn()
            , "()Ljava/lang/Object;");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, NewInstruction.class));
        }

        @Test
        void anewarrayInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(5)
                .anewarray("java/lang/String")
                .areturn()
            , "()[Ljava/lang/String;");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ANewArrayInstruction.class));
        }

        @Test
        void multianewarrayInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(3)
                .iconst(4)
                .multianewarray("[[I", 2)
                .areturn()
            , "()[[I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, MultiANewArrayInstruction.class));
        }

        @Test
        void checkcastInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .checkcast("java/lang/String")
                .areturn()
            , "(Ljava/lang/Object;)Ljava/lang/String;");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, CheckCastInstruction.class));
        }

        @Test
        void instanceofInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .instanceof_("java/lang/String")
                .ireturn()
            , "(Ljava/lang/Object;)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, InstanceOfInstruction.class));
        }
    }

    @Nested
    class ExceptionHandling {

        @Test
        void trycatchWithException() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .trycatch("start", "end", "handler", "java/lang/Exception")
                .label("start")
                .iconst(42)
                .istore(1)
                .label("end")
                .goto_("after")
                .label("handler")
                .astore(2)
                .iconst(0)
                .istore(1)
                .label("after")
                .iload(1)
                .ireturn()
            , "()I");

            assertNotNull(cf);
        }

        @Test
        void athrowInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .new_("java/lang/RuntimeException")
                .dup()
                .invokespecial("java/lang/RuntimeException", "<init>", "()V")
                .athrow()
            , "()V");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, ATHROWInstruction.class));
        }

        @Test
        void trycatchWithNullExceptionType() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .trycatch("start", "end", "handler", null)
                .label("start")
                .iconst(42)
                .istore(1)
                .label("end")
                .goto_("after")
                .label("handler")
                .astore(2)
                .iconst(0)
                .istore(1)
                .label("after")
                .iload(1)
                .ireturn()
            , "()I");

            assertNotNull(cf);
        }
    }

    @Nested
    class SynchronizationInstructions {

        @Test
        void monitorenterAndMonitorexit() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .aload(0)
                .dup()
                .monitorenter()
                .iconst(42)
                .istore(1)
                .monitorexit()
                .iload(1)
                .ireturn()
            , "(Ljava/lang/Object;)I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, MonitorEnterInstruction.class));
            assertTrue(containsOpcodeType(insns, MonitorExitInstruction.class));
        }

        @Test
        void nopInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .nop()
                .iconst(1)
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, NopInstruction.class));
        }
    }

    @Nested
    class MiscInstructions {

        @Test
        void iincInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .iconst(10)
                .istore(1)
                .iinc(1, 5)
                .iload(1)
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, IIncInstruction.class));
        }

        @Test
        void ldcStringInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .ldc("test string")
                .areturn()
            , "()Ljava/lang/String;");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, LdcInstruction.class));
        }

        @Test
        void ldcIntegerInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .ldc(12345)
                .ireturn()
            , "()I");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, SipushInstruction.class));
        }

        @Test
        void ldcLongInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .ldc(123456789L)
                .lreturn()
            , "()J");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, Ldc2WInstruction.class));
        }

        @Test
        void ldcFloatInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .ldc(3.14f)
                .freturn()
            , "()F");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, LdcInstruction.class));
        }

        @Test
        void ldcDoubleInstruction() {
            ClassFile cf = buildMethodWithCode(cb -> cb
                .ldc(3.14159)
                .dreturn()
            , "()D");

            List<Instruction> insns = getInstructions(cf, "test");
            assertTrue(containsOpcodeType(insns, Ldc2WInstruction.class));
        }

        @Test
        void ldcUnsupportedTypeThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                buildMethodWithCode(cb -> cb
                    .ldc(new Object())
                    .areturn()
                , "()Ljava/lang/Object;");
            });
        }
    }

    private ClassFile buildMethodWithCode(java.util.function.Consumer<CodeBuilder> codeConsumer) {
        return buildMethodWithCode(codeConsumer, "()I");
    }

    private ClassFile buildMethodWithCode(java.util.function.Consumer<CodeBuilder> codeConsumer, String descriptor) {
        ClassBuilder cb = ClassBuilder.create("com/test/CodeTest");
        MethodBuilder mb = cb.addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "test", descriptor);
        CodeBuilder code = mb.code();
        codeConsumer.accept(code);
        code.end();
        mb.end();
        return cb.build();
    }

    private List<Instruction> getInstructions(ClassFile cf, String methodName) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName)) {
                Bytecode bc = new Bytecode(method);
                List<Instruction> result = new ArrayList<>();
                for (Instruction insn : bc.getCodeWriter().getInstructions()) {
                    result.add(insn);
                }
                return result;
            }
        }
        return List.of();
    }

    private boolean containsOpcodeType(List<Instruction> insns, Class<? extends Instruction> type) {
        for (Instruction insn : insns) {
            if (type.isInstance(insn)) {
                return true;
            }
        }
        return false;
    }
}
