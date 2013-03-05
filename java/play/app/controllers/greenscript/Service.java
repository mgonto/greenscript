package controllers.greenscript;

import java.util.Date;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import play.libs.Time;
import play.modules.greenscript.GreenScriptPlugin;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.Header;
import play.mvc.Scope.Flash;
import play.utils.Utils;

import com.ning.http.util.DateUtil;

public class Service extends Controller {
    
    public static void getInMemoryCache(String key) {
        String content = GreenScriptPlugin.getInstance().getInMemoryFileContent(key, params.get(GreenScriptPlugin.RESOURCES_PARAM));
        notFoundIfNull(content);
        final long l = System.currentTimeMillis();
        String md5Hex = DigestUtils.md5Hex(content);
        
        final String etag = "\"" + md5Hex + "\"";
        cache(etag, "100d");
        Flash.current().keep();
        
        Header etagMatch = request.headers.get("if-none-match");
        if (etagMatch != null) {
            for (String etagValue : etagMatch.values) {
                if (etag.equals(etagValue)) {
                    response.status = Http.StatusCode.NOT_MODIFIED;
                    return;
                }
            }
        }
        
        if (key.endsWith(".js")) {
            response.setContentTypeIfNotSet("text/javascript");
        } else if (key.endsWith(".css")) {
            response.setContentTypeIfNotSet("text/css");
        }
        
        renderText(content);
    }

    private static void cache(final String etag, final String duration) {
        int maxAge = Time.parseDuration(duration);
        response.setHeader("Cache-Control", "max-age=" + maxAge);
        response.setHeader("Etag", etag);
    }

}
