package com.tonic.demo;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;
import java.util.List;

public class DebugExceptionHandlers {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DebugExceptionHandlers <classfile> <methodName>");
            return;
        }

        ClassFile cf = ClassPool.getDefault().loadClass(new FileInputStream(args[0]));
        ConstPool constPool = cf.getConstPool();
        String methodName = args[1];

        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName)) {
                System.out.println("=== Method: " + method.getName() + " ===");
                System.out.println("Entry block offset: 0");

                SSA ssa = new SSA(constPool);
                IRMethod irMethod = ssa.lift(method);

                List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();
                System.out.println("\nException handlers: " + (handlers == null ? 0 : handlers.size()));

                if (handlers != null) {
                    for (int i = 0; i < handlers.size(); i++) {
                        ExceptionHandler h = handlers.get(i);
                        System.out.println("\nHandler " + i + ":");
                        System.out.println("  tryStart: " + (h.getTryStart() != null ? h.getTryStart().getName() + " (offset " + h.getTryStart().getBytecodeOffset() + ")" : "null"));
                        System.out.println("  tryEnd: " + (h.getTryEnd() != null ? h.getTryEnd().getName() + " (offset " + h.getTryEnd().getBytecodeOffset() + ")" : "null"));
                        System.out.println("  handlerBlock: " + (h.getHandlerBlock() != null ? h.getHandlerBlock().getName() + " (offset " + h.getHandlerBlock().getBytecodeOffset() + ")" : "null"));
                        System.out.println("  catchType: " + (h.getCatchType() != null ? h.getCatchType().getInternalName() : "catch-all"));
                    }
                }

                System.out.println("\n=== Blocks ===");
                for (var block : irMethod.getBlocks()) {
                    System.out.println(block.getName() + " (offset " + block.getBytecodeOffset() + ")");
                }
                break;
            }
        }
    }
}
