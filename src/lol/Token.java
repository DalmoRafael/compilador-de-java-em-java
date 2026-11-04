package lol;

/**
 * Representa um token produzido pelo lexer.
 *
 * <p>Cada token carrega seu tipo, o texto original (lexeme), um valor literal
 * convertido quando aplicável (ex: {@code 42} para NUMBER), a posição no
 * código-fonte (linha e coluna) e, após o parsing, o ID do símbolo na
 * tabela de símbolos quando se tratar de um identificador declarado.</p>
 */
public class Token {

    public final TokenType type;
    public final String lexeme;  // texto original, ex: "x", "42", "if"
    public final Object literal; // valor convertido, ex: 42 (int), "texto" (String)
    public final int line;
    public final int column;

    /** ID na tabela de símbolos; preenchido durante o parsing e análise semântica. */
    public Integer symbolId;

    public Token(TokenType type, String lexeme, Object literal, int line, int column) {
        this.type    = type;
        this.lexeme  = lexeme;
        this.literal = literal;
        this.line    = line;
        this.column  = column;
        this.symbolId = null;
    }

    @Override
    public String toString() {
        // Prefixo visual: [PR] para palavras reservadas, [ID] para identificadores
        String prefix = "";
        if (TokenType.isKeyword(type)) {
            prefix = "[PR] ";
        } else if (type == TokenType.IDENTIFIER) {
            prefix = "[ID] ";
        }

        // Identificadores declarados mostram referência à tabela de símbolos
        String body = (type == TokenType.IDENTIFIER && symbolId != null)
                ? "-> #" + symbolId
                : "'" + lexeme + "'";

        String literalSuffix = literal != null ? " -> " + literal : "";

        return String.format("%s%-15s %s%s (linha %d, coluna %d)",
                prefix, type, body, literalSuffix, line, column);
    }
}
