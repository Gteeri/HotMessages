package ru.sitbix.hotmessages.filter;

public record FilterResult(boolean blocked, String ruleId, String reason, String normalized) {
    public static FilterResult clean(String normalized) {
        return new FilterResult(false, "", "", normalized);
    }

    public static FilterResult blocked(String ruleId, String reason, String normalized) {
        return new FilterResult(true, ruleId, reason, normalized);
    }
}
