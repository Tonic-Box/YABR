package com.tonic.analysis.absexec;

import com.tonic.builder.ClassBuilder;
import com.tonic.builder.CodeBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import com.tonic.util.Opcode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every primitive conversion must push a stack entry sized by its TARGET type. Reading the width off the
 * source operand instead is invisible to a stack-depth check, because the abstract stack models a wide
 * value as one entry, so only the pushed entry's width exposes it.
 */
class ConversionWidthTest
{

    private static MethodEntry methodNamed(ClassFile cf, String name)
    {
        for (MethodEntry m : cf.getMethods())
        {
            if (m.getName().equals(name))
            {
                return m;
            }
        }
        throw new IllegalStateException("no method " + name);
    }

    /**
     * Runs one conversion in isolation and reports the width of the entry it pushed.
     */
    private static boolean pushesWide(String name, String descriptor, UnaryOperator<CodeBuilder> body)
    {
        CodeBuilder code = ClassBuilder.create("conv" + name)
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, name, descriptor)
                .code();
        ClassFile cf = body.apply(code).end().end().build();

        List<InsnContext> contexts = new ArrayList<>();
        new Execution(methodNamed(cf, name)).addVisitor(contexts::add).run();

        InsnContext conversion = contexts.stream()
                .filter(c -> Opcode.fromCode(c.getInstruction().getOpcode()).getMnemonic().equals(name))
                .findFirst().orElse(null);
        assertNotNull(conversion, name + " should have been executed");
        assertEquals(1, conversion.getPushes().size(), name + " pushes exactly one entry");
        return conversion.getPushes().get(0).isWide();
    }

    @Test
    void longToFloatPushesANarrowEntry()
    {
        assertFalse(pushesWide("l2f", "(J)F", c -> c.lload(0).l2f().freturn()),
            "l2f produces a float, so the pushed entry must be narrow even though its operand was wide");
    }

    @Test
    void longToDoublePushesAWideEntry()
    {
        assertTrue(pushesWide("l2d", "(J)D", c -> c.lload(0).l2d().dreturn()),
            "l2d produces a double");
    }

    @Test
    void longToIntPushesANarrowEntry()
    {
        assertFalse(pushesWide("l2i", "(J)I", c -> c.lload(0).l2i().ireturn()),
            "l2i produces an int");
    }

    @Test
    void intToLongPushesAWideEntry()
    {
        assertTrue(pushesWide("i2l", "(I)J", c -> c.iload(0).i2l().lreturn()),
            "i2l produces a long");
    }

    @Test
    void floatToLongPushesAWideEntry()
    {
        assertTrue(pushesWide("f2l", "(F)J", c -> c.fload(0).f2l().lreturn()),
            "f2l produces a long");
    }

    @Test
    void doubleToFloatPushesANarrowEntry()
    {
        assertFalse(pushesWide("d2f", "(D)F", c -> c.dload(0).d2f().freturn()),
            "d2f produces a float");
    }

    @Test
    void doubleToLongPushesAWideEntry()
    {
        assertTrue(pushesWide("d2l", "(D)J", c -> c.dload(0).d2l().lreturn()),
            "d2l produces a long");
    }

    @Test
    void intToDoublePushesAWideEntry()
    {
        assertTrue(pushesWide("i2d", "(I)D", c -> c.iload(0).i2d().dreturn()),
            "i2d produces a double");
    }
}
