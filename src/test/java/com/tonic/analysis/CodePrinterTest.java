package com.tonic.analysis;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.parser.constpool.structure.FieldRef;
import com.tonic.parser.constpool.structure.MethodRef;
import com.tonic.parser.constpool.structure.NameAndType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CodePrinterTest {

    private ConstPool constPool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        ClassPool pool = new ClassPool(true);
        classFile = pool.createNewClass("com/test/CodePrinterTest", 0x0001);
        constPool = classFile.getConstPool();
    }

    @Nested
    class NoOperandInstructions {

        @Test
        void printsNopInstruction() {
            byte[] code = {0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("0000: nop"));
        }

        @Test
        void printsIconstInstructions() {
            byte[] code = {
                0x02,
                0x03,
                0x04,
                0x05,
                0x06,
                0x07,
                0x08
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iconst_m1"));
            assertTrue(result.contains("iconst_0"));
            assertTrue(result.contains("iconst_1"));
            assertTrue(result.contains("iconst_2"));
            assertTrue(result.contains("iconst_3"));
            assertTrue(result.contains("iconst_4"));
            assertTrue(result.contains("iconst_5"));
        }

        @Test
        void printsAconstNull() {
            byte[] code = {0x01};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("aconst_null"));
        }

        @Test
        void printsLconstInstructions() {
            byte[] code = {0x09, 0x0A};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("lconst_0"));
            assertTrue(result.contains("lconst_1"));
        }

        @Test
        void printsFconstInstructions() {
            byte[] code = {0x0B, 0x0C, 0x0D};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("fconst_0"));
            assertTrue(result.contains("fconst_1"));
            assertTrue(result.contains("fconst_2"));
        }

        @Test
        void printsDconstInstructions() {
            byte[] code = {0x0E, 0x0F};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("dconst_0"));
            assertTrue(result.contains("dconst_1"));
        }

        @Test
        void printsReturnInstructions() {
            byte[] code = {
                (byte) 0xAC,
                (byte) 0xAD,
                (byte) 0xAE,
                (byte) 0xAF,
                (byte) 0xB0,
                (byte) 0xB1
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ireturn"));
            assertTrue(result.contains("lreturn"));
            assertTrue(result.contains("freturn"));
            assertTrue(result.contains("dreturn"));
            assertTrue(result.contains("areturn"));
            assertTrue(result.matches("(?s).*\\breturn\\b.*"));
        }
    }

    @Nested
    class ArithmeticInstructions {

        @Test
        void printsAddInstructions() {
            byte[] code = {0x60, 0x61, 0x62, 0x63};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iadd"));
            assertTrue(result.contains("ladd"));
            assertTrue(result.contains("fadd"));
            assertTrue(result.contains("dadd"));
        }

        @Test
        void printsSubInstructions() {
            byte[] code = {0x64, 0x65, 0x66, 0x67};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("isub"));
            assertTrue(result.contains("lsub"));
            assertTrue(result.contains("fsub"));
            assertTrue(result.contains("dsub"));
        }

        @Test
        void printsMulInstructions() {
            byte[] code = {0x68, 0x69, 0x6A, 0x6B};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("imul"));
            assertTrue(result.contains("lmul"));
            assertTrue(result.contains("fmul"));
            assertTrue(result.contains("dmul"));
        }

        @Test
        void printsDivInstructions() {
            byte[] code = {0x6C, 0x6D, 0x6E, 0x6F};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("idiv"));
            assertTrue(result.contains("ldiv"));
            assertTrue(result.contains("fdiv"));
            assertTrue(result.contains("ddiv"));
        }

        @Test
        void printsRemInstructions() {
            byte[] code = {0x70, 0x71, 0x72, 0x73};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("irem"));
            assertTrue(result.contains("lrem"));
            assertTrue(result.contains("frem"));
            assertTrue(result.contains("drem"));
        }

        @Test
        void printsNegInstructions() {
            byte[] code = {0x74, 0x75, 0x76, 0x77};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ineg"));
            assertTrue(result.contains("lneg"));
            assertTrue(result.contains("fneg"));
            assertTrue(result.contains("dneg"));
        }
    }

    @Nested
    class BitwiseInstructions {

        @Test
        void printsShiftInstructions() {
            byte[] code = {0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ishl"));
            assertTrue(result.contains("lshl"));
            assertTrue(result.contains("ishr"));
            assertTrue(result.contains("lshr"));
            assertTrue(result.contains("iushr"));
            assertTrue(result.contains("lushr"));
        }

        @Test
        void printsAndInstructions() {
            byte[] code = {0x7E, 0x7F};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iand"));
            assertTrue(result.contains("land"));
        }

        @Test
        void printsOrInstructions() {
            byte[] code = {(byte) 0x80, (byte) 0x81};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ior"));
            assertTrue(result.contains("lor"));
        }

        @Test
        void printsXorInstructions() {
            byte[] code = {(byte) 0x82, (byte) 0x83};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ixor"));
            assertTrue(result.contains("lxor"));
        }
    }

    @Nested
    class TypeConversionInstructions {

        @Test
        void printsIntConversions() {
            byte[] code = {
                (byte) 0x85,
                (byte) 0x86,
                (byte) 0x87,
                (byte) 0x91,
                (byte) 0x92,
                (byte) 0x93
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("i2l"));
            assertTrue(result.contains("i2f"));
            assertTrue(result.contains("i2d"));
            assertTrue(result.contains("i2b"));
            assertTrue(result.contains("i2c"));
            assertTrue(result.contains("i2s"));
        }

        @Test
        void printsLongConversions() {
            byte[] code = {
                (byte) 0x88,
                (byte) 0x89,
                (byte) 0x8A
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("l2i"));
            assertTrue(result.contains("l2f"));
            assertTrue(result.contains("l2d"));
        }

        @Test
        void printsFloatConversions() {
            byte[] code = {
                (byte) 0x8B,
                (byte) 0x8C,
                (byte) 0x8D
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("f2i"));
            assertTrue(result.contains("f2l"));
            assertTrue(result.contains("f2d"));
        }

        @Test
        void printsDoubleConversions() {
            byte[] code = {
                (byte) 0x8E,
                (byte) 0x8F,
                (byte) 0x90
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("d2i"));
            assertTrue(result.contains("d2l"));
            assertTrue(result.contains("d2f"));
        }
    }

    @Nested
    class ComparisonInstructions {

        @Test
        void printsLcmp() {
            byte[] code = {(byte) 0x94};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("lcmp"));
        }

        @Test
        void printsFcmpInstructions() {
            byte[] code = {(byte) 0x95, (byte) 0x96};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("fcmpl"));
            assertTrue(result.contains("fcmpg"));
        }

        @Test
        void printsDcmpInstructions() {
            byte[] code = {(byte) 0x97, (byte) 0x98};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("dcmpl"));
            assertTrue(result.contains("dcmpg"));
        }
    }

    @Nested
    class LoadStoreInstructions {

        @Test
        void printsILoadVariants() {
            byte[] code = {0x1A, 0x1B, 0x1C, 0x1D};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iload_0"));
            assertTrue(result.contains("iload_1"));
            assertTrue(result.contains("iload_2"));
            assertTrue(result.contains("iload_3"));
        }

        @Test
        void printsLLoadVariants() {
            byte[] code = {0x1E, 0x1F, 0x20, 0x21};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("lload_0"));
            assertTrue(result.contains("lload_1"));
            assertTrue(result.contains("lload_2"));
            assertTrue(result.contains("lload_3"));
        }

        @Test
        void printsFLoadVariants() {
            byte[] code = {0x22, 0x23, 0x24, 0x25};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("fload_0"));
            assertTrue(result.contains("fload_1"));
            assertTrue(result.contains("fload_2"));
            assertTrue(result.contains("fload_3"));
        }

        @Test
        void printsDLoadVariants() {
            byte[] code = {0x26, 0x27, 0x28, 0x29};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("dload_0"));
            assertTrue(result.contains("dload_1"));
            assertTrue(result.contains("dload_2"));
            assertTrue(result.contains("dload_3"));
        }

        @Test
        void printsALoadVariants() {
            byte[] code = {0x2A, 0x2B, 0x2C, 0x2D};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("aload_0"));
            assertTrue(result.contains("aload_1"));
            assertTrue(result.contains("aload_2"));
            assertTrue(result.contains("aload_3"));
        }

        @Test
        void printsIStoreVariants() {
            byte[] code = {0x3B, 0x3C, 0x3D, 0x3E};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("istore_0"));
            assertTrue(result.contains("istore_1"));
            assertTrue(result.contains("istore_2"));
            assertTrue(result.contains("istore_3"));
        }

        @Test
        void printsLStoreVariants() {
            byte[] code = {0x3F, 0x40, 0x41, 0x42};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("lstore_0"));
            assertTrue(result.contains("lstore_1"));
            assertTrue(result.contains("lstore_2"));
            assertTrue(result.contains("lstore_3"));
        }

        @Test
        void printsFStoreVariants() {
            byte[] code = {0x43, 0x44, 0x45, 0x46};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("fstore_0"));
            assertTrue(result.contains("fstore_1"));
            assertTrue(result.contains("fstore_2"));
            assertTrue(result.contains("fstore_3"));
        }

        @Test
        void printsDStoreVariants() {
            byte[] code = {0x47, 0x48, 0x49, 0x4A};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("dstore_0"));
            assertTrue(result.contains("dstore_1"));
            assertTrue(result.contains("dstore_2"));
            assertTrue(result.contains("dstore_3"));
        }

        @Test
        void printsAStoreVariants() {
            byte[] code = {0x4B, 0x4C, 0x4D, 0x4E};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("astore_0"));
            assertTrue(result.contains("astore_1"));
            assertTrue(result.contains("astore_2"));
            assertTrue(result.contains("astore_3"));
        }

        @Test
        void printsLoadWithIndex() {
            byte[] code = {0x15, 0x05};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iload"));
            assertTrue(result.contains("5"));
        }

        @Test
        void printsStoreWithIndex() {
            byte[] code = {0x36, 0x03};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("istore"));
            assertTrue(result.contains("3"));
        }
    }

    @Nested
    class ArrayInstructions {

        @Test
        void printsArrayLoadInstructions() {
            byte[] code = {0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iaload"));
            assertTrue(result.contains("laload"));
            assertTrue(result.contains("faload"));
            assertTrue(result.contains("daload"));
            assertTrue(result.contains("aaload"));
            assertTrue(result.contains("baload"));
            assertTrue(result.contains("caload"));
            assertTrue(result.contains("saload"));
        }

        @Test
        void printsArrayStoreInstructions() {
            byte[] code = {0x4F, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iastore"));
            assertTrue(result.contains("lastore"));
            assertTrue(result.contains("fastore"));
            assertTrue(result.contains("dastore"));
            assertTrue(result.contains("aastore"));
            assertTrue(result.contains("bastore"));
            assertTrue(result.contains("castore"));
            assertTrue(result.contains("sastore"));
        }

        @Test
        void printsArrayLength() {
            byte[] code = {(byte) 0xBE};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("arraylength"));
        }

        @Test
        void printsNewArrayWithType() {
            byte[] code = {(byte) 0xBC, 0x0A};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("newarray"));
            assertTrue(result.contains("int"));
        }

        @Test
        void printsNewArrayBoolean() {
            byte[] code = {(byte) 0xBC, 0x04};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("boolean"));
        }

        @Test
        void printsNewArrayChar() {
            byte[] code = {(byte) 0xBC, 0x05};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("char"));
        }

        @Test
        void printsNewArrayFloat() {
            byte[] code = {(byte) 0xBC, 0x06};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("float"));
        }

        @Test
        void printsNewArrayDouble() {
            byte[] code = {(byte) 0xBC, 0x07};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("double"));
        }

        @Test
        void printsNewArrayByte() {
            byte[] code = {(byte) 0xBC, 0x08};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("byte"));
        }

        @Test
        void printsNewArrayShort() {
            byte[] code = {(byte) 0xBC, 0x09};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("short"));
        }

        @Test
        void printsNewArrayLong() {
            byte[] code = {(byte) 0xBC, 0x0B};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("long"));
        }

        @Test
        void printsNewArrayUnknownType() {
            byte[] code = {(byte) 0xBC, (byte) 0xFF};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("unknown_atype_255"));
        }
    }

    @Nested
    class StackManipulationInstructions {

        @Test
        void printsPop() {
            byte[] code = {0x57};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("pop"));
        }

        @Test
        void printsPop2() {
            byte[] code = {0x58};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("pop2"));
        }

        @Test
        void printsDup() {
            byte[] code = {0x59};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("dup"));
        }

        @Test
        void printsDupX1() {
            byte[] code = {0x5A};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("dup_x1"));
        }

        @Test
        void printsDupX2() {
            byte[] code = {0x5B};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("dup_x2"));
        }

        @Test
        void printsDup2() {
            byte[] code = {0x5C};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("dup2"));
        }

        @Test
        void printsDup2X1() {
            byte[] code = {0x5D};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("dup2_x1"));
        }

        @Test
        void printsDup2X2() {
            byte[] code = {0x5E};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("dup2_x2"));
        }

        @Test
        void printsSwap() {
            byte[] code = {0x5F};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("swap"));
        }
    }

    @Nested
    class ConstantLoadInstructions {

        @Test
        void printsBipush() {
            byte[] code = {0x10, 0x7F};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("bipush"));
            assertTrue(result.contains("127"));
        }

        @Test
        void printsBipushNegative() {
            byte[] code = {0x10, (byte) 0xFF};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("bipush"));
            assertTrue(result.contains("-1"));
        }

        @Test
        void printsSipush() {
            byte[] code = {0x11, 0x01, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("sipush"));
            assertTrue(result.contains("256"));
        }

        @Test
        void printsLdc() {
            Utf8Item utf8 = constPool.findOrAddUtf8("test");
            StringRefItem stringRef = constPool.findOrAddString("test");
            int index = constPool.getIndexOf(stringRef);

            byte[] code = {0x12, (byte) index};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ldc"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("\"test\""));
        }

        @Test
        void printsLdcW() {
            Utf8Item utf8 = constPool.findOrAddUtf8("wide test");
            StringRefItem stringRef = constPool.findOrAddString("wide test");
            int index = constPool.getIndexOf(stringRef);

            byte[] code = {0x13, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ldc_w"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("\"wide test\""));
        }

        @Test
        void printsLdc2W() {
            DoubleItem doubleItem = constPool.findOrAddDouble(3.14159);
            int index = constPool.getIndexOf(doubleItem);

            byte[] code = {0x14, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ldc2_w"));
            assertTrue(result.contains("#" + index));
        }
    }

    @Nested
    class FieldAccessInstructions {

        @Test
        void printsGetStatic() {
            FieldRefItem fieldRef = constPool.findOrAddFieldRef("java/lang/System", "out", "Ljava/io/PrintStream;");
            int index = constPool.getIndexOf(fieldRef);

            byte[] code = {(byte) 0xB2, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("getstatic"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("java.lang.System.out"));
        }

        @Test
        void printsPutStatic() {
            FieldRefItem fieldRef = constPool.findOrAddFieldRef("com/test/MyClass", "counter", "I");
            int index = constPool.getIndexOf(fieldRef);

            byte[] code = {(byte) 0xB3, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("putstatic"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("com.test.MyClass.counter"));
        }

        @Test
        void printsGetField() {
            FieldRefItem fieldRef = constPool.findOrAddFieldRef("com/test/MyClass", "value", "I");
            int index = constPool.getIndexOf(fieldRef);

            byte[] code = {(byte) 0xB4, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("getfield"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("com.test.MyClass.value"));
        }

        @Test
        void printsPutField() {
            FieldRefItem fieldRef = constPool.findOrAddFieldRef("com/test/MyClass", "data", "Ljava/lang/String;");
            int index = constPool.getIndexOf(fieldRef);

            byte[] code = {(byte) 0xB5, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("putfield"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("com.test.MyClass.data"));
        }
    }

    @Nested
    class MethodInvocationInstructions {

        @Test
        void printsInvokeVirtual() {
            MethodRefItem methodRef = constPool.findOrAddMethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
            int index = constPool.getIndexOf(methodRef);

            byte[] code = {(byte) 0xB6, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("invokevirtual"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("java.io.PrintStream.println"));
        }

        @Test
        void printsInvokeSpecial() {
            MethodRefItem methodRef = constPool.findOrAddMethodRef("java/lang/Object", "<init>", "()V");
            int index = constPool.getIndexOf(methodRef);

            byte[] code = {(byte) 0xB7, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("invokespecial"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("java.lang.Object.<init>"));
        }

        @Test
        void printsInvokeStatic() {
            MethodRefItem methodRef = constPool.findOrAddMethodRef("java/lang/Math", "sqrt", "(D)D");
            int index = constPool.getIndexOf(methodRef);

            byte[] code = {(byte) 0xB8, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("invokestatic"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("java.lang.Math.sqrt"));
        }

        @Test
        void printsInvokeInterface() {
            InterfaceRefItem interfaceRef = constPool.findOrAddInterfaceRef("java/util/List", "size", "()I");
            int index = constPool.getIndexOf(interfaceRef);

            byte[] code = {(byte) 0xB9, (byte) (index >> 8), (byte) (index & 0xFF), 0x01, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("invokeinterface"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("count=1"));
            assertTrue(result.contains("zero=0"));
        }

        @Test
        void printsInvokeDynamic() {
            byte[] code = {(byte) 0xBA, 0x00, 0x01, 0x00, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("invokedynamic"));
            assertTrue(result.contains("#1"));
            assertTrue(result.contains("InvokeDynamic"));
        }
    }

    @Nested
    class ObjectCreationInstructions {

        @Test
        void printsNew() {
            ClassRefItem classRef = constPool.findOrAddClass("java/lang/StringBuilder");
            int index = constPool.getIndexOf(classRef);

            byte[] code = {(byte) 0xBB, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("new"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("java.lang.StringBuilder"));
        }

        @Test
        void printsAnewArray() {
            ClassRefItem classRef = constPool.findOrAddClass("java/lang/String");
            int index = constPool.getIndexOf(classRef);

            byte[] code = {(byte) 0xBD, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("anewarray"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("java.lang.String"));
        }

        @Test
        void printsMultiAnewArray() {
            ClassRefItem classRef = constPool.findOrAddClass("[[I");
            int index = constPool.getIndexOf(classRef);

            byte[] code = {(byte) 0xC5, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("multianewarray"));
            assertTrue(result.contains("#" + index));
        }
    }

    @Nested
    class TypeCheckInstructions {

        @Test
        void printsCheckCast() {
            ClassRefItem classRef = constPool.findOrAddClass("java/lang/String");
            int index = constPool.getIndexOf(classRef);

            byte[] code = {(byte) 0xC0, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("checkcast"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("java.lang.String"));
        }

        @Test
        void printsInstanceOf() {
            ClassRefItem classRef = constPool.findOrAddClass("java/lang/Number");
            int index = constPool.getIndexOf(classRef);

            byte[] code = {(byte) 0xC1, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("instanceof"));
            assertTrue(result.contains("#" + index));
            assertTrue(result.contains("java.lang.Number"));
        }
    }

    @Nested
    class BranchInstructions {

        @Test
        void printsIfEq() {
            byte[] code = {(byte) 0x99, 0x00, 0x03};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ifeq"));
            assertTrue(result.contains("3"));
        }

        @Test
        void printsIfNe() {
            byte[] code = {(byte) 0x9A, 0x00, 0x05};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ifne"));
            assertTrue(result.contains("5"));
        }

        @Test
        void printsIfLt() {
            byte[] code = {(byte) 0x9B, 0x00, 0x07};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iflt"));
            assertTrue(result.contains("7"));
        }

        @Test
        void printsIfGe() {
            byte[] code = {(byte) 0x9C, 0x00, 0x09};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ifge"));
            assertTrue(result.contains("9"));
        }

        @Test
        void printsIfGt() {
            byte[] code = {(byte) 0x9D, 0x00, 0x0B};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ifgt"));
            assertTrue(result.contains("11"));
        }

        @Test
        void printsIfLe() {
            byte[] code = {(byte) 0x9E, 0x00, 0x0D};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ifle"));
            assertTrue(result.contains("13"));
        }

        @Test
        void printsIfIcmpEq() {
            byte[] code = {(byte) 0x9F, 0x00, 0x06};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("if_icmpeq"));
            assertTrue(result.contains("6"));
        }

        @Test
        void printsIfIcmpNe() {
            byte[] code = {(byte) 0xA0, 0x00, 0x08};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("if_icmpne"));
            assertTrue(result.contains("8"));
        }

        @Test
        void printsIfIcmpLt() {
            byte[] code = {(byte) 0xA1, 0x00, 0x0A};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("if_icmplt"));
            assertTrue(result.contains("10"));
        }

        @Test
        void printsIfIcmpGe() {
            byte[] code = {(byte) 0xA2, 0x00, 0x0C};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("if_icmpge"));
            assertTrue(result.contains("12"));
        }

        @Test
        void printsIfIcmpGt() {
            byte[] code = {(byte) 0xA3, 0x00, 0x0E};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("if_icmpgt"));
            assertTrue(result.contains("14"));
        }

        @Test
        void printsIfIcmpLe() {
            byte[] code = {(byte) 0xA4, 0x00, 0x10};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("if_icmple"));
            assertTrue(result.contains("16"));
        }

        @Test
        void printsIfAcmpEq() {
            byte[] code = {(byte) 0xA5, 0x00, 0x12};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("if_acmpeq"));
            assertTrue(result.contains("18"));
        }

        @Test
        void printsIfAcmpNe() {
            byte[] code = {(byte) 0xA6, 0x00, 0x14};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("if_acmpne"));
            assertTrue(result.contains("20"));
        }

        @Test
        void printsGoto() {
            byte[] code = {(byte) 0xA7, 0x00, 0x05};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("goto"));
            assertTrue(result.contains("5"));
        }

        @Test
        void printsJsr() {
            byte[] code = {(byte) 0xA8, 0x00, 0x10};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("jsr"));
            assertTrue(result.contains("16"));
        }

        @Test
        void printsRet() {
            byte[] code = {(byte) 0xA9, 0x01};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ret"));
            assertTrue(result.contains("1"));
        }

        @Test
        void printsIfNull() {
            byte[] code = {(byte) 0xC6, 0x00, 0x03};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ifnull"));
            assertTrue(result.contains("3"));
        }

        @Test
        void printsIfNonNull() {
            byte[] code = {(byte) 0xC7, 0x00, 0x03};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ifnonnull"));
            assertTrue(result.contains("3"));
        }

        @Test
        void printsGotoW() {
            byte[] code = {(byte) 0xC8, 0x00, 0x00, 0x10, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("goto_w"));
            assertTrue(result.contains("4096"));
        }

        @Test
        void printsJsrW() {
            byte[] code = {(byte) 0xC9, 0x00, 0x00, 0x20, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("jsr_w"));
            assertTrue(result.contains("8192"));
        }
    }

    @Nested
    class SwitchInstructions {

        @Test
        void printsTableSwitch() {
            byte[] code = {
                (byte) 0xAA,
                0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x10,
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x03,
                0x00, 0x00, 0x00, 0x05,
                0x00, 0x00, 0x00, 0x07,
                0x00, 0x00, 0x00, 0x09
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("tableswitch"));
            assertTrue(result.contains("default=16"));
            assertTrue(result.contains("low=1"));
            assertTrue(result.contains("high=3"));
            assertTrue(result.contains("count=3"));
            assertTrue(result.contains("case[1]"));
            assertTrue(result.contains("case[2]"));
            assertTrue(result.contains("case[3]"));
        }

        @Test
        void printsTableSwitchWithPadding() {
            byte[] code = {
                0x00,
                (byte) 0xAA,
                0x00, 0x00,
                0x00, 0x00, 0x00, 0x0C,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x04,
                0x00, 0x00, 0x00, 0x08
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("tableswitch"));
            assertTrue(result.contains("default=12"));
            assertTrue(result.contains("low=0"));
            assertTrue(result.contains("high=1"));
        }

        @Test
        void printsLookupSwitch() {
            byte[] code = {
                (byte) 0xAB,
                0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x14,
                0x00, 0x00, 0x00, 0x02,
                0x00, 0x00, 0x00, 0x0A,
                0x00, 0x00, 0x00, 0x05,
                0x00, 0x00, 0x00, 0x14,
                0x00, 0x00, 0x00, 0x09
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("lookupswitch"));
            assertTrue(result.contains("default=20"));
            assertTrue(result.contains("npairs=2"));
            assertTrue(result.contains("match=10"));
            assertTrue(result.contains("match=20"));
        }

        @Test
        void printsLookupSwitchWithPadding() {
            byte[] code = {
                0x00, 0x00,
                (byte) 0xAB,
                0x00,
                0x00, 0x00, 0x00, 0x08,
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x05,
                0x00, 0x00, 0x00, 0x03
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("lookupswitch"));
            assertTrue(result.contains("default=8"));
            assertTrue(result.contains("npairs=1"));
            assertTrue(result.contains("match=5"));
        }
    }

    @Nested
    class WideInstructions {

        @Test
        void printsWideILoad() {
            byte[] code = {(byte) 0xC4, 0x15, 0x01, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("iload"));
            assertTrue(result.contains("256"));
        }

        @Test
        void printsWideLLoad() {
            byte[] code = {(byte) 0xC4, 0x16, 0x00, (byte) 0xFF};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("lload"));
            assertTrue(result.contains("255"));
        }

        @Test
        void printsWideFLoad() {
            byte[] code = {(byte) 0xC4, 0x17, 0x02, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("fload"));
            assertTrue(result.contains("512"));
        }

        @Test
        void printsWideDLoad() {
            byte[] code = {(byte) 0xC4, 0x18, 0x03, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("dload"));
            assertTrue(result.contains("768"));
        }

        @Test
        void printsWideALoad() {
            byte[] code = {(byte) 0xC4, 0x19, 0x04, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("aload"));
            assertTrue(result.contains("1024"));
        }

        @Test
        void printsWideIStore() {
            byte[] code = {(byte) 0xC4, 0x36, 0x01, 0x50};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("istore"));
            assertTrue(result.contains("336"));
        }

        @Test
        void printsWideLStore() {
            byte[] code = {(byte) 0xC4, 0x37, 0x02, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("lstore"));
            assertTrue(result.contains("512"));
        }

        @Test
        void printsWideFStore() {
            byte[] code = {(byte) 0xC4, 0x38, 0x03, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("fstore"));
            assertTrue(result.contains("768"));
        }

        @Test
        void printsWideDStore() {
            byte[] code = {(byte) 0xC4, 0x39, 0x04, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("dstore"));
            assertTrue(result.contains("1024"));
        }

        @Test
        void printsWideAStore() {
            byte[] code = {(byte) 0xC4, 0x3A, 0x05, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("astore"));
            assertTrue(result.contains("1280"));
        }

        @Test
        void printsWideIInc() {
            byte[] code = {(byte) 0xC4, (byte) 0x84, 0x01, 0x00, 0x00, 0x0A};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("iinc"));
            assertTrue(result.contains("256"));
            assertTrue(result.contains("10"));
        }
    }

    @Nested
    class MiscellaneousInstructions {

        @Test
        void printsIInc() {
            byte[] code = {(byte) 0x84, 0x01, 0x05};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iinc"));
            assertTrue(result.contains("1"));
            assertTrue(result.contains("5"));
        }

        @Test
        void printsAThrow() {
            byte[] code = {(byte) 0xBF};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("athrow"));
        }

        @Test
        void printsMonitorEnter() {
            byte[] code = {(byte) 0xC2};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("monitorenter"));
        }

        @Test
        void printsMonitorExit() {
            byte[] code = {(byte) 0xC3};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("monitorexit"));
        }

        @Test
        void printsBreakpoint() {
            byte[] code = {(byte) 0xCA};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("breakpoint"));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void handlesEmptyBytecode() {
            byte[] code = {};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertEquals("", result);
        }

        @Test
        void handlesInvalidBipush() {
            byte[] code = {0x10};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("bipush"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidSipush() {
            byte[] code = {0x11, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("sipush"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidLdc() {
            byte[] code = {0x12};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("ldc"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidFieldRef() {
            byte[] code = {(byte) 0xB2, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("getstatic"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidInvokeInterface() {
            byte[] code = {(byte) 0xB9, 0x00, 0x01};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("invokeinterface"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidInvokeDynamic() {
            byte[] code = {(byte) 0xBA, 0x00, 0x01};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("invokedynamic"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidNewArray() {
            byte[] code = {(byte) 0xBC};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("newarray"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidIInc() {
            byte[] code = {(byte) 0x84, 0x01};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iinc"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidWide() {
            byte[] code = {(byte) 0xC4};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidWideLoad() {
            byte[] code = {(byte) 0xC4, 0x15, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("iload"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidWideIInc() {
            byte[] code = {(byte) 0xC4, (byte) 0x84, 0x00, 0x00, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("iinc"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesInvalidGotoW() {
            byte[] code = {(byte) 0xC8, 0x00, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("goto_w"));
            assertTrue(result.contains("<invalid>"));
        }

        @Test
        void handlesUnknownOpcode() {
            byte[] code = {(byte) 0xFE};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("<unknown opcode"));
            assertTrue(result.contains("0xFE"));
        }

        @Test
        void handlesUnsupportedWideOpcode() {
            byte[] code = {(byte) 0xC4, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("wide"));
            assertTrue(result.contains("<unsupported>"));
        }
    }

    @Nested
    class ConstantPoolReferences {

        @Test
        void resolvesStringReference() {
            Utf8Item utf8 = constPool.findOrAddUtf8("Hello, World!");
            StringRefItem stringRef = constPool.findOrAddString("Hello, World!");
            int index = constPool.getIndexOf(stringRef);

            byte[] code = {0x12, (byte) index};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("\"Hello, World!\""));
        }

        @Test
        void resolvesClassReference() {
            ClassRefItem classRef = constPool.findOrAddClass("com/example/MyClass");
            int index = constPool.getIndexOf(classRef);

            byte[] code = {(byte) 0xBB, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("com.example.MyClass"));
        }

        @Test
        void resolvesMethodReference() {
            MethodRefItem methodRef = constPool.findOrAddMethodRef("com/example/Calculator", "add", "(II)I");
            int index = constPool.getIndexOf(methodRef);

            byte[] code = {(byte) 0xB8, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("com.example.Calculator.add"));
            assertTrue(result.contains("(II)I"));
        }

        @Test
        void resolvesFieldReference() {
            FieldRefItem fieldRef = constPool.findOrAddFieldRef("com/example/Config", "DEBUG", "Z");
            int index = constPool.getIndexOf(fieldRef);

            byte[] code = {(byte) 0xB2, (byte) (index >> 8), (byte) (index & 0xFF)};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("com.example.Config.DEBUG"));
            assertTrue(result.contains("Z"));
        }
    }

    @Nested
    class FormattingTests {

        @Test
        void formatsInstructionOffsets() {
            byte[] code = {0x00, 0x01, 0x02};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("0000:"));
            assertTrue(result.contains("0001:"));
            assertTrue(result.contains("0002:"));
        }

        @Test
        void formatsMultiByteInstructionOffsets() {
            byte[] code = {0x10, 0x7F, 0x11, 0x01, 0x00};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("0000:"));
            assertTrue(result.contains("0002:"));
        }

        @Test
        void includesNewlinesBetweenInstructions() {
            byte[] code = {0x00, 0x01};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            String[] lines = result.split("\n");
            assertTrue(lines.length >= 2);
        }

        @Test
        void alignsInstructionMnemonics() {
            byte[] code = {0x00, 0x01, 0x02};
            String result = CodePrinter.prettyPrintCode(code, constPool);

            String[] lines = result.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    assertTrue(line.matches("\\d{4}:.*"));
                }
            }
        }
    }

    @Nested
    class ComplexBytecodeSequences {

        @Test
        void printsSimpleMethodBytecode() {
            byte[] code = {
                0x03,
                (byte) 0xAC
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iconst_0"));
            assertTrue(result.contains("ireturn"));
        }

        @Test
        void printsArithmeticSequence() {
            byte[] code = {
                0x1A,
                0x1B,
                0x60,
                (byte) 0xAC
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("iload_0"));
            assertTrue(result.contains("iload_1"));
            assertTrue(result.contains("iadd"));
            assertTrue(result.contains("ireturn"));
        }

        @Test
        void printsFieldAccessSequence() {
            FieldRefItem fieldRef = constPool.findOrAddFieldRef("java/lang/System", "out", "Ljava/io/PrintStream;");
            MethodRefItem methodRef = constPool.findOrAddMethodRef("java/io/PrintStream", "println", "(I)V");
            int fieldIndex = constPool.getIndexOf(fieldRef);
            int methodIndex = constPool.getIndexOf(methodRef);

            byte[] code = {
                (byte) 0xB2, (byte) (fieldIndex >> 8), (byte) (fieldIndex & 0xFF),
                0x05,
                (byte) 0xB6, (byte) (methodIndex >> 8), (byte) (methodIndex & 0xFF),
                (byte) 0xB1
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("getstatic"));
            assertTrue(result.contains("iconst_2"));
            assertTrue(result.contains("invokevirtual"));
            assertTrue(result.matches("(?s).*\\breturn\\b.*"));
        }

        @Test
        void printsObjectCreationSequence() {
            ClassRefItem classRef = constPool.findOrAddClass("java/lang/StringBuilder");
            MethodRefItem ctorRef = constPool.findOrAddMethodRef("java/lang/StringBuilder", "<init>", "()V");
            int classIndex = constPool.getIndexOf(classRef);
            int ctorIndex = constPool.getIndexOf(ctorRef);

            byte[] code = {
                (byte) 0xBB, (byte) (classIndex >> 8), (byte) (classIndex & 0xFF),
                0x59,
                (byte) 0xB7, (byte) (ctorIndex >> 8), (byte) (ctorIndex & 0xFF),
                0x4C,
                (byte) 0xB1
            };
            String result = CodePrinter.prettyPrintCode(code, constPool);

            assertTrue(result.contains("new"));
            assertTrue(result.contains("dup"));
            assertTrue(result.contains("invokespecial"));
            assertTrue(result.contains("astore_1"));
            assertTrue(result.matches("(?s).*\\breturn\\b.*"));
        }
    }
}
