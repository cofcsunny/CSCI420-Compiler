package edu.citadel.assembler.ast;

import java.io.IOException;
import java.util.List;

import edu.citadel.assembler.Symbol;
import edu.citadel.assembler.Token;
import edu.citadel.cvm.Opcode;

/**
 * This class implements the abstract syntax tree for the assembly
 * language instruction PUTINT.
 */
public class InstructionPUTINT extends InstructionNoArgs
  {
    public InstructionPUTINT(List<Token> labels, Token opcode)
      {
        super(labels, opcode);
      }

    @Override
    public void assertOpcode()
      {
        assertOpcode(Symbol.PUTINT);
      }

    @Override
    public void emit() throws IOException
      {
        emit(Opcode.PUTINT);
      }
  }
