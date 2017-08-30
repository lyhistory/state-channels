package papyrus.channel.node.server.ethereum;

import java.security.SignatureException;
import java.util.function.Predicate;

import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.ECKeyPair;

import papyrus.channel.node.entity.DataObject;

public abstract class SignedObject extends DataObject {
    protected byte[] signature;

    protected abstract byte[] hash();

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

    public void verifySignature(Predicate<Address> addressPredicate) throws SignatureException {
        Address address = CryptoUtil.verifySignature(signature, hash());
        if (!addressPredicate.test(address)) {
            throw new SignatureException("Invalid signature address: " + address);
        }
    }
}
