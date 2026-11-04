package lol;

/**
 * Representa um erro de compilação com sua posição no código-fonte
 * (linha e coluna) e o tipo da fase em que foi detectado.
 */
public class CompilerError {

    /** Fase do compilador em que o erro foi detectado. */
    public enum ErrorType {
        LEXICAL("léxico"),
        SYNTACTIC("sintático"),
        SEMANTIC("semântico");

        public final String label;

        ErrorType(String label) {
            this.label = label;
        }
    }

    public final int line;
    public final int column;
    public final String message;
    public final ErrorType type;

    public CompilerError(int line, int column, String message, ErrorType type) {
        this.line = line;
        this.column = column;
        this.message = message;
        this.type = type;
    }

    @Override
    public String toString() {
        return "Erro " + type.label + " na linha " + line + ", coluna " + column + ": " + message;
    }
}
