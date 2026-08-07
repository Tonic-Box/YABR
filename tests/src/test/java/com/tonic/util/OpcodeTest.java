package com.tonic.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the code-to-opcode lookup, which dispatch loops call once per instruction.
 */
class OpcodeTest
{

    @Test
    void everyOpcodeResolvesFromItsOwnCode()
    {
        for (Opcode opcode : Opcode.values())
        {
            if (opcode == Opcode.UNKNOWN)
            {
                continue;
            }
            assertSame(opcode, Opcode.fromCode(opcode.getCode()),
                opcode.name() + " must resolve from its own code");
        }
    }

    @Test
    void codesAreUniqueSoTheLookupIsUnambiguous()
    {
        Map<Integer, Opcode> seen = new HashMap<>();
        for (Opcode opcode : Opcode.values())
        {
            if (opcode == Opcode.UNKNOWN)
            {
                continue;
            }
            Opcode previous = seen.put(opcode.getCode(), opcode);
            assertEquals(null, previous,
                "two opcodes share code " + opcode.getCode() + ": " + opcode + " and " + previous);
        }
    }

    @Test
    void theDefinedRangeIsContiguous()
    {
        for (int code = 0x00; code <= 0xCA; code++)
        {
            assertNotNull(Opcode.fromCode(code));
            assertTrue(Opcode.fromCode(code) != Opcode.UNKNOWN,
                "0x" + Integer.toHexString(code) + " should name an opcode");
        }
    }

    @Test
    void codesOutsideTheTableReportUnknown()
    {
        assertSame(Opcode.UNKNOWN, Opcode.fromCode(-1));
        assertSame(Opcode.UNKNOWN, Opcode.fromCode(0xCB));
        assertSame(Opcode.UNKNOWN, Opcode.fromCode(0xFF));
        assertSame(Opcode.UNKNOWN, Opcode.fromCode(256));
        assertSame(Opcode.UNKNOWN, Opcode.fromCode(Integer.MAX_VALUE));
        assertSame(Opcode.UNKNOWN, Opcode.fromCode(Integer.MIN_VALUE),
            "a negative code must not index the table");
    }

    /**
     * Constants whose mnemonic is a Java keyword carry a trailing underscore, so the name matches only
     * once that suffix is stripped.
     */
    @Test
    void mnemonicsMatchTheConstantNames()
    {
        for (Opcode opcode : Opcode.values())
        {
            String expected = opcode.name().toLowerCase();
            if (expected.endsWith("_"))
            {
                expected = expected.substring(0, expected.length() - 1);
            }
            assertEquals(expected, opcode.getMnemonic(),
                opcode.name() + " mnemonic must match its constant name");
        }
    }
}
