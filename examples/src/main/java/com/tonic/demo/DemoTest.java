package com.tonic.demo;

import com.tonic.analysis.source.ast.ASTUtils;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.io.InputStream;

public class DemoTest {
    public static void main(String[] args) throws Exception
    {
        ClassPool classPool = ClassPool.getDefault();

        try (InputStream is = DemoTest.class.getResourceAsStream("DemoClass.class")) {
            if(is == null)
            {
                return;
            }
            ClassFile classFile = classPool.loadClass(is);
            ConstPool constPool = classFile.getConstPool();

            MethodEntry main = classFile.getMethod("test", "([I)V");

            SSA ssa = new SSA(constPool);
            IRMethod irMethod = ssa.lift(main);
            BlockStmt body = MethodRecoverer.recoverMethod(irMethod, main);

            ASTUtils.forEachStatement(body, stmt -> {
                System.out.println("[" + stmt.getClass().getName() + "] " + stmt.toString());
            });
        }
    }
}
