package hu.rapids.monitor;
import hu.rapids.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
@ApplicationScoped
public class PerformanceConsumer implements MessageHandler {
    @Inject Statistics statistics;
    @Inject ObjectMapper mapper;
    private final String group="monitor-performance-"+java.util.UUID.randomUUID();
    public String group() { return group; }
    public List<String> topics() { return List.of("performance"); }
    public void handle(MessageBus.Message message) throws Exception {
        statistics.module(message.key(),mapper.readTree(message.value()));
    }
}
