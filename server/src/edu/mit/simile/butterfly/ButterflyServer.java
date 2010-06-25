package edu.mit.simile.butterfly;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.mortbay.component.LifeCycle;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.log.Log;
import org.mortbay.thread.ThreadPool;
import org.mortbay.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ButterflyServer {
    
    static private final String DEFAULT_HOST = "127.0.0.1";
    static private final int DEFAULT_PORT = 8080;
        
    static private int port;
    static private String host;

    public static void main(String[] args) throws Exception {
        
        // tell jetty to use SLF4J for logging instead of its own stuff
        System.setProperty("VERBOSE","false");
        System.setProperty("org.mortbay.log.class","org.mortbay.log.Slf4jLog");
        
        port = Configurations.getInteger("server.port",DEFAULT_PORT);
        host = Configurations.get("server.host",DEFAULT_HOST);

        // set the log verbosity level
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.toLevel(Configurations.get("server.verbosity","info")));

        ButterflyServer server = new ButterflyServer();
        
        server.init(args);
    }

    public void init(String[] args) throws Exception {

        ServerImpl server = new ServerImpl();
        server.init(host,port);
        
        // hook up the signal handlers
        Runtime.getRuntime().addShutdownHook(
            new Thread(new ShutdownSignalHandler(server))
        );
 
        server.join();
    }
}

/* -------------- Server Impl ----------------- */

class ServerImpl extends Server {
    
    final static Logger logger = LoggerFactory.getLogger("server");
        
    private ThreadPoolExecutor threadPool;
    
    public void init(String host, int port) throws Exception {
        logger.info("Starting Server bound to '" + host + ":" + port + "'");

        String memory = Configurations.get("server.memory");
        if (memory != null) logger.info("Max memory size: " + memory);
        
        int maxThreads = Configurations.getInteger("server.queue.size", 30);
        int maxQueue = Configurations.getInteger("server.queue.max_size", 300);
        long keepAliveTime = Configurations.getInteger("server.queue.idle_time", 60);

        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(maxQueue);
        
        threadPool = new ThreadPoolExecutor(maxThreads, maxQueue, keepAliveTime, TimeUnit.SECONDS, queue);

        this.setThreadPool(new ThreadPoolExecutorAdapter(threadPool));
        
        Connector connector = new SocketConnector();
        connector.setPort(port);
        connector.setHost(host);
        connector.setMaxIdleTime(Configurations.getInteger("server.connection.max_idle_time",60000));
        connector.setStatsOn(false);
        this.addConnector(connector);

        File webapp = new File(Configurations.get("server.webapp","engine/webapp"));

        if (!isWebapp(webapp)) {
            webapp = new File("engine/webapp");
            if (!isWebapp(webapp)) {
                webapp = new File("webapp");
                if (!isWebapp(webapp)) {
                    logger.warn("Warning: Failed to find web application at '" + webapp.getAbsolutePath() + "'");
                    System.exit(-1);
                }
            }
        }

        final String contextPath = Configurations.get("server.context_path","/");
        
        logger.info("Initializing context: '" + contextPath + "' from '" + webapp.getAbsolutePath() + "'");
        WebAppContext context = new WebAppContext(webapp.getAbsolutePath(), contextPath);
        context.setMaxFormContentSize(1048576);

        this.setHandler(context);
        this.setStopAtShutdown(true);
        this.setSendServerVersion(true);

        // Enable context autoreloading
        if (Configurations.getBoolean("server.autoreload",false)) {
            scanForUpdates(webapp, context);
        }
        
        // start the server
        this.start();
        
        configure(context);
    }
    
    @Override
    protected void doStop() throws Exception {    
        try {
            // shutdown our scheduled tasks first, if any
            if (threadPool != null) threadPool.shutdown();
            
            // then let the parent stop
            super.doStop();
        } catch (InterruptedException e) {
            // ignore
        }
    }
        
    static private boolean isWebapp(File dir) {
        if (dir == null) return false;
        if (!dir.exists() || !dir.canRead()) return false;
        File webXml = new File(dir, "WEB-INF/web.xml");
        return webXml.exists() && webXml.canRead();
    }
    
    static private void scanForUpdates(final File contextRoot, final WebAppContext context) {
        List<File> scanList = new ArrayList<File>();

        scanList.add(new File(contextRoot, "WEB-INF/web.xml"));
        findFiles(".class", new File(contextRoot, "WEB-INF/classes"), scanList);
        findFiles(".jar", new File(contextRoot, "WEB-INF/lib"), scanList);

        logger.info("Starting autoreloading scanner... ");

        Scanner scanner = new Scanner();
        scanner.setScanInterval(Configurations.getInteger("server.scanner.period",1));
        scanner.setScanDirs(scanList);
        scanner.setReportExistingFilesOnStartup(false);

        scanner.addListener(new Scanner.BulkListener() {
            @SuppressWarnings("unchecked")
            public void filesChanged(List changedFiles) {
                try {
                    logger.info("Stopping context: " + contextRoot.getAbsolutePath());
                    context.stop();

                    logger.info("Starting context: " + contextRoot.getAbsolutePath());
                    context.start();
                    
                    configure(context);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        scanner.start();
    }
    
    static private void findFiles(final String extension, File baseDir, final Collection<File> found) {
        baseDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                if (pathname.isDirectory()) {
                    findFiles(extension, pathname, found);
                } else if (pathname.getName().endsWith(extension)) {
                    found.add(pathname);
                }
                return false;
            }
        });
    }

    // inject configuration parameters in the servlets
    // NOTE: this is done *after* starting the server because jetty might override the init
    // parameters if we set them in the webapp context upon reading the web.xml file    
    static private void configure(WebAppContext context) throws Exception {
        //ServletHolder servlet = context.getServletHandler().getServlet("servlet");
        //servlet.setInitParameter("blah", "blah");
        //servlet.doStart();
    }

}

class Configurations {

    public static String get(final String name) {
        return System.getProperty(name);
    }
    
    public static String get(final String name, final String def) {
        final String val = get(name);
        return (val == null) ? def : val;
    }

    public static boolean getBoolean(final String name, final boolean def) {
        final String val = get(name);
        return (val == null) ? def : Boolean.parseBoolean(val);
    }

    public static int getInteger(final String name, final int def) {
        final String val = get(name);
        try {
            return (val == null) ? def : Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Could not parse '" + val + "' as an integer number.", e);
        }
    }

    public static float getFloat(final String name, final float def) {
        final String val = get(name);
        try {
            return (val == null) ? def : Float.parseFloat(val);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Could not parse '" + val + "' as a floating point number.", e);
        }
    }
    
}

class ShutdownSignalHandler implements Runnable {
    
    private Server _server;

    public ShutdownSignalHandler(Server server) {
        this._server = server;
    }

    public void run() {

        // Tell the server we want to try and shutdown gracefully
        // this means that the server will stop accepting new connections
        // right away but it will continue to process the ones that
        // are in execution for the given timeout before attempting to stop
        // NOTE: this is *not* a blocking method, it just sets a parameter
        //       that _server.stop() will rely on
        _server.setGracefulShutdown(3000);

        try {
            _server.stop();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}
    
class ThreadPoolExecutorAdapter implements ThreadPool, LifeCycle {

    private ThreadPoolExecutor executor;

    public ThreadPoolExecutorAdapter(ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    public boolean dispatch(Runnable job) {
        try {
            executor.execute(job);
            return true;
        } catch (RejectedExecutionException e) {
            Log.warn(e);
            return false;
        }
    }

    public int getIdleThreads() {
        return executor.getPoolSize() - executor.getActiveCount();
    }

    public int getThreads() {
        return executor.getPoolSize();
    }

    public boolean isLowOnThreads() {
        return executor.getActiveCount() >= executor.getMaximumPoolSize();
    }

    public void join() throws InterruptedException {
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    public boolean isFailed() {
        return false;
    }

    public boolean isRunning() {
        return !executor.isTerminated() && !executor.isTerminating();
    }

    public boolean isStarted() {
        return !executor.isTerminated() && !executor.isTerminating();
    }

    public boolean isStarting() {
        return false;
    }

    public boolean isStopped() {
        return executor.isTerminated();
    }

    public boolean isStopping() {
        return executor.isTerminating();
    }

    public void start() throws Exception {
        if (executor.isTerminated() || executor.isTerminating()
                || executor.isShutdown()) {
            throw new IllegalStateException("Cannot restart");
        }
    }

    public void stop() throws Exception {
        executor.shutdown();
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    public void addLifeCycleListener(Listener listener) {
        System.err.println("we should implement this!");
    }

    public void removeLifeCycleListener(Listener listener) {
        System.err.println("we should implement this!");
    }
}
    