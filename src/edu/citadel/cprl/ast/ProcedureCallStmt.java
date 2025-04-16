package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;
import edu.citadel.common.ConstraintException;

import edu.citadel.cprl.Token;
import edu.citadel.cprl.ArrayType;
import edu.citadel.cprl.StringType;

import java.util.List;

/**
 * The abstract syntax tree node for a procedure call statement.
 */
public class ProcedureCallStmt extends Statement {
    private Token procId;
    private List<Expression> actualParams;

    // declaration of the procedure being called
    private ProcedureDecl procDecl; // nonstructural reference

    /*
     * Construct a procedure call statement with the procedure name
     * (an identifier token) and the list of actual parameters being
     * passed as part of the call.
     */
    public ProcedureCallStmt(Token procId, List<Expression> actualParams) {
        this.procId = procId;
        this.actualParams = actualParams;

    }

    @Override
    public void checkConstraints() {
        this.procDecl = (ProcedureDecl) idTable().get(procId.text());
        try {
            // get the declaration for this function call from the identifier table
            var decl = idTable().get(procId.text());

            if (decl == null) {
                var errorMsg = "Procedure \"" + procId + "\" has not been declared.";
                throw error(procId.position(), errorMsg);
            } else if (!(decl instanceof ProcedureDecl)) {
                var errorMsg = "Identifier \"" + procId + "\" was not declared as a procedure.";
                throw error(procId.position(), errorMsg);
            } else
                procDecl = (ProcedureDecl) decl;

            var paramDecls = procDecl.parameterDecls();

            // check that numbers of parameters match
            if (actualParams.size() != paramDecls.size()) {
                var errorMsg = "Incorrect number of actual parameters.";
                throw error(procId.position(), errorMsg);
            }

            // check constraints for each actual parameter
            for (Expression expr : actualParams)
                expr.checkConstraints();

            for (int i = 0; i < actualParams.size(); ++i) {
                var expr = actualParams.get(i);
                var paramDecl = paramDecls.get(i);

                // check that parameter types match
                if (!matchTypes(paramDecl.type(), expr)) {
                    var errorMsg = "Parameter type mismatch.";
                    throw error(expr.position(), errorMsg);
                }
                // check that variable expressions are passed for var parameters
                // (recall that arrays are passed as var parameters; checked in FunctionDecl)
                if (paramDecl.isVarParam() && paramDecl.type() instanceof ArrayType) {
                    if (expr instanceof VariableExpr variableExpr) {
                        // replace a variable expression by a variable
                        expr = new Variable(variableExpr);
                        actualParams.set(i, expr);
                    } else {
                        var errorMsg = "Expression for an array (var) parameter must be a variable.";
                        throw error(expr.position(), errorMsg);
                    }
                }
                if (paramDecl.isVarParam()) {
                    if (expr instanceof VariableExpr variableExpr) {
                        expr = new Variable(variableExpr);
                        actualParams.set(i, expr);
                    } else if (!(expr.type() instanceof ArrayType)) {
                        var errorMsg = "Expression for a var parameter must be a variable.";
                        throw error(expr.position(), errorMsg);
                    }
                }
            }
        } catch (ConstraintException e) {
            errorHandler().reportError(e);
        }
    }

    /**
     * Add "synthetic" padding parameter for string literals if needed.
     */
    private void addPadding() {
        this.procDecl = (ProcedureDecl) idTable().get(procId.text());
        var paramDecls = procDecl.parameterDecls();

        // can't use a for-loop here since the number of actual parameters
        // can change with the insertion of padding for string types
        int i = 0;
        int j = 0;

        while (i < paramDecls.size()) {
            var paramDecl = paramDecls.get(i);
            var expr = actualParams.get(j);

            if (paramDecl.type() instanceof StringType stringType
                    && expr instanceof ConstValue constValue
                    && stringType.size() > constValue.size()) {
                var padding = new Padding(stringType.size() - constValue.size());
                actualParams.add(++j, padding);
            }

            ++i;
            ++j;
        }
    }

    @Override
    public void emit() throws CodeGenException {
        addPadding();

        // emit code for actual parameters
        for (Expression expr : actualParams)
            expr.emit();

        emit("CALL " + procDecl.subprogramLabel());
    }
}
