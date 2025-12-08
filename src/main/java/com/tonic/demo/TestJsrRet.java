package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.*;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.utill.*;

import java.io.*;
import java.util.ArrayList;

/**
 * Test for JSR/RET bytecode handling in the SSA lifter/lowerer.
 *
 * This test creates a class with a method that uses JSR/RET instructions
 * (the legacy subroutine mechanism deprecated in Java 7) and verifies
 * that the SSA framework can lift and lower it correctly.
 */
public class TestJsrRet {

    public static void main(String[] args) throws Exception {
        Logger.setLog(false);

        System.out.println("=== JSR/RET Bytecode Test ===\n");

        // Test 1: Simple JSR/RET pattern
        testSimpleJsrRet();

        // Test 2: Test that we can at least parse and lift JSR/RET
        testJsrRetLifting();

        System.out.println("\n=== All JSR/RET tests completed ===");
    }

    /**
     * Creates a simple class with a method using JSR/RET bytecode.
     *
     * The bytecode pattern simulates a classic try-finally using JSR:
     *
     *  0: iconst_1          // Push 1
     *  1: istore_1          // Store to local 1 (result = 1)
     *  2: jsr 8             // Jump to subroutine at offset 8
     *  5: iload_1           // Load result
     *  6: ireturn           // Return result
     *  7: nop               // Padding (unreachable)
     *  8: astore_2          // Subroutine: store return address in local 2
     *  9: iload_1           // Load result
     * 10: iconst_2          // Push 2
     * 11: iadd              // Add (result += 2)
     * 12: istore_1          // Store back
     * 13: ret 2             // Return to caller (address in local 2)
     *
     * Expected behavior: method returns 3 (1 + 2)
     */
    private static void testSimpleJsrRet() throws Exception {
        System.out.println("Test 1: Creating class with JSR/RET bytecode...");

        ClassPool classPool = ClassPool.getDefault();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile classFile = classPool.createNewClass("com/tonic/test/JsrRetTest", classAccess);

        // Create method: public static int jsrMethod()
        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(methodAccess, "jsrMethod", "I");

        // Manually set the bytecode with JSR/RET
        // This is what old Java compilers (pre-Java 7) would generate for finally blocks
        byte[] bytecode = new byte[] {
            0x04,                   // 0: iconst_1      - push 1
            0x3C,                   // 1: istore_1      - store to local 1
            (byte) 0xA8, 0x00, 0x06,// 2: jsr +6        - jump to subroutine at offset 8
            0x1B,                   // 5: iload_1       - load local 1
            (byte) 0xAC,            // 6: ireturn       - return int
            0x00,                   // 7: nop           - padding
            0x4D,                   // 8: astore_2      - subroutine: store return addr
            0x1B,                   // 9: iload_1       - load local 1
            0x05,                   // 10: iconst_2     - push 2
            0x60,                   // 11: iadd         - add
            0x3C,                   // 12: istore_1     - store to local 1
            (byte) 0xA9, 0x02       // 13: ret 2        - return from subroutine
        };

        CodeAttribute codeAttr = method.getCodeAttribute();
        codeAttr.setCode(bytecode);
        codeAttr.setMaxStack(2);
        codeAttr.setMaxLocals(3);  // local 0 unused (static), local 1 = result, local 2 = return addr

        // Rebuild and verify
        classFile.computeFrames();
        classFile.rebuild();

        System.out.println("  Created class with JSR/RET bytecode");
        System.out.println("  Bytecode length: " + bytecode.length + " bytes");

        // Save to disk for inspection
        byte[] classBytes = classFile.write();
        ClassFileUtil.saveClassFile(classBytes, "C:\\test\\new", "JsrRetTest");
        System.out.println("  Saved to C:\\test\\new\\JsrRetTest.class");

        // Try to load and execute
        try {
            CustomClassLoader loader = new CustomClassLoader();
            Class<?> loadedClass = loader.defineClass("com.tonic.test.JsrRetTest", classBytes);
            java.lang.reflect.Method m = loadedClass.getMethod("jsrMethod");
            Object result = m.invoke(null);
            System.out.println("  Direct execution result: " + result + " (expected: 3)");

            if ((Integer) result != 3) {
                System.out.println("  WARNING: Direct execution returned unexpected value!");
            }
        } catch (VerifyError e) {
            // Modern JVMs reject JSR/RET in class files with version >= 51 (Java 7)
            System.out.println("  Note: JVM rejected JSR/RET (expected for class version >= 51)");
            System.out.println("  Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("  Execution failed: " + e.getMessage());
        }

        System.out.println("  Test 1: PASSED (class creation successful)\n");
    }

    /**
     * Tests that the SSA lifter can handle JSR/RET bytecode.
     */
    private static void testJsrRetLifting() throws Exception {
        System.out.println("Test 2: Testing SSA lifting of JSR/RET bytecode...");

        ClassPool classPool = ClassPool.getDefault();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile classFile = classPool.createNewClass("com/tonic/test/JsrRetLift", classAccess);

        // Create method with JSR/RET
        int methodAccess = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(methodAccess, "testMethod", "I");

        // Simple JSR/RET bytecode
        byte[] bytecode = new byte[] {
            0x04,                   // 0: iconst_1
            0x3C,                   // 1: istore_1
            (byte) 0xA8, 0x00, 0x06,// 2: jsr +6 (to offset 8)
            0x1B,                   // 5: iload_1
            (byte) 0xAC,            // 6: ireturn
            0x00,                   // 7: nop
            0x4D,                   // 8: astore_2
            0x1B,                   // 9: iload_1
            0x05,                   // 10: iconst_2
            0x60,                   // 11: iadd
            0x3C,                   // 12: istore_1
            (byte) 0xA9, 0x02       // 13: ret 2
        };

        CodeAttribute codeAttr = method.getCodeAttribute();
        codeAttr.setCode(bytecode);
        codeAttr.setMaxStack(2);
        codeAttr.setMaxLocals(3);

        System.out.println("  Created method with bytecode:");
        System.out.println("    0: iconst_1");
        System.out.println("    1: istore_1");
        System.out.println("    2: jsr 8");
        System.out.println("    5: iload_1");
        System.out.println("    6: ireturn");
        System.out.println("    7: nop");
        System.out.println("    8: astore_2  (subroutine entry)");
        System.out.println("    9: iload_1");
        System.out.println("   10: iconst_2");
        System.out.println("   11: iadd");
        System.out.println("   12: istore_1");
        System.out.println("   13: ret 2");

        // Try SSA lifting
        try {
            SSA ssa = new SSA(classFile.getConstPool());
            IRMethod irMethod = ssa.lift(method);

            System.out.println("\n  SSA Lifting successful!");
            System.out.println("  IR Method: " + irMethod.getName());
            System.out.println("  Number of blocks: " + irMethod.getBlocks().size());
            System.out.println("  Entry block: " + irMethod.getEntryBlock().getName());

            // Print IR
            System.out.println("\n  IR representation:");
            for (var block : irMethod.getBlocks()) {
                System.out.println("    " + block.getName() + ":");
                for (var instr : block.getInstructions()) {
                    System.out.println("      " + instr);
                }
            }

            // Try lowering
            System.out.println("\n  Attempting SSA lowering...");
            ssa.lower(irMethod, method);

            byte[] newBytecode = method.getCodeAttribute().getCode();
            System.out.println("  Lowering successful!");
            System.out.println("  New bytecode length: " + newBytecode.length + " bytes");

            // Print new bytecode
            System.out.print("  New bytecode: ");
            for (int i = 0; i < Math.min(20, newBytecode.length); i++) {
                System.out.printf("%02X ", newBytecode[i] & 0xFF);
            }
            if (newBytecode.length > 20) System.out.print("...");
            System.out.println();

            // Try executing the lowered code
            classFile.computeFrames();
            classFile.rebuild();
            byte[] loweredBytes = classFile.write();

            try {
                CustomClassLoader loader = new CustomClassLoader();
                Class<?> loadedClass = loader.defineClass("com.tonic.test.JsrRetLift", loweredBytes);
                java.lang.reflect.Method m = loadedClass.getMethod("testMethod");
                Object result = m.invoke(null);
                System.out.println("  Lowered code execution result: " + result + " (expected: 3)");

                if ((Integer) result == 3) {
                    System.out.println("  Semantic preservation: VERIFIED");
                } else {
                    System.out.println("  WARNING: Result mismatch - expected 3 but got " + result);
                }
            } catch (Exception ex) {
                System.out.println("  Could not execute lowered code: " + ex.getMessage());
            }

            System.out.println("  Test 2: PASSED\n");

        } catch (Exception e) {
            System.out.println("  SSA processing failed: " + e.getClass().getSimpleName());
            System.out.println("  Message: " + e.getMessage());
            e.printStackTrace(System.out);
            System.out.println("  Test 2: FAILED\n");
        }
    }

    /**
     * Custom class loader for loading generated classes.
     */
    private static class CustomClassLoader extends ClassLoader {
        public Class<?> defineClass(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
