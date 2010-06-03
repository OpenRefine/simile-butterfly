package edu.mit.simile.butterfly;

import java.net.URL;

public class Zone {

    private String name;
    private String protocol;
    private String host;
    private String path;
    
    private String prefix;
    private String full;

    public Zone(String name, String zonePath) {
        this.name = name;
        int index = zonePath.indexOf("://");
        if (index > 0) {
            try {
                URL url = new URL(zonePath);
                protocol = url.getProtocol();
                host = url.getHost();
                path = url.getPath();
            } catch (Exception e) {
                throw new RuntimeException("Could not parse the zone path '" + zonePath + "', make sure it's a valid HTTP URL or an absolute path.", e);
            }
        } else {
            path = zonePath;
        }
        
        if (!path.startsWith("/")) path = "/" + path;
        if (path.endsWith("/")) path = path.substring(0,path.length() - 1);
        
        StringBuffer b = new StringBuffer();
        if (protocol != null) {
            b.append(protocol);
            b.append("://");
        }
        if (host != null) {
            b.append(host);
        }
        prefix = b.toString();
        b.append(path);
        full = b.toString();
    }
    
    public String getPrefix() {
        return this.prefix;
    }
    
    public String getPath() {
        return this.path;
    }
    
    public String getName() {
        return this.name;
    }
    
    public String toString() {
        return this.full;
    }

}
