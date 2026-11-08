package lol;

/*
 * Symbol — representa uma variável registrada na tabela de símbolos.
 *
 * Quando o compilador encontra uma declaração de variável (ex: "int x = 5;"),
 * ele cria um Symbol e o armazena na SymbolTable. A partir daí, cada vez que
 * o código usar "x", o compilador consulta a tabela para saber o tipo de "x"
 * e verificar se o uso é válido.
 *
 * O campo 'id' é o índice do símbolo na tabela (0, 1, 2, ...). Ele aparece na
 * lista de tokens como "[Index: n]", ao lado do nome, ligando cada uso do
 * identificador à sua entrada na tabela.
 *
 * Além de variáveis, a tabela guarda classes, métodos e parâmetros (campo 'kind').
 * System, out e println nunca entram na tabela: não são declarados pelo programador.
 */
public class Symbol {

    // Categoria do símbolo
    public enum Kind { CLASSE, METODO, PARAMETRO, VARIAVEL }

    public final int id;       // índice do símbolo na tabela (0, 1, 2, ...)
    public final Kind kind;    // classe, método, parâmetro ou variável
    public final String type;  // tipo: "INT", "DOUBLE", "BOOLEAN", "STRING", "INT[]", ...
                               // (classe: "CLASS"; método: tipo de retorno)
    public final String name;  // nome do símbolo, ex: "x", "contador"
    public final Object value; // valor do inicializador (ex: 5), ou null se não inicializada
    public final int line;     // linha onde a variável foi declarada
    public final int column;   // coluna onde o nome da variável começa

    public Symbol(int id, String type, String name, Object value, int line, int column) {
        this(id, Kind.VARIAVEL, type, name, value, line, column);
    }

    public Symbol(int id, Kind kind, String type, String name, Object value, int line, int column) {
        this.id     = id;
        this.kind   = kind;
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
        return "[" + id + "] " + kind + " | " + type + " | " + name + " | " + valueStr;
    }
}
