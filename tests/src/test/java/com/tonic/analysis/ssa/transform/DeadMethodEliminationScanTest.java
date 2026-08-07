package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transform decides what to delete by walking raw bytecode and recording the calls it finds, so a
 * wrong instruction length makes it miss a call and delete a method that is still reachable. Only
 * private methods are removal candidates, so every probe here is private.
 */
class DeadMethodEliminationScanTest
{

    private static boolean hasMethod(ClassFile cf, String name)
    {
        for (MethodEntry m : cf.getMethods())
        {
            if (m.getName().equals(name))
            {
                return true;
            }
        }
        return false;
    }

    private static ClassFile transformed(String source, String name) throws Exception
    {
        ClassFile cf = TestUtils.compileSource(source, name);
        new DeadMethodElimination().run(cf, new SSA(cf.getConstPool()));
        return cf;
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void anUncalledPrivateMethodIsRemoved() throws Exception
    {
        String source =
                "public class DeadProbeControl {\n"
                + "    public static void main(String[] args) { kept(); }\n"
                + "    private static void kept() { }\n"
                + "    private static void neverCalled() { }\n"
                + "}\n";

        ClassFile cf = transformed(source, "DeadProbeControl");

        assertFalse(hasMethod(cf, "neverCalled"),
            "the transform must actually delete dead methods, or the checks below prove nothing");
        assertTrue(hasMethod(cf, "kept"), "kept() is called from main");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void callsAfterAnEarlierCallAreStillFound() throws Exception
    {
        String source =
                "public class DeadProbeCalls {\n"
                + "    public static void main(String[] args) { first(); second(); third(); }\n"
                + "    private static void first() { }\n"
                + "    private static void second() { }\n"
                + "    private static void third() { }\n"
                + "}\n";

        ClassFile cf = transformed(source, "DeadProbeCalls");

        assertTrue(hasMethod(cf, "first"), "first() is called from main");
        assertTrue(hasMethod(cf, "second"), "second() is called from main and must survive the scan");
        assertTrue(hasMethod(cf, "third"), "third() is called from main and must survive the scan");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void aSynchronizedBlockDoesNotStallTheScan() throws Exception
    {
        String source =
                "public class DeadProbeSync {\n"
                + "    private static final Object LOCK = new Object();\n"
                + "    public static void main(String[] args) {\n"
                + "        synchronized (LOCK) { touch(); }\n"
                + "        after();\n"
                + "    }\n"
                + "    private static void touch() { }\n"
                + "    private static void after() { }\n"
                + "}\n";

        ClassFile cf = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> transformed(source, "DeadProbeSync"),
            "a zero-length instruction would stall the scan instead of finishing it");

        assertTrue(hasMethod(cf, "touch"), "touch() is called inside the synchronized block");
        assertTrue(hasMethod(cf, "after"), "after() is called once the monitor is released");
    }

}
