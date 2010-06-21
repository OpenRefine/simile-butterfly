package edu.mit.simile.butterfly;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

import edu.mit.simile.butterfly.ButterflyModuleImpl.Level;

/**
 * This is the interface that defines what Butterfly modules behave and 
 * what functionality is made available to them.
 */
public interface ButterflyModule {

    // -------------- Life Cycle --------------

    public void init(ServletConfig config) throws Exception;
    
    public void destroy() throws Exception;
    
    public void initScope(Context context, Scriptable scope);

    // -------------- Setters --------------

    public void setClassLoader(ClassLoader classLoader);
    
    public void setMounter(ButterflyMounter mounter);

    public void setName(String name);
    
    public void setPath(File path);
    
    public void setMountPoint(MountPoint mountPoint);

    public void setExtended(ButterflyModule extended);

    public void addExtendedBy(ButterflyModule extender);
    
    public void setImplementation(String id);
    
    public void setDependency(String name, ButterflyModule module);

    public void setModules(Map<String,ButterflyModule> map);
    
    public void setScript(URL location, Script script);
    
    public void setScriptable(ButterflyScriptableObject scriptable);

    public void setTemplateEngine(VelocityEngine velocity);
        
    public void setProperties(ExtendedProperties properties);
    
    public void setTimer(Timer timer);
    
    // -------------- Getters --------------
    
    public ServletConfig getServletConfig();
    
    public ServletContext getServletContext();
    
    public String getName();
    
    public ExtendedProperties getProperties();

    public File getPath();
    
    public MountPoint getMountPoint();
    
    public ButterflyMounter getMounter();
    
    public File getTemporaryDir();

    public ButterflyModule getModule(String name);
    
    public ButterflyModule getExtendedModule();

    public Map<String,ButterflyModule> getDependencies();
    
    public Set<String> getImplementations();
    
    public Set<ButterflyScriptableObject> getScriptables();
    
    public VelocityEngine getTemplateEngine();

    public URL getResource(String name);
    
    public String getRelativePath(HttpServletRequest request);
    
    public PrintWriter getFilteringWriter(HttpServletRequest request, HttpServletResponse response, boolean absolute) throws IOException;
    
    public String getString(HttpServletRequest request) throws Exception;
    
    public String getContextPath(HttpServletRequest request, boolean absolute);

    // -------------- Operation --------------

    public boolean process(String path, HttpServletRequest request, HttpServletResponse response) throws Exception;

    // -------------- Outward Methods --------------
    
    public boolean redirect(HttpServletRequest request, HttpServletResponse response, String location) throws Exception;
        
    public boolean sendBinary(HttpServletRequest request, HttpServletResponse response, String file, String mimeType) throws Exception;

    public boolean sendBinary(HttpServletRequest request, HttpServletResponse response, URL resource, String mimeType) throws Exception;

    public boolean sendText(HttpServletRequest request, HttpServletResponse response, String file, String encoding, String mimeType, boolean absolute) throws Exception;
    
    public boolean sendText(HttpServletRequest request, HttpServletResponse response, URL resource, String encoding, String mimeType, boolean absolute) throws Exception;
    
    public boolean sendWrappedText(HttpServletRequest request, HttpServletResponse response, URL resource, String encoding, String mimeType, String prologue, String epilogue, boolean absolute) throws Exception;
    
    public boolean sendTextFromTemplate(HttpServletRequest request, HttpServletResponse response, VelocityContext velocity, String template, String encoding, String mimeType, boolean absolute) throws Exception;    

    public boolean sendString(HttpServletRequest request, HttpServletResponse response, String str, String encoding, String mimeType) throws Exception;
    
    public boolean sendError(HttpServletRequest request, HttpServletResponse response, int code, String str) throws Exception;

    // -------------- Utility Methods -----------------
    
    public List<Level> makePath(String path, Map<String,String> descs);

}
