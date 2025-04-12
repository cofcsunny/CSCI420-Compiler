package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;

import edu.citadel.cprl.Token;

/**
 * The abstract syntax tree node for a procedure declaration.
 */
public class ProcedureDecl extends SubprogramDecl {
    /**
     * Construct a procedure declaration with its name (an identifier).
     */
    public ProcedureDecl(Token procId) {
        super(procId);
    }

    // inherited checkConstraints() is sufficient

    @Override
    public void emit() throws CodeGenException {
        setRelativeAddresses();
        emitLabel(subprogramLabel());

        // Emit PROC instruction if there are local variables
        if (varLength() > 0)
            emit("PROC " + varLength());

        // Emit code for initial declarations
        for (InitialDecl decl : initialDecls())
            decl.emit();

        // Emit code for statements
        for (Statement statement : statements())
            statement.emit();

        // Emit RET instruction with parameter length adjustment
        emit("RET " + paramLength());
    }
}
