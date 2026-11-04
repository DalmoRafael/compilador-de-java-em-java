package tests;

import lol.CompilerError;
import lol.Lexer;
import lol.Main;
import lol.Parser;
import lol.SemanticAnalyzer;
import lol.Stmt;
import lol.Symbol;
import lol.SymbolTable;
import lol.Token;
import lol.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Testes manuais do compilador.
 * Executados quando {@link Main#SHOW_TESTS} for {@code true}.
 */
public class Tests {

    public static void runAll() {
        test(1, "Rastreamento de coluna",       Tests::testColumnTracking);
        test(2, "Erros léxicos",                Tests::testLexerErrors);
        test(3, "Prefixos [PR]/[ID]",           Tests::testTokenPrefixes);
        test(4, "Tabela de símbolos",           Tests::testSymbolTable);
        test(5, "Erros sintáticos",             Tests::testParserErrors);
        test(6, "Erros semânticos",             Tests::testSemanticErrors);
        test(7, "Código válido (sem erros)",    Tests::testValidCode);
        test(8, "Multi-linha (reset de coluna)",Tests::testMultiLine);
        test(9, "Formato javac com erros",      Tests::testErrorFormat);
    }

    private static void test(int number, String title, Runnable body) {
        System.out.println("\n--- Teste " + number + ": " + title + " ---");
        body.run();
    }

    // =========================================================================
    // Testes individuais
    // =========================================================================

    static void testColumnTracking() {
        String code = "int x = 5;";
        List<Token> tokens = scan(code);

        System.out.println("Código: \"" + code + "\"");
        System.out.println("Esperado: int=col1, x=col5, ==col7, 5=col9, ;=col10\n");
        printTokens(tokens);
    }

    static void testLexerErrors() {
        String code = "int @ x = 5;\n& \n\"string nao fechada";
        Lexer lexer = new Lexer(code);
        lexer.scanTokens();

        System.out.println("Código com erros léxicos:\n" + code + "\n");
        List<CompilerError> errors = lexer.getErrors();
        System.out.println("Erros encontrados: " + errors.size());
        errors.forEach(e -> System.out.println("  " + e));
    }

    static void testTokenPrefixes() {
        String code = "int x = 5;\nif (true) { }";
        System.out.println("Código: " + code.replace("\n", " | ") + "\n");
        printTokens(scan(code));
    }

    static void testSymbolTable() {
        SymbolTable st = new SymbolTable();
        st.enterScope();
        Symbol s1 = st.declare("INT",    "x", 5,    1, 5);
        Symbol s2 = st.declare("DOUBLE", "y", 3.14, 2, 5);

        System.out.println("Declarados: x=#" + s1.id + ", y=#" + s2.id);
        System.out.println("Lookup x: " + symbolRef(st.lookup("x")));
        System.out.println("Lookup z: " + symbolRef(st.lookup("z")));

        // Shadowing
        st.enterScope();
        Symbol s3 = st.declare("INT", "x", 10, 3, 5);
        System.out.println("\nNovo escopo — x=#" + s3.id + " (shadow)");
        System.out.println("Lookup x (interno): " + symbolRef(st.lookup("x")));
        st.exitScope();
        System.out.println("Lookup x (após sair): " + symbolRef(st.lookup("x")));
        st.exitScope();

        System.out.println("\nTabela final:");
        Main.printSymbolTable(st);
    }

    static void testParserErrors() {
        String code = """
        public class Main {
            public static void main() {
                int = 5;
                if x < y {
                    x = 1;
                }
                int z = 10;
            }
        }
        """;
        System.out.println("Código com erros sintáticos:\n" + code);

        Parser parser = parse(code);
        List<CompilerError> errors = parser.getErrors();
        System.out.println("Erros sintáticos: " + errors.size());
        errors.forEach(e -> System.out.println("  " + e));

        System.out.println("\nTabela de símbolos (parcial):");
        Main.printSymbolTable(parser.getSymbolTable());
    }

    static void testSemanticErrors() {
        String code = """
        public class Main {
            public static void main() {
                int x = 5;
                int x = 10;
                y = 10;
                int z = "texto";
            }
        }
        """;
        System.out.println("Código com erros semânticos:\n" + code);

        Parser parser = parse(code);
        SemanticAnalyzer analyzer = new SemanticAnalyzer(parser.getSymbolTable());
        List<CompilerError> errors = analyzer.analyze(parser.parse() /* já foi chamado, mas seguro */);

        // Roda o pipeline completo para obter os erros semânticos corretamente
        List<CompilerError> sem = runAndCollectErrors(code);
        List<CompilerError> semanticOnly = sem.stream()
                .filter(e -> e.type == CompilerError.ErrorType.SEMANTIC)
                .toList();
        System.out.println("Erros semânticos: " + semanticOnly.size());
        semanticOnly.forEach(e -> System.out.println("  " + e));
    }

    static void testValidCode() {
        String code = """
        public class Main {
            public static void main() {
                int x = 5;
                int y = 10;
                if (x < y) {
                    System.out.println(x + y);
                }
                while (x > 0) {
                    x = x - 1;
                }
            }
        }
        """;
        System.out.println("Código válido:\n" + code);

        List<CompilerError> errors = runAndCollectErrors(code);
        if (errors.isEmpty()) {
            System.out.println("OK: nenhum erro encontrado\n");
        } else {
            System.out.println("ERROS (" + errors.size() + "):");
            errors.forEach(e -> System.out.println("  " + e));
        }

        System.out.println("Tabela de símbolos:");
        Lexer lexer = new Lexer(code);
        Parser parser = new Parser(lexer.scanTokens());
        parser.parse();
        Main.printSymbolTable(parser.getSymbolTable());
    }

    static void testMultiLine() {
        String code = "int a = 1;\nint b = 2;\nint c = 3;";
        System.out.println("Código:\n" + code + "\n");
        printTokens(scan(code));
    }

    static void testErrorFormat() {
        String fileName = "Teste.java";
        String code = """
        public class Main {
            public static void main() {
                int x = 5;
                int x = 10;
                y = 20;
            }
        }
        """;
        System.out.println("Código com erros:\n" + code);

        List<CompilerError> errors = runAndCollectErrors(code);
        errors.sort((a, b) -> a.line != b.line
                ? Integer.compare(a.line, b.line)
                : Integer.compare(a.column, b.column));

        System.out.println("Erros no formato javac:\n");
        errors.forEach(e -> Main.printError(e, code, fileName));
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    /** Roda o lexer e retorna os tokens (excluindo EOF). */
    private static List<Token> scan(String code) {
        return new Lexer(code).scanTokens();
    }

    /** Roda o lexer e o parser e retorna o parser para acesso a erros e tabela. */
    private static Parser parse(String code) {
        Lexer lexer = new Lexer(code);
        Parser parser = new Parser(lexer.scanTokens());
        parser.parse();
        return parser;
    }

    /** Roda o pipeline completo e retorna todos os erros coletados. */
    private static List<CompilerError> runAndCollectErrors(String code) {
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        List<CompilerError> errors = new ArrayList<>(lexer.getErrors());

        Parser parser = new Parser(tokens);
        List<Stmt> ast = parser.parse();
        errors.addAll(parser.getErrors());

        SemanticAnalyzer analyzer = new SemanticAnalyzer(parser.getSymbolTable());
        errors.addAll(analyzer.analyze(ast));
        return errors;
    }

    /** Imprime tokens, excluindo EOF. */
    private static void printTokens(List<Token> tokens) {
        tokens.stream()
              .filter(t -> t.type != TokenType.EOF)
              .forEach(System.out::println);
    }

    /** Retorna string de referência de um símbolo ou "null". */
    private static String symbolRef(Symbol sym) {
        return sym != null ? "#" + sym.id : "null";
    }
}
