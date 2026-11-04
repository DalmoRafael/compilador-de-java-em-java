package lol;

import tests.Tests;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Ponto de entrada do compilador.
 *
 * <p>Orquestra o pipeline completo: Lexer → Parser → SemanticAnalyzer,
 * exibe erros no formato javac e imprime a tabela de símbolos e a lista
 * de tokens conforme as alavancas booleanas abaixo.</p>
 */
public class Main {

    // =========================================================================
    // Alavancas de saída
    // =========================================================================

    /** Exibe os 9 testes manuais após o pipeline principal. */
    public static boolean SHOW_TESTS = false;

    /** Exibe a tabela de símbolos (ID, Tipo, Nome, Valor). */
    public static boolean SHOW_SYMBOL_TABLE = true;

    /** Exibe a lista de tokens com prefixos [PR]/[ID] e referências à tabela. */
    public static boolean SHOW_TOKENS = true;

    // =========================================================================
    // Entrada
    // =========================================================================

    public static void main(String[] args) throws Exception {
        String source;
        String fileName = "default";

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
    // Pipeline
    // =========================================================================

    static void runPipeline(String source, String fileName) {
        // 1. Análise léxica
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();
        List<CompilerError> allErrors = new ArrayList<>(lexer.getErrors());

        // 2. Análise sintática
        Parser parser = new Parser(tokens);
        List<Stmt> ast = parser.parse();
        allErrors.addAll(parser.getErrors());
        SymbolTable symbolTable = parser.getSymbolTable();

        // 3. Análise semântica
        SemanticAnalyzer analyzer = new SemanticAnalyzer(symbolTable);
        allErrors.addAll(analyzer.analyze(ast));

        // Ordena erros por linha e coluna para exibição consistente
        allErrors.sort((a, b) -> a.line != b.line
                ? Integer.compare(a.line, b.line)
                : Integer.compare(a.column, b.column));

        // --- Erros (formato javac) ---
        if (allErrors.isEmpty()) {
            System.out.println("=== Nenhum erro encontrado ===\n");
        } else {
            System.out.println("=== ERROS ===\n");
            for (CompilerError error : allErrors) printError(error, source, fileName);
            System.out.println();
        }

        // --- Tabela de símbolos ---
        if (SHOW_SYMBOL_TABLE) {
            System.out.println("=== TABELA DE SÍMBOLOS ===\n");
            printSymbolTable(symbolTable);
            System.out.println();
        }

        // --- Lista de tokens ---
        if (SHOW_TOKENS) {
            System.out.println("=== TOKENS ===\n");
            for (Token token : tokens) {
                if (token.type != TokenType.EOF) System.out.println(token);
            }
        }
    }

    // =========================================================================
    // Formatação de saída
    // =========================================================================

    /**
     * Imprime um erro no formato javac:
     * <pre>
     * arquivo:linha:coluna: error: mensagem
     *     linha do código fonte
     *     ^
     * </pre>
     */
    public static void printError(CompilerError error, String source, String fileName) {
        String[] sourceLines = source.split("\n", -1);
        String errorLine = (error.line >= 1 && error.line <= sourceLines.length)
                ? sourceLines[error.line - 1]
                : "";

        System.out.printf("%s:%d:%d: error: %s%n", fileName, error.line, error.column, error.message);
        System.out.println("    " + errorLine);

        // Posiciona o acento circunflexo na coluna do erro
        System.out.println("    " + " ".repeat(Math.max(0, error.column - 1)) + "^");
        System.out.println();
    }

    /**
     * Imprime a tabela de símbolos no formato tabular:
     * <pre>
     * | ID     | Tipo       | Nome         | Valor        |
     * </pre>
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
