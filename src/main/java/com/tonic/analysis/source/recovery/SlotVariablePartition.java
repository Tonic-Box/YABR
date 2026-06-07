package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BinaryOpInstruction;
import com.tonic.analysis.ssa.ir.CopyInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.LoadLocalInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.analysis.ssa.ir.StoreLocalInstruction;
import com.tonic.analysis.ssa.ir.UnaryOpInstruction;
import com.tonic.analysis.ssa.value.SSAValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Partitions each JVM local slot into one or more source variables using a
 * reaching-definition analysis.
 *
 * <p>JVM bytecode reuses a single local slot for unrelated variables whose live
 * ranges do not overlap (e.g. a {@code String} in one switch case and a
 * {@code boolean} in another). The recovery layer must split such reuse into
 * distinct source variables, otherwise a slot is declared once with an
 * over-broadened type (boolean, or {@code Object}) that does not compile against
 * all its uses.
 *
 * <p>Locals are not in SSA form at this stage, so sameness cannot be read off
 * phi connectivity. Instead we compute, for every load, the set of stores that
 * reach it, and union stores that share a load into one variable. Disjoint reuse
 * therefore yields separate variables, while a genuine control-flow merge (where
 * the verifier already widened the loaded value to the common supertype) keeps
 * the stores in one variable carrying that merged type.
 */
public class SlotVariablePartition {

    private final IRMethod method;
    private final IntFunction<String> baseNameForSlot;

    private final Map<IRInstruction, String> instructionNames = new HashMap<>();

    public SlotVariablePartition(IRMethod method, IntFunction<String> baseNameForSlot) {
        this.method = method;
        this.baseNameForSlot = baseNameForSlot;
        compute();
    }

    /**
     * Returns the variable name for the value stored by the given store, or null
     * if the store was not analyzed.
     */
    public String nameForStore(StoreLocalInstruction store) {
        return instructionNames.get(store);
    }

    /**
     * Returns the variable name for the result of the given load, or null if the
     * load was not analyzed.
     */
    public String nameForLoad(LoadLocalInstruction load) {
        return instructionNames.get(load);
    }

    /**
     * Returns the variable name for the given local phi, or null if the phi does
     * not represent a local slot.
     */
    public String nameForPhi(PhiInstruction phi) {
        return instructionNames.get(phi);
    }

    private static final class Node {
        final int id;
        final int slot;
        int parent;
        Node(int id, int slot) {
            this.id = id;
            this.slot = slot;
            this.parent = id;
        }
    }

    private final List<Node> nodes = new ArrayList<>();
    private final Map<IRInstruction, Node> defNode = new HashMap<>();

    private int find(int x) {
        int root = x;
        while (nodes.get(root).parent != root) {
            root = nodes.get(root).parent;
        }
        while (nodes.get(x).parent != x) {
            int next = nodes.get(x).parent;
            nodes.get(x).parent = root;
            x = next;
        }
        return root;
    }

    private void union(int a, int b) {
        int ra = find(a);
        int rb = find(b);
        if (ra == rb) {
            return;
        }
        if (ra < rb) {
            nodes.get(rb).parent = ra;
        } else {
            nodes.get(ra).parent = rb;
        }
    }

    private Node newNode(int slot) {
        Node n = new Node(nodes.size(), slot);
        nodes.add(n);
        return n;
    }

    private void compute() {
        int paramSlots = computeParameterSlots();
        Map<Integer, Node> paramEntryDef = new HashMap<>();
        for (int slot = 0; slot < paramSlots; slot++) {
            paramEntryDef.put(slot, newNode(slot));
        }

        Map<IRBlock, Map<Integer, Set<Node>>> blockIn = new HashMap<>();
        Map<IRBlock, Map<Integer, Set<Node>>> blockOut = new HashMap<>();
        List<IRBlock> blocks = method.getBlocks();
        for (IRBlock block : blocks) {
            blockIn.put(block, new HashMap<>());
            blockOut.put(block, new HashMap<>());
        }

        // Only stores (and parameter entry) define a slot. Phis are NOT treated as defs:
        // they are transparent merge points (see the final pass). Genuine control-flow
        // merges are recovered by the loads that read the merged value, so the stores that
        // feed a phi are unioned only when something actually reads them together.
        Map<IRBlock, List<SlotDef>> blockDefs = new HashMap<>();
        for (IRBlock block : blocks) {
            List<SlotDef> defs = new ArrayList<>();
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof StoreLocalInstruction) {
                    int slot = ((StoreLocalInstruction) instr).getLocalIndex();
                    Node node = newNode(slot);
                    defNode.put(instr, node);
                    defs.add(new SlotDef(slot, node));
                }
            }
            blockDefs.put(block, defs);
        }

        IRBlock entry = blocks.isEmpty() ? null : blocks.get(0);
        if (entry != null) {
            Map<Integer, Set<Node>> entryIn = blockIn.get(entry);
            for (Map.Entry<Integer, Node> e : paramEntryDef.entrySet()) {
                Set<Node> set = new HashSet<>();
                set.add(e.getValue());
                entryIn.put(e.getKey(), set);
            }
        }

        // Exception edges are not normal CFG edges, so handler blocks have no predecessors and would
        // otherwise receive no reaching definitions — fragmenting a slot that is really one variable
        // shared between the try body and the handler/finally (e.g. `num` in a try/catch/finally).
        // Build, per handler block, the set of try-region blocks it protects; an exception can be
        // thrown at any point in that region, so the handler's reaching-in includes every definition
        // reaching anywhere in the region (its blocks' IN plus their own stores).
        Map<IRBlock, Set<IRBlock>> protectedByHandler = new HashMap<>();
        List<ExceptionHandler> handlers = method.getExceptionHandlers();
        if (handlers != null) {
            for (ExceptionHandler h : handlers) {
                IRBlock hb = h.getHandlerBlock();
                if (hb == null || h.getTryStart() == null) {
                    continue;
                }
                int startOff = h.getTryStart().getBytecodeOffset();
                int endOff = h.getTryEnd() != null ? h.getTryEnd().getBytecodeOffset() : Integer.MAX_VALUE;
                Set<IRBlock> region = protectedByHandler.computeIfAbsent(hb, k -> new HashSet<>());
                for (IRBlock b : blocks) {
                    int off = b.getBytecodeOffset();
                    if (off >= startOff && off < endOff) {
                        region.add(b);
                    }
                }
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (IRBlock block : blocks) {
                Map<Integer, Set<Node>> in = new HashMap<>();
                if (block == entry) {
                    mergeInto(in, blockIn.get(entry));
                }
                for (IRBlock pred : block.getPredecessors()) {
                    // A predecessor may be absent from blockOut if a CFG transform left a dangling
                    // predecessor edge to a block no longer in method.getBlocks(); skip it.
                    Map<Integer, Set<Node>> predOut = blockOut.get(pred);
                    if (predOut != null) {
                        mergeInto(in, predOut);
                    }
                }
                Set<IRBlock> protectedRegion = protectedByHandler.get(block);
                if (protectedRegion != null) {
                    for (IRBlock b : protectedRegion) {
                        Map<Integer, Set<Node>> bIn = blockIn.get(b);
                        if (bIn != null) {
                            mergeInto(in, bIn);
                        }
                        for (SlotDef d : blockDefs.get(b)) {
                            in.computeIfAbsent(d.slot, k -> new HashSet<>()).add(d.node);
                        }
                    }
                }
                Map<Integer, Set<Node>> out = transfer(in, blockDefs.get(block));
                if (!out.equals(blockOut.get(block))) {
                    blockOut.put(block, out);
                    changed = true;
                }
                blockIn.put(block, in);
            }
        }

        for (IRBlock block : blocks) {
            Map<Integer, Set<Node>> reaching = deepCopy(blockIn.getOrDefault(block, new HashMap<>()));
            // Phis sit at block entry and are transparent: record the value(s) reaching the
            // merge so the phi result can be named, but neither union nor redefine the slot.
            for (PhiInstruction phi : block.getPhiInstructions()) {
                int slot = localSlotOfPhi(phi);
                if (slot < 0) {
                    continue;
                }
                // If the phi result is stored back to a slot, the phi IS that store's variable;
                // name it to match the store so the SSA-destruction copies that materialize the
                // phi agree with the store target instead of a (possibly unrelated) reaching value.
                Node storeNode = storeDefForPhiResult(phi);
                if (storeNode != null) {
                    phiRepresentative.put(phi, storeNode.id);
                    continue;
                }
                int rep = minId(reaching.get(slot));
                if (rep >= 0) {
                    phiRepresentative.put(phi, rep);
                }
            }
            for (IRInstruction instr : block.getInstructions()) {
                if (instr instanceof LoadLocalInstruction) {
                    int slot = ((LoadLocalInstruction) instr).getLocalIndex();
                    Set<Node> defs = reaching.get(slot);
                    if (defs != null && !defs.isEmpty()) {
                        int rep = minId(defs);
                        for (Node d : defs) {
                            union(rep, d.id);
                        }
                        loadReaching.put((LoadLocalInstruction) instr, rep);
                    }
                } else if (instr instanceof StoreLocalInstruction) {
                    StoreLocalInstruction store = (StoreLocalInstruction) instr;
                    int slot = store.getLocalIndex();
                    Node node = defNode.get(instr);
                    // Read-modify-write: when the stored value reads the same slot (e.g. `x = x + 2`,
                    // `x++`), this store continues the same source variable, not a new one. Union it
                    // with the definitions reaching the read so the def-use webs (the value being read
                    // vs. the value being written) collapse into one variable instead of fragmenting.
                    Set<Node> priorDefs = reaching.get(slot);
                    if (priorDefs != null && !priorDefs.isEmpty()
                            && storeValueReadsSlot(store, slot)) {
                        int rep = minId(priorDefs);
                        union(rep, node.id);
                        for (Node d : priorDefs) {
                            union(rep, d.id);
                        }
                    }
                    Set<Node> set = new HashSet<>();
                    set.add(node);
                    reaching.put(slot, set);
                }
            }
        }

        assignNames();
    }

    /** True if the value stored by {@code store} transitively reads a load of the same slot (read-modify-write). */
    private boolean storeValueReadsSlot(StoreLocalInstruction store, int slot) {
        return valueReadsSlot(store.getValue(), slot, new HashSet<>());
    }

    private boolean valueReadsSlot(com.tonic.analysis.ssa.value.Value v, int slot, Set<SSAValue> seen) {
        if (!(v instanceof SSAValue)) {
            return false;
        }
        SSAValue ssa = (SSAValue) v;
        if (!seen.add(ssa)) {
            return false;
        }
        IRInstruction def = ssa.getDefinition();
        if (def == null) {
            return false;
        }
        if (def instanceof LoadLocalInstruction) {
            return ((LoadLocalInstruction) def).getLocalIndex() == slot;
        }
        // Trace through pure value-producing instructions (arithmetic, conversions, copies); stop at
        // stores/calls/field/array ops, which would make this a genuinely new value rather than an
        // in-place update of the slot.
        if (def instanceof BinaryOpInstruction || def instanceof UnaryOpInstruction
                || def instanceof CopyInstruction) {
            for (com.tonic.analysis.ssa.value.Value operand : def.getOperands()) {
                if (valueReadsSlot(operand, slot, seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int minId(Set<Node> defs) {
        if (defs == null || defs.isEmpty()) {
            return -1;
        }
        int min = Integer.MAX_VALUE;
        for (Node d : defs) {
            if (d.id < min) {
                min = d.id;
            }
        }
        return min;
    }

    private final Map<LoadLocalInstruction, Integer> loadReaching = new HashMap<>();
    private final Map<PhiInstruction, Integer> phiRepresentative = new HashMap<>();

    private void assignNames() {
        Map<Integer, Map<Integer, Integer>> slotComponentOrder = new LinkedHashMap<>();
        Map<Integer, List<Integer>> slotRoots = new LinkedHashMap<>();
        for (Node node : nodes) {
            int root = find(node.id);
            List<Integer> roots = slotRoots.computeIfAbsent(node.slot, k -> new ArrayList<>());
            if (!roots.contains(root)) {
                roots.add(root);
            }
        }
        for (Map.Entry<Integer, List<Integer>> e : slotRoots.entrySet()) {
            int slot = e.getKey();
            List<Integer> roots = new ArrayList<>(e.getValue());
            roots.sort(Integer::compareTo);
            Map<Integer, Integer> order = new HashMap<>();
            for (int i = 0; i < roots.size(); i++) {
                order.put(roots.get(i), i);
            }
            slotComponentOrder.put(slot, order);
        }

        for (Map.Entry<IRInstruction, Node> e : defNode.entrySet()) {
            IRInstruction instr = e.getKey();
            Node node = e.getValue();
            int root = find(node.id);
            int index = slotComponentOrder.get(node.slot).get(root);
            instructionNames.put(instr, nameFor(node.slot, index));
        }
        for (Map.Entry<LoadLocalInstruction, Integer> e : loadReaching.entrySet()) {
            LoadLocalInstruction load = e.getKey();
            int root = find(e.getValue());
            int slot = nodes.get(root).slot;
            int index = slotComponentOrder.get(slot).get(root);
            instructionNames.put(load, nameFor(slot, index));
        }
        for (Map.Entry<PhiInstruction, Integer> e : phiRepresentative.entrySet()) {
            int root = find(e.getValue());
            int slot = nodes.get(root).slot;
            int index = slotComponentOrder.get(slot).get(root);
            instructionNames.put(e.getKey(), nameFor(slot, index));
        }
    }

    private String nameFor(int slot, int componentIndex) {
        if (componentIndex == 0) {
            return baseNameForSlot.apply(slot);
        }
        return "local" + slot + "_" + componentIndex;
    }

    private Map<Integer, Set<Node>> transfer(Map<Integer, Set<Node>> in, List<SlotDef> defs) {
        Map<Integer, Set<Node>> out = deepCopy(in);
        for (SlotDef def : defs) {
            Set<Node> set = new HashSet<>();
            set.add(def.node);
            out.put(def.slot, set);
        }
        return out;
    }

    private void mergeInto(Map<Integer, Set<Node>> target, Map<Integer, Set<Node>> source) {
        for (Map.Entry<Integer, Set<Node>> e : source.entrySet()) {
            target.computeIfAbsent(e.getKey(), k -> new HashSet<>()).addAll(e.getValue());
        }
    }

    private Map<Integer, Set<Node>> deepCopy(Map<Integer, Set<Node>> source) {
        Map<Integer, Set<Node>> copy = new HashMap<>();
        for (Map.Entry<Integer, Set<Node>> e : source.entrySet()) {
            copy.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        return copy;
    }

    private static final class SlotDef {
        final int slot;
        final Node node;
        SlotDef(int slot, Node node) {
            this.slot = slot;
            this.node = node;
        }
    }

    /** The store def-node for a slot that the phi's result is stored into, or null. */
    private Node storeDefForPhiResult(PhiInstruction phi) {
        SSAValue result = phi.getResult();
        if (result == null) {
            return null;
        }
        int homeSlot = slotFromPhiResultName(phi);
        for (IRInstruction use : result.getUses()) {
            if (use instanceof StoreLocalInstruction) {
                // Skip cross-slot copies of the phi result (e.g. `x = i`, where i is this loop
                // phi): the phi belongs to its own slot, not a slot that merely copies its value.
                // Such a store would otherwise make the phi adopt the copy target's web.
                if (homeSlot >= 0 && ((StoreLocalInstruction) use).getLocalIndex() != homeSlot) {
                    continue;
                }
                Node node = defNode.get(use);
                if (node != null) {
                    return node;
                }
            }
        }
        return null;
    }

    private int localSlotOfPhi(PhiInstruction phi) {
        SSAValue result = phi.getResult();
        if (result == null) {
            return -1;
        }
        int homeSlot = slotFromPhiResultName(phi);
        for (IRInstruction use : result.getUses()) {
            if (use instanceof StoreLocalInstruction) {
                int slot = ((StoreLocalInstruction) use).getLocalIndex();
                if (homeSlot >= 0 && slot != homeSlot) {
                    continue;
                }
                return slot;
            }
        }
        return homeSlot;
    }

    /**
     * The local slot a phi result belongs to, derived from its SSA name. Phi results for locals
     * are named {@code phi_{slot}} or {@code v{slot}_{version}} (or {@code v{slot}}); the leading
     * number is the slot. Returns -1 when the name does not encode a slot.
     */
    private int slotFromPhiResultName(PhiInstruction phi) {
        SSAValue result = phi.getResult();
        if (result == null) {
            return -1;
        }
        String name = result.getName();
        if (name == null) {
            return -1;
        }
        if (name.startsWith("phi_")) {
            try {
                return Integer.parseInt(name.substring(4));
            } catch (NumberFormatException ignored) {
            }
        }
        if (name.matches("v\\d+(_\\d+)?")) {
            int underscore = name.indexOf('_');
            String digits = underscore >= 0 ? name.substring(1, underscore) : name.substring(1);
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private int computeParameterSlots() {
        int slots = method.isStatic() ? 0 : 1;
        String descriptor = method.getDescriptor();
        if (descriptor == null) {
            return slots;
        }
        int i = descriptor.indexOf('(');
        int end = descriptor.indexOf(')');
        if (i < 0 || end < 0) {
            return slots;
        }
        i++;
        while (i < end) {
            char c = descriptor.charAt(i);
            if (c == 'J' || c == 'D') {
                slots += 2;
                i++;
            } else if (c == 'L') {
                slots += 1;
                i = descriptor.indexOf(';', i) + 1;
            } else if (c == '[') {
                while (descriptor.charAt(i) == '[') {
                    i++;
                }
                if (descriptor.charAt(i) == 'L') {
                    i = descriptor.indexOf(';', i) + 1;
                } else {
                    i++;
                }
                slots += 1;
            } else {
                slots += 1;
                i++;
            }
        }
        return slots;
    }
}
