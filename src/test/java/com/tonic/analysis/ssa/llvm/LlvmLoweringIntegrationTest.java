package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.BytecodeBuilder.Label;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end check: lower a computational method to LLVM IR, compile/run it with {@code lli}, and
 * assert the result matches running the same method on the JVM. Skipped (via {@link Assumptions})
 * when {@code lli} is not installed, so CI without LLVM stays green.
 *
 * <p>Results are returned as the process exit code, so test inputs are chosen to yield a small
 * non-negative value (0..127).
 */
class LlvmLoweringIntegrationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
        Assumptions.assumeTrue(toolAvailable("lli"), "lli not available; skipping LLVM run test");
    }

    @Test
    void intAddMatchesJvm() throws Exception {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("add", "(II)I")
            .iload(0).iload(1).iadd().ireturn().build();
        int jvm = (int) TestUtils.loadAndVerify(cf).getMethod("add", int.class, int.class).invoke(null, 7, 5);
        String mangled = SymbolMangler.mangle("T", "add", "(II)I");
        String main = "define i32 @main() {\n  %r = call i32 " + mangled + "(i32 7, i32 5)\n  ret i32 %r\n}\n";
        assertEquals(jvm & 0xFF, runViaLli(cf, "add", main) & 0xFF);
    }

    @Test
    void intLoopMatchesJvm() throws Exception {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("T").publicStaticMethod("sum", "(I)I");
        Label head = mb.newLabel();
        Label end = mb.newLabel();
        ClassFile cf = mb
            .iconst(0).istore(1).iconst(0).istore(2)
            .label(head).iload(2).iload(0).if_icmpge(end)
            .iload(1).iload(2).iadd().istore(1).iinc(2, 1).goto_(head)
            .label(end).iload(1).ireturn().build();
        int jvm = (int) TestUtils.loadAndVerify(cf).getMethod("sum", int.class).invoke(null, 5);
        String mangled = SymbolMangler.mangle("T", "sum", "(I)I");
        String main = "define i32 @main() {\n  %r = call i32 " + mangled + "(i32 5)\n  ret i32 %r\n}\n";
        assertEquals(jvm & 0xFF, runViaLli(cf, "sum", main) & 0xFF);
    }

    private int runViaLli(ClassFile cf, String method, String mainFunction) throws Exception {
        MethodEntry m = cf.getMethods().stream()
            .filter(x -> x.getName().equals(method)).findFirst().orElseThrow();
        IRMethod ir = TestUtils.liftMethod(m);
        String module = new LlvmLowering().lower(ir) + "\n" + mainFunction;
        Path ll = tempDir.resolve(method + ".ll");
        Files.write(ll, module.getBytes());
        Process p = new ProcessBuilder("lli", ll.toString()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        return p.waitFor();
    }

    private static boolean toolAvailable(String tool) {
        try {
            Process p = new ProcessBuilder(tool, "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
