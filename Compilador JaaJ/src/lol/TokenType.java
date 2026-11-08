package lol;

public enum TokenType {
    // Literais
    NUMBER,       // 42, 3.14
    STRING,       // "texto"
    IDENTIFIER,   // nomes de variaveis, metodos

    // Palavras-chave
    INT, DOUBLE, BOOLEAN, VOID,
    IF, ELSE, WHILE, FOR,
    TRUE, FALSE,
    RETURN,
    CLASS, PUBLIC, STATIC,
    SYSTEM, OUT, PRINTLN, // tratamento especial pra System.out.println

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
    SEMICOLON,            // ;
    COMMA,                // ,
    DOT,                  // .

    EOF                   // fim do arquivo
}
