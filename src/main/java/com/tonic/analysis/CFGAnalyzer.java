package com.tonic.analysis;

import com.tonic.analysis.cfg.BasicBlock;
import com.tonic.analysis.cfg.ControlFlowMap;
import com.tonic.analysis.cfg.Statement;
import com.tonic.analysis.instruction.*;
import com.tonic.analysis.instruction.Instruction;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Analyzes the control flow of bytecode instructions and abstracts them into statements.
 */
@Getter
public class CFGAnalyzer {
    private final CodeWriter codeWriter;
    private final List<BasicBlock> basicBlocks = new ArrayList<>();
    private final Map<BasicBlock, List<BasicBlock>> rawControlFlowMap = new HashMap<>();
    private final List<Statement> statements = new ArrayList<>();
    private final ControlFlowMap controlFlowMap;

    /**
     * Constructs a CFGAnalyzer with the given CodeWriter.
     *
     * @param codeWriter The CodeWriter instance containing parsed instructions.
     */
    public CFGAnalyzer(CodeWriter codeWriter) {
        this.codeWriter = codeWriter;
        analyze();
        controlFlowMap = new ControlFlowMap(rawControlFlowMap);
    }

    /**
     * Performs the control flow analysis and statement abstraction.
     */
    public void analyze() {
        identifyBasicBlocks();
        buildControlFlowMap();
        abstractStatements();
    }

    /**
     * Identifies basic blocks within the instruction list.
     */
    private void identifyBasicBlocks() {
        Set<Integer> blockStartOffsets = new HashSet<>();
        Map<Integer, Instruction> instructionsMap = new HashMap<>();

        // Populate the instructionsMap for quick access by offset
        for (Instruction instr : codeWriter.getInstructions()) {
            instructionsMap.put(instr.getOffset(), instr);
        }

        // The first instruction is always a block start
        if (!instructionsMap.isEmpty()) {
            blockStartOffsets.add(0);
        }

        // Identify leaders (start of basic blocks)
        for (Instruction instr : codeWriter.getInstructions()) {
            if (instr instanceof ConditionalBranchInstruction || instr instanceof GotoInstruction
                    || instr instanceof JsrInstruction || instr instanceof RetInstruction) {
                int targetOffset = getBranchTargetOffset(instr);
                if (targetOffset != -1 && instructionsMap.containsKey(targetOffset)) {
                    blockStartOffsets.add(targetOffset);
                }
                // For conditional branches, the next instruction is also a leader
                if (instr instanceof ConditionalBranchInstruction) {
                    int nextOffset = instr.getOffset() + instr.getLength();
                    if (instructionsMap.containsKey(nextOffset)) {
                        blockStartOffsets.add(nextOffset);
                    }
                }
            }
        }

        // Sort the leaders in ascending order
        List<Integer> sortedLeaders = new ArrayList<>(blockStartOffsets);
        Collections.sort(sortedLeaders);

        // Create basic blocks based on identified leaders
        for (int i = 0; i < sortedLeaders.size(); i++) {
            int start = sortedLeaders.get(i);
            int end = (i + 1 < sortedLeaders.size()) ? sortedLeaders.get(i + 1) : codeWriter.getBytecode().length;
            List<Instruction> blockInstructions = new ArrayList<>();

            int offset = start; // Declare 'offset' outside the loop
            while (offset < end) {
                Instruction instr = instructionsMap.get(offset);
                if (instr == null) {
                    System.out.println("Warning: No instruction found at offset " + offset);
                    break; // No instruction found at this offset
                }
                blockInstructions.add(instr);
                offset += instr.getLength();
                // If the instruction is a branch or termination, end the block here
                if (instr instanceof GotoInstruction || instr instanceof ConditionalBranchInstruction
                        || instr instanceof ReturnInstruction || instr instanceof ATHROWInstruction
                        || instr instanceof JsrInstruction || instr instanceof RetInstruction) {
                    break;
                }
            }

            // Create and add the basic block
            BasicBlock block = new BasicBlock(basicBlocks.size(), start, offset, blockInstructions);
            basicBlocks.add(block);

            // Log basic block creation for debugging
            System.out.println("Created Block " + block.getId() + " [Start: " + block.getStartOffset() + ", End: " + block.getEndOffset() + "]");
            for (Instruction instr : block.getInstructions()) {
                System.out.println("  Instruction: " + instr);
            }
        }
    }

    /**
     * Builds the control flow map between basic blocks.
     */
    private void buildControlFlowMap() {
        // Map from bytecode offset to BasicBlock for quick lookup
        Map<Integer, BasicBlock> offsetToBlock = new HashMap<>();
        for (BasicBlock block : basicBlocks) {
            offsetToBlock.put(block.getStartOffset(), block);
        }

        for (BasicBlock block : basicBlocks) {
            List<BasicBlock> successors = new ArrayList<>();
            if (block.getInstructions().isEmpty()) {
                rawControlFlowMap.put(block, successors);
                continue;
            }

            Instruction lastInstr = block.getInstructions().get(block.getInstructions().size() - 1);

            if (lastInstr instanceof GotoInstruction) {
                int target = getBranchTargetOffset(lastInstr);
                BasicBlock targetBlock = getBlockByOffset(target);
                if (targetBlock != null) {
                    successors.add(targetBlock);
                    // Log the successor
                    System.out.println("Block " + block.getId() + " (GOTO) -> Block " + targetBlock.getId());
                } else {
                    // Log invalid target
                    System.out.println("Block " + block.getId() + " (GOTO) has invalid target offset: " + target);
                }
                // Unconditional branch should not have fall-through
            } else if (lastInstr instanceof ConditionalBranchInstruction) {
                int target = getBranchTargetOffset(lastInstr);
                BasicBlock targetBlock = getBlockByOffset(target);
                if (targetBlock != null) {
                    successors.add(targetBlock);
                    // Log the branch target
                    System.out.println("Block " + block.getId() + " (Conditional Branch) -> Block " + targetBlock.getId());
                } else {
                    // Log invalid target
                    System.out.println("Block " + block.getId() + " (Conditional Branch) has invalid target offset: " + target);
                }
                // Fall-through successor
                int fallThrough = lastInstr.getOffset() + lastInstr.getLength();
                BasicBlock fallThroughBlock = getBlockByOffset(fallThrough);
                if (fallThroughBlock != null) {
                    successors.add(fallThroughBlock);
                    // Log the fall-through
                    System.out.println("Block " + block.getId() + " (Conditional Branch) -> Block " + fallThroughBlock.getId() + " (Fall-through)");
                } else {
                    // Log invalid fall-through
                    System.out.println("Block " + block.getId() + " (Conditional Branch) has invalid fall-through offset: " + fallThrough);
                }
            } else if (lastInstr instanceof ReturnInstruction || lastInstr instanceof ATHROWInstruction) {
                // No successors
                System.out.println("Block " + block.getId() + " (Return/Throw) has no successors.");
            } else {
                // Fall-through successor for non-branch instructions
                int fallThrough = lastInstr.getOffset() + lastInstr.getLength();
                BasicBlock fallThroughBlock = getBlockByOffset(fallThrough);
                if (fallThroughBlock != null) {
                    successors.add(fallThroughBlock);
                    // Log the successor
                    System.out.println("Block " + block.getId() + " -> Block " + fallThroughBlock.getId());
                } else {
                    // Log invalid fall-through
                    System.out.println("Block " + block.getId() + " has invalid fall-through offset: " + fallThrough);
                }
            }

            rawControlFlowMap.put(block, successors);
        }
    }

    /**
     * Abstracts bytecode instructions into high-level statements.
     */
    private void abstractStatements() {
        for (BasicBlock block : basicBlocks) {
            Statement blockStatement = new Statement(block.getId(), block.getInstructions());
            statements.add(blockStatement);
        }
    }

    /**
     * Retrieves the branch target offset for a given branch instruction.
     *
     * @param instr The branch instruction.
     * @return The target bytecode offset, or -1 if not applicable.
     */
    private int getBranchTargetOffset(Instruction instr) {
        if (instr instanceof ConditionalBranchInstruction) {
            ConditionalBranchInstruction cbi = (ConditionalBranchInstruction) instr;
            // Cast branchOffset to short to correctly interpret negative values
            short signedOffset = (short) cbi.getBranchOffset();
            int targetOffset = instr.getOffset() + instr.getLength() + signedOffset;
            System.out.println("Calculating target for " + instr + ": " + targetOffset);
            return targetOffset;
        } else if (instr instanceof GotoInstruction) {
            GotoInstruction gi = (GotoInstruction) instr;
            // Cast branchOffset to short to correctly interpret negative values
            short signedOffset = (short) gi.getBranchOffset();
            int targetOffset = instr.getOffset() + gi.getLength() + signedOffset;
            System.out.println("Calculating target for " + instr + ": " + targetOffset);
            return targetOffset;
        } else if (instr instanceof JsrInstruction) {
            JsrInstruction ji = (JsrInstruction) instr;
            // Cast branchOffset to short to correctly interpret negative values
            short signedOffset = (short) ji.getBranchOffset();
            int targetOffset = instr.getOffset() + ji.getLength() + signedOffset;
            System.out.println("Calculating target for " + instr + ": " + targetOffset);
            return targetOffset;
        }
        return -1;
    }

    /**
     * Finds the basic block that contains the given bytecode offset.
     *
     * @param offset The bytecode offset to locate.
     * @return The BasicBlock containing the offset, or null if not found.
     */
    private BasicBlock getBlockByOffset(int offset) {
        for (BasicBlock block : basicBlocks) {
            if (block.getStartOffset() <= offset && offset < block.getEndOffset()) {
                return block;
            }
        }
        // Log a warning if no block contains the offset
        System.out.println("Warning: No block contains offset " + offset);
        return null;
    }

    /**
     * Exports the CFG to a DOT file for visualization using Graphviz.
     *
     * @param filePath The path to the DOT file to be created.
     * @throws IOException If an I/O error occurs writing to or creating the file.
     */
    public void exportCFGToDot(String filePath) throws IOException {
        StringBuilder dot = new StringBuilder();
        dot.append("digraph CFG {\n");
        for (BasicBlock block : basicBlocks) {
            dot.append("  Block").append(block.getId()).append(" [label=\"Block ").append(block.getId()).append("\\n")
                    .append("Start: ").append(block.getStartOffset()).append("\\nEnd: ").append(block.getEndOffset()).append("\"];\n");
        }
        for (Map.Entry<BasicBlock, List<BasicBlock>> entry : rawControlFlowMap.entrySet()) {
            BasicBlock from = entry.getKey();
            for (BasicBlock to : entry.getValue()) {
                dot.append("  Block").append(from.getId()).append(" -> Block").append(to.getId()).append(";\n");
            }
        }
        dot.append("}\n");

        Files.write(Paths.get(filePath), dot.toString().getBytes());
    }
}
