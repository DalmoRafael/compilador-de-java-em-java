package lol;

import java.util.List;

/**
 * Nós da AST que representam expressões.
 *
 * <p>Cada classe aninhada estática corresponde a uma forma de expressão da
 * linguagem. A classe base {@code Expr} é abstrata apenas para agrupar os tipos.</p>
 */
public abstract class Expr {

    /** Operação binária: {@code left op right} (ex: {@code x + y}, {@code a < b}). */
    public static class Binary extends Expr {
        public final Expr left;
        public final Token operator;
        public final Expr right;

        public Binary(Expr left, Token operator, Expr right) {
            this.left     = left;
            this.operator = operator;
            this.right    = right;
        }

        @Override
        public String toString() {
            return "(" + left + " " + operator.lexeme + " " + right + ")";
        }
    }

    /** Operação unária: {@code op right} (ex: {@code !flag}, {@code -x}). */
    public static class Unary extends Expr {
        public final Token operator;
        public final Expr right;

        public Unary(Token operator, Expr right) {
            this.operator = operator;
            this.right    = right;
        }

        @Override
        public String toString() {
            return "(" + operator.lexeme + right + ")";
        }
    }

    /** Valor literal: número, string, {@code true} ou {@code false}. */
    public static class Literal extends Expr {
        public final Object value;
        public final Token token;

        public Literal(Object value, Token token) {
            this.value = value;
            this.token = token;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    /** Referência a uma variável declarada (ex: {@code x}). */
    public static class Variable extends Expr {
        public final Token name;

        public Variable(Token name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name.lexeme;
        }
    }

    /** Atribuição: {@code nome = valor} (ex: {@code x = 5}). */
    public static class Assign extends Expr {
        public final Token name;
        public final Expr value;

        public Assign(Token name, Expr value) {
            this.name  = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return "(" + name.lexeme + " = " + value + ")";
        }
    }

    /** Expressão entre parênteses (ex: {@code (x + y)}). */
    public static class Grouping extends Expr {
        public final Expr expression;

        public Grouping(Expr expression) {
            this.expression = expression;
        }

        @Override
        public String toString() {
            return "(" + expression + ")";
        }
    }

    /** Chamada de método: {@code nome(args)} (ex: {@code soma(a, b)}). */
    public static class Call extends Expr {
        public final Token callee;
        public final List<Expr> args;

        public Call(Token callee, List<Expr> args) {
            this.callee = callee;
            this.args   = args;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(callee.lexeme).append("(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(args.get(i));
            }
            return sb.append(")").toString();
        }
    }
}
