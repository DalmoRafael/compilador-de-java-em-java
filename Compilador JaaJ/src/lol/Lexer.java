package lol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final SymbolTable symbolTable = new SymbolTable();

    private int start = 0;   // inicio do lexema atual
    private int current = 0; // posicao atual de leitura
    private int line = 1;

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("int", TokenType.INT);
        KEYWORDS.put("double", TokenType.DOUBLE);
        KEYWORDS.put("boolean", TokenType.BOOLEAN);
        KEYWORDS.put("void", TokenType.VOID);
        KEYWORDS.put("String", TokenType.STRING); // Adicionado aqui como palavra-chave/tipo
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
    }

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case ' ':
            case '\r':
            case '\t':
                break;
            case '\n':
                line++;
                break;

            case '(': addToken(TokenType.LPAREN); break;
            case ')': addToken(TokenType.RPAREN); break;
            case '{': addToken(TokenType.LBRACE); break;
            case '}': addToken(TokenType.RBRACE); break;
            case '[': addToken(TokenType.LBRACKET); break;
            case ']': addToken(TokenType.RBRACKET); break;
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
                if (match('&')) addToken(TokenType.AND);
                else throw error("Caractere inesperado: '&' (esperava '&&')");
                break;
            case '|':
                if (match('|')) addToken(TokenType.OR);
                else throw error("Caractere inesperado: '|' (esperava '||')");
                break;

            case '/':
                if (match('/')) {
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
                    throw error("Caractere inesperado: '" + c + "'");
                }
        }
    }

    private void blockComment() {
        while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }
        if (isAtEnd()) {
            throw error("Comentario de bloco nao foi fechado");
        }
        advance();
        advance();
    }

    private void string() {
        StringBuilder sb = new StringBuilder();
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            sb.append(advance());
        }
        if (isAtEnd()) {
            throw error("String nao foi fechada");
        }
        advance();
        addToken(TokenType.STRING_LITERAL, sb.toString()); // Usa STRING_LITERAL para o conteúdo entre aspas
    }

    private void number() {
        while (isDigit(peek())) advance();

        boolean isDouble = false;
        if (peek() == '.' && isDigit(peekNext())) {
            isDouble = true;
            advance();
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

        Integer Index = null;
        if (type == TokenType.IDENTIFIER) {
            Index = symbolTable.define(text);
        }

        addToken(type, null, Index);
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;
        current++;
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
        return source.charAt(current++);
    }

    private void addToken(TokenType type) {
        addToken(type, null, null);
    }

    private void addToken(TokenType type, Object literal) {
        addToken(type, literal, null);
    }

    private void addToken(TokenType type, Object literal, Integer symbolIndex) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line, symbolIndex));
    }

    private RuntimeException error(String message) {
        return new RuntimeException("[linha " + line + "] Erro lexico: " + message);
    }
}