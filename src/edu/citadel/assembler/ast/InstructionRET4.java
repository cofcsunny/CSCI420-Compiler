package edu.citadel.assembler.ast;

import java.io.IOException;
import java.util.List;

import edu.citadel.assembler.Symbol;
import edu.citadel.assembler.Token;
import edu.citadel.cvm.Opcode;

/**
 * This class implements the abstract syntax tree for the assembly
 * language instruction RET4.
 */
public class InstructionRET4 extends InstructionNoArgs
  {
    public InstructionRET4(List<Token> labels, Token opcode)
      {
        super(labels, opcode);
      }

    @Override
    public void assertOpcode()
      {
        assertOpcode(Symbol.RET4);
      }

    @Override
    public void emit() throws IOException
      {
        emit(Opcode.RET4);
      }
  }
