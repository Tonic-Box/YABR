package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.analysis.source.decompile.DecompilerConfig;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link NameRecoveryStrategy} must decide what the output is actually named. It reaches every place a debug
 * name enters recovery through one gate, so a mode holds for parameters, for body locals, and for the
 * signature line alike - output that must not depend on whether a jar carries debug info can ask for it, and
 * get the same names either way.
 */
class NameRecoveryStrategyTest
{

    private static final String SOURCE = String.join("\n",
            "public class NamedLocals {",
            "    public static int total(int count, int step) {",
            "        int running = 0;",
            "        for (int index = 0; index < count; index++) {",
            "            running = running + step;",
            "        }",
            "        return running;",
            "    }",
            "}",
            "");

    private static ClassFile compiled() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("name-strategy");
        Path src = dir.resolve("NamedLocals.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled with debug info");
        ClassPool pool = TestUtils.emptyPool();
        return pool.loadClass(Files.readAllBytes(dir.resolve("NamedLocals.class")));
    }

    private static String decompileWith(NameRecoveryStrategy strategy) throws Exception
    {
        DecompilerConfig config = DecompilerConfig.builder().nameRecoveryStrategy(strategy).build();
        return new ClassDecompiler(compiled(), config).decompile();
    }

    @Test
    void theDefaultRecoversEveryRecordedName() throws Exception
    {
        String source = decompileWith(NameRecoveryStrategy.PREFER_DEBUG_INFO);
        for (String name : new String[]{"count", "step", "running", "index"})
        {
            assertTrue(source.contains(name), name + " must be recovered from the debug info:\n" + source);
        }
    }

    @Test
    void alwaysSyntheticIgnoresRecordedNamesEverywhere() throws Exception
    {
        String source = decompileWith(NameRecoveryStrategy.ALWAYS_SYNTHETIC);
        for (String name : new String[]{"count", "step", "running", "index"})
        {
            assertFalse(source.contains(name),
                    name + " came from debug info and must not appear under ALWAYS_SYNTHETIC:\n" + source);
        }
        assertTrue(source.contains("arg0") && source.contains("arg1"),
                "parameters must fall back to their positional names:\n" + source);
    }

    @Test
    void parametersOnlyKeepsParameterNamesAndDropsLocalOnes() throws Exception
    {
        String source = decompileWith(NameRecoveryStrategy.PARAMETERS_ONLY);
        assertTrue(source.contains("count") && source.contains("step"),
                "parameter names must still be recovered:\n" + source);
        for (String name : new String[]{"running", "index"})
        {
            assertFalse(source.contains(name),
                    name + " is a body local and must be synthetic under PARAMETERS_ONLY:\n" + source);
        }
    }

    /**
     * The gate itself, at the accessors every naming path goes through. {@code count} is a parameter and
     * {@code running} a body local, so one fixture distinguishes all three modes.
     */
    @Test
    void theGateDecidesPerSlotAtTheAccessors() throws Exception
    {
        ClassFile cf = compiled();
        MethodEntry total = null;
        for (MethodEntry m : cf.getMethods())
        {
            if ("total".equals(m.getName()))
            {
                total = m;
            }
        }
        assertNotNull(total, "the fixture must have a total() method");
        IRMethod ir = TestUtils.liftMethod(total);

        int paramSlot = 0;
        int localSlot = 2;
        assertEquals("count",
                new NameRecoverer(ir, total, NameRecoveryStrategy.PREFER_DEBUG_INFO)
                        .unambiguousDebugName(paramSlot));
        assertEquals("count",
                new NameRecoverer(ir, total, NameRecoveryStrategy.PARAMETERS_ONLY)
                        .unambiguousDebugName(paramSlot));
        assertNull(new NameRecoverer(ir, total, NameRecoveryStrategy.ALWAYS_SYNTHETIC)
                .unambiguousDebugName(paramSlot));

        assertEquals("running",
                new NameRecoverer(ir, total, NameRecoveryStrategy.PREFER_DEBUG_INFO)
                        .unambiguousDebugName(localSlot));
        assertNull(new NameRecoverer(ir, total, NameRecoveryStrategy.PARAMETERS_ONLY) .unambiguousDebugName(localSlot));
        assertNull(new NameRecoverer(ir, total, NameRecoveryStrategy.ALWAYS_SYNTHETIC)
                .unambiguousDebugName(localSlot));
    }

    /**
     * Whichever strategy is in force, the output must still be the source of a class that compiles back.
     */
    @Test
    void everyStrategyStillProducesRecompilableSource() throws Exception
    {
        for (NameRecoveryStrategy strategy : NameRecoveryStrategy.values())
        {
            ClassPool pool = new ClassPool();
            ClassFile cf = compiled();
            pool.getClasses().add(cf);
            DecompilerConfig config = DecompilerConfig.builder().nameRecoveryStrategy(strategy).build();
            String source = new ClassDecompiler(cf, config).decompile();
            assertTrue(TestUtils.recompileSource(cf, pool, source, cf.getClassName()),
                    strategy + " output must recompile:\n" + source);
            assertEquals(42, TestUtils.loadAndVerify(cf).getMethod("total", int.class, int.class)
                    .invoke(null, 6, 7), strategy + " output must behave the same");
        }
    }
}
