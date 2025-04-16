package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;
import edu.citadel.common.ConstraintException;
import edu.citadel.common.Position;

/**
 * The abstract syntax tree node for a return statement.
 */
public class ReturnStmt extends Statement {
    private SubprogramDecl subprogramDecl; // nonstructural reference
    private Expression returnExpr; // may be null

    // position of the return token (needed for error reporting)
    private Position returnPosition;

    /**
     * Construct a return statement with a reference to the enclosing subprogram
     * and the expression for the value being returned, which may be null.
     */
    public ReturnStmt(SubprogramDecl subprogramDecl, Expression returnExpr, Position returnPosition) {
        this.returnExpr = returnExpr;
        this.subprogramDecl = subprogramDecl;
        this.returnPosition = returnPosition;
    }

    @Override
    public void checkConstraints() {
    	 if (returnExpr != null) {
    	        returnExpr.checkConstraints();

    	        if (subprogramDecl instanceof FunctionDecl functionDecl) {
    	            if (functionDecl.type() == null) {
    	                error(returnPosition, "Cannot return a value from a function with void return type.");
    	            } else if (!returnExpr.type().equals(functionDecl.type())) {
    	                error(returnPosition, "Type of return expression does not match function return type.");
    	            }
    	        }
    	    } else {
    	        if (subprogramDecl instanceof FunctionDecl functionDecl
    	                && !functionDecl.type().equals("none")) {
    	            error(returnPosition, "Must return a value from a function with non-void return type.");
    	        }
    	    }
    }

    @Override
    public void emit() throws CodeGenException {
        if (returnExpr != null) {
            // Emit code for the return expression
            emit("LDLADDR " + ((FunctionDecl)subprogramDecl).relAddr());
            returnExpr.emit();
            emit("STOREW");
        }

        // Emit RET instruction with parameter length adjustment
        emit("RET " + subprogramDecl.paramLength());
    }
}
