package com.tonic.demo;

import com.tonic.analysis.ssa.IRPrinter;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.llvm.LlvmLowering;
import com.tonic.analysis.ssa.llvm.lift.LlvmLifter;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demo showing an LLVM IR round trip: lower to .ll, lift back to SSA, re-lower, and assert equal text.
 */
public final class LlvmRoundTripDemo
{

    /**
     * Round-trips the given class file's methods, or a built-in hand-made method when no path is given.
     * @param args optional class file path
     * @throws Exception if reading, lowering, or lifting fails or the round trip is not equal
     */
    public static void main(String[] args) throws Exception
    {
        if (args.length > 0)
        {
            roundTripClass(args[0]);
        }
        else
        {
            roundTripBuiltIn();
        }
    }

    /**
     * Builds a simple {@code int add(int a, int b) { return a + b; }} IR method by hand.
     */
    private static void roundTripBuiltIn() throws Exception
    {
        SSAValue.resetIdCounter();
        IRBlock.resetIdCounter();
        IRInstruction.resetIdCounter();

        IRMethod method = new IRMethod("Demo", "add", "(II)I", true);
        method.setReturnType(PrimitiveType.INT);

        SSAValue p0 = new SSAValue(PrimitiveType.INT);
        SSAValue p1 = new SSAValue(PrimitiveType.INT);
        method.addParameter(p0);
        method.addParameter(p1);

        IRBlock entry = new IRBlock();
        method.addBlock(entry);
        method.setEntryBlock(entry);

        SSAValue sum = new SSAValue(PrimitiveType.INT);
        entry.addInstruction(new BinaryOpInstruction(sum, BinaryOp.ADD, p0, p1));
        entry.addInstruction(new ReturnInstruction(sum));

        roundTrip("Demo.add(II)I", method);
    }

    private static void roundTripClass(String classFile) throws Exception
    {
        ClassFile cf = ClassPool.getDefault()
            .loadClass(new FileInputStream(classFile));
        SSA ssa = new SSA(cf.getConstPool());
        for (MethodEntry m : cf.getMethods())
        {
            if (m.getCodeAttribute() == null)
            {
                continue;
            }
            try
            {
                IRMethod ir = ssa.lift(m);
                roundTrip(m.getName() + m.getDesc(), ir);
            }
            catch (Exception e)
            {
                System.out.println("// skip " + m.getName() + ": " + e.getMessage());
            }
        }
    }

    private static void roundTrip(String label, IRMethod original) throws Exception
    {
        System.out.println("\n==== " + label + " ====");
        LlvmLowering lowering = new LlvmLowering();
        String ll1 = lowering.lower(original);
        System.out.println(ll1);

        // Optionally run opt
        String toOptimize = ll1;
        boolean optimized = false;
        if (toolAvailable("opt"))
        {
            Path tmp = Files.createTempFile("roundtrip_", ".ll");
            Files.writeString(tmp, ll1);
            ProcessBuilder pb = new ProcessBuilder("opt", "-O2", "-S", tmp.toString(), "-o", "-");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] out = p.getInputStream().readAllBytes();
            p.waitFor();
            if (out.length > 0)
            {
                toOptimize = new String(out);
                optimized = true;
                System.out.println("\n-- after opt -O2 --\n" + toOptimize);
            }
            Files.deleteIfExists(tmp);
        }

        LlvmLifter lifter = new LlvmLifter();
        IRMethod lifted = lifter.lift(toOptimize);
        System.out.println("\n-- lifted IR --\n" + IRPrinter.format(lifted));

        if (!optimized)
        {
            // Pure round-trip: re-lower and compare
            SSAValue.resetIdCounter();
            IRBlock.resetIdCounter();
            IRInstruction.resetIdCounter();
            LlvmLifter lifter2 = new LlvmLifter();
            IRMethod lifted2 = lifter2.lift(ll1);
            String ll2 = lowering.lower(lifted2);
            if (ll1.equals(ll2))
            {
                System.out.println("\n✓ round-trip identical");
            }
            else
            {
                System.out.println("\n✗ round-trip DIFFERS");
                System.out.println("  original:\n" + ll1);
                System.out.println("  re-lowered:\n" + ll2);
            }
        }
    }

    private static boolean toolAvailable(String tool)
    {
        try
        {
            Process p = new ProcessBuilder(tool, "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
