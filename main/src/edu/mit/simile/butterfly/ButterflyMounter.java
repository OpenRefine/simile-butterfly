package edu.mit.simile.butterfly;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ButterflyMounter {

    private static final Logger logger = LoggerFactory.getLogger("butterfly.mounter");

    private ButterflyModule rootModule;
    
    private Map<MountPoint,ButterflyModule> modulesByMountPoint = new HashMap<MountPoint,ButterflyModule>();
    private Map<String,Set<ButterflyModule>> modulesByMountPath = new HashMap<String,Set<ButterflyModule>>();
    
    public void register(MountPoint mountPoint, ButterflyModule module) {
        module.setMountPoint(mountPoint);
        if (mountPoint.equals(MountPoint.ROOT)) {
            rootModule = module;
        }
        modulesByMountPoint.put(mountPoint, module);
        String mountPath = mountPoint.getMountPoint();
        Set<ButterflyModule> modules = modulesByMountPath.get(mountPath);
        if (modules == null) {
            modules = new HashSet<ButterflyModule>();
            modulesByMountPath.put(mountPath, modules);
        }
        modules.add(module);
    }
    
    public boolean isRegistered(MountPoint mountPoint) {
        return modulesByMountPoint.containsKey(mountPoint);
    }
    
    public ButterflyModule getRootModule() {
        return rootModule;
    }
    
    public ButterflyModule getModule(String path, Zone zone) {
        ButterflyModule m = null;
        String p = path;
        while (m == null) {
            int index = p.lastIndexOf('/');
            if (index >= 0) {
                String leader = p.substring(0,index + 1);
                Set<ButterflyModule> mods = modulesByMountPath.get(leader);
                if (mods != null) {
                    if (mods.size() == 1) {
                        m = mods.iterator().next();
                    } else {
                        for (ButterflyModule mod : mods) {
                            String z = mod.getMountPoint().getZone();
                            if (z == null) {
                                m = mod;
                            } else {
                                if (z.equals(zone.getName())) {
                                    m = mod;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (m == null) {
                    p = leader.substring(0, leader.length() - 1);
                }
            } else {
                m = rootModule;
            }
        }
        return m;
    }

    // ----------------------------------------------------------------------------
    
    private Zone rootZone;
    
    public static final String ROOT_ZONE = "root";
    
    private LinkedHashMap<String,Zone> zonesByName = new LinkedHashMap<String,Zone>();
    private LinkedHashMap<String,Zone> zonesByContext = new LinkedHashMap<String,Zone>();
    
    public void setDefaultZone(String name) {
        rootZone = this.zonesByName.get(name);
    }
    
    public void registerZone(String name, String zoneURL) {
        if (zoneURL.charAt(zoneURL.length() - 1) == '/') {
            zoneURL = zoneURL.substring(0, zoneURL.length() - 1);
        }
        logger.trace("Register Zone: {} -> {}", name, zoneURL);
        Zone z = new Zone(name, zoneURL);
        this.zonesByName.put(name, z);
        this.zonesByContext.put(zoneURL, z);
    }
    
    public Zone getZone(HttpServletRequest request) {
        logger.trace("> getZone");
        
        Zone zone = null;
        
        String zoneHeader = request.getHeader(Butterfly.CONTEXT_HEADER);
        if (zoneHeader != null) {
            // start searching with the zones defined with absolute URLs
            // (those who have a URL prefix defined)
            String context = Butterfly.getTrueContextPath(request, true);
            logger.debug("absolute context: {}", context);
            zone = zonesByContext.get(context);

            // if not found, fall back to the zones defined with relative paths 
            // (those without URL prefix)
            if (zone == null) {
                context = Butterfly.getTrueContextPath(request, false);
                logger.debug("relative context: {}", context);
                zone = zonesByContext.get(context);
            }
        }
        
        // if not zone is found, default to the rootZone
        if (zone == null) {
            logger.debug("defaulting to root zone");
            zone = rootZone;
        }
        
        logger.trace("< getZone -> {}");
        return zone;
    }

    public int size() {
        return this.zonesByName.size();
    }
        
}
