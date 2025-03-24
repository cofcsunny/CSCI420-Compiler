package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;
import edu.citadel.common.ConstraintException;

import edu.citadel.cprl.Symbol;
import edu.citadel.cprl.Token;
import edu.citadel.cprl.Type;

/**
 * The abstract syntax tree node for a not expression. A not expression is a
 * unary expression of the form "not expr" (logical negation) or "~x" (bitwise
 * negation or one's complement). A simple example would be "not isEmpty()".
 */
public class NotExpr extends UnaryExpr {
    /**
     * Construct a not expression with the specified operator and operand.
     */
    public NotExpr(Token operator, Expression operand) {
        super(operator, operand);
        var symbol = operator.symbol();
        assert symbol == Symbol.notRW || symbol == Symbol.bitwiseNot;

        if (symbol == Symbol.notRW)
            setType(Type.Boolean);
        else
            setType(Type.Integer);
    }

    @Override
    public void checkConstraints() {
        try {
            operand().checkConstraints();

            Type a = operand().type();
            Symbol b = operator().symbol();
            if (b == Symbol.minus) {
                if (a != Type.Integer) {
                    throw new ConstraintException(operand().position(), "Operator '-' requires an integer operand.");
                }
            } else if (b == Symbol.bitwiseNot) {
                if (a != Type.Integer) {
                    throw new ConstraintException(operand().position(), "Operator '-' requires an integer operand.");
                }
            } else {
                throw new ConstraintException(operand().position(), "Invalid operator for negation expression.");
            }
        } catch (ConstraintException e) {
            errorHandler().reportError(e);
            ;
        }
    }

    @Override
    public void emit() throws CodeGenException {
        // ...
    }
}
