package net.syllyaddons.compat.wynntils.v4_2_9;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class WynntilsCompatibilityGuard {
    public static final String SUPPORTED_VERSION = "4.2.9";
    public static final String SUPPORTED_SHA256 =
            "faf32c32c5ce3af3b7236a19c5b8b8c6fb44695bf4340a9083bd2eae744858ef";

    public CompatibilityResult validate() {
        FabricLoader loader = FabricLoader.getInstance();
        ModContainer wynntils = loader.getModContainer("wynntils").orElse(null);
        if (wynntils == null) {
            return new CompatibilityResult(false, "Fabric Loader did not find the required Wynntils mod");
        }

        String actualVersion = wynntils.getMetadata().getVersion().getFriendlyString();
        if (actualVersion != null && actualVersion.startsWith("v")) {
            actualVersion = actualVersion.substring(1);
        }
        if (!SUPPORTED_VERSION.equals(actualVersion)) {
            return new CompatibilityResult(
                    false, "Expected Wynntils " + SUPPORTED_VERSION + " but found " + actualVersion);
        }

        if (loader.isDevelopmentEnvironment()) {
            return new CompatibilityResult(
                    true, "Wynntils 4.2.9 metadata matches; development remapping bypasses the release checksum");
        }

        Path modJar = wynntils.getOrigin().getPaths().stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
        if (modJar == null) return new CompatibilityResult(false, "Fabric Loader did not expose the Wynntils JAR");

        try {
            String actualHash = sha256(modJar);
            if (!SUPPORTED_SHA256.equalsIgnoreCase(actualHash)) {
                return new CompatibilityResult(
                        false, "Wynntils 4.2.9 JAR checksum differs from the tested private build: " + actualHash);
            }
        } catch (IOException exception) {
            return new CompatibilityResult(false, "Could not verify the Wynntils JAR: " + exception.getMessage());
        }

        return new CompatibilityResult(true, "Wynntils 4.2.9 matches the pinned private build");
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
