package lol;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<CompilerError> errors = new ArrayList<>();

    private int start = 0;   // inicio do lexema atual
    private int current = 0; // posicao atual de leitura
    private int line = 1;
    private int column = 1;       // coluna atual
    private int startColumn = 1; // coluna do inicio do lexema atual

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("int", TokenType.INT);
        KEYWORDS.put("double", TokenType.DOUBLE);
        KEYWORDS.put("boolean", TokenType.BOOLEAN);
        KEYWORDS.put("void", TokenType.VOID);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("false", TokenType.FALSE);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("class", TokenType.CLASS);
        KEYWORDS.put("public", TokenType.PUBLIC);
        KEYWORDS.put("static", TokenType.STATIC);
        KEYWORDS.put("System", TokenType.SYSTEM);
        KEYWORDS.put("out", TokenType.OUT);
        KEYWORDS.put("println", TokenType.PRINTLN);
    }

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            startColumn = column;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, column));
        return tokens;
    }

    public List<CompilerError> getErrors() {
        return errors;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            // espacos em branco: ignora
            case ' ':
            case '\r':
            case '\t':
                break;
            case '\n':
                // advance() ja incrementou line e resetou column
                break;

            // delimitadores simples
            case '(': addToken(TokenType.LPAREN); break;
            case ')': addToken(TokenType.RPAREN); break;
            case '{': addToken(TokenType.LBRACE); break;
            case '}': addToken(TokenType.RBRACE); break;
            case ';': addToken(TokenType.SEMICOLON); break;
            case ',': addToken(TokenType.COMMA); break;
            case '.': addToken(TokenType.DOT); break;
            case '+': addToken(TokenType.PLUS); break;
            case '-': addToken(TokenType.MINUS); break;
            case '*': addToken(TokenType.STAR); break;
            case '%': addToken(TokenType.PERCENT); break;

            case '=':
                addToken(match('=') ? TokenType.EQUALS : TokenType.ASSIGN);
                break;
            case '!':
                addToken(match('=') ? TokenType.NOT_EQUALS : TokenType.NOT);
                break;
            case '<':
                addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
                break;
            case '>':
                addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
                break;
            case '&':
                if (match('&')) {
                    addToken(TokenType.AND);
                } else {
                    lexError("Caractere inesperado: '&' (esperava '&&')");
                }
                break;
            case '|':
                if (match('|')) {
                    addToken(TokenType.OR);
                } else {
                    lexError("Caractere inesperado: '|' (esperava '||')");
                }
                break;

            // comentarios ou divisao
            case '/':
                if (match('/')) {
                    // comentario de linha: ignora ate o fim da linha
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else if (match('*')) {
                    blockComment();
                } else {
                    addToken(TokenType.SLASH);
                }
                break;

            case '"':
                string();
                break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    lexError("Caractere inesperado: '" + c + "'");
                }
        }
    }

    private void blockComment() {
        while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) {
            advance(); // advance() rastreia line e column
        }
        if (isAtEnd()) {
            lexError("Comentario de bloco nao foi fechado");
            return;
        }
        advance(); // consome '*'
        advance(); // consome '/'
    }

    private void string() {
        StringBuilder sb = new StringBuilder();
        while (peek() != '"' && !isAtEnd()) {
            sb.append(advance()); // advance() rastreia line e column
        }
        if (isAtEnd()) {
            lexError("String nao foi fechada");
            return;
        }
        advance(); // consome as aspas final
        addToken(TokenType.STRING, sb.toString());
    }

    private void number() {
        while (isDigit(peek())) advance();

        boolean isDouble = false;
        if (peek() == '.' && isDigit(peekNext())) {
            isDouble = true;
            advance(); // consome o '.'
            while (isDigit(peek())) advance();
        }

        String text = source.substring(start, current);
        if (isDouble) {
            addToken(TokenType.NUMBER, Double.parseDouble(text));
        } else {
            addToken(TokenType.NUMBER, Integer.parseInt(text));
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
        addToken(type);
    }

    // --- helpers ---

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;
        advance(); // usa advance() para rastrear coluna
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
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

    private void lexError(String message) {
        errors.add(new CompilerError(line, startColumn, message, CompilerError.ErrorType.LEXICAL));
    }
}
