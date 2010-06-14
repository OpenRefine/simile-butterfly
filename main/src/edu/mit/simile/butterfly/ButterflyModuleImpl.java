package edu.mit.simile.butterfly;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.commons.collections.OrderedMap;
import org.apache.commons.collections.OrderedMapIterator;
import org.apache.commons.collections.map.ListOrderedMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.metaweb.lessen.Utilities;
import com.metaweb.lessen.tokenizers.CondensingTokenizer;
import com.metaweb.lessen.tokenizers.IndentingTokenizer;
import com.metaweb.lessen.tokenizers.Tokenizer;


/**
 * This class is the base implementation of ButterflyModule and 
 * implements the basic functionality that is made available to Butterfly
 * modules. If you want special functionality that Butterfly does not
 * expose to your modules, it is highly suggested that you extend this
 * class instead of implementing the ButterflyModule interface yourself
 */
public class ButterflyModuleImpl implements ButterflyModule {

    protected static final Logger _logger = LoggerFactory.getLogger("butterfly.module");
    
    protected ClassLoader _classLoader;
    protected Timer _timer;
    protected ServletConfig _config;
    protected File _path;
    protected MountPoint _mountPoint;
    protected ButterflyMounter _mounter;
    protected String _name;
    protected File _tempDir;
    protected ButterflyModule _extended;
    protected Set<ButterflyModule> _extendedBy = new LinkedHashSet<ButterflyModule>();
    protected Set<String> _implementations = new LinkedHashSet<String>();
    protected Map<String,ButterflyModule> _dependencies = new HashMap<String,ButterflyModule>();
    protected ExtendedProperties _properties;
    protected Map<String,ButterflyModule> _modules;
    protected VelocityEngine _templateEngine;
    protected OrderedMap _scripts = new ListOrderedMap();
    protected Set<ButterflyScriptableObject> _scriptables = new LinkedHashSet<ButterflyScriptableObject>();
    protected Set<TimerTask> _packers = new HashSet<TimerTask>();
    
    // ------------------------------------------------------------------------------------------------
    
    public void init(ServletConfig config) throws Exception {
        _config = config;
        
        scriptInit();
    }
    
    public void destroy() throws Exception {
        for (ButterflyScriptableObject scriptable : _scriptables) {
        	scriptable.destroy();
        }
    }
        
    public ServletConfig getServletConfig() {
    	return _config;
    }

    public ServletContext getServletContext() {
    	return _config.getServletContext();
    }
    
    // ------------------------------------------------------------------------------------------------

    public void setClassLoader(ClassLoader classLoader) {
        _logger.trace("{} -(classloader)-> {}", this, classLoader);
        this._classLoader = classLoader;
    }
    
    public void setPath(File path) {
        _logger.trace("{} -(path)-> {}", this, path);
        this._path = path;
    }

    public void setName(String name) {
        _logger.trace("{} -(name)-> {}", this, name);
        this._name = name;
    }

    public void setExtended(ButterflyModule extended) {
        _logger.trace("{} -(extends)-> {}", this, extended);
        this._extended = extended;
    }

    public void addExtendedBy(ButterflyModule extendedBy) {
        _logger.trace("{} -(extended by)-> {}", this, extendedBy);
        this._extendedBy.add(extendedBy);
    }
    
    public void setMountPoint(MountPoint mountPoint) {
        _logger.trace("{} -(mount point)-> {}", this, mountPoint);
        this._mountPoint = mountPoint;
    }

    public void setImplementation(String id) {
        _logger.trace("{} -(implements)-> {}", this, id);
        this._implementations.add(id);
    }
    
    public void setDependency(String name, ButterflyModule module) {
        if (!this._dependencies.containsKey(name)) {
            _logger.trace("{} -({})-> {}", new Object[] { this, name, module} );
            this._dependencies.put(name, module);
        }
    }

    public void setModules(Map<String,ButterflyModule> map) {
        this._modules = map;
    }
    
    @SuppressWarnings("unchecked")
    public void setScript(URL url, Script script) {
        _logger.trace("{} -(script)-> {}", this, url);
        this._scripts.put(url,script);
    }

    public void setScriptable(ButterflyScriptableObject scriptable) {
        _logger.trace("{} -(scriptable)-> {}", this, scriptable.getClassName());
        this._scriptables.add(scriptable);
    }
    
    public void setTemplateEngine(VelocityEngine templateEngine) {
        _logger.trace("{} gets template engine", this);
        this._templateEngine = templateEngine;
    }
        
    public void setProperties(ExtendedProperties properties) {
        _logger.trace("{} gets loaded with properties", this);
        this._properties = properties;
    }

    public void setMounter(ButterflyMounter mounter) {
        _logger.trace("{} gets the module mounter", this);
        this._mounter = mounter;
    }
    
    public void setTemporaryDir(File tempDir) {
        _logger.trace("{} -(tempDir)-> {}", this, tempDir);
        this._tempDir = tempDir;
        try {
            FileUtils.deleteDirectory(tempDir);
            tempDir.mkdirs();
        } catch (Exception e) {
            _logger.error("Error cleaning temporary directory", e);
        }
    }
    
    public void setTimer(Timer timer) {
        _logger.trace("{} gets timer", this);
        this._timer = timer;
    }
    
    // ------------------------------------------------------------------------------------------------
    
    public String getName() {
        return this._name;
    }

    public File getPath() {
        return this._path;
    }

    public ExtendedProperties getProperties() {
        return this._properties;
    }
    
    public MountPoint getMountPoint() {
        return this._mountPoint;
    }

    public ButterflyModule getExtendedModule() {
        return this._extended;
    }

    public Set<ButterflyModule> getExtendingModules() {
        return this._extendedBy;
    }
    
    public Map<String,ButterflyModule> getDependencies() {
    	return this._dependencies;
    }

    public Set<String> getImplementations() {
    	return this._implementations;
    }
    
    public Set<ButterflyScriptableObject> getScriptables() {
    	return this._scriptables;
    }
    
    public VelocityEngine getTemplateEngine() {
    	return this._templateEngine;
    }
    
    public ButterflyModule getModule(String name) {
        _logger.trace("> getModule({}) [{}]", name, this._name);
        ButterflyModule module = _dependencies.get(name);
        if (module == null && _extended != null) {
            module = _extended.getModule(name);
        }
        if (module == null) {
            module = _modules.get(name);
        }
        _logger.trace("< getModule({}) [{}] -> {}", new Object[] { name, this._name,  module });
        return module;
    }

    protected Pattern super_pattern = Pattern.compile("^@@(.*)@@$");
    
    public URL getResource(String resource) {
        _logger.trace("> getResource({}->{},{})", new Object[] { _name, _extended, resource });
        URL u = null;

        if ("".equals(resource)) {
            try {
                u = _path.toURI().toURL();
            } catch (MalformedURLException e) {
                _logger.error("Error", e);
            }
        }
        
        if (u == null && resource.charAt(0) == '@') { // fast screening for potential matchers
            Matcher m = super_pattern.matcher(resource);
            if (m.matches()) {
                resource = m.group(1);
                if (_extended != null) {
                    u = _extended.getResource(resource);
                }
            }
        }
        
        if (u == null) {
            try {
                if (resource.startsWith("file:/")) {
                    u = new URL(resource);
                } else {
                    if (resource.charAt(0) == '/') resource = resource.substring(1);
                    File f = new File(_path, resource);
                    if (f.exists()) {
                        u = f.toURI().toURL();
                    }
                }
            } catch (MalformedURLException e) {
                _logger.error("Error", e);
            }
        }

        if (u == null && _extended != null) {
            u = _extended.getResource(resource);
        }
        
        _logger.trace("< getResource({}->{},{}) -> {}", new Object[] { _name, _extended, resource, u });
        return u;
    }

    public String getRelativePath(HttpServletRequest request) {
        _logger.trace("> getRelativePath()");
        String path = request.getPathInfo();
        String mountPoint = _mountPoint.getMountPoint();
        if (path.startsWith(mountPoint)) {
            path = path.substring(mountPoint.length());
        }
        _logger.trace("< getRelativePath() -> {}", path);
        return path;
    }
    
    public PrintWriter getFilteringWriter(HttpServletRequest request, HttpServletResponse response, boolean absolute) throws IOException {
        _logger.trace("> getFilteringWriter() [{},absolute='{}']", this , absolute);
        LinkRewriter rewriter =  new LinkRewriter(response.getWriter(), this, getContextPath(request, absolute));
        _logger.trace("< getFilteringWriter() [{},absolute='{}']", this , absolute);
        return rewriter;
    }

    public String getContextPath(HttpServletRequest request, boolean absolute) {
        return Butterfly.getTrueContextPath(request, absolute);
    }

    public String getString(HttpServletRequest request) throws IOException {
        BufferedReader reader = request.getReader();
        StringWriter writer = new StringWriter();
        IOUtils.copy(reader, writer);
        writer.close();
        reader.close();
        return writer.toString();
    }
        
    public File getTemporaryDir() {
        return this._tempDir;
    }
    
    // ------------------------------------------------------------------------------------------------
    
    protected Pattern images_pattern = Pattern.compile("^/?.*\\.(jpg|gif|png)$");
    protected Pattern mod_inf_pattern = Pattern.compile("^.*/MOD-INF/.*$");

    protected String encoding = "UTF-8";
    
    /*
     * This class encapsulates the 'context action' that Rhino executes
     * when a request comes.
     */
    class Controller implements ContextAction {

        private String _path;
        private HttpServletRequest _request;
        private HttpServletResponse _response;
        private Scriptable _scope;
        
        public Controller(String path, HttpServletRequest request, HttpServletResponse response) {
            _path = path;
            _request = request;
            _response = response;
        }
        
        public Object run(Context context) {
            Scriptable scope;
            try {
                scope = getScope(context, _request);                
            } catch (Exception e) {
                throw new RuntimeException ("Error retrieving scope", e);
            }

            initScope(context,scope);

            return process(context, scope);
        }
        
        /*
         * tell whether or not the controller processed the request
         * or let it fall thru
         */
        public boolean didRespond() {
            return ((ScriptableButterfly) _scope.get("butterfly", _scope)).didRespond();
        }

        /*
         * process the request by invoking the controller "process()" function 
         */
        private Object process(Context context, Scriptable scope) {
            _scope = scope; // save this for the didRespond() method above;
            Function function = getFunction("process", scope, context);
            Object[] args = new Object[] {
                Context.javaToJS(_path, scope),
                Context.javaToJS(_request, scope),
                Context.javaToJS(_response, scope)
            };
            return function.call(context, scope, scope, args);
        }
        
        /*
         * obtain a javascript function from the given scope
         */
        private Function getFunction(String name, Scriptable scope, Context ctx) {
            Object fun;
            try {
                fun = ctx.compileString(name, null, 1, null).exec(ctx, scope);
            } catch (EcmaError ee) {
                throw new RuntimeException ("Function '" + name + "()' not found.");
            }
            
            if (!(fun instanceof Function)) {
                throw new RuntimeException("'" + name + "' is not a function");
            } else {
                return (Function) fun;
            }
        }
    }
    
    /**
     * This method is called by Butterfly when preProcess returns false and allows
     * modules that want to have a controller in Java instead of Javascript.
     */
    public boolean process(String path, HttpServletRequest request, HttpServletResponse response) throws Exception {

        if (processScript(path, request, response)) {
            return true;
        }
        
        Matcher m = null;

        if (path.equals("") || path.endsWith("/")) {
            return sendText(request, response, path + "index.html", encoding, "text/html",false);
        }
        
        if (path.endsWith(".js")) {
            return sendText(request, response, path, encoding, "text/javascript",false);
        }

        m = images_pattern.matcher(path);
        if (m.matches()) {
            return sendBinary(request, response, path, "image/" + m.group(1));
        }

        if (path.endsWith(".css")) {
            return sendText(request, response, path, encoding, "text/css",false);
        }

        if (path.endsWith(".less")) {
            return sendLessen(request, response, path, encoding, "text/css",false);
        }

        if (path.endsWith(".html")) {
            return sendText(request, response, path, encoding, "text/html",false);
        }

        if (path.endsWith(".xml")) {
            return sendText(request, response, path, encoding, "application/xml",false);
        }
                
        m = mod_inf_pattern.matcher(path);
        if (m.matches()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return true;
        }
        
        return false;
    }
    
    // ------------------------------------------------------------------------------------------------
    
    public boolean redirect(HttpServletRequest request, HttpServletResponse response, String location) throws Exception {
    	_logger.trace("> redirect: {}", location);
        String redirectURL = location;
        if (!location.startsWith("http://") && !location.startsWith("https://")) {
        	String contextPath = getContextPath(request,true);
	        String servletPath = request.getServletPath();
            if ("".equals(location)) {
                location = "/";
            }
	        if (servletPath != null) {
	        	if (servletPath.startsWith("/")) servletPath = servletPath.substring(1);
		        redirectURL = contextPath + servletPath + location;
	        } else {
		        redirectURL = contextPath + location;
	        }
        }
        _logger.info("redirecting to: {}", redirectURL);        
    	response.sendRedirect(redirectURL);
        _logger.trace("< redirect: {}", location);
        return true;
    }
        
    public boolean sendBinary(HttpServletRequest request, HttpServletResponse response, String file, String mimeType) throws Exception {
        return send(request, response, getResource(file), false, null, mimeType, null, null, false);
    }
    
    public boolean sendBinary(HttpServletRequest request, HttpServletResponse response, URL resource, String mimeType) throws Exception {
        return send(request, response, resource, false, null, mimeType, null, null, false);
    }

    public boolean sendText(HttpServletRequest request, HttpServletResponse response, String file, String encoding, String mimeType, boolean absolute) throws Exception {
        return send(request, response, getResource(file), true, encoding, mimeType, null, null, absolute);
    }
    
    public boolean sendText(HttpServletRequest request, HttpServletResponse response, URL resource, String encoding, String mimeType, boolean absolute) throws Exception {
        return send(request, response, resource, true, encoding, mimeType, null, null, absolute);
    }
    
    public boolean sendWrappedText(HttpServletRequest request, HttpServletResponse response, URL resource, String encoding, String mimeType, String prologue, String epilogue, boolean absolute) throws Exception {
        return send(request, response, resource, true, encoding, mimeType, prologue, epilogue, absolute);
    }
        
    public boolean sendTextFromTemplate(HttpServletRequest request, HttpServletResponse response, VelocityContext velocity, String template, String encoding, String mimeType, boolean absolute) throws Exception {
        _logger.trace("> template {} [{}|{}]", new String[] { template, encoding, mimeType });
        try {
            response.setContentType(mimeType);
            _templateEngine.mergeTemplate(template, encoding, velocity, getFilteringWriter(request, response, absolute));
        } catch (ResourceNotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        _logger.trace("< template {} [{}|{}]", new String[] { template, encoding, mimeType });
        return true; 
    }
    
    public boolean sendLessen(HttpServletRequest request, HttpServletResponse response, String path, String encoding, String mimeType, boolean absolute) throws Exception {
        URL url = getResource(path);
        
        Map<String, String> variables = new HashMap<String, String>();
        variables.put("module", _name);
        
        Tokenizer tokenizer = Utilities.openLess(url, variables);
        tokenizer = new CondensingTokenizer(tokenizer, false);
        tokenizer = new IndentingTokenizer(tokenizer);
        
        return sendLessenTokenStream(request, response, tokenizer, encoding, "text/css",false);
    }
    
    public boolean sendLessenTokenStream(HttpServletRequest request, HttpServletResponse response, Tokenizer tokenizer, String encoding, String mimeType, boolean absolute) throws Exception {
        try {
            response.setContentType(mimeType);
            Utilities.write(tokenizer, getFilteringWriter(request, response, absolute));
        } catch (ResourceNotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        return true; 
    }

    public boolean sendString(HttpServletRequest request, HttpServletResponse response, String str, String encoding, String mimeType) throws Exception {
        _logger.trace("> string: '{}'", str);
        response.setContentType(mimeType);
        response.setCharacterEncoding(encoding);
        PrintWriter writer = response.getWriter();
        writer.write(str);
        writer.close();
        _logger.trace("< string: '{}'", str);
        return true; 
    }

    public boolean sendError(HttpServletRequest request, HttpServletResponse response, int code, String str) throws Exception {
        _logger.trace("> error: '{}' '{}'", code, str);
        response.sendError(code, str);
        _logger.trace("< error: '{}' '{}'", code, str);
        return true; 
    }
    
    // ------------------------------------------------------------------------------------------------
    
    public static class Level {
        String name;
        String href;

        public Level(String name, String href) {
            this.name = name;
            this.href = href;
        }
        
        public String getName() {
            return name;
        }
        
        public String getHref() {
            return href;
        }
    }
    
    public List<Level> makePath(String path, Map<String,String> descs) {
        LinkedList<Level> ls = new LinkedList<Level>();
        String[] paths = path.split("/");
        if (paths.length > 1) {
            String relativePath = (path.endsWith("/")) ? "../" : "./";
            for (int i = paths.length - 2; i >= 0; i--) {
                String p = paths[i];
                String desc = descs.get(paths[i]);
                if (desc == null) desc = p;
                Level l = new Level(desc,relativePath);
                relativePath = "../" + relativePath;
                ls.addFirst(l);
            }
        }
        return ls;
    }

    public String toString() {
        return _name + " [" + this.getClass().getName() + "]";
    }

    public void initScope(Context context, Scriptable scope) {
        for (ButterflyModule m : _dependencies.values()) {
            m.initScope(context, scope);
        }
        
        OrderedMapIterator i = _scripts.orderedMapIterator();
        while (i.hasNext()) {
            URL url = (URL) i.next();
            _logger.debug("Executing script: {}", url);
            Script s = (Script) _scripts.get(url);
            s.exec(context, scope);
        }
    }
    
    // ------------------------------------------------------------------------------------------------

    protected void scriptInit() throws Exception {
        Context context = ContextFactory.getGlobal().enterContext();
        Scriptable scope = new ButterflyScope(this, context);
        
        initScope(context,scope);
        
        String functionName = "init";
        try {
            Object fun = context.compileString(functionName, null, 1, null).exec(context, scope);
            if (fun != null && fun instanceof Function) {
                try {
                    ((Function) fun).call(context, scope, scope, new Object[] {});
                } catch (EcmaError ee) {
                    _logger.error("Error initializing module " + getName() + " by script function init()", ee);
                }
            }
        } catch (EcmaError ee) {
            // ignore
        }
    }
    
    protected boolean processScript(String path, HttpServletRequest request, HttpServletResponse response) throws Exception {
        boolean result = false;
        if (_scripts.size() > 0) {
            Controller controller = new Controller(path, request, response);
            ContextFactory.getGlobal().call(controller);
            result = controller.didRespond();
        }
        if (!result && _extended != null && _extended instanceof ButterflyModuleImpl) {
            result = ((ButterflyModuleImpl) _extended).processScript(path, request, response);
        }
        return result;
    }

    protected ButterflyScope getScope(Context context, HttpServletRequest request) throws Exception {
        return new ButterflyScope(this, context);
    }
    
    protected boolean send(HttpServletRequest request, HttpServletResponse response, URL resource, boolean filtering, String encoding, String mimeType, String prologue, String epilogue, boolean absolute) throws Exception {
        _logger.trace("> send {}", resource);

        if (resource != null) {
            URLConnection urlConnection = resource.openConnection();
            
            // NOTE(SM): I've disabled the HTTP caching-related headers for now 
            //           we should introduce this back in the future once we start
            //           fine tuning the system but for now since it's hard to 
            //           understand when ajax calls are cached or not it's better
            //           to develop without worrying about the cache
            
//            long lastModified = urlConnection.getLastModified();
//
//            long ifModifiedSince = request.getDateHeader("If-Modified-Since");
//            if (lastModified == 0 || ifModifiedSince / 1000 < lastModified / 1000) {
//                response.setDateHeader("Last-Modified", lastModified);
              
                if (encoding == null) {
                    InputStream input = null;
                    OutputStream output = null;
                    try {
                        input = new BufferedInputStream(urlConnection.getInputStream()); 
                        response.setHeader("Content-Type", mimeType);
                        output = response.getOutputStream();
                        IOUtils.copy(input, output);
                    } catch (Exception e) {
                        _logger.error("Error processing " + resource, e);
                    } finally {
                        if (input != null) input.close();
                        if (output != null) output.close();
                    }
                } else {
                    Reader input = null;
                    Writer output = null;
                    try {
                        input = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), encoding));
                        response.setHeader("Content-Type", mimeType + ";charset=" + encoding);
                        response.setCharacterEncoding(encoding);
                        output = (filtering) ? getFilteringWriter(request, response, absolute) : response.getWriter();
                        
                        if (prologue != null) {
                            output.write(prologue);
                        }
                        
                        IOUtils.copy(input, output);
                        
                        if (epilogue != null) {
                            output.write(epilogue);
                        }
                    } catch (Exception e) {
                        _logger.error("Error processing " + resource, e);
                    } finally {
                        if (input != null) input.close();
                        if (output != null) output.close();
                    }
                }
//            } else {
//                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
//            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Couldn't find the specified resource");
        }

        _logger.trace("< send {}", resource);
        return true;
    }
}
