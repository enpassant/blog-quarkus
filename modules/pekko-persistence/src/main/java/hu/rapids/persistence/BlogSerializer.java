package hu.rapids.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.rapids.common.Domain.Event;
import org.apache.pekko.serialization.JSerializer;
import java.util.List;

/** Same serializer ID and UTF-8 JSON event format as rapids' blog.BlogSerializer. */
public final class BlogSerializer extends JSerializer {
    private final ObjectMapper mapper=new ObjectMapper();
    @Override public int identifier() { return 0xfecb; }
    @Override public boolean includeManifest() { return false; }
    @Override public byte[] toBinary(Object value) {
        try { return mapper.writeValueAsBytes(value); }
        catch(Exception e) { throw new IllegalArgumentException("Cannot serialize persistence payload",e); }
    }
    @Override public Object fromBinaryJava(byte[] bytes,Class<?> manifest) {
        try {
            ObjectNode json=(ObjectNode)mapper.readTree(bytes);
            String type=json.path("_t").asText();
            if(type.equals("RapidsActorSnapshot")) return mapper.treeToValue(json,ActorSnapshot.class);
            if(type.equals("Blog") || type.equals("Discussion")) return new ActorSnapshot(json,List.of());
            if(!List.of("BlogCreated","BlogModified","DiscussionStarted","CommentAdded","CommentReplied").contains(type))
                throw new IllegalArgumentException("Unknown Rapids persistence event: "+type);
            if(type.equals("CommentAdded") && !json.hasNonNull("path"))
                json.putArray("path").add(json.path("index").asInt());
            return mapper.treeToValue(json,Event.class);
        } catch(Exception e) { throw new IllegalArgumentException("Cannot deserialize Rapids persistence payload",e); }
    }
}
