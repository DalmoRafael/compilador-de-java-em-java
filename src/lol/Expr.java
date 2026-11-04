package lol;

import java.util.List;

/**
 * Classes AST para expressoes.
 */
public abstract class Expr {

    public static class Binary extends Expr {
        public final Expr left;
        public final Token operator;
        public final Expr right;

        public Binary(Expr left, Token operator, Expr right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public String toString() {
            return "(" + left + " " + operator.lexeme + " " + right + ")";
        }
    }

    public static class Unary extends Expr {
        public final Token operator;
        public final Expr right;

        public Unary(Token operator, Expr right) {
            this.operator = operator;
            this.right = right;
        }

        @Override
        public String toString() {
            return "(" + operator.lexeme + right + ")";
        }
    }

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

    public static class Assign extends Expr {
        public final Token name;
        public final Expr value;

        public Assign(Token name, Expr value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return "(" + name.lexeme + " = " + value + ")";
        }
    }

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

    public static class Call extends Expr {
        public final Token callee;
        public final List<Expr> args;

        public Call(Token callee, List<Expr> args) {
            this.callee = callee;
            this.args = args;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(callee.lexeme).append("(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(args.get(i));
            }
            sb.append(")");
            return sb.toString();
        }
    }
}
