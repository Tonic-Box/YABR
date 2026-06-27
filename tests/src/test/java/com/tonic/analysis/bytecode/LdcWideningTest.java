package com.tonic.analysis.bytecode;

import com.tonic.analysis.ClassFactory;
import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.LdcInstruction;
import com.tonic.analysis.instruction.LdcWInstruction;
import com.tonic.builder.CodeBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.IntegerItem;
import com.tonic.parser.constpool.Item;
import com.tonic.testutil.TestUtils;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the {@code ldc} constant-pool-index &gt; 255 corruption. {@link LdcInstruction} hardcoded
 * a 2-byte length and wrote its index with {@code writeByte}, so inserting a constant into a method of a
 * class whose pool already exceeds 255 entries truncated the index (and, once re-parsed as a single byte,
 * surfaced as cp index 0). A correct {@code ldc} must widen to {@code ldc_w} when its index exceeds 255.
 */
class LdcWideningTest {

    private static MethodEntry method(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    @Test
    void ldcIndexAbove255WidensAndResolvesCorrectly() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        pool.loadPlatformClass("java/lang/Object.class");

        int pubStatic = new AccessBuilder().setPublic().setStatic().build();
        ClassFile cf = pool.createNewClass("Big", new AccessBuilder().setPublic().build());
        cf.createNewMethodWithDescriptor(pubStatic, "m", "()I");
        MethodEntry m = method(cf, "m");

        CodeWriter setup = new CodeWriter(m);
        setup.replaceBody(CodeBuilder.detached().iconst(0).ireturn().assemble(cf));
        setup.write();

        // Insert many distinct large-int `ldc; pop` snippets after the leading iconst_0, growing the pool
        // well past 255 so the later constants land at indices that a 1-byte ldc cannot hold.
        int count = 320;
        int base = 0x4000_0000;
        CodeWriter cw = new CodeWriter(m);
        Instruction first = cw.getInstructionList().get(0);
        for (int i = 0; i < count; i++) {
            cw.insertAfter(first, CodeBuilder.detached().ldc(base + i).pop().assemble(cf));
        }
        cw.write();

        ConstPool cp = cf.getConstPool();
        assertTrue(cp.getItems().size() > 255, "pool should exceed 255 entries: " + cp.getItems().size());

        // Re-parse the written method: every inserted ldc/ldc_w must still resolve to a real Integer in
        // [base, base+count) — none truncated to a wrong/zero index.
        CodeWriter verify = new CodeWriter(m);
        int resolved = 0;
        for (Instruction insn : verify.getInstructionList()) {
            int idx = -1;
            if (insn instanceof LdcInstruction) {
                idx = ((LdcInstruction) insn).getCpIndex();
            } else if (insn instanceof LdcWInstruction) {
                idx = ((LdcWInstruction) insn).getCpIndex();
            }
            if (idx <= 0) {
                continue;
            }
            Item<?> item = cp.getItem(idx);
            if (item instanceof IntegerItem) {
                int v = ((IntegerItem) item).getValue();
                if (v >= base && v < base + count) {
                    resolved++;
                }
            }
        }
        assertEquals(count, resolved, "every inserted ldc must resolve to its original constant");

        ClassFactory.computeFrames(cf, m);
    }
}
