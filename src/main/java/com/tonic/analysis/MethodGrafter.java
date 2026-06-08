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
 * Copies a method from one {@link ClassFile} into another, remapping every constant-pool reference in
 * its body from the source pool into the target pool by symbolic re-resolution (the ASM tree API gets
 * this for free because its operands are symbolic strings; YABR instructions hold source-pool indices,
 * so a graft must re-resolve them). The grafted body is relinked through {@link CodeWriter} so branch
 * and switch targets, the exception table, {@code maxLocals}, and the StackMapTable are all rebuilt in
 * the target. Pair with {@link ClassFile#redirectOwner} when the moved member should now point at the
 * target class.
 *
 * <p>References are remapped by {@link ConstPoolRemapper}: method/field/interface/class refs, {@code ldc}
 * constants (String/Class/int/float/long/double/MethodHandle/MethodType), and {@code invokedynamic}/
 * dynamic constants (whose bootstrap method is copied into the target's {@code BootstrapMethods}).
 */
public final class MethodGrafter {

    private MethodGrafter() {
    }

    /**
     * Grafts {@code method} from {@code source} into {@code target}, returning the new method entry on
     * the target. The source method is left untouched.
     *
     * @param source the class file the method currently lives in
     * @param method the method to copy
     * @param target the class file to copy it into
     * @return the newly created method on {@code target}
     */
    public static MethodEntry graftMethod(ClassFile source, MethodEntry method, ClassFile target) {
        CodeAttribute srcCode = method.getCodeAttribute();
        if (srcCode == null) {
            throw new IllegalArgumentException("Cannot graft a method without a Code attribute: " + method.getName());
        }
        ConstPool tp = target.getConstPool();
        ConstPoolRemapper remapper = new ConstPoolRemapper(source, target);

        MethodEntry grafted = target.createNewMethodWithDescriptor(
                method.getAccess(), method.getName(), method.getDesc());

        CodeWriter sourceWriter = new CodeWriter(method);
        List<Instruction> src = new ArrayList<>();
        sourceWriter.getInstructions().forEach(src::add);
        if (src.isEmpty()) {
            throw new IllegalArgumentException("Cannot graft an empty method: " + method.getName());
        }
        CodeWriter.ClonedRange body = sourceWriter.cloneRangeWithTargets(
                src.get(0), src.get(src.size() - 1), 0, tp, remapper::remap);

        List<ExceptionTableEntry> exceptions = new ArrayList<>();
        for (ExceptionTableEntry ex : srcCode.getExceptionTable()) {
            int catchType = ex.getCatchType() == 0 ? 0 : remapper.remap(ex.getCatchType());
            exceptions.add(new ExceptionTableEntry(ex.getStartPc(), ex.getEndPc(), ex.getHandlerPc(), catchType));
        }

        CodeWriter targetWriter = new CodeWriter(grafted);
        targetWriter.getCodeAttribute().setMaxStack(srcCode.getMaxStack());
        targetWriter.getCodeAttribute().setMaxLocals(srcCode.getMaxLocals());
        targetWriter.replaceBody(body, exceptions);
        new FrameGenerator(tp).updateStackMapTable(grafted);
        return grafted;
    }
}
