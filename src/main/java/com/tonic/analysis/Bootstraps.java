package com.tonic.analysis;

import com.tonic.analysis.ssa.ir.BootstrapMethodInfo;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.parser.constpool.FieldRefItem;
import com.tonic.parser.constpool.InterfaceRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.MethodHandleItem;
import com.tonic.parser.constpool.MethodRefItem;
import com.tonic.parser.constpool.structure.MethodHandle;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Single source of truth for resolving invokedynamic/condy bootstraps from a {@link ClassFile}'s
 * BootstrapMethods attribute. Both the disassembly pretty-printer ({@link DisassemblyContext}) and the
 * query engine resolve through {@link #resolve(ClassFile, int)} so the bootstrap handle, static
 * arguments and family classification stay consistent across display and querying.
 *
 * <p>Family classification delegates to {@link BootstrapMethodInfo}'s owner/name predicates so the JDK
 * bootstrap owners are written down once. Public so the query engine
 * ({@code com.tonic.analysis.query.eval}) resolves bootstraps through the same path as disassembly.
 */
public final class Bootstraps {

    private Bootstraps() {
    }

    /**
     * Resolves the bootstrap at the given BootstrapMethods-attribute index.
     *
     * @param classFile the owning class file
     * @param attrIndex the bootstrap-method attribute index (from an indy/condy CP item)
     * @return the resolved reference, or {@code null} when the attribute or index is invalid
     */
    public static BootstrapRef resolve(ClassFile classFile, int attrIndex) {
        if (classFile == null) {
            return null;
        }
        BootstrapMethodsAttribute attribute = classFile.getBootstrapMethodsAttribute();
        if (attribute == null) {
            return null;
        }
        List<BootstrapMethod> methods = attribute.getBootstrapMethods();
        if (methods == null || attrIndex < 0 || attrIndex >= methods.size()) {
            return null;
        }
        BootstrapMethod method = methods.get(attrIndex);
        ConstPool constPool = classFile.getConstPool();
        Item<?> handleItem = constPool.getItem(method.getBootstrapMethodRef());
        if (!(handleItem instanceof MethodHandleItem)) {
            return null;
        }
        MethodHandle handle = ((MethodHandleItem) handleItem).getValue();
        Item<?> ref = constPool.getItem(handle.getReferenceIndex());
        String owner = ownerOf(ref);
        String name = nameOf(ref);
        String descriptor = descriptorOf(ref);
        List<Integer> args = method.getBootstrapArguments();
        return new BootstrapRef(ConstPoolFormat.referenceKindName(handle.getReferenceKind()), owner, name, descriptor,
                args != null ? args : Collections.emptyList());
    }

    /**
     * Renders a loadable constant at the given index to a readable string, mirroring disassembly's
     * constant formatting. Exposed so the query engine resolves bootstrap-argument values through the
     * same formatter without depending on package-private {@link ConstPoolFormat}.
     *
     * @param constPool the constant pool
     * @param cpIndex   the constant-pool index
     * @return a readable representation of the constant
     */
    public static String constantValue(ConstPool constPool, int cpIndex) {
        return ConstPoolFormat.constant(constPool, cpIndex);
    }

    /**
     * Renders a {@code StringConcatFactory} recipe to its readable {@code {arg}}/{@code {const}} form.
     *
     * @param raw the raw recipe string
     * @return the readable recipe, or {@code null} when {@code raw} is {@code null}
     */
    public static String readableRecipe(String raw) {
        return StringConcatRecipe.toReadable(raw);
    }

    private static String ownerOf(Item<?> ref) {
        if (ref instanceof MethodRefItem) {
            return ((MethodRefItem) ref).getOwner();
        }
        if (ref instanceof InterfaceRefItem) {
            return ((InterfaceRefItem) ref).getOwner();
        }
        if (ref instanceof FieldRefItem) {
            return ((FieldRefItem) ref).getOwner();
        }
        return null;
    }

    private static String nameOf(Item<?> ref) {
        if (ref instanceof MethodRefItem) {
            return ((MethodRefItem) ref).getName();
        }
        if (ref instanceof InterfaceRefItem) {
            return ((InterfaceRefItem) ref).getName();
        }
        if (ref instanceof FieldRefItem) {
            return ((FieldRefItem) ref).getName();
        }
        return null;
    }

    private static String descriptorOf(Item<?> ref) {
        if (ref instanceof MethodRefItem) {
            return ((MethodRefItem) ref).getDescriptor();
        }
        if (ref instanceof InterfaceRefItem) {
            return ((InterfaceRefItem) ref).getDescriptor();
        }
        if (ref instanceof FieldRefItem) {
            return ((FieldRefItem) ref).getDescriptor();
        }
        return null;
    }

    /**
     * Immutable resolved view of a single bootstrap method: the handle's reference kind, the resolved
     * owner/name/descriptor of its target member, and the constant-pool indices of its static
     * arguments.
     */
    @Getter
    public static final class BootstrapRef {
        private final String kind;
        private final String owner;
        private final String name;
        private final String descriptor;
        private final List<Integer> argCpIndices;

        BootstrapRef(String kind, String owner, String name, String descriptor, List<Integer> argCpIndices) {
            this.kind = kind;
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.argCpIndices = List.copyOf(argCpIndices);
        }

        /**
         * Classifies the bootstrap family from its owner/name.
         *
         * @return one of {@code stringconcat}, {@code lambda}, {@code switch}, {@code record} or
         *         {@code other}
         */
        public String category() {
            if (BootstrapMethodInfo.isStringConcat(owner)) {
                return "stringconcat";
            }
            if (BootstrapMethodInfo.isLambda(owner, name)) {
                return "lambda";
            }
            if (BootstrapMethodInfo.isSwitch(owner)) {
                return "switch";
            }
            if (BootstrapMethodInfo.isRecord(owner, name)) {
                return "record";
            }
            return "other";
        }
    }
}
