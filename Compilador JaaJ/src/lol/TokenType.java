package lol;

public enum TokenType {
    // Literais
    NUMBER,       // 42, 3.14
    STRING_LITERAL, // "texto" (renomeado para evitar conflito com a palavra-chave String)
    IDENTIFIER,   // nomes de variaveis, metodos

    // Palavras-chave
    INT, DOUBLE, BOOLEAN, VOID, STRING, // STRING adicionada aqui como palavra-chave/tipo
    IF, ELSE, WHILE, FOR,
    TRUE, FALSE,
    RETURN,
    CLASS, PUBLIC, STATIC,

    // Operadores
    PLUS, MINUS, STAR, SLASH, PERCENT,
    ASSIGN,          // =
    EQUALS, NOT_EQUALS,       // == !=
    LESS, LESS_EQUAL,         // < <=
    GREATER, GREATER_EQUAL,   // > >=
    AND, OR, NOT,             // && || !

    // Delimitadores
    LPAREN, RPAREN,       // ( )
    LBRACE, RBRACE,       // { }
    LBRACKET, RBRACKET,   // [ ]
    SEMICOLON,            // ;
    COMMA,                // ,
    DOT,                  // .

    EOF                   // fim do arquivo
}