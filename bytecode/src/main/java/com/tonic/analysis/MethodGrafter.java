package com.tonic.analysis;

import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Copies a method from one {@link ClassFile} into another, re-resolving its constant-pool references into
 * the target pool.
 */
public final class MethodGrafter
{

    private MethodGrafter()
    {
    }

    /**
     * Grafts {@code method} from {@code source} into {@code target} as a brand-new method, returning the new
     * method entry on the target.
     * @param source the class file the method currently lives in
     * @param method the method to copy
     * @param target the class file to copy it into
     * @return the newly created method on {@code target}
     */
    public static MethodEntry graftMethod(ClassFile source, MethodEntry method, ClassFile target)
    {
        MethodEntry grafted = target.createNewMethodWithDescriptor(
                method.getAccess(), method.getName(), method.getDesc());
        copyBodyInto(source, method, target, grafted);
        return grafted;
    }

    /**
     * Replaces {@code targetMethod}'s body in place with {@code sourceMethod}'s, remapping every constant-pool
     * reference into the target.
     * @param source       the class file {@code sourceMethod} lives in
     * @param sourceMethod the method whose body to copy
     * @param target       the class file {@code targetMethod} lives in
     * @param targetMethod the method on {@code target} to overwrite (typically same name and descriptor)
     */
    public static void replaceMethodBody(ClassFile source, MethodEntry sourceMethod, ClassFile target, MethodEntry targetMethod)
    {
        copyBodyInto(source, sourceMethod, target, targetMethod);
    }

    /**
     * Clones {@code method}'s body from {@code source} into {@code destination} on {@code target},
     * remapping constant-pool references and the exception table.
     */
    private static void copyBodyInto(ClassFile source, MethodEntry method, ClassFile target, MethodEntry destination)
    {
        CodeAttribute srcCode = method.getCodeAttribute();
        if (srcCode == null)
        {
            throw new IllegalArgumentException("Cannot graft a method without a Code attribute: " + method.getName());
        }
        ConstPool tp = target.getConstPool();
        ConstPoolRemapper remapper = new ConstPoolRemapper(source, target);

        CodeWriter sourceWriter = new CodeWriter(method);
        List<Instruction> src = new ArrayList<>();
        sourceWriter.getInstructions().forEach(src::add);
        if (src.isEmpty())
        {
            throw new IllegalArgumentException("Cannot graft an empty method: " + method.getName());
        }
        CodeWriter.ClonedRange body = sourceWriter.cloneRangeWithTargets(
                src.get(0), src.get(src.size() - 1), 0, tp, remapper::remap);

        List<ExceptionTableEntry> exceptions = new ArrayList<>();
        for (ExceptionTableEntry ex : srcCode.getExceptionTable())
        {
            int catchType = ex.getCatchType() == 0 ? 0 : remapper.remap(ex.getCatchType());
            exceptions.add(new ExceptionTableEntry(ex.getStartPc(), ex.getEndPc(), ex.getHandlerPc(), catchType));
        }

        CodeWriter targetWriter = new CodeWriter(destination);
        targetWriter.getCodeAttribute().setMaxStack(srcCode.getMaxStack());
        targetWriter.getCodeAttribute().setMaxLocals(srcCode.getMaxLocals());
        targetWriter.replaceBody(body, exceptions);
        new FrameGenerator(tp).updateStackMapTable(destination);
    }
}
