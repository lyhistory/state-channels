package test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;

import org.junit.Assert;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

public class BenchmarkEC {
    static final byte [] message= "some message 1234".getBytes();
    
    @Benchmark
    public void sign(TestData data) {
        Sign.signMessage(message, data.keyPair);
    }

    @Benchmark
    public void verify(TestData data) {
        try {
            BigInteger key = Sign.signedMessageToKey(message, data.signatureData);
            Assert.assertEquals(data.keyPair.getPublicKey(), key);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }
    
    @State(Scope.Benchmark)
    public static class TestData {
        private final ECKeyPair keyPair;
        private final Sign.SignatureData signatureData;

        public TestData() {
            try {
                keyPair = Keys.createEcKeyPair();
                signatureData = Sign.signMessage(message, keyPair);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) throws IOException, CipherException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        Credentials credentials = Credentials.create(Keys.createEcKeyPair());
        System.out.println(credentials.getEcKeyPair().getPublicKey().toString(16));
        System.out.println(credentials.getAddress());
        System.out.println("0x" + Keys.getAddress(credentials.getEcKeyPair().getPublicKey()));
    }
}
