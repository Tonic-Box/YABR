package com.tonic.analysis.query.eval;

import com.tonic.analysis.Bootstraps;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

/**
 * The polymorphic evaluation cursor. An accessor step rebinds the subject (method -> call -> arg);
 * attribute resolvers read from the concrete subject. Thin immutable wrappers over YABR/parser
 * objects plus the shared {@link EvalContext}.
 */
public interface Subject {

    SubjectKind kind();

    EvalContext context();

    final class MethodSubject implements Subject {
        private final MethodEntry method;
        private final EvalContext context;
        public MethodSubject(MethodEntry method, EvalContext context) { this.method = method; this.context = context; }
        public MethodEntry method() { return method; }
        @Override public SubjectKind kind() { return SubjectKind.METHOD; }
        @Override public EvalContext context() { return context; }
    }

    final class ClassSubject implements Subject {
        private final ClassFile classFile;
        private final EvalContext context;
        public ClassSubject(ClassFile classFile, EvalContext context) { this.classFile = classFile; this.context = context; }
        public ClassFile classFile() { return classFile; }
        @Override public SubjectKind kind() { return SubjectKind.CLASS; }
        @Override public EvalContext context() { return context; }
    }

    final class InstructionSubject implements Subject {
        private final Instruction instruction;
        private final int index;
        private final EvalContext context;
        public InstructionSubject(Instruction instruction, int index, EvalContext context) {
            this.instruction = instruction; this.index = index; this.context = context;
        }
        public Instruction instruction() { return instruction; }
        public int index() { return index; }
        @Override public SubjectKind kind() { return SubjectKind.INSTRUCTION; }
        @Override public EvalContext context() { return context; }
    }

    /** An invocation site; {@code invoke} implements {@link com.tonic.analysis.instruction.InvokeInsn}. */
    final class CallSubject implements Subject {
        private final Instruction invoke;
        private final int index;
        private final EvalContext context;
        public CallSubject(Instruction invoke, int index, EvalContext context) {
            this.invoke = invoke; this.index = index; this.context = context;
        }
        public Instruction invoke() { return invoke; }
        public int index() { return index; }
        @Override public SubjectKind kind() { return SubjectKind.CALL; }
        @Override public EvalContext context() { return context; }
    }

    final class ArgSubject implements Subject {
        private final CallSubject call;
        private final int argIndex;
        public ArgSubject(CallSubject call, int argIndex) { this.call = call; this.argIndex = argIndex; }
        public CallSubject call() { return call; }
        public int argIndex() { return argIndex; }
        @Override public SubjectKind kind() { return SubjectKind.ARG; }
        @Override public EvalContext context() { return call.context(); }
    }

    /** A declared method parameter, identified by position; {@code type} is its descriptor from the method signature. */
    final class ParamSubject implements Subject {
        private final MethodEntry method;
        private final int index;
        private final String type;
        private final EvalContext context;
        public ParamSubject(MethodEntry method, int index, String type, EvalContext context) {
            this.method = method; this.index = index; this.type = type; this.context = context;
        }
        public MethodEntry method() { return method; }
        public int index() { return index; }
        public String type() { return type; }
        @Override public SubjectKind kind() { return SubjectKind.PARAM; }
        @Override public EvalContext context() { return context; }
    }

    /** A field read/write site; {@code instruction} is a GetField/PutField instruction. */
    final class FieldAccessSubject implements Subject {
        private final Instruction instruction;
        private final EvalContext context;
        public FieldAccessSubject(Instruction instruction, EvalContext context) {
            this.instruction = instruction; this.context = context;
        }
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
    final class DynamicSubject implements Subject {
        private final Bootstraps.BootstrapRef bootstrap;
        private final String name;
        private final String descriptor;
        private final String site;
        private final Instruction instruction;
        private final EvalContext context;
        public DynamicSubject(Bootstraps.BootstrapRef bootstrap, String name, String descriptor,
                              String site, Instruction instruction, EvalContext context) {
            this.bootstrap = bootstrap; this.name = name; this.descriptor = descriptor;
            this.site = site; this.instruction = instruction; this.context = context;
        }
        public Bootstraps.BootstrapRef bootstrap() { return bootstrap; }
        public String name() { return name; }
        public String descriptor() { return descriptor; }
        public String site() { return site; }
        public Instruction instruction() { return instruction; }
        @Override public SubjectKind kind() { return SubjectKind.DYNAMIC; }
        @Override public EvalContext context() { return context; }
    }

    /** A non-dynamic static bootstrap argument, identified by its constant-pool index. */
    final class BootstrapArgSubject implements Subject {
        private final int cpIndex;
        private final EvalContext context;
        public BootstrapArgSubject(int cpIndex, EvalContext context) {
            this.cpIndex = cpIndex; this.context = context;
        }
        public int cpIndex() { return cpIndex; }
        @Override public SubjectKind kind() { return SubjectKind.BOOTSTRAP_ARG; }
        @Override public EvalContext context() { return context; }
    }
}
