package com.link.networking;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SslContextFactory {

    private static SSLContext sslContext;
    private static X509Certificate localCert;

    public static synchronized SSLContext getSSLContext() {
        if (sslContext == null) {
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair keyPair = keyGen.generateKeyPair();

                localCert = generateSelfSignedCertificate(keyPair);

                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null);

                keyStore.setKeyEntry("link-peer", keyPair.getPrivate(), "pass".toCharArray(),
                        new java.security.cert.Certificate[]{localCert});

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, "pass".toCharArray());

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