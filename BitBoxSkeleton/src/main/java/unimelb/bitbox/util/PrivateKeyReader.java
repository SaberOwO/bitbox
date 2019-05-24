package unimelb.bitbox.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;

public class PrivateKeyReader {
    private String pathName;

    public PrivateKeyReader(String pathName) {
        this.pathName = pathName;
    }

    public PrivateKey generatePrivateKey() throws IOException {
        FileReader fileReader = new FileReader(pathName);
        PEMParser parser = new PEMParser(fileReader);
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());
        KeyPair kp = converter.getKeyPair((PEMKeyPair) parser.readObject());
        return kp.getPrivate();
    }

}
