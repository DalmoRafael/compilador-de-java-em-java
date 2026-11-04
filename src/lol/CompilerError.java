package lol;

/*
 * CompilerError — representa um erro encontrado durante a compilação.
 *
 * Cada erro carrega a posição exata no código-fonte (linha e coluna),
 * a mensagem descritiva e a fase em que foi detectado (léxica, sintática
 * ou semântica). Isso permite exibir o erro no mesmo formato que o javac:
 *
 *   arquivo.java:3:10: error: mensagem
 *       linha do código fonte
 *                ^
 */
public class CompilerError {

    /*
     * ErrorType — identifica em qual fase do compilador o erro ocorreu.
     *
     * Um compilador clássico tem três fases de análise:
     *   LEXICAL   → o Lexer não reconheceu um caractere ou sequência de caracteres.
     *   SYNTACTIC → o Parser encontrou tokens fora da ordem esperada pela gramática.
     *   SEMANTIC  → o SemanticAnalyzer encontrou um problema de significado no código
     *               (variável não declarada, tipos incompatíveis, etc.).
     */
    public enum ErrorType {
        LEXICAL("léxico"),
        SYNTACTIC("sintático"),
        SEMANTIC("semântico");

        // Texto em português usado na mensagem de erro impressa no terminal.
        public final String label;

        ErrorType(String label) {
            this.label = label;
        }
    }

    public final int line;       // linha onde o erro foi detectado (começa em 1)
    public final int column;     // coluna onde o erro foi detectado (começa em 1)
    public final String message; // descrição legível do que deu errado
    public final ErrorType type; // fase do compilador que gerou o erro

    public CompilerError(int line, int column, String message, ErrorType type) {
        this.line    = line;
        this.column  = column;
        this.message = message;
        this.type    = type;
    }

    /*
     * toString() — gera a linha de texto simples do erro, usada nos testes.
     * A formatação principal com acento circunflexo fica em Main.printError().
     */
    @Override
    public String toString() {
        return "Erro " + type.label + " na linha " + line + ", coluna " + column + ": " + message;
    }
}
