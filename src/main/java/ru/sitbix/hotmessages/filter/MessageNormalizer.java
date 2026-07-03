package ru.sitbix.hotmessages.filter;

import ru.sitbix.hotmessages.config.FilterConfig;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

public final class MessageNormalizer {
    private final FilterConfig.Normalization config;

    public MessageNormalizer(FilterConfig.Normalization config) {
        this.config = config;
    }

    public NormalizedMessage normalize(String input) {
        String value = input == null ? "" : input;

        if (config.stripColorCodes()) {
            value = value.replaceAll("(?i)[&§][0-9A-FK-ORX]", "");
        }
        if (config.removeZeroWidth()) {
            value = value.replaceAll("[\\u200B-\\u200D\\uFEFF]", "");
        }
        if (config.lowercase()) {
            value = value.toLowerCase(Locale.ROOT);
        }
        if (config.stripDiacritics()) {
            value = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        }

        value = applyReplacements(value, config.replacements());
        value = limitRepeatedCharacters(value, config.maxRepeatedCharacterRun());
        value = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ");
        value = value.replaceAll("[\\r\\n\\t]+", " ");

        if (config.collapseSpaces()) {
            value = value.replaceAll("\\s+", " ").trim();
        }

        String compact = config.removeSeparatorsForCompactCheck()
            ? value.replaceAll("[\\s._,;:/\\\\\\-+()\\[\\]{}<>\"'`~*#=]+", "")
            : value;

        return new NormalizedMessage(value, compact);
    }

    private String applyReplacements(String input, Map<String, String> replacements) {
        String output = input;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            output = output.replace(replacement.getKey(), replacement.getValue());
        }
        return output;
    }

    private String limitRepeatedCharacters(String input, int maxRun) {
        StringBuilder output = new StringBuilder(input.length());
        int run = 0;
        int previous = -1;

        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            if (codePoint == previous) {
                run++;
            } else {
                previous = codePoint;
                run = 1;
            }

            if (run <= maxRun) {
                output.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }

        return output.toString();
    }
}
