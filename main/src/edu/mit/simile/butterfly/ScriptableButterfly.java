package edu.mit.simile.butterfly;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.velocity.VelocityContext;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import edu.mit.simile.butterfly.ButterflyModuleImpl.Level;

/**
 * This class represents the "butterfly" javascript object that
 * is made available to the javascript controllers and allows
 * the controller to interact with butterfly and invoke 
 * its operations.
 */
public class ScriptableButterfly extends ButterflyScriptableObject {

    private static final long serialVersionUID = -1670900391982560723L;

    protected static final Logger _logger = LoggerFactory.getLogger("butterfly.scriptable");
    protected static final Logger _jsLogger = LoggerFactory.getLogger("javascript");
        
    boolean _responded = false;

    public static String getName() {
        return "Butterfly";
    }
    
    public String getClassName() {
        return getName();
    }
    
    public boolean didRespond() {
        return _responded;
    }
        
    // ----------------------------------------------------------------------------
    
    public void jsFunction_redirect(Object request, Object response, String location) throws Exception {
        _logger.trace("> jsFunction_redirect");
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        HttpServletResponse res = (HttpServletResponse) unwrap(response);
        _module.redirect(req, res, location);
        responded();
        _logger.trace("< jsFunction_redirect");
    }
    
    public void jsFunction_sendBinary(Object request, Object response, String file, String mimeType) throws Exception {
        _logger.trace("> jsFunction_sendBinary");
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        HttpServletResponse res = (HttpServletResponse) unwrap(response);
        _module.sendBinary(req, res, file, mimeType);
        responded();
        _logger.trace("< jsFunction_sendBinary");
    }

    public void jsFunction_sendText(Object request, Object response, String file, String encoding, String mimeType, boolean absolute) throws Exception {
        _logger.trace("> jsFunction_sendText");
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        HttpServletResponse res = (HttpServletResponse) unwrap(response);
        _module.sendText(req, res, _module.getResource(file), encoding, mimeType, absolute);
        responded();
        _logger.trace("< jsFunction_sendText");
    }
    
    public void jsFunction_sendWrappedText(Object request, Object response, String file, String encoding, String mimeType, String prologue, String epilogue, boolean absolute) throws Exception {
        _logger.trace("> jsFunction_sendWrappedText");
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        HttpServletResponse res = (HttpServletResponse) unwrap(response);
        _module.sendWrappedText(req, res, _module.getResource(file), encoding, mimeType, prologue, epilogue, absolute);
        responded();
        _logger.trace("< jsFunction_sendWrappedText");
    }
    
    public void jsFunction_sendTextFromTemplate(Object request, Object response, Object context, String template, String encoding, String mimeType, boolean absolute) throws Exception {
        _logger.trace("> jsFunction_sendTextFromTemplate");
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        HttpServletResponse res = (HttpServletResponse) unwrap(response);
        _module.sendTextFromTemplate(req, res, jsToVelocity(context), template, encoding, mimeType, absolute);    
        responded();
        _logger.trace("< jsFunction_sendTextFromTemplate");
    }
    
    public void jsFunction_sendXmlDocument(Object request, Object response, Object document, String encoding, String mimeType, boolean absolute) throws Exception {
        _logger.trace("> jsFunction_sendXmlDocument");
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        HttpServletResponse res = (HttpServletResponse) unwrap(response);
        Document doc = (Document) unwrap(document);
        
        res.setContentType(mimeType);
        res.setCharacterEncoding(encoding);
        
        PrintWriter writer = _module.getFilteringWriter(req, res, absolute);
        
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(writer));

        writer.close();
        
        responded();
        _logger.trace("< jsFunction_sendXmlDocument");
    }
    
    public void jsFunction_sendXHtmlDocument(Object request, Object response, Object document, String encoding, String mimeType, boolean absolute) throws Exception {
        _logger.trace("> jsFunction_sendXHtmlDocument");
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        HttpServletResponse res = (HttpServletResponse) unwrap(response);
        Document doc = (Document) unwrap(document);
        
        res.setContentType(mimeType);
        res.setCharacterEncoding(encoding);
        
        PrintWriter writer = _module.getFilteringWriter(req, res, absolute);
        
        Transformer t = TransformerFactory.newInstance().newTransformer();

        t.setOutputProperty(OutputKeys.METHOD, "html");
        t.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "-//W3C//DTD XHTML 1.0 Transitional//EN");
        t.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd");

        t.transform(new DOMSource(doc), new StreamResult(writer));        
        
        writer.close();
        
        responded();
        _logger.trace("< jsFunction_sendXHtmlDocument");
    }
    
    public void jsFunction_log(String message) {
        if (message == null) message = "null";
        _jsLogger.info(message);
    }
    
    public Object jsFunction_makePath(String pathInfo, Object levels) throws Exception {
        _logger.trace("> jsFunction_makePath");
        Map<String,String> levelsMap = jsToMap(levels);
        List<Level> l = _module.makePath(pathInfo, levelsMap);
        _logger.trace("< jsFunction_makePath");
        return l;
    }
    
    public void jsFunction_responded() {
        _responded = true;
    }

    public void jsFunction_sendString(Object request, Object response, String str, String encoding, String mimeType) throws Exception {
        _logger.trace("> jsFunction_sendString");
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        HttpServletResponse res = (HttpServletResponse) unwrap(response);
        _module.sendString(req, res, str, encoding, mimeType);
        responded();
        _logger.trace("< jsFunction_sendString");
    }
    
    public void jsFunction_sendError(Object request, Object response, int code, String str) throws Exception {
        _logger.trace("> jsFunction_sendError");
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        HttpServletResponse res = (HttpServletResponse) unwrap(response);
        _module.sendError(req, res, code, str);
        responded();
        _logger.trace("< jsFunction_sendError");
    }
    
    public String jsFunction_getString(Object request) throws Exception {
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        return _module.getString(req);
    }

    public MountPoint jsFunction_getMountPoint() throws Exception {
        return _module.getMountPoint();
    }
    
    public ButterflyMounter jsFunction_getMounter() throws Exception {
        return _module.getMounter();
    }
    
    public String jsFunction_getProperty(String key, String defaultValue) throws Exception {
    	return _properties.getString(key, defaultValue);
    }
    
    public String jsFunction_getContextPath(Object request, boolean absolute) throws Exception {
        HttpServletRequest req = (HttpServletRequest) unwrap(request);
        return _module.getContextPath(req,absolute);
    }
            
    // ----------------------------------------------------------------------------
    
    private void responded() {
        this._responded = true;
    }
    
    public static Map<String,String> jsToMap(Object o) {
        Map<String,String> map = new HashMap<String,String>();
        if (o instanceof Scriptable) {
            Scriptable s = (Scriptable) o;
            Object[] ids = s.getIds();
            for (Object id : ids) {
                if (id instanceof String) {
                    String name = (String) id;
                    Object value = s.get(name, s);
                    if (value instanceof String) {
                        map.put(name, (String) value);
                    }
                }
            }
        }
        return map;
    }

    public static VelocityContext jsToVelocity(Object o) {
        _logger.trace("> jsToVelocity");
        VelocityContext context = new VelocityContext();
        if (o instanceof Scriptable) {
            Scriptable s = (Scriptable) o;
            Object[] ids = s.getIds();
            for (Object id : ids) {
                if (id instanceof String) {
                    String name = (String) id;
                    Object value = unwrap(s.get(name, s));
                    if (value instanceof Double) { // numbers are doubles by default in javascript!!
                        value = Integer.valueOf(((Double) value).intValue());
                    }
                    context.put(name, value);
                    if (_logger.isDebugEnabled()) _logger.trace(name + " -> " + value + " [" + ((value != null) ? value.getClass().getName() : "null") + "]");
                }
            }
        }
        _logger.trace("< jsToVelocity");
        return context;
    }

}
