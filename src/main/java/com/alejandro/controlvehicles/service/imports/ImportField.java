package com.alejandro.controlvehicles.service.imports;

import java.util.List;

/**
 * What the importer needs from a statement column, with the header words that usually name it in Spanish,
 * Portuguese and English (lower case, without accents), most specific first.
 */
public enum ImportField {
    DATE("Date", true, List.of("data/hora", "fecha/hora", "data hora", "fecha hora", "fecha", "data", "date")),
    TIME("Time", false, List.of("hora", "time")),
    PLATE("Plate", true, List.of("matricula", "plate", "viatura", "veiculo", "vehiculo", "registration")),
    LITRES("Litres", true, List.of("litros", "litres", "liters", "quantidade", "cantidad", "qtd", "volume")),
    AMOUNT("Amount", true, List.of("valor total", "importe total", "total", "importe", "valor", "montante",
            "amount")),
    ODOMETER("Odometer", false, List.of("quilometros", "kilometros", "odometro", "odometer", "kms", "km")),
    STATION("Station", false, List.of("posto", "estacion", "station", "establecimiento", "local")),
    PRODUCT("Product", false, List.of("produto", "producto", "product", "combustivel", "combustible", "artigo"));

    private final String label;
    private final boolean required;
    private final List<String> keywords;

    ImportField(String label, boolean required, List<String> keywords) {
        this.label = label;
        this.required = required;
        this.keywords = keywords;
    }

    public String label() {
        return label;
    }

    public boolean required() {
        return required;
    }

    List<String> keywords() {
        return keywords;
    }

    @Override
    public String toString() {
        return required ? label + " *" : label;
    }
}
