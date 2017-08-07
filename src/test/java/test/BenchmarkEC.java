package test;

import java.math.BigInteger;
import java.security.SignatureException;

import org.junit.Assert;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;
import org.web3j.crypto.WalletUtils;

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
            Assert.assertEquals(data.credentials.getEcKeyPair().getPublicKey(), key);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }
    
    @State(Scope.Benchmark)
    public static class TestData {
        private final Credentials credentials;
        private final ECKeyPair keyPair;
        private final Sign.SignatureData signatureData;

        public TestData() {
            try {
                credentials = WalletUtils.loadCredentials("m3tA3tPNY", "/Users/leonidtalalaev/Library/Ethereum/keystore/UTC--2017-05-08T11-53-11.885023223Z--0a3caf76f679a442561fa953b01651f8921c63fd");
                keyPair = credentials.getEcKeyPair();
                signatureData = Sign.signMessage(message, keyPair);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
