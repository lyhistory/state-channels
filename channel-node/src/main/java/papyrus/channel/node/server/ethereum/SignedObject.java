package papyrus.channel.node.server.ethereum;

import java.security.SignatureException;

import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.ECKeyPair;

import com.datastax.driver.mapping.annotations.Transient;

import papyrus.channel.node.entity.DataObject;

public abstract class SignedObject extends DataObject implements HashedObject {
    protected byte[] signature;

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public void sign(ECKeyPair keyPair) {
        byte[] hash = hash();
        signature = CryptoUtil.sign(keyPair, hash);
    }

    public void verifySignature(Address expectedSigner) throws SignatureException {
        Address signerAddress = getSignerAddress();
        if (!expectedSigner.equals(signerAddress)) {
            throw new SignatureException("Invalid signature address: " + signerAddress + ", expected: " + expectedSigner);
        }
    }

    @Transient
    public Address getSignerAddress() throws SignatureException {
        return CryptoUtil.verifySignature(signature, hash());
    }
}
