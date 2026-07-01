package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Folds the verbose array-build idiom javac emits for an array literal back into a literal:
 * <pre>
 *   int[] tmp = new int[N]; tmp[0] = a0; tmp[1] = a1; ...; tmp[N-1] = a_{N-1}; X = tmp;
 * </pre>
 * becomes
 * <pre>
 *   X = new int[]{a0, a1, ..., a_{N-1}};
 * </pre>
 * Only a fully and in-order initialized build (all indices {@code 0..N-1}, constant size, each store once)
 * whose temp is used exactly once afterward (the consumer) and nowhere else, with store values that don't
 * reference the temp, is folded - which is exactly the shape javac produces for {@code new T[]{...}}. Anything
 * sparser or reused stays in its expanded form (safe, just less pretty).
 */
public class ArrayInitializerReconstructor implements ASTTransform {

    private static final int MAX_SIZE = 4096;

    @Override
    public String getName() {
        return "ArrayInitializerReconstructor";
    }

    @Override
    public boolean transform(BlockStmt block) {
        boolean changed = false;
        boolean progress;
        do {
            progress = fold(block.getStatements());
            changed |= progress;
        } while (progress);
        for (Statement s : block.getStatements()) {
            changed |= recurse(s);
        }
        return changed;
    }

    private boolean fold(List<Statement> stmts) {
        for (int i = 0; i < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof VarDeclStmt)) {
                continue;
            }
            VarDeclStmt decl = (VarDeclStmt) stmts.get(i);
            if (!(decl.getInitializer() instanceof NewArrayExpr)) {
                continue;
            }
            NewArrayExpr na = (NewArrayExpr) decl.getInitializer();
            if (na.getInitializer() != null) {
                continue; // already `new T[]{...}`
            }
            Integer size = constSize(na);
            if (size == null || size < 0 || size > MAX_SIZE) {
                continue;
            }
            String var = decl.getName();

            if (foldConsumerFirst(stmts, i, na, size, var)) {
                return true;
            }

            List<Expression> elements = new ArrayList<>();
            int j = i + 1;
            while (j < stmts.size() && elements.size() < size) {
                Expression value = storeValue(stmts.get(j), var, elements.size());
                if (value == null || referencesVar(value, var)) {
                    break;
                }
                elements.add(value);
                j++;
            }
            if (elements.size() != size || j >= stmts.size()) {
                continue; // not a complete in-order init, or no consumer
            }

            Statement consumer = stmts.get(j);
            if (countUses(consumer, var) != 1) {
                continue;
            }
            int totalUses = 0;
            for (int t = i + 1; t < stmts.size(); t++) {
                totalUses += countUses(stmts.get(t), var);
            }
            if (totalUses != size + 1) {
                continue; // the temp is used somewhere other than the N stores + the one consumer
            }

            ArrayInitExpr arrayInit = new ArrayInitExpr(elements, na.getType());
            NewArrayExpr literal = new NewArrayExpr(na.getElementType(), new ArrayList<>(), arrayInit,
                    na.getType(), na.getLocation());

            ExpressionReplacer replacer = new ExpressionReplacer(var, literal);
            Statement replaced = replaceInStatement(consumer, replacer);
            if (replacer.replacementCount != 1) {
                continue;
            }
            stmts.set(j, replaced);
            if (j > i) {
                stmts.subList(i, j).clear();
            }
            return true;
        }
        return false;
    }

    /**
     * The consumer-first variant: {@code T[] tmp = new T[N]; X = tmp; tmp[0]=e0; ...; tmp[N-1]=e_{N-1}} folded to
     * {@code X = new T[]{e0, ..., e_{N-1}}}. javac assigns the array to its target before the element stores (the
     * stores modify the aliased array), so the store scan of the consumer-last path never starts. Safe under the
     * same conditions: the temp is used only by the one assignment plus the N in-order stores, and no store value
     * references the temp or the target X (so X's intermediate state is never observed).
     */
    private boolean foldConsumerFirst(List<Statement> stmts, int i, NewArrayExpr na, int size, String var) {
        if (i + 1 >= stmts.size()) {
            return false;
        }
        Statement consumer = stmts.get(i + 1);
        String target = assignTargetFromVar(consumer, var);
        if (target == null || target.equals(var)) {
            return false;
        }
        List<Expression> elements = new ArrayList<>();
        int j = i + 2;
        while (j < stmts.size() && elements.size() < size) {
            Expression value = storeValue(stmts.get(j), var, elements.size());
            if (value == null || referencesVar(value, var) || countUsesExpr(value, target) > 0) {
                break;
            }
            elements.add(value);
            j++;
        }
        if (elements.size() != size) {
            return false;
        }
        int total = 0;
        for (int t = i + 1; t < j; t++) {
            total += countUses(stmts.get(t), var);
        }
        if (total != size + 1) {
            return false; // the temp is used somewhere other than the one assignment + N stores
        }

        ArrayInitExpr arrayInit = new ArrayInitExpr(elements, na.getType());
        NewArrayExpr literal = new NewArrayExpr(na.getElementType(), new ArrayList<>(), arrayInit,
                na.getType(), na.getLocation());
        ExpressionReplacer replacer = new ExpressionReplacer(var, literal);
        Statement replaced = replaceInStatement(consumer, replacer);
        if (replacer.replacementCount != 1) {
            return false;
        }
        stmts.set(i + 1, replaced);      // X = new T[]{...}
        stmts.subList(i + 2, j).clear(); // remove the element stores
        stmts.remove(i);                 // remove the temp declaration
        return true;
    }

    /** If {@code stmt} is {@code X = var} with X a simple variable and rhs exactly {@code var}, X's name; else null. */
    private String assignTargetFromVar(Statement stmt, String var) {
        if (!(stmt instanceof ExprStmt)) {
            return null;
        }
        Expression e = ((ExprStmt) stmt).getExpression();
        if (!(e instanceof BinaryExpr)) {
            return null;
        }
        BinaryExpr b = (BinaryExpr) e;
        if (b.getOperator() != BinaryOperator.ASSIGN
                || !(b.getLeft() instanceof VarRefExpr)
                || !(b.getRight() instanceof VarRefExpr)
                || !((VarRefExpr) b.getRight()).getName().equals(var)) {
            return null;
        }
        return ((VarRefExpr) b.getLeft()).getName();
    }

    private Integer constSize(NewArrayExpr na) {
        if (na.getDimensions().size() != 1) {
            return null;
        }
        Expression d = na.getDimensions().get(0);
        if (d instanceof LiteralExpr && ((LiteralExpr) d).getValue() instanceof Integer) {
            return (Integer) ((LiteralExpr) d).getValue();
        }
        return null;
    }

    /** The store value if {@code stmt} is {@code var[index] = value} (constant {@code index}), else null. */
    private Expression storeValue(Statement stmt, String var, int index) {
        if (!(stmt instanceof ExprStmt)) {
            return null;
        }
        Expression e = ((ExprStmt) stmt).getExpression();
        if (!(e instanceof BinaryExpr)) {
            return null;
        }
        BinaryExpr b = (BinaryExpr) e;
        if (b.getOperator() != BinaryOperator.ASSIGN || !(b.getLeft() instanceof ArrayAccessExpr)) {
            return null;
        }
        ArrayAccessExpr aa = (ArrayAccessExpr) b.getLeft();
        if (!(aa.getArray() instanceof VarRefExpr)
                || !((VarRefExpr) aa.getArray()).getName().equals(var)
                || !(aa.getIndex() instanceof LiteralExpr)) {
            return null;
        }
        Object idx = ((LiteralExpr) aa.getIndex()).getValue();
        if (!(idx instanceof Integer) || (Integer) idx != index) {
            return null;
        }
        return b.getRight();
    }

    private boolean referencesVar(Expression expr, String var) {
        return countUsesExpr(expr, var) > 0;
    }

    private int countUses(Statement stmt, String var) {
        UsageCounter counter = new UsageCounter(var);
        stmt.accept(counter);
        return counter.count;
    }

    private int countUsesExpr(Expression expr, String var) {
        UsageCounter counter = new UsageCounter(var);
        expr.accept(counter);
        return counter.count;
    }

    private boolean recurse(Statement stmt) {
        boolean changed = false;
        if (stmt instanceof BlockStmt) {
            changed |= transform((BlockStmt) stmt);
        } else if (stmt instanceof IfStmt) {
            IfStmt s = (IfStmt) stmt;
            changed |= recurseBody(s.getThenBranch());
            if (s.hasElse()) {
                changed |= recurseBody(s.getElseBranch());
            }
        } else if (stmt instanceof WhileStmt) {
            changed |= recurseBody(((WhileStmt) stmt).getBody());
        } else if (stmt instanceof DoWhileStmt) {
            changed |= recurseBody(((DoWhileStmt) stmt).getBody());
        } else if (stmt instanceof ForStmt) {
            changed |= recurseBody(((ForStmt) stmt).getBody());
        } else if (stmt instanceof ForEachStmt) {
            changed |= recurseBody(((ForEachStmt) stmt).getBody());
        } else if (stmt instanceof SynchronizedStmt) {
            changed |= recurseBody(((SynchronizedStmt) stmt).getBody());
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt s = (TryCatchStmt) stmt;
            changed |= recurseBody(s.getTryBlock());
            for (CatchClause c : s.getCatches()) {
                changed |= recurseBody(c.body());
            }
            changed |= recurseBody(s.getFinallyBlock());
        } else if (stmt instanceof SwitchStmt) {
            for (SwitchCase c : ((SwitchStmt) stmt).getCases()) {
                List<Statement> cs = c.statements();
                if (cs != null) {
                    boolean p;
                    do {
                        p = fold(cs);
                        changed |= p;
                    } while (p);
                }
            }
        }
        return changed;
    }

    private boolean recurseBody(Statement body) {
        return body instanceof BlockStmt && transform((BlockStmt) body);
    }

    // ---- variable counting + replacement (mirrors SingleUseInliner) --------------------------------

    private static class UsageCounter extends AbstractSourceVisitor<Void> {
        private final String varName;
        int count = 0;

        UsageCounter(String varName) {
            this.varName = varName;
        }

        @Override
        public Void visitVarRef(VarRefExpr expr) {
            if (expr.getName().equals(varName)) {
                count++;
            }
            return super.visitVarRef(expr);
        }
    }

    private static class ExpressionReplacer {
        final String varName;
        final Expression replacement;
        int replacementCount = 0;

        ExpressionReplacer(String varName, Expression replacement) {
            this.varName = varName;
            this.replacement = replacement;
        }
    }

    private Statement replaceInStatement(Statement stmt, ExpressionReplacer replacer) {
        if (stmt instanceof ExprStmt) {
            return new ExprStmt(replaceInExpression(((ExprStmt) stmt).getExpression(), replacer));
        } else if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (ret.getValue() != null) {
                return new ReturnStmt(replaceInExpression(ret.getValue(), replacer));
            }
            return stmt;
        } else if (stmt instanceof VarDeclStmt) {
            VarDeclStmt decl = (VarDeclStmt) stmt;
            if (decl.getInitializer() != null) {
                return new VarDeclStmt(decl.getType(), decl.getName(),
                        replaceInExpression(decl.getInitializer(), replacer));
            }
            return stmt;
        } else if (stmt instanceof ThrowStmt) {
            return new ThrowStmt(replaceInExpression(((ThrowStmt) stmt).getException(), replacer));
        }
        return stmt;
    }

    private Expression replaceInExpression(Expression expr, ExpressionReplacer replacer) {
        if (expr instanceof VarRefExpr) {
            if (((VarRefExpr) expr).getName().equals(replacer.varName)) {
                replacer.replacementCount++;
                return replacer.replacement;
            }
            return expr;
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) expr;
            Expression l = replaceInExpression(b.getLeft(), replacer);
            Expression r = replaceInExpression(b.getRight(), replacer);
            return (l != b.getLeft() || r != b.getRight())
                    ? new BinaryExpr(b.getOperator(), l, r, b.getType()) : expr;
        } else if (expr instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) expr;
            Expression o = replaceInExpression(u.getOperand(), replacer);
            return o != u.getOperand() ? new UnaryExpr(u.getOperator(), o, u.getType()) : expr;
        } else if (expr instanceof CastExpr) {
            CastExpr c = (CastExpr) expr;
            Expression o = replaceInExpression(c.getExpression(), replacer);
            return o != c.getExpression() ? new CastExpr(c.getTargetType(), o) : expr;
        } else if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr a = (ArrayAccessExpr) expr;
            Expression arr = replaceInExpression(a.getArray(), replacer);
            Expression idx = replaceInExpression(a.getIndex(), replacer);
            return (arr != a.getArray() || idx != a.getIndex())
                    ? new ArrayAccessExpr(arr, idx, a.getType()) : expr;
        } else if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr f = (FieldAccessExpr) expr;
            if (f.getReceiver() != null) {
                Expression rec = replaceInExpression(f.getReceiver(), replacer);
                if (rec != f.getReceiver()) {
                    return new FieldAccessExpr(rec, f.getFieldName(), f.getOwnerClass(), f.isStatic(), f.getType());
                }
            }
            return expr;
        } else if (expr instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expr;
            Expression rec = call.getReceiver() != null ? replaceInExpression(call.getReceiver(), replacer) : null;
            List<Expression> args = new ArrayList<>();
            boolean changed = rec != call.getReceiver();
            for (Expression arg : call.getArguments()) {
                Expression na = replaceInExpression(arg, replacer);
                args.add(na);
                changed |= na != arg;
            }
            return changed ? new MethodCallExpr(rec, call.getMethodName(), call.getOwnerClass(),
                    args, call.isStatic(), call.getType()) : expr;
        } else if (expr instanceof NewExpr) {
            NewExpr n = (NewExpr) expr;
            List<Expression> args = new ArrayList<>();
            boolean changed = false;
            for (Expression arg : n.getArguments()) {
                Expression na = replaceInExpression(arg, replacer);
                args.add(na);
                changed |= na != arg;
            }
            return changed ? new NewExpr(n.getClassName(), args, n.getType()) : expr;
        } else if (expr instanceof ArrayInitExpr) {
            ArrayInitExpr a = (ArrayInitExpr) expr;
            List<Expression> elems = new ArrayList<>();
            boolean changed = false;
            for (Expression el : a.getElements()) {
                Expression ne = replaceInExpression(el, replacer);
                elems.add(ne);
                changed |= ne != el;
            }
            return changed ? new ArrayInitExpr(elems, a.getType()) : expr;
        }
        return expr;
    }
}
