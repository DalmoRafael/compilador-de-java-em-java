package lol;

public class CompilerError {
    public enum ErrorType {
        LEXICAL("lexico"),
        SYNTACTIC("sintatico"),
        SEMANTIC("semantico");

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
