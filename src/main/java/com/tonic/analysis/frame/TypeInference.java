package com.tonic.analysis.frame;

import com.tonic.analysis.instruction.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;

/**
 * Type inference engine for bytecode analysis.
 * Maps each instruction to its effect on the TypeState (locals and stack).
 */
public class TypeInference {
    private final ConstPool constPool;

    /**
     * Constructs a TypeInference engine for the given constant pool.
     *
     * @param constPool the constant pool
     */
    public TypeInference(ConstPool constPool) {
        this.constPool = constPool;
    }

    /**
     * Applies the type transformation of an instruction to the given state.
     *
     * @param state The current type state
     * @param instr The instruction to apply
     * @return The new type state after the instruction
     */
    public TypeState apply(TypeState state, Instruction instr) {
        int opcode = instr.getOpcode();

        if (opcode == 0x00) {
            return state;
        }

        if (opcode == 0x01) {
            return state.push(VerificationType.NULL);
        }

        if (opcode >= 0x02 && opcode <= 0x08) {
            return state.push(VerificationType.INTEGER);
        }

        if (opcode == 0x09 || opcode == 0x0A) {
            return state.push(VerificationType.LONG);
        }

        if (opcode >= 0x0B && opcode <= 0x0D) {
            return state.push(VerificationType.FLOAT);
        }

        if (opcode == 0x0E || opcode == 0x0F) {
            return state.push(VerificationType.DOUBLE);
        }

        if (opcode == 0x10 || opcode == 0x11) {
            return state.push(VerificationType.INTEGER);
        }

        if (opcode == 0x12 || opcode == 0x13 || opcode == 0x14) {
            return applyLdc(state, instr);
        }

        if (opcode == 0x15 || (opcode >= 0x1A && opcode <= 0x1D)) {
            return state.push(VerificationType.INTEGER);
        }

        if (opcode == 0x16 || (opcode >= 0x1E && opcode <= 0x21)) {
            return state.push(VerificationType.LONG);
        }

        if (opcode == 0x17 || (opcode >= 0x22 && opcode <= 0x25)) {
            return state.push(VerificationType.FLOAT);
        }

        if (opcode == 0x18 || (opcode >= 0x26 && opcode <= 0x29)) {
            return state.push(VerificationType.DOUBLE);
        }

        if (opcode == 0x19 || (opcode >= 0x2A && opcode <= 0x2D)) {
            int index = getLocalIndex(instr);
            VerificationType localType = state.getLocal(index);
            return state.push(localType);
        }

        if (opcode == 0x2E || opcode == 0x33 || opcode == 0x34 || opcode == 0x35) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == 0x2F) {
            return state.pop(2).push(VerificationType.LONG);
        }

        if (opcode == 0x30) {
            return state.pop(2).push(VerificationType.FLOAT);
        }

        if (opcode == 0x31) {
            return state.pop(2).push(VerificationType.DOUBLE);
        }

        if (opcode == 0x32) {
            VerificationType arrayType = state.peek(1);
            state = state.pop(2);
            return state.push(VerificationType.object(getObjectClassIndex()));
        }

        if (opcode == 0x36 || (opcode >= 0x3B && opcode <= 0x3E)) {
            int index = getLocalIndex(instr);
            return state.pop().setLocal(index, VerificationType.INTEGER);
        }

        if (opcode == 0x37 || (opcode >= 0x3F && opcode <= 0x42)) {
            int index = getLocalIndex(instr);
            return state.pop(2).setLocal(index, VerificationType.LONG);
        }

        if (opcode == 0x38 || (opcode >= 0x43 && opcode <= 0x46)) {
            int index = getLocalIndex(instr);
            return state.pop().setLocal(index, VerificationType.FLOAT);
        }

        if (opcode == 0x39 || (opcode >= 0x47 && opcode <= 0x4A)) {
            int index = getLocalIndex(instr);
            return state.pop(2).setLocal(index, VerificationType.DOUBLE);
        }

        if (opcode == 0x3A || (opcode >= 0x4B && opcode <= 0x4E)) {
            int index = getLocalIndex(instr);
            VerificationType type = state.peek();
            return state.pop().setLocal(index, type);
        }

        if (opcode == 0x4F || opcode == 0x54 || opcode == 0x55 || opcode == 0x56) {
            return state.pop(3);
        }

        if (opcode == 0x50) {
            return state.pop(4);
        }

        if (opcode == 0x51) {
            return state.pop(3);
        }

        if (opcode == 0x52) {
            return state.pop(4);
        }

        if (opcode == 0x53) {
            return state.pop(3);
        }

        if (opcode == 0x57) {
            return state.pop();
        }

        if (opcode == 0x58) {
            return state.pop(2);
        }

        if (opcode == 0x59) {
            VerificationType top = state.peek();
            return state.push(top);
        }

        if (opcode == 0x5A) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            return state.pop(2).push(v1).push(v2).push(v1);
        }

        if (opcode == 0x5B) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            VerificationType v3 = state.peek(2);
            return state.pop(3).push(v1).push(v3).push(v2).push(v1);
        }

        if (opcode == 0x5C) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            return state.push(v2).push(v1);
        }

        if (opcode == 0x5D) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            VerificationType v3 = state.peek(2);
            return state.pop(3).push(v2).push(v1).push(v3).push(v2).push(v1);
        }

        if (opcode == 0x5E) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            VerificationType v3 = state.peek(2);
            VerificationType v4 = state.peek(3);
            return state.pop(4).push(v2).push(v1).push(v4).push(v3).push(v2).push(v1);
        }

        if (opcode == 0x5F) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            return state.pop(2).push(v1).push(v2);
        }

        if (opcode == 0x60 || opcode == 0x64 || opcode == 0x68 || opcode == 0x6C ||
            opcode == 0x70 || opcode == 0x7E || opcode == 0x80 || opcode == 0x82) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == 0x61 || opcode == 0x65 || opcode == 0x69 || opcode == 0x6D ||
            opcode == 0x71 || opcode == 0x7F || opcode == 0x81 || opcode == 0x83) {
            return state.pop(4).push(VerificationType.LONG);
        }

        if (opcode == 0x62 || opcode == 0x66 || opcode == 0x6A || opcode == 0x6E || opcode == 0x72) {
            return state.pop(2).push(VerificationType.FLOAT);
        }

        if (opcode == 0x63 || opcode == 0x67 || opcode == 0x6B || opcode == 0x6F || opcode == 0x73) {
            return state.pop(4).push(VerificationType.DOUBLE);
        }

        if (opcode >= 0x74 && opcode <= 0x77) {
            return state;
        }

        if (opcode == 0x78 || opcode == 0x7A || opcode == 0x7C) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == 0x79 || opcode == 0x7B || opcode == 0x7D) {
            return state.pop(3).push(VerificationType.LONG);
        }

        if (opcode == 0x84) {
            return state;
        }

        if (opcode == 0x85) {
            return state.pop().push(VerificationType.LONG);
        }

        if (opcode == 0x86) {
            return state.pop().push(VerificationType.FLOAT);
        }

        if (opcode == 0x87) {
            return state.pop().push(VerificationType.DOUBLE);
        }

        if (opcode == 0x88) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == 0x89) {
            return state.pop(2).push(VerificationType.FLOAT);
        }

        if (opcode == 0x8A) {
            return state.pop(2).push(VerificationType.DOUBLE);
        }

        if (opcode == 0x8B) {
            return state.pop().push(VerificationType.INTEGER);
        }

        if (opcode == 0x8C) {
            return state.pop().push(VerificationType.LONG);
        }

        if (opcode == 0x8D) {
            return state.pop().push(VerificationType.DOUBLE);
        }

        if (opcode == 0x8E) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == 0x8F) {
            return state.pop(2).push(VerificationType.LONG);
        }

        if (opcode == 0x90) {
            return state.pop(2).push(VerificationType.FLOAT);
        }

        if (opcode == 0x91 || opcode == 0x92 || opcode == 0x93) {
            return state;
        }

        if (opcode == 0x94) {
            return state.pop(4).push(VerificationType.INTEGER);
        }

        if (opcode == 0x95 || opcode == 0x96) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == 0x97 || opcode == 0x98) {
            return state.pop(4).push(VerificationType.INTEGER);
        }

        if (opcode >= 0x99 && opcode <= 0x9E) {
            return state.pop();
        }

        if (opcode >= 0x9F && opcode <= 0xA4) {
            return state.pop(2);
        }

        if (opcode == 0xA5 || opcode == 0xA6) {
            return state.pop(2);
        }

        if (opcode == 0xA7 || opcode == 0xC8) {
            return state;
        }

        if (opcode == 0xA8 || opcode == 0xC9) {
            return state.push(VerificationType.TOP);
        }

        if (opcode == 0xA9) {
            return state;
        }

        if (opcode == 0xAA || opcode == 0xAB) {
            return state.pop();
        }

        if (opcode == 0xAC) {
            return state.clearStack();
        }

        if (opcode == 0xAD) {
            return state.clearStack();
        }

        if (opcode == 0xAE) {
            return state.clearStack();
        }

        if (opcode == 0xAF) {
            return state.clearStack();
        }

        if (opcode == 0xB0) {
            return state.clearStack();
        }

        if (opcode == 0xB1) {
            return state.clearStack();
        }

        if (opcode == 0xB2) {
            return applyGetField(state, instr, true);
        }

        if (opcode == 0xB3) {
            return applyPutField(state, instr, true);
        }

        if (opcode == 0xB4) {
            return applyGetField(state, instr, false);
        }

        if (opcode == 0xB5) {
            return applyPutField(state, instr, false);
        }

        if (opcode == 0xB6) {
            return applyInvoke(state, instr, false, false);
        }

        if (opcode == 0xB7) {
            return applyInvoke(state, instr, false, true);
        }

        if (opcode == 0xB8) {
            return applyInvoke(state, instr, true, false);
        }

        if (opcode == 0xB9) {
            return applyInvoke(state, instr, false, false);
        }

        if (opcode == 0xBA) {
            return applyInvokeDynamic(state, instr);
        }

        if (opcode == 0xBB) {
            return state.push(VerificationType.uninitialized(instr.getOffset()));
        }

        if (opcode == 0xBC) {
            return state.pop().push(VerificationType.object(getObjectClassIndex()));
        }

        if (opcode == 0xBD) {
            return state.pop().push(VerificationType.object(getObjectClassIndex()));
        }

        if (opcode == 0xBE) {
            return state.pop().push(VerificationType.INTEGER);
        }

        if (opcode == 0xBF) {
            return state.clearStack();
        }

        if (opcode == 0xC0) {
            state = state.pop();
            if (instr instanceof CheckCastInstruction) {
                CheckCastInstruction checkCast = (CheckCastInstruction) instr;
                int classIndex = checkCast.getClassIndex();
                return state.push(VerificationType.object(classIndex));
            }
            return state.push(VerificationType.object(getObjectClassIndex()));
        }

        if (opcode == 0xC1) {
            return state.pop().push(VerificationType.INTEGER);
        }

        if (opcode == 0xC2 || opcode == 0xC3) {
            return state.pop();
        }

        if (opcode == 0xC4) {
            return state;
        }

        if (opcode == 0xC5) {
            if (instr instanceof MultiANewArrayInstruction) {
                MultiANewArrayInstruction multiArray = (MultiANewArrayInstruction) instr;
                int dimensions = multiArray.getDimensions();
                state = state.pop(dimensions);
                return state.push(VerificationType.object(multiArray.getClassIndex()));
            }
            return state;
        }

        if (opcode == 0xC6 || opcode == 0xC7) {
            return state.pop();
        }

        return state;
    }

    /**
     * Handles LDC, LDC_W, LDC2_W instructions.
     *
     * @param state the current type state
     * @param instr the LDC instruction
     * @return updated state with constant pushed
     */
    private TypeState applyLdc(TypeState state, Instruction instr) {
        int cpIndex;
        if (instr instanceof LdcInstruction) {
            LdcInstruction ldc = (LdcInstruction) instr;
            cpIndex = ldc.getCpIndex();
        } else if (instr instanceof LdcWInstruction) {
            LdcWInstruction ldcW = (LdcWInstruction) instr;
            cpIndex = ldcW.getCpIndex();
        } else if (instr instanceof Ldc2WInstruction) {
            Ldc2WInstruction ldc2W = (Ldc2WInstruction) instr;
            cpIndex = ldc2W.getCpIndex();
        } else {
            return state.push(VerificationType.INTEGER);
        }

        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof IntegerItem) {
            return state.push(VerificationType.INTEGER);
        } else if (item instanceof FloatItem) {
            return state.push(VerificationType.FLOAT);
        } else if (item instanceof LongItem) {
            return state.push(VerificationType.LONG);
        } else if (item instanceof DoubleItem) {
            return state.push(VerificationType.DOUBLE);
        } else if (item instanceof StringRefItem) {
            int stringClassIndex = constPool.findOrAddClass("java/lang/String").getIndex(constPool);
            return state.push(VerificationType.object(stringClassIndex));
        } else if (item instanceof ClassRefItem) {
            int classClassIndex = constPool.findOrAddClass("java/lang/Class").getIndex(constPool);
            return state.push(VerificationType.object(classClassIndex));
        }

        return state.push(VerificationType.object(getObjectClassIndex()));
    }

    /**
     * Handles GETSTATIC and GETFIELD instructions.
     *
     * @param state the current type state
     * @param instr the field instruction
     * @param isStatic true if GETSTATIC, false if GETFIELD
     * @return updated state with field value pushed
     */
    private TypeState applyGetField(TypeState state, Instruction instr, boolean isStatic) {
        if (!isStatic) {
            state = state.pop();
        }

        if (instr instanceof GetFieldInstruction) {
            GetFieldInstruction getField = (GetFieldInstruction) instr;
            int fieldIndex = getField.getFieldIndex();
            Item<?> item = constPool.getItem(fieldIndex);
            if (item instanceof FieldRefItem) {
                FieldRefItem fieldRefItem = (FieldRefItem) item;
                String desc = fieldRefItem.getDescriptor();
                if (desc != null) {
                    VerificationType type = descriptorToType(desc);
                    return state.push(type);
                }
            }
        }

        return state.push(VerificationType.INTEGER); // Fallback
    }

    /**
     * Handles PUTSTATIC and PUTFIELD instructions.
     *
     * @param state the current type state
     * @param instr the field instruction
     * @param isStatic true if PUTSTATIC, false if PUTFIELD
     * @return updated state with value and object popped
     */
    private TypeState applyPutField(TypeState state, Instruction instr, boolean isStatic) {
        if (instr instanceof PutFieldInstruction) {
            PutFieldInstruction putField = (PutFieldInstruction) instr;
            int fieldIndex = putField.getFieldIndex();
            Item<?> item = constPool.getItem(fieldIndex);
            if (item instanceof FieldRefItem) {
                FieldRefItem fieldRefItem = (FieldRefItem) item;
                String desc = fieldRefItem.getDescriptor();
                if (desc != null) {
                    int slots = getDescriptorSlots(desc);
                    state = state.pop(slots);
                } else {
                    state = state.pop();
                }
            } else {
                state = state.pop();
            }
        } else {
            state = state.pop();
        }

        if (!isStatic) {
            state = state.pop();
        }

        return state;
    }

    /**
     * Handles INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE.
     *
     * @param state the current type state
     * @param instr the invoke instruction
     * @param isStatic true if INVOKESTATIC
     * @param isSpecial true if INVOKESPECIAL
     * @return updated state with arguments popped and return value pushed
     */
    private TypeState applyInvoke(TypeState state, Instruction instr, boolean isStatic, boolean isSpecial) {
        String descriptor = null;
        String methodName = null;

        if (instr instanceof InvokeVirtualInstruction) {
            InvokeVirtualInstruction invoke = (InvokeVirtualInstruction) instr;
            int methodIndex = invoke.getMethodIndex();
            Item<?> item = constPool.getItem(methodIndex);
            if (item instanceof MethodRefItem) {
                MethodRefItem methodRefItem = (MethodRefItem) item;
                descriptor = methodRefItem.getDescriptor();
                methodName = methodRefItem.getName();
            }
        } else if (instr instanceof InvokeSpecialInstruction) {
            InvokeSpecialInstruction invoke = (InvokeSpecialInstruction) instr;
            int methodIndex = invoke.getMethodIndex();
            Item<?> item = constPool.getItem(methodIndex);
            if (item instanceof MethodRefItem) {
                MethodRefItem methodRefItem = (MethodRefItem) item;
                descriptor = methodRefItem.getDescriptor();
                methodName = methodRefItem.getName();
            }
        } else if (instr instanceof InvokeStaticInstruction) {
            InvokeStaticInstruction invoke = (InvokeStaticInstruction) instr;
            int methodIndex = invoke.getMethodIndex();
            Item<?> item = constPool.getItem(methodIndex);
            if (item instanceof MethodRefItem) {
                MethodRefItem methodRefItem = (MethodRefItem) item;
                descriptor = methodRefItem.getDescriptor();
                methodName = methodRefItem.getName();
            }
        } else if (instr instanceof InvokeInterfaceInstruction) {
            InvokeInterfaceInstruction invoke = (InvokeInterfaceInstruction) instr;
            int methodIndex = invoke.getMethodIndex();
            Item<?> item = constPool.getItem(methodIndex);
            if (item instanceof InterfaceRefItem) {
                InterfaceRefItem interfaceRefItem = (InterfaceRefItem) item;
                int nameAndTypeIndex = interfaceRefItem.getValue().getNameAndTypeIndex();
                Item<?> natItem = constPool.getItem(nameAndTypeIndex);
                if (natItem instanceof NameAndTypeRefItem) {
                    NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) natItem;
                    descriptor = nameAndType.getDescriptor();
                    int nameIndex = nameAndType.getValue().getNameIndex();
                    Item<?> nameItem = constPool.getItem(nameIndex);
                    if (nameItem instanceof Utf8Item) {
                        Utf8Item utf8 = (Utf8Item) nameItem;
                        methodName = utf8.getValue();
                    }
                }
            }
        }

        if (descriptor == null) {
            return state;
        }

        int argSlots = countArgumentSlots(descriptor);
        state = state.pop(argSlots);

        if (!isStatic) {
            if (isSpecial && "<init>".equals(methodName)) {
                VerificationType objectRef = state.peek();
                state = state.pop();
            } else {
                state = state.pop();
            }
        }

        VerificationType returnType = TypeState.getReturnType(descriptor, constPool);
        if (returnType != null) {
            state = state.push(returnType);
        }

        return state;
    }

    /**
     * Handles INVOKEDYNAMIC instruction.
     *
     * @param state the current type state
     * @param instr the invokedynamic instruction
     * @return updated state with arguments popped and return value pushed
     */
    private TypeState applyInvokeDynamic(TypeState state, Instruction instr) {
        if (instr instanceof InvokeDynamicInstruction) {
            InvokeDynamicInstruction invoke = (InvokeDynamicInstruction) instr;
            int natIndex = invoke.getNameAndTypeIndex();
            if (natIndex <= 0) {
                return state;
            }
            Item<?> natItem = constPool.getItem(natIndex);
            if (natItem instanceof NameAndTypeRefItem) {
                NameAndTypeRefItem nat = (NameAndTypeRefItem) natItem;
                String descriptor = nat.getDescriptor();

                int argSlots = countArgumentSlots(descriptor);
                state = state.pop(argSlots);

                VerificationType returnType = TypeState.getReturnType(descriptor, constPool);
                if (returnType != null) {
                    state = state.push(returnType);
                }
            }
        }
        return state;
    }

    /**
     * Counts the number of stack slots used by method arguments.
     *
     * @param descriptor the method descriptor
     * @return total stack slots for all arguments
     */
    private int countArgumentSlots(String descriptor) {
        int slots = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            switch (c) {
                case 'B':
                case 'C':
                case 'F':
                case 'I':
                case 'S':
                case 'Z':
                    slots++;
                    i++;
                    break;
                case 'D':
                case 'J':
                    slots += 2;
                    i++;
                    break;
                case 'L':
                    slots++;
                    i = descriptor.indexOf(';', i) + 1;
                    break;
                case '[':
                    slots++;
                    while (i < descriptor.length() && descriptor.charAt(i) == '[') i++;
                    if (i < descriptor.length() && descriptor.charAt(i) == 'L') {
                        i = descriptor.indexOf(';', i) + 1;
                    } else {
                        i++;
                    }
                    break;
                default:
                    i++;
                    break;
            }
        }
        return slots;
    }

    /**
     * Converts a field descriptor to a VerificationType.
     *
     * @param desc the field descriptor
     * @return corresponding VerificationType
     */
    private VerificationType descriptorToType(String desc) {
        if (desc.isEmpty()) return VerificationType.TOP;
        char c = desc.charAt(0);
        switch (c) {
            case 'B':
            case 'C':
            case 'I':
            case 'S':
            case 'Z':
                return VerificationType.INTEGER;
            case 'F':
                return VerificationType.FLOAT;
            case 'D':
                return VerificationType.DOUBLE;
            case 'J':
                return VerificationType.LONG;
            case 'L':
                String className = desc.substring(1, desc.length() - 1);
                int classIndex = constPool.findOrAddClass(className).getIndex(constPool);
                return VerificationType.object(classIndex);
            case '[':
                int classIndex2 = constPool.findOrAddClass(desc).getIndex(constPool);
                return VerificationType.object(classIndex2);
            default:
                return VerificationType.TOP;
        }
    }

    /**
     * Gets the number of slots for a descriptor.
     *
     * @param desc the field descriptor
     * @return 2 for long/double, 1 otherwise
     */
    private int getDescriptorSlots(String desc) {
        if (desc.isEmpty()) return 0;
        char c = desc.charAt(0);
        return (c == 'D' || c == 'J') ? 2 : 1;
    }

    /**
     * Gets the local variable index from an instruction.
     *
     * @param instr the instruction
     * @return local variable index
     */
    private int getLocalIndex(Instruction instr) {
        if (instr instanceof ILoadInstruction) {
            return ((ILoadInstruction) instr).getVarIndex();
        }
        if (instr instanceof LLoadInstruction) {
            return ((LLoadInstruction) instr).getVarIndex();
        }
        if (instr instanceof FLoadInstruction) {
            return ((FLoadInstruction) instr).getVarIndex();
        }
        if (instr instanceof DLoadInstruction) {
            return ((DLoadInstruction) instr).getVarIndex();
        }
        if (instr instanceof ALoadInstruction) {
            return ((ALoadInstruction) instr).getVarIndex();
        }
        if (instr instanceof IStoreInstruction) {
            return ((IStoreInstruction) instr).getVarIndex();
        }
        if (instr instanceof LStoreInstruction) {
            return ((LStoreInstruction) instr).getVarIndex();
        }
        if (instr instanceof FStoreInstruction) {
            return ((FStoreInstruction) instr).getVarIndex();
        }
        if (instr instanceof DStoreInstruction) {
            return ((DStoreInstruction) instr).getVarIndex();
        }
        if (instr instanceof AStoreInstruction) {
            return ((AStoreInstruction) instr).getVarIndex();
        }
        return 0;
    }

    /**
     * Gets or creates a class index for java/lang/Object.
     *
     * @return constant pool index for Object class
     */
    private int getObjectClassIndex() {
        return constPool.findOrAddClass("java/lang/Object").getIndex(constPool);
    }
}
