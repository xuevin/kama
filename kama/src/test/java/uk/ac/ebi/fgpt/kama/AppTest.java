package uk.ac.ebi.fgpt.kama;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Test;

import uk.ac.ebi.ontocat.OntologyServiceException;

/**
 * Unit test for simple App.
 */
public class AppTest {
  
  @Test
  public void testFileReader() {
    File inputFile;
    try {
      inputFile = new File(getClass().getClassLoader().getResource("accessiontest.txt").toURI());
      BufferedReader br = new BufferedReader(new FileReader(inputFile));
      String text;
      while ((text = br.readLine()) != null) {
        System.out.println(text);
      }
    } catch (URISyntaxException e) {
      e.printStackTrace();
      fail();
    } catch (IOException e) {
      e.printStackTrace();
      fail();
    }
  }
  
  @Test
  public void testCreateFileTest() {
    try {
      // Create file
      BufferedWriter output;
      
      File file = new File("target/write.txt");
      output = new BufferedWriter(new FileWriter(file));
      output.write("Hello World");
      output.close();
      System.out.println("Your file has been written");
      assertTrue(true);
      
    } catch (Exception e) {// Catch exception if any
      System.err.println("Error: " + e.getMessage());
      fail();
    }
  }
  
  @Test
  public void testMain() throws OntologyServiceException, IOException, MonqException {
    String[] args = new String[7];
    args[0] = "-input";
    args[1] = "src/test/resources/accessiontest.txt";
    args[2] = "-output";
    args[3] = "target/output1.txt";
    args[4] = "-ids";
    args[5] = "src/test/resources/efo.txt";
    args[6] = "-s";
    App.main(args);
  }
  
  @Test
  public void testMain2() throws OntologyServiceException, IOException, MonqException {
    String[] args2 = new String[6];
    args2[0] = "-input";
    args2[1] = "src/test/resources/accessiontest.txt";
    args2[2] = "-output";
    args2[3] = "target/output2.txt";
    args2[4] = "-ids";
    args2[5] = "src/test/resources/efo.txt";
    App.main(args2);
  }
  
  @Test
  public void testMain3() throws OntologyServiceException, IOException, MonqException {
    String[] args2 = new String[7];
    args2[0] = "-input";
    args2[1] = "src/test/resources/accessiontest.txt";
    args2[2] = "-output";
    args2[3] = "target/output3.txt";
    args2[4] = "-ids";
    args2[5] = "src/test/resources/efo.txt";
    args2[6] = "-x";
    App.main(args2);
  }
  
  @Test
  public void testMain4() throws OntologyServiceException, IOException, MonqException {
    String[] args2 = new String[9];
    args2[0] = "-input";
    args2[1] = "src/test/resources/accessiontest.txt";
    args2[2] = "-output";
    args2[3] = "target/output4.txt";
    args2[4] = "-ids";
    args2[5] = "src/test/resources/celllineobo.txt";
    args2[6] = "-owlfile";
    args2[7] = "src/test/resources/CELL_SP_DIS_ORG_5_05_09_v1.0.obo";
    args2[8] = "-s";
    App.main(args2);
  }

  @Test
  public void testOnTheExperimentsThatFailed() throws OntologyServiceException, IOException, MonqException {
    String[] args2 = new String[8];
    args2[0] = "-input";
    args2[1] = "src/test/resources/failedAccessions.txt";
    args2[2] = "-output";
    args2[3] = "target/output5.txt";
    args2[4] = "-ids";
    args2[5] = "src/test/resources/efoHead.txt";
    args2[6] = "-owlfile";
    args2[7] = "src/test/resources/efo_2_14.owl";
//    args2[8] = "-s";
    App.main(args2);
  }
}
