package lol;

import java.util.List;

/*
 * Stmt — nós da AST (Árvore Sintática Abstrata) que representam comandos e declarações.
 *
 * Diferente de Expr (que produz um valor), um Stmt executa uma ação:
 * declara uma variável, executa um if, repete um laço, etc.
 *
 * A classe base Stmt é abstrata — ela existe apenas para criar um tipo comum.
 * Cada tipo de comando é uma classe aninhada estática que herda de Stmt e
 * armazena os dados específicos daquele tipo.
 *
 * O Parser cria esses objetos ao reconhecer cada construção da linguagem.
 * O SemanticAnalyzer depois percorre a árvore verificando regras semânticas.
 */
public abstract class Stmt {

    // =========================================================================
    // Declarações de alto nível (estrutura do programa)
    // =========================================================================

    /*
     * ClassDecl — declaração de uma classe.
     *
     * Exemplo: public class Main { ... }
     *
     *   name    → token com o nome da classe (ex: "Main")
     *   methods → lista de métodos declarados dentro da classe
     */
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

    /*
     * MethodDecl — declaração de um método.
     *
     * Exemplo: int soma(int a, int b) { return a + b; }
     *
     *   name       → token com o nome do método (ex: "soma")
     *   returnType → tipo de retorno em maiúsculas (ex: "INT", "VOID")
     *   params     → lista de parâmetros (cada um com tipo e nome)
     *   body       → lista de comandos que formam o corpo do método
     *   typeToken  → token do tipo de retorno (guardado para posição em erros)
     */
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

    /*
     * Param — parâmetro de um método.
     *
     * Exemplo: int a  (dentro de "soma(int a, int b)")
     *
     * Não é um Stmt (não é um comando), mas está aqui por estar diretamente
     * associado a MethodDecl. Parâmetros também são registrados na tabela
     * de símbolos, no escopo do método.
     */
    public static class Param {
        public final String type; // tipo do parâmetro em maiúsculas, ex: "INT"
        public final Token name;  // token com o nome do parâmetro, ex: "a"

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
    // Comandos (statements)
    // =========================================================================

    /*
     * VarDecl — declaração de variável.
     *
     * Exemplo: int x = 5;   ou   int y;
     *
     *   type        → tipo em maiúsculas, ex: "INT"
     *   name        → token com o nome da variável
     *   initializer → expressão do valor inicial, ou null se não há inicializador
     *   typeToken   → token do tipo (guardado para posição em erros)
     *
     * Durante o parsing, esta declaração cria uma entrada na tabela de símbolos
     * e preenche name.symbolId com o ID gerado.
     */
    public static class VarDecl extends Stmt {
        public final String type;
        public final Token name;
        public final Expr initializer; // pode ser null
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

    /*
     * If — comando condicional if/else.
     *
     * Exemplo: if (x < y) { ... } else { ... }
     *
     *   condition  → expressão que é avaliada (deve resultar em BOOLEAN)
     *   thenBranch → comando executado quando a condição for verdadeira
     *   elseBranch → comando executado quando a condição for falsa (pode ser null)
     */
    public static class If extends Stmt {
        public final Expr condition;
        public final Stmt thenBranch;
        public final Stmt elseBranch; // null se não houver else

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

    /*
     * While — laço while.
     *
     * Exemplo: while (x > 0) { x = x - 1; }
     *
     *   condition → expressão avaliada antes de cada iteração
     *   body      → comando executado enquanto a condição for verdadeira
     */
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

    /*
     * For — laço for com três partes opcionais.
     *
     * Exemplo: for (int i = 0; i < 10; i = i + 1) { ... }
     *
     *   init      → declaração ou comando executado uma vez antes do laço (pode ser null)
     *   condition → expressão avaliada antes de cada iteração (pode ser null = loop infinito)
     *   increment → expressão executada no final de cada iteração (pode ser null)
     *   body      → corpo do laço
     */
    public static class For extends Stmt {
        public final Stmt init;      // pode ser null
        public final Expr condition; // pode ser null
        public final Expr increment; // pode ser null
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

    /*
     * Return — comando de retorno de um método.
     *
     * Exemplos: return a + b;   ou   return;
     *
     *   value   → expressão cujo valor é retornado (null para métodos void)
     *   keyword → token 'return' (guardado para informar a posição em erros semânticos)
     */
    public static class Return extends Stmt {
        public final Expr value;    // null para "return;" sem valor
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

    /*
     * Print — instrução System.out.println().
     *
     * Exemplo: System.out.println(x + y);
     *
     * System.out.println é tratado como uma construção especial da linguagem,
     * não como uma chamada de método genérica. O Parser reconhece a sequência
     * exata de tokens System . out . println e cria este nó.
     *
     *   expression → o que será impresso
     *   token      → token 'println' (guardado para posição em erros)
     */
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

    /*
     * Block — bloco de comandos delimitado por chaves.
     *
     * Exemplo: { int x = 5; System.out.println(x); }
     *
     * Um bloco introduz um novo escopo: variáveis declaradas dentro dele
     * existem apenas até o fechamento da chave.
     *
     *   statements → lista dos comandos dentro do bloco
     */
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

    /*
     * ExprStmt — expressão usada como comando (a expressão é avaliada pelo efeito colateral).
     *
     * Exemplo: x = x - 1;  (a atribuição é uma Expr, mas aparece como statement)
     *
     * Em muitas linguagens, uma expressão pode ser usada como comando quando
     * o que importa é o efeito que ela causa (como uma atribuição), e não o
     * valor que ela retorna.
     */
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
