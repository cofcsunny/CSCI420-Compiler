package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;
import edu.citadel.common.ConstraintException;

import edu.citadel.cprl.ArrayType;
import edu.citadel.cprl.Token;

import java.util.List;

/**
 * The abstract syntax tree node for a function declaration.
 */
public class FunctionDecl extends SubprogramDecl {
    /**
     * Construct a function declaration with its name (an identifier).
     */
    public FunctionDecl(Token funcId) {
        super(funcId);
    }

    /**
     * Returns the relative address of the function return value.
     */
    public int relAddr() {
        return -type().size() - paramLength();
    }

    @Override
    public void checkConstraints() {
        super.checkConstraints();
        try {
            if (type() == null) {
                var errorMsg = "Function must have a return type.";
                throw error(this.position(), errorMsg);
            }
            if (!hasReturnStmt(statements())) {
                var errorMsg = "A function must have at least one return statement.";
                throw error(this.position(), errorMsg);
            }
            var paramDecls = this.parameterDecls();
            for (int i = 0; i < paramDecls.size(); ++i) {
                var paramDecl = this.parameterDecls().get(i);
                if (paramDecl.isVarParam()) {
                    if (paramDecl.type() instanceof ArrayType) {
                        // ...
                    } else {
                        var errorMsg = "A function cannot have var parameters.";
                        throw error(this.position(), errorMsg);
                    }

                }
            }
        } catch (ConstraintException e) {
            errorHandler().reportError(e);
        }

    }

    /**
     * Returns true if the specified list of statements contains at least one
     * return statement.
     *
     * @param statements The list of statements to check for a return statement.
     *                   If any of the statements in the list contains nested
     *                   statements (e.g., an if statement, a compound statement,
     *                   or a loop statement), then any nested statements are
     *                   also checked for a return statement.
     */
    private boolean hasReturnStmt(List<Statement> statements) {
        // Check that we have at least one return statement.
        for (Statement statement : statements) {
            if (hasReturnStmt(statement))
                return true;
        }

        return false;
    }

    /**
     * Returns true if the specified statement is a return statement or contains
     * at least one return statement.
     *
     * @param statement The statement to check for a return statement. If the
     *                  statement contains nested statements (e.g., an if statement,
     *                  a compound statement, or a loop statement), then the nested
     *                  statements are also checked for a return statement.
     */
    private Boolean hasReturnStmt(Statement statement) {
        if (statement instanceof ReturnStmt) {
            return true;
        } else if (statement instanceof IfStmt ifStmt) {
            if (hasReturnStmt(ifStmt.thenStmt())) {
                return true;
            }
        } else if (statement instanceof LoopStmt loopStmt) {
            if (hasReturnStmt(loopStmt.statement())) {
                return true;
            }
        } else if (statement instanceof CompoundStmt compoundStmt) {
            if (hasReturnStmt(compoundStmt.statements())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void emit() throws CodeGenException {
        setRelativeAddresses();
        emitLabel(subprogramLabel());
        if (varLength() > 0) {
            emit("PROC " + varLength());
        }
        for (InitialDecl decl : initialDecls()) {
            decl.emit();
        }
        for (Statement statement : statements()) {
            statement.emit();
        }
        emit("RET " + paramLength());
    }
}
