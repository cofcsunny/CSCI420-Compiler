package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;
import edu.citadel.common.ConstraintException;

import edu.citadel.cprl.Symbol;
import edu.citadel.cprl.Token;
import edu.citadel.cprl.Type;

/**
 * The abstract syntax tree node for a negation expression. A negation
 * expression is a unary expression where the operator is either "-" or "~".
 * A simple example would be "-x".
 */
public class NegationExpr extends UnaryExpr {
    /**
     * Construct a negation expression with the specified operator and operand.
     */
    public NegationExpr(Token operator, Expression operand) {
        super(operator, operand);
        setType(Type.Integer);
        assert operator.symbol() == Symbol.minus || operator.symbol() == Symbol.bitwiseNot;
    }

    @Override
    public void checkConstraints() {
        try {
            if (operand().type() != Type.Integer) {
                var errorMsg = "Operand should have type Integer.";
                var errorPos = operand().position();
                throw error(errorPos, errorMsg);
            }
        } catch (ConstraintException e) {
            errorHandler().reportError(e);
        }
    }

    @Override
    public void emit() throws CodeGenException {
        operand().emit();
    	
    	if (operator().symbol() == Symbol.minus)
            emit("NOT");
        else if (operator().symbol() == Symbol.bitwiseNot)
            emit("BITWISE_NOT");
        else 
            throw new CodeGenException(operator().position(), "Unexpected operator in NegationExpr.");
    }
}
