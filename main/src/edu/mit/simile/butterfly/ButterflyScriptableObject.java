package edu.mit.simile.butterfly;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

/**
 * This class extends the default Rhino scriptable object to provide some
 * convenience methods.
 */
public abstract class ButterflyScriptableObject extends ScriptableObject {

    private static final long serialVersionUID = 6392807122149170350L;

    protected ButterflyModule _module;
    protected PropertiesConfiguration _properties;

    public void init(ButterflyModule module) {
        _module = module;
        _properties = module.getProperties();
    }
    
    public void destroy() {
    	// do nothing;
    }
	
    public abstract String getClassName();
    
    public static Object wrap(Object obj, Scriptable scope) {
        return Context.javaToJS(obj, scope);        
    }
    
    public static Object unwrap(Object obj) {
        if (obj instanceof Wrapper) {
            obj = ((Wrapper) obj).unwrap();
        } else if (obj == Undefined.instance) {
            obj = null;
        }
        return obj;
    }

}
