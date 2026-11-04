package lol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabela de símbolos com suporte a escopos aninhados.
 *
 * <p>Mantém duas estruturas em paralelo:</p>
 * <ul>
 *   <li>{@code symbols} — lista global ordenada por declaração (usada na exibição).</li>
 *   <li>{@code scopes} — pilha de mapas nome→símbolo; o topo é o escopo mais interno.</li>
 * </ul>
 *
 * <p>O lookup percorre a pilha do topo para a base, respeitando o shadowing.</p>
 */
public class SymbolTable {

    private final List<Symbol> symbols = new ArrayList<>();
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private int nextId = 1;

    public SymbolTable() {
        // Escopo global inicial
        scopes.push(new HashMap<>());
    }

    /**
     * Declara uma variável no escopo atual e retorna o símbolo criado.
     */
    public Symbol declare(String type, String name, Object value, int line, int column) {
        Symbol symbol = new Symbol(nextId++, type, name, value, line, column);
        symbols.add(symbol);
        scopes.peek().put(name, symbol);
        return symbol;
    }

    /**
     * Busca uma variável por nome, do escopo mais interno para o mais externo.
     * Retorna {@code null} se não encontrada.
     */
    public Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol sym = scope.get(name);
            if (sym != null) return sym;
        }
        return null;
    }

    /**
     * Retorna {@code true} se a variável já foi declarada no escopo atual.
     */
    public boolean isDeclaredInCurrentScope(String name) {
        return scopes.peek().containsKey(name);
    }

    /**
     * Busca um símbolo pelo seu ID único. Retorna {@code null} se não encontrado.
     */
    public Symbol getById(int id) {
        for (Symbol s : symbols) {
            if (s.id == id) return s;
        }
        return null;
    }

    /** Cria um novo escopo interno (ex: ao entrar em um bloco ou método). */
    public void enterScope() {
        scopes.push(new HashMap<>());
    }

    /** Remove o escopo mais interno (ex: ao sair de um bloco ou método). */
    public void exitScope() {
        scopes.pop();
    }

    /** Retorna todos os símbolos declarados na ordem de criação. */
    public List<Symbol> getAll() {
        return symbols;
    }
}
