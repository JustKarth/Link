package com.link.networking;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SslContextFactory {

    /** PKCS12 keystore that persists the local identity across restarts. */
    private static final String KEYSTORE_PATH = "link_identity.p12";
    private static final char[] KEYSTORE_PASS = "link-p2p".toCharArray();
    private static final String KEY_ALIAS     = "link-peer";

    private static SSLContext sslContext;
    private static X509Certificate localCert;

    public static synchronized SSLContext getSSLContext() {
        if (sslContext == null) {
            try {
                // Load or generate a persistent keypair so the TLS fingerprint
                // stays the same across JVM restarts. Without this, every restart
                // produces a new fingerprint and isPeerTrusted() throws a
                // SecurityException("POSSIBLE MITM ATTACK") for every previously
                // trusted peer, causing all transfers to fail immediately.
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                File ksFile = new File(KEYSTORE_PATH);

                if (ksFile.exists()) {
                    // Load the existing identity from disk.
                    try (FileInputStream fis = new FileInputStream(ksFile)) {
                        keyStore.load(fis, KEYSTORE_PASS);
                    }
                    localCert = (X509Certificate) keyStore.getCertificate(KEY_ALIAS);
                } else {
                    // First run: generate a new keypair and persist it.
                    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                    keyGen.initialize(2048);
                    KeyPair keyPair = keyGen.generateKeyPair();

                    localCert = generateSelfSignedCertificate(keyPair);

                    keyStore.load(null, null);
                    keyStore.setKeyEntry(KEY_ALIAS, keyPair.getPrivate(), KEYSTORE_PASS,
                            new java.security.cert.Certificate[]{localCert});

                    try (FileOutputStream fos = new FileOutputStream(ksFile)) {
                        keyStore.store(fos, KEYSTORE_PASS);
                    }
                }

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, KEYSTORE_PASS);

                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
                };

                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(kmf.getKeyManagers(), trustAllCerts, new SecureRandom());
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize TLS Context", e);
            }
        }
        return sslContext;
    }

    public static X509Certificate getLocalCertificate() {
        if (localCert == null) getSSLContext();
        return localCert;
    }

    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 1000L * 60 * 60 * 24);
        Date notAfter = new Date(now + 1000L * 60 * 60 * 24 * 365);

        // Standard ASN.1 DER sequence construction for self-signed X.509
        ByteArrayOutputStream tbs = new ByteArrayOutputStream();
        tbs.write(new byte[]{0x02, 0x01, 0x00}); // Version v1 (0)
        
        BigInteger serial = BigInteger.valueOf(now);
        byte[] serialBytes = serial.toByteArray();
        tbs.write(0x02); // INTEGER
        tbs.write(serialBytes.length);
        tbs.write(serialBytes);

        // AlgorithmId: SHA256withRSA (1.2.840.113549.1.1.11)
        byte[] algId = new byte[]{0x30, 0x0d, 0x06, 0x09, 0x2a, (byte)0x86, 0x48, (byte)0x86, (byte)0xf7, 0x0d, 0x01, 0x01, 0x0b, 0x05, 0x00};
        tbs.write(algId);

        // Issuer & Subject Name: CN=LinkNode
        byte[] name = new byte[]{0x30, 0x11, 0x31, 0x0f, 0x30, 0x0d, 0x06, 0x03, 0x55, 0x04, 0x03, 0x0c, 0x06, 'L', 'i', 'n', 'k', 'P', '2', 'P'};
        tbs.write(name); // Issuer
        
        // Validity
        ByteArrayOutputStream validity = new ByteArrayOutputStream();
        writeUtcTime(validity, notBefore);
        writeUtcTime(validity, notAfter);
        writeSequence(tbs, validity.toByteArray());

        tbs.write(name); // Subject

        // SubjectPublicKeyInfo
        tbs.write(keyPair.getPublic().getEncoded());

        ByteArrayOutputStream tbsSeq = new ByteArrayOutputStream();
        writeSequence(tbsSeq, tbs.toByteArray());
        byte[] tbsBytes = tbsSeq.toByteArray();

        // Sign TBS
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(tbsBytes);
        byte[] signature = sig.sign();

        // Wrap full Certificate sequence
        ByteArrayOutputStream certStream = new ByteArrayOutputStream();
        certStream.write(tbsBytes);
        certStream.write(algId);
        
        // BIT STRING for signature
        certStream.write(0x03);
        certStream.write(signature.length + 1);
        certStream.write(0x00); // 0 unused bits
        certStream.write(signature);

        ByteArrayOutputStream fullCert = new ByteArrayOutputStream();
        writeSequence(fullCert, certStream.toByteArray());

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(fullCert.toByteArray()));
    }

    private static void writeSequence(ByteArrayOutputStream out, byte[] data) {
        out.write(0x30);
        writeLength(out, data.length);
        out.write(data, 0, data.length);
    }

    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 128) {
            out.write(length);
        } else if (length < 256) {
            out.write(0x81);
            out.write(length);
        } else {
            out.write(0x82);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }

    private static void writeUtcTime(ByteArrayOutputStream out, Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        byte[] bytes = sdf.format(date).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(0x17); // UTCTime tag
        out.write(bytes.length);
        out.write(bytes, 0, bytes.length);
    }
}