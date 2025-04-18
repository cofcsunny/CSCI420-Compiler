package edu.citadel.cprl.ast;

import edu.citadel.common.CodeGenException;

/**
 * The abstract syntax tree node for a variable expression. A variable
 * expression
 * is similar to a variable except that it generates different code. For
 * example,
 * consider an assignment of the form "x := y". The identifier "x" represents
 * a variable, and the identifier "y" represents a variable expression. Code
 * generation for "x" would leave its address on the top of the stack, while
 * code generation for "y" would leave its value on the top of the stack.
 */
public class VariableExpr extends Variable {
    /**
     * Construct a variable expression from a variable.
     */
    public VariableExpr(Variable var) {
        super(var.decl(), var.position(), var.selectorExprs());
    }

    // inherited checkConstraints() is sufficient

    @Override
    public void emit() throws CodeGenException {
        super.emit(); // leaves address of variable on top of stack
        emitLoadInst(type()); // replaces address by value at that address
    }
}
