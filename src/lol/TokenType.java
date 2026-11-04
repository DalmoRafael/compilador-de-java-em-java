package lol;

/**
 * Todos os tipos de token reconhecidos pelo compilador.
 */
public enum TokenType {

    // --- Literais ---
    NUMBER,     // ex: 42, 3.14
    STRING,     // ex: "texto"
    IDENTIFIER, // ex: x, soma, Main

    // --- Palavras-chave ---
    INT, DOUBLE, BOOLEAN, VOID,
    IF, ELSE, WHILE, FOR,
    TRUE, FALSE,
    RETURN,
    CLASS, PUBLIC, STATIC,
    SYSTEM, OUT, PRINTLN, // System.out.println tratado como palavras-chave

    // --- Operadores ---
    PLUS, MINUS, STAR, SLASH, PERCENT,  // + - * / %
    ASSIGN,                             // =
    EQUALS, NOT_EQUALS,                 // == !=
    LESS, LESS_EQUAL,                   // < <=
    GREATER, GREATER_EQUAL,             // > >=
    AND, OR, NOT,                       // && || !

    // --- Delimitadores ---
    LPAREN, RPAREN,   // ( )
    LBRACE, RBRACE,   // { }
    SEMICOLON,        // ;
    COMMA,            // ,
    DOT,              // .

    // --- Fim de arquivo ---
    EOF;

    /**
     * Retorna {@code true} se o tipo representa uma palavra reservada da linguagem.
     */
    public static boolean isKeyword(TokenType type) {
        return switch (type) {
            case INT, DOUBLE, BOOLEAN, VOID,
                 IF, ELSE, WHILE, FOR,
                 TRUE, FALSE, RETURN,
                 CLASS, PUBLIC, STATIC,
                 SYSTEM, OUT, PRINTLN -> true;
            default -> false;
        };
    }
}
