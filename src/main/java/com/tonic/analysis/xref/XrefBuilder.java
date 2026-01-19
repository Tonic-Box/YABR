package com.tonic.analysis.xref;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.DynamicConstant;
import com.tonic.analysis.ssa.value.MethodHandleConstant;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.ConstantDynamicItem;
import com.tonic.parser.constpool.FieldRefItem;
import com.tonic.parser.constpool.InterfaceRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.MethodHandleItem;
import com.tonic.parser.constpool.MethodRefItem;
import lombok.Getter;

import java.util.function.Consumer;

/**
 * Builds a XrefDatabase by scanning all classes in a ClassPool.
 * <p>
 * Detects:
 * - Method calls (invokevirtual, invokeinterface, invokestatic, invokespecial)
 * - Field reads (getfield, getstatic)
 * - Field writes (putfield, putstatic)
 * - Class instantiation (new)
 * - Type casts (checkcast)
 * - Instanceof checks (instanceof)
 * - Class inheritance (extends, implements)
 */
public class XrefBuilder {

    private final ClassPool classPool;
    /**
     * -- GETTER --
     *  Get the database being built (for incremental access).
     */
    @Getter
    private final XrefDatabase database;
    private Consumer<String> progressCallback;
    private int methodsProcessed;

    public XrefBuilder(ClassPool classPool) {
        this.classPool = classPool;
        this.database = new XrefDatabase();
    }

    /**
     * Set a callback to receive progress updates.
     */
    public void setProgressCallback(Consumer<String> callback) {
        this.progressCallback = callback;
    }

    /**
     * Build the xref database by scanning all classes.
     */
    public XrefDatabase build() {
        long startTime = System.currentTimeMillis();
        database.clear();
        int classesProcessed = 0;
        methodsProcessed = 0;

        if (classPool == null || classPool.getClasses() == null) {
            return database;
        }

        // Process each class
        for (ClassFile cf : classPool.getClasses()) {
            try {
                processClass(cf);
                classesProcessed++;

                if (progressCallback != null && classesProcessed % 10 == 0) {
                    progressCallback.accept("Processed " + classesProcessed + " classes...");
                }
            } catch (Exception e) {
                // Skip classes that fail to process
            }
        }

        long endTime = System.currentTimeMillis();
        database.setBuildTimeMs(endTime - startTime);
        database.setTotalClasses(classesProcessed);
        database.setTotalMethods(methodsProcessed);

        if (progressCallback != null) {
            progressCallback.accept(database.getSummary());
        }

        return database;
    }

    /**
     * Process a single class file.
     */
    private void processClass(ClassFile cf) {
        String className = cf.getClassName();

        // Class-level references: extends
        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object") && !superClass.isEmpty()) {
            database.addXref(Xref.builder()
                .sourceClass(className)
                .targetClass(superClass)
                .type(XrefType.CLASS_EXTENDS)
                .build());
        }

        // Class-level references: implements
        if (cf.getInterfaces() != null) {
            for (Integer ifaceIndex : cf.getInterfaces()) {
                String ifaceName = resolveClassName(cf, ifaceIndex);
                if (ifaceName != null && !ifaceName.isEmpty()) {
                    database.addXref(Xref.builder()
                        .sourceClass(className)
                        .targetClass(ifaceName)
                        .type(XrefType.CLASS_IMPLEMENTS)
                        .build());
                }
            }
        }

        // Process fields for type references
        if (cf.getFields() != null) {
            for (FieldEntry field : cf.getFields()) {
                String fieldType = extractTypeFromDescriptor(field.getDesc());
                if (fieldType != null && !isPrimitive(fieldType)) {
                    database.addXref(Xref.builder()
                        .sourceClass(className)
                        .targetClass(fieldType)
                        .type(XrefType.TYPE_LOCAL_VAR)
                        .build());
                }
            }
        }

        // Process each method
        if (cf.getMethods() != null) {
            for (MethodEntry method : cf.getMethods()) {
                processMethod(cf, method);
                methodsProcessed++;
            }
        }

        // Scan all bootstrap methods for method references (catches nested condy, etc.)
        processAllBootstrapMethods(cf, className);
    }

    /**
     * Process a single method.
     */
    private void processMethod(ClassFile cf, MethodEntry method) {
        if (method.getCodeAttribute() == null) {
            return;
        }

        String className = cf.getClassName();
        String methodName = method.getName();
        String methodDesc = method.getDesc();

        try {
            // Use SSA to get IR representation
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod irMethod = ssa.lift(method);

            if (irMethod == null || irMethod.getEntryBlock() == null) {
                return;
            }

            int instrIndex = 0;
            // Scan all blocks for xref-relevant instructions
            for (IRBlock block : irMethod.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    processInstruction(cf, instr, className, methodName, methodDesc, instrIndex);
                    instrIndex++;
                }
            }
        } catch (Exception e) {
            // Skip methods that fail to lift
        }
    }

    /**
     * Process a single IR instruction for xrefs.
     */
    private void processInstruction(ClassFile cf, IRInstruction instr, String sourceClass,
                                   String sourceMethod, String sourceMethodDesc, int instrIndex) {

        if (instr instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) instr;
            database.addXref(Xref.builder()
                .sourceClass(sourceClass)
                .sourceMethod(sourceMethod, sourceMethodDesc)
                .instructionIndex(instrIndex)
                .targetMethod(invoke.getOwner(), invoke.getName(), invoke.getDescriptor())
                .type(XrefType.METHOD_CALL)
                .build());

            if (invoke.isDynamic() && invoke.hasBootstrapInfo()) {
                processBootstrapInfo(cf, invoke.getBootstrapInfo(), sourceClass, sourceMethod, sourceMethodDesc, instrIndex);
            }

        } else if (instr instanceof FieldAccessInstruction) {
            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
            XrefType xrefType = fieldAccess.isLoad() ? XrefType.FIELD_READ : XrefType.FIELD_WRITE;
            database.addXref(Xref.builder()
                .sourceClass(sourceClass)
                .sourceMethod(sourceMethod, sourceMethodDesc)
                .instructionIndex(instrIndex)
                .targetField(fieldAccess.getOwner(), fieldAccess.getName(), fieldAccess.getDescriptor())
                .type(xrefType)
                .build());

        } else if (instr instanceof NewInstruction) {
            NewInstruction newInstr = (NewInstruction) instr;
            String targetClass = newInstr.getClassName();
            database.addXref(Xref.builder()
                .sourceClass(sourceClass)
                .sourceMethod(sourceMethod, sourceMethodDesc)
                .instructionIndex(instrIndex)
                .targetClass(targetClass)
                .type(XrefType.CLASS_INSTANTIATE)
                .build());

        } else if (instr instanceof TypeCheckInstruction) {
            TypeCheckInstruction typeCheck = (TypeCheckInstruction) instr;
            IRType targetType = typeCheck.getTargetType();
            if (targetType != null) {
                String typeName = extractTypeFromIRType(targetType);
                if (typeName != null && !isPrimitive(typeName)) {
                    XrefType xrefType = typeCheck.isCast() ? XrefType.CLASS_CAST : XrefType.CLASS_INSTANCEOF;
                    database.addXref(Xref.builder()
                        .sourceClass(sourceClass)
                        .sourceMethod(sourceMethod, sourceMethodDesc)
                        .instructionIndex(instrIndex)
                        .targetClass(typeName)
                        .type(xrefType)
                        .build());
                }
            }

        } else if (instr instanceof ConstantInstruction) {
            ConstantInstruction constInstr = (ConstantInstruction) instr;
            Constant constant = constInstr.getConstant();
            processConstantForXref(cf, constant, sourceClass, sourceMethod, sourceMethodDesc, instrIndex);
        }

        for (Value operand : instr.getOperands()) {
            if (operand instanceof MethodHandleConstant) {
                processMethodHandleConstant((MethodHandleConstant) operand, sourceClass, sourceMethod, sourceMethodDesc, instrIndex, XrefType.BOOTSTRAP_ARG_METHOD);
            } else if (operand instanceof DynamicConstant) {
                processDynamicConstant(cf, (DynamicConstant) operand, sourceClass, sourceMethod, sourceMethodDesc, instrIndex);
            }
        }
    }

    /**
     * Process a constant value for xrefs (method handles, dynamic constants).
     */
    private void processConstantForXref(ClassFile cf, Constant constant, String sourceClass,
                                        String sourceMethod, String sourceMethodDesc, int instrIndex) {
        if (constant instanceof MethodHandleConstant) {
            processMethodHandleConstant((MethodHandleConstant) constant, sourceClass, sourceMethod, sourceMethodDesc, instrIndex, XrefType.BOOTSTRAP_ARG_METHOD);
        } else if (constant instanceof DynamicConstant) {
            processDynamicConstant(cf, (DynamicConstant) constant, sourceClass, sourceMethod, sourceMethodDesc, instrIndex);
        }
    }

    /**
     * Process bootstrap method info from invokedynamic.
     */
    private void processBootstrapInfo(ClassFile cf, BootstrapMethodInfo bootstrapInfo, String sourceClass,
                                      String sourceMethod, String sourceMethodDesc, int instrIndex) {
        MethodHandleConstant bsm = bootstrapInfo.getBootstrapMethod();
        if (bsm != null) {
            processMethodHandleConstant(bsm, sourceClass, sourceMethod, sourceMethodDesc, instrIndex, XrefType.BOOTSTRAP_METHOD);
        }

        for (Constant arg : bootstrapInfo.getBootstrapArguments()) {
            if (arg instanceof MethodHandleConstant) {
                processMethodHandleConstant((MethodHandleConstant) arg, sourceClass, sourceMethod, sourceMethodDesc, instrIndex, XrefType.BOOTSTRAP_ARG_METHOD);
            } else if (arg instanceof DynamicConstant) {
                processDynamicConstant(cf, (DynamicConstant) arg, sourceClass, sourceMethod, sourceMethodDesc, instrIndex);
            }
        }
    }

    /**
     * Process a MethodHandle constant for xrefs.
     */
    private void processMethodHandleConstant(MethodHandleConstant mh, String sourceClass,
                                             String sourceMethod, String sourceMethodDesc,
                                             int instrIndex, XrefType xrefType) {
        if (mh.isMethodReference()) {
            database.addXref(Xref.builder()
                .sourceClass(sourceClass)
                .sourceMethod(sourceMethod, sourceMethodDesc)
                .instructionIndex(instrIndex)
                .targetMethod(mh.getOwner(), mh.getName(), mh.getDescriptor())
                .type(xrefType)
                .build());
        } else if (mh.isFieldReference()) {
            XrefType fieldXrefType = XrefType.BOOTSTRAP_ARG_FIELD;
            database.addXref(Xref.builder()
                .sourceClass(sourceClass)
                .sourceMethod(sourceMethod, sourceMethodDesc)
                .instructionIndex(instrIndex)
                .targetField(mh.getOwner(), mh.getName(), mh.getDescriptor())
                .type(fieldXrefType)
                .build());
        }
    }

    /**
     * Process a DynamicConstant (condy) for xrefs.
     * Resolves the bootstrap method and its arguments from the constant pool.
     */
    private void processDynamicConstant(ClassFile cf, DynamicConstant condy, String sourceClass,
                                        String sourceMethod, String sourceMethodDesc, int instrIndex) {
        try {
            BootstrapMethodsAttribute bsmAttr = findBootstrapMethodsAttribute(cf);
            if (bsmAttr == null || bsmAttr.getBootstrapMethods() == null) {
                return;
            }

            int bsmIndex = condy.getBootstrapMethodIndex();
            if (bsmIndex < 0 || bsmIndex >= bsmAttr.getBootstrapMethods().size()) {
                return;
            }

            BootstrapMethod bsm = bsmAttr.getBootstrapMethods().get(bsmIndex);
            processBootstrapMethodFromCP(cf, bsm, sourceClass, sourceMethod, sourceMethodDesc, instrIndex);
        } catch (Exception e) {
            // Ignore resolution failures
        }
    }

    /**
     * Scans all bootstrap methods in a class for method references.
     * This catches references in unused or indirectly referenced bootstrap entries.
     */
    private void processAllBootstrapMethods(ClassFile cf, String sourceClass) {
        try {
            BootstrapMethodsAttribute bsmAttr = findBootstrapMethodsAttribute(cf);
            if (bsmAttr == null || bsmAttr.getBootstrapMethods() == null) {
                return;
            }

            for (BootstrapMethod bsm : bsmAttr.getBootstrapMethods()) {
                processBootstrapMethodFromCP(cf, bsm, sourceClass, null, null, -1);
            }
        } catch (Exception e) {
            // Ignore failures
        }
    }

    /**
     * Finds the BootstrapMethods attribute in a class file.
     */
    private BootstrapMethodsAttribute findBootstrapMethodsAttribute(ClassFile cf) {
        if (cf.getClassAttributes() == null) {
            return null;
        }
        for (var attr : cf.getClassAttributes()) {
            if (attr instanceof BootstrapMethodsAttribute) {
                return (BootstrapMethodsAttribute) attr;
            }
        }
        return null;
    }

    /**
     * Process a bootstrap method entry from the constant pool.
     */
    private void processBootstrapMethodFromCP(ClassFile cf, BootstrapMethod bsm, String sourceClass,
                                              String sourceMethod, String sourceMethodDesc, int instrIndex) {
        try {
            Item<?> mhItem = cf.getConstPool().getItem(bsm.getBootstrapMethodRef());
            if (mhItem instanceof MethodHandleItem) {
                processMethodHandleFromCP(cf, (MethodHandleItem) mhItem, sourceClass, sourceMethod, sourceMethodDesc, instrIndex, XrefType.BOOTSTRAP_METHOD);
            }

            for (Integer argIndex : bsm.getBootstrapArguments()) {
                Item<?> argItem = cf.getConstPool().getItem(argIndex);
                if (argItem instanceof MethodHandleItem) {
                    MethodHandleItem mhArg = (MethodHandleItem) argItem;
                    int refKind = mhArg.getValue().getReferenceKind();
                    XrefType type = (refKind >= 5 && refKind <= 9) ? XrefType.BOOTSTRAP_ARG_METHOD : XrefType.BOOTSTRAP_ARG_FIELD;
                    processMethodHandleFromCP(cf, mhArg, sourceClass, sourceMethod, sourceMethodDesc, instrIndex, type);
                } else if (argItem instanceof ConstantDynamicItem) {
                    ConstantDynamicItem nestedCondy = (ConstantDynamicItem) argItem;
                    processNestedCondy(cf, nestedCondy, sourceClass, sourceMethod, sourceMethodDesc, instrIndex);
                }
            }
        } catch (Exception e) {
            // Ignore resolution failures
        }
    }

    /**
     * Process a MethodHandleItem from the constant pool by resolving its reference.
     */
    private void processMethodHandleFromCP(ClassFile cf, MethodHandleItem mhItem, String sourceClass,
                                           String sourceMethod, String sourceMethodDesc,
                                           int instrIndex, XrefType xrefType) {
        try {
            int refKind = mhItem.getValue().getReferenceKind();
            int refIndex = mhItem.getValue().getReferenceIndex();
            Item<?> refItem = cf.getConstPool().getItem(refIndex);

            String owner = null, name = null, descriptor = null;

            if (refItem instanceof MethodRefItem) {
                MethodRefItem methodRef = (MethodRefItem) refItem;
                owner = methodRef.getOwner();
                name = methodRef.getName();
                descriptor = methodRef.getDescriptor();
            } else if (refItem instanceof InterfaceRefItem) {
                InterfaceRefItem ifaceRef = (InterfaceRefItem) refItem;
                owner = ifaceRef.getOwner();
                name = ifaceRef.getName();
                descriptor = ifaceRef.getDescriptor();
            } else if (refItem instanceof FieldRefItem) {
                FieldRefItem fieldRef = (FieldRefItem) refItem;
                owner = fieldRef.getOwner();
                name = fieldRef.getName();
                descriptor = fieldRef.getDescriptor();
            }

            if (owner != null && name != null) {
                if (refKind >= 5 && refKind <= 9) {
                    database.addXref(Xref.builder()
                        .sourceClass(sourceClass)
                        .sourceMethod(sourceMethod, sourceMethodDesc)
                        .instructionIndex(instrIndex)
                        .targetMethod(owner, name, descriptor)
                        .type(xrefType)
                        .build());
                } else if (refKind >= 1 && refKind <= 4) {
                    database.addXref(Xref.builder()
                        .sourceClass(sourceClass)
                        .sourceMethod(sourceMethod, sourceMethodDesc)
                        .instructionIndex(instrIndex)
                        .targetField(owner, name, descriptor)
                        .type(XrefType.BOOTSTRAP_ARG_FIELD)
                        .build());
                }
            }
        } catch (Exception e) {
            // Ignore resolution failures
        }
    }

    /**
     * Process a nested ConstantDynamic from the constant pool.
     */
    private void processNestedCondy(ClassFile cf, ConstantDynamicItem condy, String sourceClass,
                                    String sourceMethod, String sourceMethodDesc, int instrIndex) {
        try {
            BootstrapMethodsAttribute bsmAttr = findBootstrapMethodsAttribute(cf);
            if (bsmAttr == null || bsmAttr.getBootstrapMethods() == null) {
                return;
            }

            int bsmIndex = condy.getBootstrapMethodAttrIndex();
            if (bsmIndex < 0 || bsmIndex >= bsmAttr.getBootstrapMethods().size()) {
                return;
            }

            BootstrapMethod bsm = bsmAttr.getBootstrapMethods().get(bsmIndex);
            processBootstrapMethodFromCP(cf, bsm, sourceClass, sourceMethod, sourceMethodDesc, instrIndex);
        } catch (Exception e) {
            // Ignore resolution failures
        }
    }

    /**
     * Resolve class name from constant pool index.
     */
    private String resolveClassName(ClassFile cf, int classIndex) {
        try {
            var classRef = cf.getConstPool().getItem(classIndex);
            if (classRef instanceof ClassRefItem) {
                return ((ClassRefItem) classRef).getClassName();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Extract class name from a field/method descriptor.
     * For example: "Ljava/lang/String;" -> "java/lang/String"
     */
    private String extractTypeFromDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) {
            return null;
        }

        // Handle array types - get the element type
        int arrayDepth = 0;
        while (desc.charAt(arrayDepth) == '[') {
            arrayDepth++;
        }
        if (arrayDepth > 0) {
            desc = desc.substring(arrayDepth);
        }

        // Handle object types
        if (desc.startsWith("L") && desc.endsWith(";")) {
            return desc.substring(1, desc.length() - 1);
        }

        return null; // Primitive type
    }

    /**
     * Extract class name from an IRType.
     */
    private String extractTypeFromIRType(IRType type) {
        if (type == null) {
            return null;
        }
        String typeStr = type.toString();

        // Handle array types
        if (typeStr.startsWith("[")) {
            return extractTypeFromDescriptor(typeStr);
        }

        // Handle object types like "Ljava/lang/String;"
        if (typeStr.startsWith("L") && typeStr.endsWith(";")) {
            return typeStr.substring(1, typeStr.length() - 1);
        }

        // Handle simple class names (already resolved)
        if (typeStr.contains("/") || typeStr.contains(".")) {
            return typeStr.replace('.', '/');
        }

        return null;
    }

    /**
     * Check if a type name represents a primitive type.
     */
    private boolean isPrimitive(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return true;
        }
        return typeName.length() == 1 &&
               "ZBCSIJFD".indexOf(typeName.charAt(0)) >= 0;
    }

}
