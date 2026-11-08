package lol;

import tests.Tests;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/*
 * Main — ponto de entrada do compilador e orquestrador do pipeline.
 *
 * Um compilador clássico funciona como um pipeline em estágios:
 *
 *   Código fonte (texto)
 *       ↓
 *   [Lexer]  → Lista de Tokens  +  Erros léxicos
 *       ↓
 *   [Parser] → AST (árvore)     +  Erros sintáticos  +  Tabela de Símbolos
 *       ↓
 *   [SemanticAnalyzer] → Erros semânticos
 *       ↓
 *   Saída: erros formatados, tabela de símbolos, lista de tokens
 *
 * Esta classe:
 *   1. Recebe o código-fonte (de arquivo ou do texto embutido)
 *   2. Executa o pipeline acima
 *   3. Coleta e ordena todos os erros (de todas as fases)
 *   4. Exibe os resultados conforme as alavancas booleanas
 *
 * Alavancas (flags) de saída — altere para controlar o que aparece no terminal:
 *   SHOW_TESTS        → exibe os testes manuais (útil para validar o compilador)
 *   SHOW_SYMBOL_TABLE → exibe a tabela de símbolos (índice e nome)
 *   SHOW_SYMBOL_DETAILS → a tabela também mostra categoria, tipo, valor e linha
 *   SHOW_TOKENS       → exibe a lista de tokens com prefixos [PR]/[ID] e [Index: n]
 */
public class Main {

    // Exibe os 9 testes manuais após o pipeline principal.
    public static boolean SHOW_TESTS = false;

    // Exibe a tabela de símbolos (por padrão, só índice e nome).
    public static boolean SHOW_SYMBOL_TABLE = true;

    // Se true, a tabela de símbolos mostra também categoria, tipo, valor e linha.
    public static boolean SHOW_SYMBOL_DETAILS = false;

    // Exibe cada token com prefixo [PR] (palavra reservada) ou [ID] (identificador).
    public static boolean SHOW_TOKENS = true;

    // =========================================================================
    // Entrada
    // =========================================================================

    public static void main(String[] args) throws Exception {
        String source;
        String fileName = "default";

        // Se um arquivo foi passado como argumento, lê o arquivo.
        // Caso contrário, usa o código de exemplo embutido abaixo.
        if (args.length > 0) {
            source = new String(Files.readAllBytes(Paths.get(args[0])));
            fileName = args[0];
        } else {
            source = """
            public class Teste {
                public static void main(String[] args) {
                    int x = 5;
                    int y = 10;
                    double media = 3.14;
                    String nome = "lol";
                    if (x < y) {
                        System.out.println(x + y);
                    } else {
                        x = x - 1;
                    }
                    while (x > 0) {
                        x = x - 1;
                    }
                }

                static int soma(int a, int b) {
                    return a + b;
                }
            }
            """;
        }

        runPipeline(source, fileName);

        if (SHOW_TESTS) {
            System.out.println("\n\n========================================");
            System.out.println("    TESTES ADICIONAIS");
            System.out.println("========================================\n");
            Tests.runAll();
        }
    }

    // =========================================================================
    // Pipeline do compilador
    // =========================================================================

    /*
     * runPipeline() — executa as três fases do compilador em sequência.
     *
     * Cada fase produz erros que são acumulados na lista 'allErrors'.
     * Os erros de todas as fases são ordenados por linha e coluna antes
     * de serem exibidos, garantindo que apareçam na ordem em que ocorrem
     * no código-fonte.
     */
    static void runPipeline(String source, String fileName) {

        // --- Fase 1: Análise léxica ---
        // O Lexer lê o texto e produz a lista de tokens.
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();
        List<CompilerError> allErrors = new ArrayList<>(lexer.getErrors());

        // --- Fase 2: Análise sintática ---
        // O Parser transforma os tokens em uma AST e popula a tabela de símbolos.
        Parser parser = new Parser(tokens);
        List<Stmt> ast = parser.parse();
        allErrors.addAll(parser.getErrors());
        SymbolTable symbolTable = parser.getSymbolTable();

        // --- Fase 3: Análise semântica ---
        // O SemanticAnalyzer verifica regras de tipos, escopos e declarações.
        SemanticAnalyzer analyzer = new SemanticAnalyzer(symbolTable);
        allErrors.addAll(analyzer.analyze(ast));

        // Ordena todos os erros por linha e coluna para exibição na ordem do código
        allErrors.sort((a, b) -> a.line != b.line
                ? Integer.compare(a.line, b.line)
                : Integer.compare(a.column, b.column));

        // --- Exibição dos erros ---
        if (allErrors.isEmpty()) {
            System.out.println("=== Nenhum erro encontrado ===\n");
        } else {
            System.out.println("=== ERROS ===\n");
            for (CompilerError error : allErrors) printError(error, source, fileName);
            System.out.println();
        }

        // --- Exibição dos tokens (controlada pela alavanca) ---
        // Cada identificador da tabela mostra o índice do símbolo: 'x' [Index: 3]
        if (SHOW_TOKENS) {
            System.out.println("=== TOKENS ===\n");
            for (Token token : tokens) {
                if (token.type != TokenType.EOF) System.out.println(token);
            }
            System.out.println();
        }

        // --- Exibição da tabela de símbolos (controlada pela alavanca) ---
        if (SHOW_SYMBOL_TABLE) {
            System.out.println("=== TABELA DE SÍMBOLOS ===\n");
            printSymbolTable(symbolTable);
        }
    }

    // =========================================================================
    // Métodos de formatação de saída
    // =========================================================================

    /*
     * printError() — imprime um erro no formato do compilador javac.
     *
     * Formato:
     *   arquivo.java:linha:coluna: error: mensagem do erro
     *       trecho da linha onde o erro ocorreu
     *       ^
     *
     * O acento circunflexo (^) aponta exatamente para a coluna do erro,
     * igual ao comportamento do javac ao compilar código Java.
     */
    public static void printError(CompilerError error, String source, String fileName) {
        String[] sourceLines = source.split("\n", -1);
        // Recupera a linha de código onde o erro ocorreu
        String errorLine = (error.line >= 1 && error.line <= sourceLines.length)
                ? sourceLines[error.line - 1]
                : "";

        System.out.printf("%s:%d:%d: error: %s%n", fileName, error.line, error.column, error.message);
        System.out.println("    " + errorLine);
        // Posiciona o ^ na coluna correta (4 espaços de margem + (coluna-1) espaços)
        System.out.println("    " + " ".repeat(Math.max(0, error.column - 1)) + "^");
        System.out.println();
    }

    /*
     * printSymbolTable() — imprime a tabela de símbolos.
     *
     * Por padrão mostra só o índice e o nome de cada símbolo (o mesmo índice
     * que aparece como [Index: n] na lista de tokens):
     *
     *   [0] Teste
     *   [1] main
     *   [2] args
     *
     * Com SHOW_SYMBOL_DETAILS = true, mostra também categoria, tipo, valor e linha.
     */
    public static void printSymbolTable(SymbolTable symbolTable) {
        List<Symbol> symbols = symbolTable.getAll();
        if (symbols.isEmpty()) {
            System.out.println("(nenhum identificador declarado)");
            return;
        }

        if (!SHOW_SYMBOL_DETAILS) {
            System.out.printf("%-7s %s%n", "INDEX", "NOME");
            System.out.println("-".repeat(24));
            for (Symbol sym : symbols) {
                System.out.printf("[%1d]   %s%n", sym.id, sym.name);
            }
            return;
        }

        System.out.printf("%-7s %-10s %-10s %-12s %-10s %s%n",
                "INDEX", "CATEGORIA", "TIPO", "NOME", "VALOR", "LINHA");
        System.out.println("-".repeat(62));
        for (Symbol sym : symbols) {
            String value = sym.value != null ? String.valueOf(sym.value) : "-";
            System.out.printf("[%3d]   %-10s %-10s %-12s %-10s %d%n",
                    sym.id, sym.kind, sym.type, sym.name, value, sym.line);
        }
    }
}
