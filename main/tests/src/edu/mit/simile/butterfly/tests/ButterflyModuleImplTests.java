package edu.mit.simile.butterfly.tests;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;

import edu.mit.simile.butterfly.ButterflyModuleImpl;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ButterflyModuleImplTests {
    
    ButterflyModuleImpl SUT;
    File tempDir;
    File firstFolder;
    File secondFolder;
    File textFile;
    File testFile;
    
    @BeforeMethod
    public void setUp() throws IOException {
        SUT = new ButterflyModuleImpl();
        tempDir = Files.createTempDirectory("ButterflyModuleImplTests").toFile();
        tempDir.deleteOnExit();
        firstFolder = new File(tempDir, "first_folder");
        firstFolder.mkdir();
        secondFolder = new File(tempDir, "other_folder");
        secondFolder.mkdir();
        textFile = new File(secondFolder, "file.txt");
        textFile.createNewFile();
        testFile = new File(firstFolder, "test.txt");
        testFile.createNewFile();
        SUT.setPath(firstFolder);
    }
    
    @Test
    public void testGetResource() throws MalformedURLException {
        // file exists and is in the expected directory
        Assert.assertEquals(SUT.getResource("test.txt"), testFile.toURI().toURL());
        // file does not exist
        Assert.assertNull(SUT.getResource("does_not_exist.xls"));
        
        // file exists but escapes the expected directory (it would be a security issue to accept it)
        Assert.assertEquals(SUT.getResource("../other_folder/file.txt"), null);
        // we don't support passing full URIs (it would be a security issue to accept reading any resource)
        String fullURI = testFile.toURI().toString();
        Assert.assertTrue(fullURI.startsWith("file:/"));
        Assert.assertEquals(SUT.getResource(fullURI), null);
    }

}
