package edu.registration.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsvTableTest {
    @TempDir
    Path dir;

    @Test
    void roundTripsTabsNewlinesBackslashesNullsAndEmptyStrings() {
        String[] row = {"id1", "a\tb", "line1\nline2\r", "back\\slash", null, "\\N", ""};
        new TsvTable(dir.resolve("t.tsv")).writeAll(List.<String[]>of(row));

        List<String[]> reopened = new TsvTable(dir.resolve("t.tsv")).readAll();

        assertEquals(1, reopened.size());
        assertArrayEquals(row, reopened.get(0));
    }

    @Test
    void aTableThatWasNeverWrittenReadsAsEmpty() {
        assertTrue(new TsvTable(dir.resolve("nothing.tsv")).readAll().isEmpty());
    }

    @Test
    void upsertReplacesTheRowWithTheSameKeyAndAppendsNewKeys() {
        TsvTable table = new TsvTable(dir.resolve("t.tsv"));
        table.upsert(new String[]{"k1", "old"});
        table.upsert(new String[]{"k2", "other"});
        table.upsert(new String[]{"k1", "new"});

        List<String[]> rows = table.readAll();

        assertEquals(2, rows.size());
        assertArrayEquals(new String[]{"k1", "new"}, rows.get(0));
        assertArrayEquals(new String[]{"k2", "other"}, rows.get(1));
    }

    @Test
    void createsMissingParentDirectories() {
        TsvTable table = new TsvTable(dir.resolve("deep").resolve("er").resolve("t.tsv"));
        table.upsert(new String[]{"k", "v"});

        assertEquals(1, table.readAll().size());
    }
}
