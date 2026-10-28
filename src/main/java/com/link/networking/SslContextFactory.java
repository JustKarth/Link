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

    // ── Self-signed X.509 v1 certificate generation (pure DER) ──────────

    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 1000L * 60 * 60 * 24);
        Date notAfter  = new Date(now + 1000L * 60 * 60 * 24 * 365);

        // AlgorithmIdentifier: SHA256withRSA  OID 1.2.840.113549.1.1.11, NULL params
        byte[] algId = derSequence(concat(
                derOid(new int[]{1, 2, 840, 113549, 1, 1, 11}),
                derNull()));

        // Issuer & Subject: CN=LinkP2P
        byte[] cn = derUtf8String("LinkP2P");
        byte[] atv = derSequence(concat(
                derOid(new int[]{2, 5, 4, 3}),  // OID for commonName
                cn));
        byte[] rdn = derSet(atv);
        byte[] name = derSequence(rdn);

        // Validity
        byte[] validity = derSequence(concat(
                derUtcTime(notBefore),
                derUtcTime(notAfter)));

        // Serial number
        BigInteger serial = BigInteger.valueOf(now);
        byte[] serialDer = derInteger(serial.toByteArray());

        // SubjectPublicKeyInfo (already DER-encoded by the JCA provider)
        byte[] spki = keyPair.getPublic().getEncoded();

        // TBSCertificate (v1 — no explicit version tag needed, defaults to v1)
        byte[] tbsInner = concat(
                serialDer,
                algId,
                name,       // issuer
                validity,
                name,       // subject
                spki);
        byte[] tbsBytes = derSequence(tbsInner);

        // Sign the TBSCertificate
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(tbsBytes);
        byte[] signatureValue = sig.sign();

        // Full Certificate ::= SEQUENCE { tbsCertificate, signatureAlgorithm, signatureValue }
        byte[] fullCert = derSequence(concat(
                tbsBytes,
                algId,
                derBitString(signatureValue)));

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(fullCert));
    }

    // ── DER primitive encoders ──────────────────────────────────────────

    /** Encode a tag + length + value. */
    private static byte[] derTlv(int tag, byte[] value) {
        byte[] lenBytes = derLengthBytes(value.length);
        byte[] result = new byte[1 + lenBytes.length + value.length];
        result[0] = (byte) tag;
        System.arraycopy(lenBytes, 0, result, 1, lenBytes.length);
        System.arraycopy(value, 0, result, 1 + lenBytes.length, value.length);
        return result;
    }

    /** DER definite-length encoding. */
    private static byte[] derLengthBytes(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        } else if (length < 256) {
            return new byte[]{(byte) 0x81, (byte) length};
        } else if (length < 65536) {
            return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
        } else {
            return new byte[]{(byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length};
        }
    }

    /** SEQUENCE (0x30) */
    private static byte[] derSequence(byte[] content) {
        return derTlv(0x30, content);
    }

    /** SET (0x31) */
    private static byte[] derSet(byte[] content) {
        return derTlv(0x31, content);
    }

    /** INTEGER (0x02) */
    private static byte[] derInteger(byte[] valueBytes) {
        return derTlv(0x02, valueBytes);
    }

    /** NULL (0x05 0x00) */
    private static byte[] derNull() {
        return new byte[]{0x05, 0x00};
    }

    /** UTF8String (0x0C) */
    private static byte[] derUtf8String(String s) {
        return derTlv(0x0C, s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** BIT STRING (0x03) — wraps raw signature bytes with a leading 0 unused-bits octet. */
    private static byte[] derBitString(byte[] data) {
        byte[] content = new byte[1 + data.length];
        content[0] = 0x00; // zero unused bits
        System.arraycopy(data, 0, content, 1, data.length);
        return derTlv(0x03, content);
    }

    /** UTCTime (0x17) */
    private static byte[] derUtcTime(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        byte[] ascii = sdf.format(date).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return derTlv(0x17, ascii);
    }

    /** OID (0x06) — encodes an ASN.1 Object Identifier from its integer components. */
    private static byte[] derOid(int[] components) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        // First two arcs are encoded as 40 * c[0] + c[1]
        buf.write(40 * components[0] + components[1]);
        for (int i = 2; i < components.length; i++) {
            writeBase128(buf, components[i]);
        }
        return derTlv(0x06, buf.toByteArray());
    }

    /** Encode a non-negative integer in base-128 with high-bit continuation. */
    private static void writeBase128(ByteArrayOutputStream out, int value) {
        if (value < 128) {
            out.write(value);
            return;
        }
        // Collect base-128 digits in reverse
        int count = 0;
        int temp = value;
        byte[] stack = new byte[5]; // enough for 32-bit int
        while (temp > 0) {
            stack[count++] = (byte) (temp & 0x7F);
            temp >>= 7;
        }
        // Write them out most-significant first, setting high bit on all but last
        for (int i = count - 1; i >= 0; i--) {
            if (i > 0) {
                out.write(stack[i] | 0x80);
            } else {
                out.write(stack[i]);
            }
        }
    }

    /** Concatenate byte arrays. */
    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}
