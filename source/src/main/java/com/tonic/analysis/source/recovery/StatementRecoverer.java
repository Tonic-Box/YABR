package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.Locations;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.source.recovery.StructuralAnalyzer.RegionInfo;
import com.tonic.analysis.source.recovery.rcs.ReachingConditionStructurer;
import com.tonic.analysis.source.recovery.rcs.RegionRecoveryBridge;
import com.tonic.analysis.source.recovery.rcs.SwitchDescriptor;
import com.tonic.analysis.source.recovery.rcs.TryNodeDescriptor;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.EdgeType;
import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.CompareOp;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.NullConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.StringConstant;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.util.Logger;
import java.util.*;

/**
 * Recovers Statement AST nodes from IR blocks, offering each region to the reaching-condition
 * structurer first and falling back to the legacy walk for shapes it declines.
 */
public class StatementRecoverer implements RegionRecoveryBridge
{

    private final ControlFlowContext context;
    private final StructuralAnalyzer analyzer;
    private final ExpressionRecoverer exprRecoverer;
    private final TypeRecoverer typeRecoverer;

    /**
     * The reaching-condition structurer.
     */
    private final ReachingConditionStructurer rcsStructurer;

    /**
     * The pool enum constants resolve from when a switch dispatches on {@code ordinal()} directly.
     */
    private ClassPool enumClassPool;

    /**
     * Sets the pool enum constants resolve from for switches that dispatch on ordinal() directly.
     *
     * @param pool the class pool, or null to resolve nothing
     */
    public void setEnumClassPool(ClassPool pool)
    {
        this.enumClassPool = pool;
    }

    /**
     * Creates a recoverer and pre-declares the method parameters so stores to them recover as
     * assignments rather than declarations.
     *
     * @param context the per-method recovery state
     * @param analyzer supplies the structural analysis of the control flow
     * @param exprRecoverer recovers the expressions inside the statements
     */
    public StatementRecoverer(ControlFlowContext context, StructuralAnalyzer analyzer, ExpressionRecoverer exprRecoverer)
    {
        this.context = context;
        this.analyzer = analyzer;
        this.exprRecoverer = exprRecoverer;
        this.typeRecoverer = new TypeRecoverer();
        this.rcsStructurer = new ReachingConditionStructurer(this, context);

        // Pre-declare parameters so stores to them become assignments, not declarations
        preDeclareParameters();
    }

    @Override
    public void markRegionBlockProcessed(IRBlock block, List<Statement> statements)
    {
        context.setStatements(block, statements);
        context.markProcessed(block);
    }

    @Override
    public List<Statement> processedReturnStatements(IRBlock block)
    {
        if (!context.isProcessed(block) || !isReturnBlock(block))
        {
            return Collections.emptyList();
        }
        return context.getStatements(block);
    }

    @Override
    public boolean isRegionBlockProcessed(IRBlock block)
    {
        return context.isProcessed(block);
    }

    @Override
    public boolean tryCollapseTernaryDiamond(IRBlock branch)
    {
        IRInstruction term = branch.getTerminator();
        if (!(term instanceof BranchInstruction))
        {
            return false;
        }
        BranchInstruction br = (BranchInstruction) term;
        IRBlock thenBlock = br.getTrueTarget();
        IRBlock elseBlock = br.getFalseTarget();
        if (thenBlock == null || elseBlock == null || thenBlock == elseBlock)
        {
            return false;
        }
        // Both arms must be reached only from this branch and flow to one common merge block.
        if (!isSolePredecessor(branch, thenBlock) || !isSolePredecessor(branch, elseBlock))
        {
            return false;
        }
        IRBlock merge = soleSuccessor(thenBlock);
        if (merge == null || merge != soleSuccessor(elseBlock))
        {
            return false;
        }
        PhiInstruction ternaryPhi = findTernaryPhi(thenBlock, elseBlock, merge);
        if (ternaryPhi == null)
        {
            return false;
        }
        // Only collapse a stack phi consumed directly by an expression (e.g. a call argument or a bare return
        // of the phi). If an arm stores its value into a local slot, the value flows through a variable that the
        // merge reads back: the arms structure into an `if/else` the AST pipeline folds to a ternary
        // (tryConvertIfElseToAssignment). Collapsing here would cache the ternary for the phi while the merge
        // reads the local, orphaning the arms' work and forcing the whole method into a dispatch loop. A phi
        // feeding another phi (a nested merge) likewise cannot be consumed here.
        if (armStoresToLocal(thenBlock) || armStoresToLocal(elseBlock)
                || hasStoreLocalUse(ternaryPhi.getResult()) || getPhiUsingValue(ternaryPhi.getResult()) != null)
        {
            return false;
        }
        Expression condition = recoverCondition(branch, false);
        collapseToTernaryPhiExpression(condition, ternaryPhi, thenBlock, elseBlock);
        context.markProcessed(thenBlock);
        context.markProcessed(elseBlock);
        return true;
    }

    /**
     * True when {@code block} stores a value into a local slot - the diamond value flows through a variable.
     */
    private boolean armStoresToLocal(IRBlock block)
    {
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof StoreLocalInstruction)
            {
                return true;
            }
        }
        return false;
    }

    private boolean isSolePredecessor(IRBlock pred, IRBlock block)
    {
        Set<IRBlock> preds = block.getPredecessors();
        return preds.size() == 1 && preds.contains(pred);
    }

    private IRBlock soleSuccessor(IRBlock block)
    {
        Set<IRBlock> succs = block.getSuccessors();
        return succs.size() == 1 ? succs.iterator().next() : null;
    }





    @Override
    public SwitchDescriptor decodeSwitch(IRBlock switchBlock)
    {
        if (!(switchBlock.getTerminator() instanceof SwitchInstruction))
        {
            return null;
        }
        RegionInfo info = analyzer.getRegionInfo(switchBlock);
        if (info == null || info.getType() != ControlFlowContext.StructuredRegion.SWITCH)
        {
            return null;
        }
        SwitchInstruction sw = (SwitchInstruction) switchBlock.getTerminator();
        Value key = sw.getKey();
        StringSwitchInfo stringInfo = detectStringSwitch(switchBlock);
        if (stringInfo != null)
        {
            return decodeStringSwitchDescriptor(switchBlock, stringInfo);
        }

        Expression selector = exprRecoverer.recoverOperand(key);
        EnumSwitchInfo enumInfo = detectEnumSwitchPattern(selector);
        boolean enumNamesResolved = false;
        if (enumInfo != null)
        {
            if (enumInfo.enumClassName != null && allEnumCasesResolve(info, enumInfo))
            {
                selector = enumInfo.enumVariable;
                enumNamesResolved = true;
            }
            else if (enumInfo.rawOrdinals)
            {
                selector = enumInfo.ordinalExpression;
            }
            // A $SwitchMap$ dispatch whose mapping did not resolve keeps the array access: the case keys
            // are MAP VALUES, not ordinals, so rewriting the selector to `x.ordinal()` silently redirects
            // every case to the wrong constant.
        }

        IRBlock mergeBlock = findSwitchMerge(info);

        Map<IRBlock, List<Integer>> targetToCases = new LinkedHashMap<>();
        for (Map.Entry<Integer, IRBlock> entry : info.getSwitchCases().entrySet())
        {
            targetToCases.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        Set<IRBlock> caseHeaders = new HashSet<>(targetToCases.keySet());
        IRBlock defaultTarget = info.getDefaultTarget();
        boolean emptyDefault = defaultTarget != null && defaultTarget == mergeBlock;
        if (defaultTarget != null && !emptyDefault)
        {
            caseHeaders.add(defaultTarget);
        }
        if (mergeBlock != null)
        {
            caseHeaders.remove(mergeBlock);
        }



        // Cases print in LAYOUT order - the body offsets both javac and the re-lowerer carry over
        // from source - not key order: an enum switch's $SwitchMap keys follow source order on the
        // javac layout but raw ordinals dispatch in declaration order, so key order flips the
        // printed cases between the two. Fall-through adjacency also follows layout order. The sort
        // is stable, so synthesized offset-less targets keep their key order.
        List<Map.Entry<IRBlock, List<Integer>>> orderedTargets = new ArrayList<>(targetToCases.entrySet());
        orderedTargets.sort(java.util.Comparator.comparingInt(e -> {
            int off = e.getKey().getBytecodeOffset();
            return off >= 0 ? off : Integer.MAX_VALUE;
        }));
        // A default sharing a value case's target is that case's extra LABEL (`case 6: default:`), not
        // an arm of its own: a second spec for the same header either duplicates the body or is elided
        // by the processed-block dedup, and an elided default relowers as a fall-off edge - which a
        // value-returning method must not have (the synthesized fall-off return does not verify).
        boolean defaultMergedIntoCase = defaultTarget != null && !emptyDefault
                && targetToCases.containsKey(defaultTarget);
        List<SwitchDescriptor.CaseSpec> cases = new ArrayList<>();
        for (Map.Entry<IRBlock, List<Integer>> entry : orderedTargets)
        {
            IRBlock target = entry.getKey();
            List<Integer> labels = entry.getValue();
            boolean alsoDefault = defaultMergedIntoCase && target == defaultTarget;
            if (enumNamesResolved)
            {
                List<Expression> enumLabels = new ArrayList<>();
                for (Integer caseValue : labels)
                {
                    String constantName = enumConstantForCase(enumInfo, caseValue);
                    SourceType enumType = new ReferenceSourceType(enumInfo.enumClassName, Collections.emptyList());
                    enumLabels.add(FieldAccessExpr.staticField(enumInfo.enumClassName, constantName, enumType));
                }
                cases.add(new SwitchDescriptor.CaseSpec(Collections.emptyList(), enumLabels, alsoDefault, target));
            }
            else
            {
                cases.add(new SwitchDescriptor.CaseSpec(new ArrayList<>(labels), Collections.emptyList(), alsoDefault, target));
            }
        }
        if (defaultTarget != null && !defaultMergedIntoCase)
        {
            SwitchDescriptor.CaseSpec defaultCase = new SwitchDescriptor.CaseSpec(
                    Collections.emptyList(), Collections.emptyList(), true, emptyDefault ? null : defaultTarget);
            // Place the default at its layout (bytecode-offset) position among the value cases, not blindly
            // last: a default that FALLS THROUGH to a following case (`case 2: ...; default: ...; case 3: ...`)
            // must sit between the cases it flows from and into, or the fall-through chain is broken and the
            // default's statements are dropped from the intervening path. An empty default (falls to the merge)
            // has no fall-through and stays last.
            if (emptyDefault)
            {
                cases.add(defaultCase);
            }
            else
            {
                int defOffset = defaultTarget.getBytecodeOffset();
                int pos = cases.size();
                for (int i = 0; i < cases.size(); i++)
                {
                    IRBlock target = cases.get(i).header();
                    if (target != null && target.getBytecodeOffset() > defOffset)
                    {
                        pos = i;
                        break;
                    }
                }
                cases.add(pos, defaultCase);
            }
        }

        return new SwitchDescriptor(switchBlock, selector, mergeBlock, cases, caseHeaders);
    }

    /**
     * String-switch scaffolds admitted by {@link #decodeStringSwitchDescriptor}, keyed by the hash-dispatch header.
     */
    private final Map<IRBlock, StringSwitchInfo> stringSwitchScaffolds = new HashMap<>();

    /**
     * Decodes javac's two-phase string switch into a structuring-ready descriptor over the INDEX switch.
     */
    private SwitchDescriptor decodeStringSwitchDescriptor(IRBlock header, StringSwitchInfo info)
    {
        SwitchInstruction indexSwitch = info.indexSwitch;
        IRBlock merge = stringSwitchExit(info);

        Map<Integer, List<String>> indexToLiterals = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : info.literalToIndex.entrySet())
        {
            indexToLiterals.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        Map<IRBlock, List<Expression>> targetToLabels = new LinkedHashMap<>();
        for (Map.Entry<Integer, IRBlock> entry : indexSwitch.getCases().entrySet())
        {
            List<String> literals = indexToLiterals.get(entry.getKey());
            if (literals == null)
            {
                continue;
            }
            List<Expression> labels = targetToLabels.computeIfAbsent(entry.getKey() == null ? null : entry.getValue(),
                    k -> new ArrayList<>());
            for (String literal : literals)
            {
                labels.add(LiteralExpr.ofString(literal));
            }
        }
        if (targetToLabels.isEmpty())
        {
            return null;
        }

        Expression selector = exprRecoverer.recoverOperand(info.stringValue);
        IRBlock defaultTarget = indexSwitch.getDefaultTarget();
        boolean emptyDefault = defaultTarget != null && defaultTarget == merge;

        Set<IRBlock> caseHeaders = new HashSet<>(targetToLabels.keySet());
        if (defaultTarget != null && !emptyDefault)
        {
            caseHeaders.add(defaultTarget);
        }
        if (merge != null)
        {
            caseHeaders.remove(merge);
        }

        List<SwitchDescriptor.CaseSpec> cases = new ArrayList<>();
        for (Map.Entry<IRBlock, List<Expression>> entry : targetToLabels.entrySet())
        {
            cases.add(new SwitchDescriptor.CaseSpec(Collections.emptyList(), entry.getValue(), false, entry.getKey()));
        }
        if (defaultTarget != null)
        {
            cases.add(new SwitchDescriptor.CaseSpec(Collections.emptyList(), Collections.emptyList(), true,
                    emptyDefault ? null : defaultTarget));
        }

        stringSwitchScaffolds.put(header, info);
        return new SwitchDescriptor(header, selector, merge, cases, caseHeaders, true);
    }

    @Override
    public List<Statement> recoverSwitchHeaderStatements(IRBlock header)
    {
        StringSwitchInfo info = stringSwitchScaffolds.get(header);
        if (info == null)
        {
            return recoverSimpleBlock(header);
        }
        // User code before the dispatch scaffolding (the selector's own store and anything above it);
        // the scaffolding proper starts at the hashCode call. Mirrors the walk's string recovery.
        List<Statement> lead = new ArrayList<>();
        for (IRInstruction instr : header.getInstructions())
        {
            if (instr.isTerminator())
            {
                break;
            }
            if (instr instanceof InvokeInstruction && "hashCode".equals(((InvokeInstruction) instr).getName()))
            {
                break;
            }
            if (context.shouldSkipInstruction(instr))
            {
                continue;
            }
            Statement stmt = recoverInstruction(instr);
            if (stmt != null)
            {
                lead.add(stmt);
            }
        }
        for (IRBlock block : info.scaffolding)
        {
            context.markProcessed(block);
            context.setStatements(block, Collections.emptyList());
        }
        return lead;
    }


    /**
     * Pre-declares parameter names so that stores to parameter slots
     * generate assignment statements instead of variable declarations.
     */
    private void preDeclareParameters()
    {
        IRMethod method = context.getIrMethod();
        RecoveryContext ctx = context.getExpressionContext();
        List<SSAValue> params = method.getParameters();
        // Skip the receiver (slot 0 of an instance method); declare each parameter under the name it was
        // actually given - its real LocalVariableTable name when present, else the synthetic "argN" - so a
        // store back to a parameter slot recovers as an assignment, never a self-copy declaration.
        int start = method.isStatic() ? 0 : 1;
        for (int i = start; i < params.size(); i++)
        {
            String paramName = ctx.getVariableName(params.get(i));
            if (paramName != null)
            {
                ctx.markDeclared(paramName);
            }
        }
    }

    private void registerPendingNewInstructions(IRMethod method)
    {
        for (IRBlock block : method.getBlocks())
        {
            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof NewInstruction)
                {
                    NewInstruction newInstr = (NewInstruction) instr;
                    if (newInstr.getResult() != null)
                    {
                        context.getExpressionContext().registerPendingNew(
                            newInstr.getResult(), newInstr.getClassName());
                    }
                }
            }
        }
    }

    /**
     * Recovers the whole method body, routing through try-with-resources or general exception
     * handling when the method declares handlers.
     *
     * @return the recovered body, or an empty block when the method has no entry
     */
    public BlockStmt recoverMethod()
    {
        IRMethod method = context.getIrMethod();
        IRBlock entry = method.getEntryBlock();

        if (entry == null)
        {
            return new BlockStmt(Collections.emptyList());
        }

        List<Statement> statements = new ArrayList<>();

        detectSelfStorePhis(method);

        collectForLoopInitInstructions();

        registerPendingNewInstructions(method);
        emitPhiDeclarations(method, statements);
        // The declared baseline for full re-passes: parameters plus the phi declarations above. A re-pass
        // resets to this point so block-level declarations from a discarded attempt become re-declarable.
        context.getExpressionContext().baselineDeclaredVariables();
        splitClobberedIncrementReads(method);

        List<ExceptionHandler> handlers = method.getExceptionHandlers();
        if (handlers != null && !handlers.isEmpty())
        {
            List<Statement> twr = recoverTryWithResources(entry, handlers);
            statements.addAll(Objects.requireNonNullElseGet(twr, () -> recoverWithExceptionHandling(entry, handlers)));
        }
        else
        {
            statements.addAll(recoverBlockSequence(entry, new HashSet<>()));
        }

        removeInlineFinallyDuplicates(statements);
        removeOrphanFinallyRethrows(statements);

        return new BlockStmt(statements);
    }

    private void removeOrphanFinallyRethrows(List<Statement> statements)
    {
        Set<String> declaredVars = collectDeclaredVariables(statements);
        removeOrphanThrowsRecursive(statements, declaredVars);
    }

    private void removeOrphanThrowsRecursive(List<Statement> statements, Set<String> declaredVars)
    {
        statements.removeIf(stmt -> isOrphanThrow(stmt, declaredVars));
        for (Statement stmt : statements)
        {
            cleanupOrphanThrowsInStatement(stmt, declaredVars);
        }
    }

    private boolean isOrphanThrow(Statement stmt, Set<String> declaredVars)
    {
        if (stmt instanceof ThrowStmt)
        {
            ThrowStmt throwStmt = (ThrowStmt) stmt;
            Expression exception = throwStmt.getException();
            if (exception instanceof VarRefExpr)
            {
                String varName = ((VarRefExpr) exception).getName();
                return !declaredVars.contains(varName);
            }
        }
        return false;
    }

    private void cleanupOrphanThrowsInStatement(Statement stmt, Set<String> declaredVars)
    {
        if (stmt instanceof BlockStmt)
        {
            BlockStmt block = (BlockStmt) stmt;
            removeOrphanThrowsRecursive(block.getStatements(), declaredVars);
        }
        else if (stmt instanceof TryCatchStmt)
        {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            if (tryCatch.getTryBlock() instanceof BlockStmt)
            {
                BlockStmt tryBlock = (BlockStmt) tryCatch.getTryBlock();
                removeOrphanThrowsRecursive(tryBlock.getStatements(), declaredVars);
            }
            for (CatchClause clause : tryCatch.getCatches())
            {
                Set<String> catchVars = new HashSet<>(declaredVars);
                catchVars.add(clause.variableName());
                if (clause.body() instanceof BlockStmt)
                {
                    removeOrphanThrowsRecursive(((BlockStmt) clause.body()).getStatements(), catchVars);
                }
            }
            if (tryCatch.getFinallyBlock() instanceof BlockStmt)
            {
                removeOrphanThrowsRecursive(((BlockStmt) tryCatch.getFinallyBlock()).getStatements(), declaredVars);
            }
        }
        else if (stmt instanceof IfStmt)
        {
            IfStmt ifStmt = (IfStmt) stmt;
            cleanupOrphanThrowsInStatement(ifStmt.getThenBranch(), declaredVars);
            if (ifStmt.getElseBranch() != null)
            {
                cleanupOrphanThrowsInStatement(ifStmt.getElseBranch(), declaredVars);
            }
        }
        else if (stmt instanceof WhileStmt)
        {
            cleanupOrphanThrowsInStatement(((WhileStmt) stmt).getBody(), declaredVars);
        }
        else if (stmt instanceof DoWhileStmt)
        {
            cleanupOrphanThrowsInStatement(((DoWhileStmt) stmt).getBody(), declaredVars);
        }
        else if (stmt instanceof ForStmt)
        {
            cleanupOrphanThrowsInStatement(((ForStmt) stmt).getBody(), declaredVars);
        }
    }

    private Set<String> collectDeclaredVariables(List<Statement> statements)
    {
        Set<String> declared = new HashSet<>();
        for (Statement stmt : statements)
        {
            collectDeclaredVarsRecursive(stmt, declared);
        }
        return declared;
    }

    private void collectDeclaredVarsRecursive(Statement stmt, Set<String> declared)
    {
        if (stmt instanceof VarDeclStmt)
        {
            declared.add(((VarDeclStmt) stmt).getName());
        }
        else if (stmt instanceof BlockStmt)
        {
            for (Statement s : ((BlockStmt) stmt).getStatements())
            {
                collectDeclaredVarsRecursive(s, declared);
            }
        }
        else if (stmt instanceof TryCatchStmt)
        {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            collectDeclaredVarsRecursive(tryCatch.getTryBlock(), declared);
            for (CatchClause clause : tryCatch.getCatches())
            {
                declared.add(clause.variableName());
                collectDeclaredVarsRecursive(clause.body(), declared);
            }
            if (tryCatch.getFinallyBlock() != null)
            {
                collectDeclaredVarsRecursive(tryCatch.getFinallyBlock(), declared);
            }
        }
        else if (stmt instanceof IfStmt)
        {
            IfStmt ifStmt = (IfStmt) stmt;
            collectDeclaredVarsRecursive(ifStmt.getThenBranch(), declared);
            if (ifStmt.getElseBranch() != null)
            {
                collectDeclaredVarsRecursive(ifStmt.getElseBranch(), declared);
            }
        }
        else if (stmt instanceof WhileStmt)
        {
            collectDeclaredVarsRecursive(((WhileStmt) stmt).getBody(), declared);
        }
        else if (stmt instanceof DoWhileStmt)
        {
            collectDeclaredVarsRecursive(((DoWhileStmt) stmt).getBody(), declared);
        }
        else if (stmt instanceof ForStmt)
        {
            ForStmt forStmt = (ForStmt) stmt;
            for (Statement initStmt : forStmt.getInit())
            {
                collectDeclaredVarsRecursive(initStmt, declared);
            }
            collectDeclaredVarsRecursive(forStmt.getBody(), declared);
        }
    }

    /**
     * Removes duplicate inline finally code that appears after try-catch-finally statements.
     */
    private void removeInlineFinallyDuplicates(List<Statement> statements)
    {
        for (int i = 0; i < statements.size(); i++)
        {
            Statement stmt = statements.get(i);
            if (stmt instanceof TryCatchStmt)
            {
                TryCatchStmt tryCatch = (TryCatchStmt) stmt;
                if (tryCatch.hasFinally() && tryCatch.getFinallyBlock() instanceof BlockStmt)
                {
                    List<Statement> finallyStmts = ((BlockStmt) tryCatch.getFinallyBlock()).getStatements();
                    if (!finallyStmts.isEmpty())
                    {
                        int removed = removeMatchingStatements(statements, i + 1, finallyStmts);
                        i -= removed;
                    }
                }
            }
        }
    }

    /**
     * Removes statements from the list that match the given pattern statements.
     */
    private int removeMatchingStatements(List<Statement> statements, int startIndex, List<Statement> pattern)
    {
        if (startIndex >= statements.size() || pattern.isEmpty())
        {
            return 0;
        }

        int matchCount = 0;
        for (int i = 0; i < pattern.size() && startIndex + i < statements.size(); i++)
        {
            Statement actual = statements.get(startIndex + i);
            Statement expected = pattern.get(i);
            if (statementsMatch(actual, expected))
            {
                matchCount++;
            }
            else
            {
                break;
            }
        }

        if (matchCount == pattern.size())
        {
            for (int i = 0; i < matchCount; i++)
            {
                statements.remove(startIndex);
            }
            return matchCount;
        }
        return 0;
    }

    /**
     * Checks if two statements are semantically equivalent for finally duplicate detection.
     */
    private boolean statementsMatch(Statement a, Statement b)
    {
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;

        if (a instanceof ExprStmt && b instanceof ExprStmt)
        {
            return expressionsMatch(((ExprStmt) a).getExpression(), ((ExprStmt) b).getExpression());
        }
        if (a instanceof VarDeclStmt && b instanceof VarDeclStmt)
        {
            VarDeclStmt va = (VarDeclStmt) a;
            VarDeclStmt vb = (VarDeclStmt) b;
            if (va.getInitializer() == null && vb.getInitializer() == null) return true;
            if (va.getInitializer() == null || vb.getInitializer() == null) return false;
            return expressionsMatch(va.getInitializer(), vb.getInitializer());
        }
        if (a instanceof ReturnStmt && b instanceof ReturnStmt)
        {
            Expression ea = ((ReturnStmt) a).getValue();
            Expression eb = ((ReturnStmt) b).getValue();
            if (ea == null && eb == null) return true;
            if (ea == null || eb == null) return false;
            return expressionsMatch(ea, eb);
        }

        return a.toString().equals(b.toString());
    }

    /**
     * Checks if two expressions are semantically equivalent for finally duplicate detection.
     */
    private boolean expressionsMatch(Expression a, Expression b)
    {
        if (a == null || b == null) return a == b;
        if (a.getClass() != b.getClass()) return false;

        if (a instanceof BinaryExpr && b instanceof BinaryExpr)
        {
            BinaryExpr ba = (BinaryExpr) a;
            BinaryExpr bb = (BinaryExpr) b;
            return ba.getOperator() == bb.getOperator()
                && expressionsMatch(ba.getLeft(), bb.getLeft())
                && expressionsMatch(ba.getRight(), bb.getRight());
        }
        if (a instanceof VarRefExpr && b instanceof VarRefExpr)
        {
            return ((VarRefExpr) a).getName().equals(((VarRefExpr) b).getName());
        }
        if (a instanceof LiteralExpr && b instanceof LiteralExpr)
        {
            Object va = ((LiteralExpr) a).getValue();
            Object vb = ((LiteralExpr) b).getValue();
            return Objects.equals(va, vb);
        }

        return a.toString().equals(b.toString());
    }

    /**
     * Recovers statements with exception handling structure.
     */
    private List<Statement> recoverWithExceptionHandling(IRBlock entry, List<ExceptionHandler> handlers)
    {
        List<Statement> result = new ArrayList<>();

        Set<IRBlock> handlerBlocks = new HashSet<>();
        for (ExceptionHandler handler : handlers)
        {
            if (handler.getHandlerBlock() != null)
            {
                handlerBlocks.add(handler.getHandlerBlock());
                collectReachableBlocks(handler.getHandlerBlock(), handlerBlocks);
            }
        }

        List<ExceptionHandler> mergedHandlers = mergeHandlersWithSameTarget(handlers);

        ExceptionHandler outerHandler = findOutermostHandler(entry, mergedHandlers);

        if (outerHandler != null)
        {
            // The staging owns every shape it settles; the engine (with try nodes, bounded by the
            // catch-EXCLUSIVE blocks) is its RESCUE when it throws the retired-schema signal - a
            // split protected family strangles the staging's stop set (everything past the FIRST
            // range's end becomes a stop, cutting loop latches out of the continuation region).
            // Offering the engine FIRST instead perturbed settled staging output: its catch-clause
            // attachment differs between the two layouts of the same method. FINALLY-bearing
            // methods keep the loud signal - the scaffolding's copy de-duplication has no engine
            // equivalent.
            try
            {
                result.addAll(recoverOuterHandlerRegion(entry, outerHandler, handlers, mergedHandlers, handlerBlocks));
            }
            catch (RetiredSchemaRecoveryException retired)
            {
                boolean anyFinally = false;
                for (ExceptionHandler fh : handlers)
                {
                    if (handlerRethrows(fh) && !handlerThrowsFreshException(fh) && isFinallyCatchType(fh))
                    {
                        anyFinally = true;
                        break;
                    }
                }
                if (anyFinally)
                {
                    throw retired;
                }
                List<Statement> engine = rcsStructurer.tryStructureRegion(entry, catchExclusiveBlocks(handlers), true);
                if (engine == null)
                {
                    throw retired;
                }
                result.addAll(engine);
            }
        }
        else
        {
            // No handler covers the entry: the try begins after a prelude, so a reachable unprocessed try
            // exists by construction and the reaching-condition engine alone can never own the region - it
            // is not offered here. (Offering it bounded by the handler-REACHABLE set is a miscompile: a
            // catch that flows back into its loop puts the loop header in that set, the engine then
            // "succeeds" on the truncated prelude, and the rest of the method is dropped.) The staging and
            // node attempts get only the catch-EXCLUSIVE blocks as stops - blocks a catch dominates, which
            // normal flow can never reach - while the legacy walk keeps the historical reachable set, whose
            // loose stop semantics it is built around.
            List<Statement> structured = recoverSequentialTryStages(entry, catchExclusiveBlocks(handlers));
            if (structured == null)
            {
                structured = rcsStructurer.tryStructureRegion(entry, new HashSet<>(), true);
            }
            if (structured == null)
            {
                throw retiredSchemaRecovery("handler-prelude", entry);
            }
            result.addAll(structured);
        }

        return result;
    }

    /**
     * Recovers the region of the outermost try at {@code entry}.
     */
    private List<Statement> recoverOuterHandlerRegion(IRBlock entry, ExceptionHandler outerHandler, List<ExceptionHandler> handlers, List<ExceptionHandler> mergedHandlers, Set<IRBlock> handlerBlocks)
    {
        List<ExceptionHandler> outerHandlers = new ArrayList<>();
        List<ExceptionHandler> innerHandlers = new ArrayList<>();

        int outerTryEndOffset = outerHandler.getTryEnd() != null
                ? outerHandler.getTryEnd().getBytecodeOffset() : Integer.MAX_VALUE;
        for (ExceptionHandler h : mergedHandlers)
        {
            // A handler over the identical try range is a sibling catch clause on the same try
            // (try { } catch (A) { } catch (B) { }), not a nested one. Only a handler over a strict
            // sub-range is genuinely nested; classifying a sibling as inner would rebuild it as a
            // nested try and drop the shared try body.
            if (h.getHandlerBlock() == outerHandler.getHandlerBlock() || sameTryRange(h, outerHandler))
            {
                outerHandlers.add(h);
            }
            else if (h.getTryStart() == null || h.getTryStart().getBytecodeOffset() < outerTryEndOffset)
            {
                // Only a handler whose protected range begins before the outer try's end is nested in the
                // try BODY. One at or past the end is a try inside the catch clause, or in the continuation
                // after the whole try/catch; leaving it out of innerHandlers keeps it unprocessed so the
                // catch-clause recovery (which recovers a nested try in a catch body) or the continuation
                // recovery owns it, instead of recoverWithNestedHandlers rebuilding it as a try-body handler
                // and hoisting it out of the catch (dropping the caught exception variable's scope).
                innerHandlers.add(h);
            }
        }
        Set<IRBlock> outerHandlerBlocks = new HashSet<>();
        for (ExceptionHandler h : outerHandlers)
        {
            if (h.getHandlerBlock() != null)
            {
                outerHandlerBlocks.add(h.getHandlerBlock());
            }
        }

        Set<IRBlock> stopBlocks = new HashSet<>(handlerBlocks);

        IRMethod irMethod = context.getIrMethod();
        if (outerHandler.getTryEnd() != null)
        {
            int tryEndOffset = outerHandler.getTryEnd().getBytecodeOffset();
            for (IRBlock block : irMethod.getBlocks())
            {
                if (block.getBytecodeOffset() >= tryEndOffset)
                {
                    stopBlocks.add(block);
                }
            }
        }

        processedTryHandlers.addAll(outerHandlers);
        // Mark the handler block (stable across the merge that may have replaced the handler objects) so
        // the try body's own block-sequence recovery does not rebuild the same region as a nested
        // try/catch, which would duplicate the clause (and drop multi-catch types via the merge).
        processedHandlerBlocks.addAll(outerHandlerBlocks);

        IRBlock tryStart = outerHandler.getTryStart();
        List<Statement> preTryStmts = new ArrayList<>();
        if (tryStart != null && tryStart != entry)
        {
            // The offset-based stops exist to bound the TRY region; the prefix owns a post-try-offset
            // block only IT reaches (a guard jumping forward past the try to a shared throw). Stopping
            // the prefix there dropped the guard branches entirely and the target block then surfaced
            // unconditionally. A post-try block the try region also reaches stays a stop, so the
            // continuation recovery keeps sole ownership of genuinely shared blocks.
            Set<IRBlock> reachableFromTry = new HashSet<>();
            Deque<IRBlock> work = new ArrayDeque<>();
            work.add(tryStart);
            work.addAll(handlerBlocks);
            while (!work.isEmpty())
            {
                IRBlock b = work.poll();
                if (b == null || !reachableFromTry.add(b))
                {
                    continue;
                }
                work.addAll(b.getSuccessors());
            }
            Set<IRBlock> preTryStop = new HashSet<>(handlerBlocks);
            for (IRBlock stop : stopBlocks)
            {
                if (reachableFromTry.contains(stop))
                {
                    preTryStop.add(stop);
                }
            }
            preTryStop.add(tryStart);
            preTryStmts = recoverRegionHandoff(entry, preTryStop);
        }

        IRBlock startBlock = (tryStart != null) ? tryStart : entry;
        boolean hasFinally = false;
        for (ExceptionHandler h : outerHandlers)
        {
            if (handlerRethrows(h) && !handlerThrowsFreshException(h) && isFinallyCatchType(h))
            {
                hasFinally = true;
                break;
            }
        }
        boolean savedExtendedDedup = extendedFinallyDedup;
        extendedFinallyDedup = true;
        boolean finallyDeduped;
        try
        {
            // A finally whose body carries a LOOP inlines branchy copies that only the CFG-level subgraph
            // de-duplication can excise (the statement-level folds cannot match their restructured shapes),
            // and javac splits its protected range around a nested user catch - so the range de-dup must run
            // even with inner handlers present. Restricted to a loop-carrying template: a guard-only finally
            // (a try-with-resources sentinel close) is already recovered correctly by other paths and must
            // not be re-routed here. Only this loop path consumes the excised shells (inside the window).
            boolean loopFinally = !finallyTemplateHasNestedHandler(outerHandlers)
                    && (finallyTemplateHasLoop(outerHandlers)
                        || (!innerHandlers.isEmpty() && regionHasNestedFinally(innerHandlers)));
            finallyDeduped = hasFinally
                    && dedupStraightLineFinally(outerHandlers, loopFinally);
            // A finally NESTED under this construct (the outer handler is a plain catch) gets the same
            // eager excision - opportunistic and all-or-nothing, so a shape not fully accounted for is
            // left untouched for the nested recovery.
            if (regionHasNestedFinally(innerHandlers))
            {
                dedupStraightLineFinally(innerHandlers, false);
            }
        }
        finally
        {
            extendedFinallyDedup = savedExtendedDedup;
        }
        // Either excision above retires the copies' mirrored handlers from the method table; this list was
        // built beforehand and would rebuild empty constructs around the excised blocks (and stop the
        // clause walks at their stale range starts).
        if (!innerHandlers.isEmpty())
        {
            Set<IRBlock> liveHandlerBlocks = new HashSet<>();
            for (ExceptionHandler lh : context.getIrMethod().getExceptionHandlers())
            {
                liveHandlerBlocks.add(lh.getHandlerBlock());
            }
            innerHandlers.removeIf(h -> h.getHandlerBlock() != null
                    && !liveHandlerBlocks.contains(h.getHandlerBlock()));
        }
        // A CFG-level de-duplication has identified where each excised normal-path copy converges -
        // the construct's real continuation. A relowered layout can interleave the continuation (and
        // whole downstream structures) BELOW the merged range end, where the offset stops never bound
        // the body walk - the walk would absorb continuation code and bisect its loops. The join is
        // the accurate boundary; the continuation recovery below resumes exactly there. For a javac
        // layout the join lies past the merged end and is already a stop, so this is a no-op.
        Set<IRBlock> dedupJoins = new HashSet<>();
        if (finallyDeduped && outerHandler.getTryStart() != null)
        {
            int windowLo = outerHandler.getTryStart().getBytecodeOffset();
            for (Map.Entry<IRBlock, IRBlock> e : excisedCopyExits.entrySet())
            {
                int rootOff = e.getKey().getBytecodeOffset();
                IRBlock exit = e.getValue();
                // The copy ROOT may sit at or past the merged range end (a relowered layout parks
                // the normal-path close there); only the JOIN itself must lie below the end for the
                // boundary to matter, and the exclusions plus the single-join gate scope the rest.
                if (rootOff < windowLo
                        || exit.getBytecodeOffset() >= outerTryEndOffset
                        || exit.getBytecodeOffset() < windowLo
                        || context.isProcessed(exit))
                {
                    continue;
                }
                // A return/throw exit is a stash-reload boundary terminal the body walk owns (javac
                // lowers `return v` across a finally as stash, copy, reload-return); only a plain
                // fall-through join is the construct's continuation. A join within one of the
                // family's own protected ranges is construct-internal, not a continuation either.
                IRInstruction exitTerm = exit.getTerminator();
                if (exitTerm instanceof ReturnInstruction
                        || (exitTerm instanceof SimpleInstruction
                            && ((SimpleInstruction) exitTerm).getOp() == SimpleOp.ATHROW))
                {
                    continue;
                }
                boolean inOwnRange = false;
                for (ExceptionHandler h : handlers)
                {
                    if (outerHandlerBlocks.contains(h.getHandlerBlock())
                            && h.getTryStart() != null && h.getTryEnd() != null
                            && exit.getBytecodeOffset() >= h.getTryStart().getBytecodeOffset()
                            && exit.getBytecodeOffset() < h.getTryEnd().getBytecodeOffset())
                    {
                        inOwnRange = true;
                        break;
                    }
                }
                if (inOwnRange)
                {
                    continue;
                }
                dedupJoins.add(exit);
            }
            // Only an UNAMBIGUOUS join bounds the body: a fused construct excises one copy per
            // switch arm, and its several exits are arm-interior code - making them stops would cut
            // the arms mid-way. The continuation preference below already requires uniqueness.
            if (dedupJoins.size() == 1)
            {
                stopBlocks.add(dedupJoins.iterator().next());
            }
            else
            {
                dedupJoins.clear();
            }
        }
        List<Statement> tryStmts;
        if (!innerHandlers.isEmpty())
        {
            tryStmts = recoverWithNestedHandlers(startBlock, innerHandlers, stopBlocks);
        }
        else if (hasFinally && detectSynchronizedLock(outerHandler) != null)
        {
            // A synchronized block's monitor instructions (the enter dominating the region and every inlined
            // monitorexit on the body's exits, plus the catch-all release/rethrow) are dropped during
            // statement recovery, so the body has no finally to emit and needs no copy de-duplication; hand
            // it to the engine directly instead of the legacy walk. It is wrapped in SynchronizedStmt below.
            tryStmts = recoverRegionHandoff(startBlock, stopBlocks);
        }
        else if (hasFinally && !finallyDeduped)
        {
            tryStmts = recoverRegionHandoff(startBlock, stopBlocks);
        }
        else if (hasFinally)
        {
            // A de-duplicated finally that WRITES A LOCAL must not let the try body absorb a boundary
            // terminal past its stops: the finally runs between the body and that terminal, so pulling it
            // inside the try would move its evaluation before the finally's write (wrong when the terminal
            // reads that local). The continuation recovery below places it instead. A finally that writes no
            // local (e.g. a plain `unlock()`) cannot change the terminal, so absorption stays on and the
            // terminal keeps its natural place inside the try - matching the legacy walk and avoiding a
            // needless escaped-local spill.
            boolean suppress = finallyWritesLocal(outerHandlers);
            rcsStructurer.setSuppressBoundaryTerminalAbsorption(suppress);
            try
            {
                tryStmts = recoverRegionHandoff(startBlock, stopBlocks);
            }
            finally
            {
                rcsStructurer.setSuppressBoundaryTerminalAbsorption(false);
            }
        }
        else
        {
            tryStmts = recoverRegionHandoff(startBlock, stopBlocks);
        }
        BlockStmt tryBlock = new BlockStmt(tryStmts);
        List<Statement> result = new ArrayList<>(preTryStmts);

        // Build clauses from the original (pre-merge) handlers sharing the outer handler block, so a
        // multi-catch's several exception-table entries coalesce into one `catch (A | B e)` clause.
        List<ExceptionHandler> outerRegionHandlers = new ArrayList<>();
        for (ExceptionHandler h : handlers)
        {
            if (outerHandlerBlocks.contains(h.getHandlerBlock()))
            {
                outerRegionHandlers.add(h);
            }
        }
        List<CatchClause> catchClauses = buildCatchClauses(outerRegionHandlers);

        if (!catchClauses.isEmpty())
        {
            BlockStmt finallyBlock = null;
            List<CatchClause> filteredCatches = new ArrayList<>();
            Set<String> finallyExceptionVars = new HashSet<>();

            for (CatchClause clause : catchClauses)
            {
                if (isFinallyRethrowPattern(clause) && clauseHasFinallyEvidence(clause))
                {
                    finallyBlock = extractFinallyBody(clause);
                    finallyExceptionVars.add(clause.variableName());
                }
                else
                {
                    filteredCatches.add(clause);
                }
            }

            if (!finallyExceptionVars.isEmpty())
            {
                tryStmts = filterOrphanFinallyThrows(tryStmts, finallyExceptionVars);

                List<Statement> finallyStmts = finallyBlock.getStatements();
                tryStmts = filterInlinedFinallyFromTryStatements(tryStmts, finallyStmts);
                filteredCatches = filterInlinedFinallyFromCatches(filteredCatches, finallyStmts);

                // The blocks between the protected region's end and the handler hold the finally inlined on
                // the normal exit path plus the region's real continuation (e.g. its return). Recover them
                // with the inlined-finally copies filtered out so the genuine continuation joins the try body
                // instead of being dropped (which would leave an empty try when the finally has control flow).
                // NOT after a CFG-level de-duplication: the copies are already excised and the continuation
                // is recovered after the region below - pulling it into the try would move its evaluation
                // before the finally (wrong when the finally writes an operand the continuation reads).
                if (!finallyDeduped && outerHandler.getTryEnd() != null && outerHandler.getHandlerBlock() != null)
                {
                    List<Statement> gapStmts = recoverFinallyGap(
                        outerHandler.getTryEnd(), outerHandler.getHandlerBlock(), finallyBlock, finallyExceptionVars);

                    if (!gapStmts.isEmpty() && !isTerminatingBlock(new BlockStmt(tryStmts)))
                    {
                        tryStmts = new ArrayList<>(tryStmts);
                        tryStmts.addAll(gapStmts);
                    }
                }

                tryBlock = new BlockStmt(tryStmts);
            }

            Value syncLock = filteredCatches.isEmpty() ? detectSynchronizedLock(outerHandler) : null;
            Statement region;
            if (syncLock != null)
            {
                SynchronizedStmt sync = new SynchronizedStmt(recoverLockExpr(syncLock), tryBlock);
                stampFromBody(sync, tryBlock);
                region = sync;
            }
            else
            {
                TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, filteredCatches, finallyBlock);
                stampFromBody(tryCatch, tryBlock);
                region = tryCatch;
            }
            result.add(region);
            // A region whose catch (or sync body) falls through continues at the join after
            // the protected range; without walking it the method's tail is silently dropped.
            if (!isTerminatingRecoveredTry(region))
            {
                Set<IRBlock> consumed = new HashSet<>();
                for (ExceptionHandler h : outerRegionHandlers)
                {
                    if (h.getHandlerBlock() != null)
                    {
                        collectCatchConsumedBlocks(h, consumed);
                    }
                }
                // An interleaved layout's continuation is the de-duplication's join, not whatever
                // block the offset scan finds past the merged end (that can be the middle of a loop
                // the join's own structure contains). Only an unambiguous unprocessed join is taken.
                IRBlock continuation = null;
                for (IRBlock j : dedupJoins)
                {
                    if (context.isProcessed(j))
                    {
                        continue;
                    }
                    if (continuation != null)
                    {
                        continuation = null;
                        break;
                    }
                    continuation = j;
                }
                if (continuation == null)
                {
                    continuation = findBlockAfterTryCatch(outerHandler, consumed);
                }
                if (continuation != null && !context.isProcessed(continuation))
                {
                    result.addAll(recoverRegionHandoff(continuation, new HashSet<>()));
                }
            }
        }
        else
        {
            result.addAll(tryStmts);
        }

        return result;
    }

    /**
     * Merges exception handlers that share the same handler block.
     */
    private List<ExceptionHandler> mergeHandlersWithSameTarget(List<ExceptionHandler> handlers)
    {
        Map<IRBlock, List<ExceptionHandler>> byHandlerBlock = new LinkedHashMap<>();
        for (ExceptionHandler h : handlers)
        {
            byHandlerBlock.computeIfAbsent(h.getHandlerBlock(), k -> new ArrayList<>()).add(h);
        }

        List<ExceptionHandler> merged = new ArrayList<>();
        for (List<ExceptionHandler> group : byHandlerBlock.values())
        {
            if (group.size() == 1)
            {
                merged.add(group.get(0));
            }
            else
            {
                IRBlock earliestStart = null;
                IRBlock latestEnd = null;
                int minOffset = Integer.MAX_VALUE;
                int maxOffset = Integer.MIN_VALUE;

                for (ExceptionHandler h : group)
                {
                    if (h.getTryStart() != null && h.getTryStart().getBytecodeOffset() < minOffset)
                    {
                        minOffset = h.getTryStart().getBytecodeOffset();
                        earliestStart = h.getTryStart();
                    }
                    if (h.getTryEnd() != null && h.getTryEnd().getBytecodeOffset() > maxOffset)
                    {
                        maxOffset = h.getTryEnd().getBytecodeOffset();
                        latestEnd = h.getTryEnd();
                    }
                }

                ExceptionHandler mergedHandler = new ExceptionHandler(
                    earliestStart,
                    latestEnd,
                    group.get(0).getHandlerBlock(),
                    group.get(0).getCatchType()
                );
                merged.add(mergedHandler);
            }
        }
        return merged;
    }

    /**
     * Whether two handlers protect the identical try range (making them sibling catch clauses).
     */
    private static boolean sameTryRange(ExceptionHandler a, ExceptionHandler b)
    {
        return tryOffset(a.getTryStart()) == tryOffset(b.getTryStart())
                && tryOffset(a.getTryEnd()) == tryOffset(b.getTryEnd());
    }

    private static int tryOffset(IRBlock block)
    {
        return block != null ? block.getBytecodeOffset() : -1;
    }

    private ExceptionHandler findOutermostHandler(IRBlock entry, List<ExceptionHandler> handlers)
    {
        ExceptionHandler outermost = null;
        int maxCoverage = -1;

        for (ExceptionHandler handler : handlers)
        {
            int startOffset = handler.getTryStart().getBytecodeOffset();
            int endOffset = handler.getTryEnd() != null ? handler.getTryEnd().getBytecodeOffset() : Integer.MAX_VALUE;
            int coverage = endOffset - startOffset;

            if (startOffset <= entry.getBytecodeOffset() && coverage > maxCoverage)
            {
                maxCoverage = coverage;
                outermost = handler;
            }
        }

        return outermost;
    }

    /**
     * Recovers statements with nested exception handlers.
     */
    private List<Statement> recoverWithNestedHandlers(IRBlock start, List<ExceptionHandler> innerHandlers, Set<IRBlock> stopBlocks)
    {
        Set<IRBlock> allHandlerBlocks = new HashSet<>();
        for (ExceptionHandler h : innerHandlers)
        {
            if (h.getHandlerBlock() != null)
            {
                allHandlerBlocks.add(h.getHandlerBlock());
            }
        }

        List<ExceptionHandler> normalHandlers = new ArrayList<>();
        for (ExceptionHandler h : innerHandlers)
        {
            if (!allHandlerBlocks.contains(h.getTryStart()))
            {
                normalHandlers.add(h);
            }
        }

        Set<IRBlock> innerTryStarts = new HashSet<>();
        for (ExceptionHandler h : normalHandlers)
        {
            innerTryStarts.add(h.getTryStart());
        }

        return recoverWithInnerTryCatch(start, normalHandlers, innerTryStarts, stopBlocks, new HashSet<>());
    }

    /**
     * Recursive helper that recovers blocks and emits inner try-catch statements.
     */
    private List<Statement> recoverWithInnerTryCatch(IRBlock current, List<ExceptionHandler> innerHandlers, Set<IRBlock> innerTryStarts, Set<IRBlock> stopBlocks, Set<IRBlock> visited)
    {
        List<Statement> result = new ArrayList<>();

        Set<IRBlock> combinedStops = new HashSet<>(stopBlocks);
        combinedStops.addAll(innerTryStarts);

        while (current != null && !visited.contains(current) && !stopBlocks.contains(current))
        {
            if (System.getProperty("yabr.trace.walk") != null)
            {
                System.err.println("[WALK] at=" + current.getBytecodeOffset()
                        + " processed=" + context.isProcessed(current)
                        + " info=" + (analyzer.getRegionInfo(current) == null ? "null"
                            : analyzer.getRegionInfo(current).getType()));
            }
            if (!innerTryStarts.contains(current))
            {
                visited.add(current);
            }

            ExceptionHandler innerHandler = findHandlerStartingAt(current, innerHandlers);

            if (innerHandler != null)
            {
                List<ExceptionHandler> sameRegionHandlers = new ArrayList<>();
                List<ExceptionHandler> remainingHandlers = new ArrayList<>();

                TryRegion targetRegion = createTryRegion(innerHandler);
                for (ExceptionHandler h : innerHandlers)
                {
                    TryRegion handlerRegion = createTryRegion(h);
                    if (targetRegion != null && targetRegion.equals(handlerRegion))
                    {
                        sameRegionHandlers.add(h);
                    }
                    else
                    {
                        remainingHandlers.add(h);
                    }
                }

                Set<IRBlock> innerHandlerBlocks = new HashSet<>();
                for (ExceptionHandler h : sameRegionHandlers)
                {
                    if (h.getHandlerBlock() != null)
                    {
                        innerHandlerBlocks.add(h.getHandlerBlock());
                    }
                }

                Set<IRBlock> innerStopBlocks = new HashSet<>(stopBlocks);
                innerStopBlocks.addAll(innerHandlerBlocks);

                boolean innerHasFinally = false;
                for (ExceptionHandler h : sameRegionHandlers)
                {
                    if (handlerRethrows(h))
                    {
                        innerHasFinally = true;
                        break;
                    }
                }
                if (innerHasFinally)
                {
                    boolean savedExtendedInner = extendedFinallyDedup;
                    extendedFinallyDedup = true;
                    try
                    {
                        dedupStraightLineFinally(sameRegionHandlers);
                    }
                    finally
                    {
                        extendedFinallyDedup = savedExtendedInner;
                    }
                }

                IRBlock innerTryEnd = innerHandler.getTryEnd();
                if (innerTryEnd != null)
                {
                    // The block at the try's exclusive end offset holds javac's inlined finally copy on the
                    // normal-exit path. It belongs after the try/finally, not inside the try body; without this
                    // stop the body walk absorbs that copy and the finally body executes twice (e.g. a doubled
                    // lock.unlock()). The dedup above excises the copy from that block, and the continuation
                    // logic below skips it via the try-end.
                    if (innerHasFinally)
                    {
                        innerStopBlocks.add(innerTryEnd);
                    }
                    for (IRBlock succ : innerTryEnd.getSuccessors())
                    {
                        if (!innerHandlerBlocks.contains(succ))
                        {
                            innerStopBlocks.add(succ);
                        }
                    }
                }

                // Claim this region's handlers before recovering the body: a body construct sharing the
                // try's start block would otherwise be node-ified by the engine offers as a fresh try -
                // the very construct this arm is recovering - entangling the offer with its own caller.
                for (ExceptionHandler h : sameRegionHandlers)
                {
                    processedTryHandlers.add(h);
                    if (h.getHandlerBlock() != null)
                    {
                        processedHandlerBlocks.add(h.getHandlerBlock());
                    }
                }
                List<Statement> tryStmts;
                if (!remainingHandlers.isEmpty())
                {
                    Set<IRBlock> remainingTryStarts = new HashSet<>();
                    for (ExceptionHandler h : remainingHandlers)
                    {
                        remainingTryStarts.add(h.getTryStart());
                    }
                    tryStmts = recoverWithInnerTryCatch(current, remainingHandlers, remainingTryStarts, innerStopBlocks, new HashSet<>());
                }
                else
                {
                    tryStmts = recoverBlocksForTry(current, innerStopBlocks, new HashSet<>());
                }
                BlockStmt tryBlock = new BlockStmt(tryStmts);

                List<CatchClause> catchClauses = new ArrayList<>();
                for (ExceptionHandler h : sameRegionHandlers)
                {
                    CatchClause catchClause = recoverCatchClause(h);
                    if (catchClause != null)
                    {
                        catchClauses.add(catchClause);
                    }
                }

                if (!catchClauses.isEmpty())
                {
                    BlockStmt finallyBlock = null;
                    List<CatchClause> filteredCatches = new ArrayList<>();
                    for (CatchClause clause : catchClauses)
                    {
                        if (isFinallyRethrowPattern(clause) && clauseHasFinallyEvidence(clause))
                        {
                            finallyBlock = extractFinallyBody(clause);
                        }
                        else
                        {
                            filteredCatches.add(clause);
                        }
                    }
                    // javac also inlines the finally before each early return/throw inside the try; strip those
                    // copies so the finally body appears only in the finally block, never a second time on an
                    // early-exit path.
                    if (finallyBlock != null)
                    {
                        tryStmts = filterInlinedFinallyFromTryStatements(tryStmts, finallyBlock.getStatements());
                        tryBlock = new BlockStmt(tryStmts);
                        filteredCatches = filterInlinedFinallyFromCatches(filteredCatches, finallyBlock.getStatements());
                    }
                    TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, filteredCatches, finallyBlock);
                    stampFromBody(tryCatch, tryBlock);
                    result.add(tryCatch);
                    // A try whose every path returns or throws has no continuation on this walk:
                    // walking the try-end successors would re-emit the stashed return (or whatever
                    // block follows) as unreachable code after the construct.
                    if (isTerminatingRecoveredTry(tryCatch))
                    {
                        current = null;
                        continue;
                    }
                }
                else
                {
                    result.addAll(tryStmts);
                }

                IRBlock tryEnd = innerHandler.getTryEnd();
                if (tryEnd != null)
                {
                    visited.add(tryEnd);
                    // The block at the try end can be an EXCISED copy of the finally whose exit merely
                    // reloads a return value stashed inside the try - javac's layout for a return-exit,
                    // where the try body recovery already absorbed that return as its boundary terminal.
                    // Walking on would re-emit it as an unconditional trailing return that the construct's
                    // fall-through path would wrongly adopt; the real fall-through continues elsewhere.
                    if (excisedCopyExits.containsKey(tryEnd)
                            && isStashReloadReturn(excisedCopyExits.get(tryEnd), tryRangeBlocks(innerHandler))
                            && containsReturn(tryStmts))
                    {
                        current = null;
                        continue;
                    }
                    IRBlock next = null;
                    for (IRBlock succ : tryEnd.getSuccessors())
                    {
                        if (!visited.contains(succ) && !stopBlocks.contains(succ))
                        {
                            if (innerTryStarts.contains(succ))
                            {
                                next = succ;
                                break;
                            }
                            if (!combinedStops.contains(succ) && next == null)
                            {
                                next = succ;
                            }
                        }
                    }
                    current = next;
                }
                else
                {
                    current = null;
                }
                continue;
            }

            if (context.isProcessed(current))
            {
                result.addAll(context.getStatements(current));
                IRBlock next = null;
                DominatorTree walkDt = context.getDominatorTree();
                for (IRBlock succ : current.getSuccessors())
                {
                    // A back edge leads into an enclosing loop's already-emitted body; following it
                    // would re-add that body's cached statements (an iterator advance duplicated
                    // inside the try). The loop's own emission owns the iteration - stop here.
                    if (walkDt != null && walkDt.dominates(succ, current))
                    {
                        continue;
                    }
                    if (!visited.contains(succ) && !stopBlocks.contains(succ))
                    {
                        if (innerTryStarts.contains(succ))
                        {
                            next = succ;
                            break;
                        }
                        if (!combinedStops.contains(succ) && next == null)
                        {
                            next = succ;
                        }
                    }
                }
                current = next;
                continue;
            }

            RegionInfo info = analyzer.getRegionInfo(current);
            if (info == null)
            {
                if (current.getTerminator() instanceof SwitchInstruction)
                {
                    // Same contract as the try-body walk: an unclassifiable dispatch is offered as
                    // a terminal region or declined loudly, never fallen through.
                    OfferResult offered = offerTerminalRegion(current, new HashSet<>(combinedStops));
                    if (offered != null)
                    {
                        result.addAll(offered.statements);
                        current = offered.continuation != null && !stopBlocks.contains(offered.continuation)
                                && (!context.isProcessed(offered.continuation)
                                    || isTerminalTail(offered.continuation)) ? offered.continuation : null;
                        continue;
                    }
                    throw retiredSchemaRecovery("switch", current);
                }
                List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);
                context.markProcessed(current);
                IRBlock next = null;
                DominatorTree seqDt = context.getDominatorTree();
                for (IRBlock succ : current.getSuccessors())
                {
                    // A back edge is the enclosing loop's iteration, owned by the loop's own
                    // emission; walking through it drifts into already-emitted body code.
                    if (seqDt != null && seqDt.dominates(succ, current))
                    {
                        continue;
                    }
                    if (!visited.contains(succ) && !stopBlocks.contains(succ))
                    {
                        if (innerTryStarts.contains(succ))
                        {
                            next = succ;
                            break;
                        }
                        if (!combinedStops.contains(succ) && next == null)
                        {
                            next = succ;
                        }
                    }
                }
                current = next;
                continue;
            }


            // A structural region inside the try-sequence walk is offered to the reaching-condition
            // engine FIRST, at EXACTLY the schema recovery's own scope - bounded at the structure's merge
            // or loop exit - so the offered region never spans a handler boundary and the walk's
            // continuation is identical to the schema dispatch's. The schema recoverers below remain only
            // as the decline fallback.
            IRBlock rcsBound = null;
            boolean terminalRegion = false;
            switch (info.getType())
            {
                case IF_THEN:
                case IF_THEN_ELSE:
                // A guard clause is an if with an early-exit arm; its merge is the continuation
                // after the guard, the same bound an if takes.
                case GUARD_CLAUSE:
                    rcsBound = info.getMergeBlock();
                    // An if with no merge block has terminating arms (every path returns or throws);
                    // like an exit-less loop it is offered unbounded, and the walk has no continuation.
                    terminalRegion = rcsBound == null;
                    break;
                case WHILE_LOOP:
                case DO_WHILE_LOOP:
                case FOR_LOOP:
                    rcsBound = info.getLoopExit();
                    terminalRegion = rcsBound == null;
                    break;
                case SWITCH:
                    // A dispatch met by this walk bounds at its merge like an if; without a merge
                    // (every arm terminal) it is offered unbounded. Falling into the sequential
                    // default would silently adopt one arm and drop the dispatch and the rest.
                    rcsBound = findSwitchMerge(info);
                    terminalRegion = rcsBound == null;
                    break;
                default:
                    break;
            }
            if (terminalRegion)
            {
                OfferResult offered = offerTerminalRegion(current, new HashSet<>(combinedStops));
                if (offered != null)
                {
                    result.addAll(offered.statements);
                    current = offered.continuation != null && !stopBlocks.contains(offered.continuation)
                            && (!context.isProcessed(offered.continuation)
                                || isTerminalTail(offered.continuation)) ? offered.continuation : null;
                    continue;
                }
            }
            if (rcsBound != null && !visited.contains(rcsBound))
            {
                Set<IRBlock> boundedStops = new HashSet<>(combinedStops);
                boundedStops.add(rcsBound);
                List<Statement> structuredRegion = offerRegionToEngine(current, boundedStops, rcsBound);
                if (structuredRegion != null)
                {
                    result.addAll(structuredRegion);
                    current = stopBlocks.contains(rcsBound)
                            || (context.isProcessed(rcsBound) && !isTerminalTail(rcsBound)) ? null : rcsBound;
                    continue;
                }
            }

            switch (info.getType())
            {
                case IF_THEN:
                    throw retiredSchemaRecovery("if-then", current);
                case IF_THEN_ELSE:
                    throw retiredSchemaRecovery("if-else", current);
                case WHILE_LOOP:
                    throw retiredSchemaRecovery("while", current);
                case DO_WHILE_LOOP:
                    throw retiredSchemaRecovery("do-while", current);
                case FOR_LOOP:
                    throw retiredSchemaRecovery("for", current);
                case GUARD_CLAUSE:
                    throw retiredSchemaRecovery("guard", current);
                case SWITCH:
                    throw retiredSchemaRecovery("switch", current);
                default:
                {
                    List<Statement> blockStmts = recoverSimpleBlock(current);
                    result.addAll(blockStmts);
                    context.setStatements(current, blockStmts);
                    context.markProcessed(current);
                    IRBlock next = null;
                    for (IRBlock succ : current.getSuccessors())
                    {
                        if (!visited.contains(succ) && !stopBlocks.contains(succ))
                        {
                            if (innerTryStarts.contains(succ))
                            {
                                next = succ;
                                break;
                            }
                            if (!combinedStops.contains(succ) && next == null)
                            {
                                next = succ;
                            }
                        }
                    }
                    current = next;
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Finds a handler that starts at the given block.
     */
    private ExceptionHandler findHandlerStartingAt(IRBlock block, List<ExceptionHandler> handlers)
    {
        for (ExceptionHandler handler : handlers)
        {
            if (handler.getTryStart() == block)
            {
                return handler;
            }
        }
        return null;
    }

    private TryRegion createTryRegion(ExceptionHandler handler)
    {
        if (handler == null || handler.getTryStart() == null)
        {
            return null;
        }
        return new TryRegion(handler.getTryStart(), handler.getTryEnd());
    }

    /**
     * The caught type for one exception-table entry: {@code java/lang/Throwable} for a catch-all, otherwise the
     * declared catch type.
     */
    private SourceType catchTypeOf(ExceptionHandler handler)
    {
        if (handler.isCatchAll())
        {
            return new ReferenceSourceType("java/lang/Throwable", Collections.emptyList());
        }
        return new ReferenceSourceType(handler.getCatchType().getInternalName(), Collections.emptyList());
    }

    /**
     * Builds the catch clauses for one try region, coalescing every group of handlers that share a handler block
     * into a single (multi-)catch clause.
     */
    private List<CatchClause> buildCatchClauses(List<ExceptionHandler> regionHandlers)
    {
        Map<IRBlock, List<ExceptionHandler>> byBlock = new LinkedHashMap<>();
        for (ExceptionHandler h : regionHandlers)
        {
            if (h.getHandlerBlock() != null)
            {
                byBlock.computeIfAbsent(h.getHandlerBlock(), k -> new ArrayList<>()).add(h);
            }
        }

        List<CatchClause> clauses = new ArrayList<>();
        for (List<ExceptionHandler> group : byBlock.values())
        {
            CatchClause clause = recoverCatchClause(group.get(0));
            if (clause == null)
            {
                continue;
            }
            List<SourceType> types = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ExceptionHandler h : group)
            {
                SourceType type = catchTypeOf(h);
                if (seen.add(((ReferenceSourceType) type).getInternalName()))
                {
                    types.add(type);
                }
            }
            if (types.size() > 1)
            {
                clause = CatchClause.multiCatch(types, clause.variableName(), clause.body());
            }
            clauses.add(clause);
        }
        return clauses;
    }

    /**
     * Detects a {@code synchronized} block.
     */
    private Value detectSynchronizedLock(ExceptionHandler handler)
    {
        if (handler == null || !handler.isCatchAll() || handler.getHandlerBlock() == null)
        {
            return null;
        }
        if (!blockContainsMonitorExit(handler.getHandlerBlock()))
        {
            return null;
        }
        return findMonitorEnterLock(handler.getTryStart());
    }

    private boolean blockContainsMonitorExit(IRBlock block)
    {
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof SimpleInstruction && ((SimpleInstruction) instr).getOp() == SimpleOp.MONITOREXIT)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Recovers a {@code synchronized} lock expression.
     */
    private Expression recoverLockExpr(Value syncLock)
    {
        Expression expr = exprRecoverer.recoverOperand(syncLock);
        if (expr instanceof VarRefExpr && syncLock instanceof SSAValue)
        {
            SSAValue ssa = (SSAValue) syncLock;
            String name = ((VarRefExpr) expr).getName();
            if (ssa.getDefinition() != null
                    && !context.getExpressionContext().isDeclared(name)
                    && !isParameterOrThisRef(ssa))
            {
                Expression direct = exprRecoverer.recover(ssa.getDefinition());
                if (direct != null)
                {
                    return direct;
                }
            }
        }
        return expr;
    }

    private Value findMonitorEnterLock(IRBlock tryStart)
    {
        if (tryStart == null)
        {
            return null;
        }
        Set<IRBlock> visited = new HashSet<>();
        Deque<IRBlock> work = new ArrayDeque<>(tryStart.getPredecessors());
        while (!work.isEmpty())
        {
            IRBlock block = work.poll();
            if (!visited.add(block))
            {
                continue;
            }
            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof SimpleInstruction
                        && ((SimpleInstruction) instr).getOp() == SimpleOp.MONITORENTER)
                {
                    return ((SimpleInstruction) instr).getOperand();
                }
            }
            work.addAll(block.getPredecessors());
        }
        return null;
    }

    /**
     * Recovered clauses per handler block.
     */
    private final Map<IRBlock, CatchClause> recoveredClauses = new HashMap<>();

    /**
     * Rewrites a trailing {@code if (c) { <terminates> } else { X }} to {@code if (c) <terminates>; X}, recursing
     * on the hoisted tail, so a nested rethrow ends up as the last statement.
     */
    private List<Statement> hoistTerminalThenElse(List<Statement> stmts)
    {
        if (stmts.isEmpty())
        {
            return stmts;
        }
        Statement last = stmts.get(stmts.size() - 1);
        if (!(last instanceof IfStmt))
        {
            return stmts;
        }
        IfStmt ifs = (IfStmt) last;
        if (!ifs.hasElse() || ifs.getElseBranch() == null
                || !isTerminatingBlock(new BlockStmt(flattenToStatements(ifs.getThenBranch()))))
        {
            return stmts;
        }
        List<Statement> out = new ArrayList<>(stmts.subList(0, stmts.size() - 1));
        IfStmt noElse = new IfStmt(ifs.getCondition(), ifs.getThenBranch());
        Locations.copy(ifs, noElse);
        out.add(noElse);
        out.addAll(hoistTerminalThenElse(flattenToStatements(ifs.getElseBranch())));
        return out;
    }

    private CatchClause recoverCatchClause(ExceptionHandler handler)
    {
        IRBlock handlerBlock = handler.getHandlerBlock();
        if (handlerBlock == null)
        {
            return null;
        }
        CatchClause cached = recoveredClauses.get(handlerBlock);
        if (cached != null)
        {
            return cached;
        }

        ReferenceSourceType exceptionType;
        if (handler.isCatchAll())
        {
            exceptionType = new ReferenceSourceType("java/lang/Throwable", Collections.emptyList());
        }
        else
        {
            String typeName = handler.getCatchType().getInternalName();
            exceptionType = new ReferenceSourceType(typeName, Collections.emptyList());
        }

        String exceptionVarName = findExceptionVariableName(handlerBlock);
        if (exceptionVarName == null)
        {
            String simpleName;
            simpleName = exceptionType.getSimpleName().toLowerCase();
            exceptionVarName = simpleName.charAt(0) + "_ex";
        }

        registerExceptionVariables(handler, exceptionVarName);

        Set<SSAValue> exceptionValues = collectExceptionValues(handlerBlock);

        List<Statement> handlerStmts = new ArrayList<>();

        for (IRInstruction instr : handlerBlock.getInstructions())
        {
            if (instr instanceof CopyInstruction)
            {
                continue;
            }

            if (instr instanceof StoreLocalInstruction)
            {
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                if (store.getValue() instanceof SSAValue)
                {
                    SSAValue ssaValue = (SSAValue) store.getValue();
                    if (exceptionValues.contains(ssaValue))
                    {
                        continue;
                    }
                }
            }

            Statement stmt = recoverInstruction(instr);
            if (stmt != null)
            {
                handlerStmts.add(stmt);
            }
        }

        IRInstruction handlerTerminator = handlerBlock.getTerminator();
        // A catch body that BRANCHES cannot be recovered by the flat successor walk below: the walk drops
        // the branch conditions and appends the arms in set order, so a guarded arm's effects are lost
        // (a conditional rethrow becomes unconditional; a guarded effect lands behind the other arm's
        // return and is filtered out entirely). Recover the handler's dominator subtree as a structured
        // region instead, bounded at the blocks that leave it. A linear handler keeps the flat walk,
        // whose per-instruction recovery folds compound assignments the structured walk would split.
        // A typed handler in a method that ALSO carries a finally (a rethrowing catch-all) keeps the
        // flat walk too: its branches are javac's inlined finally copy, which the flat path folds
        // against the finally template - the structured walk would resurrect the copy as a spurious
        // nested try/finally around a raw slot temp. The catch-all rethrower itself REQUIRES the
        // structured form to end in its rethrow; any other structured clause only needs to be non-empty.
        boolean rethrowCarrier = handler.isCatchAll() && handlerRethrows(handler);
        boolean finallyPresent = false;
        for (ExceptionHandler eh : context.getIrMethod().getExceptionHandlers())
        {
            if (eh.isCatchAll() && eh.getHandlerBlock() != handlerBlock && handlerRethrows(eh))
            {
                finallyPresent = true;
                break;
            }
        }
        if ((handler.isCatchAll() || handlerRethrows(handler) || !finallyPresent)
                && handlerSubtreeBranches(handlerBlock))
        {
            DominatorTree hdt = context.getDominatorTree();
            if (hdt != null)
            {
                Set<IRBlock> catchBody = new HashSet<>();
                catchBody.add(handlerBlock);
                for (IRBlock b : context.getIrMethod().getBlocks())
                {
                    if (hdt.dominates(handlerBlock, b))
                    {
                        catchBody.add(b);
                    }
                }
                // Stop at EVERY block outside the handler's dominated subtree, not just its direct exit
                // successors: a clause ending in a return has no exit successor, and the walk's sequential
                // fallback would otherwise run past the clause into sibling handlers or the method tail.
                Set<IRBlock> bodyStops = new HashSet<>();
                for (IRBlock b : context.getIrMethod().getBlocks())
                {
                    if (!catchBody.contains(b))
                    {
                        bodyStops.add(b);
                    }
                }
                // The handler's own split entries (including a rethrower's self-protection edge) target
                // this same handler block; the walk below would otherwise treat one as an unprocessed try
                // starting inside the clause and re-enter try recovery on the clause's own body. This
                // clause recovery IS those entries' recovery - consume them first.
                for (ExceptionHandler eh : context.getIrMethod().getExceptionHandlers())
                {
                    if (eh.getHandlerBlock() == handlerBlock)
                    {
                        processedTryHandlers.add(eh);
                    }
                }
                // An ENCLOSING construct's protection of this clause's code (a range starting inside
                // the subtree whose handler lies outside it) is not a nested try of the clause: it
                // belongs to a construct still being recovered outside-in, and decoding it here meets
                // a mid-family range start no node model owns. Mask such entries for this walk only.
                Set<ExceptionHandler> enclosingProtections = new HashSet<>();
                for (ExceptionHandler eh : context.getIrMethod().getExceptionHandlers())
                {
                    if (eh.getHandlerBlock() != null && eh.getHandlerBlock() != handlerBlock
                            && eh.getTryStart() != null
                            && catchBody.contains(eh.getTryStart())
                            && !catchBody.contains(eh.getHandlerBlock())
                            && !processedTryHandlers.contains(eh))
                    {
                        enclosingProtections.add(eh);
                        processedTryHandlers.add(eh);
                    }
                }
                List<Statement> structured;
                try
                {
                    structured = recoverBlockSequence(handlerBlock, bodyStops);
                }
                finally
                {
                    processedTryHandlers.removeAll(enclosingProtections);
                }
                List<Statement> filtered = new ArrayList<>();
                for (Statement st : structured)
                {
                    if (st instanceof VarDeclStmt && ((VarDeclStmt) st).getName().equals(exceptionVarName))
                    {
                        continue;
                    }
                    filtered.add(st);
                }
                // A control-flow finally's rethrow handler recovers as `if (c) { <swallow return> } else
                // { throw e }` - the throw nested in the else, not a bare trailing statement. Hoist a
                // terminating then-arm's else to a sibling so the rethrow becomes the clause's trailing throw
                // (the shape the accept below and extractFinallyBody both key on), turning the body into
                // `if (c) return ...; throw e`. Straight-line handlers are unchanged (no trailing if/else).
                if (rethrowCarrier)
                {
                    filtered = hoistTerminalThenElse(filtered);
                }
                // A rethrow carrier must END in its rethrow - but a clause whose body CARRIES CONTROL
                // FLOW keeps that rethrow nested inside the recovered structure (a loop-carrying finally
                // recovers as `while (true) { ...; throw e; }`), which is equally the clause terminal.
                boolean accept = rethrowCarrier
                        ? (!filtered.isEmpty() && endsInRethrow(filtered.get(filtered.size() - 1)))
                        : !filtered.isEmpty();
                if (accept)
                {
                    appendSharedReturnFallThrough(filtered, handlerBlock);
                    CatchClause structuredClause = CatchClause.of(exceptionType, exceptionVarName, new BlockStmt(filtered));
                    recoveredClauses.putIfAbsent(handlerBlock, structuredClause);
                    return structuredClause;
                }
            }
        }
        boolean isGoto = false;
        if (handlerTerminator instanceof SimpleInstruction)
        {
            SimpleInstruction simple = (SimpleInstruction) handlerTerminator;
            isGoto = (simple.getOp() == SimpleOp.GOTO);
        }
        // Recover the catch body's continuation blocks. A goto out of the handler block usually jumps to the
        // shared merge after the whole try/catch (post-try code, or an enclosing finally's inlined copy, that
        // must NOT be pulled into the catch), so a non-catch-all handler ending in a goto normally stops here.
        // The one exception is a catch whose body contains its own nested TRY: the handler stores the exception
        // and gotos into that try, so the try-start (dominated by the handler block) must be recovered, or the
        // rest of the catch - including the caught exception variable's uses - is dropped.
        if (!isGoto || handler.isCatchAll() || catchBodyHasNestedTry(handlerBlock) || gotoStaysInCatch(handlerBlock))
        {
            Set<IRBlock> visitedHandlerBlocks = new HashSet<>();
            visitedHandlerBlocks.add(handlerBlock);
            recoverHandlerBlocks(handlerBlock.getSuccessors(), visitedHandlerBlocks, handlerStmts, handlerBlock);
        }

        List<Statement> filteredStmts = new ArrayList<>();
        boolean reachedTerminator = false;
        for (Statement stmt : handlerStmts)
        {
            if (reachedTerminator)
            {
                continue;
            }
            if (stmt instanceof VarDeclStmt)
            {
                VarDeclStmt varDecl = (VarDeclStmt) stmt;
                if (varDecl.getName().equals(exceptionVarName))
                {
                    continue;
                }
            }
            if (stmt instanceof ExprStmt)
            {
                ExprStmt exprStmt = (ExprStmt) stmt;
                if (exprStmt.getExpression() instanceof BinaryExpr)
                {
                    BinaryExpr binExpr = (BinaryExpr) exprStmt.getExpression();
                    if (binExpr.getOperator() == BinaryOperator.ASSIGN)
                    {
                        if (binExpr.getLeft() instanceof VarRefExpr)
                        {
                            VarRefExpr varRef = (VarRefExpr) binExpr.getLeft();
                            if (varRef.getName().equals(exceptionVarName))
                            {
                                continue;
                            }
                        }
                    }
                }
            }
            filteredStmts.add(stmt);

            if (stmt instanceof ReturnStmt || stmt instanceof ThrowStmt)
            {
                reachedTerminator = true;
            }
        }

        List<Statement> bodyStmts = filteredStmts.isEmpty() ? handlerStmts : filteredStmts;
        appendSharedReturnFallThrough(bodyStmts, handlerBlock);
        BlockStmt handlerBody = new BlockStmt(bodyStmts);

        CatchClause clause = CatchClause.of(exceptionType, exceptionVarName, handlerBody);
        recoveredClauses.putIfAbsent(handlerBlock, clause);
        return clause;
    }

    /**
     * A catch clause that falls through to a merge ALREADY consumed elsewhere would silently drop off the end of
     * the method.
     */
    private void appendSharedReturnFallThrough(List<Statement> stmts, IRBlock handlerBlock)
    {
        if (stmts.isEmpty() || isTerminatingBlock(new BlockStmt(stmts)))
        {
            return;
        }
        DominatorTree dt = context.getDominatorTree();
        if (dt == null)
        {
            return;
        }
        Set<IRBlock> subtree = new HashSet<>();
        subtree.add(handlerBlock);
        for (IRBlock b : context.getIrMethod().getBlocks())
        {
            if (dt.dominates(handlerBlock, b))
            {
                subtree.add(b);
            }
        }
        IRBlock join = null;
        for (IRBlock b : subtree)
        {
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() != EdgeType.NORMAL || subtree.contains(e.getKey()))
                {
                    continue;
                }
                if (join != null && join != e.getKey())
                {
                    return;
                }
                join = e.getKey();
            }
        }
        if (join != null)
        {
            stmts.addAll(processedReturnStatements(join));
        }
    }

    /**
     * True when the catch handler block gotos directly into its own nested try.
     */
    private boolean catchBodyHasNestedTry(IRBlock handlerBlock)
    {
        DominatorTree dt = context.getDominatorTree();
        if (dt == null)
        {
            return false;
        }
        for (IRBlock succ : handlerBlock.getSuccessors())
        {
            if (!dt.dominates(handlerBlock, succ))
            {
                continue;
            }
            ExceptionHandler h = findHandlerStartingAt(succ);
            if (h != null && !processedTryHandlers.contains(h)
                    && !processedHandlerBlocks.contains(h.getHandlerBlock()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the rethrowing clause is a real finally rather than a user-written catch-and-rethrow.
     */
    private boolean clauseHasFinallyEvidence(CatchClause clause)
    {
        IRBlock hb = null;
        for (Map.Entry<IRBlock, CatchClause> e : recoveredClauses.entrySet())
        {
            if (e.getValue() == clause)
            {
                hb = e.getKey();
                break;
            }
        }
        if (hb == null)
        {
            return true;
        }
        return handlerHasFinallyEvidence(hb);
    }

    /**
     * Whether the rethrowing handler at {@code hb} is a real finally rather than a user-written catch-and-rethrow.
     */
    private boolean handlerHasFinallyEvidence(IRBlock hb)
    {
        List<ExceptionHandler> hbEntries = new ArrayList<>();
        for (ExceptionHandler h : context.getIrMethod().getExceptionHandlers())
        {
            if (h.getHandlerBlock() == hb)
            {
                hbEntries.add(h);
            }
        }
        boolean savedEvidenceExtended = extendedFinallyDedup;
        extendedFinallyDedup = true;
        try
        {
            List<IRInstruction> template = hbEntries.isEmpty()
                    ? null : straightLineFinallyTemplate(hbEntries.get(0));
            if (template != null && !template.isEmpty())
            {
                Set<IRBlock> chainBlocks = new HashSet<>();
                List<IRBlock> chain = finallyHandlerChain(hbEntries.get(0));
                if (chain != null)
                {
                    chainBlocks.addAll(chain);
                }
                Set<IRBlock> rangeBlocks = new HashSet<>();
                for (ExceptionHandler h : hbEntries)
                {
                    if (h.getTryStart() == null || h.getTryEnd() == null)
                    {
                        continue;
                    }
                    int lo = h.getTryStart().getBytecodeOffset();
                    int hi = h.getTryEnd().getBytecodeOffset();
                    for (IRBlock b : context.getIrMethod().getBlocks())
                    {
                        if (b.getBytecodeOffset() >= lo && b.getBytecodeOffset() < hi)
                        {
                            rangeBlocks.add(b);
                        }
                    }
                }
                for (IRBlock b : context.getIrMethod().getBlocks())
                {
                    if (chainBlocks.contains(b) || probeTemplateStart(b, template) < 0)
                    {
                        continue;
                    }
                    if (!rangeBlocks.contains(b))
                    {
                        return true;
                    }
                    if (b.getTerminator() instanceof ReturnInstruction)
                    {
                        return true;
                    }
                    for (IRBlock succ : b.getSuccessors())
                    {
                        if (!rangeBlocks.contains(succ) && !chainBlocks.contains(succ) && succ != hb)
                        {
                            return true;
                        }
                    }
                }
            }
        }
        finally
        {
            extendedFinallyDedup = savedEvidenceExtended;
        }
        for (ExceptionHandler h : hbEntries)
        {
            if (h.isCatchAll())
            {
                return true;
            }
        }
        for (ExceptionHandler d : finallyDeduped)
        {
            if (d.getHandlerBlock() == hb)
            {
                return true;
            }
        }
        Deque<IRBlock> work = new ArrayDeque<>();
        Set<IRBlock> seen = new HashSet<>();
        work.add(hb);
        int budget = 40;
        while (!work.isEmpty() && budget-- > 0)
        {
            IRBlock b = work.poll();
            if (!seen.add(b))
            {
                continue;
            }
            for (IRInstruction ins : b.getInstructions())
            {
                if (ins instanceof SimpleInstruction && ((SimpleInstruction) ins).getOp() == SimpleOp.MONITOREXIT)
                {
                    return true;
                }
            }
            work.addAll(b.getSuccessors());
        }
        if (TRACE)
        {
            trace("clause-evidence NONE handler=" + hb.getBytecodeOffset());
        }
        return false;
    }

    /**
     * Whether {@code s} is a rethrow, or a structure whose exit ends in one.
     */
    private boolean endsInRethrow(Statement s)
    {
        if (s instanceof ThrowStmt)
        {
            return true;
        }
        if (s instanceof BlockStmt)
        {
            List<Statement> inner = ((BlockStmt) s).getStatements();
            return !inner.isEmpty() && endsInRethrow(inner.get(inner.size() - 1));
        }
        if (s instanceof WhileStmt)
        {
            return containsRethrow(((WhileStmt) s).getBody());
        }
        if (s instanceof IfStmt)
        {
            IfStmt ifs = (IfStmt) s;
            return ifs.getThenBranch() != null && endsInRethrow(ifs.getThenBranch())
                    && ifs.getElseBranch() != null && endsInRethrow(ifs.getElseBranch());
        }
        return false;
    }

    /**
     * Whether the statement tree contains a throw on some path.
     */
    private boolean containsRethrow(Statement s)
    {
        if (s instanceof ThrowStmt)
        {
            return true;
        }
        if (s instanceof BlockStmt)
        {
            for (Statement inner : ((BlockStmt) s).getStatements())
            {
                if (containsRethrow(inner))
                {
                    return true;
                }
            }
            return false;
        }
        if (s instanceof IfStmt)
        {
            IfStmt ifs = (IfStmt) s;
            return (ifs.getThenBranch() != null && containsRethrow(ifs.getThenBranch()))
                    || (ifs.getElseBranch() != null && containsRethrow(ifs.getElseBranch()));
        }
        if (s instanceof WhileStmt)
        {
            return containsRethrow(((WhileStmt) s).getBody());
        }
        return false;
    }

    /**
     * The rethrow ending {@code s} - the statement itself, or the one nested at the end of its structure.
     */
    private ThrowStmt trailingRethrow(Statement s)
    {
        if (s instanceof ThrowStmt)
        {
            return (ThrowStmt) s;
        }
        if (s instanceof BlockStmt)
        {
            List<Statement> inner = ((BlockStmt) s).getStatements();
            return inner.isEmpty() ? null : trailingRethrow(inner.get(inner.size() - 1));
        }
        if (s instanceof WhileStmt)
        {
            return firstNestedThrow(((WhileStmt) s).getBody());
        }
        if (s instanceof IfStmt)
        {
            IfStmt ifs = (IfStmt) s;
            ThrowStmt t = ifs.getThenBranch() == null ? null : trailingRethrow(ifs.getThenBranch());
            return t != null ? t
                    : (ifs.getElseBranch() == null ? null : trailingRethrow(ifs.getElseBranch()));
        }
        return null;
    }

    /**
     * The first throw in the statement tree, or null.
     */
    private ThrowStmt firstNestedThrow(Statement s)
    {
        if (s instanceof ThrowStmt)
        {
            return (ThrowStmt) s;
        }
        if (s instanceof BlockStmt)
        {
            for (Statement inner : ((BlockStmt) s).getStatements())
            {
                ThrowStmt t = firstNestedThrow(inner);
                if (t != null)
                {
                    return t;
                }
            }
            return null;
        }
        if (s instanceof IfStmt)
        {
            IfStmt ifs = (IfStmt) s;
            ThrowStmt t = ifs.getThenBranch() == null ? null : firstNestedThrow(ifs.getThenBranch());
            return t != null ? t
                    : (ifs.getElseBranch() == null ? null : firstNestedThrow(ifs.getElseBranch()));
        }
        if (s instanceof WhileStmt)
        {
            return firstNestedThrow(((WhileStmt) s).getBody());
        }
        return null;
    }

    private boolean isFinallyRethrowPattern(CatchClause clause)
    {
        if (clause == null) return false;

        SourceType type = clause.getPrimaryType();
        if (!(type instanceof ReferenceSourceType)) return false;

        String typeName = ((ReferenceSourceType) type).getInternalName();
        if (!typeName.equals("java/lang/Throwable") && !typeName.equals("Throwable"))
        {
            return false;
        }

        Statement body = clause.body();
        if (!(body instanceof BlockStmt)) return false;

        List<Statement> stmts = ((BlockStmt) body).getStatements();
        if (stmts.isEmpty()) return false;

        // The rethrow ends the clause - as its trailing statement, or nested at the end of a structure
        // when the clause body carries control flow.
        ThrowStmt throwStmt = trailingRethrow(stmts.get(stmts.size() - 1));
        if (throwStmt == null)
        {
            return false;
        }
        Expression thrown = throwStmt.getException();

        if (thrown instanceof VarRefExpr)
        {
            String thrownVar = ((VarRefExpr) thrown).getName();
            return thrownVar.equals(clause.variableName());
        }

        return false;
    }

    /**
     * Extracts the finally body from a finally-rethrow catch clause.
     */
    private BlockStmt extractFinallyBody(CatchClause clause)
    {
        if (clause == null || !(clause.body() instanceof BlockStmt))
        {
            return new BlockStmt(Collections.emptyList());
        }

        List<Statement> stmts = ((BlockStmt) clause.body()).getStatements();
        if (stmts.isEmpty())
        {
            return new BlockStmt(Collections.emptyList());
        }

        // The clause ends in its rethrow, which the finally body drops. A clause whose body CARRIES
        // CONTROL FLOW keeps that rethrow nested inside the recovered structure (a loop-carrying finally
        // recovers as `while (true) { ...; throw e; }`), so dropping the trailing statement would discard
        // the whole structure. Strip the rethrow in place instead, leaving the body intact.
        Statement last = stmts.get(stmts.size() - 1);
        List<Statement> finallyStmts;
        if (last instanceof ThrowStmt)
        {
            finallyStmts = new ArrayList<>(stmts.subList(0, stmts.size() - 1));
        }
        else
        {
            finallyStmts = new ArrayList<>();
            for (Statement st : stmts)
            {
                Statement stripped = stripTrailingRethrow(st);
                if (stripped != null)
                {
                    finallyStmts.add(stripped);
                }
            }
        }
        finallyStmts.replaceAll(this::unwrapSuppressScaffold);
        return new BlockStmt(finallyStmts);
    }

    /**
     * Removes the caught-exception rethrow ending a recovered clause structure, keeping the structure.
     */
    private Statement stripTrailingRethrow(Statement s)
    {
        return stripTrailingRethrow(s, false);
    }

    /**
     * As above; {@code inLoop} marks that the rethrow being removed is a LOOP EXIT - dropping it outright
     * would leave the loop endless, so it becomes a {@code break}.
     */
    private Statement stripTrailingRethrow(Statement s, boolean inLoop)
    {
        if (s instanceof ThrowStmt)
        {
            return inLoop ? new BreakStmt() : null;
        }
        if (s instanceof BlockStmt)
        {
            List<Statement> kept = new ArrayList<>();
            for (Statement inner : ((BlockStmt) s).getStatements())
            {
                Statement stripped = stripTrailingRethrow(inner, inLoop);
                if (stripped != null)
                {
                    kept.add(stripped);
                }
            }
            return kept.isEmpty() ? null : new BlockStmt(kept);
        }
        if (s instanceof IfStmt)
        {
            IfStmt ifs = (IfStmt) s;
            Statement then = ifs.getThenBranch() == null ? null : stripTrailingRethrow(ifs.getThenBranch(), inLoop);
            Statement els = ifs.getElseBranch() == null ? null : stripTrailingRethrow(ifs.getElseBranch(), inLoop);
            if (then == null && els == null)
            {
                return null;
            }
            if (then == null)
            {
                return new IfStmt(invertCondition(ifs.getCondition()), els, null, ifs.getLocation());
            }
            return new IfStmt(ifs.getCondition(), then, els, ifs.getLocation());
        }
        if (s instanceof WhileStmt)
        {
            WhileStmt w = (WhileStmt) s;
            Statement body = stripTrailingRethrow(w.getBody(), true);
            return body == null ? null : new WhileStmt(w.getCondition(), body, w.getLabel());
        }
        return s;
    }


    private List<Statement> filterOrphanFinallyThrows(List<Statement> statements, Set<String> finallyExceptionVars)
    {
        List<Statement> filtered = new ArrayList<>();
        for (Statement stmt : statements)
        {
            if (stmt instanceof ThrowStmt)
            {
                ThrowStmt throwStmt = (ThrowStmt) stmt;
                Expression exception = throwStmt.getException();
                if (exception instanceof VarRefExpr)
                {
                    String varName = ((VarRefExpr) exception).getName();
                    if (finallyExceptionVars.contains(varName))
                    {
                        continue;
                    }
                }
            }
            filtered.add(stmt);
        }
        return filtered;
    }

    /**
     * Strips the inlined finally copies from user catch bodies.
     */
    private List<CatchClause> filterInlinedFinallyFromCatches(List<CatchClause> catches, List<Statement> finallyStmts)
    {
        List<CatchClause> out = new ArrayList<>(catches.size());
        for (CatchClause clause : catches)
        {
            if (!(clause.body() instanceof BlockStmt))
            {
                out.add(clause);
                continue;
            }
            List<Statement> folded = filterInlinedFinallyFromTryStatements(
                    ((BlockStmt) clause.body()).getStatements(), finallyStmts);
            // A catch that falls through to the join carries its copy as the body's TAIL (no return after
            // it for the generic fold to anchor on); the real finally clause runs at the join, so the
            // trailing copy folds away.
            int n = folded.size();
            int k = finallyStmts.size();
            if (n >= k && isStatementSequenceMatchingFinally(folded, n - k, finallyStmts))
            {
                folded = new ArrayList<>(folded.subList(0, n - k));
            }
            out.add(new CatchClause(clause.exceptionTypes(), clause.variableName(), new BlockStmt(folded)));
        }
        return out;
    }

    /**
     * Diagnostic kill switch for the statement-level inlined-finally folds.
     */
    private static final boolean FOLDS_DISABLED = System.getProperty("yabr.disable.finally.folds") != null;

    private List<Statement> filterInlinedFinallyFromTryStatements(List<Statement> statements, List<Statement> finallyStmts)
    {
        if (FOLDS_DISABLED || finallyStmts == null || finallyStmts.isEmpty())
        {
            return statements;
        }
        List<Statement> result = new ArrayList<>();
        int i = 0;
        while (i < statements.size())
        {
            Statement stmt = statements.get(i);

            if (stmt instanceof ReturnStmt || stmt instanceof ThrowStmt)
            {
                result.add(stmt);
                i++;
                continue;
            }

            if (stmt instanceof IfStmt)
            {
                if (isStatementSequenceMatchingFinally(statements, i, finallyStmts))
                {
                    i += finallyStmts.size();
                    continue;
                }
                int invertedEnd = matchInvertedFinallyCopy(statements, i, finallyStmts);
                if (invertedEnd >= 0)
                {
                    result.add(flattenToStatements(((IfStmt) stmt).getThenBranch()).get(0));
                    i = invertedEnd;
                    continue;
                }
                IfStmt ifStmt = (IfStmt) stmt;
                Statement newThen = filterInlinedFinallyFromBranch(ifStmt.getThenBranch(), finallyStmts);
                Statement newElse = ifStmt.getElseBranch() != null
                    ? filterInlinedFinallyFromBranch(ifStmt.getElseBranch(), finallyStmts)
                    : null;
                IfStmt rebuiltIf = new IfStmt(ifStmt.getCondition(), newThen, newElse);
                Locations.copy(ifStmt, rebuiltIf);
                result.add(rebuiltIf);
                i++;
                continue;
            }

            if (stmt instanceof WhileStmt)
            {
                WhileStmt whileStmt = (WhileStmt) stmt;
                Statement newBody = filterInlinedFinallyFromBranch(whileStmt.getBody(), finallyStmts);
                WhileStmt rebuiltWhile = new WhileStmt(whileStmt.getCondition(), newBody);
                Locations.copy(whileStmt, rebuiltWhile);
                result.add(rebuiltWhile);
                i++;
                continue;
            }

            if (stmt instanceof TryCatchStmt)
            {
                // A finally body that itself is a try/catch (a guarded call) makes each inlined copy a
                // TryCatchStmt too - test the copy match BEFORE descending, or the descent consumes the
                // statement and the copy is never folded.
                if (isStatementSequenceMatchingFinally(statements, i, finallyStmts))
                {
                    int nextIdx = i + finallyStmts.size();
                    Statement next = nextIdx < statements.size() ? statements.get(nextIdx) : null;
                    if (next == null || next instanceof ReturnStmt || next instanceof ThrowStmt
                            || next instanceof BreakStmt || next instanceof ContinueStmt)
                    {
                        i = nextIdx;
                        continue;
                    }
                }
                // javac splits an outer finally's protected range around the returns in its try, so the
                // range's pieces can recover as a nested try/catch whose body and catch clauses still carry
                // the outer finally's inlined copies before their returns. Fold inside the nested construct
                // too - its try body and catch bodies are part of the same protected range. The nested
                // construct's own finally clause is left alone (it is a different finally, not a copy site).
                TryCatchStmt tcs = (TryCatchStmt) stmt;
                Statement newTry = filterInlinedFinallyFromBranch(tcs.getTryBlock(), finallyStmts);
                List<CatchClause> newCatches = new ArrayList<>();
                for (CatchClause cc : tcs.getCatches())
                {
                    newCatches.add(new CatchClause(cc.exceptionTypes(), cc.variableName(),
                            filterInlinedFinallyFromBranch(cc.body(), finallyStmts)));
                }
                TryCatchStmt rebuiltTry = new TryCatchStmt(
                        newTry instanceof BlockStmt ? newTry : new BlockStmt(Collections.singletonList(newTry)),
                        newCatches, tcs.getFinallyBlock());
                Locations.copy(tcs, rebuiltTry);
                result.add(rebuiltTry);
                i++;
                continue;
            }

            if (stmt instanceof SwitchStmt)
            {
                // javac splits the finally's protected range around the returns in switch arms, so
                // an arm that leaves the try normally carries the inlined copy before its return.
                // Left in place next to the extracted finally clause, the copy runs the cleanup a
                // second time on that arm.
                SwitchStmt sw = (SwitchStmt) stmt;
                List<SwitchCase> newCases = new ArrayList<>();
                for (SwitchCase c : sw.getCases())
                {
                    newCases.add(new SwitchCase(c.labels(), c.expressionLabels(), c.isDefault(),
                            filterInlinedFinallyFromTryStatements(c.statements(), finallyStmts)));
                }
                SwitchStmt rebuiltSwitch = new SwitchStmt(sw.getSelector(), newCases);
                Locations.copy(sw, rebuiltSwitch);
                result.add(rebuiltSwitch);
                i++;
                continue;
            }

            if (stmt instanceof DoWhileStmt)
            {
                DoWhileStmt doWhileStmt = (DoWhileStmt) stmt;
                Statement newBody = filterInlinedFinallyFromBranch(doWhileStmt.getBody(), finallyStmts);
                DoWhileStmt rebuiltDoWhile = new DoWhileStmt(newBody, doWhileStmt.getCondition());
                Locations.copy(doWhileStmt, rebuiltDoWhile);
                result.add(rebuiltDoWhile);
                i++;
                continue;
            }

            if (stmt instanceof ForStmt)
            {
                ForStmt forStmt = (ForStmt) stmt;
                Statement newBody = filterInlinedFinallyFromBranch(forStmt.getBody(), finallyStmts);
                ForStmt rebuiltFor = new ForStmt(forStmt.getInit(), forStmt.getCondition(), forStmt.getUpdate(), newBody);
                Locations.copy(forStmt, rebuiltFor);
                result.add(rebuiltFor);
                i++;
                continue;
            }

            if (stmt instanceof BlockStmt)
            {
                BlockStmt blockStmt = (BlockStmt) stmt;
                List<Statement> filteredBlock = filterInlinedFinallyFromTryStatements(blockStmt.getStatements(), finallyStmts);
                result.add(new BlockStmt(filteredBlock));
                i++;
                continue;
            }

            if (isStatementSequenceMatchingFinally(statements, i, finallyStmts))
            {
                int nextIdx = i + finallyStmts.size();
                if (nextIdx < statements.size())
                {
                    Statement next = statements.get(nextIdx);
                    // javac inlines the finally before EVERY abrupt exit of the protected range, not only
                    // returns and throws: a break or continue out of the try runs the finally too. Fold the
                    // copy away before those as well - correct because the finally is re-emitted when the
                    // break/continue is lowered (the enclosing loop's cleanup drain).
                    if (next instanceof ReturnStmt || next instanceof ThrowStmt
                            || next instanceof BreakStmt || next instanceof ContinueStmt)
                    {
                        i = nextIdx;
                        continue;
                    }
                }
                else
                {
                    // A trailing copy with nothing after it is the fall-through exit's inlined finally: the
                    // body falls through into the finally clause, which runs the same statements again.
                    i = nextIdx;
                    continue;
                }
            }

            result.add(stmt);
            i++;
        }
        // Folding a copy out can leave residue stranded after a try/catch whose every path now returns -
        // the continuation's own return the try absorbed, or another exit's guard-dropped copy. Java
        // rejects unreachable code outright, so statements after a terminating try/catch at the same level
        // can only be recovery residue; lowering them produces dead blocks that fail verification.
        for (int j = 0; j < result.size() - 1; j++)
        {
            if (result.get(j) instanceof TryCatchStmt && isTerminatingTryCatch((TryCatchStmt) result.get(j)))
            {
                result.subList(j + 1, result.size()).clear();
                break;
            }
        }
        return result;
    }

    private Statement filterInlinedFinallyFromBranch(Statement branch, List<Statement> finallyStmts)
    {
        if (branch == null) return null;

        if (branch instanceof BlockStmt)
        {
            List<Statement> filtered = filterInlinedFinallyFromTryStatements(
                ((BlockStmt) branch).getStatements(), finallyStmts);
            return new BlockStmt(filtered);
        }

        List<Statement> singleStmt = new ArrayList<>();
        singleStmt.add(branch);
        List<Statement> filtered = filterInlinedFinallyFromTryStatements(singleStmt, finallyStmts);
        if (filtered.isEmpty())
        {
            return new BlockStmt(Collections.emptyList());
        }
        if (filtered.size() == 1)
        {
            return filtered.get(0);
        }
        return new BlockStmt(filtered);
    }

    /**
     * True when any block of the handler's dominator subtree ends in a conditional branch.
     */
    private boolean handlerSubtreeBranches(IRBlock handlerBlock)
    {
        DominatorTree dt = context.getDominatorTree();
        if (dt == null)
        {
            return false;
        }
        // A SWITCH branches too: a clause whose body is a switch needs the structured recovery just as
        // much as one holding an if - the flat successor walk appends the case bodies in set order and
        // collapses the construct to a single case.
        if (isBranching(handlerBlock))
        {
            return true;
        }
        for (IRBlock b : context.getIrMethod().getBlocks())
        {
            if (dt.dominates(handlerBlock, b) && isBranching(b))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code b} ends in a multi-way transfer - a conditional branch or a switch.
     */
    private boolean isBranching(IRBlock b)
    {
        IRInstruction term = b.getTerminator();
        return term instanceof BranchInstruction || term instanceof SwitchInstruction;
    }

    /**
     * Matches javac's normal-exit inlining of a single-if finally recovered as an inverted guard clause.
     */
    private int matchInvertedFinallyCopy(List<Statement> statements, int i, List<Statement> finallyStmts)
    {
        if (finallyStmts.size() != 1 || !(finallyStmts.get(0) instanceof IfStmt))
        {
            return -1;
        }
        IfStmt template = (IfStmt) finallyStmts.get(0);
        if (template.getElseBranch() != null)
        {
            return -1;
        }
        IfStmt guard = (IfStmt) statements.get(i);
        if (guard.getElseBranch() != null || !isComplementaryCondition(guard.getCondition(), template.getCondition()))
        {
            return -1;
        }
        List<Statement> jump = flattenToStatements(guard.getThenBranch());
        if (jump.size() != 1)
        {
            return -1;
        }
        Statement j = jump.get(0);
        if (!(j instanceof ReturnStmt || j instanceof ThrowStmt
                || j instanceof BreakStmt || j instanceof ContinueStmt))
        {
            return -1;
        }
        List<Statement> body = flattenToStatements(template.getThenBranch());
        if (body.isEmpty() || i + 1 + body.size() > statements.size())
        {
            return -1;
        }
        for (int k = 0; k < body.size(); k++)
        {
            if (!statementsMatch(body.get(k), statements.get(i + 1 + k)))
            {
                return -1;
            }
        }
        return i + 1 + body.size();
    }

    private List<Statement> flattenToStatements(Statement s)
    {
        if (s instanceof BlockStmt)
        {
            return ((BlockStmt) s).getStatements();
        }
        return Collections.singletonList(s);
    }

    /**
     * True when the two conditions are logical complements over matching operands (e.g. == vs !=).
     */
    private boolean isComplementaryCondition(Expression a, Expression b)
    {
        if (!(a instanceof BinaryExpr) || !(b instanceof BinaryExpr))
        {
            return false;
        }
        BinaryExpr ba = (BinaryExpr) a;
        BinaryExpr bb = (BinaryExpr) b;
        if (!expressionsMatch(ba.getLeft(), bb.getLeft()) || !expressionsMatch(ba.getRight(), bb.getRight()))
        {
            return false;
        }
        BinaryOperator x = ba.getOperator();
        BinaryOperator y = bb.getOperator();
        return (x == BinaryOperator.EQ && y == BinaryOperator.NE)
                || (x == BinaryOperator.NE && y == BinaryOperator.EQ)
                || (x == BinaryOperator.LT && y == BinaryOperator.GE)
                || (x == BinaryOperator.GE && y == BinaryOperator.LT)
                || (x == BinaryOperator.GT && y == BinaryOperator.LE)
                || (x == BinaryOperator.LE && y == BinaryOperator.GT);
    }

    private boolean isStatementSequenceMatchingFinally(List<Statement> statements, int startIdx, List<Statement> finallyStmts)
    {
        if (startIdx + finallyStmts.size() > statements.size())
        {
            return false;
        }
        Map<String, String> tempBind = new HashMap<>();
        for (int i = 0; i < finallyStmts.size(); i++)
        {
            if (!statementMatchesFinally(statements.get(startIdx + i), finallyStmts.get(i), tempBind))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Structural match of a candidate statement against a finally-body statement, tolerating a bijection between
     * the two copies' locally-declared temp names.
     */
    private boolean statementMatchesFinally(Statement cand, Statement fin, Map<String, String> bind)
    {
        if (cand == null || fin == null)
        {
            return false;
        }
        // A guard-dropped copy: the finally is `if (x != null) { <cleanup> }` but the copy on a path where
        // the guard is decided lost its if-shell (the CFG-level de-dup excised the guard block), leaving the
        // bare cleanup. Match the copy against the guard's then-branch.
        if (fin instanceof IfStmt && !(cand instanceof IfStmt) && ((IfStmt) fin).getElseBranch() == null)
        {
            List<Statement> thenStmts = flattenToStatements(((IfStmt) fin).getThenBranch());
            if (thenStmts.size() == 1 && statementMatchesFinally(cand, thenStmts.get(0), bind))
            {
                return true;
            }
        }
        if (cand.getClass() != fin.getClass())
        {
            return false;
        }
        if (fin instanceof VarDeclStmt)
        {
            VarDeclStmt fd = (VarDeclStmt) fin;
            VarDeclStmt cd = (VarDeclStmt) cand;
            Expression fi = fd.getInitializer();
            Expression ci = cd.getInitializer();
            if ((fi == null) != (ci == null))
            {
                return false;
            }
            if (fi != null && !expressionMatchesFinally(ci, fi, bind))
            {
                return false;
            }
            bind.put(fd.getName(), cd.getName());
            return true;
        }
        if (fin instanceof ExprStmt)
        {
            return expressionMatchesFinally(((ExprStmt) cand).getExpression(), ((ExprStmt) fin).getExpression(), bind);
        }
        if (fin instanceof ReturnStmt)
        {
            Expression ce = ((ReturnStmt) cand).getValue();
            Expression fe = ((ReturnStmt) fin).getValue();
            if (ce == null && fe == null)
            {
                return true;
            }
            return ce != null && fe != null && expressionMatchesFinally(ce, fe, bind);
        }
        if (fin instanceof TryCatchStmt)
        {
            // A finally body that itself contains a try/catch (a guarded call inside the finally) is inlined
            // with FRESH catch-variable names in each copy, so the name-sensitive generic match never sees
            // the copies. Match structurally, binding each clause's exception variable like a declared temp.
            TryCatchStmt ft = (TryCatchStmt) fin;
            TryCatchStmt ct = (TryCatchStmt) cand;
            if (ft.getCatches().size() != ct.getCatches().size()
                    || (ft.getFinallyBlock() == null) != (ct.getFinallyBlock() == null)
                    || !blockMatchesFinally(ct.getTryBlock(), ft.getTryBlock(), bind, false))
            {
                return false;
            }
            for (int ci = 0; ci < ft.getCatches().size(); ci++)
            {
                CatchClause fc = ft.getCatches().get(ci);
                CatchClause cc = ct.getCatches().get(ci);
                if (fc.exceptionTypes().size() != cc.exceptionTypes().size())
                {
                    return false;
                }
                bind.put(fc.variableName(), cc.variableName());
                // The copy's catch may absorb the exit's own return/throw as its tail (the clause falls
                // through to the exit the copy was inlined before); tolerate that one trailing statement.
                if (!blockMatchesFinally(cc.body(), fc.body(), bind, true))
                {
                    return false;
                }
            }
            return ft.getFinallyBlock() == null
                    || blockMatchesFinally(ct.getFinallyBlock(), ft.getFinallyBlock(), bind, false);
        }
        return statementsMatch(cand, fin);
    }

    /**
     * Element-wise {@link #statementMatchesFinally} over two statement bodies (blocks or single statements).
     */
    private boolean blockMatchesFinally(Statement cand, Statement fin, Map<String, String> bind, boolean tolerateTrailingExit)
    {
        List<Statement> cs = flattenToStatements(cand);
        List<Statement> fs = flattenToStatements(fin);
        if (cs.size() != fs.size())
        {
            if (!tolerateTrailingExit || cs.size() != fs.size() + 1
                    || !(cs.get(cs.size() - 1) instanceof ReturnStmt
                        || cs.get(cs.size() - 1) instanceof ThrowStmt))
            {
                return false;
            }
        }
        for (int i = 0; i < fs.size(); i++)
        {
            if (!statementMatchesFinally(cs.get(i), fs.get(i), bind))
            {
                return false;
            }
        }
        return true;
    }

    private boolean expressionMatchesFinally(Expression cand, Expression fin, Map<String, String> bind)
    {
        if (cand == null || fin == null)
        {
            return cand == fin;
        }
        if (cand.getClass() != fin.getClass())
        {
            return false;
        }
        if (fin instanceof VarRefExpr)
        {
            String finName = ((VarRefExpr) fin).getName();
            String candName = ((VarRefExpr) cand).getName();
            String bound = bind.get(finName);
            return bound != null ? bound.equals(candName) : finName.equals(candName);
        }
        if (fin instanceof BinaryExpr)
        {
            BinaryExpr fb = (BinaryExpr) fin;
            BinaryExpr cb = (BinaryExpr) cand;
            return fb.getOperator() == cb.getOperator()
                && expressionMatchesFinally(cb.getLeft(), fb.getLeft(), bind)
                && expressionMatchesFinally(cb.getRight(), fb.getRight(), bind);
        }
        if (fin instanceof LiteralExpr)
        {
            return Objects.equals(((LiteralExpr) cand).getValue(), ((LiteralExpr) fin).getValue());
        }
        return cand.toString().equals(fin.toString());
    }

    /**
     * Recovers the blocks between a protected region's end and its handler - the finally inlined on the normal
     * exit path followed by the region's real continuation - then strips the inlined-finally copies.
     */
    private List<Statement> recoverFinallyGap(IRBlock tryEnd, IRBlock handlerBlock, BlockStmt finallyBlock, Set<String> finallyExceptionVars)
    {
        IRMethod irMethod = context.getIrMethod();
        int tryEndOffset = tryEnd.getBytecodeOffset();
        int handlerOffset = handlerBlock.getBytecodeOffset();

        IRBlock gapStart = null;
        Set<IRBlock> stopBlocks = new HashSet<>();
        for (IRBlock block : irMethod.getBlocks())
        {
            int offset = block.getBytecodeOffset();
            if (offset >= handlerOffset)
            {
                stopBlocks.add(block);
            }
            else if (offset >= tryEndOffset && (gapStart == null || offset < gapStart.getBytecodeOffset()))
            {
                gapStart = block;
            }
        }
        if (gapStart == null)
        {
            return Collections.emptyList();
        }

        List<Statement> gapStmts;
        context.pushStopBlocks(stopBlocks);
        try
        {
            gapStmts = recoverBlockSequence(gapStart, stopBlocks);
        }
        finally
        {
            context.popStopBlocks();
        }
        gapStmts = filterOrphanFinallyThrows(gapStmts, finallyExceptionVars);
        gapStmts = filterInlinedFinallyFromTryStatements(gapStmts, finallyBlock.getStatements());
        // Drop the standalone inlined-finally copy the gap carries (the try's normal-exit copy not folded
        // into a preceding return above), so a side-effecting finally like `x += 2` appears only in the
        // finally, not also in the body. Match it as a WHOLE temp-bound sequence, not per statement: the
        // finally materializes its value into a single-use temp whose name differs between copies
        // (`int i13 = x+1; x = i13` versus the finally's `int i16 = x+1; x = i16`), so a per-statement
        // match drops the name-insensitive declaration but keeps the store that reads it - orphaning the
        // temp (an undefined-variable recompile failure).
        List<Statement> finStmts = finallyBlock.getStatements();
        if (finStmts.isEmpty())
        {
            return gapStmts;
        }
        List<Statement> deduped = new ArrayList<>();
        int gi = 0;
        while (gi < gapStmts.size())
        {
            if (isStatementSequenceMatchingFinally(gapStmts, gi, finStmts))
            {
                gi += finStmts.size();
                continue;
            }
            if (isStatementInFinallyBlock(gapStmts.get(gi), finStmts))
            {
                gi++;
                continue;
            }
            deduped.add(gapStmts.get(gi));
            gi++;
        }
        return deduped;
    }

    private boolean isStatementInFinallyBlock(Statement stmt, List<Statement> finallyStmts)
    {
        if (finallyStmts.isEmpty())
        {
            return false;
        }

        for (Statement finallyStmt : finallyStmts)
        {
            if (statementsMatch(stmt, finallyStmt))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * A return block whose every live predecessor is either inside the clause's dominated subtree or an EMPTIED
     * inlined-finally copy block.
     */
    private boolean isCatchExclusiveTail(IRBlock block, IRBlock catchEntry, DominatorTree dt)
    {
        if (!(block.getTerminator() instanceof ReturnInstruction))
        {
            return false;
        }
        for (IRBlock p : block.getPredecessors())
        {
            if (dt.dominates(catchEntry, p))
            {
                continue;
            }
            if (!excisedFinallyCopyBlocks.contains(p))
            {
                return false;
            }
        }
        return true;
    }

    private void recoverHandlerBlocks(Collection<IRBlock> successors, Set<IRBlock> visited, List<Statement> stmts, IRBlock catchEntry)
    {
        DominatorTree dt = context.getDominatorTree();
        for (IRBlock block : successors)
        {
            if (visited.contains(block)) continue;
            if (catchEntry != null && dt != null && block != catchEntry && !dt.dominates(catchEntry, block)
                    && !isCatchExclusiveTail(block, catchEntry, dt))
            {
                continue; // outside the catch body - the shared post-try-catch merge
            }

            ExceptionHandler nested = findHandlerStartingAt(block);
            if (nested != null && !processedTryHandlers.contains(nested)
                    && !processedHandlerBlocks.contains(nested.getHandlerBlock()))
            {
                Set<ExceptionHandler> savedTry = new HashSet<>(processedTryHandlers);
                Set<IRBlock> savedBlocks = new HashSet<>(processedHandlerBlocks);
                processedTryHandlers.add(nested);
                if (nested.getHandlerBlock() != null)
                {
                    processedHandlerBlocks.add(nested.getHandlerBlock());
                }
                Set<IRBlock> nestedVisited = new HashSet<>(visited);
                Statement recovered;
                try
                {
                    recovered = recoverTryCatch(block, nested, new HashSet<>(), nestedVisited);
                }
                catch (RuntimeException ex)
                {
                    recovered = null;
                }
                if (recovered != null)
                {
                    stmts.add(recovered);
                    visited.addAll(nestedVisited);
                    if (!isTerminatingRecoveredTry(recovered))
                    {
                        IRBlock after = findBlockAfterTryCatch(nested, visited);
                        if (after != null && !visited.contains(after))
                        {
                            recoverHandlerBlocks(Collections.singletonList(after), visited, stmts, catchEntry);
                        }
                    }
                    continue;
                }
                processedTryHandlers.clear();
                processedTryHandlers.addAll(savedTry);
                processedHandlerBlocks.clear();
                processedHandlerBlocks.addAll(savedBlocks);
            }

            visited.add(block);

            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof CopyInstruction) continue;
                Statement stmt = recoverInstruction(instr);
                if (stmt != null)
                {
                    stmts.add(stmt);
                }
            }

            IRInstruction terminator = block.getTerminator();
            boolean isThrow = false;
            boolean isReturn = terminator instanceof ReturnInstruction;
            boolean isGoto = false;

            if (!isReturn && terminator instanceof SimpleInstruction)
            {
                SimpleInstruction simple = (SimpleInstruction) terminator;
                if (simple.getOp() == SimpleOp.ATHROW)
                {
                    isThrow = true;
                }
                else if (simple.getOp() == SimpleOp.GOTO)
                {
                    isGoto = true;
                }
            }

            if (isThrow)
            {
                SimpleInstruction simple = (SimpleInstruction) terminator;
                Expression exception = exprRecoverer.recoverOperand(simple.getOperand());
                Statement throwStmt = new ThrowStmt(exception);
                stamp(throwStmt, simple);
                stmts.add(throwStmt);
                continue;
            }

            if (isReturn)
            {
                ReturnInstruction ret = (ReturnInstruction) terminator;
                Statement returnStmt = recoverReturn(ret);
                stamp(returnStmt, ret);
                stmts.add(returnStmt);
                continue;
            }

            if (isGoto)
            {
                // A goto normally leaves the clause for the shared merge and stops the walk. But when it
                // resolves - through the emptied inlined-finally copy chain - to the clause's OWN return
                // (shared-looking only because the copy's now-emptied mirrored handler also flowed into
                // it pre-excision), stopping would drop that return and the clause would wrongly fall
                // through to the construct's continuation.
                IRBlock tgt = singleNormalSuccessor(block);
                if (tgt != null)
                {
                    tgt = resolveThroughEmptyChain(tgt);
                }
                if (tgt != null && !visited.contains(tgt) && dt != null && catchEntry != null
                        && excisedFinallyCopyBlocks.contains(block)
                        && tgt.getTerminator() instanceof ReturnInstruction
                        && (dt.dominates(catchEntry, tgt) || isCatchExclusiveTail(tgt, catchEntry, dt)))
                {
                    recoverHandlerBlocks(Collections.singletonList(tgt), visited, stmts, catchEntry);
                }
                continue;
            }

            recoverHandlerBlocks(block.getSuccessors(), visited, stmts, catchEntry);
        }
    }

    /**
     * Finds the exception variable name from the handler block.
     */
    private String findExceptionVariableName(IRBlock handlerBlock)
    {
        SlotVariablePartition partition = context.getExpressionContext().getSlotPartition();
        for (IRInstruction instr : handlerBlock.getInstructions())
        {
            if (instr instanceof StoreLocalInstruction)
            {
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                // The partition names the slot this store writes, so a caught exception is called what the
                // source called it. Composing the name from the slot number here ignored that entirely, and
                // named the variable `localN` even where the class records a name for it.
                String named = partition == null ? null : partition.nameForStore(store);
                return named != null ? named : "local" + store.getLocalIndex();
            }
            if (instr instanceof CopyInstruction)
            {
                CopyInstruction copy = (CopyInstruction) instr;
                SSAValue result = copy.getResult();
                if (result != null)
                {
                    String name = context.getExpressionContext().getVariableName(result);
                    if (name != null)
                    {
                        return name;
                    }
                }
            }
            break;
        }
        return null;
    }

    /**
     * Collects all SSA values that represent the caught exception in a handler block.
     */
    private Set<SSAValue> collectExceptionValues(IRBlock handlerBlock)
    {
        Set<SSAValue> exceptionValues = new HashSet<>();
        for (IRInstruction instr : handlerBlock.getInstructions())
        {
            if (instr instanceof CopyInstruction)
            {
                CopyInstruction copy = (CopyInstruction) instr;
                if (copy.getResult() != null)
                {
                    exceptionValues.add(copy.getResult());
                }
                if (copy.getSource() instanceof SSAValue)
                {
                    SSAValue ssaSrc = (SSAValue) copy.getSource();
                    exceptionValues.add(ssaSrc);
                }
            }
            SSAValue result = instr.getResult();
            if (result != null)
            {
                String name = result.getName();
                if (name != null && name.startsWith("exc_"))
                {
                    exceptionValues.add(result);
                }
            }
            if (instr instanceof StoreLocalInstruction)
            {
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                if (store.getValue() instanceof SSAValue)
                {
                    SSAValue ssaValue = (SSAValue) store.getValue();
                    exceptionValues.add(ssaValue);
                }
                break;
            }
        }
        return exceptionValues;
    }

    /**
     * Registers all SSA values that represent the exception in an exception handler.
     */
    private void registerExceptionVariables(ExceptionHandler handler, String exceptionVarName)
    {
        IRBlock handlerBlock = handler.getHandlerBlock();
        // Bound the walk to the blocks the catch clause's recovery actually consumes. The
        // handler block often falls through (physically) into the join after the try/catch;
        // an unbounded successor walk would claim every later load of the exception's slot
        // (a slot javac freely reuses) and rename unrelated variables to the catch variable.
        Set<IRBlock> consumed = new HashSet<>();
        collectCatchConsumedBlocks(handler, consumed);
        Set<SSAValue> exceptionValuesSet = new HashSet<>();
        findExceptionSSAValues(handlerBlock, exceptionValuesSet, consumed);

        for (SSAValue excVal : exceptionValuesSet)
        {
            context.getExpressionContext().setVariableName(excVal, exceptionVarName);
        }
    }

    /**
     * Finds all SSA values that represent the caught exception.
     */
    private void findExceptionSSAValues(IRBlock block, Set<SSAValue> result, Set<IRBlock> bound)
    {
        Set<Integer> exceptionLocalSlots = new HashSet<>();

        findExceptionSSAValuesPass1(block, result, exceptionLocalSlots, new HashSet<>(), bound);

        if (!exceptionLocalSlots.isEmpty())
        {
            findExceptionSSAValuesPass2(block, result, exceptionLocalSlots, new HashSet<>(), bound);
        }
    }

    /**
     * First pass: Find exc_ prefix values and track which local slots they're stored to.
     */
    private void findExceptionSSAValuesPass1(IRBlock block, Set<SSAValue> result, Set<Integer> exceptionSlots, Set<IRBlock> visited, Set<IRBlock> bound)
    {
        if (visited.contains(block) || !bound.contains(block)) return;
        visited.add(block);

        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof CopyInstruction)
            {
                CopyInstruction copy = (CopyInstruction) instr;
                SSAValue copyResult = copy.getResult();
                Value copySource = copy.getSource();

                if (copySource instanceof SSAValue)
                {
                    SSAValue ssaSrc = (SSAValue) copySource;
                    String name = ssaSrc.getName();
                    if (name != null && name.startsWith("exc_"))
                    {
                        result.add(ssaSrc);
                        if (copyResult != null)
                        {
                            result.add(copyResult);
                        }
                    }
                    else if (result.contains(ssaSrc))
                    {
                        if (copyResult != null)
                        {
                            result.add(copyResult);
                        }
                    }
                }
            }

            if (instr instanceof StoreLocalInstruction)
            {
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                if (store.getValue() instanceof SSAValue)
                {
                    SSAValue ssaValue = (SSAValue) store.getValue();
                    String name = ssaValue.getName();
                    if (name != null && name.startsWith("exc_"))
                    {
                        result.add(ssaValue);
                        exceptionSlots.add(store.getLocalIndex());
                    }
                    else if (result.contains(ssaValue))
                    {
                        exceptionSlots.add(store.getLocalIndex());
                    }
                }
            }

            for (Value operand : instr.getOperands())
            {
                if (operand instanceof SSAValue)
                {
                    SSAValue ssaOp = (SSAValue) operand;
                    String name = ssaOp.getName();
                    if (name != null && name.startsWith("exc_"))
                    {
                        result.add(ssaOp);
                    }
                }
            }
        }

        for (IRBlock succ : block.getSuccessors())
        {
            findExceptionSSAValuesPass1(succ, result, exceptionSlots, visited, bound);
        }
    }

    /**
     * Second pass: Find LoadLocal from exception-containing slots.
     */
    private void findExceptionSSAValuesPass2(IRBlock block, Set<SSAValue> result, Set<Integer> exceptionSlots, Set<IRBlock> visited, Set<IRBlock> bound)
    {
        if (visited.contains(block) || !bound.contains(block)) return;
        visited.add(block);

        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof LoadLocalInstruction)
            {
                LoadLocalInstruction load = (LoadLocalInstruction) instr;
                if (exceptionSlots.contains(load.getLocalIndex()))
                {
                    SSAValue loadResult = load.getResult();
                    if (loadResult != null)
                    {
                        result.add(loadResult);
                    }
                }
            }
            else if (instr instanceof StoreLocalInstruction)
            {
                // A store of a non-exception value redefines the slot: javac reuses the catch
                // variable's slot for unrelated locals afterwards, so later loads are NOT the
                // exception and must keep their own variable.
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                if (exceptionSlots.contains(store.getLocalIndex()))
                {
                    Value stored = store.getValue();
                    boolean isExceptionValue = stored instanceof SSAValue && result.contains(stored);
                    if (!isExceptionValue)
                    {
                        exceptionSlots.remove(store.getLocalIndex());
                    }
                }
            }
        }

        for (IRBlock succ : block.getSuccessors())
        {
            findExceptionSSAValuesPass2(succ, result, exceptionSlots, visited, bound);
        }
    }

    /**
     * Represents a try region defined by start and end blocks.
     */
    private static final class TryRegion
    {
        private final IRBlock start;
        private final IRBlock end;

        public TryRegion(IRBlock start, IRBlock end)
        {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TryRegion that = (TryRegion) obj;
            return Objects.equals(start, that.start) &&
                   Objects.equals(end, that.end);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(start, end);
        }

        @Override
        public String toString()
        {
            return "TryRegion{" +
                   "start=" + start +
                   ", end=" + end +
                   '}';
        }
    }

    /**
     * Finds the exception handler whose try region starts at the given block, preferring the widest region.
     */
    private ExceptionHandler findHandlerStartingAt(IRBlock block)
    {
        IRMethod irMethod = context.getIrMethod();
        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();
        if (handlers == null || handlers.isEmpty())
        {
            return null;
        }
        ExceptionHandler best = null;
        int bestEnd = Integer.MIN_VALUE;
        for (ExceptionHandler handler : handlers)
        {
            IRBlock tryStart = handler.getTryStart();
            if (tryStart == block || (tryStart != null && tryStart.getBytecodeOffset() == block.getBytecodeOffset()))
            {
                int end = handler.getTryEnd() != null
                        ? handler.getTryEnd().getBytecodeOffset() : Integer.MAX_VALUE;
                if (best == null || end > bestEnd)
                {
                    best = handler;
                    bestEnd = end;
                }
            }
        }
        return best;
    }

    /**
     * Recovers a try-with-resources statement from javac's desugaring.
     */
    private List<Statement> recoverTryWithResources(IRBlock entry, List<ExceptionHandler> handlers)
    {
        if (!isTryWithResourcesMethod(handlers))
        {
            return null;
        }
        Set<ExceptionHandler> cleanup = twrCleanupHandlers(handlers);
        // This route folds the whole method into ONE try(...) header, so it only owns a method holding a
        // single construct. With several, the resources of the later ones are lifted into the first
        // header and their closes are dropped.
        if (twrConstructCount(cleanup) > 1)
        {
            return null;
        }
        List<ExceptionHandler> userHandlers = new ArrayList<>();
        for (ExceptionHandler h : handlers)
        {
            if (!cleanup.contains(h))
            {
                userHandlers.add(h);
            }
        }

        // This route folds the whole method into ONE try(...) header, so every user catch it takes has to
        // be a catch ON THIS RESOURCE - some entry of the clause opens no later than the resource
        // scaffolding it guards. Measured per CLAUSE, not per entry: javac splits a user catch around the
        // cleanup handlers, and the split entries open after the resource. A clause whose every entry
        // opens later than the resource belongs to a SEPARATE construct in the same method; folding it
        // here drops the clause and truncates the method where that construct began.
        int resourceStart = Integer.MAX_VALUE;
        for (ExceptionHandler h : cleanup)
        {
            if (h.getTryStart() != null)
            {
                resourceStart = Math.min(resourceStart, h.getTryStart().getBytecodeOffset());
            }
        }
        Map<IRBlock, Integer> clauseStart = new HashMap<>();
        for (ExceptionHandler h : userHandlers)
        {
            if (h.getHandlerBlock() == null || h.getTryStart() == null)
            {
                continue;
            }
            int start = h.getTryStart().getBytecodeOffset();
            Integer known = clauseStart.get(h.getHandlerBlock());
            clauseStart.put(h.getHandlerBlock(), known == null ? start : Math.min(known, start));
        }
        for (int start : clauseStart.values())
        {
            if (start > resourceStart)
            {
                return null;
            }
        }
        List<CatchClause> userClauses = buildCatchClauses(userHandlers);
        BlockStmt finallyBlock = null;
        Set<String> finallyVars = new HashSet<>();
        List<CatchClause> catchClauses = new ArrayList<>();
        for (CatchClause clause : userClauses)
        {
            if (isFinallyRethrowPattern(clause) && clauseHasFinallyEvidence(clause))
            {
                finallyBlock = extractFinallyBody(clause);
                finallyVars.add(clause.variableName());
            }
            else
            {
                catchClauses.add(clause);
            }
        }
        // A rethrowing user handler that is not the finally pattern (e.g. a catch that rethrows) is not modeled
        // here; leave the whole region to the generic recovery rather than mis-fold it.
        for (ExceptionHandler h : userHandlers)
        {
            if (finallyBlock == null && handlerRethrows(h))
            {
                return null;
            }
        }

        List<ExceptionHandler> allHandlers = new ArrayList<>(cleanup);
        allHandlers.addAll(userHandlers);
        // The attempt below EMITS the whole normal path before the fold can decline. A decline must
        // roll back every recovery mark the emission made - processed blocks, cached statements,
        // claimed handlers, declared variables - or the generic fallback runs against a half-emitted
        // method (a continuation join reads as already processed and its whole region is dropped).
        // Excision-linked state stays: its IR mutations are permanent and route-independent.
        Set<ExceptionHandler> savedTryHandlers = new HashSet<>(processedTryHandlers);
        Set<IRBlock> savedHandlerBlocks = new HashSet<>(processedHandlerBlocks);
        Set<IRBlock> savedProcessed = new HashSet<>(context.getProcessedBlocks());
        Map<IRBlock, List<Statement>> savedStatements = new HashMap<>(context.getBlockStatements());
        for (ExceptionHandler h : allHandlers)
        {
            processedTryHandlers.add(h);
            if (h.getHandlerBlock() != null)
            {
                processedHandlerBlocks.add(h.getHandlerBlock());
            }
        }

        List<Statement> normalPath = recoverBlockSequence(entry, new HashSet<>());
        flattenTrailingGuardElse(normalPath);
        List<Statement> folded = reconstructTryWithResources(normalPath, catchClauses, finallyBlock, finallyVars);
        if (folded == null)
        {
            processedTryHandlers.clear();
            processedTryHandlers.addAll(savedTryHandlers);
            processedHandlerBlocks.clear();
            processedHandlerBlocks.addAll(savedHandlerBlocks);
            context.getProcessedBlocks().clear();
            context.getProcessedBlocks().addAll(savedProcessed);
            context.getBlockStatements().clear();
            context.getBlockStatements().putAll(savedStatements);
            context.getExpressionContext().resetDeclaredVariablesToBaseline();
            return null;
        }
        return folded;
    }

    /**
     * The resource cleanup handlers of a try-with-resources method.
     */
    private Set<ExceptionHandler> twrCleanupHandlers(List<ExceptionHandler> handlers)
    {
        Set<IRBlock> cleanupBlocks = new HashSet<>();
        List<ExceptionHandler> suppress = new ArrayList<>();
        for (ExceptionHandler h : handlers)
        {
            if (h.getHandlerBlock() != null && blockCallsAddSuppressed(h.getHandlerBlock()))
            {
                suppress.add(h);
                cleanupBlocks.add(h.getHandlerBlock());
            }
        }
        for (ExceptionHandler s : suppress)
        {
            if (s.getTryStart() == null)
            {
                continue;
            }
            int closeOffset = s.getTryStart().getBytecodeOffset();
            IRBlock primaryBlock = null;
            int best = -1;
            for (ExceptionHandler h : handlers)
            {
                if (h.getHandlerBlock() == null || cleanupBlocks.contains(h.getHandlerBlock()))
                {
                    continue;
                }
                int handlerOffset = h.getHandlerBlock().getBytecodeOffset();
                if (handlerOffset <= closeOffset && handlerOffset > best)
                {
                    best = handlerOffset;
                    primaryBlock = h.getHandlerBlock();
                }
            }
            if (primaryBlock != null)
            {
                cleanupBlocks.add(primaryBlock);
            }
        }
        Set<ExceptionHandler> cleanup = new HashSet<>();
        for (ExceptionHandler h : handlers)
        {
            if (h.getHandlerBlock() != null && cleanupBlocks.contains(h.getHandlerBlock()))
            {
                cleanup.add(h);
            }
        }
        return cleanup;
    }

    /**
     * True when the block itself invokes {@code Throwable.addSuppressed} - the suppress handler's signature.
     */
    private boolean blockCallsAddSuppressed(IRBlock block)
    {
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof InvokeInstruction && "addSuppressed".equals(((InvokeInstruction) instr).getName()))
            {
                return true;
            }
        }
        return false;
    }
    
    /**
     * The number of separate try-with-resources constructs {@code cleanup} describes.
     */
    private int twrConstructCount(Set<ExceptionHandler> cleanup)
    {
        List<ExceptionHandler> ordered = new ArrayList<>();
        for (ExceptionHandler h : cleanup)
        {
            if (h.getTryStart() != null && h.getTryEnd() != null)
            {
                ordered.add(h);
            }
        }
        ordered.sort(Comparator.comparingInt(a -> a.getTryStart().getBytecodeOffset()));
        List<List<ExceptionHandler>> constructs = new ArrayList<>();
        for (ExceptionHandler h : ordered)
        {
            List<ExceptionHandler> home = null;
            for (List<ExceptionHandler> construct : constructs)
            {
                if (joinsConstruct(h, construct))
                {
                    home = construct;
                    break;
                }
            }
            if (home == null)
            {
                home = new ArrayList<>();
                constructs.add(home);
            }
            home.add(h);
        }
        return constructs.size();
    }

    /**
     * Whether {@code handler} is scaffolding of {@code construct} rather than the start of a new one.
     */
    private boolean joinsConstruct(ExceptionHandler handler, List<ExceptionHandler> construct)
    {
        IRBlock start = handler.getTryStart();
        int offset = start.getBytecodeOffset();
        DominatorTree dt = context.getDominatorTree();
        for (ExceptionHandler member : construct)
        {
            if (member.getHandlerBlock() != null && member.getHandlerBlock() == handler.getHandlerBlock())
            {
                return true;
            }
            if (offset >= member.getTryStart().getBytecodeOffset()
                    && offset < member.getTryEnd().getBytecodeOffset())
            {
                return true;
            }
            if (member.getHandlerBlock() != null && dt.dominates(member.getHandlerBlock(), start))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isTryWithResourcesMethod(List<ExceptionHandler> handlers)
    {
        Set<IRBlock> seen = new HashSet<>();
        Deque<IRBlock> work = new ArrayDeque<>();
        for (ExceptionHandler h : handlers)
        {
            if (h.getHandlerBlock() != null)
            {
                work.add(h.getHandlerBlock());
            }
        }
        while (!work.isEmpty())
        {
            IRBlock b = work.poll();
            if (!seen.add(b))
            {
                continue;
            }
            for (IRInstruction instr : b.getInstructions())
            {
                if (instr instanceof InvokeInstruction && "addSuppressed".equals(((InvokeInstruction) instr).getName()))
                {
                    return true;
                }
            }
            work.addAll(b.getSuccessors());
        }
        return false;
    }

    /**
     * Unfolds a trailing {@code if (c) { throws } else { continuation }} into guard form - the terminal arm keeps
     * the guard, the else's statements rejoin the top level.
     */
    private void flattenTrailingGuardElse(List<Statement> normalPath)
    {
        while (!normalPath.isEmpty())
        {
            Statement last = normalPath.get(normalPath.size() - 1);
            if (!(last instanceof IfStmt))
            {
                return;
            }
            IfStmt guard = (IfStmt) last;
            if (!guard.hasElse() || !endsAbruptly(guard.getThenBranch()))
            {
                return;
            }
            Statement elseBranch = guard.getElseBranch();
            guard.setElseBranch(null);
            if (elseBranch instanceof BlockStmt)
            {
                normalPath.addAll(((BlockStmt) elseBranch).getStatements());
            }
            else
            {
                normalPath.add(elseBranch);
            }
        }
    }

    /**
     * Whether every path through {@code s} throws or returns (its last reachable statement is terminal).
     */
    private boolean endsAbruptly(Statement s)
    {
        if (s instanceof ThrowStmt || s instanceof ReturnStmt)
        {
            return true;
        }
        if (s instanceof BlockStmt)
        {
            List<Statement> stmts = ((BlockStmt) s).getStatements();
            return !stmts.isEmpty() && endsAbruptly(stmts.get(stmts.size() - 1));
        }
        return false;
    }

    private List<Statement> reconstructTryWithResources(List<Statement> normalPath, List<CatchClause> catches, BlockStmt finallyBlock, Set<String> finallyVars)
    {
        Set<String> closedVars = new LinkedHashSet<>();
        for (Statement s : normalPath)
        {
            String closed = findClosedResource(s);
            if (closed != null)
            {
                closedVars.add(closed);
            }
        }
        if (closedVars.isEmpty())
        {
            return null;
        }

        List<VarDeclStmt> resourceDecls = new ArrayList<>();
        Set<String> resourceNames = new LinkedHashSet<>();
        for (Statement s : normalPath)
        {
            if (s instanceof VarDeclStmt)
            {
                VarDeclStmt d = (VarDeclStmt) s;
                if (closedVars.contains(d.getName()) && resourceNames.add(d.getName()))
                {
                    resourceDecls.add(d);
                }
            }
        }
        if (resourceDecls.isEmpty())
        {
            return null;
        }

        int firstIdx = normalPath.size();
        for (int i = 0; i < normalPath.size(); i++)
        {
            Statement s = normalPath.get(i);
            if (s instanceof VarDeclStmt && resourceNames.contains(((VarDeclStmt) s).getName()))
            {
                firstIdx = i;
                break;
            }
        }

        List<Statement> pre = new ArrayList<>(normalPath.subList(0, firstIdx));
        List<Statement> body = new ArrayList<>();
        for (int i = firstIdx; i < normalPath.size(); i++)
        {
            Statement s = normalPath.get(i);
            if (s instanceof VarDeclStmt && resourceNames.contains(((VarDeclStmt) s).getName()))
            {
                continue; // resource declaration -> lifted into the try header
            }
            Statement stripped = stripSyntheticCloses(s, resourceNames);
            if (stripped != null)
            {
                body.add(stripped);
            }
        }

        if (finallyBlock != null)
        {
            body = filterOrphanFinallyThrows(body, finallyVars);
            body = filterInlinedFinallyFromTryStatements(body, finallyBlock.getStatements());
        }

        List<Expression> resources = new ArrayList<>();
        for (VarDeclStmt d : resourceDecls)
        {
            resources.add(new VarRefExpr(d.getName(), d.getType()));
        }
        TryCatchStmt tryStmt = new TryCatchStmt(new BlockStmt(body), catches, finallyBlock, resources,
                SourceLocation.UNKNOWN);

        List<Statement> result = new ArrayList<>(pre);
        result.addAll(resourceDecls);
        result.add(tryStmt);
        return result;
    }

    /**
     * Removes the synthetic resource {@code close()} calls from {@code s}, or null when the statement was
     * nothing but a close.
     */
    private Statement stripSyntheticCloses(Statement s, Set<String> resourceNames)
    {
        if (s instanceof ExprStmt)
        {
            String r = closeReceiverName(s);
            return (r != null && resourceNames.contains(r)) ? null : s;
        }
        if (s instanceof BlockStmt)
        {
            List<Statement> kept = new ArrayList<>();
            for (Statement inner : ((BlockStmt) s).getStatements())
            {
                Statement stripped = stripSyntheticCloses(inner, resourceNames);
                if (stripped != null)
                {
                    kept.add(stripped);
                }
            }
            return kept.isEmpty() ? null : new BlockStmt(kept);
        }
        if (s instanceof IfStmt)
        {
            IfStmt ifStmt = (IfStmt) s;
            Statement then = ifStmt.getThenBranch() == null
                    ? null : stripSyntheticCloses(ifStmt.getThenBranch(), resourceNames);
            Statement els = ifStmt.getElseBranch() == null
                    ? null : stripSyntheticCloses(ifStmt.getElseBranch(), resourceNames);
            if (then == null && els == null)
            {
                return null;
            }
            ifStmt.setThenBranch(then != null ? then : new BlockStmt());
            ifStmt.setElseBranch(els);
            return ifStmt;
        }
        return s;
    }

    /**
     * The resource variable closed by {@code s}, directly or through javac's try-with-resources wrapper.
     */
    private String findClosedResource(Statement s)
    {
        if (s instanceof ExprStmt)
        {
            return closeReceiverName(s);
        }
        if (s instanceof IfStmt)
        {
            IfStmt ifStmt = (IfStmt) s;
            String r = findClosedResource(ifStmt.getThenBranch());
            return r != null ? r : findClosedResource(ifStmt.getElseBranch());
        }
        if (s instanceof BlockStmt)
        {
            for (Statement inner : ((BlockStmt) s).getStatements())
            {
                String r = findClosedResource(inner);
                if (r != null)
                {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * The receiver variable name of a {@code receiver.close()} expression statement, else null.
     */
    private String closeReceiverName(Statement s)
    {
        if (!(s instanceof ExprStmt))
        {
            return null;
        }
        Expression e = ((ExprStmt) s).getExpression();
        if (!(e instanceof MethodCallExpr))
        {
            return null;
        }
        MethodCallExpr mc = (MethodCallExpr) e;
        if (!"close".equals(mc.getMethodName()))
        {
            return null;
        }
        return mc.getReceiver() instanceof VarRefExpr ? ((VarRefExpr) mc.getReceiver()).getName() : null;
    }

    /**
     * Whether {@code h} is a split piece of a construct ENCLOSING the {@code [mainStart, mainEnd)} region that an
     * outer recovery already owns.
     */
    private boolean isEnclosingHandlerPiece(ExceptionHandler h, int mainStart, int mainEnd)
    {
        if (h.getHandlerBlock() == null || !processedHandlerBlocks.contains(h.getHandlerBlock()))
        {
            return false;
        }
        for (ExceptionHandler eh : context.getIrMethod().getExceptionHandlers())
        {
            if (eh.getHandlerBlock() != h.getHandlerBlock() || eh.getTryStart() == null || eh.getTryEnd() == null)
            {
                continue;
            }
            if (eh.getTryStart().getBytecodeOffset() < mainStart || eh.getTryEnd().getBytecodeOffset() > mainEnd)
            {
                return true;
            }
        }
        return false;
    }

    private Statement recoverTryCatch(IRBlock startBlock, ExceptionHandler mainHandler, Set<IRBlock> originalStopBlocks, Set<IRBlock> visited)
    {
        IRMethod irMethod = context.getIrMethod();
        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();

        // javac splits one try's protected range into several exception-table entries around
        // instructions that exit the try (a break/return between protected sections). All entries
        // targeting this handler block are the SAME source try; widen the effective range to their
        // union, otherwise the body recovery stops at the first entry's end and the blocks of the
        // later entries (e.g. code after an `if (...) break;` in the try) are silently skipped.
        IRBlock mergedStart = mainHandler.getTryStart();
        IRBlock mergedEnd = mainHandler.getTryEnd();
        for (ExceptionHandler h : handlers)
        {
            if (h.getHandlerBlock() != mainHandler.getHandlerBlock())
            {
                continue;
            }
            if (h.getTryStart() != null && (mergedStart == null
                    || h.getTryStart().getBytecodeOffset() < mergedStart.getBytecodeOffset()))
            {
                mergedStart = h.getTryStart();
            }
            if (h.getTryEnd() != null && (mergedEnd == null
                    || h.getTryEnd().getBytecodeOffset() > mergedEnd.getBytecodeOffset()))
            {
                mergedEnd = h.getTryEnd();
            }
        }
        if (mergedStart != mainHandler.getTryStart() || mergedEnd != mainHandler.getTryEnd())
        {
            mainHandler = new ExceptionHandler(
                mergedStart, mergedEnd, mainHandler.getHandlerBlock(), mainHandler.getCatchType());
        }

        // The region's clause set: entries targeting the same handler block (the split ranges
        // above plus multi-catch types) and entries whose range equals the merged range (a
        // finally handler protecting exactly this try).
        List<ExceptionHandler> sameRegionHandlers = new ArrayList<>();
        TryRegion mainRegion = createTryRegion(mainHandler);
        for (ExceptionHandler h : handlers)
        {
            if (h.getHandlerBlock() == mainHandler.getHandlerBlock()
                    || (mainRegion != null && mainRegion.equals(createTryRegion(h))))
            {
                sameRegionHandlers.add(h);
            }
        }

        // Claim the region's clause set before recovering the body: a body walk's engine attempt would
        // otherwise node-ify the construct's own family as a fresh try - re-recovering the construct
        // inside itself and synthesizing nested tries around the user catches. The claim covers the
        // whole HANDLER-BLOCK family: a split range of the same clause (javac splits around returns
        // and interleaved arms) is this construct's own scaffolding, and leaving it unclaimed makes
        // the continuation walk meet it as a fresh mid-family try start it can never decode.
        for (ExceptionHandler h : sameRegionHandlers)
        {
            processedTryHandlers.add(h);
            if (h.getHandlerBlock() != null)
            {
                processedHandlerBlocks.add(h.getHandlerBlock());
            }
        }
        for (ExceptionHandler h : irMethod.getExceptionHandlers())
        {
            if (h.getHandlerBlock() != null && processedHandlerBlocks.contains(h.getHandlerBlock()))
            {
                processedTryHandlers.add(h);
            }
        }
        Set<IRBlock> tryStopBlocks = new HashSet<>(originalStopBlocks);
        for (ExceptionHandler h : sameRegionHandlers)
        {
            if (h.getHandlerBlock() != null)
            {
                tryStopBlocks.add(h.getHandlerBlock());
            }
        }

        if (mainHandler.getTryEnd() != null)
        {
            int tryEndOffset = mainHandler.getTryEnd().getBytecodeOffset();
            for (IRBlock block : irMethod.getBlocks())
            {
                if (block.getBytecodeOffset() >= tryEndOffset)
                {
                    tryStopBlocks.add(block);
                }
            }
        }

        // YABR routes BOTH the try and catch normal exits through ONE shared finally block, which sits between
        // the try and the catch (below the catch-all's wider span), so it would be absorbed into the try body
        // AND re-recovered by the outer sequence -> a duplicated finally. javac duplicates the finally and has
        // no such block. Find it as the non-handler successor of an inner (catch) region that ends before the
        // catch-all, and stop there so it is recovered once, as the continuation after the try-catch-finally.
        boolean sharedFinallyStopAdded = false;
        if (mainHandler.getTryEnd() != null && mainHandler.getHandlerBlock() != null)
        {
            Set<IRBlock> allHandlerBlocks = new HashSet<>();
            for (ExceptionHandler h : handlers)
            {
                if (h.getHandlerBlock() != null)
                {
                    allHandlerBlocks.add(h.getHandlerBlock());
                }
            }
            // YABR routes the try AND catch normal exits through ONE shared finally block sitting in the gap
            // between the try region's end and the catch-all handler. It is a MERGE (reached from the try and
            // the catch); the outer block sequence recovers it once as the continuation, so stop the try body
            // there and skip the gap re-add - otherwise it is pulled into the try body too (duplicating the
            // finally / continuation). The first such merge block in the gap is the shared finally.
            int tryEndOffset = mainHandler.getTryEnd().getBytecodeOffset();
            int handlerOffset = mainHandler.getHandlerBlock().getBytecodeOffset();
            IRBlock shared = null;
            int bestOffset = Integer.MAX_VALUE;
            for (IRBlock block : irMethod.getBlocks())
            {
                int off = block.getBytecodeOffset();
                if (off >= tryEndOffset && off < handlerOffset && off < bestOffset
                        && !allHandlerBlocks.contains(block) && block.getPredecessors().size() > 1)
                {
                    shared = block;
                    bestOffset = off;
                }
            }
            if (shared != null)
            {
                tryStopBlocks.add(shared);
                sharedFinallyStopAdded = true;
            }
        }

        // Handlers whose try range sits inside this region are nested try/catch/finally structure
        // that the body recovery must build (mirroring recoverWithExceptionHandling's outer/inner
        // split); recovering the body flat would drop or misplace their clauses.
        List<ExceptionHandler> nestedHandlers = new ArrayList<>();
        if (mainHandler.getTryStart() != null && mainHandler.getTryEnd() != null)
        {
            int mainStart = mainHandler.getTryStart().getBytecodeOffset();
            int mainEnd = mainHandler.getTryEnd().getBytecodeOffset();
            for (ExceptionHandler h : handlers)
            {
                if (sameRegionHandlers.contains(h) || h.getTryStart() == null || h.getTryEnd() == null)
                {
                    continue;
                }
                int hStart = h.getTryStart().getBytecodeOffset();
                int hEnd = h.getTryEnd().getBytecodeOffset();
                if (hStart >= mainStart && hEnd <= mainEnd && !isEnclosingHandlerPiece(h, mainStart, mainEnd))
                {
                    nestedHandlers.add(h);
                }
            }
        }
        // A TYPED Throwable rethrower with no finally EVIDENCE (no inlined copy anywhere, not a true
        // catch-any) is a user catch-rethrow, not a finally: treating it as one routes the body into
        // the copy-skipping walk over copies that do not exist, refusing every structural offer. The
        // emitter writes real finally scaffolds as catch-any, so provenance survives the round trip.
        boolean hasFinally = isEvidencedFinally(mainHandler);
        for (ExceptionHandler h : sameRegionHandlers)
        {
            if (isEvidencedFinally(h))
            {
                hasFinally = true;
            }
        }
        // A catch that falls through rejoins the code after the whole try/catch - the shared continuation.
        // The recompiler can lay that continuation out BETWEEN the try's split protected ranges (below the
        // merged end offset), where the offset-based stop set does not catch it, so the try body would absorb
        // it (e.g. a trailing `return` pulled inside the try, breaking the round trip). Stop the body at the
        // catch's fall-through target so it is recovered once, after the try/catch. A successor that lies
        // WITHIN a protected range is a catch flowing back into its own loop, not a continuation - left alone.
        // For a javac layout the continuation sits past the merged end and is already a stop, so this is a
        // no-op there.
        if (!hasFinally)
        {
            for (ExceptionHandler h : sameRegionHandlers)
            {
                if (h.getHandlerBlock() == null)
                {
                    continue;
                }
                Set<IRBlock> catchBlocks = new HashSet<>();
                collectCatchConsumedBlocks(h, catchBlocks);
                for (IRBlock cb : catchBlocks)
                {
                    for (IRBlock succ : cb.getSuccessors())
                    {
                        if (!catchBlocks.contains(succ) && !isWithinProtectedRange(succ, sameRegionHandlers))
                        {
                            tryStopBlocks.add(succ);
                        }
                    }
                }
            }
        }
        boolean savedExtendedSame = extendedFinallyDedup;
        extendedFinallyDedup = true;
        boolean finallyDeduped;
        try
        {
            // Attempted with nested handlers present too: a finally whose body carries its own catch
            // inlines copies bearing MIRRORED nested handlers, which are exactly what the branchy
            // matcher's nested-template pairing excises (all-or-nothing, so a shape it cannot fully
            // account for leaves the IR untouched and the statement-level folds still apply).
            finallyDeduped = hasFinally && dedupStraightLineFinally(sameRegionHandlers);
        }
        finally
        {
            extendedFinallyDedup = savedExtendedSame;
        }
        List<Statement> tryStmts;
        if (!nestedHandlers.isEmpty())
        {
            for (ExceptionHandler h : nestedHandlers)
            {
                // Pre-marking a nested handler as processed prevents the try body's own walk from rebuilding
                // it. But a nested handler whose try-start sits INSIDE a loop body (not one that wraps a loop)
                // is not recovered by recoverWithInnerTryCatch directly - the enclosing loop's body recovery
                // reaches the nested try/catch through the processed set and so needs it left unprocessed.
                // Pre-marking it there drops the catch (a recompiler can split the outer catch range so its
                // second piece begins at the same in-loop block, colliding).
                if (isInsideLoopBody(h.getTryStart()))
                {
                    continue;
                }
                processedTryHandlers.add(h);
                if (h.getHandlerBlock() != null)
                {
                    processedHandlerBlocks.add(h.getHandlerBlock());
                }
            }
            tryStmts = recoverWithNestedHandlers(startBlock, nestedHandlers, tryStopBlocks);
        }
        else
        {
            // A synchronized region's "inlined copies" are monitor instructions the statement recovery
            // drops outright - they never inflate a guard arm - so its body takes the engine offers
            // like any de-duplicated try. Only a REAL finally's surviving copies force the skip mode.
            boolean monitorScaffold = mainHandler.getHandlerBlock() != null
                    && blockContainsMonitorExit(mainHandler.getHandlerBlock());
            tryStmts = recoverBlocksForTry(startBlock, tryStopBlocks, visited,
                    hasFinally && !finallyDeduped && !monitorScaffold);
        }
        BlockStmt tryBlock = new BlockStmt(tryStmts);

        Set<String> finallyExceptionVars = new HashSet<>();
        for (ExceptionHandler h : sameRegionHandlers)
        {
            if (h.getHandlerBlock() != null)
            {
                collectCatchConsumedBlocks(h, visited);
            }
        }
        List<CatchClause> catchClauses = buildCatchClauses(sameRegionHandlers);

        if (catchClauses.isEmpty())
        {
            return null;
        }

        BlockStmt finallyBlock = null;
        List<CatchClause> filteredCatches = new ArrayList<>();
        for (CatchClause clause : catchClauses)
        {
            if (isFinallyRethrowPattern(clause) && clauseHasFinallyEvidence(clause))
            {
                finallyBlock = extractFinallyBody(clause);
                finallyExceptionVars.add(clause.variableName());
            }
            else
            {
                filteredCatches.add(clause);
            }
        }

        if (!finallyExceptionVars.isEmpty())
        {
            tryStmts = filterOrphanFinallyThrows(tryStmts, finallyExceptionVars);

            List<Statement> finallyStmts = finallyBlock.getStatements();
            tryStmts = filterInlinedFinallyFromTryStatements(tryStmts, finallyStmts);
            filteredCatches = filterInlinedFinallyFromCatches(filteredCatches, finallyStmts);

            // Skip the gap re-add when we stopped at a shared finally block: that block IS the normal-exit
            // finally + continuation and is now recovered once by the outer block sequence; pulling it in here
            // too would duplicate it back into the try body.
            if (!sharedFinallyStopAdded && mainHandler.getTryEnd() != null && mainHandler.getHandlerBlock() != null)
            {
                List<Statement> gapStmts = recoverFinallyGap(
                    mainHandler.getTryEnd(), mainHandler.getHandlerBlock(), finallyBlock, finallyExceptionVars);
                if (!gapStmts.isEmpty() && !isTerminatingBlock(new BlockStmt(tryStmts)))
                {
                    tryStmts = new ArrayList<>(tryStmts);
                    tryStmts.addAll(gapStmts);
                }
            }

            tryBlock = new BlockStmt(tryStmts);
        }

        // A finally whose split exception-table ranges are sub-ranges of the catch's range (javac splits
        // the finally range around returns/breaks in the try) is classified as a nested handler and
        // recovered as the try body's own try/finally - so it is absent from sameRegionHandlers and the
        // catch-copy strip above (gated on a same-region finally) never runs. javac still inlined that
        // finally before each catch exit, so without stripping it the finally runs twice on the caught
        // path (once as the nested finally on the exception unwind, once as the catch's inlined copy).
        // Strip the catch copies against the nested finally's already-recovered body.
        if (finallyExceptionVars.isEmpty() && !filteredCatches.isEmpty())
        {
            BlockStmt nestedFinally = firstNestedFinallyBlock(tryStmts);
            if (nestedFinally != null && !nestedFinally.getStatements().isEmpty())
            {
                filteredCatches = filterInlinedFinallyFromCatches(filteredCatches, nestedFinally.getStatements());
            }
        }

        Value syncLock = filteredCatches.isEmpty() ? detectSynchronizedLock(mainHandler) : null;
        if (syncLock != null)
        {
            SynchronizedStmt sync = new SynchronizedStmt(recoverLockExpr(syncLock), tryBlock);
            stampFromBody(sync, tryBlock);
            return sync;
        }

        TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, filteredCatches, finallyBlock);
        stampFromBody(tryCatch, tryBlock);
        return tryCatch;
    }

    /**
     * The finally body of the first nested {@code try/finally} in a recovered statement list, if any.
     */
    private BlockStmt firstNestedFinallyBlock(List<Statement> stmts)
    {
        for (Statement s : stmts)
        {
            if (s instanceof TryCatchStmt)
            {
                TryCatchStmt t = (TryCatchStmt) s;
                if (t.getFinallyBlock() instanceof BlockStmt
                        && !((BlockStmt) t.getFinallyBlock()).getStatements().isEmpty())
                {
                    return (BlockStmt) t.getFinallyBlock();
                }
            }
        }
        return null;
    }

    /**
     * Whether {@code h} is a finally-style rethrow handler - its region ends in an {@code athrow} that re-raises
     * the caught exception.
     */
    private boolean handlerRethrows(ExceptionHandler h)
    {
        if (h == null || h.getHandlerBlock() == null)
        {
            return false;
        }
        Deque<IRBlock> work = new ArrayDeque<>();
        Set<IRBlock> seen = new HashSet<>();
        work.add(h.getHandlerBlock());
        int budget = 40;
        while (!work.isEmpty() && budget-- > 0)
        {
            IRBlock b = work.poll();
            if (!seen.add(b))
            {
                continue;
            }
            List<IRInstruction> instrs = b.getInstructions();
            if (!instrs.isEmpty())
            {
                IRInstruction last = instrs.get(instrs.size() - 1);
                if (last instanceof SimpleInstruction && ((SimpleInstruction) last).getOp() == SimpleOp.ATHROW)
                {
                    return true;
                }
            }
            work.addAll(b.getSuccessors());
        }
        return false;
    }

    /**
     * As {@link #handlerThrowsFreshException}, but traces freshness through slot round-trips.
     */
    private boolean throwsFreshExceptionThroughSlots(ExceptionHandler h)
    {
        if (h == null || h.getHandlerBlock() == null)
        {
            return false;
        }
        Set<Value> freshValues = new HashSet<>();
        Set<Integer> freshSlots = new HashSet<>();
        Value thrown = null;
        Deque<IRBlock> work = new ArrayDeque<>();
        Set<IRBlock> seen = new HashSet<>();
        work.add(h.getHandlerBlock());
        int budget = 60;
        while (!work.isEmpty() && budget-- > 0)
        {
            IRBlock b = work.poll();
            if (!seen.add(b))
            {
                continue;
            }
            for (IRInstruction ins : b.getInstructions())
            {
                if (ins instanceof NewInstruction && ins.getResult() != null)
                {
                    freshValues.add(ins.getResult());
                }
                // A layout may park the fresh exception in a slot before the throw (astore/aload
                // around the constructor); the slot carries the freshness to the reload.
                if (ins instanceof StoreLocalInstruction
                        && freshValues.contains(((StoreLocalInstruction) ins).getValue()))
                {
                    freshSlots.add(((StoreLocalInstruction) ins).getLocalIndex());
                }
                if (ins instanceof LoadLocalInstruction && ins.getResult() != null
                        && freshSlots.contains(((LoadLocalInstruction) ins).getLocalIndex()))
                {
                    freshValues.add(ins.getResult());
                }
                if (ins instanceof CopyInstruction && ins.getResult() != null
                        && freshValues.contains(((CopyInstruction) ins).getSource()))
                {
                    freshValues.add(ins.getResult());
                }
            }
            IRInstruction term = b.getTerminator();
            if (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.ATHROW)
            {
                // The FIRST athrow reached is the handler's own; descending further would wander over
                // an exception edge into ANOTHER handler's throw and judge that one instead.
                if (thrown == null)
                {
                    thrown = ((SimpleInstruction) term).getOperand();
                }
                continue;
            }
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    work.add(e.getKey());
                }
            }
        }
        return thrown != null && freshValues.contains(thrown);
    }

    private boolean handlerThrowsFreshException(ExceptionHandler h)
    {
        if (h == null || h.getHandlerBlock() == null)
        {
            return false;
        }
        Set<Value> freshValues = new HashSet<>();
        Value thrown = null;
        Deque<IRBlock> work = new ArrayDeque<>();
        Set<IRBlock> seen = new HashSet<>();
        work.add(h.getHandlerBlock());
        int budget = 60;
        while (!work.isEmpty() && budget-- > 0)
        {
            IRBlock b = work.poll();
            if (!seen.add(b))
            {
                continue;
            }
            for (IRInstruction ins : b.getInstructions())
            {
                if (ins instanceof NewInstruction && ins.getResult() != null)
                {
                    freshValues.add(ins.getResult());
                }
            }
            IRInstruction term = b.getTerminator();
            if (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.ATHROW)
            {
                thrown = ((SimpleInstruction) term).getOperand();
            }
            work.addAll(b.getSuccessors());
        }
        return thrown != null && freshValues.contains(thrown);
    }

    /**
     * The linear chain of blocks from {@code h}'s handler entry to the block that rethrows, or null when the
     * handler branches.
     */
    private List<IRBlock> finallyHandlerChain(ExceptionHandler h)
    {
        if (h == null || h.getHandlerBlock() == null)
        {
            return null;
        }
        List<IRBlock> chain = new ArrayList<>();
        Set<IRBlock> seen = new HashSet<>();
        IRBlock b = h.getHandlerBlock();
        while (b != null && seen.add(b))
        {
            chain.add(b);
            IRInstruction term = b.getTerminator();
            if (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.ATHROW)
            {
                break;
            }
            IRBlock next = null;
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    if (next != null)
                    {
                        return null;
                    }
                    next = e.getKey();
                }
            }
            b = next;
        }
        return chain;
    }

    /**
     * Whether the finally template (the rethrow handler's dominated subgraph up to its athrow) contains a nested
     * exception handler - the try-with-resources suppress {@code try close catch addSuppressed}.
     */
    private boolean finallyTemplateHasNestedHandler(List<ExceptionHandler> outerHandlers)
    {
        DominatorTree dt = context.getDominatorTree();
        if (dt == null)
        {
            return false;
        }
        for (ExceptionHandler h : outerHandlers)
        {
            IRBlock root = h.getHandlerBlock();
            if (root == null || !handlerRethrows(h))
            {
                continue;
            }
            for (ExceptionHandler other : context.getIrMethod().getExceptionHandlers())
            {
                if (other == h || other.getHandlerBlock() == null || other.getTryBlocks() == null)
                {
                    continue;
                }
                for (IRBlock tb : other.getTryBlocks())
                {
                    if (tb != root && dt.dominates(root, tb))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether some inner (nested) handler in the region is itself a finally - the pre-fused sentinel
     * try-with-resources shape, whose outer finally's protected range javac splits around the inner
     * construct's exits.
     */
    private boolean regionHasNestedFinally(List<ExceptionHandler> innerHandlers)
    {
        for (ExceptionHandler h : innerHandlers)
        {
            if (handlerRethrows(h) && !handlerThrowsFreshException(h) && isFinallyCatchType(h))
            {
                return true;
            }
        }
        return false;
    }

    private boolean finallyTemplateHasLoop(List<ExceptionHandler> outerHandlers)
    {
        LoopAnalysis la = context.getLoopAnalysis();
        DominatorTree dt = context.getDominatorTree();
        if (la == null || dt == null)
        {
            return false;
        }
        for (ExceptionHandler h : outerHandlers)
        {
            IRBlock root = h.getHandlerBlock();
            if (root == null || !handlerRethrows(h))
            {
                continue;
            }
            Deque<IRBlock> work = new ArrayDeque<>();
            Set<IRBlock> seen = new HashSet<>();
            work.add(root);
            while (!work.isEmpty())
            {
                IRBlock b = work.poll();
                if (!seen.add(b))
                {
                    continue;
                }
                if (la.isLoopHeader(b) && dt.dominates(root, b))
                {
                    return true;
                }
                IRInstruction term = b.getTerminator();
                if (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.ATHROW)
                {
                    continue;
                }
                for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
                {
                    if (e.getValue() == EdgeType.NORMAL && dt.dominates(root, e.getKey()))
                    {
                        work.add(e.getKey());
                    }
                }
            }
        }
        return false;
    }

    /**
     * De-duplicates a finally whose body CARRIES CONTROL FLOW - a guarded close, javac's inlined copies of it
     * being branchy subgraphs the contiguous matcher cannot see.
     */
    private boolean dedupBranchySubgraphFinally(List<ExceptionHandler> rethrowers, boolean consumeShells)
    {
        IRMethod method = context.getIrMethod();
        DominatorTree dt = context.getDominatorTree();
        IRBlock root = rethrowers.get(0).getHandlerBlock();
        if (method == null || dt == null || root == null)
        {
            return false;
        }
        for (ExceptionHandler r : rethrowers)
        {
            if (r.getHandlerBlock() != root)
            {
                trace("finally-dedup bail#1 root=" + root.getBytecodeOffset());
                return false;
            }
        }
        // The template is bounded by walking normal edges from the handler entry and STOPPING at the
        // rethrow: dominance alone over-collects (a handler can dominate unrelated code past its athrow,
        // and a sibling catch's own throw would read as a second rethrow). Exactly one athrow must
        // terminate the walk, and every collected block must still be handler-exclusive.
        Set<IRBlock> tblocks = new LinkedHashSet<>();
        IRBlock rethrowBlk = null;
        Deque<IRBlock> twork = new ArrayDeque<>();
        twork.add(root);
        while (!twork.isEmpty())
        {
            IRBlock b = twork.poll();
            IRInstruction term = b.getTerminator();
            if (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.ATHROW)
            {
                if (rethrowBlk != null && rethrowBlk != b)
                {
                    trace("finally-dedup bail#2 root=" + root.getBytecodeOffset());
                    return false;
                }
                rethrowBlk = b;
                continue;
            }
            if (!tblocks.add(b))
            {
                continue;
            }
            if (b != root && !dt.dominates(root, b))
            {
                trace("finally-dedup bail#3 root=" + root.getBytecodeOffset());
                return false;
            }
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    twork.add(e.getKey());
                }
            }
        }
        if (rethrowBlk == null)
        {
            trace("finally-dedup bail#4 root=" + root.getBytecodeOffset());
            return false;
        }
        // Absorb handlers wholly internal to the template: a finally clause commonly guards its own
        // close with a private catch (`try { is.close(); } catch (IOException ex) { log(ex); }`), whose
        // protected range lies entirely inside the collected blocks. The nested catch's subgraph IS part
        // of the template; each inlined copy carries a mirrored nested handler that the parallel walk
        // pairs and the excision retires.
        Map<IRBlock, ExceptionHandler> nestedTemplate = new LinkedHashMap<>();
        boolean absorbed = true;
        while (absorbed)
        {
            absorbed = false;
            for (ExceptionHandler h : method.getExceptionHandlers())
            {
                IRBlock hb = h.getHandlerBlock();
                if (hb == null || hb == root || tblocks.contains(hb) || nestedTemplate.containsKey(hb)
                        || rethrowers.contains(h))
                {
                    continue;
                }
                Set<IRBlock> hTry = h.getTryBlocks();
                if (hTry == null || hTry.isEmpty() || !tblocks.containsAll(hTry))
                {
                    continue;
                }
                Deque<IRBlock> hwork = new ArrayDeque<>();
                hwork.add(hb);
                List<IRBlock> hblocks = new ArrayList<>();
                boolean ok = true;
                Set<IRBlock> hseen = new HashSet<>();
                while (!hwork.isEmpty())
                {
                    IRBlock b = hwork.poll();
                    IRInstruction term = b.getTerminator();
                    if (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.ATHROW)
                    {
                        if (b != rethrowBlk)
                        {
                            ok = false;
                            break;
                        }
                        continue;
                    }
                    if (tblocks.contains(b) || !hseen.add(b))
                    {
                        continue;
                    }
                    if (!dt.dominates(root, b))
                    {
                        ok = false;
                        break;
                    }
                    hblocks.add(b);
                    for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
                    {
                        if (e.getValue() == EdgeType.NORMAL)
                        {
                            hwork.add(e.getKey());
                        }
                    }
                }
                if (!ok)
                {
                    continue;
                }
                tblocks.addAll(hblocks);
                nestedTemplate.put(hb, h);
                absorbed = true;
            }
        }
        for (IRBlock b : tblocks)
        {
            for (IRInstruction ins : b.getInstructions())
            {
                if (!ins.isTerminator() && !(ins instanceof CopyInstruction)
                        && !(ins instanceof StoreLocalInstruction && b == root)
                        && !isMatchableFinallyInstr(ins))
                {
                    trace("finally-dedup bail#5 root=" + root.getBytecodeOffset());
                    return false;
                }
            }
        }

        // A pure rethrower - astore + athrow, an EMPTY template - carries no finally body and so has no
        // inlined copies to excise: javac emits such an entry as another handler's self-protection over
        // its own clause. Demanding that every exit land on a (nonexistent) copy would decline the whole
        // region before the real clause's handler is ever tried; it is trivially de-duplicated instead.
        boolean emptyTemplate = true;
        for (IRBlock b : tblocks)
        {
            if (!matchableInstructions(b, b == root).isEmpty())
            {
                emptyTemplate = false;
                break;
            }
        }
        if (emptyTemplate)
        {
            finallyDeduped.addAll(rethrowers);
            return true;
        }
        // The RAW exception-table entries targeting this handler block give the true split ranges; the
        // inlined copies LIVE in the gaps between them, so a merged min..max span would swallow the copies
        // into the protected set and the exit hunt would never find a copy root. Only the loop-carrying
        // consume-shells path needs the raw ranges (its copies sit in the gaps); every other caller keeps
        // the passed (possibly merged) handler view for byte-identical behavior.
        Set<IRBlock> protectedBlocks = new HashSet<>();
        // A template with a nested protected range of its own (the try-with-resources suppress
        // {@code try close catch addSuppressed}) is matched under the absorbed-handler pairing, whose
        // behavior is calibrated to the caller-passed range view; only a PLAIN template widens to the raw
        // split ranges. The dominator test is used (not the absorbed map) - absorption can decline while
        // the nested try still exists, and a false widening lets condition-bearing code masquerade as a
        // copy and be gutted.
        boolean templateHasNestedTry = false;
        for (ExceptionHandler other : method.getExceptionHandlers())
        {
            if (other.getHandlerBlock() == root || other.getTryBlocks() == null)
            {
                continue;
            }
            for (IRBlock tb : other.getTryBlocks())
            {
                if (tb != root && dt.dominates(root, tb))
                {
                    templateHasNestedTry = true;
                    break;
                }
            }
            if (templateHasNestedTry)
            {
                break;
            }
        }
        List<ExceptionHandler> rangeSource;
        if (consumeShells || !templateHasNestedTry)
        {
            rangeSource = new ArrayList<>();
            for (ExceptionHandler r : method.getExceptionHandlers())
            {
                if (r.getHandlerBlock() == root)
                {
                    rangeSource.add(r);
                }
            }
        }
        else
        {
            rangeSource = rethrowers;
        }
        for (ExceptionHandler r : rangeSource)
        {
            int lo = r.getTryStart() == null ? -1 : r.getTryStart().getBytecodeOffset();
            int hi = r.getTryEnd() == null ? -1 : r.getTryEnd().getBytecodeOffset();
            if (lo < 0 || hi <= lo)
            {
                trace("finally-dedup bail#6 root=" + root.getBytecodeOffset());
                return false;
            }
            for (IRBlock b : method.getBlocks())
            {
                int off = b.getBytecodeOffset();
                if (off >= lo && off < hi)
                {
                    protectedBlocks.add(b);
                }
            }
        }
        if (protectedBlocks.isEmpty())
        {
            trace("finally-dedup bail#6 root=" + root.getBytecodeOffset());
            return false;
        }
        // The clause's own blocks land in the protected set through javac's self-protection entry
        // (the finally clause is covered by a rethrowing entry pointing at its own handler); the
        // template is never a copy site, so it is excluded from the hunt.
        protectedBlocks.removeAll(tblocks);
        protectedBlocks.remove(root);
        protectedBlocks.remove(rethrowBlk);
        // A return INSIDE the protected range carries its own inlined copy before it: javac places the
        // copy and the return in-range when the try ends by returning. Match those copies first - each
        // must be a protected subgraph whose continuation IS an in-range return block - then every
        // in-range return must be covered by one, and every normal exit LEAVING the range must land on a
        // matched copy of its own. Coverage is demanded against the RAW split ranges even when the
        // copy hunt runs over the merged view: a return in the GAP between ranges is the
        // continuation a relowered layout parked there, protected by nothing and owed no copy.
        Set<IRBlock> rawRangeBlocks = new HashSet<>();
        for (ExceptionHandler r : method.getExceptionHandlers())
        {
            if (r.getHandlerBlock() != root || r.getTryStart() == null || r.getTryEnd() == null)
            {
                continue;
            }
            int lo = r.getTryStart().getBytecodeOffset();
            int hi = r.getTryEnd().getBytecodeOffset();
            for (IRBlock b : method.getBlocks())
            {
                if (b.getBytecodeOffset() >= lo && b.getBytecodeOffset() < hi)
                {
                    rawRangeBlocks.add(b);
                }
            }
        }
        Set<IRBlock> returnBlocks = new HashSet<>();
        for (IRBlock p : protectedBlocks)
        {
            if (p.getTerminator() instanceof ReturnInstruction && rawRangeBlocks.contains(p))
            {
                returnBlocks.add(p);
            }
        }
        List<Map<IRBlock, IRBlock>> matches = new ArrayList<>();
        Set<IRBlock> matchedCopyBlocks = new HashSet<>();
        Set<IRBlock> coveredReturns = new HashSet<>();
        Set<ExceptionHandler> copyNestedHandlers = new HashSet<>();
        if (!returnBlocks.isEmpty())
        {
            // The signature comes from the template's first MEANINGFUL block: the root may hold only the
            // caught-exception store (split into its own block when the clause is itself protected), and
            // an empty signature would prefilter every candidate away.
            IRBlock sigBlock = root;
            while (matchableInstructions(sigBlock, sigBlock == root).isEmpty()
                    && !(sigBlock.getTerminator() instanceof BranchInstruction))
            {
                IRBlock nxt = singleNormalSuccessor(sigBlock);
                if (nxt == null || !tblocks.contains(nxt) || nxt == rethrowBlk)
                {
                    break;
                }
                sigBlock = nxt;
            }
            List<IRInstruction> rootSignature = matchableInstructions(sigBlock, sigBlock == root);
            for (IRBlock candidate : protectedBlocks)
            {
                if (returnBlocks.contains(candidate) || matchedCopyBlocks.contains(candidate) || candidate == root)
                {
                    continue;
                }
                // Cheap prefilter before the full parallel walk: the copy's root must open with the same
                // instruction as the template's, or the walk cannot possibly succeed.
                List<IRInstruction> candSignature = matchableInstructions(candidate, false);
                if (rootSignature.isEmpty() != candSignature.isEmpty()
                        || (!rootSignature.isEmpty()
                            && !sameFinallyInstr(rootSignature.get(0), candSignature.get(0))))
                {
                    continue;
                }
                Map<IRBlock, IRBlock> map =
                        matchFinallySubgraph(root, candidate, tblocks, rethrowBlk, nestedTemplate, copyNestedHandlers);
                if (map == null)
                {
                    continue;
                }
                IRBlock exitOf = null;
                for (IRBlock copy : map.values())
                {
                    for (Map.Entry<IRBlock, EdgeType> e : copy.getSuccessorEdgeTypes().entrySet())
                    {
                        if (e.getValue() == EdgeType.NORMAL && !map.containsValue(e.getKey()))
                        {
                            exitOf = e.getKey();
                        }
                    }
                }
                if (exitOf != null)
                {
                    exitOf = resolveThroughEmptyChain(exitOf);
                }
                if (exitOf != null && returnBlocks.contains(exitOf))
                {
                    matches.add(map);
                    matchedCopyBlocks.addAll(map.values());
                    coveredReturns.add(exitOf);
                }
            }
            if (!coveredReturns.containsAll(returnBlocks))
            {
                if (TRACE)
                {
                    Set<IRBlock> missing = new HashSet<>(returnBlocks);
                    missing.removeAll(coveredReturns);
                    StringBuilder sb = new StringBuilder();
                    for (IRBlock mb : missing)
                    {
                        sb.append(mb.getBytecodeOffset()).append(",");
                    }
                    trace("finally-dedup bail#7 root=" + root.getBytecodeOffset() + " uncovered=" + sb);
                }
                else
                {
                    trace("finally-dedup bail#7 root=" + root.getBytecodeOffset());
                }
                return false;
            }
        }
        for (IRBlock p : protectedBlocks)
        {
            if (matchedCopyBlocks.contains(p))
            {
                continue;
            }
            // Exit coverage is OWED only by blocks in the RAW split ranges. A gap block inside the
            // merged view can be the range's own fall-through chain reaching its copy - so its exits
            // are still HUNTED - or relowered-parked continuation whose exits carry no copies - so a
            // failed match there is not the family's failure.
            boolean mustCover = rawRangeBlocks.contains(p);
            for (Map.Entry<IRBlock, EdgeType> e : p.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() != EdgeType.NORMAL
                        || protectedBlocks.contains(e.getKey()) || e.getKey() == root
                        || tblocks.contains(e.getKey())
                        || matchedCopyBlocks.contains(e.getKey()))
                {
                    continue;
                }
                IRBlock cand = resolveThroughEmptyChain(e.getKey());
                Map<IRBlock, IRBlock> map =
                        matchFinallySubgraphPeeled(root, cand, tblocks, rethrowBlk, nestedTemplate, copyNestedHandlers);
                if (map == null)
                {
                    if (mustCover)
                    {
                        trace("finally-dedup bail#8 root=" + root.getBytecodeOffset()
                                + " exitFrom=" + p.getBytecodeOffset() + " cand=" + cand.getBytecodeOffset());
                        return false;
                    }
                    continue;
                }
                matches.add(map);
            }
        }
        if (matches.isEmpty())
        {
            trace("finally-dedup bail#9 root=" + root.getBytecodeOffset());
            return false;
        }
        // Excision must not gut a block that begins a live protected range of its own: its handler's
        // recovery would find an empty try. The TEMPLATE may freely contain protected calls (javac guards
        // a finally handler's close) - template blocks are only compared, never touched.
        Set<IRBlock> allCopyBlocks = new HashSet<>();
        for (Map<IRBlock, IRBlock> m : matches)
        {
            allCopyBlocks.addAll(m.values());
        }
        for (Map<IRBlock, IRBlock> map : matches)
        {
            for (IRBlock copy : map.values())
            {
                ExceptionHandler live = findUnprocessedHandlerStartingAt(copy);
                // A range boundary of a handler in the SAME dedup offering is not a live nested try:
                // that family's own group excises its copies, and the whole construct is consumed
                // together (an inner resource's copy legitimately starts the outer family's next
                // protected range). A handler that merely BORDERS the copy - its protected range keeps
                // blocks outside every excised copy - stays intact too: only gutting its whole range
                // would leave its recovery an empty try.
                boolean handlerKeepsContent = false;
                if (!templateHasNestedTry && live != null && live.getTryBlocks() != null)
                {
                    for (IRBlock tb : live.getTryBlocks())
                    {
                        if (!allCopyBlocks.contains(tb) && !matchableInstructions(tb, false).isEmpty())
                        {
                            handlerKeepsContent = true;
                            break;
                        }
                    }
                }
                // A live handler whose WHOLE protected range lies within the matched copies is the copy's
                // own internal guard (a finally body's close-guard, inlined along with each copy): it is
                // consumed with the copy like a nested-template mirror. A handler whose range extends
                // beyond the copies protects REAL code the match merely resembles - the bail stands.
                if (live != null && !copyNestedHandlers.contains(live)
                        && live.getTryBlocks() != null && !live.getTryBlocks().isEmpty())
                {
                    boolean copyInternal = true;
                    for (IRBlock tb : live.getTryBlocks())
                    {
                        if (!allCopyBlocks.contains(tb))
                        {
                            copyInternal = false;
                            break;
                        }
                    }
                    if (copyInternal)
                    {
                        copyNestedHandlers.add(live);
                    }
                }
                if (live != null && !copyNestedHandlers.contains(live)
                        && !currentDedupOffering.contains(live)
                        && !handlerKeepsContent)
                {
                    trace("finally-dedup bail#10 root=" + root.getBytecodeOffset());
                    return false;
                }
            }
        }
        for (Map<IRBlock, IRBlock> map : matches)
        {
            IRBlock copyRoot = null;
            IRBlock copyExit = null;
            for (Map.Entry<IRBlock, IRBlock> e : map.entrySet())
            {
                if (copyRoot == null)
                {
                    copyRoot = e.getValue();
                }
                for (Map.Entry<IRBlock, EdgeType> se : e.getValue().getSuccessorEdgeTypes().entrySet())
                {
                    if (se.getValue() == EdgeType.NORMAL && !map.containsValue(se.getKey()))
                    {
                        copyExit = se.getKey();
                    }
                }
            }
            if (copyRoot != null && copyExit != null)
            {
                excisedCopyExits.put(copyRoot, copyExit);
            }
            for (IRBlock copy : map.values())
            {
                boolean hadOwnWork = false;
                for (IRInstruction ins : new ArrayList<>(copy.getInstructions()))
                {
                    if (!ins.isTerminator() && !(ins instanceof CopyInstruction))
                    {
                        copy.removeInstruction(ins);
                        hadOwnWork = true;
                    }
                }
                if (hadOwnWork)
                {
                    excisedFinallyCopyBlocks.add(copy);
                }
                // On the finally-with-user-catch path the copies sit INSIDE the recovered window (the
                // finally's range is split around them), so the body walk would re-emit the excised shells
                // as skeleton loops; consume them outright. Only blocks the excision actually emptied are
                // consumed - a block the parallel walk merely passed through still holds the protected
                // body's own work. The gap-resident shells of the other paths stay walkable: their guard
                // conditions are recovered from the shells by the existing folds.
                if (consumeShells && hadOwnWork)
                {
                    consumedFinallyShells.add(copy);
                    context.markProcessed(copy);
                }
            }
        }
        // The copies' mirrored nested handlers protect code that no longer exists; retire them fully -
        // processed marks AND removal from the method's handler table - so no recovery path (including
        // scaffolding that enumerated handler groups before this ran) resurrects an empty try around the
        // excised blocks. The same applies to ANY handler whose whole protected range the excision
        // emptied (a chained inner copy's suppress catch reached through the exit resolution).
        for (ExceptionHandler h : copyNestedHandlers)
        {
            processedTryHandlers.add(h);
            if (h.getHandlerBlock() != null)
            {
                processedHandlerBlocks.add(h.getHandlerBlock());
            }
            if (h.getTryStart() != null)
            {
                retiredTryBoundaries.add(h.getTryStart());
            }
            method.getExceptionHandlers().remove(h);
        }
        for (ExceptionHandler h : new ArrayList<>(method.getExceptionHandlers()))
        {
            Set<IRBlock> hTry = h.getTryBlocks();
            if (h.getHandlerBlock() == null || hTry == null || hTry.isEmpty()
                    || processedTryHandlers.contains(h) || tblocks.contains(h.getHandlerBlock()))
            {
                continue;
            }
            boolean allEmpty = true;
            for (IRBlock tb : hTry)
            {
                if (!matchableInstructions(tb, false).isEmpty())
                {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty)
            {
                processedTryHandlers.add(h);
                processedHandlerBlocks.add(h.getHandlerBlock());
                if (h.getTryStart() != null)
                {
                    retiredTryBoundaries.add(h.getTryStart());
                }
                method.getExceptionHandlers().remove(h);
            }
        }
        finallyDeduped.addAll(rethrowers);
        return true;
    }

    /**
     * As {@link #matchFinallySubgraph}, retrying with LEADING GUARDS PEELED when the direct match fails.
     */
    private Map<IRBlock, IRBlock> matchFinallySubgraphPeeled(IRBlock troot, IRBlock croot, Set<IRBlock> tblocks, IRBlock rethrowBlk, Map<IRBlock, ExceptionHandler> nestedTemplate, Set<ExceptionHandler> copyNestedHandlers)
    {
        Map<IRBlock, IRBlock> map =
                matchFinallySubgraph(troot, croot, tblocks, rethrowBlk, nestedTemplate, copyNestedHandlers);
        if (map != null)
        {
            return map;
        }
        IRBlock entry = troot;
        for (int peel = 0; peel < 3; peel++)
        {
            while (matchableInstructions(entry, entry == troot).isEmpty()
                    && !(entry.getTerminator() instanceof BranchInstruction))
            {
                IRBlock nxt = singleNormalSuccessor(entry);
                if (nxt == null || !tblocks.contains(nxt) || nxt == rethrowBlk)
                {
                    return null;
                }
                entry = nxt;
            }
            if (!(entry.getTerminator() instanceof BranchInstruction))
            {
                return null;
            }
            for (IRInstruction ins : matchableInstructions(entry, entry == troot))
            {
                if (!(ins instanceof LoadLocalInstruction || ins instanceof ConstantInstruction))
                {
                    return null;
                }
            }
            BranchInstruction br = (BranchInstruction) entry.getTerminator();
            IRBlock keep;
            if (peeledArmRethrows(br.getTrueTarget(), rethrowBlk, tblocks))
            {
                keep = br.getFalseTarget();
            }
            else if (peeledArmRethrows(br.getFalseTarget(), rethrowBlk, tblocks))
            {
                keep = br.getTrueTarget();
            }
            else
            {
                return null;
            }
            if (keep == null || !tblocks.contains(keep))
            {
                return null;
            }
            map = matchFinallySubgraph(keep, croot, tblocks, rethrowBlk, nestedTemplate, copyNestedHandlers);
            if (map != null)
            {
                return map;
            }
            entry = keep;
        }
        return null;
    }

    /**
     * Whether a peeled guard's discarded arm only rethrows (directly or through empty pads).
     */
    private boolean peeledArmRethrows(IRBlock arm, IRBlock rethrowBlk, Set<IRBlock> tblocks)
    {
        if (arm == null)
        {
            return false;
        }
        IRBlock r = resolveThroughEmptyChain(arm);
        if (r == rethrowBlk)
        {
            return true;
        }
        return r != null && tblocks.contains(r) && isBareRethrowTail(r);
    }

    /**
     * Parallel walk of the template subtree and a candidate copy.
     */
    private Map<IRBlock, IRBlock> matchFinallySubgraph(IRBlock troot, IRBlock croot, Set<IRBlock> tblocks, IRBlock rethrowBlk, Map<IRBlock, ExceptionHandler> nestedTemplate, Set<ExceptionHandler> copyNestedHandlers)
    {
        Map<IRBlock, IRBlock> map = new LinkedHashMap<>();
        Map<Integer, Integer> slotMap = new HashMap<>();
        Deque<IRBlock[]> work = new ArrayDeque<>();
        // The template root holds only the caught-exception store; the clause's first real block may be
        // its successor (javac splits the astore into its own block when the clause is itself protected).
        // A copy has no store, so the parallel walk starts at the template's first MEANINGFUL block.
        IRBlock tstart = troot;
        while (matchableInstructions(tstart, tstart == troot).isEmpty()
                && !(tstart.getTerminator() instanceof BranchInstruction))
        {
            IRBlock nxt = singleNormalSuccessor(tstart);
            if (nxt == null || !tblocks.contains(nxt) || nxt == rethrowBlk)
            {
                break;
            }
            tstart = nxt;
        }
        work.add(new IRBlock[]{tstart, croot});
        IRBlock exit = null;
        List<ExceptionHandler> pendingCopyNested = new ArrayList<>();
        while (!work.isEmpty())
        {
            IRBlock[] pair = work.poll();
            IRBlock t = pair[0];
            IRBlock c = pair[1];
            IRBlock seen = map.get(t);
            if (seen != null)
            {
                if (seen != c)
                {
                    return null;
                }
                continue;
            }
            map.put(t, c);
            // A template block protected by an absorbed nested handler must find the SAME nested catch
            // mirrored on the copy side: a copy-side handler of the same catch type protecting this copy
            // block, foreign to the template. Their handler subgraphs join the parallel walk; the copy's
            // handler is retired with the excision.
            for (Map.Entry<IRBlock, ExceptionHandler> nt : nestedTemplate.entrySet())
            {
                ExceptionHandler th = nt.getValue();
                if (th.getTryBlocks() == null || !th.getTryBlocks().contains(t))
                {
                    continue;
                }
                ExceptionHandler ch = null;
                for (ExceptionHandler h : context.getIrMethod().getExceptionHandlers())
                {
                    if (h == th || h.getHandlerBlock() == null || tblocks.contains(h.getHandlerBlock())
                            || h.getTryBlocks() == null || !h.getTryBlocks().contains(c)
                            || nestedTemplate.containsKey(h.getHandlerBlock()))
                    {
                        continue;
                    }
                    // The mirror must be structurally the template handler's twin - an enclosing clause
                    // handler also covers this copy block and shares the catch type, but protects a far
                    // larger range and is no nested close-guard.
                    if (th.getTryBlocks() != null && h.getTryBlocks().size() != th.getTryBlocks().size())
                    {
                        continue;
                    }
                    // A genuine nested close-guard SWALLOWS (logs or suppresses and falls through); a
                    // finally/suppress CLAUSE rethrows. An enclosing clause handler covers this copy
                    // block with the same catch type but rethrows - it is no mirror.
                    if (handlerRethrows(h))
                    {
                        continue;
                    }
                    if (h.isCatchAll() != th.isCatchAll())
                    {
                        continue;
                    }
                    if (!h.isCatchAll() && !h.getCatchType().getInternalName()
                            .equals(th.getCatchType().getInternalName()))
                    {
                        continue;
                    }
                    boolean copySideOnly = true;
                    for (IRBlock tb : h.getTryBlocks())
                    {
                        if (tblocks.contains(tb))
                        {
                            copySideOnly = false;
                            break;
                        }
                    }
                    if (!copySideOnly)
                    {
                        continue;
                    }
                    ch = h;
                    break;
                }
                if (ch == null)
                {
                    // No mirrored handler on the copy side: javac's modern try-with-resources desugar
                    // protects the close ONLY inside the exception-path clause (addSuppressed), while the
                    // normal-path copy closes unprotected - an intentional asymmetry. The copy simply
                    // never traverses the protected variant; nothing to pair or retire.
                    continue;
                }
                work.add(new IRBlock[]{th.getHandlerBlock(), ch.getHandlerBlock()});
                pendingCopyNested.add(ch);
            }
            List<IRInstruction> ti = matchableInstructions(t, t == troot);
            List<IRInstruction> ci = matchableInstructions(c, false);
            if (ti.size() != ci.size())
            {
                return null;
            }
            for (int i = 0; i < ti.size(); i++)
            {
                if (!sameFinallyInstr(ti.get(i), ci.get(i), slotMap))
                {
                    return null;
                }
            }
            IRInstruction tt = t.getTerminator();
            if (tt instanceof BranchInstruction)
            {
                if (!(c.getTerminator() instanceof BranchInstruction))
                {
                    return null;
                }
                BranchInstruction tb = (BranchInstruction) tt;
                BranchInstruction cb = (BranchInstruction) c.getTerminator();
                // Verbatim copies branch on the SAME comparison; pairing arms across different
                // condition kinds lets an unrelated conditional masquerade as the template's guard.
                if (tb.getCondition() != cb.getCondition())
                {
                    return null;
                }
                IRBlock[][] pairs = {
                        {tb.getTrueTarget(), cb.getTrueTarget()},
                        {tb.getFalseTarget(), cb.getFalseTarget()}};
                for (IRBlock[] pr : pairs)
                {
                    if (pr[0] == rethrowBlk || !tblocks.contains(pr[0])
                            || (leadsOnlyToRethrow(pr[0], tblocks, rethrowBlk)
                                && !matchableShapeEquals(pr[0], pr[1], slotMap)))
                    {
                        if (exit != null && exit != pr[1])
                        {
                            return null;
                        }
                        exit = pr[1];
                    }
                    else
                    {
                        work.add(pr);
                    }
                }
            }
            else
            {
                IRBlock tn = singleNormalSuccessor(t);
                IRBlock cn = singleNormalSuccessor(c);
                if (tn == null || cn == null)
                {
                    return null;
                }
                if (tn == rethrowBlk || !tblocks.contains(tn)
                        || (leadsOnlyToRethrow(tn, tblocks, rethrowBlk)
                            && !matchableShapeEquals(tn, cn, slotMap)))
                {
                    if (exit != null && exit != cn)
                    {
                        return null;
                    }
                    exit = cn;
                }
                else
                {
                    work.add(new IRBlock[]{tn, cn});
                }
            }
        }
        if (exit == null || map.containsValue(exit))
        {
            return null;
        }
        copyNestedHandlers.addAll(pendingCopyNested);
        return map;
    }

    /**
     * The blocks of every raw exception-table range sharing {@code handler}'s handler block.
     */
    private Set<IRBlock> tryRangeBlocks(ExceptionHandler handler)
    {
        Set<IRBlock> out = new HashSet<>();
        if (handler.getHandlerBlock() == null)
        {
            return out;
        }
        for (ExceptionHandler h : context.getIrMethod().getExceptionHandlers())
        {
            if (h.getHandlerBlock() != handler.getHandlerBlock() || h.getTryBlocks() == null)
            {
                continue;
            }
            out.addAll(h.getTryBlocks());
        }
        return out;
    }

    /**
     * Whether the statement tree contains a return - recursion via the structural children.
     */
    private boolean containsReturn(List<Statement> stmts)
    {
        for (Statement st : stmts)
        {
            if (st instanceof ReturnStmt)
            {
                return true;
            }
            if (st instanceof BlockStmt && containsReturn(((BlockStmt) st).getStatements()))
            {
                return true;
            }
            if (st instanceof IfStmt)
            {
                IfStmt is = (IfStmt) st;
                if (containsReturn(flattenToStatements(is.getThenBranch())))
                {
                    return true;
                }
                if (is.getElseBranch() != null && containsReturn(flattenToStatements(is.getElseBranch())))
                {
                    return true;
                }
            }
            if (st instanceof TryCatchStmt)
            {
                TryCatchStmt tc = (TryCatchStmt) st;
                if (containsReturn(flattenToStatements(tc.getTryBlock())))
                {
                    return true;
                }
                for (CatchClause cc : tc.getCatches())
                {
                    if (containsReturn(flattenToStatements(cc.body())))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether {@code exit} is a stash-reload return - {@code load_local k; return} where the store to
     * {@code k} lives inside the protected range, i.e. javac's stashed boundary return whose value the try
     * body already owns.
     */
    private boolean isStashReloadReturn(IRBlock exit, Set<IRBlock> protectedBlocks)
    {
        LoadLocalInstruction load = null;
        for (IRInstruction ins : exit.getInstructions())
        {
            if (ins instanceof CopyInstruction)
            {
                continue;
            }
            if (ins instanceof LoadLocalInstruction && load == null)
            {
                load = (LoadLocalInstruction) ins;
                continue;
            }
            if (ins instanceof ReturnInstruction && load != null)
            {
                continue;
            }
            if (ins.isTerminator())
            {
                continue;
            }
            return false;
        }
        if (load == null || !(exit.getTerminator() instanceof ReturnInstruction))
        {
            return false;
        }
        int slot = load.getLocalIndex();
        for (IRBlock pb : protectedBlocks)
        {
            for (IRInstruction ins : pb.getInstructions())
            {
                if (ins instanceof StoreLocalInstruction && ((StoreLocalInstruction) ins).getLocalIndex() == slot)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The block comparable-instruction list.
     */
    private List<IRInstruction> matchableInstructions(IRBlock b, boolean skipLeadingStore)
    {
        List<IRInstruction> out = new ArrayList<>();
        for (IRInstruction ins : b.getInstructions())
        {
            if (ins.isTerminator() || ins instanceof CopyInstruction)
            {
                continue;
            }
            if (skipLeadingStore && out.isEmpty() && ins instanceof StoreLocalInstruction)
            {
                continue;
            }
            out.add(ins);
        }
        return out;
    }

    private IRBlock singleNormalSuccessor(IRBlock b)
    {
        IRBlock next = null;
        for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
        {
            if (e.getValue() == EdgeType.NORMAL)
            {
                if (next != null)
                {
                    return null;
                }
                next = e.getKey();
            }
        }
        return next;
    }

    private List<IRInstruction> straightLineFinallyTemplate(ExceptionHandler h)
    {
        List<IRBlock> chain = finallyHandlerChain(h);
        if (chain == null)
        {
            return null;
        }
        // A finally body carrying its own catch - a handler whose WHOLE protected range lies within the
        // chain - is no straight-line template even when the normal-path chain is linear: its inlined
        // copies bear MIRRORED nested handlers that only the branchy matcher's nested-template pairing
        // retires. An ENCLOSING handler that merely covers the chain along with much else (an outer
        // catch wrapping the whole construct) does not disqualify the template.
        Set<IRBlock> chainSet = new HashSet<>(chain);
        Map<IRBlock, Set<IRBlock>> otherRanges = new LinkedHashMap<>();
        for (ExceptionHandler other : context.getIrMethod().getExceptionHandlers())
        {
            if (other == h || other.getTryBlocks() == null || other.getTryBlocks().isEmpty()
                    || other.getHandlerBlock() == h.getHandlerBlock()
                    || other.getHandlerBlock() == null)
            {
                continue;
            }
            otherRanges.computeIfAbsent(other.getHandlerBlock(), k -> new HashSet<>())
                    .addAll(other.getTryBlocks());
        }
        for (Set<IRBlock> union : otherRanges.values())
        {
            // Judged on the handler's WHOLE protected range across its entries: an enclosing catch also
            // protects blocks outside the chain (its other range pieces), while a genuinely
            // nested-in-template catch protects chain blocks only.
            if (chainSet.containsAll(union))
            {
                return null;
            }
        }

        IRBlock lastBlock = chain.isEmpty() ? null : chain.get(chain.size() - 1);
        if (lastBlock == null)
        {
            return null;
        }
        IRInstruction lastTerm = lastBlock.getTerminator();
        if (!(lastTerm instanceof SimpleInstruction) || ((SimpleInstruction) lastTerm).getOp() != SimpleOp.ATHROW)
        {
            return null;
        }

        List<IRInstruction> flat = new ArrayList<>();
        for (int c = 0; c < chain.size(); c++)
        {
            List<IRInstruction> in = chain.get(c).getInstructions();
            int end = in.size();
            if (c == chain.size() - 1)
            {
                end--; // drop the trailing athrow
            }
            else if (end > 0 && in.get(end - 1) instanceof SimpleInstruction
                    && ((SimpleInstruction) in.get(end - 1)).getOp() == SimpleOp.GOTO)
            {
                end--; // drop an inter-block goto
            }
            for (int i = 0; i < end; i++)
            {
                flat.add(in.get(i));
            }
        }
        // Drop the leading materialization+store of the caught exception and the trailing reload of it that
        // feeds the rethrow, leaving only the finally body - the sequence javac inlined on every normal exit.
        int start = 0;
        while (start < flat.size()
                && (flat.get(start) instanceof CopyInstruction || flat.get(start) instanceof StoreLocalInstruction))
        {
            start++;
        }
        int stop = flat.size();
        if (stop > start && flat.get(stop - 1) instanceof LoadLocalInstruction)
        {
            stop--;
        }
        List<IRInstruction> template = new ArrayList<>();
        for (int i = start; i < stop; i++)
        {
            if (!isMatchableFinallyInstr(flat.get(i)))
            {
                return null;
            }
            template.add(flat.get(i));
        }
        return template.isEmpty() ? null : template;
    }

    /**
     * Instruction kinds a straight-line finally copy is matched by: no control flow, structurally comparable.
     */
    private boolean isMatchableFinallyInstr(IRInstruction ins)
    {
        if (extendedFinallyDedup && (ins instanceof BinaryOpInstruction || ins instanceof UnaryOpInstruction))
        {
            return true;
        }
        return ins instanceof FieldAccessInstruction || ins instanceof InvokeInstruction
                || ins instanceof ConstantInstruction || ins instanceof LoadLocalInstruction
                || ins instanceof StoreLocalInstruction;
    }

    /**
     * Whether {@code t}'s and {@code c}'s comparable instructions match shape-for-shape under a probe copy
     * of the slot correspondence.
     */
    private boolean matchableShapeEquals(IRBlock t, IRBlock c, Map<Integer, Integer> slotMap)
    {
        List<IRInstruction> ti = matchableInstructions(t, false);
        List<IRInstruction> ci = matchableInstructions(c, false);
        if (ti.size() != ci.size())
        {
            return false;
        }
        Map<Integer, Integer> probe = new HashMap<>(slotMap);
        for (int i = 0; i < ti.size(); i++)
        {
            if (!sameFinallyInstr(ti.get(i), ci.get(i), probe))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * True when every path from {@code b} within the template reaches the rethrow carrying only local LOADS - the
     * caught exception being staged for its {@code athrow}.
     */
    private boolean leadsOnlyToRethrow(IRBlock b, Set<IRBlock> tblocks, IRBlock rethrowBlk)
    {
        Deque<IRBlock> work = new ArrayDeque<>();
        Set<IRBlock> seen = new HashSet<>();
        work.add(b);
        while (!work.isEmpty())
        {
            IRBlock x = work.poll();
            if (x == rethrowBlk || !seen.add(x))
            {
                continue;
            }
            if (!tblocks.contains(x))
            {
                return false;
            }
            for (IRInstruction ins : x.getInstructions())
            {
                if (!ins.isTerminator() && !(ins instanceof CopyInstruction)
                        && !(ins instanceof LoadLocalInstruction))
                {
                    return false;
                }
            }
            IRInstruction term = x.getTerminator();
            if (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.ATHROW)
            {
                continue;
            }
            if (term instanceof BranchInstruction)
            {
                return false;
            }
            boolean any = false;
            for (Map.Entry<IRBlock, EdgeType> e : x.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    work.add(e.getKey());
                    any = true;
                }
            }
            if (!any)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Follows {@code b} through blocks with no comparable instructions and a single normal successor - an
     * already-excised copy of an inner finally, or a plain goto connector - to the block where a template match
     * can begin.
     */
    private IRBlock resolveThroughEmptyChain(IRBlock b)
    {
        Set<IRBlock> seen = new HashSet<>();
        while (b != null && seen.add(b))
        {
            IRBlock excised = excisedCopyExits.get(b);
            if (excised != null)
            {
                b = excised;
                continue;
            }
            if (!matchableInstructions(b, false).isEmpty())
            {
                return b;
            }
            IRBlock next = singleNormalSuccessor(b);
            if (next == null)
            {
                return b;
            }
            b = next;
        }
        return b;
    }

    /**
     * True when {@code h}'s handler chain performs nothing but local shuffling before its rethrow - no call, field
     * access or other observable effect.
     */
    private boolean isLocalSpillRethrower(ExceptionHandler h)
    {
        IRBlock hb = h.getHandlerBlock();
        if (hb == null)
        {
            return false;
        }
        Deque<IRBlock> work = new ArrayDeque<>();
        Set<IRBlock> seen = new HashSet<>();
        work.add(hb);
        while (!work.isEmpty())
        {
            IRBlock b = work.poll();
            if (!seen.add(b))
            {
                continue;
            }
            for (IRInstruction ins : b.getInstructions())
            {
                if (ins.isTerminator() || ins instanceof CopyInstruction)
                {
                    continue;
                }
                if (!(ins instanceof LoadLocalInstruction) && !(ins instanceof StoreLocalInstruction))
                {
                    return false;
                }
            }
            IRInstruction term = b.getTerminator();
            if (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.ATHROW)
            {
                continue;
            }
            if (term instanceof BranchInstruction)
            {
                return false;
            }
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    work.add(e.getKey());
                }
            }
        }
        return true;
    }

    /**
     * As {@link #sameFinallyInstr(IRInstruction, IRInstruction)}, but local slots are compared through a
     * consistent correspondence built during one subgraph match.
     */
    private boolean sameFinallyInstr(IRInstruction a, IRInstruction b, Map<Integer, Integer> slotMap)
    {
        if (slotMap != null && a.getClass() == b.getClass()
                && (a instanceof LoadLocalInstruction || a instanceof StoreLocalInstruction))
        {
            int ta = a instanceof LoadLocalInstruction
                    ? ((LoadLocalInstruction) a).getLocalIndex() : ((StoreLocalInstruction) a).getLocalIndex();
            int tb = b instanceof LoadLocalInstruction
                    ? ((LoadLocalInstruction) b).getLocalIndex() : ((StoreLocalInstruction) b).getLocalIndex();
            Integer bound = slotMap.get(ta);
            if (bound != null)
            {
                return bound == tb;
            }
            // Only a slot the template DEFINES (first sight is a store - the handler's caught exception,
            // a nested catch's variable) may correspond to a different copy slot. A slot first READ is
            // one of the clause's free variables - the resource, the suppress flag - which javac's
            // genuine inlined copies share verbatim with the clause; remapping those lets an isomorphic
            // copy of a DIFFERENT resource cross-match and the wrong code be excised.
            if (a instanceof LoadLocalInstruction && ta != tb)
            {
                return false;
            }
            slotMap.put(ta, tb);
            return true;
        }
        return sameFinallyInstr(a, b);
    }

    /**
     * Structural equality of two finally instructions, ignoring SSA operand identity (verbatim javac copies).
     */
    private boolean sameFinallyInstr(IRInstruction a, IRInstruction b)
    {
        if (a.getClass() != b.getClass())
        {
            return false;
        }
        if (a instanceof FieldAccessInstruction)
        {
            FieldAccessInstruction x = (FieldAccessInstruction) a, y = (FieldAccessInstruction) b;
            return x.isStatic() == y.isStatic() && x.getOwner().equals(y.getOwner())
                    && x.getName().equals(y.getName()) && x.getDescriptor().equals(y.getDescriptor());
        }
        if (a instanceof InvokeInstruction)
        {
            InvokeInstruction x = (InvokeInstruction) a, y = (InvokeInstruction) b;
            return x.getInvokeType() == y.getInvokeType() && x.getOwner().equals(y.getOwner())
                    && x.getName().equals(y.getName()) && x.getDescriptor().equals(y.getDescriptor());
        }
        if (a instanceof ConstantInstruction)
        {
            return String.valueOf(((ConstantInstruction) a).getConstant())
                    .equals(String.valueOf(((ConstantInstruction) b).getConstant()));
        }
        if (a instanceof LoadLocalInstruction)
        {
            return ((LoadLocalInstruction) a).getLocalIndex() == ((LoadLocalInstruction) b).getLocalIndex();
        }
        if (a instanceof StoreLocalInstruction)
        {
            return ((StoreLocalInstruction) a).getLocalIndex() == ((StoreLocalInstruction) b).getLocalIndex();
        }
        if (a instanceof BinaryOpInstruction)
        {
            // Operands are positional within the matched contiguous sequence (the loads and constants
            // around the op are compared themselves), so the operator is the instruction's identity.
            return ((BinaryOpInstruction) a).getOp() == ((BinaryOpInstruction) b).getOp();
        }
        if (a instanceof UnaryOpInstruction)
        {
            return ((UnaryOpInstruction) a).getOp() == ((UnaryOpInstruction) b).getOp();
        }
        return false;
    }

    /**
     * As {@link #contiguousTemplateStart} but for the EVIDENCE probe.
     */
    private int probeTemplateStart(IRBlock block, List<IRInstruction> template)
    {
        int strict = contiguousTemplateStart(block, template);
        if (strict >= 0)
        {
            return strict;
        }
        List<IRInstruction> in = block.getInstructions();
        int n = template.size();
        int start = in.size() - n;
        if (start < 0 || isBareRethrowTail(block))
        {
            return -1;
        }
        for (int i = 0; i < n; i++)
        {
            if (!sameFinallyInstr(template.get(i), in.get(start + i)))
            {
                return -1;
            }
        }
        return start;
    }

    private int contiguousTemplateStart(IRBlock block, List<IRInstruction> template)
    {
        List<IRInstruction> in = block.getInstructions();
        int n = template.size();
        for (int start = 0; start + n < in.size(); start++)
        {
            boolean ok = true;
            for (int i = 0; i < n; i++)
            {
                if (!sameFinallyInstr(template.get(i), in.get(start + i)))
                {
                    ok = false;
                    break;
                }
            }
            if (ok)
            {
                return start;
            }
        }
        return -1;
    }

    private final Set<ExceptionHandler> finallyDeduped = new HashSet<>();

    /**
     * Try-range start blocks of handlers the de-duplication RETIRED from the method's handler table.
     */
    private final Set<IRBlock> retiredTryBoundaries = new HashSet<>();
    /**
     * The rethrower families offered to the current (possibly partitioned) de-duplication together.
     */
    private final Set<ExceptionHandler> currentDedupOffering = new HashSet<>();
    /** Each excised finally copy's root mapped to its continuation, so a later (outer) group's exit hunt
     * resolves through the emptied - possibly branchy - copy to where its own copy begins. */
    private final Map<IRBlock, IRBlock> excisedCopyExits = new HashMap<>();
    /**
     * Every block emptied by a finally-copy excision.
     */
    private final Set<IRBlock> excisedFinallyCopyBlocks = new HashSet<>();
    /**
     * Widens the finally de-duplication (arithmetic templates, split-handler chains, shared-exit coverage) for the
     * staged finally-after-prelude path only.
     */
    private boolean extendedFinallyDedup;

    /**
     * Removes javac's inlined straight-line finally copies from the exits of a protected range.
     */
    private boolean dedupStraightLineFinally(List<ExceptionHandler> regionHandlers)
    {
        return dedupStraightLineFinally(regionHandlers, false);
    }

    private boolean dedupStraightLineFinally(List<ExceptionHandler> regionHandlers, boolean consumeShells)
    {
        IRMethod method = context.getIrMethod();
        if (method == null)
        {
            return false;
        }
        List<ExceptionHandler> rethrowers = new ArrayList<>();
        for (ExceptionHandler h : regionHandlers)
        {
            // A handler that wraps the caught exception in a FRESH one is a user catch, not a finally:
            // its body is no template, and admitting it here poisons the candidate set - one unmatchable
            // pseudo-rethrower declines the whole de-duplication, real finally included. Likewise a
            // rethrower that only shuffles locals - javac's try-with-resources suppress catch
            // (`catch (Throwable t) { suppressed = t; throw t; }`) - has no observable finally body and
            // no inlined copies to hunt; it is recovered as the catch clause it is.
            if (handlerRethrows(h) && !handlerThrowsFreshException(h) && !isLocalSpillRethrower(h)
                    && h.getTryStart() != null && h.getTryEnd() != null
                    && (h.isCatchAll() || h.getHandlerBlock() == null
                        || handlerHasFinallyEvidence(h.getHandlerBlock())))
            {
                // A typed rethrower without finally evidence is a user catch-rethrow, not a finally: it
                // has no inlined copies to hunt, and offering it would decline the whole family - sinking
                // the REAL finallies offered alongside it.
                rethrowers.add(h);
            }
        }
        if (rethrowers.isEmpty())
        {
            return false;
        }
        if (finallyDeduped.containsAll(rethrowers))
        {
            return true;
        }
        for (ExceptionHandler h : rethrowers)
        {
            if (straightLineFinallyTemplate(h) == null)
            {
                // Independent finally handlers (two try-with-resources resources in one region) form
                // separate groups keyed by handler block; each group's all-or-nothing invariant is its
                // own, and the region is de-duplicated only when EVERY group is.
                Map<IRBlock, List<ExceptionHandler>> byRoot = new LinkedHashMap<>();
                for (ExceptionHandler r : rethrowers)
                {
                    byRoot.computeIfAbsent(r.getHandlerBlock(), k -> new ArrayList<>()).add(r);
                }
                currentDedupOffering.clear();
                currentDedupOffering.addAll(rethrowers);
                // Nested finallys chain their inlined copies at shared exits (the inner resource's close
                // runs before the outer's). De-duplicate INNER groups first - smallest protected span -
                // so an outer group's exit hunt can resolve through the already-excised inner copies.
                List<List<ExceptionHandler>> groups = new ArrayList<>(byRoot.values());
                groups.sort(Comparator.comparingInt(g -> {
                    int span = 0;
                    for (ExceptionHandler r : g)
                    {
                        int lo = r.getTryStart() == null ? 0 : r.getTryStart().getBytecodeOffset();
                        int hi = r.getTryEnd() == null ? 0 : r.getTryEnd().getBytecodeOffset();
                        span += Math.max(0, hi - lo);
                    }
                    return span;
                }));
                for (List<ExceptionHandler> group : groups)
                {
                    // A mixed family (a branchy close guard alongside a straight-line accumulator) must
                    // not force every group branchy: a group whose own template IS straight-line gets the
                    // contiguous excision - the branchy matcher walks an empty template subtree for a
                    // single-block finally and would succeed without excising anything.
                    boolean ok = dedupContiguousGroup(group) || dedupBranchySubgraphFinally(group, consumeShells);
                    trace("finally-dedup group root=" + group.get(0).getHandlerBlock().getBytecodeOffset()
                            + " ok=" + ok);
                    if (!ok)
                    {
                        return false;
                    }
                }
                return true;
            }
        }
        return dedupContiguousGroup(rethrowers);
    }

    /**
     * The contiguous (straight-line template) de-duplication for one rethrower group.
     */
    private boolean dedupContiguousGroup(List<ExceptionHandler> rethrowers)
    {
        IRMethod method = context.getIrMethod();
        List<IRInstruction> template = null;
        Set<IRBlock> protectedBlocks = new HashSet<>();
        Set<IRBlock> handlerBlocks = new HashSet<>();
        for (ExceptionHandler h : rethrowers)
        {
            List<IRInstruction> t = straightLineFinallyTemplate(h);
            if (t == null)
            {
                return false;
            }
            if (template == null)
            {
                template = t;
            }
            int lo = h.getTryStart().getBytecodeOffset();
            int hi = h.getTryEnd().getBytecodeOffset();
            for (IRBlock b : method.getBlocks())
            {
                int off = b.getBytecodeOffset();
                if (off >= lo && off < hi)
                {
                    protectedBlocks.add(b);
                }
            }
            if (extendedFinallyDedup)
            {
                List<IRBlock> chain = finallyHandlerChain(h);
                if (chain != null)
                {
                    handlerBlocks.addAll(chain);
                }
            }
            else if (h.getHandlerBlock() != null)
            {
                handlerBlocks.add(h.getHandlerBlock());
            }
        }
        // A self-protecting entry (the finally body covered by its own handler) would put the handler
        // chain in the protected set and excise the clause itself; the template blocks are never copies.
        protectedBlocks.removeAll(handlerBlocks);
        if (protectedBlocks.isEmpty())
        {
            return false;
        }
        Set<IRBlock> scan = new LinkedHashSet<>(protectedBlocks);
        for (IRBlock p : protectedBlocks)
        {
            for (IRBlock s : p.getSuccessors())
            {
                if (!handlerBlocks.contains(s))
                {
                    scan.add(s);
                }
            }
        }
        List<List<IRInstruction>> excisions = new ArrayList<>();
        if (extendedFinallyDedup)
        {
            // The all-or-nothing invariant, per EXIT: every normal edge out of the protected region, and
            // every return from inside it, must be covered by a copy in the edge's source or target block -
            // the finally must have run on that path. A block reached only THROUGH a copy (e.g. a return
            // shared by several already-covered exits) needs no copy of its own.
            Map<IRBlock, Integer> matchAt = new HashMap<>();
            for (IRBlock b : scan)
            {
                if (!handlerBlocks.contains(b))
                {
                    matchAt.put(b, contiguousTemplateStart(b, template));
                }
            }
            for (IRBlock p : protectedBlocks)
            {
                if (handlerBlocks.contains(p))
                {
                    continue;
                }
                boolean pCovered = matchAt.getOrDefault(p, -1) >= 0;
                if (!pCovered && p.getTerminator() instanceof ReturnInstruction)
                {
                    return false;
                }
                for (IRBlock s2 : p.getSuccessors())
                {
                    if (protectedBlocks.contains(s2) || handlerBlocks.contains(s2))
                    {
                        continue;
                    }
                    if (!pCovered && matchAt.getOrDefault(s2, -1) < 0 && !isBareRethrowTail(s2))
                    {
                        return false;
                    }
                }
            }
            for (Map.Entry<IRBlock, Integer> e : matchAt.entrySet())
            {
                if (e.getValue() >= 0)
                {
                    excisions.add(new ArrayList<>(
                            e.getKey().getInstructions().subList(e.getValue(), e.getValue() + template.size())));
                }
            }
        }
        else
        {
            for (IRBlock b : scan)
            {
                int at = contiguousTemplateStart(b, template);
                if (at >= 0)
                {
                    excisions.add(new ArrayList<>(b.getInstructions().subList(at, at + template.size())));
                }
                else if (leavesRegion(b, protectedBlocks) && !isBareRethrowTail(b))
                {
                    // The copy covering this exit may sit in the edge's TARGET instead of the leaving block
                    // itself: javac places the inlined finally in a dedicated block between the protected
                    // range and the continuation. A return leaving from inside the block has no such target
                    // and stays uncovered.
                    boolean coveredByTarget = !(b.getTerminator() instanceof ReturnInstruction);
                    for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
                    {
                        if (e.getValue() != EdgeType.NORMAL)
                        {
                            continue;
                        }
                        IRBlock t = e.getKey();
                        if (protectedBlocks.contains(t) || handlerBlocks.contains(t) || isBareRethrowTail(t))
                        {
                            continue;
                        }
                        if (contiguousTemplateStart(t, template) < 0)
                        {
                            coveredByTarget = false;
                            break;
                        }
                    }
                    if (!coveredByTarget)
                    {
                        return false;
                    }
                }
            }
        }
        if (excisions.isEmpty())
        {
            // Every exit passed the coverage checks yet no copy exists anywhere: the protected range
            // is fully terminal (every path throws into the scaffolding or ends in a bare rethrow
            // tail), so javac had no normal path to inline the finally on. Vacuously de-duplicated -
            // declining would sink the whole family and force the body into the skip-mode walk over
            // copies that do not exist.
            trace("finally-dedup contiguous vacuous root="
                    + (handlerBlocks.isEmpty() ? -1 : handlerBlocks.iterator().next().getBytecodeOffset()));
            finallyDeduped.addAll(rethrowers);
            return true;
        }
        for (List<IRInstruction> run : excisions)
        {
            for (IRInstruction ins : run)
            {
                ins.getBlock().removeInstruction(ins);
            }
        }
        trace("finally-dedup contiguous ok excised=" + excisions.size());
        finallyDeduped.addAll(rethrowers);
        return true;
    }

    /**
     * A block that leaves the protected region: it returns/throws, or has a successor outside the region.
     */
    private boolean leavesRegion(IRBlock b, Set<IRBlock> region)
    {
        IRInstruction term = b.getTerminator();
        if (term instanceof ReturnInstruction)
        {
            return true;
        }
        for (IRBlock s : b.getSuccessors())
        {
            if (!region.contains(s))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * A block whose only real content is a rethrow of a caught exception (the finally handler tail itself).
     */
    private boolean isBareRethrowTail(IRBlock b)
    {
        IRInstruction term = b.getTerminator();
        return term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.ATHROW;
    }

    /**
     * An empty block or one whose only instruction is its goto terminator - a jump pad.
     */
    private boolean isBareShellBlock(IRBlock b)
    {
        if (b.getInstructions().isEmpty())
        {
            return true;
        }
        return b.getInstructions().size() == 1
                && b.getInstructions().get(0) == b.getTerminator()
                && b.getTerminator() instanceof SimpleInstruction
                && ((SimpleInstruction) b.getTerminator()).getOp() == SimpleOp.GOTO;
    }

    /**
     * Recovers blocks for a try region, stopping at the specified stop blocks.
     */
    private List<Statement> recoverBlocksForTry(IRBlock startBlock, Set<IRBlock> stopBlocks, Set<IRBlock> visited)
    {
        return recoverBlocksForTry(startBlock, stopBlocks, visited, false);
    }

    /**
     * Recovers a try body as a wholesale region hand-off.
     */
    private List<Statement> recoverBlocksForTry(IRBlock startBlock, Set<IRBlock> stopBlocks, Set<IRBlock> visited, boolean skipReachingConditions)
    {
        if (!skipReachingConditions)
        {
            List<Statement> structured = rcsStructurer.tryStructureRegion(startBlock, stopBlocks);
            if (structured == null)
            {
                // A nested try inside this body becomes an opaque node instead of failing the region.
                structured = rcsStructurer.tryStructureRegion(startBlock, stopBlocks, true);
            }
            if (structured != null)
            {
                return structured;
            }
        }
        List<Statement> result = new ArrayList<>();
        IRBlock current = startBlock;

        while (current != null && !visited.contains(current) && !stopBlocks.contains(current))
        {
            visited.add(current);

            if (context.isProcessed(current))
            {
                // A block another recovery already emitted is re-emitted ONLY when it is a bare return -
                // a terminator is idempotent, and the walk reaching it means this path's own return
                // converged there. Re-adding any OTHER processed block would run its side effects twice:
                // an inlined finally copy consumed by a clause fold, re-encountered by the continuation
                // walk, would release a lock or close a stream a second time.
                if (isReturnBlock(current))
                {
                    result.addAll(context.getStatements(current));
                }
                IRBlock revisitNext = getNextSequentialBlock(current);
                if (revisitNext == null && stopBlocks.isEmpty())
                {
                    // A re-visited region header (if/switch/loop) has two-plus successors, so
                    // getNextSequentialBlock stops the chain - but the fall-through past it continues
                    // along the region merges to a shared trailing return that other arms already
                    // emitted. Walk the merge chain and re-emit that return (a terminator is
                    // idempotent) without re-adding the intermediate, already-emitted blocks, which
                    // would duplicate them.
                    IRBlock chain = current;
                    Set<IRBlock> chainSeen = new HashSet<>();
                    while (chain != null && chainSeen.add(chain))
                    {
                        if (chain != current && isReturnBlock(chain) && context.isProcessed(chain))
                        {
                            result.addAll(context.getStatements(chain));
                            break;
                        }
                        RegionInfo chainInfo = analyzer.getRegionInfo(chain);
                        chain = chainInfo != null && chainInfo.getMergeBlock() != null
                                ? chainInfo.getMergeBlock()
                                : getNextSequentialBlock(chain);
                    }
                }
                current = revisitNext;
                continue;
            }

            RegionInfo info = analyzer.getRegionInfo(current);
            if (info == null)
            {
                if (current.getTerminator() instanceof SwitchInstruction)
                {
                    // A dispatch the analyzer could not classify (its arms woven through an
                    // enclosing construct's scaffolding) must never fall through its terminator -
                    // the sequential walk would silently adopt one arm and drop the rest. Offer it
                    // to the engine as a terminal region; otherwise decline loudly.
                    OfferResult offered = offerTerminalRegion(current, new HashSet<>(stopBlocks));
                    if (offered != null)
                    {
                        result.addAll(offered.statements);
                        current = offered.continuation != null && !stopBlocks.contains(offered.continuation)
                                && (!context.isProcessed(offered.continuation)
                                    || isTerminalTail(offered.continuation)) ? offered.continuation : null;
                        continue;
                    }
                    throw retiredSchemaRecovery("switch", current);
                }
                List<Statement> blockStmts = recoverSimpleBlock(current);
                result.addAll(blockStmts);
                context.setStatements(current, blockStmts);
                context.markProcessed(current);
                current = getNextSequentialBlock(current);
                continue;
            }

            // A structural region met by the try-body walk is offered to the reaching-condition engine
            // FIRST at exactly the schema recovery's own scope, mirroring the walk-level offers: bounded
            // at the structure's merge or loop exit through the sole-exit preflight, or unbounded for a
            // loop with no exit block. In the skip mode - the body still carries a finally's inlined
            // copies - only LOOP kinds are offered: the copies inflate a GUARD's exit arm and flip its
            // orientation, so if kinds keep the walk, while a loop bounded at its own exit is unaffected.
            {
                IRBlock bodyBound = null;
                boolean bodyTerminalRegion = false;
                boolean strictOffer = false;
                switch (info.getType())
                {
                    case GUARD_CLAUSE:
                        // A guard clause bounds at its merge like an if; the skip mode keeps the
                        // walk (a finally's inlined copies inflate the guard's exit arm).
                        if (!skipReachingConditions)
                        {
                            bodyBound = info.getMergeBlock();
                            bodyTerminalRegion = bodyBound == null;
                        }
                        break;
                    case IF_THEN:
                    case IF_THEN_ELSE:
                        if (!skipReachingConditions)
                        {
                            bodyBound = info.getMergeBlock();
                            bodyTerminalRegion = bodyBound == null;
                        }
                        else if (info.getMergeBlock() != null
                                && regionIsCopyFree(current, info.getMergeBlock(), stopBlocks))
                        {
                            // A surviving inlined copy always precedes an exit from the protected range,
                            // so a diamond whose blocks reach no terminal and whose single exit is its
                            // own non-terminal merge cannot contain one - safe to structure even while
                            // the body still carries the finally's copies elsewhere.
                            bodyBound = info.getMergeBlock();
                            strictOffer = true;
                        }
                        break;
                    case WHILE_LOOP:
                    case DO_WHILE_LOOP:
                    case FOR_LOOP:
                        bodyBound = info.getLoopExit();
                        bodyTerminalRegion = bodyBound == null;
                        break;
                    default:
                        break;
                }
                if (bodyTerminalRegion)
                {
                    OfferResult offered = offerTerminalRegion(current, new HashSet<>(stopBlocks));
                    if (offered != null)
                    {
                        result.addAll(offered.statements);
                        current = offered.continuation != null && !stopBlocks.contains(offered.continuation)
                                && (!context.isProcessed(offered.continuation)
                                || isTerminalTail(offered.continuation)) ? offered.continuation : null;
                        continue;
                    }
                }
                else if (bodyBound != null && !visited.contains(bodyBound))
                {
                    Set<IRBlock> offeredStops = new HashSet<>(stopBlocks);
                    offeredStops.add(bodyBound);
                    List<Statement> structuredRegion =
                            offerRegionToEngine(current, offeredStops, bodyBound, !strictOffer);
                    if (structuredRegion != null)
                    {
                        result.addAll(structuredRegion);
                        current = stopBlocks.contains(bodyBound)
                                || (context.isProcessed(bodyBound) && !isTerminalTail(bodyBound))
                                ? null : bodyBound;
                        continue;
                    }
                }
            }

            switch (info.getType())
            {
                case IF_THEN:
                    throw retiredSchemaRecovery("if-then", current);
                case IF_THEN_ELSE:
                    throw retiredSchemaRecovery("if-else", current);
                case WHILE_LOOP:
                {
                    if (loopCutByStops(info, stopBlocks))
                    {
                        List<Statement> headerStmts = recoverSimpleBlock(current);
                        result.addAll(headerStmts);
                        context.setStatements(current, headerStmts);
                        context.markProcessed(current);
                        current = getNextSequentialBlock(current);
                        break;
                    }
                    throw retiredSchemaRecovery("while", current);
                }
                case DO_WHILE_LOOP:
                {
                    if (loopCutByStops(info, stopBlocks))
                    {
                        List<Statement> headerStmts = recoverSimpleBlock(current);
                        result.addAll(headerStmts);
                        context.setStatements(current, headerStmts);
                        context.markProcessed(current);
                        current = getNextSequentialBlock(current);
                        break;
                    }
                    throw retiredSchemaRecovery("do-while", current);
                }
                case FOR_LOOP:
                {
                    if (loopCutByStops(info, stopBlocks))
                    {
                        List<Statement> headerStmts = recoverSimpleBlock(current);
                        result.addAll(headerStmts);
                        context.setStatements(current, headerStmts);
                        context.markProcessed(current);
                        current = getNextSequentialBlock(current);
                        break;
                    }
                    throw retiredSchemaRecovery("for", current);
                }
                case GUARD_CLAUSE:
                    throw retiredSchemaRecovery("guard", current);
                default:
                {
                    List<Statement> blockStmts = recoverSimpleBlock(current);
                    result.addAll(blockStmts);
                    context.setStatements(current, blockStmts);
                    context.markProcessed(current);
                    current = getNextSequentialBlock(current);
                    break;
                }
            }
        }

        // A path that exits this sequence into a loop's exit (break) or continue-target (continue) is an
        // explicit jump. The innermost loop yields an unlabeled break/continue; an enclosing loop yields a
        // labeled one. A redundant trailing `continue` to the innermost loop is stripped by the loop recovery.
        if (current != null)
        {
            ControlFlowContext.LoopJump jump = context.classifyLoopJump(current);
            if (jump != null)
            {
                String label = jump.loopHeader != null ? context.getOrCreateLabel(jump.loopHeader) : null;
                if (jump.kind == ControlFlowContext.JumpKind.BREAK)
                {
                    result.add(label != null ? new BreakStmt(label) : new BreakStmt());
                }
                else
                {
                    result.add(label != null ? new ContinueStmt(label) : new ContinueStmt());
                }
                return result;
            }
        }

        if (current != null && stopBlocks.contains(current) && !visited.contains(current))
        {
            // Only absorb a trailing terminator (e.g. a return) that is not the shared continuation of a
            // try/catch. A return block that a try body falls into AND a catch jumps to is the continuation
            // after the try/catch, not the try's own terminator; absorbing it emits a spurious `return;` inside
            // the try. (A switch/if merge-return reached only from normal case blocks is still absorbed.)
            if (isSimpleTerminatorBlock(current)
                    && (visited.containsAll(current.getPredecessors()) || !isReachedFromCatchHandler(current)))
            {
                List<Statement> termStmts = recoverSimpleBlock(current);
                result.addAll(termStmts);
                visited.add(current);
            }
        }

        return result;
    }

    private boolean isSimpleTerminatorBlock(IRBlock block)
    {
        List<IRInstruction> instrs = block.getInstructions();
        if (instrs.isEmpty()) return false;
        int terminatorCount = 0;
        for (IRInstruction instr : instrs)
        {
            if (instr instanceof ReturnInstruction)
            {
                terminatorCount++;
            }
            else if (instr instanceof SimpleInstruction)
            {
                SimpleOp op = ((SimpleInstruction) instr).getOp();
                if (op == SimpleOp.ATHROW || op == SimpleOp.GOTO)
                {
                    terminatorCount++;
                }
            }
        }
        return terminatorCount == instrs.size();
    }

    /**
     * Finds the block to continue from after a try-catch region.
     */
    private IRBlock findBlockAfterTryCatch(ExceptionHandler handler, Set<IRBlock> visited)
    {
        IRBlock after = findBlockAfterTryCatch0(handler, visited);
        // A continuation on an active outer stop boundary (a pushed loop exit, a finally gap's end) is
        // owned by the enclosing recovery; walking it from here would pull outer code - e.g. the method's
        // trailing return - into this nested region and mark it consumed, dropping it from its real place.
        if (after != null && context.getAllStopBlocks().contains(after))
        {
            return null;
        }
        return after;
    }

    private IRBlock findBlockAfterTryCatch0(ExceptionHandler handler, Set<IRBlock> visited)
    {
        IRBlock handlerBlock = handler.getHandlerBlock();
        if (handlerBlock != null)
        {
            for (IRBlock succ : handlerBlock.getSuccessors())
            {
                if (!visited.contains(succ))
                {
                    return succ;
                }
            }
        }

        // The protected range's end offset is EXCLUSIVE, so the continuation may begin exactly at it: javac
        // separates them with a goto over the catch, but the recompiler lays the continuation out to fall
        // through directly at the end offset, and a strictly-greater scan would skip it (dropping the code
        // between this try and the next). Pick the unvisited non-handler block nearest at-or-past the end.
        int endOffset = mergedTryEndOffset(handler);
        if (endOffset >= 0)
        {
            IRMethod irMethod = context.getIrMethod();
            IRBlock best = null;
            for (IRBlock block : irMethod.getBlocks())
            {
                // An excised inlined-finally copy carries no statements and is marked processed; picking it
                // as the continuation would land on a dead shell and drop the real continuation past it (the
                // method's trailing return). Skip consumed shells so the nearest LIVE block past the end wins.
                if (block.getBytecodeOffset() >= endOffset && !visited.contains(block)
                        && block != handler.getHandlerBlock()
                        && !consumedFinallyShells.contains(block)
                        && !(!consumedFinallyShells.isEmpty() && context.isProcessed(block))
                        && (best == null || block.getBytecodeOffset() < best.getBytecodeOffset()))
                {
                    best = block;
                }
            }
            return best;
        }

        return null;
    }

    /**
     * The end offset of a try/synchronized region, widened across every exception-table entry sharing this handler
     * block.
     */
    private int mergedTryEndOffset(ExceptionHandler handler)
    {
        int end = handler.getTryEnd() != null ? handler.getTryEnd().getBytecodeOffset() : -1;
        if (handler.getHandlerBlock() == null)
        {
            return end;
        }
        for (ExceptionHandler h : context.getIrMethod().getExceptionHandlers())
        {
            if (h.getHandlerBlock() == handler.getHandlerBlock() && h.getTryEnd() != null)
            {
                end = Math.max(end, h.getTryEnd().getBytecodeOffset());
            }
        }
        return end;
    }

    /**
     * Whether {@code b}'s bytecode offset lies within some handler's protected {@code [tryStart, tryEnd)} range.
     */
    private boolean isWithinProtectedRange(IRBlock b, List<ExceptionHandler> handlers)
    {
        int off = b.getBytecodeOffset();
        for (ExceptionHandler h : handlers)
        {
            if (h.getTryStart() != null && h.getTryEnd() != null
                    && off >= h.getTryStart().getBytecodeOffset()
                    && off < h.getTryEnd().getBytecodeOffset())
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The membership closure of a finally family.
     */
    private Set<IRBlock> buildFamilyClosure(List<int[]> protectedRanges, Set<IRBlock> familyHandlers,
            int endOff)
    {
        IRMethod irMethod = context.getIrMethod();
        Set<IRBlock> closure = new HashSet<>();
        for (IRBlock b : irMethod.getBlocks())
        {
            int boff = b.getBytecodeOffset();
            for (int[] r : protectedRanges)
            {
                if (boff >= r[0] && boff < r[1])
                {
                    closure.add(b);
                    break;
                }
            }
        }
        closure.addAll(familyHandlers);
        Set<IRBlock> seed = new HashSet<>(closure);
        boolean grew = true;
        while (grew)
        {
            grew = false;
            for (ExceptionHandler eh : irMethod.getExceptionHandlers())
            {
                if (eh.getHandlerBlock() == null || closure.contains(eh.getHandlerBlock())
                        || eh.getTryStart() == null || eh.getTryEnd() == null)
                {
                    continue;
                }
                boolean rangeInside = true;
                int lo = eh.getTryStart().getBytecodeOffset();
                int hi = eh.getTryEnd().getBytecodeOffset();
                for (IRBlock b : irMethod.getBlocks())
                {
                    int boff = b.getBytecodeOffset();
                    if (boff >= lo && boff < hi && !closure.contains(b))
                    {
                        rangeInside = false;
                        break;
                    }
                }
                if (rangeInside)
                {
                    closure.add(eh.getHandlerBlock());
                    grew = true;
                }
            }
            for (IRBlock b : irMethod.getBlocks())
            {
                if (closure.contains(b) || b == irMethod.getEntryBlock()
                        || b.getPredecessors().isEmpty()
                        || b.getTerminator() instanceof ReturnInstruction)
                {
                    continue;
                }
                // Where the construct's own boundary hands over - every predecessor still in the seed -
                // a block past the window is absorbed only when it belongs to the family: dominated by a
                // handler, or opening a repeat of the handler body (the inlined finally copies a
                // relowered layout parks out there). Anything else is the next construct, and absorbing
                // it takes the join with it - the join satisfies the predecessor rule trivially, being
                // the range's fall-through, so the closure would then cascade over the rest of the
                // method and leave the exit scan nothing to settle on. Past that first hand-off the
                // copies' own interior flow cascades normally.
                if (b.getBytecodeOffset() >= endOff && seed.containsAll(b.getPredecessors())
                        && !dominatedByAny(familyHandlers, b, context.getDominatorTree())
                        && !repeatsFamilyHandlerBody(b, familyHandlers))
                {
                    continue;
                }
                if (closure.containsAll(b.getPredecessors()))
                {
                    closure.add(b);
                    grew = true;
                }
            }
        }
        return closure;
    }

    /**
     * Removes from {@code closure} every return block outside {@code protectedRanges}.
     */
    private void carveUnprotectedReturns(Set<IRBlock> closure, List<int[]> protectedRanges)
    {
        for (IRBlock b : new ArrayList<>(closure))
        {
            if (!(b.getTerminator() instanceof ReturnInstruction))
            {
                continue;
            }
            int boff = b.getBytecodeOffset();
            boolean covered = false;
            for (int[] r : protectedRanges)
            {
                if (boff >= r[0] && boff < r[1])
                {
                    covered = true;
                    break;
                }
            }
            if (!covered)
            {
                closure.remove(b);
            }
        }
    }

    /**
     * Whether any block in {@code roots} dominates {@code block}.
     */
    private boolean dominatedByAny(Set<IRBlock> roots, IRBlock block, DominatorTree dt)
    {
        for (IRBlock root : roots)
        {
            if (dt.dominates(root, block))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code block} opens with a repeat of the body of any handler in {@code familyHandlers} - the form an
     * inlined finally copy takes on a path outside the family's protected ranges.
     */
    private boolean repeatsFamilyHandlerBody(IRBlock block, Set<IRBlock> familyHandlers)
    {
        for (IRBlock handler : familyHandlers)
        {
            List<IRInstruction> body = handlerBodyTemplate(handler);
            if (body.isEmpty() || block.getInstructions().size() < body.size())
            {
                continue;
            }
            List<IRInstruction> instructions = block.getInstructions();
            boolean match = true;
            for (int i = 0; i < body.size(); i++)
            {
                if (!sameFinallyInstr(body.get(i), instructions.get(i)))
                {
                    match = false;
                    break;
                }
            }
            if (match)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The instructions of {@code handler} that an inlined copy of it repeats.
     */
    private List<IRInstruction> handlerBodyTemplate(IRBlock handler)
    {
        List<IRInstruction> body = new ArrayList<>();
        for (IRInstruction i : handler.getInstructions())
        {
            if (i instanceof PhiInstruction)
            {
                continue;
            }
            if (body.isEmpty() && bindsCaughtException(i))
            {
                continue;
            }
            body.add(i);
        }
        return body;
    }

    /**
     * Whether {@code instruction} is part of a handler's leading bind of the caught exception rather than
     * of its body - the exception value itself, or the store that parks it in a local.
     */
    private boolean bindsCaughtException(IRInstruction instruction)
    {
        SSAValue result = instruction.getResult();
        if (result != null && result.getName() != null && result.getName().startsWith("exc_"))
        {
            return true;
        }
        if (!(instruction instanceof StoreLocalInstruction))
        {
            return false;
        }
        for (Value operand : instruction.getOperands())
        {
            if (operand instanceof SSAValue && ((SSAValue) operand).getName() != null
                    && ((SSAValue) operand).getName().startsWith("exc_"))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isTerminatingTryCatch(TryCatchStmt tryCatch)
    {
        if (!isTerminatingBranch(tryCatch.getTryBlock()))
        {
            return false;
        }
        for (CatchClause clause : tryCatch.getCatches())
        {
            if (!isTerminatingBranch(clause.body()))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a try/catch or synchronized statement recovered for a region leaves no normal fall-through.
     */
    private boolean isTerminatingRecoveredTry(Statement recovered)
    {
        if (recovered instanceof TryCatchStmt)
        {
            return isTerminatingTryCatch((TryCatchStmt) recovered);
        }
        if (recovered instanceof SynchronizedStmt)
        {
            return isTerminatingBranch(((SynchronizedStmt) recovered).getBody());
        }
        return false;
    }

    private boolean isTerminatingBlock(BlockStmt block)
    {
        if (block == null || block.getStatements().isEmpty())
        {
            return false;
        }
        Statement lastStmt = block.getStatements().get(block.getStatements().size() - 1);
        return isTerminatingStatement(lastStmt);
    }

    private boolean isTerminatingStatement(Statement stmt)
    {
        if (stmt instanceof ReturnStmt || stmt instanceof ThrowStmt
                || stmt instanceof BreakStmt || stmt instanceof ContinueStmt)
        {
            return true;
        }
        if (stmt instanceof IfStmt)
        {
            IfStmt ifStmt = (IfStmt) stmt;
            if (ifStmt.getElseBranch() == null)
            {
                return false;
            }
            return isTerminatingBranch(ifStmt.getThenBranch())
                && isTerminatingBranch(ifStmt.getElseBranch());
        }
        if (stmt instanceof TryCatchStmt)
        {
            return isTerminatingTryCatch((TryCatchStmt) stmt);
        }
        if (stmt instanceof BlockStmt)
        {
            return isTerminatingBlock((BlockStmt) stmt);
        }
        if (stmt instanceof SwitchStmt)
        {
            // A switch terminates when every arm does (returns/throws; a break falls out and does
            // NOT terminate) and a default arm makes the dispatch total.
            SwitchStmt sw = (SwitchStmt) stmt;
            boolean hasDefault = false;
            for (SwitchCase c : sw.getCases())
            {
                if (c.isDefault())
                {
                    hasDefault = true;
                }
                List<Statement> body = c.statements();
                if (body.isEmpty())
                {
                    return false;
                }
                Statement last = body.get(body.size() - 1);
                if (last instanceof BreakStmt || !isTerminatingStatement(last))
                {
                    return false;
                }
            }
            return hasDefault;
        }
        return false;
    }

    private boolean isTerminatingBranch(Statement branch)
    {
        if (branch instanceof BlockStmt)
        {
            return isTerminatingBlock((BlockStmt) branch);
        }
        return isTerminatingStatement(branch);
    }


    /**
     * Map from local slot name to unified type (computed from all assignments)
     */
    private final Map<String, SourceType> localSlotUnifiedTypes = new HashMap<>();

    /**
     * Maps slot index to (typeCategory -&gt; variableName) for consistent naming of reused slots
     */
    private final Map<Integer, Map<String, String>> slotTypeCategoryToName = new HashMap<>();

    /**
     * Emits declarations for phi variables at method scope.
     */
    private final Set<Integer> phiSlots = new HashSet<>();

    private void emitPhiDeclarations(IRMethod method, List<Statement> statements)
    {
        Set<SSAValue> phiValues = new LinkedHashSet<>();
        Set<String> declaredNames = new HashSet<>();

        Set<IRBlock> handlerBlocks = collectExceptionHandlerBlocks(method);

        Map<String, List<SourceType>> localSlotTypes = new HashMap<>();
        // The authoritative narrow-primitive declared type (char/byte/short/boolean) from the
        // LocalVariableTable, per name - used to override int-widening below. A CONFLICT marker means two
        // stores of one name disagree, so we don't override.
        Map<String, String> localSlotLvtNarrow = new HashMap<>();

        slotTypeCategoryToName.clear();
        phiSlots.clear();

        // PRE-PASS: Identify slots that have phis (values merging from multiple branches)
        // For these slots, we use coarse type categories so all branches share the same variable name
        for (IRBlock block : method.getBlocks())
        {
            if (handlerBlocks.contains(block))
            {
                continue;
            }
            for (PhiInstruction phi : block.getPhiInstructions())
            {
                int localIndex = getLocalIndexFromPhi(phi);
                if (localIndex >= 0)
                {
                    phiSlots.add(localIndex);
                }
            }
        }

        // PASS 1: Process ALL StoreLocalInstruction to establish slot names
        for (IRBlock block : method.getBlocks())
        {
            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof StoreLocalInstruction)
                {
                    StoreLocalInstruction storeLocal = (StoreLocalInstruction) instr;
                    int localIndex = storeLocal.getLocalIndex();

                    Value storedValue = storeLocal.getValue();
                    SourceType storedType = typeRecoverer.recoverType(storedValue);

                    String localName = partitionName(storeLocal);
                    if (localName == null)
                    {
                        localName = getNameForLocalSlotWithType(localIndex, storedType);
                    }
                    context.getExpressionContext().setLocalSlotName(localIndex, localName);

                    if (storedType != null && !storedType.isVoid() && !isNullValue(storedValue))
                    {
                        localSlotTypes.computeIfAbsent(localName, k -> new ArrayList<>()).add(storedType);
                        String narrow = narrowLvtDescriptor(localIndex, storeLocal.getBytecodeOffset());
                        if (narrow != null)
                        {
                            String prev = localSlotLvtNarrow.putIfAbsent(localName, narrow);
                            if (prev != null && !prev.equals(narrow))
                            {
                                localSlotLvtNarrow.put(localName, "CONFLICT");
                            }
                        }
                    }

                    if (storedValue instanceof SSAValue)
                    {
                        SSAValue sourceValue = (SSAValue) storedValue;
                        if (isUsedByArrayStore(sourceValue))
                        {
                            if (context.getExpressionContext().isPendingNew(sourceValue))
                            {
                                String className = context.getExpressionContext().consumePendingNew(sourceValue);
                                context.getExpressionContext().registerPendingNewLocalSlot(localIndex, className);
                            }
                            continue;
                        }
                        String existingName = context.getExpressionContext().getVariableName(sourceValue);
                        int existingSlot = context.getExpressionContext().getSSAValueSlot(sourceValue);
                        // Overwrite only an absent or synthetic name (a value-id like "v3"/"v3_0" or a
                        // one-letter slot name like "i5") with this store's slot name. A real name -
                        // including one that merely starts with 'v', e.g. an LVT "viewPorts" - is kept,
                        // so a copy `slot2 = viewPorts` is not clobbered into a self-reference
                        // `local2 = local2` (which reads before assignment). A store to a DIFFERENT slot than
                        // the value already belongs to is a cross-slot copy (`x1 = min(); ...; newWidth = x1`),
                        // not a redefinition: keep the value's home-slot name so the copy references it rather
                        // than renaming the value to the copy target (which turns the copy into a dropped
                        // self-store and strands its guarding branch).
                        boolean crossSlotCopy = existingName != null && existingSlot >= 0 && existingSlot != localIndex;
                        boolean shouldOverwrite = !crossSlotCopy
                                && (existingName == null
                                || isSyntheticValueName(existingName)
                                || existingName.matches("[a-z]\\d+"));
                        if (shouldOverwrite)
                        {
                            context.getExpressionContext().setVariableName(sourceValue, localName);
                            context.getExpressionContext().markMaterialized(sourceValue);
                            context.getExpressionContext().setSSAValueSlot(sourceValue, localIndex);
                        }

                        if (context.getExpressionContext().isPendingNew(sourceValue))
                        {
                            String className = context.getExpressionContext().consumePendingNew(sourceValue);
                            context.getExpressionContext().registerPendingNewLocalSlot(localIndex, className);
                        }
                    }
                }
            }
        }

        // Compute unified types for each variable name AFTER PASS 1 but BEFORE PASS 2.
        // PASS 2 needs this data to correctly determine type compatibility for loads.
        localSlotUnifiedTypes.clear();
        for (Map.Entry<String, List<SourceType>> entry : localSlotTypes.entrySet())
        {
            String slotName = entry.getKey();
            List<SourceType> types = entry.getValue();
            if (!types.isEmpty())
            {
                // A name whose stores mix primitive and reference types cannot be one Java variable: it groups
                // distinct variables that legally share a name across disjoint scopes (e.g. a boxed-value transient
                // and an int loop counter both named `i`). Unifying the whole set would widen to Object and mistype
                // the primitive uses; unify from the primitive subset so the coherent primitive variable keeps its
                // type. The reference-typed stores are a separate scope resolved by their own value expressions.
                List<SourceType> primitiveTypes = new ArrayList<>();
                boolean hasReference = false;
                for (SourceType t : types)
                {
                    if (t.isPrimitive())
                    {
                        primitiveTypes.add(t);
                    }
                    else
                    {
                        hasReference = true;
                    }
                }
                List<SourceType> unifyFrom = (hasReference && !primitiveTypes.isEmpty()) ? primitiveTypes : types;
                SourceType unifiedType = typeRecoverer.computeCommonType(unifyFrom);
                // Prefer the LocalVariableTable's declared narrow-primitive type over int-widening: a
                // char/byte/short/boolean local stored through int-shaped bytecode (e.g. a synthetic `= 0`
                // init) otherwise widens to `int`, losing the declared type and drifting from javac.
                String narrow = localSlotLvtNarrow.get(slotName);
                if (narrow != null && !"CONFLICT".equals(narrow) && unifiedType == PrimitiveSourceType.INT)
                {
                    unifiedType = typeRecoverer.recoverType(narrow);
                }
                localSlotUnifiedTypes.put(slotName, unifiedType);
            }
        }

        // PASS 2: Process ALL LoadLocalInstruction now that slot names are established
        // Always use the category-based name lookup to ensure loads match the correct store
        for (IRBlock block : method.getBlocks())
        {
            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof LoadLocalInstruction)
                {
                    LoadLocalInstruction loadLocal = (LoadLocalInstruction) instr;
                    if (loadLocal.getResult() != null)
                    {
                        int localIndex = loadLocal.getLocalIndex();
                        SourceType valueType = typeRecoverer.recoverType(loadLocal.getResult());
                        String localName = partitionName(loadLocal);
                        if (localName == null)
                        {
                            localName = getNameForLocalSlotWithType(localIndex, valueType);
                        }
                        context.getExpressionContext().setVariableName(loadLocal.getResult(), localName);
                        context.getExpressionContext().markMaterialized(loadLocal.getResult());

                        String pendingNewClass = context.getExpressionContext().consumePendingNewLocalSlot(localIndex);
                        if (pendingNewClass != null)
                        {
                            context.getExpressionContext().registerPendingNew(loadLocal.getResult(), pendingNewClass);
                        }
                    }
                }
            }
        }

        List<PhiInstruction> phiInstructions = new ArrayList<>();
        for (IRBlock block : method.getBlocks())
        {
            if (handlerBlocks.contains(block))
            {
                continue;
            }

            for (PhiInstruction phi : block.getPhiInstructions())
            {
                if (phi.getResult() != null)
                {
                    phiValues.add(phi.getResult());
                    phiInstructions.add(phi);
                }
            }
        }

        List<SSAValue> sortedValues = sortByDependencies(phiValues);

        Map<SSAValue, PhiInstruction> valueToPhiMap = new HashMap<>();
        for (PhiInstruction phi : phiInstructions)
        {
            valueToPhiMap.put(phi.getResult(), phi);
        }

        for (SSAValue value : sortedValues)
        {
            PhiInstruction phi = valueToPhiMap.get(value);
            emitPhiDeclaration(phi, statements, declaredNames, handlerBlocks);
        }

        // Additional pass: Declare handler block phis that are used through store chains
        // These phis are skipped in the main pass but may still need declarations
        // when their values are assigned via NEW instructions
        for (IRBlock block : method.getBlocks())
        {
            if (!handlerBlocks.contains(block))
            {
                continue;
            }
            for (PhiInstruction phi : block.getPhiInstructions())
            {
                if (phi.getResult() != null && !phiValues.contains(phi.getResult()))
                {
                    String phiVarName = context.getExpressionContext().getVariableName(phi.getResult());
                    if (phiVarName != null && !phiVarName.equals("this") && !isParameterOrThisRef(phi.getResult()))
                    {
                        if (!declaredNames.contains(phiVarName) && !context.getExpressionContext().isDeclared(phiVarName))
                        {
                            SourceType type = computePhiUnifiedType(phi);
                            declaredNames.add(phiVarName);
                            context.getExpressionContext().markDeclared(phiVarName);
                            context.getExpressionContext().markMaterialized(phi.getResult());
                            Expression initValue = getDefaultValue(type);
                            statements.add(new VarDeclStmt(type, phiVarName, initValue));
                        }
                    }
                }
            }
        }
    }

    /**
     * A synthetic SSA value name ("v" followed by a digit, e.g. {@code v3} or {@code v3_0}) - not a real local name.
     */
    private static boolean isSyntheticValueName(String name)
    {
        return name.length() > 1 && name.charAt(0) == 'v' && Character.isDigit(name.charAt(1));
    }

    /**
     * The LocalVariableTable declared type at a store, when it is a narrow primitive (char/byte/short/boolean) -
     * the sub-int types that int-shaped bytecode would otherwise lose.
     */
    private String narrowLvtDescriptor(int slot, int offset)
    {
        RecoveryContext ctx = context.getExpressionContext();
        String desc = ctx.debugDescriptorAtStore(slot, offset);
        if (desc == null)
        {
            desc = ctx.debugDescriptorAt(slot, offset);
        }
        return desc != null && desc.length() == 1 && "ZBCS".indexOf(desc.charAt(0)) >= 0 ? desc : null;
    }

    /**
     * Looks up the type unified across every value stored into a local slot.
     *
     * @param slotName the recovered variable name of the slot
     * @return the unified type, or null if none was computed for the slot
     */
    public SourceType getLocalSlotUnifiedType(String slotName)
    {
        return localSlotUnifiedTypes.get(slotName);
    }

    /**
     * Emits a phi variable declaration with default value.
     */
    private void emitPhiDeclaration(PhiInstruction phi, List<Statement> statements, Set<String> declaredNames, Set<IRBlock> handlerBlocks)
    {
        if (phi == null) return;
        SSAValue result = phi.getResult();
        if (result == null) return;

        // A dead phi that merges a caught exception (an operand defined in a catch handler) with the
        // try-path's undefined slot value is the catch variable's reused slot bridging the shared finally
        // join. The catch variable is declared by its own catch clause, so declaring this phi too emits a
        // spurious top-level `Exception e = null`. (Restricted to dead + handler-sourced so the load-bearing
        // pattern-switch dead phis, which have no handler operand, still declare.)
        if (isDeadCatchVarPhi(phi, handlerBlocks))
        {
            return;
        }

        // A phi that merges a primitive with a reference is a type-pun across a reused JVM
        // slot; it is only verifier-legal because its result is dead. Declaring it would
        // unify the operands to Object and mis-type the slot (e.g. Object local5 = null while
        // the slot is really an int). Skip it so the slot is declared by its actual stores.
        // Coherent dead phis (all-reference or all-primitive) still declare normally, since
        // they carry the slot's correct unified type.
        if (isTypePunDeadPhi(phi))
        {
            return;
        }

        if (selfStorePhis.contains(phi))
        {
            return;
        }

        if (isForLoopInductionPhi(phi))
        {
            return;
        }

        SourceType phiType = computePhiUnifiedType(phi);

        String name = partitionName(phi);
        String nameFromMethodRecoverer = context.getExpressionContext().getVariableName(result);
        if (name == null)
        {
            int localIndex = getLocalIndexFromPhi(phi);
            // A slot with no recovered name already carries a generated one, and that name IS the name for
            // this phi - take it as it stands. A slot that does have a recovered name goes through the typed
            // lookup instead, which is what disambiguates a slot reused at more than one type. The two were
            // previously told apart by whether the name began with `local`, so a class compiled with debug
            // info silently took a different path than the same class compiled without it.
            boolean slotIsNamed = localIndex >= 0
                    && context.getExpressionContext().debugNameForSlot(localIndex) != null;
            if (nameFromMethodRecoverer != null && !slotIsNamed)
            {
                name = nameFromMethodRecoverer;
            }
            else
            {
                if (localIndex >= 0)
                {
                    name = getNameForLocalSlotWithType(localIndex, phiType);
                }
                if (name == null)
                {
                    name = nameFromMethodRecoverer;
                }
            }
        }
        if (name == null)
        {
            name = "v" + result.getId();
        }

        if ("this".equals(name) || isParameterOrThisRef(result))
        {
            return;
        }

        // The declared type must match the variable this phi shares a name with: prefer the
        // unified type of the stores carrying this name (the partition component) so a phi the
        // SSA bridges across a heterogeneous merge does not mistype an unrelated slot variable.
        SourceType type = localSlotUnifiedTypes.getOrDefault(name, phiType);

        boolean upgradedToBoolean = false;
        if (type == PrimitiveSourceType.INT && phiReceivesBooleanConstantsOnly(phi))
        {
            type = PrimitiveSourceType.BOOLEAN;
            upgradedToBoolean = true;
        }

        if (declaredNames.contains(name) || context.getExpressionContext().isDeclared(name))
        {
            return;
        }

        declaredNames.add(name);
        context.getExpressionContext().markDeclaredWithType(name, type);
        context.getExpressionContext().markMaterialized(result);
        context.getExpressionContext().setVariableName(result, name);

        if (upgradedToBoolean)
        {
            localSlotUnifiedTypes.put(name, PrimitiveSourceType.BOOLEAN);
        }

        Expression initValue = getDefaultValue(type);
        boolean entryApplied = false;
        // A loop-carried phi's declaration should be initialized with its pre-loop (entry) value, not a
        // default: `int s = first` for `s = phi(first, s + r)`. Without this the entry value is lost
        // (e.g. an accumulator seeded from a parameter starts at 0). Only simple entry values (constant,
        // parameter, or a local load) are inlined, to avoid duplicating a side-effecting expression.
        Value entryInput = findLoopEntryInput(phi);
        if (isSafeEntryInit(entryInput) && entryDominatesPhi(entryInput, phi))
        {
            Expression entryExpr = exprRecoverer.recoverOperand(entryInput, type);
            if (isSelfReference(entryExpr, name))
            {
                // The entry value is materialized under this phi's own slot name, so recoverOperand
                // returns a reference to the variable being declared. Recover its underlying constant
                // directly so the entry value is not lost to a `T v = v` self-initializer (which a later
                // pass then resolves to the loop-body store, e.g. `boolean captured = true`).
                entryExpr = recoverEntryConstant(entryInput, type);
            }
            if (entryExpr != null)
            {
                initValue = entryExpr;
                entryApplied = true;
            }
        }
        // A merge phi whose dominating (pre-branch) operand is a side-effecting value that is ALSO consumed
        // elsewhere - `boolean result = base(s); if (result) {...} return result;`, where base(s) is both the
        // phi's entry operand and the branch condition - cannot inline that value as the initializer (it would
        // evaluate base() twice). Instead bind the value to this phi's variable, materialize it, and pin it, so
        // its single definition emits `result = base(s)` in place and the condition and phi both read `result`.
        // Without this the value is inlined into the condition, its store is lost, and the variable is undeclared.
        // Binding only makes sense for a SLOT-BACKED phi, whose name IS the source variable. A stack
        // phi (a ternary's value join) carries a synthetic name: stealing its dominating operand into
        // that name severs the operand's real store/load web - the operand's own variable is left
        // assigned-but-unread while every use reads the synthetic, which only ever holds this default.
        if (!entryApplied && (partitionName(phi) != null || getLocalIndexFromPhi(phi) >= 0))
        {
            Value dominating = findDominatingOperand(phi);
            if (dominating instanceof SSAValue && !isSafeEntryInit(dominating)
                    && entryDominatesPhi(dominating, phi)
                    && !context.getExpressionContext().isMaterialized((SSAValue) dominating))
            {
                SSAValue dominatingValue = (SSAValue) dominating;
                context.getExpressionContext().setVariableName(dominatingValue, name);
                context.getExpressionContext().markMaterialized(dominatingValue);
                context.getExpressionContext().pinToVariable(dominatingValue);
            }
        }
        VarDeclStmt phiDecl = new VarDeclStmt(type, name, initValue);
        if (partitionName(phi) == null && getLocalIndexFromPhi(phi) < 0)
        {
            // A slot-less stack phi has no source variable behind it: this declaration is the
            // recovery's own carrier (e.g. a value-yielding switch's merge), not source shape.
            phiDecl.markSynthetic();
        }
        statements.add(phiDecl);
    }

    /**
     * The single distinct phi operand whose definition dominates the phi's block - the value the variable holds on
     * entry to the merge, before any branch reassigns it.
     */
    private Value findDominatingOperand(PhiInstruction phi)
    {
        IRBlock phiBlock = phi.getBlock();
        if (phiBlock == null)
        {
            return null;
        }
        Set<Value> dominating = new HashSet<>();
        for (Value in : phi.getIncomingValues().values())
        {
            if (!(in instanceof SSAValue))
            {
                continue;
            }
            IRInstruction def = ((SSAValue) in).getDefinition();
            IRBlock defBlock = def != null ? def.getBlock() : null;
            if (defBlock != null && analyzer.getDominatorTree().dominates(defBlock, phiBlock))
            {
                dominating.add(in);
            }
        }
        return dominating.size() == 1 ? dominating.iterator().next() : null;
    }

    /**
     * Whether the entry input genuinely enters the loop from outside: its definition dominates the phi's block.
     */
    private boolean entryDominatesPhi(Value entryInput, PhiInstruction phi)
    {
        IRBlock phiBlock = phi.getBlock();
        if (phiBlock == null)
        {
            return false;
        }
        if (entryInput instanceof Constant)
        {
            return true;
        }
        if (entryInput instanceof SSAValue)
        {
            IRInstruction def = ((SSAValue) entryInput).getDefinition();
            if (def == null)
            {
                // Definition-less means a parameter - always in scope - or an UNDEFINED slot read: the
                // pre-loop path never wrote the slot, and rendering that input borrows whatever name a
                // DISJOINT component left there (`Spatial child = t;` with t from an exclusive branch).
                // Only a parameter is a real entry value.
                return isParameterOrThisRef((SSAValue) entryInput);
            }
            IRBlock defBlock = def.getBlock();
            return defBlock != null && analyzer.getDominatorTree().dominates(defBlock, phiBlock);
        }
        return false;
    }

    /**
     * Whether {@code expr} is a reference to the variable {@code name} - a self-initializer to reject.
     */
    private boolean isSelfReference(Expression expr, String name)
    {
        return expr instanceof VarRefExpr && name != null
                && name.equals(((VarRefExpr) expr).getName());
    }

    /**
     * Recovers a constant-backed entry value as a literal, bypassing slot materialization.
     */
    private Expression recoverEntryConstant(Value entryInput, SourceType type)
    {
        Constant c = null;
        if (entryInput instanceof Constant)
        {
            c = (Constant) entryInput;
        }
        else if (entryInput instanceof SSAValue)
        {
            IRInstruction def = ((SSAValue) entryInput).getDefinition();
            if (def instanceof ConstantInstruction)
            {
                c = ((ConstantInstruction) def).getConstant();
            }
        }
        return c != null ? exprRecoverer.recoverConstant(c, type) : null;
    }

    /**
     * For a loop-carried phi (exactly one input recurses through the phi result), returns the entry input; else null.
     */
    private Value findLoopEntryInput(PhiInstruction phi)
    {
        SSAValue result = phi.getResult();
        if (result == null)
        {
            return null;
        }
        Value entry = null;
        int entryCount = 0;
        int recursiveCount = 0;
        for (Value in : phi.getIncomingValues().values())
        {
            if (valueReaches(in, result, new HashSet<>()))
            {
                recursiveCount++;
            }
            else
            {
                entry = in;
                entryCount++;
            }
        }
        return (entryCount == 1 && recursiveCount >= 1) ? entry : null;
    }

    private boolean valueReaches(Value v, SSAValue target, Set<Value> seen)
    {
        if (v == target)
        {
            return true;
        }
        if (!(v instanceof SSAValue) || !seen.add(v))
        {
            return false;
        }
        IRInstruction def = ((SSAValue) v).getDefinition();
        if (def == null)
        {
            return false;
        }
        for (Value op : def.getOperands())
        {
            if (valueReaches(op, target, seen))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isSafeEntryInit(Value v)
    {
        if (v instanceof Constant)
        {
            return true;
        }
        if (v instanceof SSAValue)
        {
            IRInstruction def = ((SSAValue) v).getDefinition();
            return def == null
                    || def instanceof ConstantInstruction
                    || def instanceof LoadLocalInstruction;
        }
        return false;
    }

    /**
     * Checks if a PHI instruction corresponds to a for-loop induction variable.
     */
    private boolean isForLoopInductionPhi(PhiInstruction phi)
    {
        SSAValue result = phi.getResult();
        if (result != null && context.isForLoopInductionPhi(result))
        {
            return true;
        }
        IRBlock phiBlock = phi.getBlock();
        if (phiBlock != null && context.isForLoopHeader(phiBlock))
        {
            int localIndex = getLocalIndexFromPhi(phi);
            return localIndex >= 0 && context.isForLoopInductionLocal(localIndex);
        }
        return false;
    }

    private int getLocalIndexFromPhi(PhiInstruction phi)
    {
        SSAValue result = phi.getResult();
        if (result == null) return -1;

        // The slot is read off the phi's operands below. There used to be a fast path that parsed it out of a
        // generated `localN` name, which fired only for a class WITHOUT debug info - so the two took different
        // routes to the same answer, and only one of them was exercised by anything.

        for (Value incoming : phi.getOperands())
        {
            if (incoming instanceof SSAValue)
            {
                SSAValue ssaVal = (SSAValue) incoming;
                IRInstruction def = ssaVal.getDefinition();
                if (def instanceof LoadLocalInstruction)
                {
                    return ((LoadLocalInstruction) def).getLocalIndex();
                }
                if (def instanceof StoreLocalInstruction)
                {
                    return ((StoreLocalInstruction) def).getLocalIndex();
                }
            }
        }

        for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet())
        {
            IRBlock predBlock = entry.getKey();
            Value incomingValue = entry.getValue();
            for (IRInstruction instr : predBlock.getInstructions())
            {
                if (instr instanceof StoreLocalInstruction)
                {
                    StoreLocalInstruction store = (StoreLocalInstruction) instr;
                    if (store.getValue() == incomingValue)
                    {
                        return store.getLocalIndex();
                    }
                }
            }
        }

        return -1;
    }

    /**
     * True when {@code value} is stored to the slot the {@code phi} represents.
     */
    private boolean isStoredToPhiSlot(SSAValue value, PhiInstruction phi)
    {
        int slot = getLocalIndexFromPhi(phi);
        if (slot < 0 || value == null)
        {
            return false;
        }
        for (IRInstruction use : value.getUses())
        {
            if (use instanceof StoreLocalInstruction && ((StoreLocalInstruction) use).getLocalIndex() == slot)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a freshly-constructed {@code value} genuinely belongs to the variable {@code phiVarName} that a phi
     * merges it into.
     */
    private boolean valueBelongsToPhiVariable(SSAValue value, String phiVarName)
    {
        if (value == null || phiVarName == null)
        {
            return true;
        }
        for (IRInstruction use : value.getUses())
        {
            if (use instanceof StoreLocalInstruction)
            {
                String storeName = partitionName(use);
                if (storeName != null)
                {
                    return storeName.equals(phiVarName);
                }
            }
        }
        return true;
    }

    /**
     * Whether a StoreLocal consumes {@code value} under the same recovered name as {@code varName}.
     */
    private boolean isStoredToVariableNamed(SSAValue value, String varName)
    {
        if (value == null || varName == null)
        {
            return false;
        }
        for (IRInstruction use : value.getUses())
        {
            if (use instanceof StoreLocalInstruction && varName.equals(partitionName(use)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The local slot a value belongs to - its parameter slot, the slot it was stored to, or the slot of its
     * defining local load/store/phi - or -1 when it is not a local-slot value.
     */
    private int slotOfValue(SSAValue value)
    {
        if (value == null)
        {
            return -1;
        }
        RecoveryContext ctx = context.getExpressionContext();
        int paramSlot = ctx.parameterSlot(value);
        if (paramSlot >= 0)
        {
            return paramSlot;
        }
        int stored = ctx.getSSAValueSlot(value);
        if (stored >= 0)
        {
            return stored;
        }
        IRInstruction def = value.getDefinition();
        if (def instanceof LoadLocalInstruction)
        {
            return ((LoadLocalInstruction) def).getLocalIndex();
        }
        if (def instanceof StoreLocalInstruction)
        {
            return ((StoreLocalInstruction) def).getLocalIndex();
        }
        if (def instanceof PhiInstruction)
        {
            return getLocalIndexFromPhi((PhiInstruction) def);
        }
        return -1;
    }

    /**
     * True when {@code value} refers to the receiver or a parameter (decided by its local slot).
     */
    private boolean isParameterOrThisRef(SSAValue value)
    {
        return context.getExpressionContext().isParameterOrThisSlot(slotOfValue(value));
    }

    /**
     * Whether {@code block} is reached from a catch handler - i.e. one of its predecessors lies in some exception
     * handler's region.
     */
    private boolean isReachedFromCatchHandler(IRBlock block)
    {
        Set<IRBlock> handlerRegion = new HashSet<>();
        for (ExceptionHandler h : context.getIrMethod().getExceptionHandlers())
        {
            if (h.getHandlerBlock() != null && handlerRegion.add(h.getHandlerBlock()))
            {
                collectReachableBlocks(h.getHandlerBlock(), handlerRegion);
            }
        }
        for (IRBlock pred : block.getPredecessors())
        {
            if (handlerRegion.contains(pred))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isDeadCatchVarPhi(PhiInstruction phi, Set<IRBlock> handlerBlocks)
    {
        SSAValue result = phi.getResult();
        if (result == null || !result.getUses().isEmpty())
        {
            return false;
        }
        SourceType type = typeRecoverer.recoverType(result);
        if (type == null || type.isPrimitive() || type.isVoid())
        {
            return false;
        }
        // A reference-typed dead phi that either has no real incoming value (the catch variable's reused slot
        // is undefined on the try path and its catch def was elided) or merges a value defined in a catch
        // handler is the catch variable bridging a shared finally join - already declared by its catch clause.
        boolean hasOperand = false;
        for (Value op : phi.getOperands())
        {
            if (op instanceof SSAValue)
            {
                hasOperand = true;
                IRInstruction def = ((SSAValue) op).getDefinition();
                if (def != null && def.getBlock() != null && handlerBlocks != null
                        && handlerBlocks.contains(def.getBlock()))
                {
                    return true;
                }
            }
        }
        return !hasOperand;
    }

    private boolean isTypePunDeadPhi(PhiInstruction phi)
    {
        SSAValue result = phi.getResult();
        if (result == null || !result.getUses().isEmpty())
        {
            return false;
        }
        boolean hasPrimitive = false;
        boolean hasReference = false;
        for (Value value : phi.getOperands())
        {
            SourceType type = typeRecoverer.recoverType(value);
            if (type == null || type.isVoid())
            {
                continue;
            }
            if (type.isPrimitive())
            {
                hasPrimitive = true;
            }
            else
            {
                hasReference = true;
            }
        }
        return hasPrimitive && hasReference;
    }

    private SourceType computePhiUnifiedType(PhiInstruction phi)
    {
        SSAValue result = phi.getResult();

        List<SourceType> incomingTypes = new ArrayList<>();
        for (Value value : phi.getOperands())
        {
            if (isNullValue(value))
            {
                continue;
            }
            SourceType valueType = typeRecoverer.recoverType(value);
            if (valueType != null && !valueType.isVoid())
            {
                incomingTypes.add(valueType);
            }
        }

        if (!incomingTypes.isEmpty())
        {
            return typeRecoverer.computeCommonType(incomingTypes);
        }

        String localName = context.getExpressionContext().getVariableName(result);
        if (localName != null && localSlotUnifiedTypes.containsKey(localName))
        {
            return localSlotUnifiedTypes.get(localName);
        }

        return typeRecoverer.recoverType(result);
    }

    /**
     * Collects exception handler ENTRY blocks only.
     */
    private Set<IRBlock> collectExceptionHandlerBlocks(IRMethod method)
    {
        Set<IRBlock> handlerBlocks = new HashSet<>();
        List<ExceptionHandler> handlers = method.getExceptionHandlers();
        if (handlers == null || handlers.isEmpty())
        {
            return handlerBlocks;
        }

        for (ExceptionHandler handler : handlers)
        {
            IRBlock handlerBlock = handler.getHandlerBlock();
            if (handlerBlock != null)
            {
                handlerBlocks.add(handlerBlock);
            }
        }
        return handlerBlocks;
    }

    /**
     * Sorts SSA values so that values are declared after their dependencies.
     */
    private List<SSAValue> sortByDependencies(Set<SSAValue> values)
    {
        List<SSAValue> result = new ArrayList<>();
        Set<SSAValue> visited = new HashSet<>();
        Set<SSAValue> inProgress = new HashSet<>();

        for (SSAValue value : values)
        {
            visitForSort(value, values, visited, inProgress, result);
        }

        return result;
    }

    private void visitForSort(SSAValue value, Set<SSAValue> allValues, Set<SSAValue> visited, Set<SSAValue> inProgress, List<SSAValue> result)
    {
        if (visited.contains(value)) return;
        if (inProgress.contains(value)) return;

        inProgress.add(value);

        IRInstruction def = value.getDefinition();
        if (def != null)
        {
            for (Value operand : def.getOperands())
            {
                if (operand instanceof SSAValue)
                {
                    SSAValue ssaDep = (SSAValue) operand;
                    if (allValues.contains(ssaDep))
                    {
                        visitForSort(ssaDep, allValues, visited, inProgress, result);
                    }
                }
            }
        }

        inProgress.remove(value);
        visited.add(value);
        result.add(value);
    }

    /**
     * Checks if an SSA value is an intermediate value that should be inlined.
     */
    private boolean isIntermediateValue(SSAValue value)
    {
        if (value == null) return false;

        IRInstruction def = value.getDefinition();
        if (def instanceof InvokeInstruction && shouldStoreMethodResult((InvokeInstruction) def, value))
        {
            return false;
        }

        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return true;

        // The value is intermediate (inlinable at its use) unless some use needs it to have a home
        // variable: a branch/phi (recovered as a named condition/merge), a store to a local, or a
        // store to a field/array. Every other use kind (invoke arg, return, arithmetic, type check,
        // field/array LOAD) consumes the value inline and does not force materialization.
        for (IRInstruction use : uses)
        {
            if (use instanceof BranchInstruction
                    || use instanceof StoreLocalInstruction
                    || use instanceof PhiInstruction)
            {
                return false;
            }
            if (use instanceof FieldAccessInstruction && ((FieldAccessInstruction) use).isStore())
            {
                return false;
            }
            if (use instanceof ArrayAccessInstruction && ((ArrayAccessInstruction) use).isStore())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the object a constructor call built is used by nothing else, so the call stands alone as a
     * statement.
     */
    private boolean isDiscardedAllocation(SSAValue newValue, InvokeInstruction init)
    {
        if (newValue == null)
        {
            return false;
        }
        for (IRInstruction use : newValue.getUses())
        {
            if (use != init)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a call result must be forced into a named temporary instead of inlined.
     */
    private boolean shouldStoreMethodResult(InvokeInstruction invoke, SSAValue result)
    {
        if (result == null || result.getType() == null) return false;
        if (result.getUses().size() <= 1) return false;

        String methodName = invoke.getName();
        if (isImportantMethodName(methodName))
        {
            return isUsedAsMethodReceiver(result);
        }
        return false;
    }

    private boolean isImportantMethodName(String methodName)
    {
        if (methodName == null) return false;
        if (methodName.startsWith("get") && methodName.length() > 3) return true;
        if (methodName.startsWith("find") && methodName.length() > 4) return true;
        if (methodName.startsWith("load") && methodName.length() > 4) return true;
        if (methodName.startsWith("create") && methodName.length() > 6) return true;
        if (methodName.startsWith("compute") && methodName.length() > 7) return true;
        if (methodName.startsWith("read") && methodName.length() > 4) return true;
        if (methodName.startsWith("fetch") && methodName.length() > 5) return true;
        return methodName.startsWith("retrieve") && methodName.length() > 8;
    }

    private boolean isUsedAsMethodReceiver(SSAValue result)
    {
        for (IRInstruction use : result.getUses())
        {
            if (use instanceof InvokeInstruction)
            {
                InvokeInstruction invoke = (InvokeInstruction) use;
                if (invoke.getInvokeType() != InvokeType.STATIC)
                {
                    java.util.List<Value> args = invoke.getArguments();
                    if (!args.isEmpty() && args.get(0) == result)
                    {
                        SSAValue invokeResult = invoke.getResult();
                        if (isMethodChainIntermediate(invokeResult))
                        {
                            continue;
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isMethodChainIntermediate(SSAValue value)
    {
        if (value == null) return false;
        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return true;
        for (IRInstruction use : uses)
        {
            if (use instanceof InvokeInstruction)
            {
                InvokeInstruction invoke = (InvokeInstruction) use;
                java.util.List<Value> args = invoke.getArguments();
                if (!args.isEmpty() && args.get(0) == value)
                {
                    SSAValue invokeResult = invoke.getResult();
                    if (isMethodChainIntermediate(invokeResult))
                    {
                        continue;
                    }
                }
                continue;
            }
            if (use instanceof ReturnInstruction) continue;
            if (use instanceof BranchInstruction) continue;
            return false;
        }
        return true;
    }

    /**
     * Checks if an SSA value is used exactly once and that use is a FieldAccessInstruction store.
     */
    private boolean isSingleUsePutField(SSAValue value)
    {
        if (value == null) return false;
        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.size() != 1) return false;
        IRInstruction use = uses.get(0);
        if (use instanceof FieldAccessInstruction)
        {
            FieldAccessInstruction fa = (FieldAccessInstruction) use;
            return fa.isStore();
        }
        return false;
    }

    private boolean isSingleUsePhiOperand(SSAValue value)
    {
        if (value == null) return false;
        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.size() != 1) return false;
        IRInstruction use = uses.get(0);
        return use instanceof PhiInstruction;
    }

    /**
     * Checks if an SSA value is used by a StoreLocalInstruction.
     */
    private boolean isUsedByStoreLocal(SSAValue value)
    {
        if (value == null) return false;
        return isUsedByStoreLocalWithVisited(value, new HashSet<>());
    }

    private boolean isUsedByStoreLocalWithVisited(SSAValue value, Set<SSAValue> visited)
    {
        if (value == null || !visited.add(value)) return false;

        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return false;

        for (IRInstruction use : uses)
        {
            if (use instanceof StoreLocalInstruction)
            {
                return true;
            }
            if (use instanceof CopyInstruction)
            {
                CopyInstruction copy = (CopyInstruction) use;
                if (copy.getResult() != null)
                {
                    if (isUsedByStoreLocalWithVisited(copy.getResult(), visited))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Gets the PHI instruction that uses this value, if any.
     */
    private PhiInstruction getPhiUsingValue(SSAValue value)
    {
        if (value == null) return null;
        return getPhiUsingValueWithVisited(value, new HashSet<>());
    }

    /**
     * Whether the value is stored into a local, materializing it as a real assignment statement.
     */
    private boolean hasStoreLocalUse(SSAValue value)
    {
        if (value == null)
        {
            return false;
        }
        for (IRInstruction use : value.getUses())
        {
            if (use instanceof StoreLocalInstruction)
            {
                return true;
            }
        }
        return false;
    }

    private PhiInstruction getPhiUsingValueWithVisited(SSAValue value, Set<SSAValue> visited)
    {
        if (value == null || !visited.add(value)) return null;

        java.util.List<IRInstruction> uses = value.getUses();
        for (IRInstruction use : uses)
        {
            if (use instanceof PhiInstruction)
            {
                // A degenerate phi (all incoming values identical) carries no merge information and
                // must not drive an assignment statement: doing so duplicates the store that already
                // materializes the value (e.g. spurious phi(B:v, C:v) on a local written in one branch).
                if (isDegeneratePhi((PhiInstruction) use))
                {
                    continue;
                }
                return (PhiInstruction) use;
            }
            if (use instanceof CopyInstruction)
            {
                CopyInstruction copy = (CopyInstruction) use;
                if (copy.getResult() != null)
                {
                    PhiInstruction phi = getPhiUsingValueWithVisited(copy.getResult(), visited);
                    if (phi != null)
                    {
                        return phi;
                    }
                }
            }
        }
        return null;
    }

    /**
     * A phi is degenerate when all its incoming values are the same {@link Value} (or it has a single incoming).
     */
    private static boolean isDegeneratePhi(PhiInstruction phi)
    {
        Value common = null;
        boolean first = true;
        for (Value incoming : phi.getIncomingValues().values())
        {
            if (first)
            {
                common = incoming;
                first = false;
            }
            else if (incoming != common)
            {
                return false;
            }
        }
        return true;
    }

    private PhiInstruction getPhiThroughStoreChain(SSAValue value)
    {
        if (value == null) return null;

        for (IRInstruction use : value.getUses())
        {
            if (use instanceof StoreLocalInstruction)
            {
                StoreLocalInstruction store = (StoreLocalInstruction) use;
                int slot = store.getLocalIndex();

                for (IRBlock block : context.getIrMethod().getBlocks())
                {
                    for (IRInstruction instr : block.getInstructions())
                    {
                        if (instr instanceof LoadLocalInstruction)
                        {
                            LoadLocalInstruction load = (LoadLocalInstruction) instr;
                            if (load.getLocalIndex() == slot && load.getResult() != null)
                            {
                                PhiInstruction phi = getPhiUsingValue(load.getResult());
                                if (phi != null)
                                {
                                    return phi;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private SSAValue findNewInstructionValue(SSAValue value)
    {
        if (value == null) return null;
        Set<SSAValue> visited = new HashSet<>();
        SSAValue current = value;
        while (visited.add(current))
        {
            IRInstruction def = current.getDefinition();
            if (def instanceof NewInstruction)
            {
                return current;
            }
            if (def instanceof CopyInstruction)
            {
                Value source = ((CopyInstruction) def).getSource();
                if (source instanceof SSAValue)
                {
                    current = (SSAValue) source;
                    continue;
                }
            }
            break;
        }
        return null;
    }

    /**
     * Whether {@code v} is a compile-time constant operand (a bare constant or a constant SSA def).
     */
    private static boolean isConstantOperand(Value v)
    {
        return v instanceof Constant
                || (v instanceof SSAValue && ((SSAValue) v).getDefinition() instanceof ConstantInstruction);
    }

    private boolean isUsedByArrayStore(SSAValue value)
    {
        if (value == null) return false;

        java.util.List<IRInstruction> uses = value.getUses();
        for (IRInstruction use : uses)
        {
            if (use instanceof ArrayAccessInstruction)
            {
                ArrayAccessInstruction aa = (ArrayAccessInstruction) use;
                if (aa.isStore() && aa.getArray() == value)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True if {@code value} is consumed solely as the stored value of a single array store ({@code arr[i] =
     * value}).
     */
    private boolean isSingleUseArrayStoreValue(SSAValue value)
    {
        if (value == null || value.getUses().size() != 1) return false;
        IRInstruction use = value.getUses().get(0);
        if (use instanceof ArrayAccessInstruction)
        {
            ArrayAccessInstruction aa = (ArrayAccessInstruction) use;
            return aa.isStore() && aa.getValue() == value;
        }
        return false;
    }

    /**
     * Gets the local variable name from the StoreLocalInstruction that stores this value.
     */
    private String getLocalNameFromStoreLocal(SSAValue value)
    {
        if (value == null) return null;

        java.util.List<IRInstruction> uses = value.getUses();
        for (IRInstruction use : uses)
        {
            if (use instanceof StoreLocalInstruction)
            {
                StoreLocalInstruction store = (StoreLocalInstruction) use;
                int localIndex = store.getLocalIndex();
                SourceType valueType = typeRecoverer.recoverType(value);
                String name = partitionName(store);
                if (name == null)
                {
                    name = getNameForLocalSlotWithType(localIndex, valueType);
                }
                if (name != null && context.getExpressionContext().isDeclared(name))
                {
                    SourceType declaredType = context.getExpressionContext().getDeclaredType(name);
                    if (declaredType != null && !typesAreCompatibleForDeclaration(declaredType, valueType))
                    {
                        name = generateUniqueLocalName(localIndex);
                    }
                }
                return name;
            }
        }
        return null;
    }

    private boolean typesAreCompatibleForDeclaration(SourceType type1, SourceType type2)
    {
        if (type1 == null || type2 == null)
        {
            return true;
        }
        if (type1.equals(type2))
        {
            return true;
        }
        boolean type1Array = type1 instanceof ArraySourceType;
        boolean type2Array = type2 instanceof ArraySourceType;
        return type1Array == type2Array;
    }

    private final Set<PhiInstruction> selfStorePhis = new HashSet<>();

    private void detectSelfStorePhis(IRMethod method)
    {
        selfStorePhis.clear();
        for (IRBlock block : method.getBlocks())
        {
            for (PhiInstruction phi : block.getPhiInstructions())
            {
                if (isSelfStorePhiPattern(phi))
                {
                    selfStorePhis.add(phi);
                    markSelfStorePhiChain(phi);
                }
            }
        }
    }

    private void markSelfStorePhiChain(PhiInstruction phi)
    {
        FieldAccessInstruction fieldLoad = findFieldLoadInPhiChain(phi, new HashSet<>());
        if (fieldLoad == null) return;

        SourceType fieldType = typeRecoverer.recoverType(fieldLoad.getDescriptor());
        Expression fieldExpr = new FieldAccessExpr(
            null, fieldLoad.getName(), fieldLoad.getOwner(), fieldLoad.isStatic(), fieldType)
            .withDescriptor(fieldLoad.getDescriptor());

        Set<PhiInstruction> visited = new HashSet<>();
        cacheFieldExprForPhiChain(phi, fieldExpr, visited);
    }

    private void cacheFieldExprForPhiChain(PhiInstruction phi, Expression fieldExpr, Set<PhiInstruction> visited)
    {
        if (phi == null || visited.contains(phi)) return;
        visited.add(phi);

        if (phi.getResult() != null)
        {
            context.getExpressionContext().cacheExpression(phi.getResult(), fieldExpr);
        }

        for (Value operand : phi.getOperands())
        {
            if (operand instanceof SSAValue)
            {
                SSAValue ssaOp = (SSAValue) operand;
                IRInstruction def = ssaOp.getDefinition();
                if (def instanceof PhiInstruction)
                {
                    PhiInstruction nestedPhi = (PhiInstruction) def;
                    selfStorePhis.add(nestedPhi);
                    cacheFieldExprForPhiChain(nestedPhi, fieldExpr, visited);
                }
            }
        }
    }

    private boolean isSelfStorePhiPattern(PhiInstruction phi)
    {
        if (phi == null || phi.getResult() == null) return false;

        FieldAccessInstruction fieldLoad = findFieldLoadInPhiChain(phi, new HashSet<>());
        if (fieldLoad == null)
        {
            return false;
        }

        // The recovery renders this field cursor with an implicit (null) receiver, which only
        // reproduces the original access for a static field or a `this`-receiver field. An instance
        // field whose receiver is the cursor itself (e.g. `e.next` where `e` is this phi) would lose
        // its receiver and collapse to `this.next`; such a phi is an ordinary local, not a cursor.
        if (!selfStoreFieldReceiverIsImplicit(fieldLoad))
        {
            return false;
        }

        String fieldOwner = fieldLoad.getOwner();
        String fieldName = fieldLoad.getName();

        return hasFieldStoreInPhiUseChain(phi, fieldOwner, fieldName, new HashSet<>());
    }

    /**
     * True when {@code fieldLoad}'s receiver matches the implicit (null) receiver the self-store recovery emits.
     */
    private boolean selfStoreFieldReceiverIsImplicit(FieldAccessInstruction fieldLoad)
    {
        if (fieldLoad.isStatic())
        {
            return true;
        }
        Value receiver = fieldLoad.getObjectRef();
        if (!(receiver instanceof SSAValue))
        {
            return false;
        }
        return !context.getIrMethod().isStatic() && slotOfValue((SSAValue) receiver) == 0;
    }

    /**
     * True when {@code result} (a field load feeding {@code targetPhi}) is written by a store_local to a source
     * variable other than the phi's own.
     */
    private boolean fieldLoadValueBelongsToOtherVariable(SSAValue result, PhiInstruction targetPhi)
    {
        String phiName = context.getExpressionContext().getVariableName(targetPhi.getResult());
        if (phiName == null)
        {
            return false;
        }
        for (IRInstruction use : result.getUses())
        {
            if (use instanceof StoreLocalInstruction)
            {
                String storeName = partitionName(use);
                if (storeName != null && !storeName.equals(phiName))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasFieldStoreInPhiUseChain(PhiInstruction phi, String fieldOwner, String fieldName, Set<PhiInstruction> visited)
    {
        if (phi == null || visited.contains(phi)) return false;
        visited.add(phi);

        SSAValue phiResult = phi.getResult();
        if (phiResult == null) return false;

        java.util.List<IRInstruction> uses = phiResult.getUses();
        for (IRInstruction use : uses)
        {
            if (use instanceof FieldAccessInstruction)
            {
                FieldAccessInstruction fai = (FieldAccessInstruction) use;
                if (fai.isStore() && fieldOwner.equals(fai.getOwner()) && fieldName.equals(fai.getName()))
                {
                    return true;
                }
            }
            else if (use instanceof PhiInstruction)
            {
                if (hasFieldStoreInPhiUseChain((PhiInstruction) use, fieldOwner, fieldName, visited))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private FieldAccessInstruction findFieldLoadInPhiChain(PhiInstruction phi, Set<PhiInstruction> visited)
    {
        if (phi == null || visited.contains(phi)) return null;
        visited.add(phi);

        for (Value operand : phi.getOperands())
        {
            if (operand instanceof SSAValue)
            {
                SSAValue ssaOp = (SSAValue) operand;
                IRInstruction def = ssaOp.getDefinition();
                if (def instanceof FieldAccessInstruction)
                {
                    FieldAccessInstruction fai = (FieldAccessInstruction) def;
                    if (fai.isLoad())
                    {
                        return fai;
                    }
                }
                else if (def instanceof PhiInstruction)
                {
                    FieldAccessInstruction nested = findFieldLoadInPhiChain((PhiInstruction) def, visited);
                    if (nested != null)
                    {
                        return nested;
                    }
                }
            }
        }
        return null;
    }

    private FieldAccessInstruction getSelfStoreFieldInfo(PhiInstruction phi)
    {
        return findFieldLoadInPhiChain(phi, new HashSet<>());
    }

    /**
     * Gets a default value for the given type.
     */
    private Expression getDefaultValue(SourceType type)
    {
        if (type instanceof PrimitiveSourceType)
        {
            PrimitiveSourceType pst = (PrimitiveSourceType) type;
            if (pst == PrimitiveSourceType.BOOLEAN)
            {
                return LiteralExpr.ofBoolean(false);
            }
            else if (pst == PrimitiveSourceType.LONG)
            {
                return LiteralExpr.ofLong(0L);
            }
            else if (pst == PrimitiveSourceType.FLOAT)
            {
                return LiteralExpr.ofFloat(0.0f);
            }
            else if (pst == PrimitiveSourceType.DOUBLE)
            {
                return LiteralExpr.ofDouble(0.0);
            }
            else
            {
                return LiteralExpr.ofInt(0);
            }
        }
        return LiteralExpr.ofNull();
    }

    /**
     * Checks if an expression is a default value (0, false, null, 0L, 0.0, etc.).
     */
    private boolean isDefaultValue(Expression expr)
    {
        if (!(expr instanceof LiteralExpr))
        {
            return false;
        }
        LiteralExpr literal = (LiteralExpr) expr;
        Object value = literal.getValue();
        if (value == null)
        {
            return true;
        }
        if (value instanceof Number)
        {
            return ((Number) value).doubleValue() == 0.0;
        }
        if (value instanceof Boolean)
        {
            return !((Boolean) value);
        }
        return false;
    }

    /**
     * Tracks try handlers that have already been processed to avoid infinite loops
     */
    private final Set<ExceptionHandler> processedTryHandlers = new HashSet<>();
    /**
     * For-loop induction inits already re-emitted (as a for-init or in front of a while), never twice.
     */
    private final Set<IRInstruction> consumedForLoopInits = new HashSet<>();

    /**
     * Stores recovered ahead of their own position, at the call whose carried result they consume.
     */
    private final Set<IRInstruction> earlyRecoveredStores = new HashSet<>();
    /**
     * Excised inlined-finally copy blocks consumed outright; the walk and continuation route around them.
     */
    private final Set<IRBlock> consumedFinallyShells = new HashSet<>();
    /**
     * Tracks handler blocks to prevent nested try-finally for same finally block
     */
    private final Set<IRBlock> processedHandlerBlocks = new HashSet<>();

    /**
     * Recovers the statements of one region as a hand-off, preserving the surrounding recovery's
     * processed marks.
     *
     * @param startBlock the block the region starts at
     * @param stopBlocks blocks that bound the region and are not recovered into it
     * @return the recovered statements
     */
    public List<Statement> recoverBlockSequence(IRBlock startBlock, Set<IRBlock> stopBlocks)
    {
        // Every sub-region (an if arm, a loop body, a clause body) is a region hand-off: a sub-region
        // pass preserves the surrounding recovery's processed marks (only the top-level whole-method
        // pass owns the mark namespace), and a re-entrant pass launched from inside an engine emit is
        // snapshot-protected by the delegate.
        return recoverRegionHandoff(startBlock, stopBlocks);
    }

    /**
     * Recovers a wholesale region hand-off - an exception-scaffolding piece (the code before a try, a try body, or
     * the continuation after a try/catch) - preferring the RC engine, then the legacy walk.
     */
    private List<Statement> recoverRegionHandoff(IRBlock startBlock, Set<IRBlock> stopBlocks)
    {
        // A stop that is a bare goto pad jumping BACK to a block that dominates it is a loop's own
        // latch pad past a try range's end - the construct's internal edge, not a boundary. The engine
        // attempts run without it so the loop can close; the staging and the legacy walk keep the
        // original stops, whose continuation semantics they are built around.
        Set<IRBlock> engineStops = stopBlocks;
        DominatorTree handoffDt = context.getDominatorTree();
        if (handoffDt != null)
        {
            for (IRBlock stop : stopBlocks)
            {
                if (isLatchPad(stop, handoffDt))
                {
                    if (engineStops == stopBlocks)
                    {
                        engineStops = new HashSet<>(stopBlocks);
                    }
                    engineStops.remove(stop);
                }
            }
        }
        List<Statement> structured = rcsStructurer.tryStructureRegion(startBlock, engineStops);
        if (structured != null)
        {
            return structured;
        }
        List<Statement> staged = recoverSequentialTryStages(startBlock, stopBlocks);
        if (staged != null)
        {
            return staged;
        }
        // No linear staging (a try inside a loop body or on one arm of a branch): let the engine place
        // each try as an opaque composite node at its structural position.
        structured = rcsStructurer.tryStructureRegion(startBlock, engineStops, true);
        if (structured != null)
        {
            return structured;
        }
        throw retiredSchemaRecovery("region-handoff", startBlock);
    }

    /**
     * Whether {@code b} is a bare goto pad whose single successor dominates it - a loop latch pad.
     */
    private boolean isLatchPad(IRBlock b, DominatorTree dt)
    {
        if (b.getSuccessors().size() != 1)
        {
            return false;
        }
        List<IRInstruction> instrs = b.getInstructions();
        boolean bare = instrs.isEmpty()
                || (instrs.size() == 1 && instrs.get(0) == b.getTerminator()
                    && b.getTerminator() instanceof SimpleInstruction
                    && ((SimpleInstruction) b.getTerminator()).getOp() == SimpleOp.GOTO);
        if (!bare)
        {
            return false;
        }
        IRBlock target = b.getSuccessors().iterator().next();
        return dt.dominates(target, b);
    }

    /**
     * Whether {@code info}'s loop is CUT by the walk's stop blocks - some loop block (typically the latch beyond a
     * try's protected range) is a stop.
     */
    private boolean loopCutByStops(RegionInfo info, Set<IRBlock> stops)
    {
        if (info.getLoop() == null)
        {
            return false;
        }
        for (IRBlock lb : info.getLoop().getBlocks())
        {
            if (stops.contains(lb))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code b} lies inside a switch case body - some block in the method switches, and {@code b}
     * is one of its case targets or dominated by one.
     */
    private boolean enclosingSwitchCase(IRBlock b)
    {
        DominatorTree dt = context.getDominatorTree();
        IRMethod m = context.getIrMethod();
        if (dt == null || m == null)
        {
            return false;
        }
        for (IRBlock sb : m.getBlocks())
        {
            if (!(sb.getTerminator() instanceof SwitchInstruction) || sb == b)
            {
                continue;
            }
            for (IRBlock target : sb.getSuccessors())
            {
                if (target == b || dt.dominates(target, b))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether every block of {@code loop} lies within {@code handler}'s merged protected range.
     */
    private boolean loopWithinTryRange(LoopAnalysis.Loop loop, ExceptionHandler handler)
    {
        if (handler == null || handler.getTryStart() == null)
        {
            return false;
        }
        int lo = handler.getTryStart().getBytecodeOffset();
        int hi = mergedTryEndOffset(handler);
        if (hi < 0)
        {
            return false;
        }
        for (IRBlock lb : loop.getBlocks())
        {
            int off = lb.getBytecodeOffset();
            if (off < lo || off >= hi)
            {
                return false;
            }
        }
        return true;
    }

    private List<Statement> recoverSequentialTryStages(IRBlock startBlock, Set<IRBlock> stopBlocks)
    {
        Set<IRBlock> prefix = new HashSet<>();
        Deque<IRBlock> work = new ArrayDeque<>();
        work.add(startBlock);
        IRBlock tryStart = null;
        while (!work.isEmpty())
        {
            IRBlock b = work.poll();
            if (findUnprocessedHandlerStartingAt(b) != null)
            {
                if (tryStart != null && tryStart != b)
                {
                    return null;
                }
                // A try nested INSIDE a loop has no linear staging: control re-enters the blocks before it on
                // the next iteration, so prefix-try-continuation sequencing would hoist the try out of the
                // loop. A try that WRAPS a loop (its start block is that loop's header) is fine - the loop is
                // wholly within the protected range. Decline only when an ENCLOSING loop (one whose header is
                // a different block) contains the try start.
                // A try INSIDE a switch case has no linear staging: the case bodies are branches of the
                // switch, so prefix-try-continuation sequencing hoists the try out of the construct.
                if (enclosingSwitchCase(b))
                {
                    return null;
                }
                if (context.getLoopAnalysis() != null)
                {
                    ExceptionHandler stageHandler = findUnprocessedHandlerStartingAt(b);
                    LoopAnalysis.Loop enclosing = context.getLoopAnalysis().getLoop(b);
                    while (enclosing != null)
                    {
                        // "Try wraps loop" is judged by CONTAINMENT, not header identity: a do-while whose
                        // body STARTS with the try makes the try-start block the loop header too, but the
                        // latch lies outside the protected range - staging that shape hoists the try out
                        // of the loop. Only a loop wholly within the range stages linearly.
                        if (!loopWithinTryRange(enclosing, stageHandler))
                        {
                            return null;
                        }
                        enclosing = enclosing.getParent();
                    }
                }
                tryStart = b;
                continue;
            }
            if (!prefix.add(b))
            {
                continue;
            }
            for (IRBlock s : b.getSuccessors())
            {
                if (stopBlocks.contains(s))
                {
                    return null;
                }
                if (!prefix.contains(s))
                {
                    work.add(s);
                }
            }
        }
        if (tryStart == null)
        {
            return null;
        }
        ExceptionHandler handler = findUnprocessedHandlerStartingAt(tryStart);
        if (handler == null || prefix.contains(handler.getHandlerBlock()))
        {
            return null;
        }
        if (tryHasFinallyHandler(tryStart))
        {
            // A finally (a rethrowing catch-all) needs its inlined copies de-duplicated and its clause
            // built; that is the outer-handler scaffolding's job. Delegate the stage to it: the
            // handler-free prefix first, then the scaffolding from the try, which recovers the region's
            // continuation itself.
            return recoverFinallyStage(startBlock, tryStart, stopBlocks);
        }

        List<Statement> out = new ArrayList<>();
        if (tryStart != startBlock)
        {
            Set<IRBlock> prefixStops = new HashSet<>(stopBlocks);
            prefixStops.add(tryStart);
            out.addAll(recoverRegionHandoff(startBlock, prefixStops));
            // The prefix stop keeps the handoff's direct steps before the try, but a legacy sub-walk inside
            // it (an if-arm containing the try) recovers the try/catch through its own handler branch. When
            // the prefix consumed the handler, it owned the whole region including the continuation;
            // recovering the try again here would emit an empty duplicate after it.
            if (processedTryHandlers.contains(handler) || processedHandlerBlocks.contains(handler.getHandlerBlock()))
            {
                return out;
            }
            // A prefix that ends by terminating unconditionally consumed the try's shared continuation (a
            // path bypassing a one-arm try to the merge): the try appended here would be unreachable dead
            // code and the arm it was cut from has already been emitted without it. A guard-shaped prefix
            // (its return inside an if) leaves the fall-through open and stages normally.
            if (isTerminatingBlock(new BlockStmt(out)))
            {
                return null;
            }
        }
        processedTryHandlers.add(handler);
        if (handler.getHandlerBlock() != null)
        {
            processedHandlerBlocks.add(handler.getHandlerBlock());
        }
        Set<IRBlock> tryVisited = new HashSet<>(prefix);
        Statement recovered = recoverTryCatch(tryStart, handler, stopBlocks, tryVisited);
        if (recovered == null)
        {
            throw retiredSchemaRecovery("try-stage", tryStart);
        }
        out.add(recovered);
        if (!isTerminatingRecoveredTry(recovered))
        {
            IRBlock after = findBlockAfterTryCatch(handler, tryVisited);
            // The try's normal exit is often an empty goto shell in front of the real continuation (the
            // shared join past the catch). Resolve through it so the processed-return check below sees the
            // join itself, not the shell.
            after = resolveThroughGotoShells(after);
            if (after != null && !stopBlocks.contains(after))
            {
                if (context.isProcessed(after) && isReturnBlock(after))
                {
                    // The continuation is a shared trailing return a prefix arm already absorbed (a guard's
                    // early exit and the try's fall-through converge on one return block). The hand-off
                    // emits nothing for a processed region, which would drop the fall-through's return
                    // entirely; a return terminator is idempotent, so re-emit its recovered statements.
                    out.addAll(context.getStatements(after));
                }
                else
                {
                    out.addAll(recoverRegionHandoff(after, stopBlocks));
                }
            }
        }
        return out;
    }

    /**
     * The blocks that belong exclusively to catch code: each handler's entry block plus every block it dominates.
     */
    private Set<IRBlock> catchExclusiveBlocks(List<ExceptionHandler> handlers)
    {
        Set<IRBlock> out = new HashSet<>();
        DominatorTree dt = context.getDominatorTree();
        for (ExceptionHandler h : handlers)
        {
            IRBlock hb = h.getHandlerBlock();
            if (hb == null)
            {
                continue;
            }
            out.add(hb);
            if (dt != null)
            {
                for (IRBlock b : context.getIrMethod().getBlocks())
                {
                    if (dt.dominates(hb, b))
                    {
                        out.add(b);
                    }
                }
            }
        }
        return out;
    }


    @Override
    public boolean startsUnprocessedHandler(IRBlock block)
    {
        return findUnprocessedHandlerStartingAt(block) != null;
    }

    @Override
    public boolean isRetiredHandlerBlock(IRBlock block)
    {
        return processedHandlerBlocks.contains(block);
    }

    @Override
    public TryNodeDescriptor decodeTryNode(IRBlock block, Set<IRBlock> regionStops)
    {
        this.decodeRegionStops = regionStops == null ? Collections.emptySet() : regionStops;
        ExceptionHandler h = findUnprocessedHandlerStartingAt(block);
        if (h == null || h.getHandlerBlock() == null)
        {
            return null;
        }
        IRMethod irMethod = context.getIrMethod();
        int startOff = h.getTryStart() != null ? h.getTryStart().getBytecodeOffset() : -1;
        int endOff = -1;
        for (ExceptionHandler eh : irMethod.getExceptionHandlers())
        {
            if (eh.getHandlerBlock() != h.getHandlerBlock())
            {
                continue;
            }
            if (eh.getTryStart() != null && (startOff < 0 || eh.getTryStart().getBytecodeOffset() < startOff))
            {
                startOff = eh.getTryStart().getBytecodeOffset();
            }
            if (eh.getTryEnd() != null && eh.getTryEnd().getBytecodeOffset() > endOff)
            {
                endOff = eh.getTryEnd().getBytecodeOffset();
            }
        }
        if (startOff < 0 || endOff <= startOff || block.getBytecodeOffset() != startOff)
        {
            trace("node-decline range block=" + block.getBytecodeOffset()
                    + " startOff=" + startOff + " endOff=" + endOff);
            return null;
        }
        boolean finallyNode = false;
        if (tryHasFinallyHandler(block))
        {
            // A finally-protected try can be a node: the merged window is widened over EVERY same-start
            // handler's family, so it covers the try body, the user catch bodies, the inlined finally
            // copies between them, and the rethrower's own split ranges. The delegate is still handed the
            // ORIGINAL widest pick (a user catch when ranges tie), matching what the legacy walk recovers -
            // the flat try/catch/finally with the rethrow clause folded into the finally. The inlined
            // copies are de-duplicated from the exits up front; a shape whose copies cannot be excised
            // statically declines (leaving them would double the finally's effect on the normal path).
            finallyNode = true;
            for (ExceptionHandler sib : irMethod.getExceptionHandlers())
            {
                if (sib.getHandlerBlock() == null || sib.getTryStart() == null
                        || sib.getTryStart().getBytecodeOffset() != block.getBytecodeOffset())
                {
                    continue;
                }
                for (ExceptionHandler eh : irMethod.getExceptionHandlers())
                {
                    if (eh.getHandlerBlock() == sib.getHandlerBlock() && eh.getTryEnd() != null
                            && eh.getTryEnd().getBytecodeOffset() > endOff)
                    {
                        endOff = eh.getTryEnd().getBytecodeOffset();
                    }
                }
            }
        }
        Set<IRBlock> consumed = new HashSet<>();
        boolean monitorFamily = false;
        for (ExceptionHandler sib : irMethod.getExceptionHandlers())
        {
            if (sib.getTryStart() != null && sib.getTryStart().getBytecodeOffset() == startOff
                    && detectSynchronizedLock(sib) != null)
            {
                monitorFamily = true;
                break;
            }
        }
        if (finallyNode && !monitorFamily && context.getLoopAnalysis() != null && context.getLoopAnalysis().getLoop(block) != null)
        {
            // Inside a loop the finally family's span window swallows the loop's own continuation
            // (the code between the protected range and the latch, where a relowered layout parks
            // it) and the predecessor closure is off-limits. Membership is the family's actual
            // RANGES: the de-duplication excises the close copies from the epilogue, and the
            // epilogue's real code stays outside the node as region code at the join.
            for (ExceptionHandler sib : irMethod.getExceptionHandlers())
            {
                if (sib.getHandlerBlock() == null || sib.getTryStart() == null || sib.getTryStart().getBytecodeOffset() != block.getBytecodeOffset())
                {
                    continue;
                }
                for (ExceptionHandler eh : irMethod.getExceptionHandlers())
                {
                    if (eh.getHandlerBlock() != sib.getHandlerBlock() || eh.getTryStart() == null || eh.getTryEnd() == null)
                    {
                        continue;
                    }
                    int lo = eh.getTryStart().getBytecodeOffset();
                    int hi = eh.getTryEnd().getBytecodeOffset();
                    for (IRBlock b : irMethod.getBlocks())
                    {
                        if (b.getBytecodeOffset() >= lo && b.getBytecodeOffset() < hi)
                        {
                            consumed.add(b);
                        }
                    }
                }
            }
        }
        else
        {
            for (IRBlock b : irMethod.getBlocks())
            {
                int off = b.getBytecodeOffset();
                if (off >= startOff && off < endOff)
                {
                    consumed.add(b);
                }
            }
        }
        // A plain typed catch with SPLIT ranges (javac splits around a return/break in the try) has
        // the same interleaving disease as a finally family: the merged window swallows the gap
        // between its ranges, where a relowered layout parks unrelated code (a return arm, a loop
        // continuation). Carve the window down to the family's actual ranges plus the blocks only
        // the construct reaches - same closure, keyed on this handler's own family.
        if (!finallyNode)
        {
            List<int[]> ownRanges = new ArrayList<>();
            for (ExceptionHandler eh : irMethod.getExceptionHandlers())
            {
                if (eh.getHandlerBlock() == h.getHandlerBlock() && eh.getTryStart() != null && eh.getTryEnd() != null)
                {
                    ownRanges.add(new int[]{eh.getTryStart().getBytecodeOffset(),
                            eh.getTryEnd().getBytecodeOffset()});
                }
            }
            boolean acyclicPlain = context.getLoopAnalysis() == null || context.getLoopAnalysis().getLoop(block) == null;
            if (ownRanges.size() > 1 && acyclicPlain)
            {
                Set<IRBlock> plainClosure = new HashSet<>();
                for (IRBlock b : irMethod.getBlocks())
                {
                    int boff = b.getBytecodeOffset();
                    for (int[] r : ownRanges)
                    {
                        if (boff >= r[0] && boff < r[1])
                        {
                            plainClosure.add(b);
                            break;
                        }
                    }
                }
                plainClosure.add(h.getHandlerBlock());
                boolean plainGrew = true;
                while (plainGrew)
                {
                    plainGrew = false;
                    for (IRBlock b : irMethod.getBlocks())
                    {
                        if (plainClosure.contains(b) || b == irMethod.getEntryBlock()
                                || b.getPredecessors().isEmpty()
                                || b.getTerminator() instanceof ReturnInstruction)
                        {
                            continue;
                        }
                        if (plainClosure.containsAll(b.getPredecessors()))
                        {
                            plainClosure.add(b);
                            plainGrew = true;
                        }
                    }
                }
                consumed = plainClosure;
            }
            else if (ownRanges.size() > 1)
            {
                // Inside a loop the predecessor closure is off-limits (it swallows latches), but the
                // merged window still over-consumes: a relowered layout parks unrelated code in the
                // gap between the family's split ranges. A gap block no consumed block flows into is
                // foreign - carve it out so the join scan never meets its exits. Strictly narrowing:
                // range-covered blocks and interior-reached gap blocks (the latch path) are untouched.
                boolean carved = true;
                while (carved)
                {
                    carved = false;
                    for (IRBlock b : new ArrayList<>(consumed))
                    {
                        if (b == block || b == h.getHandlerBlock())
                        {
                            continue;
                        }
                        int boff = b.getBytecodeOffset();
                        boolean inRange = false;
                        for (int[] r : ownRanges)
                        {
                            if (boff >= r[0] && boff < r[1])
                            {
                                inRange = true;
                                break;
                            }
                        }
                        if (inRange)
                        {
                            continue;
                        }
                        boolean reached = false;
                        for (IRBlock p : b.getPredecessors())
                        {
                            if (consumed.contains(p))
                            {
                                reached = true;
                                break;
                            }
                        }
                        if (!reached)
                        {
                            consumed.remove(b);
                            carved = true;
                        }
                    }
                }
            }
        }
        if (finallyNode)
        {
            // The widened window spans min..max over the sibling families' SPLIT ranges, and javac can lay
            // the construct's continuation - the return the normal-exit copy converges on - in the gap
            // between a family's ranges (before a catch body the family also protects). A terminal block in
            // such a gap is protected by nothing and is the join the node must continue at, not part of the
            // construct; leave it unconsumed so the join scan finds it.
            List<int[]> protectedRanges = new ArrayList<>();
            Set<IRBlock> familyHandlers = new HashSet<>();
            for (ExceptionHandler sib : irMethod.getExceptionHandlers())
            {
                if (sib.getHandlerBlock() == null || sib.getTryStart() == null
                        || sib.getTryStart().getBytecodeOffset() != block.getBytecodeOffset())
                {
                    continue;
                }
                for (ExceptionHandler eh : irMethod.getExceptionHandlers())
                {
                    if (eh.getHandlerBlock() == sib.getHandlerBlock())
                    {
                        familyHandlers.add(eh.getHandlerBlock());
                        if (eh.getTryStart() != null && eh.getTryEnd() != null)
                        {
                            protectedRanges.add(new int[]{eh.getTryStart().getBytecodeOffset(),
                                    eh.getTryEnd().getBytecodeOffset()});
                        }
                    }
                }
            }
            // A RELOWERED layout interleaves the construct with unrelated blocks: continuation code
            // and foreign handlers can sit between the family's ranges while construct code (the
            // de-duplicated guarded close on the normal path) lands past the window's end. The
            // offset window then both over- and under-consumes, and the join scan meets phantom
            // rivals. Membership is really a CLOSURE: the family's protected ranges and handlers,
            // every block all of whose predecessors already belong (interior flow can't escape), and
            // every nested handler whose whole protected range lies inside. The unprotected-return
            // carve stays: a gap return the exits converge on is the join, never construct interior.
            // ACYCLIC, non-synchronized contexts only: inside a loop the predecessor closure
            // swallows latches and break continuations the loop model owns, and extending it there
            // (dominance-bounded, wrapper-gated) let a wrapped retry loop re-wire its join through
            // excised copies and spin - the TryLoop2 trap. The monitor gate checks the WHOLE
            // same-start family: a user catch inside a synchronized body shares its try start with
            // the monitor rethrower.
            boolean acyclicNode = context.getLoopAnalysis() == null
                    || context.getLoopAnalysis().getLoop(block) == null;
            // The monitor scaffolding may not be the node's OWN handler: a user catch inside a
            // synchronized body shares its try start with the sync rethrower, so every same-start
            // handler is checked before the closure engages.
            boolean syncFamily = false;
            for (ExceptionHandler sib : irMethod.getExceptionHandlers())
            {
                if (sib.getTryStart() != null && sib.getTryStart().getBytecodeOffset() == startOff
                        && detectSynchronizedLock(sib) != null)
                {
                    syncFamily = true;
                    break;
                }
            }
            if (acyclicNode && !syncFamily)
            {
            Set<IRBlock> closure = buildFamilyClosure(protectedRanges, familyHandlers, endOff);
            carveUnprotectedReturns(closure, protectedRanges);
            consumed = closure;
            }
            else
            {
                for (IRBlock b : new ArrayList<>(consumed))
                {
                    if (!(b.getTerminator() instanceof ReturnInstruction))
                    {
                        continue;
                    }
                    int boff = b.getBytecodeOffset();
                    boolean covered = false;
                    for (int[] r : protectedRanges)
                    {
                        if (boff >= r[0] && boff < r[1])
                        {
                            covered = true;
                            break;
                        }
                    }
                    if (!covered)
                    {
                        consumed.remove(b);
                    }
                }
            }
        }
        // A different, unprocessed handler protecting a sub-range is a nested try. The try/catch recovery
        // handles nesting itself, so the node stays decodable as long as the nested catch code lies wholly
        // within the consumed range - its handler entry and every block that entry dominates must fall inside
        // the merged protected window, or the recovery would consume blocks the model cannot predict.
        DominatorTree nestDt = context.getDominatorTree();
        Set<IRBlock> siblingHandlerBlocks = new HashSet<>();
        if (!finallyNode)
        {
            // A same-start handler with its own handler block is this try's sibling CATCH CLAUSE
            // (a multi-catch), not a nested try: the delegate recovers every clause of the statement,
            // so the node consumes the sibling's catch code and scans its exits for the join.
            for (ExceptionHandler sib : irMethod.getExceptionHandlers())
            {
                if (sib.getHandlerBlock() != null && sib.getHandlerBlock() != h.getHandlerBlock()
                        && sib.getTryStart() != null
                        && sib.getTryStart().getBytecodeOffset() == block.getBytecodeOffset()
                        && !processedTryHandlers.contains(sib)
                        && !processedHandlerBlocks.contains(sib.getHandlerBlock()))
                {
                    siblingHandlerBlocks.add(sib.getHandlerBlock());
                }
            }
        }
        if (finallyNode)
        {
            // The finally's sibling scaffolding - every same-start handler family, including the
            // rethrower's own split ranges - is blanket-consumed: the delegate recovery owns the whole
            // construct (flat clauses via the caller's handler pick, copies folded out of the try and
            // catch bodies alike).
            for (ExceptionHandler sib : irMethod.getExceptionHandlers())
            {
                if (sib.getHandlerBlock() != null && sib.getTryStart() != null
                        && sib.getTryStart().getBytecodeOffset() == block.getBytecodeOffset())
                {
                    siblingHandlerBlocks.add(sib.getHandlerBlock());
                }
            }
        }
        for (ExceptionHandler eh : irMethod.getExceptionHandlers())
        {
            if (eh.getHandlerBlock() == null || eh.getHandlerBlock() == h.getHandlerBlock()
                    || eh.getTryStart() == null)
            {
                continue;
            }
            if (siblingHandlerBlocks.contains(eh.getHandlerBlock()))
            {
                // Any entry targeting a same-start sibling's handler block - the sibling's other split
                // ranges included - is the finally/catch scaffolding itself, not a nested try; the finally
                // decode consumes those handlers' whole subtrees.
                continue;
            }
            int s = eh.getTryStart().getBytecodeOffset();
            if (s >= startOff && s < endOff
                    && !processedTryHandlers.contains(eh)
                    && !processedHandlerBlocks.contains(eh.getHandlerBlock()))
            {
                // A handler whose protected blocks lie entirely outside the consumed set is not
                // nested at all: it is a sibling construct a relowered layout parked in the gap
                // between this family's split ranges. Its offset alone puts it in the window;
                // membership says it belongs to the surrounding region.
                boolean protectsConsumed = false;
                int lo = eh.getTryStart().getBytecodeOffset();
                int hi = eh.getTryEnd() != null ? eh.getTryEnd().getBytecodeOffset() : Integer.MAX_VALUE;
                for (IRBlock nb : consumed)
                {
                    int off = nb.getBytecodeOffset();
                    if (off >= lo && off < hi)
                    {
                        protectsConsumed = true;
                        break;
                    }
                }
                if (!protectsConsumed)
                {
                    continue;
                }
                // Containment is judged against the CONSUMED SET, not the offset window: a
                // relowered layout may park the nested catch's subtree past the window's end while
                // the closure has legitimately absorbed it. A block in neither is genuinely outside
                // the model's reach.
                boolean contained = true;
                for (IRBlock nb : irMethod.getBlocks())
                {
                    if (nb == eh.getHandlerBlock() || nestDt.dominates(eh.getHandlerBlock(), nb))
                    {
                        int off = nb.getBytecodeOffset();
                        if ((off < startOff || off >= endOff) && !consumed.contains(nb))
                        {
                            contained = false;
                            break;
                        }
                    }
                }
                if (!contained)
                {
                    trace("node-decline nested-uncontained block=" + block.getBytecodeOffset()
                            + " nested=" + eh.getHandlerBlock().getBytecodeOffset());
                    return null;
                }
            }
        }
        consumed.add(h.getHandlerBlock());

        DominatorTree dt = context.getDominatorTree();
        if (finallyNode)
        {
            return decodeFinallyTryNode(block, h, consumed, dt);
        }
        // A multi-block catch body (a catch with its own branches) is dominated by the handler entry;
        // consume the whole subtree and scan ITS exits for the join, rather than declining on the
        // handler's internal control flow. Only the catch's edges determine the join - the try body's
        // own exits stay the delegate recovery's concern, as before.
        Set<IRBlock> handlerBody = new HashSet<>();
        handlerBody.add(h.getHandlerBlock());
        handlerBody.addAll(siblingHandlerBlocks);
        for (IRBlock b : irMethod.getBlocks())
        {
            for (IRBlock entryBlock : handlerBody.toArray(new IRBlock[0]))
            {
                if (dt.dominates(entryBlock, b))
                {
                    handlerBody.add(b);
                    break;
                }
            }
        }
        consumed.addAll(handlerBody);
        IRBlock after = null;
        for (IRBlock cb : handlerBody)
        {
            for (Map.Entry<IRBlock, EdgeType> e : cb.getSuccessorEdgeTypes().entrySet())
            {
                IRBlock succ = e.getKey();
                if (e.getValue() == EdgeType.EXCEPTION || consumed.contains(succ) || dt.dominates(succ, cb))
                {
                    continue;
                }
                succ = resolveThroughGotoShells(succ);
                if (consumed.contains(succ))
                {
                    continue;
                }
                if (after != null && after != succ)
                {
                    trace("node-decline handler-two-join block=" + block.getBytecodeOffset()
                            + " a=" + after.getBytecodeOffset() + " b=" + succ.getBytecodeOffset());
                    return null;
                }
                after = succ;
            }
        }
        if (after == null)
        {
            // The range-end fallback presumes the try's normal continuation physically follows the
            // protected range (javac's contiguous layout). When the try side is fully TERMINAL too
            // (`try { return f(); } catch { throw wrap; }`), there is no join at all - fabricating
            // one from the next offset wires the node into unrelated code (a relowered layout may
            // place any block there) and the phantom edge reads as an irreducible cycle.
            Set<IRBlock> trySideTargets = new HashSet<>();
            for (IRBlock cb : consumed)
            {
                for (Map.Entry<IRBlock, EdgeType> e : cb.getSuccessorEdgeTypes().entrySet())
                {
                    if (e.getValue() == EdgeType.NORMAL && !consumed.contains(e.getKey()))
                    {
                        trySideTargets.add(e.getKey());
                    }
                }
            }
            if (trySideTargets.size() == 1)
            {
                // The try side's one real continuation IS the join; the offset fallback below can
                // point at whatever block a relowered layout happens to place past the range end
                // (an empty pad, unrelated code).
                after = trySideTargets.iterator().next();
            }
            else if (!trySideTargets.isEmpty())
            {
                int best = Integer.MAX_VALUE;
                for (IRBlock b : irMethod.getBlocks())
                {
                    int off = b.getBytecodeOffset();
                    if (off >= endOff && off < best && !consumed.contains(b))
                    {
                        after = b;
                        best = off;
                    }
                }
            }
            if (after != null && dt.dominates(h.getHandlerBlock(), after))
            {
                trace("node-decline handler-dominates-after block=" + block.getBytecodeOffset());
                return null;
            }
        }
        if (after == block)
        {
            trace("finally-node decline block=" + block.getBytecodeOffset() + " self-join");
            return null;
        }
        // A try INSIDE a loop whose catch jumps OUT of the loop must not take the catch's target as the
        // node's join: the try's own in-loop continuation is the join (the walk resumes inside the loop,
        // keeping the latch in the region model), and the loop model owns the catch's exit edge, which
        // the delegate emits as the loop jump. Bare goto pads on the try-side chain are consumed with it
        // - left out they dangle outside both the node and the region and break the latch model.
        LoopAnalysis loopsForPads = context.getLoopAnalysis();
        LoopAnalysis.Loop enclosingLoop = loopsForPads == null ? null : loopsForPads.getLoop(block);
        if (loopsForPads != null)
        {
            boolean afterOutOfLoop = enclosingLoop != null && after != null
                    && !enclosingLoop.getBlocks().contains(after);
            Set<IRBlock> exitPads = new HashSet<>();
            for (IRBlock cb : consumed)
            {
                if (handlerBody.contains(cb))
                {
                    continue;
                }
                for (Map.Entry<IRBlock, EdgeType> e : cb.getSuccessorEdgeTypes().entrySet())
                {
                    if (e.getValue() != EdgeType.NORMAL || consumed.contains(e.getKey()))
                    {
                        continue;
                    }
                    Set<IRBlock> chain = new HashSet<>();
                    IRBlock landing = resolveThroughGotoShells(e.getKey(), chain);
                    if (landing == null)
                    {
                        continue;
                    }
                    LoopAnalysis.Loop landingLoop = loopsForPads.getLoop(landing);
                    if (landingLoop != null && landingLoop.getHeader() == landing
                            && landingLoop.getBlocks().contains(block))
                    {
                        exitPads.addAll(chain);
                    }
                    else if (afterOutOfLoop && enclosingLoop.getBlocks().contains(landing))
                    {
                        after = landing;
                        afterOutOfLoop = false;
                        exitPads.addAll(chain);
                    }
                }
            }
            exitPads.remove(after);
            consumed.addAll(exitPads);
        }
        trace("finally-node OK block=" + block.getBytecodeOffset()
                + " after=" + (after == null ? "none" : after.getBytecodeOffset()));
        return new TryNodeDescriptor(h, consumed, after);
    }

    @Override
    public List<Statement> recoverBoundaryTail(IRBlock tail)
    {
        if (!isTerminalTail(tail))
        {
            return null;
        }
        List<Statement> out = new ArrayList<>();
        IRBlock b = tail;
        int hops = 0;
        while (b != null && hops++ < 8)
        {
            out.addAll(recoverSimpleBlock(b));
            if (b.getTerminator() instanceof ReturnInstruction
                    || (b.getTerminator() instanceof SimpleInstruction
                        && ((SimpleInstruction) b.getTerminator()).getOp() == SimpleOp.ATHROW))
            {
                return out;
            }
            IRBlock next = null;
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    next = e.getKey();
                }
            }
            b = next;
        }
        return null;
    }

    @Override
    public Statement recoverTryNode(IRBlock block, TryNodeDescriptor node, Set<IRBlock> stopBlocks, Set<IRBlock> alreadyEmitted)
    {
        ExceptionHandler h = node.handler();
        processedTryHandlers.add(h);
        if (h.getHandlerBlock() != null)
        {
            processedHandlerBlocks.add(h.getHandlerBlock());
        }
        // A node whose continuation is a genuine CODE join (not a return tail) hands that boundary to
        // the delegate: without it, a split-range desugar fools the delegate's offset-based
        // continuation scan and the try body absorbs the region's own continuation. The stop goes on
        // the CONTEXT stack too - the delegate's inner walks (if arms, clause bodies) build their stop
        // sets from it, not from the passed parameter. Return continuations are NOT pushed: walks
        // legitimately emit a converging return per path (idempotent), and stopping them drops it.
        IRBlock after = node.after();
        if (after == null || after.getTerminator() instanceof ReturnInstruction
                || (context.getLoopAnalysis() != null && context.getLoopAnalysis().getLoop(block) != null))
        {
            return recoverTryCatch(block, h, stopBlocks, new HashSet<>(alreadyEmitted));
        }
        Set<IRBlock> stops = new HashSet<>(stopBlocks);
        stops.add(after);
        context.pushStopBlocks(stops);
        try
        {
            return recoverTryCatch(block, h, stops, new HashSet<>(alreadyEmitted));
        }
        finally
        {
            context.popStopBlocks();
        }
    }

    /**
     * Whether {@code h} is a finally's rethrow scaffold by shape AND provenance.
     */
    private boolean isEvidencedFinally(ExceptionHandler h)
    {
        return handlerRethrows(h) && !handlerThrowsFreshException(h) && isFinallyCatchType(h)
                && (h.isCatchAll() || h.getHandlerBlock() == null || handlerHasFinallyEvidence(h.getHandlerBlock()));
    }

    @Override
    public boolean recoveredTryTerminates(Statement recovered)
    {
        return isTerminatingRecoveredTry(recovered);
    }

    @Override
    public void unrecoveredTryNode(IRBlock block)
    {
        throw retiredSchemaRecovery("try-node", block);
    }





    /**
     * Recovers a staged region whose try at {@code tryStart} carries a finally, by delegating the try and its
     * continuation to the outer-handler scaffolding.
     */
    private List<Statement> recoverFinallyStage(IRBlock startBlock, IRBlock tryStart, Set<IRBlock> stopBlocks)
    {
        List<ExceptionHandler> handlers = context.getIrMethod().getExceptionHandlers();
        List<ExceptionHandler> mergedHandlers = mergeHandlersWithSameTarget(handlers);
        ExceptionHandler outer = findOutermostHandler(tryStart, mergedHandlers);
        if (outer == null || outer.getTryStart() == null
                || outer.getTryStart().getBytecodeOffset() != tryStart.getBytecodeOffset()
                || processedTryHandlers.contains(outer)
                || processedHandlerBlocks.contains(outer.getHandlerBlock()))
        {
            return null;
        }
        // A USER finally with a nested catch of its own (a different unprocessed handler inside the range) is
        // left to the legacy walk: the outer-handler scaffolding cannot de-duplicate the inlined finally copies
        // at the CFG level when inner handlers are present, and the legacy walk's own try recovery is the
        // cleaner form for it. A SYNCHRONIZED region is the exception: its finally is a monitorexit that is
        // dropped during recovery (no copies to place), and the legacy walk silently DROPS a nested catch
        // inside a loop within the synchronized (a wait-loop's `catch (InterruptedException) {}`), producing
        // uncompilable output - so a synchronized region with a nested catch is delegated to the scaffolding,
        // which recovers the nested try/catch via recoverWithNestedHandlers.
        if (detectSynchronizedLock(outer) == null)
        {
            int outerStart = outer.getTryStart().getBytecodeOffset();
            int outerEnd = outer.getTryEnd() != null ? outer.getTryEnd().getBytecodeOffset() : Integer.MAX_VALUE;
            for (ExceptionHandler h : mergedHandlers)
            {
                if (h == outer || h.getHandlerBlock() == outer.getHandlerBlock() || sameTryRange(h, outer)
                        || h.getTryStart() == null
                        || processedTryHandlers.contains(h)
                        || processedHandlerBlocks.contains(h.getHandlerBlock()))
                {
                    continue;
                }
                int hs = h.getTryStart().getBytecodeOffset();
                if (hs >= outerStart && hs < outerEnd)
                {
                    return null;
                }
            }
        }
        List<Statement> out = new ArrayList<>();
        if (tryStart != startBlock)
        {
            Set<IRBlock> prefixStops = new HashSet<>(stopBlocks);
            prefixStops.add(tryStart);
            out.addAll(recoverRegionHandoff(startBlock, prefixStops));
        }
        Set<IRBlock> handlerBlocks = new HashSet<>();
        for (ExceptionHandler h : handlers)
        {
            if (h.getHandlerBlock() != null)
            {
                handlerBlocks.add(h.getHandlerBlock());
                collectReachableBlocks(h.getHandlerBlock(), handlerBlocks);
            }
        }
        boolean savedExtended = extendedFinallyDedup;
        extendedFinallyDedup = true;
        try
        {
            out.addAll(recoverOuterHandlerRegion(tryStart, outer, handlers, mergedHandlers, handlerBlocks));
        }
        finally
        {
            extendedFinallyDedup = savedExtended;
        }
        return out;
    }

    /**
     * Whether a store is a handler's caught-exception spill.
     */
    private boolean isExceptionSpill(StoreLocalInstruction store)
    {
        Value v = store.getValue();
        Set<IRInstruction> seen = new HashSet<>();
        int hops = 0;
        while (v instanceof SSAValue && hops++ < 4)
        {
            IRInstruction def = ((SSAValue) v).getDefinition();
            if (def == null || !seen.add(def))
            {
                // No definition, or a self-referential copy cycle: the raw incoming exception.
                return true;
            }
            if (!(def instanceof CopyInstruction))
            {
                return false;
            }
            v = ((CopyInstruction) def).getSource();
        }
        return !(v instanceof SSAValue);
    }

    private boolean finallyWritesLocal(List<ExceptionHandler> handlers)
    {
        for (ExceptionHandler h : handlers)
        {
            if (!handlerRethrows(h) || handlerThrowsFreshException(h))
            {
                continue;
            }
            List<IRInstruction> template = straightLineFinallyTemplate(h);
            if (template != null)
            {
                for (IRInstruction ins : template)
                {
                    if (ins instanceof StoreLocalInstruction)
                    {
                        return true;
                    }
                }
                continue;
            }
            // The template can be unextractable AFTER the de-duplication excised the copies; the
            // HANDLER side is never touched by excision, so scan its straight-line chain instead.
            // The caught exception's own spill (the store the rethrow reloads) is scaffolding, not a
            // finally body write.
            IRBlock hb = h.getHandlerBlock();
            int hops = 0;
            while (hb != null && hops++ < 8)
            {
                for (IRInstruction ins : hb.getInstructions())
                {
                    if (ins instanceof StoreLocalInstruction && !isExceptionSpill((StoreLocalInstruction) ins))
                    {
                        return true;
                    }
                }
                if (hb.getTerminator() instanceof SimpleInstruction
                        && ((SimpleInstruction) hb.getTerminator()).getOp() == SimpleOp.ATHROW)
                {
                    break;
                }
                IRBlock next = null;
                for (Map.Entry<IRBlock, EdgeType> e : hb.getSuccessorEdgeTypes().entrySet())
                {
                    if (e.getValue() == EdgeType.NORMAL)
                    {
                        if (next != null)
                        {
                            return true;
                        }
                        next = e.getKey();
                    }
                }
                if (next == null && !(hb.getTerminator() instanceof SimpleInstruction
                        && ((SimpleInstruction) hb.getTerminator()).getOp() == SimpleOp.ATHROW))
                {
                    return true;
                }
                hb = next;
            }
            if (hops >= 8)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Completes the node decode for a finally-protected try (with or without sibling user catches).
     */
    private TryNodeDescriptor decodeFinallyTryNode(IRBlock block, ExceptionHandler h, Set<IRBlock> consumed, DominatorTree dt)
    {
        IRMethod irMethod = context.getIrMethod();
        ExceptionHandler rethrower = null;
        List<ExceptionHandler> siblings = new ArrayList<>();
        for (ExceptionHandler eh : irMethod.getExceptionHandlers())
        {
            if (eh.getHandlerBlock() == null || eh.getTryStart() == null
                    || eh.getTryStart().getBytecodeOffset() != block.getBytecodeOffset())
            {
                continue;
            }
            siblings.add(eh);
            if (rethrower == null && handlerRethrows(eh) && !handlerThrowsFreshException(eh))
            {
                rethrower = eh;
            }
        }
        if (rethrower == null)
        {
            trace("finally-node decline block=" + block.getBytecodeOffset() + " no-rethrower");
            return null;
        }
        List<ExceptionHandler> family = new ArrayList<>();
        for (ExceptionHandler eh : irMethod.getExceptionHandlers())
        {
            if (eh.getHandlerBlock() == rethrower.getHandlerBlock())
            {
                family.add(eh);
            }
        }
        // A nested resource's fused scaffolding - javac's modern try-with-resources lays an INNER
        // rethrowing family (suppress catch fused with its close clause) inside the outer window, at
        // its own start offset. Offer those families to the de-duplication together (the partitioned
        // dedup orders inner-first and resolves chained copies) and consume their clause subtrees, or
        // the continuation scan reads the inner clause and the surviving inner copies as extra joins.
        Set<IRBlock> nestedRethrowerBlocks = new HashSet<>();
        // The widening is for the ACYCLIC fused try-with-resources shape only. A construct wrapping a
        // loop (the try-around-infinite-retry family) has its own recovery: consuming its inner
        // scaffolding here excises the loop's exit logic and the recompiled loop never terminates.
        boolean windowHasLoop = false;
        if (context.getLoopAnalysis() != null)
        {
            for (IRBlock cb : consumed)
            {
                if (context.getLoopAnalysis().isLoopHeader(cb))
                {
                    windowHasLoop = true;
                    break;
                }
            }
        }
        for (ExceptionHandler eh : windowHasLoop ? java.util.Collections.<ExceptionHandler>emptyList()
                : irMethod.getExceptionHandlers())
        {
            if (eh.getHandlerBlock() == null || eh.getHandlerBlock() == rethrower.getHandlerBlock()
                    || eh.getTryStart() == null || !consumed.contains(eh.getTryStart())
                    || !handlerRethrows(eh) || handlerThrowsFreshException(eh)
                    || isLocalSpillRethrower(eh))
            {
                continue;
            }
            // A clause's own nested close-guard also reaches the rethrow, but it is the clause's
            // scaffolding, not a resource family of its own: its handler sits inside another family
            // handler's dominated subtree.
            boolean insideClause = dt.dominates(rethrower.getHandlerBlock(), eh.getHandlerBlock());
            if (!insideClause)
            {
                for (ExceptionHandler fam : irMethod.getExceptionHandlers())
                {
                    if (fam.getHandlerBlock() != null && fam.getHandlerBlock() != eh.getHandlerBlock()
                            && fam != eh && handlerRethrows(fam) && !isLocalSpillRethrower(fam)
                            && dt.dominates(fam.getHandlerBlock(), eh.getHandlerBlock()))
                    {
                        insideClause = true;
                        break;
                    }
                }
            }
            if (insideClause)
            {
                continue;
            }
            family.add(eh);
            nestedRethrowerBlocks.add(eh.getHandlerBlock());
        }
        for (IRBlock nrb : nestedRethrowerBlocks)
        {
            consumed.add(nrb);
            for (IRBlock b : irMethod.getBlocks())
            {
                if (dt.dominates(nrb, b))
                {
                    consumed.add(b);
                }
            }
        }
        // Excising the straight-line copies up front gives the cleanest body - EXTENDED, so arithmetic
        // templates (`log += 10`) and split-handler chains are excised here too instead of leaking to the
        // statement-level folds. A finally whose copies carry control flow cannot be excised statically,
        // but the node stays decodable: the copies lie inside the widened window and are consumed with it,
        // and the delegate recovery folds them out at statement level against the structurally recovered
        // clause (try body, catch bodies, and the trailing fall-through copy alike).
        boolean savedDecodeExtended = extendedFinallyDedup;
        extendedFinallyDedup = true;
        boolean decodeDeduped;
        try
        {
            decodeDeduped = dedupStraightLineFinally(family);
        }
        finally
        {
            extendedFinallyDedup = savedDecodeExtended;
        }
        Set<IRBlock> carveFreed = Collections.emptySet();
        // With the copies EXCISED, scaffold and continuation are distinguishable: everything the
        // predecessor closure absorbed that is neither protected, a family handler's subtree, an
        // excised copy, nor a bare shell/rethrow tail is the construct's CONTINUATION - a linear
        // method reaches it only through the construct, so all-preds-inside holds for it too, and
        // leaving it consumed silently drops it (an array build between the close and the next use
        // vanished whole). Only after a successful excision: un-excised copies must stay consumed
        // for the delegate's statement-level folds.
        if (decodeDeduped)
        {
            Set<IRBlock> familySubtrees = new HashSet<>();
            for (ExceptionHandler sib : siblings)
            {
                familySubtrees.add(sib.getHandlerBlock());
            }
            familySubtrees.addAll(nestedRethrowerBlocks);
            familySubtrees.add(rethrower.getHandlerBlock());
            Set<IRBlock> candidates = new LinkedHashSet<>();
            for (IRBlock cb : consumed)
            {
                int off = cb.getBytecodeOffset();
                boolean keep = excisedFinallyCopyBlocks.contains(cb)
                        || consumedFinallyShells.contains(cb)
                        || isBareRethrowTail(cb) || isBareShellBlock(cb);
                if (!keep)
                {
                    for (ExceptionHandler fh : family)
                    {
                        if (fh.getTryStart() != null && fh.getTryEnd() != null
                                && off >= fh.getTryStart().getBytecodeOffset()
                                && off < fh.getTryEnd().getBytecodeOffset())
                        {
                            keep = true;
                            break;
                        }
                    }
                }
                if (!keep)
                {
                    for (IRBlock hb : familySubtrees)
                    {
                        if (hb != null && (cb == hb || dt.dominates(hb, cb)))
                        {
                            keep = true;
                            break;
                        }
                    }
                }
                if (!keep)
                {
                    candidates.add(cb);
                }
            }
            // A candidate is freed only when its forward flow leaves the construct: a candidate
            // whose successor is KEPT scaffolding still flows back into the model and must stay
            // consumed (freeing it puts the region on a jump into node interior). Chains free
            // together; blocked chains stay whole.
            boolean freedAny = true;
            Set<IRBlock> freed = new HashSet<>();
            while (freedAny)
            {
                freedAny = false;
                for (IRBlock cb : candidates)
                {
                    if (freed.contains(cb))
                    {
                        continue;
                    }
                    boolean flowsOut = true;
                    for (Map.Entry<IRBlock, EdgeType> e : cb.getSuccessorEdgeTypes().entrySet())
                    {
                        if (e.getValue() != EdgeType.NORMAL)
                        {
                            continue;
                        }
                        IRBlock t = resolveThroughGotoShells(e.getKey());
                        if (consumed.contains(t) && !freed.contains(t) && !candidates.contains(t))
                        {
                            flowsOut = false;
                            break;
                        }
                        if (candidates.contains(t) && !freed.contains(t))
                        {
                            flowsOut = false;
                            break;
                        }
                    }
                    if (flowsOut)
                    {
                        freed.add(cb);
                        freedAny = true;
                    }
                }
            }
            consumed.removeAll(freed);
            carveFreed = freed;
        }
        Set<IRBlock> siblingBlocks = new HashSet<>();
        for (ExceptionHandler sib : siblings)
        {
            siblingBlocks.add(sib.getHandlerBlock());
        }
        for (ExceptionHandler sib : siblings)
        {
            consumed.add(sib.getHandlerBlock());
            for (IRBlock b : irMethod.getBlocks())
            {
                if (dt.dominates(sib.getHandlerBlock(), b))
                {
                    consumed.add(b);
                }
            }
        }
        for (ExceptionHandler eh : irMethod.getExceptionHandlers())
        {
            if (eh.getHandlerBlock() == null || siblingBlocks.contains(eh.getHandlerBlock()))
            {
                continue;
            }
            if (consumed.contains(eh.getHandlerBlock()))
            {
                for (IRBlock b : irMethod.getBlocks())
                {
                    if (dt.dominates(eh.getHandlerBlock(), b))
                    {
                        consumed.add(b);
                    }
                }
            }
        }
        IRBlock after = null;
        boolean allExitsTerminal = false;
        Set<IRBlock> exitShells = new HashSet<>();
        boolean rescan = true;
        scan:
        while (rescan)
        {
        rescan = false;
        for (IRBlock cb : new ArrayList<>(consumed))
        {
            if (cb != rethrower.getHandlerBlock() && dt.dominates(rethrower.getHandlerBlock(), cb))
            {
                continue;
            }
            for (Map.Entry<IRBlock, EdgeType> e : cb.getSuccessorEdgeTypes().entrySet())
            {
                IRBlock succ = e.getKey();
                if (e.getValue() != EdgeType.NORMAL || consumed.contains(succ) || dt.dominates(succ, cb))
                {
                    continue;
                }
                // The try's fall-through often exits into a bare goto shell in front of the join the
                // handler paths reach directly; both are the same continuation once resolved. The shells
                // are collected into the node - left out, a shell on the exit path dangles outside both
                // the node and the region (e.g. as a loop's back-edge source) and breaks the model.
                succ = resolveThroughGotoShells(succ, exitShells);
                if (consumed.contains(succ))
                {
                    continue;
                }
                if (after != null && after != succ)
                {
                    // Two distinct continuations: when EVERY candidate is a terminal return tail (javac's
                    // per-exit inlined finally copy carrying that exit's own return), the construct has no
                    // external join at all - the delegate recovery owns those tails, re-attaching each
                    // return behind the de-duplicated finally. Any non-terminal rival is a genuine second
                    // join the node model cannot express.
                    if (delegateOwnsExitTail(after, consumed, rethrower)
                            && delegateOwnsExitTail(succ, consumed, rethrower))
                    {
                        after = null;
                        allExitsTerminal = true;
                        break;
                    }
                    // Exactly one candidate is delegate-owned AND the other is a genuine CODE
                    // continuation (not a return tail the delegate should also own): the code
                    // continuation is the construct's one join. A terminal rival keeps the decline -
                    // accepting it hands the delegate a boundary it re-attaches on the wrong path.
                    boolean acyclicContext = context.getLoopAnalysis() == null
                            || context.getLoopAnalysis().getLoop(block) == null;
                    if (acyclicContext && delegateOwnsExitTail(succ, consumed, rethrower)
                            && !(after.getTerminator() instanceof ReturnInstruction))
                    {
                        continue;
                    }
                    if (acyclicContext && delegateOwnsExitTail(after, consumed, rethrower)
                            && !(succ.getTerminator() instanceof ReturnInstruction))
                    {
                        after = succ;
                        continue;
                    }
                    // A rival that is a SHARED terminal (reached from outside the window too, so not
                    // delegate-owned) is the construct's join even when it is a return: the offering
                    // region re-emits a converging terminal once per reaching path, and the delegate's
                    // own absorption of the tail covers only its in-construct paths.
                    if (acyclicContext && delegateOwnsExitTail(after, consumed, rethrower)
                            && !delegateOwnsExitTail(succ, consumed, rethrower))
                    {
                        after = succ;
                        continue;
                    }
                    if (acyclicContext && delegateOwnsExitTail(succ, consumed, rethrower)
                            && !delegateOwnsExitTail(after, consumed, rethrower))
                    {
                        continue;
                    }
                    // A rival that is one of the offering REGION'S OWN STOPS is the region's boundary,
                    // not a second join of the construct: the delegate's walks stop there (the engine
                    // pushes the boundary onto the context stack), and the region model reaches the
                    // boundary through the kept join's continuation. Keep the in-region join.
                    if (decodeRegionStops.contains(succ) && !decodeRegionStops.contains(after))
                    {
                        continue;
                    }
                    if (decodeRegionStops.contains(after) && !decodeRegionStops.contains(succ))
                    {
                        after = succ;
                        continue;
                    }
                    // A try INSIDE a loop may exit both to its in-loop continuation and - via a break in
                    // the try body - out of the loop. The in-loop continuation is the node's join; the
                    // loop model owns the break edge (it stays visible on the consumed blocks' CFG edges,
                    // where the loop's break-target scan finds it and settles it through guarded-close
                    // intermediates, and the delegate emits the jump).
                    if (!acyclicContext)
                    {
                        LoopAnalysis.Loop encl = context.getLoopAnalysis().getLoop(block);
                        boolean afterIn = encl.getBlocks().contains(after);
                        boolean succIn = encl.getBlocks().contains(succ);
                        if (afterIn && !succIn)
                        {
                            continue;
                        }
                        if (succIn && !afterIn)
                        {
                            after = succ;
                            continue;
                        }
                    }
                    if (!carveFreed.isEmpty())
                    {
                        // The carve exposed a second join this model cannot take; the pre-carve
                        // consumed set had the construct whole. Restore it and rescan once.
                        trace("finally-node carve-retry block=" + block.getBytecodeOffset());
                        consumed.addAll(carveFreed);
                        carveFreed = Collections.emptySet();
                        after = null;
                        exitShells.clear();
                        continue scan;
                    }
                    trace("finally-node decline block=" + block.getBytecodeOffset() + " second-join="
                            + succ.getBytecodeOffset() + " first=" + after.getBytecodeOffset());
                    return null;
                }
                after = succ;
            }
            if (allExitsTerminal)
            {
                break;
            }
        }
        }
        if (after == block)
        {
            return null;
        }
        consumed.addAll(exitShells);
        trace("finally-node OK block=" + block.getBytecodeOffset()
                + " after=" + (after == null ? "terminal" : after.getBytecodeOffset())
                + " consumed=" + consumed.size() + " hasLoop=" + windowHasLoop);
        return new TryNodeDescriptor(h, consumed, after);
    }

    /**
     * Resolves a continuation candidate through bare goto shells.
     */
    private IRBlock resolveThroughGotoShells(IRBlock b)
    {
        return resolveThroughGotoShells(b, null);
    }

    private IRBlock resolveThroughGotoShells(IRBlock b, Set<IRBlock> traversed)
    {
        int hops = 0;
        while (b != null && b.getTerminator() instanceof SimpleInstruction
                && ((SimpleInstruction) b.getTerminator()).getOp() == SimpleOp.GOTO
                && b.getSuccessors().size() == 1
                && (b.getInstructions().isEmpty()
                    || (b.getInstructions().size() == 1
                        && b.getInstructions().get(0) == b.getTerminator()))
                && hops++ < 8)
        {
            if (traversed != null)
            {
                traversed.add(b);
            }
            b = b.getSuccessors().iterator().next();
        }
        return b;
    }

    /**
     * Whether an exit chain from the construct is a per-exit tail the DELEGATE recovery may own.
     */
    private boolean delegateOwnsExitTail(IRBlock exit, Set<IRBlock> consumed, ExceptionHandler rethrower)
    {
        // A tail crossing an active outer stop boundary belongs to the ENCLOSING recovery: the node's
        // delegate stops there and cannot emit the rest, whatever the tail's shape.
        if (exitChainCrossesContextStop(exit))
        {
            return false;
        }
        // The exclusive-reachability claim holds only for a DE-DUPLICATED finally, whose delegate
        // re-attaches each per-exit tail behind the extracted clause. A construct whose copies were
        // never excised (a synchronized region's release scaffold) has no such re-attachment pass:
        // claiming its tail hides the real continuation from the region model and drops the code.
        // A bare or copy-carrying RETURN tail stays claimable - re-emitting a return is idempotent.
        if (finallyDeduped.contains(rethrower) && nodeOwnsExitTail(exit, consumed))
        {
            return true;
        }
        return isBareReturnTail(exit) || isFinallyCopyReturnTail(exit, rethrower);
    }

    /**
     * An exit chain that is an inlined finally COPY feeding a return.
     */
    private boolean isFinallyCopyReturnTail(IRBlock exit, ExceptionHandler rethrower)
    {
        IRBlock hb = rethrower.getHandlerBlock();
        DominatorTree dt = context.getDominatorTree();
        if (hb == null || dt == null)
        {
            return false;
        }
        Set<String> templateCalls = new HashSet<>();
        for (IRBlock b : context.getIrMethod().getBlocks())
        {
            if (b != hb && !dt.dominates(hb, b))
            {
                continue;
            }
            for (IRInstruction ins : b.getInstructions())
            {
                if (ins instanceof InvokeInstruction)
                {
                    InvokeInstruction iv = (InvokeInstruction) ins;
                    templateCalls.add(iv.getOwner() + "." + iv.getName());
                }
            }
        }
        Deque<IRBlock> work = new ArrayDeque<>();
        Set<IRBlock> seen = new HashSet<>();
        work.add(exit);
        int budget = 24;
        while (!work.isEmpty())
        {
            IRBlock b = work.poll();
            if (!seen.add(b))
            {
                continue;
            }
            if (budget-- <= 0)
            {
                return false;
            }
            for (IRInstruction ins : b.getInstructions())
            {
                if (ins.isTerminator() || ins instanceof CopyInstruction
                        || ins instanceof LoadLocalInstruction || ins instanceof StoreLocalInstruction
                        || ins instanceof ConstantInstruction)
                {
                    continue;
                }
                if (ins instanceof InvokeInstruction && templateCalls.contains(
                        ((InvokeInstruction) ins).getOwner() + "." + ((InvokeInstruction) ins).getName()))
                        {
                    continue;
                }
                return false;
            }
            if (b.getTerminator() instanceof ReturnInstruction)
            {
                continue;
            }
            boolean any = false;
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    work.add(e.getKey());
                    any = true;
                }
            }
            if (!any)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * A straight terminal tail.
     */
    private boolean isTerminalTail(IRBlock b)
    {
        int hops = 0;
        while (b != null && hops++ < 8)
        {
            if (b.getTerminator() instanceof ReturnInstruction)
            {
                return true;
            }
            if (b.getTerminator() instanceof SimpleInstruction
                    && ((SimpleInstruction) b.getTerminator()).getOp() == SimpleOp.ATHROW)
            {
                return true;
            }
            IRBlock excised = excisedCopyExits.get(b);
            if (excised != null)
            {
                b = excised;
                continue;
            }
            if (b.getTerminator() instanceof BranchInstruction || b.getTerminator() instanceof SwitchInstruction)
            {
                return false;
            }
            IRBlock next = null;
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    if (next != null)
                    {
                        return false;
                    }
                    next = e.getKey();
                }
            }
            b = next;
        }
        return false;
    }

    private boolean isBareReturnTail(IRBlock b)
    {
        int hops = 0;
        while (b != null && hops++ < 8)
        {
            IRBlock excised = excisedCopyExits.get(b);
            if (excised == null)
            {
                for (IRInstruction ins : b.getInstructions())
                {
                    if (!ins.isTerminator() && !(ins instanceof CopyInstruction)
                            && !(ins instanceof LoadLocalInstruction)
                            && !(ins instanceof StoreLocalInstruction)
                            && !(ins instanceof ConstantInstruction))
                    {
                        return false;
                    }
                }
            }
            if (b.getTerminator() instanceof ReturnInstruction)
            {
                return true;
            }
            if (excised != null)
            {
                b = excised;
                continue;
            }
            if (b.getTerminator() instanceof BranchInstruction)
            {
                return false;
            }
            IRBlock next = null;
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    if (next != null)
                    {
                        return false;
                    }
                    next = e.getKey();
                }
            }
            b = next;
        }
        return false;
    }

    /**
     * The offering region's stop set, captured at decode entry for the tail-ownership checks.
     */
    private Set<IRBlock> decodeRegionStops = Collections.emptySet();

    /**
     * Whether the successor closure from {@code exit} (bounded) touches a boundary the node's delegate
     * must stop at - an active context stop or the offering region's own stop set.
     */
    private boolean exitChainCrossesContextStop(IRBlock exit)
    {
        Set<IRBlock> contextStops = new HashSet<>(context.getAllStopBlocks());
        contextStops.addAll(decodeRegionStops);
        if (contextStops.isEmpty())
        {
            return false;
        }
        Deque<IRBlock> work = new ArrayDeque<>();
        Set<IRBlock> seen = new HashSet<>();
        work.add(exit);
        int budget = 64;
        while (!work.isEmpty() && budget-- > 0)
        {
            IRBlock b = work.poll();
            if (!seen.add(b))
            {
                continue;
            }
            if (contextStops.contains(b))
            {
                return true;
            }
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    work.add(e.getKey());
                }
            }
        }
        return false;
    }

    private boolean nodeOwnsExitTail(IRBlock exit, Set<IRBlock> consumed)
    {
        Deque<IRBlock> work = new ArrayDeque<>();
        Set<IRBlock> chain = new HashSet<>();
        work.add(exit);
        int budget = 64;
        while (!work.isEmpty())
        {
            IRBlock b = work.poll();
            if (!chain.add(b))
            {
                continue;
            }
            if (budget-- <= 0)
            {
                return false;
            }
            for (IRBlock p : b.getPredecessors())
            {
                if (!consumed.contains(p) && !chain.contains(p))
                {
                    return false;
                }
            }
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                if (e.getValue() == EdgeType.NORMAL)
                {
                    work.add(e.getKey());
                }
            }
        }
        return true;
    }

    private boolean tryHasFinallyHandler(IRBlock tryStart)
    {
        int startOff = tryStart.getBytecodeOffset();
        for (ExceptionHandler h : context.getIrMethod().getExceptionHandlers())
        {
            // A typed wrap-rethrow user clause (`catch (FNF e) { throw wrap(e); }`) must not read as
            // finally scaffolding: the slot-tracing freshness check sees the wrap even when a layout
            // parks the fresh exception in a slot before its throw. (Gating on the declared catch
            // type instead broke real finally scaffolding whose merged handler carries a user type.)
            if (h.getTryStart() != null && h.getTryStart().getBytecodeOffset() == startOff
                    && handlerRethrows(h) && !throwsFreshExceptionThroughSlots(h))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the handler's declared type is one javac (catch-any) or the recompiler ({@code Throwable}) uses for
     * a finally's synthetic rethrow handler.
     */
    private boolean isFinallyCatchType(ExceptionHandler h)
    {
        return h.isCatchAll() || h.getCatchType() == null
                || "java/lang/Throwable".equals(h.getCatchType().getInternalName());
    }

    /**
     * Whether the block sits inside an enclosing loop's body - i.e. some loop containing it has a DIFFERENT header
     * block.
     */
    private boolean isInsideLoopBody(IRBlock block)
    {
        if (block == null || context.getLoopAnalysis() == null)
        {
            return false;
        }
        LoopAnalysis.Loop enclosing = context.getLoopAnalysis().getLoop(block);
        while (enclosing != null)
        {
            if (enclosing.getHeader() != block)
            {
                return true;
            }
            enclosing = enclosing.getParent();
        }
        return false;
    }

    /**
     * An accepted engine offer: the structured statements and where the walk resumes (null = nowhere).
     */
    private static final class OfferResult
    {
        final List<Statement> statements;
        final IRBlock continuation;
        OfferResult(List<Statement> statements, IRBlock continuation)
        {
            this.statements = statements;
            this.continuation = continuation;
        }
    }

    /**
     * Offers a region with no analyzer-known bound.
     */
    private OfferResult offerTerminalRegion(IRBlock entry, Set<IRBlock> offeredStops)
    {
        releaseInternalStops(entry, offeredStops, null);
        Set<IRBlock> exits = rcsStructurer.probeRegionExits(entry, offeredStops, true);
        Set<IRBlock> flaggedTails = new HashSet<>();
        if (exits != null && exits.size() > 1)
        {
            exits = retryOfferReleases(entry, offeredStops, null, exits, flaggedTails);
        }
        if (exits == null || exits.size() > 1)
        {
            if (System.getProperty("yabr.trace.offer") != null)
            {
                System.err.println("[OFFER-T] entry=" + entry.getBytecodeOffset()
                        + " refused exits=" + (exits == null ? "probe-decline"
                            : exits.stream().map(x -> String.valueOf(x.getBytecodeOffset()))
                                .sorted().collect(java.util.stream.Collectors.joining(","))));
            }
            if (!flaggedTails.isEmpty())
            {
                rcsStructurer.setBoundaryDuplicableTails(null);
            }
            return null;
        }
        IRBlock continuation = exits.isEmpty() ? null : exits.iterator().next();
        List<Statement> out;
        try
        {
            out = rcsStructurer.tryStructureRegion(entry, offeredStops, true);
        }
        finally
        {
            if (!flaggedTails.isEmpty())
            {
                rcsStructurer.setBoundaryDuplicableTails(null);
            }
        }
        if (System.getProperty("yabr.trace.offer") != null)
        {
            System.err.println("[OFFER-T] entry=" + entry.getBytecodeOffset()
                    + " cont=" + (continuation == null ? "null" : continuation.getBytecodeOffset())
                    + " stops=" + offeredStops.stream().map(x -> String.valueOf(x.getBytecodeOffset()))
                        .sorted().collect(java.util.stream.Collectors.joining(","))
                    + " ok=" + (out != null)
                    + " from=" + java.util.Arrays.stream(new Throwable().getStackTrace())
                        .skip(1).limit(3).map(StackTraceElement::getLineNumber)
                        .map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        }
        return out == null ? null : new OfferResult(out, continuation);
    }

    /**
     * Releases stops the offered construct owns internally.
     */
    private void releaseInternalStops(IRBlock entry, Set<IRBlock> offeredStops, IRBlock bound)
    {
        DominatorTree dt = context.getDominatorTree();
        if (dt == null)
        {
            return;
        }
        offeredStops.removeIf(stop -> stop != entry && stop != bound
                && dt.dominates(entry, stop)
                && (findUnprocessedHandlerStartingAt(stop) != null
                    || isBareReturnTail(stop)
                    || startsClaimedHandlerRange(stop)
                    || retiredTryBoundaries.contains(stop)
                    || (bound == null && excisedFinallyCopyBlocks.contains(stop))));
    }

    /**
     * Whether {@code b} starts a protected range of a CLAIMED handler still being recovered (claimed but its
     * clause not yet emitted).
     */
    private boolean startsClaimedHandlerRange(IRBlock b)
    {
        List<ExceptionHandler> handlers = context.getIrMethod().getExceptionHandlers();
        if (handlers == null)
        {
            return false;
        }
        for (ExceptionHandler h : handlers)
        {
            IRBlock ts = h.getTryStart();
            boolean startsHere = ts == b
                    || (ts != null && ts.getBytecodeOffset() == b.getBytecodeOffset());
            if (startsHere && h.getHandlerBlock() != null
                    && processedHandlerBlocks.contains(h.getHandlerBlock())
                    && !context.isProcessed(h.getHandlerBlock()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the construct spanned from {@code entry} to {@code merge} can hold a surviving inlined finally copy.
     */
    private boolean regionIsCopyFree(IRBlock entry, IRBlock merge, Set<IRBlock> stopBlocks)
    {
        if (isTerminalBlockShape(merge))
        {
            return false;
        }
        Deque<IRBlock> work = new ArrayDeque<>();
        Set<IRBlock> seen = new HashSet<>();
        work.add(entry);
        seen.add(entry);
        while (!work.isEmpty())
        {
            IRBlock b = work.poll();
            if (isTerminalBlockShape(b))
            {
                return false;
            }
            for (Map.Entry<IRBlock, EdgeType> e : b.getSuccessorEdgeTypes().entrySet())
            {
                IRBlock succ = e.getKey();
                if (e.getValue() != EdgeType.NORMAL || succ == merge || stopBlocks.contains(succ) || !seen.add(succ))
                {
                    continue;
                }
                work.add(succ);
            }
        }
        return true;
    }

    private boolean isTerminalBlockShape(IRBlock b)
    {
        return b.getTerminator() instanceof ReturnInstruction
                || (b.getTerminator() instanceof SimpleInstruction
                    && ((SimpleInstruction) b.getTerminator()).getOp() == SimpleOp.ATHROW);
    }

    private List<Statement> offerRegionToEngine(IRBlock entry, Set<IRBlock> offeredStops, IRBlock bound)
    {
        return offerRegionToEngine(entry, offeredStops, bound, true);
    }

    private List<Statement> offerRegionToEngine(IRBlock entry, Set<IRBlock> offeredStops, IRBlock bound, boolean allowTailRelease)
    {
        // A stop that is an unprocessed try's start STRICTLY inside the offered construct is the walk's
        // own hand-off boundary, not the construct's: the engine models that try as an opaque node, so
        // the offer spans it. Without this, a loop whose body opens a try is cut at its first block.
        // A stop that is a BARE terminator strictly inside the construct is released too: a bare return
        // is idempotent (the walks already re-emit a converging return per path), so the region may
        // absorb it as its own exit arm's terminal rather than refusing on a second boundary.
        // A bound that is itself a bare goto pad fronts the construct's real continuation: resolve it,
        // so the pad is absorbed in-region and the offer is judged against the landing.
        if (bound != null)
        {
            IRBlock landing = resolveThroughGotoShells(bound);
            // Resolve only a pad the region owns exclusively (the entry dominates it). A shell with
            // predecessors outside the offered construct is a SHARED convergence - the walk's real
            // continuation - and swapping it for its landing leaves the region no reachable exit.
            DominatorTree padDt = context.getDominatorTree();
            if (landing != bound && padDt != null && padDt.dominates(entry, bound))
            {
                offeredStops.remove(bound);
                offeredStops.add(landing);
                bound = landing;
            }
        }
        releaseInternalStops(entry, offeredStops, bound);
        // A BOUNDED offer must actually flow into its bound: the caller resumes there, so a region
        // that exits nowhere (every path terminal) would have absorbed code the caller re-emits after
        // the bound - the construct duplicates. Only an UNBOUNDED (terminal) offer accepts empty exits.
        Set<IRBlock> exits = rcsStructurer.probeRegionExits(entry, offeredStops, true);
        boolean exitsOk = boundSatisfied(exits, bound);
        // An enclosing loop's body tail continues into its own header: the bound is reached through
        // a back edge, which the probe never counts as an exit. The offer is sound - the region ends
        // in the loop's continue and the caller resumes at the already-processed header.
        if (!exitsOk && bound != null && exits != null && exits.isEmpty()
                && rcsStructurer.lastRegionContinuesInto(bound))
        {
            exitsOk = true;
        }
        // A bounded construct may ALSO fall out through the end boundary of a handler range still
        // being recovered (a split-range desugar lays a terminal arm across it) and on into a bare
        // return: both are the construct's own terminal tail, not rival continuations. Release such
        // extra exits and re-probe; the retry never touches an offer that already met its contract.
        Set<IRBlock> flaggedTails = new HashSet<>();
        if (allowTailRelease && !exitsOk && bound != null && exits != null && exits.contains(bound))
        {
            exits = retryOfferReleases(entry, offeredStops, bound, exits, flaggedTails);
            exitsOk = boundSatisfied(exits, bound);
        }
        List<Statement> out;
        try
        {
            out = exitsOk ? rcsStructurer.tryStructureRegion(entry, offeredStops, true) : null;
        }
        finally
        {
            if (!flaggedTails.isEmpty())
            {
                rcsStructurer.setBoundaryDuplicableTails(null);
            }
        }
        if (System.getProperty("yabr.trace.offer") != null)
        {
            System.err.println("[OFFER] entry=" + entry.getBytecodeOffset()
                    + " bound=" + (bound == null ? "null" : bound.getBytecodeOffset())
                    + " stops=" + offeredStops.stream().map(x -> String.valueOf(x.getBytecodeOffset()))
                        .sorted().collect(java.util.stream.Collectors.joining(","))
                    + " exits=" + (exits == null ? "probe-decline"
                        : exits.stream().map(x -> String.valueOf(x.getBytecodeOffset()))
                            .collect(java.util.stream.Collectors.joining(",")))
                    + " ok=" + (out != null));
        }
        return out;
    }

    /**
     * Whether a probed exit set satisfies the offer's bound contract.
     */
    private boolean boundSatisfied(Set<IRBlock> exits, IRBlock bound)
    {
        if (exits == null)
        {
            return false;
        }
        if (bound == null)
        {
            return exits.isEmpty();
        }
        if (exits.size() != 1)
        {
            return false;
        }
        IRBlock only = exits.iterator().next();
        return only == bound || resolveThroughGotoShells(only) == bound;
    }

    /**
     * The failed-probe retry shared by the bounded and terminal offers.
     */
    private Set<IRBlock> retryOfferReleases(IRBlock entry, Set<IRBlock> offeredStops, IRBlock bound, Set<IRBlock> exits, Set<IRBlock> flaggedTails)
    {
        DominatorTree dt = context.getDominatorTree();
        Set<IRBlock> released = new HashSet<>();
        for (int round = 0; dt != null && round < 8; round++)
        {
            Set<IRBlock> extras = new HashSet<>(exits);
            if (bound != null)
            {
                extras.remove(bound);
            }
            extras.removeAll(flaggedTails);
            boolean releasable = !extras.isEmpty() || !flaggedTails.isEmpty();
            Set<IRBlock> toFlag = new HashSet<>();
            for (IRBlock extra : extras)
            {
                boolean insideReleasedTail = false;
                for (IRBlock r : released)
                {
                    if (dt.dominates(r, extra))
                    {
                        insideReleasedTail = true;
                        break;
                    }
                }
                if (insideReleasedTail
                        || (endsClaimedHandlerRange(extra) && dt.dominates(entry, extra))
                        || (followsClaimedHandlerRange(extra) && dt.dominates(entry, extra))
                        || (isTerminalTail(extra) && dt.dominates(entry, extra)))
                {
                    continue;
                }
                // A shared terminal tail the entry does NOT dominate cannot be absorbed into the
                // region (multi-entry); the engine inlines it once at the region's convergence
                // instead, while it stays a stop for everyone else.
                if (isTerminalTail(extra))
                {
                    toFlag.add(extra);
                    continue;
                }
                releasable = false;
                break;
            }
            if (!releasable || extras.isEmpty())
            {
                break;
            }
            extras.removeAll(toFlag);
            flaggedTails.addAll(toFlag);
            released.addAll(extras);
            offeredStops.removeAll(extras);
            offeredStops.removeIf(stop -> stop != bound && !flaggedTails.contains(stop)
                    && released.stream().anyMatch(r -> dt.dominates(r, stop)));
            rcsStructurer.setBoundaryDuplicableTails(flaggedTails);
            exits = rcsStructurer.probeRegionExits(entry, offeredStops, true);
            if (exits == null || (bound != null && !exits.contains(bound)) || exits.size() <= 1)
            {
                return exits;
            }
        }
        return exits;
    }

    /**
     * Whether {@code b} sits at the exclusive end offset of a CLAIMED handler's protected range
     * (claimed but its clause not yet emitted) - the in-progress construct's own boundary, which a
     * terminal arm inside the body may legitimately cross on its way out.
     */
    private boolean endsClaimedHandlerRange(IRBlock b)
    {
        List<ExceptionHandler> handlers = context.getIrMethod().getExceptionHandlers();
        if (handlers == null)
        {
            return false;
        }
        for (ExceptionHandler h : handlers)
        {
            IRBlock te = h.getTryEnd();
            boolean endsHere = te == b
                    || (te != null && te.getBytecodeOffset() == b.getBytecodeOffset());
            if (endsHere && h.getHandlerBlock() != null
                    && processedHandlerBlocks.contains(h.getHandlerBlock())
                    && !context.isProcessed(h.getHandlerBlock()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code b} directly follows a claimed handler's protected range.
     */
    private boolean followsClaimedHandlerRange(IRBlock b)
    {
        List<ExceptionHandler> handlers = context.getIrMethod().getExceptionHandlers();
        if (handlers == null)
        {
            return false;
        }
        for (ExceptionHandler h : handlers)
        {
            IRBlock te = h.getTryEnd();
            if (te != null && h.getHandlerBlock() != null
                    && processedHandlerBlocks.contains(h.getHandlerBlock())
                    && !context.isProcessed(h.getHandlerBlock())
                    && te.getSuccessors().contains(b))
            {
                return true;
            }
        }
        return false;
    }

    private ExceptionHandler findUnprocessedHandlerStartingAt(IRBlock block)
    {
        IRMethod irMethod = context.getIrMethod();
        List<ExceptionHandler> handlers = irMethod.getExceptionHandlers();
        if (handlers == null || handlers.isEmpty())
        {
            return null;
        }
        // The widest UNPROCESSED handler at this block, not the widest handler filtered afterward. When a
        // wider handler is already recovered (a split outer catch range whose second piece begins at this
        // same block as a nested try) picking the widest-then-null misses the genuinely nested handler and
        // drops it; the widest still-open handler is the one to recover here.
        ExceptionHandler best = null;
        int bestEnd = Integer.MIN_VALUE;
        for (ExceptionHandler handler : handlers)
        {
            IRBlock tryStart = handler.getTryStart();
            boolean startsHere = tryStart == block
                    || (tryStart != null && tryStart.getBytecodeOffset() == block.getBytecodeOffset());
            if (!startsHere || processedTryHandlers.contains(handler)
                    || processedHandlerBlocks.contains(handler.getHandlerBlock()))
            {
                continue;
            }
            int end = handler.getTryEnd() != null
                    ? handler.getTryEnd().getBytecodeOffset() : Integer.MAX_VALUE;
            if (best == null || end > bestEnd)
            {
                best = handler;
                bestEnd = end;
            }
        }
        return best;
    }

    /**
     * Whether {@code -Dyabr.trace} route/decode diagnostics are enabled (dormant otherwise).
     */
    private static final boolean TRACE = System.getProperty("yabr.trace") != null;

    /**
     * Emits a route/decode diagnostic when {@code -Dyabr.trace} is set.
     */
    private static void trace(String msg)
    {
        if (TRACE)
        {
            System.err.println("[yabr] " + msg);
        }
    }




    @Override
    public List<Statement> recoverSimpleBlock(IRBlock block)
    {
        List<Statement> statements = new ArrayList<>();

        for (IRInstruction instr : block.getInstructions())
        {
            if (context.shouldSkipInstruction(instr))
            {
                continue;
            }
            Statement stmt = recoverInstruction(instr);
            if (stmt != null)
            {
                    statements.add(stmt);
            }
        }

        return statements;
    }

    /**
     * Recovers a statement for an instruction and stamps it with the instruction's bytecode-offset
     * provenance, so decompiled output can be mapped back to bytecode positions.
     */
    private Statement recoverInstruction(IRInstruction instr)
    {
        Statement stmt = recoverInstruction0(instr);
        if (stmt != null && instr.getBytecodeOffset() >= 0 && !stmt.getLocation().hasOffset())
        {
            stmt.setLocation(SourceLocation.fromOffset(instr.getBytecodeOffset()));
        }
        return stmt;
    }

    private void stamp(Statement stmt, IRInstruction instr)
    {
        if (stmt != null && instr != null && instr.getBytecodeOffset() >= 0 && !stmt.getLocation().hasOffset())
        {
            stmt.setLocation(SourceLocation.fromOffset(instr.getBytecodeOffset()));
        }
    }


    /**
     * Stamps a wrapper statement (e.g. try/catch) from the first stamped statement in its body.
     */
    private void stampFromBody(Statement stmt, BlockStmt body)
    {
        if (stmt == null || body == null || stmt.getLocation().hasOffset())
        {
            return;
        }
        for (Statement child : body.getStatements())
        {
            if (child.getLocation() != null && child.getLocation().hasOffset())
            {
                stmt.setLocation(child.getLocation());
                return;
            }
        }
    }

    /**
     * True when a field-load value is read again after the same field is reassigned before that use.
     */
    private boolean fieldLoadClobberedBeforeUse(FieldAccessInstruction load, SSAValue result)
    {
        if (result == null)
        {
            return false;
        }
        IRBlock block = load.getBlock();
        if (block == null || !blockHasFieldStore(block))
        {
            return false;
        }
        Set<IRInstruction> uses = null;
        boolean seenLoad = false;
        boolean clobbered = false;
        for (IRInstruction between : block.getInstructions())
        {
            if (!seenLoad)
            {
                seenLoad = between == load;
                continue;
            }
            if (between instanceof FieldAccessInstruction)
            {
                FieldAccessInstruction store = (FieldAccessInstruction) between;
                if (store.isStore()
                        && store.isStatic() == load.isStatic()
                        && load.getName().equals(store.getName())
                        && load.getOwner().equals(store.getOwner())
                        && load.getObjectRef() == store.getObjectRef())
                {
                    clobbered = true;
                }
            }
            if (clobbered)
            {
                if (uses == null)
                {
                    uses = new HashSet<>(result.getUses());
                }
                if (uses.contains(between))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a block writes any field, cached so store-free blocks skip the clobber scan in O(1).
     */
    private boolean blockHasFieldStore(IRBlock block)
    {
        Boolean cached = blockHasFieldStoreCache.get(block);
        if (cached != null)
        {
            return cached;
        }
        boolean has = false;
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof FieldAccessInstruction && ((FieldAccessInstruction) instr).isStore())
            {
                has = true;
                break;
            }
        }
        blockHasFieldStoreCache.put(block, has);
        return has;
    }

    private final java.util.Map<IRBlock, Boolean> blockHasFieldStoreCache = new java.util.IdentityHashMap<>();

    /**
     * Whether {@code name} is one of the per-value temporaries this recovery mints for a value with no variable of
     * its own ({@code v} followed by an id) - so a store may take its slot's name over it.
     */
    private boolean isPerValueTemporaryName(String name)
    {
        if (name.length() < 2 || name.charAt(0) != 'v')
        {
            return false;
        }
        for (int i = 1; i < name.length(); i++)
        {
            if (!Character.isDigit(name.charAt(i)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Recovers the store consuming {@code result} at the CALL's own position.
     */
    private Statement storeCarriedCallInPlace(InvokeInstruction invoke, SSAValue result)
    {
        StoreLocalInstruction store = null;
        for (IRInstruction use : result.getUses())
        {
            if (use instanceof StoreLocalInstruction)
            {
                if (store != null)
                {
                    return null;
                }
                store = (StoreLocalInstruction) use;
            }
            else if (!(use instanceof PhiInstruction))
            {
                // A phi beside the store is the join of the stored slot - the text renders once, at
                // the store. Any other extra use reads the value elsewhere and pins the default path.
                return null;
            }
        }
        if (store == null)
        {
            return null;
        }
        IRBlock block = invoke.getBlock();
        if (block == null || store.getBlock() != block || !exprRecoverer.renderingAtUseCrossesEffects(invoke, store))
        {
            return null;
        }
        String targetName = partitionName(store);
        if (targetName == null || !context.getExpressionContext().isDeclared(targetName))
        {
            return null;
        }
        // Every merge of the value must render under the same name, or an arm-end edge copy would
        // still read it and re-print the call there.
        for (IRInstruction use : result.getUses())
        {
            if (use instanceof PhiInstruction && use.getResult() != null
                    && !targetName.equals(context.getExpressionContext().getVariableName(use.getResult())))
            {
                return null;
            }
        }
        List<IRInstruction> instrs = block.getInstructions();
        int from = instrs.indexOf(invoke);
        int to = instrs.indexOf(store);
        if (from < 0 || to <= from)
        {
            return null;
        }
        for (int i = from + 1; i < to; i++)
        {
            IRInstruction between = instrs.get(i);
            if (between instanceof LoadLocalInstruction
                    && ((LoadLocalInstruction) between).getLocalIndex() == store.getLocalIndex())
            {
                return null;
            }
            if (between instanceof StoreLocalInstruction
                    && ((StoreLocalInstruction) between).getLocalIndex() == store.getLocalIndex())
            {
                return null;
            }
            for (Value op : between.getOperands())
            {
                if (op instanceof SSAValue
                        && targetName.equals(context.getExpressionContext().getVariableName((SSAValue) op)))
                {
                    return null;
                }
            }
        }
        SourceType type = getLocalSlotUnifiedType(targetName);
        if (type == null)
        {
            type = typeRecoverer.recoverType(result);
        }
        Expression rhs = exprRecoverer.recover(invoke);
        // Binding the result to the variable's own name makes the arm-end phi edge copy an identity
        // (`node = node`), which the copy lowering already skips - the assignment lives here instead.
        context.getExpressionContext().markMaterialized(result);
        context.getExpressionContext().setVariableName(result, targetName);
        earlyRecoveredStores.add(store);
        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, new VarRefExpr(targetName, type, result), rhs, type));
    }

    private Statement materializeClobberedLoad(SSAValue result, Expression value)
    {
        // Prefer names that are stable across bytecode layouts: the captured FIELD's own simple
        // name is derived from the expression itself, identical whichever layout the method was
        // compiled from; the partition's store name and the id-derived fallback both shift with the
        // layout (the partition bumps around reserved names, ids renumber), renaming the temp on
        // every round trip.
        if (value instanceof FieldAccessExpr)
        {
            String fieldName = ((FieldAccessExpr) value).getFieldName();
            if (fieldName != null && !fieldName.isEmpty())
            {
                Statement s = materializeIntoTemporary(result, value, fieldName);
                if (s != null)
                {
                    return s;
                }
            }
        }
        for (IRInstruction use : result.getUses())
        {
            if (use instanceof StoreLocalInstruction)
            {
                String stored = partitionName(use);
                if (stored != null && !context.getExpressionContext().isDeclared(stored))
                {
                    Statement s = materializeIntoTemporary(result, value, stored);
                    if (s != null)
                    {
                        return s;
                    }
                }
            }
        }
        // Not "v" plus the id: the synthetic namer mints "v" plus a running COUNTER, so an id-based name
        // of the same shape can land on a name the naming pass already gave another value. Whether the two
        // numbers coincide depends on the bytecode layout, which would make the capture appear and vanish
        // between generations. A prefix the synthetic namer never produces cannot collide at all.
        return materializeIntoTemporary(result, value, "tmp" + result.getId());
    }

    /**
     * Declares {@code value} into a temporary under {@code name} and binds later reads to it, pinning the
     * expression to this position in the statement sequence.
     */
    private Statement materializeIntoTemporary(SSAValue result, Expression value, String name)
    {
        SourceType type = value.getType();
        if (type == null)
        {
            type = typeRecoverer.recoverType(result);
        }
        if (type == null)
        {
            return null;
        }
        if (context.getExpressionContext().isDeclared(name))
        {
            return null;
        }
        context.getExpressionContext().markDeclaredWithType(name, type);
        context.getExpressionContext().markMaterialized(result);
        context.getExpressionContext().setVariableName(result, name);
        return new VarDeclStmt(type, name, value);
    }

    /**
     * A name for a captured call result, taken from the call itself: {@code getEnteredPassword()} names its
     * temporary {@code enteredPassword}.
     */
    private String temporaryNameForCall(InvokeInstruction invoke)
    {
        String method = invoke.getName();
        if (method == null || method.isEmpty() || "<init>".equals(method) || "<clinit>".equals(method))
        {
            return null;
        }
        String base = method;
        for (String prefix : new String[]{"get", "is", "read", "fetch"})
        {
            if (method.length() > prefix.length() && method.startsWith(prefix)
                    && Character.isUpperCase(method.charAt(prefix.length())))
            {
                base = method.substring(prefix.length());
                break;
            }
        }
        base = Character.toLowerCase(base.charAt(0)) + base.substring(1);
        if (!Character.isJavaIdentifierStart(base.charAt(0)))
        {
            return null;
        }
        for (int i = 1; i < base.length(); i++)
        {
            if (!Character.isJavaIdentifierPart(base.charAt(i)))
            {
                return null;
            }
        }
        // A taken name declines rather than taking a numbered variant: which names are already in scope
        // depends on the layout being decompiled, so a suffix would differ between round-trip generations
        // and turn a stable output into an oscillating one.
        return context.getExpressionContext().isDeclared(base) ? null : base;
    }

    private final Set<SSAValue> splitIncrementTemps = new HashSet<>();

    /**
     * Splits the live range of a self-increment's pre-value when it is read again, later in the same block, after
     * the increment store.
     */
    private void splitClobberedIncrementReads(IRMethod method)
    {
        for (IRBlock block : method.getBlocks())
        {
            List<IRInstruction> snapshot = new ArrayList<>(block.getInstructions());
            for (int i = 0; i < snapshot.size(); i++)
            {
                if (!(snapshot.get(i) instanceof StoreLocalInstruction))
                {
                    continue;
                }
                StoreLocalInstruction store = (StoreLocalInstruction) snapshot.get(i);
                SSAValue pre = incrementPreValue(store);
                if (pre == null)
                {
                    continue;
                }
                String storeName = partitionName(store);
                if (storeName == null || !storeName.equals(context.getExpressionContext().getVariableName(pre)))
                {
                    continue;
                }
                List<IRInstruction> laterUses = new ArrayList<>();
                for (IRInstruction use : pre.getUses())
                {
                    if (snapshot.indexOf(use) > i)
                    {
                        laterUses.add(use);
                    }
                }
                if (laterUses.isEmpty())
                {
                    continue;
                }
                SourceType type = typeRecoverer.recoverType(pre);
                if (type == null)
                {
                    continue;
                }
                SSAValue copy = new SSAValue(pre.getType());
                String name = "v" + copy.getId();
                context.getExpressionContext().markDeclaredWithType(name, type);
                context.getExpressionContext().markMaterialized(copy);
                context.getExpressionContext().setVariableName(copy, name);
                splitIncrementTemps.add(copy);
                block.insertInstruction(block.getInstructions().indexOf(store), new CopyInstruction(copy, pre));
                for (IRInstruction use : laterUses)
                {
                    use.replaceOperand(pre, copy);
                }
            }
        }
    }

    /**
     * The incremented pre-value {@code x} of a {@code slot = x +/- constant} store, else null.
     */
    private SSAValue incrementPreValue(StoreLocalInstruction store)
    {
        Value stored = store.getValue();
        if (!(stored instanceof SSAValue))
        {
            return null;
        }
        IRInstruction def = ((SSAValue) stored).getDefinition();
        if (!(def instanceof BinaryOpInstruction))
        {
            return null;
        }
        BinaryOpInstruction bin = (BinaryOpInstruction) def;
        if (bin.getOp() != BinaryOp.ADD && bin.getOp() != BinaryOp.SUB)
        {
            return null;
        }
        Value left = bin.getLeft();
        Value right = bin.getRight();
        if (left instanceof SSAValue && isConstantValue(right))
        {
            return (SSAValue) left;
        }
        if (right instanceof SSAValue && isConstantValue(left) && bin.getOp() == BinaryOp.ADD)
        {
            return (SSAValue) right;
        }
        return null;
    }

    /**
     * A raw constant, or an SSA value produced by a constant load.
     */
    private boolean isConstantValue(Value v)
    {
        if (v instanceof Constant)
        {
            return true;
        }
        return v instanceof SSAValue && ((SSAValue) v).getDefinition() instanceof ConstantInstruction;
    }

    private Statement recoverInstruction0(IRInstruction instr)
    {
        if (instr.isTerminator())
        {
            return recoverTerminator(instr);
        }

        if (instr instanceof StoreLocalInstruction)
        {
            StoreLocalInstruction store = (StoreLocalInstruction) instr;
            if (earlyRecoveredStores.remove(store))
            {
                return null;
            }
            return recoverStoreLocal(store);
        }

        if (instr instanceof FieldAccessInstruction)
        {
            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
            if (fieldAccess.isStore())
            {
                Value storedValue = fieldAccess.getValue();
                if (storedValue instanceof SSAValue)
                {
                    SSAValue ssaStored = (SSAValue) storedValue;
                    IRInstruction def = ssaStored.getDefinition();
                    if (def instanceof PhiInstruction && selfStorePhis.contains((PhiInstruction) def))
                    {
                        return null;
                    }
                }
                Expression receiver = fieldAccess.isStatic() ? null :
                    exprRecoverer.recoverOperand(fieldAccess.getObjectRef());
                SourceType fieldType = typeRecoverer.recoverType(fieldAccess.getDescriptor());
                Expression value = exprRecoverer.recoverOperand(fieldAccess.getValue(), fieldType);
                Expression target = new FieldAccessExpr(
                    receiver, fieldAccess.getName(), fieldAccess.getOwner(), fieldAccess.isStatic(), fieldType)
                    .withDescriptor(fieldAccess.getDescriptor());
                return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, fieldType));
            }
            if (fieldAccess.isLoad() && fieldAccess.getResult() != null)
            {
                SSAValue result = fieldAccess.getResult();
                Expression value = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(result, value);
                if (fieldLoadClobberedBeforeUse(fieldAccess, result)
                        || exprRecoverer.inliningWouldReorderEffects(result))
                {
                    // The same protection when the field is mutated INDIRECTLY: a call between the load
                    // and its use may write the field (`double a = g.time; g.setTime(x); use(a)`), so a
                    // load carried across any effect is captured where it was performed, not re-read at
                    // the use. Operands of the use itself are exempt inside the reorder check.
                    Statement decl = materializeClobberedLoad(result, value);
                    if (decl != null)
                    {
                        return decl;
                    }
                }
                PhiInstruction targetPhi = getPhiUsingValue(result);
                if (targetPhi != null && targetPhi.getResult() != null)
                {
                    if (selfStorePhis.contains(targetPhi))
                    {
                        return null;
                    }
                    if (context.isForLoopInductionPhi(targetPhi.getResult()))
                    {
                        return null;
                    }
                    // When this field-load value is written by a store_local to a source variable other
                    // than the phi's own, that store is its real assignment; a copy here naming the phi's
                    // variable would be a spurious cross-variable one (a redundant advance, or a
                    // type-punned reuse like `i = model`). Cache the expression and let the store emit it.
                    if (fieldLoadValueBelongsToOtherVariable(result, targetPhi))
                    {
                        return null;
                    }
                    String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
                    if (phiVarName != null)
                    {
                        SourceType type = value.getType();
                        if (type == null)
                        {
                            type = typeRecoverer.recoverType(result);
                        }
                        VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
                        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
                    }
                }
            }
            return null;
        }

        if (instr instanceof ArrayAccessInstruction)
        {
            ArrayAccessInstruction arrayAccess = (ArrayAccessInstruction) instr;
            if (arrayAccess.isStore())
            {
                Expression array = exprRecoverer.recoverOperand(arrayAccess.getArray());
                Expression index = exprRecoverer.recoverOperand(arrayAccess.getIndex());
                Expression value = exprRecoverer.recoverOperand(arrayAccess.getValue());
                if (!(index instanceof LiteralExpr) && isConstantOperand(arrayAccess.getIndex()))
                {
                    Logger.error("decompiler: constant array-store index recovered as non-literal '"
                            + index + "' on '" + array + "' — likely a mis-materialized constant");
                }
                SourceType arrayType = array.getType();
                SourceType elemType = (arrayType instanceof ArraySourceType)
                    ? ((ArraySourceType) arrayType).getElementType()
                    : value.getType();
                value = coerceForStore(value, elemType);
                Expression target = new ArrayAccessExpr(array, index, elemType);
                return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, elemType));
            }
            else
            {
                SSAValue result = arrayAccess.getResult();
                if (result != null)
                {
                    Expression expr = exprRecoverer.recover(arrayAccess);
                    context.getExpressionContext().cacheExpression(result, expr);
                }
                return null;
            }
        }

        if (instr instanceof InvokeInstruction)
        {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            if ("<init>".equals(invoke.getName()))
            {
                Value receiver = invoke.getArguments().isEmpty() ? null : invoke.getArguments().get(0);
                SSAValue ssaReceiver = (receiver instanceof SSAValue) ? (SSAValue) receiver : null;
                Expression expr = exprRecoverer.recover(invoke);
                if (expr instanceof MethodCallExpr)
                {
                    MethodCallExpr mce = (MethodCallExpr) expr;
                    String methodName = mce.getMethodName();
                    if ("super".equals(methodName) || "this".equals(methodName))
                    {
                        return new ExprStmt(expr);
                    }
                }
                if (expr instanceof NewExpr)
                {
                    if (ssaReceiver != null)
                    {
                        IRInstruction receiverDef = ssaReceiver.getDefinition();
                        if (receiverDef instanceof NewInstruction)
                        {
                            NewInstruction newInstr = (NewInstruction) receiverDef;
                            SSAValue newResult = newInstr.getResult();
                            boolean usedByStore = isUsedByStoreLocal(newResult);
                            PhiInstruction targetPhi = getPhiUsingValue(newResult);
                            if (targetPhi == null && usedByStore)
                            {
                                targetPhi = getPhiThroughStoreChain(newResult);
                            }
                            if (newResult != null && targetPhi != null && targetPhi.getResult() != null
                                    && !isStoredToPhiSlot(newResult, targetPhi))
                            {
                                String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
                                if (phiVarName != null && !phiVarName.equals("this")
                                        && !isParameterOrThisRef(targetPhi.getResult())
                                        && valueBelongsToPhiVariable(newResult, phiVarName)
                                        && !isStoredToVariableNamed(newResult, phiVarName))
                                {
                                    SourceType type = expr.getType();
                                    VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
                                    return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, expr, type));
                                }
                            }
                            if (newResult != null)
                            {
                                context.getExpressionContext().cacheExpression(newResult, expr);
                            }
                            if (usedByStore && targetPhi == null)
                            {
                                return null;
                            }
                            // Nothing keeps the object - no store, no merge, no name - but constructing it is
                            // still what the statement DOES: the constructor runs, and whatever it throws or
                            // writes happens. Caching the expression for a use that does not exist dropped the
                            // allocation from the output entirely, leaving an empty method behind.
                            if (isDiscardedAllocation(newResult, invoke))
                            {
                                return new ExprStmt(expr);
                            }
                            return null;
                        }
                        SSAValue actualNewValue = findNewInstructionValue(ssaReceiver);
                        boolean usedByStoreLocal = isUsedByStoreLocal(actualNewValue);
                        if (actualNewValue != null && !usedByStoreLocal)
                        {
                            PhiInstruction targetPhi = getPhiUsingValue(actualNewValue);
                            if (targetPhi != null && targetPhi.getResult() != null)
                            {
                                String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
                                if (phiVarName != null && !phiVarName.equals("this")
                                        && !isParameterOrThisRef(targetPhi.getResult())
                                        && valueBelongsToPhiVariable(actualNewValue, phiVarName))
                                {
                                    SourceType type = expr.getType();
                                    VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
                                    return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, expr, type));
                                }
                            }
                        }
                        String varName = context.getExpressionContext().getVariableName(ssaReceiver);
                        if (varName != null && !varName.equals("this") && !isParameterOrThisRef(ssaReceiver))
                        {
                            SourceType type = expr.getType();
                            if (!context.getExpressionContext().isDeclared(varName))
                            {
                                context.getExpressionContext().markDeclared(varName);
                                return new VarDeclStmt(type, varName, expr);
                            }
                        }
                        if (actualNewValue != null)
                        {
                            context.getExpressionContext().cacheExpression(actualNewValue, expr);
                        }
                        // Nothing keeps the object - no store, no merge, no name - but constructing it is
                        // still what the statement DOES: the constructor runs, and whatever it throws or
                        // writes happens. Caching the expression for a use that does not exist dropped the
                        // allocation from the output entirely, leaving an empty method behind.
                        if (isDiscardedAllocation(actualNewValue, invoke))
                        {
                            return new ExprStmt(expr);
                        }
                    }
                    return null;
                }
                return new ExprStmt(expr);
            }
            if (invoke.getResult() == null || invoke.getResult().getType() == null)
            {
                Expression expr = exprRecoverer.recover(invoke);
                return new ExprStmt(expr);
            }
            SSAValue result = invoke.getResult();
            if (context.getExpressionContext().isRecovered(result))
            {
                return null;
            }
            boolean usedByStoreLocal = isUsedByStoreLocal(result);
            if (usedByStoreLocal)
            {
                Statement inPlace = storeCarriedCallInPlace(invoke, result);
                if (inPlace != null)
                {
                    return inPlace;
                }
                if (exprRecoverer.inliningWouldReorderEffects(result))
                {
                    String name = temporaryNameForCall(invoke);
                    if (name != null)
                    {
                        Expression captured = exprRecoverer.recover(invoke);
                        Statement decl = materializeIntoTemporary(result, captured, name);
                        if (decl != null)
                        {
                            return decl;
                        }
                    }
                }
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
            if (isSingleUseArrayStoreValue(result))
            {
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
            if (result.getUses().isEmpty())
            {
                Expression expr = exprRecoverer.recover(invoke);
                return new ExprStmt(expr);
            }
            if (exprRecoverer.inliningWouldReorderEffects(result))
            {
                // The value stays on the stack across a statement that has its own effect. Rendering the
                // call at its use site would print the two effects in the wrong order, so capture it into
                // a temporary here and let the use read that name.
                String name = temporaryNameForCall(invoke);
                if (name != null)
                {
                    Expression expr = exprRecoverer.recover(invoke);
                    Statement captured = materializeIntoTemporary(result, expr, name);
                    if (captured != null)
                    {
                        return captured;
                    }
                }
            }
            if (isIntermediateValue(result))
            {
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
            if (isSingleUsePutField(result))
            {
                Expression expr = exprRecoverer.recover(invoke);
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
            if (isSingleUsePhiOperand(result))
            {
                Expression expr = exprRecoverer.recover(invoke);
                // If the value feeds an already-declared phi (e.g. a structured switch-expression
                // merge), emit the copy `phiVar = expr` here: the structured recovery path has no
                // lowerPhisOnEdge step, so otherwise a non-constant arm value would be dropped.
                Statement phiCopy = phiCopyForDeclaredMerge(result, expr);
                if (phiCopy != null)
                {
                    return phiCopy;
                }
                context.getExpressionContext().cacheExpression(result, expr);
                return null;
            }
        }

        if (instr instanceof NewInstruction)
        {
            SSAValue result = instr.getResult();
            if (result != null)
            {
                Expression expr = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(result, expr);
            }
            return null;
        }

        if (instr instanceof NewArrayInstruction)
        {
            SSAValue result = instr.getResult();
            if (result != null)
            {
                boolean usedByStore = isUsedByStoreLocal(result);
                boolean usedByArrayStore = isUsedByArrayStore(result);
                boolean usesEmpty = result.getUses().isEmpty();

                // If used by both array store and local store, emit declaration here
                // so array stores can use the variable name correctly.
                // Without this, array stores would appear before the declaration.
                if (usedByArrayStore && usedByStore)
                {
                    String varName = getLocalNameFromStoreLocal(result);
                    if (varName != null)
                    {
                        SourceType type = typeRecoverer.recoverType(result);
                        Expression value = exprRecoverer.recover(instr);
                        context.getExpressionContext().cacheExpression(result, value);
                        context.getExpressionContext().setVariableName(result, varName);
                        context.getExpressionContext().markMaterialized(result);
                        // The slot may already be declared in an enclosing scope (its declaration was
                        // emitted at method scope or by an earlier store). Re-declaring here shadows it,
                        // so the array is built into a fresh inner variable and the outer one keeps its
                        // old value - the store is lost. Assign to the existing variable instead.
                        if (context.getExpressionContext().isDeclared(varName))
                        {
                            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
                                    new VarRefExpr(varName, type, result), value, type));
                        }
                        context.getExpressionContext().markDeclared(varName);
                        return new VarDeclStmt(type, varName, value);
                    }
                }

                if (usedByStore)
                {
                    Expression expr = exprRecoverer.recover(instr);
                    context.getExpressionContext().cacheExpression(result, expr);
                    return null;
                }
                if (usesEmpty)
                {
                    Expression expr = exprRecoverer.recover(instr);
                    return new ExprStmt(expr);
                }
                return recoverVarDecl(instr);
            }
            return null;
        }

        if (instr instanceof CopyInstruction)
        {
            CopyInstruction copy = (CopyInstruction) instr;
            if (copy.getResult() != null && splitIncrementTemps.contains(copy.getResult()))
            {
                SourceType type = typeRecoverer.recoverType(copy.getResult());
                String name = context.getExpressionContext().getVariableName(copy.getResult());
                Expression src = exprRecoverer.recoverOperand(copy.getSource());
                return new VarDeclStmt(type, name, src);
            }
            Value source = copy.getSource();
            Set<SSAValue> visited = new HashSet<>();
            while (source instanceof SSAValue)
            {
                SSAValue ssaSource = (SSAValue) source;
                if (!visited.add(ssaSource))
                {
                    break;
                }
                if (context.getExpressionContext().isPendingNew(ssaSource))
                {
                    return null;
                }
                IRInstruction def = ssaSource.getDefinition();
                if (def instanceof NewInstruction)
                {
                    return null;
                }
                if (def instanceof CopyInstruction)
                {
                    source = ((CopyInstruction) def).getSource();
                    continue;
                }
                break;
            }
        }

        if (instr instanceof SimpleInstruction)
        {
            SimpleInstruction simple = (SimpleInstruction) instr;
            if (simple.getOp() == SimpleOp.MONITORENTER || simple.getOp() == SimpleOp.MONITOREXIT)
            {
                return null;
            }
            if (simple.getOp() == SimpleOp.ATHROW)
            {
                Expression exception = exprRecoverer.recoverOperand(simple.getOperand());
                return new ThrowStmt(exception);
            }
        }

        if (instr instanceof LoadLocalInstruction)
        {
            LoadLocalInstruction loadLocal = (LoadLocalInstruction) instr;
            if (loadLocal.getResult() != null)
            {
                String existingName = context.getExpressionContext().getVariableName(loadLocal.getResult());
                if (existingName == null)
                {
                    int localIndex = loadLocal.getLocalIndex();
                    String localName = getNameForLocalSlot(localIndex);
                    context.getExpressionContext().setVariableName(loadLocal.getResult(), localName);
                }
                // Mark as materialized so recoverOperand returns a VarRefExpr instead of
                // re-recovering the instruction (which would create duplicate new expressions)
                context.getExpressionContext().markMaterialized(loadLocal.getResult());
                Expression value = exprRecoverer.recover(loadLocal);
                context.getExpressionContext().cacheExpression(loadLocal.getResult(), value);
            }
            return null;
        }

        if (instr instanceof BinaryOpInstruction || instr instanceof UnaryOpInstruction || instr instanceof TypeCheckInstruction)
        {
            if (instr.getResult() != null && isIntermediateValue(instr.getResult()))
            {
                Expression value = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), value);
                return null;
            }
            if (instr.getResult() != null && isSingleUsePhiOperand(instr.getResult()))
            {
                Expression value = exprRecoverer.recover(instr);
                Statement phiCopy = phiCopyForDeclaredMerge(instr.getResult(), value);
                if (phiCopy != null)
                {
                    return phiCopy;
                }
                context.getExpressionContext().cacheExpression(instr.getResult(), value);
                return null;
            }
        }

        if (instr instanceof TypeCheckInstruction)
        {
            if (instr.getResult() != null && isIntermediateValue(instr.getResult()))
            {
                Expression value = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), value);
                return null;
            }
        }

        if (instr instanceof ConstantInstruction)
        {
            ConstantInstruction constInstr = (ConstantInstruction) instr;
            SSAValue result = constInstr.getResult();
            if (result != null)
            {
                PhiInstruction targetPhi = getPhiUsingValue(result);
                // A dead phi (or the primitive/reference pun a reused slot leaves behind) carries an
                // arbitrary component's name; materializing the constant into it writes another
                // variable entirely (`minor = null` for a try-with-resources sentinel). The same
                // guard every other phi-copy emitter applies. The store consuming this constant
                // still emits the real initialization.
                if (targetPhi != null && targetPhi.getResult() != null
                        && (targetPhi.getResult().getUses().isEmpty() || isTypePunDeadPhi(targetPhi)))
                {
                    targetPhi = null;
                }
                // The phi of a REUSED slot can carry the other occupant's name and type; writing this
                // constant into it is the same reused-slot fiction lowerPhisOnEdge refuses (`minor =
                // null` for a try-with-resources sentinel, with minor an int). The store consuming
                // the constant still emits the real initialization under the right name.
                if (targetPhi != null && targetPhi.getResult() != null)
                {
                    String punName = context.getExpressionContext().getVariableName(targetPhi.getResult());
                    if (punName != null && !copyTypeCompatible(getLocalSlotUnifiedType(punName), result))
                    {
                        targetPhi = null;
                    }
                }
                if (targetPhi != null && targetPhi.getResult() != null)
                {
                    if (selfStorePhis.contains(targetPhi))
                    {
                        FieldAccessInstruction fieldInfo = getSelfStoreFieldInfo(targetPhi);
                        if (fieldInfo != null)
                        {
                            Expression value = exprRecoverer.recover(constInstr);
                            SourceType fieldType = typeRecoverer.recoverType(fieldInfo.getDescriptor());
                            Expression fieldTarget = new FieldAccessExpr(
                                null, fieldInfo.getName(), fieldInfo.getOwner(), fieldInfo.isStatic(), fieldType)
                                .withDescriptor(fieldInfo.getDescriptor());
                            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, fieldTarget, value, fieldType));
                        }
                    }
                    if (context.isForLoopInductionPhi(targetPhi.getResult()))
                    {
                        Expression value = exprRecoverer.recover(constInstr);
                        context.getExpressionContext().cacheExpression(result, value);
                        return null;
                    }
                    String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
                    if (phiVarName != null)
                    {
                        Expression value = exprRecoverer.recover(constInstr);
                        // A phi-feeding constant is normally folded into the phi variable's declaration
                        // default - but NOT when an exception handler reads the variable's slot: the
                        // handler observes the slot at fault time, so the init must exist as a real
                        // statement before the protected range (and the declaration with it), or the
                        // handler's read references a variable declared inside the try it covers.
                        boolean handlerRead = slotReadByReachableHandler(constInstr, result);
                        if (!handlerRead && !context.getExpressionContext().isDeclared(phiVarName))
                        {
                            context.getExpressionContext().cacheExpression(result, value);
                            return null;
                        }
                        if (!handlerRead && isDefaultValue(value))
                        {
                            context.getExpressionContext().cacheExpression(result, value);
                            return null;
                        }
                        SourceType type = getLocalSlotUnifiedType(phiVarName);
                        if (type == null)
                        {
                            type = value.getType();
                        }
                        if (type == null)
                        {
                            type = typeRecoverer.recoverType(result);
                        }
                        if (type == PrimitiveSourceType.BOOLEAN)
                        {
                            Expression boolValue = tryConvertToBooleanLiteral(value, result);
                            if (boolValue != null)
                            {
                                value = boolValue;
                            }
                        }
                        VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
                        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
                    }
                }
                Expression value = exprRecoverer.recover(constInstr);
                context.getExpressionContext().cacheExpression(result, value);
            }
            return null;
        }

        if (instr.getResult() != null)
        {
            if (isUsedByStoreLocal(instr.getResult()))
            {
                Expression expr = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), expr);
                return null;
            }
            if (isUsedOnlyByTerminator(instr.getResult()))
            {
                Expression expr = exprRecoverer.recover(instr);
                context.getExpressionContext().cacheExpression(instr.getResult(), expr);
                return null;
            }
            return recoverVarDecl(instr);
        }

        return null;
    }

    private boolean isUsedOnlyByTerminator(SSAValue value)
    {
        if (value == null) return false;
        java.util.List<IRInstruction> uses = value.getUses();
        if (uses.isEmpty()) return false;
        for (IRInstruction use : uses)
        {
            if (use instanceof BranchInstruction) continue;
            if (use instanceof ReturnInstruction) continue;
            if (use instanceof SwitchInstruction) continue;
            return false;
        }
        return true;
    }

    private Statement recoverTerminator(IRInstruction instr)
    {
        if (instr instanceof ReturnInstruction)
        {
            ReturnInstruction ret = (ReturnInstruction) instr;
            return recoverReturn(ret);
        }
        if (instr instanceof SimpleInstruction)
        {
            SimpleInstruction simple = (SimpleInstruction) instr;
            if (simple.getOp() == SimpleOp.ATHROW)
            {
                Expression exception = exprRecoverer.recoverOperand(simple.getOperand());
                return new ThrowStmt(exception);
            }
        }
        return null;
    }

    private Statement recoverStoreLocal(StoreLocalInstruction store)
    {
        Value storeValue = store.getValue();

        // Early exit: if this is a NewArrayInstruction result that was already
        // declared at the NewArray position (because it's used by array stores),
        // skip to avoid duplicate declarations. This is a specific case handled
        // in recoverInstruction for NewArrayInstruction.
        if (storeValue instanceof SSAValue)
        {
            SSAValue ssaValue = (SSAValue) storeValue;
            IRInstruction def = ssaValue.getDefinition();
            if (def instanceof NewArrayInstruction && isUsedByArrayStore(ssaValue))
            {
                if (context.getExpressionContext().isMaterialized(ssaValue))
                {
                    String existingName = context.getExpressionContext().getVariableName(ssaValue);
                    if (existingName != null && context.getExpressionContext().isDeclared(existingName))
                    {
                        int localIndex = store.getLocalIndex();
                        SourceType valueType = typeRecoverer.recoverType(ssaValue);
                        String expectedName = partitionName(store);
                        if (expectedName == null)
                        {
                            expectedName = getNameForLocalSlotWithType(localIndex, valueType);
                        }
                        if (existingName.equals(expectedName))
                        {
                            return null;
                        }
                    }
                }
            }
        }

        int localIndex = store.getLocalIndex();

        // When recovering the initialization value for a variable declaration,
        // we need to recover the actual expression (e.g., "new GridBagConstraints()"),
        // not a variable reference (e.g., "local1"). So we temporarily un-materialize
        // the value during recovery, then re-materialize it after.
        // BUT: if the value was already stored to a DIFFERENT slot, we want the
        // variable reference (e.g., "result = i" should reference "i", not "new Integer(999)").
        boolean wasMaterialized;
        boolean shouldUnmaterialize = false;
        if (storeValue instanceof SSAValue)
        {
            SSAValue ssaValue = (SSAValue) storeValue;
            wasMaterialized = context.getExpressionContext().isMaterialized(ssaValue);
            int previousSlot = context.getExpressionContext().getSSAValueSlot(ssaValue);
            // An array initializer (`new Object[]{a, b}`) is emitted as its own declaration with the
            // element stores attached (`Object[] tmp = new Object[2]; tmp[0] = a; ...`). A store of
            // that array to another local must reference the temp, not re-recover the bare
            // `new Object[2]` expression, which would silently drop the element stores.
            String valueName = context.getExpressionContext().getVariableName(ssaValue);
            boolean declaredArrayInit = ssaValue.getDefinition() instanceof NewArrayInstruction
                    && isUsedByArrayStore(ssaValue)
                    && valueName != null
                    && context.getExpressionContext().isDeclared(valueName);
            // Only unmaterialize if this is the first store OR if we're storing to the same slot
            shouldUnmaterialize = wasMaterialized && !declaredArrayInit
                    && (previousSlot == -1 || previousSlot == localIndex);
            if (shouldUnmaterialize)
            {
                context.getExpressionContext().unmarkMaterialized(ssaValue);
            }
        }

        Expression value = exprRecoverer.recoverOperand(storeValue);

        // Re-materialize after recovery if we unmaterialized
        if (shouldUnmaterialize)
        {
            context.getExpressionContext().markMaterialized((SSAValue) storeValue);
        }

        value = stripDoubleNot(value);

        if (value == null)
        {
            // The stored expression could not be recovered as an operand (e.g. a copy that aliases an
            // unnamed entry value). Fall back to referencing the stored value by its own name, or the
            // slot's, so the store still emits a well-formed statement rather than a null right-hand side.
            SourceType fallbackType = typeRecoverer.recoverType(storeValue);
            String fallbackName = storeValue instanceof SSAValue
                    ? context.getExpressionContext().getVariableName((SSAValue) storeValue) : null;
            if (fallbackName == null)
            {
                fallbackName = getNameForLocalSlotWithType(localIndex, fallbackType);
            }
            value = new VarRefExpr(fallbackName, fallbackType,
                    storeValue instanceof SSAValue ? (SSAValue) storeValue : null);
        }

        SourceType valueType = value.getType();
        if (valueType == null)
        {
            valueType = typeRecoverer.recoverType(store.getValue());
        }

        // Name the store via the reaching-definition partition so this slot's variable
        // matches the loads that read it; fall back to category naming if unplaced.
        String name = partitionName(store);
        if (System.getProperty("yabr.debug.store") != null
                && store.getBytecodeOffset() == Integer.parseInt(System.getProperty("yabr.debug.store")))
        {
            System.err.println("[store] off=" + store.getBytecodeOffset() + " slot=" + localIndex
                    + " partition=" + name
                    + " valName=" + (storeValue instanceof SSAValue
                        ? context.getExpressionContext().getVariableName((SSAValue) storeValue) : "-")
                    + " materialized=" + (storeValue instanceof SSAValue
                        && context.getExpressionContext().isMaterialized((SSAValue) storeValue)));
        }
        if (name == null)
        {
            name = getNameForLocalSlotWithType(localIndex, valueType);
        }

        SourceType type = getLocalSlotUnifiedType(name);
        if (type == null)
        {
            type = value.getType();
        }
        if (type == null)
        {
            type = VoidSourceType.INSTANCE;
        }

        // Prefer boolean expression type over int unified type (JVM uses int for boolean)
        SourceType exprType = value.getType();
        if (exprType == PrimitiveSourceType.BOOLEAN && type == PrimitiveSourceType.INT)
        {
            type = PrimitiveSourceType.BOOLEAN;
        }

        // Convert int literal to boolean when storing to a boolean variable
        if (type == PrimitiveSourceType.BOOLEAN)
        {
            Expression boolValue = tryConvertToBooleanLiteral(value, store.getValue());
            if (boolValue != null)
            {
                value = boolValue;
            }
        }

        // If the source value is an SSA value, mark it as materialized.
        // For NEW values (from NewInstruction etc.), use the SSA value's existing name if it has one,
        // otherwise use the slot name. This ensures consistency between the variable declaration
        // and subsequent references to the SSA value.
        // For LOADED values (from LoadLocalInstruction), DON'T copy the source's name to the target slot.
        // The target slot should keep its own name, and the assignment should reference the source.
        if (store.getValue() instanceof SSAValue)
        {
            SSAValue sourceValue = (SSAValue) store.getValue();
            IRInstruction sourceDef = sourceValue.getDefinition();
            // A value from a load, a phi, or a parameter is a read of an existing variable (the
            // slot's current value, a loop-carried merge, or a method argument), not a value freshly
            // defined by this store. Copying the store's slot name onto it would conflate distinct
            // variables - e.g. `a = b` (b is a loop phi or a parameter) would rename b to "a" and
            // collapse the assignment into an elided self-store.
            boolean isLoadedValue = sourceDef instanceof LoadLocalInstruction
                || sourceDef instanceof PhiInstruction
                || isParameterOrThisRef(sourceValue);
            boolean isAlreadyMaterialized = context.getExpressionContext().isMaterialized(sourceValue);

            if (!isLoadedValue && !isAlreadyMaterialized)
            {
                String existingName = context.getExpressionContext().getVariableName(sourceValue);
                if (existingName != null && !isPerValueTemporaryName(existingName))
                {
                    name = existingName;
                }
                else
                {
                    context.getExpressionContext().setVariableName(sourceValue, name);
                }
                context.getExpressionContext().setSSAValueSlot(sourceValue, localIndex);
            }
            context.getExpressionContext().markMaterialized(sourceValue);
        }

        context.getExpressionContext().setLocalSlotName(localIndex, name);

        boolean isDeclared = context.getExpressionContext().isDeclared(name);

        if (isDeclared)
        {
            // A default-value store is covered by the declaration's default initializer only while the variable
            // provably still holds that default: outside a loop (a loop re-runs the store each iteration where
            // the hoisted declaration ran once) AND with no non-default store to the slot dominating this one. An
            // intervening reassignment (`result = fValue; ... result = 0.0f`) makes the later default store a
            // genuine re-initialization; eliding it there drops a live write and silently changes the value.
            boolean inLoopBlock = context.getLoopAnalysis() != null
                    && context.getLoopAnalysis().getLoop(store.getBlock()) != null;
            // A default store inside a switch CASE is that arm's own assignment converging on the
            // merge (a switch expression's phi input): eliding it in favor of the declaration's
            // initializer empties the arm and the switch-expression fold loses the default value.
            if (isDefaultValue(value) && context.getLoopStack().isEmpty() && !inLoopBlock
                    && !context.inInnermostSwitchCase(store.getBlock())
                    && !hasDominatingNonDefaultStore(store)
                    && !slotReadByReachableHandler(store, null))
            {
                return null;
            }
            if (value instanceof VarRefExpr)
            {
                VarRefExpr varRef = (VarRefExpr) value;
                if (name.equals(varRef.getName()))
                {
                    return null;
                }
            }
            VarRefExpr target = new VarRefExpr(name, type, null);
            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
        }

        if (value instanceof VarRefExpr && name.equals(((VarRefExpr) value).getName()))
        {
            // An identity store - the value already lives under this name (e.g. a caught exception whose
            // catch clause introduced it) - declares nothing.
            return null;
        }
        context.getExpressionContext().markDeclared(name);
        return new VarDeclStmt(type, name, value);
    }

    /**
     * True when the slot written by {@code instr} (a StoreLocal, or the definition a StoreLocal in the same block
     * consumes as {@code storedValue}) is read inside an exception handler protecting blocks reachable from it.
     */
    private boolean slotReadByReachableHandler(IRInstruction instr, SSAValue storedValue)
    {
        IRBlock origin = instr.getBlock();
        if (origin == null)
        {
            return false;
        }
        int slot = -1;
        if (instr instanceof StoreLocalInstruction)
        {
            slot = ((StoreLocalInstruction) instr).getLocalIndex();
        }
        else if (storedValue != null)
        {
            for (IRInstruction ins : origin.getInstructions())
            {
                if (ins instanceof StoreLocalInstruction && ((StoreLocalInstruction) ins).getValue() == storedValue)
                {
                    slot = ((StoreLocalInstruction) ins).getLocalIndex();
                    break;
                }
            }
        }
        if (slot < 0)
        {
            return false;
        }
        if (handlerReadSlots == null)
        {
            handlerReadSlots = new LinkedHashMap<>();
            DominatorTree dt = context.getDominatorTree();
            for (ExceptionHandler h : context.getIrMethod().getExceptionHandlers())
            {
                IRBlock hb = h.getHandlerBlock();
                Set<IRBlock> tryBlocks = h.getTryBlocks();
                if (hb == null || tryBlocks == null || tryBlocks.isEmpty())
                {
                    continue;
                }
                Set<Integer> reads = new HashSet<>();
                for (IRBlock b : context.getIrMethod().getBlocks())
                {
                    if (b != hb && (dt == null || !dt.dominates(hb, b)))
                    {
                        continue;
                    }
                    for (IRInstruction ins : b.getInstructions())
                    {
                        if (ins instanceof LoadLocalInstruction)
                        {
                            reads.add(((LoadLocalInstruction) ins).getLocalIndex());
                        }
                    }
                }
                if (!reads.isEmpty())
                {
                    handlerReadSlots.put(h, reads);
                }
            }
        }
        for (Map.Entry<ExceptionHandler, Set<Integer>> e : handlerReadSlots.entrySet())
        {
            if (!e.getValue().contains(slot))
            {
                continue;
            }
            Set<IRBlock> tryBlocks = e.getKey().getTryBlocks();
            boolean covers = tryBlocks.contains(origin);
            if (!covers)
            {
                Deque<IRBlock> work = new ArrayDeque<>();
                Set<IRBlock> seen = new HashSet<>();
                work.add(origin);
                while (!work.isEmpty())
                {
                    IRBlock b = work.poll();
                    if (!seen.add(b))
                    {
                        continue;
                    }
                    if (tryBlocks.contains(b))
                    {
                        covers = true;
                        break;
                    }
                    work.addAll(b.getSuccessors());
                }
            }
            if (covers)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Slots each handler's dominated subtree loads, built once per method for the elision guard.
     */
    private Map<ExceptionHandler, Set<Integer>> handlerReadSlots;

    /**
     * True when a non-default value is written to {@code store}'s slot on the path reaching it - either earlier in
     * its own block or in a dominating block.
     */
    private boolean hasDominatingNonDefaultStore(StoreLocalInstruction store)
    {
        int slot = store.getLocalIndex();
        IRBlock block = store.getBlock();
        if (block == null)
        {
            return false;
        }
        Value inBlock = lastSlotStoreValueBefore(block, store, slot);
        if (inBlock != null)
        {
            return !isDefaultIrValue(inBlock);
        }
        DominatorTree dom = context.getDominatorTree();
        if (dom == null)
        {
            return false;
        }
        IRBlock b = dom.getImmediateDominator(block);
        while (b != null && b != block)
        {
            Value v = lastSlotStoreValueBefore(b, null, slot);
            if (v != null)
            {
                return !isDefaultIrValue(v);
            }
            IRBlock next = dom.getImmediateDominator(b);
            if (next == b)
            {
                break; // the entry block immediately dominates itself; stop at the root
            }
            b = next;
        }
        return false;
    }

    /**
     * The value of the last {@code store_local slot} in {@code block} occurring before {@code limit} (or the
     * last one in the block when {@code limit} is null), or null when the block stores nothing to {@code slot}.
     */
    private Value lastSlotStoreValueBefore(IRBlock block, IRInstruction limit, int slot)
    {
        Value found = null;
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr == limit)
            {
                break;
            }
            if (instr instanceof StoreLocalInstruction && ((StoreLocalInstruction) instr).getLocalIndex() == slot)
            {
                found = ((StoreLocalInstruction) instr).getValue();
            }
        }
        return found;
    }

    /**
     * True when an IR value is a default constant (0, 0.0, false, or null).
     */
    private boolean isDefaultIrValue(Value value)
    {
        if (value instanceof NullConstant)
        {
            return true;
        }
        IRInstruction def = value instanceof SSAValue ? ((SSAValue) value).getDefinition() : null;
        if (def instanceof ConstantInstruction)
        {
            Constant c = ((ConstantInstruction) def).getConstant();
            if (c instanceof NullConstant)
            {
                return true;
            }
            Object v = c.getValue();
            if (v instanceof Number)
            {
                return ((Number) v).doubleValue() == 0.0;
            }
            if (v instanceof Boolean)
            {
                return !((Boolean) v);
            }
        }
        return false;
    }


    private Statement recoverReturn(ReturnInstruction ret)
    {
        if (ret.isVoidReturn())
        {
            return new ReturnStmt(null);
        }
        String methodDesc = context.getIrMethod().getDescriptor();
        SourceType returnType = null;
        if (methodDesc != null)
        {
            int parenEnd = methodDesc.indexOf(')');
            if (parenEnd >= 0 && parenEnd + 1 < methodDesc.length())
            {
                String retDesc = methodDesc.substring(parenEnd + 1);
                returnType = typeRecoverer.recoverType(retDesc);
            }
        }
        Expression value = exprRecoverer.recoverOperand(ret.getReturnValue(), returnType);
        value = applyReturnTypeCoercion(value, returnType);
        ReturnStmt returnStmt = new ReturnStmt(value);
        returnStmt.setMethodReturnType(returnType);
        return returnStmt;
    }

    private Expression applyReturnTypeCoercion(Expression expr, SourceType returnType)
    {
        if (returnType == null) return expr;
        if (expr instanceof LiteralExpr)
        {
            LiteralExpr lit = (LiteralExpr) expr;
            Object val = lit.getValue();
            if (val instanceof Integer)
            {
                int intVal = (Integer) val;
                if (returnType == PrimitiveSourceType.BOOLEAN)
                {
                    return LiteralExpr.ofBoolean(intVal != 0);
                }
                if (returnType == PrimitiveSourceType.CHAR)
                {
                    return LiteralExpr.ofChar((char) intVal);
                }
            }
        }
        // `cond ? 1 : 0` returned from a boolean method is the JVM's int form of the boolean; in source it is
        // just `cond`. Folding it makes the round trip stable (recovery materializes the boolean differently on
        // recompiled vs javac bytecode, so one pass produces the ternary and the other the bare condition).
        if (returnType == PrimitiveSourceType.BOOLEAN && expr instanceof TernaryExpr)
        {
            TernaryExpr tern = (TernaryExpr) expr;
            Integer thenV = intLiteralValue(tern.getThenExpr());
            Integer elseV = intLiteralValue(tern.getElseExpr());
            if (thenV != null && elseV != null && thenV == 1 && elseV == 0)
            {
                return tern.getCondition();
            }
        }
        return expr;
    }

    /**
     * The int value of {@code e} when it is an integer literal, else null.
     */
    private Integer intLiteralValue(Expression e)
    {
        if (e instanceof LiteralExpr && ((LiteralExpr) e).getValue() instanceof Integer)
        {
            return (Integer) ((LiteralExpr) e).getValue();
        }
        return null;
    }


    private Statement recoverVarDecl(IRInstruction instr)
    {
        SSAValue result = instr.getResult();
        String name = context.getExpressionContext().getVariableName(result);
        if (name == null)
        {
            name = "v" + result.getId();
        }

        SourceType type = typeRecoverer.recoverType(result);
        Expression value = exprRecoverer.recover(instr);

        context.getExpressionContext().cacheExpression(result, value);

        context.getExpressionContext().markMaterialized(result);
        context.getExpressionContext().setVariableName(result, name);

        if (value instanceof VarRefExpr && name.equals(((VarRefExpr) value).getName()))
        {
            // An identity copy (a cyclic loop-carried copy chain resolves to the variable itself):
            // the variable already holds the value, so neither `T x = x;` nor `x = x;` is emitted.
            return null;
        }

        if (context.getExpressionContext().isDeclared(name))
        {
            VarRefExpr target = new VarRefExpr(name, type, result);
            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, value, type));
        }

        context.getExpressionContext().markDeclared(name);

        return new VarDeclStmt(type, name, value);
    }


    /**
     * Folds javac's try-with-resources suppress scaffolding out of a finally body.
     */
    private Statement unwrapSuppressScaffold(Statement stmt)
    {
        if (stmt instanceof IfStmt)
        {
            IfStmt ifs = (IfStmt) stmt;
            Statement then = unwrapSuppressScaffold(ifs.getThenBranch());
            if (then != ifs.getThenBranch() && ifs.getElseBranch() == null)
            {
                return new IfStmt(ifs.getCondition(), then, null, ifs.getLocation());
            }
            return stmt;
        }
        if (stmt instanceof BlockStmt)
        {
            List<Statement> inner = ((BlockStmt) stmt).getStatements();
            List<Statement> out = new ArrayList<>(inner.size());
            boolean changed = false;
            for (Statement s : inner)
            {
                Statement u = unwrapSuppressScaffold(s);
                changed |= u != s;
                out.add(u);
            }
            return changed ? new BlockStmt(out) : stmt;
        }
        if (!(stmt instanceof TryCatchStmt))
        {
            return stmt;
        }
        TryCatchStmt tcs = (TryCatchStmt) stmt;
        if (tcs.hasFinally() || tcs.hasResources() || tcs.getCatches().size() != 1)
        {
            return stmt;
        }
        CatchClause cc = tcs.getCatches().get(0);
        Statement cbody = cc.body();
        List<Statement> cstmts = cbody instanceof BlockStmt
                ? ((BlockStmt) cbody).getStatements() : Collections.singletonList(cbody);
        if (cstmts.size() != 1 || !(cstmts.get(0) instanceof ExprStmt))
        {
            return stmt;
        }
        Expression e = ((ExprStmt) cstmts.get(0)).getExpression();
        if (!(e instanceof MethodCallExpr) || !"addSuppressed".equals(((MethodCallExpr) e).getMethodName()))
        {
            return stmt;
        }
        MethodCallExpr call = (MethodCallExpr) e;
        if (call.getArguments().size() != 1 || !(call.getArguments().get(0) instanceof VarRefExpr)
                || !cc.variableName().equals(((VarRefExpr) call.getArguments().get(0)).getName()))
        {
            return stmt;
        }
        return tcs.getTryBlock();
    }


    /**
     * A retired schema recoverer's dispatch arm.
     */
    private RetiredSchemaRecoveryException retiredSchemaRecovery(String kind, IRBlock header)
    {
        return new RetiredSchemaRecoveryException("schema " + kind + " recovery retired; unrouted " + kind
                + " at offset " + header.getBytecodeOffset() + " in " + context.getIrMethod().getName());
    }

    /**
     * Signals a region classification whose schema recoverer is retired and which no engine route owned.
     */
    public static final class RetiredSchemaRecoveryException extends IllegalStateException
    {
        RetiredSchemaRecoveryException(String message)
        {
            super(message);
        }
    }


    private Statement recoverStoreLocalAsForInit(StoreLocalInstruction store)
    {
        int localIndex = store.getLocalIndex();
        Value storedValue = store.getValue();

        SourceType storedType = typeRecoverer.recoverType(storedValue);
        String localName = partitionName(store);
        if (localName == null)
        {
            localName = getNameForLocalSlotWithType(localIndex, storedType);
        }

        // The init value is frequently coalesced into this same loop-variable partition (e.g. the
        // GETFIELD feeding `for (int i = this.position; ...)`), so a plain recovery would render it
        // as the variable's own name and produce a self-referential `int i = i`. Un-materialize it
        // across recovery so its defining expression is inlined, mirroring recoverStoreLocal.
        boolean shouldUnmaterialize = false;
        if (storedValue instanceof SSAValue)
        {
            SSAValue ssaValue = (SSAValue) storedValue;
            boolean wasMaterialized = context.getExpressionContext().isMaterialized(ssaValue);
            int previousSlot = context.getExpressionContext().getSSAValueSlot(ssaValue);
            String valueName = context.getExpressionContext().getVariableName(ssaValue);
            boolean declaredArrayInit = ssaValue.getDefinition() instanceof NewArrayInstruction
                    && isUsedByArrayStore(ssaValue)
                    && valueName != null
                    && context.getExpressionContext().isDeclared(valueName);
            shouldUnmaterialize = wasMaterialized && !declaredArrayInit
                    && (previousSlot == -1 || previousSlot == localIndex);
            if (shouldUnmaterialize)
            {
                context.getExpressionContext().unmarkMaterialized(ssaValue);
            }
        }

        Expression valueExpr = recoverExpressionDirectly(storedValue, storedType);

        if (shouldUnmaterialize)
        {
            context.getExpressionContext().markMaterialized((SSAValue) storedValue);
        }

        if (context.getExpressionContext().isDeclared(localName))
        {
            VarRefExpr target = new VarRefExpr(localName, storedType, null);
            return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, valueExpr, storedType));
        }
        else
        {
            context.getExpressionContext().markDeclaredInForLoopInit(localName);
            return new VarDeclStmt(storedType, localName, valueExpr);
        }
    }

    private Expression recoverExpressionDirectly(Value value, SourceType typeHint)
    {
        if (value instanceof Constant)
        {
            return exprRecoverer.recoverConstant((Constant) value, typeHint);
        }

        SSAValue ssa = (SSAValue) value;
        IRInstruction def = ssa.getDefinition();

        if (def instanceof ConstantInstruction)
        {
            return exprRecoverer.recoverConstant(((ConstantInstruction) def).getConstant(), typeHint);
        }

        if (def instanceof BinaryOpInstruction)
        {
            BinaryOpInstruction binOp = (BinaryOpInstruction) def;
            Expression left = recoverExpressionOrVarRef(binOp.getLeft());
            Expression right = recoverExpressionOrVarRef(binOp.getRight());
            BinaryOperator op = mapBinaryOp(binOp.getOp());
            SourceType resultType = typeRecoverer.recoverType(ssa);
            return new BinaryExpr(op, left, right, resultType);
        }

        return exprRecoverer.recoverOperand(value, typeHint);
    }

    private Expression recoverExpressionOrVarRef(Value value)
    {
        if (value instanceof Constant)
        {
            return exprRecoverer.recoverConstant((Constant) value, null);
        }

        SSAValue ssa = (SSAValue) value;
        IRInstruction def = ssa.getDefinition();

        if (def instanceof ConstantInstruction)
        {
            return exprRecoverer.recoverConstant(((ConstantInstruction) def).getConstant(), null);
        }

        if (def instanceof LoadLocalInstruction)
        {
            LoadLocalInstruction load = (LoadLocalInstruction) def;
            int localIndex = load.getLocalIndex();
            SourceType type = typeRecoverer.recoverType(ssa);
            String name = partitionName(load);
            if (name == null)
            {
                name = getNameForLocalSlotWithType(localIndex, type);
            }
            return new VarRefExpr(name, type, ssa);
        }

        if (def instanceof PhiInstruction)
        {
            PhiInstruction phi = (PhiInstruction) def;
            for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet())
            {
                Value incoming = entry.getValue();
                if (incoming instanceof SSAValue)
                {
                    SSAValue incomingSSA = (SSAValue) incoming;
                    if (incomingSSA.getDefinition() instanceof LoadLocalInstruction)
                    {
                        LoadLocalInstruction load = (LoadLocalInstruction) incomingSSA.getDefinition();
                        int localIndex = load.getLocalIndex();
                        SourceType type = typeRecoverer.recoverType(incomingSSA);
                        String name = partitionName(load);
                        if (name == null)
                        {
                            name = getNameForLocalSlotWithType(localIndex, type);
                        }
                        return new VarRefExpr(name, type, incomingSSA);
                    }
                }
            }
        }

        return exprRecoverer.recoverOperand(value);
    }

    private BinaryOperator mapBinaryOp(BinaryOp op)
    {
        switch (op)
        {
            case SUB: return BinaryOperator.SUB;
            case MUL: return BinaryOperator.MUL;
            case DIV: return BinaryOperator.DIV;
            case REM: return BinaryOperator.MOD;
            case AND: return BinaryOperator.BAND;
            case OR: return BinaryOperator.BOR;
            case XOR: return BinaryOperator.BXOR;
            case SHL: return BinaryOperator.SHL;
            case SHR: return BinaryOperator.SHR;
            case USHR: return BinaryOperator.USHR;
            default: return BinaryOperator.ADD;
        }
    }

    /**
     * If {@code value} is a single-use operand of a phi whose variable is already declared - a structured
     * switch-expression / if-merge - returns the {@code phiVar = expr} assignment that destructs the phi at this
     * predecessor block.
     */
    private Statement phiCopyForDeclaredMerge(SSAValue value, Expression expr)
    {
        PhiInstruction targetPhi = getPhiUsingValue(value);
        if (targetPhi == null || targetPhi.getResult() == null)
        {
            return null;
        }
        if (context.isForLoopInductionPhi(targetPhi.getResult()) || selfStorePhis.contains(targetPhi))
        {
            return null;
        }
        // A phi mixing primitives with references is a type-pun across a reused slot, verifier-legal only
        // because its result is dead - the declarations already skip it, and a copy INTO it is the same
        // nonsense written as an assignment (`cIndex = sdBuf` with cIndex an int and sdBuf a buffer).
        // A dead result more generally has nothing downstream to read the copy.
        if (isTypePunDeadPhi(targetPhi) || targetPhi.getResult().getUses().isEmpty())
        {
            return null;
        }
        String phiVarName = context.getExpressionContext().getVariableName(targetPhi.getResult());
        if (phiVarName == null || !context.getExpressionContext().isDeclared(phiVarName)
                || phiVarName.equals("this")
                || isParameterOrThisRef(targetPhi.getResult()))
        {
            return null;
        }
        SourceType type = getLocalSlotUnifiedType(phiVarName);
        if (type == null)
        {
            type = expr.getType();
        }
        if (type == null)
        {
            type = typeRecoverer.recoverType(value);
        }
        VarRefExpr target = new VarRefExpr(phiVarName, type, targetPhi.getResult());
        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, target, expr, type));
    }

    /**
     * Reconstructed {@code switch} on a {@code String}.
     */
    private static final class StringSwitchInfo
    {
        final Value stringValue;
        final Map<String, Integer> literalToIndex;
        final IRBlock mergeBlock;
        final SwitchInstruction indexSwitch;
        final Set<IRBlock> scaffolding;

        StringSwitchInfo(Value stringValue, Map<String, Integer> literalToIndex, IRBlock mergeBlock,
                         SwitchInstruction indexSwitch, Set<IRBlock> scaffolding)
                         {
            this.stringValue = stringValue;
            this.literalToIndex = literalToIndex;
            this.mergeBlock = mergeBlock;
            this.indexSwitch = indexSwitch;
            this.scaffolding = scaffolding;
        }
    }

    private static final class EqualsStep
    {
        final String literal;
        final IRBlock matchBlock;
        final IRBlock noMatchBlock;

        EqualsStep(String literal, IRBlock matchBlock, IRBlock noMatchBlock)
        {
            this.literal = literal;
            this.matchBlock = matchBlock;
            this.noMatchBlock = noMatchBlock;
        }
    }

    /**
     * Recognizes javac's two-phase {@code String} switch rooted at a {@code switch (s.hashCode())} and returns
     * the data needed to rebuild a single {@code switch (s)}, or null if {@code header} is an ordinary switch.
     */
    private StringSwitchInfo detectStringSwitch(IRBlock header)
    {
        IRInstruction term = header.getTerminator();
        if (!(term instanceof SwitchInstruction))
        {
            return null;
        }
        SwitchInstruction hashSwitch = (SwitchInstruction) term;

        Value key = hashSwitch.getKey();
        if (!(key instanceof SSAValue))
        {
            return null;
        }
        IRInstruction keyDef = ((SSAValue) key).getDefinition();
        if (!(keyDef instanceof InvokeInstruction))
        {
            return null;
        }
        InvokeInstruction hashCall = (InvokeInstruction) keyDef;
        if (!"hashCode".equals(hashCall.getName()) || !"()I".equals(hashCall.getDescriptor()))
        {
            return null;
        }
        Value stringValue = hashCall.getReceiver();
        if (stringValue == null)
        {
            return null;
        }

        Map<String, Integer> literalToIndex = new LinkedHashMap<>();
        Set<IRBlock> scaffolding = new HashSet<>();
        scaffolding.add(header);
        IRBlock mergeBlock = null;

        for (IRBlock caseTarget : hashSwitch.getCases().values())
        {
            IRBlock current = caseTarget;
            Set<IRBlock> guard = new HashSet<>();
            while (current != null && guard.add(current))
            {
                EqualsStep step = matchEqualsStep(current);
                if (step == null)
                {
                    return null;
                }
                Integer index = indexAssignedIn(step.matchBlock);
                IRBlock matchMerge = singleSuccessor(step.matchBlock);
                if (index == null || matchMerge == null)
                {
                    return null;
                }
                if (mergeBlock == null)
                {
                    mergeBlock = matchMerge;
                }
                else if (mergeBlock != matchMerge)
                {
                    return null;
                }
                literalToIndex.put(step.literal, index);
                scaffolding.add(current);
                scaffolding.add(step.matchBlock);
                // Several strings can share a hashCode: the no-match edge then tests the next equals.
                current = (matchEqualsStep(step.noMatchBlock) != null) ? step.noMatchBlock : null;
            }
        }

        if (literalToIndex.isEmpty())
        {
            return null;
        }
        IRInstruction mergeTerm = mergeBlock.getTerminator();
        if (!(mergeTerm instanceof SwitchInstruction))
        {
            return null;
        }
        SwitchInstruction indexSwitch = (SwitchInstruction) mergeTerm;
        for (Integer index : literalToIndex.values())
        {
            if (!indexSwitch.getCases().containsKey(index))
            {
                return null;
            }
        }
        scaffolding.add(mergeBlock);
        return new StringSwitchInfo(stringValue, literalToIndex, mergeBlock, indexSwitch, scaffolding);
    }

    /**
     * The block following the entire string switch (where non-returning cases converge), or null.
     */
    private IRBlock stringSwitchExit(StringSwitchInfo info)
    {
        var postDom = analyzer.getPostDominatorTree();
        if (postDom == null)
        {
            return null;
        }
        IRBlock exit = postDom.getImmediatePostDominator(info.mergeBlock);
        if (exit == null || info.scaffolding.contains(exit) || info.indexSwitch.getCases().containsValue(exit))
        {
            return null;
        }
        return exit;
    }

    private EqualsStep matchEqualsStep(IRBlock block)
    {
        if (block == null)
        {
            return null;
        }
        InvokeInstruction equalsCall = null;
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof InvokeInstruction)
            {
                InvokeInstruction invoke = (InvokeInstruction) instr;
                if ("equals".equals(invoke.getName()) && "(Ljava/lang/Object;)Z".equals(invoke.getDescriptor()))
                {
                    equalsCall = invoke;
                }
            }
        }
        if (equalsCall == null || equalsCall.getMethodArguments().size() != 1)
        {
            return null;
        }
        String literal = stringConstantOf(equalsCall.getMethodArguments().get(0));
        if (literal == null)
        {
            return null;
        }
        if (!(block.getTerminator() instanceof BranchInstruction))
        {
            return null;
        }
        BranchInstruction branch = (BranchInstruction) block.getTerminator();
        if (branch.getLeft() != equalsCall.getResult())
        {
            return null;
        }
        if (branch.getCondition() == CompareOp.IFEQ)
        {
            return new EqualsStep(literal, branch.getFalseTarget(), branch.getTrueTarget());
        }
        if (branch.getCondition() == CompareOp.IFNE)
        {
            return new EqualsStep(literal, branch.getTrueTarget(), branch.getFalseTarget());
        }
        return null;
    }

    private Integer indexAssignedIn(IRBlock block)
    {
        if (block == null)
        {
            return null;
        }
        Integer index = null;
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof StoreLocalInstruction)
            {
                Integer constant = intConstantOf(((StoreLocalInstruction) instr).getValue());
                if (constant != null)
                {
                    index = constant;
                }
            }
        }
        return index;
    }

    private IRBlock singleSuccessor(IRBlock block)
    {
        if (block == null)
        {
            return null;
        }
        IRInstruction term = block.getTerminator();
        if (term instanceof SimpleInstruction && ((SimpleInstruction) term).getOp() == SimpleOp.GOTO)
        {
            return ((SimpleInstruction) term).getTarget();
        }
        return block.getSuccessors().size() == 1 ? block.getSuccessors().iterator().next() : null;
    }

    private String stringConstantOf(Value value)
    {
        if (!(value instanceof SSAValue))
        {
            return null;
        }
        IRInstruction def = ((SSAValue) value).getDefinition();
        if (def instanceof ConstantInstruction && ((ConstantInstruction) def).getConstant() instanceof StringConstant)
        {
            return ((StringConstant) ((ConstantInstruction) def).getConstant()).getValue();
        }
        return null;
    }

    private Integer intConstantOf(Value value)
    {
        if (!(value instanceof SSAValue))
        {
            return null;
        }
        IRInstruction def = ((SSAValue) value).getDefinition();
        if (def instanceof ConstantInstruction && ((ConstantInstruction) def).getConstant() instanceof IntConstant)
        {
            return ((IntConstant) ((ConstantInstruction) def).getConstant()).getValue();
        }
        return null;
    }






    private static class EnumSwitchInfo
    {
        Expression enumVariable;
        Expression ordinalExpression;
        String enumClassName;
        String holderClass;
        boolean rawOrdinals;
    }

    private EnumSwitchInfo detectEnumSwitchPattern(Expression selector)
    {
        if (!(selector instanceof ArrayAccessExpr))
        {
            // A switch dispatched on the ordinal directly, without javac's $SwitchMap indirection - the
            // relowered form of an enum switch. The case keys ARE the ordinals, and the enum class is the
            // ordinal() call's owner, so the constants resolve from the enum itself with no holder.
            if (selector instanceof MethodCallExpr)
            {
                MethodCallExpr call = (MethodCallExpr) selector;
                if ("ordinal".equals(call.getMethodName()) && call.getReceiver() != null
                        && call.getOwnerClass() != null)
                {
                    EnumSwitchInfo info = new EnumSwitchInfo();
                    info.enumVariable = call.getReceiver();
                    info.ordinalExpression = call;
                    info.enumClassName = call.getOwnerClass();
                    info.rawOrdinals = true;
                    return info;
                }
            }
            return null;
        }

        ArrayAccessExpr arrayAccess = (ArrayAccessExpr) selector;
        Expression array = arrayAccess.getArray();
        Expression index = arrayAccess.getIndex();

        if (!(array instanceof FieldAccessExpr))
        {
            return null;
        }

        FieldAccessExpr fieldAccess = (FieldAccessExpr) array;
        String fieldName = fieldAccess.getFieldName();

        if (!fieldName.startsWith("$SwitchMap$"))
        {
            return null;
        }

        if (!(index instanceof MethodCallExpr))
        {
            return null;
        }

        MethodCallExpr methodCall = (MethodCallExpr) index;
        if (!"ordinal".equals(methodCall.getMethodName()))
        {
            return null;
        }

        Expression enumVar = methodCall.getReceiver();
        if (enumVar == null)
        {
            return null;
        }

        EnumSwitchInfo info = new EnumSwitchInfo();
        info.enumVariable = enumVar;
        info.ordinalExpression = methodCall;
        info.enumClassName = EnumSwitchMapRegistry.parseEnumClassFromFieldName(fieldName);
        info.holderClass = fieldAccess.getOwnerClass();
        return info;
    }

    /**
     * True when every case value of an enum switch resolves to a constant name via the switch-map registry.
     */
    private boolean allEnumCasesResolve(RegionInfo info, EnumSwitchInfo enumInfo)
    {
        if (enumInfo.rawOrdinals)
        {
            for (Integer caseValue : info.getSwitchCases().keySet())
            {
                if (enumConstantForCase(enumInfo, caseValue) == null)
                {
                    return false;
                }
            }
            return true;
        }
        EnumSwitchMapRegistry registry = EnumSwitchMapRegistry.getInstance();
        if (!registry.hasMapping(enumInfo.holderClass, enumInfo.enumClassName))
        {
            return false;
        }
        for (Integer caseValue : info.getSwitchCases().keySet())
        {
            if (registry.lookupEnumConstant(enumInfo.holderClass, enumInfo.enumClassName, caseValue) == null)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * The enum constant a case key selects: the ordinal itself, or the holder's switch-map entry.
     */
    private String enumConstantForCase(EnumSwitchInfo enumInfo, int caseValue)
    {
        if (enumInfo.rawOrdinals)
        {
            return EnumConstants.nameByOrdinal(enumClassPool, enumInfo.enumClassName, caseValue);
        }
        return EnumSwitchMapRegistry.getInstance()
                .lookupEnumConstant(enumInfo.holderClass, enumInfo.enumClassName, caseValue);
    }



    private static final String DISPATCH_LABEL = "$dispatch$";

    /**
     * Invoke names the recovery folds into syntax, so faithful output carries no call expression for them.
     */
    private static final Set<String> FOLDED_CALL_NAMES = new HashSet<>(Arrays.asList(
        "<init>", "<clinit>", "append", "toString", "valueOf",
        "intValue", "longValue", "doubleValue", "floatValue", "booleanValue",
        "byteValue", "shortValue", "charValue",
        "iterator", "hasNext", "next", "makeConcatWithConstants",
        // ordinal() is folded into switch(enumVar) syntax by the $SwitchMap$ enum-switch idiom
        "ordinal",
        // hashCode()/equals() are folded into switch(stringVar) syntax by the String-switch idiom
        "hashCode", "equals"));

    /**
     * Completeness invariant.
     *
     * @param body the recovered method body to audit
     * @return true if a reachable call is missing from the body
     */
    public boolean hasDroppedOperations(BlockStmt body)
    {
        IRMethod method = context.getIrMethod();
        IRBlock entry = method.getEntryBlock();
        if (entry == null)
        {
            return false;
        }
        Set<IRBlock> reachable = new HashSet<>();
        collectReachableBlocks(entry, reachable);

        Set<String> irCalls = new HashSet<>();
        for (IRBlock block : reachable)
        {
            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof InvokeInstruction)
                {
                    InvokeInstruction inv = (InvokeInstruction) instr;
                    if (FOLDED_CALL_NAMES.contains(inv.getName()))
                    {
                        continue;
                    }
                    if (inv.isDynamic())
                    {
                        continue;
                    }
                    irCalls.add(simpleName(inv.getOwner()) + "." + inv.getName());
                }
            }
        }
        if (irCalls.isEmpty())
        {
            return false;
        }

        Set<String> astCalls = new HashSet<>();
        body.walk(node -> {
            if (node instanceof MethodCallExpr)
            {
                MethodCallExpr call = (MethodCallExpr) node;
                astCalls.add(simpleName(call.getOwnerClass()) + "." + call.getMethodName());
            }
        });

        for (String key : irCalls)
        {
            if (!astCalls.contains(key))
            {
                return true;
            }
        }
        return false;
    }

    private static String simpleName(String owner)
    {
        if (owner == null)
        {
            return "";
        }
        int slash = owner.lastIndexOf('/');
        int dot = owner.lastIndexOf('.');
        int cut = Math.max(slash, dot);
        return cut >= 0 ? owner.substring(cut + 1) : owner;
    }

    /**
     * Recovers the whole method as a structured dispatch loop.
     *
     * @return the method body as a dispatch loop, or an empty block when the method has no entry
     */
    public BlockStmt recoverMethodAsDispatch()
    {
        IRMethod method = context.getIrMethod();
        IRBlock entry = method.getEntryBlock();
        if (entry == null)
        {
            return new BlockStmt(Collections.emptyList());
        }
        List<Statement> statements = new ArrayList<>();
        detectSelfStorePhis(method);
        collectForLoopInitInstructions();
        registerPendingNewInstructions(method);
        emitPhiDeclarations(method, statements);

        Set<IRBlock> reachable = new HashSet<>();
        collectReachableBlocks(entry, reachable);
        List<IRBlock> ordered = new ArrayList<>();
        for (IRBlock block : method.getBlocksInOrder())
        {
            if (reachable.contains(block))
            {
                ordered.add(block);
            }
        }
        hoistDispatchLocals(ordered, statements);
        statements.addAll(buildDispatchLoop(ordered, entry));
        return new BlockStmt(statements);
    }

    /**
     * Pre-declares, at method scope, every non-phi local stored within the dispatch block set so a declaration
     * inside one switch case is neither out of scope nor "might not be initialized" in a sibling case.
     */
    private void hoistDispatchLocals(List<IRBlock> blocks, List<Statement> statements)
    {
        Set<String> done = new HashSet<>();
        for (IRBlock block : blocks)
        {
            for (IRInstruction instr : block.getInstructions())
            {
                if (!(instr instanceof StoreLocalInstruction))
                {
                    continue;
                }
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                String name = partitionName(store);
                if (name == null)
                {
                    name = getNameForLocalSlotWithType(store.getLocalIndex(),
                        typeRecoverer.recoverType(store.getValue()));
                }
                if (name == null || name.equals("this")
                        || context.getExpressionContext().isParameterOrThisSlot(store.getLocalIndex()))
                {
                    continue;
                }
                if (!done.add(name) || context.getExpressionContext().isDeclared(name))
                {
                    continue;
                }
                SourceType type = getLocalSlotUnifiedType(name);
                if (type == null)
                {
                    type = typeRecoverer.recoverType(store.getValue());
                }
                if (type == null)
                {
                    type = PrimitiveSourceType.INT;
                }
                statements.add(new VarDeclStmt(type, name, getDefaultValue(type)));
                context.getExpressionContext().markDeclaredWithType(name, type);
            }
        }
    }

    private List<Statement> buildDispatchLoop(List<IRBlock> ordered, IRBlock entry)
    {
        Map<IRBlock, Integer> pc = new LinkedHashMap<>();
        int next = 0;
        for (IRBlock block : ordered)
        {
            pc.put(block, next++);
        }
        String pcName = "$pc$";
        while (context.getExpressionContext().isDeclared(pcName))
        {
            pcName = pcName + "$";
        }

        List<Statement> out = new ArrayList<>();
        out.add(new VarDeclStmt(PrimitiveSourceType.INT, pcName, LiteralExpr.ofInt(pc.getOrDefault(entry, 0))));

        List<SwitchCase> cases = new ArrayList<>();
        for (IRBlock block : ordered)
        {
            List<Statement> body = new ArrayList<>(recoverSimpleBlock(block));
            body.addAll(dispatchCaseTail(block, pc, pcName));
            context.markProcessed(block);
            cases.add(SwitchCase.of(pc.get(block), body));
        }
        List<Statement> def = new ArrayList<>();
        def.add(new BreakStmt(DISPATCH_LABEL));
        cases.add(SwitchCase.defaultCase(def));

        SwitchStmt sw = new SwitchStmt(new VarRefExpr(pcName, PrimitiveSourceType.INT), cases);
        List<Statement> loopBody = new ArrayList<>();
        loopBody.add(sw);
        WhileStmt loop = new WhileStmt(LiteralExpr.ofBoolean(true), new BlockStmt(loopBody));
        out.add(new LabeledStmt(DISPATCH_LABEL, loop));
        return out;
    }

    private Statement assignPc(String pcName, Map<IRBlock, Integer> pc, IRBlock target)
    {
        int label = pc.getOrDefault(target, -1);
        return new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
            new VarRefExpr(pcName, PrimitiveSourceType.INT),
            LiteralExpr.ofInt(label), PrimitiveSourceType.INT));
    }

    /**
     * Translates a block's terminator into the dispatch-loop case tail.
     */
    private List<Statement> dispatchCaseTail(IRBlock block, Map<IRBlock, Integer> pc, String pcName)
    {
        List<Statement> tail = new ArrayList<>();
        IRInstruction term = block.getTerminator();

        if (term instanceof ReturnInstruction)
        {
            return tail;
        }
        if (term instanceof SimpleInstruction)
        {
            SimpleInstruction simple = (SimpleInstruction) term;
            if (simple.getOp() == SimpleOp.ATHROW)
            {
                return tail;
            }
            if (simple.getOp() == SimpleOp.GOTO && simple.getTarget() != null)
            {
                IRBlock t = simple.getTarget();
                tail.addAll(lowerPhisOnEdge(block, t));
                tail.add(assignPc(pcName, pc, t));
                tail.add(new BreakStmt());
                return tail;
            }
        }
        if (term instanceof BranchInstruction)
        {
            BranchInstruction branch = (BranchInstruction) term;
            IRBlock t = branch.getTrueTarget();
            IRBlock f = branch.getFalseTarget();
            Expression cond = recoverCondition(block, false);
            List<Statement> thenS = new ArrayList<>(lowerPhisOnEdge(block, t));
            thenS.add(assignPc(pcName, pc, t));
            List<Statement> elseS = new ArrayList<>(lowerPhisOnEdge(block, f));
            elseS.add(assignPc(pcName, pc, f));
            tail.add(new IfStmt(cond, new BlockStmt(thenS), new BlockStmt(elseS)));
            tail.add(new BreakStmt());
            return tail;
        }
        if (term instanceof SwitchInstruction)
        {
            SwitchInstruction switchInstr = (SwitchInstruction) term;
            Expression key = exprRecoverer.recoverOperand(switchInstr.getKey());
            List<SwitchCase> inner = new ArrayList<>();
            for (Map.Entry<Integer, IRBlock> e : switchInstr.getCases().entrySet())
            {
                List<Statement> cs = new ArrayList<>(lowerPhisOnEdge(block, e.getValue()));
                cs.add(assignPc(pcName, pc, e.getValue()));
                cs.add(new BreakStmt());
                inner.add(SwitchCase.of(e.getKey(), cs));
            }
            List<Statement> ds = new ArrayList<>(lowerPhisOnEdge(block, switchInstr.getDefaultTarget()));
            ds.add(assignPc(pcName, pc, switchInstr.getDefaultTarget()));
            ds.add(new BreakStmt());
            inner.add(SwitchCase.defaultCase(ds));
            tail.add(new SwitchStmt(key, inner));
            tail.add(new BreakStmt());
            return tail;
        }

        Set<IRBlock> succs = block.getSuccessors();
        if (succs.size() == 1)
        {
            IRBlock s = succs.iterator().next();
            tail.addAll(lowerPhisOnEdge(block, s));
            tail.add(assignPc(pcName, pc, s));
            tail.add(new BreakStmt());
        }
        else
        {
            tail.add(new BreakStmt(DISPATCH_LABEL));
        }
        return tail;
    }

    /**
     * SSA destruction on a CFG edge.
     */
    @Override
    public List<Statement> lowerPhisOnEdge(IRBlock pred, IRBlock succ)
    {
        return lowerPhisOnEdge(pred, succ, false);
    }

    /**
     * As {@link #lowerPhisOnEdge}, but only for the loop's for-induction counter phis - the ones {@link
     * #emitPhiDeclaration} deliberately does not declare.
     */
    @Override
    public List<Statement> lowerInductionPhiInitsOnEdge(IRBlock pred, IRBlock succ)
    {
        return lowerPhisOnEdge(pred, succ, true);
    }

    @Override
    public List<Statement> recoverUnconsumedForLoopInits(IRBlock header)
    {
        // Scoped to loops living inside an exception handler's subtree: only there does the counter
        // carry no phi (handler-entry code is not merged into SSA form the same way), leaving the marked
        // init with no other re-emission point. A normal loop's init is realized by the for-init fold or
        // a phi edge copy; re-emitting it here would re-declare the counter at the preheader with the
        // slot-unified type and break the recompiled layout's fixed point.
        DominatorTree dt = context.getDominatorTree();
        boolean handlerOnly = false;
        if (dt != null)
        {
            for (ExceptionHandler h : context.getIrMethod().getExceptionHandlers())
            {
                if (h.getHandlerBlock() != null
                        && (h.getHandlerBlock() == header || dt.dominates(h.getHandlerBlock(), header)))
                {
                    handlerOnly = true;
                    break;
                }
            }
        }
        if (!handlerOnly)
        {
            return Collections.emptyList();
        }
        List<Statement> inits = new ArrayList<>();
        LoopAnalysis.Loop loop = context.getLoopAnalysis() != null
                ? context.getLoopAnalysis().getLoop(header) : null;
        for (IRBlock pred : header.getPredecessors())
        {
            if (loop != null && loop.contains(pred))
            {
                continue;
            }
            for (IRInstruction instr : pred.getInstructions())
            {
                if (!(instr instanceof StoreLocalInstruction) || !context.isForLoopInit(instr)
                        || consumedForLoopInits.contains(instr))
                {
                    continue;
                }
                StoreLocalInstruction store = (StoreLocalInstruction) instr;
                // A phi at the header for this slot owns the init: its edge copy (or the for-init fold)
                // realizes the value, and re-emitting here would duplicate it.
                boolean phiOwned = false;
                for (PhiInstruction phi : header.getPhiInstructions())
                {
                    if (isPhiForLocal(phi, store.getLocalIndex(), loop))
                    {
                        phiOwned = true;
                        break;
                    }
                }
                if (phiOwned)
                {
                    continue;
                }
                consumedForLoopInits.add(instr);
                Statement initStmt = recoverStoreLocalAsForInit(store);
                inits.add(initStmt);
            }
        }
        return inits;
    }

    /**
     * Whether a phi edge copy of {@code incoming} into a variable of type {@code target} is type- coherent.
     */
    private boolean copyTypeCompatible(SourceType target, Value incoming)
    {
        SourceType in = typeRecoverer.recoverType(incoming);
        if (target == null || in == null)
        {
            return true;
        }
        boolean targetPrim = target instanceof PrimitiveSourceType;
        boolean inPrim = in instanceof PrimitiveSourceType;
        if (targetPrim != inPrim)
        {
            return false;
        }
        if (targetPrim)
        {
            return true;
        }
        if (!(target instanceof ReferenceSourceType) || !(in instanceof ReferenceSourceType))
        {
            return true;
        }
        String a = ((ReferenceSourceType) target).getInternalName();
        String b = ((ReferenceSourceType) in).getInternalName();
        if (a.equals(b) || "java/lang/Object".equals(a) || "java/lang/Object".equals(b))
        {
            return true;
        }
        return reachesInHierarchy(a, b) || reachesInHierarchy(b, a);
    }

    /**
     * The last name segment, splitting on every separator a nested reference can be spelled with.
     */
    private static String nestedSimpleName(String internal)
    {
        int cut = Math.max(internal.lastIndexOf('/'), Math.max(internal.lastIndexOf('$'), internal.lastIndexOf('.')));
        return cut < 0 ? internal : internal.substring(cut + 1);
    }

    /**
     * Pool lookup tolerant of the source-dotted spelling of a nested class.
     */
    private ClassFile poolClassFor(String name)
    {
        if (enumClassPool == null)
        {
            return null;
        }
        String n = name.replace('.', '/');
        ClassFile cf = enumClassPool.get(n);
        char[] chars = n.toCharArray();
        for (int i = chars.length - 1; cf == null && i >= 0; i--)
        {
            if (chars[i] == '/')
            {
                chars[i] = '$';
                cf = enumClassPool.get(new String(chars));
            }
        }
        return cf;
    }

    /**
     * Whether {@code sub} reaches {@code sup} walking supers and interfaces; unresolvable =&gt; true.
     */
    private boolean reachesInHierarchy(String sub, String sup)
    {
        java.util.ArrayDeque<String> work = new java.util.ArrayDeque<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        work.add(sub);
        while (!work.isEmpty())
        {
            String cur = work.poll();
            if (cur == null || !seen.add(cur))
            {
                continue;
            }
            if (cur.equals(sup) || nestedSimpleName(cur).equals(nestedSimpleName(sup)))
            {
                return true;
            }
            ClassFile cf = poolClassFor(cur);
            if (cf != null)
            {
                if (cf.getSuperClassName() != null)
                {
                    work.add(cf.getSuperClassName());
                }
                for (int idx : cf.getInterfaces())
                {
                    work.add(cf.resolveClassName(idx));
                }
                continue;
            }
            try
            {
                Class<?> c = Class.forName(cur.replace('/', '.'), false, getClass().getClassLoader());
                if (c.getSuperclass() != null)
                {
                    work.add(c.getSuperclass().getName().replace('.', '/'));
                }
                for (Class<?> i : c.getInterfaces())
                {
                    work.add(i.getName().replace('.', '/'));
                }
            }
            catch (Throwable unresolvable)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Copies for {@code succ}'s operand-stack merge phis whose incoming on this edge no instruction in {@code
     * pred} produces.
     *
     * @param pred the source block of the edge
     * @param succ the merge block whose stack phis are lowered
     * @return the copies that arm owes the merge, empty when the arm already produced them
     */
    @Override
    public List<Statement> stackPhiCopiesOnEdge(IRBlock pred, IRBlock succ)
    {
        List<Statement> copies = new ArrayList<>();
        for (PhiInstruction phi : succ.getPhiInstructions())
        {
            SSAValue result = phi.getResult();
            if (result == null || !isStackMergePhi(result) || result.getUses().isEmpty())
            {
                continue;
            }
            Value incoming = phi.getIncoming(pred);
            if (!(incoming instanceof SSAValue))
            {
                continue;
            }
            SSAValue in = (SSAValue) incoming;
            IRInstruction inDef = in.getDefinition();
            // Produced in this block: the instruction's own recovery already emitted the copy.
            if (inDef != null && inDef.getBlock() == pred)
            {
                continue;
            }
            if (inDef == null && !isParameterOrThisRef(in))
            {
                continue;
            }
            if (inDef != null && (inDef.getBlock() == null
                    || !analyzer.getDominatorTree().dominates(inDef.getBlock(), pred)))
            {
                continue;
            }
            String target = context.getExpressionContext().getVariableName(result);
            if (target == null || !context.getExpressionContext().isDeclared(target))
            {
                continue;
            }
            SourceType type = getLocalSlotUnifiedType(target);
            if (type == null)
            {
                type = typeRecoverer.recoverType(result);
            }
            if (!copyTypeCompatible(type, incoming))
            {
                continue;
            }
            Expression rhs = exprRecoverer.recoverOperand(incoming, type);
            if (rhs instanceof VarRefExpr && target.equals(((VarRefExpr) rhs).getName()))
            {
                continue;
            }
            copies.add(new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
                    new VarRefExpr(target, type, result), rhs, type)));
        }
        return copies;
    }

    /** Whether the value is an operand-stack merge phi result, which the lifter names {@code stack_phi_N}. */
    private boolean isStackMergePhi(SSAValue value)
    {
        return value.getName() != null && value.getName().startsWith("stack_phi_");
    }

    private List<Statement> lowerPhisOnEdge(IRBlock pred, IRBlock succ, boolean inductionOnly)
    {
        List<Statement> copies = new ArrayList<>();
        List<Integer> copySlots = new ArrayList<>();
        boolean allLiteral = true;
        for (PhiInstruction phi : succ.getPhiInstructions())
        {
            SSAValue result = phi.getResult();
            if (result == null)
            {
                continue;
            }
            if (inductionOnly && !isForLoopInductionPhi(phi))
            {
                continue;
            }
            // A dead phi has nothing downstream to read the copy, and a primitive/reference pun across a
            // reused slot exists only BECAUSE it is dead - a copy into either is a nonsense assignment
            // (`cIndex = sdBuf` with cIndex an int and sdBuf a buffer).
            if (result.getUses().isEmpty() || isTypePunDeadPhi(phi))
            {
                continue;
            }
            String target = context.getExpressionContext().getVariableName(result);
            if (target == null || target.equals("this") || isParameterOrThisRef(result))
            {
                continue;
            }
            Value incoming = phi.getIncoming(pred);
            if (incoming == null)
            {
                continue;
            }
            // A phi operand must dominate its predecessor (the SSA invariant). The lift's reaching defs
            // are imprecise across EXCLUSIVE branches sharing a slot, so an "incoming" can be the other
            // branch's variable - `child = t` with t defined on a path that returns before the loop.
            // Rendering such an incoming borrows a disjoint component's name; the slot is really
            // undefined on this edge and the declaration's default covers it.
            if (incoming instanceof SSAValue)
            {
                SSAValue in = (SSAValue) incoming;
                IRInstruction inDef = in.getDefinition();
                if (inDef == null && !isParameterOrThisRef(in))
                {
                    continue;
                }
                if (inDef != null && (inDef.getBlock() == null
                        || !analyzer.getDominatorTree().dominates(inDef.getBlock(), pred)))
                {
                    continue;
                }
            }
            SourceType type = getLocalSlotUnifiedType(target);
            if (type == null)
            {
                type = typeRecoverer.recoverType(result);
            }
            // A copy whose incoming TYPE is unrelated to the target variable's (a float or an
            // Iterator into a Vertex) is the same reused-slot fiction the dominance filter catches
            // for cross-branch reads - no verified program produces it. Rendering it both emits a
            // nonsense assignment and poisons the variable's type at every later use.
            if (!copyTypeCompatible(type, incoming))
            {
                continue;
            }
            Expression rhs = exprRecoverer.recoverOperand(incoming, type);
            if (rhs instanceof VarRefExpr && target.equals(((VarRefExpr) rhs).getName()))
            {
                // A for-induction phi's incoming is materialized UNDER THIS PHI'S OWN NAME because the
                // for-init pre-pass SKIPPED its store - recoverOperand then echoes the variable being
                // assigned while the real entry value was never emitted anywhere. Recover the constant
                // behind it directly. Any other phi's echo is a genuine identity copy (its init store
                // was emitted normally) and stays skipped.
                Expression direct = isForLoopInductionPhi(phi)
                        && !context.getExpressionContext().isDeclared(target)
                        ? recoverEntryConstant(incoming, type) : null;
                if (direct == null)
                {
                    continue;
                }
                rhs = direct;
            }
            allLiteral &= rhs instanceof LiteralExpr;
            copySlots.add(getLocalIndexFromPhi(phi));
            copies.add(new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN, new VarRefExpr(target, type), rhs, type)));
        }
        // Copies on one edge are PARALLEL in SSA semantics; their emission order is an artifact of
        // phi order, which differs between the javac and relowered layouts. When every rhs is a
        // literal (no copy can read another's target), order them by slot so both layouts agree.
        if (allLiteral && copies.size() > 1)
        {
            List<Statement> ordered = new ArrayList<>(copies);
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < copies.size(); i++)
            {
                idx.add(i);
            }
            idx.sort(java.util.Comparator.comparingInt(copySlots::get));
            for (int i = 0; i < idx.size(); i++)
            {
                ordered.set(i, copies.get(idx.get(i)));
            }
            return ordered;
        }
        return copies;
    }

    /**
     * Folds a three-way-compare result tested against zero into a direct relational comparison.
     */
    private Expression recoverThreeWayCompare(BranchInstruction branch, boolean negate)
    {
        if (!(branch.getLeft() instanceof SSAValue))
        {
            return null;
        }
        IRInstruction def = ((SSAValue) branch.getLeft()).getDefinition();
        if (!(def instanceof BinaryOpInstruction))
        {
            return null;
        }
        BinaryOpInstruction cmp = (BinaryOpInstruction) def;
        switch (cmp.getOp())
        {
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                break;
            default:
                return null;
        }
        Expression a = exprRecoverer.recoverOperand(cmp.getLeft());
        Expression b = exprRecoverer.recoverOperand(cmp.getRight());
        BinaryOperator op = OperatorMapper.mapCompareOp(branch.getCondition());
        if (negate)
        {
            op = negateOperator(op);
        }
        // fcmpl/dcmpl bias NaN to -1, fcmpg/dcmpg to +1, so the int test reading in the biased
        // direction is NOT the plain relational: dcmpg >= 0 is !(a < b), true for NaN, while
        // a >= b is false. Emit the negated complement for those combinations.
        boolean nanGreater = cmp.getOp() == BinaryOp.FCMPG || cmp.getOp() == BinaryOp.DCMPG;
        boolean floatCmp = cmp.getOp() != BinaryOp.LCMP;
        if (floatCmp && (nanGreater
                ? (op == BinaryOperator.GT || op == BinaryOperator.GE)
                : (op == BinaryOperator.LT || op == BinaryOperator.LE)))
        {
            BinaryExpr complement = new BinaryExpr(negateOperator(op), a, b, PrimitiveSourceType.BOOLEAN);
            return new UnaryExpr(UnaryOperator.NOT, complement, PrimitiveSourceType.BOOLEAN);
        }
        return new BinaryExpr(op, a, b, PrimitiveSourceType.BOOLEAN);
    }

    @Override
    public Expression recoverCondition(IRBlock block, boolean negate)
    {
        IRInstruction terminator = block.getTerminator();
        if (terminator instanceof BranchInstruction)
        {
            BranchInstruction branch = (BranchInstruction) terminator;

            // `Xcmp(a,b) <cond> 0` - an lcmp/fcmp/dcmp result tested by a compare-to-zero branch - is the
            // direct relational comparison `a <cond> b`. Recovering it as `(a - b) <cond> 0` is wrong-looking
            // and non-idempotent: it re-lowers to `lcmp((a-b), 0L)`, which then recovers as `(a - b - 0)
            // <cond> 0`, accumulating a spurious `- 0` on every round trip.
            if (branch.getRight() == null)
            {
                Expression threeWay = recoverThreeWayCompare(branch, negate);
                if (threeWay != null)
                {
                    return threeWay;
                }
            }

            Expression left = exprRecoverer.recoverOperand(branch.getLeft());
            CompareOp condition = branch.getCondition();

            if (branch.getRight() != null)
            {
                Expression right = exprRecoverer.recoverOperand(branch.getRight());
                BinaryOperator op =
                    OperatorMapper.mapCompareOp(condition);
                if (negate)
                {
                    op = negateOperator(op);
                }
                return new BinaryExpr(op, left, right, PrimitiveSourceType.BOOLEAN);
            }

            if (OperatorMapper.isNullCheck(condition))
            {
                Expression nullExpr = LiteralExpr.ofNull();
                BinaryOperator op =
                    OperatorMapper.mapCompareOp(condition);
                if (negate)
                {
                    op = negateOperator(op);
                }
                return new BinaryExpr(op, left, nullExpr, PrimitiveSourceType.BOOLEAN);
            }

            if (isBooleanExpression(left) || isBooleanSSAValue(branch.getLeft())
                    || isBooleanLocalAt(branch.getLeft(), branch.getBytecodeOffset()))
            {
                boolean wantTrue = (condition == CompareOp.NE || condition == CompareOp.IFNE);
                if (negate)
                {
                    wantTrue = !wantTrue;
                }
                if (wantTrue)
                {
                    return left;
                }
                else
                {
                    return new UnaryExpr(UnaryOperator.NOT, left, PrimitiveSourceType.BOOLEAN);
                }
            }

            BinaryOperator op =
                OperatorMapper.mapCompareOp(condition);
            if (negate)
            {
                op = negateOperator(op);
            }
            Expression zero = LiteralExpr.ofInt(0);
            return new BinaryExpr(op, left, zero, PrimitiveSourceType.BOOLEAN);
        }
        return LiteralExpr.ofBoolean(!negate);
    }

    @Override
    public boolean conditionInlinesSideEffect(IRBlock block)
    {
        IRInstruction terminator = block.getTerminator();
        if (!(terminator instanceof BranchInstruction))
        {
            return false;
        }
        BranchInstruction branch = (BranchInstruction) terminator;
        if (branch.getLeft() != null && exprRecoverer.operandInlinesSideEffect(branch.getLeft()))
        {
            return true;
        }
        return branch.getRight() != null && exprRecoverer.operandInlinesSideEffect(branch.getRight());
    }

    @Override
    public boolean guardAtomExceptionFree(IRBlock block)
    {
        IRInstruction terminator = block.getTerminator();
        if (!(terminator instanceof BranchInstruction))
        {
            return false;
        }
        BranchInstruction branch = (BranchInstruction) terminator;
        if (branch.getLeft() != null && exprRecoverer.operandMayThrowInline(branch.getLeft()))
        {
            return false;
        }
        return branch.getRight() == null || !exprRecoverer.operandMayThrowInline(branch.getRight());
    }

    @Override
    public boolean isDuplicationSafe(IRBlock block)
    {
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr instanceof FieldAccessInstruction)
            {
                FieldAccessInstruction field = (FieldAccessInstruction) instr;
                if (field.isStore())
                {
                    return false;
                }
                if (field.isLoad() && field.getResult() != null
                        && fieldLoadClobberedBeforeUse(field, field.getResult()))
                {
                    return false;
                }
            }
            else if (instr instanceof ArrayAccessInstruction && ((ArrayAccessInstruction) instr).isStore())
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean regionContainsUnprocessedHandler(Set<IRBlock> region)
    {
        List<ExceptionHandler> handlers = context.getIrMethod().getExceptionHandlers();
        if (handlers == null || handlers.isEmpty())
        {
            return false;
        }
        for (IRBlock block : region)
        {
            // The widest UNPROCESSED handler, not widest-then-filtered: a split outer range can begin at
            // the same block as a nested try, and the processed outer piece must not mask the nested
            // handler (the engine would structure the region and silently drop the nested catch).
            if (findUnprocessedHandlerStartingAt(block) != null)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an expression is boolean-typed.
     */
    private boolean isBooleanExpression(Expression expr)
    {
        SourceType type = expr.getType();
        if (type == PrimitiveSourceType.BOOLEAN)
        {
            return true;
        }

        if (expr instanceof MethodCallExpr)
        {
            MethodCallExpr mce = (MethodCallExpr) expr;
            String name = mce.getMethodName();
            if (name.startsWith("is") || name.startsWith("has") || name.startsWith("can") ||
                name.startsWith("should") || name.startsWith("was") ||
                "equals".equals(name) || "contains".equals(name) ||
                "startsWith".equals(name) || "endsWith".equals(name) ||
                "isEmpty".equals(name) || "isPresent".equals(name))
                {
                return true;
            }
        }

        if (expr instanceof FieldAccessExpr)
        {
            FieldAccessExpr fae = (FieldAccessExpr) expr;
            SourceType fieldType = fae.getType();
            if (fieldType == PrimitiveSourceType.BOOLEAN)
            {
                return true;
            }
        }

        if (expr instanceof VarRefExpr)
        {
            int slot = slotOfValue(((VarRefExpr) expr).getSsaValue());
            return context.getExpressionContext().isParameterOrThisSlot(slot)
                    && isParameterBoolean(context.getExpressionContext().parameterIndexForSlot(slot));
        }

        return false;
    }

    /**
     * Checks an SSAValue's IR type for boolean directly, as a fallback for when the recovered
     * expression type is not detected as boolean.
     */
    private boolean isBooleanSSAValue(Value value)
    {
        if (value instanceof SSAValue)
        {
            SSAValue ssaValue = (SSAValue) value;
            IRType type = ssaValue.getType();
            if (type == PrimitiveType.BOOLEAN)
            {
                return true;
            }
            IRInstruction def = ssaValue.getDefinition();
            if (def instanceof TypeCheckInstruction)
            {
                TypeCheckInstruction typeCheck = (TypeCheckInstruction) def;
                return typeCheck.isInstanceOf();
            }
        }
        return false;
    }

    /**
     * Whether {@code value} reads a local the class declares {@code boolean}, tested at {@code offset}.
     */
    private boolean isBooleanLocalAt(Value value, int offset)
    {
        if (!(value instanceof SSAValue))
        {
            return false;
        }
        IRInstruction def = ((SSAValue) value).getDefinition();
        int slot = -1;
        if (def instanceof LoadLocalInstruction)
        {
            slot = ((LoadLocalInstruction) def).getLocalIndex();
        }
        else if (def instanceof PhiInstruction)
        {
            slot = getLocalIndexFromPhi((PhiInstruction) def);
        }
        return slot >= 0 && "Z".equals(narrowLvtDescriptor(slot, offset));
    }

    /**
     * Checks if a parameter at the given index is a boolean type based on method descriptor.
     */
    private boolean isParameterBoolean(int argIndex)
    {
        String descriptor = context.getIrMethod().getDescriptor();
        if (descriptor == null) return false;

        List<String> paramTypes = parseParameterTypes(descriptor);
        if (argIndex >= 0 && argIndex < paramTypes.size())
        {
            return "Z".equals(paramTypes.get(argIndex));
        }
        return false;
    }

    /**
     * Parses parameter types from a method descriptor.
     * @param descriptor method descriptor like "(ZILjava/lang/String;)V"
     * @return list of type descriptors for each parameter
     */
    private List<String> parseParameterTypes(String descriptor)
    {
        List<String> types = new ArrayList<>();
        int start = descriptor.indexOf('(');
        int end = descriptor.indexOf(')', start);
        if (start == -1 || end == -1 || end <= start)
        {
            return types;
        }

        int index = start + 1;
        while (index < end)
        {
            char c = descriptor.charAt(index);
            if (c == 'B' || c == 'C' || c == 'D' || c == 'F' || c == 'I' || c == 'J' || c == 'S' || c == 'Z')
            {
                types.add(String.valueOf(c));
                index++;
            }
            else if (c == 'L')
            {
                int semicolon = descriptor.indexOf(';', index);
                if (semicolon > index)
                {
                    types.add(descriptor.substring(index, semicolon + 1));
                    index = semicolon + 1;
                }
                else
                {
                    break;
                }
            }
            else if (c == '[')
            {
                int arrayStart = index;
                while (index < end && descriptor.charAt(index) == '[')
                {
                    index++;
                }
                if (index < end)
                {
                    char baseType = descriptor.charAt(index);
                    if (baseType == 'L')
                    {
                        int semicolon = descriptor.indexOf(';', index);
                        if (semicolon > index)
                        {
                            types.add(descriptor.substring(arrayStart, semicolon + 1));
                            index = semicolon + 1;
                        }
                        else
                        {
                            break;
                        }
                    }
                    else
                    {
                        types.add(descriptor.substring(arrayStart, index + 1));
                        index++;
                    }
                }
            }
            else
            {
                index++;
            }
        }
        return types;
    }





    private BinaryOperator negateOperator(BinaryOperator op)
    {
        if (op == BinaryOperator.EQ)
        {
            return BinaryOperator.NE;
        }
        else if (op == BinaryOperator.NE)
        {
            return BinaryOperator.EQ;
        }
        else if (op == BinaryOperator.LT)
        {
            return BinaryOperator.GE;
        }
        else if (op == BinaryOperator.GE)
        {
            return BinaryOperator.LT;
        }
        else if (op == BinaryOperator.GT)
        {
            return BinaryOperator.LE;
        }
        else if (op == BinaryOperator.LE)
        {
            return BinaryOperator.GT;
        }
        else
        {
            return op;
        }
    }

    private IRBlock getNextSequentialBlock(IRBlock block)
    {
        if (block.getSuccessors().size() == 1)
        {
            return block.getSuccessors().iterator().next();
        }
        return null;
    }

    private boolean isReturnBlock(IRBlock block)
    {
        if (block == null) return false;
        IRInstruction terminator = block.getTerminator();
        return terminator instanceof ReturnInstruction;
    }


    private IRBlock findSwitchMerge(RegionInfo info)
    {
        Set<IRBlock> caseTargets = new HashSet<>(info.getSwitchCases().values());
        Set<IRBlock> allTargets = new HashSet<>(caseTargets);
        if (info.getDefaultTarget() != null)
        {
            allTargets.add(info.getDefaultTarget());
        }
        var postDomTree = analyzer.getPostDominatorTree();
        if (postDomTree != null)
        {
            IRBlock ipdom = postDomTree.getImmediatePostDominator(info.getHeader());
            // The post-dominator tree is unreliable when the switch has several distinct `return`/`throw`
            // exits and no single sink: it can name a block reached through only one case body (e.g. the
            // `return true` arm of `case: return a && b; default: return false;`) as the header's ipdom,
            // even though the default path never reaches it. A genuine merge post-dominates the header, so
            // it must be reachable from every case target and the default; reject candidates that are not.
            if (ipdom != null && !caseTargets.contains(ipdom) && reachedFromAllTargets(ipdom, allTargets))
            {
                return ipdom;
            }
        }

        // Reachable region of each target, stopping at the other targets so one case body's blocks
        // do not bleed into another's region.
        Map<IRBlock, Set<IRBlock>> reachableByTarget = new LinkedHashMap<>();
        for (IRBlock target : allTargets)
        {
            Set<IRBlock> reachable = new HashSet<>();
            Set<IRBlock> otherTargets = new HashSet<>(allTargets);
            otherTargets.remove(target);
            collectReachableBlocks(target, reachable, otherTargets);
            reachableByTarget.put(target, reachable);
        }

        // A case that returns or throws without rejoining the others never reaches the merge - its
        // region is disjoint from every sibling's. The merge is where the CONVERGING cases meet, so
        // intersect only over targets whose region overlaps another's. Requiring every target
        // (including a throwing default) to reach the merge collapses the intersection to empty and
        // leaves the shared post-switch tail to be absorbed into whichever case is recovered first.
        Set<IRBlock> converging = new HashSet<>();
        for (IRBlock a : allTargets)
        {
            for (IRBlock b : allTargets)
            {
                if (a != b && !Collections.disjoint(reachableByTarget.get(a), reachableByTarget.get(b)))
                {
                    converging.add(a);
                    break;
                }
            }
        }
        if (converging.size() < 2)
        {
            return null;
        }

        Set<IRBlock> commonSuccessors = null;
        for (IRBlock target : converging)
        {
            if (commonSuccessors == null)
            {
                commonSuccessors = new HashSet<>(reachableByTarget.get(target));
            }
            else
            {
                commonSuccessors.retainAll(reachableByTarget.get(target));
            }
        }
        commonSuccessors.removeAll(caseTargets);
        if (commonSuccessors.isEmpty())
        {
            return null;
        }

        // The merge is the ENTRY of the shared region - the earliest common block reached straight
        // from a converging case (a predecessor outside the region), not one buried deeper in the
        // tail. Pick by lowest bytecode offset so the choice is deterministic.
        IRBlock merge = null;
        int mergeOffset = Integer.MAX_VALUE;
        for (IRBlock candidate : commonSuccessors)
        {
            boolean isEntry = false;
            for (IRBlock pred : candidate.getPredecessors())
            {
                if (!commonSuccessors.contains(pred))
                {
                    isEntry = true;
                    break;
                }
            }
            if (!isEntry)
            {
                continue;
            }
            int off = candidate.getInstructions().isEmpty()
                    ? Integer.MAX_VALUE
                    : candidate.getInstructions().get(0).getBytecodeOffset();
            if (off < mergeOffset)
            {
                mergeOffset = off;
                merge = candidate;
            }
        }
        return merge;
    }

    /**
     * True when {@code merge} is reachable from every switch target (each case target and the default), i.e. it is
     * a real convergence point that post-dominates the header.
     */
    private boolean reachedFromAllTargets(IRBlock merge, Set<IRBlock> allTargets)
    {
        for (IRBlock target : allTargets)
        {
            if (target == merge)
            {
                continue;
            }
            Set<IRBlock> reachable = new HashSet<>();
            collectReachableBlocks(target, reachable);
            if (!reachable.contains(merge))
            {
                return false;
            }
        }
        return true;
    }

    private void collectReachableBlocks(IRBlock start, Set<IRBlock> result)
    {
        collectReachableBlocks(start, result, Collections.emptySet());
    }


    /**
     * Marks exactly the blocks a catch clause's recovery consumes.
     */
    private void collectCatchConsumedBlocks(ExceptionHandler handler, Set<IRBlock> result)
    {
        IRBlock handlerBlock = handler.getHandlerBlock();
        result.add(handlerBlock);
        if (isGotoTerminated(handlerBlock) && !handler.isCatchAll() && !gotoStaysInCatch(handlerBlock))
        {
            return;
        }
        Deque<IRBlock> worklist = new ArrayDeque<>(handlerBlock.getSuccessors());
        while (!worklist.isEmpty())
        {
            IRBlock current = worklist.poll();
            if (!result.add(current))
            {
                continue;
            }
            IRInstruction terminator = current.getTerminator();
            if (terminator instanceof ReturnInstruction)
            {
                continue;
            }
            if (terminator instanceof SimpleInstruction)
            {
                SimpleOp op = ((SimpleInstruction) terminator).getOp();
                if (op == SimpleOp.GOTO || op == SimpleOp.ATHROW)
                {
                    continue;
                }
            }
            worklist.addAll(current.getSuccessors());
        }
    }

    private static boolean isGotoTerminated(IRBlock block)
    {
        IRInstruction terminator = block.getTerminator();
        return terminator instanceof SimpleInstruction
                && ((SimpleInstruction) terminator).getOp() == SimpleOp.GOTO;
    }

    /**
     * True when a goto-terminated catch entry jumps WITHIN its own body - its target is dominated by the handler
     * block (e.g. the return-value spill falling into the inlined finally copy) - rather than out to the shared
     * post-try merge.
     */
    private boolean gotoStaysInCatch(IRBlock handlerBlock)
    {
        DominatorTree dt = context.getDominatorTree();
        if (dt == null)
        {
            return false;
        }
        for (IRBlock succ : handlerBlock.getSuccessors())
        {
            if (succ != handlerBlock && dt.dominates(handlerBlock, succ))
            {
                return true;
            }
        }
        return false;
    }

    private void collectReachableBlocks(IRBlock start, Set<IRBlock> result, Set<IRBlock> stopBlocks)
    {
        Deque<IRBlock> worklist = new ArrayDeque<>();
        worklist.add(start);

        while (!worklist.isEmpty())
        {
            IRBlock current = worklist.poll();
            if (result.contains(current) || stopBlocks.contains(current))
            {
                continue;
            }
            result.add(current);
            worklist.addAll(current.getSuccessors());
        }
    }







    /**
     * Inverts a condition expression.
     */
    private Expression invertCondition(Expression condition)
    {
        if (condition instanceof BinaryExpr)
        {
            BinaryExpr binExpr = (BinaryExpr) condition;
            BinaryOperator op = binExpr.getOperator();
            BinaryOperator inverted = negateOperator(op);
            if (inverted != op)
            {
                return new BinaryExpr(inverted, binExpr.getLeft(), binExpr.getRight(), binExpr.getType());
            }
        }
        return new UnaryExpr(UnaryOperator.NOT, condition, PrimitiveSourceType.BOOLEAN);
    }

    /**
     * Coerces a value to the slot type it is being stored into when the source-level types disagree but the JVM
     * treats them identically.
     */
    private Expression coerceForStore(Expression value, SourceType target)
    {
        if (target == null)
        {
            return value;
        }
        value = stripDoubleNot(value);
        boolean targetBool = target == PrimitiveSourceType.BOOLEAN;
        boolean valueBool = value.getType() == PrimitiveSourceType.BOOLEAN;
        if (targetBool == valueBool)
        {
            return value;
        }
        // Exactly one side is boolean here: bridge the JVM's int-as-boolean representation.
        if (valueBool && isIntegralType(target))
        {
            // a boolean expression stored into an int-like slot: `cond ? 1 : 0`
            return new TernaryExpr(value, LiteralExpr.ofInt(1), LiteralExpr.ofInt(0), target);
        }
        if (targetBool && isIntegralType(value.getType()))
        {
            // an int-like value stored into a boolean slot: `value != 0`
            return new BinaryExpr(BinaryOperator.NE, value, LiteralExpr.ofInt(0), PrimitiveSourceType.BOOLEAN);
        }
        return value;
    }

    private boolean isIntegralType(SourceType t)
    {
        return t == PrimitiveSourceType.INT || t == PrimitiveSourceType.SHORT
            || t == PrimitiveSourceType.BYTE || t == PrimitiveSourceType.CHAR;
    }

    /**
     * Collapses {@code !!x} to {@code x}.
     */
    private Expression stripDoubleNot(Expression e)
    {
        while (e instanceof UnaryExpr && ((UnaryExpr) e).getOperator() == UnaryOperator.NOT)
        {
            Expression inner = ((UnaryExpr) e).getOperand();
            if (inner instanceof UnaryExpr && ((UnaryExpr) inner).getOperator() == UnaryOperator.NOT)
            {
                e = ((UnaryExpr) inner).getOperand();
            }
            else
            {
                break;
            }
        }
        return e;
    }

    /**
     * Extracts a boolean constant value (0 or 1) from an IR Value.
     */
    private Integer extractBooleanConstant(Value val)
    {
        if (val instanceof IntConstant)
        {
            IntConstant ic = (IntConstant) val;
            int v = ic.getValue();
            if (v == 0 || v == 1)
            {
                return v;
            }
            return null;
        }

        if (val instanceof SSAValue)
        {
            SSAValue ssaVal = (SSAValue) val;
            IRInstruction def = ssaVal.getDefinition();
            if (def instanceof ConstantInstruction)
            {
                ConstantInstruction constInstr = (ConstantInstruction) def;
                Constant c = constInstr.getConstant();
                if (c instanceof IntConstant)
                {
                    IntConstant ic = (IntConstant) c;
                    int v = ic.getValue();
                    if (v == 0 || v == 1)
                    {
                        return v;
                    }
                }
            }
        }

        return null;
    }

    private Expression tryConvertToBooleanLiteral(Expression expr, Value sourceValue)
    {
        if (expr instanceof LiteralExpr)
        {
            LiteralExpr lit = (LiteralExpr) expr;
            Object litValue = lit.getValue();
            if (litValue instanceof Integer)
            {
                int intVal = (Integer) litValue;
                if (intVal == 0 || intVal == 1)
                {
                    return LiteralExpr.ofBoolean(intVal != 0);
                }
            }
        }

        Integer boolConst = extractBooleanConstant(sourceValue);
        if (boolConst != null)
        {
            return LiteralExpr.ofBoolean(boolConst != 0);
        }

        return null;
    }









    private boolean hasMultipleConditionalPredecessors(IRBlock block)
    {
        Set<IRBlock> preds = block.getPredecessors();
        if (preds.size() <= 1)
        {
            return false;
        }
        int conditionalCount = 0;
        for (IRBlock pred : preds)
        {
            IRInstruction terminator = pred.getTerminator();
            if (terminator instanceof BranchInstruction)
            {
                conditionalCount++;
            }
        }
        return conditionalCount > 1;
    }



    /**
     * Checks if all phi operands are boolean constants (0 or 1).
     */
    private boolean phiReceivesBooleanConstantsOnly(PhiInstruction phi)
    {
        List<Value> operands = phi.getOperands();
        if (operands.isEmpty())
        {
            return false;
        }
        for (Value val : operands)
        {
            Integer constVal = extractBooleanConstant(val);
            if (constVal == null)
            {
                return false;
            }
        }
        return true;
    }



    /**
     * Pre-pass to collect for-loop initializer instructions before block processing.
     */
    private void collectForLoopInitInstructions()
    {
        for (RegionInfo info : analyzer.getForLoopRegions())
        {
            int targetLocal = info.getInductionLocalIndex();
            if (targetLocal < 0) continue;

            context.markAsForLoopInductionLocal(targetLocal);

            IRBlock header = info.getHeader();
            context.markAsForLoopHeader(header);
            LoopAnalysis.Loop loop = info.getLoop();

            for (PhiInstruction phi : header.getPhiInstructions())
            {
                if (isPhiForLocal(phi, targetLocal, loop))
                {
                    context.markAsForLoopInductionPhi(phi.getResult());
                }
            }

            for (IRBlock pred : header.getPredecessors())
            {
                if (loop != null && loop.contains(pred))
                {
                    continue;
                }

                for (IRInstruction instr : pred.getInstructions())
                {
                    if (instr instanceof StoreLocalInstruction)
                    {
                        StoreLocalInstruction store = (StoreLocalInstruction) instr;
                        if (store.getLocalIndex() == targetLocal)
                        {
                            context.markAsForLoopInit(instr);
                        }
                    }
                }
            }
        }
    }

    private boolean isPhiForLocal(PhiInstruction phi, int localIndex, LoopAnalysis.Loop loop)
    {
        for (Map.Entry<IRBlock, Value> entry : phi.getIncomingValues().entrySet())
        {
            IRBlock sourceBlock = entry.getKey();
            Value incomingValue = entry.getValue();

            boolean fromInsideLoop = loop != null && loop.contains(sourceBlock);
            if (fromInsideLoop)
            {
                if (incomingValue instanceof SSAValue)
                {
                    SSAValue ssaVal = (SSAValue) incomingValue;
                    IRInstruction def = ssaVal.getDefinition();
                    if (def instanceof BinaryOpInstruction)
                    {
                        BinaryOpInstruction binOp = (BinaryOpInstruction) def;
                        if ((binOp.getOp() == BinaryOp.ADD || binOp.getOp() == BinaryOp.SUB)
                                && isInductionStep(binOp, phi.getResult()))
                        {
                            return true;
                        }
                    }
                    if (isValueFromLocal(ssaVal, localIndex))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * True when {@code binOp} is {@code phiResult +/- constant} - the canonical induction step.
     */
    private boolean isInductionStep(BinaryOpInstruction binOp, SSAValue phiResult)
    {
        if (phiResult == null)
        {
            return false;
        }
        Value left = binOp.getLeft();
        Value right = binOp.getRight();
        return (left == phiResult && isConstantOperand(right))
                || (right == phiResult && isConstantOperand(left));
    }

    private boolean isValueFromLocal(SSAValue value, int localIndex)
    {
        IRInstruction def = value.getDefinition();
        if (def instanceof LoadLocalInstruction)
        {
            return ((LoadLocalInstruction) def).getLocalIndex() == localIndex;
        }
        if (def instanceof BinaryOpInstruction)
        {
            BinaryOpInstruction binOp = (BinaryOpInstruction) def;
            Value left = binOp.getLeft();
            Value right = binOp.getRight();
            if (left instanceof SSAValue && isValueFromLocal((SSAValue) left, localIndex))
            {
                return true;
            }
            return right instanceof SSAValue && isValueFromLocal((SSAValue) right, localIndex);
        }
        return false;
    }


    /**
     * The phi in {@code mergeBlock} that a diamond over {@code thenBlock}/{@code elseBlock} collapses to a
     * ternary - one incoming value from each arm, each arm a valid single-value producer - or null.
     */
    private PhiInstruction findTernaryPhi(IRBlock thenBlock, IRBlock elseBlock, IRBlock mergeBlock)
    {
        if (thenBlock == null || elseBlock == null || mergeBlock == null)
        {
            return null;
        }

        if (hasMultipleConditionalPredecessors(thenBlock) || hasMultipleConditionalPredecessors(elseBlock))
        {
            return null;
        }

        for (PhiInstruction phi : mergeBlock.getPhiInstructions())
        {
            // A phi taking one input from each arm is a ternary when each arm is either the classic
            // single-value block or a compound but provably pure computation (e.g. `-k` = load +
            // neg). Arms with calls/allocations stay on the single-value rule: their statements are
            // consumed by the collapse, so anything beyond the produced value would be lost.
            Set<IRBlock> incomingBlocks = phi.getIncomingBlocks();
            if (incomingBlocks.size() == 2
                    && incomingBlocks.contains(thenBlock) && incomingBlocks.contains(elseBlock)
                    && phi.getIncoming(thenBlock) != null && phi.getIncoming(elseBlock) != null
                    && isTernaryArm(thenBlock, phi.getIncoming(thenBlock))
                    && isTernaryArm(elseBlock, phi.getIncoming(elseBlock)))
            {
                return phi;
            }
        }

        return null;
    }

    private boolean isTernaryArm(IRBlock block, Value phiIncoming)
    {
        return extractSingleProducedValue(block) != null || isPureComputeArm(block, phiIncoming)
                || isSingleValueComputeArm(block, phiIncoming);
    }

    /**
     * True when the arm computes exactly the phi's incoming value and nothing else observable.
     */
    private boolean isSingleValueComputeArm(IRBlock block, Value phiIncoming)
    {
        if (!(phiIncoming instanceof SSAValue))
        {
            return false;
        }
        Set<IRInstruction> armInstrs = new HashSet<>(block.getInstructions());
        boolean producesIncoming = false;
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr.isTerminator())
            {
                continue;
            }
            if (instr instanceof StoreLocalInstruction)
            {
                if (((StoreLocalInstruction) instr).getValue() != phiIncoming)
                {
                    return false;
                }
                continue;
            }
            if (instr instanceof FieldAccessInstruction && ((FieldAccessInstruction) instr).isStore())
            {
                return false;
            }
            if (instr instanceof ArrayAccessInstruction && ((ArrayAccessInstruction) instr).isStore())
            {
                return false;
            }
            SSAValue result = instr.getResult();
            if (result == null)
            {
                return false;
            }
            if (result == phiIncoming)
            {
                producesIncoming = true;
                continue;
            }
            for (IRInstruction use : result.getUses())
            {
                if (!armInstrs.contains(use))
                {
                    return false;
                }
            }
        }
        return producesIncoming;
    }

    /**
     * True when every instruction in a diamond arm is a side-effect-free computation.
     */
    private boolean isPureComputeArm(IRBlock block, Value phiIncoming)
    {
        for (IRInstruction instr : block.getInstructions())
        {
            if (instr.isTerminator())
            {
                continue;
            }
            if (instr instanceof StoreLocalInstruction)
            {
                if (((StoreLocalInstruction) instr).getValue() != phiIncoming)
                {
                    return false;
                }
            }
            else if (instr instanceof FieldAccessInstruction)
            {
                if (((FieldAccessInstruction) instr).isStore())
                {
                    return false;
                }
            }
            else if (instr instanceof ArrayAccessInstruction)
            {
                if (((ArrayAccessInstruction) instr).isStore())
                {
                    return false;
                }
            }
            else if (!(instr instanceof LoadLocalInstruction
                    || instr instanceof ConstantInstruction
                    || instr instanceof UnaryOpInstruction
                    || instr instanceof BinaryOpInstruction
                    || instr instanceof TypeCheckInstruction
                    || instr instanceof CopyInstruction))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts the single SSA value produced by a block (excluding the goto terminator).
     */
    private SSAValue extractSingleProducedValue(IRBlock block)
    {
        List<IRInstruction> instructions = block.getInstructions();

        SSAValue producedValue = null;
        for (IRInstruction instr : instructions)
        {
            if (instr instanceof SimpleInstruction)
            {
                SimpleInstruction simple = (SimpleInstruction) instr;
                if (simple.getOp() == SimpleOp.GOTO)
                {
                    continue;
                }
            }
            if (instr.isTerminator())
            {
                continue;
            }

            SSAValue result = instr.getResult();
            if (result != null)
            {
                if (producedValue != null)
                {
                    return null;
                }
                producedValue = result;
            }
        }

        return producedValue;
    }

    /**
     * Collapses a ternary PHI pattern to a cached TernaryExpr.
     */
    private void collapseToTernaryPhiExpression(Expression condition, PhiInstruction phi, IRBlock thenBlock, IRBlock elseBlock)
    {
        Value thenValue = phi.getIncoming(thenBlock);
        Value elseValue = phi.getIncoming(elseBlock);

        Expression thenExpr = recoverTernaryArmValue(thenValue, thenBlock);
        Expression elseExpr = recoverTernaryArmValue(elseValue, elseBlock);

        SSAValue phiResult = phi.getResult();
        SourceType type = typeRecoverer.recoverType(phiResult);

        TernaryExpr ternaryExpr = new TernaryExpr(condition, thenExpr, elseExpr, type);

        if (phiResult != null)
        {
            context.getExpressionContext().cacheExpression(phiResult, ternaryExpr);
            context.getExpressionContext().unmarkMaterialized(phiResult);
        }
    }

    /**
     * Recovers a ternary arm's incoming value as an inlined expression.
     */
    private Expression recoverTernaryArmValue(Value value, IRBlock armBlock)
    {
        if (value instanceof SSAValue)
        {
            SSAValue ssa = (SSAValue) value;
            // Only inline the defining expression for a value the arm itself produced (a temp the collapse
            // discards). A value defined before the arm - a pre-existing local like a loop-invariant bound -
            // must be referenced by its name: re-inlining its defining call would recompute it against the
            // current (possibly mutated) operands, changing its meaning.
            boolean definedInArm = ssa.getDefinition() != null && ssa.getDefinition().getBlock() == armBlock;
            boolean wasMaterialized = context.getExpressionContext().isMaterialized(ssa);
            if (definedInArm && wasMaterialized)
            {
                context.getExpressionContext().unmarkMaterialized(ssa);
            }
            // An arm-produced value's statements are discarded by the collapse, so any name the
            // normal path would reference (a materialization temp, or the bare vN fallback for an
            // allocation recoverOperand declines to inline) no longer exists. Derive the expression
            // from the definition itself; fall back to the operand path only when that yields nothing.
            Expression expr = exprRecoverer.recoverOperand(value);
            if (definedInArm && expr instanceof VarRefExpr && !context.getExpressionContext().isMaterialized(ssa))
            {
                Expression direct = exprRecoverer.recover(ssa.getDefinition());
                if (direct != null)
                {
                    expr = direct;
                    context.getExpressionContext().cacheExpression(ssa, direct);
                }
            }
            if (definedInArm && wasMaterialized)
            {
                context.getExpressionContext().markMaterialized(ssa);
            }
            return expr;
        }
        return exprRecoverer.recoverOperand(value);
    }

    /**
     * Gets the appropriate variable name for a local slot index.
     */
    private String getNameForLocalSlot(int localIndex)
    {
        boolean isStatic = context.getIrMethod().isStatic();
        if (!isStatic && localIndex == 0)
        {
            return "this";
        }

        int paramIndex = getParamIndexForSlot(localIndex);
        if (paramIndex >= 0)
        {
            return "arg" + paramIndex;
        }

        return "local" + localIndex;
    }

    /**
     * True if the value is the null reference (directly or via a constant instruction).
     */
    private boolean isNullValue(Value value)
    {
        if (value instanceof NullConstant)
        {
            return true;
        }
        if (value instanceof SSAValue)
        {
            IRInstruction def = ((SSAValue) value).getDefinition();
            if (def instanceof ConstantInstruction)
            {
                return ((ConstantInstruction) def).getConstant() instanceof NullConstant;
            }
        }
        return false;
    }

    /**
     * Resolves a local load/store/phi to its source-variable name via the reaching-definition
     * slot partition, or null when the partition could not place the instruction.
     */
    private String partitionName(IRInstruction instr)
    {
        SlotVariablePartition partition = context.getExpressionContext().getSlotPartition();
        if (partition == null)
        {
            return null;
        }
        if (instr instanceof StoreLocalInstruction)
        {
            return partition.nameForStore((StoreLocalInstruction) instr);
        }
        if (instr instanceof LoadLocalInstruction)
        {
            return partition.nameForLoad((LoadLocalInstruction) instr);
        }
        if (instr instanceof PhiInstruction)
        {
            return partition.nameForPhi((PhiInstruction) instr);
        }
        return null;
    }

    private String getNameForLocalSlotWithType(int localIndex, SourceType valueType)
    {
        boolean isStatic = context.getIrMethod().isStatic();
        if (!isStatic && localIndex == 0)
        {
            return "this";
        }

        int paramIndex = getParamIndexForSlot(localIndex);
        if (paramIndex >= 0)
        {
            return "arg" + paramIndex;
        }

        boolean isPhi = phiSlots.contains(localIndex);
        String typeCategory = isPhi
            ? getCoarseTypeCategory(valueType)
            : getTypeCategory(valueType);
        Map<String, String> categoryMap = slotTypeCategoryToName.computeIfAbsent(localIndex, k -> new LinkedHashMap<>());

        if (categoryMap.containsKey(typeCategory))
        {
            return categoryMap.get(typeCategory);
        }

        if (categoryMap.isEmpty())
        {
            String name = "local" + localIndex;
            if (context.getExpressionContext().isDeclared(name))
            {
                name = generateUniqueLocalName(localIndex);
            }
            categoryMap.put(typeCategory, name);
            return name;
        }

        int suffix = categoryMap.size();
        String name = "local" + localIndex + "_" + suffix;
        if (context.getExpressionContext().isDeclared(name))
        {
            name = generateUniqueLocalName(localIndex);
        }
        categoryMap.put(typeCategory, name);
        return name;
    }

    private String getCoarseTypeCategory(SourceType type)
    {
        if (type == null)
        {
            return "unknown";
        }
        if (type.isPrimitive())
        {
            // Distinguish by JVM verification width so a slot reused as int vs long
            // (genuinely distinct source variables) is not merged under one name.
            switch (((PrimitiveSourceType) type).getKind())
            {
                case LONG:   return "primitive:long";
                case FLOAT:  return "primitive:float";
                case DOUBLE: return "primitive:double";
                default:     return "primitive:int";
            }
        }
        if (type instanceof ArraySourceType)
        {
            return "array";
        }
        return "object";
    }

    private String generateUniqueLocalName(int baseIndex)
    {
        String baseName = "local" + baseIndex;
        int suffix = 2;
        String candidate = baseName + "_" + suffix;
        while (context.getExpressionContext().isDeclared(candidate))
        {
            suffix++;
            candidate = baseName + "_" + suffix;
        }
        return candidate;
    }

    /**
     * Gets the parameter index for a given local slot.
     */
    private int getParamIndexForSlot(int slot)
    {
        IRMethod method = context.getIrMethod();
        boolean isStatic = method.isStatic();

        if (!isStatic && slot == 0)
        {
            return -1;
        }

        String descriptor = method.getDescriptor();
        if (descriptor == null)
        {
            return isStatic ? slot : slot - 1;
        }

        List<String> paramTypes = parseParameterTypes(descriptor);
        int currentSlot = isStatic ? 0 : 1;

        for (int paramIndex = 0; paramIndex < paramTypes.size(); paramIndex++)
        {
            String paramType = paramTypes.get(paramIndex);
            int slotsForParam = 1;
            if ("J".equals(paramType) || "D".equals(paramType))
            {
                slotsForParam = 2;
            }

            if (slot >= currentSlot && slot < currentSlot + slotsForParam)
            {
                return paramIndex;
            }
            currentSlot += slotsForParam;
        }

        return -1;
    }

    private String getTypeCategory(SourceType type)
    {
        if (type == null)
        {
            return "unknown";
        }
        if (type.isPrimitive())
        {
            // Split primitives by JVM verification type so a slot reused across
            // incompatible widths (e.g. int vs long) gets distinct source variables.
            // boolean/byte/char/short/int share the "int" verification type and stay
            // grouped (preserving int<->boolean handling).
            switch (((PrimitiveSourceType) type).getKind())
            {
                case LONG:   return "primitive:long";
                case FLOAT:  return "primitive:float";
                case DOUBLE: return "primitive:double";
                default:     return "primitive:int";
            }
        }
        if (type instanceof ArraySourceType)
        {
            return "array";
        }
        if (type instanceof ReferenceSourceType)
        {
            ReferenceSourceType refType = (ReferenceSourceType) type;
            return refType.getInternalName();
        }
        return "reference";
    }
}
