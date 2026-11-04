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
 * Executados quando Main.SHOW_TESTS = true.
 */
public class Tests {

    public static void runAll() {
        System.out.println("--- Teste 1: Rastreamento de coluna ---");
        testColumnTracking();

        System.out.println("\n--- Teste 2: Erros lexicos ---");
        testLexerErrors();

        System.out.println("\n--- Teste 3: Prefixos [PR]/[ID] ---");
        testTokenPrefixes();

        System.out.println("\n--- Teste 4: Tabela de simbolos ---");
        testSymbolTable();

        System.out.println("\n--- Teste 5: Erros sintaticos ---");
        testParserErrors();

        System.out.println("\n--- Teste 6: Erros semanticos ---");
        testSemanticErrors();

        System.out.println("\n--- Teste 7: Codigo valido (sem erros) ---");
        testValidCode();

        System.out.println("\n--- Teste 8: Multi-linha (reset de coluna) ---");
        testMultiLine();

        System.out.println("\n--- Teste 9: Formato javac com erros ---");
        testErrorFormat();
    }

    static void testColumnTracking() {
        String code = "int x = 5;";
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();

        System.out.println("Codigo: \"" + code + "\"");
        System.out.println("Esperado: int=col1, x=col5, ==col7, 5=col9, ;=col10\n");
        for (Token t : tokens) {
            if (t.type != TokenType.EOF) System.out.println(t);
        }
    }

    static void testLexerErrors() {
        String code = "int @ x = 5;\n& \n\"string nao fechada";
        Lexer lexer = new Lexer(code);
        lexer.scanTokens();

        System.out.println("Codigo com erros lexicos:\n" + code + "\n");

        List<CompilerError> errors = lexer.getErrors();
        System.out.println("Erros encontrados: " + errors.size());
        for (CompilerError e : errors) {
            System.out.println("  " + e);
        }
    }

    static void testTokenPrefixes() {
        String code = "int x = 5;\nif (true) { }";
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();

        System.out.println("Codigo: " + code.replace("\n", " | ") + "\n");
        for (Token t : tokens) {
            if (t.type != TokenType.EOF) System.out.println(t);
        }
    }

    static void testSymbolTable() {
        SymbolTable st = new SymbolTable();

        st.enterScope();
        Symbol s1 = st.declare("INT", "x", 5, 1, 5);
        Symbol s2 = st.declare("DOUBLE", "y", 3.14, 2, 5);

        System.out.println("Declarados: x=#" + s1.id + ", y=#" + s2.id);
        System.out.println("Lookup x: " + (st.lookup("x") != null ? "#" + st.lookup("x").id : "null"));
        System.out.println("Lookup z: " + (st.lookup("z") != null ? "#" + st.lookup("z").id : "null"));

        // Testa shadowing entre escopos
        st.enterScope();
        Symbol s3 = st.declare("INT", "x", 10, 3, 5);
        System.out.println("\nNovo escopo, declarado x=#" + s3.id + " (shadow)");
        System.out.println("Lookup x (interno): #" + st.lookup("x").id);
        st.exitScope();
        System.out.println("Lookup x (apos sair): #" + st.lookup("x").id);
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

        System.out.println("Codigo com erros sintaticos:\n" + code);

        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        parser.parse();

        List<CompilerError> errors = parser.getErrors();
        System.out.println("Erros sintaticos: " + errors.size());
        for (CompilerError e : errors) {
            System.out.println("  " + e);
        }

        System.out.println("\nTabela de simbolos (parcial):");
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

        System.out.println("Codigo com erros semanticos:\n" + code);

        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        List<Stmt> ast = parser.parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer(parser.getSymbolTable());
        List<CompilerError> errors = analyzer.analyze(ast);

        System.out.println("Erros semanticos: " + errors.size());
        for (CompilerError e : errors) {
            System.out.println("  " + e);
        }
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

        System.out.println("Codigo valido:\n" + code);

        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        List<CompilerError> allErrors = new ArrayList<>(lexer.getErrors());

        Parser parser = new Parser(tokens);
        List<Stmt> ast = parser.parse();
        allErrors.addAll(parser.getErrors());

        SemanticAnalyzer analyzer = new SemanticAnalyzer(parser.getSymbolTable());
        allErrors.addAll(analyzer.analyze(ast));

        if (allErrors.isEmpty()) {
            System.out.println("OK: nenhum erro encontrado\n");
        } else {
            System.out.println("ERROS (" + allErrors.size() + "):");
            for (CompilerError e : allErrors) {
                System.out.println("  " + e);
            }
        }

        System.out.println("Tabela de simbolos:");
        Main.printSymbolTable(parser.getSymbolTable());
    }

    static void testMultiLine() {
        String code = "int a = 1;\nint b = 2;\nint c = 3;";
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();

        System.out.println("Codigo:\n" + code + "\n");
        for (Token t : tokens) {
            if (t.type != TokenType.EOF) System.out.println(t);
        }
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

        System.out.println("Codigo com erros:\n" + code);

        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        List<CompilerError> allErrors = new ArrayList<>(lexer.getErrors());

        Parser parser = new Parser(tokens);
        List<Stmt> ast = parser.parse();
        allErrors.addAll(parser.getErrors());

        SemanticAnalyzer analyzer = new SemanticAnalyzer(parser.getSymbolTable());
        allErrors.addAll(analyzer.analyze(ast));

        allErrors.sort((a, b) -> {
            if (a.line != b.line) return Integer.compare(a.line, b.line);
            return Integer.compare(a.column, b.column);
        });

        System.out.println("Erros no formato javac:\n");
        for (CompilerError error : allErrors) {
            Main.printError(error, code, fileName);
        }
    }
}
