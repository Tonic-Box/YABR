package com.tonic.analysis.execution.dispatch;

public final class InvokeDynamicInfo {

    private final int bootstrapMethodIndex;
    private final String methodName;
    private final String descriptor;
    private final int constantPoolIndex;

    public InvokeDynamicInfo(int bootstrapMethodIndex, String methodName, String descriptor, int constantPoolIndex) {
        this.bootstrapMethodIndex = bootstrapMethodIndex;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.constantPoolIndex = constantPoolIndex;
    }

    public int getBootstrapMethodIndex() {
        return bootstrapMethodIndex;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public int getConstantPoolIndex() {
        return constantPoolIndex;
    }

    public int getParameterSlots() {
        if (descriptor == null || !descriptor.startsWith("(")) {
            return 0;
        }

        int slots = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            switch (c) {
                case 'J':
                case 'D':
                    slots += 2;
                    i++;
                    break;
                case 'L':
                    slots++;
                    while (i < descriptor.length() && descriptor.charAt(i) != ';') {
                        i++;
                    }
                    i++;
                    break;
                case '[':
                    slots++;
                    while (i < descriptor.length() && descriptor.charAt(i) == '[') {
                        i++;
                    }
                    if (i < descriptor.length() && descriptor.charAt(i) == 'L') {
                        while (i < descriptor.length() && descriptor.charAt(i) != ';') {
                            i++;
                        }
                    }
                    i++;
                    break;
                default:
                    slots++;
                    i++;
                    break;
            }
        }
        return slots;
    }

    public String getReturnType() {
        if (descriptor == null) {
            return "V";
        }
        int parenIndex = descriptor.indexOf(')');
        if (parenIndex >= 0 && parenIndex < descriptor.length() - 1) {
            return descriptor.substring(parenIndex + 1);
        }
        return "V";
    }

    public boolean isVoidReturn() {
        return "V".equals(getReturnType());
    }

    public boolean isLambdaMetafactory() {
        return "run".equals(methodName) || "apply".equals(methodName) ||
               "accept".equals(methodName) || "test".equals(methodName) ||
               "get".equals(methodName) || "getAsInt".equals(methodName) ||
               "getAsLong".equals(methodName) || "getAsDouble".equals(methodName);
    }

    public boolean isStringConcat() {
        return "makeConcatWithConstants".equals(methodName) || "makeConcat".equals(methodName);
    }

    @Override
    public String toString() {
        return "InvokeDynamicInfo{" +
            "bsm=" + bootstrapMethodIndex +
            ", name='" + methodName + '\'' +
            ", desc='" + descriptor + '\'' +
            ", cpIndex=" + constantPoolIndex +
            '}';
    }
}
