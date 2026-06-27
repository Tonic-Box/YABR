package com.tonic.analysis.absexec;

import com.tonic.analysis.instruction.ArithmeticInstruction;
import com.tonic.analysis.instruction.GetFieldInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the abstract {@link Execution} def-use engine on the exact pattern the ModArith port consumes:
 * a field-load multiplied by a constant. The engine must link the {@code imul}'s popped operands back to the
 * {@code getfield} and the constant push.
 */
class ExecutionTest {

    private static final int IMUL = 0x68;

    @Test
    void buildsDefUseForFieldTimesConstant() {
        int accStatic = AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC;
        ClassFile cf = ClassBuilder.create("a")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addField(AccessFlags.ACC_PRIVATE | AccessFlags.ACC_STATIC, "f", "I").end()
                .addMethod(accStatic, "get", "()I")
                .code().getstatic("a", "f", "I").iconst(3).imul().ireturn().end().end()
                .build();

        MethodEntry get = methodNamed(cf, "get");
        List<InsnContext> contexts = new ArrayList<>();
        new Execution(get).addVisitor(contexts::add).run();

        InsnContext mul = contexts.stream()
                .filter(c -> c.getInstruction() instanceof ArithmeticInstruction
                        && c.getInstruction().getOpcode() == IMUL)
                .findFirst().orElse(null);
        assertNotNull(mul, "the imul should have been executed");
        assertEquals(2, mul.getPops().size(), "imul pops two operands");

        boolean sawField = false;
        boolean sawConst = false;
        for (StackCtx pop : mul.getPops()) {
            Instruction pusher = pop.getPushed().getInstruction();
            if (pusher instanceof GetFieldInstruction) {
                sawField = true;
            } else if (pusher.getOpcode() == 0x06) { // iconst_3
                sawConst = true;
            }
        }
        assertTrue(sawField, "one imul operand traces to the getfield");
        assertTrue(sawConst, "the other imul operand traces to the iconst_3");
    }

    @Test
    void exploresBranchesWithoutHangingAndReachesBothArms() {
        // if (p) return f; else return f2;  — both returns must be marked executed, loop guard must terminate.
        int accStatic = AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC;
        ClassFile cf = ClassBuilder.create("b")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addField(AccessFlags.ACC_PRIVATE | AccessFlags.ACC_STATIC, "f", "I").end()
                .addMethod(accStatic, "pick", "(I)I")
                .code()
                .iload(0).ifeq("elseArm")
                .getstatic("b", "f", "I").ireturn()
                .label("elseArm")
                .iconst(0).ireturn()
                .end().end()
                .build();

        MethodEntry pick = methodNamed(cf, "pick");
        // Collect executed instructions via the visitor so we observe the engine's OWN instruction objects
        // (a separate CodeWriter would yield different identities than the engine's executed set).
        List<InsnContext> contexts = new ArrayList<>();
        new Execution(pick).addVisitor(contexts::add).run();

        long returns = contexts.stream()
                .map(c -> c.getInstruction().getOffset())
                .distinct()
                .filter(off -> contexts.stream().anyMatch(
                        c -> c.getInstruction().getOffset() == off && c.getInstruction().getOpcode() == 0xAC))
                .count();
        assertEquals(2, returns, "both branch arms' ireturn should be reached");
    }

    private static MethodEntry methodNamed(ClassFile cf, String name) {
        return cf.getMethods().stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    }
}
