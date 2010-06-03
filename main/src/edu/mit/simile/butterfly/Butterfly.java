package edu.mit.simile.butterfly;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.log4j.PropertyConfigurator;
import org.apache.velocity.app.VelocityEngine;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.mit.simile.butterfly.velocity.ButterflyResourceLoader;
import edu.mit.simile.butterfly.velocity.Super;

/**
 * This is the Butterfly servlet and the main entry point
 * for a Butterfly-powered web application. This servlet is
 * responsible for loading, configuring and wire together 
 * the various modules that compose your webapp and then
 * manages the dispatching of requests to the modules that
 * are supposed to handle them.
 */
public class Butterfly extends HttpServlet implements Runnable {

    public static final String HOST_HEADER = "X-Forwarded-Host";
    public static final String CONTEXT_HEADER = "X-Context-Path";
    
    private static final long serialVersionUID = 1938797827088619577L;

    private static final long developmentWatcherDelay = 1000;
    private static final long productionWatcherDelay = 3000;
    
    public static final String NAME = "butterfly.name";
    public static final String DEVELOPMENT = "butterfly.development";
    public static final String HOME = "butterfly.home";
    public static final String ZONE = "butterfly.zone";
    public static final String BASE_URL = "butterfly.url";
    public static final String DEFAULT_ZONE = "butterfly.default.zone";

    public static final String MAIN_ZONE = "main";
    
    public static final List<String> CONTROLLER;
    
    private static final ContextFactory contextFactory = new ButterflyContextFactory();
    
    static {
        ContextFactory.initGlobal(contextFactory);
        
        CONTROLLER = new ArrayList<String>();
        CONTROLLER.add("controller.js");
    }
    
    static class ButterflyContextFactory extends ContextFactory {
        protected void onContextCreated(Context cx) {
            cx.setOptimizationLevel(9);
            super.onContextCreated(cx);
        }
    }    
    
    // --------------------- static ----------------------------------
    
    public static String getTrueHost(HttpServletRequest request) {
        String host = request.getHeader(HOST_HEADER);
        if (host != null) {
            String[] hosts = host.split(",");
            host = hosts[hosts.length - 1];
        }
        return host;
    }

    public static String getTrueContextPath(HttpServletRequest request, boolean absolute) {
        String context = request.getHeader(CONTEXT_HEADER);
        if (context != null) {
            if (context.charAt(context.length() - 1) == '/') context = context.substring(0, context.length() - 1);
        } else {
            context = request.getContextPath();
        }
        if (absolute) {
            return getFullHost(request) + context;
        } else {
            return context;
        }
    }

    public static String getTrueRequestURI(HttpServletRequest request, boolean absolute) {
        return getTrueContextPath(request,absolute) + request.getPathInfo();
    }
    
    public static String getFullHost(HttpServletRequest request) {
        StringBuffer prefix = new StringBuffer();
        String protocol = request.getScheme();
        prefix.append(protocol);
        prefix.append("://");
        String proxy = getTrueHost(request);
        if (proxy != null) {
            prefix.append(proxy);
        } else {
            prefix.append(request.getServerName());
            int port = request.getServerPort();
            if (!((protocol.equals("http") && port == 80) || (protocol.equals("https") && port == 443))) {
                prefix.append(':');
                prefix.append(port);
            }
        }
        return prefix.toString();
    }    

    // ---------------------------------------------------------------

    transient private Logger _logger;

    private boolean _development;
    private String _name;
    private int _routingCookieMaxAge;
    
    transient protected Timer _timer;
    transient protected ButterflyClassLoader _classLoader;
    transient protected ButterflyScriptWatcher _scriptWatcher;
    transient protected ServletContext _context;
    transient protected ButterflyMounter _mounter;

    protected ExtendedProperties _properties;
    protected File _contextDir;
    protected File _homeDir;
    protected File _webInfDir;
    protected File _tempDir;
    protected Exception _configurationException;

    protected boolean _configured = false;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        
        _name = System.getProperty(NAME, "butterfly");

        _context = config.getServletContext();
        _context.setAttribute(NAME, _name);
        
        _tempDir = (File) _context.getAttribute("javax.servlet.context.tempdir");
        
        _contextDir = new File(_context.getRealPath("/"));
        _webInfDir = new File(_contextDir, "WEB-INF");
        _timer = new Timer(true);
        _properties = new ExtendedProperties();

        _mounter = new ButterflyMounter();
        
        // Load the butterfly properties
        String props = System.getProperty("butterfly.properties");
        File butterflyProperties = (props == null) ? new File(_webInfDir, "butterfly.properties") : new File(props);

        BufferedInputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(butterflyProperties)); 
            _properties.load(is);
        } catch (FileNotFoundException e) {
            throw new ServletException("Could not find butterfly properties file",e);
        } catch (IOException e) {
            throw new ServletException("Could not read butterfly properties file",e);
        } finally {
            try {
                is.close();
            } catch (Exception e) {
                // ignore
            }
        }

        // Overload with properties set from the command line 
        // using the -Dkey=value parameters to the JVM
        Properties systemProperties = System.getProperties();
        for (Iterator<Object> i = systemProperties.keySet().iterator(); i.hasNext(); ) {
            String key = (String) i.next();
            String value = systemProperties.getProperty(key);
            _properties.setProperty(key, value);
        }

        _development = _properties.getBoolean(DEVELOPMENT, false);
        _scriptWatcher = new ButterflyScriptWatcher();
        
        String log4j = System.getProperty("butterfly.log4j");
        File logProperties = (log4j == null) ? new File(_webInfDir, "log4j.properties") : new File(log4j);
        
        if (logProperties.exists()) {
            _logger = LoggerFactory.getLogger(_name);
            if (_development) {
                PropertyConfigurator.configureAndWatch(logProperties.getAbsolutePath(), developmentWatcherDelay);
            } else {
                PropertyConfigurator.configureAndWatch(logProperties.getAbsolutePath(), productionWatcherDelay);
            }
        }

        _logger.info("Starting {} ...", _name);

        _logger.info("Properties loaded from {}", butterflyProperties);
        
        _logger.debug("> init");

        _logger.debug("> initialize classloader");
        try {
            _classLoader = AccessController.doPrivileged (
                new PrivilegedAction<ButterflyClassLoader>() {
                    public ButterflyClassLoader run() {
                        return new ButterflyClassLoader(Thread.currentThread().getContextClassLoader());
                    }
                }
             );
            TimerTask classloaderWatcher = _classLoader.getClassLoaderWatcher(new Trigger(_contextDir));
            _timer.schedule(classloaderWatcher, developmentWatcherDelay, developmentWatcherDelay);
            Thread.currentThread().setContextClassLoader(_classLoader);
            _classLoader.watch(butterflyProperties); // reload if the butterfly properties change
            contextFactory.initApplicationClassLoader(_classLoader); // tell rhino to use this classloader as well
        } catch (Exception e) {
            throw new ServletException("Failed to load butterfly classloader", e);
        }
        _logger.debug("< initialize classloader");

        if (_development) {
            _logger.debug("> initialize script watcher");
            _timer.schedule(_scriptWatcher, developmentWatcherDelay, developmentWatcherDelay);
            _logger.debug("< initialize script watcher");
        }

        try {
            _logger.debug("> spawn configuration thread");
            Thread configurationThread = new Thread(this);
            configurationThread.start();
            _logger.debug("< spawn configuration thread");
        } catch (Exception e) {
            // for Google App Engine or other strict servlet containers that don't allow spawning threads
            _logger.debug("> inline configuration");
            this.run();
            _logger.debug("< inline configuration");
        }
        
        _logger.debug("< init");
    }
    
    @Override
    public void destroy() {
        _logger.info("Stopping Butterfly...");
        for (ButterflyModule m : _modulesByName.values()) {
            try {
                _logger.debug("> destroying {}", m);
                m.destroy();
                _logger.debug("< destroying {}", m);
            } catch (Exception e) {
                _logger.error("Exception caught while destroying '" + m + "'", e);
            }
        }
        _timer.cancel();
        _logger.info("done.");
    }
    
    /*
     * We do configuration asynchronously so that we can start responding 
     * to web requests right away. 
     */
    @SuppressWarnings("unchecked")
    public void run() {
        _logger.debug("> configure");

        _logger.debug("> process properties");
        try {

            String homePath = _properties.getString(HOME);
            if (homePath == null) {
                _homeDir = _contextDir;
            } else {
                _homeDir = new File(homePath);
            }
            _logger.info("Butterfly home: {}", _homeDir);
            
            Iterator<String> i = _properties.getKeys(ZONE);
            while (i.hasNext()) {
                String zone = i.next();
                String path = _properties.getString(zone);
                zone = zone.substring(ZONE.length() + 1);
                _logger.info("Zone path: [{}] -> {}", zone, path);
                _mounter.registerZone(zone, path);
            }

            String defaultZone = _properties.getString(DEFAULT_ZONE);
            if (defaultZone != null) {
                _logger.info("Default zone is: '{}'", defaultZone);
                _mounter.setDefaultZone(defaultZone);
            } else {
                String baseURL = _properties.getString(BASE_URL,"/");
                _mounter.registerZone(MAIN_ZONE, baseURL);
                _mounter.setDefaultZone(MAIN_ZONE);
            }
            
            String language = _properties.getString("butterfly.locale.language");
            String country =  _properties.getString("butterfly.locale.country");
            String variant =  _properties.getString("butterfly.locale.variant");
            if (language != null) {
                if (country != null) {
                    if (variant != null) {
                        Locale.setDefault(new Locale(language, country, variant));
                    } else {
                        Locale.setDefault(new Locale(language, country));
                    }
                } else {
                    Locale.setDefault(new Locale(language));
                }
            }

            String timeZone = _properties.getString("butterfly.timeZone");
            if (timeZone != null) {
                TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
            }
            
            _routingCookieMaxAge = _properties.getInt("butterfly.routing.cookie.maxage",-1);
        } catch (Exception e) {
            _configurationException = new Exception("Failed to load butterfly properties", e);
        }
        _logger.debug("< process properties");
                                
        _logger.debug("> load modules");
        List<String> paths = _properties.getList("butterfly.modules.path");
        for (String path : paths) {
            findModulesIn(absolutize(_homeDir, path));
        }
        _logger.debug("< load modules");
        
        _logger.debug("> create modules");
        for (String name : _moduleProperties.keySet()) {
            createModule(name);
        }
        _logger.debug("< create modules");
        
        _logger.debug("> load module wirings");
        ExtendedProperties wirings = new ExtendedProperties();
        try {
            // Load the wiring properties
            File moduleWirings = absolutize(_homeDir, _properties.getString("butterfly.modules.wirings","WEB-INF/modules.properties"));
            _logger.info("Loaded module wirings from: {}", moduleWirings);
            _classLoader.watch(moduleWirings); // reload if the module wirings change
            FileInputStream fis = new FileInputStream(moduleWirings);
            wirings.load(fis);
            fis.close();
        } catch (Exception e) {
            _configurationException = new Exception("Failed to load module wirings", e);
        }
        _logger.debug("< load module wirings");
        
        _logger.debug("> wire modules");
        try {
            wireModules(wirings);
        } catch (Exception e) {
            _configurationException = new Exception("Failed to wire modules", e);
        }
        _logger.debug("< wire modules");

        _logger.debug("> configure modules");
        try {
            configureModules();
        } catch (Exception e) {
            _configurationException = new Exception("Failed to configure modules", e);
        }
        _logger.debug("< configure modules");
        
        _logger.debug("> initialize modules");
        for (ButterflyModule m : _modulesByName.values()) {
            try {
                _logger.debug("> initialize " + m.getName());
                m.init(getServletConfig());
                _logger.debug("< initialize " + m.getName());
            } catch (Exception e) {
                _configurationException = new Exception("Failed to initialize module " + m, e);
            }
        }
        _logger.debug("< initialize modules");
        
        _configured = true;
        
        _logger.debug("< configure");
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getPathInfo();
        String urlQuery = request.getQueryString();

        Zone zone = _mounter.getZone(request);
        
        if (_logger.isDebugEnabled()) {
            _logger.debug("> " + method + " [" + zone.getName() + "] " + path + ((urlQuery != null) ? "?" + urlQuery : ""));
            Enumeration<String> en = request.getHeaderNames();
            while (en.hasMoreElements()) {
                String header = en.nextElement();
                _logger.trace("{}: {}", header, request.getHeader(header));
            }
        } else if (_logger.isInfoEnabled()) {
            _logger.info("{} {} [{}]", new String[] { method,path,zone.getName() });
        }

        setRoutingCookie(request, response);
        
        try {
            if (_configured) {
                if (_configurationException == null) {
                    ButterflyModule module = _mounter.getModule(path,zone);
                    _logger.debug("Module '{}' will handle the request", module.getName());
                    String localPath = module.getRelativePath(request);
                    if (!module.process(localPath, request, response)) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    }
                } else {
                    error(response, "Butterfly Error", "Butterfly incurred in the following errors while initializing:", _configurationException);
                }
            } else {
                delay(response, "Butterfly is still initializing...");
            }
        } catch (FileNotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (Exception e) {
            error(response, "Butterfly Error", "Butterfly caught the following error while processing the request:", e);
        }

        response.flushBuffer();

        if (_logger.isDebugEnabled()) _logger.debug("< " + method + " [" + zone.getName() + "] " + path + ((urlQuery != null) ? "?" + urlQuery : ""));
    }
    
    // ---------------------------- private -----------------------------------
    
    final static private String dependencyPrefix = "requires";
    final static private String implementsProperty = "implements";
    final static private String extendsProperty = "extends";
    
    protected Map<String,ButterflyModule> _modulesByName = new HashMap<String,ButterflyModule>();
    protected Map<String,Map<String,ButterflyModule>> _modulesByInterface = new HashMap<String,Map<String,ButterflyModule>>();
    protected Map<String,ExtendedProperties> _moduleProperties = new HashMap<String,ExtendedProperties>();
    protected Map<String,Boolean> _created = new HashMap<String,Boolean>();

    final static private String routingCookie = "host";
    
    /*
     * This method adds a cookie to the response that will be used by mod_proxy_balancer
     * to know what server is supposed to be handling all the requests of this user agent. 
     */
    protected void setRoutingCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (routingCookie.equals(cookie.getName())) {
                    return;
                }
            }
        }
        
        Cookie cookie = new Cookie(routingCookie, "." + _name); // IMPORTANT: the initial dot is required by mod_proxy_balancer!
        cookie.setMaxAge(_routingCookieMaxAge); // delete at end of browser session
        cookie.setPath("/");
        response.addCookie(cookie);
    }
    
    protected File absolutize(File base, String location) {
        if (location == null || location.length() == 0) { // we got an empty location
            return base;
        } else if (location.indexOf(':') > 0) { // we got an absolute windows location (ie c:\blah)
            return new File(location);
        } else if (location.charAt(0) == '/' || location.charAt(0) == '\\') { // we got an absolute location
            return new File(location);
        } else { // we got a relative location
            return new File(base, location);
        }
    }

    protected static final String PATH_PROP = "__path__";
    
    protected void findModulesIn(File f) {
        File modFile = new File(f,"MOD-INF");
        if (modFile.exists()) {
            _logger.trace("> findModulesIn({})", f);
            try {
                String name = f.getName();

                ExtendedProperties p  = new ExtendedProperties();
                File propFile = new File(modFile,"module.properties");
                if (propFile.exists()) {
                    _classLoader.watch(propFile); // reload if the the module properties change
                    BufferedInputStream stream = new BufferedInputStream(new FileInputStream(propFile));
                    p.load(stream);
                    stream.close();
                }

                p.addProperty(PATH_PROP, f.getAbsolutePath());
                
                _moduleProperties.put(name, p);
            } catch (Exception e) {
                _logger.error("Error finding module wirings", e);
            }
            _logger.trace("< findModulesIn({})", f);
        } else {
            File[] files = f.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File file = files[i];
                    if (file.isDirectory()) {
                        findModulesIn(file);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected ButterflyModule createModule(String name) {
        _logger.trace("> Creating module: {}", name);

        if (_modulesByName.containsKey(name)) {
            _logger.trace("< Module '{}' already exists", name);
            return _modulesByName.get(name);
        }

        ExtendedProperties p = _moduleProperties.get(name);
        File path = new File(p.getString(PATH_PROP));
        _logger.debug("Module path: {}", path);
            
        File tempDir = new File(_tempDir,name);
        if (!tempDir.exists()) { 
            tempDir.mkdirs();
        }

        File classes = new File(path,"MOD-INF/classes");
        if (classes.exists()) {
            _classLoader.addRepository(classes);
        }
        
        File libs = new File(path,"MOD-INF/lib");
        if (libs.exists()) {
            _classLoader.addRepository(libs);
        }

        ButterflyModule m = new ButterflyModuleImpl();
        
        // process module's controller
        String manager = p.getString("module-impl");
        if (manager != null && !manager.equals(m.getClass().getName())) {
            try {
                Class c = _classLoader.loadClass(manager);
                m = (ButterflyModule) c.newInstance();
            } catch (Exception e) {
                _logger.error("Error loading special module manager", e);
            }
        }
        
        m.setName(name);
        m.setPath(path);
        m.setModules(_modulesByName);
        m.setMounter(_mounter);
        m.setClassLoader(_classLoader);
        m.setTimer(_timer);
            
        _modulesByName.put(name,m);
            
        // process inheritance
        ButterflyModule parentModule = null;
        String parentName = p.getString(extendsProperty);
        if (parentName != null) {
            if (_moduleProperties.containsKey(parentName)) {
                if (_modulesByName.containsKey(parentName)) {
                    parentModule = _modulesByName.get(parentName);
                } else {
                    parentModule = createModule(parentName);
                }
            } else {
                throw new RuntimeException("Cannot wire module '" + name + "' because the extended module '" + parentName + "' is not defined.");
            }
        }

        if (parentModule != null) {
            m.setExtended(parentModule);
            parentModule.addExtendedBy(m);
        }
            
        _logger.trace("< Creating module: {}", name);
        
        return m; 
    }

    @SuppressWarnings("unchecked")
	protected void wireModules(ExtendedProperties wirings) {
        _logger.trace("> wireModules()");

        for (String name : _moduleProperties.keySet()) {
            _logger.trace("> Mounting module: {}", name);
            ButterflyModule m = _modulesByName.get(name);
            String mountPointStr = wirings.getString(m.getName());
            if (mountPointStr == null) {
                _logger.info("No mount point defined for module '{}', it won't be exposed.", name);
            } else {
                MountPoint mountPoint = new MountPoint(mountPointStr);
                if (_mounter.isRegistered(mountPoint)) {
                    throw new RuntimeException("Cannot have two different modules with the same mount point '" + mountPoint + "'.");
                } else {
                    _mounter.register(mountPoint, m);
                }
            }
            _logger.trace("< Mounting module: {}", name);
        }
        
        for (String name : _moduleProperties.keySet()) {
            _logger.trace("> Expanding properties for module: {}", name);
            ButterflyModule m = _modulesByName.get(name);
            ExtendedProperties p = _moduleProperties.get(name);
            ButterflyModule extended = m.getExtendedModule();

            while (extended != null) {
                _logger.trace("> Merging properties from extended module: {}", name);
                ExtendedProperties temp = p;
                p = _moduleProperties.get(extended.getName());
                p.combine(temp);
                _logger.trace("< Merging properties from extended module: {} -> {}", name, p);
                extended = extended.getExtendedModule();
            }
            
            _moduleProperties.put(name,p);
            
            List<String> implementations = p.getList(implementsProperty);
            if (implementations != null) {
                for (String i : implementations) {
                    Map<String, ButterflyModule> map = _modulesByInterface.get(i);
                    if (map == null) {
                        map = new HashMap<String,ButterflyModule>();
                        _modulesByInterface.put(i, map);
                    }
                    map.put(name, m);
                    m.setImplementation(i);
                }
            }
            _logger.trace("< Expanding properties for module: {}", name);
        }
            
        for (String name : _moduleProperties.keySet()) {
            _logger.trace("> Inject dependencies in module: {}", name);
            ExtendedProperties p = _moduleProperties.get(name);
            ButterflyModule m = _modulesByName.get(name);

            for (Object o : p.keySet()) {
                String s = (String) o;
                if (s.equals(dependencyPrefix)) {
                    for (Object oo : p.getList(s)) {
                        String dep = (String) oo;
                        _logger.trace("> Processing dependency: {}", dep);
                        dep = dep.trim();
                        Map<String,ButterflyModule> modules = _modulesByInterface.get(dep);
                        if (modules != null) {
                            if (modules.size() == 1) {
                                // if there's only one module implementing that interface, wiring is automatic
                                setDependency(m, dep, modules.values().iterator().next());
                            } else {
                                ButterflyModule parent = m.getExtendedModule();
                                do {
                                    String wiredDependency = wirings.getString(name + "." + dep);
                                    if (wiredDependency != null) {
                                        setDependency(m, dep, _modulesByName.get(wiredDependency));
                                        break;
                                    } else {
                                        if (parent != null) {
                                            name = parent.getName();
                                        }
                                    }
                                } while (parent != null);
                            }
                        } else {
                            throw new RuntimeException("Cannot wire module '" + name + "' because no module implements the required interface '" + dep + "'");
                        }
                        _logger.trace("< Processing dependency: {}", dep);
                    }
                }
            }
            
            _logger.trace("< Inject dependencies in module: {}", name);
        }
        
        ButterflyModule rootModule = _mounter.getRootModule();
        
        // in case nothing defined the root mount point use the default one
        if (rootModule == null) {
            rootModule = _modulesByName.get("main");
        }
        
        // in case not even the 'main' module is available, give up
        if (rootModule == null) {
            throw new RuntimeException("Cannot initialize the modules because I can't guess which module to mount to '/'");
        }
        
        _logger.trace("< wireModules()");
    }    
        
    @SuppressWarnings("unchecked")
    protected void configureModules() {
        _logger.trace("> configureModules()");
        for (String name : _moduleProperties.keySet()) {
            _logger.trace("> Configuring module: {}", name);
            ExtendedProperties p = _moduleProperties.get(name);
            ButterflyModule m = _modulesByName.get(name);
            
            // make the system properties accessible to the modules
            m.setProperties(_properties);
            
            try {
                if (p.getBoolean("templating", Boolean.TRUE)) {
                    _logger.trace("> enabling templating");
                    // load the default velocity properties
                    Properties properties = new Properties();
                    File velocityProperties = new File(_webInfDir, "velocity.properties");
                    _classLoader.watch(velocityProperties); // reload if the velocity properties change
                    FileInputStream fis = new FileInputStream(velocityProperties);
                    properties.load(fis);
                    fis.close();
        
                    // set properties for resource loading
                    properties.setProperty("resource.loader", "butterfly");
                    properties.setProperty("butterfly.resource.loader.class", ButterflyResourceLoader.class.getName());
                    properties.setProperty("butterfly.resource.loader.cache", "true");
                    properties.setProperty("butterfly.resource.loader.modificationCheckInterval", "1");
                    properties.setProperty("butterfly.resource.loader.description", "Butterfly Resource Loader");
                        
                    // set properties for macros
                    properties.setProperty("velocimacro.library", p.getString("templating.macros", ""));
        
                    // Set our special parent injection directive
                    properties.setProperty("userdirective", Super.class.getName());
        
                    // create a module-specific velocity engine
                    VelocityEngine velocity = new VelocityEngine();
                    velocity.setApplicationAttribute("module", m); // this is how we pass the module to the resource loader
                    velocity.init(properties);
                    
                    // inject the template engine in the module
                    m.setTemplateEngine(velocity);
                    _logger.trace("< enabling templating");
                }

                List<String> scriptables = p.getList("scriptables");
                if (scriptables.size() > 0) {
                    Context context = Context.enter();

                    BufferedReader initializerReader = null; 

                    for (String scriptable : scriptables) {
                        if (!scriptable.equals("")) {
                            try {
                                _logger.trace("> adding scriptable object: {}", scriptable);
                                Class c  = _classLoader.loadClass(scriptable);
                                ButterflyScriptableObject o = (ButterflyScriptableObject) c.newInstance();
                                setScriptable(m, o);
                                URL initializer = c.getResource("init.js");
                                if (initializer != null) {
                                    initializerReader = new BufferedReader(new InputStreamReader(initializer.openStream()));
                                    setScript(m, initializer, context.compileReader(initializerReader, "init.js", 1, null));
                                    _scriptWatcher.watch(initializer,m);
                                    _logger.trace("Parsed scriptable javascript initializer successfully");
                                }
                                _logger.trace("< adding scriptable object: {}", scriptable);
                            } catch (Exception e) {
                                _logger.trace("Error initializing scriptable object '{}': {}", scriptable, e);
                            } finally {
                                if (initializerReader != null) initializerReader.close();
                            }
                        }
                    }

                    Context.exit();
                }
                
                List<String> controllers = p.getList("controller", CONTROLLER);
                Set<URL> controllerURLs = new HashSet<URL>(controllers.size());
                for (String controller : controllers) {
                    URL controllerURL = m.getResource("MOD-INF/" + controller);
                    if (controllerURL != null) {
                        controllerURLs.add(controllerURL);
                    }
                }
                
                if (controllerURLs.size() > 0) {
                    _logger.trace("> enabling javascript control");
                    
                    Context context = Context.enter();

                    BufferedReader initializerReader = null; 
                    
                    try {
                        URL initializer = this.getClass().getClassLoader().getResource("edu/mit/simile/butterfly/Butterfly.js");
                        initializerReader = new BufferedReader(new InputStreamReader(initializer.openStream()));
                        setScript(m, initializer, context.compileReader(initializerReader, "Butterfly.js", 1, null));
                        _scriptWatcher.watch(initializer,m);
                        _logger.trace("Parsed javascript initializer successfully");
                    } finally {
                        if (initializerReader != null) initializerReader.close();
                    }
                    
                    BufferedReader controllerReader = null;

                    for (URL controllerURL : controllerURLs) {
                        try{
                            controllerReader = new BufferedReader(new InputStreamReader(controllerURL.openStream()));
                            setScript(m, controllerURL, context.compileReader(controllerReader, controllerURL.toString(), 1, null));
                            _scriptWatcher.watch(controllerURL,m);
                            _logger.trace("Parsed javascript controller successfully: {}", controllerURL);
                        } finally {
                            if (controllerReader != null) controllerReader.close();
                        }
                    }
                    
                    Context.exit();
    
                    _logger.trace("< enabling javascript control");
                }
            } catch (Exception e) {
                _logger.error("Error enabling javascript control",e);
            }
            _logger.trace("< Configuring module: {}", name);
        }
        _logger.trace("< configureModules()");
    }

    protected void setDependency(ButterflyModule subj, String dep, ButterflyModule obj) {
        subj.setDependency(dep, obj);
        ButterflyModule extended = subj.getExtendedModule();
        if (extended != null) {
            setDependency(extended, dep, obj);
        }
    }

    protected void setScriptable(ButterflyModule mod, ButterflyScriptableObject scriptable) {
        mod.setScriptable(scriptable);
        ButterflyModule extended = mod.getExtendedModule();
        if (extended != null) {
            setScriptable(extended, scriptable);
        }
    }
    
    /*
     * NOTE(SM): I'm fully aware that these embedded HTML snippets are really ugly, but I don't
     * want to depend on velocity for error reporting as that would prevent us from reporting
     * errors about velocity's dependency itself. 
     */
    
    String header = 
        "<html>" +
        " <head>" +
        " </head>" +
        " <body>";
    
    String footer = "</body></html>";

    protected void delay(HttpServletResponse response, String title) throws IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        PrintWriter writer = response.getWriter();
        writer.println(header);
        writer.println("<h1>" + title + "</h1>");
        writer.println("<script>setTimeout(function() { window.location = '.' }, 3000);</script>");
        writer.println(footer);
        writer.close();
    }
    
    protected void error(HttpServletResponse response, String title, String msg, Exception e) throws IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        writer.println(title);
        writer.println(msg);
        if (e != null) {
            e.printStackTrace(writer);
        }
        writer.close();
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, stringWriter.toString());
    }
    
    static protected void setScript(ButterflyModule mod, URL location, Script script) {
        mod.setScript(location, script);
        ButterflyModule extended = mod.getExtendedModule();
        if (extended != null) {
            setScript(extended, location, script);
        }
    }
    
    /*
     * This is the trigger invoked by the butterfly classloader if any of the observed classes or files 
     * has changed. This trigger attempts to find the Butterfly.class on disk and changes its lastModified
     * time if found. This has no effect in some servlet containers, but in others (for example the Jetty 
     * plugin for Maven) this triggers a context autoreload.
     * NOTE: this is only invoked when files that were found when the application started are modified 
     * Adding new files to the classpath does not trigger a restart!
     */
    private static class Trigger implements Runnable {

        final static private Logger _logger = LoggerFactory.getLogger("butterfly.trigger");

        private File _context;
        
        Trigger(File context) {
            _context = context; 
        }
        
        public void run() {
            _logger.debug("classloader changed trigger invoked");
            List<File> tries = new ArrayList<File>();
            tries.add(new File(_context, "WEB-INF/classes/edu/mit/simile/butterfly/Butterfly.class"));
            for (File f : tries) {
                _logger.debug(" trying: " + f.getAbsolutePath());
                if (f.exists()) {
                    f.setLastModified((new Date()).getTime());
                    _logger.debug("  touched!!");
                    return;
                }
            }
            _logger.debug("  but could not find any class to touch!!");
        }
    }
}

class ButterflyScriptWatcher extends TimerTask {

    final static private Logger _logger = LoggerFactory.getLogger("butterfly.script_watcher");
    
    private Map<URL,ButterflyModule> scripts = new HashMap<URL,ButterflyModule>();
    private Map<URL,Long> lastModifieds = new HashMap<URL,Long>();
            
    protected void watch(URL script, ButterflyModule module) throws Exception {
        _logger.debug("Watching {}", script);
        this.lastModifieds.put(script, script.openConnection().getLastModified());
        this.scripts.put(script, module);
    }
    
    public void run() {
        for (URL url : this.scripts.keySet()) {
            try {
                URLConnection connection = url.openConnection();
                long lastModified = connection.getLastModified(); 
                if (lastModified > this.lastModifieds.get(url)) {
                    _logger.debug("{} has changed, reparsing...", url);
                    this.lastModifieds.put(url, lastModified);
                    ButterflyModule module = this.scripts.get(url);
                    BufferedReader reader = null;
                    try {
                        Context context = Context.enter();
                        reader = new BufferedReader(new InputStreamReader(url.openStream()));
                        Butterfly.setScript(module, url, context.compileReader(reader, url.getFile(), 1, null));
                        _logger.info("{} reloaded", url);
                        Context.exit();
                    } finally {
                        if (reader != null) reader.close();
                    }
                }
                connection.getInputStream().close(); // NOTE(SM): this avoids leaking file descriptions in some JVMs
            } catch (Exception e) {
                _logger.error("", e);
            }
        }
    }
}

