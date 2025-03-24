package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;
import edu.citadel.common.ConstraintException;

import edu.citadel.cprl.Type;

/**
 * The abstract syntax tree node for a loop statement.
 */
public class LoopStmt extends Statement {
    private Expression whileExpr;
    private Statement statement = EmptyStatement.instance();

    // labels used during code generation
    protected String L1 = newLabel(); // label for start of loop
    protected String L2 = newLabel(); // label for end of loop

    /**
     * Constructs a loop statement with a null while expression.
     */
    public LoopStmt() {
        whileExpr = null;
    }

    /**
     * Constructs a loop statement with the specified while expression.
     */
    public LoopStmt(Expression whileExpr) {
        this.whileExpr = whileExpr;
    }

    /**
     * Returns the statement for the body of this loop statement.
     */
    public Statement statement() {
        return statement;
    }

    /**
     * Set the statement for the body of this loop statement.
     */
    public void setStatement(Statement statement) {
        this.statement = statement;
    }

    /**
     * Returns the label for the end of the loop statement.
     */
    public String exitLabel() {
        return L2;
    }

    @Override
    public void checkConstraints() {
        try {
            if (whileExpr != null) {
                whileExpr.checkConstraints();

                if (!whileExpr.type().equals(Type.Boolean)) {
                    var errorMsg = "Loop condition must be of type Boolean.";
                    var errorPos = whileExpr.position();
                    throw new ConstraintException(errorPos, errorMsg);
                }
            }
            if (statement != null) {
                statement.checkConstraints();
            }
        } catch (ConstraintException e) {
            errorHandler().reportError(e);
        }
    }

    @Override
    public void emit() throws CodeGenException {
        // ...
    }
}
