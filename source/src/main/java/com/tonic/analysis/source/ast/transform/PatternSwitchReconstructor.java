package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.Locations;
import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.CastExpr;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.InvokeDynamicExpr;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.MethodCallExpr;
import com.tonic.analysis.source.ast.expr.NewExpr;
import com.tonic.analysis.source.ast.expr.SwitchExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.BreakStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.SwitchCase;
import com.tonic.analysis.source.ast.stmt.SwitchStmt;
import com.tonic.analysis.source.ast.stmt.ThrowStmt;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.stmt.WhileStmt;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconstructs a pattern-matching switch (Java 21, {@code SwitchBootstraps.typeSwitch}) from the
 * {@code $pc$} dispatch loop that control-flow recovery falls back to.
 *
 * <p>javac compiles {@code return switch (o) { case Integer i -> ...; case String s -> ...; default
 * -> ... }} to a {@code typeSwitch} invokedynamic whose result drives a switch; YABR currently
 * recovers this as a {@code while(true) switch($pc$){...}} state machine. This pass recognizes that
 * shape and folds it back into a {@link SwitchExpr} with type-pattern arms wrapped in a
 * {@code return}. It is strictly gated on the inner switch's selector being a {@code typeSwitch}
 * invokedynamic, so the other dispatch loops YABR emits for genuinely unstructurable code are left
 * untouched.
 *
 * <p>Scope: type-pattern arms (with optional binding) + default, in both the return and assignment
 * forms, plus record-deconstruction arms ({@code case Point(int x, int y) -> ...}) recognized via the
 * {@code CastExpr.recordDeconstruction} marker the recovery sets from the MatchException handler.
 * Guards ({@code when}) are not yet folded.
 */
public class PatternSwitchReconstructor implements ASTTransform {

    @Override
    public String getName() {
        return "PatternSwitchReconstructor";
    }

    @Override
    public boolean transform(BlockStmt block) {
        return process(block.getStatements());
    }

    private boolean process(List<Statement> stmts) {
        boolean changed = false;
        for (int i = 0; i < stmts.size(); i++) {
            WhileStmt loop = asDispatchLoop(stmts.get(i));
            if (loop != null) {
                Folded f = tryFold(loop);
                if (f != null) {
                    Locations.copy(stmts.get(i), f.replacement);
                    stmts.set(i, f.replacement);
                    removeDeadDecls(stmts, i, f.deadVars);
                    changed = true;
                    i = -1; // list mutated; restart scan
                    continue;
                }
            }
            // Structured form: `{ ...; switch (typeSwitch(sel,idx)) { case k: ... } }` where each
            // arm assigns a result variable (the assignment form of a pattern switch).
            SwitchStmt structured = findStructuredTypeSwitch(stmts.get(i));
            if (structured != null && tryFoldStructured(stmts, i, structured)) {
                changed = true;
                i = -1; // list mutated; restart scan
            }
        }
        for (Statement s : stmts) {
            changed |= recurse(s);
        }
        return changed;
    }

    /** A {@code switch(typeSwitch(...))} that is statement {@code i} or the body of a block there. */
    private SwitchStmt findStructuredTypeSwitch(Statement s) {
        if (isTypeSwitchStmt(s)) {
            return (SwitchStmt) s;
        }
        if (s instanceof BlockStmt) {
            for (Statement inner : ((BlockStmt) s).getStatements()) {
                if (isTypeSwitchStmt(inner)) {
                    return (SwitchStmt) inner;
                }
            }
        }
        return null;
    }

    private boolean isTypeSwitchStmt(Statement s) {
        return s instanceof SwitchStmt
                && ((SwitchStmt) s).getSelector() instanceof InvokeDynamicExpr
                && "typeSwitch".equals(((InvokeDynamicExpr) ((SwitchStmt) s).getSelector()).getBootstrapName());
    }

    /** The dispatch {@code while(true)}, whether bare or wrapped in a {@code $label:} LabeledStmt. */
    private WhileStmt asDispatchLoop(Statement s) {
        if (s instanceof WhileStmt) {
            return (WhileStmt) s;
        }
        if (s instanceof com.tonic.analysis.source.ast.stmt.LabeledStmt
                && ((com.tonic.analysis.source.ast.stmt.LabeledStmt) s).getStatement() instanceof WhileStmt) {
            return (WhileStmt) ((com.tonic.analysis.source.ast.stmt.LabeledStmt) s).getStatement();
        }
        return null;
    }

    private boolean recurse(ASTNode node) {
        boolean changed = false;
        for (ASTNode child : node.getChildren()) {
            if (child instanceof BlockStmt) {
                changed |= process(((BlockStmt) child).getStatements());
            } else {
                changed |= recurse(child);
            }
        }
        return changed;
    }

    private static final class Folded {
        final Statement replacement;
        final Set<String> deadVars;
        Folded(Statement replacement, Set<String> deadVars) {
            this.replacement = replacement;
            this.deadVars = deadVars;
        }
    }

    private Folded tryFold(WhileStmt loop) {
        if (!isTrueLiteral(loop.getCondition()) || !(loop.getBody() instanceof BlockStmt)) {
            return null;
        }
        List<Statement> loopBody = ((BlockStmt) loop.getBody()).getStatements();
        if (loopBody.size() != 1 || !(loopBody.get(0) instanceof SwitchStmt)) {
            return null;
        }
        SwitchStmt dispatch = (SwitchStmt) loopBody.get(0);
        if (!isVarRef(dispatch.getSelector(), "$pc$")) {
            return null;
        }

        // state number -> its case statements
        Map<Integer, List<Statement>> states = new HashMap<>();
        for (SwitchCase c : dispatch.getCases()) {
            for (Integer label : c.labels()) {
                states.put(label, c.statements());
            }
        }

        // Find the entry state: the one whose body holds the inner typeSwitch dispatch.
        SwitchStmt typeSwitchInner = null;
        int dispatchState = -1;
        for (Map.Entry<Integer, List<Statement>> e : states.entrySet()) {
            SwitchStmt inner = findTypeSwitch(e.getValue());
            if (inner != null) {
                typeSwitchInner = inner;
                dispatchState = e.getKey();
                break;
            }
        }
        if (typeSwitchInner == null) {
            return null;
        }
        InvokeDynamicExpr typeSwitch = (InvokeDynamicExpr) typeSwitchInner.getSelector();
        if (typeSwitch.getArguments().isEmpty()) {
            return null;
        }
        Expression selector = typeSwitch.getArguments().get(0);
        List<String> caseTypeNames = typeSwitch.getBootstrapClassArgs();

        // typeSwitch result index -> target state; inner default -> default state
        Map<Integer, Integer> resultToState = new HashMap<>();
        Integer defaultState = null;
        for (SwitchCase c : typeSwitchInner.getCases()) {
            Integer target = pcTarget(c.statements());
            if (target == null) {
                return null;
            }
            if (c.isDefault()) {
                defaultState = target;
            } else {
                for (Integer label : c.labels()) {
                    resultToState.put(label, target);
                }
            }
        }
        if (defaultState == null) {
            return null;
        }

        // Determine the merge state and the result variable (return form only).
        // Every arm ends with `$pc$ = M`; M's body is `return resultVar`.
        Set<String> deadVars = new HashSet<>();
        deadVars.add("$pc$");
        // typeSwitch restart-index arg (2nd dynamic arg), if a simple local.
        if (typeSwitch.getArguments().size() > 1 && typeSwitch.getArguments().get(1) instanceof VarRefExpr) {
            deadVars.add(((VarRefExpr) typeSwitch.getArguments().get(1)).getName());
        }

        List<SwitchExpr.Arm> arms = new ArrayList<>();
        Integer mergeState = null;
        String resultVar = null;

        for (int k = 0; k < caseTypeNames.size(); k++) {
            Integer stateNum = resultToState.get(k);
            if (stateNum == null || !states.containsKey(stateNum)) {
                return null;
            }
            ArmInfo arm = analyzeArm(states.get(stateNum), selector, states, dispatchState);
            if (arm == null) {
                return null;
            }
            mergeState = checkMerge(mergeState, arm.mergeState);
            resultVar = checkSame(resultVar, arm.resultVar);
            if (mergeState == null || resultVar == null) {
                return null;
            }
            SourceType type = arm.bindingType != null ? arm.bindingType
                    : new ReferenceSourceType(caseTypeNames.get(k), Collections.emptyList());
            if (arm.binding != null) {
                deadVars.add(arm.binding);
            }
            arms.add(new SwitchExpr.Arm(new ArrayList<>(), false, type,
                    arm.binding != null ? arm.binding : synthBinding(type), null, arm.guard, arm.result));
        }

        // default arm
        if (!states.containsKey(defaultState)) {
            return null;
        }
        ArmInfo def = analyzeArm(states.get(defaultState), selector, states, dispatchState);
        if (def == null) {
            return null;
        }
        mergeState = checkMerge(mergeState, def.mergeState);
        resultVar = checkSame(resultVar, def.resultVar);
        if (mergeState == null || resultVar == null) {
            return null;
        }
        arms.add(new SwitchExpr.Arm(new ArrayList<>(), true, def.result));

        // merge state must be `return resultVar`
        List<Statement> mergeBody = states.get(mergeState);
        if (mergeBody == null || mergeBody.size() != 1 || !(mergeBody.get(0) instanceof ReturnStmt)) {
            return null;
        }
        Expression ret = ((ReturnStmt) mergeBody.get(0)).getValue();
        if (!isVarRef(ret, resultVar)) {
            return null;
        }
        deadVars.add(resultVar);

        SourceType resultType = arms.get(0).getResult() == null
                ? null : arms.get(0).getResult().getType();
        SwitchExpr switchExpr = new SwitchExpr(selector, arms, resultType);
        return new Folded(new ReturnStmt(switchExpr), deadVars);
    }

    /** True for a {@code { throw new MatchException(...); }} body — the synthetic default of an exhaustive switch. */
    private boolean isMatchExceptionThrow(List<Statement> stmts) {
        if (stmts.size() != 1 || !(stmts.get(0) instanceof ThrowStmt)) {
            return false;
        }
        Expression ex = ((ThrowStmt) stmts.get(0)).getException();
        if (!(ex instanceof NewExpr)) {
            return false;
        }
        String cn = ((NewExpr) ex).getClassName();
        return cn != null && (cn.endsWith("/MatchException") || cn.equals("MatchException")
                || cn.endsWith("$MatchException"));
    }

    private boolean tryFoldStructured(List<Statement> stmts, int index, SwitchStmt sw) {
        InvokeDynamicExpr typeSwitch = (InvokeDynamicExpr) sw.getSelector();
        if (typeSwitch.getArguments().isEmpty()) {
            return false;
        }
        Expression selector = typeSwitch.getArguments().get(0);
        List<String> caseTypeNames = typeSwitch.getBootstrapClassArgs();

        Map<Integer, SwitchCase> byLabel = new HashMap<>();
        SwitchCase defaultCase = null;
        for (SwitchCase c : sw.getCases()) {
            if (c.isDefault()) {
                defaultCase = c;
            } else {
                for (Integer l : c.labels()) {
                    byLabel.put(l, c);
                }
            }
        }
        if (defaultCase == null) {
            return false;
        }

        List<SwitchExpr.Arm> arms = new ArrayList<>();
        Set<String> bindings = new HashSet<>();
        String resultVar = null;
        boolean returnForm = false;
        for (int k = 0; k <= caseTypeNames.size(); k++) {
            boolean isDefault = k == caseTypeNames.size();
            SwitchCase c = isDefault ? defaultCase : byLabel.get(k);
            if (c == null) {
                return false;
            }
            if (isDefault && isMatchExceptionThrow(c.statements())) {
                continue; // exhaustive (sealed) switch: the synthetic MatchException default has no source arm
            }
            ArmInfo a = analyzeStructuredArm(c.statements(), selector);
            if (a == null) {
                return false;
            }
            returnForm |= a.isReturn;
            if (a.resultVar != null) {
                resultVar = checkSame(resultVar, a.resultVar);
                if (resultVar == null) {
                    return false; // inconsistent result variables across arms
                }
            }
            if (isDefault) {
                arms.add(new SwitchExpr.Arm(new ArrayList<>(), true, a.result));
            } else if (a.isDeconstruction) {
                SourceType type = a.bindingType != null ? a.bindingType
                        : new ReferenceSourceType(caseTypeNames.get(k), Collections.emptyList());
                bindings.add(a.deconstructTemp);
                for (SwitchExpr.Component comp : a.components) {
                    bindings.add(comp.getBinding());
                }
                arms.add(new SwitchExpr.Arm(new ArrayList<>(), false, type, null,
                        new ArrayList<>(a.components), a.result));
            } else {
                SourceType type = a.bindingType != null ? a.bindingType
                        : new ReferenceSourceType(caseTypeNames.get(k), Collections.emptyList());
                if (a.binding != null) {
                    bindings.add(a.binding);
                }
                arms.add(new SwitchExpr.Arm(new ArrayList<>(), false, type,
                        a.binding != null ? a.binding : synthBinding(type), a.result));
            }
        }
        if (!returnForm && resultVar == null) {
            return false; // assignment form needs a result variable
        }

        SourceType resultType = arms.get(0).getResult() == null ? null : arms.get(0).getResult().getType();
        SwitchExpr switchExpr = new SwitchExpr(selector, arms, resultType);

        if (returnForm) {
            ReturnStmt returnStmt = new ReturnStmt(switchExpr);
            Locations.copy(stmts.get(index), returnStmt);
            stmts.set(index, returnStmt);
            if (resultVar != null) {
                // The trailing `return resultVar` is now unreachable; drop it and the result decl.
                if (index + 1 < stmts.size() && isReturnOfVar(stmts.get(index + 1), resultVar)) {
                    stmts.remove(index + 1);
                }
                bindings.add(resultVar);
            }
            removeDeadDeclsByName(stmts, bindings);
            return true;
        }

        // Assignment form: fold a preceding `T resultVar = <default>;` decl into the initializer.
        for (int j = index - 1; j >= 0; j--) {
            if (stmts.get(j) instanceof VarDeclStmt && resultVar.equals(((VarDeclStmt) stmts.get(j)).getName())) {
                VarDeclStmt decl = (VarDeclStmt) stmts.get(j);
                VarDeclStmt foldedDecl = new VarDeclStmt(decl.getType(), resultVar, switchExpr);
                Locations.copy(decl, foldedDecl);
                stmts.set(j, foldedDecl);
                stmts.remove(index);
                removeDeadDeclsByName(stmts, bindings);
                return true;
            }
        }
        ExprStmt assignStmt = new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
                new VarRefExpr(resultVar, resultType), switchExpr, resultType));
        Locations.copy(stmts.get(index), assignStmt);
        stmts.set(index, assignStmt);
        removeDeadDeclsByName(stmts, bindings);
        return true;
    }

    private static boolean isReturnOfVar(Statement s, String name) {
        return s instanceof ReturnStmt && ((ReturnStmt) s).getValue() instanceof VarRefExpr
                && name.equals(((VarRefExpr) ((ReturnStmt) s).getValue()).getName());
    }

    /**
     * An arm body {@code [binding = (T) selector;] resultVar = expr;} terminated by either a
     * {@code break} (assignment form) or {@code return resultVar} / {@code return expr} (return form).
     */
    private ArmInfo analyzeStructuredArm(List<Statement> body, Expression selector) {
        ArmInfo info = new ArmInfo();
        for (Statement s : body) {
            if (s instanceof BreakStmt) {
                continue;
            }
            if (s instanceof ReturnStmt) {
                info.isReturn = true;
                Expression rv = ((ReturnStmt) s).getValue();
                // `return expr` directly (no separate result assignment)
                if (info.result == null && rv != null && !(rv instanceof VarRefExpr)) {
                    info.result = rv;
                }
                continue;
            }
            // A record deconstruction's component bound by an inline declaration: `T b = temp.comp();`
            if (s instanceof VarDeclStmt) {
                VarDeclStmt decl = (VarDeclStmt) s;
                if (info.isDeconstruction && decl.hasInitializer()
                        && isAccessorCall(decl.getInitializer(), info.deconstructTemp)) {
                    info.components.add(new SwitchExpr.Component(decl.getType(), decl.getName()));
                    continue;
                }
                return null;
            }
            if (!(s instanceof ExprStmt) || !(((ExprStmt) s).getExpression() instanceof BinaryExpr)) {
                return null;
            }
            BinaryExpr assign = (BinaryExpr) ((ExprStmt) s).getExpression();
            if (assign.getOperator() != BinaryOperator.ASSIGN || !(assign.getLeft() instanceof VarRefExpr)) {
                return null;
            }
            String lhs = ((VarRefExpr) assign.getLeft()).getName();
            Expression rhs = assign.getRight();
            if (rhs instanceof CastExpr && sameExpr(((CastExpr) rhs).getExpression(), selector)) {
                CastExpr cast = (CastExpr) rhs;
                info.bindingType = cast.getType();
                if (cast.isRecordDeconstruction()) {
                    info.isDeconstruction = true;
                    info.deconstructTemp = lhs;
                } else {
                    info.binding = lhs;
                }
            } else if (info.isDeconstruction && isAccessorCall(rhs, info.deconstructTemp)) {
                // A component bound by assignment to a pre-declared local: `b = temp.comp();`
                info.components.add(new SwitchExpr.Component(rhs.getType(), lhs));
            } else {
                info.resultVar = lhs;
                info.result = rhs;
            }
        }
        // Return form may have no resultVar (direct `return expr`); assignment form requires one.
        if (info.result == null || (!info.isReturn && info.resultVar == null)) {
            return null;
        }
        if (info.isDeconstruction && info.components.isEmpty()) {
            return null;
        }
        return info;
    }

    /** True when {@code e} is a zero-argument call {@code temp.comp()} on the deconstruction temp. */
    private boolean isAccessorCall(Expression e, String temp) {
        if (temp == null || !(e instanceof MethodCallExpr)) {
            return false;
        }
        MethodCallExpr mc = (MethodCallExpr) e;
        return mc.getReceiver() instanceof VarRefExpr
                && temp.equals(((VarRefExpr) mc.getReceiver()).getName())
                && mc.getArgumentCount() == 0;
    }

    private void removeDeadDeclsByName(List<Statement> stmts, Set<String> names) {
        for (int i = stmts.size() - 1; i >= 0; i--) {
            if (stmts.get(i) instanceof VarDeclStmt && names.contains(((VarDeclStmt) stmts.get(i)).getName())) {
                stmts.remove(i);
            }
        }
    }

    private static final class ArmInfo {
        String binding;
        SourceType bindingType;
        Expression result;
        String resultVar;
        Integer mergeState;
        boolean isReturn;
        boolean isDeconstruction;
        String deconstructTemp;
        Expression guard;
        final List<SwitchExpr.Component> components = new ArrayList<>();
    }

    /**
     * An arm state is {@code [binding = (T) selector;] resultVar = expr; ... $pc$ = M; break}, or a
     * guarded arm {@code binding = (T) selector; if (guard) { $pc$ = P } else { $pc$ = F }} where the
     * pass state {@code P} produces the result and the fail state {@code F} re-dispatches (restart) to
     * {@code dispatchState}. Extracts the optional binding/guard, the result value, and the merge state.
     */
    private ArmInfo analyzeArm(List<Statement> body, Expression selector,
                               Map<Integer, List<Statement>> states, int dispatchState) {
        ArmInfo info = new ArmInfo();
        for (Statement s : body) {
            if (s instanceof BreakStmt) {
                continue;
            }
            if (s instanceof IfStmt) {
                IfStmt iff = (IfStmt) s;
                Integer passState = singlePcTarget(iff.getThenBranch());
                Integer failState = singlePcTarget(iff.getElseBranch());
                if (passState == null || failState == null
                        || !states.containsKey(passState) || !states.containsKey(failState)) {
                    return null;
                }
                // The guard-fail edge must re-dispatch (restart) to the typeSwitch state.
                if (!Integer.valueOf(dispatchState).equals(pcTarget(states.get(failState)))) {
                    return null;
                }
                ArmInfo pass = analyzeArm(states.get(passState), selector, states, dispatchState);
                if (pass == null) {
                    return null;
                }
                info.guard = iff.getCondition();
                info.result = pass.result;
                info.resultVar = pass.resultVar;
                info.mergeState = pass.mergeState;
                continue;
            }
            if (!(s instanceof ExprStmt) || !(((ExprStmt) s).getExpression() instanceof BinaryExpr)) {
                return null;
            }
            BinaryExpr assign = (BinaryExpr) ((ExprStmt) s).getExpression();
            if (assign.getOperator() != BinaryOperator.ASSIGN || !(assign.getLeft() instanceof VarRefExpr)) {
                return null;
            }
            String lhs = ((VarRefExpr) assign.getLeft()).getName();
            Expression rhs = assign.getRight();
            if ("$pc$".equals(lhs)) {
                if (!(rhs instanceof LiteralExpr) || !(((LiteralExpr) rhs).getValue() instanceof Integer)) {
                    return null;
                }
                info.mergeState = (Integer) ((LiteralExpr) rhs).getValue();
            } else if (rhs instanceof CastExpr && sameExpr(((CastExpr) rhs).getExpression(), selector)) {
                CastExpr cast = (CastExpr) rhs;
                // binding: b = (T) selector
                info.binding = lhs;
                info.bindingType = cast.getType();
            } else {
                // result computation: resultVar = expr (last one wins)
                info.resultVar = lhs;
                info.result = rhs;
            }
        }
        if (info.result == null || info.resultVar == null || info.mergeState == null) {
            return null;
        }
        return info;
    }

    /** The single {@code $pc$ = N} target inside a branch (a block or bare statement), or null. */
    private Integer singlePcTarget(Statement branch) {
        if (branch instanceof BlockStmt) {
            return pcTarget(((BlockStmt) branch).getStatements());
        }
        if (branch != null) {
            return pcTarget(Collections.singletonList(branch));
        }
        return null;
    }


    private SwitchStmt findTypeSwitch(List<Statement> stmts) {
        for (Statement s : stmts) {
            if (s instanceof SwitchStmt) {
                SwitchStmt sw = (SwitchStmt) s;
                if (sw.getSelector() instanceof InvokeDynamicExpr
                        && "typeSwitch".equals(((InvokeDynamicExpr) sw.getSelector()).getBootstrapName())) {
                    return sw;
                }
            }
        }
        return null;
    }

    private Integer pcTarget(List<Statement> body) {
        for (Statement s : body) {
            if (s instanceof ExprStmt && ((ExprStmt) s).getExpression() instanceof BinaryExpr) {
                BinaryExpr a = (BinaryExpr) ((ExprStmt) s).getExpression();
                if (a.getOperator() == BinaryOperator.ASSIGN && isVarRef(a.getLeft(), "$pc$")
                        && a.getRight() instanceof LiteralExpr
                        && ((LiteralExpr) a.getRight()).getValue() instanceof Integer) {
                    return (Integer) ((LiteralExpr) a.getRight()).getValue();
                }
            }
        }
        return null;
    }

    private void removeDeadDecls(List<Statement> stmts, int loopIndex, Set<String> deadVars) {
        for (int i = loopIndex - 1; i >= 0; i--) {
            if (stmts.get(i) instanceof VarDeclStmt && deadVars.contains(((VarDeclStmt) stmts.get(i)).getName())) {
                stmts.remove(i);
            }
        }
    }

    private static Integer checkMerge(Integer current, Integer next) {
        if (current == null) return next;
        return current.equals(next) ? current : null;
    }

    private static String checkSame(String current, String next) {
        if (current == null) return next;
        return current.equals(next) ? current : null;
    }

    private static boolean isTrueLiteral(Expression e) {
        return e instanceof LiteralExpr && Boolean.TRUE.equals(((LiteralExpr) e).getValue());
    }

    private static boolean isVarRef(Expression e, String name) {
        return e instanceof VarRefExpr && name.equals(((VarRefExpr) e).getName());
    }

    private static boolean sameExpr(Expression a, Expression b) {
        return a != null && b != null && a.toString().equals(b.toString());
    }

    /**
     * A binding name for a type-pattern arm whose source binding was unused (so no cast survives in
     * bytecode). Java requires a binding identifier for a type pattern, so we synthesize one from the
     * type's simple name (e.g. {@code String -> string}).
     */
    private static String synthBinding(SourceType type) {
        String name = type.toJavaSource();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        int lt = name.indexOf('<');
        if (lt >= 0) {
            name = name.substring(0, lt);
        }
        name = name.replace("[]", "");
        if (name.isEmpty()) {
            return "value";
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
