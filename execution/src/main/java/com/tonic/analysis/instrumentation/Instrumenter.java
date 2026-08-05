package com.tonic.analysis.instrumentation;

import com.tonic.analysis.instrumentation.filter.*;
import com.tonic.analysis.instrumentation.hook.*;
import com.tonic.analysis.instrumentation.transform.InstrumentationTransform;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.util.*;

/**
 * Fluent entry point for registering instrumentation hooks and applying them to a set of classes.
 */
public class Instrumenter
{

    private final List<ClassFile> targetClasses;
    private final List<Hook> hooks;
    private final InstrumentationConfig.Builder configBuilder;

    private InstrumentationReport lastReport;

    private Instrumenter(List<ClassFile> targetClasses)
    {
        this.targetClasses = new ArrayList<>(targetClasses);
        this.hooks = new ArrayList<>();
        this.configBuilder = InstrumentationConfig.builder();
    }

    /**
     * @return the last report
     */
    public InstrumentationReport getLastReport()
    {
        return lastReport;
    }

    /**
     * Creates an instrumenter targeting every class in a pool.
     *
     * @param pool the class pool
     * @return a new instrumenter
     */
    public static Instrumenter forClassPool(ClassPool pool)
    {
        return forClasses(pool.getClasses());
    }

    /**
     * Creates an instrumenter targeting one class.
     *
     * @param classFile the class file to instrument
     * @return a new instrumenter
     */
    public static Instrumenter forClass(ClassFile classFile)
    {
        return new Instrumenter(List.of(classFile));
    }

    /**
     * Creates an instrumenter targeting a list of classes.
     *
     * @param classes the class files to instrument
     * @return a new instrumenter
     */
    public static Instrumenter forClasses(List<ClassFile> classes)
    {
        return new Instrumenter(classes);
    }

    /**
     * Creates an instrumenter targeting the given classes.
     *
     * @param classes the class files to instrument
     * @return a new instrumenter
     */
    public static Instrumenter forClasses(ClassFile... classes)
    {
        return new Instrumenter(Arrays.asList(classes));
    }

    // Hook Builders

    /**
     * @return a builder for a hook that runs on entry to matching methods
     */
    public MethodEntryHookBuilder onMethodEntry()
    {
        return new MethodEntryHookBuilder(this);
    }

    /**
     * @return a builder for a hook that runs at every return of matching methods
     */
    public MethodExitHookBuilder onMethodExit()
    {
        return new MethodExitHookBuilder(this);
    }

    /**
     * @return a builder for a hook that runs at matching field writes
     */
    public FieldWriteHookBuilder onFieldWrite()
    {
        return new FieldWriteHookBuilder(this);
    }

    /**
     * @return a builder for a hook that runs at matching field reads
     */
    public FieldReadHookBuilder onFieldRead()
    {
        return new FieldReadHookBuilder(this);
    }

    /**
     * @return a builder for a hook that runs at matching array stores
     */
    public ArrayStoreHookBuilder onArrayStore()
    {
        return new ArrayStoreHookBuilder(this);
    }

    /**
     * @return a builder for a hook that runs at matching array loads
     */
    public ArrayLoadHookBuilder onArrayLoad()
    {
        return new ArrayLoadHookBuilder(this);
    }

    /**
     * @return a builder for a hook that runs around matching call sites
     */
    public MethodCallHookBuilder onMethodCall()
    {
        return new MethodCallHookBuilder(this);
    }

    /**
     * @return a builder for a hook that runs in matching exception handlers
     */
    public ExceptionHookBuilder onException()
    {
        return new ExceptionHookBuilder(this);
    }

    // Configuration

    /**
     * Sets whether abstract methods are left alone.
     *
     * @param skip true to skip them
     * @return this instrumenter
     */
    public Instrumenter skipAbstract(boolean skip)
    {
        configBuilder.skipAbstract(skip);
        return this;
    }

    /**
     * Sets whether native methods are left alone.
     *
     * @param skip true to skip them
     * @return this instrumenter
     */
    public Instrumenter skipNative(boolean skip)
    {
        configBuilder.skipNative(skip);
        return this;
    }

    /**
     * Sets whether constructors are left alone.
     *
     * @param skip true to skip them
     * @return this instrumenter
     */
    public Instrumenter skipConstructors(boolean skip)
    {
        configBuilder.skipConstructors(skip);
        return this;
    }

    /**
     * Sets whether static initializers are left alone.
     *
     * @param skip true to skip them
     * @return this instrumenter
     */
    public Instrumenter skipStaticInitializers(boolean skip)
    {
        configBuilder.skipStaticInitializers(skip);
        return this;
    }

    /**
     * Sets whether synthetic methods are left alone.
     *
     * @param skip true to skip them
     * @return this instrumenter
     */
    public Instrumenter skipSynthetic(boolean skip)
    {
        configBuilder.skipSynthetic(skip);
        return this;
    }

    /**
     * Sets whether bridge methods are left alone.
     *
     * @param skip true to skip them
     * @return this instrumenter
     */
    public Instrumenter skipBridge(boolean skip)
    {
        configBuilder.skipBridge(skip);
        return this;
    }

    /**
     * Sets whether a failure to instrument a method is rethrown instead of counted.
     *
     * @param fail true to rethrow
     * @return this instrumenter
     */
    public Instrumenter failOnError(boolean fail)
    {
        configBuilder.failOnError(fail);
        return this;
    }

    /**
     * Sets whether instrumentation failures are printed to stderr.
     *
     * @param verbose true to print
     * @return this instrumenter
     */
    public Instrumenter verbose(boolean verbose)
    {
        configBuilder.verbose(verbose);
        return this;
    }

    // Execution

    /**
     * Applies all registered hooks to the target classes.
     *
     * @return the total number of instrumentation points applied
     * @throws RuntimeException if a method fails to instrument and failOnError is set
     */
    public int apply()
    {
        InstrumentationReport report = applyWithReport();
        return report.getTotalInstrumentationPoints();
    }

    /**
     * Applies all registered hooks and records the result as the last report.
     *
     * @return counts of points, classes, methods, and failures
     * @throws RuntimeException if a method fails to instrument and failOnError is set
     */
    public InstrumentationReport applyWithReport()
    {
        InstrumentationConfig config = configBuilder.build();
        InstrumentationTransform transform = new InstrumentationTransform(hooks, config);

        InstrumentationReport.Builder reportBuilder = InstrumentationReport.builder();

        int totalPoints = 0;
        int classesInstrumented = 0;
        int methodsInstrumented = 0;
        int errors = 0;

        for (ClassFile classFile : targetClasses)
        {
            SSA ssa = new SSA(classFile.getConstPool());
            int classPoints = 0;

            for (MethodEntry method : classFile.getMethods())
            {
                if (method.getCodeAttribute() == null) continue;

                try
                {
                    IRMethod irMethod = ssa.lift(method);
                    int points = transform.instrumentMethod(irMethod, method, classFile);

                    if (points > 0)
                    {
                        ssa.lower(irMethod, method);
                        classPoints += points;
                        methodsInstrumented++;
                    }
                }
                catch (Exception e)
                {
                    errors++;
                    if (config.isFailOnError())
                    {
                        throw new RuntimeException("Instrumentation failed for " +
                                classFile.getClassName() + "." + method.getName(), e);
                    }
                    if (config.isVerbose())
                    {
                        System.err.println("Warning: Failed to instrument " +
                                classFile.getClassName() + "." + method.getName() + ": " + e.getMessage());
                    }
                }
            }

            if (classPoints > 0)
            {
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
     * Adds a built hook to the set applied by the next run.
     *
     * @param hook the hook to add
     */
    void registerHook(Hook hook)
    {
        hooks.add(hook);
    }

    // Hook Builders

    /**
     * Fluent builder for a method entry hook.
     */
    public static class MethodEntryHookBuilder
    {
        private final Instrumenter instrumenter;
        private final MethodEntryHook.Builder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        MethodEntryHookBuilder(Instrumenter instrumenter)
        {
            this.instrumenter = instrumenter;
            this.hookBuilder = MethodEntryHook.builder();
        }

        /**
         * Restricts this hook to a single class.
         * @param className the internal class name
         * @return this builder
         */
        public MethodEntryHookBuilder inClass(String className)
        {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        /**
         * Restricts this hook to classes whose name matches a pattern.
         * @param pattern the class name pattern
         * @return this builder
         */
        public MethodEntryHookBuilder inClassMatching(String pattern)
        {
            filters.add(ClassFilter.matching(pattern));
            return this;
        }

        /**
         * Restricts this hook to classes under a package.
         * @param packagePrefix the internal package name prefix
         * @return this builder
         */
        public MethodEntryHookBuilder inPackage(String packagePrefix)
        {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        /**
         * Restricts this hook to methods whose name matches a pattern.
         * @param pattern the method name pattern
         * @return this builder
         */
        public MethodEntryHookBuilder matchingMethod(String pattern)
        {
            filters.add(MethodFilter.matching(pattern));
            return this;
        }

        /**
         * Restricts this hook to methods carrying an annotation.
         * @param annotationType the annotation type
         * @return this builder
         */
        public MethodEntryHookBuilder withAnnotation(String annotationType)
        {
            filters.add(AnnotationFilter.forAnnotation(annotationType));
            return this;
        }

        /**
         * Sets the static method the hook dispatches to.
         * @param owner internal name of the class declaring the hook method
         * @param name the hook method name
         * @param descriptor the hook method descriptor
         * @return this builder
         */
        public MethodEntryHookBuilder callStatic(String owner, String name, String descriptor)
        {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        /**
         * Passes the receiver as a hook argument.
         * @return this builder
         */
        public MethodEntryHookBuilder withThis()
        {
            hookBuilder.passThis(true);
            return this;
        }

        /**
         * Passes the enclosing class name as a hook argument.
         * @return this builder
         */
        public MethodEntryHookBuilder withClassName()
        {
            hookBuilder.passClassName(true);
            return this;
        }

        /**
         * Passes the instrumented method name as a hook argument.
         * @return this builder
         */
        public MethodEntryHookBuilder withMethodName()
        {
            hookBuilder.passMethodName(true);
            return this;
        }

        /**
         * Passes every parameter of the instrumented method as hook arguments.
         * @return this builder
         */
        public MethodEntryHookBuilder withAllParameters()
        {
            hookBuilder.passAllParameters(true);
            return this;
        }

        /**
         * Sets the ordering priority of this hook against others at the same site.
         * @param priority the priority value
         * @return this builder
         */
        public MethodEntryHookBuilder priority(int priority)
        {
            hookBuilder.priority(priority);
            return this;
        }

        /**
         * Attaches the collected filters and registers the hook.
         * @return the instrumenter this builder came from
         */
        public Instrumenter register()
        {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Fluent builder for a method exit hook.
     */
    public static class MethodExitHookBuilder
    {
        private final Instrumenter instrumenter;
        private final MethodExitHook.Builder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        MethodExitHookBuilder(Instrumenter instrumenter)
        {
            this.instrumenter = instrumenter;
            this.hookBuilder = MethodExitHook.builder();
        }

        /**
         * Restricts this hook to a single class.
         * @param className the internal class name
         * @return this builder
         */
        public MethodExitHookBuilder inClass(String className)
        {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        /**
         * Restricts this hook to classes under a package.
         * @param packagePrefix the internal package name prefix
         * @return this builder
         */
        public MethodExitHookBuilder inPackage(String packagePrefix)
        {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        /**
         * Restricts this hook to methods whose name matches a pattern.
         * @param pattern the method name pattern
         * @return this builder
         */
        public MethodExitHookBuilder matchingMethod(String pattern)
        {
            filters.add(MethodFilter.matching(pattern));
            return this;
        }

        /**
         * Sets the static method the hook dispatches to.
         * @param owner internal name of the class declaring the hook method
         * @param name the hook method name
         * @param descriptor the hook method descriptor
         * @return this builder
         */
        public MethodExitHookBuilder callStatic(String owner, String name, String descriptor)
        {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        /**
         * Passes the receiver as a hook argument.
         * @return this builder
         */
        public MethodExitHookBuilder withThis()
        {
            hookBuilder.passThis(true);
            return this;
        }

        /**
         * Passes the enclosing class name as a hook argument.
         * @return this builder
         */
        public MethodExitHookBuilder withClassName()
        {
            hookBuilder.passClassName(true);
            return this;
        }

        /**
         * Passes the instrumented method name as a hook argument.
         * @return this builder
         */
        public MethodExitHookBuilder withMethodName()
        {
            hookBuilder.passMethodName(true);
            return this;
        }

        /**
         * Passes the value being returned as a hook argument.
         * @return this builder
         */
        public MethodExitHookBuilder withReturnValue()
        {
            hookBuilder.passReturnValue(true);
            return this;
        }

        /**
         * Lets the hook replace the returned value.
         * @return this builder
         */
        public MethodExitHookBuilder allowModification()
        {
            hookBuilder.canModifyReturn(true);
            return this;
        }

        /**
         * Sets the ordering priority of this hook against others at the same site.
         * @param priority the priority value
         * @return this builder
         */
        public MethodExitHookBuilder priority(int priority)
        {
            hookBuilder.priority(priority);
            return this;
        }

        /**
         * Attaches the collected filters and registers the hook.
         * @return the instrumenter this builder came from
         */
        public Instrumenter register()
        {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Fluent builder for a field write hook.
     */
    public static class FieldWriteHookBuilder
    {
        private final Instrumenter instrumenter;
        private final FieldWriteHook.Builder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        FieldWriteHookBuilder(Instrumenter instrumenter)
        {
            this.instrumenter = instrumenter;
            this.hookBuilder = FieldWriteHook.builder();
        }

        /**
         * Restricts this hook to a single class.
         * @param className the internal class name
         * @return this builder
         */
        public FieldWriteHookBuilder inClass(String className)
        {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        /**
         * Restricts this hook to classes under a package.
         * @param packagePrefix the internal package name prefix
         * @return this builder
         */
        public FieldWriteHookBuilder inPackage(String packagePrefix)
        {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        /**
         * Restricts this hook to one field name.
         * @param fieldName the field name
         * @return this builder
         */
        public FieldWriteHookBuilder forField(String fieldName)
        {
            filters.add(FieldFilter.forField(fieldName));
            return this;
        }

        /**
         * Restricts this hook to fields whose name matches a pattern.
         * @param pattern the field name pattern
         * @return this builder
         */
        public FieldWriteHookBuilder forFieldsMatching(String pattern)
        {
            filters.add(FieldFilter.matching(pattern));
            return this;
        }

        /**
         * Restricts this hook to fields of one type.
         * @param descriptor the field descriptor
         * @return this builder
         */
        public FieldWriteHookBuilder ofType(String descriptor)
        {
            filters.add(FieldFilter.ofType(descriptor));
            return this;
        }

        /**
         * Instruments static field writes only.
         * @return this builder
         */
        public FieldWriteHookBuilder staticOnly()
        {
            hookBuilder.instrumentStatic(true);
            hookBuilder.instrumentInstance(false);
            return this;
        }

        /**
         * Instruments instance field writes only.
         * @return this builder
         */
        public FieldWriteHookBuilder instanceOnly()
        {
            hookBuilder.instrumentStatic(false);
            hookBuilder.instrumentInstance(true);
            return this;
        }

        /**
         * Sets the static method the hook dispatches to.
         * @param owner internal name of the class declaring the hook method
         * @param name the hook method name
         * @param descriptor the hook method descriptor
         * @return this builder
         */
        public FieldWriteHookBuilder callStatic(String owner, String name, String descriptor)
        {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        /**
         * Passes the field owner as a hook argument.
         * @return this builder
         */
        public FieldWriteHookBuilder withOwner()
        {
            hookBuilder.passOwner(true);
            return this;
        }

        /**
         * Passes the field name as a hook argument.
         * @return this builder
         */
        public FieldWriteHookBuilder withFieldName()
        {
            hookBuilder.passFieldName(true);
            return this;
        }

        /**
         * Passes the value being written as a hook argument.
         * @return this builder
         */
        public FieldWriteHookBuilder withNewValue()
        {
            hookBuilder.passNewValue(true);
            return this;
        }

        /**
         * Passes the field's prior value as a hook argument.
         * @return this builder
         */
        public FieldWriteHookBuilder withOldValue()
        {
            hookBuilder.passOldValue(true);
            return this;
        }

        /**
         * Lets the hook replace the value written.
         * @return this builder
         */
        public FieldWriteHookBuilder allowModification()
        {
            hookBuilder.canModifyValue(true);
            return this;
        }

        /**
         * Sets the ordering priority of this hook against others at the same site.
         * @param priority the priority value
         * @return this builder
         */
        public FieldWriteHookBuilder priority(int priority)
        {
            hookBuilder.priority(priority);
            return this;
        }

        /**
         * Attaches the collected filters and registers the hook.
         * @return the instrumenter this builder came from
         */
        public Instrumenter register()
        {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Fluent builder for a field read hook.
     */
    public static class FieldReadHookBuilder
    {
        private final Instrumenter instrumenter;
        private final FieldReadHook.Builder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        FieldReadHookBuilder(Instrumenter instrumenter)
        {
            this.instrumenter = instrumenter;
            this.hookBuilder = FieldReadHook.builder();
        }

        /**
         * Restricts this hook to a single class.
         * @param className the internal class name
         * @return this builder
         */
        public FieldReadHookBuilder inClass(String className)
        {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        /**
         * Restricts this hook to classes under a package.
         * @param packagePrefix the internal package name prefix
         * @return this builder
         */
        public FieldReadHookBuilder inPackage(String packagePrefix)
        {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        /**
         * Restricts this hook to one field name.
         * @param fieldName the field name
         * @return this builder
         */
        public FieldReadHookBuilder forField(String fieldName)
        {
            filters.add(FieldFilter.forField(fieldName));
            return this;
        }

        /**
         * Instruments static field reads only.
         * @return this builder
         */
        public FieldReadHookBuilder staticOnly()
        {
            hookBuilder.instrumentStatic(true);
            hookBuilder.instrumentInstance(false);
            return this;
        }

        /**
         * Instruments instance field reads only.
         * @return this builder
         */
        public FieldReadHookBuilder instanceOnly()
        {
            hookBuilder.instrumentStatic(false);
            hookBuilder.instrumentInstance(true);
            return this;
        }

        /**
         * Sets the static method the hook dispatches to.
         * @param owner internal name of the class declaring the hook method
         * @param name the hook method name
         * @param descriptor the hook method descriptor
         * @return this builder
         */
        public FieldReadHookBuilder callStatic(String owner, String name, String descriptor)
        {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        /**
         * Passes the field owner as a hook argument.
         * @return this builder
         */
        public FieldReadHookBuilder withOwner()
        {
            hookBuilder.passOwner(true);
            return this;
        }

        /**
         * Passes the field name as a hook argument.
         * @return this builder
         */
        public FieldReadHookBuilder withFieldName()
        {
            hookBuilder.passFieldName(true);
            return this;
        }

        /**
         * Passes the value read as a hook argument.
         * @return this builder
         */
        public FieldReadHookBuilder withReadValue()
        {
            hookBuilder.passReadValue(true);
            return this;
        }

        /**
         * Sets the ordering priority of this hook against others at the same site.
         * @param priority the priority value
         * @return this builder
         */
        public FieldReadHookBuilder priority(int priority)
        {
            hookBuilder.priority(priority);
            return this;
        }

        /**
         * Attaches the collected filters and registers the hook.
         * @return the instrumenter this builder came from
         */
        public Instrumenter register()
        {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Fluent builder for an array store hook.
     */
    public static class ArrayStoreHookBuilder
    {
        private final Instrumenter instrumenter;
        private final ArrayStoreHook.Builder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        ArrayStoreHookBuilder(Instrumenter instrumenter)
        {
            this.instrumenter = instrumenter;
            this.hookBuilder = ArrayStoreHook.builder();
        }

        /**
         * Restricts this hook to a single class.
         * @param className the internal class name
         * @return this builder
         */
        public ArrayStoreHookBuilder inClass(String className)
        {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        /**
         * Restricts this hook to classes under a package.
         * @param packagePrefix the internal package name prefix
         * @return this builder
         */
        public ArrayStoreHookBuilder inPackage(String packagePrefix)
        {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        /**
         * Restricts this hook to methods whose name matches a pattern.
         * @param methodPattern the method name pattern
         * @return this builder
         */
        public ArrayStoreHookBuilder inMethod(String methodPattern)
        {
            filters.add(MethodFilter.matching(methodPattern));
            return this;
        }

        /**
         * Restricts this hook to arrays of one type.
         * @param typeDescriptor the array type descriptor
         * @return this builder
         */
        public ArrayStoreHookBuilder forArrayType(String typeDescriptor)
        {
            hookBuilder.arrayTypeFilter(typeDescriptor);
            return this;
        }

        /**
         * Restricts this hook to Object array stores.
         * @return this builder
         */
        public ArrayStoreHookBuilder forObjectArrays()
        {
            hookBuilder.arrayTypeFilter("[Ljava/lang/Object;");
            return this;
        }

        /**
         * Restricts this hook to int array stores.
         * @return this builder
         */
        public ArrayStoreHookBuilder forIntArrays()
        {
            hookBuilder.arrayTypeFilter("[I");
            return this;
        }

        /**
         * Sets the static method the hook dispatches to.
         * @param owner internal name of the class declaring the hook method
         * @param name the hook method name
         * @param descriptor the hook method descriptor
         * @return this builder
         */
        public ArrayStoreHookBuilder callStatic(String owner, String name, String descriptor)
        {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        /**
         * Passes the target array as a hook argument.
         * @return this builder
         */
        public ArrayStoreHookBuilder withArray()
        {
            hookBuilder.passArray(true);
            return this;
        }

        /**
         * Passes the store index as a hook argument.
         * @return this builder
         */
        public ArrayStoreHookBuilder withIndex()
        {
            hookBuilder.passIndex(true);
            return this;
        }

        /**
         * Passes the stored value as a hook argument.
         * @return this builder
         */
        public ArrayStoreHookBuilder withValue()
        {
            hookBuilder.passValue(true);
            return this;
        }

        /**
         * Passes the array, index, and value as hook arguments.
         * @return this builder
         */
        public ArrayStoreHookBuilder withAll()
        {
            hookBuilder.passArray(true);
            hookBuilder.passIndex(true);
            hookBuilder.passValue(true);
            return this;
        }

        /**
         * Lets the hook replace the stored value.
         * @return this builder
         */
        public ArrayStoreHookBuilder allowModification()
        {
            hookBuilder.canModifyValue(true);
            return this;
        }

        /**
         * Sets the ordering priority of this hook against others at the same site.
         * @param priority the priority value
         * @return this builder
         */
        public ArrayStoreHookBuilder priority(int priority)
        {
            hookBuilder.priority(priority);
            return this;
        }

        /**
         * Attaches the collected filters and registers the hook.
         * @return the instrumenter this builder came from
         */
        public Instrumenter register()
        {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Fluent builder for an array load hook.
     */
    public static class ArrayLoadHookBuilder
    {
        private final Instrumenter instrumenter;
        private final ArrayLoadHook.Builder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        ArrayLoadHookBuilder(Instrumenter instrumenter)
        {
            this.instrumenter = instrumenter;
            this.hookBuilder = ArrayLoadHook.builder();
        }

        /**
         * Restricts this hook to a single class.
         * @param className the internal class name
         * @return this builder
         */
        public ArrayLoadHookBuilder inClass(String className)
        {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        /**
         * Restricts this hook to classes under a package.
         * @param packagePrefix the internal package name prefix
         * @return this builder
         */
        public ArrayLoadHookBuilder inPackage(String packagePrefix)
        {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        /**
         * Restricts this hook to arrays of one type.
         * @param typeDescriptor the array type descriptor
         * @return this builder
         */
        public ArrayLoadHookBuilder forArrayType(String typeDescriptor)
        {
            hookBuilder.arrayTypeFilter(typeDescriptor);
            return this;
        }

        /**
         * Sets the static method the hook dispatches to.
         * @param owner internal name of the class declaring the hook method
         * @param name the hook method name
         * @param descriptor the hook method descriptor
         * @return this builder
         */
        public ArrayLoadHookBuilder callStatic(String owner, String name, String descriptor)
        {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        /**
         * Passes the source array as a hook argument.
         * @return this builder
         */
        public ArrayLoadHookBuilder withArray()
        {
            hookBuilder.passArray(true);
            return this;
        }

        /**
         * Passes the load index as a hook argument.
         * @return this builder
         */
        public ArrayLoadHookBuilder withIndex()
        {
            hookBuilder.passIndex(true);
            return this;
        }

        /**
         * Passes the loaded value as a hook argument.
         * @return this builder
         */
        public ArrayLoadHookBuilder withValue()
        {
            hookBuilder.passValue(true);
            return this;
        }

        /**
         * Sets the ordering priority of this hook against others at the same site.
         * @param priority the priority value
         * @return this builder
         */
        public ArrayLoadHookBuilder priority(int priority)
        {
            hookBuilder.priority(priority);
            return this;
        }

        /**
         * Attaches the collected filters and registers the hook.
         * @return the instrumenter this builder came from
         */
        public Instrumenter register()
        {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Fluent builder for a method call hook.
     */
    public static class MethodCallHookBuilder
    {
        private final Instrumenter instrumenter;
        private final MethodCallHook.Builder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        MethodCallHookBuilder(Instrumenter instrumenter)
        {
            this.instrumenter = instrumenter;
            this.hookBuilder = MethodCallHook.builder();
        }

        /**
         * Restricts this hook to call sites in a single class.
         * @param className the internal class name
         * @return this builder
         */
        public MethodCallHookBuilder inClass(String className)
        {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        /**
         * Restricts this hook to call sites under a package.
         * @param packagePrefix the internal package name prefix
         * @return this builder
         */
        public MethodCallHookBuilder inPackage(String packagePrefix)
        {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        /**
         * Restricts this hook to calls of one method, whatever its descriptor.
         * @param targetClass internal name of the called class
         * @param targetMethod the called method name
         * @return this builder
         */
        public MethodCallHookBuilder targeting(String targetClass, String targetMethod)
        {
            hookBuilder.targetClass(targetClass);
            hookBuilder.targetMethod(targetMethod);
            return this;
        }

        /**
         * Restricts this hook to calls of one exact method signature.
         * @param targetClass internal name of the called class
         * @param targetMethod the called method name
         * @param targetDescriptor the called method descriptor
         * @return this builder
         */
        public MethodCallHookBuilder targeting(String targetClass, String targetMethod, String targetDescriptor)
        {
            hookBuilder.targetClass(targetClass);
            hookBuilder.targetMethod(targetMethod);
            hookBuilder.targetDescriptor(targetDescriptor);
            return this;
        }

        /**
         * Places the hook call before the instrumented call, clearing the after placement.
         * @return this builder
         */
        public MethodCallHookBuilder before()
        {
            hookBuilder.before(true);
            hookBuilder.after(false);
            return this;
        }

        /**
         * Places the hook call after the instrumented call, clearing the before placement.
         * @return this builder
         */
        public MethodCallHookBuilder after()
        {
            hookBuilder.before(false);
            hookBuilder.after(true);
            return this;
        }

        /**
         * Sets the static method the hook dispatches to.
         * @param owner internal name of the class declaring the hook method
         * @param name the hook method name
         * @param descriptor the hook method descriptor
         * @return this builder
         */
        public MethodCallHookBuilder callStatic(String owner, String name, String descriptor)
        {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        /**
         * Passes the call receiver as a hook argument.
         * @return this builder
         */
        public MethodCallHookBuilder withReceiver()
        {
            hookBuilder.passReceiver(true);
            return this;
        }

        /**
         * Passes the call arguments as hook arguments.
         * @return this builder
         */
        public MethodCallHookBuilder withArguments()
        {
            hookBuilder.passArguments(true);
            return this;
        }

        /**
         * Passes the call result as a hook argument.
         * @return this builder
         */
        public MethodCallHookBuilder withResult()
        {
            hookBuilder.passResult(true);
            return this;
        }

        /**
         * Passes the called method name as a hook argument.
         * @return this builder
         */
        public MethodCallHookBuilder withMethodName()
        {
            hookBuilder.passMethodName(true);
            return this;
        }

        /**
         * Sets the ordering priority of this hook against others at the same site.
         * @param priority the priority value
         * @return this builder
         */
        public MethodCallHookBuilder priority(int priority)
        {
            hookBuilder.priority(priority);
            return this;
        }

        /**
         * Attaches the collected filters and registers the hook.
         * @return the instrumenter this builder came from
         */
        public Instrumenter register()
        {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    /**
     * Fluent builder for an exception handler hook.
     */
    public static class ExceptionHookBuilder
    {
        private final Instrumenter instrumenter;
        private final ExceptionHook.Builder hookBuilder;
        private final List<InstrumentationFilter> filters = new ArrayList<>();

        ExceptionHookBuilder(Instrumenter instrumenter)
        {
            this.instrumenter = instrumenter;
            this.hookBuilder = ExceptionHook.builder();
        }

        /**
         * Restricts this hook to a single class.
         * @param className the internal class name
         * @return this builder
         */
        public ExceptionHookBuilder inClass(String className)
        {
            filters.add(ClassFilter.exact(className));
            return this;
        }

        /**
         * Restricts this hook to classes under a package.
         * @param packagePrefix the internal package name prefix
         * @return this builder
         */
        public ExceptionHookBuilder inPackage(String packagePrefix)
        {
            filters.add(PackageFilter.forPackage(packagePrefix));
            return this;
        }

        /**
         * Restricts this hook to handlers of one exception type.
         * @param exceptionType the internal exception class name
         * @return this builder
         */
        public ExceptionHookBuilder forExceptionType(String exceptionType)
        {
            hookBuilder.exceptionType(exceptionType);
            return this;
        }

        /**
         * Sets the static method the hook dispatches to.
         * @param owner internal name of the class declaring the hook method
         * @param name the hook method name
         * @param descriptor the hook method descriptor
         * @return this builder
         */
        public ExceptionHookBuilder callStatic(String owner, String name, String descriptor)
        {
            hookBuilder.hookDescriptor(HookDescriptor.staticHook(owner, name, descriptor));
            return this;
        }

        /**
         * Passes the caught exception as a hook argument.
         * @return this builder
         */
        public ExceptionHookBuilder withException()
        {
            hookBuilder.passException(true);
            return this;
        }

        /**
         * Passes the enclosing method name as a hook argument.
         * @return this builder
         */
        public ExceptionHookBuilder withMethodName()
        {
            hookBuilder.passMethodName(true);
            return this;
        }

        /**
         * Passes the enclosing class name as a hook argument.
         * @return this builder
         */
        public ExceptionHookBuilder withClassName()
        {
            hookBuilder.passClassName(true);
            return this;
        }

        /**
         * Lets the hook swallow the exception instead of rethrowing it.
         * @return this builder
         */
        public ExceptionHookBuilder canSuppress()
        {
            hookBuilder.canSuppress(true);
            return this;
        }

        /**
         * Sets the ordering priority of this hook against others at the same site.
         * @param priority the priority value
         * @return this builder
         */
        public ExceptionHookBuilder priority(int priority)
        {
            hookBuilder.priority(priority);
            return this;
        }

        /**
         * Attaches the collected filters and registers the hook.
         * @return the instrumenter this builder came from
         */
        public Instrumenter register()
        {
            hookBuilder.filters(filters);
            instrumenter.registerHook(hookBuilder.build());
            return instrumenter;
        }
    }

    // Report Class

    /**
     * Immutable counts from one instrumentation run.
     */
    public static class InstrumentationReport
    {
        private final int totalInstrumentationPoints;
        private final int classesInstrumented;
        private final int methodsInstrumented;
        private final int errors;

        private InstrumentationReport(Builder b)
        {
            this.totalInstrumentationPoints = b.totalInstrumentationPoints;
            this.classesInstrumented = b.classesInstrumented;
            this.methodsInstrumented = b.methodsInstrumented;
            this.errors = b.errors;
        }

        /**
         * @return the total instrumentation points
         */
        public int getTotalInstrumentationPoints()
        {
            return totalInstrumentationPoints;
        }

        /**
         * @return the classes instrumented
         */
        public int getClassesInstrumented()
        {
            return classesInstrumented;
        }

        /**
         * @return the methods instrumented
         */
        public int getMethodsInstrumented()
        {
            return methodsInstrumented;
        }

        /**
         * @return the errors
         */
        public int getErrors()
        {
            return errors;
        }

        /**
         * @return a fresh report builder with every counter at zero
         */
        public static Builder builder()
        {
            return new Builder();
        }

        @Override
        public String toString()
        {
            return String.format("InstrumentationReport{points=%d, classes=%d, methods=%d, errors=%d}",
                    totalInstrumentationPoints, classesInstrumented, methodsInstrumented, errors);
        }

        /**
         * Mutable accumulator for the report counters.
         */
        public static final class Builder
        {
            private int totalInstrumentationPoints;
            private int classesInstrumented;
            private int methodsInstrumented;
            private int errors;

            /**
             * Sets the number of instrumentation points applied.
             * @param totalInstrumentationPoints the point count
             * @return this builder
             */
            public Builder totalInstrumentationPoints(int totalInstrumentationPoints)
            {
                this.totalInstrumentationPoints = totalInstrumentationPoints;
                return this;
            }

            /**
             * Sets the number of classes that received at least one point.
             * @param classesInstrumented the class count
             * @return this builder
             */
            public Builder classesInstrumented(int classesInstrumented)
            {
                this.classesInstrumented = classesInstrumented;
                return this;
            }

            /**
             * Sets the number of methods that received at least one point.
             * @param methodsInstrumented the method count
             * @return this builder
             */
            public Builder methodsInstrumented(int methodsInstrumented)
            {
                this.methodsInstrumented = methodsInstrumented;
                return this;
            }

            /**
             * Sets the number of methods that failed to instrument.
             * @param errors the error count
             * @return this builder
             */
            public Builder errors(int errors)
            {
                this.errors = errors;
                return this;
            }

            /**
             * @return an immutable report holding the accumulated counters
             */
            public InstrumentationReport build()
            {
                return new InstrumentationReport(this);
            }
        }
    }
}
