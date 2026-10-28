package com.link.networking;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SslContextFactory {

    private static final String KEYSTORE_PATH = "link_identity.p12";
    private static final char[] KEYSTORE_PASS = "link-p2p".toCharArray();
    private static final String KEY_ALIAS     = "link-peer";

    private static SSLContext sslContext;
    private static X509Certificate localCert;

    public static synchronized SSLContext getSSLContext() {
        if (sslContext == null) {
            try {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                File ksFile = new File(KEYSTORE_PATH);

                if (ksFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(ksFile)) {
                        keyStore.load(fis, KEYSTORE_PASS);
                    }
                    localCert = (X509Certificate) keyStore.getCertificate(KEY_ALIAS);
                } else {
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
                    new javax.net.ssl.X509ExtendedTrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        
                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        
                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {}
                        
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}
                        
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
                        
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
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
        Date notAfter  = new Date(now + 1000L * 60 * 60 * 24 * 365);

        byte[] algId = derSequence(concat(
                derOid(new int[]{1, 2, 840, 113549, 1, 1, 11}),
                derNull()));

        byte[] cn = derUtf8String("LinkP2P");
        byte[] atv = derSequence(concat(
                derOid(new int[]{2, 5, 4, 3}),
                cn));
        byte[] rdn = derSet(atv);
        byte[] name = derSequence(rdn);

        byte[] validity = derSequence(concat(
                derUtcTime(notBefore),
                derUtcTime(notAfter)));

        BigInteger serial = BigInteger.valueOf(now);
        byte[] serialDer = derInteger(serial.toByteArray());

        byte[] spki = keyPair.getPublic().getEncoded();

        byte[] tbsInner = concat(
                serialDer,
                algId,
                name,       
                validity,
                name,       
                spki);
        byte[] tbsBytes = derSequence(tbsInner);

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(tbsBytes);
        byte[] signatureValue = sig.sign();

        byte[] fullCert = derSequence(concat(
                tbsBytes,
                algId,
                derBitString(signatureValue)));

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(fullCert));
    }

    private static byte[] derTlv(int tag, byte[] value) {
        byte[] lenBytes = derLengthBytes(value.length);
        byte[] result = new byte[1 + lenBytes.length + value.length];
        result[0] = (byte) tag;
        System.arraycopy(lenBytes, 0, result, 1, lenBytes.length);
        System.arraycopy(value, 0, result, 1 + lenBytes.length, value.length);
        return result;
    }

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

    private static byte[] derSequence(byte[] content) {
        return derTlv(0x30, content);
    }

    private static byte[] derSet(byte[] content) {
        return derTlv(0x31, content);
    }

    private static byte[] derInteger(byte[] valueBytes) {
        return derTlv(0x02, valueBytes);
    }

    private static byte[] derNull() {
        return new byte[]{0x05, 0x00};
    }

    private static byte[] derUtf8String(String s) {
        return derTlv(0x0C, s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] derBitString(byte[] data) {
        byte[] content = new byte[1 + data.length];
        content[0] = 0x00; 
        System.arraycopy(data, 0, content, 1, data.length);
        return derTlv(0x03, content);
    }

    private static byte[] derUtcTime(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        byte[] ascii = sdf.format(date).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return derTlv(0x17, ascii);
    }

    private static byte[] derOid(int[] components) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(40 * components[0] + components[1]);
        for (int i = 2; i < components.length; i++) {
            writeBase128(buf, components[i]);
        }
        return derTlv(0x06, buf.toByteArray());
    }

    private static void writeBase128(ByteArrayOutputStream out, int value) {
        if (value < 128) {
            out.write(value);
            return;
        }
        int count = 0;
        int temp = value;
        byte[] stack = new byte[5]; 
        while (temp > 0) {
            stack[count++] = (byte) (temp & 0x7F);
            temp >>= 7;
        }
        for (int i = count - 1; i >= 0; i--) {
            if (i > 0) {
                out.write(stack[i] | 0x80);
            } else {
                out.write(stack[i]);
            }
        }
    }

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