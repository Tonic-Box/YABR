package com.tonic.analysis.query.eval;

import com.tonic.analysis.Bootstraps;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

/**
 * The polymorphic evaluation cursor. An accessor step rebinds the subject (method -&gt; call -&gt; arg);
 * attribute resolvers read from the concrete subject. Thin immutable wrappers over YABR/parser
 * objects plus the shared {@link EvalContext}.
 */
public interface Subject
{

    /**
     * @return which variant this subject is, selecting the attributes that apply to it
     */
    SubjectKind kind();

    /**
     * @return the evaluation context shared across every subject in a run
     */
    EvalContext context();

    final class MethodSubject implements Subject
    {
        private final MethodEntry method;
        private final EvalContext context;
        /**
         * Binds the cursor to a method.
         *
         * @param method the method to bind
         * @param context the shared evaluation context
         */
        public MethodSubject(MethodEntry method, EvalContext context) { this.method = method; this.context = context; }
        /**
         * @return the bound method
         */
        public MethodEntry method() { return method; }
        @Override public SubjectKind kind() { return SubjectKind.METHOD; }
        @Override public EvalContext context() { return context; }
    }

    final class ClassSubject implements Subject
    {
        private final ClassFile classFile;
        private final EvalContext context;
        /**
         * Binds the cursor to a class.
         *
         * @param classFile the class to bind
         * @param context the shared evaluation context
         */
        public ClassSubject(ClassFile classFile, EvalContext context) { this.classFile = classFile; this.context = context; }
        /**
         * @return the bound class
         */
        public ClassFile classFile() { return classFile; }
        @Override public SubjectKind kind() { return SubjectKind.CLASS; }
        @Override public EvalContext context() { return context; }
    }

    final class InstructionSubject implements Subject
    {
        private final Instruction instruction;
        private final int index;
        private final EvalContext context;
        /**
         * Binds the cursor to one instruction of a method body.
         *
         * @param instruction the instruction to bind
         * @param index its position in the body
         * @param context the shared evaluation context
         */
        public InstructionSubject(Instruction instruction, int index, EvalContext context)
        {
            this.instruction = instruction; this.index = index; this.context = context;
        }
        /**
         * @return the bound instruction
         */
        public Instruction instruction() { return instruction; }
        /**
         * @return the instruction's position in the method body
         */
        public int index() { return index; }
        @Override public SubjectKind kind() { return SubjectKind.INSTRUCTION; }
        @Override public EvalContext context() { return context; }
    }

    /**
     * An invocation site; {@code invoke} implements {@link com.tonic.analysis.instruction.InvokeInsn}.
     */
    final class CallSubject implements Subject
    {
        private final Instruction invoke;
        private final int index;
        private final EvalContext context;
        /**
         * Binds the cursor to a call site.
         *
         * @param invoke the invoke instruction
         * @param index its position in the body
         * @param context the shared evaluation context
         */
        public CallSubject(Instruction invoke, int index, EvalContext context)
        {
            this.invoke = invoke; this.index = index; this.context = context;
        }
        /**
         * @return the invoke instruction at this call site
         */
        public Instruction invoke() { return invoke; }
        /**
         * @return the invoke's position in the method body
         */
        public int index() { return index; }
        @Override public SubjectKind kind() { return SubjectKind.CALL; }
        @Override public EvalContext context() { return context; }
    }

    final class ArgSubject implements Subject
    {
        private final CallSubject call;
        private final int argIndex;
        /**
         * Binds the cursor to one argument of a call site, inheriting its context.
         *
         * @param call the call site the argument is passed to
         * @param argIndex the argument's position
         */
        public ArgSubject(CallSubject call, int argIndex) { this.call = call; this.argIndex = argIndex; }
        /**
         * @return the call site this argument belongs to
         */
        public CallSubject call() { return call; }
        /**
         * @return the argument's position in the call
         */
        public int argIndex() { return argIndex; }
        @Override public SubjectKind kind() { return SubjectKind.ARG; }
        @Override public EvalContext context() { return call.context(); }
    }

    /**
     * A declared method parameter, identified by position; {@code type} is its descriptor from the method signature.
     */
    final class ParamSubject implements Subject
    {
        private final MethodEntry method;
        private final int index;
        private final String type;
        private final EvalContext context;
        /**
         * Binds the cursor to a declared parameter.
         *
         * @param method the declaring method
         * @param index the parameter's position in the signature
         * @param type the parameter's descriptor
         * @param context the shared evaluation context
         */
        public ParamSubject(MethodEntry method, int index, String type, EvalContext context)
        {
            this.method = method; this.index = index; this.type = type; this.context = context;
        }
        /**
         * @return the method declaring this parameter
         */
        public MethodEntry method() { return method; }
        /**
         * @return the parameter's position in the signature
         */
        public int index() { return index; }
        /**
         * @return the parameter's descriptor
         */
        public String type() { return type; }
        @Override public SubjectKind kind() { return SubjectKind.PARAM; }
        @Override public EvalContext context() { return context; }
    }

    /**
     * A field read/write site; {@code instruction} is a GetField/PutField instruction.
     */
    final class FieldAccessSubject implements Subject
    {
        private final Instruction instruction;
        private final EvalContext context;
        /**
         * Binds the cursor to a field read or write.
         *
         * @param instruction the field access instruction
         * @param context the shared evaluation context
         */
        public FieldAccessSubject(Instruction instruction, EvalContext context)
        {
            this.instruction = instruction; this.context = context;
        }
        /**
         * @return the field access instruction
         */
        public Instruction instruction() { return instruction; }
        @Override public SubjectKind kind() { return SubjectKind.FIELD_ACCESS; }
        @Override public EvalContext context() { return context; }
    }

    /**
     * A dynamic site: an invokedynamic call site ({@code site == "indy"}), a {@code CONSTANT_Dynamic}
     * load, or a nested condy bootstrap argument ({@code site == "condy"}). Holds the resolved
     * bootstrap and the call-site name/descriptor (the indy/condy name+type), plus the originating
     * instruction (for evidence) when there is one.
     */
    final class DynamicSubject implements Subject
    {
        private final Bootstraps.BootstrapRef bootstrap;
        private final String name;
        private final String descriptor;
        private final String site;
        private final Instruction instruction;
        private final EvalContext context;
        /**
         * Binds the cursor to an invokedynamic or dynamic-constant site.
         *
         * @param bootstrap the resolved bootstrap method
         * @param name the call site name
         * @param descriptor the call site descriptor
         * @param site "indy" or "condy"
         * @param instruction the originating instruction, or null for a nested condy argument
         * @param context the shared evaluation context
         */
        public DynamicSubject(Bootstraps.BootstrapRef bootstrap, String name, String descriptor, String site, Instruction instruction, EvalContext context)
        {
            this.bootstrap = bootstrap; this.name = name; this.descriptor = descriptor;
            this.site = site; this.instruction = instruction; this.context = context;
        }
        /**
         * @return the resolved bootstrap method
         */
        public Bootstraps.BootstrapRef bootstrap() { return bootstrap; }
        /**
         * @return the call site name
         */
        public String name() { return name; }
        /**
         * @return the call site descriptor
         */
        public String descriptor() { return descriptor; }
        /**
         * @return "indy" or "condy", identifying which kind of site this is
         */
        public String site() { return site; }
        /**
         * @return the originating instruction, or null for a nested condy argument
         */
        public Instruction instruction() { return instruction; }
        @Override public SubjectKind kind() { return SubjectKind.DYNAMIC; }
        @Override public EvalContext context() { return context; }
    }

    /**
     * A non-dynamic static bootstrap argument, identified by its constant-pool index.
     */
    final class BootstrapArgSubject implements Subject
    {
        private final int cpIndex;
        private final EvalContext context;
        /**
         * Binds the cursor to a static bootstrap argument.
         *
         * @param cpIndex the argument's constant pool index
         * @param context the shared evaluation context
         */
        public BootstrapArgSubject(int cpIndex, EvalContext context)
        {
            this.cpIndex = cpIndex; this.context = context;
        }
        /**
         * @return the argument's constant pool index
         */
        public int cpIndex() { return cpIndex; }
        @Override public SubjectKind kind() { return SubjectKind.BOOTSTRAP_ARG; }
        @Override public EvalContext context() { return context; }
    }
}
