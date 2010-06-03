package edu.mit.simile.butterfly.tests;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import edu.mit.simile.butterfly.ButterflyMounter;

public class ZoneTests extends ButterflyTest {

    @BeforeTest
    public void init() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    // SUT
    ButterflyMounter mounter;
    
    // mocks
    HttpServletRequest request;

    @BeforeTest 
    public void setup() { 
        mounter = new ButterflyMounter();

        mounter.registerZone(ButterflyMounter.ROOT_ZONE,"/main/");
        mounter.registerZone("bar","/bar/");
        mounter.registerZone("foo","http://foo.com/main/");
    }
    
    @AfterMethod
    public void tearDown() {
        mounter = null;
        request = null;
    }
    
    @Test 
    public void testZonesResolution() throws Exception {
        logger.trace("> testZonesResolution()");

        request = getRequest("http","localhost","/main/index.html", "/index.html", "localhost", "/main");
        Assert.assertEquals(mounter.getZone(request).getName(),ButterflyMounter.ROOT_ZONE);

        request = getRequest("http","localhost","/main/whatever/index.html", "/whatever/index.html", "localhost", "/bar");
        Assert.assertEquals(mounter.getZone(request).getName(),"bar");

        request = getRequest("http","localhost","/main/whatever/index.html", "/whatever/index.html", "foo.com", "/bar");
        Assert.assertEquals(mounter.getZone(request).getName(),"bar");

        request = getRequest("http","localhost","/main/whatever/index.html", "/whatever/index.html", "foo.com", "/main");
        Assert.assertEquals(mounter.getZone(request).getName(),"foo");

        logger.trace("< testZonesResolution()");
    }   
            
}
