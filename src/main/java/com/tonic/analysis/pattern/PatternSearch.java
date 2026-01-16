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
 * High-level API for searching code patterns across a ClassPool.
 *
 * Provides fluent interface for finding:
 * - Method calls (with filtering by owner, name, descriptor)
 * - Field accesses
 * - Type checks (instanceof, casts)
 * - Object allocations
 * - Null checks
 * - And more...
 *
 * Can leverage Call Graph, Dependency Analysis, and Type Inference for semantic queries.
 */
public class PatternSearch {

    private final ClassPool classPool;
    private final List<ClassFile> targetClasses;
    private final List<MethodEntry> targetMethods;
    private CallGraph callGraph;
    private DependencyAnalyzer dependencyAnalyzer;
    private boolean useTypeInference;
    private int maxResults = Integer.MAX_VALUE;

    public PatternSearch(ClassPool classPool) {
        this.classPool = classPool;
        this.targetClasses = new ArrayList<>();
        this.targetMethods = new ArrayList<>();
        this.useTypeInference = false;
    }

    // ===== Scope Configuration =====

    /**
     * Search in all classes in the pool.
     */
    public PatternSearch inAllClasses() {
        targetClasses.clear();
        targetMethods.clear();
        return this;
    }

    /**
     * Search in a specific class.
     */
    public PatternSearch inClass(String className) {
        ClassFile cf = classPool.get(className);
        if (cf != null) {
            targetClasses.add(cf);
        }
        return this;
    }

    /**
     * Search in classes matching a package prefix.
     */
    public PatternSearch inPackage(String packagePrefix) {
        // Would need to iterate classPool's internal list
        // For now, this is a placeholder - implementation depends on ClassPool API
        return this;
    }

    /**
     * Search in a specific method.
     */
    public PatternSearch inMethod(MethodEntry method) {
        targetMethods.add(method);
        return this;
    }

    /**
     * Search in all methods of a class.
     */
    public PatternSearch inAllMethodsOf(ClassFile classFile) {
        targetClasses.add(classFile);
        return this;
    }

    /**
     * Limit the number of results returned.
     */
    public PatternSearch limit(int maxResults) {
        this.maxResults = maxResults;
        return this;
    }

    // ===== Analysis Integration =====

    /**
     * Use existing call graph for semantic queries.
     */
    public PatternSearch withCallGraph(CallGraph callGraph) {
        this.callGraph = callGraph;
        return this;
    }

    /**
     * Build and use call graph for semantic queries.
     */
    public PatternSearch withCallGraph() {
        this.callGraph = CallGraph.build(classPool);
        return this;
    }

    /**
     * Use existing dependency analyzer.
     */
    public PatternSearch withDependencies(DependencyAnalyzer analyzer) {
        this.dependencyAnalyzer = analyzer;
        return this;
    }

    /**
     * Build and use dependency analysis.
     */
    public PatternSearch withDependencies() {
        this.dependencyAnalyzer = new DependencyAnalyzer(classPool);
        return this;
    }

    /**
     * Enable type inference for nullability-aware searches.
     */
    public PatternSearch withTypeInference() {
        this.useTypeInference = true;
        return this;
    }

    // ===== Basic Pattern Searches =====

    /**
     * Find all method calls matching the given pattern.
     */
    public List<SearchResult> findMethodCalls(PatternMatcher pattern) {
        return findInstructions(pattern);
    }

    /**
     * Find all calls to methods on a specific class.
     */
    public List<SearchResult> findMethodCalls(String ownerClass) {
        return findMethodCalls(Patterns.methodCallTo(ownerClass));
    }

    /**
     * Find all calls to a specific method.
     */
    public List<SearchResult> findMethodCalls(String ownerClass, String methodName) {
        return findMethodCalls(Patterns.methodCall(ownerClass, methodName));
    }

    /**
     * Find all field accesses (reads and writes).
     */
    public List<SearchResult> findFieldAccesses(String ownerClass) {
        return findInstructions(Patterns.fieldAccessOn(ownerClass));
    }

    /**
     * Find all field accesses by name.
     */
    public List<SearchResult> findFieldsByName(String fieldName) {
        return findInstructions(Patterns.fieldNamed(fieldName));
    }

    /**
     * Find all instanceof checks.
     */
    public List<SearchResult> findInstanceOfChecks() {
        return findInstructions(Patterns.anyInstanceOf());
    }

    /**
     * Find instanceof checks for a specific type.
     */
    public List<SearchResult> findInstanceOfChecks(String typeName) {
        return findInstructions(Patterns.instanceOf(typeName));
    }

    /**
     * Find all type casts.
     */
    public List<SearchResult> findCasts() {
        return findInstructions(Patterns.anyCast());
    }

    /**
     * Find casts to a specific type.
     */
    public List<SearchResult> findCastsTo(String typeName) {
        return findInstructions(Patterns.castTo(typeName));
    }

    /**
     * Find all object allocations.
     */
    public List<SearchResult> findAllocations() {
        return findInstructions(Patterns.anyNew());
    }

    /**
     * Find allocations of a specific class.
     */
    public List<SearchResult> findAllocations(String className) {
        return findInstructions(Patterns.newInstance(className));
    }

    /**
     * Find all null checks.
     */
    public List<SearchResult> findNullChecks() {
        return findInstructions(Patterns.nullCheck());
    }

    /**
     * Find all throw statements.
     */
    public List<SearchResult> findThrows() {
        return findInstructions(Patterns.anyThrow());
    }

    /**
     * Find instructions matching a custom pattern.
     */
    public List<SearchResult> findPattern(PatternMatcher pattern) {
        return findInstructions(pattern);
    }

    // ===== Call Graph Queries =====

    /**
     * Find all callers of a specific method.
     * Requires call graph to be built.
     */
    public List<SearchResult> findCallersOf(String owner, String name, String descriptor) {
        if (callGraph == null) {
            withCallGraph();
        }

        MethodReference target = new MethodReference(owner, name, descriptor);
        Set<MethodReference> callers = callGraph.getCallers(target);

        List<SearchResult> results = new ArrayList<>();
        for (MethodReference caller : callers) {
            if (results.size() >= maxResults) break;
            ClassFile cf = classPool.get(caller.getOwner());
            if (cf == null) continue;

            MethodEntry method = findMethod(cf, caller.getName(), caller.getDescriptor());
            if (method != null) {
                results.add(new SearchResult(cf, method,
                    "calls " + owner + "." + name + descriptor));
            }
        }
        return results;
    }

    /**
     * Find all methods called by a specific method.
     * Requires call graph to be built.
     */
    public List<SearchResult> findCalleesOf(String owner, String name, String descriptor) {
        if (callGraph == null) {
            withCallGraph();
        }

        MethodReference caller = new MethodReference(owner, name, descriptor);
        Set<MethodReference> callees = callGraph.getCallees(caller);

        List<SearchResult> results = new ArrayList<>();
        for (MethodReference callee : callees) {
            if (results.size() >= maxResults) break;
            ClassFile cf = classPool.get(callee.getOwner());
            results.add(new SearchResult(cf, null,
                "called by " + owner + "." + name + ": " + callee.getOwner() + "." + callee.getName()));
        }
        return results;
    }

    // ===== Dependency Queries =====

    /**
     * Find classes that depend on a specific class.
     */
    public List<SearchResult> findDependentsOf(String className) {
        if (dependencyAnalyzer == null) {
            withDependencies();
        }

        Set<String> dependents = dependencyAnalyzer.getDependents(className);
        List<SearchResult> results = new ArrayList<>();
        for (String dep : dependents) {
            if (results.size() >= maxResults) break;
            ClassFile cf = classPool.get(dep);
            results.add(new SearchResult(cf, "depends on " + className));
        }
        return results;
    }

    /**
     * Find classes that a specific class depends on.
     */
    public List<SearchResult> findDependenciesOf(String className) {
        if (dependencyAnalyzer == null) {
            withDependencies();
        }

        Set<String> dependencies = dependencyAnalyzer.getDependencies(className);
        List<SearchResult> results = new ArrayList<>();
        for (String dep : dependencies) {
            if (results.size() >= maxResults) break;
            ClassFile cf = classPool.get(dep);
            results.add(new SearchResult(cf, className + " depends on this"));
        }
        return results;
    }

    // ===== Type Inference Queries =====

    /**
     * Find potential null pointer dereferences.
     * Uses type inference to find method calls or field accesses on potentially null values.
     */
    public List<SearchResult> findPotentialNullDereferences() {
        List<SearchResult> results = new ArrayList<>();

        for (MethodEntry method : getTargetMethods()) {
            if (results.size() >= maxResults) break;
            if (method.getCodeAttribute() == null) continue;

            ClassFile cf = getClassFileForMethod(method);
            if (cf == null) continue;

            try {
                SSA ssa = new SSA(cf.getConstPool());
                IRMethod irMethod = ssa.lift(method);
                if (irMethod == null) continue;

                TypeInferenceAnalyzer typeAnalyzer = new TypeInferenceAnalyzer(irMethod);
                typeAnalyzer.analyze();

                for (IRBlock block : irMethod.getBlocks()) {
                    for (IRInstruction instr : block.getInstructions()) {
                        // Check for method calls on nullable receivers
                        if (instr instanceof InvokeInstruction) {
                            InvokeInstruction invoke = (InvokeInstruction) instr;
                            if (invoke.getInvokeType() != InvokeType.STATIC) {
                                var receiver = invoke.getReceiver();
                                if (receiver instanceof SSAValue) {
                                    TypeState state = typeAnalyzer.getTypeState(
                                        (SSAValue) receiver);
                                    if (state.getNullability() == Nullability.UNKNOWN ||
                                        state.getNullability() == Nullability.NULL) {
                                        results.add(new SearchResult(cf, method, instr, -1,
                                            "potential null dereference: " + invoke.getOwner() + "." +
                                            invoke.getName() + " on nullable receiver"));
                                        if (results.size() >= maxResults) return results;
                                    }
                                }
                            }
                        }

                        // Check for field access on nullable receiver
                        if (instr instanceof FieldAccessInstruction) {
                            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
                            if (fieldAccess.isLoad()) {
                                var obj = fieldAccess.getObjectRef();
                                if (obj instanceof SSAValue) {
                                    TypeState state = typeAnalyzer.getTypeState(
                                        (SSAValue) obj);
                                    if (state.getNullability() == Nullability.UNKNOWN ||
                                        state.getNullability() == Nullability.NULL) {
                                        results.add(new SearchResult(cf, method, instr, -1,
                                            "potential null dereference: field access on nullable"));
                                        if (results.size() >= maxResults) return results;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Skip methods that fail to analyze
            }
        }

        return results;
    }

    // ===== Internal Implementation =====

    private List<SearchResult> findInstructions(PatternMatcher pattern) {
        List<SearchResult> results = new ArrayList<>();

        for (MethodEntry method : getTargetMethods()) {
            if (results.size() >= maxResults) break;
            if (method.getCodeAttribute() == null) continue;

            ClassFile cf = getClassFileForMethod(method);
            if (cf == null) continue;

            try {
                SSA ssa = new SSA(cf.getConstPool());
                IRMethod irMethod = ssa.lift(method);
                if (irMethod == null) continue;

                for (IRBlock block : irMethod.getBlocks()) {
                    for (IRInstruction instr : block.getInstructions()) {
                        if (results.size() >= maxResults) break;
                        if (pattern.matches(instr, irMethod, method, cf)) {
                            results.add(new SearchResult(cf, method, instr, -1,
                                describeInstruction(instr)));
                        }
                    }
                }
            } catch (Exception e) {
                // Skip methods that fail to lift
            }
        }

        return results;
    }

    private List<MethodEntry> getTargetMethods() {
        if (!targetMethods.isEmpty()) {
            return targetMethods;
        }

        List<MethodEntry> methods = new ArrayList<>();
        if (targetClasses.isEmpty()) {
            // Would need to iterate all classes - placeholder
            return methods;
        }

        for (ClassFile cf : targetClasses) {
            methods.addAll(cf.getMethods());
        }
        return methods;
    }

    private ClassFile getClassFileForMethod(MethodEntry method) {
        String owner = method.getOwnerName();
        if (owner != null) {
            return classPool.get(owner);
        }
        // Try to find by checking target classes
        for (ClassFile cf : targetClasses) {
            if (cf.getMethods().contains(method)) {
                return cf;
            }
        }
        return null;
    }

    private MethodEntry findMethod(ClassFile cf, String name, String descriptor) {
        for (MethodEntry method : cf.getMethods()) {
            if (name.equals(method.getName()) && descriptor.equals(method.getDesc())) {
                return method;
            }
        }
        return null;
    }

    private String describeInstruction(IRInstruction instr) {
        if (instr instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return "call " + invoke.getOwner() + "." + invoke.getName();
        } else if (instr instanceof FieldAccessInstruction) {
            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
            String op = fieldAccess.isLoad() ? "read" : "write";
            return op + " " + fieldAccess.getOwner() + "." + fieldAccess.getName();
        } else if (instr instanceof NewInstruction) {
            return "new " + ((NewInstruction) instr).getClassName();
        } else if (instr instanceof TypeCheckInstruction) {
            TypeCheckInstruction typeCheck = (TypeCheckInstruction) instr;
            if (typeCheck.isInstanceOf()) {
                return "instanceof " + typeCheck.getTargetType();
            } else {
                return "cast to " + typeCheck.getTargetType();
            }
        } else if (instr instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) instr;
            if (simple.getOp() == SimpleOp.ATHROW) {
                return "throw";
            }
        } else if (instr instanceof ReturnInstruction) {
            return "return";
        }
        return instr.getClass().getSimpleName();
    }
}
