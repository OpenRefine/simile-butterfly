package edu.mit.simile.butterfly.tests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import edu.mit.simile.butterfly.Butterfly;
import edu.mit.simile.butterfly.ButterflyModule;
import edu.mit.simile.butterfly.ButterflyModuleImpl;
import edu.mit.simile.butterfly.ButterflyMounter;
import edu.mit.simile.butterfly.MountPoint;
import edu.mit.simile.butterfly.Zone;

public class MountPointTests extends Butterfly {

    private static final long serialVersionUID = 1L;

    transient Logger logger;
    
    @BeforeTest
    public void init() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    String[] _mountPoints = { 
        "/", "/blah/" , "/whatever/", "/blah/blah/", "/blah/whatever/", "/blah/whatever/blah/",
        "/ [blah]", "/blah/ [blah]", "/blah/ [whatever]"
    };
    
    @BeforeTest 
    public void setup() { 
        logger.trace("> setup()");

        _mounter = new ButterflyMounter();
        
        for (String mountPointStr : _mountPoints) {
            _mounter.register(new MountPoint(mountPointStr),new ButterflyModuleImpl());
        }
        
        logger.trace("< setup()");
    }

    @Test 
    public void testMountPoints() throws Exception {
        logger.trace("> testMountPoints()");

        Zone zone = new Zone("main","http://localhost/");
        
        ButterflyModule m = _mounter.getModule("/",zone);
        Assert.assertEquals(m.getMountPoint(),new MountPoint("/"));

        m = _mounter.getModule("/index.html",zone);
        Assert.assertEquals(m.getMountPoint(),new MountPoint("/"));

        m = _mounter.getModule("/blah/",zone);
        Assert.assertEquals(m.getMountPoint(),new MountPoint("/blah/"));

        m = _mounter.getModule("/blah/index.html",zone);
        Assert.assertEquals(m.getMountPoint(),new MountPoint("/blah/"));

        m = _mounter.getModule("/blah/foo/",zone);
        Assert.assertEquals(m.getMountPoint(),new MountPoint("/blah/"));

        m = _mounter.getModule("/blah/blah/",zone);
        Assert.assertEquals(m.getMountPoint(),new MountPoint("/blah/blah/"));
        
        m = _mounter.getModule("/blah/whatever/blah/blah/blah.html",zone);
        Assert.assertEquals(m.getMountPoint(),new MountPoint("/blah/whatever/blah/"));

        zone = new Zone("blah","http://whatever.com/blah/blah/");

        m = _mounter.getModule("/",zone);
        Assert.assertEquals(m.getMountPoint(), new MountPoint("/ [blah]"));

        m = _mounter.getModule("/index.html",zone);
        Assert.assertEquals(m.getMountPoint(), new MountPoint("/ [blah]"));

        m = _mounter.getModule("/blah/",zone);
        Assert.assertEquals(m.getMountPoint(), new MountPoint("/blah/ [blah]"));

        zone = new Zone("whatever","http://blah.com/whatever/");
        
        m = _mounter.getModule("/blah/",zone);
        Assert.assertEquals(m.getMountPoint(), new MountPoint("/blah/ [whatever]"));
        
        logger.trace("< testMountPoints()");
    }
    
}