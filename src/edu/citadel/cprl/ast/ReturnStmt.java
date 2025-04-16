package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;
import edu.citadel.common.ConstraintException;
import edu.citadel.common.Position;
import edu.citadel.cprl.Type;

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
        try {
            if (returnExpr != null) {
                returnExpr.checkConstraints();
                if (subprogramDecl instanceof FunctionDecl) {
                    if (subprogramDecl.type().equals(Type.none)) {
                        var errorMsg = "Cannot return a value from a function with void return type.";
                        throw error(returnPosition, errorMsg);
                    } else if (!returnExpr.type().equals(subprogramDecl.type())) {
                        var errorMsg = "Return expression type does not match function return type.";
                        throw error(returnExpr.position(), errorMsg);
                    }
                }
                if (subprogramDecl instanceof ProcedureDecl) {
                    var errorMsg = "Return expression allowed only within functions.";
                    throw error(returnExpr.position(), errorMsg);
                }
            } else if (subprogramDecl instanceof FunctionDecl) {
                if (!subprogramDecl.type().equals(Type.none)) {
                    var errorMsg = "A return statement nested within a function must return a value.";
                    throw error(returnPosition, errorMsg);
                }
            }
        } catch (ConstraintException e) {
            errorHandler().reportError(e);
        }
    }

    @Override
    public void emit() throws CodeGenException {
        if (returnExpr != null) {
            // Emit code for the return expression
            if (subprogramDecl instanceof FunctionDecl) {
                emit("LDLADDR " + ((FunctionDecl) subprogramDecl).relAddr());
                returnExpr.emit();
                emitStoreInst(subprogramDecl.type());
            }
        }

        // Emit RET instruction with parameter length adjustment
        emit("RET " + subprogramDecl.paramLength());
    }
}
