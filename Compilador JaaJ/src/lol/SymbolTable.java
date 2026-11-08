package lol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SymbolTable {
    private final Map<String, Integer> symbols = new LinkedHashMap<>();
    private final List<String> symbolList = new ArrayList<>();

    public int define(String name) {
        if (symbols.containsKey(name)) {
            return symbols.get(name);
        }
        int index = symbolList.size();
        symbolList.add(name);
        symbols.put(name, index);
        return index;
    }

    public Integer getIndex(String name) {
        return symbols.get(name);
    }

    public void printTable() {
        System.out.println("=== TABELA DE SÍMBOLOS ===");
        if (symbolList.isEmpty()) {
            System.out.println("(Tabela vazia)");
        } else {
            for (int i = 0; i < symbolList.size(); i++) {
                System.out.printf("[%d] %s%n", i, symbolList.get(i));
            }
        }
    }
}