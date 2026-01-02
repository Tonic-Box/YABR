package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.FieldEntry;

import java.util.*;

public class SwitchMapAnalyzer {

    public static void analyzeClass(List<FieldEntry> fields, List<IRMethod> methods) {
        Set<String> switchMapFieldNames = new HashSet<>();
        for (FieldEntry field : fields) {
            String name = field.getName();
            if (name != null && name.startsWith("$SwitchMap$")) {
                switchMapFieldNames.add(name);
            }
        }

        if (switchMapFieldNames.isEmpty()) {
            return;
        }

        for (IRMethod method : methods) {
            if ("<clinit>".equals(method.getName())) {
                analyzeStaticInit(method, switchMapFieldNames);
                break;
            }
        }
    }

    private static void analyzeStaticInit(IRMethod clinit, Set<String> switchMapFieldNames) {
        Map<SSAValue, String> valueToEnumConstant = new HashMap<>();
        Map<SSAValue, String> valueToSwitchMapField = new HashMap<>();

        for (IRBlock block : clinit.getBlocks()) {
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof FieldAccessInstruction) {
                    FieldAccessInstruction fieldInstr = (FieldAccessInstruction) instr;
                    if (fieldInstr.isStatic() && fieldInstr.isLoad()) {
                        String fieldName = fieldInstr.getName();
                        String fieldOwner = fieldInstr.getOwner();

                        if (fieldName != null && switchMapFieldNames.contains(fieldName) &&
                            fieldInstr.getResult() != null) {
                            valueToSwitchMapField.put(fieldInstr.getResult(), fieldName);
                        }
                        else if (fieldOwner != null && fieldInstr.getResult() != null) {
                            String enumClassName = fieldOwner.replace('/', '.');
                            valueToEnumConstant.put(fieldInstr.getResult(), enumClassName + "." + fieldName);
                        }
                    }
                }
                else if (instr instanceof InvokeInstruction) {
                    InvokeInstruction invokeInstr = (InvokeInstruction) instr;
                    if ("ordinal".equals(invokeInstr.getName()) &&
                        "()I".equals(invokeInstr.getDescriptor()) &&
                        invokeInstr.getResult() != null) {

                        List<Value> args = invokeInstr.getArguments();
                        if (!args.isEmpty()) {
                            Value receiver = args.get(0);
                            if (receiver instanceof SSAValue) {
                                String enumConstant = valueToEnumConstant.get(receiver);
                                if (enumConstant != null) {
                                    valueToEnumConstant.put(invokeInstr.getResult(), enumConstant);
                                }
                            }
                        }
                    }
                }
                else if (instr instanceof ArrayAccessInstruction) {
                    ArrayAccessInstruction arrayInstr = (ArrayAccessInstruction) instr;
                    if (!arrayInstr.isStore()) {
                        continue;
                    }

                    Value array = arrayInstr.getArray();
                    Value index = arrayInstr.getIndex();
                    Value storeValue = arrayInstr.getValue();

                    if (!(array instanceof SSAValue)) {
                        continue;
                    }

                    Integer caseValue = null;
                    if (storeValue instanceof IntConstant) {
                        caseValue = ((IntConstant) storeValue).getValue();
                    } else if (storeValue instanceof SSAValue) {
                        SSAValue storeSSA = (SSAValue) storeValue;
                        IRInstruction def = storeSSA.getDefinition();
                        if (def instanceof ConstantInstruction) {
                            Value constValue = ((ConstantInstruction) def).getConstant();
                            if (constValue instanceof IntConstant) {
                                caseValue = ((IntConstant) constValue).getValue();
                            }
                        }
                    }

                    if (caseValue == null) {
                        continue;
                    }

                    String switchMapField = valueToSwitchMapField.get(array);
                    if (switchMapField == null) {
                        SSAValue arraySSA = (SSAValue) array;
                        IRInstruction defInstr = arraySSA.getDefinition();
                        if (defInstr instanceof FieldAccessInstruction) {
                            FieldAccessInstruction fieldDef = (FieldAccessInstruction) defInstr;
                            if (switchMapFieldNames.contains(fieldDef.getName())) {
                                switchMapField = fieldDef.getName();
                            }
                        }
                    }

                    if (switchMapField == null) {
                        continue;
                    }

                    String enumConstant = null;
                    if (index instanceof SSAValue) {
                        enumConstant = valueToEnumConstant.get(index);
                    }

                    if (enumConstant == null) {
                        continue;
                    }

                    String enumClassName = EnumSwitchMapRegistry.parseEnumClassFromFieldName(switchMapField);
                    if (enumClassName != null) {
                        String constantName = extractConstantName(enumConstant);
                        EnumSwitchMapRegistry.getInstance().registerMapping(enumClassName, caseValue, constantName);
                    }
                }
            }
        }
    }

    private static String extractConstantName(String fullConstant) {
        int lastDot = fullConstant.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fullConstant.length() - 1) {
            return fullConstant.substring(lastDot + 1);
        }
        return fullConstant;
    }
}
