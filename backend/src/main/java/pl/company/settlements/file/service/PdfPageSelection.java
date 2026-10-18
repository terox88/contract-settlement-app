package pl.company.settlements.file.service;

import java.util.List;
import java.util.TreeSet;

public final class PdfPageSelection {
    private PdfPageSelection() { }

    public static List<Integer> parse(String selection, int totalPages) {
        if (totalPages <= 0) throw new IllegalArgumentException("PDF nie zawiera stron.");
        if (selection == null || selection.isBlank()) throw new IllegalArgumentException("Podaj strony PDF do zapisania.");
        var selected = new TreeSet<Integer>();
        for (String part : selection.split(",", -1)) {
            String value = part.trim();
            if (!value.matches("\\d+(?:\\s*-\\s*\\d+)?")) {
                throw new IllegalArgumentException("Nieprawidłowy wybór stron: " + value + ". Przykład: 1-3,7,10-12.");
            }
            String[] range = value.split("-");
            int first = number(range[0].trim(), totalPages);
            int last = range.length == 1 ? first : number(range[1].trim(), totalPages);
            if (first > last) throw new IllegalArgumentException("Początek zakresu stron nie może być większy od końca: " + value + ".");
            for (int page = first; ; page++) {
                selected.add(page);
                if (page == last) break;
            }
        }
        return List.copyOf(selected);
    }

    private static int number(String text, int totalPages) {
        int page;
        try { page = Integer.parseInt(text); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Nieprawidłowy numer strony: " + text + "."); }
        if (page < 1 || page > totalPages) {
            throw new IllegalArgumentException("Strona " + page + " nie istnieje. PDF ma " + totalPages + " stron.");
        }
        return page;
    }
}
