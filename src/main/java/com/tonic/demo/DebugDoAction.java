package com.tonic.demo;

import com.tonic.analysis.source.ast.ASTUtils;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds if-blocks matching pattern: if (var == const) or if (const == var).
 */
public class DebugDoAction {

    public static void main(String[] args) throws Exception {
        String classPath = "C:/test/dumper/do.class";
        String methodName = "lx";

        ClassFile cf = ClassPool.getDefault().loadClass(new FileInputStream(classPath));

        MethodEntry target = null;
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(methodName)) {
                target = m;
                break;
            }
        }
        if (target == null) {
            System.err.println("Method not found: " + methodName);
            return;
        }

        IRMethod ir = new SSA(cf.getConstPool()).lift(target);
        BlockStmt body = MethodRecoverer.recoverMethod(ir, target);
        List<IfStmt> matches = new ArrayList<>();
        ASTUtils.forEachStatement(body, stmt -> {
            if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                if (isVarEqualsConst(ifStmt.getCondition())) {
                    matches.add(ifStmt);
                }
            }
        });

        System.out.println("Found " + matches.size() + " matching if(var == const) blocks:\n");
        for (int i = 0; i < matches.size(); i++) {
            IfStmt ifStmt = matches.get(i);
            System.out.println("[" + (i + 1) + "] " + SourceEmitter.emit(ifStmt.getCondition()));
            System.out.println(SourceEmitter.emit(ifStmt));
            System.out.println();
        }
    }

    private static boolean isVarEqualsConst(Expression expr) {
        if (!(expr instanceof BinaryExpr)) return false;
        BinaryExpr bin = (BinaryExpr) expr;
        if (bin.getOperator() != BinaryOperator.EQ) return false;
        return (bin.getLeft() instanceof VarRefExpr && bin.getRight() instanceof LiteralExpr)
            || (bin.getLeft() instanceof LiteralExpr && bin.getRight() instanceof VarRefExpr);
    }
}
