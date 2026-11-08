package lol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * SymbolTable — tabela de símbolos com suporte a escopos aninhados.
 *
 * A tabela de símbolos é uma estrutura central em compiladores. Ela guarda
 * informações sobre todas as variáveis declaradas no programa (nome, tipo,
 * valor inicial, posição no código) e permite que o compilador verifique se
 * uma variável foi declarada antes de ser usada e se os tipos são compatíveis.
 *
 * Esta implementação usa duas estruturas em paralelo:
 *
 *   symbols → lista global com todos os símbolos na ordem em que foram
 *             declarados. Usada na exibição final da tabela.
 *
 *   scopes  → pilha de mapas (nome → símbolo). Cada mapa representa um
 *             escopo (um bloco de código entre { e }). O topo da pilha
 *             é sempre o escopo mais interno (o que está sendo executado
 *             no momento).
 *
 * Escopos aninhados permitem que variáveis com o mesmo nome existam em
 * blocos diferentes sem conflito (shadowing):
 *
 *   int x = 5;        // escopo externo: x = #1
 *   if (...) {
 *       int x = 10;   // escopo interno: x = #2, "esconde" o #1
 *   }
 *   // aqui x volta a ser #1
 */
public class SymbolTable {

    // Lista global de todos os símbolos, na ordem de declaração
    private final List<Symbol> symbols = new ArrayList<>();

    // Pilha de escopos: cada escopo é um mapa de nome → símbolo.
    // O topo (peek) é o escopo mais interno.
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();

    // Contador que gera IDs únicos e sequenciais para cada símbolo
    private int nextId = 1;

    /*
     * Construtor — cria a tabela com um escopo global inicial.
     * Sem esse escopo inicial, o primeiro enterScope() não teria onde voltar.
     */
    public SymbolTable() {
        scopes.push(new HashMap<>());
    }

    /*
     * declare() — registra uma nova variável no escopo atual.
     *
     * Cria um Symbol com ID único, adiciona à lista global (para exibição)
     * e ao mapa do escopo atual (para lookup por nome).
     * Retorna o símbolo criado para que o Parser possa associar o ID ao Token.
     */
    public Symbol declare(String type, String name, Object value, int line, int column) {
        Symbol symbol = new Symbol(nextId++, type, name, value, line, column);
        symbols.add(symbol);
        scopes.peek().put(name, symbol);
        return symbol;
    }

    /*
     * lookup() — busca uma variável pelo nome, do escopo mais interno para o externo.
     *
     * Percorre a pilha de escopos do topo (mais interno) até a base (global).
     * Isso implementa o shadowing: se "x" existe em dois escopos, o mais interno
     * é encontrado primeiro. Retorna null se a variável não foi declarada em nenhum escopo.
     */
    public Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol sym = scope.get(name);
            if (sym != null) return sym;
        }
        return null;
    }

    /*
     * isDeclaredInCurrentScope() — verifica se a variável já existe NO ESCOPO ATUAL.
     *
     * Diferente de lookup(), que busca em todos os escopos, este método olha
     * apenas o escopo mais interno. Usado pelo SemanticAnalyzer para detectar
     * declarações duplicadas: "int x = 5; int x = 10;" no mesmo bloco é um erro.
     */
    public boolean isDeclaredInCurrentScope(String name) {
        return scopes.peek().containsKey(name);
    }

    /*
     * getById() — busca um símbolo pelo seu ID único.
     *
     * Usado pelo SemanticAnalyzer para recuperar o símbolo a partir do ID
     * armazenado no Token (token.symbolId), permitindo consultar o tipo
     * da variável durante a análise de expressões.
     */
    public Symbol getById(int id) {
        for (Symbol s : symbols) {
            if (s.id == id) return s;
        }
        return null;
    }

    /*
     * enterScope() — cria um novo escopo interno.
     *
     * Chamado ao entrar em um bloco de código ({ ... }): início de método,
     * corpo de if, corpo de while, etc.
     */
    public void enterScope() {
        scopes.push(new HashMap<>());
    }

    /*
     * exitScope() — destrói o escopo mais interno.
     *
     * Chamado ao sair de um bloco de código. As variáveis declaradas dentro
     * do bloco deixam de existir para o compilador.
     */
    public void exitScope() {
        scopes.pop();
    }

    /*
     * getAll() — retorna todos os símbolos declarados, na ordem de criação.
     * Usado por Main.printSymbolTable() para exibir a tabela completa.
     */
    public List<Symbol> getAll() {
        return symbols;
    }
}
