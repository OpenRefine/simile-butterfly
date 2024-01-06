package edu.mit.simile.butterfly.tests;

import javax.servlet.http.HttpServletRequest;

import org.testng.Assert;
import org.testng.annotations.Test;

import edu.mit.simile.butterfly.Butterfly;

public class ServletAPIExtensionTests extends ButterflyTest {

    HttpServletRequest request;
 
    @Test
    public void testExtensions() {
        request = getRequest("http","localhost","/playground/index.html", "/index.html", "simile.mit.edu", "/playground");
        Assert.assertEquals(Butterfly.getTrueHost(request),"simile.mit.edu");
        Assert.assertEquals(Butterfly.getTrueContextPath(request,false),"/playground");
        Assert.assertEquals(Butterfly.getFullHost(request),"http://simile.mit.edu");
        Assert.assertEquals(Butterfly.getTrueContextPath(request,true),"http://simile.mit.edu/playground");
        Assert.assertEquals(Butterfly.getTrueRequestURI(request,true),"http://simile.mit.edu/playground/index.html");
        Assert.assertEquals(Butterfly.getTrueRequestURI(request,false),"/playground/index.html");

        request = getRequest("http","localhost","/playground/index.html", "/index.html", "simile.mit.edu", "/cmp");
        Assert.assertEquals(Butterfly.getTrueHost(request),"simile.mit.edu");
        Assert.assertEquals(Butterfly.getTrueContextPath(request,false),"/cmp");
        Assert.assertEquals(Butterfly.getFullHost(request),"http://simile.mit.edu");
        Assert.assertEquals(Butterfly.getTrueContextPath(request,true),"http://simile.mit.edu/cmp");
        Assert.assertEquals(Butterfly.getTrueRequestURI(request,true),"http://simile.mit.edu/cmp/index.html");
        Assert.assertEquals(Butterfly.getTrueRequestURI(request,false),"/cmp/index.html");
    }
        
}
