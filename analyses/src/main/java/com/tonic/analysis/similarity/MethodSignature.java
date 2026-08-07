package com.tonic.analysis.similarity;

import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.util.ClassNameUtil;
import com.tonic.util.InstructionLength;

import static com.tonic.util.Opcode.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A method's comparison fingerprints: bytecode hash, opcode sequence, and structural counters.
 */
public class MethodSignature
{

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

    /**
     * Creates an empty signature identifying a method; fingerprints are filled by fromMethod.
     * @param className the owning class name
     * @param methodName the method name
     * @param descriptor the method descriptor
     */
    public MethodSignature(String className, String methodName, String descriptor)
    {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
    }

    /**
     * Builds a signature from a method entry, analyzing its bytecode when present.
     * @param method the method to fingerprint
     * @param className the owning class name
     * @return the populated signature
     */
    public static MethodSignature fromMethod(MethodEntry method, String className)
    {
        MethodSignature sig = new MethodSignature(className, method.getName(), method.getDesc());
        sig.analyze(method);
        return sig;
    }

    private void analyze(MethodEntry method)
    {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null)
        {
            return;
        }

        byte[] bytecode = code.getCode();
        if (bytecode == null || bytecode.length == 0)
        {
            return;
        }

        this.maxStack = code.getMaxStack();
        this.maxLocals = code.getMaxLocals();

        this.bytecodeHash = hashBytecode(bytecode);

        this.opcodeSequence = extractOpcodes(bytecode);
        this.instructionCount = opcodeSequence.length;

        analyzeOpcodes(opcodeSequence);
    }

    private byte[] hashBytecode(byte[] bytecode)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(bytecode);
        }
        catch (NoSuchAlgorithmException e)
        {
            // MD5 is always available
            return new byte[16];
        }
    }

    private int[] extractOpcodes(byte[] bytecode)
    {
        List<Integer> opcodes = new ArrayList<>();
        int i = 0;
        while (i < bytecode.length)
        {
            opcodes.add(bytecode[i] & 0xFF);
            int length = InstructionLength.at(bytecode, i);
            i += length > 0 ? length : 1;
        }
        return opcodes.stream().mapToInt(Integer::intValue).toArray();
    }

    private void analyzeOpcodes(int[] opcodes)
    {
        for (int opcode : opcodes)
        {
            // Count branches (conditional jumps, switches)
            if (isBranchOpcode(opcode))
            {
                branchCount++;
            }
            // Count backward jumps as potential loops
            if (isBackwardJumpPotential(opcode))
            {
                loopCount++;
            }
            // Count method calls
            if (isInvokeOpcode(opcode))
            {
                callCount++;
            }
            // Count field accesses
            if (isFieldOpcode(opcode))
            {
                fieldAccessCount++;
            }
        }
    }

    private boolean isBranchOpcode(int opcode)
    {
        return (opcode >= IFEQ.getCode() && opcode <= JSR.getCode())
                || opcode == TABLESWITCH.getCode() || opcode == LOOKUPSWITCH.getCode();
    }

    private boolean isBackwardJumpPotential(int opcode)
    {
        return opcode == GOTO.getCode() || opcode == GOTO_W.getCode()
                || (opcode >= IFEQ.getCode() && opcode <= IF_ACMPNE.getCode());
    }

    private boolean isInvokeOpcode(int opcode)
    {
        return opcode >= INVOKEVIRTUAL.getCode() && opcode <= INVOKEDYNAMIC.getCode();
    }

    private boolean isFieldOpcode(int opcode)
    {
        return opcode >= GETSTATIC.getCode() && opcode <= PUTFIELD.getCode();
    }

    // Comparison Methods

    /**
     * Compares bytecode hashes for an exact match.
     * @param other the signature to compare against
     * @return 1.0 on equal hashes, otherwise 0.0
     */
    public double compareExactBytecode(MethodSignature other)
    {
        if (bytecodeHash == null || other.bytecodeHash == null)
        {
            return 0.0;
        }
        return Arrays.equals(bytecodeHash, other.bytecodeHash) ? 1.0 : 0.0;
    }

    /**
     * Compares opcode sequences using longest common subsequence length.
     * @param other the signature to compare against
     * @return the LCS length over the longer sequence, in [0, 1]
     */
    public double compareOpcodeSequence(MethodSignature other)
    {
        if (opcodeSequence == null || other.opcodeSequence == null ||
            opcodeSequence.length == 0 || other.opcodeSequence.length == 0)
            {
            return 0.0;
        }

        int lcs = longestCommonSubsequence(opcodeSequence, other.opcodeSequence);
        int maxLen = Math.max(opcodeSequence.length, other.opcodeSequence.length);
        return (double) lcs / maxLen;
    }

    /**
     * Compares structural metrics: instruction, branch, and call count ratios.
     * @param other the signature to compare against
     * @return the average ratio in [0, 1], 0 if no metrics are comparable
     */
    public double compareStructural(MethodSignature other)
    {
        double score = 0.0;
        int comparisons = 0;

        if (instructionCount > 0 && other.instructionCount > 0)
        {
            double ratio = (double) Math.min(instructionCount, other.instructionCount) /
                          Math.max(instructionCount, other.instructionCount);
            score += ratio;
            comparisons++;
        }

        if (branchCount > 0 || other.branchCount > 0)
        {
            int maxBranch = Math.max(branchCount, other.branchCount);
            int minBranch = Math.min(branchCount, other.branchCount);
            score += (double) minBranch / maxBranch;
            comparisons++;
        }

        if (callCount > 0 || other.callCount > 0)
        {
            int maxCall = Math.max(callCount, other.callCount);
            int minCall = Math.min(callCount, other.callCount);
            score += (double) minCall / maxCall;
            comparisons++;
        }

        return comparisons > 0 ? score / comparisons : 0.0;
    }

    /**
     * LCS algorithm for opcode comparison.
     */
    private int longestCommonSubsequence(int[] a, int[] b)
    {
        int m = a.length;
        int n = b.length;

        // Optimize for memory when arrays are large
        if (m > 1000 || n > 1000)
        {
            return approximateLCS(a, b);
        }

        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++)
        {
            for (int j = 1; j <= n; j++)
            {
                if (a[i - 1] == b[j - 1])
                {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                }
                else
                {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp[m][n];
    }

    private int approximateLCS(int[] a, int[] b)
    {
        // Simple approximation for large arrays using sampling
        int sampleSize = 100;
        int matches = 0;

        for (int i = 0; i < sampleSize && i < a.length; i++)
        {
            int idx = (i * a.length) / sampleSize;
            for (int k : b)
            {
                if (a[idx] == k)
                {
                    matches++;
                    break;
                }
            }
        }

        return (matches * Math.min(a.length, b.length)) / sampleSize;
    }

    // Getters

    /**
     * @return the class name
     */
    public String getClassName()
    {
        return className;
    }

    /**
     * @return the method name
     */
    public String getMethodName()
    {
        return methodName;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return the bytecode hash
     */
    public byte[] getBytecodeHash()
    {
        return bytecodeHash;
    }

    /**
     * @return the opcode sequence
     */
    public int[] getOpcodeSequence()
    {
        return opcodeSequence;
    }

    /**
     * @return the instruction count
     */
    public int getInstructionCount()
    {
        return instructionCount;
    }

    /**
     * @return the max stack
     */
    public int getMaxStack()
    {
        return maxStack;
    }

    /**
     * @return the max locals
     */
    public int getMaxLocals()
    {
        return maxLocals;
    }

    /**
     * @return the loop count
     */
    public int getLoopCount()
    {
        return loopCount;
    }

    /**
     * @return the branch count
     */
    public int getBranchCount()
    {
        return branchCount;
    }

    /**
     * @return the call count
     */
    public int getCallCount()
    {
        return callCount;
    }

    /**
     * @return the field access count
     */
    public int getFieldAccessCount()
    {
        return fieldAccessCount;
    }

    /**
     * @return a short display string of simple class name and method name
     */
    public String getDisplayName()
    {
        String simpleClass = ClassNameUtil.getSimpleNameWithInnerClasses(className);
        return simpleClass + "." + methodName;
    }

    /**
     * @return the full reference in class.name+descriptor form
     */
    public String getFullReference()
    {
        return className + "." + methodName + descriptor;
    }

    @Override
    public String toString()
    {
        return getDisplayName() + " [" + instructionCount + " instrs]";
    }
}
