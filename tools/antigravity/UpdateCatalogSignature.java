import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

class UpdateCatalogSignature {
    public static void main(String[] args) throws Exception {
        var keystore = KeyStore.getInstance("JKS");
        var password = "android".toCharArray();
        try (var file = Files.newInputStream(Path.of(System.getenv("RIKKA_DEBUG_KEYSTORE")))) {
            keystore.load(file, password);
        }
        var signature = Signature.getInstance("SHA256withRSA");
        var alias = "androiddebugkey";
        try (InputStream input = System.in) {
            byte[] payload = input.readAllBytes();
            if (args[0].equals("sign")) {
                signature.initSign((PrivateKey) keystore.getKey(alias, password));
                signature.update(payload);
                System.out.write(signature.sign());
            } else if (args[0].equals("verify")) {
                signature.initVerify(keystore.getCertificate(alias));
                signature.update(payload);
                if (!signature.verify(java.util.Base64.getDecoder().decode(args[1]))) {
                    throw new SecurityException("Update catalog signature mismatch");
                }
            } else {
                throw new IllegalArgumentException("Expected sign or verify");
            }
        }
    }
}
