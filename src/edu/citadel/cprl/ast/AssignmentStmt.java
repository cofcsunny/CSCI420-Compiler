package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;
import edu.citadel.common.ConstraintException;
import edu.citadel.common.InternalCompilerException;
import edu.citadel.common.Position;

/**
 * The abstract syntax tree node for an assignment statement.
 */
public class AssignmentStmt extends Statement {
    private Variable variable;
    private Expression expr;

    // position of the assignment symbol (for error reporting)
    private Position assignPosition;

    /**
     * Construct an assignment statement with a variable, an expression,
     * and the position of the assignment symbol.
     *
     * @param variable       The variable on the left side of the assignment symbol.
     * @param expr           The expression on the right side of the assignment
     *                       symbol.
     * @param assignPosition The position of the assignment symbol (for error
     *                       reporting).
     */
    public AssignmentStmt(Variable variable, Expression expr, Position assignPosition) {
        this.variable = variable;
        this.expr = expr;
        this.assignPosition = assignPosition;
    }

    @Override
    public void checkConstraints() {
        try {
            variable.checkConstraints();
            expr.checkConstraints();
            if (!matchTypes(variable.type(), expr)) {
                var errorMsg = "Type mismatch for assignment statement.";
                throw error(assignPosition, errorMsg);
            }
        } catch (ConstraintException ex) {
            errorHandler().reportError(ex);
        }
    }

    @Override
    public void emit() throws CodeGenException {
        variable.emit();
        expr.emit();
        emitStoreInst(expr.type());
    }
}
