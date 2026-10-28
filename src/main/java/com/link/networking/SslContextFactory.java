package com.link.networking;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
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
        Date notBefore = new Date(now - 86_400_000L);
        Date notAfter  = new Date(now + 365L * 86_400_000L);

        // AlgorithmIdentifier for SHA256withRSA (OID 1.2.840.113549.1.1.11)
        byte[] algId = new byte[]{
            0x30, 0x0d,
            0x06, 0x09, 0x2a, (byte)0x86, 0x48, (byte)0x86, (byte)0xf7, 0x0d, 0x01, 0x01, 0x0b,
            0x05, 0x00
        };

        // Issuer / Subject Name: SEQUENCE { SET { SEQUENCE { OID CN, UTF8String "LinkP2P" } } }
        byte[] name = new byte[]{
            0x30, 0x13,
            0x31, 0x11,
            0x30, 0x0f,
            0x06, 0x03, 0x55, 0x04, 0x03,        // OID 2.5.4.3 = CN
            0x0c, 0x08,                            // UTF8String, length 8
            'L', 'i', 'n', 'k', 'P', '2', 'P', '2'
        };

        // SerialNumber
        BigInteger serial = BigInteger.valueOf(now & 0x7FFFFFFFFFFFFFFFL);
        byte[] serialBytes = serial.toByteArray();
        byte[] serialTlv = tlv(0x02, serialBytes);

        // Validity SEQUENCE { UTCTime notBefore, UTCTime notAfter }
        byte[] validityTlv = tlv(0x30, concat(utcTime(notBefore), utcTime(notAfter)));

        // SubjectPublicKeyInfo
        byte[] spki = keyPair.getPublic().getEncoded();

        // TBSCertificate body (v1: no explicit Version field)
        byte[] tbsBody = concat(serialTlv, algId, name, validityTlv, name, spki);
        byte[] tbsCert = tlv(0x30, tbsBody);

        // Sign the TBSCertificate
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(tbsCert);
        byte[] sigBytes = sig.sign();

        // BIT STRING: tag 0x03 + length + 0x00 (no unused bits) + signature bytes
        byte[] bitStringContent = concat(new byte[]{0x00}, sigBytes);
        byte[] bitStringTlv = tlv(0x03, bitStringContent);

        // Full certificate: SEQUENCE { TBSCertificate, AlgorithmId, BIT STRING }
        byte[] certDer = tlv(0x30, concat(tbsCert, algId, bitStringTlv));

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
                new java.io.ByteArrayInputStream(certDer));
    }

    /** Encode a DER TLV: tag byte + proper length encoding + value bytes. */
    private static byte[] tlv(int tag, byte[] value) {
        int len = value.length;
        byte[] lenBytes;
        if (len < 128) {
            lenBytes = new byte[]{(byte) len};
        } else if (len < 256) {
            lenBytes = new byte[]{(byte) 0x81, (byte) len};
        } else {
            lenBytes = new byte[]{(byte) 0x82, (byte) (len >> 8), (byte) (len & 0xFF)};
        }
        byte[] result = new byte[1 + lenBytes.length + len];
        result[0] = (byte) tag;
        System.arraycopy(lenBytes, 0, result, 1, lenBytes.length);
        System.arraycopy(value, 0, result, 1 + lenBytes.length, len);
        return result;
    }

    /** Encode a Date as a DER UTCTime (tag 0x17). */
    private static byte[] utcTime(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        byte[] bytes = sdf.format(date).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return tlv(0x17, bytes);
    }

    /** Concatenate byte arrays. */
    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) { System.arraycopy(a, 0, result, pos, a.length); pos += a.length; }
        return result;
    }
}
