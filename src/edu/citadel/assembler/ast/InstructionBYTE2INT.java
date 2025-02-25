package edu.citadel.assembler.ast;

import java.io.IOException;
import java.util.List;

import edu.citadel.assembler.Symbol;
import edu.citadel.assembler.Token;
import edu.citadel.cvm.Opcode;

/**
 * This class implements the abstract syntax tree for the assembly
 * language instruction BYTE2INT.
 */
public class InstructionBYTE2INT extends InstructionNoArgs
  {
    public InstructionBYTE2INT(List<Token> labels, Token opcode)
      {
        super(labels, opcode);
      }

    @Override
    public void assertOpcode()
      {
        assertOpcode(Symbol.BYTE2INT);
      }

    @Override
    public void emit() throws IOException
      {
        emit(Opcode.BYTE2INT);
      }
  }
