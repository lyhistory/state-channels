package papyrus.channel.node.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;

import papyrus.channel.node.ChannelPoolMessage;
import papyrus.channel.node.GetChannelPoolsRequest;

public class PersistenceTest extends BaseChannelTest {
    @Test
    public void test() throws ExecutionException, InterruptedException {
        //add participant - this will initiate channels opening

        BigDecimal deposit = new BigDecimal("0.01");

        openChannel(ChannelPoolMessage.newBuilder()
            .setDeposit(deposit.toString())
            .build()
        );
        
        //restart server
        stopContext(sender);
        initSender();

        List<ChannelPoolMessage> poolList = senderClient.getClientAdmin().getChannelPools(
            GetChannelPoolsRequest.newBuilder()
                .setSenderAddress(senderAddress.toString())
                .build()
        ).getPoolList();

        Assert.assertEquals(1, poolList.size());
    }
}
