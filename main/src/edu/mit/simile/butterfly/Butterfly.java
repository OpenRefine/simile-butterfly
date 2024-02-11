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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.AccessControlException;
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

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
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
public class Butterfly extends HttpServlet {

    public static final String HOST_HEADER = "X-Forwarded-Host";
    public static final String CONTEXT_HEADER = "X-Context-Path";
    
    private static final long serialVersionUID = 1938797827088619577L;

    private static final long watcherDelay = 1000;
    
    public static final String NAME = "butterfly.name";
    public static final String APPENGINE = "butterfly.appengine";
    public static final String AUTORELOAD = "butterfly.autoreload";
    public static final String HOME = "butterfly.home";
    public static final String ZONE = "butterfly.zone";
    public static final String BASE_URL = "butterfly.url";
    public static final String DEFAULT_ZONE = "butterfly.default.zone";
    public static final String DEFAULT_MOUNTPOINT = "butterfly.default.mountpoint";
    public static final String MODULES_IGNORE = "butterfly.modules.ignore";
    public static final String MODULES_PATH = "butterfly.modules.path";
    
    public static final String MAIN_ZONE = "main";

    final static List<String> CONTROLLER;
    
    static {
        CONTROLLER = new ArrayList<String>();
        CONTROLLER.add("controller.js");
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

    public static boolean isGAE(ServletConfig config) {
        return (config.getServletContext().getServerInfo().indexOf("Google App Engine") != -1);
    }
    
    // ---------------------------------------------------------------

    transient private Logger _logger;

    private boolean _autoreload;
    private boolean _appengine;
    private String _name;
    private String _default_mountpoint;
    private int _routingCookieMaxAge;
    
    private String[] _ignores;
    
    transient protected Timer _timer;
    transient protected ButterflyClassLoader _classLoader;
    transient protected ButterflyScriptWatcher _scriptWatcher;
    transient protected ServletConfig _config;
    transient protected ServletContext _context;
    transient protected ButterflyMounter _mounter;

    protected PropertiesConfiguration _properties;
    protected File _contextDir;
    protected File _homeDir;
    protected File _webInfDir;
    protected Exception _configurationException;

    protected boolean _configured = false;

    protected ContextFactory contextFactory;
    
    class ButterflyContextFactory extends ContextFactory {
        protected void onContextCreated(Context cx) {
            cx.setOptimizationLevel(9);
            super.onContextCreated(cx);
        }
    }    
    
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        _config = config;

        _appengine = isGAE(config);
        
        _name = System.getProperty(NAME, "butterfly");

        _context = config.getServletContext();
        _context.setAttribute(NAME, _name);
        _context.setAttribute(APPENGINE, _appengine);
                
        _contextDir = new File(_context.getRealPath("/"));
        _webInfDir = new File(_contextDir, "WEB-INF");
        _properties = new PropertiesConfiguration();
        _mounter = new ButterflyMounter();

        // Load the butterfly properties
        String props = System.getProperty("butterfly.properties");
        File butterflyProperties = (props == null) ? new File(_webInfDir, "butterfly.properties") : new File(props);

        try (BufferedInputStream is = new BufferedInputStream(Files.newInputStream(butterflyProperties.toPath()))){
            _properties.read(new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (IOException|ConfigurationException e) {
            throw new ServletException("Could not find butterfly properties file",e);
        }

        // Process eventual properties includes
        String includes = _properties.getString("butterfly.includes");
        if (includes != null) {
            for (String prop : includes.split(",")) {
                File prop_file = (prop.startsWith("/")) ? new File(prop) : new File(_webInfDir, prop);
                try (BufferedInputStream is = new BufferedInputStream(Files.newInputStream(prop_file.toPath()))){
                    PropertiesConfiguration p = new PropertiesConfiguration();
                    p.read(new InputStreamReader(is, StandardCharsets.UTF_8));
                    _properties.append(p);
                } catch (ConfigurationException | IOException e) {
                    // ignore - TODO: clean this up
                }
            }
        }
        // Overload with properties set from the command line 
        // using the -Dkey=value parameters to the JVM
        Properties systemProperties = System.getProperties();
        for (Object o : systemProperties.keySet()) {
            String key = (String) o;
            String value = systemProperties.getProperty(key);
            _properties.setProperty(key, value);
        }

        _default_mountpoint = _properties.getString(DEFAULT_MOUNTPOINT, "/modules");
        _ignores = _properties.getString(MODULES_IGNORE, "").split(",");

        _autoreload = _properties.getBoolean(AUTORELOAD, false);
        
        _logger = LoggerFactory.getLogger(_name);
        
        _logger.info("Starting {} ...", _name);

        _logger.info("Properties loaded from {}", butterflyProperties);

        if (_autoreload) _logger.info("Autoreloading is enabled");
        if (_appengine) _logger.info("Running in Google App Engine");

        _logger.debug("> init");
        
        _logger.debug("> initialize classloader");
        try {
            _classLoader = AccessController.doPrivileged (
                new PrivilegedAction<ButterflyClassLoader>() {
                    public ButterflyClassLoader run() {
                        return new ButterflyClassLoader(this.getClass().getClassLoader());
                    }
                }
            );
            
            Thread.currentThread().setContextClassLoader(_classLoader);
            _classLoader.watch(butterflyProperties); // reload if the butterfly properties change
            contextFactory = new ButterflyContextFactory();
            contextFactory.initApplicationClassLoader(_classLoader); // tell rhino to use this classloader as well

            ContextFactory.initGlobal(contextFactory);
            
            if (_autoreload && !_appengine) {
                _timer = new Timer(true);
                TimerTask classloaderWatcher = _classLoader.getClassLoaderWatcher(new Trigger(_contextDir));
                _timer.schedule(classloaderWatcher, watcherDelay, watcherDelay);
            }
        } catch (Exception e) {
            throw new ServletException("Failed to load butterfly classloader", e);
        }
        _logger.debug("< initialize classloader");

        if (_autoreload && !_appengine) {
            _logger.debug("> initialize script watcher");
            _scriptWatcher = new ButterflyScriptWatcher();
            _timer.schedule(_scriptWatcher, watcherDelay, watcherDelay);
            _logger.debug("< initialize script watcher");
        }

        this.configure();
        
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
        
        if (_timer != null) {
            _timer.cancel();
        }
        
        _logger.info("done.");
    }

    public void configure() {
        _logger.debug("> configure");

        _logger.info("> process properties");
        try {

            String homePath = _properties.getString(HOME);
            if (homePath == null) {
                _homeDir = _contextDir;
            } else {
                _homeDir = new File(homePath);
            }
            _logger.info("Butterfly home: {}", _homeDir);
            
            for(Iterator<String> i = _properties.getKeys(ZONE); i.hasNext(); ) {
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
        _logger.info("< process properties");

        _logger.info("> load modules");
        // load modules from the properties found in the butterfly.properties
        List<String> paths = _properties.getList(String.class, MODULES_PATH);
        for (String path : paths) {
            findModulesIn(absolutize(_homeDir, path.trim()));
        }

        // load modules from the path found in the servlet init properties
        String servlet_paths = this._config.getInitParameter(MODULES_PATH);
        if (servlet_paths != null) {
            for (String path : servlet_paths.split(",")) {
                findModulesIn(absolutize(_homeDir, path.trim()));
            }
        }
        _logger.info("< load modules");
        
        _logger.info("> create modules");
        for (String name : _moduleProperties.keySet()) {
            createModule(name);
        }
        _logger.info("< create modules");
        
        _logger.info("> load module wirings");
        PropertiesConfiguration wirings = new PropertiesConfiguration();

        // Load the wiring properties
        File moduleWirings = absolutize(_homeDir, _properties.getString("butterfly.modules.wirings","WEB-INF/modules.properties"));
        _logger.info("Loaded module wirings from: {}", moduleWirings);
        _classLoader.watch(moduleWirings); // reload if the module wirings change
        try (FileInputStream fis = new FileInputStream(moduleWirings)) {
            wirings.read(new InputStreamReader(fis, StandardCharsets.UTF_8));
        } catch (IOException | ConfigurationException e) {
            _configurationException = new Exception("Failed to load module wirings", e);
        }
        _logger.info("< load module wirings");

        _logger.info("> wire modules");
        try {
            wireModules(wirings);
        } catch (Exception e) {
            _configurationException = new Exception("Failed to wire modules", e);
        }
        _logger.info("< wire modules");

        _logger.info("> configure modules");
        try {
            configureModules();
        } catch (Exception e) {
            _configurationException = new Exception("Failed to configure modules", e);
        }
        _logger.info("< configure modules");
                
        _logger.info("> initialize modules");
        Set<String> initialized = new HashSet<String>();
        Set<String> initializing = new HashSet<String>();
        for (String name : _modulesByName.keySet()) {
            initializeModule(name, initialized, initializing);
        }
        _logger.info("< initialize modules");
        
        _configured = true;
        
        _logger.debug("< configure");
    }
    
    protected void initializeModule(String name, Set<String> initialized, Set<String> initializing) {
        ButterflyModule m = _modulesByName.get(name);
        if (m != null && !initialized.contains(name)) {
            _logger.debug("> initialize " + m.getName());
            
            if (initializing.contains(name)) {
                _logger.warn("Circular dependencies detected involving module " + m);
            } else {
                initializing.add(name);
                for (String depends : m.getDependencies().keySet()) {
                    initializeModule(depends, initialized, initializing);
                }
                initializing.remove(name);
            }
            
            try {
                m.init(getServletConfig());
            } catch (Exception e) {
                _configurationException = new Exception("Failed to initialize module: " + m, e);
            } catch (NoClassDefFoundError e) {
                _configurationException = new Exception("Failed to initialize module (missing Java class definition): " + m, e);
            }
            
            _logger.debug("< initialize " + m.getName());
            initialized.add(name);
        }
    }
    
    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String method = request.getMethod();
        String path = request.getPathInfo();
        String urlQuery = request.getQueryString();

        if (_mounter != null) {
            Zone zone = _mounter.getZone(request);

            if (_logger.isDebugEnabled()) {
                _logger.debug("> " + method + " [" + ((zone != null) ? zone.getName() : "") + "] " + path + ((urlQuery != null) ? "?" + urlQuery : ""));
                Enumeration<String> en = request.getHeaderNames();
                while (en.hasMoreElements()) {
                    String header = en.nextElement();
                    _logger.trace("{}: {}", header, request.getHeader(header));
                }
            } else if (_logger.isInfoEnabled()) {
                String zoneName = (zone != null) ? zone.getName() : "";
                _logger.info("{} {} [{}]", new String[] { method,path,zoneName });
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
            if (_logger.isDebugEnabled()) _logger.debug("< " + method + " [" + ((zone != null) ? zone.getName() : "") + "] " + path + ((urlQuery != null) ? "?" + urlQuery : ""));

        } else {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }
    
    // ---------------------------- private -----------------------------------
    
    final static private String dependencyPrefix = "requires";
    final static private String implementsProperty = "implements";
    final static private String extendsProperty = "extends";
    
    protected Map<String,ButterflyModule> _modulesByName = new HashMap<String,ButterflyModule>();
    protected Map<String,Map<String,ButterflyModule>> _modulesByInterface = new HashMap<String,Map<String,ButterflyModule>>();
    protected Map<String, PropertiesConfiguration> _moduleProperties = new HashMap<>();
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
        _logger.debug("look for modules in {}", f);
        File modFile = new File(f,"MOD-INF");
        if (modFile.exists()) {
            _logger.trace("> findModulesIn({})", f);
            try {
                String name = f.getName();

                PropertiesConfiguration p  = new PropertiesConfiguration();
                File propFile = new File(modFile,"module.properties");
                if (propFile.exists()) {
                    _classLoader.watch(propFile); // reload if the module properties change
                    BufferedInputStream stream = new BufferedInputStream(Files.newInputStream(propFile.toPath()));
                    p.read(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    stream.close();
                }

                p.addProperty(PATH_PROP, f.getAbsolutePath());
                
                if (p.containsKey("name")) {
                    name = p.getString("name");
                }
                
                boolean load = true;
                
                for (String s : _ignores) {
                    if (name.matches(s)) {
                        load = false;
                        break;
                    }
                }
                
                if (load) {
                    _moduleProperties.put(name, p);
                }
            } catch (Exception e) {
                _logger.error("Error finding module wirings", e);
            }
            _logger.trace("< findModulesIn({})", f);
        } else {
            File[] files = f.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File file = files[i];
                    try {
                        if (file.isDirectory()) {
                            findModulesIn(file);
                        }
                    } catch (AccessControlException e) {
                        // skip
                        // NOTE: this is needed for Google App Engine that doesn't like us snooping around the internal file system
                        // TODO: Does GAE still throw this deprecated exception?
                    }
                }
            }
        }
    }

    protected ButterflyModule createModule(String name) {
        _logger.trace("> Creating module: {}", name);

        if (_modulesByName.containsKey(name)) {
            _logger.trace("< Module '{}' already exists", name);
            return _modulesByName.get(name);
        }

        PropertiesConfiguration p = _moduleProperties.get(name);
        File path = new File(p.getString(PATH_PROP));
        _logger.debug("Module path: {}", path);
            
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
                Class<?> c = _classLoader.loadClass(manager);
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

    protected void wireModules(PropertiesConfiguration wirings) {
        _logger.trace("> wireModules()");

        _logger.info("mounting modules");
        
        for (String name : _moduleProperties.keySet()) {
            _logger.trace("> Mounting module: {}", name);
            ButterflyModule m = _modulesByName.get(name);
            String mountPointStr = wirings.getString(m.getName());
            if (mountPointStr == null) {
            	String moduleName = m.getName(); 
            	String mountPoint = _default_mountpoint + "/" + m.getName();
                _logger.info("No mount point defined for module '" + moduleName + "', mounting to '" + mountPoint + "'");
            	mountPointStr = mountPoint;
            }
            
            MountPoint mountPoint = new MountPoint(mountPointStr);
            if (_mounter.isRegistered(mountPoint)) {
                throw new RuntimeException("Cannot have two different modules with the same mount point '" + mountPoint + "'.");
            } else {
                _mounter.register(mountPoint, m);
            }
            _logger.trace("< Mounting module: {}", name);
        }
        
        for (String name : _moduleProperties.keySet()) {
            _logger.trace("> Expanding properties for module: {}", name);
            ButterflyModule m = _modulesByName.get(name);
            PropertiesConfiguration p = _moduleProperties.get(name);
            ButterflyModule extended = m.getExtendedModule();

            while (extended != null) {
                _logger.trace("> Merging properties from extended module: {}", name);
                PropertiesConfiguration temp = p;
                p = _moduleProperties.get(extended.getName());
                p.append(temp); // TODO: Double check that this is correct
                _logger.trace("< Merging properties from extended module: {} -> {}", name, p);
                extended = extended.getExtendedModule();
            }
            
            _moduleProperties.put(name,p);
            
            List<String> implementations = p.getList(String.class, implementsProperty);
            if (implementations != null) {
                for (String i : implementations) {
                    Map<String, ButterflyModule> map = _modulesByInterface.computeIfAbsent(i, k -> new HashMap<>());
                    map.put(name, m);
                    m.setImplementation(i);
                }
            }
            _logger.trace("< Expanding properties for module: {}", name);
        }
        
        for (String name : _moduleProperties.keySet()) {
            _logger.trace("> Inject dependencies in module: {}", name);
            PropertiesConfiguration p = _moduleProperties.get(name);
            ButterflyModule m = _modulesByName.get(name);

            for (Iterator<String> keys = p.getKeys(); keys.hasNext(); ) {
                String s = keys.next();
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


    protected void configureModules() {
        _logger.trace("> configureModules()");
        for (String name : _moduleProperties.keySet()) {
            _logger.trace("> Configuring module: {}", name);
            PropertiesConfiguration p = _moduleProperties.get(name);
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
                    properties.setProperty("resource.loaders", "butterfly");
                    properties.setProperty("resource.loader.butterfly.class", ButterflyResourceLoader.class.getName());
                    properties.setProperty("resource.loader.butterfly.cache", "true");
                    properties.setProperty("resource.loader.butterfly.modification_check_interval", "1");
                    properties.setProperty("resource.loader.butterfly.description", "Butterfly Resource Loader");
                        
                    // set properties for macros
                    properties.setProperty("velocimacro.library.path", p.getString("templating.macros", ""));
        
                    // Set our special parent injection directive
                    properties.setProperty("runtime.custom_directives", Super.class.getName());
        
                    // Set logging properties
                    if (_appengine) {
                        properties.setProperty(RuntimeConstants.RUNTIME_LOG_NAME, "org.apache.velocity.runtime.log.JdkLogChute");
                    } else {
                        properties.setProperty(RuntimeConstants.RUNTIME_LOG_NAME, "org.apache.velocity.runtime.log.Log4JLogChute");
                        properties.setProperty("runtime.log.logsystem.log4j.logger", "velocity");
                    }

                    // create a module-specific velocity engine
                    VelocityEngine velocity = new VelocityEngine();
                    velocity.setProperties(properties);
                    velocity.setApplicationAttribute("module", m); // this is how we pass the module to the resource loader
                    velocity.init(properties);
                    
                    // inject the template engine in the module
                    m.setTemplateEngine(velocity);
                    _logger.trace("< enabling templating");
                }

                List<String> scriptables = p.getList(String.class, "scriptables");
                if (scriptables.size() > 0) {
                    Context context = Context.enter();

                    BufferedReader initializerReader = null;

                    for (String scriptable : scriptables) {
                        if (!scriptable.equals("")) {
                            try {
                                _logger.trace("> adding scriptable object: {}", scriptable);
                                @SuppressWarnings("rawtypes")
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
                
                List<String> controllers = p.getList(String.class, "controller", CONTROLLER);
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
                        watch(initializer,m);
                        _logger.trace("Parsed javascript initializer successfully");
                    } finally {
                        if (initializerReader != null) initializerReader.close();
                    }

                    for (URL controllerURL : controllerURLs) {
                        try (BufferedReader controllerReader = new BufferedReader(new InputStreamReader(controllerURL.openStream()))) {
                            setScript(m, controllerURL, context.compileReader(controllerReader, controllerURL.toString(), 1, null));
                            watch(controllerURL, m);
                            _logger.trace("Parsed javascript controller successfully: {}", controllerURL);
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
    
    protected void watch(URL script, ButterflyModule module) throws IOException {
        if (_scriptWatcher != null) {
            _scriptWatcher.watch(script, module);
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

        private List<File> tries = new ArrayList<File>();
        
        Trigger(File context) {
            File web_inf = new File(context, "WEB-INF");
            File classes = new File(web_inf, "classes");
            if (classes.exists()) {
                tries.add(findFile(classes, ".class"));
            }
            File libs = new File(web_inf, "lib");
            if (libs.exists()) {
                tries.add(findFile(libs, ".jar"));
            }
        }
        
        public void run() {
            _logger.info("classloader changed trigger invoked");
            for (File f : tries) {
                _logger.debug("trying: " + f.getAbsolutePath());
                if (f.exists()) {
                    f.setLastModified((new Date()).getTime());
                    _logger.debug(" touched!!");
                    return;
                }
            }
            _logger.warn("could not find anything to touch");
        }
        
        private File findFile(File start, String extension) {
            for (File f : start.listFiles()) {
                if (f.isDirectory()) {
                    return findFile(f, extension);
                } else {
                    if (f.getName().endsWith(extension)) { 
                        return f;
                    }
                }
            }
            return null;
        }
    }
}

class ButterflyScriptWatcher extends TimerTask {

    final static private Logger _logger = LoggerFactory.getLogger("butterfly.script_watcher");
    
    private Map<URL,ButterflyModule> scripts = new HashMap<URL,ButterflyModule>();
    private Map<URL,Long> lastModifieds = new HashMap<URL,Long>();
            
    protected void watch(URL script, ButterflyModule module) throws IOException  {
        _logger.debug("Watching {}", script);
        this.lastModifieds.put(script, script.openConnection().getLastModified());
        this.scripts.put(script, module);
    }
    
    public void run() {
        try {
            // Make a copy of the set to protect against changes
            List<URL> urls = new ArrayList<URL>(this.scripts.keySet());
            for (URL url : urls) {
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
            }
        } catch (Exception e) {
            _logger.error("", e);
        }
    }
}

