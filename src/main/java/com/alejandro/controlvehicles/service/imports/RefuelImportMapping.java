package com.alejandro.controlvehicles.service.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Which column of the statement (by its header) carries each {@link ImportField}; an absent field has none. */
public record RefuelImportMapping(Map<ImportField, String> columns) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public RefuelImportMapping {
        EnumMap<ImportField, String> copy = new EnumMap<>(ImportField.class);
        columns.forEach((field, column) -> {
            if (column != null && !column.isBlank()) {
                copy.put(field, column);
            }
        });
        columns = java.util.Collections.unmodifiableMap(copy);
    }

    public String column(ImportField field) {
        return columns.get(field);
    }

    public RefuelImportMapping with(ImportField field, String column) {
        Map<ImportField, String> changed = new EnumMap<>(ImportField.class);
        changed.putAll(columns);
        changed.remove(field);
        if (column != null) {
            changed.put(field, column);
        }
        return new RefuelImportMapping(changed);
    }

    public List<ImportField> missingRequired() {
        return java.util.Arrays.stream(ImportField.values())
                .filter(field -> field.required() && !columns.containsKey(field)).toList();
    }

    /** Only the columns that exist in this header: a saved mapping applied to a file that changed shape. */
    public RefuelImportMapping restrictedTo(List<String> header) {
        Map<ImportField, String> kept = new EnumMap<>(ImportField.class);
        columns.forEach((field, column) -> {
            if (header.contains(column)) {
                kept.put(field, column);
            }
        });
        return new RefuelImportMapping(kept);
    }

    /**
     * A first proposal from the header's words. Fields are looked at in order and each takes the first column, not
     * taken yet, whose name contains its most specific keyword; a short keyword ("km") must be a whole word.
     */
    public static RefuelImportMapping guess(List<String> header) {
        Map<ImportField, String> guessed = new EnumMap<>(ImportField.class);
        List<String> taken = new ArrayList<>();
        for (ImportField field : ImportField.values()) {
            find:
            for (String keyword : field.keywords()) {
                for (String column : header) {
                    if (!taken.contains(column) && matches(normalise(column), keyword)) {
                        guessed.put(field, column);
                        taken.add(column);
                        break find;
                    }
                }
            }
        }
        return new RefuelImportMapping(guessed);
    }

    public String toJson() {
        Map<String, String> plain = new LinkedHashMap<>();
        columns.forEach((field, column) -> plain.put(field.name(), column));
        try {
            return JSON.writeValueAsString(plain);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Optional<RefuelImportMapping> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, String> plain = JSON.readValue(json, new TypeReference<Map<String, String>>() {
            });
            Map<ImportField, String> columns = new EnumMap<>(ImportField.class);
            plain.forEach((field, column) -> {
                try {
                    columns.put(ImportField.valueOf(field), column);
                } catch (IllegalArgumentException unknown) {
                    // Un campo de otra versión no estropea los demás.
                }
            });
            return Optional.of(new RefuelImportMapping(columns));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    static String normalise(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }

    private static boolean matches(String column, String keyword) {
        if (keyword.length() <= 3) {
            return column.matches(".*\\b" + java.util.regex.Pattern.quote(keyword) + "\\b.*");
        }
        return column.contains(keyword);
    }
}
