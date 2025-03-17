package edu.citadel.cprl;

import edu.citadel.common.Position;
import edu.citadel.common.ErrorHandler;
import edu.citadel.common.ParserException;
import edu.citadel.common.InternalCompilerException;

import edu.citadel.cprl.ast.*;

import java.io.IOException;
import java.util.*;

public final class Parser
  {
    private Scanner scanner;
    private IdTable idTable;
    private ErrorHandler errorHandler;
    private LoopContext loopContext = new LoopContext();
    private SubprogramContext supprogramContext = new SubprogramContext();

    private final Set<Symbol> stmtFollowers = EnumSet.of(
    		Symbol.identifier, Symbol.ifRW, Symbol.elseRW,
    		Symbol.whileRW, Symbol.loopRW, Symbol.forRW,
    		Symbol.readRW, Symbol.writeRW, Symbol.writelnRW,
    		Symbol.exitRW, Symbol.leftBrace, Symbol.rightBrace,
    		Symbol.returnRW);

    private final Set<Symbol> subprogDeclFollowers = EnumSet.of(Symbol.EOF,
    		Symbol.procRW, Symbol.funRW);

    private final Set<Symbol> factorFollowers = EnumSet.of(
        Symbol.semicolon,   Symbol.loopRW,      Symbol.thenRW,
        Symbol.rightParen,  Symbol.andRW,       Symbol.orRW,
        Symbol.equals,      Symbol.notEqual,    Symbol.lessThan,
        Symbol.lessOrEqual, Symbol.greaterThan, Symbol.greaterOrEqual,
        Symbol.plus,        Symbol.minus,       Symbol.times,
        Symbol.divide,      Symbol.modRW,       Symbol.rightBracket,
        Symbol.comma,       Symbol.bitwiseAnd,  Symbol.bitwiseOr,
        Symbol.bitwiseXor,  Symbol.leftShift,   Symbol.rightShift,
        Symbol.dotdot);

    private Set<Symbol> initialDeclFollowers()
      {
        // An initial declaration can always be followed by another
        // initial declaration, regardless of the scope level.
        var followers = EnumSet.of(Symbol.constRW, Symbol.varRW,
        		Symbol.typeRW);

        if (idTable.scopeLevel() == ScopeLevel.GLOBAL)
            followers.addAll(EnumSet.of(Symbol.procRW, Symbol.funRW));
        else
          {
            followers.addAll(stmtFollowers);
            followers.remove(Symbol.elseRW);
          }

        return followers;
      }

    public Parser(Scanner scanner, IdTable idTable, ErrorHandler errorHandler)
      {
        this.scanner = scanner;
        this.idTable = idTable;
        this.errorHandler = errorHandler;
      }
    
	/**program = initialDecls subprogramDecls.
	 * 
     *@return The parsed program.  Returns a program with an empty list
     *of initial declarations and an empty list of subprogram
     *declarations if parsing fails.
     */
    public Program parseProgram() throws IOException
      {
        try
          {
            var initialDecls = parseInitialDecls();
            var subprogramDecls = parseSubprogramDecls();
            if (scanner.symbol() != Symbol.EOF)
              {
                var errorMsg = "Expecting \"proc\" or \"fun\" but found \""
                             + scanner.token() + "\" instead.";
                throw error(errorMsg);
              }
            return new Program(initialDecls, subprogDecls);
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(EnumSet.of(Symbol.EOF));
            return new Program();
          }
      }

    /**initialDecls = { initialDecl }.
     * 
     *@return The list of initial declarations.
     */
    private List<InitialDecl> parseInitialDecls() throws IOException
      {
    	var initialDecls = new ArrayList<InitialDecl>(10);
    	
        while (scanner.symbol().isInitialDeclStarter())
        	parseInitialDecl();
        
        return initialDecls;
      }

    /**
     *initialDecl = constDecl | varDecl | typeDecl.
     *@return The parsed initial declaration.  Returns an
     *empty initial declaration if parsing fails.
     */
    private InitialDecl parseInitialDecl() throws IOException
      {
    	try {
    		var symbol = scanner.symbol();
        	if(symbol == Symbol.constRW) {
        		parseConstDecl();
        	}else if(symbol == Symbol.varRW){
        		parseVarDecl();
        	}else if(symbol == Symbol.typeRW){
        		parseTypeDecl();
        	}else {
        		throw error("Invalid initial declaration.");
        	}
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(initialDeclFollowers());
    	}
      }

    /**
     *constDecl = "const" constId ":=" [ "-" ] literal ";".
     *
     *@return The parsed constant declaration.  Returns an
     *empty initial declaration if parsing fails.
     */
    private InitialDecl parseConstDecl() throws IOException
      {
    	try {
    		match(Symbol.constRW);
    		var idToken = scanner.token();
    		match(Symbol.identifier);
    		match(Symbol.assign);
    		if(scanner.symbol()==Symbol.minus) {
    			matchCurrentSymbol();
    		}
    		parseLiteral();
    		match(Symbol.semicolon);
    		idTable.add(idToken,IdType.constantId);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(initialDeclFollowers());
    	}
      }

    /**
     * literal = intLiteral | charLiteral | stringLiteral | "true" | "false" .
     * 
     * @return The parsed literal token.  Returns a default token if parsing fails.
     */
    private Token parseLiteral() throws IOException
      {
    	try
        {
          if (scanner.symbol().isLiteral())
            {
              var literal = scanner.token();
              matchCurrentSymbol();
              return literal;
            }
          else
              throw error("Invalid literal expression.");
        }
      catch (ParserException e)
        {
          errorHandler.reportError(e);
          recover(factorFollowers);
          return new Token();
        }
      }

    /**varDecl = "var" identifiers ":" 
     * 			( typeName | arrayTypeConstr | stringTypeConstr)
     *          [ ":=" initializer] ";".
     * 
     * @return The parsed variable declaration.  Returns an
     *         empty initial declaration if parsing fails.
     */
    private InitialDecl parseVarDecl() throws IOException
      {
    	try
        {
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
          else
            {
              var errorMsg = "Expecting a type name, reserved word \"array\", "
                           + "or reserved word \"string\".";
              throw error(errorMsg);
            }

          Initializer initializer = EmptyInitializer.instance();
          if (scanner.symbol() == Symbol.assign)
            {
              matchCurrentSymbol();
              initializer = parseInitializer();
            }

          match(Symbol.semicolon);

          var varDecl = new VarDecl(identifiers, varType, initializer,
                                    idTable.scopeLevel());

          for (SingleVarDecl decl : varDecl.singleVarDecls())
              idTable.add(decl);

          return varDecl;
        }
      catch (ParserException e)
        {
          errorHandler.reportError(e);
          recover(initialDeclFollowers());
          return EmptyInitialDecl.instance();
        }
      }

    /**identifiers = identifier { "," identifier } .
     * 
     * @return The list of identifier tokens.  Returns an empty list if parsing fails.
     */
    private List<Token> parseIdentifiers() throws IOException
    {
        try
          {
            var identifiers = new ArrayList<Token>(10);
            var idToken = scanner.token();
            match(Symbol.identifier);
            identifiers.add(idToken);

            while (scanner.symbol() == Symbol.comma)
              {
                matchCurrentSymbol();
                idToken = scanner.token();
                match(Symbol.identifier);
                identifiers.add(idToken);
              }

            return identifiers;
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(EnumSet.of(Symbol.colon, Symbol.greaterThan));
            return Collections.emptyList();
          }
      }

    /**initializer = constValue | compositeInitializer .
     * 
     * @return The parsed initializer.  Returns an empty
     *         initializer if parsing fails.
     */
    private Initializer parseInitializer() throws IOException
    {
        try
          {
            var symbol = scanner.symbol();
            if (symbol == Symbol.identifier || symbol.isLiteral() || symbol == Symbol.minus)
              {
                var expr = parseConstValue();
                return expr instanceof ConstValue constValue ? constValue
                                           : EmptyInitializer.instance();
              }
            else if (symbol == Symbol.leftBrace)
                return parseCompositeInitializer();
            else
              {
                var errorMsg = "Expecting literal, identifier, or left brace.";
                throw error(errorMsg);
              }
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(initialDeclFollowers());
            return EmptyInitializer.instance();
          }
      }

    /**compositeInitializer = "{" initializer { "," initializer } "}" .
     * 
     * @return The parsed composite initializer.  Returns an empty composite
     *         initializer if parsing fails.
     */
    private CompositeInitializer parseCompositeInitializer() throws IOException
      {
    	try {
    		match(Symbol.leftBrace);
    		parseInitializer();
    		while(scanner.symbol()==Symbol.comma) {
    			match(Symbol.comma);
    			parseInitializer();
    		}
    		match(Symbol.rightBrace);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(EnumSet.of(Symbol.comma, Symbol.rightBrace, Symbol.semicolon));
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private InitialDecl parseTypeDecl() throws IOException
      {
        assert scanner.symbol() == Symbol.typeRW;

        try
          {
            switch (scanner.lookahead(4).symbol())
              {
                case arrayRW  -> parseArrayTypeDecl();
                case recordRW -> parseRecordTypeDecl();
                case stringRW -> parseStringTypeDecl();
                default ->
                  {
                    Position errorPos = scanner.lookahead(4).position();
                    throw error(errorPos, "Invalid type declaration.");
                  }
              };
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            matchCurrentSymbol();   // force scanner past "type"
            recover(initialDeclFollowers());
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private InitialDecl parseArrayTypeDecl() throws IOException
      {
    	try{
    		match(Symbol.typeRW);
    		var idToken = scanner.token();
    		match(Symbol.identifier);
    		match(Symbol.equals);
    		match(Symbol.arrayRW);
    		match(Symbol.leftBracket);
    		parseConstValue();
    		match(Symbol.rightBracket);
    		match(Symbol.ofRW);
    		parseTypeName();
    		match(Symbol.semicolon);
    		idTable.add(idToken, IdType.arrayTypeId);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(initialDeclFollowers());
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private ArrayType parseArrayTypeConstr() throws IOException
      {
        try
          {
            match(Symbol.arrayRW);
            match(Symbol.leftBracket);
            parseConstValue();
            match(Symbol.rightBracket);
            match(Symbol.ofRW);
            parseTypeName();
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(EnumSet.of(Symbol.semicolon));
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private InitialDecl parseRecordTypeDecl() throws IOException
      {
        try
          {
            match(Symbol.typeRW);
            var typeId = scanner.token();
            match(Symbol.identifier);
            match(Symbol.equals);
            match(Symbol.recordRW);
            match(Symbol.leftBrace);

            try
              {
                idTable.openScope(ScopeLevel.RECORD);
                parseFieldDecls();
              }
            finally
              {
                idTable.closeScope();
              }

            match(Symbol.rightBrace);
            match(Symbol.semicolon);
            idTable.add(typeId, IdType.recordTypeId);
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(initialDeclFollowers());
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private List<FieldDecl> parseFieldDecls() throws IOException
      {
    	while(scanner.symbol()==Symbol.identifier) {
    		parseFieldDecl();
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private FieldDecl parseFieldDecl() throws IOException
      {
    	try {
    		var fieldId = scanner.token();
    		idTable.add(fieldId, IdType.fieldId);
    		match(Symbol.identifier);
    		match(Symbol.colon);
    		parseTypeName();
    		match(Symbol.semicolon);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(EnumSet.of(Symbol.identifier, Symbol.rightBrace));
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private InitialDecl parseStringTypeDecl() throws IOException
      {
    	try {
    		match(Symbol.typeRW);
    		var idToken = scanner.token();
    		match(Symbol.identifier);
    		match(Symbol.equals);
    		match(Symbol.stringRW);
    		match(Symbol.leftBracket);
    		parseConstValue();
    		match(Symbol.rightBracket);
    		match(Symbol.semicolon);
    		idTable.add(idToken, IdType.stringTypeId);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(initialDeclFollowers());
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private StringType parseStringTypeConstr() throws IOException
      {
        try
          {
            match(Symbol.stringRW);
            match(Symbol.leftBracket);
            parseConstValue();
            match(Symbol.rightBracket);
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(EnumSet.of(Symbol.semicolon));
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Type parseTypeName() throws IOException
      {
        try
          {
            switch (scanner.symbol())
              {
                case IntegerRW  -> matchCurrentSymbol();
                case BooleanRW  -> matchCurrentSymbol();
                case CharRW     -> matchCurrentSymbol();
                case identifier ->
                  {
                    var typeId = scanner.token();
                    matchCurrentSymbol();
                    var type = idTable.get(typeId.text());

                    if (type != null)
                      {
                        if (type == IdType.arrayTypeId || type == IdType.recordTypeId || type == IdType.stringTypeId)
                            ;   // empty statement for versions 1 and 2 of Parser
                        else
                          {
                            var errorMsg = "Identifier \"" + typeId + "\" is not a valid type name.";
                            throw error(typeId.position(), errorMsg);
                          }
                      }
                    else
                      {
                        var errorMsg = "Identifier \"" + typeId + "\" has not been declared.";
                        throw error(typeId.position(), errorMsg);
                      }
                  }
                default -> throw error("Invalid type name.");
              }
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(EnumSet.of(Symbol.semicolon,  Symbol.comma,
                               Symbol.rightParen, Symbol.leftBrace));
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private List<SubprogramDecl> parseSubprogramDecls() throws IOException
      {
    	while(scanner.symbol().isSubprogramDeclStarter()) {
    		parseSubprogramDecl();
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private SubprogramDecl parseSubprogramDecl() throws IOException
      {
    	try {
    		switch(scanner.symbol()) {
	    		case procRW -> parseProcedureDecl();
	    		case funRW -> parseFunctionDecl();
	    		default -> throw error("Invalid subprogram declaration");
    		}
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(subprogDeclFollowers);
    	}
    	
      }
    
    /**
     * 
     * @return
     * @throws IOException
     */
    private SubprogramDecl parseProcedureDecl() throws IOException
      {
        try
          {
            match(Symbol.procRW);
            var procId = scanner.token();
            match(Symbol.identifier);
            idTable.add(procId, IdType.procedureId);
            match(Symbol.leftParen);

            try
              {
                idTable.openScope(ScopeLevel.LOCAL);

                if (scanner.symbol().isParameterDeclStarter())
                    parseParameterDecls();

                match(Symbol.rightParen);
                match(Symbol.leftBrace);
                parseInitialDecls();
                parseStatements();
              }
            finally
              {
                idTable.closeScope();
              }

            match(Symbol.rightBrace);
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(subprogDeclFollowers);
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private SubprogramDecl parseFunctionDecl() throws IOException
      {
    	try {
    		match(Symbol.funRW);
    		var funId = scanner.token();
    		match(Symbol.identifier);
    		match(Symbol.leftParen);
    		try {
    			idTable.openScope(ScopeLevel.LOCAL);
    			idTable.add(funId, IdType.functionId);
    		}finally {
    			idTable.closeScope();
    		}
    		parseParameterDecls();
    		match(Symbol.rightParen);
    		match(Symbol.colon);
    		match(Symbol.leftBrace);
    		parseInitialDecls();
    		parseStatements();
    		match(Symbol.rightBrace);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
    		recover(subprogDeclFollowers);
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private List<ParameterDecl> parseParameterDecls() throws IOException
      {
    	parseParameterDecl();
		while(scanner.symbol()==Symbol.comma) {
			matchCurrentSymbol();
			parseParameterDecl();
		}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private ParameterDecl parseParameterDecl() throws IOException
      {
    	try {
    		if(scanner.symbol()==Symbol.varRW) {
        		matchCurrentSymbol();
        	}
    		match(Symbol.identifier);
    		match(Symbol.colon);
    		parseTypeName();
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(EnumSet.of(Symbol.comma, Symbol.rightParen));
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private List<Statement> parseStatements() throws IOException
      {
    	while(scanner.symbol().isStmtStarter()) {
    		parseStatement();
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseStatement() throws IOException
      {
        try
          {
            if (scanner.symbol() == Symbol.identifier)
              {
                // Handle identifiers based on how they are declared,
                // or use the lookahead symbol if not declared.
                var idStr  = scanner.text();
                var idType = idTable.get(idStr);

                if (idType != null)
                  {
                    if (idType == IdType.variableId) 
                        parseAssignmentStmt();
                    else if (idType == IdType.procedureId)
                        parseProcedureCallStmt();
                    else
                        throw error("Identifier \"" + idStr + "\" cannot start a statement.");
                  }
                else
                  {
            		if (scanner.lookahead(2).symbol() == Symbol.leftParen) {
            			parseProcedureCallStmt();
            		}else {
            			throw error("Identifier \"" + idStr + "\" has not been declared.");
            		}
                  }
              }
            else
              {
            	switch (scanner.symbol())
                {
                case assign -> parseAssignmentStmt();
                case leftBrace -> parseCompoundStmt();
                case ifRW      -> parseIfStmt();
                case whileRW -> parseLoopStmt();
                case loopRW -> parseLoopStmt();
                case forRW -> parseForLoopStmt();
                case exitRW -> parseExitStmt();
                case readRW -> parseReadStmt();
                case writeRW -> parseWriteStmt();
                case writelnRW -> parseWritelnStmt();
                case returnRW -> parseReturnStmt();
                default -> throw internalError(scanner.token()
                                 + " cannot start a statement.");
                }
              }
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            // Error recovery here is complicated for identifiers since they can both
            // start a statement and appear elsewhere in the statement.  (Consider,
            // for example, an assignment statement or a procedure call statement.)
            // Since the most common error is to declare or reference an identifier
            // incorrectly, we will assume that this is the case and advance to the
            // end of the current statement before performing error recovery.
            scanner.advanceTo(EnumSet.of(Symbol.semicolon, Symbol.rightBrace));
            recover(stmtFollowers);
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseAssignmentStmt() throws IOException
      {
    	try{
            parseVariable(); 
            try {
		    match(Symbol.assign);
	    } catch (ParserException e){
		    if(scanner.symbol() == Symbol.equals){
			    errorHandler.reportError(e);
			    matchCurrentSymbol();
		    } else {
			    throw e;
		    }
	    }
            parseExpression();
            match(Symbol.semicolon);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(stmtFollowers);
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseCompoundStmt() throws IOException
      {
    	try {
    		match(Symbol.leftBrace);
    		parseStatements();
    		match(Symbol.rightBrace);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(stmtFollowers);
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseIfStmt() throws IOException
      {
    	try {
    		match(Symbol.ifRW);
    		parseExpression();
    		match(Symbol.thenRW);
    		parseStatement();
    		if(scanner.symbol()==Symbol.elseRW) {
    			matchCurrentSymbol();
    			parseStatement();
    		}
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(stmtFollowers);
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseLoopStmt() throws IOException
      {
    	try {
      		if(scanner.symbol()==Symbol.whileRW) {
      			matchCurrentSymbol();
      			parseExpression();
      		}
      		match(Symbol.loopRW);
      		parseStatement();
      	}catch(ParserException e) {
      		errorHandler.reportError(e);
            recover(stmtFollowers);
      	}
      }
    
    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseForLoopStmt() throws IOException
      {
        try
          {
            // create a new scope for the loop variable
            idTable.openScope(ScopeLevel.LOCAL);

            match(Symbol.forRW);
            var loopId = scanner.token();
            match(Symbol.identifier);
            idTable.add(loopId, IdType.variableId);
            match(Symbol.inRW);
            parseExpression();
            match(Symbol.dotdot);
            parseExpression();
            match(Symbol.loopRW);
            parseStatement();
          }
        catch (ParserException e)
        {
          errorHandler.reportError(e);
          recover(stmtFollowers);
        }
        finally
          {
            idTable.closeScope();
          }
        
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseExitStmt() throws IOException
      {
    	try{
    		match(Symbol.exitRW);
    		if(scanner.symbol() == Symbol.whenRW) {
    			matchCurrentSymbol();
    			parseExpression();
    		}
    		match(Symbol.semicolon);
    	}catch (ParserException e) {
    		errorHandler.reportError(e);
            recover(stmtFollowers);
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseReadStmt() throws IOException
      {
    	try {
    		match(Symbol.readRW);
    		parseVariable();
    		match(Symbol.semicolon);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(stmtFollowers);
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseWriteStmt() throws IOException
      {
    	try {
    		match(Symbol.writeRW);
    		parseExpressions();
    		match(Symbol.semicolon);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(stmtFollowers);
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private List<Expression> parseExpressions() throws IOException
      {
    	parseExpression();
		while(scanner.symbol()==Symbol.comma) {
			matchCurrentSymbol();
			parseExpression();
		}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseWritelnStmt() throws IOException
      {
        try
          {
            match(Symbol.writelnRW);

            if (scanner.symbol().isExprStarter())
                parseExpressions();

            match(Symbol.semicolon);
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(stmtFollowers);
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseProcedureCallStmt() throws IOException
      {
    	try {
    		match(Symbol.identifier);
    		match(Symbol.leftParen);
    		parseExpressions();
    		match(Symbol.rightParen);
    		match(Symbol.semicolon);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(stmtFollowers);
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Statement parseReturnStmt() throws IOException
      {
    	try {
    		match(Symbol.returnRW);
    		if(scanner.symbol()!=Symbol.semicolon) {
    			parseExpression();
    		}
    		match(Symbol.semicolon);
    	}catch(ParserException e) {
  		errorHandler.reportError(e);
          recover(stmtFollowers);
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     * @throws ParserException
     */
    private Variable parseVariableCommon() throws IOException, ParserException
      {
        var idToken = scanner.token();
        match(Symbol.identifier);
        var idType = idTable.get(idToken.text());

        if (idType == null)
          {
            var errorMsg = "Identifier \"" + idToken + "\" has not been declared.";
            throw error(idToken.position(), errorMsg);
          }
        else if (idType != IdType.variableId)
          {
            var errorMsg = "Identifier \"" + idToken + "\" is not a variable.";
            throw error(idToken.position(), errorMsg);
          }

        while (scanner.symbol().isSelectorStarter())
          {
            if (scanner.symbol() == Symbol.leftBracket)
              {
                // parse index expression
                match(Symbol.leftBracket);
                parseExpression();
                match(Symbol.rightBracket);
              }
            else if (scanner.symbol() == Symbol.dot)
              {
                // parse field expression
                match(Symbol.dot);
                match(Symbol.identifier);
              }
          }

      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Variable parseVariable() throws IOException
      {
        try
          {
            parseVariableCommon();
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(EnumSet.of(Symbol.assign, Symbol.semicolon));
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Expression parseExpression() throws IOException
      {
        parseRelation();

        while (scanner.symbol().isLogicalOperator())
          {
            matchCurrentSymbol();
            parseRelation();
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Expression parseRelation() throws IOException
      {
    	parseSimpleExpr();
    	if(scanner.symbol().isRelationalOperator()) {
    		matchCurrentSymbol();
    		parseSimpleExpr();
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Expression parseSimpleExpr() throws IOException
      {
    	if(scanner.symbol().isSignOperator()) {
    		matchCurrentSymbol();
    	}
    	parseTerm();
    	while(scanner.symbol().isAddingOperator()) {
    		matchCurrentSymbol();
    		parseTerm();
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Expression parseTerm() throws IOException
      {
    	parseFactor();
    	while(scanner.symbol().isMultiplyingOperator()) {
    		matchCurrentSymbol();
    		parseFactor();
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Expression parseFactor() throws IOException
      {
        try
          {
            var symbol = scanner.symbol();

            if (symbol == Symbol.notRW || symbol == Symbol.bitwiseNot)
              {
                matchCurrentSymbol();
                parseFactor();
              }
            else if (symbol.isLiteral())
              {
                // Handle constant literals separately from constant identifiers.
                parseConstValue();
              }
            else if (symbol == Symbol.identifier)
              {
                // Three possible cases: a declared constant, a variable
                // expression, or a function call expression.  Use lookahead
                // tokens and declaration to determine correct parsing action.

                var idStr  = scanner.text();
                var idType = idTable.get(idStr);

                if (idType != null)
                  {
                    if (idType == IdType.constantId)
                        parseConstValue();
                    else if (idType == IdType.variableId)
                        parseVariableExpr();
                    else if (idType == IdType.functionId)
                        parseFunctionCallExpr();
                    else
                      {
                        var errorPos = scanner.position();
                        var errorMsg = "Identifier \"" + idStr
                                     + "\" is not valid as an expression.";

                        // special handling when procedure call is used as a function call
                        if (idType == IdType.procedureId)
                          {
                            scanner.advance();
                            if (scanner.symbol() == Symbol.leftParen)
                              {
                                scanner.advanceTo(Symbol.rightParen);
                                scanner.advance();   // advance past the right paren
                              }
                          }

                        throw error(errorPos, errorMsg);
                      }
                  }
                else
                  {
                    // Make parsing decision using an additional lookahead symbol.
                    if (scanner.lookahead(2).symbol() == Symbol.leftParen)
                        parseFunctionCallExpr();
                    else
                        throw error("Identifier \"" + scanner.token()
                                  + "\" has not been declared.");
                  }
              }
            else if (symbol == Symbol.leftParen)
              {
                matchCurrentSymbol();
                parseExpression();
                match(Symbol.rightParen);
              }
            else
                throw error("Invalid expression.");
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(factorFollowers);
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Expression parseConstValue() throws IOException
      {
    	try {
    		if(scanner.symbol()==Symbol.identifier) {
    			matchCurrentSymbol();
    		}else {
    			if(scanner.symbol()==Symbol.minus) {
    				if(scanner.lookahead(2).symbol()!=Symbol.intLiteral) {
    					throw error("Invalid constant.");
    				}
    				matchCurrentSymbol();
    			}
    			parseLiteral();
    		}
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(EnumSet.of(Symbol.semicolon, Symbol.comma, Symbol.rightBracket,
            	Symbol.rightParen, Symbol.equals, Symbol.notEqual, Symbol.lessThan, 
            	Symbol.lessOrEqual, Symbol.greaterThan, Symbol.greaterOrEqual, Symbol.plus, 
            	Symbol.minus, Symbol.times, Symbol.divide, 
            	Symbol.modRW, Symbol.andRW, Symbol.orRW));
    	}
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Expression parseVariableExpr() throws IOException
      {
        try
          {
            parseVariableCommon();
          }
        catch (ParserException e)
          {
            errorHandler.reportError(e);
            recover(factorFollowers);
          }
      }

    /**
     * 
     * @return
     * @throws IOException
     */
    private Expression parseFunctionCallExpr() throws IOException
      {
    	try{
    		match(Symbol.identifier);
    		match(Symbol.leftParen);
    		if(scanner.symbol().isExprStarter()) {
    			parseExpressions();
    		}
    		match(Symbol.rightParen);
    	}catch(ParserException e) {
    		errorHandler.reportError(e);
            recover(factorFollowers);
    	}
      }
    /**
     * 
     * @return
     * @throws IOException
     */
    private ConstValue parseIntConstValue() throws IOException
    {
      var token = new Token(Symbol.intLiteral, new Position(), "1");
      var defaultConstValue = new ConstValue(token);

      var intConstValue = parseConstValue();

      if (intConstValue instanceof EmptyExpression)
          intConstValue = defaultConstValue;   // Error has already been reported.
      else if (intConstValue.type() != Type.Integer)
        {
          var errorMsg = "Constant value should have type Integer.";
          // no error recovery required here
          errorHandler.reportError(error(intConstValue.position(), errorMsg));
          intConstValue = defaultConstValue;
        }

      return (ConstValue) intConstValue;
    }

    // Utility parsing methods
    private void match(Symbol expectedSymbol) throws IOException, ParserException
      {
        if (scanner.symbol() == expectedSymbol)
            scanner.advance();
        else
          {
            var errorMsg = "Expecting \"" + expectedSymbol + "\" but found \""
                         + scanner.token() + "\" instead.";
            throw error(errorMsg);
          }
      }

    private void matchCurrentSymbol() throws IOException
      {
        scanner.advance();
      }

    private void recover(Set<Symbol> followers) throws IOException
      {
        scanner.advanceTo(followers);
      }

    private ParserException error(String errorMsg)
      {
        return error(scanner.position(), errorMsg);
      }

    private ParserException error(Position errorPos, String errorMsg)
      {
        return new ParserException(errorPos, errorMsg);
      }

    private InternalCompilerException internalError(String errorMsg)
      {
        return new InternalCompilerException(scanner.position(), errorMsg);
      }
  }
