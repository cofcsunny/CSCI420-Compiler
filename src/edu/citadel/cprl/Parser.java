package edu.citadel.cprl;

import edu.citadel.common.Position;
import edu.citadel.common.ErrorHandler;
import edu.citadel.common.ParserException;
import edu.citadel.common.InternalCompilerException;

import edu.citadel.cprl.ast.*;

import java.io.IOException;
import java.util.*;

public final class Parser {
  private Scanner scanner;
  private IdTable idTable;
  private ErrorHandler errorHandler;
  private LoopContext loopContext = new LoopContext();
  private SubprogramContext subprogramContext = new SubprogramContext();

  private final Set<Symbol> stmtFollowers = EnumSet.of(
      Symbol.identifier, Symbol.ifRW, Symbol.elseRW,
      Symbol.whileRW, Symbol.loopRW, Symbol.forRW,
      Symbol.readRW, Symbol.writeRW, Symbol.writelnRW,
      Symbol.exitRW, Symbol.leftBrace, Symbol.rightBrace,
      Symbol.returnRW);

  private final Set<Symbol> subprogDeclFollowers = EnumSet.of(Symbol.EOF,
      Symbol.procRW, Symbol.funRW);

  private final Set<Symbol> factorFollowers = EnumSet.of(
      Symbol.semicolon, Symbol.loopRW, Symbol.thenRW,
      Symbol.rightParen, Symbol.andRW, Symbol.orRW,
      Symbol.equals, Symbol.notEqual, Symbol.lessThan,
      Symbol.lessOrEqual, Symbol.greaterThan, Symbol.greaterOrEqual,
      Symbol.plus, Symbol.minus, Symbol.times,
      Symbol.divide, Symbol.modRW, Symbol.rightBracket,
      Symbol.comma, Symbol.bitwiseAnd, Symbol.bitwiseOr,
      Symbol.bitwiseXor, Symbol.leftShift, Symbol.rightShift,
      Symbol.dotdot);

  private Set<Symbol> initialDeclFollowers() {
    // An initial declaration can always be followed by another
    // initial declaration, regardless of the scope level.
    var followers = EnumSet.of(Symbol.constRW, Symbol.varRW,
        Symbol.typeRW);

    if (idTable.scopeLevel() == ScopeLevel.GLOBAL)
      followers.addAll(EnumSet.of(Symbol.procRW, Symbol.funRW));
    else {
      followers.addAll(stmtFollowers);
      followers.remove(Symbol.elseRW);
    }

    return followers;
  }

  public Parser(Scanner scanner, IdTable idTable, ErrorHandler errorHandler) {
    this.scanner = scanner;
    this.idTable = idTable;
    this.errorHandler = errorHandler;
  }

  /**
   * GIVEN
   * 
   * program = initialDecls subprogramDecls.
   * 
   * @return The parsed program. Returns a program with an empty list
   *         of initial declarations and an empty list of subprogram
   *         declarations if parsing fails.
   */
  public Program parseProgram() throws IOException {
    try {
      var initialDecls = parseInitialDecls();
      var subprogramDecls = parseSubprogramDecls();
      if (scanner.symbol() != Symbol.EOF) {
        var errorMsg = "Expecting \"proc\" or \"fun\" but found \""
            + scanner.token() + "\" instead.";
        throw error(errorMsg);
      }

      return new Program(initialDecls, subprogramDecls);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(EnumSet.of(Symbol.EOF));

      return new Program();
    }
  }

  /**
   * GIVEN
   * 
   * initialDecls = { initialDecl }.
   * 
   * @return The list of initial declarations.
   */
  private List<InitialDecl> parseInitialDecls() throws IOException {
    var initialDecls = new ArrayList<InitialDecl>(10);

    while (scanner.symbol().isInitialDeclStarter())
      initialDecls.add(parseInitialDecl());

    return initialDecls;
  }

  /**
   * initialDecl = constDecl | varDecl | typeDecl.
   * 
   * @return The parsed initial declaration. Returns an
   *         empty initial declaration if parsing fails.
   */
  private InitialDecl parseInitialDecl() throws IOException {
    try {
      var symbol = scanner.symbol();
      if (symbol == Symbol.constRW) {
        return parseConstDecl();
      } else if (symbol == Symbol.varRW) {
        return parseVarDecl();
      } else if (symbol == Symbol.typeRW) {
        return parseTypeDecl();
      } else {
        throw error("Invalid initial declaration.");
      }
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(initialDeclFollowers());

      return EmptyInitialDecl.instance();
    }
  }

  /**
   * constDecl = "const" constId ":=" [ "-" ] literal ";".
   *
   * @return The parsed constant declaration. Returns an
   *         empty initial declaration if parsing fails.
   */
  private InitialDecl parseConstDecl() throws IOException {
    try {
      match(Symbol.constRW);
      var constId = scanner.token();
      match(Symbol.identifier);
      match(Symbol.assign);
      if (scanner.symbol() == Symbol.minus) {
        matchCurrentSymbol();
      }
      var literal = parseLiteral();
      match(Symbol.semicolon);
      var constDecl = new ConstDecl(constId,
          Type.typeOf(literal), literal);
      idTable.add(constDecl);

      return constDecl;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(initialDeclFollowers());

      return EmptyInitialDecl.instance();
    }
  }

  /**
   * GIVEN
   * 
   * literal = intLiteral | charLiteral | stringLiteral | "true" | "false" .
   * 
   * @return The parsed literal token. Returns a default token if parsing fails.
   */
  private Token parseLiteral() throws IOException {
    try {
      if (scanner.symbol().isLiteral()) {
        var literal = scanner.token();
        matchCurrentSymbol();
        return literal;
      } else
        throw error("Invalid literal expression.");
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(factorFollowers);

      return new Token();
    }
  }

  /**
   * GIVEN
   * 
   * varDecl = "var" identifiers ":"
   * ( typeName | arrayTypeConstr | stringTypeConstr)
   * [ ":=" initializer] ";".
   * 
   * @return The parsed variable declaration. Returns an
   *         empty initial declaration if parsing fails.
   */
  private InitialDecl parseVarDecl() throws IOException {
    try {
      match(Symbol.varRW);
      var identifiers = parseIdentifiers();
      match(Symbol.colon);

      Type varType;
      var symbol = scanner.symbol();
      if (symbol.isPredefinedType() || symbol == Symbol.identifier)
        varType = parseTypeName();
      else if (symbol == Symbol.arrayRW)
        varType = parseArrayTypeConstr();
      else if (symbol == Symbol.stringRW)
        varType = parseStringTypeConstr();
      else {
        var errorMsg = "Expecting a type name, reserved word \"array\", "
            + "or reserved word \"string\".";
        throw error(errorMsg);
      }

      Initializer initializer = EmptyInitializer.instance();
      if (scanner.symbol() == Symbol.assign) {
        matchCurrentSymbol();
        initializer = parseInitializer();
      }

      match(Symbol.semicolon);

      var varDecl = new VarDecl(identifiers, varType, initializer,
          idTable.scopeLevel());

      for (SingleVarDecl decl : varDecl.singleVarDecls())
        idTable.add(decl);

      return varDecl;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(initialDeclFollowers());

      return EmptyInitialDecl.instance();
    }
  }

  /**
   * GIVEN
   * 
   * identifiers = identifier { "," identifier } .
   * 
   * @return The list of identifier tokens. Returns an empty list if parsing
   *         fails.
   */
  private List<Token> parseIdentifiers() throws IOException {
    try {
      var identifiers = new ArrayList<Token>(10);
      var idToken = scanner.token();
      match(Symbol.identifier);
      identifiers.add(idToken);

      while (scanner.symbol() == Symbol.comma) {
        matchCurrentSymbol();
        idToken = scanner.token();
        match(Symbol.identifier);
        identifiers.add(idToken);
      }

      return identifiers;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(EnumSet.of(Symbol.colon, Symbol.greaterThan));

      return Collections.emptyList();
    }
  }

  /**
   * GIVEN
   * 
   * initializer = constValue | compositeInitializer .
   * 
   * @return The parsed initializer. Returns an empty
   *         initializer if parsing fails.
   */
  private Initializer parseInitializer() throws IOException {
    try {
      var symbol = scanner.symbol();
      if (symbol == Symbol.identifier || symbol.isLiteral() || symbol == Symbol.minus) {
        var expr = parseConstValue();
        return expr instanceof ConstValue constValue ? constValue
            : EmptyInitializer.instance();
      } else if (symbol == Symbol.leftBrace)
        return parseCompositeInitializer();
      else {
        var errorMsg = "Expecting literal, identifier, or left brace.";
        throw error(errorMsg);
      }
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(initialDeclFollowers());

      return EmptyInitializer.instance();
    }
  }

  /**
   * compositeInitializer = "{" initializer { "," initializer } "}" .
   * 
   * @return The parsed composite initializer. Returns an empty composite
   *         initializer if parsing fails.
   */
  private CompositeInitializer parseCompositeInitializer() throws IOException {
    try {
      var position = scanner.position();
      var compositeInitializer = new CompositeInitializer(position);
      match(Symbol.leftBrace);
      compositeInitializer.add(parseInitializer());
      while (scanner.symbol() == Symbol.comma) {
        match(Symbol.comma);
        compositeInitializer.add(parseInitializer());
      }
      match(Symbol.rightBrace);

      return compositeInitializer;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(EnumSet.of(Symbol.comma, Symbol.rightBrace, Symbol.semicolon));

      return new CompositeInitializer(scanner.position());
    }
  }

  /**
   * GIVEN
   * 
   * typeDecl = arrayTypeDecl | recordTypeDecl | stringTypeDecl.
   * 
   * @return The parsed type declaration. Returns an
   *         empty initial declaration parsing fails.
   */
  private InitialDecl parseTypeDecl() throws IOException {
    assert scanner.symbol() == Symbol.typeRW;

    try {
      return switch (scanner.lookahead(4).symbol()) {
        case Symbol.arrayRW -> parseArrayTypeDecl();
        case recordRW -> parseRecordTypeDecl();
        case stringRW -> parseStringTypeDecl();
        default -> {
          Position errorPos = scanner.lookahead(4).position();
          throw error(errorPos, "Invalid type declaration.");
        }
      };
    } catch (ParserException e) {
      errorHandler.reportError(e);
      matchCurrentSymbol(); // force scanner past "type"
      recover(initialDeclFollowers());

      return EmptyInitialDecl.instance();
    }
  }

  /**
   * arrayTypeDecl = "type" typeId "=" "array" "[" intConstValue "]"
   * "of" typeName ";" .
   * 
   * @return The parsed array type declaration. Returns an
   *         empty initial declaration if parsing fails.
   */
  private InitialDecl parseArrayTypeDecl() throws IOException {
    try {
      match(Symbol.typeRW);
      var typeID = scanner.token();
      match(Symbol.identifier);
      match(Symbol.equals);
      match(Symbol.arrayRW);
      match(Symbol.leftBracket);
      var constValue = parseIntConstValue();
      match(Symbol.rightBracket);
      match(Symbol.ofRW);
      var elemType = parseTypeName();
      match(Symbol.semicolon);
      var arrayTypeDecl = new ArrayTypeDecl(typeID, elemType, constValue);
      idTable.add(arrayTypeDecl);

      return arrayTypeDecl;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(initialDeclFollowers());

      return EmptyInitialDecl.instance();
    }
  }

  /**
   * GIVEN
   * 
   * arrayTypeConstr = "array" "[" intConstValue "]" "of" typeName.
   * 
   * @return The array type defined by this array type constructor.
   *         Returns an empty array type if parsing fails.
   */
  private ArrayType parseArrayTypeConstr() throws IOException {
    try {
      match(Symbol.arrayRW);
      match(Symbol.leftBracket);
      var numElements = parseIntConstValue().intValue();
      match(Symbol.rightBracket);
      match(Symbol.ofRW);
      var elemType = parseTypeName();
      var typeName = "array[" + numElements + "] of " + elemType;

      return new ArrayType(typeName, numElements, elemType);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(EnumSet.of(Symbol.semicolon));

      return new ArrayType("_", 0, Type.UNKNOWN);
    }
  }

  /**
   * GIVEN
   * 
   * recordTypeDecl = "type" typeId "=" "record" "{" fieldDecls "}" ";".
   * 
   * @return The parsed record type declaration. Returns
   *         an empty initial declaration if parsing fails.
   */
  private InitialDecl parseRecordTypeDecl() throws IOException {
    try {
      match(Symbol.typeRW);
      var typeId = scanner.token();
      match(Symbol.identifier);
      match(Symbol.equals);
      match(Symbol.recordRW);
      match(Symbol.leftBrace);

      List<FieldDecl> fieldDecls;
      try {
        idTable.openScope(ScopeLevel.RECORD);
        fieldDecls = parseFieldDecls();
      } finally {
        idTable.closeScope();
      }

      match(Symbol.rightBrace);
      match(Symbol.semicolon);

      var typeDecl = new RecordTypeDecl(typeId, fieldDecls);
      idTable.add(typeDecl);

      return typeDecl;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(initialDeclFollowers());

      return EmptyInitialDecl.instance();
    }
  }

  /**
   * fieldDecls = { fieldDecl }.
   * 
   * @return A list of field declarations.
   */
  private List<FieldDecl> parseFieldDecls() throws IOException {
    ArrayList<FieldDecl> fieldDecls = new ArrayList<FieldDecl>(4);
    while (scanner.symbol() == Symbol.identifier) {
      fieldDecls.add(parseFieldDecl());
    }

    return fieldDecls;
  }

  /**
   * fieldDecl = fieldId ":" typeName ";".
   * 
   * @return The parsed field declaration. Returns null if parsing fails.
   */
  private FieldDecl parseFieldDecl() throws IOException {
    try {
      var fieldId = scanner.token();
      match(Symbol.identifier);
      match(Symbol.colon);
      var fieldType = parseTypeName();
      match(Symbol.semicolon);
      var fieldDecl = new FieldDecl(fieldId, fieldType);
      idTable.add(fieldDecl);

      return fieldDecl;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(EnumSet.of(Symbol.identifier, Symbol.rightBrace));

      return null;
    }
  }

  /**
   * 
   * stringTypeDecl = "type" typeId "=" "string" "[" intConstValue "]" ";".
   * 
   * @return The parsed string type declaration. Returns an
   *         empty initial declaration if parsing fails.
   */
  private InitialDecl parseStringTypeDecl() throws IOException {
    try {
      match(Symbol.typeRW);
      var typeId = scanner.token();
      match(Symbol.identifier);
      match(Symbol.equals);
      match(Symbol.stringRW);
      match(Symbol.leftBracket);
      var capacity = parseIntConstValue();
      match(Symbol.rightBracket);
      match(Symbol.semicolon);
      var stringTypeDecl = new StringTypeDecl(typeId, capacity);
      idTable.add(stringTypeDecl);

      return stringTypeDecl;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(initialDeclFollowers());

      return EmptyInitialDecl.instance();
    }
  }

  /**
   * GIVEN
   * 
   * stringTypeConstr = "string" "[" intConstValue "]" .
   * 
   * @return The string type defined by this string type constructor.
   *         Returns an empty string type if parsing fails.
   */
  private StringType parseStringTypeConstr() throws IOException {
    try {
      match(Symbol.stringRW);
      match(Symbol.leftBracket);
      var capacity = parseIntConstValue().intValue();
      match(Symbol.rightBracket);
      return new StringType(capacity);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(EnumSet.of(Symbol.semicolon));

      return new StringType(0);
    }
  }

  /**
   * GIVEN
   * 
   * typeName = "Integer" | "Boolean" | "Char" | typeId.
   * 
   * @return The parsed named type. Returns Type.UNKNOWN if parsing fails.
   */
  private Type parseTypeName() throws IOException {
    try {
      switch (scanner.symbol()) {
        case IntegerRW -> {
          matchCurrentSymbol();
          return Type.Integer;
        }
        case BooleanRW -> {
          matchCurrentSymbol();
          return Type.Boolean;
        }
        case CharRW -> {
          matchCurrentSymbol();
          return Type.Char;
        }
        case identifier -> {
          var typeId = scanner.token();
          matchCurrentSymbol();
          var decl = idTable.get(typeId.text());

          if (decl != null) {
            if (decl instanceof ArrayTypeDecl
                || decl instanceof RecordTypeDecl
                || decl instanceof StringTypeDecl) {
              return decl.type();
            } else {
              var errorMsg = "Identifier \"" + typeId + "\" is not a valid type name.";
              throw error(typeId.position(), errorMsg);
            }
          } else {
            var errorMsg = "Identifier \"" + typeId + "\" has not been declared.";
            throw error(typeId.position(), errorMsg);
          }
        }
        default -> throw error("Invalid type name.");
      }

    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(EnumSet.of(Symbol.semicolon, Symbol.comma,
          Symbol.rightParen, Symbol.leftBrace));

      return Type.UNKNOWN;
    }
  }

  /**
   * subprogramDecls = { subprogramDecl }.
   * 
   * @return The list of subprogram declarations.
   * @throws IOException
   */
  private List<SubprogramDecl> parseSubprogramDecls() throws IOException {
    ArrayList<SubprogramDecl> subprogramDecls = new ArrayList<SubprogramDecl>(4);
    while (scanner.symbol().isSubprogramDeclStarter()) {
      subprogramDecls.add(parseSubprogramDecl());
    }

    return subprogramDecls;
  }

  /**
   * subprogramDecl = procedureDecl | functionDecl .
   * 
   * @return The parsed subprogram declaration. Returns an
   *         empty subprogram declaration if parsing fails.
   */
  private SubprogramDecl parseSubprogramDecl() throws IOException {
    try {
      return switch (scanner.symbol()) {
        case Symbol.procRW -> parseProcedureDecl();
        case funRW -> parseFunctionDecl();
        default ->
          throw error("Invalid subprogram declaration");
      };
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(subprogDeclFollowers);

      return EmptySubprogramDecl.instance();
    }

  }

  /**
   * GIVEN
   * 
   * procedureDecl = "proc" procId "(" [ parameterDecls ] ")"
   * "{" initialDecls statements "}".
   * 
   * @return The parsed procedure declaration. Returns an
   *         empty subprogram declaration if parsing fails.
   */
  private SubprogramDecl parseProcedureDecl() throws IOException {
    try {
      match(Symbol.procRW);
      var procId = scanner.token();
      match(Symbol.identifier);

      var procDecl = new ProcedureDecl(procId);
      idTable.add(procDecl);
      match(Symbol.leftParen);

      try {
        idTable.openScope(ScopeLevel.LOCAL);

        if (scanner.symbol().isParameterDeclStarter())
          procDecl.setParameterDecls(parseParameterDecls());

        match(Symbol.rightParen);
        match(Symbol.leftBrace);
        procDecl.setInitialDecls(parseInitialDecls());

        subprogramContext.beginSubprogramDecl(procDecl);
        procDecl.setStatements(parseStatements());
        subprogramContext.endSubprogramDecl();
      } finally {
        idTable.closeScope();
      }

      match(Symbol.rightBrace);

      return procDecl;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(subprogDeclFollowers);

      return EmptySubprogramDecl.instance();
    }
  }

  /**
   * functionDecl = "fun" funcId "(" [ parameterDecls ] ")" ":" typeName
   * "{" initialDecls statements "}".
   * 
   * This is wrong somehow, model it @see {@link #parseProcedureDecl()}
   * 
   * @return The parsed function declaration. Returns an
   *         empty subprogram declaration if parsing fails.
   */
  private SubprogramDecl parseFunctionDecl() throws IOException {
    try {
      match(Symbol.funRW);
      var funcId = scanner.token();
      match(Symbol.identifier);

      var funcDecl = new FunctionDecl(funcId);
      idTable.add(funcDecl);
      match(Symbol.leftParen);

      try {
        idTable.openScope(ScopeLevel.LOCAL);

        if (scanner.symbol().isParameterDeclStarter()) {
          funcDecl.setParameterDecls(parseParameterDecls());
        }
        match(Symbol.rightParen);
        match(Symbol.colon);
        funcDecl.setType(parseTypeName());
        match(Symbol.leftBrace);
        funcDecl.setInitialDecls(parseInitialDecls());
        subprogramContext.beginSubprogramDecl(funcDecl);
        funcDecl.setStatements(parseStatements());
        subprogramContext.endSubprogramDecl();

      } finally {
        idTable.closeScope();
      }
      match(Symbol.rightBrace);

      return funcDecl;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(subprogDeclFollowers);

      return EmptySubprogramDecl.instance();
    }
  }

  /**
   * parameterDecls = parameterDecl { "," parameterDecl }.
   * 
   * @return A list of parameter declarations.
   */
  private List<ParameterDecl> parseParameterDecls() throws IOException {
    ArrayList<ParameterDecl> parameterDecls = new ArrayList<ParameterDecl>(4);
    parameterDecls.add(parseParameterDecl());
    while (scanner.symbol() == Symbol.comma) {
      matchCurrentSymbol();
      parameterDecls.add(parseParameterDecl());
    }

    return parameterDecls;
  }

  /**
   * parameterDecl = [ "var" ] paramId ":" typeName.
   * 
   * @return The parsed parameter declaration. Returns null if parsing fails.
   */
  private ParameterDecl parseParameterDecl() throws IOException {
    try {
      boolean isVarParam = false;
      if (scanner.symbol() == Symbol.varRW) {
        matchCurrentSymbol();
        isVarParam = true;
      }
      var paramId = scanner.token();
      match(Symbol.identifier);
      match(Symbol.colon);
      var paramType = parseTypeName();
      var parameterDecl = new ParameterDecl(paramId, paramType, isVarParam);
      idTable.add(parameterDecl);

      return parameterDecl;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(EnumSet.of(Symbol.comma, Symbol.rightParen));

      return null;
    }
  }

  /**
   * statements = { statement }.
   * 
   * @return A list of statements.
   */
  private List<Statement> parseStatements() throws IOException {
    ArrayList<Statement> statements = new ArrayList<Statement>(4);
    while (scanner.symbol().isStmtStarter()) {
      statements.add(parseStatement());
    }

    return statements;
  }

  /**
   * statement = assignmentStmt | procedureCallStmt
   * | compoundStmt | ifStmt
   * | loopStmt | forLoopStmt | exitStmt | readStmt
   * | writeStmt | writelnStmt | returnStmt .
   * PARTIALLY GIVEN
   * 
   * @return The parsed statement. Returns an empty statement if parsing fails.
   */
  private Statement parseStatement() throws IOException {
    try {
      if (scanner.symbol() == Symbol.identifier) {
        // Handle identifiers based on how they are declared,
        // or use the lookahead symbol if not declared.
        var idStr = scanner.text();
        var decl = idTable.get(idStr);

        if (decl != null) {
          if (decl instanceof VariableDecl)
            return parseAssignmentStmt();
          else if (decl instanceof ProcedureDecl)
            return parseProcedureCallStmt();
          else
            throw error("Identifier \"" + idStr + "\" cannot start a statement.");
        } else {
          if (scanner.lookahead(2).symbol() == Symbol.leftParen) {
            return parseProcedureCallStmt();
          } else {
            throw error("Identifier \"" + idStr
                + "\" has not been declared");
          }
        }
      } else {
        return switch (scanner.symbol()) {
          case Symbol.identifier -> parseAssignmentStmt();
          case Symbol.leftBrace -> parseCompoundStmt();
          case Symbol.ifRW -> parseIfStmt();
          case Symbol.whileRW -> parseLoopStmt();
          case Symbol.loopRW -> parseLoopStmt();
          case Symbol.forRW -> parseForLoopStmt();
          case Symbol.exitRW -> parseExitStmt();
          case Symbol.readRW -> parseReadStmt();
          case Symbol.writeRW -> parseWriteStmt();
          case Symbol.writelnRW -> parseWritelnStmt();
          case Symbol.returnRW -> parseReturnStmt();
          default -> throw internalError(scanner.token()
              + " cannot start a statement.");
        };
      }
    } catch (

    ParserException e) {
      errorHandler.reportError(e);

      // Error recovery here is complicated for identifiers since they can both
      // start a statement and appear elsewhere in the statement. (Consider,
      // for example, an assignment statement or a procedure call statement.)
      // Since the most common error is to declare or reference an identifier
      // incorrectly, we will assume that this is the case and advance to the
      // end of the current statement before performing error recovery.
      scanner.advanceTo(EnumSet.of(Symbol.semicolon, Symbol.rightBrace));
      recover(stmtFollowers);

      return EmptyStatement.instance();
    }
  }

  /**
   * 
   * //perhaps needs to check idTable, not sure yet, @TODO
   * 
   * assignmentStmt = variable ":=" expression ";".
   * 
   * @return The parsed assignment statement. Returns
   *         an empty statement if parsing fails.
   */
  private Statement parseAssignmentStmt() throws IOException {
    try {
      var identifier = scanner.text();
      Variable variable = null;
      if (idTable.get(identifier) != null) {
        variable = parseVariable();
      } else {
        var errorMsg = "Identifier \"" + identifier + "\" has not been declared.";
        throw error(errorMsg);
      }
      var assignPosition = scanner.position();
      try {
        match(Symbol.assign);
      } catch (ParserException e) {
        if (scanner.symbol() == Symbol.equals) {
          errorHandler.reportError(e);
          matchCurrentSymbol();
        } else {
          throw e;
        }
      }
      var expr = parseExpression();
      match(Symbol.semicolon);
      var assignmentStmt = new AssignmentStmt(variable, expr, assignPosition);

      return assignmentStmt;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);

      return EmptyStatement.instance();
    }
  }

  /**
   * compoundStmt = "{" statements "}" .
   * 
   * @return The parsed compound statement. Returns an empty statement if parsing
   *         fails.
   */
  private Statement parseCompoundStmt() throws IOException {
    try {
      match(Symbol.leftBrace);
      var statements = parseStatements();
      match(Symbol.rightBrace);

      return new CompoundStmt(statements);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);

      return EmptyStatement.instance();
    }
  }

  /**
   * ifStmt = "if" booleanExpr "then" statement [ "else" statement ].
   * 
   * @return The parsed if statement. Returns an empty statement if parsing fails.
   */
  private Statement parseIfStmt() throws IOException {
    try {
      match(Symbol.ifRW);
      var booleanExpr = parseExpression();
      match(Symbol.thenRW);
      var thenStmt = parseStatement();
      Statement elseStmt = null;
      if (scanner.symbol() == Symbol.elseRW) {
        matchCurrentSymbol();
        elseStmt = parseStatement();
      }

      return new IfStmt(booleanExpr, thenStmt, elseStmt);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);

      return EmptyStatement.instance();
    }
  }

  /**
   * loopStmt = [ "while" booleanExpr ] "loop" statement.
   * 
   * @return The parsed loop statement. Returns an empty statement if parsing
   *         fails.
   */
  private Statement parseLoopStmt() throws IOException {
    try {
      LoopStmt loopStmt;
      if (scanner.symbol() == Symbol.whileRW) {
        matchCurrentSymbol();
        loopStmt = new LoopStmt(parseExpression());
      } else {
        loopStmt = new LoopStmt();
      }
      match(Symbol.loopRW);
      loopContext.beginLoop(loopStmt);
      loopStmt.setStatement(parseStatement());
      loopContext.endLoop();

      return loopStmt;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);

      return EmptyStatement.instance();
    }
  }

  /**
   * GIVEN
   * 
   * forLoopStmt = "for" varId "in" intExpr ".." intExpr "loop" statement.
   * 
   * @return The parsed for-loop statement. Returns an empty statement if parsing
   *         fails.
   */
  private Statement parseForLoopStmt() throws IOException {
    try {
      // create a new scope for the loop variable
      idTable.openScope(ScopeLevel.LOCAL);

      match(Symbol.forRW);
      var loopId = scanner.token();
      match(Symbol.identifier);
      match(Symbol.inRW);
      var rangeStart = parseExpression();
      match(Symbol.dotdot);
      var rangeEnd = parseExpression();

      // Create an implicit variable declaration for the loop variable and add
      // it to the list of initial declarations for the subprogram declaration.
      var varDecl = new VarDecl(List.of(loopId), Type.Integer,
          EmptyInitializer.instance(), ScopeLevel.LOCAL);
      var subprogDecl = subprogramContext.subprogramDecl();
      assert subprogDecl != null;
      subprogDecl.initialDecls().add(varDecl);

      // Add the corresponding single variable declaration to the identifier tables.
      var loopSvDecl = varDecl.singleVarDecls().getLast();
      idTable.add(loopSvDecl);

      // Create loop variable to add to AST class ForLoopStmt
      var loopVariable = new Variable(loopSvDecl, loopId.position(),
          Collections.emptyList());
      match(Symbol.loopRW);
      var forLoopStmt = new ForLoopStmt(loopVariable, rangeStart, rangeEnd);
      loopContext.beginLoop(forLoopStmt);
      forLoopStmt.setStatement(parseStatement());
      loopContext.endLoop();

      return forLoopStmt;
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);
      return EmptyStatement.instance();
    } finally {
      idTable.closeScope();
    }
  }

  /**
   * PARTIALLY BOOK
   * 
   * exitStmt = "exit" [ "when" booleanExpr ] ";".
   * 
   * @return The parsed exit statement. Returns an empty statement if parsing
   *         fails.
   */
  private Statement parseExitStmt() throws IOException {
    try {
      match(Symbol.exitRW);
      Expression whenExpr = null;
      if (scanner.symbol() == Symbol.whenRW) {
        matchCurrentSymbol();
        whenExpr = parseExpression();
      }
      var loopStmt = loopContext.loopStmt();
      if (loopStmt == null) {
        throw error("Exit statement is not nested within a loop");
      }
      match(Symbol.semicolon);

      return new ExitStmt(whenExpr, loopStmt);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);

      return EmptyStatement.instance();
    }
  }

  /**
   * readStmt = "read" variable ";" .
   * 
   * @return The parsed read statement. Returns an empty statement if parsing
   *         fails.
   */
  private Statement parseReadStmt() throws IOException {
    try {
      match(Symbol.readRW);
      var variable = parseVariable();
      match(Symbol.semicolon);

      return new ReadStmt(variable);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);

      return EmptyStatement.instance();
    }
  }

  /**
   * writeStmt = "write" expressions ";".
   * 
   * @return The parsed write statement. Returns an empty statement if parsing
   *         fails.
   */
  private Statement parseWriteStmt() throws IOException {
    try {
      match(Symbol.writeRW);
      var expressions = parseExpressions();
      match(Symbol.semicolon);

      return new OutputStmt(expressions, false);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);

      return EmptyStatement.instance();
    }
  }

  /**
   * expressions = expression [ "," expression ] .
   * 
   * @return A list of expressions.
   * @throws IOException
   */
  private List<Expression> parseExpressions() throws IOException {
    ArrayList<Expression> expressions = new ArrayList<Expression>(4);
    expressions.add(parseExpression());
    while (scanner.symbol() == Symbol.comma) {
      matchCurrentSymbol();
      expressions.add(parseExpression());
    }

    return expressions;
  }

  /**
   * GIVEN
   * 
   * writelnStmt = "writeln" [ expressions ] ";".
   * 
   * @return The parsed writeln statement. Returns an empty statement if parsing
   *         fails.
   */
  private Statement parseWritelnStmt() throws IOException {
    try {
      match(Symbol.writelnRW);

      List<Expression> expressions;
      if (scanner.symbol().isExprStarter())
        expressions = parseExpressions();
      else
        expressions = Collections.emptyList();

      match(Symbol.semicolon);

      return new OutputStmt(expressions, true);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);
      return EmptyStatement.instance();
    }
  }

  /**
   * procedureCallStmt = procId "(" [ actualParameters ] ")" ";".
   * actualParameters = expressions.
   * 
   * @return The parsed procedure call statement. Returns
   *         an empty statement if parsing fails.
   */
  private Statement parseProcedureCallStmt() throws IOException {
    try {
      var procId = scanner.token();
      match(Symbol.identifier);
      match(Symbol.leftParen);
      var actualParams = parseExpressions();
      match(Symbol.rightParen);
      match(Symbol.semicolon);

      return new ProcedureCallStmt(procId, actualParams);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);

      return EmptyStatement.instance();
    }
  }

  /**
   * returnStmt = "return" [ expression ] ";".
   * 
   * @return The parsed return statement. Returns an empty statement if parsing
   *         fails.
   */
  private Statement parseReturnStmt() throws IOException {
    try {
      var returnPosition = scanner.position();
      match(Symbol.returnRW);
      Expression returnExpr = null;
      if (scanner.symbol() != Symbol.semicolon) {
        returnExpr = parseExpression();
      }
      match(Symbol.semicolon);
      var subprogramDecl = subprogramContext.subprogramDecl();

      return new ReturnStmt(subprogramDecl, returnExpr, returnPosition);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(stmtFollowers);

      return EmptyStatement.instance();
    }
  }

  /**
   * GIVEN
   * 
   * Parse the following grammar rules:<br>
   * <code>variable = ( varId | paramId ) { indexExpr | fieldExpr } .<br>
   *       indexExpr = "[" expression "]" .<br>
   *       fieldExpr = "." fieldId .</code>
   * <br>
   * This helper method provides common logic for methods parseVariable() and
   * parseVariableExpr(). The method does not handle any ParserExceptions but
   * throws them back to the calling method where they can be handled
   * appropriately.
   *
   * @return The parsed variable.
   * @throws ParserException if parsing fails.
   * @see #parseVariable()
   * @see #parseVariableExpr()
   */
  private Variable parseVariableCommon() throws IOException, ParserException {
    var idToken = scanner.token();
    match(Symbol.identifier);
    var decl = idTable.get(idToken.text());

    if (decl == null) {
      var errorMsg = "Identifier \"" + idToken + "\" has not been declared.";
      throw error(idToken.position(), errorMsg);
    } else if (!(decl instanceof VariableDecl)) {
      var errorMsg = "Identifier \"" + idToken + "\" is not a variable.";
      throw error(idToken.position(), errorMsg);
    }

    var variableDecl = (VariableDecl) decl;

    var selectorExprs = new ArrayList<Expression>(5);

    while (scanner.symbol().isSelectorStarter()) {
      if (scanner.symbol() == Symbol.leftBracket) {
        // parse index expression
        match(Symbol.leftBracket);
        selectorExprs.add(parseExpression());
        match(Symbol.rightBracket);
      } else if (scanner.symbol() == Symbol.dot) {
        // parse field expression
        match(Symbol.dot);
        var fieldId = scanner.token();
        match(Symbol.identifier);
        selectorExprs.add(new FieldExpr(fieldId));
      }
    }

    return new Variable(variableDecl, idToken.position(), selectorExprs);
  }

  /**
   * variable = ( varId | paramId ) { indexExpr | fieldExpr }.
   * 
   * GIVEN
   * 
   * @return The parsed variable. Returns null if parsing fails.
   */
  private Variable parseVariable() throws IOException {
    try {
      return parseVariableCommon();
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(EnumSet.of(Symbol.assign, Symbol.semicolon));

      return null;
    }
  }

  /**
   * GIVEN
   * 
   * expression = relation { logicalOp relation } .
   * logicalOp = "and" | "or"
   * 
   * @return The parsed expression.
   */
  private Expression parseExpression() throws IOException {
    var expr = parseRelation();

    while (scanner.symbol().isLogicalOperator()) {
      var operator = scanner.token();
      matchCurrentSymbol();
      expr = new LogicalExpr(expr, operator, parseRelation());
    }

    return expr;
  }

  /**
   * relation = simpleExpr [ relationalOp simpleExpr ].
   * relationalOp = "=" | "!=" | "&lt;" | "&lt;=" | "&gt;" | "&gt;=".
   * 
   * @return The parsed relational expression.
   */
  private Expression parseRelation() throws IOException {
    var relation = parseSimpleExpr();
    while (scanner.symbol().isRelationalOperator()) {
      var operator = scanner.token();
      matchCurrentSymbol();
      var rightOperand = parseSimpleExpr();
      relation = new RelationalExpr(relation, operator, rightOperand);
    }
    return relation;
  }

  /**
   * //add signop to AST?
   * 
   * simpleExpr = [ signOp ] term { addingOp term }.
   * signOp = "+" | "-".
   * addingOp = "+" | "-" | "|" | "^".
   * 
   * @return The parsed simple expression.
   */
  private Expression parseSimpleExpr() throws IOException {
    if (scanner.symbol().isSignOperator()) {
      matchCurrentSymbol();
    }
    var simpleExpr = parseTerm();
    while (scanner.symbol().isAddingOperator()) {
      var operator = scanner.token();
      matchCurrentSymbol();
      var rightOperand = parseTerm();
      simpleExpr = new AddingExpr(simpleExpr, operator, rightOperand);
    }
    return simpleExpr;
  }

  /**
   * //also recursive, but multiplyingexpression
   * 
   * term = factor { multiplyingOp factor }.
   * multiplyingOp = "*" | "/" | "mod" | "&" | "<<" | ">>" .
   * 
   * @return The parsed term expression.
   */
  private Expression parseTerm() throws IOException {
    var term = parseFactor();
    while (scanner.symbol().isMultiplyingOperator()) {
      var operator = scanner.token();
      matchCurrentSymbol();
      var rightOperand = parseFactor();
      term = new MultiplyingExpr(term, operator, rightOperand);
    }
    return term;// fix return
  }

  /**
   * GIVEN
   * 
   * factor = ("not" | "~") factor | literal | constId | variableExpr
   * | functionCallExpr | "(" expression ")".
   * 
   * @return The parsed factor expression. Returns an empty expression if parsing
   *         fails.
   */
  private Expression parseFactor() throws IOException {
    try {
      var symbol = scanner.symbol();

      if (symbol == Symbol.notRW || symbol == Symbol.bitwiseNot) {
        var operator = scanner.token();
        matchCurrentSymbol();
        return new NotExpr(operator, parseFactor());
      } else if (symbol.isLiteral()) {
        // Handle constant literals separately from constant identifiers.
        return parseConstValue();
      } else if (symbol == Symbol.identifier) {
        // Three possible cases: a declared constant, a variable
        // expression, or a function call expression. Use lookahead
        // tokens and declaration to determine correct parsing action.
        var idStr = scanner.text();
        var decl = idTable.get(idStr);

        if (decl != null) {
          if (decl instanceof ConstDecl)
            return parseConstValue();
          else if (decl instanceof VariableDecl)
            return parseVariableExpr();
          else if (decl instanceof FunctionDecl)
            return parseFunctionCallExpr();
          else {
            var errorPos = scanner.position();
            var errorMsg = "Identifier \"" + idStr
                + "\" is not valid as an expression.";

            // special recovery when procedure call is used as a function call
            if (decl instanceof ProcedureDecl) {
              scanner.advance();
              if (scanner.symbol() == Symbol.leftParen) {
                scanner.advanceTo(Symbol.rightParen);
                scanner.advance(); // advance past the right paren
              }
            }

            throw error(errorPos, errorMsg);
          }
        } else {
          // Make parsing decision using an additional lookahead symbol.
          if (scanner.lookahead(2).symbol() == Symbol.leftParen)
            return parseFunctionCallExpr();
          else
            throw error("Identifier \"" + scanner.token()
                + "\" has not been declared.");
        }
      } else if (symbol == Symbol.leftParen) {
        matchCurrentSymbol();
        var expr = parseExpression(); // save expression
        match(Symbol.rightParen);
        return expr;
      } else
        throw error("Invalid expression.");
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(factorFollowers);
      return EmptyExpression.instance();
    }
  }

  /**
   * perhaps handle the minus sign, @TODO
   * 
   * PARTIALLY BOOK
   * 
   * constValue = ( [ "-" ] literal ) | constId.
   * 
   * @return The parsed constant value. Returns
   *         an empty expression if parsing fails.
   */
  private Expression parseConstValue() throws IOException {
    try {
      if (scanner.symbol() == Symbol.identifier) {
        var text = scanner.text();
        var constId = scanner.token();
        var constdecl = idTable.get(text);
        if (constdecl instanceof ConstDecl) {
          return new ConstValue(constId, (ConstDecl) constdecl);
        } else {
          throw error("Identifier \"" + text + "\" is not a constant.");
        }
      } else {
        Expression constValue;
        if (scanner.symbol() == Symbol.minus) {
          var operator = scanner.token();
          matchCurrentSymbol();
          var operand = parseConstValue();
          constValue = new NegationExpr(operator, operand);
        } else {
          var literal = parseLiteral();
          constValue = new ConstValue(literal);
        }
        return constValue;
      }
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(EnumSet.of(Symbol.semicolon, Symbol.comma, Symbol.rightBracket,
          Symbol.rightParen, Symbol.equals, Symbol.notEqual, Symbol.lessThan,
          Symbol.lessOrEqual, Symbol.greaterThan, Symbol.greaterOrEqual, Symbol.plus,
          Symbol.minus, Symbol.times, Symbol.divide,
          Symbol.modRW, Symbol.andRW, Symbol.orRW));
      return EmptyExpression.instance();
    }
  }

  /**
   * variableExpr = variable.
   * 
   * GIVEN
   * 
   * @return The parsed variable expression. Returns
   *         an empty expression if parsing fails.
   */
  private Expression parseVariableExpr() throws IOException {
    try {
      var variable = parseVariableCommon();
      return new VariableExpr(variable);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(factorFollowers);
      return EmptyExpression.instance();
    }
  }

  /**
   * functionCallExpr = funcId "(" [ actualParameters ] ")".
   * actualParameters = expressions.
   * 
   * @return The parsed function call expression. Returns
   *         an empty expression if parsing fails.
   */
  private Expression parseFunctionCallExpr() throws IOException {
    try {
      var funId = scanner.token();
      match(Symbol.identifier);
      match(Symbol.leftParen);
      var actualParams = new ArrayList<Expression>(4);
      if (scanner.symbol().isExprStarter()) {
        actualParams.addAll(parseExpressions());
      }
      match(Symbol.rightParen);
      return new FunctionCallExpr(funId, actualParams);
    } catch (ParserException e) {
      errorHandler.reportError(e);
      recover(factorFollowers);
      return EmptyExpression.instance();
    }
  }

  // Utility parsing methods

  /**
   * Wrapper around method parseConstValue() that always
   * returns a valid constant integer value.
   */
  private ConstValue parseIntConstValue() throws IOException {
    var token = new Token(Symbol.intLiteral, new Position(), "1");
    var defaultConstValue = new ConstValue(token);

    var intConstValue = parseConstValue();

    if (intConstValue instanceof EmptyExpression)
      intConstValue = defaultConstValue; // Error has already been reported.
    else if (intConstValue.type() != Type.Integer) {
      var errorMsg = "Constant value should have type Integer.";
      // no error recovery required here
      errorHandler.reportError(error(intConstValue.position(), errorMsg));
      intConstValue = defaultConstValue;
    }

    return (ConstValue) intConstValue;
  }

  private void match(Symbol expectedSymbol) throws IOException, ParserException {
    if (scanner.symbol() == expectedSymbol)
      scanner.advance();
    else {
      StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
      String callerMethod = stackTraceElements[2].getMethodName();
      var errorMsg = "Expecting \"" + expectedSymbol + "\" but found \""
          + scanner.token() + "\" instead. @" + callerMethod;
      throw error(errorMsg);
    }
  }

  private void matchCurrentSymbol() throws IOException {
    scanner.advance();
  }

  private void recover(Set<Symbol> followers) throws IOException {
    scanner.advanceTo(followers);
  }

  private ParserException error(String errorMsg) {
    return error(scanner.position(), errorMsg);
  }

  private ParserException error(Position errorPos, String errorMsg) {
    return new ParserException(errorPos, errorMsg);
  }

  private InternalCompilerException internalError(String errorMsg) {
    return new InternalCompilerException(scanner.position(), errorMsg);
  }
}
