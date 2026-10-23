package edu.registration.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;









public final class TsvTable {
    private static final String NULL_MARKER = "\\N";
    private final Path file;

    public TsvTable(Path file) {
        this.file = file;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String[]> readAll() {
        List<String[]> rows = new ArrayList<>();
        if (!Files.exists(file)) {
            return rows;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = decode(parts[i]);
                }
                rows.add(parts);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    public void writeAll(List<String[]> rows) {
        List<String> lines = new ArrayList<>();
        for (String[] row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    line.append('\t');
                }
                line.append(encode(row[i]));
            }
            lines.add(line.toString());
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

     
    public void upsert(String[] row) {
        List<String[]> rows = readAll();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i)[0].equals(row[0])) {
                rows.set(i, row);
                writeAll(rows);
                return;
            }
        }
        rows.add(row);
        writeAll(rows);
    }

    static String encode(String value) {
        if (value == null) {
            return NULL_MARKER;
        }
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    static String decode(String text) {
        if (NULL_MARKER.equals(text)) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                switch (next) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    default -> out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
