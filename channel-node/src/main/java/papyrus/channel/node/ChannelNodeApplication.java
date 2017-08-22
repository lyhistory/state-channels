package papyrus.channel.node;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import papyrus.channel.node.server.NodeServer;

@SpringBootApplication(scanBasePackageClasses = ChannelNodeApplication.class)
public class ChannelNodeApplication {
    
    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext context = SpringApplication.run(ChannelNodeApplication.class, args);
        context.start();
        context.getBean(NodeServer.class).awaitTermination();
    }
    
}
