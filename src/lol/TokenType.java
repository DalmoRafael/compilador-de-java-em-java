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
 *
 * Palavras reservadas:
 *   Cada palavra reservada carrega o seu próprio texto (ex: INT("int")).
 *   O Lexer monta a tabela de palavras reservadas a partir desses textos,
 *   então para criar uma nova palavra reservada basta declarar uma nova
 *   constante aqui — não é preciso mexer em mais nenhum lugar.
 *
 *   System, out e println NÃO são palavras reservadas: são identificadores
 *   comuns (System.out.println vira IDENTIFIER DOT IDENTIFIER DOT IDENTIFIER).
 */
public enum TokenType {

    // --- Literais e identificadores ---
    NUMBER,          // número inteiro ou decimal, ex: 42, 3.14
    STRING_LITERAL,  // texto entre aspas duplas, ex: "olá"
    IDENTIFIER,      // nome criado pelo programador (variáveis, métodos, classes)

    // --- Palavras reservadas: tipos de dados ---
    INT("int"), DOUBLE("double"), BOOLEAN("boolean"), VOID("void"),
    FLOAT("float"), LONG("long"), SHORT("short"), BYTE("byte"), CHAR("char"),
    STRING("String"),   // tipo String (não é reservada em Java, mas é tratada como tipo aqui)

    // --- Palavras reservadas: valores ---
    TRUE("true"), FALSE("false"), NULL("null"),

    // --- Palavras reservadas: estruturas de controle ---
    IF("if"), ELSE("else"),
    WHILE("while"), DO("do"), FOR("for"),
    SWITCH("switch"), CASE("case"), DEFAULT("default"),
    BREAK("break"), CONTINUE("continue"), RETURN("return"),
    TRY("try"), CATCH("catch"), FINALLY("finally"),
    THROW("throw"), THROWS("throws"),

    // --- Palavras reservadas: classes e modificadores ---
    CLASS("class"), INTERFACE("interface"), ENUM("enum"),
    EXTENDS("extends"), IMPLEMENTS("implements"),
    PACKAGE("package"), IMPORT("import"),
    NEW("new"), THIS("this"), SUPER("super"), INSTANCEOF("instanceof"),
    PUBLIC("public"), PRIVATE("private"), PROTECTED("protected"),
    STATIC("static"), FINAL("final"), ABSTRACT("abstract"),
    NATIVE("native"), SYNCHRONIZED("synchronized"), TRANSIENT("transient"),
    VOLATILE("volatile"), STRICTFP("strictfp"),
    ASSERT("assert"), CONST("const"), GOTO("goto"),

    // --- Operadores ---
    PLUS, MINUS, STAR, SLASH, PERCENT,  // aritméticos:   + - * / %
    ASSIGN,                             // atribuição:     =
    EQUALS, NOT_EQUALS,                 // igualdade:      == !=
    LESS, LESS_EQUAL,                   // comparação:     < <=
    GREATER, GREATER_EQUAL,             // comparação:     > >=
    AND, OR, NOT,                       // lógicos:        && || !

    // --- Delimitadores ---
    LPAREN, RPAREN,     // parênteses:     ( )
    LBRACE, RBRACE,     // chaves:         { }
    LBRACKET, RBRACKET, // colchetes:      [ ]
    SEMICOLON,          // ponto e vírgula: ;
    COMMA,              // vírgula:         ,
    DOT,                // ponto:           .

    // --- Marcador de fim de arquivo ---
    // Adicionado artificialmente pelo Lexer para indicar que não há mais tokens.
    EOF;

    // Texto da palavra reservada (ex: "int"), ou null se não for palavra reservada.
    public final String keyword;

    TokenType()               { this.keyword = null; }
    TokenType(String keyword) { this.keyword = keyword; }

    /*
     * isKeyword() — verifica se um tipo de token é uma palavra reservada.
     *
     * Usado em Token.toString() para decidir se imprime o prefixo [PR]
     * (palavra reservada) ou [ID] (identificador) na saída.
     */
    public static boolean isKeyword(TokenType type) {
        return type.keyword != null;
    }
}
