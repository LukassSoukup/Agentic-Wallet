package dev.agenticwallet.spike.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Throwaway test PKI made with the JDK's own keytool at test time: a CA, a server leaf for
 * localhost and a client leaf. Nothing here is committed; the material lives in a temp directory.
 * Labelled synthetic.
 */
final class TestCertificates {

    static final String PASSWORD = "t008-synthetic-password";

    record Material(Path dir, Path trustStore, Path serverKeyStore, Path clientKeyStore) {}

    private TestCertificates() {}

    static Material generate(Path dir) throws IOException, InterruptedException {
        Path ca = dir.resolve("ca.p12");
        keytool(
                dir,
                "-genkeypair",
                "-alias",
                "ca",
                "-keyalg",
                "EC",
                "-groupname",
                "secp256r1",
                "-dname",
                "CN=T-008 synthetic test CA",
                "-ext",
                "bc:c",
                "-ext",
                "ku:c=keyCertSign,cRLSign",
                "-validity",
                "2",
                "-keystore",
                ca.toString(),
                "-storetype",
                "PKCS12",
                "-storepass",
                PASSWORD,
                "-keypass",
                PASSWORD);
        Path caPem = dir.resolve("ca.pem");
        keytool(
                dir,
                "-exportcert",
                "-alias",
                "ca",
                "-keystore",
                ca.toString(),
                "-storepass",
                PASSWORD,
                "-rfc",
                "-file",
                caPem.toString());
        Path trust = dir.resolve("truststore.p12");
        keytool(
                dir,
                "-importcert",
                "-noprompt",
                "-alias",
                "ca",
                "-file",
                caPem.toString(),
                "-keystore",
                trust.toString(),
                "-storetype",
                "PKCS12",
                "-storepass",
                PASSWORD);
        Path server = dir.resolve("server.p12");
        Path client = dir.resolve("client.p12");
        issueLeaf(dir, "server", "CN=localhost", "serverAuth", server);
        issueLeaf(dir, "client", "CN=t008-harness", "clientAuth", client);
        return new Material(dir, trust, server, client);
    }

    /** Re-issues a leaf with a new key and serial and swaps the file in place. */
    static void reissue(Material material, String alias) throws IOException, InterruptedException {
        Path target =
                alias.equals("server") ? material.serverKeyStore() : material.clientKeyStore();
        Path fresh = material.dir().resolve(alias + "-fresh.p12");
        Files.deleteIfExists(fresh);
        String eku = alias.equals("server") ? "serverAuth" : "clientAuth";
        String dn = alias.equals("server") ? "CN=localhost" : "CN=t008-harness";
        issueLeaf(material.dir(), alias, dn, eku, fresh);
        Files.move(
                fresh, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void issueLeaf(Path dir, String alias, String dn, String eku, Path keyStore)
            throws IOException, InterruptedException {
        Path ca = dir.resolve("ca.p12");
        Path caPem = dir.resolve("ca.pem");
        Path csr = dir.resolve(alias + ".csr");
        Path crt = dir.resolve(alias + ".crt");
        Files.deleteIfExists(keyStore);
        keytool(
                dir,
                "-genkeypair",
                "-alias",
                alias,
                "-keyalg",
                "EC",
                "-groupname",
                "secp256r1",
                "-dname",
                dn,
                "-validity",
                "2",
                "-keystore",
                keyStore.toString(),
                "-storetype",
                "PKCS12",
                "-storepass",
                PASSWORD,
                "-keypass",
                PASSWORD);
        keytool(
                dir,
                "-certreq",
                "-alias",
                alias,
                "-keystore",
                keyStore.toString(),
                "-storepass",
                PASSWORD,
                "-file",
                csr.toString());
        keytool(
                dir,
                "-gencert",
                "-alias",
                "ca",
                "-keystore",
                ca.toString(),
                "-storepass",
                PASSWORD,
                "-infile",
                csr.toString(),
                "-outfile",
                crt.toString(),
                "-rfc",
                "-validity",
                "2",
                "-ext",
                "san=dns:localhost,ip:127.0.0.1",
                "-ext",
                "ku:c=digitalSignature,keyEncipherment",
                "-ext",
                "eku=" + eku);
        keytool(
                dir,
                "-importcert",
                "-noprompt",
                "-alias",
                "ca",
                "-file",
                caPem.toString(),
                "-keystore",
                keyStore.toString(),
                "-storepass",
                PASSWORD);
        keytool(
                dir,
                "-importcert",
                "-noprompt",
                "-alias",
                alias,
                "-file",
                crt.toString(),
                "-keystore",
                keyStore.toString(),
                "-storepass",
                PASSWORD);
    }

    private static void keytool(Path dir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(args));
        Process process =
                new ProcessBuilder(command)
                        .directory(dir.toFile())
                        .redirectErrorStream(true)
                        .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException(
                    "keytool failed: " + String.join(" ", args) + "\n" + output);
        }
    }
}
