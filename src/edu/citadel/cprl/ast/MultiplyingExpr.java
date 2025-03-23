package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;
import edu.citadel.common.ConstraintException;
import edu.citadel.common.InternalCompilerException;

import edu.citadel.cprl.Token;
import edu.citadel.cprl.Type;

/**
 * The abstract syntax tree node for a multiplying expression. A multiplying
 * expression is a binary expression where the operator is a multiplying
 * operator such a "*", "/", "mod", "<<", etc. A simple example would be "5*x".
 */
public class MultiplyingExpr extends BinaryExpr {
    /**
     * Construct a multiplying expression with the operator and the two operands.
     */
    public MultiplyingExpr(Expression leftOperand, Token operator, Expression rightOperand) {
        super(leftOperand, operator, rightOperand);
        setType(Type.Integer);
        assert operator.symbol().isMultiplyingOperator();
    }

    @Override
    public void checkConstraints() {
        try {
            leftOperand().checkConstraints();
            rightOperand().checkConstraints();

            if (leftOperand().type() != Type.Integer) {
                var errorMsg = "Left operand should have type Integer.";
                throw error(leftOperand().position(), errorMsg);
            }

            if (rightOperand().type() != Type.Integer) {
                var errorMsg = "Right operand should have type Integer.";
                throw error(rightOperand().position(), errorMsg);
            }
        } catch (ConstraintException ex) {
            errorHandler().reportError(ex);
        }
    }

    @Override
    public void emit() throws CodeGenException {
        // ...
    }
}
