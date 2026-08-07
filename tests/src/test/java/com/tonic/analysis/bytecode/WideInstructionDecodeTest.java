package com.tonic.analysis.bytecode;

import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InstructionFactory;
import com.tonic.analysis.instruction.WideInstruction;
import com.tonic.analysis.instruction.WideIIncInstruction;
import com.tonic.util.InstructionLength;
import com.tonic.util.Opcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Covers every form the wide prefix can take. A form the decoder does not recognise falls back to a
 * two-byte unknown, which leaves the operand bytes to be read as instructions from that point on.
 */
class WideInstructionDecodeTest
{

    private static byte[] wide(Opcode modified, int... operandBytes)
    {
        byte[] code = new byte[2 + operandBytes.length];
        code[0] = (byte) Opcode.WIDE.getCode();
        code[1] = (byte) modified.getCode();
        for (int i = 0; i < operandBytes.length; i++)
        {
            code[2 + i] = (byte) operandBytes[i];
        }
        return code;
    }

    private static Instruction decode(byte[] code)
    {
        return InstructionFactory.createInstruction(Byte.toUnsignedInt(code[0]), 0, code, null);
    }

    @Test
    void wideRetCarriesATwoByteIndexLikeEveryOtherLocalForm()
    {
        Instruction decoded = decode(wide(Opcode.RET, 0x01, 0x02));

        WideInstruction wide = assertInstanceOf(WideInstruction.class, decoded,
            "wide ret is a local-variable form, not an unknown instruction");
        assertEquals(Opcode.RET, wide.getModifiedOpcode());
        assertEquals(0x0102, wide.getVarIndex());
        assertEquals(4, wide.getLength(),
            "decoding wide ret as two bytes would leave its index to be read as instructions");
    }

    @Test
    void everyWideLocalFormDecodesToAFourByteInstruction()
    {
        Opcode[] forms = {Opcode.ILOAD, Opcode.LLOAD, Opcode.FLOAD, Opcode.DLOAD, Opcode.ALOAD,
            Opcode.ISTORE, Opcode.LSTORE, Opcode.FSTORE, Opcode.DSTORE, Opcode.ASTORE, Opcode.RET};

        for (Opcode form : forms)
        {
            byte[] code = wide(form, 0x00, 0x2A);
            Instruction decoded = decode(code);

            WideInstruction wide = assertInstanceOf(WideInstruction.class, decoded, "wide " + form);
            assertEquals(form, wide.getModifiedOpcode(), "wide " + form);
            assertEquals(42, wide.getVarIndex(), "wide " + form);
            assertEquals(4, wide.getLength(), "wide " + form);
        }
    }

    @Test
    void wideIincCarriesTwoTwoByteOperands()
    {
        Instruction decoded = decode(wide(Opcode.IINC, 0x00, 0x2A, 0x00, 0x07));

        WideIIncInstruction wide = assertInstanceOf(WideIIncInstruction.class, decoded);
        assertEquals(6, wide.getLength());
    }

    @Test
    void theDecoderAndTheLengthScannerAgreeOnEveryWideForm()
    {
        Opcode[] forms = {Opcode.ILOAD, Opcode.LLOAD, Opcode.FLOAD, Opcode.DLOAD, Opcode.ALOAD,
            Opcode.ISTORE, Opcode.LSTORE, Opcode.FSTORE, Opcode.DSTORE, Opcode.ASTORE, Opcode.RET};

        for (Opcode form : forms)
        {
            byte[] code = wide(form, 0x00, 0x2A);
            assertEquals(decode(code).getLength(), InstructionLength.at(code, 0),
                "the decoder and the raw scanner must not disagree for wide " + form);
        }

        byte[] iinc = wide(Opcode.IINC, 0x00, 0x2A, 0x00, 0x07);
        assertEquals(decode(iinc).getLength(), InstructionLength.at(iinc, 0),
            "the decoder and the raw scanner must not disagree for wide iinc");
    }

    @Test
    void aTruncatedWideDoesNotOverrun()
    {
        byte[] justThePrefix = {(byte) Opcode.WIDE.getCode()};
        assertEquals(1, decode(justThePrefix).getLength(),
            "a wide with no modified opcode has only the byte that is present");

        byte[] missingIndex = {(byte) Opcode.WIDE.getCode(), (byte) Opcode.ILOAD.getCode(), 0x00};
        assertEquals(3, decode(missingIndex).getLength(),
            "a wide load missing half its index cannot claim four bytes");
    }
}
