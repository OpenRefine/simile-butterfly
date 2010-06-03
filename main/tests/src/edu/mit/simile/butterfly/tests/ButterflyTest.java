package edu.mit.simile.butterfly.tests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.testng.annotations.BeforeSuite;

import edu.mit.simile.butterfly.Butterfly;

public class ButterflyTest {

    transient protected Logger logger;

    @BeforeSuite
    public void init() {
        System.setProperty("log4j.configuration", "tests.log4j.properties");
    }
            
    protected HttpServletRequest getRequest(String scheme, String host, String requestURI, String pathInfo, String originalHost, String originalContext) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getScheme()).thenReturn(scheme);
        when(request.getServerName()).thenReturn(host);
        when(request.getHeader(Butterfly.HOST_HEADER)).thenReturn(originalHost);
        when(request.getHeader(Butterfly.CONTEXT_HEADER)).thenReturn(originalContext);
        when(request.getRequestURI()).thenReturn(requestURI);
        when(request.getPathInfo()).thenReturn(pathInfo);
        return request;
    }
    
}
