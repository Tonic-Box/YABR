package com.tonic.analysis.xref;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;

import java.util.function.Consumer;

/**
 * Builds a XrefDatabase by scanning all classes in a ClassPool.
 *
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
    private final XrefDatabase database;
    private Consumer<String> progressCallback;
    private int classesProcessed;
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
        classesProcessed = 0;
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
                    processInstruction(instr, className, methodName, methodDesc, instrIndex);
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
    private void processInstruction(IRInstruction instr, String sourceClass,
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
        }
    }

    /**
     * Resolve class name from constant pool index.
     */
    private String resolveClassName(ClassFile cf, int classIndex) {
        try {
            var classRef = cf.getConstPool().getItem(classIndex);
            if (classRef instanceof com.tonic.parser.constpool.ClassRefItem) {
                return ((com.tonic.parser.constpool.ClassRefItem) classRef).getClassName();
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

    /**
     * Get the database being built (for incremental access).
     */
    public XrefDatabase getDatabase() {
        return database;
    }
}
