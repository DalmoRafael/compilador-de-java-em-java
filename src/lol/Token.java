package lol;

/*
 * Token — representa uma unidade léxica produzida pelo Lexer.
 *
 * Durante a análise léxica, o código-fonte é quebrado em tokens. Cada token
 * guarda quatro informações principais:
 *
 *   type    → o tipo do token (INT, IDENTIFIER, PLUS, etc.)
 *   lexeme  → o texto exato como aparece no código fonte (ex: "int", "x", "+")
 *   literal → o valor já convertido para o tipo Java correspondente,
 *             usado apenas para NUMBER e STRING (ex: 42 como Integer, "texto" como String)
 *   line    → linha onde o token aparece no código fonte
 *   column  → coluna onde o token começa na linha
 *
 * O campo symbolId é preenchido depois, durante o parsing e a análise semântica,
 * quando o token é um identificador (variável) que existe na tabela de símbolos.
 * Ele serve para vincular cada uso de uma variável à sua entrada na tabela.
 */
public class Token {

    public final TokenType type;
    public final String lexeme;  // texto original no código, ex: "x", "42", "if"
    public final Object literal; // valor convertido: 42 (Integer), 3.14 (Double), "texto" (String)
    public final int line;
    public final int column;

    // Preenchido pelo Parser/SemanticAnalyzer quando este token é um identificador declarado.
    // Aponta para o ID do símbolo correspondente na tabela de símbolos.
    public Integer symbolId;

    public Token(TokenType type, String lexeme, Object literal, int line, int column) {
        this.type     = type;
        this.lexeme   = lexeme;
        this.literal  = literal;
        this.line     = line;
        this.column   = column;
        this.symbolId = null;
    }

    /*
     * toString() — formata o token para exibição no terminal.
     *
     * O formato distingue visualmente três categorias:
     *   [PR] → palavra reservada da linguagem (int, if, while, ...)
     *   [ID] → identificador criado pelo programador (x, soma, Main, ...)
     *   sem prefixo → operador, delimitador ou literal
     *
     * Quando um identificador está vinculado à tabela de símbolos (symbolId != null),
     * mostra "-> #1" em vez de repetir o nome, deixando claro que aquele uso
     * de variável corresponde à entrada #1 da tabela.
     */
    @Override
    public String toString() {
        String prefix = "";
        if (TokenType.isKeyword(type)) {
            prefix = "[PR] "; // palavra reservada
        } else if (type == TokenType.IDENTIFIER) {
            prefix = "[ID] "; // identificador
        }

        // Se o identificador está declarado, mostra referência à tabela de símbolos
        String body = (type == TokenType.IDENTIFIER && symbolId != null)
                ? "-> #" + symbolId
                : "'" + lexeme + "'";

        // Sufixo com o valor convertido, exibido apenas para NUMBER e STRING
        String literalSuffix = literal != null ? " -> " + literal : "";

        return String.format("%s%-15s %s%s (linha %d, coluna %d)",
                prefix, type, body, literalSuffix, line, column);
    }
}
