package com.natibiton.babytogether.installer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** No network, persisted private key, or logging of signing material. */
public final class LocalSigner {
    public static final String ORIGINAL_SHA1 = "9D06C8E214DB0D27D29EAB8F544AC766D0666D91C";
    public static final String PACKAGE = "com.natibiton.babytogether";
    private static final int MAX_BACKUP = 1024 * 1024;
    private LocalSigner() {}

    public static File prepareUpdate(Context context, InputStream backup) throws Exception {
        File input = new File(context.getCacheDir(), "baby-together-unsigned.apk");
        File directory = new File(context.getCacheDir(), "signed");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create update directory");
        File output = new File(directory, "baby-together-0.3.0.apk");
        try {
            try (InputStream asset = context.getAssets().open("baby-together-unsigned.apk");
                 OutputStream destination = new FileOutputStream(input)) {
                copy(asset, destination);
            }
            if (!digest(input, "SHA-256").equalsIgnoreCase(BuildConfig.UPDATE_SHA256))
                throw new SecurityException("Bundled update integrity check failed");
            checkIdentity(context, input);
            sign(input, output, backup, ORIGINAL_SHA1);
            checkIdentity(context, output);
            return output;
        } catch (Exception e) {
            output.delete();
            throw e;
        } finally {
            input.delete();
        }
    }

    static void checkIdentity(Context context, File apk) {
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null || !PACKAGE.equals(info.packageName) || info.getLongVersionCode() != 3
                || !"0.3.0".equals(info.versionName))
            throw new SecurityException("Unexpected application or version");
    }

    /** Package-private fingerprint parameter exists for synthetic-key instrumentation tests only. */
    static void sign(File input, File output, InputStream backup, String expectedSha1) throws Exception {
        byte[] archive = null, keyBytes = null, passwordBytes = null;
        char[] password = null;
        try {
            archive = boundedRead(backup, MAX_BACKUP);
            int entries = 0, expanded = 0;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > 32) throw new IOException("Too many backup entries");
                    if (entry.isDirectory()) continue;
                    String name = entry.getName().replace('\\', '/');
                    name = name.substring(name.lastIndexOf('/') + 1);
                    byte[] data = boundedRead(zip, MAX_BACKUP);
                    expanded += data.length;
                    if (expanded > MAX_BACKUP) {
                        Arrays.fill(data, (byte) 0);
                        throw new IOException("Backup expands beyond limit");
                    }
                    if (name.equals("baby-together-signing.p12")) {
                        if (keyBytes != null) { Arrays.fill(data, (byte) 0); throw new IOException("Duplicate key"); }
                        keyBytes = data;
                    } else if (name.equals("keystore-password.txt")) {
                        if (passwordBytes != null) { Arrays.fill(data, (byte) 0); throw new IOException("Duplicate password"); }
                        passwordBytes = data;
                    } else {
                        Arrays.fill(data, (byte) 0);
                    }
                }
            }
            if (keyBytes == null || passwordBytes == null) throw new IOException("Incomplete signing backup");
            // Backup password file contains one line. Do not change intentional leading spaces.
            String passwordText = new String(passwordBytes, StandardCharsets.UTF_8);
            passwordText = passwordText.replaceFirst("[\\r\\n]+$", "");
            password = passwordText.toCharArray();
            KeyStore store = KeyStore.getInstance("PKCS12", new BouncyCastleProvider());
            store.load(new ByteArrayInputStream(keyBytes), password);
            PrivateKey key = null;
            List<X509Certificate> certificates = null;
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!store.isKeyEntry(alias)) continue;
                java.security.cert.Certificate certificate = store.getCertificate(alias);
                if (!(certificate instanceof X509Certificate)) continue;
                if (!hex(MessageDigest.getInstance("SHA-1").digest(certificate.getEncoded()))
                        .equalsIgnoreCase(expectedSha1)) continue;
                Key candidate = store.getKey(alias, password);
                if (!(candidate instanceof PrivateKey)) continue;
                key = (PrivateKey) candidate;
                certificates = new ArrayList<>();
                for (java.security.cert.Certificate chain : store.getCertificateChain(alias))
                    certificates.add((X509Certificate) chain);
                break;
            }
            if (key == null) throw new SecurityException("Backup is not the original signing key");
            ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder("Baby Together", key, certificates).build();
            new ApkSigner.Builder(Collections.singletonList(config))
                .setInputApk(input).setOutputApk(output).setMinSdkVersion(26)
                .setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true)
                .setV4SigningEnabled(false).build().sign();
            verify(output, expectedSha1);
        } catch (Exception e) {
            output.delete();
            throw e;
        } finally {
            if (archive != null) Arrays.fill(archive, (byte) 0);
            if (keyBytes != null) Arrays.fill(keyBytes, (byte) 0);
            if (passwordBytes != null) Arrays.fill(passwordBytes, (byte) 0);
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    static void verify(File apk, String expectedSha1) throws Exception {
        ApkVerifier.Result result = new ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(26).build().verify();
        if (!result.isVerified() || !result.isVerifiedUsingV2Scheme() || !result.isVerifiedUsingV3Scheme()
                || result.getSignerCertificates().size() != 1
                || !hex(MessageDigest.getInstance("SHA-1").digest(result.getSignerCertificates().get(0).getEncoded()))
                   .equalsIgnoreCase(expectedSha1))
            throw new SecurityException("Signed update verification failed");
    }

    static byte[] boundedRead(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        try {
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maximum) throw new IOException("Backup exceeds size limit");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally { Arrays.fill(buffer, (byte) 0); }
    }

    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    static String digest(File file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02X", b & 255));
        return result.toString();
    }
}
