package com.tonic.analysis.verifier;

import com.tonic.analysis.verifier.controlflow.ControlFlowVerifier;
import com.tonic.analysis.verifier.controlflow.ExceptionTableVerifier;
import com.tonic.analysis.verifier.stackmap.StackMapVerifier;
import com.tonic.analysis.verifier.structural.StructuralVerifier;
import com.tonic.analysis.verifier.type.TypeVerifier;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bytecode verifier that runs the configured structural, type, control-flow, and stack-map passes
 * over methods, classes, or whole pools.
 */
public final class Verifier
{
    private final VerifierConfig config;
    private final ClassPool classPool;

    private Verifier(Builder builder)
    {
        this.config = builder.config != null ? builder.config : VerifierConfig.defaults();
        this.classPool = builder.classPool;
    }

    /**
     * @return the config
     */
    public VerifierConfig getConfig()
    {
        return config;
    }

    /**
     * @return the class pool
     */
    public ClassPool getClassPool()
    {
        return classPool;
    }

    /**
     * Creates a builder for a verifier.
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Verifies every method of a class, honoring the configured error mode.
     * @param classFile the class to verify
     * @return the combined result for the class
     */
    public VerificationResult verify(ClassFile classFile)
    {
        Objects.requireNonNull(classFile, "classFile");

        String className = classFile.getClassName();
        ErrorCollector collector = ErrorCollector.forConfig(config);
        List<VerificationResult> methodResults = new ArrayList<>();

        for (MethodEntry method : classFile.getMethods())
        {
            if (collector.shouldStop())
            {
                break;
            }

            VerificationResult methodResult = verify(method, classFile, collector);
            methodResults.add(methodResult);
        }

        List<VerificationError> allErrors = new ArrayList<>(collector.getErrors());
        List<VerificationError> allWarnings = new ArrayList<>(collector.getWarnings());

        for (VerificationResult mr : methodResults)
        {
            allErrors.addAll(mr.getErrors());
            allWarnings.addAll(mr.getWarnings());
        }

        List<VerificationError> combined = new ArrayList<>(allErrors);
        combined.addAll(allWarnings);

        if (combined.isEmpty())
        {
            return VerificationResult.success(className);
        }
        return VerificationResult.failure(combined, className);
    }

    /**
     * Verifies a single method without an enclosing class file.
     * @param method the method to verify
     * @return the method's result
     */
    public VerificationResult verify(MethodEntry method)
    {
        Objects.requireNonNull(method, "method");
        return verify(method, null, ErrorCollector.forConfig(config));
    }

    private VerificationResult verify(MethodEntry method, ClassFile classFile, ErrorCollector collector)
    {
        String className = classFile != null ? classFile.getClassName() : "unknown";
        String methodName = method.getName() + method.getDesc();

        if (method.getCodeAttribute() == null)
        {
            return VerificationResult.success(className, methodName);
        }

        if (config.isVerifyStructure() && !collector.shouldStop())
        {
            StructuralVerifier structural = new StructuralVerifier(classFile);
            structural.verify(method, collector);
        }

        if (config.isStrictTypeChecking() && !collector.shouldStop())
        {
            TypeVerifier typeVerifier = new TypeVerifier(classFile, classPool);
            typeVerifier.verify(method, collector);
        }

        if (config.isVerifyControlFlow() && !collector.shouldStop())
        {
            ControlFlowVerifier cfVerifier = new ControlFlowVerifier();
            cfVerifier.verify(method, collector);

            ExceptionTableVerifier exVerifier = new ExceptionTableVerifier(classFile);
            exVerifier.verify(method, collector);
        }

        if (config.isVerifyStackMapTable() && !collector.shouldStop())
        {
            StackMapVerifier stackMapVerifier = new StackMapVerifier(classFile);
            stackMapVerifier.verify(method, collector);
        }

        List<VerificationError> allIssues = new ArrayList<>(collector.getErrors());
        allIssues.addAll(collector.getWarnings());

        List<VerificationError> locatedIssues = new ArrayList<>();
        for (VerificationError e : allIssues)
        {
            locatedIssues.add(e.withLocation(className, methodName));
        }

        if (locatedIssues.isEmpty())
        {
            return VerificationResult.success(className, methodName);
        }
        return VerificationResult.failure(locatedIssues, className, methodName);
    }

    /**
     * Verifies every class in a pool, stopping early on the first invalid class in fail-fast mode.
     * @param pool the classes to verify
     * @return the combined result across all classes
     */
    public VerificationResult verifyAll(ClassPool pool)
    {
        Objects.requireNonNull(pool, "pool");

        List<VerificationError> allErrors = new ArrayList<>();
        List<VerificationError> allWarnings = new ArrayList<>();
        boolean allValid = true;

        for (ClassFile cf : pool.getClasses())
        {
            VerificationResult result = verify(cf);
            allErrors.addAll(result.getErrors());
            allWarnings.addAll(result.getWarnings());
            if (!result.isValid())
            {
                allValid = false;
            }

            if (config.isFailFast() && !allValid)
            {
                break;
            }
        }

        List<VerificationError> combined = new ArrayList<>(allErrors);
        combined.addAll(allWarnings);

        if (combined.isEmpty())
        {
            return VerificationResult.success();
        }
        return VerificationResult.failure(combined);
    }

    /**
     * Builder for a {@link Verifier}.
     */
    public static final class Builder
    {
        private VerifierConfig config;
        private ClassPool classPool;

        /**
         * Sets the full verifier configuration.
         * @param config the configuration
         * @return this builder
         */
        public Builder config(VerifierConfig config)
        {
            this.config = config;
            return this;
        }

        /**
         * Sets the class pool used for cross-class type checks.
         * @param classPool the class pool
         * @return this builder
         */
        public Builder classPool(ClassPool classPool)
        {
            this.classPool = classPool;
            return this;
        }

        /**
         * Sets the error mode, rebuilding the config to keep any other settings already chosen.
         * @param mode the error mode
         * @return this builder
         */
        public Builder errorMode(VerifierConfig.ErrorMode mode)
        {
            if (this.config == null)
            {
                this.config = VerifierConfig.builder().errorMode(mode).build();
            }
            else
            {
                this.config = VerifierConfig.builder()
                        .errorMode(mode)
                        .verifyStackMapTable(config.isVerifyStackMapTable())
                        .strictTypeChecking(config.isStrictTypeChecking())
                        .verifyControlFlow(config.isVerifyControlFlow())
                        .verifyStructure(config.isVerifyStructure())
                        .maxErrors(config.getMaxErrors())
                        .treatWarningsAsErrors(config.isTreatWarningsAsErrors())
                        .build();
            }
            return this;
        }

        /**
         * Selects FAIL_FAST error mode.
         * @return this builder
         */
        public Builder failFast()
        {
            return errorMode(VerifierConfig.ErrorMode.FAIL_FAST);
        }

        /**
         * Selects COLLECT_ALL error mode.
         * @return this builder
         */
        public Builder collectAll()
        {
            return errorMode(VerifierConfig.ErrorMode.COLLECT_ALL);
        }

        /**
         * Builds the verifier, defaulting to {@link VerifierConfig#defaults()} when no config was set.
         * @return the verifier
         */
        public Verifier build()
        {
            return new Verifier(this);
        }
    }
}
