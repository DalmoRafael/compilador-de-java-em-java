package lol;

/**
 * Representa uma variável declarada na tabela de símbolos.
 *
 * <p>O {@code id} é único e sequencial (começa em 1), exibido como {@code #1},
 * {@code #2}, etc. O {@code value} é {@code null} quando a variável foi
 * declarada sem inicializador (ex: {@code int x;}).</p>
 */
public class Symbol {

    public final int id;       // identificador único, ex: 1 → exibido como #1
    public final String type;  // tipo da variável: "INT", "DOUBLE" ou "BOOLEAN"
    public final String name;  // nome da variável, ex: "x"
    public final Object value; // valor inicial ou null se não inicializada
    public final int line;     // linha da declaração
    public final int column;   // coluna da declaração

    public Symbol(int id, String type, String name, Object value, int line, int column) {
        this.id     = id;
        this.type   = type;
        this.name   = name;
        this.value  = value;
        this.line   = line;
        this.column = column;
    }

    @Override
    public String toString() {
        String valueStr = value != null ? String.valueOf(value) : "null";
        return "#" + id + " | " + type + " | " + name + " | " + valueStr;
    }
}
