package papyrus.channel.node;

import org.junit.Assert;
import org.junit.Test;

import papyrus.channel.node.contract.EthUtil;

public class TestUtil {
    @Test
    public void testContractAddress() {
        String address = "6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0";
        Assert.assertEquals("cd234a471b72ba2f1ccf0a70fcaba648a5eecd8d", EthUtil.getContractAddress(address, 0));
        Assert.assertEquals("343c43a37d37dff08ae8c4a11544c718abb4fcf8", EthUtil.getContractAddress(address, 1));
        Assert.assertEquals("f778b86fa74e846c4f0a1fbd1335fe81c00a0c91", EthUtil.getContractAddress(address, 2));
    }
}
