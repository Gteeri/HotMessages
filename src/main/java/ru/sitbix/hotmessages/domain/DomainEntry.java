package ru.sitbix.hotmessages.domain;

public record DomainEntry(String domain, boolean enabled) {
    public DomainEntry toggle() {
        return new DomainEntry(domain, !enabled);
    }
}
