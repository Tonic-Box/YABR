package com.tonic.analysis.execution.dispatch;

public final class ConstantDynamicInfo {

    private final int bootstrapMethodIndex;
    private final String name;
    private final String descriptor;
    private final int constantPoolIndex;

    public ConstantDynamicInfo(int bootstrapMethodIndex, String name, String descriptor, int constantPoolIndex) {
        this.bootstrapMethodIndex = bootstrapMethodIndex;
        this.name = name;
        this.descriptor = descriptor;
        this.constantPoolIndex = constantPoolIndex;
    }

    public int getBootstrapMethodIndex() {
        return bootstrapMethodIndex;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public int getConstantPoolIndex() {
        return constantPoolIndex;
    }

    public String getReturnType() {
        return descriptor;
    }

    public boolean isWideType() {
        return "J".equals(descriptor) || "D".equals(descriptor);
    }

    public boolean isPrimitive() {
        if (descriptor == null || descriptor.isEmpty()) {
            return false;
        }
        char c = descriptor.charAt(0);
        return c == 'Z' || c == 'B' || c == 'C' || c == 'S' ||
               c == 'I' || c == 'J' || c == 'F' || c == 'D';
    }

    public boolean isReference() {
        if (descriptor == null || descriptor.isEmpty()) {
            return false;
        }
        char c = descriptor.charAt(0);
        return c == 'L' || c == '[';
    }

    @Override
    public String toString() {
        return "ConstantDynamicInfo{" +
            "bsm=" + bootstrapMethodIndex +
            ", name='" + name + '\'' +
            ", desc='" + descriptor + '\'' +
            ", cpIndex=" + constantPoolIndex +
            '}';
    }
}
