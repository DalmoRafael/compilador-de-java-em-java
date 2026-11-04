package lol;

import tests.Tests;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    /** Alavanca para mostrar os testes no terminal. */
    public static boolean SHOW_TESTS = false;

    /** Alavanca para mostrar a tabela de simbolos. */
    public static boolean SHOW_SYMBOL_TABLE = false;

    /** Alavanca para mostrar a lista de tokens. */
    public static boolean SHOW_TOKENS = false;

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

    // === Pipeline completo ===

    static void runPipeline(String source, String fileName) {
        List<CompilerError> allErrors = new ArrayList<>();

        // 1. Lexer
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();
        allErrors.addAll(lexer.getErrors());

        // 2. Parser
        Parser parser = new Parser(tokens);
        List<Stmt> ast = parser.parse();
        allErrors.addAll(parser.getErrors());
        SymbolTable symbolTable = parser.getSymbolTable();

        // 3. Analise semantica
        SemanticAnalyzer analyzer = new SemanticAnalyzer(symbolTable);
        allErrors.addAll(analyzer.analyze(ast));

        // Ordena erros por linha e coluna
        allErrors.sort((a, b) -> {
            if (a.line != b.line) return Integer.compare(a.line, b.line);
            return Integer.compare(a.column, b.column);
        });

        // Imprime erros (formato javac)
        if (!allErrors.isEmpty()) {
            System.out.println("=== ERROS ===\n");
            for (CompilerError error : allErrors) {
                printError(error, source, fileName);
            }
            System.out.println();
        } else {
            System.out.println("=== Nenhum erro encontrado ===\n");
        }

        // Imprime tabela de simbolos
        if (SHOW_SYMBOL_TABLE) {
            System.out.println("=== TABELA DE SIMBOLOS ===\n");
            printSymbolTable(symbolTable);
            System.out.println();
        }

        // Imprime tokens
        if (SHOW_TOKENS) {
            System.out.println("=== TOKENS ===\n");
            for (Token token : tokens) {
                if (token.type != TokenType.EOF) {
                    System.out.println(token);
                }
            }
        }
    }

    // === Formatação de saída ===

    public static void printError(CompilerError error, String source, String fileName) {
        String[] lines = source.split("\n", -1);
        String errorLine = "";
        if (error.line >= 1 && error.line <= lines.length) {
            errorLine = lines[error.line - 1];
        }

        System.out.printf("%s:%d:%d: error: %s%n", fileName, error.line, error.column, error.message);
        System.out.println("    " + errorLine);
        StringBuilder caret = new StringBuilder("    ");
        for (int i = 1; i < error.column; i++) {
            caret.append(" ");
        }
        caret.append("^");
        System.out.println(caret);
        System.out.println();
    }

    public static void printSymbolTable(SymbolTable symbolTable) {
        List<Symbol> symbols = symbolTable.getAll();
        if (symbols.isEmpty()) {
            System.out.println("(nenhum identificador declarado)");
            return;
        }

        System.out.printf("| %-6s | %-10s | %-12s | %-12s |%n", "ID", "Tipo", "Nome", "Valor");
        System.out.println("|--------|------------|--------------|--------------|");
        for (Symbol sym : symbols) {
            String valueStr = sym.value != null ? String.valueOf(sym.value) : "null";
            System.out.printf("| #%-5d | %-10s | %-12s | %-12s |%n",
                    sym.id, sym.type, sym.name, valueStr);
        }
    }
}
