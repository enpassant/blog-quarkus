package hu.rapids.persistence;

import com.typesafe.config.ConfigFactory;
import hu.rapids.config.MongoProvider;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.pekko.actor.ActorSystem;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class PekkoRuntime {
    @Inject MongoProvider mongo;
    @ConfigProperty(name="blog.data-directory",defaultValue="data") String directory;
    private ActorSystem system;
    public synchronized ActorSystem system() {
        if(system!=null) return system;
        Map<String,Object> config=new HashMap<>();
        config.put("pekko.actor.serializers.blog","hu.rapids.persistence.BlogSerializer");
        config.put("pekko.actor.serialization-bindings.\"hu.rapids.common.PersistencePayload\"","blog");
        config.put("pekko.loggers",java.util.List.of("org.apache.pekko.event.slf4j.Slf4jLogger"));
        config.put("pekko.logging-filter","org.apache.pekko.event.slf4j.Slf4jLoggingFilter");
        if(mongo.store().equals("mongo")) {
            config.put("pekko.persistence.journal.plugin","pekko-contrib-mongodb-persistence-journal");
            config.put("pekko.persistence.snapshot-store.plugin","pekko-contrib-mongodb-persistence-snapshot");
            config.put("pekko.contrib.persistence.mongodb.mongo.mongouri",mongo.commandUri());
        } else if(mongo.store().equals("file")) {
            // IQ80's mmap unmapper accesses JDK internals; use portable file I/O on Java 21.
            System.setProperty("leveldb.mmap","false");
            config.put("pekko.persistence.journal.plugin","pekko.persistence.journal.leveldb");
            config.put("pekko.persistence.journal.leveldb.native",false);
            config.put("pekko.persistence.journal.leveldb.dir",Path.of(directory,"pekko-journal").toAbsolutePath().toString());
            config.put("pekko.persistence.snapshot-store.plugin","pekko.persistence.snapshot-store.local");
            config.put("pekko.persistence.snapshot-store.local.dir",Path.of(directory,"pekko-snapshots").toAbsolutePath().toString());
        } else {
            config.put("pekko.persistence.journal.plugin","pekko.persistence.journal.inmem");
            config.put("pekko.persistence.snapshot-store.plugin","pekko.persistence.snapshot-store.local");
            config.put("pekko.persistence.snapshot-store.local.dir",Path.of(System.getProperty("java.io.tmpdir"),"rapids-snapshots-"+java.util.UUID.randomUUID()).toString());
        }
        ClassLoader loader=Thread.currentThread().getContextClassLoader();
        system=ActorSystem.create("rapids",ConfigFactory.parseMap(config).withFallback(ConfigFactory.load(loader)),loader);
        return system;
    }
    @PreDestroy void stop() {
        if(system!=null) {
            system.terminate();
            try { system.getWhenTerminated().toCompletableFuture().get(15,TimeUnit.SECONDS); }
            catch(Exception e) { org.jboss.logging.Logger.getLogger(PekkoRuntime.class).warn("Pekko shutdown did not complete",e); }
        }
    }
}
