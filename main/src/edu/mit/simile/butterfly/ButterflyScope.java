package edu.mit.simile.butterfly;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents the "scope" of that is given to a javascript controller
 * and contains all the various objects that the controller might need
 * to interact with the system.
 */
public class ButterflyScope extends ScriptableObject {

    private static final long serialVersionUID = -7468104001568307817L;

    protected static final Logger _logger = LoggerFactory.getLogger("butterfly.scope");
    
    public ButterflyScope(ButterflyModule module, Context context) throws Exception {
    	_logger.trace("> new ButterflyScope for module: {}", module.getName());
    	
        // first, get a juicy top level scope that contains all the ECMA
        // objects and some Java related utility methods that Rhino provides
        Scriptable scope = new ImporterTopLevel(context);
        
        // make the "ButteflyModule" object available to this scope
        defineClass(scope, ScriptableButterfly.class);

        // and set this scope as our prototype
        setPrototype(scope);

        // We want this to be a new top-level scope, so set its
        // parent scope to null. This means that any variables created
        // by assignments will be properties of this.
        setParentScope(null);

        // We need to create an instance of the ButterflyModule object and 
        // inject it into this scope. We keep a reference to this instance
        // because it's what we'll need to know if the controller has responded
        // to the request or not
        final Object[] args = {};
        ScriptableButterfly _scriptableButterfly = (ScriptableButterfly) context.newObject(this, ScriptableButterfly.getName(), args);
        _scriptableButterfly.init(module);
        _scriptableButterfly.setParentScope(this);
        super.put("butterfly", this, _scriptableButterfly);

        prepareScope(context, scope, module);
    	_logger.trace("< new ButterflyScope for module: {}", module.getName());
    }

    private void prepareScope(Context context, Scriptable scope, ButterflyModule module) throws Exception {
    	_logger.trace("> prepareScope({})", module.getName());
    	
        Map<String,ButterflyModule> dependencies = module.getDependencies();
        
        for (ButterflyModule m : dependencies.values()) {
        	prepareScope(context, scope, m);
        }

        Set<ButterflyScriptableObject> scriptables = module.getScriptables();

        final Object[] args = {};
        
    	// make the scriptable classes available to this scope 
        // (both as classes and instances)
        Iterator<ButterflyScriptableObject> i = scriptables.iterator();
        while (i.hasNext()) {
        	ButterflyScriptableObject c = i.next();
            defineClass(scope, c.getClass());
        	_logger.debug("defined class: {}", c.getClassName());

            ButterflyScriptableObject scriptable = (ButterflyScriptableObject) context.newObject(this, c.getClassName(), args);
            scriptable.init(module);
            scriptable.setParentScope(this);
            Set<String> implementations = module.getImplementations();
            for (String name : implementations) {
	            super.put(name, this, scriptable);
	        	_logger.debug("defined instance: {}", name);
            }
        }

        _logger.trace("< prepareScope({})", module.getName());
    }

    public String getClassName() {
        return "ButterflyScope";
    }
}
