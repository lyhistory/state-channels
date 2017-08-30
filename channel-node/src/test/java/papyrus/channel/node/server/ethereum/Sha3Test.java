package papyrus.channel.node.server.ethereum;

import java.math.BigInteger;

import org.junit.Assert;
import org.junit.Test;
import org.web3j.utils.Numeric;

public class Sha3Test {
    //from https://github.com/raineorshine/solidity-sha3/blob/master/test/spec.js
    @Test
    public void soliditySha3() throws Exception {
        Assert.assertEquals(
            "0xb10e2d527612073b26eecdfd717e6a320cf44b4afac2b0732d9fcbe2b7fa0cf6", 
            Numeric.toHexString(CryptoUtil.soliditySha3(1))
        );
        Assert.assertEquals(
            "0xb10e2d527612073b26eecdfd717e6a320cf44b4afac2b0732d9fcbe2b7fa0cf6", 
            Numeric.toHexString(CryptoUtil.soliditySha3(1))
        );
        Assert.assertEquals(
            "0xa9c584056064687e149968cbab758a3376d22aedc6a55823d1b3ecbee81b8fb9",
            Numeric.toHexString(CryptoUtil.soliditySha3(-1))
        );
        Assert.assertEquals(
            "0x26700e13983fefbd9cf16da2ed70fa5c6798ac55062a4803121a869731e308d2",
            Numeric.toHexString(CryptoUtil.soliditySha3(BigInteger.valueOf(100)))
        );
    }

}