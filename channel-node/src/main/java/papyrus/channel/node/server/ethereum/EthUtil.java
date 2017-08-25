package papyrus.channel.node.server.ethereum;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SignatureException;
import java.util.Arrays;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Uint;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;

import com.google.common.base.Preconditions;

public class EthUtil {
    public static String getContractAddress(String fromAddress, long nonce) {
        byte[] bytes = RlpEncoder.encode(new RlpList(RlpString.create(Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(fromAddress))), RlpString.create(nonce)));
        byte[] sha3 = Hash.sha3(bytes);
        String hash = Numeric.toHexStringNoPrefix(sha3);
        return hash.substring(24);
    }

    public static String getContractCode(Web3j web3j, String contractAddress) throws IOException {
        EthGetCode ethGetCode = web3j
            .ethGetCode(contractAddress, DefaultBlockParameterName.LATEST)
            .send();

        if (ethGetCode.hasError()) {
            throw new IllegalStateException("Failed to get code for " + contractAddress + ": " + ethGetCode.getError().getMessage());
        }

        return Numeric.cleanHexPrefix(ethGetCode.getCode());
    }

    public static byte[] sign(ECKeyPair keyPair, byte[] hash) {
        Sign.SignatureData signatureData = Sign.signMessage(hash, keyPair);
        return signatureEncode(signatureData);
    }

    public static Address verifySignature(byte[] signature, byte[] hash) throws SignatureException {
        Sign.SignatureData signatureData = signatureSplit(signature);
        BigInteger publicKey = Sign.signedMessageToKey(hash, signatureData);
        return getAddress(publicKey);
    }

    public static Address getAddress(BigInteger publicKey) {
        //TODO get rid of conversions string<->byte[]
        return new Address(Keys.getAddress(publicKey));
    }

    public static byte[] toBytes32(Object obj) {
        if (obj instanceof byte[]) {
            Preconditions.checkArgument(((byte[]) obj).length == 32);
            return (byte[]) obj;
        } else if (obj instanceof BigInteger) {
            return Numeric.toBytesPadded((BigInteger) obj, 32);
        } else if (obj instanceof Uint) {
            return toBytes32(((Uint) obj).getValue());
        } else if (obj instanceof Number) {
            return toBytes32(BigInteger.valueOf(((Number) obj).longValue()));
        }
        throw new IllegalArgumentException(obj.getClass().getName());
    }

    public static byte[] signatureEncode(Sign.SignatureData signatureData) {
        Preconditions.checkArgument(signatureData.getR().length == 32);
        Preconditions.checkArgument(signatureData.getS().length == 32);
        Preconditions.checkArgument(signatureData.getV() == 27 || signatureData.getV() == 28);
        ByteBuffer buffer = ByteBuffer.allocate(65);
        buffer.put(signatureData.getR());
        buffer.put(signatureData.getS());
        buffer.put(signatureData.getV());
        assert buffer.position() == 65;
        return buffer.array();
    }

    public static Sign.SignatureData signatureSplit(byte[] signature) {
        Preconditions.checkArgument(signature.length == 65);
        byte v = signature[64];
        Preconditions.checkArgument(v == 27 || v ==28);
        byte[] r = Arrays.copyOfRange(signature, 0, 32);
        byte[] s = Arrays.copyOfRange(signature, 32, 64);
        return new Sign.SignatureData(v, r, s);
    }

    public static byte[] soliditySha3(Object... data) {
        ByteBuffer buffer = ByteBuffer.allocate(data.length * 32);
        for (Object d : data) {
            buffer.put(toBytes32(d));
        }
        byte[] array = buffer.array();
        assert buffer.position() == array.length;
        return Hash.sha3(array);
    }
}
