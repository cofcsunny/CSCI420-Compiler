package edu.citadel.assembler.optimize;

import java.util.List;

import edu.citadel.assembler.Symbol;
import edu.citadel.assembler.Token;
import edu.citadel.assembler.ast.Instruction;
import edu.citadel.assembler.ast.InstructionOneArg;
import edu.citadel.assembler.ast.InstructionRET0;
import edu.citadel.assembler.ast.InstructionRET4;

/**
 * Replaces RET 0 with RET0 and RET 4 with RET4.
 * IMPORTANT: This optimization should not be performed until after
 * the optimization for dead code elimination.
 */
public class ReturnSpecialConstants implements Optimization
  {
    @Override
    public void optimize(List<Instruction> instructions, int instNum)
      {
        var instruction = instructions.get(instNum);
        var symbol = instruction.opcode().symbol();

        if (symbol == Symbol.RET)
          {
            var inst   = (InstructionOneArg) instruction;
            var arg    = inst.arg().text();
            var labels = inst.labels();

            if (arg.equals("0"))
              {
                // replace RET 0 with RET0
                var retToken = new Token(Symbol.RET0);
                var retInst  = new InstructionRET0(labels, retToken);
                instructions.set(instNum, retInst);
              }
            else if (arg.equals("4"))
              {
                // replace RET 4 with RET4
                var retToken = new Token(Symbol.RET4);
                var retInst  = new InstructionRET4(labels, retToken);
                instructions.set(instNum, retInst);
              }
          }
      }
  }
