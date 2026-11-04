package lol;


public class Token {
    public final TokenType type;
    public final String lexeme;   // o texto original, ex: "x", "42", "if"
    public final Object literal;  // valor convertido, ex: 42 (int), "texto" (String)
    public final int line;
    public final int column;
    public Integer symbolId;      // ID na tabela de simbolos (preenchido durante parsing/semantica)

    public Token(TokenType type, String lexeme, Object literal, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
        this.column = column;
        this.symbolId = null;
    }

    @Override
    public String toString() {
        String prefix = "";
        if (TokenType.isKeyword(type)) {
            prefix = "[PR] ";
        } else if (type == TokenType.IDENTIFIER) {
            prefix = "[ID] ";
        }

        String mainPart;
        if (type == TokenType.IDENTIFIER && symbolId != null) {
            // Identificador declarado: mostra referencia a tabela de simbolos
            mainPart = "-> #" + symbolId;
        } else {
            // Mostra o lexeme entre aspas
            mainPart = "'" + lexeme + "'";
        }

        String literalPart = literal != null ? " -> " + literal : "";

        return String.format("%s%-15s %s%s (linha %d, coluna %d)",
                prefix,
                type,
                mainPart,
                literalPart,
                line,
                column);
    }
}
