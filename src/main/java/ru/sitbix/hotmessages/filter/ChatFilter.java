package ru.sitbix.hotmessages.filter;

import ru.sitbix.hotmessages.config.FilterConfig;
import ru.sitbix.hotmessages.domain.DomainService;

import java.util.regex.Pattern;

public final class ChatFilter {
    private final FilterConfig config;
    private final MessageNormalizer normalizer;
    private final DomainService domainService;

    public ChatFilter(FilterConfig config, DomainService domainService) {
        this.config = config;
        this.normalizer = new MessageNormalizer(config.normalization());
        this.domainService = domainService;
    }

    public FilterResult check(String message) {
        NormalizedMessage normalized = normalizer.normalize(message);
        String spaced = removeWhitelist(normalized.spaced());
        String compact = removeWhitelist(normalized.compact());

        for (Pattern pattern : domainService.compiledPatterns()) {
            if (safeFind(pattern, spaced) || safeFind(pattern, compact)) {
                return FilterResult.blocked("domain:" + pattern.pattern(), "запрещённый домен", normalized.spaced());
            }
        }

        for (String fragment : config.blacklistFragments()) {
            NormalizedMessage normalizedFragment = normalizer.normalize(fragment);
            if (!normalizedFragment.spaced().isBlank() && spaced.contains(normalizedFragment.spaced())) {
                return FilterResult.blocked("blacklist:" + fragment, "запрещённая ссылка", normalized.spaced());
            }
        }

        for (String fragment : config.blacklistCompactFragments()) {
            NormalizedMessage normalizedFragment = normalizer.normalize(fragment);
            if (!normalizedFragment.compact().isBlank() && compact.contains(normalizedFragment.compact())) {
                return FilterResult.blocked("compact-blacklist:" + fragment, "обфусцированная ссылка", normalized.spaced());
            }
        }

        for (FilterConfig.RegexRule rule : config.regexRules()) {
            if (safeFind(rule.pattern(), spaced) || safeFind(rule.pattern(), compact)) {
                return FilterResult.blocked(rule.id(), rule.reason(), normalized.spaced());
            }
        }

        return FilterResult.clean(normalized.spaced());
    }

    private String removeWhitelist(String value) {
        String output = value;
        for (String fragment : config.whitelistFragments()) {
            NormalizedMessage normalizedFragment = normalizer.normalize(fragment);
            if (!normalizedFragment.spaced().isBlank()) {
                output = output.replace(normalizedFragment.spaced(), " ");
            }
            if (!normalizedFragment.compact().isBlank()) {
                output = output.replace(normalizedFragment.compact(), " ");
            }
        }

        for (FilterConfig.RegexRule rule : config.whitelistRegex()) {
            try {
                output = rule.pattern().matcher(output).replaceAll(" ");
            } catch (Exception e) {
                // skip broken whitelist regex
            }
        }

        return output;
    }

    private boolean safeFind(Pattern pattern, String input) {
        try {
            return pattern.matcher(input).find();
        } catch (Exception e) {
            return true; // treat errors as suspicious
        }
    }
}
