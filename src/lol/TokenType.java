package lol;

/*
 * TokenType — define todos os tipos de token que o compilador reconhece.
 *
 * Um token é a menor unidade com significado no código-fonte. O Lexer lê
 * o código caractere por caractere e agrupa os caracteres em tokens, cada
 * um com um tipo definido aqui.
 *
 * Exemplos:
 *   "int"   → INT        (palavra reservada)
 *   "x"     → IDENTIFIER (nome criado pelo programador)
 *   "5"     → NUMBER     (valor numérico)
 *   "="     → ASSIGN     (operador de atribuição)
 *   "=="    → EQUALS     (operador de comparação)
 */
public enum TokenType {

    // --- Literais ---
    // Valores que o programador escreve diretamente no código.
    NUMBER,     // número inteiro ou decimal, ex: 42, 3.14
    STRING,     // texto entre aspas duplas, ex: "olá"
    IDENTIFIER, // nome criado pelo programador para variáveis e métodos, ex: x, soma

    // --- Palavras reservadas ---
    // Palavras que a linguagem reserva para uso próprio; não podem ser
    // usadas como nomes de variáveis.
    INT, DOUBLE, BOOLEAN, VOID,   // tipos de dados
    IF, ELSE, WHILE, FOR,         // estruturas de controle
    TRUE, FALSE,                  // valores booleanos
    RETURN,                       // retorno de método
    CLASS, PUBLIC, STATIC,        // estrutura de classe
    SYSTEM, OUT, PRINTLN,         // System.out.println tratado como palavras reservadas

    // --- Operadores ---
    PLUS, MINUS, STAR, SLASH, PERCENT,  // aritméticos:   + - * / %
    ASSIGN,                             // atribuição:     =
    EQUALS, NOT_EQUALS,                 // igualdade:      == !=
    LESS, LESS_EQUAL,                   // comparação:     < <=
    GREATER, GREATER_EQUAL,             // comparação:     > >=
    AND, OR, NOT,                       // lógicos:        && || !

    // --- Delimitadores ---
    // Caracteres que delimitam estruturas do código.
    LPAREN, RPAREN,   // parênteses:    ( )
    LBRACE, RBRACE,   // chaves:        { }
    SEMICOLON,        // ponto e vírgula: ;
    COMMA,            // vírgula:       ,
    DOT,              // ponto:         .

    // --- Marcador de fim de arquivo ---
    // Adicionado artificialmente pelo Lexer para indicar que não há mais tokens.
    // Permite que o Parser saiba quando parar de ler.
    EOF;

    /*
     * isKeyword() — verifica se um tipo de token é uma palavra reservada.
     *
     * Usado em Token.toString() para decidir se imprime o prefixo [PR]
     * (palavra reservada) ou [ID] (identificador) na saída.
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
