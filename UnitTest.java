package gitlet;

import org.junit.Assert;
import ucb.junit.textui;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/** The suite of all JUnit tests for the gitlet package.
 *  @author Jianing Yu
 */
public class UnitTest {

    /** Run the JUnit tests in the loa package. Add xxxTest.class entries to
     *  the arguments of runClasses to run other JUnit tests. */
    public static void main(String[] ignored) {
        textui.runClasses(UnitTest.class);
    }

    @Test
    public void testInit() {
        Utils.join(".gitlet", "git").delete();
        new File(".gitlet").delete();
        Main.main("init");
        Assert.assertTrue(new File(".gitlet").exists());
    }


    @Test
    public void testAdd() {
        Utils.join(".gitlet", "git").delete();
        new File(".gitlet").delete();
        Main.main("init");
        Utils.writeContents(new File("wug.txt"),
                "Hello World".getBytes(StandardCharsets.UTF_8));
        Main.main("add", "wug.txt");
        Assert.assertTrue(new File(".gitlet").exists());
    }

    @Test
    public void testCommit() {
        Utils.join(".gitlet", "git").delete();
        new File(".gitlet").delete();
        Main.main("init");
        Utils.writeContents(new File("wug.txt"),
                "Hello World".getBytes(StandardCharsets.UTF_8));
        Main.main("add",  "wug.txt");
        Main.main("commit", "wug.txt");
        Assert.assertTrue(new File(".gitlet").exists());
    }

}


