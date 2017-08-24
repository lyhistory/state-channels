package papyrus.channel.node.contract;

import java.io.IOException;

import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;

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
}
