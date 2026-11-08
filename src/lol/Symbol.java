package lol;

/*
 * Symbol — representa uma variável registrada na tabela de símbolos.
 *
 * Quando o compilador encontra uma declaração de variável (ex: "int x = 5;"),
 * ele cria um Symbol e o armazena na SymbolTable. A partir daí, cada vez que
 * o código usar "x", o compilador consulta a tabela para saber o tipo de "x"
 * e verificar se o uso é válido.
 *
 * O campo 'id' é um número sequencial único (1, 2, 3, ...) que serve como
 * referência na lista de tokens — em vez de repetir o nome "x", o token
 * mostra "-> #1", indicando que aquele uso corresponde ao símbolo de número 1.
 */
public class Symbol {

    public final int id;       // número único sequencial, exibido como #1, #2, ...
    public final String type;  // tipo da variável: "INT", "DOUBLE" ou "BOOLEAN"
    public final String name;  // nome da variável, ex: "x", "contador"
    public final Object value; // valor do inicializador (ex: 5), ou null se não inicializada
    public final int line;     // linha onde a variável foi declarada
    public final int column;   // coluna onde o nome da variável começa

    public Symbol(int id, String type, String name, Object value, int line, int column) {
        this.id     = id;
        this.type   = type;
        this.name   = name;
        this.value  = value;
        this.line   = line;
        this.column = column;
    }

    /*
     * toString() — formato simples para debug e exibição nos testes.
     * A tabela formatada com colunas alinhadas é impressa por Main.printSymbolTable().
     */
    @Override
    public String toString() {
        String valueStr = value != null ? String.valueOf(value) : "null";
        return "#" + id + " | " + type + " | " + name + " | " + valueStr;
    }
}
