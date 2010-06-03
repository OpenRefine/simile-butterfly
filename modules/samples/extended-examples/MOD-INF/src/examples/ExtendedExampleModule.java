package examples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.velocity.VelocityContext;

import edu.mit.simile.butterfly.ButterflyModuleImpl;

public class ExtendedExampleModule extends ButterflyModuleImpl {
            
    private static final long serialVersionUID = 6618162897508691358L;

    final protected static String html = "text/html";
    final protected static String encoding = "UTF-8";
    
    Map<String,String> _levelNames = new HashMap<String,String>();
    
    @Override
    public void init(ServletConfig config) throws Exception {
        super.init(config);
        _levelNames.put("", "Home");
        _levelNames.put(getMountPoint().getMountPoint().replace("/",""), "Samples");
        _levelNames.put("dhtml", "DHTML");
    }
    
    @Override
    public boolean process(String path, HttpServletRequest request, HttpServletResponse response) throws Exception {

        VelocityContext velocity = new VelocityContext();

        velocity.put("paths", makePath(request.getPathInfo(), _levelNames));
        
        if (path.equals("") || path.endsWith("/")) {
            return send(request, response, velocity, "Some Butterfly Examples", path + "index.vt");
        }

        if ("extended".equals(path)) {
                        
            List<String> items = new ArrayList<String>();
            items.add("1");
            items.add("2");
            items.add("3");
            items.add("4");
            items.add("5");
           
            velocity.put("items", items);

            return send(request,response,velocity,"Extended Nested Templating","nested.vt");
        }
        
        return super.process(path, request, response);
    }
    
    protected boolean send(HttpServletRequest request, HttpServletResponse response, VelocityContext velocity, String title, String template) throws Exception {
        velocity.put("title", title);
        velocity.put("body", template);
        return sendTextFromTemplate(request, response, velocity, "template.vt", encoding, html, false);
    }
    
}
