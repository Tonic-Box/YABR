package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTUtils;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.expr.ArrayAccessExpr;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.MethodCallExpr;
import com.tonic.analysis.source.ast.expr.NewArrayExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.CatchClause;
import com.tonic.analysis.source.ast.stmt.DoWhileStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.ForEachStmt;
import com.tonic.analysis.source.ast.stmt.ForStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.LabeledStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.SynchronizedStmt;
import com.tonic.analysis.source.ast.stmt.TryCatchStmt;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.stmt.WhileStmt;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.util.DescriptorUtil;
import com.tonic.util.Modifiers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs varargs calls by collapsing an explicit trailing array argument back into individual
 * arguments. javac compiles {@code foo(a, b)} for a varargs method {@code foo(T...)} into
 * {@code foo(new T[]{a, b})}; because the array is filled element-by-element and then read by the
 * call it is materialized as a local. This transform recognises that shape --
 * {@code T[] v = new T[N]; v[0] = a; ...; v[N-1] = z; ... foo(..., v)} where {@code v} is used
 * nowhere else and the consuming call immediately follows the construction -- and rewrites the call
 * to {@code foo(..., a, ..., z)}, dropping the now-dead array construction.
 * <p>
 * Collapsing is applied only when the callee is resolvable (the class being decompiled or a
 * {@code java.base} class) and carries {@code ACC_VARARGS} with a parameter count matching the call.
 * Unresolvable callees keep the explicit array, which is always semantically valid.
 */
public class VarargsReconstructor implements ASTTransform {

    private final ClassFile classFile;

    public VarargsReconstructor(ClassFile classFile) {
        this.classFile = classFile;
    }

    @Override
    public String getName() {
        return "VarargsReconstructor";
    }

    @Override
    public boolean transform(BlockStmt block) {
        boolean changed = false;
        boolean progress;
        do {
            Map<String, Integer> uses = countVarRefs(block);
            progress = process(block.getStatements(), uses);
            changed |= progress;
        } while (progress);
        changed |= collapseInlineArrays(block);
        return changed;
    }

    /**
     * Collapses varargs calls whose trailing argument is an inline {@code new T[]{...}} or an empty
     * {@code new T[0]} (the form javac emits when the array is single-use and never materialized,
     * e.g. {@code String.format("x")} -> {@code String.format("x", new Object[0])}).
     */
    private boolean collapseInlineArrays(BlockStmt block) {
        List<InlineTarget> targets = new ArrayList<>();
        ASTUtils.forEachExpression(block, expr -> {
            if (!(expr instanceof MethodCallExpr)) {
                return;
            }
            MethodCallExpr call = (MethodCallExpr) expr;
            List<Expression> args = call.getArguments();
            if (args.isEmpty() || !(args.get(args.size() - 1) instanceof NewArrayExpr)) {
                return;
            }
            List<Expression> elements = inlineElements((NewArrayExpr) args.get(args.size() - 1));
            if (elements != null && isVarargsCallee(call)) {
                targets.add(new InlineTarget(call, elements));
            }
        });
        for (InlineTarget target : targets) {
            NodeList<Expression> args = target.call.getArguments();
            args.remove(args.size() - 1);
            args.addAll(target.elements);
        }
        return !targets.isEmpty();
    }

    /**
     * The varargs elements an inline array argument expands to, or {@code null} if it is not a safe
     * inline form: an array with an initializer expands to its elements, an empty {@code new T[0]}
     * expands to nothing, anything else (e.g. {@code new T[n]} with no initializer) is left intact.
     */
    private List<Expression> inlineElements(NewArrayExpr array) {
        if (array.getInitializer() != null) {
            return array.getInitializer().getElements();
        }
        if (array.getDimensions().size() == 1) {
            Integer size = intLiteral(array.getDimensions().get(0));
            if (size != null && size == 0) {
                return List.of();
            }
        }
        return null;
    }

    private Map<String, Integer> countVarRefs(BlockStmt block) {
        Map<String, Integer> counts = new HashMap<>();
        ASTUtils.forEachExpression(block, expr -> {
            if (expr instanceof VarRefExpr) {
                counts.merge(((VarRefExpr) expr).getName(), 1, Integer::sum);
            }
        });
        return counts;
    }

    /**
     * Scans a statement list (recursing into nested blocks) for one collapsible array-build group
     * whose consumer is the immediately following statement, performs the collapse, and returns
     * {@code true}. Returns after the first rewrite so the caller can recompute use counts.
     */
    private boolean process(List<Statement> stmts, Map<String, Integer> uses) {
        for (int i = 0; i < stmts.size(); i++) {
            ArrayBuild build = matchArrayBuild(stmts, i);
            if (build != null
                    && uses.getOrDefault(build.varName, 0) == build.stmtCount
                    && i + build.stmtCount < stmts.size()) {
                MethodCallExpr consumer = findConsumer(stmts.get(i + build.stmtCount), build.varName);
                if (consumer != null && isVarargsCallee(consumer)) {
                    NodeList<Expression> args = consumer.getArguments();
                    args.remove(args.size() - 1);
                    args.addAll(build.elements);
                    for (int k = 0; k < build.stmtCount; k++) {
                        stmts.remove(i);
                    }
                    return true;
                }
            }
            for (List<Statement> child : childLists(stmts.get(i))) {
                if (process(child, uses)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Matches {@code T[] v = new T[N]} followed by N consecutive constant-index stores
     * {@code v[0..N-1] = ...} starting at {@code index}. Returns the variable name, the number of
     * statements the group spans (declaration + stores), and the element expressions in index order,
     * or {@code null} if the shape does not match.
     */
    private ArrayBuild matchArrayBuild(List<Statement> stmts, int index) {
        Statement first = stmts.get(index);
        if (!(first instanceof VarDeclStmt)) {
            return null;
        }
        VarDeclStmt decl = (VarDeclStmt) first;
        if (!(decl.getInitializer() instanceof NewArrayExpr)) {
            return null;
        }
        NewArrayExpr array = (NewArrayExpr) decl.getInitializer();
        if (array.getInitializer() != null || array.getDimensions().size() != 1) {
            return null;
        }
        Integer size = intLiteral(array.getDimensions().get(0));
        if (size == null || size < 0) {
            return null;
        }
        String varName = decl.getName();
        Expression[] elements = new Expression[size];
        for (int k = 0; k < size; k++) {
            int storeIndex = index + 1 + k;
            if (storeIndex >= stmts.size() || !(stmts.get(storeIndex) instanceof ExprStmt)) {
                return null;
            }
            Expression expr = ((ExprStmt) stmts.get(storeIndex)).getExpression();
            if (!(expr instanceof BinaryExpr)) {
                return null;
            }
            BinaryExpr assign = (BinaryExpr) expr;
            if (assign.getOperator() != BinaryOperator.ASSIGN
                    || !(assign.getLeft() instanceof ArrayAccessExpr)) {
                return null;
            }
            ArrayAccessExpr target = (ArrayAccessExpr) assign.getLeft();
            if (!(target.getArray() instanceof VarRefExpr)
                    || !((VarRefExpr) target.getArray()).getName().equals(varName)) {
                return null;
            }
            Integer slot = intLiteral(target.getIndex());
            if (slot == null || slot < 0 || slot >= size || elements[slot] != null) {
                return null;
            }
            elements[slot] = assign.getRight();
        }
        return new ArrayBuild(varName, 1 + size, new ArrayList<>(List.of(elements)));
    }

    /**
     * Finds the method call inside {@code statement} whose last argument is a reference to
     * {@code varName}. The caller has already verified the variable has exactly one use outside its
     * stores, so any match here is that sole consuming use.
     */
    private MethodCallExpr findConsumer(Statement statement, String varName) {
        MethodCallExpr[] found = {null};
        ASTUtils.forEachExpression(statement, expr -> {
            if (found[0] != null || !(expr instanceof MethodCallExpr)) {
                return;
            }
            MethodCallExpr call = (MethodCallExpr) expr;
            List<Expression> args = call.getArguments();
            if (!args.isEmpty()) {
                Expression last = args.get(args.size() - 1);
                if (last instanceof VarRefExpr && ((VarRefExpr) last).getName().equals(varName)) {
                    found[0] = call;
                }
            }
        });
        return found[0];
    }

    /**
     * Resolves the callee and reports whether an overload named like the call, with a parameter
     * count equal to the call's current argument count, carries {@code ACC_VARARGS}.
     */
    private boolean isVarargsCallee(MethodCallExpr call) {
        String owner = call.getOwnerClass();
        if (owner == null) {
            return false;
        }
        ClassFile callee = resolveOwner(owner.replace('.', '/'));
        if (callee == null) {
            return false;
        }
        int argCount = call.getArguments().size();
        for (MethodEntry method : callee.getMethods(call.getMethodName())) {
            if (Modifiers.isVarArgs(method.getAccess())
                    && DescriptorUtil.countParameters(method.getDesc()) == argCount) {
                return true;
            }
        }
        return false;
    }

    private ClassFile resolveOwner(String internalName) {
        try {
            if (classFile != null && internalName.equals(classFile.getClassName())) {
                return classFile;
            }
            return ClassPool.getDefault().get(internalName);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Integer intLiteral(Expression expr) {
        if (expr instanceof LiteralExpr && ((LiteralExpr) expr).getValue() instanceof Number) {
            return ((Number) ((LiteralExpr) expr).getValue()).intValue();
        }
        return null;
    }

    private List<List<Statement>> childLists(Statement stmt) {
        List<List<Statement>> lists = new ArrayList<>();
        if (stmt instanceof BlockStmt) {
            lists.add(((BlockStmt) stmt).getStatements());
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            addBlock(lists, ifStmt.getThenBranch());
            if (ifStmt.hasElse()) {
                addBlock(lists, ifStmt.getElseBranch());
            }
        } else if (stmt instanceof WhileStmt) {
            addBlock(lists, ((WhileStmt) stmt).getBody());
        } else if (stmt instanceof DoWhileStmt) {
            addBlock(lists, ((DoWhileStmt) stmt).getBody());
        } else if (stmt instanceof ForStmt) {
            addBlock(lists, ((ForStmt) stmt).getBody());
        } else if (stmt instanceof ForEachStmt) {
            addBlock(lists, ((ForEachStmt) stmt).getBody());
        } else if (stmt instanceof SynchronizedStmt) {
            addBlock(lists, ((SynchronizedStmt) stmt).getBody());
        } else if (stmt instanceof LabeledStmt) {
            addBlock(lists, ((LabeledStmt) stmt).getStatement());
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            addBlock(lists, tryCatch.getTryBlock());
            for (CatchClause clause : tryCatch.getCatches()) {
                if (clause.body() instanceof BlockStmt) {
                    lists.add(((BlockStmt) clause.body()).getStatements());
                }
            }
            addBlock(lists, tryCatch.getFinallyBlock());
        }
        return lists;
    }

    private void addBlock(List<List<Statement>> lists, Statement candidate) {
        if (candidate instanceof BlockStmt) {
            lists.add(((BlockStmt) candidate).getStatements());
        }
    }

    private static final class InlineTarget {
        final MethodCallExpr call;
        final List<Expression> elements;

        InlineTarget(MethodCallExpr call, List<Expression> elements) {
            this.call = call;
            this.elements = elements;
        }
    }

    private static final class ArrayBuild {
        final String varName;
        final int stmtCount;
        final List<Expression> elements;

        ArrayBuild(String varName, int stmtCount, List<Expression> elements) {
            this.varName = varName;
            this.stmtCount = stmtCount;
            this.elements = elements;
        }
    }
}
