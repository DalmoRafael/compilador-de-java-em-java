package lol;

import lol.Lexer;
import lol.Token;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

    public class Main {
        public static void main(String[] args) throws Exception {
            String source;

            if (args.length > 0) {
                source = new String(Files.readAllBytes(Paths.get(args[0])));
            } else {

                source = """
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
                """;
            }

            Lexer lexer = new Lexer(source);
            List<Token> tokens = lexer.scanTokens();

            System.out.println("=== TOKENS ===");
            for (Token token : tokens) {
                System.out.println(token);
            }
        }
    }

