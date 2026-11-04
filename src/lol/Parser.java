package lol;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser recursive descent com recuperacao de erros (panic mode).
 * Converte uma lista de tokens em uma AST (lista de Stmt.ClassDecl).
 * Popula a tabela de simbolos durante o parsing de declaracoes de variaveis.
 */
public class Parser {
    private final List<Token> tokens;
    private int current = 0;
    private final List<CompilerError> errors = new ArrayList<>();
    private final SymbolTable symbolTable = new SymbolTable();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            int startCurrent = current;
            Stmt stmt = classDecl();
            if (stmt != null) {
                statements.add(stmt);
            }
            // Evita loop infinito: se nada foi consumido, avanca
            if (current == startCurrent) {
                advance();
            }
        }
        return statements;
    }

    public List<CompilerError> getErrors() {
        return errors;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    // === Declarações ===

    private Stmt classDecl() {
        try {
            // modificadores antes de class (public, static)
            while (match(TokenType.PUBLIC, TokenType.STATIC)) {}
            consume(TokenType.CLASS, "Esperado 'class'");
            Token name = consume(TokenType.IDENTIFIER, "Esperado nome da classe");
            consume(TokenType.LBRACE, "Esperado '{' após nome da classe");

            List<Stmt.MethodDecl> methods = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                int startCurrent = current;
                Stmt.MethodDecl method = methodDecl();
                if (method != null) {
                    methods.add(method);
                }
                if (current == startCurrent) {
                    advance();
                }
            }
            consume(TokenType.RBRACE, "Esperado '}' apos corpo da classe");
            return new Stmt.ClassDecl(name, methods);
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    private Stmt.MethodDecl methodDecl() {
        try {
            // modificadores
            while (match(TokenType.PUBLIC, TokenType.STATIC)) {}

            // tipo de retorno
            Token typeToken;
            if (match(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN, TokenType.VOID)) {
                typeToken = previous();
            } else {
                throw error(peek(), "Esperado tipo de retorno (int, double, boolean ou void), mas encontrou '" + peek().lexeme + "'");
            }
            String returnType = typeToken.lexeme.toUpperCase();

            // nome do metodo
            Token name = consume(TokenType.IDENTIFIER, "Esperado nome do método");
            consume(TokenType.LPAREN, "Esperado '(' apos nome do método");

            // parametros
            List<Stmt.Param> params = new ArrayList<>();
            symbolTable.enterScope(); // escopo do metodo (parametros)

            if (!check(TokenType.RPAREN)) {
                do {
                    Token paramType;
                    if (match(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN)) {
                        paramType = previous();
                    } else {
                        throw error(peek(), "Esperado tipo do parâmetro (int, double ou boolean), mas encontrou '" + peek().lexeme + "'");
                    }
                    Token paramName = consume(TokenType.IDENTIFIER, "Esperado nome do parâmetro");
                    params.add(new Stmt.Param(paramType.lexeme.toUpperCase(), paramName));
                    // Adiciona parametro na tabela de simbolos
                    Symbol sym = symbolTable.declare(
                            paramType.lexeme.toUpperCase(), paramName.lexeme, null,
                            paramName.line, paramName.column);
                    paramName.symbolId = sym.id;
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "Esperado ')' após parâmetros");

            // corpo
            Stmt.Block body = block();

            symbolTable.exitScope(); // sai do escopo do metodo

            return new Stmt.MethodDecl(name, returnType, params,
                    body != null ? body.statements : new ArrayList<>(), typeToken);
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    // === Statements ===

    private Stmt.Block block() {
        try {
            consume(TokenType.LBRACE, "Esperado '{'");
            symbolTable.enterScope();
            List<Stmt> statements = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                int startCurrent = current;
                Stmt stmt = statement();
                if (stmt != null) {
                    statements.add(stmt);
                }
                if (current == startCurrent) {
                    advance();
                }
            }
            consume(TokenType.RBRACE, "Esperado '}'");
            symbolTable.exitScope();
            return new Stmt.Block(statements);
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        try {
            if (check(TokenType.INT) || check(TokenType.DOUBLE) || check(TokenType.BOOLEAN)) {
                return varDecl();
            }
            // Detecta tipos de Java que nao sao suportados (ex: String, float, long)
            if (check(TokenType.IDENTIFIER) && isUnsupportedType(peek().lexeme)) {
                return unsupportedTypeStmt();
            }
            if (check(TokenType.IF)) return ifStmt();
            if (check(TokenType.WHILE)) return whileStmt();
            if (check(TokenType.FOR)) return forStmt();
            if (check(TokenType.RETURN)) return returnStmt();
            if (check(TokenType.SYSTEM)) return printStmt();
            if (check(TokenType.LBRACE)) return block();
            return exprStmt();
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    /**
     * Trata declaracoes com tipos nao suportados (ex: String x = 5; ou String = 5;).
     * Da uma mensagem clara sobre o tipo nao suportado e se falta o nome da variavel.
     */
    private Stmt unsupportedTypeStmt() {
        Token typeToken = advance(); // consome o nome do tipo (String, etc.)

        if (check(TokenType.IDENTIFIER)) {
            // String x = 10; — tipo nao suportado, mas tem nome de variavel
            error(typeToken, "Tipo '" + typeToken.lexeme
                    + "' não é suportado. Tipos suportados: int, double, boolean");
        } else {
            // String = 10; ou String; — tipo nao suportado e falta o nome da variavel
            error(typeToken, "Tipo '" + typeToken.lexeme
                    + "' não é suportado e falta o nome da variável. Ex: int nome = valor");
        }

        // Recupera: pula ate o ';'
        while (!check(TokenType.SEMICOLON) && !isAtEnd()) {
            advance();
        }
        if (check(TokenType.SEMICOLON)) {
            advance();
        }
        return null;
    }

    /**
     * Verifica se o lexeme eh um tipo de Java que nao suportamos.
     */
    private boolean isUnsupportedType(String name) {
        String lower = name.toLowerCase();
        return lower.equals("string") || lower.equals("float") || lower.equals("long")
                || lower.equals("char") || lower.equals("byte") || lower.equals("short");
    }

    private Stmt.VarDecl varDecl() {
        Token typeToken;
        if (match(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN)) {
            typeToken = previous();
        } else {
            throw error(peek(), "Falta o tipo da variável (int, double ou boolean), mas encontrou '" + peek().lexeme + "'");
        }

        Token name = consume(TokenType.IDENTIFIER, "Esperado nome de variável");

        Expr initializer = null;
        boolean hadError = false;

        if (match(TokenType.ASSIGN)) {
            initializer = expression();
        } else if (!check(TokenType.SEMICOLON)) {
            // Algo diferente de '=' e ';' segue o nome da variavel.
            // Provavelmente o '=' foi esquecido.
            error(peek(), "Esperado '=' ou ';' após nome de variável");
            hadError = true;
            // Recupera: pula ate o ';' para nao gerar erros em cascata
            while (!check(TokenType.SEMICOLON) && !isAtEnd()) {
                advance();
            }
        }

        // Extrai valor do inicializador se for literal
        Object value = null;
        if (initializer instanceof Expr.Literal) {
            value = ((Expr.Literal) initializer).value;
        }

        // Declara na tabela de simbolos ANTES de checar o ';'.
        Symbol symbol = symbolTable.declare(
                typeToken.lexeme.toUpperCase(), name.lexeme, value,
                name.line, name.column);
        name.symbolId = symbol.id;

        // Consome o ';'
        if (hadError) {
            // Ja reportamos o erro acima; so consome o ';' se existir
            if (check(TokenType.SEMICOLON)) {
                advance();
            }
        } else {
            expectSemicolon("Esperado ';'");
        }

        return new Stmt.VarDecl(typeToken.lexeme.toUpperCase(), name, initializer, typeToken);
    }

    private Stmt ifStmt() {
        consume(TokenType.IF, "Esperado 'if'");
        consume(TokenType.LPAREN, "Esperado '(' após 'if'");
        Expr condition = expression();
        consume(TokenType.RPAREN, "Esperado ')' após condição do if");
        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = statement();
        }
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStmt() {
        consume(TokenType.WHILE, "Esperado 'while'");
        consume(TokenType.LPAREN, "Esperado '(' após 'while'");
        Expr condition = expression();
        consume(TokenType.RPAREN, "Esperado ')' após condição do while");
        Stmt body = statement();
        return new Stmt.While(condition, body);
    }

    private Stmt forStmt() {
        consume(TokenType.FOR, "Esperado 'for'");
        consume(TokenType.LPAREN, "Esperado '(' após 'for'");

        // init: varDecl, exprStmt, ou apenas ';'
        Stmt init = null;
        if (check(TokenType.SEMICOLON)) {
            advance();
        } else if (check(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN)) {
            init = varDecl();
        } else {
            init = exprStmt();
        }

        // condicao (opcional)
        Expr condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = expression();
        }
        consume(TokenType.SEMICOLON, "Esperado ';' após condição do for");

        // incremento (opcional)
        Expr increment = null;
        if (!check(TokenType.RPAREN)) {
            increment = expression();
        }
        consume(TokenType.RPAREN, "Esperado ')' após incremento do for");

        Stmt body = statement();
        return new Stmt.For(init, condition, increment, body);
    }

    private Stmt returnStmt() {
        Token keyword = consume(TokenType.RETURN, "Esperado 'return'");
        Expr value = null;
        if (!check(TokenType.SEMICOLON)) {
            value = expression();
        }
        expectSemicolon("Esperado ';'");
        return new Stmt.Return(value, keyword);
    }

    private Stmt printStmt() {
        consume(TokenType.SYSTEM, "Esperado 'System'");
        consume(TokenType.DOT, "Esperado '.' após 'System'");
        consume(TokenType.OUT, "Esperado 'out'");
        consume(TokenType.DOT, "Esperado '.' após 'out'");
        Token printlnToken = consume(TokenType.PRINTLN, "Esperado 'println'");
        consume(TokenType.LPAREN, "Esperado '(' após 'println'");
        Expr expr = expression();
        consume(TokenType.RPAREN, "Esperado ')' após expressao do println");
        expectSemicolon("Esperado ';'");
        return new Stmt.Print(expr, printlnToken);
    }

    private Stmt exprStmt() {
        Expr expr = expression();
        expectSemicolon("Esperado ';'");
        return new Stmt.ExprStmt(expr);
    }

    // === Expressões (precedência crescente) ===

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = logicOr();
        if (match(TokenType.ASSIGN)) {
            Token equals = previous();
            Expr value = assignment(); // associatividade a direita
            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }
            error(equals, "Atribuição inválida");
        }
        return expr;
    }

    private Expr logicOr() {
        Expr expr = logicAnd();
        while (match(TokenType.OR)) {
            Token operator = previous();
            Expr right = logicAnd();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr logicAnd() {
        Expr expr = equality();
        while (match(TokenType.AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(TokenType.EQUALS, TokenType.NOT_EQUALS)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(TokenType.LESS, TokenType.LESS_EQUAL,
                       TokenType.GREATER, TokenType.GREATER_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        // Chamada de metodo direta: identificador(args)
        if (expr instanceof Expr.Variable && match(TokenType.LPAREN)) {
            Token callee = ((Expr.Variable) expr).name;
            List<Expr> args = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                do {
                    args.add(expression());
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "Esperado ')' após argumentos");
            expr = new Expr.Call(callee, args);
        }
        return expr;
    }

    private Expr primary() {
        if (match(TokenType.NUMBER)) {
            Token token = previous();
            return new Expr.Literal(token.literal, token);
        }
        if (match(TokenType.STRING)) {
            Token token = previous();
            return new Expr.Literal(token.literal, token);
        }
        if (match(TokenType.TRUE)) {
            Token token = previous();
            return new Expr.Literal(Boolean.TRUE, token);
        }
        if (match(TokenType.FALSE)) {
            Token token = previous();
            return new Expr.Literal(Boolean.FALSE, token);
        }
        if (match(TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous());
        }
        if (match(TokenType.LPAREN)) {
            Expr expr = expression();
            consume(TokenType.RPAREN, "Esperado ')' apos expressao");
            return new Expr.Grouping(expr);
        }
        throw error(peek(), "Esperado expressao, mas encontrou '" + peek().lexeme + "'");
    }

    // === Helpers ===

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message + ", mas encontrou '" + peek().lexeme + "'");
    }

    /**
     * Consome um ';'. Se faltar, registra o erro apontando para o final
     * do último token (onde o ';' deveria estar), sem lançar excecao.
     */
    private void expectSemicolon(String message) {
        if (check(TokenType.SEMICOLON)) {
            advance();
        } else {
            Token last = previous();
            errors.add(new CompilerError(last.line,
                    last.column + last.lexeme.length(),
                    message, CompilerError.ErrorType.SYNTACTIC));
        }
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type == type;
    }

    private boolean check(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) return true;
        }
        return false;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private ParseError error(Token token, String message) {
        errors.add(new CompilerError(token.line, token.column, message,
                CompilerError.ErrorType.SYNTACTIC));
        return new ParseError();
    }

    /**
     * Recuperação de erro: descarta tokens ate um ponto de sincronizacao.
     */
    private void synchronize() {
        // Nao pula o token atual cegamente: ele pode ser o inicio de
        // um novo statement valido (ex: 'int' quando falta um ';').
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return;

            switch (peek().type) {
                case CLASS: case PUBLIC: case STATIC:
                case INT: case DOUBLE: case BOOLEAN: case VOID:
                case IF: case WHILE: case FOR: case RETURN:
                case RBRACE:
                    return;
            }
            advance();
        }
    }

    private static class ParseError extends RuntimeException {}
}
