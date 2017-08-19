package papyrus.channel.node.contract;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import com.google.common.base.Preconditions;

class ContractLinker {
    private static final int ADDRESS_CHARS = 40;
    private String binary;

    public ContractLinker(String binary) {
        this.binary = Preconditions.checkNotNull(binary);
    }

    public String getBinary() {
        return binary;
    }

    public boolean isLinked() {
        return !binary.contains("__");
    }

    public boolean link(String library, Address address) {
        //see https://github.com/ethereum/browser-solidity/blob/ab0593386a761e9755e3c79968767ffa8ad2fd82/src/universal-dapp.js#L634
        String libLabel = "__" + library + StringUtils.repeat('_', ADDRESS_CHARS - 2 - library.length());
        assert libLabel.length() == ADDRESS_CHARS;
        if (!binary.contains(libLabel)) return false;
        String addressHex = Numeric.toHexStringNoPrefixZeroPadded(address.getValue(), Keys.ADDRESS_LENGTH_IN_HEX);
        assert addressHex.length() == ADDRESS_CHARS;
        binary = StringUtils.replace(binary, libLabel, addressHex);
        return true;
    }

    public Set<String> getLibraries() {
        Set<String> libraries = new HashSet<>();
        int pos = 0;
        while (true) {
            pos = binary.indexOf('_', pos);
            if (pos < 0) break;
            String library = binary.substring(pos, pos + ADDRESS_CHARS).replace('_', ' ').trim().replace(' ', '_');
            libraries.add(library);
            pos += ADDRESS_CHARS;
        }
        return libraries;
    }

    public void assertLinked() {
        if (!isLinked()) {
            throw new IllegalStateException("Not linked libraries: " + getLibraries());
        }
    }
}
