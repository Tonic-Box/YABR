package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.ir.ConstantInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Simplifies control flow in AST to reduce nesting depth and improve readability.
 *
 * Transformations:
 * 1. Empty if-block inversion: if(x){} else{body} -> if(!x){body}
 * 2. AND-chain merging: if(a){if(b){body}} -> if(a && b){body}
 * 3. Guard clause conversion: if(x){long} else{return} -> if(!x)return; long
 * 4. If-else to ternary: if(c){x=0}else{x=1} -> x=c?0:1
 * 5. Sequential guard merging: if(a)ret; if(b)ret; -> if(a||b)ret;
 * 6. Boolean flag inlining: bool f=x; if(!f)... -> if(!x)...
 * 7. Nested negated guard flattening: if(!a){if(!b){body}} ret; -> if(a||b){ret} body
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

        // Simplify expressions (ternary with equal branches, etc.)
        changed |= simplifyExpressions(stmts);

        // NEW: Inline single-use boolean variables (Phase 3)
        // This must run before guard merging so conditions are inlined
        changed |= inlineSingleUseBooleans(stmts);

        // NEW: Merge sequential guards (Phase 1)
        // Pattern: if(a)ret; if(b)ret; -> if(a||b)ret;
        changed |= mergeSequentialGuards(stmts);

        // NEW: Flatten nested negated guards (Phase 4)
        // Pattern: if(!a){if(!b){body}} ret; -> if(a||b){ret} body
        changed |= flattenNestedNegatedGuards(stmts);

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

    /**
     * Simplifies expressions within statements.
     * - Ternary with equal branches: cond ? x : x -> x
     */
    private boolean simplifyExpressions(List<Statement> stmts) {
        boolean changed = false;
        for (int i = 0; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);
            Statement simplified = simplifyExpressionsInStatement(stmt);
            if (simplified != stmt) {
                stmts.set(i, simplified);
                changed = true;
            }
        }
        return changed;
    }

    private Statement simplifyExpressionsInStatement(Statement stmt) {
        if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (ret.getValue() != null) {
                Expression simplified = simplifyExpression(ret.getValue());
                if (simplified != ret.getValue()) {
                    return new ReturnStmt(simplified);
                }
            }
        } else if (stmt instanceof ExprStmt) {
            ExprStmt exprStmt = (ExprStmt) stmt;
            Expression simplified = simplifyExpression(exprStmt.getExpression());
            if (simplified != exprStmt.getExpression()) {
                return new ExprStmt(simplified);
            }
        } else if (stmt instanceof VarDeclStmt) {
            VarDeclStmt decl = (VarDeclStmt) stmt;
            if (decl.getInitializer() != null) {
                Expression simplified = simplifyExpression(decl.getInitializer());
                if (simplified != decl.getInitializer()) {
                    return new VarDeclStmt(decl.getType(), decl.getName(), simplified);
                }
            }
        }
        return stmt;
    }

    private Expression simplifyExpression(Expression expr) {
        // Try to inline VarRefExpr with constant SSA definitions
        if (expr instanceof VarRefExpr) {
            VarRefExpr varRef = (VarRefExpr) expr;
            Expression inlined = tryInlineConstantVarRef(varRef);
            if (inlined != null) {
                return inlined;
            }
        }

        if (expr instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) expr;
            Expression thenExpr = simplifyExpression(ternary.getThenExpr());
            Expression elseExpr = simplifyExpression(ternary.getElseExpr());

            // If both branches are equal, just return one of them
            if (expressionsEqual(thenExpr, elseExpr)) {
                return thenExpr;
            }

            // If branches changed, create new ternary
            if (thenExpr != ternary.getThenExpr() || elseExpr != ternary.getElseExpr()) {
                return new TernaryExpr(
                    simplifyExpression(ternary.getCondition()),
                    thenExpr,
                    elseExpr,
                    ternary.getType()
                );
            }
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            Expression left = simplifyExpression(binary.getLeft());
            Expression right = simplifyExpression(binary.getRight());
            if (left != binary.getLeft() || right != binary.getRight()) {
                return new BinaryExpr(binary.getOperator(), left, right, binary.getType());
            }
        } else if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            Expression operand = simplifyExpression(unary.getOperand());
            if (operand != unary.getOperand()) {
                return new UnaryExpr(unary.getOperator(), operand, unary.getType());
            }
        } else if (expr instanceof CastExpr) {
            CastExpr cast = (CastExpr) expr;
            Expression inner = simplifyExpression(cast.getExpression());
            if (inner != cast.getExpression()) {
                return new CastExpr(cast.getTargetType(), inner);
            }
        }
        return expr;
    }

    /**
     * Tries to inline a VarRefExpr that has an SSA value with a constant definition.
     * This handles cases where phi constant propagation left a reference to a variable
     * that was never declared because the ternary was simplified away.
     */
    private Expression tryInlineConstantVarRef(VarRefExpr varRef) {
        SSAValue ssaValue = varRef.getSsaValue();
        if (ssaValue == null) {
            return null;
        }

        IRInstruction def = ssaValue.getDefinition();
        if (def instanceof ConstantInstruction) {
            ConstantInstruction constInstr = (ConstantInstruction) def;
            Constant constant = constInstr.getConstant();
            return constantToLiteral(constant, varRef.getType());
        }

        return null;
    }

    /**
     * Converts an SSA constant to a literal expression.
     */
    private Expression constantToLiteral(Constant constant, SourceType type) {
        if (constant instanceof IntConstant) {
            return LiteralExpr.ofInt(((IntConstant) constant).getValue());
        } else if (constant instanceof LongConstant) {
            return LiteralExpr.ofLong(((LongConstant) constant).getValue());
        } else if (constant instanceof FloatConstant) {
            return LiteralExpr.ofFloat(((FloatConstant) constant).getValue());
        } else if (constant instanceof DoubleConstant) {
            return LiteralExpr.ofDouble(((DoubleConstant) constant).getValue());
        } else if (constant instanceof StringConstant) {
            return LiteralExpr.ofString(((StringConstant) constant).getValue());
        } else if (constant instanceof NullConstant) {
            return LiteralExpr.ofNull();
        }
        return null;
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

    // ========== Sequential Guard Merging (Phase 1) ==========

    /**
     * Merges sequential guard clauses with identical early-exit bodies.
     * Pattern: if(a) { return X; } if(b) { return X; } -> if(a || b) { return X; }
     */
    private boolean mergeSequentialGuards(List<Statement> stmts) {
        boolean changed = false;

        for (int i = 0; i < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof IfStmt)) continue;
            IfStmt firstIf = (IfStmt) stmts.get(i);

            // Must be guard clause (no else, body is early exit)
            if (firstIf.hasElse()) continue;
            if (!isEarlyExit(firstIf.getThenBranch())) continue;

            // Collect consecutive guards with identical early-exit bodies
            List<IfStmt> guards = new ArrayList<>();
            guards.add(firstIf);

            int j = i + 1;
            while (j < stmts.size() && stmts.get(j) instanceof IfStmt) {
                IfStmt nextIf = (IfStmt) stmts.get(j);
                if (nextIf.hasElse()) break;
                if (!isEarlyExit(nextIf.getThenBranch())) break;
                if (!earlyExitsEqual(firstIf.getThenBranch(), nextIf.getThenBranch())) break;
                guards.add(nextIf);
                j++;
            }

            // Need at least 2 guards to merge
            if (guards.size() < 2) continue;

            // Build combined OR condition
            Expression combined = guards.get(0).getCondition();
            for (int k = 1; k < guards.size(); k++) {
                combined = new BinaryExpr(
                    BinaryOperator.OR,
                    combined,
                    guards.get(k).getCondition(),
                    PrimitiveSourceType.BOOLEAN
                );
            }

            // Replace with merged if statement
            IfStmt mergedIf = new IfStmt(combined, firstIf.getThenBranch());
            stmts.set(i, mergedIf);

            // Remove the other guards
            for (int k = 1; k < guards.size(); k++) {
                stmts.remove(i + 1);
            }

            changed = true;
        }

        return changed;
    }

    /**
     * Compares two early-exit statements for semantic equality.
     */
    private boolean earlyExitsEqual(Statement a, Statement b) {
        Statement ua = unwrapSingleStatement(a);
        Statement ub = unwrapSingleStatement(b);

        if (ua.getClass() != ub.getClass()) return false;

        if (ua instanceof ReturnStmt) {
            ReturnStmt ra = (ReturnStmt) ua;
            ReturnStmt rb = (ReturnStmt) ub;
            return expressionsEqual(ra.getValue(), rb.getValue());
        }

        if (ua instanceof ThrowStmt) {
            ThrowStmt ta = (ThrowStmt) ua;
            ThrowStmt tb = (ThrowStmt) ub;
            return expressionsEqual(ta.getException(), tb.getException());
        }

        // Fall back to string comparison for other types
        return ua.toString().equals(ub.toString());
    }

    /**
     * Compares two expressions for semantic equality.
     */
    private boolean expressionsEqual(Expression a, Expression b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;

        if (a instanceof LiteralExpr) {
            Object va = ((LiteralExpr) a).getValue();
            Object vb = ((LiteralExpr) b).getValue();
            return Objects.equals(va, vb);
        }

        if (a instanceof VarRefExpr) {
            return ((VarRefExpr) a).getName().equals(((VarRefExpr) b).getName());
        }

        if (a instanceof MethodCallExpr) {
            MethodCallExpr ma = (MethodCallExpr) a;
            MethodCallExpr mb = (MethodCallExpr) b;
            if (!ma.getMethodName().equals(mb.getMethodName())) return false;
            if (!ma.getOwnerClass().equals(mb.getOwnerClass())) return false;
            if (ma.getArguments().size() != mb.getArguments().size()) return false;
            for (int i = 0; i < ma.getArguments().size(); i++) {
                if (!expressionsEqual(ma.getArguments().get(i), mb.getArguments().get(i))) {
                    return false;
                }
            }
            return expressionsEqual(ma.getReceiver(), mb.getReceiver());
        }

        if (a instanceof BinaryExpr) {
            BinaryExpr ba = (BinaryExpr) a;
            BinaryExpr bb = (BinaryExpr) b;
            return ba.getOperator() == bb.getOperator() &&
                   expressionsEqual(ba.getLeft(), bb.getLeft()) &&
                   expressionsEqual(ba.getRight(), bb.getRight());
        }

        if (a instanceof UnaryExpr) {
            UnaryExpr ua = (UnaryExpr) a;
            UnaryExpr ub = (UnaryExpr) b;
            return ua.getOperator() == ub.getOperator() &&
                   expressionsEqual(ua.getOperand(), ub.getOperand());
        }

        // Fall back to string comparison
        return a.toString().equals(b.toString());
    }

    // ========== Boolean Flag Inlining (Phase 3) ==========

    /**
     * Inlines single-use boolean variables into their condition usage.
     * Pattern: boolean flag = expr; if (!flag) { ... } -> if (!expr) { ... }
     */
    private boolean inlineSingleUseBooleans(List<Statement> stmts) {
        boolean changed = false;

        for (int i = 0; i < stmts.size() - 1; i++) {
            Statement stmt = stmts.get(i);

            // Look for: Type varName = expr;
            if (!(stmt instanceof VarDeclStmt)) continue;
            VarDeclStmt decl = (VarDeclStmt) stmt;

            // Must be boolean type with initializer
            if (!isBooleanType(decl.getType())) continue;
            if (decl.getInitializer() == null) continue;

            String varName = decl.getName();
            Expression initExpr = decl.getInitializer();

            // Check next statement uses this variable exactly once in condition
            Statement next = stmts.get(i + 1);
            if (next instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) next;
                Expression cond = ifStmt.getCondition();

                int useCount = countVariableUses(cond, varName);
                if (useCount == 1 && !isVariableUsedAfter(stmts, i + 1, varName, ifStmt)) {
                    // Inline: replace varRef with initExpr
                    Expression newCond = substituteVariable(cond, varName, initExpr);
                    ifStmt.setCondition(newCond);
                    stmts.remove(i);  // Remove the declaration
                    changed = true;
                    i--;  // Reprocess this index
                }
            } else if (next instanceof WhileStmt) {
                WhileStmt whileStmt = (WhileStmt) next;
                Expression cond = whileStmt.getCondition();

                int useCount = countVariableUses(cond, varName);
                if (useCount == 1 && !isVariableUsedAfter(stmts, i + 1, varName, whileStmt)) {
                    Expression newCond = substituteVariable(cond, varName, initExpr);
                    whileStmt.setCondition(newCond);
                    stmts.remove(i);
                    changed = true;
                    i--;
                }
            }
        }

        return changed;
    }

    private boolean isBooleanType(SourceType type) {
        if (type == null) return false;
        String typeName = type.toJavaSource();
        return "boolean".equals(typeName) || "Boolean".equals(typeName) ||
               "java.lang.Boolean".equals(typeName);
    }

    private int countVariableUses(Expression expr, String varName) {
        int[] count = {0};
        visitExpressions(expr, e -> {
            if (e instanceof VarRefExpr && ((VarRefExpr) e).getName().equals(varName)) {
                count[0]++;
            }
        });
        return count[0];
    }

    private void visitExpressions(Expression expr, Consumer<Expression> visitor) {
        if (expr == null) return;
        visitor.accept(expr);

        if (expr instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) expr;
            visitExpressions(b.getLeft(), visitor);
            visitExpressions(b.getRight(), visitor);
        } else if (expr instanceof UnaryExpr) {
            visitExpressions(((UnaryExpr) expr).getOperand(), visitor);
        } else if (expr instanceof MethodCallExpr) {
            MethodCallExpr mc = (MethodCallExpr) expr;
            visitExpressions(mc.getReceiver(), visitor);
            for (Expression arg : mc.getArguments()) {
                visitExpressions(arg, visitor);
            }
        } else if (expr instanceof TernaryExpr) {
            TernaryExpr t = (TernaryExpr) expr;
            visitExpressions(t.getCondition(), visitor);
            visitExpressions(t.getThenExpr(), visitor);
            visitExpressions(t.getElseExpr(), visitor);
        } else if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr aa = (ArrayAccessExpr) expr;
            visitExpressions(aa.getArray(), visitor);
            visitExpressions(aa.getIndex(), visitor);
        } else if (expr instanceof FieldAccessExpr) {
            visitExpressions(((FieldAccessExpr) expr).getReceiver(), visitor);
        } else if (expr instanceof CastExpr) {
            visitExpressions(((CastExpr) expr).getExpression(), visitor);
        } else if (expr instanceof NewExpr) {
            for (Expression arg : ((NewExpr) expr).getArguments()) {
                visitExpressions(arg, visitor);
            }
        }
    }

    private boolean isVariableUsedAfter(List<Statement> stmts, int startIdx, String varName, Statement excluding) {
        for (int i = startIdx; i < stmts.size(); i++) {
            Statement s = stmts.get(i);
            if (s == excluding) continue;
            if (usesVariable(s, varName)) return true;
        }
        return false;
    }

    private Expression substituteVariable(Expression expr, String varName, Expression replacement) {
        if (expr == null) return null;

        if (expr instanceof VarRefExpr) {
            VarRefExpr ref = (VarRefExpr) expr;
            if (ref.getName().equals(varName)) {
                return replacement;
            }
            return expr;
        }

        if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            Expression newOperand = substituteVariable(unary.getOperand(), varName, replacement);
            if (newOperand != unary.getOperand()) {
                return new UnaryExpr(unary.getOperator(), newOperand, unary.getType());
            }
            return expr;
        }

        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            Expression newLeft = substituteVariable(binary.getLeft(), varName, replacement);
            Expression newRight = substituteVariable(binary.getRight(), varName, replacement);
            if (newLeft != binary.getLeft() || newRight != binary.getRight()) {
                return new BinaryExpr(binary.getOperator(), newLeft, newRight, binary.getType());
            }
            return expr;
        }

        if (expr instanceof MethodCallExpr) {
            MethodCallExpr mc = (MethodCallExpr) expr;
            Expression newReceiver = substituteVariable(mc.getReceiver(), varName, replacement);
            List<Expression> newArgs = new ArrayList<>();
            boolean argsChanged = false;
            for (Expression arg : mc.getArguments()) {
                Expression newArg = substituteVariable(arg, varName, replacement);
                newArgs.add(newArg);
                if (newArg != arg) argsChanged = true;
            }
            if (newReceiver != mc.getReceiver() || argsChanged) {
                return new MethodCallExpr(newReceiver, mc.getMethodName(), mc.getOwnerClass(),
                    newArgs, mc.isStatic(), mc.getType());
            }
            return expr;
        }

        // For other expression types, return as-is
        return expr;
    }

    // ========== Nested Negated Guard Flattening (Phase 4) ==========

    /**
     * Flattens nested negated guard patterns into OR conditions.
     * Pattern: if(!a) { if(!b) { if(!c) { body } } } earlyExit; -> if(a || b || c) { earlyExit; } body
     */
    private boolean flattenNestedNegatedGuards(List<Statement> stmts) {
        boolean changed = false;

        for (int i = 0; i < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof IfStmt)) continue;
            IfStmt outerIf = (IfStmt) stmts.get(i);

            // Must not have else
            if (outerIf.hasElse()) continue;

            // Collect chain of nested negated-condition ifs
            List<Expression> positiveConditions = new ArrayList<>();
            Statement innermostBody = collectNestedNegatedConditions(outerIf, positiveConditions);

            // Need at least 2 negated conditions to transform
            if (positiveConditions.size() < 2) continue;

            // Check if there's an early exit after the chain
            if (i + 1 >= stmts.size()) continue;
            Statement afterIf = stmts.get(i + 1);
            if (!isEarlyExit(afterIf)) continue;

            // Build OR condition from all the positive conditions
            Expression orCondition = positiveConditions.get(0);
            for (int k = 1; k < positiveConditions.size(); k++) {
                orCondition = new BinaryExpr(
                    BinaryOperator.OR,
                    orCondition,
                    positiveConditions.get(k),
                    PrimitiveSourceType.BOOLEAN
                );
            }

            // Create new if with OR condition containing the early exit
            IfStmt newIf = new IfStmt(orCondition, afterIf);

            // Replace the nested if with the new OR'd if
            stmts.set(i, newIf);

            // Replace the early exit with the innermost body
            if (innermostBody != null) {
                // Unwrap if it's a block with statements
                List<Statement> bodyStmts = getStatements(innermostBody);
                stmts.remove(i + 1);  // Remove old early exit
                for (int k = 0; k < bodyStmts.size(); k++) {
                    stmts.add(i + 1 + k, bodyStmts.get(k));
                }
            } else {
                stmts.remove(i + 1);  // Just remove the early exit
            }

            changed = true;
        }

        return changed;
    }

    /**
     * Collects positive conditions from nested negated if statements.
     * Returns the innermost body statement.
     */
    private Statement collectNestedNegatedConditions(IfStmt ifStmt, List<Expression> positiveConditions) {
        Expression cond = ifStmt.getCondition();

        // Check if condition is negated
        Expression positiveCond = getPositiveCondition(cond);
        if (positiveCond == null) {
            // Not a negation - stop here
            return null;
        }

        positiveConditions.add(positiveCond);

        // Check the then branch
        Statement inner = unwrapSingleStatement(ifStmt.getThenBranch());

        if (inner instanceof IfStmt) {
            IfStmt innerIf = (IfStmt) inner;
            if (!innerIf.hasElse()) {
                // Recursively collect from nested if
                return collectNestedNegatedConditions(innerIf, positiveConditions);
            }
        }

        // This is the innermost body
        return ifStmt.getThenBranch();
    }

    /**
     * Extracts the positive form of a negated condition.
     * Returns null if the condition is not a negation.
     */
    private Expression getPositiveCondition(Expression expr) {
        // Direct NOT: !x -> x
        if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            if (unary.getOperator() == UnaryOperator.NOT) {
                return unary.getOperand();
            }
        }

        // Negated comparison: x == false -> x, x != true -> x
        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            if (binary.getOperator() == BinaryOperator.EQ) {
                // x == false -> x (positive is x being true)
                if (isFalseLiteral(binary.getRight())) {
                    return binary.getLeft();
                }
                if (isFalseLiteral(binary.getLeft())) {
                    return binary.getRight();
                }
            }
            if (binary.getOperator() == BinaryOperator.NE) {
                // x != true -> x (positive is x being true)
                if (isTrueLiteral(binary.getRight())) {
                    return binary.getLeft();
                }
                if (isTrueLiteral(binary.getLeft())) {
                    return binary.getRight();
                }
            }
        }

        return null;
    }

    private boolean isFalseLiteral(Expression expr) {
        if (expr instanceof LiteralExpr) {
            Object val = ((LiteralExpr) expr).getValue();
            return Boolean.FALSE.equals(val) || Integer.valueOf(0).equals(val);
        }
        return false;
    }

    private boolean isTrueLiteral(Expression expr) {
        if (expr instanceof LiteralExpr) {
            Object val = ((LiteralExpr) expr).getValue();
            return Boolean.TRUE.equals(val) || Integer.valueOf(1).equals(val);
        }
        return false;
    }
}
