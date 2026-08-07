package com.tonic.util;

import java.util.Arrays;

/**
 * Enum representing all JVM opcodes as per Java 11 Specification.
 */
public enum Opcode
{
    /**
     * Does nothing.
     */
    NOP(0x00, "nop", 0),
    /**
     * Pushes a null reference onto the operand stack.
     */
    ACONST_NULL(0x01, "aconst_null", 0),
    /**
     * Pushes the int constant -1 onto the operand stack.
     */
    ICONST_M1(0x02, "iconst_m1", 0),
    /**
     * Pushes the int constant 0 onto the operand stack.
     */
    ICONST_0(0x03, "iconst_0", 0),
    /**
     * Pushes the int constant 1 onto the operand stack.
     */
    ICONST_1(0x04, "iconst_1", 0),
    /**
     * Pushes the int constant 2 onto the operand stack.
     */
    ICONST_2(0x05, "iconst_2", 0),
    /**
     * Pushes the int constant 3 onto the operand stack.
     */
    ICONST_3(0x06, "iconst_3", 0),
    /**
     * Pushes the int constant 4 onto the operand stack.
     */
    ICONST_4(0x07, "iconst_4", 0),
    /**
     * Pushes the int constant 5 onto the operand stack.
     */
    ICONST_5(0x08, "iconst_5", 0),
    /**
     * Pushes the long constant 0 onto the operand stack.
     */
    LCONST_0(0x09, "lconst_0", 0),
    /**
     * Pushes the long constant 1 onto the operand stack.
     */
    LCONST_1(0x0A, "lconst_1", 0),
    /**
     * Pushes the float constant 0.0f onto the operand stack.
     */
    FCONST_0(0x0B, "fconst_0", 0),
    /**
     * Pushes the float constant 1.0f onto the operand stack.
     */
    FCONST_1(0x0C, "fconst_1", 0),
    /**
     * Pushes the float constant 2.0f onto the operand stack.
     */
    FCONST_2(0x0D, "fconst_2", 0),
    /**
     * Pushes the double constant 0.0 onto the operand stack.
     */
    DCONST_0(0x0E, "dconst_0", 0),
    /**
     * Pushes the double constant 1.0 onto the operand stack.
     */
    DCONST_1(0x0F, "dconst_1", 0),
    /**
     * Sign-extends the single-byte operand to an int and pushes it onto the operand stack.
     */
    BIPUSH(0x10, "bipush", 1),
    /**
     * Sign-extends the two-byte operand to an int and pushes it onto the operand stack.
     */
    SIPUSH(0x11, "sipush", 2),
    /**
     * Pushes a one-word constant - int, float, String, class, method type or method handle -
     * from the constant pool entry named by the one-byte index.
     */
    LDC(0x12, "ldc", 1),
    /**
     * Wide form of LDC using a two-byte constant pool index, reaching entries above index 255.
     */
    LDC_W(0x13, "ldc_w", 2),
    /**
     * Pushes the long or double held in the constant pool entry named by the two-byte index,
     * occupying two operand stack slots.
     */
    LDC2_W(0x14, "ldc2_w", 2),
    /**
     * Loads an int from the local variable at the given index onto the operand stack.
     */
    ILOAD(0x15, "iload", 1),
    /**
     * Loads a long from the local variable at the given index onto the operand stack.
     */
    LLOAD(0x16, "lload", 1),
    /**
     * Loads a float from the local variable at the given index onto the operand stack.
     */
    FLOAD(0x17, "fload", 1),
    /**
     * Loads a double from the local variable at the given index onto the operand stack.
     */
    DLOAD(0x18, "dload", 1),
    /**
     * Loads a reference from the local variable at the given index onto the operand stack.
     */
    ALOAD(0x19, "aload", 1),
    /**
     * Loads the int in local variable 0 onto the operand stack.
     */
    ILOAD_0(0x1A, "iload_0", 0),
    /**
     * Loads the int in local variable 1 onto the operand stack.
     */
    ILOAD_1(0x1B, "iload_1", 0),
    /**
     * Loads the int in local variable 2 onto the operand stack.
     */
    ILOAD_2(0x1C, "iload_2", 0),
    /**
     * Loads the int in local variable 3 onto the operand stack.
     */
    ILOAD_3(0x1D, "iload_3", 0),
    /**
     * Loads the long in local variable 0 onto the operand stack.
     */
    LLOAD_0(0x1E, "lload_0", 0),
    /**
     * Loads the long in local variable 1 onto the operand stack.
     */
    LLOAD_1(0x1F, "lload_1", 0),
    /**
     * Loads the long in local variable 2 onto the operand stack.
     */
    LLOAD_2(0x20, "lload_2", 0),
    /**
     * Loads the long in local variable 3 onto the operand stack.
     */
    LLOAD_3(0x21, "lload_3", 0),
    /**
     * Loads the float in local variable 0 onto the operand stack.
     */
    FLOAD_0(0x22, "fload_0", 0),
    /**
     * Loads the float in local variable 1 onto the operand stack.
     */
    FLOAD_1(0x23, "fload_1", 0),
    /**
     * Loads the float in local variable 2 onto the operand stack.
     */
    FLOAD_2(0x24, "fload_2", 0),
    /**
     * Loads the float in local variable 3 onto the operand stack.
     */
    FLOAD_3(0x25, "fload_3", 0),
    /**
     * Loads the double in local variable 0 onto the operand stack.
     */
    DLOAD_0(0x26, "dload_0", 0),
    /**
     * Loads the double in local variable 1 onto the operand stack.
     */
    DLOAD_1(0x27, "dload_1", 0),
    /**
     * Loads the double in local variable 2 onto the operand stack.
     */
    DLOAD_2(0x28, "dload_2", 0),
    /**
     * Loads the double in local variable 3 onto the operand stack.
     */
    DLOAD_3(0x29, "dload_3", 0),
    /**
     * Loads the reference in local variable 0 onto the operand stack; in an instance method
     * this is the receiver.
     */
    ALOAD_0(0x2A, "aload_0", 0),
    /**
     * Loads the reference in local variable 1 onto the operand stack.
     */
    ALOAD_1(0x2B, "aload_1", 0),
    /**
     * Loads the reference in local variable 2 onto the operand stack.
     */
    ALOAD_2(0x2C, "aload_2", 0),
    /**
     * Loads the reference in local variable 3 onto the operand stack.
     */
    ALOAD_3(0x2D, "aload_3", 0),
    /**
     * Pops an array reference and an int index, and pushes the int element stored there.
     */
    IALOAD(0x2E, "iaload", 0),
    /**
     * Pops an array reference and an int index, and pushes the long element stored there.
     */
    LALOAD(0x2F, "laload", 0),
    /**
     * Pops an array reference and an int index, and pushes the float element stored there.
     */
    FALOAD(0x30, "faload", 0),
    /**
     * Pops an array reference and an int index, and pushes the double element stored there.
     */
    DALOAD(0x31, "daload", 0),
    /**
     * Pops an array reference and an int index, and pushes the reference element stored there.
     */
    AALOAD(0x32, "aaload", 0),
    /**
     * Pops a byte or boolean array reference and an int index, and pushes the element
     * sign-extended to an int.
     */
    BALOAD(0x33, "baload", 0),
    /**
     * Pops a char array reference and an int index, and pushes the element zero-extended to an int.
     */
    CALOAD(0x34, "caload", 0),
    /**
     * Pops a short array reference and an int index, and pushes the element sign-extended to an int.
     */
    SALOAD(0x35, "saload", 0),
    /**
     * Pops an int and stores it in the local variable at the given index.
     */
    ISTORE(0x36, "istore", 1),
    /**
     * Pops a long and stores it in the local variable at the given index.
     */
    LSTORE(0x37, "lstore", 1),
    /**
     * Pops a float and stores it in the local variable at the given index.
     */
    FSTORE(0x38, "fstore", 1),
    /**
     * Pops a double and stores it in the local variable at the given index.
     */
    DSTORE(0x39, "dstore", 1),
    /**
     * Pops a reference or return address and stores it in the local variable at the given index.
     */
    ASTORE(0x3A, "astore", 1),
    /**
     * Pops an int and stores it in local variable 0.
     */
    ISTORE_0(0x3B, "istore_0", 0),
    /**
     * Pops an int and stores it in local variable 1.
     */
    ISTORE_1(0x3C, "istore_1", 0),
    /**
     * Pops an int and stores it in local variable 2.
     */
    ISTORE_2(0x3D, "istore_2", 0),
    /**
     * Pops an int and stores it in local variable 3.
     */
    ISTORE_3(0x3E, "istore_3", 0),
    /**
     * Pops a long and stores it in local variable 0.
     */
    LSTORE_0(0x3F, "lstore_0", 0),
    /**
     * Pops a long and stores it in local variable 1.
     */
    LSTORE_1(0x40, "lstore_1", 0),
    /**
     * Pops a long and stores it in local variable 2.
     */
    LSTORE_2(0x41, "lstore_2", 0),
    /**
     * Pops a long and stores it in local variable 3.
     */
    LSTORE_3(0x42, "lstore_3", 0),
    /**
     * Pops a float and stores it in local variable 0.
     */
    FSTORE_0(0x43, "fstore_0", 0),
    /**
     * Pops a float and stores it in local variable 1.
     */
    FSTORE_1(0x44, "fstore_1", 0),
    /**
     * Pops a float and stores it in local variable 2.
     */
    FSTORE_2(0x45, "fstore_2", 0),
    /**
     * Pops a float and stores it in local variable 3.
     */
    FSTORE_3(0x46, "fstore_3", 0),
    /**
     * Pops a double and stores it in local variable 0.
     */
    DSTORE_0(0x47, "dstore_0", 0),
    /**
     * Pops a double and stores it in local variable 1.
     */
    DSTORE_1(0x48, "dstore_1", 0),
    /**
     * Pops a double and stores it in local variable 2.
     */
    DSTORE_2(0x49, "dstore_2", 0),
    /**
     * Pops a double and stores it in local variable 3.
     */
    DSTORE_3(0x4A, "dstore_3", 0),
    /**
     * Pops a reference or return address and stores it in local variable 0.
     */
    ASTORE_0(0x4B, "astore_0", 0),
    /**
     * Pops a reference or return address and stores it in local variable 1.
     */
    ASTORE_1(0x4C, "astore_1", 0),
    /**
     * Pops a reference or return address and stores it in local variable 2.
     */
    ASTORE_2(0x4D, "astore_2", 0),
    /**
     * Pops a reference or return address and stores it in local variable 3.
     */
    ASTORE_3(0x4E, "astore_3", 0),
    /**
     * Pops an array reference, an int index and an int value, and stores the value at that index.
     */
    IASTORE(0x4F, "iastore", 0),
    /**
     * Pops an array reference, an int index and a long value, and stores the value at that index.
     */
    LASTORE(0x50, "lastore", 0),
    /**
     * Pops an array reference, an int index and a float value, and stores the value at that index.
     */
    FASTORE(0x51, "fastore", 0),
    /**
     * Pops an array reference, an int index and a double value, and stores the value at that index.
     */
    DASTORE(0x52, "dastore", 0),
    /**
     * Pops an array reference, an int index and a reference value, and stores the value at that index.
     */
    AASTORE(0x53, "aastore", 0),
    /**
     * Pops a byte or boolean array reference, an int index and an int value, and stores the value
     * truncated to a byte.
     */
    BASTORE(0x54, "bastore", 0),
    /**
     * Pops a char array reference, an int index and an int value, and stores the value truncated
     * to a char.
     */
    CASTORE(0x55, "castore", 0),
    /**
     * Pops a short array reference, an int index and an int value, and stores the value truncated
     * to a short.
     */
    SASTORE(0x56, "sastore", 0),
    /**
     * Discards the top value of the operand stack; the value must not be a long or double.
     */
    POP(0x57, "pop", 0),
    /**
     * Discards either the top two one-word values or a single long or double.
     */
    POP2(0x58, "pop2", 0),
    /**
     * Duplicates the top value of the operand stack; the value must not be a long or double.
     */
    DUP(0x59, "dup", 0),
    /**
     * Duplicates the top one-word value and inserts the copy beneath the value below it.
     */
    DUP_X1(0x5A, "dup_x1", 0),
    /**
     * Duplicates the top one-word value and inserts the copy beneath the next two one-word values,
     * or beneath a single long or double.
     */
    DUP_X2(0x5B, "dup_x2", 0),
    /**
     * Duplicates either the top two one-word values or a single long or double.
     */
    DUP2(0x5C, "dup2", 0),
    /**
     * Duplicates the top one or two words and inserts the copy beneath the one-word value below them.
     */
    DUP2_X1(0x5D, "dup2_x1", 0),
    /**
     * Duplicates the top one or two words and inserts the copy beneath the two words below them.
     */
    DUP2_X2(0x5E, "dup2_x2", 0),
    /**
     * Exchanges the top two values on the operand stack; neither may be a long or double.
     */
    SWAP(0x5F, "swap", 0),
    /**
     * Pops two ints and pushes their sum, wrapping around on overflow.
     */
    IADD(0x60, "iadd", 0),
    /**
     * Pops two longs and pushes their sum, wrapping around on overflow.
     */
    LADD(0x61, "ladd", 0),
    /**
     * Pops two floats and pushes their IEEE 754 sum.
     */
    FADD(0x62, "fadd", 0),
    /**
     * Pops two doubles and pushes their IEEE 754 sum.
     */
    DADD(0x63, "dadd", 0),
    /**
     * Pops two ints and pushes the lower one minus the upper one.
     */
    ISUB(0x64, "isub", 0),
    /**
     * Pops two longs and pushes the lower one minus the upper one.
     */
    LSUB(0x65, "lsub", 0),
    /**
     * Pops two floats and pushes the lower one minus the upper one.
     */
    FSUB(0x66, "fsub", 0),
    /**
     * Pops two doubles and pushes the lower one minus the upper one.
     */
    DSUB(0x67, "dsub", 0),
    /**
     * Pops two ints and pushes their product, wrapping around on overflow.
     */
    IMUL(0x68, "imul", 0),
    /**
     * Pops two longs and pushes their product, wrapping around on overflow.
     */
    LMUL(0x69, "lmul", 0),
    /**
     * Pops two floats and pushes their IEEE 754 product.
     */
    FMUL(0x6A, "fmul", 0),
    /**
     * Pops two doubles and pushes their IEEE 754 product.
     */
    DMUL(0x6B, "dmul", 0),
    /**
     * Pops two ints and pushes the quotient truncated toward zero; throws ArithmeticException
     * if the divisor is zero.
     */
    IDIV(0x6C, "idiv", 0),
    /**
     * Pops two longs and pushes the quotient truncated toward zero; throws ArithmeticException
     * if the divisor is zero.
     */
    LDIV(0x6D, "ldiv", 0),
    /**
     * Pops two floats and pushes their IEEE 754 quotient; division by zero yields an infinity
     * or NaN rather than an exception.
     */
    FDIV(0x6E, "fdiv", 0),
    /**
     * Pops two doubles and pushes their IEEE 754 quotient; division by zero yields an infinity
     * or NaN rather than an exception.
     */
    DDIV(0x6F, "ddiv", 0),
    /**
     * Pops two ints and pushes the remainder of the division, taking the sign of the dividend;
     * throws ArithmeticException if the divisor is zero.
     */
    IREM(0x70, "irem", 0),
    /**
     * Pops two longs and pushes the remainder of the division, taking the sign of the dividend;
     * throws ArithmeticException if the divisor is zero.
     */
    LREM(0x71, "lrem", 0),
    /**
     * Pops two floats and pushes the remainder of a truncating division, taking the sign of the dividend.
     */
    FREM(0x72, "frem", 0),
    /**
     * Pops two doubles and pushes the remainder of a truncating division, taking the sign of the dividend.
     */
    DREM(0x73, "drem", 0),
    /**
     * Pops an int and pushes its arithmetic negation.
     */
    INEG(0x74, "ineg", 0),
    /**
     * Pops a long and pushes its arithmetic negation.
     */
    LNEG(0x75, "lneg", 0),
    /**
     * Pops a float and pushes it with its sign bit flipped, which also negates zeros and NaN.
     */
    FNEG(0x76, "fneg", 0),
    /**
     * Pops a double and pushes it with its sign bit flipped, which also negates zeros and NaN.
     */
    DNEG(0x77, "dneg", 0),
    /**
     * Pops an int shift distance and an int value, and pushes the value shifted left by the low
     * five bits of the distance.
     */
    ISHL(0x78, "ishl", 0),
    /**
     * Pops an int shift distance and a long value, and pushes the value shifted left by the low
     * six bits of the distance.
     */
    LSHL(0x79, "lshl", 0),
    /**
     * Pops an int shift distance and an int value, and pushes the value shifted right with sign
     * extension by the low five bits of the distance.
     */
    ISHR(0x7A, "ishr", 0),
    /**
     * Pops an int shift distance and a long value, and pushes the value shifted right with sign
     * extension by the low six bits of the distance.
     */
    LSHR(0x7B, "lshr", 0),
    /**
     * Pops an int shift distance and an int value, and pushes the value shifted right with zero
     * extension by the low five bits of the distance.
     */
    IUSHR(0x7C, "iushr", 0),
    /**
     * Pops an int shift distance and a long value, and pushes the value shifted right with zero
     * extension by the low six bits of the distance.
     */
    LUSHR(0x7D, "lushr", 0),
    /**
     * Pops two ints and pushes their bitwise AND.
     */
    IAND(0x7E, "iand", 0),
    /**
     * Pops two longs and pushes their bitwise AND.
     */
    LAND(0x7F, "land", 0),
    /**
     * Pops two ints and pushes their bitwise inclusive OR.
     */
    IOR(0x80, "ior", 0),
    /**
     * Pops two longs and pushes their bitwise inclusive OR.
     */
    LOR(0x81, "lor", 0),
    /**
     * Pops two ints and pushes their bitwise exclusive OR.
     */
    IXOR(0x82, "ixor", 0),
    /**
     * Pops two longs and pushes their bitwise exclusive OR.
     */
    LXOR(0x83, "lxor", 0),
    /**
     * Adds the signed second operand byte to the int local variable named by the first operand
     * byte, leaving the operand stack untouched.
     */
    IINC(0x84, "iinc", 2),
    /**
     * Pops an int and pushes it widened to a long.
     */
    I2L(0x85, "i2l", 0),
    /**
     * Pops an int and pushes it converted to a float, which may lose precision for large values.
     */
    I2F(0x86, "i2f", 0),
    /**
     * Pops an int and pushes it widened to a double without loss.
     */
    I2D(0x87, "i2d", 0),
    /**
     * Pops a long and pushes its low 32 bits as an int, discarding the high bits.
     */
    L2I(0x88, "l2i", 0),
    /**
     * Pops a long and pushes it converted to a float, which may lose precision.
     */
    L2F(0x89, "l2f", 0),
    /**
     * Pops a long and pushes it converted to a double, which may lose precision.
     */
    L2D(0x8A, "l2d", 0),
    /**
     * Pops a float and pushes it converted to an int, rounding toward zero; NaN becomes 0 and
     * out-of-range values saturate.
     */
    F2I(0x8B, "f2i", 0),
    /**
     * Pops a float and pushes it converted to a long, rounding toward zero; NaN becomes 0 and
     * out-of-range values saturate.
     */
    F2L(0x8C, "f2l", 0),
    /**
     * Pops a float and pushes it widened to a double without loss.
     */
    F2D(0x8D, "f2d", 0),
    /**
     * Pops a double and pushes it converted to an int, rounding toward zero; NaN becomes 0 and
     * out-of-range values saturate.
     */
    D2I(0x8E, "d2i", 0),
    /**
     * Pops a double and pushes it converted to a long, rounding toward zero; NaN becomes 0 and
     * out-of-range values saturate.
     */
    D2L(0x8F, "d2l", 0),
    /**
     * Pops a double and pushes it narrowed to a float, which may lose precision or overflow to
     * an infinity.
     */
    D2F(0x90, "d2f", 0),
    /**
     * Pops an int, truncates it to eight bits and pushes the sign-extended result as an int.
     */
    I2B(0x91, "i2b", 0),
    /**
     * Pops an int, truncates it to sixteen bits and pushes the zero-extended result as an int.
     */
    I2C(0x92, "i2c", 0),
    /**
     * Pops an int, truncates it to sixteen bits and pushes the sign-extended result as an int.
     */
    I2S(0x93, "i2s", 0),
    /**
     * Pops two longs and pushes the int -1, 0 or 1 according to whether the lower one is less
     * than, equal to or greater than the upper one.
     */
    LCMP(0x94, "lcmp", 0),
    /**
     * Pops two floats and pushes the int -1, 0 or 1 comparing them, pushing -1 when either
     * operand is NaN.
     */
    FCMPL(0x95, "fcmpl", 0),
    /**
     * Pops two floats and pushes the int -1, 0 or 1 comparing them, pushing 1 when either
     * operand is NaN.
     */
    FCMPG(0x96, "fcmpg", 0),
    /**
     * Pops two doubles and pushes the int -1, 0 or 1 comparing them, pushing -1 when either
     * operand is NaN.
     */
    DCMPL(0x97, "dcmpl", 0),
    /**
     * Pops two doubles and pushes the int -1, 0 or 1 comparing them, pushing 1 when either
     * operand is NaN.
     */
    DCMPG(0x98, "dcmpg", 0),
    /**
     * Pops an int and branches by the signed 16-bit offset if it is zero.
     */
    IFEQ(0x99, "ifeq", 2),
    /**
     * Pops an int and branches by the signed 16-bit offset if it is not zero.
     */
    IFNE(0x9A, "ifne", 2),
    /**
     * Pops an int and branches by the signed 16-bit offset if it is negative.
     */
    IFLT(0x9B, "iflt", 2),
    /**
     * Pops an int and branches by the signed 16-bit offset if it is zero or positive.
     */
    IFGE(0x9C, "ifge", 2),
    /**
     * Pops an int and branches by the signed 16-bit offset if it is positive.
     */
    IFGT(0x9D, "ifgt", 2),
    /**
     * Pops an int and branches by the signed 16-bit offset if it is zero or negative.
     */
    IFLE(0x9E, "ifle", 2),
    /**
     * Pops two ints and branches by the signed 16-bit offset if they are equal.
     */
    IF_ICMPEQ(0x9F, "if_icmpeq", 2),
    /**
     * Pops two ints and branches by the signed 16-bit offset if they differ.
     */
    IF_ICMPNE(0xA0, "if_icmpne", 2),
    /**
     * Pops two ints and branches by the signed 16-bit offset if the lower one is less than the
     * upper one.
     */
    IF_ICMPLT(0xA1, "if_icmplt", 2),
    /**
     * Pops two ints and branches by the signed 16-bit offset if the lower one is greater than or
     * equal to the upper one.
     */
    IF_ICMPGE(0xA2, "if_icmpge", 2),
    /**
     * Pops two ints and branches by the signed 16-bit offset if the lower one is greater than the
     * upper one.
     */
    IF_ICMPGT(0xA3, "if_icmpgt", 2),
    /**
     * Pops two ints and branches by the signed 16-bit offset if the lower one is less than or
     * equal to the upper one.
     */
    IF_ICMPLE(0xA4, "if_icmple", 2),
    /**
     * Pops two references and branches by the signed 16-bit offset if they point to the same
     * object, or are both null.
     */
    IF_ACMPEQ(0xA5, "if_acmpeq", 2),
    /**
     * Pops two references and branches by the signed 16-bit offset if they do not point to the
     * same object.
     */
    IF_ACMPNE(0xA6, "if_acmpne", 2),
    /**
     * Transfers control unconditionally by the signed 16-bit offset from this instruction.
     */
    GOTO(0xA7, "goto", 2),
    /**
     * Pushes the address of the following instruction and branches by the signed 16-bit offset.
     */
    JSR(0xA8, "jsr", 2),
    /**
     * Resumes execution at the return address held in the local variable at the given index,
     * the counterpart of JSR.
     */
    RET(0xA9, "ret", 1),
    /**
     * Pops an int and branches through a table of offsets covering a contiguous key range, or to the default
     * offset.
     */
    TABLESWITCH(0xAA, "tableswitch", -1),
    /**
     * Pops an int and branches through a sorted table of key-offset pairs, or to the default offset.
     */
    LOOKUPSWITCH(0xAB, "lookupswitch", -1),

    /**
     * Pops an int and returns it to the caller, discarding the rest of the frame.
     */
    IRETURN(0xAC, "ireturn", 0),
    /**
     * Pops a long and returns it to the caller, discarding the rest of the frame.
     */
    LRETURN(0xAD, "lreturn", 0),
    /**
     * Pops a float and returns it to the caller, discarding the rest of the frame.
     */
    FRETURN(0xAE, "freturn", 0),
    /**
     * Pops a double and returns it to the caller, discarding the rest of the frame.
     */
    DRETURN(0xAF, "dreturn", 0),
    /**
     * Pops a reference and returns it to the caller, discarding the rest of the frame.
     */
    ARETURN(0xB0, "areturn", 0),
    /**
     * Returns from a method declared void, producing no value; the trailing underscore avoids
     * the Java keyword.
     */
    RETURN_(0xB1, "return", 0),

    /**
     * Pushes the current value of the static field named by the two-byte constant pool index.
     */
    GETSTATIC(0xB2, "getstatic", 2),
    /**
     * Pops a value and stores it into the static field named by the two-byte constant pool index.
     */
    PUTSTATIC(0xB3, "putstatic", 2),
    /**
     * Pops an object reference and pushes the value of the instance field named by the two-byte
     * constant pool index.
     */
    GETFIELD(0xB4, "getfield", 2),
    /**
     * Pops an object reference and a value, and stores the value into the instance field named by
     * the two-byte constant pool index.
     */
    PUTFIELD(0xB5, "putfield", 2),
    /**
     * Pops the receiver and arguments and invokes an instance method selected by virtual dispatch
     * on the receiver's runtime class.
     */
    INVOKEVIRTUAL(0xB6, "invokevirtual", 2),
    /**
     * Pops the receiver and arguments and invokes an instance method without virtual dispatch,
     * as used for constructors, private methods and super calls.
     */
    INVOKESPECIAL(0xB7, "invokespecial", 2),
    /**
     * Pops the arguments and invokes the named static method, initializing its class if needed.
     */
    INVOKESTATIC(0xB8, "invokestatic", 2),
    /**
     * Pops the receiver and arguments and invokes an interface method resolved against the receiver's runtime
     * class.
     */
    INVOKEINTERFACE(0xB9, "invokeinterface", 4),
    /**
     * Invokes the dynamically linked target of a call site, running its bootstrap method on first execution.
     */
    INVOKEDYNAMIC(0xBA, "invokedynamic", 4),
    /**
     * Allocates an uninitialized instance of the class named by the two-byte constant pool index and pushes the
     * reference.
     */
    NEW(0xBB, "new", 2),
    /**
     * Pops an int length and pushes a new zero-filled array of the primitive type selected by the
     * one-byte type code.
     */
    NEWARRAY(0xBC, "newarray", 1),
    /**
     * Pops an int length and pushes a new null-filled array whose component type is the class
     * named by the two-byte constant pool index.
     */
    ANEWARRAY(0xBD, "anewarray", 2),
    /**
     * Pops an array reference and pushes its length as an int.
     */
    ARRAYLENGTH(0xBE, "arraylength", 0),
    /**
     * Pops a throwable reference and throws it, transferring control to the matching handler or
     * to the caller.
     */
    ATHROW(0xBF, "athrow", 0),
    /**
     * Checks the reference on top of the stack against the type named by the two-byte constant pool index, leaving
     * it in place or throwing ClassCastException.
     */
    CHECKCAST(0xC0, "checkcast", 2),
    /**
     * Pops a reference and pushes the int 1 if it is a non-null instance of the type named by the
     * two-byte constant pool index, otherwise 0.
     */
    INSTANCEOF(0xC1, "instanceof", 2),
    /**
     * Pops an object reference and acquires its monitor, blocking until the monitor is available.
     */
    MONITORENTER(0xC2, "monitorenter", 0),
    /**
     * Pops an object reference and releases one hold on its monitor.
     */
    MONITOREXIT(0xC3, "monitorexit", 0),
    /**
     * Prefix that widens the following local variable instruction to a two-byte variable index,
     * or an IINC to a two-byte index and a two-byte signed increment.
     */
    WIDE(0xC4, "wide", -1), // variable-length
    /**
     * Pops one int length per dimension and pushes a new multidimensional array.
     */
    MULTIANEWARRAY(0xC5, "multianewarray", 3),
    /**
     * Pops a reference and branches by the signed 16-bit offset if it is null.
     */
    IFNULL(0xC6, "ifnull", 2),
    /**
     * Pops a reference and branches by the signed 16-bit offset if it is not null.
     */
    IFNONNULL(0xC7, "ifnonnull", 2),
    /**
     * Transfers control unconditionally by the signed 32-bit offset from this instruction.
     */
    GOTO_W(0xC8, "goto_w", 4),
    /**
     * Wide form of JSR that branches by a signed 32-bit offset.
     */
    JSR_W(0xC9, "jsr_w", 4),
    /**
     * Reserved for debugger use inside a JVM implementation; it never appears in a valid class file.
     */
    BREAKPOINT(0xCA, "breakpoint", 0),
    /**
     * Sentinel returned by {@link #fromCode(int)} for a byte value that matches no defined opcode.
     */
    UNKNOWN(-1, "unknown", 0);

    private final int code;
    private final String mnemonic;
    private final int operandCount;

    Opcode(int code, String mnemonic, int operandCount)
    {
        this.code = code;
        this.mnemonic = mnemonic;
        this.operandCount = operandCount;
    }

    /**
     * @return the code
     */
    public int getCode()
    {
        return code;
    }

    /**
     * @return the mnemonic
     */
    public String getMnemonic()
    {
        return mnemonic;
    }

    /**
     * @return the operand count
     */
    public int getOperandCount()
    {
        return operandCount;
    }

    private static final Opcode[] BY_CODE = buildLookup();

    private static Opcode[] buildLookup()
    {
        Opcode[] table = new Opcode[256];
        Arrays.fill(table, UNKNOWN);
        for (Opcode opcode : Opcode.values())
        {
            if (opcode.code >= 0 && opcode.code < table.length)
            {
                table[opcode.code] = opcode;
            }
        }
        return table;
    }

    /**
     * Retrieves the Opcode enum constant corresponding to the specified bytecode value. Indexes a
     * lookup table, so this is safe to call once per instruction on a dispatch path.
     * @param code the bytecode value
     * @return the corresponding Opcode, or UNKNOWN if the value names no opcode
     */
    public static Opcode fromCode(int code)
    {
        if (code < 0 || code >= BY_CODE.length)
        {
            return UNKNOWN;
        }
        return BY_CODE[code];
    }
}
