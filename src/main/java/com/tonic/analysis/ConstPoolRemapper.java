package com.tonic.analysis;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.table.BootstrapMethod;
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
 * Symbolically re-resolves constant-pool entries from a source {@link ClassFile}'s pool into a target
 * {@link ClassFile}'s pool, returning the target index for any source index. Method/field/interface,
 * class, string and numeric constants are find-or-added; {@code invokedynamic}/dynamic constants also
 * copy and remap the referenced bootstrap method (handle + static arguments) into the target's
 * {@code BootstrapMethods} attribute.
 *
 * <p>Pass {@link #remap} as the {@code cpRemap} of
 * {@link CodeWriter#cloneRangeWithTargets(com.tonic.analysis.instruction.Instruction,
 * com.tonic.analysis.instruction.Instruction, int, ConstPool, java.util.function.IntUnaryOperator)} to
 * relocate a method body across pools — used by {@link MethodGrafter}, and directly when splicing a
 * source body into a method the target already owns (e.g. merging into an existing {@code <clinit>}).
 * One instance is stateful (it caches results and accumulates bootstrap entries) and is reusable
 * across several bodies sharing the same source/target pair.
 */
public final class ConstPoolRemapper {
    private final ClassFile source;
    private final ClassFile target;
    private final ConstPool sp;
    private final ConstPool tp;
    private final Map<Integer, Integer> cache = new HashMap<>();

    public ConstPoolRemapper(ClassFile source, ClassFile target) {
        this.source = source;
        this.target = target;
        this.sp = source.getConstPool();
        this.tp = target.getConstPool();
    }

    /**
     * Returns the index in the target pool of the constant equivalent to {@code srcIndex} in the
     * source pool, creating it (and any dependencies) if absent.
     *
     * @param srcIndex a constant-pool index in the source pool
     * @return the equivalent index in the target pool
     */
    public int remap(int srcIndex) {
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
                    "cannot remap constant-pool item of type " + item.getClass().getSimpleName());
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
                    "cannot remap a method handle referencing " + ref.getClass().getSimpleName());
        }
        return tp.getIndexOf(tp.findOrAddMethodHandle(v.getReferenceKind(), owner, name, desc));
    }

    /** Copies the source bootstrap-method entry into the target's BootstrapMethods, returning its index. */
    private int remapBootstrap(int srcBootstrapIndex) {
        BootstrapMethodsAttribute srcBsm = source.getBootstrapMethodsAttribute();
        if (srcBsm == null) {
            throw new IllegalStateException("source class has no BootstrapMethods attribute");
        }
        BootstrapMethod bm = srcBsm.getBootstrapMethods().get(srcBootstrapIndex);
        int handle = remap(bm.getBootstrapMethodRef());
        List<Integer> args = new ArrayList<>(bm.getBootstrapArguments().size());
        for (int a : bm.getBootstrapArguments()) {
            args.add(remap(a));
        }
        return target.addBootstrapMethod(handle, args);
    }
}
