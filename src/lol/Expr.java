package lol;

import java.util.List;

/*
 * Expr — nós da AST (Árvore Sintática Abstrata) que representam expressões.
 *
 * Uma expressão é qualquer trecho de código que produz um valor:
 *   5           → Literal (valor 5)
 *   x           → Variable (o valor de x)
 *   x + 5       → Binary  (soma de x com 5)
 *   x = 5       → Assign  (atribui 5 a x, retorna o valor atribuído)
 *   !flag       → Unary   (negação de flag)
 *   (x + y)     → Grouping (expressão entre parênteses)
 *   soma(a, b)  → Call    (chamada de método)
 *
 * A classe base Expr é abstrata — ela existe apenas para criar um tipo comum
 * para todos esses nós. Cada tipo de expressão é uma classe aninhada estática
 * que herda de Expr e armazena os dados específicos daquele tipo.
 *
 * O Parser cria esses objetos durante a análise sintática. O SemanticAnalyzer
 * depois percorre a árvore verificando tipos e vínculos de variáveis.
 */
public abstract class Expr {

    /*
     * Binary — expressão com dois operandos e um operador entre eles.
     *
     * Exemplos: x + y, a < b, x == 10, flag && true
     *
     *   left     → expressão do lado esquerdo
     *   operator → token do operador (+, -, *, <, ==, &&, etc.)
     *   right    → expressão do lado direito
     */
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

    /*
     * Unary — expressão com um único operando precedido por um operador.
     *
     * Exemplos: !flag, -x
     *
     *   operator → token do operador (! ou -)
     *   right    → a expressão sobre a qual o operador age
     */
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

    /*
     * Literal — um valor fixo escrito diretamente no código.
     *
     * Exemplos: 42, 3.14, "texto", true, false
     *
     *   value → o valor Java correspondente (Integer, Double, String ou Boolean)
     *   token → o token original (usado para recuperar posição em caso de erro)
     */
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

    /*
     * Variable — referência a uma variável pelo nome.
     *
     * Exemplo: quando o código escreve "x" em uma expressão como "x + 5",
     * o Parser cria um Variable com o token do nome "x".
     * O SemanticAnalyzer depois preenche token.symbolId com o ID da variável
     * na tabela de símbolos.
     */
    public static class Variable extends Expr {
        public final Token name; // token do identificador, ex: token para "x"

        public Variable(Token name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name.lexeme;
        }
    }

    /*
     * Assign — atribuição de um valor a uma variável.
     *
     * Exemplo: x = 5  (atribui 5 à variável x)
     *
     * No compilador, atribuição é tratada como expressão (não como comando),
     * o que permite encadeamentos como: x = y = 5.
     *
     *   name  → token do nome da variável que recebe o valor
     *   value → expressão cujo resultado será atribuído
     */
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

    /*
     * Grouping — expressão entre parênteses.
     *
     * Exemplo: (x + y)
     *
     * Os parênteses alteram a precedência das operações. O Parser os trata
     * criando este nó que envolve a expressão interna.
     */
    public static class Grouping extends Expr {
        public final Expr expression; // a expressão dentro dos parênteses

        public Grouping(Expr expression) {
            this.expression = expression;
        }

        @Override
        public String toString() {
            return "(" + expression + ")";
        }
    }

    /*
     * Call — chamada de método.
     *
     * Exemplo: soma(a, b)
     *
     *   callee → token com o nome do método chamado
     *   args   → lista de expressões passadas como argumentos
     */
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
