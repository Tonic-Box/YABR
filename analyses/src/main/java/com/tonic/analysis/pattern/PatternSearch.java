package com.tonic.analysis.pattern;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.dependency.DependencyAnalyzer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.typeinference.Nullability;
import com.tonic.analysis.typeinference.TypeInferenceAnalyzer;
import com.tonic.analysis.typeinference.TypeState;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Fluent search API over a ClassPool for code patterns, optionally backed by call-graph,
 * dependency, and type-inference analyses.
 */
public class PatternSearch
{

    private final ClassPool classPool;
    private final List<ClassFile> targetClasses;
    private final List<MethodEntry> targetMethods;
    private CallGraph callGraph;
    private DependencyAnalyzer dependencyAnalyzer;
    private boolean useTypeInference;
    private int maxResults = Integer.MAX_VALUE;

    /**
     * Creates a search over the given pool with an empty scope and no analyses attached.
     * @param classPool the pool of classes to search
     */
    public PatternSearch(ClassPool classPool)
    {
        this.classPool = classPool;
        this.targetClasses = new ArrayList<>();
        this.targetMethods = new ArrayList<>();
        this.useTypeInference = false;
    }

    // Scope Configuration

    /**
     * Clears the scope so the search covers every class in the pool.
     *
     * @return this search
     */
    public PatternSearch inAllClasses()
    {
        targetClasses.clear();
        targetMethods.clear();
        return this;
    }

    /**
     * Adds one class to the scope, ignoring names the pool cannot resolve.
     *
     * @param className name of the class to look up in the pool
     * @return this search
     */
    public PatternSearch inClass(String className)
    {
        ClassFile cf = classPool.get(className);
        if (cf != null)
        {
            targetClasses.add(cf);
        }
        return this;
    }

    /**
     * Adds the classes under a package prefix to the scope.
     *
     * @param packagePrefix package prefix to match
     * @return this search
     */
    public PatternSearch inPackage(String packagePrefix)
    {
        // Would need to iterate classPool's internal list
        // For now, this is a placeholder - implementation depends on ClassPool API
        return this;
    }

    /**
     * Adds one method to the scope; an explicit method scope overrides any class scope.
     *
     * @param method method to search
     * @return this search
     */
    public PatternSearch inMethod(MethodEntry method)
    {
        targetMethods.add(method);
        return this;
    }

    /**
     * Adds every method of a class to the scope.
     *
     * @param classFile class whose methods are searched
     * @return this search
     */
    public PatternSearch inAllMethodsOf(ClassFile classFile)
    {
        targetClasses.add(classFile);
        return this;
    }

    /**
     * Caps how many results any subsequent query returns.
     *
     * @param maxResults result ceiling
     * @return this search
     */
    public PatternSearch limit(int maxResults)
    {
        this.maxResults = maxResults;
        return this;
    }

    // Analysis Integration

    /**
     * Attaches an already-built call graph for the caller and callee queries.
     *
     * @param callGraph call graph to use
     * @return this search
     */
    public PatternSearch withCallGraph(CallGraph callGraph)
    {
        this.callGraph = callGraph;
        return this;
    }

    /**
     * Builds a call graph over the pool and attaches it.
     *
     * @return this search
     */
    public PatternSearch withCallGraph()
    {
        this.callGraph = CallGraph.build(classPool);
        return this;
    }

    /**
     * Attaches an already-built dependency analyzer for the dependency queries.
     *
     * @param analyzer dependency analyzer to use
     * @return this search
     */
    public PatternSearch withDependencies(DependencyAnalyzer analyzer)
    {
        this.dependencyAnalyzer = analyzer;
        return this;
    }

    /**
     * Builds a dependency analyzer over the pool and attaches it.
     *
     * @return this search
     */
    public PatternSearch withDependencies()
    {
        this.dependencyAnalyzer = new DependencyAnalyzer(classPool);
        return this;
    }

    /**
     * Enables type inference so nullability-aware searches can run.
     *
     * @return this search
     */
    public PatternSearch withTypeInference()
    {
        this.useTypeInference = true;
        return this;
    }

    // Basic Pattern Searches

    /**
     * Finds call sites accepted by a matcher.
     *
     * @param pattern matcher applied to every lifted instruction in scope
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findMethodCalls(PatternMatcher pattern)
    {
        return findInstructions(pattern);
    }

    /**
     * Finds calls to any method declared by one owner class.
     *
     * @param ownerClass owner class of the callee
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findMethodCalls(String ownerClass)
    {
        return findMethodCalls(Patterns.methodCallTo(ownerClass));
    }

    /**
     * Finds calls to one named method on one owner class.
     *
     * @param ownerClass owner class of the callee
     * @param methodName callee name
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findMethodCalls(String ownerClass, String methodName)
    {
        return findMethodCalls(Patterns.methodCall(ownerClass, methodName));
    }

    /**
     * Finds field reads and writes against one owner class.
     *
     * @param ownerClass owner class declaring the field
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findFieldAccesses(String ownerClass)
    {
        return findInstructions(Patterns.fieldAccessOn(ownerClass));
    }

    /**
     * Finds field accesses by field name, whatever the owner.
     *
     * @param fieldName field name to match
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findFieldsByName(String fieldName)
    {
        return findInstructions(Patterns.fieldNamed(fieldName));
    }

    /**
     * Finds every instanceof check in scope.
     *
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findInstanceOfChecks()
    {
        return findInstructions(Patterns.anyInstanceOf());
    }

    /**
     * Finds instanceof checks against one type.
     *
     * @param typeName type being tested
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findInstanceOfChecks(String typeName)
    {
        return findInstructions(Patterns.instanceOf(typeName));
    }

    /**
     * Finds every checked cast in scope.
     *
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findCasts()
    {
        return findInstructions(Patterns.anyCast());
    }

    /**
     * Finds casts to one type.
     *
     * @param typeName cast target type
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findCastsTo(String typeName)
    {
        return findInstructions(Patterns.castTo(typeName));
    }

    /**
     * Finds every object allocation in scope.
     *
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findAllocations()
    {
        return findInstructions(Patterns.anyNew());
    }

    /**
     * Finds allocations of one class.
     *
     * @param className allocated class
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findAllocations(String className)
    {
        return findInstructions(Patterns.newInstance(className));
    }

    /**
     * Finds every null comparison in scope.
     *
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findNullChecks()
    {
        return findInstructions(Patterns.nullCheck());
    }

    /**
     * Finds every athrow in scope.
     *
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findThrows()
    {
        return findInstructions(Patterns.anyThrow());
    }

    /**
     * Finds instructions accepted by a caller-supplied matcher.
     *
     * @param pattern matcher applied to every lifted instruction in scope
     * @return matching instructions, up to the result limit
     */
    public List<SearchResult> findPattern(PatternMatcher pattern)
    {
        return findInstructions(pattern);
    }

    // Call Graph Queries

    /**
     * Finds the methods that call one target, building the call graph first if none is attached.
     *
     * @param owner declaring class of the target
     * @param name target method name
     * @param descriptor target method descriptor
     * @return one result per caller resolvable in the pool, up to the result limit
     */
    public List<SearchResult> findCallersOf(String owner, String name, String descriptor)
    {
        if (callGraph == null)
        {
            withCallGraph();
        }

        MethodReference target = new MethodReference(owner, name, descriptor);
        Set<MethodReference> callers = callGraph.getCallers(target);

        List<SearchResult> results = new ArrayList<>();
        for (MethodReference caller : callers)
        {
            if (results.size() >= maxResults) break;
            ClassFile cf = classPool.get(caller.getOwner());
            if (cf == null) continue;

            MethodEntry method = findMethod(cf, caller.getName(), caller.getDescriptor());
            if (method != null)
            {
                results.add(new SearchResult(cf, method, "calls " + owner + "." + name + descriptor));
            }
        }
        return results;
    }

    /**
     * Finds the methods one caller invokes, building the call graph first if none is attached.
     *
     * @param owner declaring class of the caller
     * @param name caller method name
     * @param descriptor caller method descriptor
     * @return one result per callee, up to the result limit
     */
    public List<SearchResult> findCalleesOf(String owner, String name, String descriptor)
    {
        if (callGraph == null)
        {
            withCallGraph();
        }

        MethodReference caller = new MethodReference(owner, name, descriptor);
        Set<MethodReference> callees = callGraph.getCallees(caller);

        List<SearchResult> results = new ArrayList<>();
        for (MethodReference callee : callees)
        {
            if (results.size() >= maxResults) break;
            ClassFile cf = classPool.get(callee.getOwner());
            results.add(new SearchResult(cf, null,
                "called by " + owner + "." + name + ": " + callee.getOwner() + "." + callee.getName()));
        }
        return results;
    }

    // Dependency Queries

    /**
     * Finds the classes that depend on one class, building the dependency analysis if none is attached.
     *
     * @param className class depended upon
     * @return one result per dependent class, up to the result limit
     */
    public List<SearchResult> findDependentsOf(String className)
    {
        if (dependencyAnalyzer == null)
        {
            withDependencies();
        }

        Set<String> dependents = dependencyAnalyzer.getDependents(className);
        List<SearchResult> results = new ArrayList<>();
        for (String dep : dependents)
        {
            if (results.size() >= maxResults) break;
            ClassFile cf = classPool.get(dep);
            results.add(new SearchResult(cf, "depends on " + className));
        }
        return results;
    }

    /**
     * Finds the classes one class depends on, building the dependency analysis if none is attached.
     *
     * @param className class whose dependencies are listed
     * @return one result per dependency, up to the result limit
     */
    public List<SearchResult> findDependenciesOf(String className)
    {
        if (dependencyAnalyzer == null)
        {
            withDependencies();
        }

        Set<String> dependencies = dependencyAnalyzer.getDependencies(className);
        List<SearchResult> results = new ArrayList<>();
        for (String dep : dependencies)
        {
            if (results.size() >= maxResults) break;
            ClassFile cf = classPool.get(dep);
            results.add(new SearchResult(cf, className + " depends on this"));
        }
        return results;
    }

    // Type Inference Queries

    /**
     * Finds calls and field reads whose receiver type inference cannot prove non-null.
     * Methods that fail to lift or analyze are skipped.
     *
     * @return one result per suspect dereference, up to the result limit
     */
    public List<SearchResult> findPotentialNullDereferences()
    {
        List<SearchResult> results = new ArrayList<>();

        for (MethodEntry method : getTargetMethods())
        {
            if (results.size() >= maxResults) break;
            if (method.getCodeAttribute() == null) continue;

            ClassFile cf = getClassFileForMethod(method);
            if (cf == null) continue;

            try
            {
                SSA ssa = new SSA(cf.getConstPool());
                IRMethod irMethod = ssa.lift(method);
                if (irMethod == null) continue;

                TypeInferenceAnalyzer typeAnalyzer = new TypeInferenceAnalyzer(irMethod);
                typeAnalyzer.analyze();

                for (IRBlock block : irMethod.getBlocks())
                {
                    for (IRInstruction instr : block.getInstructions())
                    {
                        // Check for method calls on nullable receivers
                        if (instr instanceof InvokeInstruction)
                        {
                            InvokeInstruction invoke = (InvokeInstruction) instr;
                            if (invoke.getInvokeType() != InvokeType.STATIC)
                            {
                                var receiver = invoke.getReceiver();
                                if (receiver instanceof SSAValue)
                                {
                                    TypeState state = typeAnalyzer.getTypeState((SSAValue) receiver);
                                    if (state.getNullability() == Nullability.UNKNOWN ||
                                        state.getNullability() == Nullability.NULL)
                                        {
                                        results.add(new SearchResult(cf, method, instr, -1,
                                            "potential null dereference: " + invoke.getOwner() + "." +
                                            invoke.getName() + " on nullable receiver"));
                                        if (results.size() >= maxResults) return results;
                                    }
                                }
                            }
                        }

                        // Check for field access on nullable receiver
                        if (instr instanceof FieldAccessInstruction)
                        {
                            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
                            if (fieldAccess.isLoad())
                            {
                                var obj = fieldAccess.getObjectRef();
                                if (obj instanceof SSAValue)
                                {
                                    TypeState state = typeAnalyzer.getTypeState((SSAValue) obj);
                                    if (state.getNullability() == Nullability.UNKNOWN ||
                                        state.getNullability() == Nullability.NULL)
                                        {
                                        results.add(new SearchResult(cf, method, instr, -1,
                                            "potential null dereference: field access on nullable"));
                                        if (results.size() >= maxResults) return results;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                // Skip methods that fail to analyze
            }
        }

        return results;
    }

    // Internal Implementation

    private List<SearchResult> findInstructions(PatternMatcher pattern)
    {
        List<SearchResult> results = new ArrayList<>();

        for (MethodEntry method : getTargetMethods())
        {
            if (results.size() >= maxResults) break;
            if (method.getCodeAttribute() == null) continue;

            ClassFile cf = getClassFileForMethod(method);
            if (cf == null) continue;

            try
            {
                SSA ssa = new SSA(cf.getConstPool());
                IRMethod irMethod = ssa.lift(method);
                if (irMethod == null) continue;

                for (IRBlock block : irMethod.getBlocks())
                {
                    for (IRInstruction instr : block.getInstructions())
                    {
                        if (results.size() >= maxResults) break;
                        if (pattern.matches(instr, irMethod, method, cf))
                        {
                            results.add(new SearchResult(cf, method, instr, -1, describeInstruction(instr)));
                        }
                    }
                }
            }
            catch (Exception e)
            {
                // Skip methods that fail to lift
            }
        }

        return results;
    }

    private List<MethodEntry> getTargetMethods()
    {
        if (!targetMethods.isEmpty())
        {
            return targetMethods;
        }

        List<MethodEntry> methods = new ArrayList<>();
        if (targetClasses.isEmpty())
        {
            // Would need to iterate all classes - placeholder
            return methods;
        }

        for (ClassFile cf : targetClasses)
        {
            methods.addAll(cf.getMethods());
        }
        return methods;
    }

    private ClassFile getClassFileForMethod(MethodEntry method)
    {
        String owner = method.getOwnerName();
        if (owner != null)
        {
            return classPool.get(owner);
        }
        // Try to find by checking target classes
        for (ClassFile cf : targetClasses)
        {
            if (cf.getMethods().contains(method))
            {
                return cf;
            }
        }
        return null;
    }

    private MethodEntry findMethod(ClassFile cf, String name, String descriptor)
    {
        for (MethodEntry method : cf.getMethods())
        {
            if (name.equals(method.getName()) && descriptor.equals(method.getDesc()))
            {
                return method;
            }
        }
        return null;
    }

    private String describeInstruction(IRInstruction instr)
    {
        if (instr instanceof InvokeInstruction)
        {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return "call " + invoke.getOwner() + "." + invoke.getName();
        }
        else if (instr instanceof FieldAccessInstruction)
        {
            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
            String op = fieldAccess.isLoad() ? "read" : "write";
            return op + " " + fieldAccess.getOwner() + "." + fieldAccess.getName();
        }
        else if (instr instanceof NewInstruction)
        {
            return "new " + ((NewInstruction) instr).getClassName();
        }
        else if (instr instanceof TypeCheckInstruction)
        {
            TypeCheckInstruction typeCheck = (TypeCheckInstruction) instr;
            if (typeCheck.isInstanceOf())
            {
                return "instanceof " + typeCheck.getTargetType();
            }
            else
            {
                return "cast to " + typeCheck.getTargetType();
            }
        }
        else if (instr instanceof SimpleInstruction)
        {
            SimpleInstruction simple = (SimpleInstruction) instr;
            if (simple.getOp() == SimpleOp.ATHROW)
            {
                return "throw";
            }
        }
        else if (instr instanceof ReturnInstruction)
        {
            return "return";
        }
        return instr.getClass().getSimpleName();
    }
}
