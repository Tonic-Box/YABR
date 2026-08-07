package com.tonic.analysis.instrumentation;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.InstrumentationRecorder;
import com.tonic.testutil.TestClassLoader;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs instrumented bytecode. The rest of the instrumentation suite asserts that the builder accepted a
 * hook and that {@code apply()} returned a count; none of it loads the modified class, so a weave that
 * silently produced nothing - or produced unverifiable code - would pass everywhere. These tests link the
 * instrumented class, invoke it, and assert the hook actually fired.
 */
class InstrumentedExecutionTest
{

    private static final String RECORDER = "com/tonic/testutil/InstrumentationRecorder";

    @BeforeEach
    void setUp()
    {
        InstrumentationRecorder.reset();
    }

    /**
     * Compiles a target class, applies the hooks the caller registered, then defines and links the
     * instrumented result so the JVM verifier runs over it.
     */
    private static Class<?> instrumentAndLoad(String source, String internalName,
            java.util.function.Consumer<Instrumenter> register) throws Exception
    {
        ClassFile target = TestUtils.compileSource(source, internalName);
        Instrumenter instrumenter = Instrumenter.forClass(target);
        register.accept(instrumenter);
        int points = instrumenter.apply();
        assertTrue(points > 0, "the weave must apply at least one instrumentation point");

        TestClassLoader loader = new TestClassLoader();
        String binary = internalName.replace('/', '.');
        loader.defineClass(binary, target.write());
        return Class.forName(binary, true, loader);
    }

    @Test
    void methodEntryHookFiresWhenTheInstrumentedMethodRuns() throws Exception
    {
        Class<?> cls = instrumentAndLoad(
                "public class EntryTarget {\n"
                + "    public static int work() {\n"
                + "        return 7;\n"
                + "    }\n"
                + "}\n",
                "test/EntryTarget",
                in -> in.onMethodEntry()
                        .callStatic(RECORDER, "onEntry", "()V")
                        .register());

        Method work = cls.getMethod("work");
        assertEquals(List.of(), InstrumentationRecorder.events(), "the hook must not fire before the call");

        Object result = work.invoke(null);

        assertEquals(7, result, "instrumentation must not change what the method returns");
        assertEquals(List.of("entry"), InstrumentationRecorder.events(),
                "the entry hook must fire exactly once per call");
    }

    @Test
    void methodEntryHookFiresOncePerCall() throws Exception
    {
        Class<?> cls = instrumentAndLoad(
                "public class RepeatTarget {\n"
                + "    public static int work() {\n"
                + "        return 1;\n"
                + "    }\n"
                + "}\n",
                "test/RepeatTarget",
                in -> in.onMethodEntry()
                        .callStatic(RECORDER, "onEntry", "()V")
                        .register());

        Method work = cls.getMethod("work");
        work.invoke(null);
        work.invoke(null);
        work.invoke(null);

        assertEquals(List.of("entry", "entry", "entry"), InstrumentationRecorder.events(),
                "each invocation must fire the hook once");
    }

    @Test
    void methodExitHookFiresAfterTheBodyRuns() throws Exception
    {
        Class<?> cls = instrumentAndLoad(
                "public class ExitTarget {\n"
                + "    public static int work() {\n"
                + "        return 3;\n"
                + "    }\n"
                + "}\n",
                "test/ExitTarget",
                in -> in.onMethodExit()
                        .callStatic(RECORDER, "onExit", "()V")
                        .register());

        Object result = cls.getMethod("work").invoke(null);

        assertEquals(3, result, "instrumentation must not change what the method returns");
        assertEquals(List.of("exit"), InstrumentationRecorder.events(), "the exit hook must fire");
    }

    @Test
    void entryAndExitHooksBothFireInOrder() throws Exception
    {
        Class<?> cls = instrumentAndLoad(
                "public class BothTarget {\n"
                + "    public static int work() {\n"
                + "        return 5;\n"
                + "    }\n"
                + "}\n",
                "test/BothTarget",
                in -> {
                    in.onMethodEntry().callStatic(RECORDER, "onEntry", "()V").register();
                    in.onMethodExit().callStatic(RECORDER, "onExit", "()V").register();
                });

        cls.getMethod("work").invoke(null);

        assertEquals(List.of("entry", "exit"), InstrumentationRecorder.events(),
                "entry must be woven before the body and exit after it");
    }

    @Test
    void methodNameIsPassedToTheHook() throws Exception
    {
        Class<?> cls = instrumentAndLoad(
                "public class NamedTarget {\n"
                + "    public static int work() {\n"
                + "        return 0;\n"
                + "    }\n"
                + "}\n",
                "test/NamedTarget",
                in -> in.onMethodEntry()
                        .withMethodName()
                        .callStatic(RECORDER, "onEntryNamed", "(Ljava/lang/String;)V")
                        .register());

        cls.getMethod("work").invoke(null);

        assertEquals(List.of("entry:work"), InstrumentationRecorder.events(),
                "the hook must receive the instrumented method's name");
    }

    @Test
    void anUninstrumentedMethodIsLeftAlone() throws Exception
    {
        Class<?> cls = instrumentAndLoad(
                "public class FilteredTarget {\n"
                + "    public static int hooked() {\n"
                + "        return 1;\n"
                + "    }\n"
                + "    public static int untouched() {\n"
                + "        return 2;\n"
                + "    }\n"
                + "}\n",
                "test/FilteredTarget",
                in -> in.onMethodEntry()
                        .matchingMethod("hooked")
                        .callStatic(RECORDER, "onEntry", "()V")
                        .register());

        assertEquals(2, cls.getMethod("untouched").invoke(null));
        assertEquals(List.of(), InstrumentationRecorder.events(),
                "a method the filter excluded must not call the hook");

        assertEquals(1, cls.getMethod("hooked").invoke(null));
        assertEquals(List.of("entry"), InstrumentationRecorder.events(),
                "the matching method must still be instrumented");
    }
}
