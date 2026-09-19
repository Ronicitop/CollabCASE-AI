package com.collabcase.generacion.servicio;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Component
public class NormalizadorNombreJava {

    private static final Set<String> RESERVADAS = Set.of(
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "record", "sealed", "permits", "var", "yield"
    );

    public String clase(String texto) {
        String limpio = limpiar(texto);

        StringBuilder resultado = new StringBuilder();
        boolean mayuscula = true;

        for (char c : limpio.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                mayuscula = true;
                continue;
            }

            if (resultado.isEmpty() && Character.isDigit(c)) {
                resultado.append('C');
            }

            resultado.append(
                    mayuscula
                            ? Character.toUpperCase(c)
                            : c
            );

            mayuscula = false;
        }

        validarNoVacio(resultado, texto);

        String valor = resultado.toString();

        if (RESERVADAS.contains(valor.toLowerCase(Locale.ROOT))) {
            valor = valor + "Entidad";
        }

        return valor;
    }

    public String campo(String texto) {
        String nombreClase = clase(texto);

        String valor =
                Character.toLowerCase(nombreClase.charAt(0))
                        + nombreClase.substring(1);

        if (RESERVADAS.contains(valor)) {
            valor = valor + "Valor";
        }

        return valor;
    }

    public String ruta(String texto) {
        String base = clase(texto)
                .toLowerCase(Locale.ROOT);

        return base + "s";
    }

    public String tabla(String texto) {
        String base = clase(texto);
        StringBuilder resultado = new StringBuilder();

        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);

            if (Character.isUpperCase(c) && i > 0) {
                resultado.append('_');
            }

            resultado.append(
                    Character.toLowerCase(c)
            );
        }

        return resultado.toString();
    }

    private String limpiar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre no puede estar vacío"
            );
        }

        return Normalizer.normalize(
                        texto.trim(),
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}", "");
    }

    private void validarNoVacio(
            StringBuilder resultado,
            String original
    ) {
        if (resultado.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se pudo convertir a un nombre Java válido: "
                            + original
            );
        }
    }
}
