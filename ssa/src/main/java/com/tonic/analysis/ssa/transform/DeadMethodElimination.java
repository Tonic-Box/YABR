package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.constpool.*;
import com.tonic.util.InstructionLength;

import java.lang.reflect.Modifier;
import java.util.*;

import static com.tonic.util.Opcode.*;

/**
 * Dead method elimination optimization.
 */
public class DeadMethodElimination implements ClassTransform
{

    @Override
    public String getName()
    {
        return "DeadMethodElimination";
    }

    @Override
    public boolean run(ClassFile classFile, SSA ssa)
    {
        String className = classFile.getClassName();

        Set<String> referencedMethods = buildReferencedMethods(classFile, className);

        List<MethodEntry> deadMethods = findDeadMethods(classFile, referencedMethods);

        if (deadMethods.isEmpty())
        {
            return false;
        }

        for (MethodEntry method : deadMethods)
        {
            classFile.getMethods().remove(method);
        }

        return true;
    }

    /**
     * Builds the set of methods that are referenced (called) within the class.
     */
    private Set<String> buildReferencedMethods(ClassFile classFile, String className)
    {
        Set<String> referenced = new HashSet<>();

        for (MethodEntry method : classFile.getMethods())
        {
            CodeAttribute code = method.getCodeAttribute();
            if (code == null) continue;

            byte[] bytecode = code.getCode();
            int i = 0;

            while (i < bytecode.length)
            {
                int opcode = bytecode[i] & 0xFF;

                if (isInvokeInstruction(opcode))
                {
                    int cpIndex = ((bytecode[i + 1] & 0xFF) << 8) | (bytecode[i + 2] & 0xFF);

                    String targetOwner = resolveMethodOwner(classFile, cpIndex);
                    if (targetOwner != null && targetOwner.equals(className))
                    {
                        String targetName = resolveMethodName(classFile, cpIndex);
                        String targetDesc = resolveMethodDescriptor(classFile, cpIndex);
                        if (targetName != null && targetDesc != null)
                        {
                            referenced.add(targetName + targetDesc);
                        }
                    }
                }

                int length = InstructionLength.at(bytecode, i);
                i += length > 0 ? length : 1;
            }
        }

        for (MethodEntry method : classFile.getMethods())
        {
            if (isEntryPoint(method))
            {
                referenced.add(method.getName() + method.getDesc());
            }
        }

        return referenced;
    }

    /**
     * Finds methods that are dead (not referenced and eligible for removal).
     */
    private List<MethodEntry> findDeadMethods(ClassFile classFile, Set<String> referencedMethods)
    {
        List<MethodEntry> dead = new ArrayList<>();

        for (MethodEntry method : classFile.getMethods())
        {
            String key = method.getName() + method.getDesc();

            if (referencedMethods.contains(key))
            {
                continue;
            }

            int access = method.getAccess();
            if (!Modifier.isPrivate(access))
            {
                continue;
            }

            String name = method.getName();
            if (name.equals("<init>") || name.equals("<clinit>"))
            {
                continue;
            }

            dead.add(method);
        }

        return dead;
    }

    /**
     * Checks if a method is an entry point (should never be removed).
     */
    private boolean isEntryPoint(MethodEntry method)
    {
        String name = method.getName();
        int access = method.getAccess();

        if (name.equals("<init>") || name.equals("<clinit>"))
        {
            return true;
        }

        if (name.equals("main") && method.getDesc().equals("([Ljava/lang/String;)V")
                && Modifier.isPublic(access) && Modifier.isStatic(access))
        {
            return true;
        }

        return !Modifier.isPrivate(access);
    }

    /**
     * Checks if an opcode is an invoke instruction.
     */
    private boolean isInvokeInstruction(int opcode)
    {
        return opcode == INVOKEVIRTUAL.getCode()
                || opcode == INVOKESPECIAL.getCode()
                || opcode == INVOKESTATIC.getCode()
                || opcode == INVOKEINTERFACE.getCode();
    }

    /**
     * Resolves the owner class from a method reference constant pool entry.
     */
    private String resolveMethodOwner(ClassFile classFile, int cpIndex)
    {
        try
        {
            var item = classFile.getConstPool().getItem(cpIndex);
            if (item instanceof MethodRefItem)
            {
                MethodRefItem mri = (MethodRefItem) item;
                var classRef = classFile.getConstPool().getItem(mri.getValue().getClassIndex());
                if (classRef instanceof ClassRefItem)
                {
                    ClassRefItem cri = (ClassRefItem) classRef;
                    var nameUtf8 = classFile.getConstPool().getItem(cri.getValue());
                    if (nameUtf8 instanceof Utf8Item)
                    {
                        Utf8Item ui = (Utf8Item) nameUtf8;
                        return ui.getValue();
                    }
                }
            }
            else if (item instanceof InterfaceRefItem)
            {
                InterfaceRefItem imri = (InterfaceRefItem) item;
                var classRef = classFile.getConstPool().getItem(imri.getValue().getClassIndex());
                if (classRef instanceof ClassRefItem)
                {
                    ClassRefItem cri = (ClassRefItem) classRef;
                    var nameUtf8 = classFile.getConstPool().getItem(cri.getValue());
                    if (nameUtf8 instanceof Utf8Item)
                    {
                        Utf8Item ui = (Utf8Item) nameUtf8;
                        return ui.getValue();
                    }
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return null;
    }

    /**
     * Resolves the method name from a method reference constant pool entry.
     */
    private String resolveMethodName(ClassFile classFile, int cpIndex)
    {
        try
        {
            var item = classFile.getConstPool().getItem(cpIndex);
            int natIndex = -1;
            if (item instanceof MethodRefItem)
            {
                MethodRefItem mri = (MethodRefItem) item;
                natIndex = mri.getValue().getNameAndTypeIndex();
            }
            else if (item instanceof InterfaceRefItem)
            {
                InterfaceRefItem imri = (InterfaceRefItem) item;
                natIndex = imri.getValue().getNameAndTypeIndex();
            }
            if (natIndex > 0)
            {
                var nat = classFile.getConstPool().getItem(natIndex);
                if (nat instanceof NameAndTypeRefItem)
                {
                    NameAndTypeRefItem nati = (NameAndTypeRefItem) nat;
                    var nameUtf8 = classFile.getConstPool().getItem(nati.getValue().getNameIndex());
                    if (nameUtf8 instanceof Utf8Item)
                    {
                        Utf8Item ui = (Utf8Item) nameUtf8;
                        return ui.getValue();
                    }
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return null;
    }

    /**
     * Resolves the method descriptor from a method reference constant pool entry.
     */
    private String resolveMethodDescriptor(ClassFile classFile, int cpIndex)
    {
        try
        {
            var item = classFile.getConstPool().getItem(cpIndex);
            int natIndex = -1;
            if (item instanceof MethodRefItem)
            {
                MethodRefItem mri = (MethodRefItem) item;
                natIndex = mri.getValue().getNameAndTypeIndex();
            }
            else if (item instanceof InterfaceRefItem)
            {
                InterfaceRefItem imri = (InterfaceRefItem) item;
                natIndex = imri.getValue().getNameAndTypeIndex();
            }
            if (natIndex > 0)
            {
                var nat = classFile.getConstPool().getItem(natIndex);
                if (nat instanceof NameAndTypeRefItem)
                {
                    NameAndTypeRefItem nati = (NameAndTypeRefItem) nat;
                    var descUtf8 = classFile.getConstPool().getItem(nati.getValue().getDescriptorIndex());
                    if (descUtf8 instanceof Utf8Item)
                    {
                        Utf8Item ui = (Utf8Item) descUtf8;
                        return ui.getValue();
                    }
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return null;
    }

}
