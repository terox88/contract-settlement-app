package pl.company.settlements.file.service;

import java.util.Locale;

public final class AttachmentNames {
    private AttachmentNames() { }
    public static String validate(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Podaj nazwę wyświetlaną dokumentu.");
        String value = name.strip();
        if (value.length() > 255 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Nazwa może mieć do 255 znaków i nie może zawierać znaków sterujących.");
        }
        return value;
    }
    public static String downloadName(String title, String original) {
        String name = title == null || title.isBlank() ? original : title;
        if (name == null || name.isBlank()) name = "dokument";
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("^\\.+", "").strip();
        if (name.isBlank()) name = "dokument";
        if (name.length() > 200) name = name.substring(0, 200);
        String extension = "";
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot >= 0 && original.substring(dot + 1).matches("[A-Za-z0-9]{1,10}")) extension = original.substring(dot).toLowerCase(Locale.ROOT);
        }
        return !extension.isEmpty() && !name.toLowerCase(Locale.ROOT).endsWith(extension) ? name + extension : name;
    }
}
