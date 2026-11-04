package lol;

import java.util.List;

/**
 * Nós da AST que representam declarações e comandos (statements).
 *
 * <p>Cada classe aninhada estática corresponde a uma construção da linguagem.
 * A classe base {@code Stmt} é abstrata apenas para agrupar os tipos.</p>
 */
public abstract class Stmt {

    // =========================================================================
    // Declarações de alto nível
    // =========================================================================

    /** Declaração de classe: {@code class Nome { métodos }}. */
    public static class ClassDecl extends Stmt {
        public final Token name;
        public final List<MethodDecl> methods;

        public ClassDecl(Token name, List<MethodDecl> methods) {
            this.name    = name;
            this.methods = methods;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("class ").append(name.lexeme).append(" {\n");
            for (MethodDecl m : methods) {
                sb.append("  ").append(m.toString().replace("\n", "\n  ")).append("\n");
            }
            return sb.append("}").toString();
        }
    }

    /** Declaração de método: {@code tipo nome(params) { corpo }}. */
    public static class MethodDecl extends Stmt {
        public final Token name;
        public final String returnType;
        public final List<Param> params;
        public final List<Stmt> body;
        public final Token typeToken;

        public MethodDecl(Token name, String returnType, List<Param> params,
                          List<Stmt> body, Token typeToken) {
            this.name       = name;
            this.returnType = returnType;
            this.params     = params;
            this.body       = body;
            this.typeToken  = typeToken;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(returnType).append(" ").append(name.lexeme).append("(");
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(params.get(i));
            }
            sb.append(") {\n");
            for (Stmt s : body) {
                if (s != null) sb.append("  ").append(s.toString().replace("\n", "\n  ")).append("\n");
            }
            return sb.append("}").toString();
        }
    }

    /** Parâmetro de método: {@code tipo nome}. */
    public static class Param {
        public final String type;
        public final Token name;

        public Param(String type, Token name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public String toString() {
            return type + " " + name.lexeme;
        }
    }

    // =========================================================================
    // Comandos
    // =========================================================================

    /** Declaração de variável: {@code tipo nome [= expr];}. */
    public static class VarDecl extends Stmt {
        public final String type;
        public final Token name;
        public final Expr initializer;
        public final Token typeToken;

        public VarDecl(String type, Token name, Expr initializer, Token typeToken) {
            this.type        = type;
            this.name        = name;
            this.initializer = initializer;
            this.typeToken   = typeToken;
        }

        @Override
        public String toString() {
            return initializer != null
                    ? type + " " + name.lexeme + " = " + initializer + ";"
                    : type + " " + name.lexeme + ";";
        }
    }

    /** Comando if/else: {@code if (cond) então [else senão]}. */
    public static class If extends Stmt {
        public final Expr condition;
        public final Stmt thenBranch;
        public final Stmt elseBranch; // pode ser null

        public If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
            this.condition  = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public String toString() {
            String base = "if (" + condition + ") " + thenBranch;
            return elseBranch != null ? base + " else " + elseBranch : base;
        }
    }

    /** Laço while: {@code while (cond) corpo}. */
    public static class While extends Stmt {
        public final Expr condition;
        public final Stmt body;

        public While(Expr condition, Stmt body) {
            this.condition = condition;
            this.body      = body;
        }

        @Override
        public String toString() {
            return "while (" + condition + ") " + body;
        }
    }

    /** Laço for: {@code for (init; cond; incr) corpo}. Qualquer parte pode ser null. */
    public static class For extends Stmt {
        public final Stmt init;       // pode ser null
        public final Expr condition;  // pode ser null
        public final Expr increment;  // pode ser null
        public final Stmt body;

        public For(Stmt init, Expr condition, Expr increment, Stmt body) {
            this.init      = init;
            this.condition = condition;
            this.increment = increment;
            this.body      = body;
        }

        @Override
        public String toString() {
            String initStr = init != null ? init.toString() : ";";
            String condStr = condition != null ? condition.toString() : "";
            String incrStr = increment != null ? increment.toString() : "";
            return "for (" + initStr + " " + condStr + "; " + incrStr + ") " + body;
        }
    }

    /** Comando return: {@code return [expr];}. */
    public static class Return extends Stmt {
        public final Expr value;    // pode ser null (return sem valor)
        public final Token keyword; // token 'return', usado para posição nos erros

        public Return(Expr value, Token keyword) {
            this.value   = value;
            this.keyword = keyword;
        }

        @Override
        public String toString() {
            return value != null ? "return " + value + ";" : "return;";
        }
    }

    /** Instrução de impressão: {@code System.out.println(expr);}. */
    public static class Print extends Stmt {
        public final Expr expression;
        public final Token token; // token 'println', usado para posição nos erros

        public Print(Expr expression, Token token) {
            this.expression = expression;
            this.token      = token;
        }

        @Override
        public String toString() {
            return "System.out.println(" + expression + ");";
        }
    }

    /** Bloco de comandos: {@code { stmt* }}. */
    public static class Block extends Stmt {
        public final List<Stmt> statements;

        public Block(List<Stmt> statements) {
            this.statements = statements;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{\n");
            for (Stmt s : statements) {
                if (s != null) sb.append("  ").append(s.toString().replace("\n", "\n  ")).append("\n");
            }
            return sb.append("}").toString();
        }
    }

    /** Expressão usada como comando (ex: atribuição, chamada de método): {@code expr;}. */
    public static class ExprStmt extends Stmt {
        public final Expr expression;

        public ExprStmt(Expr expression) {
            this.expression = expression;
        }

        @Override
        public String toString() {
            return expression + ";";
        }
    }
}
