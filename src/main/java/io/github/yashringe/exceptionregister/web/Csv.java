package io.github.yashringe.exceptionregister.web;

import java.util.List;
import java.util.stream.Collectors;

/** Minimal RFC 4180 writer with protection against spreadsheet formula injection. */
final class Csv {

    private Csv() {
    }

    static String row(List<String> fields) {
        return fields.stream().map(Csv::cell).collect(Collectors.joining(",")) + "\n";
    }

    /**
     * Titles and justifications are typed by users. A value like {@code =HYPERLINK(...)} would run as a formula
     * when a risk manager opens the export in Excel, so such values get a leading apostrophe.
     */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
