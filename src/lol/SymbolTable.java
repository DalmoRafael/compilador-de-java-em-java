package lol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabela de simbolos com suporte a escopos aninhados.
 * Mantem uma lista global de todos os simbolos (para exibicao) e uma pilha
 * de escopos para lookup por nome.
 */
public class SymbolTable {
    private final List<Symbol> symbols = new ArrayList<>();
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private int nextId = 1;

    public SymbolTable() {
        scopes.push(new HashMap<>());
    }

    /**
     * Declara uma variavel no escopo atual. Retorna o simbolo criado.
     */
    public Symbol declare(String type, String name, Object value, int line, int column) {
        Symbol symbol = new Symbol(nextId++, type, name, value, line, column);
        symbols.add(symbol);
        scopes.peek().put(name, symbol);
        return symbol;
    }

    /**
     * Busca uma variavel por nome, do escopo mais interno para o mais externo.
     */
    public Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return null;
    }

    /**
     * Verifica se a variavel ja foi declarada no escopo atual.
     */
    public boolean isDeclaredInCurrentScope(String name) {
        return scopes.peek().containsKey(name);
    }

    public Symbol getById(int id) {
        for (Symbol s : symbols) {
            if (s.id == id) return s;
        }
        return null;
    }

    public void enterScope() {
        scopes.push(new HashMap<>());
    }

    public void exitScope() {
        scopes.pop();
    }

    public List<Symbol> getAll() {
        return symbols;
    }
}
