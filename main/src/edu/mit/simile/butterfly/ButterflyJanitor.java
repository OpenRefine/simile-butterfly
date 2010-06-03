package edu.mit.simile.butterfly;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ButterflyJanitor extends TimerTask {

    final static private Logger _logger = LoggerFactory.getLogger("butterfly.jannitor");
    
    private Map<File,Set<File>> origins = new HashMap<File,Set<File>>();
    
    public void recordOrigin(File resource, File origin) throws Exception {
        _logger.trace("Recording that {} depends on {}", resource, origin);
        Set<File> set = null;
        
        if (!this.origins.containsKey(resource)) {
            set = new HashSet<File>();
            this.origins.put(resource,set);
        } else {
            set = this.origins.get(resource);
        }
        set.add(origin);
    }
    
    public void run() {
        Iterator<File> resources = this.origins.keySet().iterator();
        while(resources.hasNext()) {
            File resource = resources.next();
            try {
                Set<File> origins = this.origins.get(resource);
                long lastModified = resource.lastModified();
                for (File origin : origins) {
                    if ((origin.exists() && origin.lastModified() > lastModified) || !origin.exists()) {
                        _logger.debug("{} has changed, invalidating dependent resource {}", origin, resource);
                        if (resource.exists() && resource.canWrite()) {
                            resource.delete();
                        }
                        resources.remove();
                    }
                }
            } catch (Exception e) {
                _logger.error("Error", e);
            }
        }
    }
}
