package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplifies control flow in AST to reduce nesting depth.
 *
 * Transformations:
 * 1. Empty if-block inversion: if(x){} else{body} -> if(!x){body}
 * 2. Guard clause conversion: if(x){long} else{return} -> if(!x)return; long
 */
public class ControlFlowSimplifier implements ASTTransform {

    @Override
    public String getName() {
        return "ControlFlowSimplifier";
    }

    @Override
    public boolean transform(BlockStmt block) {
        boolean changed = false;
        List<Statement> stmts = block.getStatements();

        // First: recurse into nested blocks (bottom-up)
        for (Statement stmt : stmts) {
            changed |= recurseInto(stmt);
        }

        // Run cleanup passes until no changes (fixpoint)
        boolean passChanged;
        do {
            passChanged = false;
            passChanged |= removeRedundantStatements(stmts);
            passChanged |= moveDeclarationsToFirstUse(stmts);
            changed |= passChanged;
        } while (passChanged);

        // Transform control flow (if-else, guards, etc.)
        for (int i = 0; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);

            if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;

                Statement replacement = tryConvertIfElseToAssignment(ifStmt);
                if (replacement != null) {
                    stmts.set(i, replacement);
                    changed = true;
                    continue;
                }

                changed |= transformIf(ifStmt, stmts, i);
            }
        }

        return changed;
    }

    private boolean transformIf(IfStmt ifStmt, List<Statement> parentList, int index) {
        boolean changed = false;

        // Transform 1: Empty if-block inversion
        if (isEmptyBlock(ifStmt.getThenBranch()) && ifStmt.hasElse()) {
            invertCondition(ifStmt);
            ifStmt.setThenBranch(ifStmt.getElseBranch());
            ifStmt.setElseBranch(null);
            changed = true;
        }

        // Transform 2: AND-chain merging - nested ifs without else
        if (!ifStmt.hasElse()) {
            Statement inner = unwrapSingleStatement(ifStmt.getThenBranch());
            if (inner instanceof IfStmt) {
                IfStmt innerIf = (IfStmt) inner;
                if (!innerIf.hasElse()) {
                    // Merge: if(a) { if(b) { body } } -> if(a && b) { body }
                    Expression combined = new BinaryExpr(
                        BinaryOperator.AND,
                        ifStmt.getCondition(),
                        innerIf.getCondition(),
                        PrimitiveSourceType.BOOLEAN
                    );
                    ifStmt.setCondition(combined);
                    ifStmt.setThenBranch(innerIf.getThenBranch());
                    changed = true;
                }
            }
        }

        // Transform 3: Guard clause - else is early exit
        if (ifStmt.hasElse() && isEarlyExit(ifStmt.getElseBranch())) {
            Statement earlyExit = unwrapSingleStatement(ifStmt.getElseBranch());
            Statement thenBody = ifStmt.getThenBranch();

            // Create guard: if(!cond) return/throw;
            IfStmt guard = new IfStmt(negate(ifStmt.getCondition()), earlyExit);

            // Replace original if with guard + then body
            parentList.set(index, guard);

            // Insert then body statements after guard
            List<Statement> thenStmts = getStatements(thenBody);
            for (int j = 0; j < thenStmts.size(); j++) {
                parentList.add(index + 1 + j, thenStmts.get(j));
            }
            changed = true;
        }

        return changed;
    }

    private boolean recurseInto(Statement stmt) {
        boolean changed = false;

        if (stmt instanceof BlockStmt) {
            changed |= transform((BlockStmt) stmt);
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            if (ifStmt.getThenBranch() instanceof BlockStmt) {
                changed |= transform((BlockStmt) ifStmt.getThenBranch());
            }
            if (ifStmt.hasElse() && ifStmt.getElseBranch() instanceof BlockStmt) {
                changed |= transform((BlockStmt) ifStmt.getElseBranch());
            }
        } else if (stmt instanceof WhileStmt) {
            WhileStmt ws = (WhileStmt) stmt;
            if (ws.getBody() instanceof BlockStmt) {
                changed |= transform((BlockStmt) ws.getBody());
            }
        } else if (stmt instanceof ForStmt) {
            ForStmt fs = (ForStmt) stmt;
            if (fs.getBody() instanceof BlockStmt) {
                changed |= transform((BlockStmt) fs.getBody());
            }
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tc = (TryCatchStmt) stmt;
            if (tc.getTryBlock() instanceof BlockStmt) {
                changed |= transform((BlockStmt) tc.getTryBlock());
            }
            for (CatchClause cc : tc.getCatches()) {
                if (cc.body() instanceof BlockStmt) {
                    changed |= transform((BlockStmt) cc.body());
                }
            }
            if (tc.getFinallyBlock() instanceof BlockStmt) {
                changed |= transform((BlockStmt) tc.getFinallyBlock());
            }
        }

        return changed;
    }

    private boolean isEmptyBlock(Statement stmt) {
        if (stmt instanceof BlockStmt) {
            return ((BlockStmt) stmt).isEmpty();
        }
        return false;
    }

    private boolean isEarlyExit(Statement stmt) {
        Statement unwrapped = unwrapSingleStatement(stmt);
        return unwrapped instanceof ReturnStmt || unwrapped instanceof ThrowStmt;
    }

    private Statement unwrapSingleStatement(Statement stmt) {
        if (stmt instanceof BlockStmt) {
            BlockStmt block = (BlockStmt) stmt;
            if (block.size() == 1) {
                return block.getStatements().get(0);
            }
        }
        return stmt;
    }

    private List<Statement> getStatements(Statement stmt) {
        if (stmt instanceof BlockStmt) {
            return new ArrayList<>(((BlockStmt) stmt).getStatements());
        }
        List<Statement> list = new ArrayList<>();
        list.add(stmt);
        return list;
    }

    private void invertCondition(IfStmt ifStmt) {
        ifStmt.setCondition(negate(ifStmt.getCondition()));
    }

    private Expression negate(Expression expr) {
        // If already negated, remove negation
        if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            if (unary.getOperator() == UnaryOperator.NOT) {
                return unary.getOperand();
            }
        }

        // If comparison, flip operator instead of wrapping with NOT
        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            BinaryOperator flipped = flipComparison(binary.getOperator());
            if (flipped != null) {
                return new BinaryExpr(flipped, binary.getLeft(), binary.getRight(), binary.getType());
            }
        }

        // Otherwise wrap with NOT
        return new UnaryExpr(UnaryOperator.NOT, expr, PrimitiveSourceType.BOOLEAN);
    }

    private BinaryOperator flipComparison(BinaryOperator op) {
        switch (op) {
            case EQ: return BinaryOperator.NE;
            case NE: return BinaryOperator.EQ;
            case LT: return BinaryOperator.GE;
            case LE: return BinaryOperator.GT;
            case GT: return BinaryOperator.LE;
            case GE: return BinaryOperator.LT;
            default: return null;
        }
    }

    /**
     * Removes redundant statements: self-assignments, consecutive duplicates, empty blocks.
     */
    private boolean removeRedundantStatements(List<Statement> stmts) {
        boolean changed = false;

        for (int i = stmts.size() - 1; i >= 0; i--) {
            Statement stmt = stmts.get(i);

            // Remove empty blocks
            if (stmt instanceof BlockStmt && ((BlockStmt) stmt).isEmpty()) {
                stmts.remove(i);
                changed = true;
                continue;
            }

            // Check for self-assignment or duplicate assignment
            if (stmt instanceof ExprStmt) {
                Expression expr = ((ExprStmt) stmt).getExpression();
                if (expr instanceof BinaryExpr) {
                    BinaryExpr binary = (BinaryExpr) expr;
                    if (binary.getOperator() == BinaryOperator.ASSIGN) {
                        // Self-assignment: x = x
                        if (isSameVariable(binary.getLeft(), binary.getRight())) {
                            stmts.remove(i);
                            changed = true;
                            continue;
                        }

                        // Check for duplicate consecutive assignment to same var
                        if (i > 0) {
                            Statement prev = stmts.get(i - 1);
                            if (isDuplicateAssignment(prev, binary.getLeft())) {
                                stmts.remove(i - 1);
                                i--; // adjust index
                                changed = true;
                            }
                        }
                    }
                }
            }
        }

        return changed;
    }

    private boolean isSameVariable(Expression left, Expression right) {
        if (left instanceof VarRefExpr && right instanceof VarRefExpr) {
            return ((VarRefExpr) left).getName().equals(((VarRefExpr) right).getName());
        }
        return false;
    }

    private boolean isDuplicateAssignment(Statement prev, Expression targetVar) {
        if (!(prev instanceof ExprStmt)) return false;
        Expression prevExpr = ((ExprStmt) prev).getExpression();
        if (!(prevExpr instanceof BinaryExpr)) return false;
        BinaryExpr prevBinary = (BinaryExpr) prevExpr;
        if (prevBinary.getOperator() != BinaryOperator.ASSIGN) return false;
        return isSameVariable(prevBinary.getLeft(), targetVar);
    }

    /**
     * Converts if-else that assigns 0/1 to same variable into boolean expression.
     * if (cond) { x = 0; } else { x = 1; } -> x = cond ? 0 : 1;
     * if (cond) { x = 0; } else { x = 1; } where x is int assigned 0/1 -> x = !cond ? 1 : 0 or simplified
     */
    private Statement tryConvertIfElseToAssignment(IfStmt ifStmt) {
        if (!ifStmt.hasElse()) return null;

        // Get single statements from both branches
        Statement thenStmt = unwrapSingleStatement(ifStmt.getThenBranch());
        Statement elseStmt = unwrapSingleStatement(ifStmt.getElseBranch());

        // Both must be expression statements with assignments
        BinaryExpr thenAssign = getAssignment(thenStmt);
        BinaryExpr elseAssign = getAssignment(elseStmt);
        if (thenAssign == null || elseAssign == null) return null;

        // Must assign to same variable
        if (!isSameVariable(thenAssign.getLeft(), elseAssign.getLeft())) return null;

        // Get the literal values
        Integer thenVal = getIntLiteral(thenAssign.getRight());
        Integer elseVal = getIntLiteral(elseAssign.getRight());
        if (thenVal == null || elseVal == null) return null;

        // Pattern: if (cond) { x = 0; } else { x = 1; } -> x = cond ? 0 : 1
        // Or simplify to boolean if 0/1
        Expression newValue;
        if (thenVal == 0 && elseVal == 1) {
            // x = !cond (as int, use ternary)
            newValue = new TernaryExpr(
                ifStmt.getCondition(),
                LiteralExpr.ofInt(0),
                LiteralExpr.ofInt(1),
                thenAssign.getType()
            );
        } else if (thenVal == 1 && elseVal == 0) {
            // x = cond (as int, use ternary)
            newValue = new TernaryExpr(
                ifStmt.getCondition(),
                LiteralExpr.ofInt(1),
                LiteralExpr.ofInt(0),
                thenAssign.getType()
            );
        } else {
            // General ternary
            newValue = new TernaryExpr(
                ifStmt.getCondition(),
                thenAssign.getRight(),
                elseAssign.getRight(),
                thenAssign.getType()
            );
        }

        Expression newAssign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            thenAssign.getLeft(),
            newValue,
            thenAssign.getType()
        );

        return new ExprStmt(newAssign);
    }

    private BinaryExpr getAssignment(Statement stmt) {
        if (!(stmt instanceof ExprStmt)) return null;
        Expression expr = ((ExprStmt) stmt).getExpression();
        if (!(expr instanceof BinaryExpr)) return null;
        BinaryExpr binary = (BinaryExpr) expr;
        if (binary.getOperator() != BinaryOperator.ASSIGN) return null;
        return binary;
    }

    private Integer getIntLiteral(Expression expr) {
        if (!(expr instanceof LiteralExpr)) return null;
        Object val = ((LiteralExpr) expr).getValue();
        if (val instanceof Integer) return (Integer) val;
        return null;
    }

    // ========== Declaration Movement ==========

    private boolean moveDeclarationsToFirstUse(List<Statement> stmts) {
        boolean changed = false;

        // Pass 1: Move to first assignment in same scope
        for (int i = 0; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);
            if (!(stmt instanceof VarDeclStmt)) continue;

            VarDeclStmt decl = (VarDeclStmt) stmt;
            if (!isDefaultValue(decl.getInitializer())) continue;

            String varName = decl.getName();
            int firstAssignIdx = findFirstAssignment(stmts, i + 1, varName);

            if (firstAssignIdx == -1) continue;
            if (isVariableReadBetween(stmts, i + 1, firstAssignIdx, varName)) continue;

            // Safe to merge
            BinaryExpr assign = getAssignmentTo(stmts.get(firstAssignIdx), varName);
            if (assign == null) continue;

            // If assigning same default value, just remove the redundant assignment
            if (isDefaultValue(assign.getRight())) {
                stmts.remove(firstAssignIdx);
                changed = true;
                i--;
                continue;
            }

            VarDeclStmt merged = new VarDeclStmt(decl.getType(), varName, assign.getRight());
            stmts.remove(i);
            stmts.set(firstAssignIdx - 1, merged);
            changed = true;
            i--;
        }

        // Pass 2: Move declarations into nested blocks when variable only used there
        changed |= moveDeclarationsIntoBlocks(stmts);

        return changed;
    }

    private boolean moveDeclarationsIntoBlocks(List<Statement> stmts) {
        boolean changed = false;

        for (int i = 0; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);
            if (!(stmt instanceof VarDeclStmt)) continue;

            VarDeclStmt decl = (VarDeclStmt) stmt;
            if (!isDefaultValue(decl.getInitializer())) continue;

            String varName = decl.getName();

            // Find which nested block exclusively uses this variable
            BlockStmt targetBlock = findExclusiveUseBlock(stmts, i + 1, varName);
            if (targetBlock == null) continue;

            // Move declaration into the target block
            stmts.remove(i);
            targetBlock.getStatements().add(0, decl);

            // Re-run transforms on the target block since we modified it
            transform(targetBlock);

            changed = true;
            i--;
        }

        return changed;
    }

    private BlockStmt findExclusiveUseBlock(List<Statement> stmts, int start, String varName) {
        BlockStmt candidate = null;

        for (int i = start; i < stmts.size(); i++) {
            Statement s = stmts.get(i);

            // Check if used in simple statements before any block
            if (s instanceof ExprStmt || s instanceof VarDeclStmt || s instanceof ReturnStmt) {
                if (readsVariable(s, varName)) return null;
                continue;
            }

            // Try block - check if variable is only used in try body (not catch/finally)
            if (s instanceof TryCatchStmt) {
                TryCatchStmt tc = (TryCatchStmt) s;
                boolean usedInTry = usesVariable(tc.getTryBlock(), varName);
                boolean usedInCatch = false;
                for (CatchClause cc : tc.getCatches()) {
                    if (usesVariable(cc.body(), varName)) usedInCatch = true;
                }
                boolean usedInFinally = tc.getFinallyBlock() != null && usesVariable(tc.getFinallyBlock(), varName);

                if (usedInTry && !usedInCatch && !usedInFinally) {
                    if (candidate != null) return null; // Used in multiple blocks
                    if (tc.getTryBlock() instanceof BlockStmt) {
                        candidate = (BlockStmt) tc.getTryBlock();
                    }
                } else if (usedInCatch || usedInFinally) {
                    return null; // Can't move - used in catch/finally
                }
                continue;
            }

            // Other control flow - conservative
            if (usesVariable(s, varName)) return null;
        }

        return candidate;
    }

    private boolean usesVariable(Statement stmt, String varName) {
        if (stmt == null) return false;
        if (stmt instanceof BlockStmt) {
            for (Statement s : ((BlockStmt) stmt).getStatements()) {
                if (usesVariable(s, varName)) return true;
            }
            return false;
        }
        if (stmt instanceof ExprStmt) {
            return readsVariableExpr(((ExprStmt) stmt).getExpression(), varName, false);
        }
        if (stmt instanceof VarDeclStmt) {
            VarDeclStmt vd = (VarDeclStmt) stmt;
            if (vd.getName().equals(varName)) return true;
            return vd.getInitializer() != null && readsVariableExpr(vd.getInitializer(), varName, false);
        }
        if (stmt instanceof IfStmt) {
            IfStmt is = (IfStmt) stmt;
            return readsVariableExpr(is.getCondition(), varName, false) ||
                   usesVariable(is.getThenBranch(), varName) ||
                   (is.hasElse() && usesVariable(is.getElseBranch(), varName));
        }
        if (stmt instanceof WhileStmt) {
            WhileStmt ws = (WhileStmt) stmt;
            return readsVariableExpr(ws.getCondition(), varName, false) || usesVariable(ws.getBody(), varName);
        }
        if (stmt instanceof ForStmt) {
            ForStmt fs = (ForStmt) stmt;
            for (Statement init : fs.getInit()) {
                if (usesVariable(init, varName)) return true;
            }
            if (fs.getCondition() != null && readsVariableExpr(fs.getCondition(), varName, false)) return true;
            for (Expression upd : fs.getUpdate()) {
                if (readsVariableExpr(upd, varName, false)) return true;
            }
            return usesVariable(fs.getBody(), varName);
        }
        if (stmt instanceof ReturnStmt) {
            ReturnStmt rs = (ReturnStmt) stmt;
            return rs.getValue() != null && readsVariableExpr(rs.getValue(), varName, false);
        }
        if (stmt instanceof ThrowStmt) {
            return readsVariableExpr(((ThrowStmt) stmt).getException(), varName, false);
        }
        if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tc = (TryCatchStmt) stmt;
            if (usesVariable(tc.getTryBlock(), varName)) return true;
            for (CatchClause cc : tc.getCatches()) {
                if (usesVariable(cc.body(), varName)) return true;
            }
            return tc.getFinallyBlock() != null && usesVariable(tc.getFinallyBlock(), varName);
        }
        return false;
    }

    private boolean isDefaultValue(Expression expr) {
        if (expr == null) return true;
        if (!(expr instanceof LiteralExpr)) return false;
        Object val = ((LiteralExpr) expr).getValue();
        if (val == null) return true;
        if (val instanceof Integer) return ((Integer) val) == 0;
        if (val instanceof Long) return ((Long) val) == 0L;
        if (val instanceof Boolean) return !((Boolean) val);
        if (val instanceof Character) return ((Character) val) == 0;
        if (val instanceof Float) return ((Float) val) == 0.0f;
        if (val instanceof Double) return ((Double) val) == 0.0d;
        return false;
    }

    private int findFirstAssignment(List<Statement> stmts, int start, String varName) {
        for (int i = start; i < stmts.size(); i++) {
            Statement s = stmts.get(i);
            if (getAssignmentTo(s, varName) != null) return i;
            if (containsControlFlow(s)) return -1;
        }
        return -1;
    }

    private boolean containsControlFlow(Statement s) {
        return s instanceof IfStmt || s instanceof WhileStmt ||
               s instanceof ForStmt || s instanceof TryCatchStmt ||
               s instanceof SwitchStmt || s instanceof DoWhileStmt;
    }

    private BinaryExpr getAssignmentTo(Statement stmt, String varName) {
        if (!(stmt instanceof ExprStmt)) return null;
        Expression expr = ((ExprStmt) stmt).getExpression();
        if (!(expr instanceof BinaryExpr)) return null;
        BinaryExpr binary = (BinaryExpr) expr;
        if (binary.getOperator() != BinaryOperator.ASSIGN) return null;
        if (!(binary.getLeft() instanceof VarRefExpr)) return null;
        if (!((VarRefExpr) binary.getLeft()).getName().equals(varName)) return null;
        return binary;
    }

    private boolean isVariableReadBetween(List<Statement> stmts, int start, int end, String varName) {
        for (int i = start; i < end; i++) {
            if (readsVariable(stmts.get(i), varName)) return true;
        }
        return false;
    }

    private boolean readsVariable(Statement stmt, String varName) {
        if (stmt instanceof ExprStmt) {
            return readsVariableExpr(((ExprStmt) stmt).getExpression(), varName, true);
        }
        if (stmt instanceof VarDeclStmt) {
            VarDeclStmt vd = (VarDeclStmt) stmt;
            return vd.getInitializer() != null && readsVariableExpr(vd.getInitializer(), varName, false);
        }
        if (stmt instanceof ReturnStmt) {
            ReturnStmt rs = (ReturnStmt) stmt;
            return rs.getValue() != null && readsVariableExpr(rs.getValue(), varName, false);
        }
        return true; // Conservative for unknown statements
    }

    private boolean readsVariableExpr(Expression expr, String varName, boolean skipAssignLeft) {
        if (expr instanceof VarRefExpr) {
            return ((VarRefExpr) expr).getName().equals(varName);
        }
        if (expr instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) expr;
            boolean checkLeft = !skipAssignLeft || b.getOperator() != BinaryOperator.ASSIGN;
            if (checkLeft && readsVariableExpr(b.getLeft(), varName, false)) return true;
            return readsVariableExpr(b.getRight(), varName, false);
        }
        if (expr instanceof UnaryExpr) {
            return readsVariableExpr(((UnaryExpr) expr).getOperand(), varName, false);
        }
        if (expr instanceof MethodCallExpr) {
            MethodCallExpr mc = (MethodCallExpr) expr;
            if (mc.getReceiver() != null && readsVariableExpr(mc.getReceiver(), varName, false)) return true;
            for (Expression arg : mc.getArguments()) {
                if (readsVariableExpr(arg, varName, false)) return true;
            }
        }
        if (expr instanceof TernaryExpr) {
            TernaryExpr t = (TernaryExpr) expr;
            return readsVariableExpr(t.getCondition(), varName, false) ||
                   readsVariableExpr(t.getThenExpr(), varName, false) ||
                   readsVariableExpr(t.getElseExpr(), varName, false);
        }
        if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr aa = (ArrayAccessExpr) expr;
            return readsVariableExpr(aa.getArray(), varName, false) ||
                   readsVariableExpr(aa.getIndex(), varName, false);
        }
        if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr fa = (FieldAccessExpr) expr;
            return fa.getReceiver() != null && readsVariableExpr(fa.getReceiver(), varName, false);
        }
        if (expr instanceof CastExpr) {
            return readsVariableExpr(((CastExpr) expr).getExpression(), varName, false);
        }
        if (expr instanceof NewExpr) {
            NewExpr ne = (NewExpr) expr;
            for (Expression arg : ne.getArguments()) {
                if (readsVariableExpr(arg, varName, false)) return true;
            }
        }
        return false;
    }
}
