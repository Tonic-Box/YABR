package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Method invocation instruction.
 */
@Getter
public class InvokeInstruction extends IRInstruction {

    private final InvokeType invokeType;
    private final String owner;
    private final String name;
    private final String descriptor;
    private final List<Value> arguments;
    private final int originalCpIndex;
    private final BootstrapMethodInfo bootstrapInfo; // For invokedynamic

    public InvokeInstruction(SSAValue result, InvokeType invokeType, String owner, String name, String descriptor, List<Value> arguments) {
        this(result, invokeType, owner, name, descriptor, arguments, 0, null);
    }

    public InvokeInstruction(SSAValue result, InvokeType invokeType, String owner, String name, String descriptor, List<Value> arguments, int originalCpIndex) {
        this(result, invokeType, owner, name, descriptor, arguments, originalCpIndex, null);
    }

    public InvokeInstruction(SSAValue result, InvokeType invokeType, String owner, String name, String descriptor, List<Value> arguments, int originalCpIndex, BootstrapMethodInfo bootstrapInfo) {
        super(result);
        this.invokeType = invokeType;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.arguments = new ArrayList<>(arguments);
        this.originalCpIndex = originalCpIndex;
        this.bootstrapInfo = bootstrapInfo;
        registerUses();
    }

    public InvokeInstruction(InvokeType invokeType, String owner, String name, String descriptor, List<Value> arguments) {
        this(invokeType, owner, name, descriptor, arguments, 0, null);
    }

    public InvokeInstruction(InvokeType invokeType, String owner, String name, String descriptor, List<Value> arguments, int originalCpIndex) {
        this(invokeType, owner, name, descriptor, arguments, originalCpIndex, null);
    }

    public InvokeInstruction(InvokeType invokeType, String owner, String name, String descriptor, List<Value> arguments, int originalCpIndex, BootstrapMethodInfo bootstrapInfo) {
        super();
        this.invokeType = invokeType;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.arguments = new ArrayList<>(arguments);
        this.originalCpIndex = originalCpIndex;
        this.bootstrapInfo = bootstrapInfo;
        registerUses();
    }

    private void registerUses() {
        for (Value arg : arguments) {
            if (arg instanceof SSAValue ssa) ssa.addUse(this);
        }
    }

    /**
     * Gets the receiver object for instance method calls.
     *
     * @return the receiver value, or null for static calls
     */
    public Value getReceiver() {
        if (invokeType == InvokeType.STATIC) return null;
        return arguments.isEmpty() ? null : arguments.get(0);
    }

    /**
     * Gets the method arguments excluding the receiver.
     *
     * @return list of method arguments
     */
    public List<Value> getMethodArguments() {
        if (invokeType == InvokeType.STATIC) return arguments;
        return arguments.size() > 1 ? arguments.subList(1, arguments.size()) : List.of();
    }

    /**
     * Checks if this is an invokedynamic instruction.
     *
     * @return true if this is a dynamic invocation
     */
    public boolean isDynamic() {
        return invokeType == InvokeType.DYNAMIC;
    }

    /**
     * Checks if this invocation has bootstrap method info available.
     *
     * @return true if bootstrap info is available
     */
    public boolean hasBootstrapInfo() {
        return bootstrapInfo != null;
    }

    @Override
    public List<Value> getOperands() {
        return new ArrayList<>(arguments);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        for (int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i).equals(oldValue)) {
                if (arguments.get(i) instanceof SSAValue ssa) ssa.removeUse(this);
                arguments.set(i, newValue);
                if (newValue instanceof SSAValue ssa) ssa.addUse(this);
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitInvoke(this);
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        return new InvokeInstruction(newResult, invokeType, owner, name, descriptor, newOperands, originalCpIndex, bootstrapInfo);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (result != null) {
            sb.append(result).append(" = ");
        }
        sb.append("invoke").append(invokeType.name().toLowerCase()).append(" ");
        sb.append(owner).append(".").append(name).append(descriptor);
        sb.append("(");
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(arguments.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}
