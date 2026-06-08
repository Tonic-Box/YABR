package com.tonic.analysis;

import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.ConstantDynamicItem;
import com.tonic.parser.constpool.DoubleItem;
import com.tonic.parser.constpool.FieldRefItem;
import com.tonic.parser.constpool.FloatItem;
import com.tonic.parser.constpool.IntegerItem;
import com.tonic.parser.constpool.InterfaceRefItem;
import com.tonic.parser.constpool.InvokeDynamicItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.LongItem;
import com.tonic.parser.constpool.MethodHandleItem;
import com.tonic.parser.constpool.MethodRefItem;
import com.tonic.parser.constpool.MethodTypeItem;
import com.tonic.parser.constpool.NameAndTypeRefItem;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.parser.constpool.structure.ConstantDynamic;
import com.tonic.parser.constpool.structure.InvokeDynamic;
import com.tonic.parser.constpool.structure.MethodHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copies a method from one {@link ClassFile} into another, remapping every constant-pool reference in
 * its body from the source pool into the target pool by symbolic re-resolution (the ASM tree API gets
 * this for free because its operands are symbolic strings; YABR instructions hold source-pool indices,
 * so a graft must re-resolve them). The grafted body is relinked through {@link CodeWriter} so branch
 * and switch targets, the exception table, {@code maxLocals}, and the StackMapTable are all rebuilt in
 * the target. Pair with {@link ClassFile#redirectOwner} when the moved member should now point at the
 * target class.
 *
 * <p>Supported references: method/field/interface-method refs, class refs, {@code ldc} constants
 * (String/Class/int/float/long/double/MethodHandle/MethodType), and {@code invokedynamic}/dynamic
 * constants — for the latter the referenced bootstrap method (handle + static arguments) is copied and
 * remapped into the target's {@code BootstrapMethods} attribute.
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
        Remapper remapper = new Remapper(source, target);

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

    /** Symbolically re-resolves source constant-pool entries (and bootstrap methods) into the target. */
    private static final class Remapper {
        private final ClassFile source;
        private final ClassFile target;
        private final ConstPool sp;
        private final ConstPool tp;
        private final Map<Integer, Integer> cache = new HashMap<>();
        private BootstrapMethodsAttribute targetBootstraps;

        Remapper(ClassFile source, ClassFile target) {
            this.source = source;
            this.target = target;
            this.sp = source.getConstPool();
            this.tp = target.getConstPool();
        }

        int remap(int srcIndex) {
            Integer cached = cache.get(srcIndex);
            if (cached != null) {
                return cached;
            }
            Item<?> item = sp.getItem(srcIndex);
            int out;
            if (item instanceof Utf8Item) {
                out = tp.getIndexOf(tp.findOrAddUtf8(((Utf8Item) item).getValue()));
            } else if (item instanceof ClassRefItem) {
                out = tp.getIndexOf(tp.findOrAddClass(((ClassRefItem) item).getClassName()));
            } else if (item instanceof MethodRefItem) {
                MethodRefItem m = (MethodRefItem) item;
                out = tp.getIndexOf(tp.findOrAddMethodRef(m.getOwner(), m.getName(), m.getDescriptor()));
            } else if (item instanceof FieldRefItem) {
                FieldRefItem f = (FieldRefItem) item;
                out = tp.getIndexOf(tp.findOrAddFieldRef(f.getOwner(), f.getName(), f.getDescriptor()));
            } else if (item instanceof InterfaceRefItem) {
                InterfaceRefItem n = (InterfaceRefItem) item;
                out = tp.getIndexOf(tp.findOrAddInterfaceRef(n.getOwner(), n.getName(), n.getDescriptor()));
            } else if (item instanceof NameAndTypeRefItem) {
                NameAndTypeRefItem nt = (NameAndTypeRefItem) item;
                out = tp.getIndexOf(tp.findOrAddNameAndType(nt.getName(), nt.getDescriptor()));
            } else if (item instanceof StringRefItem) {
                String value = ((Utf8Item) sp.getItem(((StringRefItem) item).getValue())).getValue();
                out = tp.getIndexOf(tp.findOrAddString(value));
            } else if (item instanceof IntegerItem) {
                out = tp.getIndexOf(tp.findOrAddInteger(((IntegerItem) item).getValue()));
            } else if (item instanceof FloatItem) {
                out = tp.getIndexOf(tp.findOrAddFloat(((FloatItem) item).getValue()));
            } else if (item instanceof LongItem) {
                out = tp.getIndexOf(tp.findOrAddLong(((LongItem) item).getValue()));
            } else if (item instanceof DoubleItem) {
                out = tp.getIndexOf(tp.findOrAddDouble(((DoubleItem) item).getValue()));
            } else if (item instanceof MethodHandleItem) {
                out = remapMethodHandle((MethodHandleItem) item);
            } else if (item instanceof MethodTypeItem) {
                String desc = ((Utf8Item) sp.getItem(((MethodTypeItem) item).getValue())).getValue();
                out = tp.getIndexOf(tp.findOrAddMethodType(desc));
            } else if (item instanceof InvokeDynamicItem) {
                InvokeDynamic v = ((InvokeDynamicItem) item).getValue();
                int nat = remap(v.getNameAndTypeIndex());
                int bsm = remapBootstrap(v.getBootstrapMethodAttrIndex());
                out = tp.addInvokeDynamic(bsm, nat);
            } else if (item instanceof ConstantDynamicItem) {
                ConstantDynamicItem cd = (ConstantDynamicItem) item;
                ConstantDynamic v = cd.getValue();
                int bsm = remapBootstrap(v.getBootstrapMethodAttrIndex());
                out = tp.getIndexOf(tp.findOrAddConstantDynamic(bsm, cd.getName(), cd.getDescriptor()));
            } else {
                throw new UnsupportedOperationException(
                        "graft cannot remap constant-pool item of type " + item.getClass().getSimpleName());
            }
            cache.put(srcIndex, out);
            return out;
        }

        private int remapMethodHandle(MethodHandleItem mh) {
            MethodHandle v = mh.getValue();
            Item<?> ref = sp.getItem(v.getReferenceIndex());
            String owner;
            String name;
            String desc;
            if (ref instanceof MethodRefItem) {
                MethodRefItem m = (MethodRefItem) ref;
                owner = m.getOwner();
                name = m.getName();
                desc = m.getDescriptor();
            } else if (ref instanceof FieldRefItem) {
                FieldRefItem f = (FieldRefItem) ref;
                owner = f.getOwner();
                name = f.getName();
                desc = f.getDescriptor();
            } else if (ref instanceof InterfaceRefItem) {
                InterfaceRefItem n = (InterfaceRefItem) ref;
                owner = n.getOwner();
                name = n.getName();
                desc = n.getDescriptor();
            } else {
                throw new UnsupportedOperationException(
                        "graft cannot remap a method handle referencing " + ref.getClass().getSimpleName());
            }
            return tp.getIndexOf(tp.findOrAddMethodHandle(v.getReferenceKind(), owner, name, desc));
        }

        /** Copies the source bootstrap-method entry to the target's BootstrapMethods, returning its index. */
        private int remapBootstrap(int srcBootstrapIndex) {
            BootstrapMethodsAttribute srcBsm = findBootstraps(source);
            if (srcBsm == null) {
                throw new IllegalStateException("source class has no BootstrapMethods attribute");
            }
            BootstrapMethod bm = srcBsm.getBootstrapMethods().get(srcBootstrapIndex);
            int handle = remap(bm.getBootstrapMethodRef());
            List<Integer> args = new ArrayList<>(bm.getBootstrapArguments().size());
            for (int a : bm.getBootstrapArguments()) {
                args.add(remap(a));
            }
            BootstrapMethodsAttribute dst = ensureTargetBootstraps();
            List<BootstrapMethod> entries = dst.getBootstrapMethods();
            for (int k = 0; k < entries.size(); k++) {
                BootstrapMethod e = entries.get(k);
                if (e.getBootstrapMethodRef() == handle && e.getBootstrapArguments().equals(args)) {
                    return k;
                }
            }
            dst.addBootstrapMethod(handle, args);
            return entries.size() - 1;
        }

        private BootstrapMethodsAttribute ensureTargetBootstraps() {
            if (targetBootstraps == null) {
                targetBootstraps = findBootstraps(target);
                if (targetBootstraps == null) {
                    targetBootstraps = new BootstrapMethodsAttribute(tp);
                    target.getClassAttributes().add(targetBootstraps);
                }
            }
            return targetBootstraps;
        }

        private static BootstrapMethodsAttribute findBootstraps(ClassFile cf) {
            for (Attribute a : cf.getClassAttributes()) {
                if (a instanceof BootstrapMethodsAttribute) {
                    return (BootstrapMethodsAttribute) a;
                }
            }
            return null;
        }
    }
}
