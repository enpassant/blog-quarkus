package hu.rapids.common;

import jakarta.ws.rs.*;
import java.time.ZonedDateTime;
import java.util.*;
import static hu.rapids.common.Domain.*;
public final class CommandValidation {
    public static Comment find(List<Comment> comments, String id) {
        for(Comment c:comments) { if(c.commentId.equals(id)) return c; Comment found=find(c.comments,id); if(found!=null) return found; }
        return null;
    }
    public static List<Integer> findPath(List<Comment> comments, String id, List<Integer> suffix) {
        for(int i=0;i<comments.size();i++) {
            List<Integer> path=new ArrayList<>(); path.add(i); path.addAll(suffix);
            if(comments.get(i).commentId.equals(id)) return path;
            List<Integer> found=findPath(comments.get(i).comments,id,path); if(found!=null) return found;
        }
        return null;
    }
    public static void identifier(String value) {
        if(value==null || !value.matches("[\\p{L}\\p{N}_.-]{1,200}")) throw new BadRequestException("Invalid id");
    }
    public static void text(String value, String field) {
        if(value==null || value.isBlank() || value.length()>100000) throw new BadRequestException("Invalid " + field);
    }
    public static String date(String value) {
        if(value==null || value.isBlank()) return ZonedDateTime.now().toString();
        try { return ZonedDateTime.parse(value).toString(); }
        catch (RuntimeException e) { throw new BadRequestException("Invalid datetime"); }
    }
}
