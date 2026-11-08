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
 *   SHOW_SYMBOL_TABLE → exibe a tabela de símbolos (variáveis declaradas)
 *   SHOW_TOKENS       → exibe a lista de tokens com prefixos [PR]/[ID]
 */
public class Main {

    // Exibe os 9 testes manuais após o pipeline principal.
    public static boolean SHOW_TESTS = false;

    // Exibe a tabela de símbolos no formato: | ID | Tipo | Nome | Valor |
    public static boolean SHOW_SYMBOL_TABLE = true;

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
            public class Main {
                public static void main() {
                    int x = 5;
                    int y = 10;
                    if (x < y) {
                        System.out.println(x + y);
                    } else {
                        x = x - 1;
                    }
                    while (x > 0) {
                        x = x - 1;
                    }
                }

                int soma(int a, int b) {
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

        // --- Exibição da tabela de símbolos (controlada pela alavanca) ---
        if (SHOW_SYMBOL_TABLE) {
            System.out.println("=== TABELA DE SÍMBOLOS ===\n");
            printSymbolTable(symbolTable);
            System.out.println();
        }

        // --- Exibição dos tokens (controlada pela alavanca) ---
        if (SHOW_TOKENS) {
            System.out.println("=== TOKENS ===\n");
            for (Token token : tokens) {
                if (token.type != TokenType.EOF) System.out.println(token);
            }
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
     * printSymbolTable() — imprime a tabela de símbolos em formato tabular.
     *
     * Exibe todos os identificadores declarados no programa na ordem em que
     * foram criados, com ID, tipo, nome e valor inicial.
     *
     * Exemplo:
     *   | ID     | Tipo       | Nome         | Valor        |
     *   |--------|------------|--------------|--------------|
     *   | #1     | INT        | x            | 5            |
     *   | #2     | INT        | y            | 10           |
     */
    public static void printSymbolTable(SymbolTable symbolTable) {
        List<Symbol> symbols = symbolTable.getAll();
        if (symbols.isEmpty()) {
            System.out.println("(nenhum identificador declarado)");
            return;
        }

        System.out.printf("| %-6s | %-10s | %-12s | %-12s |%n", "ID", "Tipo", "Nome", "Valor");
        System.out.println("|--------|------------|--------------|--------------|");
        for (Symbol sym : symbols) {
            String value = sym.value != null ? String.valueOf(sym.value) : "null";
            System.out.printf("| #%-5d | %-10s | %-12s | %-12s |%n",
                    sym.id, sym.type, sym.name, value);
        }
    }
}
