package lol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * SemanticAnalyzer — analisador semântico (terceira fase do compilador).
 *
 * O analisador semântico recebe a AST produzida pelo Parser e verifica
 * regras que a gramática não consegue capturar. Por exemplo, a gramática
 * permite "x = 5" sem saber se "x" foi declarado — é o analisador semântico
 * que verifica isso.
 *
 * Regras verificadas:
 *   1. Variável não declarada: uso de "x" sem "int x" antes
 *   2. Declaração duplicada: "int x = 5; int x = 10;" no mesmo bloco
 *   3. Tipos incompatíveis: "int x = "texto";" (STRING em variável INT)
 *   4. Return com valor em método void: método void não pode retornar valor
 *   5. Tipo de retorno incompatível: método int retornando uma String
 *
 * Além de verificar, o analisador também vincula cada uso de variável ao
 * seu símbolo na tabela: preenche token.symbolId para cada referência a
 * variável reconhecida.
 *
 * Escopos:
 *   O analisador mantém sua própria pilha de escopos (separada da tabela
 *   de símbolos do Parser) para saber quais variáveis estão visíveis no
 *   ponto atual do código. Ao entrar em um bloco { }, cria um novo escopo;
 *   ao sair }, remove o escopo.
 *
 * Inferência de tipos:
 *   analyzeExpr() retorna o tipo da expressão (como String: "INT", "DOUBLE",
 *   "BOOLEAN", "STRING") ou null se não for possível inferir.
 */
public class SemanticAnalyzer {

    private final List<CompilerError> errors = new ArrayList<>();
    private final SymbolTable symbolTable; // tabela populada pelo Parser

    // Pilha de escopos: cada escopo é um mapa nome → símbolo.
    // O topo é o escopo mais interno (bloco atual).
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();

    // Tipo de retorno do método sendo analisado no momento.
    // null quando estamos fora de qualquer método.
    private String currentReturnType = null;

    public SemanticAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        scopes.push(new HashMap<>()); // escopo global inicial
    }

    /*
     * analyze() — ponto de entrada: analisa a AST completa.
     * Percorre cada declaração de classe e retorna a lista de erros encontrados.
     */
    public List<CompilerError> analyze(List<Stmt> ast) {
        for (Stmt stmt : ast) analyzeStmt(stmt);
        return errors;
    }

    // =========================================================================
    // Análise de statements — cada método trata um tipo de nó da AST
    // =========================================================================

    /*
     * analyzeStmt() — despacha para o método correto baseado no tipo do nó.
     *
     * É um dispatcher: verifica o tipo real do Stmt e chama o método
     * especializado correspondente. Cada tipo de nó tem suas próprias
     * regras semânticas a verificar.
     */
    private void analyzeStmt(Stmt stmt) {
        if (stmt == null) return;

        if      (stmt instanceof Stmt.ClassDecl)  analyzeClassDecl((Stmt.ClassDecl) stmt);
        else if (stmt instanceof Stmt.MethodDecl) analyzeMethodDecl((Stmt.MethodDecl) stmt);
        else if (stmt instanceof Stmt.VarDecl)    analyzeVarDecl((Stmt.VarDecl) stmt);
        else if (stmt instanceof Stmt.If)         analyzeIf((Stmt.If) stmt);
        else if (stmt instanceof Stmt.While)      analyzeWhile((Stmt.While) stmt);
        else if (stmt instanceof Stmt.For)        analyzeFor((Stmt.For) stmt);
        else if (stmt instanceof Stmt.Return)     analyzeReturn((Stmt.Return) stmt);
        else if (stmt instanceof Stmt.Print)      analyzeExpr(((Stmt.Print) stmt).expression);
        else if (stmt instanceof Stmt.Block)      analyzeBlock((Stmt.Block) stmt);
        else if (stmt instanceof Stmt.ExprStmt)   analyzeExpr(((Stmt.ExprStmt) stmt).expression);
    }

    /*
     * analyzeClassDecl() — analisa uma declaração de classe.
     * Simplesmente percorre e analisa cada método da classe.
     */
    private void analyzeClassDecl(Stmt.ClassDecl classDecl) {
        for (Stmt.MethodDecl method : classDecl.methods) analyzeMethodDecl(method);
    }

    /*
     * analyzeMethodDecl() — analisa um método.
     *
     * Guarda o tipo de retorno do método em 'currentReturnType' para que
     * os comandos return dentro do corpo possam verificar compatibilidade.
     * Cria um novo escopo e registra os parâmetros nele antes de analisar
     * o corpo.
     */
    private void analyzeMethodDecl(Stmt.MethodDecl method) {
        String previousReturnType = currentReturnType; // salva o contexto anterior
        currentReturnType = method.returnType;         // entra no contexto deste método

        enterScope();

        // Registra os parâmetros no escopo do método
        // (o symbolId já foi preenchido pelo Parser durante a declaração)
        for (Stmt.Param param : method.params) {
            if (param.name.symbolId != null) {
                Symbol sym = symbolTable.getById(param.name.symbolId);
                if (sym != null) currentScope().put(param.name.lexeme, sym);
            }
        }

        // Analisa cada comando do corpo do método
        for (Stmt stmt : method.body) analyzeStmt(stmt);

        exitScope();
        currentReturnType = previousReturnType; // restaura o contexto anterior
    }

    /*
     * analyzeVarDecl() — analisa uma declaração de variável.
     *
     * Verifica:
     *   1. Se a variável já foi declarada neste mesmo escopo (duplicata)
     *   2. Se o tipo do inicializador é compatível com o tipo da variável
     *
     * Também registra a variável no escopo semântico para que usos
     * posteriores possam encontrá-la via lookup().
     */
    private void analyzeVarDecl(Stmt.VarDecl decl) {
        // Verifica declaração duplicada NO MESMO ESCOPO
        if (isDeclaredInCurrentScope(decl.name.lexeme)) {
            addError(decl.name,
                    "Variável '" + decl.name.lexeme + "' já foi declarada neste escopo");
        }

        // Registra a variável no escopo semântico usando o símbolo criado pelo Parser
        if (decl.name.symbolId != null) {
            Symbol sym = symbolTable.getById(decl.name.symbolId);
            if (sym != null) currentScope().put(decl.name.lexeme, sym);
        }

        // Verifica compatibilidade do inicializador (ex: int x = "texto" é erro)
        if (decl.initializer != null) {
            String initType = analyzeExpr(decl.initializer);
            if (initType != null && !isCompatible(decl.type, initType)) {
                addError(decl.name,
                        "Tipos incompatíveis: variável '" + decl.name.lexeme
                        + "' é do tipo " + decl.type
                        + ", mas o valor atribuído é " + initType);
            }
        }
    }

    /*
     * analyzeIf() — analisa um comando if/else.
     * Analisa a condição e os dois ramos (o else pode ser null).
     */
    private void analyzeIf(Stmt.If stmt) {
        analyzeExpr(stmt.condition);
        analyzeStmt(stmt.thenBranch);
        analyzeStmt(stmt.elseBranch); // analyzeStmt trata null com segurança
    }

    /*
     * analyzeWhile() — analisa um laço while.
     * Analisa a condição e o corpo do laço.
     */
    private void analyzeWhile(Stmt.While stmt) {
        analyzeExpr(stmt.condition);
        analyzeStmt(stmt.body);
    }

    /*
     * analyzeFor() — analisa um laço for.
     * Cria um novo escopo para a variável de controle declarada no init
     * (ex: "for (int i = 0; ...)") e analisa as três partes e o corpo.
     */
    private void analyzeFor(Stmt.For stmt) {
        enterScope(); // escopo para variável de controle do for
        if (stmt.init      != null) analyzeStmt(stmt.init);
        if (stmt.condition != null) analyzeExpr(stmt.condition);
        if (stmt.increment != null) analyzeExpr(stmt.increment);
        analyzeStmt(stmt.body);
        exitScope();
    }

    /*
     * analyzeReturn() — analisa um comando return.
     *
     * Verifica:
     *   1. Se estamos dentro de um método (currentReturnType != null)
     *   2. Se o método é void mas tem um valor de retorno
     *   3. Se o tipo do valor retornado é compatível com o tipo de retorno do método
     */
    private void analyzeReturn(Stmt.Return stmt) {
        if (currentReturnType == null) {
            addError(stmt.keyword, "Return fora de método");
            return;
        }
        if (stmt.value == null) return; // "return;" sem valor — sempre válido

        String valueType = analyzeExpr(stmt.value);
        if ("VOID".equals(currentReturnType)) {
            // Método void não pode retornar valor
            addError(stmt.keyword, "Retorno de valor em método void");
        } else if (valueType != null && !isCompatible(currentReturnType, valueType)) {
            // Tipo do valor retornado não bate com o tipo de retorno do método
            addError(stmt.keyword,
                    "Tipo de retorno incompatível: esperado " + currentReturnType
                    + " mas obteve " + valueType);
        }
    }

    /*
     * analyzeBlock() — analisa um bloco de comandos { }.
     * Cria um novo escopo para as variáveis declaradas dentro do bloco.
     */
    private void analyzeBlock(Stmt.Block block) {
        enterScope();
        for (Stmt stmt : block.statements) analyzeStmt(stmt);
        exitScope();
    }

    // =========================================================================
    // Análise de expressões — retorna o tipo inferido (ou null se desconhecido)
    // =========================================================================

    /*
     * analyzeExpr() — analisa uma expressão e retorna seu tipo.
     *
     * Percorre a árvore de expressão recursivamente. Cada tipo de expressão
     * tem regras diferentes e o método correspondente retorna o tipo resultante
     * (ex: "INT", "DOUBLE", "BOOLEAN", "STRING") ou null quando não é possível
     * determinar o tipo (ex: chamada de método, cujo retorno não é rastreado).
     */
    private String analyzeExpr(Expr expr) {
        if (expr == null) return null;

        if (expr instanceof Expr.Literal)  return literalType(((Expr.Literal) expr).value);
        if (expr instanceof Expr.Variable) return analyzeVariable((Expr.Variable) expr);
        if (expr instanceof Expr.Assign)   return analyzeAssign((Expr.Assign) expr);
        if (expr instanceof Expr.Binary)   return analyzeBinary((Expr.Binary) expr);
        if (expr instanceof Expr.Grouping) return analyzeExpr(((Expr.Grouping) expr).expression);

        if (expr instanceof Expr.Unary) {
            Expr.Unary un = (Expr.Unary) expr;
            String rightType = analyzeExpr(un.right);
            // "!" sempre resulta em BOOLEAN; "-" mantém o tipo do operando
            return un.operator.type == TokenType.NOT ? "BOOLEAN" : rightType;
        }

        if (expr instanceof Expr.Call) {
            // Analisa os argumentos da chamada para detectar erros dentro deles
            for (Expr arg : ((Expr.Call) expr).args) analyzeExpr(arg);
            return null; // tipo de retorno de chamadas não é rastreado nesta versão
        }

        return null;
    }

    /*
     * analyzeVariable() — analisa uma referência a variável (ex: usar "x" em expressão).
     *
     * Busca a variável na pilha de escopos (lookup). Se não encontrar,
     * reporta erro de variável não declarada com dica de que pode ter
     * faltado o tipo. Se encontrar, preenche token.symbolId para vincular
     * o uso ao símbolo na tabela.
     */
    private String analyzeVariable(Expr.Variable expr) {
        Symbol sym = lookup(expr.name.lexeme);
        if (sym == null) {
            addError(expr.name,
                    "Variável '" + expr.name.lexeme + "' não foi declarada"
                    + " (faltou o tipo? ex: int " + expr.name.lexeme + ")");
            return null;
        }
        expr.name.symbolId = sym.id; // vincula o token ao símbolo correspondente
        return sym.type;
    }

    /*
     * analyzeAssign() — analisa uma atribuição (ex: x = 5).
     *
     * Verifica se a variável foi declarada e se o tipo do valor atribuído
     * é compatível com o tipo da variável. Preenche token.symbolId.
     */
    private String analyzeAssign(Expr.Assign expr) {
        String valueType = analyzeExpr(expr.value); // infere o tipo do lado direito
        Symbol sym = lookup(expr.name.lexeme);
        if (sym == null) {
            addError(expr.name,
                    "Variável '" + expr.name.lexeme + "' não foi declarada"
                    + " (faltou o tipo? ex: int " + expr.name.lexeme + ")");
            return null;
        }
        expr.name.symbolId = sym.id; // vincula o token ao símbolo
        if (valueType != null && !isCompatible(sym.type, valueType)) {
            addError(expr.name,
                    "Tipos incompatíveis: não pode atribuir " + valueType
                    + " a variável '" + expr.name.lexeme + "' do tipo " + sym.type);
        }
        return sym.type;
    }

    /*
     * analyzeBinary() — analisa uma expressão binária e infere seu tipo.
     *
     * Operadores de comparação (< <= > >= == !=) e lógicos (&& ||) sempre
     * produzem BOOLEAN. Operadores aritméticos (+ - * / %) produzem DOUBLE
     * se algum operando for DOUBLE (promoção automática), ou INT caso contrário.
     */
    private String analyzeBinary(Expr.Binary expr) {
        String leftType  = analyzeExpr(expr.left);
        String rightType = analyzeExpr(expr.right);
        String op = expr.operator.lexeme;

        // Comparações e operadores lógicos sempre resultam em BOOLEAN
        if (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=")
                || op.equals("==") || op.equals("!=")
                || op.equals("&&") || op.equals("||")) {
            return "BOOLEAN";
        }

        // Aritméticos: DOUBLE se algum operando for DOUBLE (widening), senão INT
        if ("DOUBLE".equals(leftType) || "DOUBLE".equals(rightType)) return "DOUBLE";
        return "INT";
    }

    // =========================================================================
    // Verificação e inferência de tipos
    // =========================================================================

    /*
     * literalType() — retorna o tipo de um valor literal Java.
     * Usado em analyzeExpr() para inferir o tipo de Expr.Literal.
     */
    private String literalType(Object value) {
        if (value instanceof Integer) return "INT";
        if (value instanceof Double)  return "DOUBLE";
        if (value instanceof String)  return "STRING";
        if (value instanceof Boolean) return "BOOLEAN";
        return null;
    }

    /*
     * isCompatible() — verifica se um valor do tipo 'valueType' pode ser
     * atribuído a uma variável do tipo 'varType'.
     *
     * Regras:
     *   - Mesmo tipo sempre é compatível (INT → INT, DOUBLE → DOUBLE)
     *   - INT pode ser atribuído a DOUBLE (widening: 5 → 5.0)
     *   - Qualquer outra combinação diferente é incompatível
     */
    private boolean isCompatible(String varType, String valueType) {
        if (varType == null || valueType == null) return true; // dúvida → não reporta erro
        if (varType.equals(valueType)) return true;            // mesmo tipo
        return varType.equals("DOUBLE") && valueType.equals("INT"); // widening INT → DOUBLE
    }

    // =========================================================================
    // Gerenciamento de escopos
    // =========================================================================

    // Cria um novo escopo ao entrar em um bloco, método ou laço for
    private void enterScope()  { scopes.push(new HashMap<>()); }

    // Remove o escopo mais interno ao sair do bloco correspondente
    private void exitScope()   { scopes.pop(); }

    // Retorna o mapa do escopo atual (o mais interno)
    private Map<String, Symbol> currentScope() { return scopes.peek(); }

    /*
     * lookup() — busca uma variável nos escopos, do mais interno para o global.
     * Retorna null se a variável não foi declarada em nenhum escopo visível.
     */
    private Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol sym = scope.get(name);
            if (sym != null) return sym;
        }
        return null;
    }

    /*
     * isDeclaredInCurrentScope() — verifica se a variável existe APENAS
     * no escopo atual. Usado para detectar declarações duplicadas no mesmo bloco.
     */
    private boolean isDeclaredInCurrentScope(String name) {
        return scopes.peek().containsKey(name);
    }

    // =========================================================================
    // Helper de erro
    // =========================================================================

    /*
     * addError() — cria e registra um erro semântico na posição do token informado.
     * Centraliza a criação de CompilerError para manter o código limpo.
     */
    private void addError(Token token, String message) {
        errors.add(new CompilerError(token.line, token.column, message,
                CompilerError.ErrorType.SEMANTIC));
    }
}
