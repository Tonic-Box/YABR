package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.FieldDecl;
import com.tonic.analysis.source.ast.decl.ImportDecl;
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

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class TypeResolver {

    private final ClassPool classPool;
    @Getter
    private final String currentClass;
    @Setter
    private ClassDecl currentClassDecl;
    @Setter
    private List<ImportDecl> imports = new ArrayList<>();

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
            SourceType jdkType = resolveJdkMethodReturnType(ownerClass, methodName, argTypes);
            if (jdkType != null) {
                return jdkType;
            }
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

    private SourceType resolveJdkMethodReturnType(String ownerClass, String methodName, List<SourceType> argTypes) {
        if ("java/lang/Object".equals(ownerClass)) {
            switch (methodName) {
                case "hashCode":
                    if (argTypes.isEmpty()) return PrimitiveSourceType.INT;
                    break;
                case "equals":
                    if (argTypes.size() == 1) return PrimitiveSourceType.BOOLEAN;
                    break;
                case "toString":
                    if (argTypes.isEmpty()) return ReferenceSourceType.STRING;
                    break;
                case "getClass":
                    if (argTypes.isEmpty()) return new ReferenceSourceType("java/lang/Class");
                    break;
                case "clone":
                    if (argTypes.isEmpty()) return ReferenceSourceType.OBJECT;
                    break;
                case "notify":
                case "notifyAll":
                case "wait":
                    return VoidSourceType.INSTANCE;
            }
        } else if ("java/lang/String".equals(ownerClass)) {
            switch (methodName) {
                case "length":
                    if (argTypes.isEmpty()) return PrimitiveSourceType.INT;
                    break;
                case "charAt":
                    if (argTypes.size() == 1) return PrimitiveSourceType.CHAR;
                    break;
                case "substring":
                    return ReferenceSourceType.STRING;
                case "equals":
                case "equalsIgnoreCase":
                case "startsWith":
                case "endsWith":
                case "contains":
                case "isEmpty":
                    return PrimitiveSourceType.BOOLEAN;
                case "toLowerCase":
                case "toUpperCase":
                case "trim":
                case "concat":
                case "replace":
                case "valueOf":
                    return ReferenceSourceType.STRING;
                case "indexOf":
                case "lastIndexOf":
                case "compareTo":
                case "compareToIgnoreCase":
                    return PrimitiveSourceType.INT;
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

    public String resolveMethodDescriptor(String ownerClass, String methodName, int expectedParamCount) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName)) {
                candidates.add(method.getDesc());
            }
        }

        if (candidates.isEmpty()) {
            String superClass = cf.getSuperClassName();
            if (superClass != null && !superClass.equals("java/lang/Object")) {
                String result = resolveMethodDescriptor(superClass, methodName, expectedParamCount);
                if (result != null) {
                    return result;
                }
            }
            for (int ifaceIdx : cf.getInterfaces()) {
                String iface = cf.resolveClassName(ifaceIdx);
                String result = resolveMethodDescriptor(iface, methodName, expectedParamCount);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        if (expectedParamCount >= 0) {
            for (String desc : candidates) {
                if (countParams(desc) == expectedParamCount) {
                    return desc;
                }
            }
        }

        return candidates.get(0);
    }

    public String resolveConstructorDescriptor(String ownerClass, int expectedParamCount) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return "()V";
        }

        List<String> candidates = new ArrayList<>();
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals("<init>")) {
                candidates.add(method.getDesc());
            }
        }

        if (candidates.isEmpty()) {
            return "()V";
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        if (expectedParamCount >= 0) {
            for (String desc : candidates) {
                if (countParams(desc) == expectedParamCount) {
                    return desc;
                }
            }
        }

        for (String desc : candidates) {
            if (desc.equals("()V")) {
                return desc;
            }
        }

        return candidates.get(0);
    }

    public List<String> findAllMethodDescriptors(String ownerClass, String methodName) {
        List<String> results = new ArrayList<>();
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return results;
        }

        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName)) {
                results.add(method.getDesc());
            }
        }

        if (results.isEmpty()) {
            String superClass = cf.getSuperClassName();
            if (superClass != null && !superClass.equals("java/lang/Object")) {
                results.addAll(findAllMethodDescriptors(superClass, methodName));
            }
            for (int ifaceIdx : cf.getInterfaces()) {
                String iface = cf.resolveClassName(ifaceIdx);
                results.addAll(findAllMethodDescriptors(iface, methodName));
            }
        }

        return results;
    }

    public boolean isStaticMethod(String ownerClass, String methodName, String descriptor) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return false;
        }

        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName) && method.getDesc().equals(descriptor)) {
                return (method.getAccess() & 0x0008) != 0;
            }
        }

        return false;
    }

    private int countParams(String descriptor) {
        int count = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                while (i < descriptor.length() && descriptor.charAt(i) != ';') {
                    i++;
                }
                i++;
                count++;
            } else if (c == '[') {
                i++;
            } else {
                i++;
                count++;
            }
        }
        return count;
    }

    public String resolveClassName(String simpleName) {
        if (simpleName.contains("/")) {
            return simpleName;
        }
        if (simpleName.contains(".")) {
            return simpleName.replace('.', '/');
        }

        for (ImportDecl imp : imports) {
            if (!imp.isStatic() && !imp.isWildcard()) {
                String importName = imp.getName();
                String simpleImport = imp.getSimpleName();
                if (simpleImport.equals(simpleName)) {
                    return importName.replace('.', '/');
                }
            }
        }

        for (ImportDecl imp : imports) {
            if (!imp.isStatic() && imp.isWildcard()) {
                String packageName = imp.getName().replace('.', '/');
                String candidate = packageName + "/" + simpleName;
                if (classPool.get(candidate) != null) {
                    return candidate;
                }
            }
        }

        if (simpleName.equals("System")) return "java/lang/System";
        if (simpleName.equals("Math")) return "java/lang/Math";
        if (simpleName.equals("String")) return "java/lang/String";
        if (simpleName.equals("Object")) return "java/lang/Object";
        if (simpleName.equals("Integer")) return "java/lang/Integer";
        if (simpleName.equals("Long")) return "java/lang/Long";
        if (simpleName.equals("Double")) return "java/lang/Double";
        if (simpleName.equals("Float")) return "java/lang/Float";
        if (simpleName.equals("Boolean")) return "java/lang/Boolean";
        if (simpleName.equals("Character")) return "java/lang/Character";
        if (simpleName.equals("Byte")) return "java/lang/Byte";
        if (simpleName.equals("Short")) return "java/lang/Short";
        if (simpleName.equals("Class")) return "java/lang/Class";
        if (simpleName.equals("StringBuilder")) return "java/lang/StringBuilder";
        if (simpleName.equals("Thread")) return "java/lang/Thread";
        if (simpleName.equals("Throwable")) return "java/lang/Throwable";
        if (simpleName.equals("Exception")) return "java/lang/Exception";
        if (simpleName.equals("RuntimeException")) return "java/lang/RuntimeException";

        String ownerSimpleName = currentClass.contains("/")
            ? currentClass.substring(currentClass.lastIndexOf('/') + 1)
            : currentClass;
        if (simpleName.equals(ownerSimpleName)) {
            return currentClass;
        }

        return simpleName;
    }
}
