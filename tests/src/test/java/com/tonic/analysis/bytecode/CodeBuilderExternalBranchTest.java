package com.tonic.analysis.bytecode;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.NopInstruction;
import com.tonic.builder.ClassBuilder;
import com.tonic.builder.CodeBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises detached-snippet branches whose targets live in the host method: explicit
 * {@code externalLabel(name)} bound at splice via {@code ClonedRange.bindLabel}, and the tail
 * continuation label that auto-binds to the insertion successor. Bound branches participate in the
 * host's identity-based relink, so they survive later offset shifts.
 */
class CodeBuilderExternalBranchTest {

    private static MethodEntry method(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static List<Instruction> list(CodeWriter cw) {
        List<Instruction> out = new ArrayList<>();
        cw.getInstructions().forEach(out::add);
        return out;
    }

    @Test
    void externalLabelBranchesToHostInstruction() throws Exception {
        // host f(n): return n*2; with a separate `neg: return -1` block to branch into.
        ClassFile cf = ClassBuilder.create("Ext")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "f", "(I)I")
                .code()
                    .iload(0).iconst(2).imul().ireturn()
                    .label("neg").iconst(-1).ireturn()
                .end().end().build();

        CodeWriter cw = new CodeWriter(method(cf, "f"));
        List<Instruction> host = list(cw);
        Instruction first = host.get(0);          // iload_0 (body)
        Instruction negTarget = host.get(4);       // iconst_m1 (neg block)

        // Guard prepended before the body: if (n < 0) goto host `neg`; else fall through into the body.
        CodeWriter.ClonedRange guard = CodeBuilder.detached()
                .externalLabel("neg")
                .iload(0).iflt("neg")
                .assemble(cf);

        cw.insertBefore(first, guard.bindLabel("neg", negTarget));
        cw.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(6, (int) clazz.getMethod("f", int.class).invoke(null, 3));
        assertEquals(-1, (int) clazz.getMethod("f", int.class).invoke(null, -5));
    }

    @Test
    void continuationLabelFallsThroughOnInsertBefore() throws Exception {
        // Prepend a counted loop whose exit edge (the tail `done` label) continues into the host body.
        ClassFile cf = ClassBuilder.create("ContB")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "g", "()I")
                .code().iconst(99).ireturn().end().end().build();

        CodeWriter cw = new CodeWriter(method(cf, "g"));
        Instruction first = cw.getInstructions().iterator().next();

        CodeWriter.ClonedRange loop = CodeBuilder.detached()
                .iconst(0).istore(1)
                .label("top")
                .iload(1).iconst(3).if_icmpge("done")
                .iinc(1, 1)
                .goto_("top")
                .label("done")
                .assemble(cf);

        cw.insertBefore(first, loop);
        cw.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(99, (int) clazz.getMethod("g").invoke(null));
    }

    @Test
    void continuationLabelFallsThroughOnInsertAfter() throws Exception {
        // host h(): x=7; return x;  Insert a no-op `goto done` after the store; `done` continues to iload.
        ClassFile cf = ClassBuilder.create("ContA")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "h", "()I")
                .code().iconst(7).istore(0).iload(0).ireturn().end().end().build();

        CodeWriter cw = new CodeWriter(method(cf, "h"));
        Instruction store = list(cw).get(1);   // istore_0

        CodeWriter.ClonedRange snip = CodeBuilder.detached()
                .goto_("done")
                .label("done")
                .assemble(cf);

        cw.insertAfter(store, snip);
        cw.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(7, (int) clazz.getMethod("h").invoke(null));
    }

    @Test
    void externalBranchSurvivesFurtherHostEdits() throws Exception {
        ClassFile cf = ClassBuilder.create("Dur")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "f", "(I)I")
                .code()
                    .iload(0).iconst(2).imul().ireturn()
                    .label("neg").iconst(-1).ireturn()
                .end().end().build();

        CodeWriter cw = new CodeWriter(method(cf, "f"));
        List<Instruction> host = list(cw);
        CodeWriter.ClonedRange guard = CodeBuilder.detached()
                .externalLabel("neg").iload(0).iflt("neg").assemble(cf);
        cw.insertBefore(host.get(0), guard.bindLabel("neg", host.get(4)));
        cw.write();

        // A second, unrelated edit shifts offsets; the bound external branch must still resolve.
        CodeWriter cw2 = new CodeWriter(method(cf, "f"));
        Instruction mid = list(cw2).get(list(cw2).size() / 2);
        cw2.insertBefore(mid, new NopInstruction(0x00, 0));
        cw2.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(6, (int) clazz.getMethod("f", int.class).invoke(null, 3));
        assertEquals(-1, (int) clazz.getMethod("f", int.class).invoke(null, -5));
    }

    @Test
    void unboundExternalLabelThrowsAtSplice() throws Exception {
        ClassFile cf = ClassBuilder.create("Unb")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "f", "(I)I")
                .code().iload(0).ireturn().end().end().build();

        CodeWriter cw = new CodeWriter(method(cf, "f"));
        Instruction first = cw.getInstructions().iterator().next();
        CodeWriter.ClonedRange r = CodeBuilder.detached()
                .externalLabel("x").iload(0).iflt("x").assemble(cf);

        assertThrows(IllegalStateException.class, () -> cw.insertBefore(first, r));
    }

    @Test
    void replaceBodyRejectsContinuationBranch() throws Exception {
        ClassFile cf = ClassBuilder.create("RB")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "f", "()I")
                .code().iconst(1).ireturn().end().end().build();

        CodeWriter cw = new CodeWriter(method(cf, "f"));
        CodeWriter.ClonedRange r = CodeBuilder.detached()
                .goto_("done").label("done").assemble(cf);

        assertThrows(IllegalStateException.class, () -> cw.replaceBody(r));
    }

    @Test
    void externalTargetFromAnotherMethodThrows() throws Exception {
        ClassFile cf = ClassBuilder.create("Wrong")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "a", "(I)I")
                .code().iload(0).ireturn().end().end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "b", "()I")
                .code().iconst(5).ireturn().end().end().build();

        Instruction foreign = new CodeWriter(method(cf, "b")).getInstructions().iterator().next();
        CodeWriter cw = new CodeWriter(method(cf, "a"));
        Instruction first = cw.getInstructions().iterator().next();
        CodeWriter.ClonedRange r = CodeBuilder.detached()
                .externalLabel("x").iload(0).iflt("x").assemble(cf);

        assertThrows(IllegalArgumentException.class,
                () -> cw.insertBefore(first, r, Collections.singletonMap("x", foreign)));
    }
}
