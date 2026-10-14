package hu.rapids.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Each participating module owns its consumer group, in either deployment mode. */
@ApplicationScoped
public class MessageBus {
    public record Message(String topic,String key,String value,long timestampMillis) {
        public Message(String topic,String key,String value) { this(topic,key,value,System.currentTimeMillis()); }
    }
    @Inject ObjectMapper mapper;
    @Inject Instance<MessageHandler> handlers;
    @Inject Statistics statistics;
    @ConfigProperty(name="blog.kafka.enabled",defaultValue="false") boolean enabled;
    @ConfigProperty(name="blog.kafka.bootstrap-servers",defaultValue="localhost:9092") String servers;
    private final List<KafkaConsumer<String,String>> consumers=new CopyOnWriteArrayList<>();
    private KafkaProducer<String,String> producer;
    private ExecutorService workers;
    private volatile boolean running;
    private static final Logger LOG=Logger.getLogger(MessageBus.class);

    void start(@Observes @jakarta.annotation.Priority(2000) StartupEvent event) {
        if(!enabled) return;
        Properties p=new Properties(); p.put("bootstrap.servers",servers);
        p.put("key.serializer","org.apache.kafka.common.serialization.StringSerializer");
        p.put("value.serializer","org.apache.kafka.common.serialization.StringSerializer");
        p.put("acks","all"); p.put("enable.idempotence","true"); p.put("max.block.ms","10000");
        producer=new KafkaProducer<>(p); running=true;
        workers=Executors.newCachedThreadPool();
        for(MessageHandler handler:handlers) workers.submit(() -> {
            while(running) {
                try { consume(handler); }
                catch(Exception e) { if(running) LOG.error("Consumer initialization failed; retrying",e); }
                if(running) {
                    try { Thread.sleep(1000); }
                    catch(InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            }
        });
    }
    public boolean kafkaEnabled() { return enabled; }
    public void send(String topic,String key,Object value) {
        try {
            String json=value instanceof String s?s:mapper.writeValueAsString(value);
            if(enabled) producer.send(new ProducerRecord<>(topic,key,json)).get(15,TimeUnit.SECONDS);
            else {
                Message message=new Message(topic,key,json);
                for(MessageHandler handler:handlers) if(handler.topics().contains(topic)) { handler.handle(message); count(topic); }
            }
        } catch(RuntimeException e) { throw e; }
        catch(Exception e) { throw new IllegalStateException("Cannot publish to "+topic,e); }
    }
    private void consume(MessageHandler handler) {
        Properties p=new Properties(); p.put("bootstrap.servers",servers); p.put("group.id",handler.group());
        p.put("key.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        p.put("value.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        p.put("enable.auto.commit","false"); p.put("auto.offset.reset","earliest");
        KafkaConsumer<String,String> consumer=new KafkaConsumer<>(p); consumers.add(consumer);
        try {
            consumer.subscribe(handler.topics());
            while(running) {
                ConsumerRecords<String,String> records=consumer.poll(Duration.ofMillis(500));
                for(TopicPartition partition:records.partitions()) {
                    for(ConsumerRecord<String,String> record:records.records(partition)) {
                        try {
                            try {
                                handler.handle(new Message(record.topic(),record.key(),record.value(),record.timestamp()));
                            } catch(jakarta.ws.rs.WebApplicationException | com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
                                reject(handler,record,e);
                            }
                            count(record.topic());
                            consumer.commitSync(Map.of(partition,new OffsetAndMetadata(record.offset()+1)));
                        } catch(Exception e) {
                            LOG.errorf(e,"%s failed at %s offset %d; retrying",handler.group(),partition,record.offset());
                            consumer.seek(partition,record.offset()); Thread.sleep(1000); break;
                        }
                    }
                }
            }
        } catch(WakeupException e) { if(running) LOG.error("Consumer interrupted",e); }
        catch(Exception e) { LOG.errorf(e,"Consumer stopped: %s",handler.group()); }
        finally { consumers.remove(consumer); consumer.close(); }
    }
    private void reject(MessageHandler handler,ConsumerRecord<String,String> record,Exception error) {
        send("error",handler.group(),Map.of("_t","WrongMessage","message",String.valueOf(error.getMessage())));
        try {
            var json=mapper.readTree(record.value()); String user=json.path("loggedIn").path("userId").asText();
            if(!user.isBlank()) send("client-commands",user,Map.of("value","CommandFailed","requestId",json.path("_requestId").asText(),"message",String.valueOf(error.getMessage())));
        } catch(com.fasterxml.jackson.core.JsonProcessingException ignored) { }
    }
    private void count(String topic) {
        if(topic.equals("blog-command") || topic.equals("discussion-command")) statistics.commandProcessed();
        else if(topic.equals("blog-event") || topic.equals("discussion-event")) statistics.eventProcessed();
    }
    @PreDestroy void stop() {
        running=false; consumers.forEach(KafkaConsumer::wakeup);
        if(workers!=null) {
            workers.shutdown(); try { workers.awaitTermination(10,TimeUnit.SECONDS); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if(producer!=null) producer.close(Duration.ofSeconds(5));
    }
}
