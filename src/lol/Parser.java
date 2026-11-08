package lol;

import java.util.ArrayList;
import java.util.List;

/*
 * Parser — analisador sintático (segunda fase do compilador).
 *
 * O Parser recebe a lista de tokens produzida pelo Lexer e verifica se eles
 * estão na ordem correta de acordo com as regras da gramática da linguagem.
 * Ao mesmo tempo, constrói a AST (Árvore Sintática Abstrata) — uma representação
 * em árvore da estrutura do programa, que será usada nas fases seguintes.
 *
 * Técnica: Recursive Descent (Descida Recursiva)
 *   Cada regra da gramática vira um método Java. Os métodos se chamam
 *   mutuamente seguindo a estrutura da gramática. Por exemplo:
 *     program → classDecl → methodDecl → block → statement → varDecl → expression
 *
 * Recuperação de erros: Panic Mode
 *   Quando um erro é encontrado, o parser não para. Ele registra o erro e chama
 *   synchronize() para descartar tokens até um ponto seguro (início de um novo
 *   statement). Assim é possível reportar vários erros por compilação.
 *
 * Tabela de símbolos:
 *   Durante o parsing de declarações de variáveis (varDecl) e parâmetros
 *   (methodDecl), o Parser popula a tabela de símbolos. Isso garante que o
 *   SemanticAnalyzer já encontra as variáveis registradas quando as analisa.
 *
 * Gramática suportada (simplificada):
 *   program    → classDecl*
 *   classDecl  → modifier* CLASS IDENTIFIER '{' methodDecl* '}'
 *   methodDecl → modifier* type IDENTIFIER '(' params? ')' block
 *   params     → type IDENTIFIER (',' type IDENTIFIER)*
 *   block      → '{' stmt* '}'
 *   stmt       → varDecl | ifStmt | whileStmt | forStmt | returnStmt
 *              | printStmt | block | exprStmt
 *   varDecl    → type IDENTIFIER ('=' expr)? ';'
 *   expression → assignment → logicOr → logicAnd → equality
 *              → comparison → term → factor → unary → call → primary
 */
public class Parser {

    private final List<Token> tokens; // lista de tokens recebida do Lexer
    private int current = 0;          // índice do próximo token a ser lido
    private final List<CompilerError> errors = new ArrayList<>();
    private final SymbolTable symbolTable = new SymbolTable();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // =========================================================================
    // API pública
    // =========================================================================

    /*
     * parse() — ponto de entrada: faz o parsing do programa completo.
     *
     * Um programa é uma sequência de declarações de classe. O método tenta
     * parsear cada uma até chegar ao EOF. A proteção "if (current == before)"
     * evita loop infinito: se uma tentativa de parsing não consumiu nenhum
     * token (falhou silenciosamente), avança forçosamente para não travar.
     */
    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            int before = current;
            Stmt stmt = classDecl();
            if (stmt != null) statements.add(stmt);
            if (current == before) advance(); // proteção contra loop infinito
        }
        return statements;
    }

    // Retorna os erros sintáticos coletados durante o parsing.
    public List<CompilerError> getErrors() {
        return errors;
    }

    // Retorna a tabela de símbolos populada durante o parsing.
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    // =========================================================================
    // Declarações de alto nível
    // =========================================================================

    /*
     * classDecl() — parseia uma declaração de classe.
     *
     * Regra: modifier* CLASS IDENTIFIER '{' methodDecl* '}'
     *
     * O try/catch captura ParseError (lançado por consume() ou error()).
     * Se ocorrer um erro, synchronize() descarta tokens até um ponto seguro
     * e retorna null (o método não foi reconhecido corretamente).
     */
    private Stmt classDecl() {
        try {
            while (match(TokenType.PUBLIC, TokenType.STATIC)) {} // consome modificadores opcionais
            consume(TokenType.CLASS, "Esperado 'class'");
            Token name = consume(TokenType.IDENTIFIER, "Esperado nome da classe");
            consume(TokenType.LBRACE, "Esperado '{' após nome da classe");

            List<Stmt.MethodDecl> methods = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                int before = current;
                Stmt.MethodDecl method = methodDecl();
                if (method != null) methods.add(method);
                if (current == before) advance(); // proteção contra loop infinito
            }
            consume(TokenType.RBRACE, "Esperado '}' após corpo da classe");
            return new Stmt.ClassDecl(name, methods);
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    /*
     * methodDecl() — parseia a declaração de um método.
     *
     * Regra: modifier* type IDENTIFIER '(' params? ')' block
     *
     * Os parâmetros são registrados na tabela de símbolos dentro de um novo
     * escopo criado aqui (enterScope). O corpo do método é parseado por block(),
     * que cria outro escopo internamente.
     */
    private Stmt.MethodDecl methodDecl() {
        try {
            while (match(TokenType.PUBLIC, TokenType.STATIC)) {} // modificadores opcionais

            // Tipo de retorno — obrigatório
            if (!match(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN, TokenType.VOID)) {
                throw error(peek(),
                        "Esperado tipo de retorno (int, double, boolean ou void), mas encontrou '"
                        + peek().lexeme + "'");
            }
            Token typeToken = previous();
            String returnType = typeToken.lexeme.toUpperCase();

            Token name = consume(TokenType.IDENTIFIER, "Esperado nome do método");
            consume(TokenType.LPAREN, "Esperado '(' após nome do método");

            // Parâmetros — cada parâmetro é registrado na tabela de símbolos
            List<Stmt.Param> params = new ArrayList<>();
            symbolTable.enterScope(); // abre escopo do método para os parâmetros
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
                    // Registra o parâmetro na tabela e vincula o ID ao token
                    Symbol sym = symbolTable.declare(
                            paramType.lexeme.toUpperCase(), paramName.lexeme, null,
                            paramName.line, paramName.column);
                    paramName.symbolId = sym.id;
                } while (match(TokenType.COMMA)); // continua enquanto houver vírgula
            }
            consume(TokenType.RPAREN, "Esperado ')' após parâmetros");

            Stmt.Block body = block(); // parseia o corpo entre { }
            symbolTable.exitScope();  // fecha o escopo do método

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

    /*
     * block() — parseia um bloco de comandos entre chaves { }.
     *
     * Um bloco cria um novo escopo na tabela de símbolos. Variáveis declaradas
     * dentro do bloco existem apenas até o '}'.
     */
    private Stmt.Block block() {
        try {
            consume(TokenType.LBRACE, "Esperado '{'");
            symbolTable.enterScope(); // variáveis deste bloco ficam neste escopo
            List<Stmt> statements = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                int before = current;
                Stmt stmt = statement();
                if (stmt != null) statements.add(stmt);
                if (current == before) advance(); // proteção contra loop infinito
            }
            consume(TokenType.RBRACE, "Esperado '}'");
            symbolTable.exitScope(); // descarta as variáveis do bloco
            return new Stmt.Block(statements);
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    /*
     * statement() — decide qual tipo de comando parsear baseado no token atual.
     *
     * Olha o próximo token (sem consumi-lo) e chama o método correspondente.
     * Se nenhum caso específico for reconhecido, trata como expressão (exprStmt).
     */
    private Stmt statement() {
        try {
            // Declaração de variável com tipo suportado (int, double, boolean)
            if (check(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN)) return varDecl();

            // Detecta tipos Java não suportados (String, float, etc.) para dar mensagem clara
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

    /*
     * unsupportedTypeStmt() — trata declarações com tipos não suportados.
     *
     * Quando o programador usa um tipo Java que nossa linguagem não suporta
     * (String, float, long, etc.), em vez de mostrar uma mensagem genérica
     * de "variável não declarada", este método registra um erro específico
     * e descarta os tokens até o próximo ';' para não gerar erros em cascata.
     */
    private Stmt unsupportedTypeStmt() {
        Token typeToken = advance(); // consome o nome do tipo não suportado (ex: "String")

        if (check(TokenType.IDENTIFIER)) {
            // Caso: "String x = 10;" — tem nome de variável mas tipo errado
            error(typeToken, "Tipo '" + typeToken.lexeme
                    + "' não é suportado. Tipos suportados: int, double, boolean");
        } else {
            // Caso: "String = 10;" ou "String;" — tipo errado e sem nome de variável
            error(typeToken, "Tipo '" + typeToken.lexeme
                    + "' não é suportado e falta o nome da variável. Ex: int nome = valor");
        }

        // Descarta todos os tokens até o ';' para evitar erros em cascata
        while (!check(TokenType.SEMICOLON) && !isAtEnd()) advance();
        if (check(TokenType.SEMICOLON)) advance();
        return null;
    }

    /*
     * isUnsupportedType() — verifica se um nome é um tipo Java que não suportamos.
     * Usado em statement() para reconhecer e tratar esse caso com mensagem clara.
     */
    private boolean isUnsupportedType(String name) {
        return switch (name.toLowerCase()) {
            case "string", "float", "long", "char", "byte", "short" -> true;
            default -> false;
        };
    }

    /*
     * varDecl() — parseia uma declaração de variável.
     *
     * Regra: type IDENTIFIER ('=' expression)? ';'
     *
     * A variável é registrada na tabela de símbolos ANTES de consumir o ';'.
     * Isso é importante: mesmo que o ';' esteja faltando, a variável fica
     * registrada e os usos subsequentes dela não geram erros falsos de
     * "variável não declarada".
     *
     * Se após o nome houver algo diferente de '=' e ';', provavelmente
     * o '=' foi esquecido — reporta erro específico e recupera até o ';'.
     */
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
            // Há '=' → parseia a expressão inicializadora
            initializer = expression();
        } else if (!check(TokenType.SEMICOLON)) {
            // Não há '=' nem ';' → provavelmente faltou o '='
            error(peek(), "Esperado '=' ou ';' após nome de variável");
            hadError = true;
            while (!check(TokenType.SEMICOLON) && !isAtEnd()) advance(); // recupera até ';'
        }

        // Registra na tabela de símbolos ANTES de consumir o ';'
        // para garantir que a variável existe mesmo se o ';' estiver faltando
        Object value = (initializer instanceof Expr.Literal) ? ((Expr.Literal) initializer).value : null;
        Symbol symbol = symbolTable.declare(
                typeToken.lexeme.toUpperCase(), name.lexeme, value, name.line, name.column);
        name.symbolId = symbol.id; // vincula o token da variável ao ID na tabela

        // Consome o ';' — se já reportamos erro acima, não reporta de novo
        if (hadError) { if (check(TokenType.SEMICOLON)) advance(); }
        else          { expectSemicolon("Esperado ';'"); }

        return new Stmt.VarDecl(typeToken.lexeme.toUpperCase(), name, initializer, typeToken);
    }

    /*
     * ifStmt() — parseia um comando if/else.
     * Regra: IF '(' expression ')' statement (ELSE statement)?
     */
    private Stmt ifStmt() {
        consume(TokenType.IF,     "Esperado 'if'");
        consume(TokenType.LPAREN, "Esperado '(' após 'if'");
        Expr condition = expression();
        consume(TokenType.RPAREN, "Esperado ')' após condição do if");
        Stmt thenBranch = statement();
        // O else é opcional — só parseia se o próximo token for ELSE
        Stmt elseBranch = match(TokenType.ELSE) ? statement() : null;
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    /*
     * whileStmt() — parseia um laço while.
     * Regra: WHILE '(' expression ')' statement
     */
    private Stmt whileStmt() {
        consume(TokenType.WHILE,  "Esperado 'while'");
        consume(TokenType.LPAREN, "Esperado '(' após 'while'");
        Expr condition = expression();
        consume(TokenType.RPAREN, "Esperado ')' após condição do while");
        return new Stmt.While(condition, statement());
    }

    /*
     * forStmt() — parseia um laço for com três partes opcionais.
     * Regra: FOR '(' (varDecl | exprStmt | ';') expression? ';' expression? ')' statement
     *
     * Cada uma das três partes pode estar ausente:
     *   for (;;) é válido (loop infinito)
     *   for (int i = 0; i < 10; i = i + 1) é o caso completo
     */
    private Stmt forStmt() {
        consume(TokenType.FOR,    "Esperado 'for'");
        consume(TokenType.LPAREN, "Esperado '(' após 'for'");

        // Inicialização: pode ser declaração, expressão ou vazia
        Stmt init = null;
        if      (check(TokenType.SEMICOLON))                                advance(); // vazia
        else if (check(TokenType.INT, TokenType.DOUBLE, TokenType.BOOLEAN)) init = varDecl();
        else                                                                 init = exprStmt();

        // Condição: opcional — null significa loop infinito
        Expr condition = check(TokenType.SEMICOLON) ? null : expression();
        consume(TokenType.SEMICOLON, "Esperado ';' após condição do for");

        // Incremento: opcional
        Expr increment = check(TokenType.RPAREN) ? null : expression();
        consume(TokenType.RPAREN, "Esperado ')' após incremento do for");

        return new Stmt.For(init, condition, increment, statement());
    }

    /*
     * returnStmt() — parseia um comando return.
     * Regra: RETURN expression? ';'
     * O valor é opcional (return; para métodos void).
     */
    private Stmt returnStmt() {
        Token keyword = consume(TokenType.RETURN, "Esperado 'return'");
        Expr value = check(TokenType.SEMICOLON) ? null : expression();
        expectSemicolon("Esperado ';'");
        return new Stmt.Return(value, keyword);
    }

    /*
     * printStmt() — parseia System.out.println(expressão).
     *
     * System.out.println é tratado como uma construção especial: o parser
     * espera exatamente a sequência "System . out . println ( expr )".
     */
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

    /*
     * exprStmt() — parseia uma expressão usada como comando.
     * Regra: expression ';'
     * Usado para atribuições soltas: x = x - 1;
     */
    private Stmt exprStmt() {
        Expr expr = expression();
        expectSemicolon("Esperado ';'");
        return new Stmt.ExprStmt(expr);
    }

    // =========================================================================
    // Expressões — hierarquia de precedência (maior precedência = fim da lista)
    //
    // A ordem dos métodos abaixo define a precedência dos operadores:
    // expression (mais fraca) → assignment → logicOr → logicAnd → equality
    //   → comparison → term → factor → unary → call → primary (mais forte)
    //
    // Operadores de menor precedência ficam no topo. Cada método parseia
    // seu próprio operador e chama o método do nível abaixo para os operandos.
    // =========================================================================

    private Expr expression() {
        return assignment();
    }

    /*
     * assignment() — parseia uma atribuição: variável = expressão
     *
     * A atribuição tem associatividade à direita: x = y = 5 é válido.
     * Por isso, após o '=', o método chama a si mesmo recursivamente
     * em vez de chamar logicOr() diretamente.
     *
     * Se a expressão à esquerda não for uma variável (ex: 5 = 3),
     * registra erro de "atribuição inválida".
     */
    private Expr assignment() {
        Expr expr = logicOr();
        if (match(TokenType.ASSIGN)) {
            Token equals = previous();
            Expr value = assignment(); // recursão à direita (associatividade à direita)
            if (expr instanceof Expr.Variable) {
                return new Expr.Assign(((Expr.Variable) expr).name, value);
            }
            error(equals, "Atribuição inválida");
        }
        return expr;
    }

    /*
     * logicOr() — parseia "||" (ou lógico)
     * Precedência: mais fraca que &&, mais forte que atribuição.
     */
    private Expr logicOr() {
        return parseBinary(this::logicAnd, TokenType.OR);
    }

    /*
     * logicAnd() — parseia "&&" (e lógico)
     * Precedência: mais forte que ||, mais fraca que comparações de igualdade.
     */
    private Expr logicAnd() {
        return parseBinary(this::equality, TokenType.AND);
    }

    /*
     * equality() — parseia "==" e "!=" (igualdade e diferença)
     * Precedência: mais forte que &&, mais fraca que comparações relacionais.
     */
    private Expr equality() {
        return parseBinary(this::comparison, TokenType.EQUALS, TokenType.NOT_EQUALS);
    }

    /*
     * comparison() — parseia "<", "<=", ">", ">=" (comparações relacionais)
     * Precedência: mais forte que ==, mais fraca que soma/subtração.
     */
    private Expr comparison() {
        return parseBinary(this::term,
                TokenType.LESS, TokenType.LESS_EQUAL,
                TokenType.GREATER, TokenType.GREATER_EQUAL);
    }

    /*
     * term() — parseia "+" e "-" (adição e subtração)
     * Precedência: mais forte que comparações, mais fraca que multiplicação.
     */
    private Expr term() {
        return parseBinary(this::factor, TokenType.PLUS, TokenType.MINUS);
    }

    /*
     * factor() — parseia "*", "/" e "%" (multiplicação, divisão, módulo)
     * Precedência: mais forte que soma/subtração, mais fraca que operadores unários.
     */
    private Expr factor() {
        return parseBinary(this::unary, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT);
    }

    /*
     * parseBinary() — helper para parsear expressões binárias com associatividade à esquerda.
     *
     * Todos os métodos acima (logicOr até factor) seguem o mesmo padrão:
     *   1. Parseia o operando esquerdo (chamando o nível de maior precedência)
     *   2. Enquanto houver um dos operadores esperados, consome e parseia o direito
     *   3. Cria um nó Binary agrupando left op right
     *
     * Este helper elimina a repetição desse padrão.
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

    /*
     * unary() — parseia operadores unários "!" e "-" (negação lógica e aritmética).
     *
     * É recursivo: "!!flag" parseia o primeiro "!" e chama unary() novamente
     * para parsear o segundo "!". Quando não há operador unário, vai para call().
     */
    private Expr unary() {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            Token operator = previous();
            return new Expr.Unary(operator, unary()); // recursão para múltiplos unários
        }
        return call();
    }

    /*
     * call() — parseia uma chamada de método simples: nome(args)
     *
     * Primeiro parseia uma expressão primária. Se o próximo token for '(',
     * trata como chamada de método. Os argumentos são separados por vírgulas.
     */
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

    /*
     * primary() — parseia os átomos das expressões (valores e agrupamentos).
     *
     * Este é o nível de maior precedência. Trata:
     *   - Literais: números, strings, true, false
     *   - Identificadores: nomes de variáveis
     *   - Expressões agrupadas entre parênteses: (expr)
     *
     * Se nenhum desses for reconhecido, lança ParseError.
     */
    private Expr primary() {
        if (match(TokenType.NUMBER)) {
            Token t = previous();
            return new Expr.Literal(t.literal, t); // literal numérico
        }
        if (match(TokenType.STRING)) {
            Token t = previous();
            return new Expr.Literal(t.literal, t); // literal string
        }
        if (match(TokenType.TRUE)) {
            return new Expr.Literal(Boolean.TRUE, previous()); // literal true
        }
        if (match(TokenType.FALSE)) {
            return new Expr.Literal(Boolean.FALSE, previous()); // literal false
        }
        if (match(TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous()); // referência a variável
        }
        if (match(TokenType.LPAREN)) {
            Expr expr = expression(); // parseia a expressão interna
            consume(TokenType.RPAREN, "Esperado ')' após expressão");
            return new Expr.Grouping(expr); // expressão agrupada por parênteses
        }
        throw error(peek(), "Esperado expressão, mas encontrou '" + peek().lexeme + "'");
    }

    // =========================================================================
    // Helpers de consumo de tokens
    // =========================================================================

    /*
     * consume() — exige que o próximo token seja de um tipo específico.
     *
     * Se o token atual for do tipo esperado, consome-o e retorna.
     * Caso contrário, lança ParseError com a mensagem de erro informada.
     * Usado quando um token é obrigatório pela gramática (ex: o ';' em varDecl).
     */
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message + ", mas encontrou '" + peek().lexeme + "'");
    }

    /*
     * expectSemicolon() — consome o ';' sem lançar exceção.
     *
     * Diferente de consume(), este método não interrompe o parsing se o ';'
     * estiver faltando. Em vez disso, registra o erro apontando para o final
     * do último token consumido (onde o ';' deveria estar), e continua.
     * Isso evita que a falta de ';' gere erros em cascata nas linhas seguintes.
     */
    private void expectSemicolon(String message) {
        if (check(TokenType.SEMICOLON)) {
            advance();
            return;
        }
        // Aponta o erro para logo após o último token (onde o ';' deveria estar)
        Token last = previous();
        errors.add(new CompilerError(
                last.line, last.column + last.lexeme.length(),
                message, CompilerError.ErrorType.SYNTACTIC));
    }

    /*
     * check() — verifica o tipo do token atual SEM consumi-lo.
     * Retorna false se chegou ao fim do arquivo.
     */
    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type == type;
    }

    // Variante que aceita múltiplos tipos — retorna true se qualquer um bater.
    private boolean check(TokenType... types) {
        for (TokenType t : types) { if (check(t)) return true; }
        return false;
    }

    /*
     * match() — consome o token atual se for de um dos tipos esperados.
     *
     * Combina check() com advance(): se o tipo bater, consome e retorna true;
     * caso contrário, não avança e retorna false.
     * Usado quando um token é opcional (ex: o '=' em varDecl).
     */
    private boolean match(TokenType... types) {
        for (TokenType t : types) {
            if (check(t)) { advance(); return true; }
        }
        return false;
    }

    // Avança para o próximo token e retorna o que acabou de ser consumido.
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    // Retorna o token atual sem consumi-lo.
    private Token peek()     { return tokens.get(current); }

    // Retorna o token que acabou de ser consumido (o anterior ao atual).
    private Token previous() { return tokens.get(current - 1); }

    // Verifica se chegou ao fim da lista de tokens (token EOF).
    private boolean isAtEnd(){ return peek().type == TokenType.EOF; }

    // =========================================================================
    // Tratamento e recuperação de erros
    // =========================================================================

    /*
     * error() — registra um erro sintático e retorna uma ParseError.
     *
     * O ParseError é lançado pelo chamador para interromper o parsing do
     * construto atual. O try/catch nos métodos de declaração captura isso,
     * chama synchronize() e tenta continuar.
     */
    private ParseError error(Token token, String message) {
        errors.add(new CompilerError(token.line, token.column, message,
                CompilerError.ErrorType.SYNTACTIC));
        return new ParseError();
    }

    /*
     * synchronize() — recuperação panic mode: descarta tokens até um ponto seguro.
     *
     * Chamado após um erro para tentar retomar o parsing. Avança pelos tokens
     * até encontrar um que provavelmente inicia um novo statement (int, if,
     * while, etc.) ou o fechamento de um bloco (}).
     *
     * Importante: NÃO pula o token atual cegamente. Se o token atual já é um
     * ponto de sincronização (ex: "int" logo após um ';' ausente), retorna
     * imediatamente sem descartar nada — evitando erros em cascata.
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

    /*
     * ParseError — exceção interna usada APENAS para interromper o parsing
     * quando um erro é detectado. Não carrega informação adicional; a
     * mensagem de erro já foi registrada em 'errors' por error().
     */
    private static class ParseError extends RuntimeException {}
}
