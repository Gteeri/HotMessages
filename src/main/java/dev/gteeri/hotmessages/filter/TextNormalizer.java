package dev.gteeri.hotmessages.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Приводит текст к «нормальной» форме для сравнения со списком запрещённых слов:
 * убирает разделители, используемые для обхода фильтра (точки, дефисы, пробелы между буквами),
 * заменяет цифро-буквенные подмены (leet-speak) и схлопывает повторяющиеся буквы.
 * <p>Используется как для сообщений игроков, так и для самих слов в списке — обе стороны
 * должны проходить через одну и ту же нормализацию, иначе сравнение потеряет смысл.
 */
public final class TextNormalizer {

    private static final Map<Character, Character> LEET = Map.ofEntries(
            Map.entry('0', 'o'), Map.entry('1', 'i'), Map.entry('3', 'e'),
            Map.entry('4', 'a'), Map.entry('5', 's'), Map.entry('7', 't'),
            Map.entry('@', 'a'), Map.entry('$', 's'), Map.entry('!', 'i')
    );

    // символы-разделители, которые игроки вставляют между буквами, чтобы обойти фильтр
    private static final String IGNORED_SEPARATORS = " .,_-*~^`'\"|/\\";

    private TextNormalizer() {
    }

    public record Result(String normalized, int[] originalIndex) {
    }

    /**
     * @return нормализованная строка + массив, где originalIndex[i] — индекс символа
     * в исходной строке, породившего i-й символ нормализованной строки.
     */
    public static Result normalize(String input) {
        StringBuilder out = new StringBuilder(input.length());
        List<Integer> indexes = new ArrayList<>(input.length());
        char lastAppended = '\0';

        for (int i = 0; i < input.length(); i++) {
            char c = Character.toLowerCase(input.charAt(i));

            if (IGNORED_SEPARATORS.indexOf(c) >= 0) {
                continue; // разделитель — просто пропускаем, не считается символом
            }

            Character mapped = LEET.get(c);
            char normalizedChar = mapped != null ? mapped : c;

            if (normalizedChar == lastAppended) {
                continue; // схлопываем повторы: "baaaad" -> "bad"
            }

            out.append(normalizedChar);
            indexes.add(i);
            lastAppended = normalizedChar;
        }

        int[] arr = new int[indexes.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = indexes.get(i);
        return new Result(out.toString(), arr);
    }

    /** Нормализует слово из списка запрещённых (без карты индексов — она не нужна). */
    public static String normalizeWord(String word) {
        return normalize(word).normalized();
    }
}
