package com.tonic.analysis.similarity;

import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates fingerprints for method comparison.
 * Multiple fingerprint types allow for different similarity matching strategies.
 */
public class MethodSignature {

    private final String className;
    private final String methodName;
    private final String descriptor;

    // Fingerprints
    private byte[] bytecodeHash;
    private int[] opcodeSequence;
    private int instructionCount;
    private int maxStack;
    private int maxLocals;
    private int loopCount;
    private int branchCount;
    private int callCount;
    private int fieldAccessCount;

    public MethodSignature(String className, String methodName, String descriptor) {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
    }

    /**
     * Build the signature from a method entry.
     */
    public static MethodSignature fromMethod(MethodEntry method, String className) {
        MethodSignature sig = new MethodSignature(className, method.getName(), method.getDesc());
        sig.analyze(method);
        return sig;
    }

    private void analyze(MethodEntry method) {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return;
        }

        byte[] bytecode = code.getCode();
        if (bytecode == null || bytecode.length == 0) {
            return;
        }

        // Basic metrics
        this.maxStack = code.getMaxStack();
        this.maxLocals = code.getMaxLocals();

        // Hash the bytecode
        this.bytecodeHash = hashBytecode(bytecode);

        // Extract opcode sequence
        this.opcodeSequence = extractOpcodes(bytecode);
        this.instructionCount = opcodeSequence.length;

        // Count patterns
        analyzeOpcodes(opcodeSequence);
    }

    private byte[] hashBytecode(byte[] bytecode) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(bytecode);
        } catch (NoSuchAlgorithmException e) {
            // MD5 is always available
            return new byte[16];
        }
    }

    private int[] extractOpcodes(byte[] bytecode) {
        List<Integer> opcodes = new ArrayList<>();
        int i = 0;
        while (i < bytecode.length) {
            int opcode = bytecode[i] & 0xFF;
            opcodes.add(opcode);
            i += getInstructionLength(opcode, bytecode, i);
        }
        return opcodes.stream().mapToInt(Integer::intValue).toArray();
    }

    private void analyzeOpcodes(int[] opcodes) {
        for (int opcode : opcodes) {
            // Count branches (conditional jumps, switches)
            if (isBranchOpcode(opcode)) {
                branchCount++;
            }
            // Count backward jumps as potential loops
            if (isBackwardJumpPotential(opcode)) {
                loopCount++;
            }
            // Count method calls
            if (isInvokeOpcode(opcode)) {
                callCount++;
            }
            // Count field accesses
            if (isFieldOpcode(opcode)) {
                fieldAccessCount++;
            }
        }
    }

    private boolean isBranchOpcode(int opcode) {
        return (opcode >= 153 && opcode <= 168) || // if*, goto*
               (opcode == 170 || opcode == 171);   // tableswitch, lookupswitch
    }

    private boolean isBackwardJumpPotential(int opcode) {
        // goto, goto_w or conditional jumps could be loops
        return opcode == 167 || opcode == 200 ||
               (opcode >= 153 && opcode <= 166);
    }

    private boolean isInvokeOpcode(int opcode) {
        return opcode >= 182 && opcode <= 186; // invokevirtual through invokedynamic
    }

    private boolean isFieldOpcode(int opcode) {
        return opcode >= 178 && opcode <= 181; // getstatic through putfield
    }

    /**
     * Get the length of an instruction in bytes.
     */
    private int getInstructionLength(int opcode, byte[] code, int offset) {
        // Handle variable-length instructions
        switch (opcode) {
            case 170: // tableswitch
                int padding = (4 - ((offset + 1) % 4)) % 4;
                int low = readInt(code, offset + 1 + padding + 4);
                int high = readInt(code, offset + 1 + padding + 8);
                return 1 + padding + 12 + (high - low + 1) * 4;
            case 171: // lookupswitch
                padding = (4 - ((offset + 1) % 4)) % 4;
                int npairs = readInt(code, offset + 1 + padding + 4);
                return 1 + padding + 8 + npairs * 8;
            case 196: // wide
                int wideopcode = code[offset + 1] & 0xFF;
                return (wideopcode == 132) ? 6 : 4; // iinc vs others
            default:
                return INSTRUCTION_LENGTHS[opcode];
        }
    }

    private int readInt(byte[] code, int offset) {
        if (offset + 3 >= code.length) return 0;
        return ((code[offset] & 0xFF) << 24) |
               ((code[offset + 1] & 0xFF) << 16) |
               ((code[offset + 2] & 0xFF) << 8) |
               (code[offset + 3] & 0xFF);
    }

    // Standard instruction lengths (most are 1-3 bytes)
    private static final int[] INSTRUCTION_LENGTHS = new int[256];
    static {
        Arrays.fill(INSTRUCTION_LENGTHS, 1);
        // 2-byte instructions
        for (int op : new int[]{16, 18, 21, 22, 23, 24, 25, 54, 55, 56, 57, 58,
                                169, 188, 189, 192, 193}) {
            INSTRUCTION_LENGTHS[op] = 2;
        }
        // 3-byte instructions
        for (int op : new int[]{17, 19, 20, 132, 153, 154, 155, 156, 157, 158,
                                159, 160, 161, 162, 163, 164, 165, 166, 167, 168,
                                178, 179, 180, 181, 182, 183, 184, 187, 192, 193,
                                198, 199}) {
            INSTRUCTION_LENGTHS[op] = 3;
        }
        // 4-byte instructions
        INSTRUCTION_LENGTHS[185] = 5; // invokeinterface
        INSTRUCTION_LENGTHS[186] = 5; // invokedynamic
        INSTRUCTION_LENGTHS[197] = 4; // multianewarray
        INSTRUCTION_LENGTHS[200] = 5; // goto_w
        INSTRUCTION_LENGTHS[201] = 5; // jsr_w
    }

    // ==================== Comparison Methods ====================

    /**
     * Compare bytecode hashes for exact match.
     */
    public double compareExactBytecode(MethodSignature other) {
        if (bytecodeHash == null || other.bytecodeHash == null) {
            return 0.0;
        }
        return Arrays.equals(bytecodeHash, other.bytecodeHash) ? 1.0 : 0.0;
    }

    /**
     * Compare opcode sequences using longest common subsequence.
     */
    public double compareOpcodeSequence(MethodSignature other) {
        if (opcodeSequence == null || other.opcodeSequence == null ||
            opcodeSequence.length == 0 || other.opcodeSequence.length == 0) {
            return 0.0;
        }

        int lcs = longestCommonSubsequence(opcodeSequence, other.opcodeSequence);
        int maxLen = Math.max(opcodeSequence.length, other.opcodeSequence.length);
        return (double) lcs / maxLen;
    }

    /**
     * Compare structural metrics.
     */
    public double compareStructural(MethodSignature other) {
        double score = 0.0;
        int comparisons = 0;

        // Compare instruction count (allow 20% variance)
        if (instructionCount > 0 && other.instructionCount > 0) {
            double ratio = (double) Math.min(instructionCount, other.instructionCount) /
                          Math.max(instructionCount, other.instructionCount);
            score += ratio;
            comparisons++;
        }

        // Compare branch count
        if (branchCount > 0 || other.branchCount > 0) {
            int maxBranch = Math.max(branchCount, other.branchCount);
            int minBranch = Math.min(branchCount, other.branchCount);
            score += (maxBranch == 0) ? 1.0 : (double) minBranch / maxBranch;
            comparisons++;
        }

        // Compare call count
        if (callCount > 0 || other.callCount > 0) {
            int maxCall = Math.max(callCount, other.callCount);
            int minCall = Math.min(callCount, other.callCount);
            score += (maxCall == 0) ? 1.0 : (double) minCall / maxCall;
            comparisons++;
        }

        return comparisons > 0 ? score / comparisons : 0.0;
    }

    /**
     * LCS algorithm for opcode comparison.
     */
    private int longestCommonSubsequence(int[] a, int[] b) {
        int m = a.length;
        int n = b.length;

        // Optimize for memory when arrays are large
        if (m > 1000 || n > 1000) {
            return approximateLCS(a, b);
        }

        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a[i - 1] == b[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp[m][n];
    }

    private int approximateLCS(int[] a, int[] b) {
        // Simple approximation for large arrays using sampling
        int sampleSize = 100;
        int matches = 0;

        for (int i = 0; i < sampleSize && i < a.length; i++) {
            int idx = (i * a.length) / sampleSize;
            for (int j = 0; j < b.length; j++) {
                if (a[idx] == b[j]) {
                    matches++;
                    break;
                }
            }
        }

        return (matches * Math.min(a.length, b.length)) / sampleSize;
    }

    // ==================== Getters ====================

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public byte[] getBytecodeHash() {
        return bytecodeHash;
    }

    public int[] getOpcodeSequence() {
        return opcodeSequence;
    }

    public int getInstructionCount() {
        return instructionCount;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public int getMaxLocals() {
        return maxLocals;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public int getBranchCount() {
        return branchCount;
    }

    public int getCallCount() {
        return callCount;
    }

    public int getFieldAccessCount() {
        return fieldAccessCount;
    }

    /**
     * Get a display string for this method.
     */
    public String getDisplayName() {
        String simpleClass = className.contains("/") ?
            className.substring(className.lastIndexOf('/') + 1) : className;
        return simpleClass + "." + methodName;
    }

    /**
     * Get the full method reference.
     */
    public String getFullReference() {
        return className + "." + methodName + descriptor;
    }

    @Override
    public String toString() {
        return getDisplayName() + " [" + instructionCount + " instrs]";
    }
}
