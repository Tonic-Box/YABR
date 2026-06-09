package com.tonic.analysis.bytecode;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.NopInstruction;
import com.tonic.builder.ClassBuilder;
import com.tonic.type.AccessFlags;
import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the handle-keyed structural edits ({@code insertBefore}/{@code removeInstruction}/
 * {@code replaceInstruction}) and the relink/layout pass: a method with backward+forward branches (a
 * loop) and a switch is edited at a point inside a branch span, then loaded and invoked. Correct
 * results prove branch/switch targets, the exception table, and StackMapTable frames are all relinked
 * — and that the previously latent branch-corruption bug in offset-shifting edits is fixed.
 */
class BytecodeEditTest {

    /** Compiles a single-class source to a fresh ClassFile via the YABR front end (all methods static). */
    private ClassFile compile(String src, String name) throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        JavaParser parser = JavaParser.create();
        CompilationUnit cu = parser.parse(src);
        ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
        ClassFile cf = pool.createNewClass(name, new AccessBuilder().setPublic().build());
        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(cls);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(cf.getConstPool());
        for (MethodDecl m : cls.getMethods()) {
            if (m.getBody() == null) {
                continue;
            }
            List<SourceType> params = new ArrayList<>();
            for (ParameterDecl p : m.getParameters()) {
                params.add(p.getType());
            }
            StringBuilder d = new StringBuilder("(");
            for (SourceType t : params) {
                d.append(t.toIRType().getDescriptor());
            }
            d.append(")").append(m.getReturnType().toIRType().getDescriptor());
            cf.createNewMethodWithDescriptor(new AccessBuilder().setPublic().setStatic().build(), m.getName(), d.toString());
            MethodEntry e = null;
            for (MethodEntry me : cf.getMethods()) {
                if (me.getName().equals(m.getName())) {
                    e = me;
                }
            }
            ssa.lower(lowerer.lower(m, name), e);
        }
        return cf;
    }

    private static final String SRC =
            "public class BE {"
            + " public static int loopSum(int n) { int s = 0; for (int i = 0; i < n; i++) { s = s + i; } return s; }"
            + " public static int pick(int x) { int r; switch (x) { case 0: r = 10; break; case 1: r = 20;"
            + "   break; case 2: r = 30; break; default: r = -1; } return r; } }";

    private static MethodEntry method(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static Instruction middle(CodeWriter cw) {
        List<Instruction> list = new ArrayList<>();
        cw.getInstructions().forEach(list::add);
        return list.get(list.size() / 2);
    }

    private void assertBehaviour(ClassFile cf) throws Exception {
        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(10, (int) clazz.getMethod("loopSum", int.class).invoke(null, 5));
        assertEquals(30, (int) clazz.getMethod("pick", int.class).invoke(null, 2));
        assertEquals(-1, (int) clazz.getMethod("pick", int.class).invoke(null, 7));
    }

    @Test
    void insertBeforeAcrossBranchSpansRelinks() throws Exception {
        ClassFile cf = compile(SRC, "BE");
        for (String mn : new String[]{"loopSum", "pick"}) {
            CodeWriter cw = new CodeWriter(method(cf, mn));
            cw.insertBefore(middle(cw), new NopInstruction(0x00, 0));
            cw.write();
        }
        assertBehaviour(cf);
    }

    @Test
    void removeInstructionRelinks() throws Exception {
        ClassFile cf = compile(SRC, "BE");
        for (String mn : new String[]{"loopSum", "pick"}) {
            // Insert a NOP mid-method, then remove it: net no-op, but both edits relink across branches.
            CodeWriter cw = new CodeWriter(method(cf, mn));
            NopInstruction nop = new NopInstruction(0x00, 0);
            cw.insertBefore(middle(cw), nop);
            cw.write();

            CodeWriter cw2 = new CodeWriter(method(cf, mn));
            Instruction toRemove = null;
            for (Instruction i : cw2.getInstructions()) {
                if (i instanceof NopInstruction) {
                    toRemove = i;
                    break;
                }
            }
            cw2.removeInstruction(toRemove);
            cw2.write();
        }
        assertBehaviour(cf);
    }

    @Test
    void replaceInstructionRelinks() throws Exception {
        ClassFile cf = compile(SRC, "BE");
        for (String mn : new String[]{"loopSum", "pick"}) {
            // Insert a NOP, then replace it with another NOP: stack-neutral, so behaviour is preserved
            // while exercising replace + relink across branch spans.
            CodeWriter cw = new CodeWriter(method(cf, mn));
            cw.insertBefore(middle(cw), new NopInstruction(0x00, 0));
            cw.write();

            CodeWriter cw2 = new CodeWriter(method(cf, mn));
            Instruction nop = null;
            for (Instruction i : cw2.getInstructions()) {
                if (i instanceof NopInstruction) {
                    nop = i;
                    break;
                }
            }
            cw2.replaceInstruction(nop, new NopInstruction(0x00, 0));
            cw2.write();
        }
        assertBehaviour(cf);
    }

    @Test
    void cloneRangeCanonicalizesLocalVarOpcodes() throws Exception {
        // Locals at slots 0 (param n) and 1 (a). Clone with localOffset so a slot lands in 0-3 and,
        // separately, beyond 255 — the clone must use the compact 1-byte form for the former and the
        // wide form for the latter (rather than always emitting a general 2-byte load/store).
        ClassFile cf = compile("public class LV { static int f(int n) { int a = n + 1; return a; } }", "LV");
        CodeWriter cw = new CodeWriter(method(cf, "f"));
        List<Instruction> body = new ArrayList<>();
        cw.getInstructions().forEach(body::add);

        List<Instruction> shifted = cw.cloneRange(body.get(0), body.get(body.size() - 1), 1);
        boolean sawLowLocal = false;
        for (Instruction i : shifted) {
            if (i instanceof com.tonic.analysis.instruction.ILoadInstruction
                    || i instanceof com.tonic.analysis.instruction.IStoreInstruction) {
                assertEquals(1, i.getLength(), "a local at index 0-3 must use the compact 1-byte form: " + i);
                sawLowLocal = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(sawLowLocal, "expected compact local-var ops in the clone");

        List<Instruction> wide = cw.cloneRange(body.get(0), body.get(body.size() - 1), 300);
        boolean sawWide = false;
        for (Instruction i : wide) {
            if (i instanceof com.tonic.analysis.instruction.WideInstruction) {
                sawWide = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(sawWide, "a local index > 255 must use the wide form");
    }

    @Test
    void cloneRangeReproducesBodyWithRelinkedBranches() throws Exception {
        // A method with a loop + an if + locals; clone its whole body into a twin method (same pool)
        // and confirm the twin computes the same result — proving cloneRange relinks the cloned
        // branches and copies local-variable instructions correctly.
        ClassFile cf = compile(
                "public class CL { public static int absSum(int n) { int s = 0;"
                + " for (int i = 0; i < n; i++) { int v = i - 2; if (v < 0) { v = -v; } s = s + v; } return s; } }",
                "CL");
        cf.createNewMethodWithDescriptor(new AccessBuilder().setPublic().setStatic().build(), "absSum2", "(I)I");

        CodeWriter src = new CodeWriter(method(cf, "absSum"));
        List<Instruction> body = new ArrayList<>();
        src.getInstructions().forEach(body::add);
        List<Instruction> clones = src.cloneRange(body.get(0), body.get(body.size() - 1), 0);

        CodeWriter twin = new CodeWriter(method(cf, "absSum2"));
        twin.replaceBody(clones);
        twin.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        for (int n : new int[]{0, 1, 5, 10}) {
            int a = (int) clazz.getMethod("absSum", int.class).invoke(null, n);
            int b = (int) clazz.getMethod("absSum2", int.class).invoke(null, n);
            assertEquals(a, b, "clone diverged at n=" + n);
        }
    }

    @Test
    void stripStackMapTablesRemovesFramesOnWrite() throws Exception {
        ClassFile cf = compile(SRC, "BE");
        int before = countStackMapTables(new ClassFile(new java.io.ByteArrayInputStream(cf.write())));
        org.junit.jupiter.api.Assertions.assertTrue(before > 0, "fixture should have frames to strip");

        int removed = cf.stripStackMapTables();
        assertEquals(before, removed, "strip count should match the frames present");
        int after = countStackMapTables(new ClassFile(new java.io.ByteArrayInputStream(cf.write())));
        assertEquals(0, after, "no StackMapTable attribute should survive a frameless write");
    }

    @Test
    void branchWideningOnLargeSpan() throws Exception {
        // Insert ~40k NOPs inside a loop and an if/else so branch spans exceed +/-32767, forcing
        // goto->goto_w and conditional->inverted+goto_w. Behaviour is preserved and the class verifies.
        ClassFile cf = compile(
                "public class WD {"
                + " public static int loop(int x) { int s = 0; for (int i = 0; i < x; i++) { s = s + 1; } return s; }"
                + " public static int branch(int x) { int r; if (x > 0) { r = 1; } else { r = 2; } return r; } }",
                "WD");
        for (String mn : new String[]{"loop", "branch"}) {
            CodeWriter cw = new CodeWriter(method(cf, mn));
            List<Instruction> nops = new ArrayList<>();
            for (int i = 0; i < 40000; i++) {
                nops.add(new NopInstruction(0x00, 0));
            }
            cw.insertBefore(middle(cw), nops);
            cw.write();
        }
        // Confirm at least one branch actually widened (otherwise the test isn't exercising widening).
        org.junit.jupiter.api.Assertions.assertTrue(
                hasWideGoto(new CodeWriter(method(cf, "loop"))) || hasWideGoto(new CodeWriter(method(cf, "branch"))),
                "expected a goto_w from widening");

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(3, (int) clazz.getMethod("loop", int.class).invoke(null, 3));
        assertEquals(1, (int) clazz.getMethod("branch", int.class).invoke(null, 5));
        assertEquals(2, (int) clazz.getMethod("branch", int.class).invoke(null, -5));
    }

    private static boolean hasWideGoto(CodeWriter cw) {
        for (Instruction i : cw.getInstructions()) {
            if (i instanceof com.tonic.analysis.instruction.GotoInstruction
                    && ((com.tonic.analysis.instruction.GotoInstruction) i).getType()
                       == com.tonic.analysis.instruction.GotoInstruction.GotoType.GOTO_WIDE) {
                return true;
            }
        }
        return false;
    }

    @Test
    void clonedSwitchSurvivesRealignment() throws Exception {
        // Clone a switch-containing body (identity-tracked targets), install it as another method's
        // body, then prepend NOPs to shift the switch to a different 4-byte alignment. With targets
        // carried by identity the switch relinks (re-pads + retargets) correctly at any alignment.
        ClassFile cf = compile(
                "public class SC { static int pick(int x) { int r; switch (x) {"
                + " case 1: r = 11; break; case 2: r = 22; break; case 5: r = 55; break; default: r = -7; }"
                + " return r; } }", "SC");
        cf.createNewMethodWithDescriptor(new AccessBuilder().setPublic().setStatic().build(), "h", "(I)I");

        CodeWriter pw = new CodeWriter(method(cf, "pick"));
        List<Instruction> pl = new ArrayList<>();
        pw.getInstructions().forEach(pl::add);
        CodeWriter.ClonedRange cr = pw.cloneRangeWithTargets(pl.get(0), pl.get(pl.size() - 1), 0, null, null);

        CodeWriter hw = new CodeWriter(method(cf, "h"));
        hw.replaceBody(cr);
        Instruction first = hw.getInstructions().iterator().next();
        hw.insertBefore(first, java.util.Arrays.asList(
                new NopInstruction(0x00, 0), new NopInstruction(0x00, 0)));
        hw.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        for (int x : new int[]{1, 2, 5, 9}) {
            assertEquals((int) clazz.getMethod("pick", int.class).invoke(null, x),
                    (int) clazz.getMethod("h", int.class).invoke(null, x), "switch clone diverged at x=" + x);
        }
    }

    @Test
    void insertIntoMethodWithTryCatchPreservesHandler() throws Exception {
        // Host has a real try/catch (10/x, catching ArithmeticException -> -1). Insert a NOP whose
        // scratch offset (0) collides with the host's first instruction; the exception table must
        // relink by identity (the prior offset-keyed remap corrupted it on such a collision).
        ClassFile cf = ClassBuilder.create("TC")
                .version(AccessFlags.V11, 0)
                .access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "guarded", "(I)I")
                .code()
                    .label("try")
                    .bipush(10)
                    .iload(0)
                    .idiv()
                    .ireturn()
                    .label("handler")
                    .pop()
                    .iconst(-1)
                    .ireturn()
                    .trycatch("try", "handler", "handler", "java/lang/ArithmeticException")
                .end()
                .end()
                .build();

        CodeWriter cw = new CodeWriter(method(cf, "guarded"));
        cw.insertBefore(cw.getInstructions().iterator().next(), new NopInstruction(0x00, 0));
        cw.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(5, (int) clazz.getMethod("guarded", int.class).invoke(null, 2));
        assertEquals(-1, (int) clazz.getMethod("guarded", int.class).invoke(null, 0));
    }

    private static int countStackMapTables(ClassFile cf) {
        int n = 0;
        for (MethodEntry m : cf.getMethods()) {
            if (m.getCodeAttribute() == null) {
                continue;
            }
            for (com.tonic.parser.attribute.Attribute a : m.getCodeAttribute().getAttributes()) {
                if (a.getClass().getSimpleName().equals("StackMapTableAttribute")) {
                    n++;
                }
            }
        }
        return n;
    }
}
