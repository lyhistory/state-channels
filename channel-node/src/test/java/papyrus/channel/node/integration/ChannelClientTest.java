package papyrus.channel.node.integration;

import papyrus.channel.ChannelPropertiesMessage;
import papyrus.channel.node.AddChannelPoolRequest;
import papyrus.channel.node.ChannelStatusRequest;
import papyrus.channel.node.ChannelStatusResponse;
import papyrus.channel.node.HealthCheckRequest;

public class ChannelClientTest {
    public static final String SSP_ADDRESS = "51ec6501f31d2ae6cd799864b3598e188495cd76";
    public static final String DSP_ADDRESS = "b508d41ecb22e9b9bb85c15b5fb3a90cdaddc4ea";

    public static void main(String[] args) throws Exception {
        NodeClientFactory dsp = new NodeClientFactory("grpc://35.195.5.183:8080");
        System.out.println("DSP uid: " + dsp.getClientAdmin().healthCheck(HealthCheckRequest.newBuilder().build()).getServerUid());

        NodeClientFactory ssp = new NodeClientFactory("grpc://35.195.91.148:8080");
        System.out.println("DSP uid: " + ssp.getClientAdmin().healthCheck(HealthCheckRequest.newBuilder().build()).getServerUid());

        System.out.printf("Opening channel %s->%s%n", DSP_ADDRESS, SSP_ADDRESS);

        AddChannelPoolRequest.Builder builder = AddChannelPoolRequest.newBuilder();
        builder.setSenderAddress(DSP_ADDRESS);
        builder.setReceiverAddress(SSP_ADDRESS);
        builder.setMinActiveChannels(1);
        builder.setMaxActiveChannels(1);
        builder.setDeposit("00.01");
        ChannelPropertiesMessage.Builder propertiesBuilder = builder.getPropertiesBuilder();
        propertiesBuilder.setCloseTimeout(1);
        propertiesBuilder.setSettleTimeout(6);
        
        dsp.getClientAdmin().addChannelPool(builder.build());

        ChannelStatusResponse response = Util.waitFor(() ->
                ssp.getOutgoingChannelClient().getChannels(
                    ChannelStatusRequest.newBuilder()
                        .setSenderAddress(DSP_ADDRESS)
                        .setReceiverAddress(SSP_ADDRESS)
                        .build()
                ),
            r -> !r.getChannelList().isEmpty()
        );

        String channelAddress = response.getChannel(0).getChannelAddress();
        System.out.printf("Channel address: %s%n", channelAddress);



    }
}
