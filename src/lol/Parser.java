package lol;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser recursive descent com recuperação de erros (panic mode).
 *
 * <p>Converte a lista de tokens produzida pelo {@link Lexer} em uma AST
 * (lista de {@link Stmt.ClassDecl}) e popula a {@link SymbolTable} durante
 * o parsing de declarações de variáveis e parâmetros.</p>
 *
 * <p>Erros sintáticos são coletados em uma lista; ao detectar um erro o parser
 * chama {@link #synchronize()} para descartar tokens até um ponto seguro e
 * continuar, permitindo reportar múltiplos erros por compilação.</p>
 *
 * <p>Gramática suportada (simplificada):</p>
 * <pre>
 * program    → classDecl*
 * classDecl  → modifier* CLASS IDENTIFIER '{' methodDecl* '}'
 * methodDecl → modifier* type IDENTIFIER '(' params? ')' block
 * params     → type IDENTIFIER (',' type IDENTIFIER)*
 * block      → '{' stmt* '}'
 * stmt       → varDecl | ifStmt | whileStmt | forStmt | returnStmt
 *            | printStmt | block | exprStmt
 * varDecl    → type IDENTIFIER ('=' expr)? ';'
 * expression → assignment → logicOr → logicAnd → equality
 *            → comparison → term → factor → unary → call → primary
 * </pre>
 */
public class Parser {

    private final List<Token> tokens;
    private int current = 0;
    private final List<CompilerError> errors = new ArrayList<>();
    private final SymbolTable symbolTable = new SymbolTable();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // =========================================================================
    // API pública
    // =========================================================================

    /** Faz o parsing completo e retorna a lista de declarações de classe. */
    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            int before = current;
            Stmt stmt = classDecl();
            if (stmt != null) statements.add(stmt);
            // Proteção contra loop infinito: se nada foi consumido, avança
            if (current == before) advance();
        }
        return statements;
    }

    /** Retorna os erros sintáticos coletados durante o parsing. */
    public List<CompilerError> getErrors() {
        return errors;
    }

    /** Retorna a tabela de símbolos populada durante o parsing. */
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    // =========================================================================
    // Declarações de alto nível
    // =========================================================================

    private Stmt classDecl() {
        try {
            while (match(TokenType.PUBLIC, TokenType.STATIC)) {} // modificadores opcionais
            consume(TokenType.CLASS, "Esperado 'class'");
            Token name = consume(TokenType.IDENTIFIER, "Esperado nome da classe");
            consume(TokenType.LBRACE, "Esperado '{' após nome da classe");

            List<Stmt.MethodDecl> methods = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                int before = current;
                Stmt.MethodDecl method = methodDecl();
                if (method != null) methods.add(method);
                if (current == before) advance();
            }
            consume(TokenType.RBRACE, "Esperado '}' após corpo da classe");
            return new Stmt.ClassDecl(name, methods);
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    private Stmt.MethodDecl methodDecl() {
        try {
            while (match(TokenType.PUBLIC, TokenType.STATIC)) {} // modificadores opcionais

            // Tipo de retorno
            if (!match(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN, TokenType.VOID)) {
                throw error(peek(),
                        "Esperado tipo de retorno (int, double, boolean ou void), mas encontrou '"
                        + peek().lexeme + "'");
            }
            Token typeToken = previous();
            String returnType = typeToken.lexeme.toUpperCase();

            Token name = consume(TokenType.IDENTIFIER, "Esperado nome do método");
            consume(TokenType.LPAREN, "Esperado '(' após nome do método");

            // Parâmetros — cada parâmetro é registrado no escopo do método
            List<Stmt.Param> params = new ArrayList<>();
            symbolTable.enterScope();
            if (!check(TokenType.RPAREN)) {
                do {
                    if (!match(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN)) {
                        throw error(peek(),
                                "Esperado tipo do parâmetro (int, double ou boolean), mas encontrou '"
                                + peek().lexeme + "'");
                    }
                    Token paramType = previous();
                    Token paramName = consume(TokenType.IDENTIFIER, "Esperado nome do parâmetro");
                    params.add(new Stmt.Param(paramType.lexeme.toUpperCase(), paramName));
                    Symbol sym = symbolTable.declare(
                            paramType.lexeme.toUpperCase(), paramName.lexeme, null,
                            paramName.line, paramName.column);
                    paramName.symbolId = sym.id;
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "Esperado ')' após parâmetros");

            Stmt.Block body = block();
            symbolTable.exitScope();

            List<Stmt> bodyStmts = body != null ? body.statements : new ArrayList<>();
            return new Stmt.MethodDecl(name, returnType, params, bodyStmts, typeToken);
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    // =========================================================================
    // Comandos (statements)
    // =========================================================================

    private Stmt.Block block() {
        try {
            consume(TokenType.LBRACE, "Esperado '{'");
            symbolTable.enterScope();
            List<Stmt> statements = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                int before = current;
                Stmt stmt = statement();
                if (stmt != null) statements.add(stmt);
                if (current == before) advance();
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
            // Declaração de variável com tipo suportado
            if (check(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN)) return varDecl();

            // Tipo de Java não suportado (String, float, etc.) — mensagem clara
            if (check(TokenType.IDENTIFIER) && isUnsupportedType(peek().lexeme)) {
                return unsupportedTypeStmt();
            }

            if (check(TokenType.IF))     return ifStmt();
            if (check(TokenType.WHILE))  return whileStmt();
            if (check(TokenType.FOR))    return forStmt();
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
     * Trata declarações com tipos não suportados (ex: {@code String x = 5;}).
     * Emite uma mensagem clara e descarta tokens até o {@code ;} para não gerar erros em cascata.
     */
    private Stmt unsupportedTypeStmt() {
        Token typeToken = advance(); // consome o nome do tipo não suportado

        if (check(TokenType.IDENTIFIER)) {
            // Ex: String x = 10; — tipo errado mas com nome de variável presente
            error(typeToken, "Tipo '" + typeToken.lexeme
                    + "' não é suportado. Tipos suportados: int, double, boolean");
        } else {
            // Ex: String = 10; ou String; — tipo errado e nome ausente
            error(typeToken, "Tipo '" + typeToken.lexeme
                    + "' não é suportado e falta o nome da variável. Ex: int nome = valor");
        }

        // Recuperação: descarta até o próximo ';'
        while (!check(TokenType.SEMICOLON) && !isAtEnd()) advance();
        if (check(TokenType.SEMICOLON)) advance();
        return null;
    }

    /** Retorna {@code true} se {@code name} é um tipo Java que não suportamos. */
    private boolean isUnsupportedType(String name) {
        return switch (name.toLowerCase()) {
            case "string", "float", "long", "char", "byte", "short" -> true;
            default -> false;
        };
    }

    private Stmt.VarDecl varDecl() {
        if (!match(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN)) {
            throw error(peek(),
                    "Falta o tipo da variável (int, double ou boolean), mas encontrou '"
                    + peek().lexeme + "'");
        }
        Token typeToken = previous();
        Token name = consume(TokenType.IDENTIFIER, "Esperado nome de variável");

        Expr initializer = null;
        boolean hadError = false;

        if (match(TokenType.ASSIGN)) {
            initializer = expression();
        } else if (!check(TokenType.SEMICOLON)) {
            // Algo inesperado após o nome — provavelmente faltou o '='
            error(peek(), "Esperado '=' ou ';' após nome de variável");
            hadError = true;
            while (!check(TokenType.SEMICOLON) && !isAtEnd()) advance();
        }

        // Registra na tabela de símbolos ANTES do ';' para não perder a declaração
        Object value = (initializer instanceof Expr.Literal) ? ((Expr.Literal) initializer).value : null;
        Symbol symbol = symbolTable.declare(
                typeToken.lexeme.toUpperCase(), name.lexeme, value, name.line, name.column);
        name.symbolId = symbol.id;

        // Consome o ';' (já reportamos o erro acima se hadError == true)
        if (hadError) { if (check(TokenType.SEMICOLON)) advance(); }
        else          { expectSemicolon("Esperado ';'"); }

        return new Stmt.VarDecl(typeToken.lexeme.toUpperCase(), name, initializer, typeToken);
    }

    private Stmt ifStmt() {
        consume(TokenType.IF,     "Esperado 'if'");
        consume(TokenType.LPAREN, "Esperado '(' após 'if'");
        Expr condition = expression();
        consume(TokenType.RPAREN, "Esperado ')' após condição do if");
        Stmt thenBranch = statement();
        Stmt elseBranch = match(TokenType.ELSE) ? statement() : null;
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStmt() {
        consume(TokenType.WHILE,  "Esperado 'while'");
        consume(TokenType.LPAREN, "Esperado '(' após 'while'");
        Expr condition = expression();
        consume(TokenType.RPAREN, "Esperado ')' após condição do while");
        return new Stmt.While(condition, statement());
    }

    private Stmt forStmt() {
        consume(TokenType.FOR,    "Esperado 'for'");
        consume(TokenType.LPAREN, "Esperado '(' após 'for'");

        // Inicialização: varDecl, exprStmt ou ';' vazio
        Stmt init = null;
        if      (check(TokenType.SEMICOLON))                              advance();
        else if (check(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN)) init = varDecl();
        else                                                               init = exprStmt();

        Expr condition = check(TokenType.SEMICOLON) ? null : expression();
        consume(TokenType.SEMICOLON, "Esperado ';' após condição do for");

        Expr increment = check(TokenType.RPAREN) ? null : expression();
        consume(TokenType.RPAREN, "Esperado ')' após incremento do for");

        return new Stmt.For(init, condition, increment, statement());
    }

    private Stmt returnStmt() {
        Token keyword = consume(TokenType.RETURN, "Esperado 'return'");
        Expr value = check(TokenType.SEMICOLON) ? null : expression();
        expectSemicolon("Esperado ';'");
        return new Stmt.Return(value, keyword);
    }

    private Stmt printStmt() {
        consume(TokenType.SYSTEM,    "Esperado 'System'");
        consume(TokenType.DOT,       "Esperado '.' após 'System'");
        consume(TokenType.OUT,       "Esperado 'out'");
        consume(TokenType.DOT,       "Esperado '.' após 'out'");
        Token printlnToken = consume(TokenType.PRINTLN, "Esperado 'println'");
        consume(TokenType.LPAREN,    "Esperado '(' após 'println'");
        Expr expr = expression();
        consume(TokenType.RPAREN,    "Esperado ')' após expressão do println");
        expectSemicolon("Esperado ';'");
        return new Stmt.Print(expr, printlnToken);
    }

    private Stmt exprStmt() {
        Expr expr = expression();
        expectSemicolon("Esperado ';'");
        return new Stmt.ExprStmt(expr);
    }

    // =========================================================================
    // Expressões (precedência crescente de cima para baixo)
    // =========================================================================

    private Expr expression() {
        return assignment();
    }

    /** assignment → logicOr ('=' assignment)? */
    private Expr assignment() {
        Expr expr = logicOr();
        if (match(TokenType.ASSIGN)) {
            Token equals = previous();
            Expr value = assignment(); // associatividade à direita
            if (expr instanceof Expr.Variable) {
                return new Expr.Assign(((Expr.Variable) expr).name, value);
            }
            error(equals, "Atribuição inválida");
        }
        return expr;
    }

    /** logicOr → logicAnd ('||' logicAnd)* */
    private Expr logicOr() {
        return parseBinary(this::logicAnd, TokenType.OR);
    }

    /** logicAnd → equality ('&&' equality)* */
    private Expr logicAnd() {
        return parseBinary(this::equality, TokenType.AND);
    }

    /** equality → comparison (('==' | '!=') comparison)* */
    private Expr equality() {
        return parseBinary(this::comparison, TokenType.EQUALS, TokenType.NOT_EQUALS);
    }

    /** comparison → term (('<' | '<=' | '>' | '>=') term)* */
    private Expr comparison() {
        return parseBinary(this::term,
                TokenType.LESS, TokenType.LESS_EQUAL,
                TokenType.GREATER, TokenType.GREATER_EQUAL);
    }

    /** term → factor (('+' | '-') factor)* */
    private Expr term() {
        return parseBinary(this::factor, TokenType.PLUS, TokenType.MINUS);
    }

    /** factor → unary (('*' | '/' | '%') unary)* */
    private Expr factor() {
        return parseBinary(this::unary, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT);
    }

    /**
     * Helper para regras de expressão binárias com left-associatividade.
     * Elimina a repetição de while + match + new Binary.
     */
    @FunctionalInterface
    private interface ExprSupplier { Expr get(); }

    private Expr parseBinary(ExprSupplier operand, TokenType... ops) {
        Expr expr = operand.get();
        while (match(ops)) {
            Token operator = previous();
            expr = new Expr.Binary(expr, operator, operand.get());
        }
        return expr;
    }

    /** unary → ('!' | '-') unary | call */
    private Expr unary() {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            Token operator = previous();
            return new Expr.Unary(operator, unary());
        }
        return call();
    }

    /** call → primary ('(' args? ')')? — chamada de método simples */
    private Expr call() {
        Expr expr = primary();
        if (expr instanceof Expr.Variable && match(TokenType.LPAREN)) {
            Token callee = ((Expr.Variable) expr).name;
            List<Expr> args = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                do { args.add(expression()); } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "Esperado ')' após argumentos");
            expr = new Expr.Call(callee, args);
        }
        return expr;
    }

    /** primary → NUMBER | STRING | true | false | IDENTIFIER | '(' expr ')' */
    private Expr primary() {
        if (match(TokenType.NUMBER)) {
            Token t = previous();
            return new Expr.Literal(t.literal, t);
        }
        if (match(TokenType.STRING)) {
            Token t = previous();
            return new Expr.Literal(t.literal, t);
        }
        if (match(TokenType.TRUE)) {
            return new Expr.Literal(Boolean.TRUE, previous());
        }
        if (match(TokenType.FALSE)) {
            return new Expr.Literal(Boolean.FALSE, previous());
        }
        if (match(TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous());
        }
        if (match(TokenType.LPAREN)) {
            Expr expr = expression();
            consume(TokenType.RPAREN, "Esperado ')' após expressão");
            return new Expr.Grouping(expr);
        }
        throw error(peek(), "Esperado expressão, mas encontrou '" + peek().lexeme + "'");
    }

    // =========================================================================
    // Helpers de consumo de tokens
    // =========================================================================

    /** Consome o token atual se for do tipo esperado; lança {@link ParseError} caso contrário. */
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message + ", mas encontrou '" + peek().lexeme + "'");
    }

    /**
     * Consome um {@code ;} sem lançar exceção.
     * Se faltar, registra o erro apontando para o final do último token
     * (posição onde o {@code ;} deveria estar).
     */
    private void expectSemicolon(String message) {
        if (check(TokenType.SEMICOLON)) {
            advance();
            return;
        }
        Token last = previous();
        errors.add(new CompilerError(
                last.line, last.column + last.lexeme.length(),
                message, CompilerError.ErrorType.SYNTACTIC));
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type == type;
    }

    private boolean check(TokenType... types) {
        for (TokenType t : types) { if (check(t)) return true; }
        return false;
    }

    private boolean match(TokenType... types) {
        for (TokenType t : types) {
            if (check(t)) { advance(); return true; }
        }
        return false;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private Token peek()     { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }
    private boolean isAtEnd(){ return peek().type == TokenType.EOF; }

    // =========================================================================
    // Tratamento e recuperação de erros
    // =========================================================================

    /** Registra um erro sintático e retorna uma {@link ParseError} para ser lançada. */
    private ParseError error(Token token, String message) {
        errors.add(new CompilerError(token.line, token.column, message,
                CompilerError.ErrorType.SYNTACTIC));
        return new ParseError();
    }

    /**
     * Recuperação panic mode: avança até um ponto de sincronização seguro
     * (início de statement, fechamento de bloco, ou fim de arquivo).
     *
     * <p>Não pula o token atual cegamente — ele pode já ser um ponto de
     * sincronização (ex: {@code int} logo após um {@code ;} ausente).</p>
     */
    private void synchronize() {
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return;
            switch (peek().type) {
                case CLASS, PUBLIC, STATIC,
                     INT, DOUBLE, BOOLEAN, VOID,
                     IF, WHILE, FOR, RETURN,
                     RBRACE -> { return; }
            }
            advance();
        }
    }

    /** Exceção interna usada para interromper o parsing ao encontrar um erro sintático. */
    private static class ParseError extends RuntimeException {}
}
