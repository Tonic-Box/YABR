package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.LvtSupport;
import org.junit.jupiter.api.Test;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Range-aware LVT attribution invariants that the corpus cannot exercise:
 * <ul>
 * <li>A store takes effect at the NEXT INSTRUCTION, whatever its byte length - the fixed forward
 *     window this replaced missed a store whose following boundary sits more than two bytes away
 *     and could overshoot into the next variable's scope.</li>
 * <li>A duplicated instruction keeps its bytecode offset - the reducibility transform's copies
 *     otherwise feed the partition offset-less instructions whose names all fall back.</li>
 * <li>A parameter is named by the entry covering pc 0 even when its slot is reused later (which
 *     makes the whole-slot name set ambiguous and used to degrade the parameter).</li>
 * </ul>
 */
class RangeAttributionTest {

    /** Compiles a tiny fixture and returns its ClassFile + the requested method. */
    private static Object[] fixture() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("rangeattr");
        Files.writeString(dir.resolve("Rng.java"), String.join(System.lineSeparator(),
                "public class Rng {",
                "    static int f(int a) {",
                "        int s = a + 1;",
                "        if (a > 0) { s = s * 2; }",
                "        return s;",
                "    }",
                "}"));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(),
                dir.resolve("Rng.java").toString()) == 0, "fixture compiled");
        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("Rng.class")));
        MethodEntry m = cf.getMethods().stream().filter(x -> x.getName().equals("f")).findFirst().orElseThrow();
        return new Object[]{cf, m};
    }

    private static LocalVariableTableAttribute lvtOf(MethodEntry m) {
        for (Attribute a : m.getCodeAttribute().getAttributes()) {
            if (a instanceof LocalVariableTableAttribute) {
                return (LocalVariableTableAttribute) a;
            }
        }
        throw new AssertionError("fixture has an LVT");
    }

    @Test
    void aStoreResolvesAtTheNextInstructionNotAFixedWindow() throws Exception {
        Object[] fx = fixture();
        ClassFile cf = (ClassFile) fx[0];
        MethodEntry m = (MethodEntry) fx[1];

        // Rebuild the LVT as a reused slot: the PREVIOUS occupant's entry runs up to the store
        // (covering the store's own pc), and the store's variable begins at the next instruction.
        // A fixed probe starting at the store's pc lands inside the previous occupant's range and
        // returns the WRONG name; the exact rule binds at the boundary where the store takes effect.
        CodeAttribute code = m.getCodeAttribute();
        LocalVariableTableAttribute lvt = lvtOf(m);
        List<com.tonic.parser.attribute.table.LocalVariableTableEntry> entries = new ArrayList<>();
        int codeLen = code.getCode().length;
        int storePc = 3;
        int boundary = 4;
        entries.add(LvtSupport.entry(cf.getConstPool(), 0, "a", "I", 0, codeLen));
        entries.add(LvtSupport.entry(cf.getConstPool(), 1, "olds", "I", 0, boundary));
        entries.add(LvtSupport.entry(cf.getConstPool(), 1, "s", "I", boundary, codeLen - boundary));
        lvt.setLocalVariableTable(entries);

        IRMethod ir = new SSA(cf.getConstPool()).lift(m);
        NameRecoverer names = new NameRecoverer(ir, m, NameRecoveryStrategy.PREFER_DEBUG_INFO);
        assertEquals("s", names.debugNameAtStore(1, storePc),
                "the store binds at the next instruction boundary, not inside the previous range");
        assertEquals("olds", names.debugNameAt(1, storePc),
                "the store's own pc still belongs to the previous occupant");
    }

    @Test
    void aClonedMethodKeepsItsInstructionOffsets() throws Exception {
        Object[] fx = fixture();
        ClassFile cf = (ClassFile) fx[0];
        MethodEntry m = (MethodEntry) fx[1];
        IRMethod ir = new SSA(cf.getConstPool()).lift(m);
        int stamped = 0;
        for (com.tonic.analysis.ssa.cfg.IRBlock b : ir.getBlocks()) {
            for (com.tonic.analysis.ssa.ir.IRInstruction i : b.getInstructions()) {
                if (i.getBytecodeOffset() >= 0) {
                    stamped++;
                }
            }
        }
        assumeTrue(stamped > 0, "the lift stamps offsets");
        IRMethod cloned = new com.tonic.analysis.ssa.util.IRMethodCloner().clone(ir);
        int clonedStamped = 0;
        for (com.tonic.analysis.ssa.cfg.IRBlock b : cloned.getBlocks()) {
            for (com.tonic.analysis.ssa.ir.IRInstruction i : b.getInstructions()) {
                if (i.getBytecodeOffset() >= 0) {
                    clonedStamped++;
                }
            }
        }
        assertEquals(stamped, clonedStamped,
                "every cloned instruction stands in at the original's source position");
    }

    @Test
    void aParameterKeepsItsNameWhenItsSlotIsReusedLater() throws Exception {
        Object[] fx = fixture();
        ClassFile cf = (ClassFile) fx[0];
        MethodEntry m = (MethodEntry) fx[1];

        // Inject a second entry on the PARAMETER's slot covering a later range, as javac emits when
        // it reuses a dead parameter's slot: the whole-slot name set becomes ambiguous.
        CodeAttribute code = m.getCodeAttribute();
        LocalVariableTableAttribute lvt = lvtOf(m);
        int codeLen = code.getCode().length;
        List<com.tonic.parser.attribute.table.LocalVariableTableEntry> entries =
                new ArrayList<>(lvt.getLocalVariableTable());
        entries.add(LvtSupport.entry(cf.getConstPool(), 0, "reused", "I", codeLen - 2, 2));
        lvt.setLocalVariableTable(entries);

        String d1 = com.tonic.analysis.source.decompile.ClassDecompiler.decompile(cf);
        assertTrue(d1.contains("f(int a)"), "the entry covering pc 0 names the parameter, ambiguity notwithstanding:\n" + d1);
    }
}
