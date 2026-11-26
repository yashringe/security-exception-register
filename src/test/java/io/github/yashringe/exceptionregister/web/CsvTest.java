package io.github.yashringe.exceptionregister.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvTest {

    @Test
    void plainValuesStayAsTheyAre() {
        assertEquals("a,b,c\n", Csv.row(List.of("a", "b", "c")));
    }

    @Test
    void commasAndQuotesAreEscaped() {
        assertEquals("\"Server, legacy\"", Csv.cell("Server, legacy"));
        assertEquals("\"say \"\"hi\"\"\"", Csv.cell("say \"hi\""));
    }

    @Test
    void formulasAreDefused() {
        assertEquals("'=1+1", Csv.cell("=1+1"));
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", Csv.cell("=HYPERLINK(\"http://x\")"));
        assertEquals("'+49 155", Csv.cell("+49 155"));
        assertEquals("'@SUM(A1)", Csv.cell("@SUM(A1)"));
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", Csv.cell(null));
    }
}
