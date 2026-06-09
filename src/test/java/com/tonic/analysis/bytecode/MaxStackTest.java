package com.tonic.analysis.bytecode;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.analysis.instruction.NopInstruction;
import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises CFG-correct {@code max_stack} ({@link CodeWriter#computeMaxStack}). The worklist over the
 * control-flow graph sees the true peak — including exception-handler entry states a linear textual
 * scan misses — and category-2 types count as two slots.
 */
class MaxStackTest {

    private static MethodEntry method(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    /** risky(x): try { return 100/x; } catch (ArithmeticException) { return 1+2+3; } — handler peak 3 > body peak 2. */
    private static ClassFile buildRisky() {
        return ClassBuilder.create("MS")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "risky", "(I)I")
                .code()
                    .label("try").bipush(100).iload(0).idiv().ireturn()
                    .label("handler").pop().iconst(1).iconst(2).iconst(3).iadd().iadd().ireturn()
                    .trycatch("try", "handler", "handler", "java/lang/ArithmeticException")
                .end().end().build();
    }

    private static int cfgMaxStack(ClassFile cf, String name) {
        return new FrameGenerator(cf.getConstPool()).computeMaxStack(method(cf, name));
    }

    @Test
    void handlerDepthCountedOverCfg() {
        // A linear textual scan peaks at 2 (the divide); the handler reaches 3 — only the CFG sees it.
        assertEquals(3, cfgMaxStack(buildRisky(), "risky"));
    }

    @Test
    void categoryTwoCountsAsTwoSlots() {
        ClassFile cf = ClassBuilder.create("L2")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "l", "()J")
                .code().lconst(1).lreturn().end().end().build();
        assertEquals(2, cfgMaxStack(cf, "l"));
    }

    @Test
    void straightLineMatchesTruePeak() {
        ClassFile cf = ClassBuilder.create("SL")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "s", "()I")
                .code().iconst(1).iconst(2).iadd().ireturn().end().end().build();
        assertEquals(2, cfgMaxStack(cf, "s"));
    }

    @Test
    void relinkAppliesCfgMaxStackEndToEnd() throws Exception {
        ClassFile cf = buildRisky();
        MethodEntry m = method(cf, "risky");
        m.getCodeAttribute().setMaxStack(2);   // simulate a linear under-count

        CodeWriter cw = new CodeWriter(m);
        cw.insertBefore(cw.getInstructions().iterator().next(), new NopInstruction(0x00, 0));
        cw.write();

        assertEquals(3, m.getCodeAttribute().getMaxStack(), "relink must raise max_stack to the CFG peak");

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(50, (int) clazz.getMethod("risky", int.class).invoke(null, 2));
        assertEquals(6, (int) clazz.getMethod("risky", int.class).invoke(null, 0));
    }
}
