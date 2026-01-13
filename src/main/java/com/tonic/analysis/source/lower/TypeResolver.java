package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.FieldDecl;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;

@RequiredArgsConstructor
public class TypeResolver {

    private final ClassPool classPool;
    @Getter
    private final String currentClass;
    @Setter
    private ClassDecl currentClassDecl;

    public SourceType resolveFieldType(String ownerClass, String fieldName) {
        if (currentClassDecl != null && isCurrentClass(ownerClass)) {
            for (FieldDecl field : currentClassDecl.getFields()) {
                if (field.getName().equals(fieldName)) {
                    return field.getType();
                }
            }
            throw new LoweringException("Cannot resolve field: " + ownerClass + "." + fieldName + " in current class");
        }

        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return null;
        }

        for (FieldEntry field : cf.getFields()) {
            if (field.getName().equals(fieldName)) {
                return parseDescriptor(field.getDesc());
            }
        }

        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object") && !superClass.startsWith("Invalid")) {
            return resolveFieldType(superClass, fieldName);
        }

        return null;
    }

    private boolean isCurrentClass(String ownerClass) {
        if (ownerClass.equals(currentClass)) {
            return true;
        }
        String simpleCurrentClass = currentClass.contains("/")
            ? currentClass.substring(currentClass.lastIndexOf('/') + 1)
            : currentClass;
        return ownerClass.equals(simpleCurrentClass);
    }

    public SourceType resolveMethodReturnType(String ownerClass, String methodName, List<SourceType> argTypes) {
        if (currentClassDecl != null && isCurrentClass(ownerClass)) {
            for (MethodDecl method : currentClassDecl.getMethods()) {
                if (method.getName().equals(methodName) && parametersMatch(method.getParameters(), argTypes)) {
                    return method.getReturnType();
                }
            }
        }

        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return null;
        }

        String expectedParamDesc = buildParamDescriptor(argTypes);

        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName)) {
                String desc = method.getDesc();
                int parenEnd = desc.indexOf(')');
                String paramPart = desc.substring(1, parenEnd);
                if (paramPart.equals(expectedParamDesc)) {
                    String returnPart = desc.substring(parenEnd + 1);
                    return parseDescriptor(returnPart);
                }
            }
        }

        String superClass = cf.getSuperClassName();
        if (superClass != null) {
            SourceType result = resolveMethodReturnType(superClass, methodName, argTypes);
            if (result != null) {
                return result;
            }
        }

        for (int ifaceIdx : cf.getInterfaces()) {
            String iface = cf.resolveClassName(ifaceIdx);
            SourceType result = resolveMethodReturnType(iface, methodName, argTypes);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private boolean parametersMatch(List<ParameterDecl> params, List<SourceType> argTypes) {
        if (params.size() != argTypes.size()) {
            return false;
        }
        for (int i = 0; i < params.size(); i++) {
            SourceType paramType = params.get(i).getType();
            SourceType argType = argTypes.get(i);
            if (argType == null || argType == ReferenceSourceType.OBJECT) {
                continue;
            }
            if (!paramType.equals(argType)) {
                return false;
            }
        }
        return true;
    }

    public SourceType resolveArrayElementType(SourceType arrayType) {
        if (arrayType instanceof ArraySourceType) {
            return ((ArraySourceType) arrayType).getElementType();
        }
        throw new LoweringException("Not an array type: " + arrayType);
    }

    private String buildParamDescriptor(List<SourceType> argTypes) {
        StringBuilder sb = new StringBuilder();
        for (SourceType t : argTypes) {
            sb.append(t.toIRType().getDescriptor());
        }
        return sb.toString();
    }

    private SourceType parseDescriptor(String desc) {
        return parseDescriptor(desc, new int[]{0});
    }

    private SourceType parseDescriptor(String desc, int[] pos) {
        if (pos[0] >= desc.length()) {
            throw new LoweringException("Invalid descriptor: " + desc);
        }

        char c = desc.charAt(pos[0]++);
        switch (c) {
            case 'V':
                return VoidSourceType.INSTANCE;
            case 'Z':
                return PrimitiveSourceType.BOOLEAN;
            case 'B':
                return PrimitiveSourceType.BYTE;
            case 'C':
                return PrimitiveSourceType.CHAR;
            case 'S':
                return PrimitiveSourceType.SHORT;
            case 'I':
                return PrimitiveSourceType.INT;
            case 'J':
                return PrimitiveSourceType.LONG;
            case 'F':
                return PrimitiveSourceType.FLOAT;
            case 'D':
                return PrimitiveSourceType.DOUBLE;
            case 'L':
                int semi = desc.indexOf(';', pos[0]);
                if (semi < 0) {
                    throw new LoweringException("Invalid reference descriptor: " + desc);
                }
                String className = desc.substring(pos[0], semi);
                pos[0] = semi + 1;
                return new ReferenceSourceType(className);
            case '[':
                SourceType elementType = parseDescriptor(desc, pos);
                return new ArraySourceType(elementType);
            default:
                throw new LoweringException("Unknown descriptor character: " + c);
        }
    }
}
