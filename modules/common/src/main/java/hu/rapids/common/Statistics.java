package hu.rapids.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
@ApplicationScoped
public class Statistics {
    @Inject jakarta.enterprise.inject.Instance<BlogReadModel> blogs;
    @Inject jakarta.enterprise.inject.Instance<DiscussionReadModel> discussions;
    private final LongAdder commands=new LongAdder(),events=new LongAdder();
    private final String instance=java.util.UUID.randomUUID().toString();
    private final Map<String,com.fasterxml.jackson.databind.JsonNode> modules=new java.util.concurrent.ConcurrentHashMap<>();
    public void commandProcessed() { commands.increment(); }
    public void eventProcessed() { events.increment(); }
    public Map<String,Object> counters() { return Map.of("commands",commands.sum(),"events",events.sum(),"time",System.currentTimeMillis(),"metricInstance",instance); }
    public void module(String name,com.fasterxml.jackson.databind.JsonNode stats) { modules.put(name,stats); }
    public Map<String,Object> stats() {
        long now=System.currentTimeMillis();
        modules.entrySet().removeIf(entry -> {
            long time=entry.getValue().path("time").asLong(); if(time<1000000000000L) time*=1000;
            return time<now-45000;
        });
        Map<String,com.fasterxml.jackson.databind.JsonNode> current=Map.copyOf(modules);
        long totalCommands=commands.sum(),totalEvents=events.sum();
        for(var metrics:current.values()) {
            if(instance.equals(metrics.path("metricInstance").asText())) continue;
            totalCommands+=metrics.path("commands").asLong(); totalEvents+=metrics.path("events").asLong();
        }
        return Map.of("commands",totalCommands,"events",totalEvents,"blogs",blogs.isUnsatisfied()?0:blogs.get().count(),"discussions",discussions.isUnsatisfied()?0:discussions.get().count(),"modules",current);
    }
}
