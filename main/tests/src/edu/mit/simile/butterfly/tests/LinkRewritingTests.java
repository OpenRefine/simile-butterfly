package edu.mit.simile.butterfly.tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import edu.mit.simile.butterfly.Butterfly;
import edu.mit.simile.butterfly.ButterflyModule;
import edu.mit.simile.butterfly.ButterflyModuleImpl;
import edu.mit.simile.butterfly.LinkRewriter;
import edu.mit.simile.butterfly.MountPoint;

public class LinkRewritingTests extends Butterfly {

    private static final long serialVersionUID = 1L;

    transient Logger logger;
    
    @BeforeTest
    public void init() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    private URL _outputURL;
    private URL _inputURL;
    
    @BeforeTest
    public void setup() { 
        logger.trace("> setup()");

        ButterflyModule examples = new ButterflyModuleImpl();
        examples.setName("examples");
        examples.setMountPoint(new MountPoint("/samples/"));
        _modulesByName.put("examples", examples);
        examples.setModules(_modulesByName);

        ButterflyModule classic = new ButterflyModuleImpl();
        classic.setName("classic");
        classic.setMountPoint(new MountPoint("/style/"));
        _modulesByName.put("classic", classic);
        classic.setModules(_modulesByName);
        examples.setDependency("skin", classic);

        ButterflyModule tests = new ButterflyModuleImpl();
        tests.setName("tests");
        tests.setMountPoint(new MountPoint("/tests/"));
        tests.setModules(_modulesByName);
        _modulesByName.put("tests", tests);
        
        _outputURL = this.getClass().getResource("output.html");
        Assert.assertNotNull(_outputURL);

        _inputURL = this.getClass().getResource("input.html");
        Assert.assertNotNull(_inputURL);
        
        logger.trace("< setup()");
    }
    
    private int rounds = 10;

    @Test
    public void copy() throws Exception {
        for (int i = rounds; i >= 0; i--) {
            copy(1 << i);
        }
    }

    @Test 
    public void rewrite() throws Exception {
        for (int i = rounds; i >= 0; i--) {
            rewrite(1 << i, true);
        }
    }
        
    private void copy(int size) throws Exception {
        logger.trace("> copy({})", size);

        Reader input = null;
        Writer output = null;
        StringWriter str = new StringWriter();
        try {
            input = new BufferedReader(new InputStreamReader(_outputURL.openStream(), "UTF-8"));
            output = new PrintWriter(str);
            IOUtils.copy(input, output);
        } finally {
            if (input != null) input.close();
            if (output != null) output.close();
        }
        String expected = str.toString();

        str = new StringWriter();
        try {
            input = new BufferedReader(new InputStreamReader(_outputURL.openStream(), "UTF-8"));
            output = new LoggingPrintWriter(str);
            copy(input, output, size);
        } finally {
            if (input != null) input.close();
            if (output != null) output.close();
        }
        String actual = str.toString();
        
        Assert.assertEquals(expected, actual);
        
        logger.trace("< copy({})", size);
    }

    private void rewrite(int size, boolean variableSize) throws Exception {
        logger.trace("> rewrite({},{})", size, variableSize);
        Reader input = null;
        Writer output = null;
        StringWriter str = new StringWriter();
        try {
            input = new BufferedReader(new InputStreamReader(_outputURL.openStream(), "UTF-8"));
            output = new PrintWriter(str);
            IOUtils.copy(input, output);
        } finally {
            if (input != null) input.close();
            if (output != null) output.close();
        }
        String expected = str.toString();
        logger.debug(expected);

        input = null;
        output = null;
        str = new StringWriter();
        try {
            input = new BufferedReader(new InputStreamReader(_inputURL.openStream(), "UTF-8"));
            output = new LinkRewriter(new LoggingPrintWriter(str), _modulesByName.get("examples"));
            if (variableSize) {
                copy(input, output, size);
            } else {
                IOUtils.copy(input, output);
            }
        } finally {
            if (input != null) input.close();
            if (output != null) output.close();
        }
        String actual = str.toString();
        logger.debug(actual);
        
        Assert.assertEquals(expected, actual);
        logger.trace("< rewrite({},{})", size, variableSize);
    }
    
    private Random random = new Random();
    
    private void copy(Reader input, Writer output, int maxSize) throws IOException {
        while (true) {
            int size = random.nextInt(maxSize);
            if (size == 0) size = 1;
            char[] buffer = new char[size];
            int length = input.read(buffer);
            if (length > -1) {
                output.write(buffer,0,length);
            } else {
                break;
            }
        }
    }
    
    private class LoggingPrintWriter extends PrintWriter {
        public LoggingPrintWriter(Writer out) {
            super(out);
        }
        public void write(char[] buf, int off, int len) {
            String str = new String(buf, off, len);
            logger.debug("written: " + str);
            super.write(buf, off, len);
        }
    }
}