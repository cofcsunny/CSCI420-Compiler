package edu.citadel.cprl.ast;

import java.util.List;

import edu.citadel.cprl.RecordType;
import edu.citadel.cprl.Token;

/**
 * The abstract syntax tree node for a record type declaration.
 */
public class RecordTypeDecl extends InitialDecl
  {
    private List<FieldDecl> fieldDecls;

    /**
     * Construct a record type declaration with its type name (identifier)
     * and list of field declarations.
     *
     * @param typeId     The token containing the identifier for the record type name.
     * @param fieldDecls The list of field declarations for the record.
     */
    public RecordTypeDecl(Token typeId, List<FieldDecl> fieldDecls)
      {
        super(typeId, new RecordType(typeId.text(), fieldDecls));
        this.fieldDecls = fieldDecls;
      }

    @Override
    public void checkConstraints()
      {
// ...
      }
  }
