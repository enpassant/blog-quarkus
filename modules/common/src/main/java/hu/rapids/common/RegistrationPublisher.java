package hu.rapids.common;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.annotation.PreDestroy;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.util.*;
import java.util.concurrent.*;
@ApplicationScoped
public class RegistrationPublisher {
    @Inject Instance<ModuleDescriptor> descriptors;
    @Inject MessageBus bus;
    @Inject Statistics statistics;
    @ConfigProperty(name="quarkus.application.name",defaultValue="rapids-app") String application;
    @ConfigProperty(name="blog.registration.base-url",defaultValue="http://localhost:8080") String baseUrl;
    private final String instance=UUID.randomUUID().toString();
    private ScheduledExecutorService heartbeat;
    private static final Logger LOG=Logger.getLogger(RegistrationPublisher.class);
    void start(@Observes @jakarta.annotation.Priority(3000) StartupEvent startup) {
        if(!bus.kafkaEnabled()) { announce(); return; }
        heartbeat=Executors.newSingleThreadScheduledExecutor();
        heartbeat.scheduleWithFixedDelay(() -> { try { announce(); } catch(RuntimeException e) { LOG.warn("Service registration failed; retrying",e); } },0,10,TimeUnit.SECONDS);
        heartbeat.scheduleWithFixedDelay(() -> { try { publishMetrics(); } catch(RuntimeException e) { LOG.warn("Metrics publication failed; retrying",e); } },0,1,TimeUnit.SECONDS);
    }
    private void announce() {
        if(!bus.kafkaEnabled()) publishMetrics();
        for(ModuleDescriptor descriptor:descriptors) {
            long expiration=bus.kafkaEnabled()?System.currentTimeMillis()+45000:Long.MAX_VALUE;
            bus.send("web-app",descriptor.serviceId()+":"+instance,new FunctionLink("FunctionLink",descriptor.order(),descriptor.url(),descriptor.title(),descriptor.serviceId(),instance,baseUrl,expiration,descriptor.navigation()));
        }
    }
    private void publishMetrics() { bus.send("performance",application+":"+instance,statistics.counters()); }
    @PreDestroy void stop() { if(heartbeat!=null) heartbeat.shutdownNow(); }
}
