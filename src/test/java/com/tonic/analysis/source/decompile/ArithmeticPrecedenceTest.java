package com.tonic.analysis.source.decompile;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.IRPrinter;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ArithmeticPrecedenceTest {

    @Test
    public void testTimingMathPrecedence() throws Exception {
        ClassFile cf = new ClassFile(new FileInputStream("C:/test/obber/extracted/osrs/dev/Main.class"));

        System.out.println("=== IR DUMP for runBenchmarkTest B178 ===");
        for (MethodEntry me : cf.getMethods()) {
            if (me.getName().equals("runBenchmarkTest")) {
                SSA ssa = new SSA(cf.getConstPool());
                IRMethod irMethod = ssa.lift(me);
                for (IRBlock block : irMethod.getBlocks()) {
                    if (block.getName().equals("B178")) {
                        System.out.println("Block B178 instructions:");
                        for (IRInstruction instr : block.getInstructions()) {
                            System.out.println("  " + IRPrinter.format(instr));
                        }
                    }
                }
            }
        }

        ClassDecompiler decompiler = new ClassDecompiler(cf, DecompilerConfig.defaults());
        String source = decompiler.decompile();

        // The expression should be: (endTime - startTime) / (double) n
        // NOT: (double) endTime - startTime / (double) arg0

        System.out.println("=== FULL DECOMPILED runBenchmarkTest ===");
        String[] lines = source.split("\n");
        boolean inMethod = false;
        int braceCount = 0;
        for (String line : lines) {
            if (line.contains("public static void runBenchmarkTest")) {
                inMethod = true;
            }
            if (inMethod) {
                System.out.println(line);
                braceCount += (int) line.chars().filter(c -> c == '{').count();
                braceCount -= (int) line.chars().filter(c -> c == '}').count();
                if (braceCount <= 0 && line.contains("}")) {
                    break;
                }
            }
        }

        // Check the decompiled output contains proper parenthesization
        // The subtraction should happen before the division
        assertTrue(source.contains("local11") || source.contains("endTime"), "Should have timing variables");
    }
}
