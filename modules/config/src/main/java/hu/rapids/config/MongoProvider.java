package hu.rapids.config;


import com.mongodb.client.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MongoProvider {
    @ConfigProperty(name="blog.store", defaultValue="mongo") String store;
    @ConfigProperty(name="blog.mongo.uri", defaultValue="mongodb://localhost:27017") String uri;
    @ConfigProperty(name="blog.mongo.database", defaultValue="blog") String database;
    @ConfigProperty(name="blog.mongo.command-database", defaultValue="store") String commandDatabase;
    private MongoClient client;
    @PostConstruct void open() {
        if (store.equals("mongo")) client=MongoClients.create(uri);
        else if (!store.equals("file") && !store.equals("memory")) throw new IllegalArgumentException("Unknown blog.store: "+store);
    }
    public MongoDatabase database() { return client==null?null:client.getDatabase(database); }
    public MongoDatabase commandDatabase() { return client==null?null:client.getDatabase(commandDatabase); }
    public String store() { return store; }
    public String commandUri() {
        int query=uri.indexOf('?');
        String base=query<0?uri:uri.substring(0,query);
        int slash=base.indexOf('/',base.indexOf("://")+3);
        return (slash<0?base:base.substring(0,slash))+"/"+commandDatabase+(query<0?"":uri.substring(query));
    }
    @PreDestroy void close() { if(client!=null) client.close(); }
}
