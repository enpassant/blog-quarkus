package hu.rapids.common.web;
import hu.rapids.common.Domain;
import hu.rapids.common.Markdown;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import static hu.rapids.common.Domain.*;

/** HTML is escaped here; only sanitized Markdown is inserted as markup. */
@ApplicationScoped
public class Pages {
    @jakarta.inject.Inject hu.rapids.common.ServiceRegistry registry;
    public static String escape(String s) {
        return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    public String page(String title,String body) {
        return "<!doctype html><html lang=\"hu\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"+escape(title)+"</title><link rel=\"stylesheet\" href=\"/css/app.css?v=services-1\"><script src=\"/js/app.js?v=services-1\" defer></script></head><body><header><a href=\"/blog\">Rapids Blog</a><nav id=\"navigation\">"+navigation()+"</nav><button id=\"login\" type=\"button\">Belépés</button></header><main>"+body+"<p id=\"message\" role=\"status\"></p><noscript>A belépéshez és a mentéshez engedélyezd a JavaScriptet.</noscript></main>"+loginDialog()+"</body></html>";
    }
    private String navigation() {
        StringBuilder html=new StringBuilder();
        registry.links().forEach(link -> html.append("<a href=\"").append(escape(link.url())).append("\">").append(escape(link.title())).append("</a>"));
        return html.toString();
    }
    private String loginDialog() {
        return "<dialog id=\"login-dialog\" aria-labelledby=\"login-title\"><form id=\"login-form\"><h2 id=\"login-title\">Belépés</h2><label>Felhasználónév<input name=\"username\" autocomplete=\"username\" required></label><label>Jelszó<input name=\"password\" type=\"password\" autocomplete=\"current-password\" required></label><p id=\"login-error\" role=\"alert\"></p><div class=\"actions\"><button type=\"submit\">Belépés</button><button id=\"login-cancel\" type=\"button\">Mégse</button></div></form></dialog>";
    }
    public String blogs(String title,List<Blog> blogs) {
        StringBuilder out=new StringBuilder("<h1>"+escape(title)+"</h1><p><a href=\"/blog/new\">Új bejegyzés</a></p>");
        if(blogs.isEmpty()) out.append("<p>Még nincs bejegyzés.</p>");
        blogs.forEach(b -> out.append("<article><h2><a href=\"/blog/").append(escape(b.id)).append("\">").append(escape(b.title)).append("</a></h2><p>").append(escape(b.userName)).append(" · ").append(escape(b.datetime)).append("</p></article>"));
        return page(title,out.toString());
    }
    public String blog(Blog b) {
        StringBuilder out=new StringBuilder("<article><h1>"+escape(b.title)+"</h1><p>"+escape(b.userName)+" · "+escape(b.datetime)+"</p><div>"+b.htmlContent+"</div><a href=\"/blog/"+escape(b.id)+"/edit\">Szerkesztés</a></article><h2>Beszélgetések</h2>");
        b.discussions.forEach(d -> out.append("<p><a href=\"/discussion/").append(escape(d.id())).append("\">").append(escape(d.title())).append("</a></p>"));
        return page(b.title,out.toString());
    }
    public String blogForm(String id,Blog existing) {
        return page(existing==null?"Új bejegyzés":"Szerkesztés","<h1>"+(existing==null?"Új bejegyzés":"Szerkesztés")+"</h1><form data-topic=\"blog\" data-id=\""+escape(id)+"\" data-type=\""+(existing==null?"CreateBlog":"ModifyBlog")+"\" data-next=\"/blog/"+escape(id)+"\"><label>Cím<input name=\"title\" required value=\""+escape(existing==null?"":existing.title)+"\"></label><label>Tartalom (Markdown)<textarea name=\"content\" required>"+escape(existing==null?"":existing.content)+"</textarea></label><button>Mentés</button></form>");
    }
    public String discussion(Discussion d) {
        return page(d.title,"<h1>"+escape(d.title)+"</h1><p><a href=\"/blog/"+escape(d.blogId)+"\">Vissza a bejegyzéshez</a> · <a href=\"/discussion/"+escape(d.id)+"/new\">Hozzászólás</a></p>"+comments(d.id,d.comments));
    }
    private String comments(String id,List<Comment> comments) {
        StringBuilder out=new StringBuilder("<ol class=\"comments\">");
        comments.forEach(c -> out.append("<li><p>").append(escape(c.userName)).append(" · ").append(escape(c.datetime)).append("</p><div>").append(c.htmlContent).append("</div><a href=\"/discussion/").append(escape(id)).append("/comment/").append(escape(c.commentId)).append("/new\">Válasz</a>").append(comments(id,c.comments)).append("</li>"));
        return out.append("</ol>").toString();
    }
    public String commentForm(String id,String parent,String uuid) {
        return page("Hozzászólás","<h1>"+(parent==null?"Új hozzászólás":"Válasz")+"</h1><form data-topic=\"discussion\" data-id=\""+escape(id)+"\" data-type=\""+(parent==null?"AddComment":"ReplyComment")+"\" data-next=\"/discussion/"+escape(id)+"\"><input type=\"hidden\" name=\"id\" value=\""+escape(uuid)+"\">"+(parent==null?"":"<input type=\"hidden\" name=\"parentId\" value=\""+escape(parent)+"\">")+"<label>Hozzászólás (Markdown)<textarea name=\"content\" required></textarea></label><button>Mentés</button></form>");
    }
}
