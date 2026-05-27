package util;

import java.io.File;
import java.io.FileWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

public class RSAKeyGenerator {

    public static void main(String[] args) throws Exception {
        int keySize = 2048;
        String folderPath = "D:\\tcc\\keys";
        File folder = new File(folderPath);

        if (!folder.exists()) {
            folder.mkdir();
            System.out.println("pasta criada");

        }
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(keySize);
        KeyPair keyPair = keyGen.generateKeyPair();

        RSAPublicKey publickey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privatekey = (RSAPrivateKey) keyPair.getPrivate();

        saveKeyToFile(folderPath + "/public_key.pem", "PUBLIC KEY", publickey.getEncoded());
        saveKeyToFile(folderPath + "/private_key.pem", "PRIVATE KEY", privatekey.getEncoded());

        System.out.println("chaves criadas, pasta: " + folderPath);
    }

    private static void saveKeyToFile(String filePath, String type, byte[] keyBytes) throws Exception {
        String pem = "-----BEGIN " + type + "-----\n" +
                Base64.getEncoder().encodeToString(keyBytes).replaceAll("(.{64})", "$1\n") +
                "\n-----END " + type + "-----\n";
        try(FileWriter fw = new FileWriter(filePath)) {
            fw.write(pem);
        }
        System.out.println("sucesso" + filePath);
    }
}