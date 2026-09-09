package com.kael.title.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NeteaseRegistry {

    private final Path mappingFile;
    private final Map<String, String> originals = new ConcurrentHashMap<>();
    private volatile long loadedAt = Long.MIN_VALUE;

    public NeteaseRegistry(String file) {
        mappingFile = Path.of(file).toAbsolutePath().normalize();
    }

    public String originalName(String internalName) {
        reloadIfChanged();
        return originals.get(internalName.toLowerCase());
    }

    private void reloadIfChanged() {
        try {
            long modified = Files.exists(mappingFile) ? Files.getLastModifiedTime(mappingFile).toMillis() : -1L;
            if (modified == loadedAt) {
                return;
            }
            Map<String, String> fresh = new ConcurrentHashMap<>();
            if (modified >= 0) {
                for (String line : Files.readAllLines(mappingFile, StandardCharsets.UTF_8)) {
                    String[] parts = line.split("\\t", -1);
                    if (parts.length == 2) {
                        try {
                            fresh.put(parts[0].toLowerCase(), new String(
                                    Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
            originals.clear();
            originals.putAll(fresh);
            loadedAt = modified;
        } catch (IOException ignored) {
        }
    }
}
