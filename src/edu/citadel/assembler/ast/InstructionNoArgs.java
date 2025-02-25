package edu.citadel.assembler.ast;

import java.util.List;

import edu.citadel.assembler.Token;
import edu.citadel.common.ConstraintException;

/**
 * This class serves as a base class for the abstract syntax
 * tree for an assembly language instruction with no arguments.
 */
public abstract class InstructionNoArgs extends Instruction
  {
    /**
     * Construct a no-argument instruction with a list of labels and an opcode.
     */
    public InstructionNoArgs(List<Token> labels, Token opcode)
      {
        super(labels, opcode);
      }

    @Override
    public void checkConstraints()
      {
        try
          {
            assertOpcode();
            checkLabels();
          }
        catch (ConstraintException e)
          {
            errorHandler().reportError(e);
          }
      }

    @Override
    protected int argSize()
      {
        return 0;
      }
  }
