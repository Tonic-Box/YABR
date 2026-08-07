package com.tonic.analysis.query.eval;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.LineNumberTableAttribute;
import com.tonic.parser.attribute.table.LineNumberTableEntry;
import com.tonic.renamer.hierarchy.ClassHierarchy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-subject evaluation scope with lazy, cached views of a method so repeated accessors (every {@code
 * arg(n)}, {@code instructions}, {@code line}) share one decode/analysis.
 */
public final class EvalContext
{

    private final ClassFile classFile;
    private final MethodEntry method;
    private final EvidenceCollector evidence;
    private final Supplier<ClassHierarchy> hierarchySupplier;
    private final Map<Object, Object> cache = new HashMap<>();

    private CodeWriter codeWriter;
    private List<Instruction> instructions;
    private Map<Integer, Integer> lineByOffset;
    private boolean codeAttempted;
    private IRMethod ir;
    private boolean irAttempted;
    private LoopAnalysis loopAnalysis;
    private boolean loopAttempted;
    private List<IRBlock> blocksByOffset;
    private DefUseChains defUse;
    private boolean defUseAttempted;

    /**
     * Creates a scope with no class hierarchy, leaving subtype checks unresolvable.
     *
     * @param classFile the class in scope
     * @param method the method in scope, or null for a class-scoped context
     * @param evidence the collector match evidence is reported to
     */
    public EvalContext(ClassFile classFile, MethodEntry method, EvidenceCollector evidence)
    {
        this(classFile, method, evidence, null);
    }

    /**
     * Creates a scope; all cached views start empty and are built on first use.
     *
     * @param classFile the class in scope
     * @param method the method in scope, or null for a class-scoped context
     * @param evidence the collector match evidence is reported to
     * @param hierarchySupplier supplies the shared class hierarchy, or null when none is available
     */
    public EvalContext(ClassFile classFile, MethodEntry method, EvidenceCollector evidence, Supplier<ClassHierarchy> hierarchySupplier)
    {
        this.classFile = classFile;
        this.method = method;
        this.evidence = evidence;
        this.hierarchySupplier = hierarchySupplier;
    }

    /**
     * @return the class in scope
     */
    public ClassFile classFile()
    {
        return classFile;
    }

    /**
     * @return the shared class hierarchy used for transitive subtype checks, or null when none was supplied
     */
    public ClassHierarchy hierarchy()
    {
        return hierarchySupplier != null ? hierarchySupplier.get() : null;
    }

    /**
     * @return the method in scope, or null for a class-scoped context
     */
    public MethodEntry method()
    {
        return method;
    }

    /**
     * @return the collector that match evidence is reported to
     */
    public EvidenceCollector evidence()
    {
        return evidence;
    }

    /**
     * @return the decoded code, built on first use, or null for abstract or native methods and class-scoped contexts
     */
    public CodeWriter codeWriter()
    {
        if (!codeAttempted)
        {
            codeAttempted = true;
            if (method != null && method.getCodeAttribute() != null)
            {
                try
                {
                    codeWriter = new CodeWriter(method);
                }
                catch (Exception e)
                {
                    codeWriter = null;
                }
            }
        }
        return codeWriter;
    }

    /**
     * @return the decoded instructions, cached, or an empty list when there is no code
     */
    public List<Instruction> instructions()
    {
        if (instructions == null)
        {
            CodeWriter cw = codeWriter();
            instructions = cw != null ? cw.getInstructionList() : Collections.emptyList();
        }
        return instructions;
    }

    /**
     * @return the method lifted to SSA IR, built on first use, or null if it cannot be lifted
     */
    public IRMethod ir()
    {
        if (!irAttempted)
        {
            irAttempted = true;
            if (method != null && method.getCodeAttribute() != null)
            {
                try
                {
                    ir = new SSA(classFile.getConstPool()).lift(method);
                }
                catch (Exception e)
                {
                    ir = null;
                }
            }
        }
        return ir;
    }

    /**
     * @return loop analysis over the SSA IR, built on first use, or null if the method cannot be lifted
     */
    public LoopAnalysis loopAnalysis()
    {
        if (!loopAttempted)
        {
            loopAttempted = true;
            IRMethod m = ir();
            if (m != null)
            {
                try
                {
                    DominatorTree dom = new DominatorTree(m);
                    dom.compute();
                    LoopAnalysis la = new LoopAnalysis(m, dom);
                    la.compute();
                    loopAnalysis = la;
                }
                catch (Exception e)
                {
                    loopAnalysis = null;
                }
            }
        }
        return loopAnalysis;
    }

    /**
     * @return def-use chains over the SSA IR, built on first use, or null if the method cannot be lifted
     */
    public DefUseChains defUse()
    {
        if (!defUseAttempted)
        {
            defUseAttempted = true;
            IRMethod m = ir();
            if (m != null)
            {
                try
                {
                    DefUseChains chains = new DefUseChains(m);
                    chains.compute();
                    defUse = chains;
                }
                catch (Exception e)
                {
                    defUse = null;
                }
            }
        }
        return defUse;
    }

    /**
     * Finds the IR block containing a bytecode offset - the block with the greatest start offset
     * not past it.
     *
     * @param offset the bytecode offset
     * @return the containing block, or null when the method cannot be lifted
     */
    public IRBlock blockForOffset(int offset)
    {
        IRMethod m = ir();
        if (m == null)
        {
            return null;
        }
        if (blocksByOffset == null)
        {
            blocksByOffset = new ArrayList<>(m.getBlocksInOrder());
            blocksByOffset.sort(Comparator.comparingInt(IRBlock::getBytecodeOffset));
        }
        IRBlock best = null;
        for (IRBlock b : blocksByOffset)
        {
            int start = b.getBytecodeOffset();
            if (start >= 0 && start <= offset)
            {
                best = b;
            }
            else if (start > offset)
            {
                break;
            }
        }
        return best;
    }

    /**
     * Resolves a bytecode offset to a source line using the nearest preceding LineNumberTable entry.
     *
     * @param offset the bytecode offset
     * @return the source line, or -1 when no LineNumberTable is present
     */
    public int lineForOffset(int offset)
    {
        if (lineByOffset == null)
        {
            lineByOffset = buildLineTable();
        }
        int best = -1;
        int bestPc = -1;
        for (Map.Entry<Integer, Integer> e : lineByOffset.entrySet())
        {
            int pc = e.getKey();
            if (pc <= offset && pc > bestPc)
            {
                bestPc = pc;
                best = e.getValue();
            }
        }
        return best;
    }

    /**
     * Computes a value once per key and caches it for the life of this context.
     *
     * @param <T> the cached value type
     * @param key identifies the cached value
     * @param supplier produces the value on first use
     * @return the cached value
     */
    @SuppressWarnings("unchecked")
    public <T> T memo(Object key, java.util.function.Supplier<T> supplier)
    {
        return (T) cache.computeIfAbsent(key, k -> supplier.get());
    }

    private Map<Integer, Integer> buildLineTable()
    {
        Map<Integer, Integer> map = new HashMap<>();
        if (method == null || method.getCodeAttribute() == null)
        {
            return map;
        }
        for (Attribute attr : method.getCodeAttribute().getAttributes())
        {
            if (attr instanceof LineNumberTableAttribute)
            {
                List<LineNumberTableEntry> table = ((LineNumberTableAttribute) attr).getLineNumberTable();
                if (table != null)
                {
                    for (LineNumberTableEntry e : table)
                    {
                        map.put(e.getStartPc(), e.getLineNumber());
                    }
                }
            }
        }
        return map;
    }
}
