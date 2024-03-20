package edu.mit.simile.butterfly.velocity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.FileResourceLoader;
import org.apache.velocity.util.ExtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.mit.simile.butterfly.ButterflyModule;

/**
 * This is a Velocity resource loader that is aware of the hierarchy of modules
 * and therefore is capable of dealing with concepts such as template overloading and inheritance
 * based on the wiring of Butterfly modules.
 */
public class ButterflyResourceLoader extends FileResourceLoader {

    final static private Logger _logger = LoggerFactory.getLogger("butterfly.resource_loader");
    
    private ButterflyModule _module;
    
    @Override
    public void commonInit(RuntimeServices rs, ExtProperties configuration) {
        super.commonInit(rs, configuration);
        Object o = rs.getApplicationAttribute("module");
        if (o != null) {
            _module = (ButterflyModule) o;
        } else {
            throw new RuntimeException("The ButterflyResourceLoader couldn't find an instance to the module!");
        }
    }

    @Override
    public synchronized Reader getResourceReader(String name, String encoding) throws ResourceNotFoundException {
        InputStream inputStream = null;
        
        if (StringUtils.isEmpty(name)) {
            throw new ResourceNotFoundException ("No template name provided");
        }
        
        URL url = getResource(name);
        if (url != null) {
            try {
                inputStream = url.openStream();
            } catch (Exception e) {
                _logger.error("Error opening stream", e);
                throw new ResourceNotFoundException(e.getMessage());
            }
        } else {
            // concatenation is safe because we checked name above
            throw new ResourceNotFoundException("Resource '" + name + "' count not be found");
        }

        if (StringUtils.isNotEmpty(encoding)) {
            try {
                return new InputStreamReader(inputStream, encoding);
            } catch (UnsupportedEncodingException e) {
                throw new ResourceNotFoundException("Unsupported encoding requested", e);
            }
        } else {
            return new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        }
    }
    
    @Override
    public boolean isSourceModified(Resource resource) {
        return getLastModified(resource) > resource.getLastModified();
    }

    @Override
    public long getLastModified(Resource resource) {
        long result = Long.MAX_VALUE;
        try {
            URLConnection c = getResource(resource.getName()).openConnection();
            result = c.getLastModified();
        } catch (Exception e) {
            _logger.error("Error opening connection", e);
        }
        return result;
    }
    
    private URL getResource(String name) {
        return _module.getResource(name);
    }
}

