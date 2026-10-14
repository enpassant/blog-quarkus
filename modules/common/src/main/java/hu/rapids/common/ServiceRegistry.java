package hu.rapids.common;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@ApplicationScoped
public class ServiceRegistry implements MessageHandler {
    @Inject ObjectMapper mapper;
    private final String instance=UUID.randomUUID().toString();
    private final Map<String,FunctionLink> services=new ConcurrentHashMap<>();
    // New registry instances rebuild their in-memory table from Kafka's retained log.
    public String group() { return "webapp-registry-"+instance; }
    public List<String> topics() { return List.of("web-app"); }
    public void handle(MessageBus.Message message) throws Exception {
        var json=mapper.readTree(message.value());
        if(!"FunctionLink".equals(json.path("_t").asText())) return;
        FunctionLink link=mapper.treeToValue(json,FunctionLink.class);
        if(link.navigation() && link.title()==null) throw new IllegalArgumentException("Missing registered title");
        if(link.url()==null || !link.url().matches("/[\\p{L}\\p{N}/._-]*") || link.url().startsWith("//") || link.url().contains("..")) throw new IllegalArgumentException("Invalid registered path");
        String key=link.instanceId()==null?message.key()+":"+link.url():link.instanceId()+":"+link.serviceId();
        if(link.expiresAt()>System.currentTimeMillis()) services.put(key,link);
        else services.remove(key);
    }
    public List<FunctionLink> all() {
        long now=System.currentTimeMillis(); services.entrySet().removeIf(e -> e.getValue().expiresAt()<=now);
        return services.values().stream().sorted(Comparator.comparingInt(FunctionLink::order).thenComparing(FunctionLink::url)).toList();
    }
    public List<FunctionLink> links() {
        Map<String,FunctionLink> urls=new LinkedHashMap<>();
        all().stream().filter(FunctionLink::navigation).forEach(link -> urls.put(link.url(),link));
        return List.copyOf(urls.values());
    }
    public String header() {
        return links().stream().map(link -> "<"+link.url()+">; title=\""+link.title().replace("\\","\\\\").replace("\"","\\\"").replace("\r","").replace("\n","")+"\"")
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
