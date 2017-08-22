package papyrus.channel.node.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NodeConfiguration {
    @Bean
    public ScheduledExecutorService getExecutor() {
        return Executors.newScheduledThreadPool(4);
    }
}
