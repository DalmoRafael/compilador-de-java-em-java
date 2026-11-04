package lol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analisador léxico (lexer).
 *
 * <p>Converte o código-fonte em uma lista de {@link Token tokens}, rastreando
 * linha e coluna de cada token. Erros léxicos são coletados em uma lista e
 * não interrompem a varredura, permitindo reportar múltiplos erros.</p>
 */
public class Lexer {

    // --- Mapa de palavras-chave reconhecidas ---
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("int",     TokenType.INT);
        KEYWORDS.put("double",  TokenType.DOUBLE);
        KEYWORDS.put("boolean", TokenType.BOOLEAN);
        KEYWORDS.put("void",    TokenType.VOID);
        KEYWORDS.put("if",      TokenType.IF);
        KEYWORDS.put("else",    TokenType.ELSE);
        KEYWORDS.put("while",   TokenType.WHILE);
        KEYWORDS.put("for",     TokenType.FOR);
        KEYWORDS.put("true",    TokenType.TRUE);
        KEYWORDS.put("false",   TokenType.FALSE);
        KEYWORDS.put("return",  TokenType.RETURN);
        KEYWORDS.put("class",   TokenType.CLASS);
        KEYWORDS.put("public",  TokenType.PUBLIC);
        KEYWORDS.put("static",  TokenType.STATIC);
        KEYWORDS.put("System",  TokenType.SYSTEM);
        KEYWORDS.put("out",     TokenType.OUT);
        KEYWORDS.put("println", TokenType.PRINTLN);
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<CompilerError> errors = new ArrayList<>();

    private int start = 0;       // índice do início do lexema atual
    private int current = 0;     // índice do caractere sendo lido
    private int line = 1;
    private int column = 1;
    private int startColumn = 1; // coluna do início do lexema atual

    public Lexer(String source) {
        this.source = source;
    }

    /**
     * Varre o código-fonte inteiro e retorna a lista de tokens produzidos.
     * Erros léxicos ficam disponíveis em {@link #getErrors()}.
     */
    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            startColumn = column;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, column));
        return tokens;
    }

    /** Retorna os erros léxicos encontrados durante a varredura. */
    public List<CompilerError> getErrors() {
        return errors;
    }

    // -------------------------------------------------------------------------
    // Varredura de tokens
    // -------------------------------------------------------------------------

    private void scanToken() {
        char c = advance();
        switch (c) {
            // Espaços em branco — ignorados
            case ' ', '\r', '\t' -> {}

            // Nova linha — advance() já atualizou linha e coluna
            case '\n' -> {}

            // Delimitadores de um caractere
            case '(' -> addToken(TokenType.LPAREN);
            case ')' -> addToken(TokenType.RPAREN);
            case '{' -> addToken(TokenType.LBRACE);
            case '}' -> addToken(TokenType.RBRACE);
            case ';' -> addToken(TokenType.SEMICOLON);
            case ',' -> addToken(TokenType.COMMA);
            case '.' -> addToken(TokenType.DOT);
            case '+' -> addToken(TokenType.PLUS);
            case '-' -> addToken(TokenType.MINUS);
            case '*' -> addToken(TokenType.STAR);
            case '%' -> addToken(TokenType.PERCENT);

            // Operadores de um ou dois caracteres
            case '=' -> addToken(match('=') ? TokenType.EQUALS       : TokenType.ASSIGN);
            case '!' -> addToken(match('=') ? TokenType.NOT_EQUALS    : TokenType.NOT);
            case '<' -> addToken(match('=') ? TokenType.LESS_EQUAL    : TokenType.LESS);
            case '>' -> addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);

            case '&' -> {
                if (match('&')) addToken(TokenType.AND);
                else lexError("Caractere inesperado: '&' (esperava '&&')");
            }
            case '|' -> {
                if (match('|')) addToken(TokenType.OR);
                else lexError("Caractere inesperado: '|' (esperava '||')");
            }

            // Comentários ou divisão
            case '/' -> {
                if (match('/'))      skipLineComment();
                else if (match('*')) blockComment();
                else                 addToken(TokenType.SLASH);
            }

            // String literal
            case '"' -> string();

            default -> {
                if (isDigit(c))     number();
                else if (isAlpha(c)) identifier();
                else lexError("Caractere inesperado: '" + c + "'");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Leitura de literais e comentários
    // -------------------------------------------------------------------------

    /** Ignora tudo até o fim da linha (comentário //). */
    private void skipLineComment() {
        while (peek() != '\n' && !isAtEnd()) advance();
    }

    /** Consome um comentário de bloco (/* ... *\/). Registra erro se não fechado. */
    private void blockComment() {
        while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) {
            advance();
        }
        if (isAtEnd()) {
            lexError("Comentário de bloco não foi fechado");
            return;
        }
        advance(); // consome '*'
        advance(); // consome '/'
    }

    /** Consome uma string literal entre aspas duplas. */
    private void string() {
        StringBuilder sb = new StringBuilder();
        while (peek() != '"' && !isAtEnd()) {
            sb.append(advance());
        }
        if (isAtEnd()) {
            lexError("String não foi fechada");
            return;
        }
        advance(); // consome a aspas final
        addToken(TokenType.STRING, sb.toString());
    }

    /** Consome um número inteiro ou decimal. */
    private void number() {
        while (isDigit(peek())) advance();

        boolean isDouble = peek() == '.' && isDigit(peekNext());
        if (isDouble) {
            advance(); // consome o '.'
            while (isDigit(peek())) advance();
        }

        String text = source.substring(start, current);
        if (isDouble) addToken(TokenType.NUMBER, Double.parseDouble(text));
        else          addToken(TokenType.NUMBER, Integer.parseInt(text));
    }

    /** Consome um identificador ou palavra-chave. */
    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
        addToken(type);
    }

    // -------------------------------------------------------------------------
    // Helpers de leitura
    // -------------------------------------------------------------------------

    /** Consome o próximo caractere se for igual a {@code expected}. */
    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;
        advance();
        return true;
    }

    /** Retorna o caractere atual sem consumi-lo. */
    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    /** Retorna o próximo caractere (look-ahead de 2) sem consumi-lo. */
    private char peekNext() {
        return (current + 1 >= source.length()) ? '\0' : source.charAt(current + 1);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    /** Consome e retorna o caractere atual, atualizando linha e coluna. */
    private char advance() {
        char c = source.charAt(current++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line, startColumn));
    }

    /** Registra um erro léxico na posição do início do lexema atual. */
    private void lexError(String message) {
        errors.add(new CompilerError(line, startColumn, message, CompilerError.ErrorType.LEXICAL));
    }
}
