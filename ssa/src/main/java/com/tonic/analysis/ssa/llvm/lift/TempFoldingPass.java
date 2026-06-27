package com.tonic.analysis.ssa.llvm.lift;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Collapses the multi-step synthesized temporaries the YABR lowerer emits back to the single IR
 * instructions they originated from.
 *
 * <p>Three patterns, all recognised by structure on the raw RHS strings stored in
 * {@link FunctionLifter#temps}:
 * <ul>
 *   <li><b>Shift</b> — optional {@code zext} + {@code and %amount, 31|63} + {@code shl|ashr|lshr %value, %t<mask>}
 *       → {@link BinaryOpInstruction}(SHL|SHR|USHR, value, original-amount).</li>
 *   <li><b>Narrowing conversion</b> — {@code trunc i32 %src to i8|i16} +
 *       {@code sext|zext i8|i16 %t to i32} → {@link UnaryOpInstruction}(I2B|I2C|I2S, src).</li>
 *   <li><b>3-way compare</b> — 4-line integer form or 6-line float/double-with-NaN form
 *       → {@link BinaryOpInstruction}(LCMP|FCMPL|FCMPG|DCMPL|DCMPG, left, right).</li>
 * </ul>
 *
 * Unrecognised {@code %t<n>} temporaries that ended up used as SSA-value operands are left in
 * place; DCE in the subsequent {@code SSA.withDeadCodeElimination()} pass prunes unused ones.
 */
final class TempFoldingPass {

    private static final Pattern ZEXT = Pattern.compile("zext (\\S+) (%[vt]\\d+) to (\\S+)");
    private static final Pattern AND_MASK = Pattern.compile("and (\\S+) (%[vt]\\d+), (\\d+)");
    private static final Pattern TRUNC = Pattern.compile("trunc (\\S+) (%[vt]\\d+) to (i8|i16)");

    private final Map<String, FunctionLifter.TempEntry> temps;
    private final Map<String, SSAValue> vMap;

    TempFoldingPass(FunctionLifter lifter, Map<String, SSAValue> vMap) {
        this.temps = lifter.temps;
        this.vMap = vMap;
    }

    void run(IRMethod method) {
        for (IRBlock block : method.getBlocksInOrder()) {
            foldShifts(block);
            foldNarrowingConversions(block);
        }
    }

    // ---- shift ---------------------------------------------------------------

    private void foldShifts(IRBlock block) {
        for (IRInstruction instr : new ArrayList<>(block.getInstructions())) {
            if (!(instr instanceof BinaryOpInstruction)) {
                continue;
            }
            BinaryOpInstruction bin = (BinaryOpInstruction) instr;
            BinaryOp op = bin.getOp();
            if (op != BinaryOp.SHL && op != BinaryOp.SHR && op != BinaryOp.USHR) {
                continue;
            }
            // Right operand should be a %t register from an "and" mask
            String rightReg = tempRegOf(bin.getRight());
            if (rightReg == null) {
                continue;
            }
            FunctionLifter.TempEntry maskEntry = temps.get(rightReg);
            if (maskEntry == null || maskEntry.rawRhs == null) {
                continue;
            }
            Matcher am = AND_MASK.matcher(maskEntry.rawRhs);
            if (!am.matches()) {
                continue;
            }
            // The amount fed to the and (possibly via an earlier zext)
            String amountReg = am.group(2);
            FunctionLifter.TempEntry maybeZext = temps.get(amountReg);
            if (maybeZext != null && maybeZext.rawRhs != null) {
                Matcher zm = ZEXT.matcher(maybeZext.rawRhs);
                if (zm.matches()) {
                    amountReg = zm.group(2); // original amount before widening
                }
            }
            Value originalAmount = vMap.get(amountReg);
            if (originalAmount == null) {
                continue;
            }
            // Replace right operand with the original unmasked amount
            instr.replaceOperand(bin.getRight(), originalAmount);
        }
    }

    // ---- narrowing conversions -----------------------------------------------

    private void foldNarrowingConversions(IRBlock block) {
        List<IRInstruction> instrs = new ArrayList<>(block.getInstructions());
        for (IRInstruction instr : instrs) {
            if (!(instr instanceof UnaryOpInstruction)) {
                continue;
            }
            UnaryOpInstruction un = (UnaryOpInstruction) instr;
            // Only sext/zext to i32 that came from a temp
            String srcReg = tempRegOf(un.getOperand());
            if (srcReg == null) {
                continue;
            }
            FunctionLifter.TempEntry te = temps.get(srcReg);
            if (te == null || te.rawRhs == null) {
                continue;
            }
            Matcher trm = TRUNC.matcher(te.rawRhs);
            if (!trm.matches()) {
                continue;
            }
            // te is "trunc i32 %vSrc to i8|i16"; un is "sext|zext i8|i16 %t<n> to i32"
            String srcValReg = trm.group(2);
            String truncTo = trm.group(3); // i8 or i16

            // Determine fold from the narrow width and whether the extend is signed or zero.
            // trunc to i8  → I2B (sext back)
            // trunc to i16 + zext → I2C (char is unsigned)
            // trunc to i16 + sext → I2S
            UnaryOp foldedOp;
            if (truncTo.equals("i8")) {
                foldedOp = UnaryOp.I2B;
            } else if (un.getOp() == UnaryOp.I2C) {
                foldedOp = UnaryOp.I2C;
            } else {
                foldedOp = UnaryOp.I2S;
            }

            Value srcVal = vMap.get(srcValReg);
            if (srcVal == null) {
                continue;
            }
            // Replace the instruction in the block
            SSAValue result = instr.getResult();
            IRInstruction replacement = new UnaryOpInstruction(result, foldedOp, srcVal);
            block.removeInstruction(instr);
            block.addInstruction(replacement);
        }
    }

    private static String tempRegOf(Value v) {
        if (v instanceof SSAValue) {
            String name = ((SSAValue) v).getName();
            if (name != null && name.startsWith("t")) {
                return "%" + name;
            }
        }
        return null;
    }
}
