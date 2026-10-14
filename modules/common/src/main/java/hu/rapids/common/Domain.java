package hu.rapids.common;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/** Wire fields deliberately retain the original Rapids JSON names. */
public final class Domain {
    private Domain() {}
    public record User(String userId, String userName) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Command(@JsonProperty("_t") String type, String id, String parentId,
                          String blogId, String title, String content, String datetime,
                          @JsonProperty("_requestId") String requestId) {
        public Command(String type,String id,String parentId,String blogId,String title,String content,String datetime) {
            this(type,id,parentId,blogId,title,content,datetime,null);
        }
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Event(@JsonProperty("_t") String type, String id, String commentId,
                        String parentId, String blogId, String userId, String userName,
                        String title, String content, String datetime, List<Integer> path,
                        @JsonProperty("_requestId") String requestId) implements PersistencePayload {
        @JsonProperty("index") public Integer index() {
            return "CommentAdded".equals(type) && path!=null && !path.isEmpty()?path.getFirst():null;
        }
        public Event(String type,String id,String commentId,String parentId,String blogId,String userId,String userName,
                     String title,String content,String datetime,List<Integer> path) {
            this(type,id,commentId,parentId,blogId,userId,userName,title,content,datetime,path,null);
        }
        public Event withRequestId(String requestId) {
            return new Event(type,id,commentId,parentId,blogId,userId,userName,title,content,datetime,path,requestId);
        }
    }
    public record DiscussionItem(String id, String title, String userId, String userName) {
        public DiscussionItem(String id,String title) { this(id,title,null,null); }
    }
    public static class Blog {
        @JsonProperty("_id") public String id;
        public String userId, userName, title, content, htmlContent, datetime;
        public List<DiscussionItem> discussions = new ArrayList<>();
    }
    public static class Discussion {
        @JsonProperty("_id") public String id;
        public String blogId, userId, userName, title, datetime;
        public List<Comment> comments = new ArrayList<>();
    }
    public static class Comment {
        public String commentId, userId, userName, content, htmlContent, datetime;
        public List<Comment> comments = new ArrayList<>();
    }
}
