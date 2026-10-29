package org.example.synchro.entities;

import java.util.Locale;

public final class CatalogName {
    private CatalogName() {}

    public static String key(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Nome obrigatório no catálogo.");
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
