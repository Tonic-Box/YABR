package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A call's receiver must end up beneath the whole argument on the operand stack. The emitter can push a
 * re-loadable receiver early so the argument need not spill to a local, but the push goes immediately before
 * the instruction that PRODUCES the argument - which is beneath the whole argument only when that one
 * instruction evaluates all of it. For {@code target.absorb(parent.get().flip())} the argument is produced by
 * the last of two chained calls, so the receiver landed between them and {@code flip()} ran against the
 * receiver instead: the two objects swapped roles, silently.
 * <p>
 * Asserted on behaviour, not on text - the fixture records which object each call ran against, so a swap
 * changes the trace rather than merely the formatting.
 */
class ReceiverBeneathArgumentTest {

    private static final String[] LINES = {
            "public class ReceiverOrder {",
            "    static StringBuilder trace = new StringBuilder();",
            "    final String name;",
            "    ReceiverOrder(String name) {",
            "        this.name = name;",
            "    }",
            "    ReceiverOrder get() {",
            "        return this;",
            "    }",
            "    ReceiverOrder flip() {",
            "        trace.append(\"flip:\").append(name).append(' ');",
            "        return this;",
            "    }",
            "    ReceiverOrder absorb(ReceiverOrder other) {",
            "        trace.append(\"absorb:\").append(name).append('/').append(other.name).append(' ');",
            "        return this;",
            "    }",
            "    static void run(ReceiverOrder target, ReceiverOrder parent) {",
            "        target.absorb(parent.get());",
            "        if (parent != null) {",
            "            target.absorb(parent.get().flip());",
            "        }",
            "    }",
            "    public static String check() {",
            "        trace = new StringBuilder();",
            "        run(new ReceiverOrder(\"a\"), new ReceiverOrder(\"b\"));",
            "        return trace.toString().trim();",
            "    }",
            "}",
    };

    @Test
    void aReceiverIsPushedBeneathTheWholeArgument() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("receiver-order");
        Path src = dir.resolve("ReceiverOrder.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("ReceiverOrder.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("absorb:a/b flip:b absorb:a/b", original,
                "the fixture itself must flip the argument and absorb it into the receiver");

        String source = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, source, "ReceiverOrder"),
                "the decompiled source must re-lower:\n" + source);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the re-lowered class must call the same methods on the same objects:\n" + source);
    }
}
