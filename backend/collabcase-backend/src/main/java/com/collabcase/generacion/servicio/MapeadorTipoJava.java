package com.collabcase.generacion.servicio;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class MapeadorTipoJava {

    private static final Map<String, String> TIPOS = Map.ofEntries(
            Map.entry("string", "String"),
            Map.entry("texto", "String"),
            Map.entry("char", "String"),
            Map.entry("character", "String"),

            Map.entry("uuid", "UUID"),

            Map.entry("int", "Integer"),
            Map.entry("integer", "Integer"),
            Map.entry("entero", "Integer"),
            Map.entry("short", "Short"),
            Map.entry("byte", "Byte"),

            Map.entry("long", "Long"),

            Map.entry("decimal", "BigDecimal"),
            Map.entry("bigdecimal", "BigDecimal"),
            Map.entry("numeric", "BigDecimal"),
            Map.entry("number", "BigDecimal"),

            Map.entry("double", "Double"),
            Map.entry("float", "Float"),

            Map.entry("boolean", "Boolean"),
            Map.entry("bool", "Boolean"),

            Map.entry("date", "LocalDate"),
            Map.entry("localdate", "LocalDate"),
            Map.entry("fecha", "LocalDate"),

            Map.entry("datetime", "LocalDateTime"),
            Map.entry("localdatetime", "LocalDateTime"),
            Map.entry("timestamp", "LocalDateTime"),
            Map.entry("fechahora", "LocalDateTime"),

            Map.entry("time", "LocalTime"),
            Map.entry("localtime", "LocalTime")
    );

    private static final Set<String> IMPORTS_JAVA_UTIL = Set.of(
            "UUID"
    );

    private static final Set<String> IMPORTS_JAVA_MATH = Set.of(
            "BigDecimal"
    );

    private static final Set<String> IMPORTS_JAVA_TIME = Set.of(
            "LocalDate",
            "LocalDateTime",
            "LocalTime"
    );

    public String mapear(String tipoDiagrama) {

        if (tipoDiagrama == null || tipoDiagrama.isBlank()) {
            throw new IllegalArgumentException(
                    "El tipo de dato del atributo no puede estar vacío"
            );
        }

        String normalizado =
                tipoDiagrama.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace(" ", "");

        String tipoJava =
                TIPOS.get(normalizado);

        if (tipoJava == null) {
            throw new IllegalArgumentException(
                    "Tipo de dato no soportado para generación Java: "
                            + tipoDiagrama
            );
        }

        return tipoJava;
    }

    public String importNecesario(String tipoJava) {

        if (IMPORTS_JAVA_UTIL.contains(tipoJava)) {
            return "java.util." + tipoJava;
        }

        if (IMPORTS_JAVA_MATH.contains(tipoJava)) {
            return "java.math." + tipoJava;
        }

        if (IMPORTS_JAVA_TIME.contains(tipoJava)) {
            return "java.time." + tipoJava;
        }

        return null;
    }
}
