package hu.rapids.common;
import hu.rapids.config.MongoProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import jakarta.ws.rs.NotFoundException;
import java.nio.file.*;
import java.util.*;
import static com.mongodb.client.model.Filters.eq;

/** Each module owns its collection; file/memory backends use the same documents. */
public final class DocumentRepository<T> {
    private final ObjectMapper mapper;
    private final Class<T> type;
    private final MongoCollection<Document> collection;
    private final Path path;
    private final Map<String,T> documents=new LinkedHashMap<>();
    public DocumentRepository(MongoProvider mongo,ObjectMapper mapper,String name,Class<T> type,String directory) {
        this.mapper=mapper; this.type=type;
        MongoDatabase database=mongo.database();
        collection=database==null?null:database.getCollection(name);
        path=mongo.store().equals("file")?Path.of(directory,name+".json").toAbsolutePath():null;
        if(path!=null && Files.exists(path)) {
            try {
                for(var node:mapper.readTree(Files.readString(path))) documents.put(node.path("_id").asText(),mapper.treeToValue(node,type));
            } catch(Exception e) { throw new IllegalStateException("Cannot load "+name,e); }
        }
    }
    public synchronized T get(String id) {
        if(collection==null) { T result=documents.get(id); if(result==null) throw new NotFoundException("Document not found: "+id); return copy(result); }
        Document doc=collection.find(eq("_id",id)).first();
        if(doc==null) throw new NotFoundException("Document not found: "+id);
        return read(doc);
    }
    public synchronized List<T> all() {
        if(collection==null) return documents.values().stream().map(this::copy).toList();
        List<T> result=new ArrayList<>(); collection.find().forEach(d -> result.add(read(d))); return result;
    }
    public synchronized long count() { return collection==null?documents.size():collection.countDocuments(); }
    public synchronized void save(String id,T entity) {
        try {
            if(collection!=null) collection.replaceOne(eq("_id",id),Document.parse(mapper.writeValueAsString(entity)),new ReplaceOptions().upsert(true));
            else {
                Map<String,T> next=new LinkedHashMap<>(documents); next.put(id,copy(entity));
                if(path!=null) {
                    Files.createDirectories(path.getParent()); Path tmp=Files.createTempFile(path.getParent(),"documents-",".tmp");
                    try {
                        Files.writeString(tmp,mapper.writeValueAsString(next.values()));
                        try { Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                        catch(AtomicMoveNotSupportedException e) { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING); }
                    } finally { Files.deleteIfExists(tmp); }
                }
                documents.clear(); documents.putAll(next);
            }
        } catch(Exception e) { throw new IllegalStateException("Cannot save "+id,e); }
    }
    private T read(Document document) {
        try { return mapper.readValue(document.toJson(),type); }
        catch(Exception e) { throw new IllegalStateException("Invalid document in "+collection.getNamespace(),e); }
    }
    private T copy(T value) { return mapper.convertValue(value,type); }
}
