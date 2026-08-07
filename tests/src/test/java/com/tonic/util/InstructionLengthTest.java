package com.tonic.util;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the raw-bytecode length scanner against the instruction parser, which decodes the same code
 * independently. A scanner that gets one length wrong desynchronises from that point on, so every
 * later opcode it reports is an operand byte.
 */
class InstructionLengthTest
{

    private static final String SOURCE =
            "import java.util.*;\n"
            + "public class LenProbe {\n"
            + "    static String[] makeArray(int n) { return new String[n]; }\n"
            + "    static Object[][] nested(int n) { return new Object[n][2]; }\n"
            + "    static int tableSwitch(int x) {\n"
            + "        switch (x) { case 0: return 1; case 1: return 2; case 2: return 3;\n"
            + "                     case 3: return 4; default: return 5; }\n"
            + "    }\n"
            + "    static int lookupSwitch(int x) {\n"
            + "        switch (x) { case 1: return 1; case 900: return 2; case 100000: return 3;\n"
            + "                     default: return 4; }\n"
            + "    }\n"
            + "    static String strSwitch(String s) {\n"
            + "        switch (s) { case \"a\": return \"x\"; case \"bb\": return \"y\"; default: return \"z\"; }\n"
            + "    }\n"
            + "    static long arith(long a, int b) {\n"
            + "        double d = a * 1.5; float f = (float) a; int i = (int) d;\n"
            + "        return a + b + (long) f + i;\n"
            + "    }\n"
            + "    static int loop(List<String> xs) {\n"
            + "        int n = 0;\n"
            + "        for (String s : xs) { if (s != null && s.length() > 2) { n += s.hashCode(); } }\n"
            + "        return n;\n"
            + "    }\n"
            + "    static Object cast(Object o) { return (o instanceof String) ? (String) o : o; }\n"
            + "    static synchronized void sync() { }\n"
            + "    static int tryIt(int x) {\n"
            + "        try { return 10 / x; } catch (ArithmeticException e) { return -1; } finally { x = 0; }\n"
            + "    }\n"
            + "}\n";

    /**
     * The offsets the scanner visits when walking the code from zero.
     */
    private static List<Integer> scannedOffsets(byte[] code)
    {
        List<Integer> offsets = new ArrayList<>();
        int i = 0;
        while (i < code.length)
        {
            offsets.add(i);
            int length = InstructionLength.at(code, i);
            assertTrue(length > 0, "the scanner must report a usable length at offset " + i);
            i += length;
        }
        return offsets;
    }

    @Test
    void scannerAgreesWithTheInstructionParserOnEveryMethod()
    {
        ClassFile cf;
        try
        {
            cf = TestUtils.compileSource(SOURCE, "LenProbe");
        }
        catch (Exception e)
        {
            throw new AssertionError("fixture must compile", e);
        }

        int methodsChecked = 0;
        for (MethodEntry method : cf.getMethods())
        {
            CodeAttribute attribute = method.getCodeAttribute();
            if (attribute == null || attribute.getCode() == null || attribute.getCode().length == 0)
            {
                continue;
            }

            List<Integer> parsed = new ArrayList<>();
            for (Instruction instruction : new CodeWriter(method).getInstructions())
            {
                parsed.add(instruction.getOffset());
            }
            assertFalse(parsed.isEmpty(), method.getName() + " should parse to instructions");

            assertEquals(parsed, scannedOffsets(attribute.getCode()),
                method.getName() + ": scanned offsets must match the parser's");
            methodsChecked++;
        }
        assertTrue(methodsChecked >= 8, "expected the fixture's methods, got " + methodsChecked);
    }

    @Test
    void everyParsedInstructionLengthMatchesTheScanner()
    {
        ClassFile cf;
        try
        {
            cf = TestUtils.compileSource(SOURCE, "LenProbe");
        }
        catch (Exception e)
        {
            throw new AssertionError("fixture must compile", e);
        }

        for (MethodEntry method : cf.getMethods())
        {
            CodeAttribute attribute = method.getCodeAttribute();
            if (attribute == null || attribute.getCode() == null || attribute.getCode().length == 0)
            {
                continue;
            }
            byte[] code = attribute.getCode();
            for (Instruction instruction : new CodeWriter(method).getInstructions())
            {
                assertEquals(instruction.getLength(), InstructionLength.at(code, instruction.getOffset()),
                    method.getName() + ": length disagreement at offset " + instruction.getOffset()
                        + " for opcode 0x" + Integer.toHexString(instruction.getOpcode()));
            }
        }
    }

    @Test
    void anewarrayIsThreeBytes()
    {
        byte[] code = {(byte) Opcode.ANEWARRAY.getCode(), 0x00, 0x05, (byte) Opcode.ARETURN.getCode()};

        assertEquals(3, InstructionLength.at(code, 0),
            "anewarray carries a two-byte class index; reading it as two bytes desynchronises the scan");
        assertEquals(1, InstructionLength.at(code, 3));
    }

    @Test
    void fixedLengthOpcodesComeFromTheOperandCount()
    {
        for (int value = 0x00; value <= 0xCA; value++)
        {
            Opcode opcode = Opcode.fromCode(value);
            if (opcode == Opcode.TABLESWITCH || opcode == Opcode.LOOKUPSWITCH || opcode == Opcode.WIDE)
            {
                continue;
            }
            byte[] code = new byte[16];
            code[0] = (byte) value;
            assertEquals(1 + opcode.getOperandCount(), InstructionLength.at(code, 0),
                opcode + " length must follow its operand count");
        }
    }

    @Test
    void wideSizesItselfFromTheInstructionItModifies()
    {
        byte[] wideIinc = {(byte) Opcode.WIDE.getCode(), (byte) Opcode.IINC.getCode(), 0, 1, 0, 1};
        byte[] wideLoad = {(byte) Opcode.WIDE.getCode(), (byte) Opcode.ILOAD.getCode(), 0, 1};

        assertEquals(6, InstructionLength.at(wideIinc, 0), "wide iinc carries two two-byte operands");
        assertEquals(4, InstructionLength.at(wideLoad, 0), "any other wide form carries one");
    }

    @Test
    void truncatedInstructionsAreRejectedRatherThanGuessed()
    {
        assertEquals(-1, InstructionLength.at(new byte[]{(byte) Opcode.WIDE.getCode()}, 0),
            "a wide with no modified opcode is truncated");
        assertEquals(-1, InstructionLength.at(new byte[]{(byte) Opcode.TABLESWITCH.getCode()}, 0),
            "a tableswitch with no header is truncated");
        assertEquals(-1, InstructionLength.at(new byte[]{(byte) Opcode.LOOKUPSWITCH.getCode()}, 0),
            "a lookupswitch with no header is truncated");
        assertEquals(-1, InstructionLength.at(new byte[]{0x00}, 5), "an offset past the end has no instruction");
        assertEquals(-1, InstructionLength.at(new byte[]{0x00}, -1), "a negative offset has no instruction");
        assertEquals(-1, InstructionLength.at(null, 0), "no code means no instruction");
    }

    @Test
    void anUnknownOpcodeIsTreatedAsASingleByte()
    {
        assertNotNull(Opcode.fromCode(0xFE));
        assertEquals(1, InstructionLength.at(new byte[]{(byte) 0xFE, 0x00}, 0),
            "an unrecognised byte advances by one so the scan still terminates");
    }
}
