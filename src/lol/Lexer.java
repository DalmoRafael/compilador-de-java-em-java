package lol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Lexer — analisador léxico (primeira fase do compilador).
 *
 * O Lexer lê o código-fonte caractere por caractere e agrupa os caracteres em
 * tokens — as menores unidades com significado. Por exemplo, os caracteres
 * 'i', 'n', 't', ' ', 'x', ' ', '=', ' ', '5', ';' se tornam os tokens:
 *   INT  'int'  (linha 1, coluna 1)
 *   IDENTIFIER 'x'  (linha 1, coluna 5)
 *   ASSIGN '='  (linha 1, coluna 7)
 *   NUMBER '5'  (linha 1, coluna 9)
 *   SEMICOLON ';'  (linha 1, coluna 10)
 *
 * Rastreamento de posição:
 *   - 'line' conta as linhas (incrementa a cada '\n')
 *   - 'column' conta a coluna dentro da linha atual (reseta para 1 a cada '\n')
 *   - 'start' e 'startColumn' guardam onde o token atual começou
 *
 * Recuperação de erros:
 *   Em vez de lançar uma exceção e parar na primeira problema, o Lexer registra
 *   o erro na lista 'errors' e continua lendo. Assim é possível reportar vários
 *   erros léxicos de uma só vez.
 */
public class Lexer {

    /*
     * KEYWORDS — mapa estático que associa cada palavra reservada ao seu TokenType.
     * É preenchido automaticamente a partir de TokenType (campo 'keyword').
     *
     * Quando o Lexer lê uma sequência de letras (ex: "int", "if", "x"), ele consulta
     * este mapa. Se encontrar, produz o token da palavra reservada correspondente.
     * Se não encontrar, produz IDENTIFIER (é um nome criado pelo programador).
     */
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        // Cada palavra reservada declara o seu texto no próprio TokenType;
        // aqui só montamos o mapa texto → tipo a partir dessas declarações.
        for (TokenType type : TokenType.values()) {
            if (type.keyword != null) KEYWORDS.put(type.keyword, type);
        }
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<CompilerError> errors = new ArrayList<>();

    private int start = 0;       // índice no source onde o token atual começa
    private int current = 0;     // índice do próximo caractere a ser lido
    private int line = 1;        // linha atual (começa em 1)
    private int column = 1;      // coluna atual (começa em 1, reseta a cada '\n')
    private int startColumn = 1; // coluna onde o token atual começou

    public Lexer(String source) {
        this.source = source;
    }

    /*
     * scanTokens() — ponto de entrada: varre o código-fonte inteiro.
     *
     * A cada iteração, marca o início do próximo token (start, startColumn)
     * e chama scanToken() para identificar e consumir um token.
     * Ao terminar, adiciona um token EOF para sinalizar o fim da entrada.
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

    // Retorna os erros léxicos coletados durante a varredura.
    public List<CompilerError> getErrors() {
        return errors;
    }

    // -------------------------------------------------------------------------
    // Varredura de um único token
    // -------------------------------------------------------------------------

    /*
     * scanToken() — lê um caractere e decide qual token produzir.
     *
     * Este é o coração do Lexer. Usa um switch no caractere atual para
     * tratar cada caso: espaços ignorados, delimitadores simples, operadores
     * que podem ter um ou dois caracteres (=, ==, !=, etc.), comentários,
     * strings e números.
     */
    private void scanToken() {
        char c = advance();
        switch (c) {
            // Espaços em branco não geram tokens — simplesmente ignorados.
            // A quebra de linha '\n' já foi tratada dentro de advance().
            case ' ', '\r', '\t', '\n' -> {}

            // Delimitadores de um único caractere — cada um gera exatamente um token.
            case '(' -> addToken(TokenType.LPAREN);
            case ')' -> addToken(TokenType.RPAREN);
            case '{' -> addToken(TokenType.LBRACE);
            case '}' -> addToken(TokenType.RBRACE);
            case '[' -> addToken(TokenType.LBRACKET);
            case ']' -> addToken(TokenType.RBRACKET);
            case ';' -> addToken(TokenType.SEMICOLON);
            case ',' -> addToken(TokenType.COMMA);
            case '.' -> addToken(TokenType.DOT);
            case '+' -> addToken(TokenType.PLUS);
            case '-' -> addToken(TokenType.MINUS);
            case '*' -> addToken(TokenType.STAR);
            case '%' -> addToken(TokenType.PERCENT);

            // Operadores que podem ter um ou dois caracteres.
            // match() consome o próximo caractere apenas se for o esperado.
            //   '='  sozinho → ASSIGN (atribuição)
            //   '==' → EQUALS (comparação de igualdade)
            case '=' -> addToken(match('=') ? TokenType.EQUALS       : TokenType.ASSIGN);
            case '!' -> addToken(match('=') ? TokenType.NOT_EQUALS    : TokenType.NOT);
            case '<' -> addToken(match('=') ? TokenType.LESS_EQUAL    : TokenType.LESS);
            case '>' -> addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);

            // '&' sozinho é inválido na linguagem; só aceita '&&'
            case '&' -> {
                if (match('&')) addToken(TokenType.AND);
                else lexError("Caractere inesperado: '&' (esperava '&&')");
            }
            // '|' sozinho é inválido na linguagem; só aceita '||'
            case '|' -> {
                if (match('|')) addToken(TokenType.OR);
                else lexError("Caractere inesperado: '|' (esperava '||')");
            }

            // '/' pode ser divisão, comentário de linha (//) ou bloco (/* */)
            case '/' -> {
                if      (match('/')) skipLineComment();
                else if (match('*')) blockComment();
                else                 addToken(TokenType.SLASH);
            }

            // Início de string literal
            case '"' -> string();

            // Qualquer outro caractere: número, identificador/palavra-chave ou erro
            default -> {
                if      (isDigit(c))  number();
                else if (isAlpha(c))  identifier();
                else lexError("Caractere inesperado: '" + c + "'");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Leitura de comentários
    // -------------------------------------------------------------------------

    /*
     * skipLineComment() — ignora tudo até o fim da linha.
     * Chamado depois de consumir "//".
     */
    private void skipLineComment() {
        while (peek() != '\n' && !isAtEnd()) advance();
    }

    /*
     * blockComment() — ignora tudo até encontrar o fechamento "* /".
     * Chamado depois de consumir "/*". Se chegar ao fim do arquivo sem fechar,
     * registra um erro léxico.
     */
    private void blockComment() {
        while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) {
            advance(); // advance() já conta linhas e colunas corretamente
        }
        if (isAtEnd()) {
            lexError("Comentário de bloco não foi fechado");
            return;
        }
        advance(); // consome o '*'
        advance(); // consome o '/'
    }

    // -------------------------------------------------------------------------
    // Leitura de literais
    // -------------------------------------------------------------------------

    /*
     * string() — lê uma string literal entre aspas duplas.
     *
     * Consome caracteres até encontrar o fechamento '"'. O valor armazenado
     * no token é o texto sem as aspas. Se o arquivo terminar antes do '"',
     * registra um erro léxico.
     */
    private void string() {
        StringBuilder sb = new StringBuilder();
        while (peek() != '"' && !isAtEnd()) {
            sb.append(advance());
        }
        if (isAtEnd()) {
            lexError("String não foi fechada");
            return;
        }
        advance(); // consome o '"' de fechamento
        addToken(TokenType.STRING_LITERAL, sb.toString()); // literal = conteúdo sem as aspas
    }

    /*
     * number() — lê um número inteiro ou decimal.
     *
     * Consome dígitos. Se encontrar '.' seguido de dígito, continua consumindo
     * para tratar números decimais (ex: 3.14). O literal é convertido para
     * Integer ou Double conforme o caso.
     */
    private void number() {
        while (isDigit(peek())) advance();

        // Verifica se é um decimal: precisa de '.' seguido de pelo menos um dígito
        boolean isDouble = peek() == '.' && isDigit(peekNext());
        if (isDouble) {
            advance(); // consome o '.'
            while (isDigit(peek())) advance();
        }

        String text = source.substring(start, current);
        if (isDouble) {
            addToken(TokenType.NUMBER, Double.parseDouble(text));
        } else {
            try {
                addToken(TokenType.NUMBER, Integer.parseInt(text));
            } catch (NumberFormatException e) {
                lexError("Número inteiro fora do limite: " + text);
                addToken(TokenType.NUMBER, null); // mantém o token para não gerar erro em cascata no parser
            }
        }
    }

    /*
     * identifier() — lê um identificador ou palavra reservada.
     *
     * Consome letras, dígitos e '_' enquanto houver. Depois consulta o mapa
     * KEYWORDS para ver se o texto é uma palavra reservada (ex: "int", "if")
     * ou um identificador criado pelo programador (ex: "x", "soma").
     */
    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        // Se o texto está no mapa, é palavra reservada; senão, é IDENTIFIER
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
        addToken(type);
    }

    // -------------------------------------------------------------------------
    // Helpers de leitura (navegação pelo código-fonte)
    // -------------------------------------------------------------------------

    /*
     * match() — lê o próximo caractere condicionalmente (look-ahead de 1).
     *
     * Se o próximo caractere for igual ao esperado, consome-o e retorna true.
     * Caso contrário, não avança e retorna false.
     * Usado para reconhecer operadores de dois caracteres (==, !=, <=, >=, &&, ||).
     */
    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;
        advance();
        return true;
    }

    /*
     * peek() — retorna o caractere atual SEM consumi-lo (look-ahead de 1).
     * Retorna '\0' se chegou ao fim do arquivo.
     */
    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    /*
     * peekNext() — retorna o caractere SEGUINTE sem consumi-lo (look-ahead de 2).
     * Usado em number() para saber se o '.' faz parte de um decimal,
     * e em blockComment() para detectar o fechamento "* /".
     */
    private char peekNext() {
        return (current + 1 >= source.length()) ? '\0' : source.charAt(current + 1);
    }

    // Verifica se o caractere é um dígito decimal (0–9).
    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    // Verifica se o caractere pode iniciar um identificador (letra ou underscore).
    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    // Verifica se o caractere pode continuar um identificador (letra, dígito ou underscore).
    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    // Verifica se todos os caracteres do código-fonte já foram lidos.
    private boolean isAtEnd() {
        return current >= source.length();
    }

    /*
     * advance() — consome e retorna o caractere atual, atualizando a posição.
     *
     * É o único lugar onde 'current' é incrementado. Também atualiza 'line'
     * e 'column': ao encontrar '\n', avança a linha e reseta a coluna para 1.
     */
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

    // Variante de addToken() sem valor literal (para operadores e delimitadores).
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    /*
     * addToken() — cria um Token com o texto do lexema atual e o adiciona à lista.
     *
     * O lexema é extraído de source usando os índices 'start' e 'current',
     * que delimitam o trecho de texto que compõe o token.
     * A coluna usada é 'startColumn' (onde o token começou, não onde terminou).
     */
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line, startColumn));
    }

    /*
     * lexError() — registra um erro léxico sem interromper a varredura.
     *
     * Em vez de lançar uma exceção, adiciona o erro à lista e continua.
     * Isso permite que o Lexer encontre e reporte vários erros de uma vez.
     */
    private void lexError(String message) {
        errors.add(new CompilerError(line, startColumn, message, CompilerError.ErrorType.LEXICAL));
    }
}
