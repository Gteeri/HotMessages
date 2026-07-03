package dev.gteeri.hotmessages.filter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Поиск запрещённых слов по алгоритму Ахо-Корасик — все слова ищутся за один проход
 * по тексту (O(n)), что важно, поскольку проверка идёт синхронно на каждое сообщение в чате.
 */
public final class WordFilter {

    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        Node fail;
        int wordLength = -1; // >=0, если в этом узле заканчивается слово (нормализованная длина)
    }

    private volatile Node root = new Node();
    private volatile boolean built;

    public void rebuild(List<String> bannedWords) {
        Node newRoot = new Node();
        for (String word : bannedWords) {
            String normalized = TextNormalizer.normalizeWord(word);
            if (normalized.isEmpty()) continue;
            Node node = newRoot;
            for (char c : normalized.toCharArray()) {
                node = node.children.computeIfAbsent(c, k -> new Node());
            }
            node.wordLength = normalized.length();
        }
        buildFailLinks(newRoot);
        this.root = newRoot;
        this.built = true;
    }

    private void buildFailLinks(Node root) {
        Deque<Node> queue = new ArrayDeque<>();
        root.fail = root;
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char c = entry.getKey();
                Node child = entry.getValue();
                Node failCandidate = current.fail;
                while (failCandidate != root && !failCandidate.children.containsKey(c)) {
                    failCandidate = failCandidate.fail;
                }
                Node candidateNode = failCandidate.children.get(c);
                child.fail = (candidateNode != null && candidateNode != child) ? candidateNode : root;
                queue.add(child);
            }
        }
    }

    /** [startNormalized, endNormalizedExclusive) для каждого найденного совпадения. */
    public List<int[]> findMatches(String normalizedText) {
        List<int[]> matches = new ArrayList<>();
        Node root = this.root;
        if (!built) return matches;
        Node node = root;
        for (int i = 0; i < normalizedText.length(); i++) {
            char c = normalizedText.charAt(i);
            while (node != root && !node.children.containsKey(c)) {
                node = node.fail;
            }
            node = node.children.getOrDefault(c, root);
            Node output = node;
            while (output != root) {
                if (output.wordLength >= 0) {
                    matches.add(new int[]{i - output.wordLength + 1, i + 1});
                }
                output = output.fail;
            }
        }
        return matches;
    }

    public boolean containsBannedWord(String message) {
        return !findMatches(TextNormalizer.normalize(message).normalized()).isEmpty();
    }

    /** Заменяет найденные слова символом цензуры прямо в исходном (не нормализованном) тексте. */
    public String censor(String message, char symbol) {
        TextNormalizer.Result result = TextNormalizer.normalize(message);
        List<int[]> matches = findMatches(result.normalized());
        if (matches.isEmpty()) return message;

        char[] chars = message.toCharArray();
        for (int[] match : matches) {
            int fromOriginal = result.originalIndex()[match[0]];
            int toOriginalExclusive = result.originalIndex()[match[1] - 1] + 1;
            for (int i = fromOriginal; i < toOriginalExclusive; i++) {
                if (!Character.isWhitespace(chars[i])) {
                    chars[i] = symbol;
                }
            }
        }
        return new String(chars);
    }
}
