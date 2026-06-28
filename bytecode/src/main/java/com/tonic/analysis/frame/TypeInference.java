package com.tonic.analysis.frame;

import com.tonic.analysis.instruction.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;

import static com.tonic.util.Opcode.*;

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
        // A WIDE prefix carries the real load/store/iinc/ret opcode plus a 2-byte local index; dispatch on
        // the modified opcode so the wrapped instruction's stack/local effect is applied. Its own opcode is
        // the wide prefix 0xC4, which matches no case below, so without this it is a silent no-op that leaves
        // the stack height wrong (e.g. a wide astore never pops) and corrupts frame computation.
        int opcode = instr instanceof WideInstruction
                ? ((WideInstruction) instr).getModifiedOpcode().getCode()
                : instr.getOpcode();

        if (opcode == NOP.getCode()) {
            return state;
        }

        if (opcode == ACONST_NULL.getCode()) {
            return state.push(VerificationType.NULL);
        }

        if (opcode >= ICONST_M1.getCode() && opcode <= ICONST_5.getCode()) {
            return state.push(VerificationType.INTEGER);
        }

        if (opcode == LCONST_0.getCode() || opcode == LCONST_1.getCode()) {
            return state.push(VerificationType.LONG);
        }

        if (opcode >= FCONST_0.getCode() && opcode <= FCONST_2.getCode()) {
            return state.push(VerificationType.FLOAT);
        }

        if (opcode == DCONST_0.getCode() || opcode == DCONST_1.getCode()) {
            return state.push(VerificationType.DOUBLE);
        }

        if (opcode == BIPUSH.getCode() || opcode == SIPUSH.getCode()) {
            return state.push(VerificationType.INTEGER);
        }

        if (opcode == LDC.getCode() || opcode == LDC_W.getCode() || opcode == LDC2_W.getCode()) {
            return applyLdc(state, instr);
        }

        if (opcode == ILOAD.getCode() || (opcode >= ILOAD_0.getCode() && opcode <= ILOAD_3.getCode())) {
            return state.push(VerificationType.INTEGER);
        }

        if (opcode == LLOAD.getCode() || (opcode >= LLOAD_0.getCode() && opcode <= LLOAD_3.getCode())) {
            return state.push(VerificationType.LONG);
        }

        if (opcode == FLOAD.getCode() || (opcode >= FLOAD_0.getCode() && opcode <= FLOAD_3.getCode())) {
            return state.push(VerificationType.FLOAT);
        }

        if (opcode == DLOAD.getCode() || (opcode >= DLOAD_0.getCode() && opcode <= DLOAD_3.getCode())) {
            return state.push(VerificationType.DOUBLE);
        }

        if (opcode == ALOAD.getCode() || (opcode >= ALOAD_0.getCode() && opcode <= ALOAD_3.getCode())) {
            int index = getLocalIndex(instr);
            VerificationType localType = state.getLocal(index);
            return state.push(localType);
        }

        if (opcode == IALOAD.getCode() || opcode == BALOAD.getCode() || opcode == CALOAD.getCode() || opcode == SALOAD.getCode()) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == LALOAD.getCode()) {
            return state.pop(2).push(VerificationType.LONG);
        }

        if (opcode == FALOAD.getCode()) {
            return state.pop(2).push(VerificationType.FLOAT);
        }

        if (opcode == DALOAD.getCode()) {
            return state.pop(2).push(VerificationType.DOUBLE);
        }

        if (opcode == AALOAD.getCode()) {
            VerificationType arrayType = state.peek(1);
            state = state.pop(2);
            if (arrayType.getTag() == VerificationType.TAG_OBJECT) {
                VerificationType.ObjectType objType = (VerificationType.ObjectType) arrayType;
                int arrayClassIndex = objType.getClassIndex();
                Item<?> item = constPool.getItem(arrayClassIndex);
                if (item instanceof ClassRefItem) {
                    String className = ((ClassRefItem) item).getClassName();
                    if (className != null && className.startsWith("[")) {
                        String elementDesc = className.substring(1);
                        VerificationType elemType = descriptorToType(elementDesc);
                        return state.push(elemType);
                    }
                }
            }
            return state.push(VerificationType.object(getObjectClassIndex()));
        }

        if (opcode == ISTORE.getCode() || (opcode >= ISTORE_0.getCode() && opcode <= ISTORE_3.getCode())) {
            int index = getLocalIndex(instr);
            return state.pop().setLocal(index, VerificationType.INTEGER);
        }

        if (opcode == LSTORE.getCode() || (opcode >= LSTORE_0.getCode() && opcode <= LSTORE_3.getCode())) {
            int index = getLocalIndex(instr);
            return state.pop(2).setLocal(index, VerificationType.LONG);
        }

        if (opcode == FSTORE.getCode() || (opcode >= FSTORE_0.getCode() && opcode <= FSTORE_3.getCode())) {
            int index = getLocalIndex(instr);
            return state.pop().setLocal(index, VerificationType.FLOAT);
        }

        if (opcode == DSTORE.getCode() || (opcode >= DSTORE_0.getCode() && opcode <= DSTORE_3.getCode())) {
            int index = getLocalIndex(instr);
            return state.pop(2).setLocal(index, VerificationType.DOUBLE);
        }

        if (opcode == ASTORE.getCode() || (opcode >= ASTORE_0.getCode() && opcode <= ASTORE_3.getCode())) {
            int index = getLocalIndex(instr);
            VerificationType type = state.peek();
            return state.pop().setLocal(index, type);
        }

        if (opcode == IASTORE.getCode() || opcode == BASTORE.getCode() || opcode == CASTORE.getCode() || opcode == SASTORE.getCode()) {
            return state.pop(3);
        }

        if (opcode == LASTORE.getCode()) {
            return state.pop(4);
        }

        if (opcode == FASTORE.getCode()) {
            return state.pop(3);
        }

        if (opcode == DASTORE.getCode()) {
            return state.pop(4);
        }

        if (opcode == AASTORE.getCode()) {
            return state.pop(3);
        }

        if (opcode == POP.getCode()) {
            return state.pop();
        }

        if (opcode == POP2.getCode()) {
            return state.pop(2);
        }

        // The dup/swap family operate on raw stack SLOTS (a long/double occupies two: {VALUE, TOP}).
        // They reconstruct the stack from peek() results with pushRaw (not push) so a duplicated long's
        // value slot is not re-expanded into a second {VALUE, TOP} pair, which would inflate the depth.
        if (opcode == DUP.getCode()) {
            return state.pushRaw(state.peek(0));
        }

        if (opcode == DUP_X1.getCode()) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            return state.pop(2).pushRaw(v1).pushRaw(v2).pushRaw(v1);
        }

        if (opcode == DUP_X2.getCode()) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            VerificationType v3 = state.peek(2);
            return state.pop(3).pushRaw(v1).pushRaw(v3).pushRaw(v2).pushRaw(v1);
        }

        if (opcode == DUP2.getCode()) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            return state.pushRaw(v2).pushRaw(v1);
        }

        if (opcode == DUP2_X1.getCode()) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            VerificationType v3 = state.peek(2);
            return state.pop(3).pushRaw(v2).pushRaw(v1).pushRaw(v3).pushRaw(v2).pushRaw(v1);
        }

        if (opcode == DUP2_X2.getCode()) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            VerificationType v3 = state.peek(2);
            VerificationType v4 = state.peek(3);
            return state.pop(4).pushRaw(v2).pushRaw(v1).pushRaw(v4).pushRaw(v3).pushRaw(v2).pushRaw(v1);
        }

        if (opcode == SWAP.getCode()) {
            VerificationType v1 = state.peek(0);
            VerificationType v2 = state.peek(1);
            return state.pop(2).pushRaw(v1).pushRaw(v2);
        }

        if (opcode == IADD.getCode() || opcode == ISUB.getCode() || opcode == IMUL.getCode() || opcode == IDIV.getCode() ||
            opcode == IREM.getCode() || opcode == IAND.getCode() || opcode == IOR.getCode() || opcode == IXOR.getCode()) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == LADD.getCode() || opcode == LSUB.getCode() || opcode == LMUL.getCode() || opcode == LDIV.getCode() ||
            opcode == LREM.getCode() || opcode == LAND.getCode() || opcode == LOR.getCode() || opcode == LXOR.getCode()) {
            return state.pop(4).push(VerificationType.LONG);
        }

        if (opcode == FADD.getCode() || opcode == FSUB.getCode() || opcode == FMUL.getCode() || opcode == FDIV.getCode() || opcode == FREM.getCode()) {
            return state.pop(2).push(VerificationType.FLOAT);
        }

        if (opcode == DADD.getCode() || opcode == DSUB.getCode() || opcode == DMUL.getCode() || opcode == DDIV.getCode() || opcode == DREM.getCode()) {
            return state.pop(4).push(VerificationType.DOUBLE);
        }

        if (opcode >= INEG.getCode() && opcode <= DNEG.getCode()) {
            return state;
        }

        if (opcode == ISHL.getCode() || opcode == ISHR.getCode() || opcode == IUSHR.getCode()) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == LSHL.getCode() || opcode == LSHR.getCode() || opcode == LUSHR.getCode()) {
            return state.pop(3).push(VerificationType.LONG);
        }

        if (opcode == IINC.getCode()) {
            return state;
        }

        if (opcode == I2L.getCode()) {
            return state.pop().push(VerificationType.LONG);
        }

        if (opcode == I2F.getCode()) {
            return state.pop().push(VerificationType.FLOAT);
        }

        if (opcode == I2D.getCode()) {
            return state.pop().push(VerificationType.DOUBLE);
        }

        if (opcode == L2I.getCode()) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == L2F.getCode()) {
            return state.pop(2).push(VerificationType.FLOAT);
        }

        if (opcode == L2D.getCode()) {
            return state.pop(2).push(VerificationType.DOUBLE);
        }

        if (opcode == F2I.getCode()) {
            return state.pop().push(VerificationType.INTEGER);
        }

        if (opcode == F2L.getCode()) {
            return state.pop().push(VerificationType.LONG);
        }

        if (opcode == F2D.getCode()) {
            return state.pop().push(VerificationType.DOUBLE);
        }

        if (opcode == D2I.getCode()) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == D2L.getCode()) {
            return state.pop(2).push(VerificationType.LONG);
        }

        if (opcode == D2F.getCode()) {
            return state.pop(2).push(VerificationType.FLOAT);
        }

        if (opcode == I2B.getCode() || opcode == I2C.getCode() || opcode == I2S.getCode()) {
            return state;
        }

        if (opcode == LCMP.getCode()) {
            return state.pop(4).push(VerificationType.INTEGER);
        }

        if (opcode == FCMPL.getCode() || opcode == FCMPG.getCode()) {
            return state.pop(2).push(VerificationType.INTEGER);
        }

        if (opcode == DCMPL.getCode() || opcode == DCMPG.getCode()) {
            return state.pop(4).push(VerificationType.INTEGER);
        }

        if (opcode >= IFEQ.getCode() && opcode <= IFLE.getCode()) {
            return state.pop();
        }

        if (opcode >= IF_ICMPEQ.getCode() && opcode <= IF_ICMPLE.getCode()) {
            return state.pop(2);
        }

        if (opcode == IF_ACMPEQ.getCode() || opcode == IF_ACMPNE.getCode()) {
            return state.pop(2);
        }

        if (opcode == GOTO.getCode() || opcode == GOTO_W.getCode()) {
            return state;
        }

        if (opcode == JSR.getCode() || opcode == JSR_W.getCode()) {
            return state.push(VerificationType.TOP);
        }

        if (opcode == RET.getCode()) {
            return state;
        }

        if (opcode == TABLESWITCH.getCode() || opcode == LOOKUPSWITCH.getCode()) {
            return state.pop();
        }

        if (opcode == IRETURN.getCode()) {
            return state.clearStack();
        }

        if (opcode == LRETURN.getCode()) {
            return state.clearStack();
        }

        if (opcode == FRETURN.getCode()) {
            return state.clearStack();
        }

        if (opcode == DRETURN.getCode()) {
            return state.clearStack();
        }

        if (opcode == ARETURN.getCode()) {
            return state.clearStack();
        }

        if (opcode == RETURN_.getCode()) {
            return state.clearStack();
        }

        if (opcode == GETSTATIC.getCode()) {
            return applyGetField(state, instr, true);
        }

        if (opcode == PUTSTATIC.getCode()) {
            return applyPutField(state, instr, true);
        }

        if (opcode == GETFIELD.getCode()) {
            return applyGetField(state, instr, false);
        }

        if (opcode == PUTFIELD.getCode()) {
            return applyPutField(state, instr, false);
        }

        if (opcode == INVOKEVIRTUAL.getCode()) {
            return applyInvoke(state, instr, false, false);
        }

        if (opcode == INVOKESPECIAL.getCode()) {
            return applyInvoke(state, instr, false, true);
        }

        if (opcode == INVOKESTATIC.getCode()) {
            return applyInvoke(state, instr, true, false);
        }

        if (opcode == INVOKEINTERFACE.getCode()) {
            return applyInvoke(state, instr, false, false);
        }

        if (opcode == INVOKEDYNAMIC.getCode()) {
            return applyInvokeDynamic(state, instr);
        }

        if (opcode == NEW.getCode()) {
            return state.push(VerificationType.uninitialized(instr.getOffset()));
        }

        if (opcode == NEWARRAY.getCode()) {
            state = state.pop();
            if (instr instanceof NewArrayInstruction) {
                NewArrayInstruction newArray = (NewArrayInstruction) instr;
                String arrayDesc = newArrayTypeDescriptor(newArray.getArrayType());
                int classIndex = constPool.findOrAddClass(arrayDesc).getIndex(constPool);
                return state.push(VerificationType.object(classIndex));
            }
            return state.push(VerificationType.object(getObjectClassIndex()));
        }

        if (opcode == ANEWARRAY.getCode()) {
            state = state.pop();
            if (instr instanceof ANewArrayInstruction) {
                ANewArrayInstruction anewArray = (ANewArrayInstruction) instr;
                String elementClass = anewArray.resolveClass();
                String arrayDesc;
                if (elementClass.startsWith("[")) {
                    arrayDesc = "[" + elementClass;
                } else {
                    arrayDesc = "[L" + elementClass + ";";
                }
                int classIndex = constPool.findOrAddClass(arrayDesc).getIndex(constPool);
                return state.push(VerificationType.object(classIndex));
            }
            return state.push(VerificationType.object(getObjectClassIndex()));
        }

        if (opcode == ARRAYLENGTH.getCode()) {
            return state.pop().push(VerificationType.INTEGER);
        }

        if (opcode == ATHROW.getCode()) {
            return state.clearStack();
        }

        if (opcode == CHECKCAST.getCode()) {
            state = state.pop();
            if (instr instanceof CheckCastInstruction) {
                CheckCastInstruction checkCast = (CheckCastInstruction) instr;
                int classIndex = checkCast.getClassIndex();
                return state.push(VerificationType.object(classIndex));
            }
            return state.push(VerificationType.object(getObjectClassIndex()));
        }

        if (opcode == INSTANCEOF.getCode()) {
            return state.pop().push(VerificationType.INTEGER);
        }

        if (opcode == MONITORENTER.getCode() || opcode == MONITOREXIT.getCode()) {
            return state.pop();
        }

        if (opcode == WIDE.getCode()) {
            return state;
        }

        if (opcode == MULTIANEWARRAY.getCode()) {
            if (instr instanceof MultiANewArrayInstruction) {
                MultiANewArrayInstruction multiArray = (MultiANewArrayInstruction) instr;
                int dimensions = multiArray.getDimensions();
                state = state.pop(dimensions);
                return state.push(VerificationType.object(multiArray.getClassIndex()));
            }
            return state;
        }

        if (opcode == IFNULL.getCode() || opcode == IFNONNULL.getCode()) {
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

        if (instr instanceof com.tonic.analysis.instruction.GetFieldInstruction) {
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

        return state.push(VerificationType.INTEGER);
    }

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
                if (objectRef instanceof VerificationType.UninitializedType
                        || objectRef.equals(VerificationType.UNINITIALIZED_THIS)) {
                    int classIndex = getInitClassIndex(instr);
                    VerificationType initializedType = VerificationType.object(classIndex);
                    state = state.replaceType(objectRef, initializedType);
                }
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
        return instr instanceof LocalVarInstruction ? ((LocalVarInstruction) instr).getVarIndex() : 0;
    }

    /**
     * Gets or creates a class index for java/lang/Object.
     *
     * @return constant pool index for Object class
     */
    private int getInitClassIndex(Instruction instr) {
        if (instr instanceof InvokeSpecialInstruction) {
            InvokeSpecialInstruction invoke = (InvokeSpecialInstruction) instr;
            int methodIndex = invoke.getMethodIndex();
            Item<?> item = constPool.getItem(methodIndex);
            if (item instanceof MethodRefItem) {
                return ((MethodRefItem) item).getValue().getClassIndex();
            }
        }
        return getObjectClassIndex();
    }

    private int getObjectClassIndex() {
        return constPool.findOrAddClass("java/lang/Object").getIndex(constPool);
    }

    private String newArrayTypeDescriptor(NewArrayInstruction.ArrayType arrayType) {
        if (arrayType == null) {
            return "[Ljava/lang/Object;";
        }
        switch (arrayType) {
            case T_BOOLEAN: return "[Z";
            case T_CHAR:    return "[C";
            case T_FLOAT:   return "[F";
            case T_DOUBLE:  return "[D";
            case T_BYTE:    return "[B";
            case T_SHORT:   return "[S";
            case T_INT:     return "[I";
            case T_LONG:    return "[J";
            default:        return "[Ljava/lang/Object;";
        }
    }
}
