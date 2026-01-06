package com.tonic.analysis.source.decompile;

import com.tonic.analysis.ssa.IRPrinter;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.SwitchInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObfuscatedDecompileTest {

    @Test
    void decompileObfuscatedKeyPressed() throws Exception {
        byte[] classBytes = Files.readAllBytes(Paths.get("a.class"));
        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(classBytes);

        String decompiled = ClassDecompiler.decompile(cf);

        Files.writeString(Paths.get("decompiled_output.java"), decompiled);

        assertNotNull(decompiled);
        assertTrue(decompiled.contains("keyPressed"));
    }

    @Test
    void dumpIRForKeyPressed() throws Exception {
        byte[] classBytes = Files.readAllBytes(Paths.get("a.class"));
        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(classBytes);

        MethodEntry method = null;
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals("keyPressed")) {
                method = m;
                break;
            }
        }
        assertNotNull(method, "keyPressed method not found");

        SSA ssa = new SSA(cf.getConstPool());
        IRMethod ir = ssa.lift(method);

        String irDump = IRPrinter.format(ir);
        Files.writeString(Paths.get("ir_dump.txt"), irDump);
    }
}
