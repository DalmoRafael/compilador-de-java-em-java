package lol;

import java.util.List;

/**
 * Classes AST para statements e declaracoes.
 */
public abstract class Stmt {

    public static class ClassDecl extends Stmt {
        public final Token name;
        public final List<MethodDecl> methods;

        public ClassDecl(Token name, List<MethodDecl> methods) {
            this.name = name;
            this.methods = methods;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("class " + name.lexeme + " {\n");
            for (MethodDecl m : methods) {
                sb.append("  ").append(m.toString().replace("\n", "\n  ")).append("\n");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    public static class MethodDecl extends Stmt {
        public final Token name;
        public final String returnType;
        public final List<Param> params;
        public final List<Stmt> body;
        public final Token typeToken;

        public MethodDecl(Token name, String returnType, List<Param> params, List<Stmt> body, Token typeToken) {
            this.name = name;
            this.returnType = returnType;
            this.params = params;
            this.body = body;
            this.typeToken = typeToken;
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
                if (s != null) {
                    sb.append("  ").append(s.toString().replace("\n", "\n  ")).append("\n");
                }
            }
            sb.append("}");
            return sb.toString();
        }
    }

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

    public static class VarDecl extends Stmt {
        public final String type;
        public final Token name;
        public final Expr initializer;
        public final Token typeToken;

        public VarDecl(String type, Token name, Expr initializer, Token typeToken) {
            this.type = type;
            this.name = name;
            this.initializer = initializer;
            this.typeToken = typeToken;
        }

        @Override
        public String toString() {
            if (initializer != null) {
                return type + " " + name.lexeme + " = " + initializer + ";";
            }
            return type + " " + name.lexeme + ";";
        }
    }

    public static class If extends Stmt {
        public final Expr condition;
        public final Stmt thenBranch;
        public final Stmt elseBranch;

        public If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("if (" + condition + ") " + thenBranch);
            if (elseBranch != null) {
                sb.append(" else ").append(elseBranch);
            }
            return sb.toString();
        }
    }

    public static class While extends Stmt {
        public final Expr condition;
        public final Stmt body;

        public While(Expr condition, Stmt body) {
            this.condition = condition;
            this.body = body;
        }

        @Override
        public String toString() {
            return "while (" + condition + ") " + body;
        }
    }

    public static class For extends Stmt {
        public final Stmt init;
        public final Expr condition;
        public final Expr increment;
        public final Stmt body;

        public For(Stmt init, Expr condition, Expr increment, Stmt body) {
            this.init = init;
            this.condition = condition;
            this.increment = increment;
            this.body = body;
        }

        @Override
        public String toString() {
            String initStr = init != null ? init.toString() : ";";
            String condStr = condition != null ? condition.toString() : "";
            String incrStr = increment != null ? increment.toString() : "";
            return "for (" + initStr + " " + condStr + "; " + incrStr + ") " + body;
        }
    }

    public static class Return extends Stmt {
        public final Expr value;
        public final Token keyword;

        public Return(Expr value, Token keyword) {
            this.value = value;
            this.keyword = keyword;
        }

        @Override
        public String toString() {
            if (value != null) {
                return "return " + value + ";";
            }
            return "return;";
        }
    }

    public static class Print extends Stmt {
        public final Expr expression;
        public final Token token;

        public Print(Expr expression, Token token) {
            this.expression = expression;
            this.token = token;
        }

        @Override
        public String toString() {
            return "System.out.println(" + expression + ");";
        }
    }

    public static class Block extends Stmt {
        public final List<Stmt> statements;

        public Block(List<Stmt> statements) {
            this.statements = statements;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{\n");
            for (Stmt s : statements) {
                if (s != null) {
                    sb.append("  ").append(s.toString().replace("\n", "\n  ")).append("\n");
                }
            }
            sb.append("}");
            return sb.toString();
        }
    }

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
