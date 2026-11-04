package lol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analisador semantico que percorre a AST validando regras semânticas:
 * - variavel nao declarada
 * - declaracao duplicada no mesmo escopo
 * - atribuicao com tipo incompativel
 * - return com valor em metodo void
 * Tambem vincula tokens de uso (Variable/Assign) aos simbolos da tabela.
 */
public class SemanticAnalyzer {
    private final List<CompilerError> errors = new ArrayList<>();
    private final SymbolTable symbolTable;
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private String currentMethodReturnType = null;

    public SemanticAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        scopes.push(new HashMap<>());
    }

    public List<CompilerError> analyze(List<Stmt> ast) {
        for (Stmt stmt : ast) {
            analyzeStmt(stmt);
        }
        return errors;
    }

    // === Statements ===

    private void analyzeStmt(Stmt stmt) {
        if (stmt == null) return;
        if (stmt instanceof Stmt.ClassDecl) {
            analyzeClassDecl((Stmt.ClassDecl) stmt);
        } else if (stmt instanceof Stmt.MethodDecl) {
            analyzeMethodDecl((Stmt.MethodDecl) stmt);
        } else if (stmt instanceof Stmt.VarDecl) {
            analyzeVarDecl((Stmt.VarDecl) stmt);
        } else if (stmt instanceof Stmt.If) {
            analyzeIf((Stmt.If) stmt);
        } else if (stmt instanceof Stmt.While) {
            analyzeWhile((Stmt.While) stmt);
        } else if (stmt instanceof Stmt.For) {
            analyzeFor((Stmt.For) stmt);
        } else if (stmt instanceof Stmt.Return) {
            analyzeReturn((Stmt.Return) stmt);
        } else if (stmt instanceof Stmt.Print) {
            analyzePrint((Stmt.Print) stmt);
        } else if (stmt instanceof Stmt.Block) {
            analyzeBlock((Stmt.Block) stmt);
        } else if (stmt instanceof Stmt.ExprStmt) {
            analyzeExpr(((Stmt.ExprStmt) stmt).expression);
        }
    }

    private void analyzeClassDecl(Stmt.ClassDecl classDecl) {
        for (Stmt.MethodDecl method : classDecl.methods) {
            analyzeMethodDecl(method);
        }
    }

    private void analyzeMethodDecl(Stmt.MethodDecl method) {
        String previousReturnType = currentMethodReturnType;
        currentMethodReturnType = method.returnType;

        enterScope();

        // Adiciona parametros ao escopo
        for (Stmt.Param param : method.params) {
            if (param.name.symbolId != null) {
                Symbol sym = symbolTable.getById(param.name.symbolId);
                if (sym != null) {
                    currentScope().put(param.name.lexeme, sym);
                }
            }
        }

        // Analisa corpo
        for (Stmt stmt : method.body) {
            analyzeStmt(stmt);
        }

        exitScope();
        currentMethodReturnType = previousReturnType;
    }

    private void analyzeVarDecl(Stmt.VarDecl decl) {
        // Verifica declaracao duplicada no escopo atual
        if (isDeclaredInCurrentScope(decl.name.lexeme)) {
            errors.add(new CompilerError(decl.name.line, decl.name.column,
                    "Variável '" + decl.name.lexeme + "' já foi declarada neste escopo",
                    CompilerError.ErrorType.SEMANTIC));
        }

        // Adiciona ao escopo atual usando o simbolo criado pelo parser
        if (decl.name.symbolId != null) {
            Symbol sym = symbolTable.getById(decl.name.symbolId);
            if (sym != null) {
                currentScope().put(decl.name.lexeme, sym);
            }
        }

        // Verifica tipo do inicializador
        if (decl.initializer != null) {
            String initType = analyzeExpr(decl.initializer);
            if (initType != null && !isCompatible(decl.type, initType)) {
                errors.add(new CompilerError(decl.name.line, decl.name.column,
                        "Tipos incompatíveis: variável '" + decl.name.lexeme
                                + "' é do tipo " + decl.type
                                + ", mas o valor atribuido é " + initType,
                        CompilerError.ErrorType.SEMANTIC));
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
        if (stmt.init != null) analyzeStmt(stmt.init);
        if (stmt.condition != null) analyzeExpr(stmt.condition);
        if (stmt.increment != null) analyzeExpr(stmt.increment);
        analyzeStmt(stmt.body);
        exitScope();
    }

    private void analyzeReturn(Stmt.Return stmt) {
        if (currentMethodReturnType == null) {
            errors.add(new CompilerError(stmt.keyword.line, stmt.keyword.column,
                    "Return fora de metodo",
                    CompilerError.ErrorType.SEMANTIC));
            return;
        }

        if (stmt.value != null) {
            String valueType = analyzeExpr(stmt.value);
            // return com valor em metodo void
            if ("VOID".equals(currentMethodReturnType)) {
                errors.add(new CompilerError(stmt.keyword.line, stmt.keyword.column,
                        "Retorno de valor em metodo void",
                        CompilerError.ErrorType.SEMANTIC));
            } else if (valueType != null && !isCompatible(currentMethodReturnType, valueType)) {
                errors.add(new CompilerError(stmt.keyword.line, stmt.keyword.column,
                        "Tipo de retorno incompatível: esperado " + currentMethodReturnType
                                + " mas obteve " + valueType,
                        CompilerError.ErrorType.SEMANTIC));
            }
        }
    }

    private void analyzePrint(Stmt.Print stmt) {
        analyzeExpr(stmt.expression);
    }

    private void analyzeBlock(Stmt.Block block) {
        enterScope();
        for (Stmt stmt : block.statements) {
            analyzeStmt(stmt);
        }
        exitScope();
    }

    // === Expressões ===

    private String analyzeExpr(Expr expr) {
        if (expr == null) return null;

        if (expr instanceof Expr.Literal) {
            Object value = ((Expr.Literal) expr).value;
            if (value instanceof Integer) return "INT";
            if (value instanceof Double) return "DOUBLE";
            if (value instanceof String) return "STRING";
            if (value instanceof Boolean) return "BOOLEAN";
            return null;
        }

        if (expr instanceof Expr.Variable) {
            Token name = ((Expr.Variable) expr).name;
            Symbol sym = lookup(name.lexeme);
            if (sym == null) {
                errors.add(new CompilerError(name.line, name.column,
                        "Variável '" + name.lexeme + "' não foi declarada"
                                + " (faltou o tipo? ex: int, " + name.lexeme + ")",
                        CompilerError.ErrorType.SEMANTIC));
                return null;
            }
            // Vincula o token de uso ao simbolo
            name.symbolId = sym.id;
            return sym.type;
        }

        if (expr instanceof Expr.Assign) {
            Expr.Assign assign = (Expr.Assign) expr;
            String valueType = analyzeExpr(assign.value);
            Symbol sym = lookup(assign.name.lexeme);
            if (sym == null) {
                errors.add(new CompilerError(assign.name.line, assign.name.column,
                        "Variável '" + assign.name.lexeme + "' não foi declarada"
                                + " (faltou o tipo? ex: int " + assign.name.lexeme + ")",
                        CompilerError.ErrorType.SEMANTIC));
                return null;
            }
            assign.name.symbolId = sym.id;
            if (valueType != null && !isCompatible(sym.type, valueType)) {
                errors.add(new CompilerError(assign.name.line, assign.name.column,
                        "Tipos incompatíveis: não pode atribuir " + valueType
                                + " a variável '" + assign.name.lexeme
                                + "' do tipo " + sym.type,
                        CompilerError.ErrorType.SEMANTIC));
            }
            return sym.type;
        }

        if (expr instanceof Expr.Binary) {
            Expr.Binary bin = (Expr.Binary) expr;
            String leftType = analyzeExpr(bin.left);
            String rightType = analyzeExpr(bin.right);
            String op = bin.operator.lexeme;

            // Operadores de comparacao retornam BOOLEAN
            if (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=")
                    || op.equals("==") || op.equals("!=")) {
                return "BOOLEAN";
            }
            // Operadores logicos retornam BOOLEAN
            if (op.equals("&&") || op.equals("||")) {
                return "BOOLEAN";
            }
            // Operadores aritmeticos
            if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/") || op.equals("%")) {
                if ("DOUBLE".equals(leftType) || "DOUBLE".equals(rightType)) return "DOUBLE";
                return "INT";
            }
            return null;
        }

        if (expr instanceof Expr.Unary) {
            Expr.Unary un = (Expr.Unary) expr;
            String rightType = analyzeExpr(un.right);
            if (un.operator.type == TokenType.NOT) return "BOOLEAN";
            if (un.operator.type == TokenType.MINUS) return rightType;
            return null;
        }

        if (expr instanceof Expr.Grouping) {
            return analyzeExpr(((Expr.Grouping) expr).expression);
        }

        if (expr instanceof Expr.Call) {
            Expr.Call call = (Expr.Call) expr;
            for (Expr arg : call.args) {
                analyzeExpr(arg);
            }
            return null;
        }

        return null;
    }

    // === Tipos ===

    private boolean isCompatible(String varType, String valueType) {
        if (varType == null || valueType == null) return true;
        if (varType.equals(valueType)) return true;
        // INT pode ser atribuido a DOUBLE (widening)
        if (varType.equals("DOUBLE") && valueType.equals("INT")) return true;
        return false;
    }

    // === Escopos ===

    private void enterScope() {
        scopes.push(new HashMap<>());
    }

    private void exitScope() {
        scopes.pop();
    }

    private Map<String, Symbol> currentScope() {
        return scopes.peek();
    }

    private Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return null;
    }

    private boolean isDeclaredInCurrentScope(String name) {
        return scopes.peek().containsKey(name);
    }
}
