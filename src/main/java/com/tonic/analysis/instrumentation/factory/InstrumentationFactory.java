package com.tonic.analysis.instrumentation.factory;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.hook.*;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.MethodEntry;

import java.util.*;

/**
 * Factory for creating instrumentation IR instructions.
 * Generates the IR instruction sequences needed to call hook methods
 * with the appropriate parameters.
 */
public class InstrumentationFactory {

    /**
     * Creates instructions to invoke a method entry hook.
     *
     * @param hook the method entry hook configuration
     * @param irMethod the IR method being instrumented
     * @param sourceMethod the source method entry
     * @param className the class name (internal format)
     * @return list of IR instructions to insert
     */
    public List<IRInstruction> createMethodEntryHook(
            MethodEntryHook hook,
            IRMethod irMethod,
            MethodEntry sourceMethod,
            String className) {

        List<IRInstruction> instructions = new ArrayList<>();
        List<Value> arguments = new ArrayList<>();
        HookDescriptor descriptor = hook.getHookDescriptor();

        // Build arguments based on configuration
        if (hook.isPassThis() && !isStatic(sourceMethod)) {
            // 'this' is the first parameter for instance methods
            List<SSAValue> params = irMethod.getParameters();
            if (!params.isEmpty()) {
                arguments.add(params.get(0));
            }
        }

        if (hook.isPassClassName()) {
            SSAValue classNameValue = new SSAValue(ReferenceType.STRING);
            instructions.add(new ConstantInstruction(classNameValue, new StringConstant(className.replace('/', '.'))));
            arguments.add(classNameValue);
        }

        if (hook.isPassMethodName()) {
            SSAValue methodNameValue = new SSAValue(ReferenceType.STRING);
            instructions.add(new ConstantInstruction(methodNameValue, new StringConstant(sourceMethod.getName())));
            arguments.add(methodNameValue);
        }

        if (hook.isPassAllParameters()) {
            // Create Object[] containing all parameters (boxed if primitive)
            List<SSAValue> params = irMethod.getParameters();
            int startIdx = isStatic(sourceMethod) ? 0 : 1;  // Skip 'this' for instance methods
            int paramCount = params.size() - startIdx;

            // Create array size constant
            SSAValue arraySizeValue = new SSAValue(PrimitiveType.INT);
            instructions.add(new ConstantInstruction(arraySizeValue, IntConstant.of(paramCount)));

            // Create Object[] array
            SSAValue arrayRef = new SSAValue(new ArrayType(ReferenceType.OBJECT));
            instructions.add(new NewArrayInstruction(arrayRef, ReferenceType.OBJECT, arraySizeValue));

            // Store each parameter into the array
            for (int i = startIdx; i < params.size(); i++) {
                SSAValue param = params.get(i);
                int arrayIndex = i - startIdx;

                // Create index constant
                SSAValue indexValue = new SSAValue(PrimitiveType.INT);
                instructions.add(new ConstantInstruction(indexValue, IntConstant.of(arrayIndex)));

                // Box primitive if needed
                Value boxedValue = boxIfPrimitive(param, instructions);

                // Store in array
                instructions.add(ArrayAccessInstruction.createStore(arrayRef, indexValue, boxedValue));
            }

            arguments.add(arrayRef);
        }

        // Handle specific parameter indices
        if (!hook.getParameterIndices().isEmpty()) {
            List<SSAValue> params = irMethod.getParameters();
            int startIdx = isStatic(sourceMethod) ? 0 : 1;

            for (Integer paramIndex : hook.getParameterIndices()) {
                int actualIndex = startIdx + paramIndex;
                if (actualIndex < params.size()) {
                    SSAValue param = params.get(actualIndex);
                    Value boxedValue = boxIfPrimitive(param, instructions);
                    arguments.add(boxedValue);
                }
            }
        }

        // Create the invoke instruction
        SSAValue result = null;
        String returnType = extractReturnType(descriptor.getDescriptor());
        if (!returnType.equals("V")) {
            result = new SSAValue(IRType.fromDescriptor(returnType));
        }

        InvokeInstruction invoke = new InvokeInstruction(
                result,
                descriptor.getInvokeType(),
                descriptor.getOwner(),
                descriptor.getName(),
                descriptor.getDescriptor(),
                arguments
        );
        instructions.add(invoke);

        return instructions;
    }

    /**
     * Creates instructions to invoke a method exit hook.
     *
     * @param hook the method exit hook configuration
     * @param irMethod the IR method being instrumented
     * @param returnInstr the return instruction being hooked
     * @param sourceMethod the source method entry
     * @param className the class name (internal format)
     * @return list of IR instructions to insert
     */
    public List<IRInstruction> createMethodExitHook(
            MethodExitHook hook,
            IRMethod irMethod,
            ReturnInstruction returnInstr,
            MethodEntry sourceMethod,
            String className) {

        List<IRInstruction> instructions = new ArrayList<>();
        List<Value> arguments = new ArrayList<>();
        HookDescriptor descriptor = hook.getHookDescriptor();

        if (hook.isPassThis() && !isStatic(sourceMethod)) {
            List<SSAValue> params = irMethod.getParameters();
            if (!params.isEmpty()) {
                arguments.add(params.get(0));
            }
        }

        if (hook.isPassClassName()) {
            SSAValue classNameValue = new SSAValue(ReferenceType.STRING);
            instructions.add(new ConstantInstruction(classNameValue, new StringConstant(className.replace('/', '.'))));
            arguments.add(classNameValue);
        }

        if (hook.isPassMethodName()) {
            SSAValue methodNameValue = new SSAValue(ReferenceType.STRING);
            instructions.add(new ConstantInstruction(methodNameValue, new StringConstant(sourceMethod.getName())));
            arguments.add(methodNameValue);
        }

        if (hook.isPassReturnValue()) {
            Value returnValue = returnInstr.getReturnValue();
            if (returnValue != null) {
                Value boxedValue = boxIfPrimitive(returnValue, instructions);
                arguments.add(boxedValue);
            } else {
                // Void return - pass null
                arguments.add(NullConstant.INSTANCE);
            }
        }

        // Create the invoke instruction
        SSAValue result = null;
        String returnType = extractReturnType(descriptor.getDescriptor());
        if (!returnType.equals("V")) {
            result = new SSAValue(IRType.fromDescriptor(returnType));
        }

        InvokeInstruction invoke = new InvokeInstruction(
                result,
                descriptor.getInvokeType(),
                descriptor.getOwner(),
                descriptor.getName(),
                descriptor.getDescriptor(),
                arguments
        );
        instructions.add(invoke);

        return instructions;
    }


    public List<IRInstruction> createFieldWriteHook(
            FieldWriteHook hook,
            FieldAccessInstruction fieldAccess,
            IRMethod irMethod,
            String className) {

        List<IRInstruction> instructions = new ArrayList<>();
        List<Value> arguments = new ArrayList<>();
        HookDescriptor descriptor = hook.getHookDescriptor();

        if (hook.isPassOwner()) {
            if (fieldAccess.isStatic()) {
                arguments.add(NullConstant.INSTANCE);
            } else {
                arguments.add(fieldAccess.getObjectRef());
            }
        }

        if (hook.isPassFieldName()) {
            SSAValue fieldNameValue = new SSAValue(ReferenceType.STRING);
            instructions.add(new ConstantInstruction(fieldNameValue, new StringConstant(fieldAccess.getName())));
            arguments.add(fieldNameValue);
        }

        if (hook.isPassNewValue()) {
            Value newValue = fieldAccess.getValue();
            Value boxedValue = boxIfPrimitive(newValue, instructions);
            arguments.add(boxedValue);
        }

        if (hook.isPassOldValue()) {
            SSAValue oldValue = new SSAValue(IRType.fromDescriptor(fieldAccess.getDescriptor()));
            FieldAccessInstruction loadField;
            if (fieldAccess.isStatic()) {
                loadField = FieldAccessInstruction.createStaticLoad(
                    oldValue, fieldAccess.getOwner(), fieldAccess.getName(), fieldAccess.getDescriptor());
            } else {
                loadField = FieldAccessInstruction.createLoad(
                    oldValue, fieldAccess.getOwner(), fieldAccess.getName(),
                    fieldAccess.getDescriptor(), fieldAccess.getObjectRef());
            }
            instructions.add(loadField);
            Value boxedValue = boxIfPrimitive(oldValue, instructions);
            arguments.add(boxedValue);
        }

        SSAValue result = null;
        if (hook.isCanModifyValue()) {
            result = new SSAValue(ReferenceType.OBJECT);
        }

        InvokeInstruction invoke = new InvokeInstruction(
                result,
                descriptor.getInvokeType(),
                descriptor.getOwner(),
                descriptor.getName(),
                descriptor.getDescriptor(),
                arguments
        );
        instructions.add(invoke);

        return instructions;
    }


    public List<IRInstruction> createArrayStoreHook(
            ArrayStoreHook hook,
            ArrayAccessInstruction arrayAccess,
            IRMethod irMethod) {

        List<IRInstruction> instructions = new ArrayList<>();
        List<Value> arguments = new ArrayList<>();
        HookDescriptor descriptor = hook.getHookDescriptor();

        if (hook.isPassArray()) {
            arguments.add(arrayAccess.getArray());
        }

        if (hook.isPassIndex()) {
            arguments.add(arrayAccess.getIndex());
        }

        if (hook.isPassValue()) {
            Value value = arrayAccess.getValue();
            Value boxedValue = boxIfPrimitive(value, instructions);
            arguments.add(boxedValue);
        }

        SSAValue result = null;
        if (hook.isCanModifyValue()) {
            result = new SSAValue(ReferenceType.OBJECT);
        }

        InvokeInstruction invoke = new InvokeInstruction(
                result,
                descriptor.getInvokeType(),
                descriptor.getOwner(),
                descriptor.getName(),
                descriptor.getDescriptor(),
                arguments
        );
        instructions.add(invoke);

        return instructions;
    }


    public List<IRInstruction> createFieldReadHook(
            FieldReadHook hook,
            FieldAccessInstruction fieldAccess,
            IRMethod irMethod,
            String className) {

        List<IRInstruction> instructions = new ArrayList<>();
        List<Value> arguments = new ArrayList<>();
        HookDescriptor descriptor = hook.getHookDescriptor();

        if (hook.isPassOwner()) {
            if (fieldAccess.isStatic()) {
                arguments.add(NullConstant.INSTANCE);
            } else {
                arguments.add(fieldAccess.getObjectRef());
            }
        }

        if (hook.isPassFieldName()) {
            SSAValue fieldNameValue = new SSAValue(ReferenceType.STRING);
            instructions.add(new ConstantInstruction(fieldNameValue, new StringConstant(fieldAccess.getName())));
            arguments.add(fieldNameValue);
        }

        if (hook.isPassReadValue()) {
            SSAValue readValue = fieldAccess.getResult();
            if (readValue != null) {
                Value boxedValue = boxIfPrimitive(readValue, instructions);
                arguments.add(boxedValue);
            }
        }

        SSAValue result = null;
        String returnType = extractReturnType(descriptor.getDescriptor());
        if (!returnType.equals("V")) {
            result = new SSAValue(IRType.fromDescriptor(returnType));
        }

        InvokeInstruction invoke = new InvokeInstruction(
                result,
                descriptor.getInvokeType(),
                descriptor.getOwner(),
                descriptor.getName(),
                descriptor.getDescriptor(),
                arguments
        );
        instructions.add(invoke);

        return instructions;
    }


    public List<IRInstruction> createArrayLoadHook(
            ArrayLoadHook hook,
            ArrayAccessInstruction arrayAccess,
            IRMethod irMethod) {

        List<IRInstruction> instructions = new ArrayList<>();
        List<Value> arguments = new ArrayList<>();
        HookDescriptor descriptor = hook.getHookDescriptor();

        if (hook.isPassArray()) {
            arguments.add(arrayAccess.getArray());
        }

        if (hook.isPassIndex()) {
            arguments.add(arrayAccess.getIndex());
        }

        if (hook.isPassValue()) {
            SSAValue loadedValue = arrayAccess.getResult();
            if (loadedValue != null) {
                Value boxedValue = boxIfPrimitive(loadedValue, instructions);
                arguments.add(boxedValue);
            }
        }

        SSAValue result = null;
        String returnType = extractReturnType(descriptor.getDescriptor());
        if (!returnType.equals("V")) {
            result = new SSAValue(IRType.fromDescriptor(returnType));
        }

        InvokeInstruction invoke = new InvokeInstruction(
                result,
                descriptor.getInvokeType(),
                descriptor.getOwner(),
                descriptor.getName(),
                descriptor.getDescriptor(),
                arguments
        );
        instructions.add(invoke);

        return instructions;
    }

    /**
     * Creates instructions to invoke a method call hook.
     *
     * @param hook the method call hook configuration
     * @param invoke the invoke instruction being hooked
     * @param irMethod the IR method
     * @param className the class name
     * @param isBefore true if this is a before-call hook
     * @return list of IR instructions to insert
     */
    public List<IRInstruction> createMethodCallHook(
            MethodCallHook hook,
            InvokeInstruction invoke,
            IRMethod irMethod,
            String className,
            boolean isBefore) {

        List<IRInstruction> instructions = new ArrayList<>();
        List<Value> arguments = new ArrayList<>();
        HookDescriptor descriptor = hook.getHookDescriptor();

        if (hook.isPassReceiver()) {
            // For instance methods, the first argument is the receiver
            List<Value> invokeArgs = invoke.getArguments();
            if (!invokeArgs.isEmpty() && invoke.getInvokeType() != InvokeType.STATIC) {
                arguments.add(invokeArgs.get(0));
            } else {
                arguments.add(NullConstant.INSTANCE);
            }
        }

        if (hook.isPassMethodName()) {
            SSAValue methodNameValue = new SSAValue(ReferenceType.STRING);
            instructions.add(new ConstantInstruction(methodNameValue,
                    new StringConstant(invoke.getOwner() + "." + invoke.getName())));
            arguments.add(methodNameValue);
        }

        if (hook.isPassArguments()) {
            // Create Object[] containing all arguments (boxed if primitive)
            List<Value> invokeArgs = invoke.getArguments();
            int startIdx = invoke.getInvokeType() == InvokeType.STATIC ? 0 : 1;  // Skip receiver for instance methods
            int argCount = invokeArgs.size() - startIdx;

            // Create array size constant
            SSAValue arraySizeValue = new SSAValue(PrimitiveType.INT);
            instructions.add(new ConstantInstruction(arraySizeValue, IntConstant.of(argCount)));

            // Create Object[] array
            SSAValue arrayRef = new SSAValue(new ArrayType(ReferenceType.OBJECT));
            instructions.add(new NewArrayInstruction(arrayRef, ReferenceType.OBJECT, arraySizeValue));

            // Store each argument into the array
            for (int i = startIdx; i < invokeArgs.size(); i++) {
                Value arg = invokeArgs.get(i);
                int arrayIndex = i - startIdx;

                // Create index constant
                SSAValue indexValue = new SSAValue(PrimitiveType.INT);
                instructions.add(new ConstantInstruction(indexValue, IntConstant.of(arrayIndex)));

                // Box primitive if needed
                Value boxedValue = boxIfPrimitive(arg, instructions);

                // Store in array
                instructions.add(ArrayAccessInstruction.createStore(arrayRef, indexValue, boxedValue));
            }

            arguments.add(arrayRef);
        }

        if (hook.isPassResult() && !isBefore) {
            // For after hooks, pass the result of the invoked method
            SSAValue invokeResult = invoke.getResult();
            if (invokeResult != null) {
                Value boxedValue = boxIfPrimitive(invokeResult, instructions);
                arguments.add(boxedValue);
            } else {
                arguments.add(NullConstant.INSTANCE);
            }
        }

        // Create the hook invoke instruction
        SSAValue hookResult = null;
        String returnType = extractReturnType(descriptor.getDescriptor());
        if (!returnType.equals("V")) {
            hookResult = new SSAValue(IRType.fromDescriptor(returnType));
        }

        InvokeInstruction hookInvoke = new InvokeInstruction(
                hookResult,
                descriptor.getInvokeType(),
                descriptor.getOwner(),
                descriptor.getName(),
                descriptor.getDescriptor(),
                arguments
        );
        instructions.add(hookInvoke);

        return instructions;
    }

    /**
     * Creates instructions to invoke an exception hook.
     *
     * @param hook the exception hook configuration
     * @param exceptionValue the exception value
     * @param irMethod the IR method
     * @param className the class name
     * @param methodName the method name
     * @return list of IR instructions to insert
     */
    public List<IRInstruction> createExceptionHook(
            ExceptionHook hook,
            SSAValue exceptionValue,
            IRMethod irMethod,
            String className,
            String methodName) {

        List<IRInstruction> instructions = new ArrayList<>();
        List<Value> arguments = new ArrayList<>();
        HookDescriptor descriptor = hook.getHookDescriptor();

        if (hook.isPassException()) {
            arguments.add(exceptionValue);
        }

        if (hook.isPassClassName()) {
            SSAValue classNameValue = new SSAValue(ReferenceType.STRING);
            instructions.add(new ConstantInstruction(classNameValue, new StringConstant(className.replace('/', '.'))));
            arguments.add(classNameValue);
        }

        if (hook.isPassMethodName()) {
            SSAValue methodNameValue = new SSAValue(ReferenceType.STRING);
            instructions.add(new ConstantInstruction(methodNameValue, new StringConstant(methodName)));
            arguments.add(methodNameValue);
        }

        // Create the invoke instruction
        SSAValue result = null;
        if (hook.isCanSuppress()) {
            // Hook returns boolean - true to suppress
            result = new SSAValue(PrimitiveType.BOOLEAN);
        }

        InvokeInstruction invoke = new InvokeInstruction(
                result,
                descriptor.getInvokeType(),
                descriptor.getOwner(),
                descriptor.getName(),
                descriptor.getDescriptor(),
                arguments
        );
        instructions.add(invoke);

        return instructions;
    }

    /**
     * Boxes a primitive value if needed.
     */
    private Value boxIfPrimitive(Value value, List<IRInstruction> instructions) {
        IRType type = value.getType();
        if (type == null || !type.isPrimitive()) {
            return value;
        }

        String wrapperClass = getWrapperClass(type);
        String boxDesc = "(" + type.getDescriptor() + ")L" + wrapperClass + ";";

        SSAValue boxed = new SSAValue(new ReferenceType(wrapperClass));
        instructions.add(new InvokeInstruction(
                boxed,
                InvokeType.STATIC,
                wrapperClass,
                "valueOf",
                boxDesc,
                List.of(value)
        ));
        return boxed;
    }

    /**
     * Gets the wrapper class for a primitive type.
     */
    private String getWrapperClass(IRType type) {
        if (type == PrimitiveType.INT) return "java/lang/Integer";
        if (type == PrimitiveType.LONG) return "java/lang/Long";
        if (type == PrimitiveType.FLOAT) return "java/lang/Float";
        if (type == PrimitiveType.DOUBLE) return "java/lang/Double";
        if (type == PrimitiveType.BOOLEAN) return "java/lang/Boolean";
        if (type == PrimitiveType.BYTE) return "java/lang/Byte";
        if (type == PrimitiveType.CHAR) return "java/lang/Character";
        if (type == PrimitiveType.SHORT) return "java/lang/Short";
        return "java/lang/Object";
    }

    /**
     * Extracts the return type from a method descriptor.
     */
    private String extractReturnType(String descriptor) {
        int idx = descriptor.lastIndexOf(')');
        if (idx >= 0 && idx < descriptor.length() - 1) {
            return descriptor.substring(idx + 1);
        }
        return "V";
    }

    /**
     * Checks if a method is static.
     */
    private boolean isStatic(MethodEntry method) {
        return (method.getAccess() & 0x0008) != 0;  // ACC_STATIC
    }
}
