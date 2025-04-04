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

            Symbol b = operator().symbol();
            if (operator().symbol() == Symbol.notRW) {
                if (operand().type() != Type.Boolean) {
                    var errorMsg = "Expression following \"not\" operator should have type Boolean.";
                    var errorPos = operand().position();
                    throw new ConstraintException(errorPos, errorMsg);
                }
            } else if (operator().symbol() == Symbol.bitwiseNot) {
                if (operand().type() != Type.Integer) {
                    var errorMsg = "Operator '~' requires an integer operand.";
                    var errorPos = operand().position();
                    throw new ConstraintException(errorPos, errorMsg);
                }
            } else {
                var errorMsg = "Invalid operator for negation expression.";
                var errorPos = operand().position();
                throw new ConstraintException(errorPos, errorMsg);
            }
        } catch (ConstraintException e) {
            errorHandler().reportError(e);
        }
    }

    @Override
    public void emit() throws CodeGenException {
        operand().emit();
    	
    	if(operator().symbol() ==  Symbol.notRW)
    		emit("NOT");
    	else if(operator().symbol() == Symbol.bitwiseNot)
    		emit("BITNOT");
    	else
    		throw new CodeGenException(operand().position(), "Unexpected operator in NotExpr.");
    }
}
