package edu.mit.simile.butterfly;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is Butterfly's own classloader that allows it to load classes and libraries
 * from modules directly, instead of forcing them to be in the container's classpath or
 * in the WEB-INF/(classes|lib) folders. This allows modules to be developed in
 * isolation.
 * 
 * Also, this classloader is capable of monitoring changes to the loaded classes
 * (or to special files that we want watched for changes) and it's capable of 
 * executing a given Runnable action when such changes occur. 
 */
public class ButterflyClassLoader extends URLClassLoader {

    private static final Logger _logger = LoggerFactory.getLogger("butterfly.classloader");
    
    private ButterflyClassLoaderWatcher _watcher;

    public ButterflyClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    public TimerTask getClassLoaderWatcher(Runnable trigger) {
        if (_watcher == null) {
            _watcher = new ButterflyClassLoaderWatcher(trigger);
        }
        return _watcher;
    }
    
    @SuppressWarnings("unchecked")
	@Override
    protected Class loadClass(String name, boolean resolve) throws ClassNotFoundException {

        Class clazz = findLoadedClass(name);

        if (clazz == null) {
            try {
                _logger.trace("> Loading: {}", name);
                clazz = findClass(name);
                _logger.trace("< Loaded: {}", name);
            } catch (ClassNotFoundException cnfe) {
            	try {
                    _logger.trace("> Parent loading: {}", name);
                    ClassLoader parent = getParent();
                    clazz = parent.loadClass(name);
                    _logger.trace("< Parent loaded: {}", name);
            	} catch (ClassNotFoundException cnfe2) {
            		try {
	                    _logger.trace("> Current loading: {}", name);
	            		ClassLoader current = this.getClass().getClassLoader();
	            		clazz = current.loadClass(name);
	                    _logger.trace("< Current loaded: {}", name);
            		} catch (ClassNotFoundException cnfe3) {
	                    _logger.trace("> System loading: {}", name);
	            		ClassLoader system = ClassLoader.getSystemClassLoader();
	            		clazz = system.loadClass(name);
	                    _logger.trace("< System loaded: {}", name);
            		}
            	}
            }
        }

        if (resolve) {
            resolveClass(clazz);
        }

        return clazz;
    }

    public void addRepository(File repository) {
    	_logger.trace("> Processing class repository: {}", repository);

        if (repository.exists()) {
            if (repository.isDirectory()) {
                File[] jars = repository.listFiles();
                try  {
                	_logger.trace("Adding folder: {}", repository);
                    super.addURL(repository.toURI().toURL());
                    if (this._watcher != null) {
                        this._watcher.addFile(repository);
                    }
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException(e.toString());
                }

                for (int i = 0; i < jars.length; i++) {
                    if (jars[i].getAbsolutePath().endsWith(".jar")) {
                        addJar(jars[i]);
                    }
                }
            } else {
                addJar(repository);
            }
        } else {
            _logger.info("Repository {} does not exist", repository);
        }

        _logger.trace("> Processing class repository: {}", repository);
    }
    
    public void watch(File file) {
        if (this._watcher != null) {
            this._watcher.watch(file);
        }
    }
    
    private void addJar(File file) {
        try  {
            URL url = file.toURI().toURL();
            _logger.trace("Adding jar: {}", file);
            super.addURL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e.toString());
        }
    }
}

class ButterflyClassLoaderWatcher extends TimerTask {

    final static private Logger _logger = LoggerFactory.getLogger("butterfly.classloader.watcher");
    
    private Set<File> files;
    private Map<File,Long> lastModifieds;
    private Runnable trigger;

    ButterflyClassLoaderWatcher (Runnable t) {
        this.trigger = t;
        this.files = new LinkedHashSet<File>();
        this.lastModifieds = new HashMap<File,Long>();
    }
        
    protected void addFile(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            for (int i = 0; i < files.length; i++) {
                addFile(files[i]);
            }
        } else {
            if (f.getName().endsWith(".jar") || f.getName().endsWith(".class")) {
                watch(f);
            } else {
                _logger.debug("Not watching '{}' since it's not java bytecode.", f);
            }
        }
    }
    
    protected void watch(File f) {
        _logger.trace("Watching {}", f);
        synchronized(this) {
            this.files.add(f);
            this.lastModifieds.put(f, Long.valueOf(f.lastModified()));
        }
    }
    
    public void run() {
        int counter = 0;
        
        synchronized(this) {
            for (File f : this.files) {
                if (f.lastModified() > this.lastModifieds.get(f).longValue()) {
                	_logger.debug(f + " has changed");
                    this.lastModifieds.put(f, Long.valueOf(f.lastModified()));
                    counter++;
                }
            }
        }
        
        if (counter > 0) {
            _logger.debug("Classloading space has changed. Triggering the signal...");
            this.trigger.run();
            _logger.debug("..done");
        }
    }
}
