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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            ArrayBuild build = matchArrayBuild(stmts, i, uses);
            if (build != null
                    && uses.getOrDefault(build.varName, 0) == build.elements.size() + build.extraRefs + 1) {
                // The uses check guarantees the array var is referenced ONLY by its stores and the single
                // consuming call, so any statements between the build and that call (e.g. the recompile
                // hoists the array build ahead of an intervening `dialog.updateStatus(...)`) cannot touch it.
                // Scan forward to the consumer rather than requiring it immediately after the build.
                int ci = build.consumerIndex;
                MethodCallExpr consumer = null;
                while (ci < stmts.size()) {
                    consumer = findConsumer(stmts.get(ci), build.varName);
                    if (consumer != null) {
                        break;
                    }
                    ci++;
                }
                if (consumer != null && isVarargsCallee(consumer)) {
                    NodeList<Expression> args = consumer.getArguments();
                    args.remove(args.size() - 1);
                    args.addAll(build.elements);
                    for (int k = build.removeIndices.size() - 1; k >= 0; k--) {
                        stmts.remove((int) build.removeIndices.get(k));
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
    private ArrayBuild matchArrayBuild(List<Statement> stmts, int index, Map<String, Integer> uses) {
        Statement first = stmts.get(index);
        // The array creation is either a declaration `T[] v = new T[N]` or - when a slot-split gives the local
        // a separate null-init declaration - a plain assignment `v = new T[N]`. Accept both as the build start.
        String varName;
        NewArrayExpr array;
        if (first instanceof VarDeclStmt && ((VarDeclStmt) first).getInitializer() instanceof NewArrayExpr) {
            VarDeclStmt decl = (VarDeclStmt) first;
            varName = decl.getName();
            array = (NewArrayExpr) decl.getInitializer();
        } else if (first instanceof ExprStmt && ((ExprStmt) first).getExpression() instanceof BinaryExpr) {
            BinaryExpr assign = (BinaryExpr) ((ExprStmt) first).getExpression();
            if (assign.getOperator() != BinaryOperator.ASSIGN
                    || !(assign.getLeft() instanceof VarRefExpr)
                    || !(assign.getRight() instanceof NewArrayExpr)) {
                return null;
            }
            varName = ((VarRefExpr) assign.getLeft()).getName();
            array = (NewArrayExpr) assign.getRight();
        } else {
            return null;
        }
        if (array.getInitializer() != null || array.getDimensions().size() != 1) {
            return null;
        }
        Integer size = intLiteral(array.getDimensions().get(0));
        if (size == null || size < 0) {
            return null;
        }
        Expression[] elements = new Expression[size];
        List<Integer> removeIndices = new ArrayList<>();
        removeIndices.add(index);
        Map<String, Expression> elementTemps = new HashMap<>();
        Set<String> consumedTemps = new HashSet<>();
        int cursor = index + 1;
        int matched = 0;
        int extraRefs = 0;
        while (matched < size) {
            if (cursor >= stmts.size()) {
                return null;
            }
            Statement s = stmts.get(cursor);
            // An interleaved inert self-assignment or declaration of the array var (the spurious
            // `local6 = null` / `Object[] local6 = null` a slot-split round trip emits between the array
            // creation and its stores) is folded into the build and removed. extraRefs tracks the var
            // references it contributes so the "used only by this build" check below stays exact.
            int ir = inertRefs(s, varName);
            if (ir >= 0) {
                extraRefs += ir;
                removeIndices.add(cursor);
                cursor++;
                continue;
            }
            // A single-use temp holding one element's value (`T = <expr>` then later `arr[k] = T`) - the
            // recompile's multi-element eval-order spill. Record it; the store that reads T inlines <expr>.
            String temp = elementTempName(s, varName, uses);
            if (temp != null && !elementTemps.containsKey(temp)) {
                elementTemps.put(temp, ((BinaryExpr) ((ExprStmt) s).getExpression()).getRight());
                removeIndices.add(cursor);
                cursor++;
                continue;
            }
            if (!(s instanceof ExprStmt)) {
                return null;
            }
            Expression expr = ((ExprStmt) s).getExpression();
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
            Expression element = assign.getRight();
            if (element instanceof VarRefExpr && elementTemps.containsKey(((VarRefExpr) element).getName())) {
                String t = ((VarRefExpr) element).getName();
                consumedTemps.add(t);
                element = elementTemps.get(t);
            }
            elements[slot] = element;
            removeIndices.add(cursor);
            matched++;
            cursor++;
        }
        // Every recorded element temp must be consumed by a store; an unconsumed one means that `T = <expr>`
        // was not actually part of this build, so the match is invalid (do not remove unrelated statements).
        if (!consumedTemps.containsAll(elementTemps.keySet())) {
            return null;
        }
        return new ArrayBuild(varName, removeIndices, cursor, extraRefs, new ArrayList<>(List.of(elements)));
    }

    /**
     * If {@code s} is `T = <expr>` for a single-use temp {@code T} (not the array var, LHS a plain variable),
     * returns {@code T} - a candidate element value the recompile spilled out of the array store. {@code T}
     * must be referenced at most twice (its definition and the one store that reads it) so inlining is safe.
     */
    private String elementTempName(Statement s, String varName, Map<String, Integer> uses) {
        if (!(s instanceof ExprStmt) || !(((ExprStmt) s).getExpression() instanceof BinaryExpr)) {
            return null;
        }
        BinaryExpr b = (BinaryExpr) ((ExprStmt) s).getExpression();
        if (b.getOperator() != BinaryOperator.ASSIGN || !(b.getLeft() instanceof VarRefExpr)) {
            return null;
        }
        String t = ((VarRefExpr) b.getLeft()).getName();
        if (t.equals(varName) || uses.getOrDefault(t, 0) > 2) {
            return null;
        }
        return t;
    }

    /**
     * Classifies an interleaved statement that may sit inside an array build. Returns the number of {@code
     * varName} references it contributes if it is an inert (literal-valued) self-assignment ({@code 1}) or
     * declaration ({@code 0}) of the array var - both safe to fold into and remove with the build - or
     * {@code -1} if it is anything else (which ends the build).
     */
    private int inertRefs(Statement s, String varName) {
        if (s instanceof VarDeclStmt) {
            VarDeclStmt d = (VarDeclStmt) s;
            Expression init = d.getInitializer();
            if (d.getName().equals(varName) && (init == null || init instanceof LiteralExpr)) {
                return 0;
            }
            return -1;
        }
        if (s instanceof ExprStmt && ((ExprStmt) s).getExpression() instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) ((ExprStmt) s).getExpression();
            if (b.getOperator() == BinaryOperator.ASSIGN && b.getLeft() instanceof VarRefExpr
                    && ((VarRefExpr) b.getLeft()).getName().equals(varName)
                    && b.getRight() instanceof LiteralExpr) {
                return 1;
            }
        }
        return -1;
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
        final List<Integer> removeIndices;
        final int consumerIndex;
        final int extraRefs;
        final List<Expression> elements;

        ArrayBuild(String varName, List<Integer> removeIndices, int consumerIndex, int extraRefs,
                   List<Expression> elements) {
            this.varName = varName;
            this.removeIndices = removeIndices;
            this.consumerIndex = consumerIndex;
            this.extraRefs = extraRefs;
            this.elements = elements;
        }
    }
}
