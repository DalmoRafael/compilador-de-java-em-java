package lol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analisador semântico.
 *
 * <p>Percorre a AST produzida pelo {@link Parser} e valida as seguintes regras:</p>
 * <ul>
 *   <li>Variável não declarada (com dica de tipo faltante)</li>
 *   <li>Declaração duplicada no mesmo escopo</li>
 *   <li>Incompatibilidade de tipos em atribuições e inicializadores</li>
 *   <li>Return com valor em método void</li>
 *   <li>Tipo de retorno incompatível com a assinatura do método</li>
 * </ul>
 *
 * <p>Além disso, vincula os tokens de uso (referências a variáveis) aos símbolos
 * correspondentes na tabela, populando {@link Token#symbolId}.</p>
 */
public class SemanticAnalyzer {

    private final List<CompilerError> errors = new ArrayList<>();
    private final SymbolTable symbolTable;

    /**
     * Pilha de escopos própria do analisador semântico.
     * Separada da tabela de símbolos para permitir o lookup correto por escopo.
     */
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();

    /** Tipo de retorno do método sendo analisado no momento. {@code null} fora de métodos. */
    private String currentReturnType = null;

    public SemanticAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        scopes.push(new HashMap<>()); // escopo global
    }

    /**
     * Analisa a AST completa e retorna a lista de erros semânticos encontrados.
     */
    public List<CompilerError> analyze(List<Stmt> ast) {
        for (Stmt stmt : ast) analyzeStmt(stmt);
        return errors;
    }

    // =========================================================================
    // Análise de statements
    // =========================================================================

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

    private void analyzeClassDecl(Stmt.ClassDecl classDecl) {
        for (Stmt.MethodDecl method : classDecl.methods) analyzeMethodDecl(method);
    }

    private void analyzeMethodDecl(Stmt.MethodDecl method) {
        String previousReturnType = currentReturnType;
        currentReturnType = method.returnType;

        enterScope();

        // Registra parâmetros no escopo do método
        for (Stmt.Param param : method.params) {
            if (param.name.symbolId != null) {
                Symbol sym = symbolTable.getById(param.name.symbolId);
                if (sym != null) currentScope().put(param.name.lexeme, sym);
            }
        }

        for (Stmt stmt : method.body) analyzeStmt(stmt);

        exitScope();
        currentReturnType = previousReturnType;
    }

    private void analyzeVarDecl(Stmt.VarDecl decl) {
        // Declaração duplicada no mesmo escopo
        if (isDeclaredInCurrentScope(decl.name.lexeme)) {
            addError(decl.name,
                    "Variável '" + decl.name.lexeme + "' já foi declarada neste escopo");
        }

        // Registra no escopo semântico usando o símbolo criado pelo parser
        if (decl.name.symbolId != null) {
            Symbol sym = symbolTable.getById(decl.name.symbolId);
            if (sym != null) currentScope().put(decl.name.lexeme, sym);
        }

        // Verifica compatibilidade do inicializador
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

    private void analyzeIf(Stmt.If stmt) {
        analyzeExpr(stmt.condition);
        analyzeStmt(stmt.thenBranch);
        analyzeStmt(stmt.elseBranch);
    }

    private void analyzeWhile(Stmt.While stmt) {
        analyzeExpr(stmt.condition);
        analyzeStmt(stmt.body);
    }

    private void analyzeFor(Stmt.For stmt) {
        enterScope();
        if (stmt.init      != null) analyzeStmt(stmt.init);
        if (stmt.condition != null) analyzeExpr(stmt.condition);
        if (stmt.increment != null) analyzeExpr(stmt.increment);
        analyzeStmt(stmt.body);
        exitScope();
    }

    private void analyzeReturn(Stmt.Return stmt) {
        if (currentReturnType == null) {
            addError(stmt.keyword, "Return fora de método");
            return;
        }
        if (stmt.value == null) return;

        String valueType = analyzeExpr(stmt.value);
        if ("VOID".equals(currentReturnType)) {
            addError(stmt.keyword, "Retorno de valor em método void");
        } else if (valueType != null && !isCompatible(currentReturnType, valueType)) {
            addError(stmt.keyword,
                    "Tipo de retorno incompatível: esperado " + currentReturnType
                    + " mas obteve " + valueType);
        }
    }

    private void analyzeBlock(Stmt.Block block) {
        enterScope();
        for (Stmt stmt : block.statements) analyzeStmt(stmt);
        exitScope();
    }

    // =========================================================================
    // Análise de expressões — retorna o tipo inferido ou null se desconhecido
    // =========================================================================

    private String analyzeExpr(Expr expr) {
        if (expr == null) return null;

        if (expr instanceof Expr.Literal) {
            return literalType(((Expr.Literal) expr).value);
        }

        if (expr instanceof Expr.Variable) {
            return analyzeVariable((Expr.Variable) expr);
        }

        if (expr instanceof Expr.Assign) {
            return analyzeAssign((Expr.Assign) expr);
        }

        if (expr instanceof Expr.Binary) {
            return analyzeBinary((Expr.Binary) expr);
        }

        if (expr instanceof Expr.Unary) {
            Expr.Unary un = (Expr.Unary) expr;
            String rightType = analyzeExpr(un.right);
            return un.operator.type == TokenType.NOT ? "BOOLEAN" : rightType;
        }

        if (expr instanceof Expr.Grouping) {
            return analyzeExpr(((Expr.Grouping) expr).expression);
        }

        if (expr instanceof Expr.Call) {
            for (Expr arg : ((Expr.Call) expr).args) analyzeExpr(arg);
            return null; // tipo de retorno de chamadas não é inferido
        }

        return null;
    }

    private String analyzeVariable(Expr.Variable expr) {
        Symbol sym = lookup(expr.name.lexeme);
        if (sym == null) {
            addError(expr.name,
                    "Variável '" + expr.name.lexeme + "' não foi declarada"
                    + " (faltou o tipo? ex: int " + expr.name.lexeme + ")");
            return null;
        }
        expr.name.symbolId = sym.id; // vincula o uso ao símbolo
        return sym.type;
    }

    private String analyzeAssign(Expr.Assign expr) {
        String valueType = analyzeExpr(expr.value);
        Symbol sym = lookup(expr.name.lexeme);
        if (sym == null) {
            addError(expr.name,
                    "Variável '" + expr.name.lexeme + "' não foi declarada"
                    + " (faltou o tipo? ex: int " + expr.name.lexeme + ")");
            return null;
        }
        expr.name.symbolId = sym.id;
        if (valueType != null && !isCompatible(sym.type, valueType)) {
            addError(expr.name,
                    "Tipos incompatíveis: não pode atribuir " + valueType
                    + " a variável '" + expr.name.lexeme + "' do tipo " + sym.type);
        }
        return sym.type;
    }

    private String analyzeBinary(Expr.Binary expr) {
        String leftType  = analyzeExpr(expr.left);
        String rightType = analyzeExpr(expr.right);
        String op = expr.operator.lexeme;

        // Operadores de comparação e lógicos → sempre BOOLEAN
        if (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=")
                || op.equals("==") || op.equals("!=")
                || op.equals("&&") || op.equals("||")) {
            return "BOOLEAN";
        }

        // Operadores aritméticos → DOUBLE se algum operando for DOUBLE, senão INT
        if ("DOUBLE".equals(leftType) || "DOUBLE".equals(rightType)) return "DOUBLE";
        return "INT";
    }

    // =========================================================================
    // Verificação de tipos
    // =========================================================================

    /**
     * Retorna o tipo de um valor literal, ou {@code null} se não reconhecido.
     */
    private String literalType(Object value) {
        if (value instanceof Integer) return "INT";
        if (value instanceof Double)  return "DOUBLE";
        if (value instanceof String)  return "STRING";
        if (value instanceof Boolean) return "BOOLEAN";
        return null;
    }

    /**
     * Verifica se {@code valueType} pode ser atribuído a uma variável do tipo {@code varType}.
     * Aceita widening de INT para DOUBLE.
     */
    private boolean isCompatible(String varType, String valueType) {
        if (varType == null || valueType == null) return true;
        if (varType.equals(valueType)) return true;
        return varType.equals("DOUBLE") && valueType.equals("INT"); // widening
    }

    // =========================================================================
    // Gerenciamento de escopos
    // =========================================================================

    private void enterScope()  { scopes.push(new HashMap<>()); }
    private void exitScope()   { scopes.pop(); }
    private Map<String, Symbol> currentScope() { return scopes.peek(); }

    private Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol sym = scope.get(name);
            if (sym != null) return sym;
        }
        return null;
    }

    private boolean isDeclaredInCurrentScope(String name) {
        return scopes.peek().containsKey(name);
    }

    // =========================================================================
    // Helper de erro
    // =========================================================================

    private void addError(Token token, String message) {
        errors.add(new CompilerError(token.line, token.column, message,
                CompilerError.ErrorType.SEMANTIC));
    }
}
