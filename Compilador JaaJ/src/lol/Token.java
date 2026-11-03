package lol;


public class Token {
    public final TokenType type;
    public final String lexeme;   // o texto original, ex: "x", "42", "if"
    public final Object literal;  // valor convertido, ex: 42 (int), "texto" (String)
    public final int line;

    public Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    @Override
    public String toString() {
        return String.format("%-15s '%s'%s (linha %d)",
                type,
                lexeme,
                literal != null ? " -> " + literal : "",
                line);
    }
}