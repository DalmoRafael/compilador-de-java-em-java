package lol;

public class Token {
    public final TokenType type;
    public final String lexeme;   // o texto original, ex: "x", "42", "if"
    public final Object literal;  // valor convertido, ex: 42 (int), "texto" (String)
    public final int line;
    public final Integer symbolIndex; // Índice na tabela de símbolos (null se não for identificador)

    public Token(TokenType type, String lexeme, Object literal, int line) {
        this(type, lexeme, literal, line, null);
    }

    public Token(TokenType type, String lexeme, Object literal, int line, Integer symbolIndex) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
        this.symbolIndex = symbolIndex;
    }

    @Override
    public String toString() {
        String extra = "";
        if (literal != null) {
            extra += " -> " + literal;
        }
        if (symbolIndex != null) {
            extra += " [Index: " + symbolIndex + "]";
        }
        return String.format("%-15s '%s'%s (linha %d)",
                type,
                lexeme,
                extra,
                line);
    }
}