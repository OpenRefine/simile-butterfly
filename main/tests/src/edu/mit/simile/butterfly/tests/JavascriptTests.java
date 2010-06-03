package edu.mit.simile.butterfly.tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.tools.shell.Global;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class JavascriptTests extends ButterflyTest {

    public static final MyContextFactory _contextFactory = new MyContextFactory();
    public static final Global _global = new Global();

    static {
        _global.init(_contextFactory);
    }

    @BeforeTest
    public void init() {
        logger = LoggerFactory.getLogger(this.getClass());
    }
    
    @Test 
    public void testRhino() throws Exception {
        Interpreter i = new Interpreter();
        Object o = null;
        
        i.src = "simple.js";
        o = _contextFactory.call(i);
        Assert.assertEquals("1blahblahblahblahblah", Context.toString(o));
    }
    
    public class Interpreter implements ContextAction {
        String src;

        public Object run(Context context) {
            BufferedReader reader = null;
            URL url = this.getClass().getResource(src);
            try {
                Scriptable scope = context.initStandardObjects();
                if (url != null) {
                    reader = new BufferedReader(new InputStreamReader(url.openStream()));
                    Script script = context.compileReader(reader, url.toString(), 1, null);
                    Object o = script.exec(context, scope);
                    return Context.toString(o);
                } else {
                    throw new RuntimeException("could not find '" + src + "'");
                }
            } catch (Exception e) {
                logger.error("Error found while executing the script '" + url + "'",e);
            } finally {
                try {
                    if (reader != null) reader.close();
                } catch (IOException ioe) {
                    logger.error("could not close reader", ioe);
                }
            }
            return null;
        }
    }
    
    public static class MyContextFactory extends ContextFactory {

       private boolean strictMode;
       private int languageVersion;
       private int optimizationLevel;
       private ErrorReporter errorReporter;

       protected boolean hasFeature(Context cx, int featureIndex) {
           switch (featureIndex) {
             case Context.FEATURE_STRICT_VARS:
             case Context.FEATURE_STRICT_EVAL:
               return strictMode;
           }
           return super.hasFeature(cx, featureIndex);
       }

       protected void onContextCreated(Context cx) {
           cx.setLanguageVersion(languageVersion);
           cx.setOptimizationLevel(optimizationLevel);
           if (errorReporter != null) {
               cx.setErrorReporter(errorReporter);
           }
           super.onContextCreated(cx);
       }

       public void setStrictMode(boolean flag) {
           checkNotSealed();
           this.strictMode = flag;
       }

       public void setLanguageVersion(int version) {
           Context.checkLanguageVersion(version);
           checkNotSealed();
           this.languageVersion = version;
       }

       public void setOptimizationLevel(int optimizationLevel) {
           Context.checkOptimizationLevel(optimizationLevel);
           checkNotSealed();
           this.optimizationLevel = optimizationLevel;
       }

       public void setErrorReporter(ErrorReporter errorReporter) {
           if (errorReporter == null) throw new IllegalArgumentException();
           this.errorReporter = errorReporter;
       }
   }
}