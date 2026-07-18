package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.Locations;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;
import com.tonic.analysis.ssa.ir.ConstantInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplifies control flow in AST to reduce nesting depth and improve readability.
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

    /** The method body of the in-progress top-level {@code transform} call; whole-method passes read it. */
    private BlockStmt methodRoot;

    @Override
    public String getName() {
        return "ControlFlowSimplifier";
    }

    @Override
    public boolean transform(BlockStmt block) {
        boolean top = methodRoot == null;
        if (top) {
            methodRoot = block;
        }
        try {
            return transformBlock(block);
        } finally {
            if (top) {
                methodRoot = null;
            }
        }
    }

    private boolean transformBlock(BlockStmt block) {
        boolean changed = false;
        List<Statement> stmts = block.getStatements();

        for (Statement stmt : stmts) {
            changed |= recurseInto(stmt);
        }

        boolean passChanged;
        do {
            passChanged = removeRedundantStatements(stmts);
            passChanged |= moveDeclarationsToFirstUse(stmts);
            changed |= passChanged;
        } while (passChanged);

        changed |= simplifyExpressions(stmts);

        changed |= simplifyConditions(stmts);

        changed |= inlineSingleUseBooleans(stmts);

        changed |= collapseConditionalMaterialization(stmts);
        changed |= collapseReturnedBooleanPhi(stmts);

        changed |= mergeSequentialGuards(stmts);

        changed |= mergeComplementaryGuards(stmts);

        changed |= flattenNestedNegatedGuards(stmts);

        changed |= collapseGuardWithSharedEarlyExit(stmts);

        for (int i = 0; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);

            if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;

                Statement replacement = tryConvertIfElseToAssignment(ifStmt);
                if (replacement != null) {
                    Locations.copy(ifStmt, replacement);
                    stmts.set(i, replacement);
                    changed = true;
                    continue;
                }

                changed |= transformIf(ifStmt, stmts, i);
            }
        }

        return changed;
    }

    /**
     * Reports whether an expression may legally stand alone as a Java statement (a method/
     * dynamic invocation, object/array creation, assignment, or pre/post increment-decrement).
     * Comparisons and other pure expressions are not statement expressions.
     */
    private static boolean isStatementExpression(Expression e) {
        if (e instanceof MethodCallExpr || e instanceof NewExpr
                || e instanceof NewArrayExpr || e instanceof InvokeDynamicExpr) {
            return true;
        }
        if (e instanceof BinaryExpr) {
            return ((BinaryExpr) e).getOperator().isAssignment();
        }
        if (e instanceof UnaryExpr) {
            UnaryOperator op = ((UnaryExpr) e).getOperator();
            return op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC
                    || op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC;
        }
        return false;
    }

    private boolean transformIf(IfStmt ifStmt, List<Statement> parentList, int index) {
        boolean changed = false;

        // An if with an empty then-branch and no else is dead. If the condition is side-effect
        // free, drop it entirely. If it is itself a valid statement expression (a call/new/
        // assignment/increment), lower it to that statement. Otherwise (e.g. an opaque-predicate
        // comparison wrapping a call) keep the empty if: a bare comparison is not a legal Java
        // statement, and extracting the call would break short-circuit semantics.
        if (isEmptyBlock(ifStmt.getThenBranch()) && !ifStmt.hasElse()) {
            Expression cond = ifStmt.getCondition();
            boolean sideEffecting = Boolean.TRUE.equals(
                    cond.accept(SideEffectDetector.INSTANCE));
            if (!sideEffecting) {
                parentList.remove(index);
                return true;
            }
            if (isStatementExpression(cond)) {
                ExprStmt condStmt = new ExprStmt(cond);
                Locations.copy(ifStmt, condStmt);
                parentList.set(index, condStmt);
                return true;
            }
            return false;
        }

        if (isEmptyBlock(ifStmt.getThenBranch()) && ifStmt.hasElse()) {
            invertCondition(ifStmt);
            ifStmt.setThenBranch(ifStmt.getElseBranch());
            ifStmt.setElseBranch(null);
            changed = true;
        }

        // Drop an empty else (`if (c) { body } else {}`): semantically identical to no else, and the emitter
        // omits it anyway - but its presence blocks the AND-merge below (whose `!hasElse()` guard would fail).
        // The recompiled short-circuit shape gives each `&&` guard such an empty else, so without this a javac
        // `a && b && c` chain oscillates between nested and merged forms on round trip.
        if (ifStmt.hasElse() && isEmptyBlock(ifStmt.getElseBranch())) {
            ifStmt.setElseBranch(null);
            changed = true;
        }

        if (!ifStmt.hasElse()) {
            Statement inner = unwrapSingleStatement(ifStmt.getThenBranch());
            if (inner instanceof IfStmt) {
                IfStmt innerIf = (IfStmt) inner;
                if (!innerIf.hasElse()) {
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

        if (ifStmt.hasElse() && isEarlyExit(ifStmt.getElseBranch())) {
            Statement earlyExit = unwrapSingleStatement(ifStmt.getElseBranch());
            Statement thenBody = ifStmt.getThenBranch();

            IfStmt guard = new IfStmt(negate(ifStmt.getCondition()), earlyExit);
            Locations.copy(ifStmt, guard);

            parentList.set(index, guard);

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
        Statement simplified = simplifyExpressionsInStatement0(stmt);
        if (simplified != stmt) {
            Locations.copy(stmt, simplified);
        }
        return simplified;
    }

    /**
     * Folds boolean identities ({@code x && true}/{@code x || false}) left behind by compound-condition
     * reconstruction out of {@code if}/loop conditions. Only these identity folds are applied - NOT the constant
     * variable-reference inlining that {@code simplifyExpression} also does, which would rewrite {@code if (x >=
     * y)} to {@code if (5 >= 10)} for constant-valued locals, leaving {@code x}/{@code y} dead and drifting on
     * round trip. Conditions are mutated in place (preserving the statement's label and location).
     */
    private boolean simplifyConditions(List<Statement> stmts) {
        boolean changed = false;
        for (Statement s : stmts) {
            Expression cond = conditionOf(s);
            if (cond == null) {
                continue;
            }
            Expression simplified = foldConditionIdentities(cond);
            if (simplified != cond) {
                setConditionOf(s, simplified);
                changed = true;
            }
        }
        return changed;
    }

    /** Recursively folds {@code x && true}/{@code x || false} identities, leaving all other operands untouched. */
    private Expression foldConditionIdentities(Expression e) {
        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            Expression left = foldConditionIdentities(b.getLeft());
            Expression right = foldConditionIdentities(b.getRight());
            Expression identity = foldBooleanIdentity(b.getOperator(), left, right);
            if (identity != null) {
                return identity;
            }
            if (left != b.getLeft() || right != b.getRight()) {
                return new BinaryExpr(b.getOperator(), left, right, b.getType());
            }
        } else if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            Expression operand = foldConditionIdentities(u.getOperand());
            if (operand != u.getOperand()) {
                return new UnaryExpr(u.getOperator(), operand, u.getType());
            }
        }
        return e;
    }

    private Expression conditionOf(Statement s) {
        if (s instanceof IfStmt) {
            return ((IfStmt) s).getCondition();
        }
        if (s instanceof WhileStmt) {
            return ((WhileStmt) s).getCondition();
        }
        if (s instanceof ForStmt) {
            return ((ForStmt) s).getCondition();
        }
        if (s instanceof DoWhileStmt) {
            return ((DoWhileStmt) s).getCondition();
        }
        return null;
    }

    private void setConditionOf(Statement s, Expression cond) {
        if (s instanceof IfStmt) {
            ((IfStmt) s).setCondition(cond);
        } else if (s instanceof WhileStmt) {
            ((WhileStmt) s).setCondition(cond);
        } else if (s instanceof ForStmt) {
            ((ForStmt) s).setCondition(cond);
        } else if (s instanceof DoWhileStmt) {
            ((DoWhileStmt) s).setCondition(cond);
        }
    }

    private Statement simplifyExpressionsInStatement0(Statement stmt) {
        if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (ret.getValue() != null) {
                Expression simplified = simplifyExpression(ret.getValue());
                // `cond ? 1 : 0` returned from a boolean method is the boolean `cond` (the JVM's int form of
                // the boolean). Fold it here, after inlining has resolved the ternary arms to their 1/0
                // literals, so a boolean-returning comparison round-trips stably instead of drifting to a
                // ternary. (The recovery's own coercion runs before the arms resolve, so it cannot see this.)
                if (ret.getMethodReturnType() == PrimitiveSourceType.BOOLEAN) {
                    Expression bool = foldBooleanIntTernary(simplified);
                    if (bool != null) {
                        simplified = bool;
                    }
                }
                if (simplified != ret.getValue()) {
                    ReturnStmt newRet = new ReturnStmt(simplified);
                    newRet.setMethodReturnType(ret.getMethodReturnType());
                    return newRet;
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

    /**
     * Folds {@code cond ? 1 : 0} to {@code cond} - the boolean whose int materialization the ternary is.
     * Returns null when {@code e} is not that shape.
     */
    private Expression foldBooleanIntTernary(Expression e) {
        if (!(e instanceof TernaryExpr)) {
            return null;
        }
        TernaryExpr t = (TernaryExpr) e;
        Integer thenV = constInt(t.getThenExpr());
        Integer elseV = constInt(t.getElseExpr());
        if (thenV != null && thenV == 1 && elseV != null && elseV == 0) {
            return t.getCondition();
        }
        return null;
    }

    private Integer constInt(Expression e) {
        if (e instanceof LiteralExpr && ((LiteralExpr) e).getValue() instanceof Integer) {
            return (Integer) ((LiteralExpr) e).getValue();
        }
        return null;
    }

    /**
     * Folds a boolean short-circuit that javac materializes as an int-carrying ternary back into {@code &&}/{@code
     * ||}: {@code c ? 1 : y} is {@code c || y}, {@code c ? y : 0} is {@code c && y} (and the negated {@code 0}/
     * {@code 1} forms), where the non-literal arm {@code y} is itself boolean. Recompiling a {@code ||}/{@code &&}
     * and decompiling it yields this int-ternary shape; folding it makes the round trip a fixed point. Returns
     * null when the ternary is a genuine value select (neither arm is a boolean-materialized {@code 0}/{@code 1}).
     */
    private Expression foldBooleanShortCircuit(Expression cond, Expression thenExpr, Expression elseExpr) {
        Integer t = constInt(thenExpr);
        Integer e = constInt(elseExpr);
        if (t != null && t == 1 && e != null && e == 0) {
            return cond;
        }
        if (t != null && t == 0 && e != null && e == 1) {
            return negate(cond);
        }
        if (t != null && t == 1 && isBooleanExpr(elseExpr)) {
            return new BinaryExpr(BinaryOperator.OR, cond, elseExpr, PrimitiveSourceType.BOOLEAN);
        }
        if (t != null && t == 0 && isBooleanExpr(elseExpr)) {
            return new BinaryExpr(BinaryOperator.AND, negate(cond), elseExpr, PrimitiveSourceType.BOOLEAN);
        }
        if (e != null && e == 0 && isBooleanExpr(thenExpr)) {
            return new BinaryExpr(BinaryOperator.AND, cond, thenExpr, PrimitiveSourceType.BOOLEAN);
        }
        if (e != null && e == 1 && isBooleanExpr(thenExpr)) {
            return new BinaryExpr(BinaryOperator.OR, negate(cond), thenExpr, PrimitiveSourceType.BOOLEAN);
        }
        return null;
    }

    /** A boolean-valued expression: a comparison/logical operator, a negation, or a boolean-typed leaf. */
    private boolean isBooleanExpr(Expression e) {
        if (e instanceof BinaryExpr) {
            BinaryOperator op = ((BinaryExpr) e).getOperator();
            switch (op) {
                case EQ: case NE: case LT: case LE: case GT: case GE: case AND: case OR:
                    return true;
                default:
                    break;
            }
        }
        if (e instanceof UnaryExpr && ((UnaryExpr) e).getOperator() == UnaryOperator.NOT) {
            return true;
        }
        return isBooleanType(e.getType());
    }

    private Expression simplifyExpression(Expression expr) {
        if (expr instanceof VarRefExpr) {
            VarRefExpr varRef = (VarRefExpr) expr;
            Expression inlined = tryInlineConstantVarRef(varRef);
            if (inlined != null) {
                return inlined;
            }
        }

        if (expr instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) expr;
            Expression cond = simplifyExpression(ternary.getCondition());
            Expression thenExpr = simplifyExpression(ternary.getThenExpr());
            Expression elseExpr = simplifyExpression(ternary.getElseExpr());

            if (expressionsEqual(thenExpr, elseExpr)) {
                return thenExpr;
            }

            Expression shortCircuit = foldBooleanShortCircuit(cond, thenExpr, elseExpr);
            if (shortCircuit != null) {
                return shortCircuit;
            }

            if (cond != ternary.getCondition() || thenExpr != ternary.getThenExpr()
                    || elseExpr != ternary.getElseExpr()) {
                return new TernaryExpr(cond, thenExpr, elseExpr, ternary.getType());
            }
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            Expression left = simplifyExpression(binary.getLeft());
            Expression right = simplifyExpression(binary.getRight());
            Expression identity = foldBooleanIdentity(binary.getOperator(), left, right);
            if (identity != null) {
                return identity;
            }
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
            return constantToLiteral(constant);
        }

        return null;
    }

    /**
     * Converts an SSA constant to a literal expression.
     */
    private Expression constantToLiteral(Constant constant) {
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
                changed |= stripRedundantSwitchContinue((BlockStmt) ws.getBody());
            }
        } else if (stmt instanceof ForStmt) {
            ForStmt fs = (ForStmt) stmt;
            if (fs.getBody() instanceof BlockStmt) {
                changed |= transform((BlockStmt) fs.getBody());
                changed |= stripRedundantSwitchContinue((BlockStmt) fs.getBody());
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
        } else if (stmt instanceof SwitchStmt) {
            changed |= simplifySwitchCases((SwitchStmt) stmt);
        }

        return changed;
    }

    /**
     * Removes a redundant unlabeled {@code continue} that is the last statement of the LAST case of a switch
     * which is itself the last statement of a loop body: control reaches the loop's back-edge either way, so
     * the continue is a no-op. javac omits it; the recovery emits it for its own recompiled shape, so it drifts
     * on round trip. The last case has no case after it, so dropping the continue cannot introduce fall-through.
     */
    private boolean stripRedundantSwitchContinue(BlockStmt loopBody) {
        List<Statement> stmts = loopBody.getStatements();
        if (stmts.isEmpty() || !(stmts.get(stmts.size() - 1) instanceof SwitchStmt)) {
            return false;
        }
        List<SwitchCase> cases = ((SwitchStmt) stmts.get(stmts.size() - 1)).getCases();
        if (cases.isEmpty()) {
            return false;
        }
        int last = cases.size() - 1;
        SwitchCase c = cases.get(last);
        List<Statement> body = c.statements();
        if (body == null || body.isEmpty()) {
            return false;
        }
        Statement tail = body.get(body.size() - 1);
        if (!(tail instanceof ContinueStmt) || ((ContinueStmt) tail).hasLabel()) {
            return false;
        }
        List<Statement> trimmed = new java.util.ArrayList<>(body.subList(0, body.size() - 1));
        SwitchCase rebuilt = (c.isDefault()
                ? SwitchCase.defaultCase(trimmed)
                : c.hasExpressionLabels()
                    ? SwitchCase.ofExpressions(c.expressionLabels(), trimmed)
                    : SwitchCase.of(c.labels(), trimmed))
                .withFallsThrough(c.fallsThrough());
        cases.set(last, rebuilt);
        return true;
    }

    private boolean simplifySwitchCases(SwitchStmt sw) {
        boolean changed = false;
        List<SwitchCase> cases = sw.getCases();
        for (int i = 0; i < cases.size(); i++) {
            SwitchCase c = cases.get(i);
            BlockStmt body = new BlockStmt(new java.util.ArrayList<>(c.statements()));
            if (transform(body)) {
                List<Statement> newBody = new java.util.ArrayList<>(body.getStatements());
                SwitchCase rebuilt = (c.isDefault()
                        ? SwitchCase.defaultCase(newBody)
                        : c.hasExpressionLabels()
                            ? SwitchCase.ofExpressions(c.expressionLabels(), newBody)
                            : SwitchCase.of(c.labels(), newBody))
                        .withFallsThrough(c.fallsThrough());
                cases.set(i, rebuilt);
                changed = true;
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
        if (unwrapped instanceof ReturnStmt || unwrapped instanceof ThrowStmt) {
            return true;
        }
        // A multi-statement block is also an early exit when its last statement unconditionally returns or
        // throws: the whole block runs and leaves the method, so the early-exit-guard can hoist it (the full
        // block becomes the guard body). The decompiler recovers a multi-statement guard like
        // `if (expired) { invalidate(); return ...; }` as an if/else with a negated condition; widening this
        // rule recovers the original guard form (and makes the first decompile a round-trip fixed point).
        if (stmt instanceof BlockStmt) {
            BlockStmt block = (BlockStmt) stmt;
            if (!block.getStatements().isEmpty()) {
                Statement last = block.getStatements().get(block.size() - 1);
                return last instanceof ReturnStmt || last instanceof ThrowStmt;
            }
        }
        return false;
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
        if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            if (unary.getOperator() == UnaryOperator.NOT) {
                return unary.getOperand();
            }
        }

        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            BinaryOperator flipped = flipComparison(binary.getOperator());
            if (flipped != null) {
                return new BinaryExpr(flipped, binary.getLeft(), binary.getRight(), binary.getType());
            }
        }

        return new UnaryExpr(UnaryOperator.NOT, expr, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Merges two adjacent guards with complementary conditions into an if/else: {@code if (C) { A } if (!C) { B }}
     * becomes {@code if (C) { A } else { B }}. The reaching-condition structurer emits the then and else arms of a
     * compound-condition branch as separate guards under {@code C} and {@code !C} rather than one if/else, so this
     * recovers the source shape. Sound only when C is side-effect-free (the merge evaluates it once where the two
     * guards evaluated C then !C) and A does not write a variable C reads (so C's truth is unchanged once A has run
     * and the else is correctly not entered).
     */
    private boolean mergeComplementaryGuards(List<Statement> stmts) {
        boolean changed = false;
        for (int i = 0; i + 1 < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof IfStmt) || !(stmts.get(i + 1) instanceof IfStmt)) {
                continue;
            }
            IfStmt first = (IfStmt) stmts.get(i);
            IfStmt second = (IfStmt) stmts.get(i + 1);
            if (first.hasElse() || second.hasElse()) {
                continue;
            }
            Expression c1 = first.getCondition();
            Expression c2 = second.getCondition();
            if (isSideEffecting(c1) || isSideEffecting(c2) || !areComplementary(c1, c2)) {
                continue;
            }
            if (writesAnyReadVar(first.getThenBranch(), c1)) {
                continue;
            }
            IfStmt merged = new IfStmt(c1, first.getThenBranch(), second.getThenBranch());
            Locations.copy(first, merged);
            stmts.set(i, merged);
            stmts.remove(i + 1);
            changed = true;
        }
        return changed;
    }

    /** Whether {@code b} is the logical negation of {@code a} - directly ({@code !a}), by comparison flip, or De Morgan. */
    private boolean areComplementary(Expression a, Expression b) {
        if (isNotOf(a, b) || isNotOf(b, a)) {
            return true;
        }
        if (a instanceof BinaryExpr && b instanceof BinaryExpr) {
            BinaryExpr ba = (BinaryExpr) a;
            BinaryExpr bb = (BinaryExpr) b;
            BinaryOperator flipped = flipComparison(ba.getOperator());
            if (flipped != null && flipped == bb.getOperator()
                    && expressionsEqual(ba.getLeft(), bb.getLeft())
                    && expressionsEqual(ba.getRight(), bb.getRight())) {
                return true;
            }
            if ((ba.getOperator() == BinaryOperator.AND && bb.getOperator() == BinaryOperator.OR)
                    || (ba.getOperator() == BinaryOperator.OR && bb.getOperator() == BinaryOperator.AND)) {
                return areComplementary(ba.getLeft(), bb.getLeft())
                        && areComplementary(ba.getRight(), bb.getRight());
            }
        }
        return false;
    }

    /** Whether {@code notExpr} is {@code !base}. */
    private boolean isNotOf(Expression base, Expression notExpr) {
        return notExpr instanceof UnaryExpr
                && ((UnaryExpr) notExpr).getOperator() == UnaryOperator.NOT
                && expressionsEqual(((UnaryExpr) notExpr).getOperand(), base);
    }

    /** Whether {@code body} writes (assigns or increments) any variable that {@code cond} reads. */
    private boolean writesAnyReadVar(Statement body, Expression cond) {
        WrittenVarCollector collector = new WrittenVarCollector();
        body.accept(collector);
        for (String written : collector.written) {
            if (countVariableUses(cond, written) > 0) {
                return true;
            }
        }
        return false;
    }

    private static final class WrittenVarCollector extends AbstractSourceVisitor<Void> {
        final java.util.Set<String> written = new java.util.HashSet<>();

        @Override
        public Void visitBinary(BinaryExpr expr) {
            if (expr.getOperator().isAssignment() && expr.getLeft() instanceof VarRefExpr) {
                written.add(((VarRefExpr) expr.getLeft()).getName());
            }
            return super.visitBinary(expr);
        }

        @Override
        public Void visitUnary(UnaryExpr expr) {
            UnaryOperator op = expr.getOperator();
            if ((op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC
                    || op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC)
                    && expr.getOperand() instanceof VarRefExpr) {
                written.add(((VarRefExpr) expr.getOperand()).getName());
            }
            return super.visitUnary(expr);
        }
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

            if (stmt instanceof BlockStmt && ((BlockStmt) stmt).isEmpty()) {
                stmts.remove(i);
                changed = true;
                continue;
            }

            if (stmt instanceof ExprStmt) {
                Expression expr = ((ExprStmt) stmt).getExpression();
                if (expr instanceof BinaryExpr) {
                    BinaryExpr binary = (BinaryExpr) expr;
                    if (binary.getOperator() == BinaryOperator.ASSIGN) {
                        if (isSameVariable(binary.getLeft(), binary.getRight())) {
                            stmts.remove(i);
                            changed = true;
                            continue;
                        }

                        if (i > 0) {
                            Statement prev = stmts.get(i - 1);
                            // The prior assignment to this variable is dead only if the current
                            // assignment does not read it; `x = f(); x = x.g()` keeps the first store.
                            if (isDuplicateAssignment(prev, binary.getLeft())
                                    && !references(binary.getRight(), binary.getLeft())) {
                                stmts.remove(i - 1);
                                i--;
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

    /** Whether {@code expr} reads the variable named by {@code varRef}. */
    private static boolean references(Expression expr, Expression varRef) {
        return varRef instanceof VarRefExpr
                && referencesName(expr, ((VarRefExpr) varRef).getName());
    }

    private static boolean referencesName(ASTNode node, String name) {
        if (node instanceof VarRefExpr && ((VarRefExpr) node).getName().equals(name)) {
            return true;
        }
        for (ASTNode child : node.getChildren()) {
            if (referencesName(child, name)) {
                return true;
            }
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

        Statement thenStmt = unwrapSingleStatement(ifStmt.getThenBranch());
        Statement elseStmt = unwrapSingleStatement(ifStmt.getElseBranch());

        BinaryExpr thenAssign = getAssignment(thenStmt);
        BinaryExpr elseAssign = getAssignment(elseStmt);
        if (thenAssign == null || elseAssign == null) return null;

        if (!isSameVariable(thenAssign.getLeft(), elseAssign.getLeft())) return null;

        Integer thenVal = getIntLiteral(thenAssign.getRight());
        Integer elseVal = getIntLiteral(elseAssign.getRight());
        if (thenVal == null || elseVal == null) return null;

        Expression newValue;
        if (thenVal == 0 && elseVal == 1) {
            newValue = new TernaryExpr(
                ifStmt.getCondition(),
                LiteralExpr.ofInt(0),
                LiteralExpr.ofInt(1),
                thenAssign.getType()
            );
        } else if (thenVal == 1 && elseVal == 0) {
            newValue = new TernaryExpr(
                ifStmt.getCondition(),
                LiteralExpr.ofInt(1),
                LiteralExpr.ofInt(0),
                thenAssign.getType()
            );
        } else {
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

    /**
     * Moves variable declarations to their first use point.
     */
    private boolean moveDeclarationsToFirstUse(List<Statement> stmts) {
        boolean changed = false;

        for (int i = 0; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);
            if (!(stmt instanceof VarDeclStmt)) continue;

            VarDeclStmt decl = (VarDeclStmt) stmt;
            if (!isDefaultValue(decl.getInitializer())) continue;

            String varName = decl.getName();
            int firstAssignIdx = findFirstAssignment(stmts, i + 1, varName);

            if (firstAssignIdx == -1) continue;
            if (isVariableReadBetween(stmts, i + 1, firstAssignIdx, varName)) continue;

            BinaryExpr assign = getAssignmentTo(stmts.get(firstAssignIdx), varName);
            if (assign == null) continue;

            if (isDefaultValue(assign.getRight())) {
                stmts.remove(firstAssignIdx);
                changed = true;
                i--;
                continue;
            }

            // The reassignment's right-hand side may read the variable itself (e.g. an accumulator
            // `result = f(x, result)` seeded by the default init). Folding the default declaration into
            // it would make the initializer read the variable before it is assigned, which is not
            // definitely assigned; keep the separate default init in that case.
            if (readsVariableExpr(assign.getRight(), varName, false)) continue;

            VarDeclStmt merged = new VarDeclStmt(decl.getType(), varName, assign.getRight());
            Locations.copy(stmts.get(firstAssignIdx), merged);
            stmts.remove(i);
            stmts.set(firstAssignIdx - 1, merged);
            changed = true;
            i--;
        }

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

            BlockStmt targetBlock = findExclusiveUseBlock(stmts, i + 1, varName);
            if (targetBlock == null) continue;

            stmts.remove(i);
            targetBlock.getStatements().add(0, decl);

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

            if (s instanceof ExprStmt || s instanceof VarDeclStmt || s instanceof ReturnStmt) {
                if (readsVariable(s, varName)) return null;
                continue;
            }

            if (s instanceof TryCatchStmt) {
                TryCatchStmt tc = (TryCatchStmt) s;
                boolean usedInTry = usesVariable(tc.getTryBlock(), varName);
                boolean usedInCatch = false;
                for (CatchClause cc : tc.getCatches()) {
                    if (usesVariable(cc.body(), varName)) usedInCatch = true;
                }
                boolean usedInFinally = tc.getFinallyBlock() != null && usesVariable(tc.getFinallyBlock(), varName);

                if (usedInTry && !usedInCatch && !usedInFinally) {
                    if (candidate != null) return null;
                    if (tc.getTryBlock() instanceof BlockStmt) {
                        candidate = (BlockStmt) tc.getTryBlock();
                    }
                } else if (usedInCatch || usedInFinally) {
                    return null;
                }
                continue;
            }

            if (usesVariable(s, varName)) return null;
        }

        return candidate;
    }

    /**
     * Checks if a statement uses (reads or declares) the given variable.
     */
    private boolean usesVariable(Statement stmt, String varName) {
        if (stmt == null) return false;
        AtomicBoolean found = new AtomicBoolean(false);
        stmt.accept(new AbstractSourceVisitor<Void>() {
            @Override
            public Void visitVarRef(VarRefExpr expr) {
                if (expr.getName().equals(varName)) {
                    found.set(true);
                }
                return super.visitVarRef(expr);
            }

            @Override
            public Void visitVarDecl(VarDeclStmt stmt) {
                if (stmt.getName().equals(varName)) {
                    found.set(true);
                }
                return super.visitVarDecl(stmt);
            }
        });
        return found.get();
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

    /**
     * Checks if an expression reads the given variable.
     * Uses visitor pattern with special handling for assignment LHS.
     */
    private boolean readsVariableExpr(Expression expr, String varName, boolean skipAssignLeft) {
        if (expr == null) return false;
        AtomicBoolean found = new AtomicBoolean(false);
        expr.accept(new AbstractSourceVisitor<Void>() {
            @Override
            public Void visitVarRef(VarRefExpr e) {
                if (e.getName().equals(varName)) {
                    found.set(true);
                }
                return null;
            }

            @Override
            public Void visitBinary(BinaryExpr e) {
                if (skipAssignLeft && e.getOperator() == BinaryOperator.ASSIGN) {
                    e.getRight().accept(this);
                    return null;
                }
                return super.visitBinary(e);
            }
        });
        return found.get();
    }

    /**
     * Merges sequential guard clauses with identical early-exit bodies.
     * Pattern: if(a) { return X; } if(b) { return X; } -> if(a || b) { return X; }
     */
    private boolean mergeSequentialGuards(List<Statement> stmts) {
        boolean changed = false;

        for (int i = 0; i < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof IfStmt)) continue;
            IfStmt firstIf = (IfStmt) stmts.get(i);

            if (firstIf.hasElse()) continue;
            if (!isEarlyExit(firstIf.getThenBranch())) continue;

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

            if (guards.size() < 2) continue;

            Expression combined = guards.get(0).getCondition();
            for (int k = 1; k < guards.size(); k++) {
                combined = new BinaryExpr(
                    BinaryOperator.OR,
                    combined,
                    guards.get(k).getCondition(),
                    PrimitiveSourceType.BOOLEAN
                );
            }

            IfStmt mergedIf = new IfStmt(combined, firstIf.getThenBranch());
            Locations.copy(firstIf, mergedIf);
            stmts.set(i, mergedIf);

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

        // Multi-statement / nested bodies must be compared DEEPLY. The previous toString() fallback was
        // unsound: BlockStmt.toString() reports only the statement count ("{ 2 statements }") and
        // IfStmt.toString() omits its body, so two guards with different bodies but the same shape compared
        // equal - merging `if(x==0){A} if(y==0){B}` into `if(x==0||y==0){A}` and silently dropping B.
        if (ua instanceof BlockStmt) {
            // Normalize a trailing single-use return temp first: an asymmetric `||` lowering can emit
            // `temp = expr; return temp` on one merge path and `return expr` on the other; both mean the same
            // exit, so collapse them before comparing (else equal guards fail to merge and the round trip drifts).
            List<Statement> sa = normalizeReturnTemp(((BlockStmt) ua).getStatements());
            List<Statement> sb = normalizeReturnTemp(((BlockStmt) ub).getStatements());
            if (sa.size() != sb.size()) return false;
            for (int k = 0; k < sa.size(); k++) {
                if (!earlyExitsEqual(sa.get(k), sb.get(k))) return false;
            }
            return true;
        }

        if (ua instanceof IfStmt) {
            IfStmt ia = (IfStmt) ua;
            IfStmt ib = (IfStmt) ub;
            if (!expressionsEqual(ia.getCondition(), ib.getCondition())) return false;
            if (!earlyExitsEqual(ia.getThenBranch(), ib.getThenBranch())) return false;
            if (ia.hasElse() != ib.hasElse()) return false;
            return !ia.hasElse() || earlyExitsEqual(ia.getElseBranch(), ib.getElseBranch());
        }

        if (ua instanceof ExprStmt) {
            return expressionsEqual(((ExprStmt) ua).getExpression(), ((ExprStmt) ub).getExpression());
        }

        // For other statement kinds, toString() is content-bearing (only BlockStmt and IfStmt, handled above,
        // are lossy), so it is a sound equality here.
        return ua.toString().equals(ub.toString());
    }

    /**
     * If {@code stmts} ends in {@code temp = expr; return temp} where {@code temp} is a pure return temp (not
     * referenced earlier in the list), returns the list with that pair replaced by {@code return expr}; else
     * returns {@code stmts} unchanged. Used only to normalize guard bodies for equality comparison.
     */
    private List<Statement> normalizeReturnTemp(List<Statement> stmts) {
        int n = stmts.size();
        if (n < 2) return stmts;
        Statement last = stmts.get(n - 1);
        Statement prev = stmts.get(n - 2);
        if (!(last instanceof ReturnStmt) || !(prev instanceof ExprStmt)) return stmts;
        Expression retVal = ((ReturnStmt) last).getValue();
        if (!(retVal instanceof VarRefExpr)) return stmts;
        String var = ((VarRefExpr) retVal).getName();
        Expression pe = ((ExprStmt) prev).getExpression();
        if (!(pe instanceof BinaryExpr)) return stmts;
        BinaryExpr assign = (BinaryExpr) pe;
        if (assign.getOperator() != BinaryOperator.ASSIGN
                || !(assign.getLeft() instanceof VarRefExpr)
                || !((VarRefExpr) assign.getLeft()).getName().equals(var)) {
            return stmts;
        }
        for (int i = 0; i < n - 2; i++) {
            if (referencesVar(stmts.get(i), var)) return stmts;
        }
        List<Statement> out = new ArrayList<>(stmts.subList(0, n - 2));
        out.add(new ReturnStmt(assign.getRight()));
        return out;
    }

    private boolean referencesVar(Statement stmt, String var) {
        AtomicBoolean found = new AtomicBoolean(false);
        stmt.accept(new AbstractSourceVisitor<Void>() {
            @Override
            public Void visitVarRef(VarRefExpr e) {
                if (e.getName().equals(var)) {
                    found.set(true);
                }
                return null;
            }
        });
        return found.get();
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

        return a.toString().equals(b.toString());
    }

    /**
     * Inlines single-use boolean variables into their condition usage.
     * Pattern: boolean flag = expr; if (!flag) { ... } -> if (!expr) { ... }
     */
    private boolean inlineSingleUseBooleans(List<Statement> stmts) {
        boolean changed = false;

        for (int i = 0; i < stmts.size() - 1; i++) {
            Statement stmt = stmts.get(i);

            if (!(stmt instanceof VarDeclStmt)) continue;
            VarDeclStmt decl = (VarDeclStmt) stmt;

            if (!isBooleanType(decl.getType())) continue;
            if (decl.getInitializer() == null) continue;

            String varName = decl.getName();
            Expression initExpr = decl.getInitializer();

            Statement next = stmts.get(i + 1);
            if (next instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) next;
                Expression cond = ifStmt.getCondition();

                int useCount = countVariableUses(cond, varName);
                // The variable must be used ONLY in the condition: not read or reassigned inside the branches
                // (a reassigned phi variable like `result = false` in the body must keep its declaration), and
                // not used after. usesVariable counts an assignment's target too, so a body reassignment blocks
                // the inline.
                if (useCount == 1 && !usesVariable(ifStmt.getThenBranch(), varName)
                        && !(ifStmt.hasElse() && usesVariable(ifStmt.getElseBranch(), varName))
                        && !isVariableUsedAfter(stmts, i + 1, varName, ifStmt)) {
                    Expression newCond = substituteVariable(cond, varName, initExpr);
                    ifStmt.setCondition(newCond);
                    stmts.remove(i);
                    changed = true;
                    i--;
                }
            } else if (next instanceof WhileStmt) {
                WhileStmt whileStmt = (WhileStmt) next;
                Expression cond = whileStmt.getCondition();

                int useCount = countVariableUses(cond, varName);
                if (useCount == 1 && !usesVariable(whileStmt.getBody(), varName)
                        && !isVariableUsedAfter(stmts, i + 1, varName, whileStmt)) {
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

    /**
     * Collapses a value materialized through a default init and a one-armed {@code if} back into the condition:
     * <pre>
     *   boolean t = false; if (c) { t = true; }   =&gt;   boolean t = c;
     *   boolean t = true;  if (c) { t = false; }   =&gt;   boolean t = !c;
     * </pre>
     * The reaching-condition engine lowers a boolean phi as these two statements where javac wrote the condition
     * directly; a later single-use inline then folds {@code boolean t = c; return t;} to {@code return c}. Applies
     * to a declaration ({@code T t = V0}) or a plain assignment, and only when the init, the conditional value,
     * and the condition are side-effect free and {@code c} does not read {@code t} - so evaluating {@code c}
     * first changes nothing.
     */
    private boolean collapseConditionalMaterialization(List<Statement> stmts) {
        boolean changed = false;
        for (int i = 0; i + 1 < stmts.size(); i++) {
            String var = assignedVar(stmts.get(i));
            Expression v0 = assignedValue(stmts.get(i));
            if (var == null || v0 == null || isSideEffecting(v0) || countVariableUses(v0, var) > 0) {
                continue;
            }
            if (!(stmts.get(i + 1) instanceof IfStmt)) {
                continue;
            }
            IfStmt ifStmt = (IfStmt) stmts.get(i + 1);
            if (ifStmt.hasElse()) {
                continue;
            }
            Statement inner = unwrapSingleStatement(ifStmt.getThenBranch());
            if (inner == null || !var.equals(simpleAssignVar(inner))) {
                continue;
            }
            Expression v1 = simpleAssignValue(inner);
            Expression cond = ifStmt.getCondition();
            if (v1 == null || isSideEffecting(v1) || isSideEffecting(cond)
                    || countVariableUses(cond, var) > 0 || countVariableUses(v1, var) > 0) {
                continue;
            }
            Expression folded = materializeBoolean(cond, v1, v0);
            if (folded == null) {
                continue;
            }
            Statement rebuilt = rebuildAssignment(stmts.get(i), folded);
            if (rebuilt == null) {
                continue;
            }
            Locations.copy(stmts.get(i), rebuilt);
            stmts.set(i, rebuilt);
            stmts.remove(i + 1);
            changed = true;
        }
        return changed;
    }

    /**
     * Collapses a boolean flag whose default init, single conditional write, and single read live at different
     * nesting levels:
     * <pre>
     *   boolean t = false; if (a) { ...; if (c) { t = true; } return t; } return false;
     *   ==&gt;  boolean-free: ...; if (a) { ...; return c; } return false;
     * </pre>
     * The reaching-condition engine hoists the phi default ({@code t = false}) to the flag's method-scope
     * declaration while its conditional {@code t = true} and its {@code return t} sit inside a branch. This folds
     * when {@code t} has exactly one declaration with a boolean-literal init, exactly one other write (the
     * conditional one, immediately followed by {@code return t}), and no other use - so the flag is exactly the
     * boolean value of its condition.
     */
    private boolean collapseReturnedBooleanPhi(List<Statement> stmts) {
        if (methodRoot == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i + 1 < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof IfStmt) || !(stmts.get(i + 1) instanceof ReturnStmt)) {
                continue;
            }
            IfStmt ifStmt = (IfStmt) stmts.get(i);
            ReturnStmt ret = (ReturnStmt) stmts.get(i + 1);
            if (ifStmt.hasElse() || !(ret.getValue() instanceof VarRefExpr)) {
                continue;
            }
            String var = ((VarRefExpr) ret.getValue()).getName();
            Statement inner = unwrapSingleStatement(ifStmt.getThenBranch());
            if (inner == null || !var.equals(simpleAssignVar(inner))) {
                continue;
            }
            Expression v1 = simpleAssignValue(inner);
            Expression cond = ifStmt.getCondition();
            // The condition may have side effects (e.g. a call): the `if` evaluates it exactly once, and the
            // folded `return cond` (or `!cond`) at the same position evaluates it exactly once too.
            if (v1 == null || isSideEffecting(v1) || countVariableUses(cond, var) > 0) {
                continue;
            }
            VarDeclStmt decl = findDeclaration(methodRoot, var);
            if (decl == null || decl.getInitializer() == null || !isBooleanType(decl.getType())
                    || countUsesInTree(methodRoot, var) != 2) {
                continue; // exactly the `var = v1` write and the `return var` read
            }
            Expression folded = materializeBoolean(cond, v1, decl.getInitializer());
            if (folded == null) {
                continue;
            }
            ReturnStmt newRet = new ReturnStmt(folded);
            newRet.setMethodReturnType(ret.getMethodReturnType());
            Locations.copy(ret, newRet);
            stmts.set(i + 1, newRet);
            stmts.remove(i);
            // The flag is now unused; a later dead-variable pass drops its declaration. Removing it here would
            // mutate an ancestor statement list while the recursive walk still iterates it.
            changed = true;
            i--;
        }
        return changed;
    }

    /** The first {@code VarDeclStmt} declaring {@code var} anywhere in the tree, else null. */
    private VarDeclStmt findDeclaration(Statement s, String var) {
        for (List<Statement> lst : childStatementLists(s)) {
            for (Statement st : lst) {
                if (st instanceof VarDeclStmt && var.equals(((VarDeclStmt) st).getName())) {
                    return (VarDeclStmt) st;
                }
                VarDeclStmt inner = findDeclaration(st, var);
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    private int countUsesInTree(Statement s, String var) {
        AtomicInteger count = new AtomicInteger(0);
        s.accept(new AbstractSourceVisitor<Void>() {
            @Override
            public Void visitVarRef(VarRefExpr e) {
                if (e.getName().equals(var)) {
                    count.incrementAndGet();
                }
                return super.visitVarRef(e);
            }
        });
        return count.get();
    }

    private List<List<Statement>> childStatementLists(Statement s) {
        List<List<Statement>> lists = new ArrayList<>();
        if (s instanceof BlockStmt) {
            lists.add(((BlockStmt) s).getStatements());
        } else if (s instanceof IfStmt) {
            IfStmt i = (IfStmt) s;
            addStatementBody(lists, i.getThenBranch());
            if (i.hasElse()) {
                addStatementBody(lists, i.getElseBranch());
            }
        } else if (s instanceof ForStmt) {
            addStatementBody(lists, ((ForStmt) s).getBody());
        } else if (s instanceof WhileStmt) {
            addStatementBody(lists, ((WhileStmt) s).getBody());
        } else if (s instanceof DoWhileStmt) {
            addStatementBody(lists, ((DoWhileStmt) s).getBody());
        } else if (s instanceof ForEachStmt) {
            addStatementBody(lists, ((ForEachStmt) s).getBody());
        } else if (s instanceof SynchronizedStmt) {
            addStatementBody(lists, ((SynchronizedStmt) s).getBody());
        } else if (s instanceof TryCatchStmt) {
            TryCatchStmt t = (TryCatchStmt) s;
            addStatementBody(lists, t.getTryBlock());
            for (CatchClause c : t.getCatches()) {
                addStatementBody(lists, c.body());
            }
            addStatementBody(lists, t.getFinallyBlock());
        } else if (s instanceof SwitchStmt) {
            for (SwitchCase c : ((SwitchStmt) s).getCases()) {
                if (c.statements() != null) {
                    lists.add(c.statements());
                }
            }
        }
        return lists;
    }

    private void addStatementBody(List<List<Statement>> lists, Statement body) {
        if (body instanceof BlockStmt) {
            lists.add(((BlockStmt) body).getStatements());
        }
    }

    /**
     * Folds a logical identity where one operand is a boolean constant: {@code x && true} and {@code true && x}
     * to {@code x}, {@code x || false} and {@code false || x} to {@code x}. These keep the other operand as-is,
     * so they are always safe (no side effect is dropped). The absorbing cases ({@code x && false},
     * {@code x || true}) are left alone since they would discard the other operand. Returns null when neither
     * operand is the matching constant, or the operator is not a short-circuit {@code &&}/{@code ||}.
     */
    private Expression foldBooleanIdentity(BinaryOperator op, Expression left, Expression right) {
        if (op == BinaryOperator.AND) {
            if (isBoolLiteral(right, true)) {
                return left;
            }
            if (isBoolLiteral(left, true)) {
                return right;
            }
        } else if (op == BinaryOperator.OR) {
            if (isBoolLiteral(right, false)) {
                return left;
            }
            if (isBoolLiteral(left, false)) {
                return right;
            }
        }
        return null;
    }

    /** {@code c} for {@code true/false}, {@code !c} for {@code false/true}; null when the arms are not that pair. */
    private Expression materializeBoolean(Expression cond, Expression whenTrue, Expression whenFalse) {
        if (isBoolLiteral(whenTrue, true) && isBoolLiteral(whenFalse, false)) {
            return cond;
        }
        if (isBoolLiteral(whenTrue, false) && isBoolLiteral(whenFalse, true)) {
            return negate(cond);
        }
        return null;
    }

    private boolean isBoolLiteral(Expression e, boolean value) {
        if (!(e instanceof LiteralExpr)) {
            return false;
        }
        Object v = ((LiteralExpr) e).getValue();
        if (v instanceof Boolean) {
            return ((Boolean) v) == value;
        }
        if (v instanceof Integer) {
            return ((Integer) v) == (value ? 1 : 0); // JVM materializes boolean as int 1/0
        }
        return false;
    }

    private boolean isSideEffecting(Expression e) {
        return Boolean.TRUE.equals(e.accept(SideEffectDetector.INSTANCE));
    }

    /** The variable written by a declaration-with-init or a plain {@code v = ...} assignment, else null. */
    private String assignedVar(Statement s) {
        if (s instanceof VarDeclStmt && ((VarDeclStmt) s).getInitializer() != null) {
            return ((VarDeclStmt) s).getName();
        }
        return simpleAssignVar(s);
    }

    private Expression assignedValue(Statement s) {
        if (s instanceof VarDeclStmt) {
            return ((VarDeclStmt) s).getInitializer();
        }
        return simpleAssignValue(s);
    }

    /** The variable written by a plain {@code v = ...} expression statement, else null. */
    private String simpleAssignVar(Statement s) {
        if (s instanceof ExprStmt && ((ExprStmt) s).getExpression() instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) ((ExprStmt) s).getExpression();
            if (b.getOperator() == BinaryOperator.ASSIGN && b.getLeft() instanceof VarRefExpr) {
                return ((VarRefExpr) b.getLeft()).getName();
            }
        }
        return null;
    }

    private Expression simpleAssignValue(Statement s) {
        if (s instanceof ExprStmt && ((ExprStmt) s).getExpression() instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) ((ExprStmt) s).getExpression();
            if (b.getOperator() == BinaryOperator.ASSIGN) {
                return b.getRight();
            }
        }
        return null;
    }

    /** Rebuilds statement {@code s} (declaration or assignment) with a new right-hand side. */
    private Statement rebuildAssignment(Statement s, Expression value) {
        if (s instanceof VarDeclStmt) {
            VarDeclStmt d = (VarDeclStmt) s;
            return new VarDeclStmt(d.getType(), d.getName(), value);
        }
        if (s instanceof ExprStmt && ((ExprStmt) s).getExpression() instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) ((ExprStmt) s).getExpression();
            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, b.getLeft(), value, b.getType()));
        }
        return null;
    }

    private boolean isBooleanType(SourceType type) {
        if (type == null) return false;
        String typeName = type.toJavaSource();
        return "boolean".equals(typeName) || "Boolean".equals(typeName) ||
               "java.lang.Boolean".equals(typeName);
    }

    /**
     * Counts how many times a variable is referenced in an expression.
     */
    private int countVariableUses(Expression expr, String varName) {
        AtomicInteger count = new AtomicInteger(0);
        expr.accept(new AbstractSourceVisitor<Void>() {
            @Override
            public Void visitVarRef(VarRefExpr e) {
                if (e.getName().equals(varName)) {
                    count.incrementAndGet();
                }
                return super.visitVarRef(e);
            }
        });
        return count.get();
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
                    newArgs, mc.isStatic(), mc.getType()).withDescriptor(mc.getDescriptor())
                    .withSuperCall(mc.isSuperCall());
            }
            return expr;
        }

        return expr;
    }

    /**
     * Collapses a positive guard whose outer-else and inner guard reach the SAME early exit into one OR guard:
     * <pre>if (cond) { if (inner) E; rest } E   =&gt;   if (!cond || inner) E; rest</pre>
     * javac compiles {@code if (!cond || inner) E; rest} (e.g. {@code if (session == null || expired()) return
     * null;}) as short-circuit branches that the recovery renders as the expanded nested form, while YABR's own
     * recompiled shape recovers the OR directly - so d1 and d2 diverge. Rebuilding the OR here makes the first
     * decompile a fixed point. Requires the two early exits to be identical so control flow is preserved exactly.
     */
    private boolean collapseGuardWithSharedEarlyExit(List<Statement> stmts) {
        boolean changed = false;
        for (int i = 0; i + 1 < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof IfStmt)) {
                continue;
            }
            IfStmt outer = (IfStmt) stmts.get(i);
            if (outer.hasElse()) {
                continue;
            }
            Statement afterExit = stmts.get(i + 1);
            if (!isEarlyExit(afterExit)) {
                continue;
            }
            List<Statement> body = getStatements(outer.getThenBranch());
            if (body.isEmpty() || !(body.get(0) instanceof IfStmt)) {
                continue;
            }
            IfStmt guard = (IfStmt) body.get(0);
            if (guard.hasElse()) {
                continue;
            }
            Statement guardExit = unwrapSingleStatement(guard.getThenBranch());
            if (!(guardExit instanceof ReturnStmt || guardExit instanceof ThrowStmt
                    || guardExit instanceof ContinueStmt || guardExit instanceof BreakStmt)
                    || !earlyExitsEqual(guardExit, afterExit)) {
                continue;
            }
            Expression collapsed = new BinaryExpr(BinaryOperator.OR,
                    negate(outer.getCondition()), guard.getCondition(), PrimitiveSourceType.BOOLEAN);
            List<Statement> guardBody = new ArrayList<>();
            guardBody.add(afterExit);
            IfStmt newIf = new IfStmt(collapsed, new BlockStmt(guardBody));
            Locations.copy(outer, newIf);
            stmts.set(i, newIf);
            stmts.remove(i + 1);
            List<Statement> rest = new ArrayList<>(body.subList(1, body.size()));
            for (int k = 0; k < rest.size(); k++) {
                stmts.add(i + 1 + k, rest.get(k));
            }
            changed = true;
        }
        return changed;
    }

    private boolean flattenNestedNegatedGuards(List<Statement> stmts) {
        boolean changed = false;

        for (int i = 0; i < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof IfStmt)) continue;
            IfStmt outerIf = (IfStmt) stmts.get(i);

            if (outerIf.hasElse()) continue;

            List<Expression> positiveConditions = new ArrayList<>();
            Statement innermostBody = collectNestedNegatedConditions(outerIf, positiveConditions);

            if (positiveConditions.size() < 2) continue;

            if (i + 1 >= stmts.size()) continue;
            Statement afterIf = stmts.get(i + 1);
            if (!isEarlyExit(afterIf)) continue;

            Expression orCondition = positiveConditions.get(0);
            for (int k = 1; k < positiveConditions.size(); k++) {
                orCondition = new BinaryExpr(
                    BinaryOperator.OR,
                    orCondition,
                    positiveConditions.get(k),
                    PrimitiveSourceType.BOOLEAN
                );
            }

            IfStmt newIf = new IfStmt(orCondition, afterIf);
            Locations.copy(stmts.get(i), newIf);

            stmts.set(i, newIf);

            if (innermostBody != null) {
                List<Statement> bodyStmts = getStatements(innermostBody);
                stmts.remove(i + 1);
                for (int k = 0; k < bodyStmts.size(); k++) {
                    stmts.add(i + 1 + k, bodyStmts.get(k));
                }
            } else {
                stmts.remove(i + 1);
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

        Expression positiveCond = getPositiveCondition(cond);
        if (positiveCond == null) {
            return null;
        }

        positiveConditions.add(positiveCond);

        Statement inner = unwrapSingleStatement(ifStmt.getThenBranch());

        if (inner instanceof IfStmt) {
            IfStmt innerIf = (IfStmt) inner;
            if (!innerIf.hasElse()) {
                return collectNestedNegatedConditions(innerIf, positiveConditions);
            }
        }

        return ifStmt.getThenBranch();
    }

    /**
     * Extracts the positive form of a negated condition.
     * Returns null if the condition is not a negation.
     */
    private Expression getPositiveCondition(Expression expr) {
        if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            if (unary.getOperator() == UnaryOperator.NOT) {
                return unary.getOperand();
            }
        }

        if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            if (binary.getOperator() == BinaryOperator.EQ) {
                if (isFalseLiteral(binary.getRight())) {
                    return binary.getLeft();
                }
                if (isFalseLiteral(binary.getLeft())) {
                    return binary.getRight();
                }
            }
            if (binary.getOperator() == BinaryOperator.NE) {
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
