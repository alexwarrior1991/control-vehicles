package com.alejandro.controlvehicles.service.imports;

import java.util.List;

/** A statement read as text: its header and its rows, each padded to the header's width. */
public record TabularFile(String name, List<String> header, List<List<String>> rows) {

    public TabularFile {
        header = List.copyOf(header);
        int width = header.size();
        rows = rows.stream()
                .filter(row -> row.stream().anyMatch(cell -> cell != null && !cell.isBlank()))
                .map(row -> {
                    List<String> padded = new java.util.ArrayList<>(row.stream()
                            .map(cell -> cell == null ? "" : cell.trim()).toList());
                    while (padded.size() < width) {
                        padded.add("");
                    }
                    return List.copyOf(padded.subList(0, Math.max(width, 0)));
                })
                .toList();
    }

    /** The first row as header; a file whose header repeats a name gets "Name (2)" so every column is addressable. */
    public static TabularFile of(String name, List<List<String>> lines) {
        if (lines.isEmpty()) {
            return new TabularFile(name, List.of(), List.of());
        }
        List<String> header = new java.util.ArrayList<>();
        for (String cell : lines.getFirst()) {
            String title = cell == null || cell.isBlank() ? "Column " + (header.size() + 1) : cell.trim();
            String unique = title;
            for (int n = 2; header.contains(unique); n++) {
                unique = title + " (" + n + ")";
            }
            header.add(unique);
        }
        return new TabularFile(name, header, lines.subList(1, lines.size()));
    }

    public int column(String title) {
        return title == null ? -1 : header.indexOf(title);
    }
}
