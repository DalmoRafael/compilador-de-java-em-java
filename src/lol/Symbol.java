package lol;

/**
 * Representa uma entrada na tabela de simbolos (uma variavel declarada).
 */
public class Symbol {
    public final int id;       // identificador unico (ex: 1 -> exibido como #1)
    public final String type;  // tipo da variavel (ex: "INT", "DOUBLE", "BOOLEAN")
    public final String name;  // nome da variavel (ex: "x")
    public final Object value; // valor inicial (ex: 5, 3.14) ou null se nao inicializada
    public final int line;     // linha da declaracao
    public final int column;   // coluna da declaracao

    public Symbol(int id, String type, String name, Object value, int line, int column) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        String valueStr = value != null ? String.valueOf(value) : "null";
        return "#" + id + " | " + type + " | " + name + " | " + valueStr;
    }
}
