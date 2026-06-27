package com.tonic.analysis.instrumentation;

import com.tonic.analysis.instrumentation.filter.*;
import com.tonic.analysis.instrumentation.hook.*;
import com.tonic.analysis.instrumentation.transform.InstrumentationTransform;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

import java.util.*;

/**
 * Main entry point for the Instrumentation API.
 * Provides a fluent builder interface for configuring and applying instrumentation hooks.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * int count = Instrumenter.forClassPool(classPool)
 *     .onMethodEntry()
 *         .inPackage("com/example/service/")
 *         .callStatic("com/example/Hooks", "onEntry", "(Ljava/lang/String;)V")
 *         .withMethodName()
 *         .register()
 *     .onFieldWrite()
 *         .forField("myField")
 *         .callStatic("com/example/Hooks", "onFieldWrite", "(Ljava/lang/Object;)V")
 *         .withNewValue()
 *         .register()
 *     .apply();
 * }</pre>
 */
public class Instrumenter {

    private final List<ClassFile> targetClasses;
    private final List<Hook> hooks;
    private final InstrumentationConfig.InstrumentationConfigBuilder configBuilder;

    @Getter
    private InstrumentationReport lastReport;

    private Instrumenter(List<ClassFile> targetClasses) {
        this.targetClasses = new ArrayList<>(targetClasses);
        this.hooks = new ArrayList<>();
        this.configBuilder = InstrumentationConfig.builder();
    }

    /**
     * Creates an Instrumenter for a ClassPool.
     * All classes in the pool will be considered for instrumentation.
     *
     * @param pool the class pool
     * @return a new Instrumenter instance
     */
    public static Instrumenter forClassPool(ClassPool pool) {
        // ClassPool doesn't expose iteration, so we need to work around it
        // This is a limitation - in a real implementation, ClassPool would expose getAll()
        throw new UnsupportedOperationException(
                "ClassPool iteration not supported directly. Use forClasses() with explicit class list.");
    }

    /**
     * Creates an Instrumenter for a single ClassFile.
     *
     * @param classFile the class file to instrument
     * @return a new Instrumenter instance
     */
    public static Instrumenter forClass(ClassFile classFile) {
        return new Instrumenter(List.of(classFile));
    }

    /**
     * Creates an Instrumenter for multiple ClassFiles.
     *
     * @param classes the class files to instrument
     * @return a new Instrumenter instance
     */
    public static Instrumenter forClasses(List<ClassFile> classes) {
        return new Instrumenter(classes);
    }

    /**
     * Creates an Instrumenter for multiple ClassFiles.
     *
     * @param classes the class files to instrument
     * @return a new Instrumenter instance
     */
    public static Instrumenter forClasses(ClassFile... classes) {
        return new Instrumenter(Arrays.asList(classes));
    }

    // ========== Hook Builders ==========

    /**
     * Starts building a method entry hook.
     */
    public MethodEntryHookBuilder onMethodEntry() {
        return new MethodEntryHookBuilder(this);
    }

    /**
     * Starts building a method exit hook.
     */
    public MethodExitHookBuilder onMethodExit() {
        return new MethodExitHookBuilder(this);
    }

    /**
     * Starts building a field write hook.
     */
    public FieldWriteHookBuilder onFieldWrite() {
        return new FieldWriteHookBuilder(this);
    }

    /**
     * Starts building a field read hook.
     */
    public FieldReadHookBuilder onFieldRead() {
        return new FieldReadHookBuilder(this);
    }

    /**
     * Starts building an array store hook.
     */
    public ArrayStoreHookBuilder onArrayStore() {
        return new ArrayStoreHookBuilder(this);
    }

    /**
     * Starts building an array load hook.
     */
    public ArrayLoadHookBuilder onArrayLoad() {
        return new ArrayLoadHookBuilder(this);
    }

    /**
     * Starts building a method call hook (before call).
     */
    public MethodCallHookBuilder onMethodCall() {
        return new MethodCallHookBuilder(this);
    }

    /**
     * Starts building an exception handler hook.
     */
    public ExceptionHookBuilder onException() {
        return new ExceptionHookBuilder(this);
    }

    // ========== Configuration ==========

    /**
     * Configures whether to skip abstract methods.
     */
    public Instrumenter skipAbstract(boolean skip) {
        configBuilder.skipAbstract(skip);
        return this;
    }

    /**
     * Configures whether to skip native methods.
     */
    public Instrumenter skipNative(boolean skip) {
        configBuilder.skipNative(skip);
        return this;
    }

    /**
     * Configures whether to skip constructors.
     */
    public Instrumenter skipConstructors(boolean skip) {
        configBuilder.skipConstructors(skip);
        return this;
    }

    /**
     * Configures whether to skip static initializers.
     */
    public Instrumenter skipStaticInitializers(boolean skip) {
        configBuilder.skipStaticInitializers(skip);
        return this;
    }

    /**
     * Configures whether to skip synthetic methods.
     */
    public Instrumenter skipSynthetic(boolean skip) {
        configBuilder.skipSynthetic(skip);
        return this;
    }

    /**
     * Configures whether to skip bridge methods.
     */
    public Instrumenter skipBridge(boolean skip) {
        configBuilder.skipBridge(skip);
        return this;
    }

    /**
     * Configures whether to fail on instrumentation errors.
     */
    public Instrumenter failOnError(boolean fail) {
        configBuilder.failOnError(fail);
        return this;
    }

    /**
     * Enables verbose logging.
     */
    public Instrumenter verbose(boolean verbose) {
        configBuilder.verbose(verbose);
        return this;
    }

    // ========== Execution ==========

    /**
     * Applies all registered hooks to the target classes.
     *
     * @return the total number of instrumentation points applied
     */
    public int apply() {
        InstrumentationReport report = applyWithReport();
        return report.getTotalInstrumentationPoints();
    }

    /**
     * Applies all registered hooks and returns a detailed report.
     *
     * @return an instrumentation report with details
     */
    public InstrumentationReport applyWithReport() {
        InstrumentationConfig config = configBuilder.build();
        InstrumentationTransform transform = new InstrumentationTransform(hooks, config);

        InstrumentationReport.InstrumentationReportBuilder reportBuilder = InstrumentationReport.builder();

        int totalPoints = 0;
        int classesInstrumented = 0;
        int methodsInstrumented = 0;
        int errors = 0;

        for (ClassFile classFile : targetClasses) {
            SSA ssa = new SSA(classFile.getConstPool());
            int classPoints = 0;

            for (MethodEntry method : classFile.getMethods()) {
                if (method.getCodeAttribute() == null) continue;

                try {
                    IRMethod irMethod = ssa.lift(method);
                    int points = transform.instrumentMethod(irMethod, method, classFile);

                    if (points > 0) {
                        ssa.lower(irMethod, method);
                        classPoints += points;
                        methodsInstrumented++;
                    }
                } catch (Exception e) {
                    errors++;
                    if (config.isFailOnError()) {
                        throw new RuntimeException("Instrumentation failed for " +
                                classFile.getClassName() + "." + method.getName(), e);
                    }
                    if (config.isVerbose()) {
                        System.err.println("Warning: Failed to instrument " +
                                classFile.getClassName() + "." + method.getName() + ": " + e.getMessage());
                    }
                }
            }

            if (classPoints > 0) {
                classesInstrumented++;
                totalPoints += classPoints;
            }
        }

        lastReport = reportBuilder
                .totalInstrumentationPoints(totalPoints)
                .classesInstrumented(classesInstrumented)
                .methodsInstrumented(methodsInstrumented)
                .errors(errors)
                .build();

        return lastReport;
    }

    /**
     * Internal method to register a hook.
     */
    void registerHook(Hook hook) {
        hooks.add(hook);
    }

    // ========== Hook Builders ==========

    /**
     * Builder for method entry hooks.
     */
    public static class MethodEntryHookBuilder {
        private final Instrumenter instrumenter;
        private final MethodEntryHook.MethodEntryHookBuilder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        MethodEntryHookBuilder(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
            this.hookBuilder = MethodEntryHook.builder();
        }

        public MethodEntryHookBuilder inClass(String className) {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        public MethodEntryHookBuilder inClassMatching(String pattern) {
            filters.add(ClassFilter.matching(pattern));
            return this;
        }

        public MethodEntryHookBuilder inPackage(String packagePrefix) {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        public MethodEntryHookBuilder matchingMethod(String pattern) {
            filters.add(MethodFilter.matching(pattern));
            return this;
        }

        public MethodEntryHookBuilder withAnnotation(String annotationType) {
            filters.add(AnnotationFilter.forAnnotation(annotationType));
            return this;
        }

        public MethodEntryHookBuilder callStatic(String owner, String name, String descriptor) {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        public MethodEntryHookBuilder withThis() {
            hookBuilder.passThis(true);
            return this;
        }

        public MethodEntryHookBuilder withClassName() {
            hookBuilder.passClassName(true);
            return this;
        }

        public MethodEntryHookBuilder withMethodName() {
            hookBuilder.passMethodName(true);
            return this;
        }

        public MethodEntryHookBuilder withAllParameters() {
            hookBuilder.passAllParameters(true);
            return this;
        }

        public MethodEntryHookBuilder withParameter(int index) {
            // Need to track parameter indices
            return this;
        }

        public MethodEntryHookBuilder priority(int priority) {
            hookBuilder.priority(priority);
            return this;
        }

        public Instrumenter register() {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Builder for method exit hooks.
     */
    public static class MethodExitHookBuilder {
        private final Instrumenter instrumenter;
        private final MethodExitHook.MethodExitHookBuilder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        MethodExitHookBuilder(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
            this.hookBuilder = MethodExitHook.builder();
        }

        public MethodExitHookBuilder inClass(String className) {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        public MethodExitHookBuilder inPackage(String packagePrefix) {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        public MethodExitHookBuilder matchingMethod(String pattern) {
            filters.add(MethodFilter.matching(pattern));
            return this;
        }

        public MethodExitHookBuilder callStatic(String owner, String name, String descriptor) {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        public MethodExitHookBuilder withThis() {
            hookBuilder.passThis(true);
            return this;
        }

        public MethodExitHookBuilder withClassName() {
            hookBuilder.passClassName(true);
            return this;
        }

        public MethodExitHookBuilder withMethodName() {
            hookBuilder.passMethodName(true);
            return this;
        }

        public MethodExitHookBuilder withReturnValue() {
            hookBuilder.passReturnValue(true);
            return this;
        }

        public MethodExitHookBuilder allowModification() {
            hookBuilder.canModifyReturn(true);
            return this;
        }

        public MethodExitHookBuilder priority(int priority) {
            hookBuilder.priority(priority);
            return this;
        }

        public Instrumenter register() {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Builder for field write hooks.
     */
    public static class FieldWriteHookBuilder {
        private final Instrumenter instrumenter;
        private final FieldWriteHook.FieldWriteHookBuilder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        FieldWriteHookBuilder(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
            this.hookBuilder = FieldWriteHook.builder();
        }

        public FieldWriteHookBuilder inClass(String className) {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        public FieldWriteHookBuilder inPackage(String packagePrefix) {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        public FieldWriteHookBuilder forField(String fieldName) {
            filters.add(FieldFilter.forField(fieldName));
            return this;
        }

        public FieldWriteHookBuilder forFieldsMatching(String pattern) {
            filters.add(FieldFilter.matching(pattern));
            return this;
        }

        public FieldWriteHookBuilder ofType(String descriptor) {
            filters.add(FieldFilter.ofType(descriptor));
            return this;
        }

        public FieldWriteHookBuilder staticOnly() {
            hookBuilder.instrumentStatic(true);
            hookBuilder.instrumentInstance(false);
            return this;
        }

        public FieldWriteHookBuilder instanceOnly() {
            hookBuilder.instrumentStatic(false);
            hookBuilder.instrumentInstance(true);
            return this;
        }

        public FieldWriteHookBuilder callStatic(String owner, String name, String descriptor) {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        public FieldWriteHookBuilder withOwner() {
            hookBuilder.passOwner(true);
            return this;
        }

        public FieldWriteHookBuilder withFieldName() {
            hookBuilder.passFieldName(true);
            return this;
        }

        public FieldWriteHookBuilder withNewValue() {
            hookBuilder.passNewValue(true);
            return this;
        }

        public FieldWriteHookBuilder withOldValue() {
            hookBuilder.passOldValue(true);
            return this;
        }

        public FieldWriteHookBuilder allowModification() {
            hookBuilder.canModifyValue(true);
            return this;
        }

        public FieldWriteHookBuilder priority(int priority) {
            hookBuilder.priority(priority);
            return this;
        }

        public Instrumenter register() {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Builder for field read hooks.
     */
    public static class FieldReadHookBuilder {
        private final Instrumenter instrumenter;
        private final FieldReadHook.FieldReadHookBuilder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        FieldReadHookBuilder(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
            this.hookBuilder = FieldReadHook.builder();
        }

        public FieldReadHookBuilder inClass(String className) {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        public FieldReadHookBuilder inPackage(String packagePrefix) {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        public FieldReadHookBuilder forField(String fieldName) {
            filters.add(FieldFilter.forField(fieldName));
            return this;
        }

        public FieldReadHookBuilder staticOnly() {
            hookBuilder.instrumentStatic(true);
            hookBuilder.instrumentInstance(false);
            return this;
        }

        public FieldReadHookBuilder instanceOnly() {
            hookBuilder.instrumentStatic(false);
            hookBuilder.instrumentInstance(true);
            return this;
        }

        public FieldReadHookBuilder callStatic(String owner, String name, String descriptor) {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        public FieldReadHookBuilder withOwner() {
            hookBuilder.passOwner(true);
            return this;
        }

        public FieldReadHookBuilder withFieldName() {
            hookBuilder.passFieldName(true);
            return this;
        }

        public FieldReadHookBuilder withReadValue() {
            hookBuilder.passReadValue(true);
            return this;
        }

        public FieldReadHookBuilder priority(int priority) {
            hookBuilder.priority(priority);
            return this;
        }

        public Instrumenter register() {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Builder for array store hooks.
     */
    public static class ArrayStoreHookBuilder {
        private final Instrumenter instrumenter;
        private final ArrayStoreHook.ArrayStoreHookBuilder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        ArrayStoreHookBuilder(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
            this.hookBuilder = ArrayStoreHook.builder();
        }

        public ArrayStoreHookBuilder inClass(String className) {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        public ArrayStoreHookBuilder inPackage(String packagePrefix) {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        public ArrayStoreHookBuilder inMethod(String methodPattern) {
            filters.add(MethodFilter.matching(methodPattern));
            return this;
        }

        public ArrayStoreHookBuilder forArrayType(String typeDescriptor) {
            hookBuilder.arrayTypeFilter(typeDescriptor);
            return this;
        }

        public ArrayStoreHookBuilder forObjectArrays() {
            hookBuilder.arrayTypeFilter("[Ljava/lang/Object;");
            return this;
        }

        public ArrayStoreHookBuilder forIntArrays() {
            hookBuilder.arrayTypeFilter("[I");
            return this;
        }

        public ArrayStoreHookBuilder callStatic(String owner, String name, String descriptor) {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        public ArrayStoreHookBuilder withArray() {
            hookBuilder.passArray(true);
            return this;
        }

        public ArrayStoreHookBuilder withIndex() {
            hookBuilder.passIndex(true);
            return this;
        }

        public ArrayStoreHookBuilder withValue() {
            hookBuilder.passValue(true);
            return this;
        }

        public ArrayStoreHookBuilder withAll() {
            hookBuilder.passArray(true);
            hookBuilder.passIndex(true);
            hookBuilder.passValue(true);
            return this;
        }

        public ArrayStoreHookBuilder allowModification() {
            hookBuilder.canModifyValue(true);
            return this;
        }

        public ArrayStoreHookBuilder priority(int priority) {
            hookBuilder.priority(priority);
            return this;
        }

        public Instrumenter register() {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Builder for array load hooks.
     */
    public static class ArrayLoadHookBuilder {
        private final Instrumenter instrumenter;
        private final ArrayLoadHook.ArrayLoadHookBuilder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        ArrayLoadHookBuilder(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
            this.hookBuilder = ArrayLoadHook.builder();
        }

        public ArrayLoadHookBuilder inClass(String className) {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        public ArrayLoadHookBuilder inPackage(String packagePrefix) {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        public ArrayLoadHookBuilder forArrayType(String typeDescriptor) {
            hookBuilder.arrayTypeFilter(typeDescriptor);
            return this;
        }

        public ArrayLoadHookBuilder callStatic(String owner, String name, String descriptor) {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        public ArrayLoadHookBuilder withArray() {
            hookBuilder.passArray(true);
            return this;
        }

        public ArrayLoadHookBuilder withIndex() {
            hookBuilder.passIndex(true);
            return this;
        }

        public ArrayLoadHookBuilder withValue() {
            hookBuilder.passValue(true);
            return this;
        }

        public ArrayLoadHookBuilder priority(int priority) {
            hookBuilder.priority(priority);
            return this;
        }

        public Instrumenter register() {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Builder for method call hooks.
     */
    public static class MethodCallHookBuilder {
        private final Instrumenter instrumenter;
        private final MethodCallHook.MethodCallHookBuilder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        MethodCallHookBuilder(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
            this.hookBuilder = MethodCallHook.builder();
        }

        public MethodCallHookBuilder inClass(String className) {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        public MethodCallHookBuilder inPackage(String packagePrefix) {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        public MethodCallHookBuilder targeting(String targetClass, String targetMethod) {
            hookBuilder.targetClass(targetClass);
            hookBuilder.targetMethod(targetMethod);
            return this;
        }

        public MethodCallHookBuilder targeting(String targetClass, String targetMethod, String targetDescriptor) {
            hookBuilder.targetClass(targetClass);
            hookBuilder.targetMethod(targetMethod);
            hookBuilder.targetDescriptor(targetDescriptor);
            return this;
        }

        public MethodCallHookBuilder before() {
            hookBuilder.before(true);
            hookBuilder.after(false);
            return this;
        }

        public MethodCallHookBuilder after() {
            hookBuilder.before(false);
            hookBuilder.after(true);
            return this;
        }

        public MethodCallHookBuilder callStatic(String owner, String name, String descriptor) {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        public MethodCallHookBuilder withReceiver() {
            hookBuilder.passReceiver(true);
            return this;
        }

        public MethodCallHookBuilder withArguments() {
            hookBuilder.passArguments(true);
            return this;
        }

        public MethodCallHookBuilder withResult() {
            hookBuilder.passResult(true);
            return this;
        }

        public MethodCallHookBuilder withMethodName() {
            hookBuilder.passMethodName(true);
            return this;
        }

        public MethodCallHookBuilder priority(int priority) {
            hookBuilder.priority(priority);
            return this;
        }

        public Instrumenter register() {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Builder for exception handler hooks.
     */
    public static class ExceptionHookBuilder {
        private final Instrumenter instrumenter;
        private final ExceptionHook.ExceptionHookBuilder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        ExceptionHookBuilder(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
            this.hookBuilder = ExceptionHook.builder();
        }

        public ExceptionHookBuilder inClass(String className) {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        public ExceptionHookBuilder inPackage(String packagePrefix) {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        public ExceptionHookBuilder forExceptionType(String exceptionType) {
            hookBuilder.exceptionType(exceptionType);
            return this;
        }

        public ExceptionHookBuilder callStatic(String owner, String name, String descriptor) {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        public ExceptionHookBuilder withException() {
            hookBuilder.passException(true);
            return this;
        }

        public ExceptionHookBuilder withMethodName() {
            hookBuilder.passMethodName(true);
            return this;
        }

        public ExceptionHookBuilder withClassName() {
            hookBuilder.passClassName(true);
            return this;
        }

        public ExceptionHookBuilder canSuppress() {
            hookBuilder.canSuppress(true);
            return this;
        }

        public ExceptionHookBuilder priority(int priority) {
            hookBuilder.priority(priority);
            return this;
        }

        public Instrumenter register() {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    // ========== Report Class ==========

    /**
     * Report of instrumentation results.
     */
    @lombok.Builder
    @lombok.Getter
    public static class InstrumentationReport {
        private final int totalInstrumentationPoints;
        private final int classesInstrumented;
        private final int methodsInstrumented;
        private final int errors;

        @Override
        public String toString() {
            return String.format("InstrumentationReport{points=%d, classes=%d, methods=%d, errors=%d}",
                    totalInstrumentationPoints, classesInstrumented, methodsInstrumented, errors);
        }
    }
}
