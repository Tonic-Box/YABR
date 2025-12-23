package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.dispatch.InvokeDynamicInfo;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.concurrent.atomic.AtomicLong;

public final class LambdaProxyFactory {

    private static final AtomicLong lambdaCounter = new AtomicLong(0);

    private final HeapManager heapManager;

    public LambdaProxyFactory(HeapManager heapManager) {
        this.heapManager = heapManager;
    }

    public ObjectInstance createProxy(InvokeDynamicInfo info, ConcreteValue[] capturedArgs) {
        String proxyClassName = "$Lambda$" + lambdaCounter.incrementAndGet();

        ObjectInstance proxy = heapManager.newObject(proxyClassName);

        for (int i = 0; i < capturedArgs.length; i++) {
            String fieldName = "capture$" + i;
            String fieldDesc = getDescriptorForValue(capturedArgs[i]);
            switch (capturedArgs[i].getTag()) {
                case INT:
                    proxy.setField(proxyClassName, fieldName, fieldDesc, capturedArgs[i].asInt());
                    break;
                case LONG:
                    proxy.setField(proxyClassName, fieldName, fieldDesc, capturedArgs[i].asLong());
                    break;
                case FLOAT:
                    proxy.setField(proxyClassName, fieldName, fieldDesc, capturedArgs[i].asFloat());
                    break;
                case DOUBLE:
                    proxy.setField(proxyClassName, fieldName, fieldDesc, capturedArgs[i].asDouble());
                    break;
                case REFERENCE:
                    proxy.setField(proxyClassName, fieldName, fieldDesc, capturedArgs[i].asReference());
                    break;
                default:
                    break;
            }
        }

        return proxy;
    }

    public boolean isLambdaFactory(InvokeDynamicInfo info) {
        if (info == null) {
            return false;
        }
        String name = info.getMethodName();
        return "metafactory".equals(name) || "altMetafactory".equals(name);
    }

    public int getCapturedArgCount(String descriptor) {
        if (descriptor == null || !descriptor.startsWith("(")) {
            return 0;
        }

        int parenClose = descriptor.indexOf(')');
        if (parenClose <= 1) {
            return 0;
        }

        String paramPart = descriptor.substring(1, parenClose);
        if (paramPart.isEmpty()) {
            return 0;
        }

        int count = 0;
        int i = 0;
        while (i < paramPart.length()) {
            char c = paramPart.charAt(i);
            switch (c) {
                case 'J':
                case 'D':
                case 'I':
                case 'F':
                case 'Z':
                case 'B':
                case 'C':
                case 'S':
                    count++;
                    i++;
                    break;
                case 'L':
                    count++;
                    while (i < paramPart.length() && paramPart.charAt(i) != ';') {
                        i++;
                    }
                    i++;
                    break;
                case '[':
                    count++;
                    while (i < paramPart.length() && paramPart.charAt(i) == '[') {
                        i++;
                    }
                    if (i < paramPart.length() && paramPart.charAt(i) == 'L') {
                        while (i < paramPart.length() && paramPart.charAt(i) != ';') {
                            i++;
                        }
                    }
                    i++;
                    break;
                default:
                    i++;
                    break;
            }
        }
        return count;
    }

    public String extractInterfaceType(String descriptor) {
        if (descriptor == null) {
            return "java/lang/Object";
        }

        int parenClose = descriptor.indexOf(')');
        if (parenClose < 0 || parenClose >= descriptor.length() - 1) {
            return "java/lang/Object";
        }

        String returnType = descriptor.substring(parenClose + 1);

        if (returnType.startsWith("L") && returnType.endsWith(";")) {
            return returnType.substring(1, returnType.length() - 1);
        }

        return returnType;
    }

    public String getTargetMethodName(InvokeDynamicInfo info) {
        return info.getMethodName();
    }

    private String getDescriptorForValue(ConcreteValue value) {
        if (value == null || value.isNull()) {
            return "Ljava/lang/Object;";
        }
        switch (value.getTag()) {
            case INT:
                return "I";
            case LONG:
                return "J";
            case FLOAT:
                return "F";
            case DOUBLE:
                return "D";
            case REFERENCE:
                return "Ljava/lang/Object;";
            default:
                return "Ljava/lang/Object;";
        }
    }
}
