package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.dispatch.InvokeDynamicInfo;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for heap proxy objects that stand in for invokedynamic lambda instances, storing captured arguments as capture$N fields.
 */
public final class LambdaProxyFactory
{

    private static final AtomicLong lambdaCounter = new AtomicLong(0);

    private final HeapManager heapManager;

    /**
     * Creates a factory that allocates proxies on the given heap.
     * @param heapManager the heap used to allocate proxy objects
     */
    public LambdaProxyFactory(HeapManager heapManager)
    {
        this.heapManager = heapManager;
    }

    /**
     * Names a proxy after the functional interface its call site yields, so the object records which
     * interface it stands in for, followed by a counter that keeps separate call sites distinct. A call
     * site whose return type is not a class descriptor keeps the bare counter form.
     * @param info the invokedynamic call site information, may be null
     * @return the proxy's class name
     */
    private static String proxyClassName(InvokeDynamicInfo info)
    {
        long id = lambdaCounter.incrementAndGet();
        String descriptor = info == null ? null : info.getReturnType();
        if (descriptor == null || descriptor.length() < 3
                || descriptor.charAt(0) != 'L' || !descriptor.endsWith(";"))
        {
            return "$Lambda$" + id;
        }
        return descriptor.substring(1, descriptor.length() - 1) + "$$Lambda$" + id;
    }

    /**
     * Allocates a fresh proxy object for a lambda call site and copies each captured argument into a capture$N field.
     * @param info the invokedynamic call site information, naming the functional interface the proxy implements
     * @param capturedArgs the values captured at the call site
     * @return the proxy instance
     */
    public ObjectInstance createProxy(InvokeDynamicInfo info, ConcreteValue[] capturedArgs)
    {
        String proxyClassName = proxyClassName(info);

        ObjectInstance proxy = heapManager.newObject(proxyClassName);

        for (int i = 0; i < capturedArgs.length; i++)
        {
            String fieldName = "capture$" + i;
            String fieldDesc = getDescriptorForValue(capturedArgs[i]);
            switch (capturedArgs[i].getTag())
            {
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

    /**
     * Checks whether the call site's bootstrap method is the lambda metafactory.
     * @param info the invokedynamic call site information, may be null
     * @return true if the bootstrap is metafactory or altMetafactory
     */
    public boolean isLambdaFactory(InvokeDynamicInfo info)
    {
        if (info == null)
        {
            return false;
        }
        String name = info.getMethodName();
        return "metafactory".equals(name) || "altMetafactory".equals(name);
    }

    /**
     * Counts the parameters in a call site descriptor, which equals the number of captured arguments.
     * @param descriptor the call site method descriptor, may be null
     * @return the parameter count, or 0 for a malformed descriptor
     */
    public int getCapturedArgCount(String descriptor)
    {
        if (descriptor == null || !descriptor.startsWith("("))
        {
            return 0;
        }

        int parenClose = descriptor.indexOf(')');
        if (parenClose <= 1)
        {
            return 0;
        }

        String paramPart = descriptor.substring(1, parenClose);
        if (paramPart.isEmpty())
        {
            return 0;
        }

        int count = 0;
        int i = 0;
        while (i < paramPart.length())
        {
            char c = paramPart.charAt(i);
            switch (c)
            {
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
                    while (i < paramPart.length() && paramPart.charAt(i) != ';')
                    {
                        i++;
                    }
                    i++;
                    break;
                case '[':
                    count++;
                    while (i < paramPart.length() && paramPart.charAt(i) == '[')
                    {
                        i++;
                    }
                    if (i < paramPart.length() && paramPart.charAt(i) == 'L')
                    {
                        while (i < paramPart.length() && paramPart.charAt(i) != ';')
                        {
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

    /**
     * Extracts the functional interface internal name from a call site descriptor's return type.
     * @param descriptor the call site method descriptor, may be null
     * @return the interface internal name, or java/lang/Object if it cannot be determined
     */
    public String extractInterfaceType(String descriptor)
    {
        if (descriptor == null)
        {
            return "java/lang/Object";
        }

        int parenClose = descriptor.indexOf(')');
        if (parenClose < 0 || parenClose >= descriptor.length() - 1)
        {
            return "java/lang/Object";
        }

        String returnType = descriptor.substring(parenClose + 1);

        if (returnType.startsWith("L") && returnType.endsWith(";"))
        {
            return returnType.substring(1, returnType.length() - 1);
        }

        return returnType;
    }

    /**
     * Returns the interface method name the lambda implements.
     * @param info the invokedynamic call site information
     * @return the invoked method name
     */
    public String getTargetMethodName(InvokeDynamicInfo info)
    {
        return info.getMethodName();
    }

    private String getDescriptorForValue(ConcreteValue value)
    {
        if (value == null || value.isNull())
        {
            return "Ljava/lang/Object;";
        }
        switch (value.getTag())
        {
            case INT:
                return "I";
            case LONG:
                return "J";
            case FLOAT:
                return "F";
            case DOUBLE:
                return "D";
            default:
                return "Ljava/lang/Object;";
        }
    }
}
