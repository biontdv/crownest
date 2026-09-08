package com.crownest.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * Self-signed certificate lifecycle, built on the JDK's own keytool so the
 * application needs no third party crypto library.
 */
public final class TlsManager {

    private static final String ALIAS = "crownest";
    private static final String STORE_TYPE = "PKCS12";

    private TlsManager() {
    }

    /** Per-user configuration directory, created with owner-only permissions. */
    public static Path configDir() throws IOException {
        return com.crownest.AppPaths.configDir();
    }

    private static void restrict(Path path, boolean directory) {
        com.crownest.AppPaths.restrict(path, directory);
    }

    private static Path keystorePath() throws IOException {
        return configDir().resolve("keystore.p12");
    }

    private static Path passwordPath() throws IOException {
        return configDir().resolve("keystore.pass");
    }

    /** Read the stored keystore password, generating one on first use. */
    private static char[] password() throws IOException {
        Path file = passwordPath();
        if (Files.exists(file)) {
            String stored = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (!stored.isEmpty()) {
                return stored.toCharArray();
            }
        }
        String generated = Secrets.randomToken(24);
        Files.writeString(file, generated, StandardCharsets.UTF_8);
        restrict(file, false);
        return generated.toCharArray();
    }

    /**
     * Ensure a valid keystore exists, regenerating it when missing or expired,
     * and return an SSLContext ready for the HTTPS server.
     */
    public static SSLContext sslContext(List<String> subjectAltIps) throws Exception {
        char[] pass = password();
        Path store = keystorePath();

        if (!Files.exists(store) || !isStillValid(store, pass)) {
            Files.deleteIfExists(store);
            generateKeystore(store, pass, subjectAltIps);
        }

        KeyStore ks = KeyStore.getInstance(STORE_TYPE);
        try (InputStream in = Files.newInputStream(store)) {
            ks.load(in, pass);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pass);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    private static boolean isStillValid(Path store, char[] pass) {
        try {
            KeyStore ks = KeyStore.getInstance(STORE_TYPE);
            try (InputStream in = Files.newInputStream(store)) {
                ks.load(in, pass);
            }
            Certificate cert = ks.getCertificate(ALIAS);
            if (!(cert instanceof X509Certificate x509)) {
                return false;
            }
            x509.checkValidity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void generateKeystore(Path store, char[] pass, List<String> subjectAltIps)
            throws IOException, InterruptedException {

        List<String> san = new ArrayList<>();
        san.add("dns:localhost");
        san.add("dns:crownest.local");
        san.add("ip:127.0.0.1");
        if (subjectAltIps != null) {
            for (String ip : subjectAltIps) {
                if (ip == null || ip.isBlank()) {
                    continue;
                }
                if (ip.equals("0.0.0.0") || ip.equals("::")) {
                    continue;
                }
                String entry = "ip:" + ip;
                if (!san.contains(entry)) {
                    san.add(entry);
                }
            }
        }

        List<String> cmd = new ArrayList<>(List.of(
                keytoolBinary(),
                "-genkeypair",
                "-alias", ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-sigalg", "SHA256withRSA",
                "-validity", "825",
                "-storetype", STORE_TYPE,
                "-keystore", store.toString(),
                "-storepass", new String(pass),
                "-keypass", new String(pass),
                "-dname", "CN=crownest.local, O=Crownest, OU=HTTP File Server",
                "-ext", "SAN=" + String.join(",", san),
                "-ext", "KeyUsage=digitalSignature,keyEncipherment",
                "-ext", "ExtendedKeyUsage=serverAuth"));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = proc.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            throw new IOException("keytool timed out while generating the certificate");
        }
        if (proc.exitValue() != 0 || !Files.exists(store)) {
            throw new IOException("keytool failed to generate a certificate: " + output.trim());
        }
        restrict(store, false);
    }

    private static String keytoolBinary() {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = win ? "keytool.exe" : "keytool";
        Path bundled = Paths.get(System.getProperty("java.home"), "bin", exe);
        return Files.isExecutable(bundled) ? bundled.toString() : "keytool";
    }

    /** SHA-256 fingerprint of the server certificate, for out-of-band verification. */
    public static String fingerprint() {
        try {
            char[] pass = password();
            Path store = keystorePath();
            if (!Files.exists(store)) {
                return "unavailable";
            }
            KeyStore ks = KeyStore.getInstance(STORE_TYPE);
            try (InputStream in = Files.newInputStream(store)) {
                ks.load(in, pass);
            }
            Certificate cert = ks.getCertificate(ALIAS);
            if (cert == null) {
                return "unavailable";
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder(digest.length * 3);
            for (byte b : digest) {
                if (sb.length() > 0) {
                    sb.append(':');
                }
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unavailable";
        }
    }

    /** Force regeneration on next use, for example after changing the bind IP. */
    public static void invalidate() {
        try {
            Files.deleteIfExists(keystorePath());
        } catch (IOException e) {
            // best effort
        }
    }
}
