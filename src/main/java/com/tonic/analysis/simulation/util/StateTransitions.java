package com.tonic.analysis.simulation.util;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.VoidType;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.utill.DescriptorUtil;

/**
 * Computes state transitions for IR instructions.
 *
 * <p>Each instruction type has a specific effect on the simulation state
 * (stack and locals). This class encapsulates all transition logic.
 */
public final class StateTransitions {

    private StateTransitions() {}

    /**
     * Apply an instruction's effect to the simulation state.
     *
     * @param state the current state before the instruction
     * @param instr the instruction to execute
     * @return the new state after the instruction
     */
    public static SimulationState apply(SimulationState state, IRInstruction instr) {
        if (instr instanceof ConstantInstruction) {
            return applyConstant(state, (ConstantInstruction) instr);
        } else if (instr instanceof LoadLocalInstruction) {
            return applyLoadLocal(state, (LoadLocalInstruction) instr);
        } else if (instr instanceof StoreLocalInstruction) {
            return applyStoreLocal(state, (StoreLocalInstruction) instr);
        } else if (instr instanceof BinaryOpInstruction) {
            return applyBinaryOp(state, (BinaryOpInstruction) instr);
        } else if (instr instanceof UnaryOpInstruction) {
            return applyUnaryOp(state, (UnaryOpInstruction) instr);
        } else if (instr instanceof FieldAccessInstruction) {
            return applyFieldAccess(state, (FieldAccessInstruction) instr);
        } else if (instr instanceof ArrayAccessInstruction) {
            return applyArrayAccess(state, (ArrayAccessInstruction) instr);
        } else if (instr instanceof NewInstruction) {
            return applyNew(state, (NewInstruction) instr);
        } else if (instr instanceof NewArrayInstruction) {
            return applyNewArray(state, (NewArrayInstruction) instr);
        } else if (instr instanceof InvokeInstruction) {
            return applyInvoke(state, (InvokeInstruction) instr);
        } else if (instr instanceof ReturnInstruction) {
            return applyReturn(state, (ReturnInstruction) instr);
        } else if (instr instanceof BranchInstruction) {
            return applyBranch(state, (BranchInstruction) instr);
        } else if (instr instanceof SwitchInstruction) {
            return applySwitch(state, (SwitchInstruction) instr);
        } else if (instr instanceof TypeCheckInstruction) {
            return applyTypeCheck(state, (TypeCheckInstruction) instr);
        } else if (instr instanceof SimpleInstruction) {
            return applySimple(state, (SimpleInstruction) instr);
        } else if (instr instanceof PhiInstruction) {
            return applyPhi(state, (PhiInstruction) instr);
        } else if (instr instanceof CopyInstruction) {
            return applyCopy(state, (CopyInstruction) instr);
        }

        // Unknown instruction - return state unchanged
        return state;
    }

    // ========== Instruction Handlers ==========

    private static SimulationState applyConstant(SimulationState state, ConstantInstruction instr) {
        // Push constant value onto stack
        IRType type = instr.getResultType();
        Object value = instr.getConstant() != null ? instr.getConstant().getValue() : null;
        SimValue simValue = SimValue.constant(value, type, instr);

        if (isWideType(type)) {
            return state.pushWide(simValue);
        }
        return state.push(simValue);
    }

    private static SimulationState applyLoadLocal(SimulationState state, LoadLocalInstruction instr) {
        // Load value from local and push onto stack
        int index = instr.getLocalIndex();
        SimValue value = state.getLocal(index);
        if (value == null) {
            value = SimValue.ofType(instr.getResultType(), instr);
        }

        if (isWideType(instr.getResultType())) {
            return state.pushWide(value);
        }
        return state.push(value);
    }

    private static SimulationState applyStoreLocal(SimulationState state, StoreLocalInstruction instr) {
        // Pop value from stack and store in local
        int index = instr.getLocalIndex();
        SimValue value = state.peek();

        IRType type = instr.getValue() != null ? instr.getValue().getType() : null;
        if (isWideType(type)) {
            return state.popWide().setLocalWide(index, value);
        }
        return state.pop().setLocal(index, value);
    }

    private static SimulationState applyBinaryOp(SimulationState state, BinaryOpInstruction instr) {
        // Pop two operands, push result
        IRType type = instr.getResultType();
        boolean wide = isWideType(type);

        SimulationState newState;
        if (wide) {
            // Wide operands: pop 4 slots total (2 per operand)
            newState = state.popWide().popWide();
        } else {
            // Regular: pop 2 slots
            newState = state.pop(2);
        }

        SimValue result = SimValue.ofType(type, instr);
        if (wide) {
            return newState.pushWide(result);
        }
        return newState.push(result);
    }

    private static SimulationState applyUnaryOp(SimulationState state, UnaryOpInstruction instr) {
        // Pop operand, push result
        IRType inputType = instr.getOperand() != null ? instr.getOperand().getType() : null;
        IRType resultType = instr.getResultType();

        SimulationState newState;
        if (isWideType(inputType)) {
            newState = state.popWide();
        } else {
            newState = state.pop();
        }

        SimValue result = SimValue.ofType(resultType, instr);
        if (isWideType(resultType)) {
            return newState.pushWide(result);
        }
        return newState.push(result);
    }


    private static SimulationState applyNew(SimulationState state, NewInstruction instr) {
        // Push new object reference
        IRType type = instr.getResultType();
        return state.push(SimValue.ofType(type, instr));
    }

    private static SimulationState applyNewArray(SimulationState state, NewArrayInstruction instr) {
        // Pop count(s), push array reference
        int dimensions = instr.getDimensions().size();
        SimulationState newState = state.pop(dimensions);

        IRType type = instr.getResultType();
        return newState.push(SimValue.ofType(type, instr));
    }

    private static SimulationState applyInvoke(SimulationState state, InvokeInstruction instr) {
        // Pop arguments (and receiver for instance methods)
        String descriptor = instr.getDescriptor();
        int argSlots = DescriptorUtil.countParameterSlots(descriptor);

        SimulationState newState = state;

        // Pop arguments
        newState = popSlots(newState, argSlots);

        // Pop receiver for non-static calls
        if (instr.getInvokeType() != InvokeType.STATIC) {
            newState = newState.pop();
        }

        // Push return value if not void
        String returnDesc = DescriptorUtil.parseReturnDescriptor(descriptor);
        if (!"V".equals(returnDesc)) {
            IRType returnType = getTypeFromDescriptor(returnDesc);
            SimValue result = SimValue.ofType(returnType, instr);
            if (isWideType(returnType)) {
                newState = newState.pushWide(result);
            } else {
                newState = newState.push(result);
            }
        }

        return newState;
    }

    private static SimulationState applyReturn(SimulationState state, ReturnInstruction instr) {
        // For non-void returns, the value is on the stack
        // We don't modify the state as control flow leaves the method
        return state;
    }

    private static SimulationState applyBranch(SimulationState state, BranchInstruction instr) {
        // Pop comparison operands
        // The number depends on whether it's a single-value or two-value comparison
        Value left = instr.getLeft();
        Value right = instr.getRight();

        int popCount = 0;
        if (left != null) popCount++;
        if (right != null) popCount++;

        return state.pop(popCount);
    }

    private static SimulationState applySwitch(SimulationState state, SwitchInstruction instr) {
        // Pop the key value
        return state.pop();
    }

    private static SimulationState applyPhi(SimulationState state, PhiInstruction instr) {
        // Phi nodes don't directly affect the stack in simulation
        // They represent value merging at control flow join points
        // The result is conceptually "on the stack" but handled via SSA values
        IRType type = instr.getResultType();
        SimValue result = SimValue.ofType(type, instr);
        if (isWideType(type)) {
            return state.pushWide(result);
        }
        return state.push(result);
    }

    private static SimulationState applyCopy(SimulationState state, CopyInstruction instr) {
        // Copy instruction moves a value (no stack effect in SSA)
        // Treat as identity
        return state;
    }

    private static SimulationState applyFieldAccess(SimulationState state, FieldAccessInstruction instr) {
        if (instr.isLoad()) {
            SimulationState newState = state;
            if (!instr.isStatic()) {
                newState = state.pop();
            }
            IRType fieldType = getTypeFromDescriptor(instr.getDescriptor());
            SimValue result = SimValue.ofType(fieldType, instr);
            if (isWideType(fieldType)) {
                return newState.pushWide(result);
            }
            return newState.push(result);
        } else {
            IRType fieldType = getTypeFromDescriptor(instr.getDescriptor());
            SimulationState newState;
            if (isWideType(fieldType)) {
                newState = state.popWide();
            } else {
                newState = state.pop();
            }
            if (!instr.isStatic()) {
                newState = newState.pop();
            }
            return newState;
        }
    }

    private static SimulationState applyArrayAccess(SimulationState state, ArrayAccessInstruction instr) {
        if (instr.isLoad()) {
            SimulationState newState = state.pop(2);
            IRType elementType = instr.getResult() != null ? instr.getResult().getType() : null;
            SimValue result = SimValue.ofType(elementType, instr);
            if (isWideType(elementType)) {
                return newState.pushWide(result);
            }
            return newState.push(result);
        } else {
            Value valueOperand = instr.getValue();
            IRType valueType = valueOperand != null ? valueOperand.getType() : null;
            SimulationState newState;
            if (isWideType(valueType)) {
                newState = state.popWide();
            } else {
                newState = state.pop();
            }
            return newState.pop(2);
        }
    }

    private static SimulationState applyTypeCheck(SimulationState state, TypeCheckInstruction instr) {
        if (instr.isCast()) {
            IRType targetType = instr.getTargetType();
            Value sourceValue = instr.getOperand();
            IRType sourceType = sourceValue != null ? sourceValue.getType() : null;
            SimulationState newState;
            if (isWideType(sourceType)) {
                newState = state.popWide();
            } else {
                newState = state.pop();
            }
            SimValue result = SimValue.ofType(targetType, instr);
            if (isWideType(targetType)) {
                return newState.pushWide(result);
            }
            return newState.push(result);
        } else {
            return state.pop().push(SimValue.ofType(PrimitiveType.INT, instr));
        }
    }

    private static SimulationState applySimple(SimulationState state, SimpleInstruction instr) {
        switch (instr.getOp()) {
            case ARRAYLENGTH:
                return state.pop().push(SimValue.ofType(PrimitiveType.INT, instr));
            case MONITORENTER:
                return state.pop();
            case MONITOREXIT:
                return state.pop();
            case ATHROW:
                return state.pop();
            case GOTO:
                return state;
            default:
                return state;
        }
    }

    // ========== Helper Methods ==========

    private static boolean isWideType(IRType type) {
        if (type == null) return false;
        return type.isTwoSlot();
    }

    private static IRType getTypeFromDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) return null;

        char first = descriptor.charAt(0);
        switch (first) {
            case 'V': return VoidType.INSTANCE;
            case 'Z': return PrimitiveType.BOOLEAN;
            case 'B': return PrimitiveType.BYTE;
            case 'C': return PrimitiveType.CHAR;
            case 'S': return PrimitiveType.SHORT;
            case 'I': return PrimitiveType.INT;
            case 'J': return PrimitiveType.LONG;
            case 'F': return PrimitiveType.FLOAT;
            case 'D': return PrimitiveType.DOUBLE;
            case 'L':
            case '[':
                return IRType.fromDescriptor(descriptor);
            default:
                return null;
        }
    }

    /**
     * Pop a specific number of slots from the stack.
     * This accounts for wide values taking 2 slots.
     */
    private static SimulationState popSlots(SimulationState state, int slots) {
        SimulationState newState = state;
        int popped = 0;
        while (popped < slots && newState.stackDepth() > 0) {
            SimValue top = newState.peek();
            if (top.isWide()) {
                newState = newState.popWide();
                popped += 2;
            } else if (top.isWideSecondSlot()) {
                // Skip second slot (should be handled with the first)
                newState = newState.pop();
                popped += 1;
            } else {
                newState = newState.pop();
                popped += 1;
            }
        }
        return newState;
    }

    /**
     * Get the number of stack slots an instruction pops.
     */
    public static int getPopCount(IRInstruction instr) {
        if (instr instanceof ConstantInstruction) return 0;
        if (instr instanceof LoadLocalInstruction) return 0;
        if (instr instanceof StoreLocalInstruction) {
            Value val = ((StoreLocalInstruction) instr).getValue();
            IRType type = val != null ? val.getType() : null;
            return isWideType(type) ? 2 : 1;
        }
        if (instr instanceof BinaryOpInstruction) {
            IRType type = ((BinaryOpInstruction) instr).getResultType();
            return isWideType(type) ? 4 : 2;
        }
        if (instr instanceof UnaryOpInstruction) {
            Value op = ((UnaryOpInstruction) instr).getOperand();
            return op != null && isWideType(op.getType()) ? 2 : 1;
        }
        if (instr instanceof FieldAccessInstruction) {
            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
            if (fieldAccess.isLoad()) {
                return fieldAccess.isStatic() ? 0 : 1;
            } else {
                int slots = isWideType(getTypeFromDescriptor(fieldAccess.getDescriptor())) ? 2 : 1;
                return fieldAccess.isStatic() ? slots : slots + 1;
            }
        }
        if (instr instanceof ArrayAccessInstruction) {
            ArrayAccessInstruction arrayAccess = (ArrayAccessInstruction) instr;
            if (arrayAccess.isLoad()) {
                return 2;
            } else {
                Value val = arrayAccess.getValue();
                return 2 + (val != null && isWideType(val.getType()) ? 2 : 1);
            }
        }
        if (instr instanceof NewInstruction) return 0;
        if (instr instanceof NewArrayInstruction) return ((NewArrayInstruction) instr).getDimensions().size();
        if (instr instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            int slots = DescriptorUtil.countParameterSlots(invoke.getDescriptor());
            return invoke.getInvokeType() != InvokeType.STATIC ? slots + 1 : slots;
        }
        if (instr instanceof BranchInstruction) {
            BranchInstruction branch = (BranchInstruction) instr;
            int count = 0;
            if (branch.getLeft() != null) count++;
            if (branch.getRight() != null) count++;
            return count;
        }
        if (instr instanceof SwitchInstruction) return 1;
        if (instr instanceof TypeCheckInstruction) {
            TypeCheckInstruction typeCheck = (TypeCheckInstruction) instr;
            if (typeCheck.isCast()) {
                Value val = typeCheck.getOperand();
                return val != null && isWideType(val.getType()) ? 2 : 1;
            } else {
                return 1;
            }
        }
        if (instr instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) instr;
            switch (simple.getOp()) {
                case ARRAYLENGTH:
                case MONITORENTER:
                case MONITOREXIT:
                case ATHROW:
                    return 1;
                case GOTO:
                default:
                    return 0;
            }
        }
        return 0;
    }

    /**
     * Get the number of stack slots an instruction pushes.
     */
    public static int getPushCount(IRInstruction instr) {
        if (instr instanceof ConstantInstruction) {
            return isWideType(((ConstantInstruction) instr).getResultType()) ? 2 : 1;
        }
        if (instr instanceof LoadLocalInstruction) {
            return isWideType(((LoadLocalInstruction) instr).getResultType()) ? 2 : 1;
        }
        if (instr instanceof BinaryOpInstruction) {
            return isWideType(((BinaryOpInstruction) instr).getResultType()) ? 2 : 1;
        }
        if (instr instanceof UnaryOpInstruction) {
            return isWideType(((UnaryOpInstruction) instr).getResultType()) ? 2 : 1;
        }
        if (instr instanceof FieldAccessInstruction) {
            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
            if (fieldAccess.isLoad()) {
                String desc = fieldAccess.getDescriptor();
                return isWideType(getTypeFromDescriptor(desc)) ? 2 : 1;
            } else {
                return 0;
            }
        }
        if (instr instanceof ArrayAccessInstruction) {
            ArrayAccessInstruction arrayAccess = (ArrayAccessInstruction) instr;
            if (arrayAccess.isLoad()) {
                Value result = arrayAccess.getResult();
                return result != null && isWideType(result.getType()) ? 2 : 1;
            } else {
                return 0;
            }
        }
        if (instr instanceof NewInstruction) return 1;
        if (instr instanceof NewArrayInstruction) return 1;
        if (instr instanceof InvokeInstruction) {
            String returnDesc = DescriptorUtil.parseReturnDescriptor(
                ((InvokeInstruction) instr).getDescriptor());
            if ("V".equals(returnDesc)) return 0;
            return isWideType(getTypeFromDescriptor(returnDesc)) ? 2 : 1;
        }
        if (instr instanceof TypeCheckInstruction) {
            TypeCheckInstruction typeCheck = (TypeCheckInstruction) instr;
            if (typeCheck.isCast()) {
                return isWideType(typeCheck.getTargetType()) ? 2 : 1;
            } else {
                return 1;
            }
        }
        if (instr instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) instr;
            if (simple.getOp() == SimpleOp.ARRAYLENGTH) {
                return 1;
            }
            return 0;
        }
        if (instr instanceof PhiInstruction) {
            return isWideType(((PhiInstruction) instr).getResultType()) ? 2 : 1;
        }
        return 0;
    }
}
