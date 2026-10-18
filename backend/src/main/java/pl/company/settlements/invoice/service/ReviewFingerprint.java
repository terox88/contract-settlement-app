package pl.company.settlements.invoice.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Kanoniczny zapis z długościami pól: bez kolizji separatorów i zależności od kolejności odczytu. */
public final class ReviewFingerprint {
    private final List<String> entries = new ArrayList<>();
    public ReviewFingerprint add(String key, Object... values) {
        StringBuilder entry = new StringBuilder(); append(entry, key);
        for (Object value : values) append(entry, value instanceof BigDecimal decimal
                ? decimal.stripTrailingZeros().toPlainString() : value == null ? null : value.toString());
        entries.add(entry.toString()); return this;
    }
    private static void append(StringBuilder entry, String value) {
        if (value == null) entry.append("-1:");
        else entry.append(value.length()).append(':').append(value);
    }
    public String value() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            entries.stream().sorted().forEach(entry -> {
                byte[] bytes = entry.getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) ':'); digest.update(bytes);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
