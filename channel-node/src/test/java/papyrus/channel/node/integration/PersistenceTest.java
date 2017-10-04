package papyrus.channel.node.integration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import papyrus.channel.node.ChannelPoolMessage;
import papyrus.channel.node.ChannelStatusMessage;
import papyrus.channel.node.ChannelStatusRequest;
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

        List<ChannelStatusMessage> channelList = senderClient.getOutgoingChannelClient().getChannels(
            ChannelStatusRequest.newBuilder()
                .setSenderAddress(senderAddress.toString())
                .setReceiverAddress(receiverAddress.toString())
                .build()
        ).getChannelList();

        Assert.assertEquals(1, channelList.size());
    }

    @Override
    protected void configureContext(SpringApplicationBuilder builder) {
        builder.properties(Collections.singletonMap("eth.sync", "true"));
    }
}
