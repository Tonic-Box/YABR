package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.CastExpr;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.InvokeDynamicExpr;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.MethodCallExpr;
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
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.stmt.WhileStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RecordAttribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Desugars a Java 21 pattern-matching switch EXPRESSION (type-pattern and guarded arms) into the
 * {@code SwitchBootstraps.typeSwitch} invokedynamic + an integer dispatch switch, for the
 * source-to-bytecode front end — the inverse of {@link PatternSwitchReconstructor}.
 *
 * <pre>
 *   T r = switch (sel) { case A a -&gt; e0; case B b when g -&gt; e1; default -&gt; d; };
 *     =&gt;
 *   T r = &lt;default&gt;;
 *   S $psel$ = sel;                       // S = selector's static type
 *   java.util.Objects.requireNonNull($psel$);
 *   int $pidx$ = typeSwitch($psel$, 0);   // indy, bsm static args [A, B]
 *   switch ($pidx$) {                      // (a restart loop when any arm is guarded)
 *     case 0: { A a = (A) $psel$; r = e0; break; }
 *     case 1: { B b = (B) $psel$; if (g) { r = e1; break; } $pidx$ = typeSwitch($psel$, 2); ... }
 *     default: { r = d; break; }
 *   }
 * </pre>
 *
 * Runs before {@link SwitchExpressionDesugar}: it lowers pattern switches fully (the resulting
 * integer switch and casts are handled by the ordinary statement lowering), leaving only constant
 * switch expressions for that pass. Record-deconstruction arms are not yet desugared here.
 */
public class PatternSwitchDesugar implements ASTTransform {

    private int counter = 0;
    private final ClassPool classPool;

    public PatternSwitchDesugar() {
        this(null);
    }

    public PatternSwitchDesugar(ClassPool classPool) {
        this.classPool = classPool;
    }

    @Override
    public String getName() {
        return "PatternSwitchDesugar";
    }

    @Override
    public boolean transform(BlockStmt block) {
        return process(block.getStatements());
    }

    private boolean process(List<Statement> stmts) {
        boolean changed = false;
        for (int i = 0; i < stmts.size(); i++) {
            Statement s = stmts.get(i);
            if (s instanceof VarDeclStmt) {
                VarDeclStmt vd = (VarDeclStmt) s;
                if (isLowerablePatternSwitch(vd.getInitializer())) {
                    SwitchExpr se = (SwitchExpr) vd.getInitializer();
                    List<Statement> repl = new ArrayList<>();
                    repl.add(new VarDeclStmt(vd.getType(), vd.getName(), defaultLiteral(vd.getType())));
                    repl.addAll(desugar(se, vd.getName(), vd.getType()));
                    stmts.remove(i);
                    stmts.addAll(i, repl);
                    i += repl.size() - 1;
                    changed = true;
                    continue;
                }
            }
            if (s instanceof ReturnStmt) {
                ReturnStmt rs = (ReturnStmt) s;
                if (isLowerablePatternSwitch(rs.getValue())) {
                    SwitchExpr se = (SwitchExpr) rs.getValue();
                    SourceType type = se.getType();
                    String tmp = "$pres$" + (counter++);
                    List<Statement> repl = new ArrayList<>();
                    repl.add(new VarDeclStmt(type, tmp, defaultLiteral(type)));
                    repl.addAll(desugar(se, tmp, type));
                    repl.add(new ReturnStmt(new VarRefExpr(tmp, type)));
                    stmts.remove(i);
                    stmts.addAll(i, repl);
                    i += repl.size() - 1;
                    changed = true;
                    continue;
                }
            }
            if (s instanceof ExprStmt && ((ExprStmt) s).getExpression() instanceof BinaryExpr) {
                BinaryExpr assign = (BinaryExpr) ((ExprStmt) s).getExpression();
                if (assign.getOperator() == BinaryOperator.ASSIGN
                        && assign.getLeft() instanceof VarRefExpr
                        && isLowerablePatternSwitch(assign.getRight())) {
                    SwitchExpr se = (SwitchExpr) assign.getRight();
                    VarRefExpr lhs = (VarRefExpr) assign.getLeft();
                    List<Statement> repl = desugar(se, lhs.getName(), lhs.getType());
                    stmts.remove(i);
                    stmts.addAll(i, repl);
                    i += repl.size() - 1;
                    changed = true;
                    continue;
                }
            }
        }
        for (Statement s : stmts) {
            changed |= recurse(s);
        }
        return changed;
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

    /**
     * True for a pattern switch this pass can fully desugar: it has at least one type-pattern arm,
     * and every record-deconstruction arm's record type is resolvable (so its component accessors
     * can be named).
     */
    private boolean isLowerablePatternSwitch(Expression e) {
        if (!(e instanceof SwitchExpr)) {
            return false;
        }
        SwitchExpr se = (SwitchExpr) e;
        boolean anyPattern = false;
        for (SwitchExpr.Arm arm : se.getArms()) {
            if (arm.isRecordDeconstruction()) {
                if (recordAccessorNames(arm.getPatternType()) == null) {
                    return false;
                }
                anyPattern = true;
            } else if (arm.isTypePattern()) {
                anyPattern = true;
            }
        }
        return anyPattern;
    }

    /** The record's component accessor names in declaration order, or null if not resolvable as a record. */
    private List<String> recordAccessorNames(SourceType recordType) {
        if (classPool == null || !(recordType instanceof ReferenceSourceType)) {
            return null;
        }
        ClassFile cf = classPool.get(((ReferenceSourceType) recordType).getInternalName());
        if (cf == null) {
            return null;
        }
        for (Attribute a : cf.getClassAttributes()) {
            if (a instanceof RecordAttribute) {
                List<String> names = new ArrayList<>();
                for (String[] nd : ((RecordAttribute) a).getComponentNameAndDescriptors()) {
                    names.add(nd[0]);
                }
                return names;
            }
        }
        return null;
    }

    private List<Statement> desugar(SwitchExpr se, String target, SourceType targetType) {
        // The selector temp is typed Object: typeSwitch matches against Object, and a uniform Object
        // temp keeps the indy descriptor, the null check, and the per-arm casts mutually consistent.
        SourceType selType = ReferenceSourceType.OBJECT;
        String selInternal = "java/lang/Object";
        String selTemp = "$psel$" + counter;
        String idxTemp = "$pidx$" + (counter++);

        List<SwitchExpr.Arm> patternArms = new ArrayList<>();
        SwitchExpr.Arm defaultArm = null;
        for (SwitchExpr.Arm arm : se.getArms()) {
            if (arm.isDefault()) {
                defaultArm = arm;
            } else if (arm.isTypePattern()) {
                patternArms.add(arm);
            }
        }
        List<String> caseTypeInternals = new ArrayList<>();
        for (SwitchExpr.Arm arm : patternArms) {
            caseTypeInternals.add(internalName(arm.getPatternType()));
        }

        List<Statement> out = new ArrayList<>();
        out.add(new VarDeclStmt(selType, selTemp, se.getSelector()));
        // Null check: a selector switch without a `case null` arm must NPE on null (typeSwitch would
        // otherwise return -1 and fall through to default). A discarded virtual call achieves this
        // without depending on a resolved Objects.requireNonNull descriptor.
        out.add(new ExprStmt(MethodCallExpr.instanceCall(
                new VarRefExpr(selTemp, selType), "getClass", "java/lang/Object",
                new ArrayList<>(), new ReferenceSourceType("java/lang/Class"))));
        out.add(new VarDeclStmt(PrimitiveSourceType.INT, idxTemp,
                typeSwitchIndy(selTemp, selType, selInternal, caseTypeInternals, 0)));

        boolean guarded = false;
        for (SwitchExpr.Arm arm : patternArms) {
            if (arm.getGuard() != null) {
                guarded = true;
                break;
            }
        }

        if (!guarded) {
            out.add(buildDispatchSwitch(patternArms, defaultArm, selTemp, selType, idxTemp,
                    target, targetType, null, selInternal, caseTypeInternals));
        } else {
            String label = "$pswitch$" + counter;
            SwitchStmt sw = buildDispatchSwitch(patternArms, defaultArm, selTemp, selType, idxTemp,
                    target, targetType, label, selInternal, caseTypeInternals);
            WhileStmt loop = new WhileStmt(LiteralExpr.ofBoolean(true),
                    new BlockStmt(new ArrayList<>(List.of((Statement) sw))), label);
            out.add(loop);
        }
        return out;
    }

    /**
     * The integer dispatch switch. When {@code loopLabel} is null (no guards) every arm assigns the
     * result and {@code break}s the switch. Otherwise the switch runs inside a {@code while(true)}
     * labeled {@code loopLabel}: each arm exits via {@code break loopLabel}, and a guard-fail re-runs
     * {@code typeSwitch} with the next restart index and {@code break}s only the inner switch so the
     * loop re-dispatches.
     */
    private SwitchStmt buildDispatchSwitch(List<SwitchExpr.Arm> patternArms, SwitchExpr.Arm defaultArm,
                                           String selTemp, SourceType selType, String idxTemp,
                                           String target, SourceType targetType, String loopLabel,
                                           String selInternal, List<String> caseTypeInternals) {
        List<SwitchCase> cases = new ArrayList<>();
        for (int k = 0; k < patternArms.size(); k++) {
            SwitchExpr.Arm arm = patternArms.get(k);
            List<Statement> body = new ArrayList<>(bindPattern(arm, k, selTemp, selType));
            if (arm.getGuard() != null) {
                List<Statement> pass = new ArrayList<>();
                pass.add(assign(target, targetType, arm.getResult()));
                pass.add(new BreakStmt(loopLabel));
                body.add(new IfStmt(arm.getGuard(), new BlockStmt(pass)));
                body.add(new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
                        new VarRefExpr(idxTemp, PrimitiveSourceType.INT),
                        typeSwitchIndy(selTemp, selType, selInternal, caseTypeInternals, k + 1),
                        PrimitiveSourceType.INT)));
                body.add(new BreakStmt());
            } else {
                body.add(assign(target, targetType, arm.getResult()));
                body.add(new BreakStmt(loopLabel));
            }
            cases.add(SwitchCase.of(k, body));
        }
        List<Statement> defBody = new ArrayList<>();
        defBody.add(assign(target, targetType, defaultArm.getResult()));
        defBody.add(new BreakStmt(loopLabel));
        cases.add(SwitchCase.defaultCase(defBody));
        return new SwitchStmt(new VarRefExpr(idxTemp, PrimitiveSourceType.INT), cases);
    }

    /**
     * The binding statements for one pattern arm: a single cast for a type pattern
     * ({@code T b = (T) sel;}), or a temp cast plus a component-accessor declaration per binding for
     * a record deconstruction ({@code T $pdec = (T) sel; C0 b0 = $pdec.comp0(); ...}).
     */
    private List<Statement> bindPattern(SwitchExpr.Arm arm, int k, String selTemp, SourceType selType) {
        List<Statement> body = new ArrayList<>();
        SourceType recordType = arm.getPatternType();
        if (!arm.isRecordDeconstruction()) {
            body.add(new VarDeclStmt(recordType, arm.getPatternBinding(),
                    new CastExpr(recordType, new VarRefExpr(selTemp, selType))));
            return body;
        }
        String decTemp = selTemp + "_d" + k;
        body.add(new VarDeclStmt(recordType, decTemp,
                new CastExpr(recordType, new VarRefExpr(selTemp, selType))));
        List<String> accessors = recordAccessorNames(recordType);
        String recordInternal = internalName(recordType);
        List<SwitchExpr.Component> comps = arm.getDeconstructionComponents();
        for (int c = 0; c < comps.size(); c++) {
            SwitchExpr.Component comp = comps.get(c);
            body.add(new VarDeclStmt(comp.getType(), comp.getBinding(),
                    MethodCallExpr.instanceCall(new VarRefExpr(decTemp, recordType),
                            accessors.get(c), recordInternal, new ArrayList<>(), comp.getType())));
        }
        return body;
    }

    private static Statement assign(String target, SourceType targetType, Expression value) {
        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
                new VarRefExpr(target, targetType), value, targetType));
    }

    private InvokeDynamicExpr typeSwitchIndy(String selTemp, SourceType selType, String selInternal,
                                             List<String> caseTypeInternals, int restartIndex) {
        InvokeDynamicExpr indy = new InvokeDynamicExpr(
                "typeSwitch",
                "(L" + selInternal + ";I)I",
                Arrays.asList(new VarRefExpr(selTemp, selType), LiteralExpr.ofInt(restartIndex)),
                "java/lang/runtime/SwitchBootstraps", "typeSwitch", PrimitiveSourceType.INT);
        indy.setBootstrapClassArgs(new ArrayList<>(caseTypeInternals));
        return indy;
    }

    private static String internalName(SourceType type) {
        if (type instanceof ReferenceSourceType) {
            return ((ReferenceSourceType) type).getInternalName();
        }
        return "java/lang/Object";
    }

    /** A type-appropriate default so the target local is definitely-assigned. */
    private static Expression defaultLiteral(SourceType type) {
        if (type instanceof PrimitiveSourceType) {
            PrimitiveSourceType p = (PrimitiveSourceType) type;
            if (p == PrimitiveSourceType.BOOLEAN) return LiteralExpr.ofBoolean(false);
            if (p == PrimitiveSourceType.LONG) return LiteralExpr.ofLong(0L);
            if (p == PrimitiveSourceType.FLOAT) return LiteralExpr.ofFloat(0.0f);
            if (p == PrimitiveSourceType.DOUBLE) return LiteralExpr.ofDouble(0.0);
            return LiteralExpr.ofInt(0);
        }
        return LiteralExpr.ofNull();
    }
}
