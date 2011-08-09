package uk.ac.ebi.fgpt.kama;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import monq.ie.Term2Re;
import monq.jfa.CompileDfaException;
import monq.jfa.Dfa;
import monq.jfa.DfaRun;
import monq.jfa.Nfa;
import monq.jfa.ReSyntaxException;
import monq.jfa.actions.Printf;

import org.apache.commons.net.ftp.FTPClient;
import org.junit.Before;
import org.junit.Test;

import uk.ac.ebi.fgpt.kama.Kama.Scope;
import uk.ac.ebi.ontocat.OntologyServiceException;

public class KamaTest {
  private List<String> listOfOntologyIdsForBlood;
  private List<String> listOfOntologyIdsForNull;
  
  @Before
  public void initialize() {
    listOfOntologyIdsForBlood = new ArrayList<String>();
    listOfOntologyIdsForNull = new ArrayList<String>();
    
    listOfOntologyIdsForBlood.add("EFO_0000798");
    listOfOntologyIdsForNull.add("NOT_AN_ACCESSION");
    
  }
  
  @Test
  public void testIfKamaCanBeInstantiated() throws OntologyServiceException {
    Kama testKama = new Kama();
    assertNotNull(testKama);
  }
  
  @Test
  public void testIfKamaCanBeInstantiatedFromEFO() throws OntologyServiceException {
    File owlFile;
    try {
      owlFile = new File(getClass().getClassLoader().getResource("EFO_inferred_v142.owl").toURI());
      Kama testKama = new Kama(owlFile);
      assertNotNull(testKama);
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
  }
  
  @Test
  public void testIfKamaCanBeInstantiatedFromOBO() throws OntologyServiceException {
    File owlFile;
    try {
      owlFile = new File(getClass().getClassLoader().getResource("CELL_SP_DIS_ORG_5_05_09_v1.0.obo").toURI());
      Kama testKama = new Kama(owlFile);
      assertNotNull(testKama);
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  @Test
  public void testGetChildrenOfEFOAccession() throws OntologyServiceException {
    Kama testKama = new Kama();
    String[] listOfChildren = testKama.getDictionaryOfTermsFromOntologyIds(listOfOntologyIdsForBlood);
    boolean found = false;
    for (int i = 0; i < listOfChildren.length; i++) {
      if (listOfChildren[i].equals("thymus")) {
        found = true;
        break;
      }
    }
    if (found == false) {
      fail("Could not find thymus");
    }
  }
  
  @Test
  public void testIfMonqCanFilterFromString() {
    // It seems to only recognize dict words by those which have a space.
    
    String inputText = "blood had to run to the Blood bank to withdraw blood.";
    String dictWord = "blood";
    String regEx = Term2Re.convert(dictWord);
    
    // Add one pattern action pair to an Nfa.
    
    try {
      Nfa nfa = new Nfa(regEx, new Printf("%0"));
      
      // Compile into the Dfa, specify that all text not matching any
      // regular expression shall be copied from input to output
      Dfa dfa = nfa.compile(DfaRun.UNMATCHED_DROP);
      
      // get a machinery (DfaRun) to operate the Dfa
      DfaRun r = new DfaRun(dfa);
      
      // Print out only the filtered text
      assertEquals("blood Blood blood.", r.filter(inputText));
      
    } catch (ReSyntaxException e) {
      e.printStackTrace();
      fail();
    } catch (CompileDfaException e) {
      e.printStackTrace();
      fail();
    } catch (IOException e) {
      e.printStackTrace();
      fail();
    }
  }
  
  @Test
  public void assertPassageIsFromBlood() throws OntologyServiceException {
    String passage = "the thymus ran far into blood";
    Kama kama = new Kama();
    String[] listOfChildren = kama.getDictionaryOfTermsFromOntologyIds(listOfOntologyIdsForBlood);
    
    boolean found = false;
    for (String dictWord : listOfChildren) {
      // First make regex
      String regEx = Term2Re.convert(dictWord);
      
      try {
        Nfa nfa = new Nfa(regEx, new Printf("%0"));
        
        // Compile into the Dfa, specify that all text not matching any
        // regular expression shall be copied from input to output
        Dfa dfa = nfa.compile(DfaRun.UNMATCHED_DROP);
        
        // get a machinery (DfaRun) to operate the Dfa
        DfaRun r = new DfaRun(dfa);
        
        // Print out only the filtered text
        if (r.filter(passage).length() != 0) {
          found = true;
          break;
        }
      } catch (ReSyntaxException e) {
        e.printStackTrace();
        fail();
      } catch (CompileDfaException e) {
        e.printStackTrace();
        fail();
      } catch (IOException e) {
        e.printStackTrace();
        fail();
      }
    }
    if (!found) {
      fail();
    }
  }
  
  @Test
  public void assertPassageIsNotFromBlood() throws OntologyServiceException {
    String passage = "the happy chicken ran far";
    Kama kama = new Kama();
    String[] listOfChildren = kama.getDictionaryOfTermsFromOntologyIds(listOfOntologyIdsForBlood);
    
    boolean found = false;
    for (String dictWord : listOfChildren) {
      // First make regex
      String regEx = Term2Re.convert(dictWord);
      
      try {
        Nfa nfa = new Nfa(regEx, new Printf("%0"));
        
        // Compile into the Dfa, specify that all text not matching any
        // regular expression shall be copied from input to output
        Dfa dfa = nfa.compile(DfaRun.UNMATCHED_DROP);
        
        // get a machinery (DfaRun) to operate the Dfa
        DfaRun r = new DfaRun(dfa);
        
        // Print out only the filtered text
        if (r.filter(passage).length() != 0) {
          found = true;
          break;
        }
      } catch (ReSyntaxException e) {
        e.printStackTrace();
        fail();
      } catch (CompileDfaException e) {
        e.printStackTrace();
        fail();
      } catch (IOException e) {
        e.printStackTrace();
        fail();
      }
    }
    if (found) {
      fail("Blood was found in passage when there was no mention of it!");
    }
  }
  
  @Test
  public void testPassageContainsEFO() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    assertEquals(false, kama.getIfPassageContainsOntologyTerm("there is no reference to it here",
      listOfOntologyIdsForBlood));
    assertEquals(true, kama.getIfPassageContainsOntologyTerm("thymus is found", listOfOntologyIdsForBlood));
    assertEquals(false, kama.getIfPassageContainsOntologyTerm("thymus", listOfOntologyIdsForBlood)); // Requires
    // a
    // space
    // or period
    // following
  }
  
  @Test
  public void testFTP() {
    
    FTPClient client = new FTPClient();
    FileOutputStream fos = null;
    File temp = null;
    String arrayExpressFtp = "ftp.ebi.ac.uk";
    String arrayExpressFtpPath = "/pub/databases/microarray/data/experiment/";
    String testGeoFile = "GEOD/E-GEOD-10001/E-GEOD-10001.sdrf.txt";
    try {
      temp = File.createTempFile("kama_", ".tmp");
      temp.deleteOnExit();
      
      fos = new FileOutputStream(temp);
      
      client.connect(arrayExpressFtp);
      client.login("anonymous", "");
      if (client.getReplyString().contains("Login successful")) {
        System.out.println(client.getReplyString());
      } else {
        fail("Login Failed");
      }
      
      client.retrieveFile(arrayExpressFtpPath + testGeoFile, fos);
      
      System.out.println(client.getReplyString());
      
    } catch (SocketException e) {
      e.printStackTrace();
      fail();
    } catch (IOException e) {
      e.printStackTrace();
      fail();
    } finally {
      try {
        if (fos != null) {
          fos.close();
          client.disconnect();
          
          if (temp != null) {
            
            BufferedReader br = new BufferedReader(new FileReader(temp));
            String text;
            while ((text = br.readLine()) != null) {
              System.out.println(text);
            }
          } else {
            fail();
          }
        } else {
          fail();
        }
      } catch (IOException e) {
        e.printStackTrace();
        fail();
      }
    }
    
  }
  
  @Test
  public void testGetHashMapForListofIDF() throws OntologyServiceException, MonqException {
    ArrayList<String> accessionIDS = new ArrayList<String>();
    
    // Noticed weird error with this accession ID
    
    accessionIDS.add("E-GEOD-24734"); // Title contains thymus but will fail because FTP is broken
    accessionIDS.add("E-TABM-721"); // Title contains thymus
    accessionIDS.add("E-MEXP-2895"); // Should return false
    Kama kama = new Kama();
    Map<String,Boolean> actual = kama.getTrueFalseMapForListOfAccessions(accessionIDS, Scope.idf,
      listOfOntologyIdsForBlood);
    assertNotNull(actual);
    
    assertEquals(true, actual.get("E-TABM-721")); // Should pass because title contains thymus
    // assertEquals(true, actual.get("E-GEOD-24734")); // Should return true
    assertEquals(false, actual.get("E-MEXP-2895")); // Should fail because there is no reference to blood
  }
  
  @Test
  public void testGetHashMapForListofSDRF() throws OntologyServiceException, MonqException {
    ArrayList<String> accessionIDS = new ArrayList<String>();
    accessionIDS.add("E-GEOD-24734"); // Title contains thymus but will fail because FTP is broken
    accessionIDS.add("E-TABM-721"); // Title contains thymus
    accessionIDS.add("E-MEXP-2895"); // Should return false
    
    Kama kama = new Kama();
    Map<String,Boolean> actual = kama.getTrueFalseMapForListOfAccessions(accessionIDS, Scope.sdrf,
      listOfOntologyIdsForBlood);
    assertNotNull(actual);
    
    assertEquals(true, actual.get("E-TABM-721")); // Should pass because sdrf contains thymus
    // assertEquals(false, actual.get("E-GEOD-24734")); //This sample contains nothign about blood... but idf
    // does. it is also broken
    assertEquals(false, actual.get("E-MEXP-2895")); // Should fail because there is no reference to blood
    
  }
  
  @Test
  public void testIfAppUsesParentTerm() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    assertEquals(true, kama.getIfPassageContainsOntologyTerm("haemopoietic system is included",
      listOfOntologyIdsForBlood));
  }
  
  @Test
  public void testThreeFieldsForTrueFalseFetches() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    ArrayList<String> accessionIDS = new ArrayList<String>();
    accessionIDS.add("E-GEOD-24734"); // Title contains thymus but will fail because FTP is broken
    accessionIDS.add("E-TABM-721"); // Title contains thymus
    accessionIDS.add("E-MEXP-2895"); // Should return false
    
    Map<String,Boolean> sdrf = kama.getTrueFalseMapForListOfAccessions(accessionIDS, Scope.sdrf,
      listOfOntologyIdsForBlood);
    Map<String,Boolean> idf = kama.getTrueFalseMapForListOfAccessions(accessionIDS, Scope.idf,
      listOfOntologyIdsForBlood);
    
    assertEquals(true, idf.get("E-TABM-721")); // Should pass because title contains thymus
    // assertEquals(true, idf.get("E-GEOD-24734")); // Should return true
    assertEquals(false, idf.get("E-MEXP-2895")); // Should fail because there is no reference to blood
    
    assertEquals(true, sdrf.get("E-TABM-721")); // Should pass because sdrf contains thymus
    // assertEquals(false, sdrf.get("E-GEOD-24734")); //This sample contains nothign about blood... but idf
    // does. it is also broken
    assertEquals(false, sdrf.get("E-MEXP-2895")); // Should fail because there is no reference to blood
  }
  
  @Test
  public void getTrueFalseHashMapForListOfAccessions_both() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    ArrayList<String> accessionIDS = new ArrayList<String>();
    accessionIDS.add("E-GEOD-24734"); // Title contains thymus but will fail because FTP is broken
    accessionIDS.add("E-TABM-721"); // Title contains thymus
    accessionIDS.add("E-MEXP-2895"); // Should return false
    
    Map<String,Boolean> both = kama.getTrueFalseMapForListOfAccessions(accessionIDS, Scope.both,
      listOfOntologyIdsForBlood);
    assertEquals(true, both.get("E-TABM-721")); // Should pass because title contains thymus
    // assertEquals(true, both.get("E-GEOD-24734")); // Should return true
    assertEquals(false, both.get("E-MEXP-2895")); // Should fail because there is no reference to blood
  }
  
  @Test
  public void testGetTrueFalseHashMapForExperimentCELFiles() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    assertEquals(false, kama.getTrueFalseMapForExperimentCELFiles("E-GEOD-26672", listOfOntologyIdsForBlood)
        .get("GSM656451.CEL"));
    assertEquals(null, kama.getTrueFalseMapForExperimentCELFiles("E-TABM-721", listOfOntologyIdsForBlood)
        .get("GSM656451.CEL"));
  }
  
  @Test
  public void testPassageCountOfEFO() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    assertEquals(2, kama.getTotalCountOfRelatedOntologyTermsInPassage("blood blood blood",
      listOfOntologyIdsForBlood));
    assertEquals(3, kama.getTotalCountOfRelatedOntologyTermsInPassage("blood blood blood.",
      listOfOntologyIdsForBlood));
    assertEquals(4, kama.getTotalCountOfRelatedOntologyTermsInPassage("blood blood blood thymus ",
      listOfOntologyIdsForBlood));
    assertEquals(4, kama.getTotalCountOfRelatedOntologyTermsInPassage("blood blood \n blood thymus ",
      listOfOntologyIdsForBlood));
    
  }
  
  @Test
  public void testGetCountOfEachTermInExperiment() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    // In this experiment thymus and spleen are the key words
    // IDF: Spleen:3 Thymus:3
    // SDRF: Thymus: 212, spleen: 105
    
    // To make sure that Scope.both works
    assertEquals(107, kama
        .getCountOfEachTermInExperiment("E-TABM-721", Scope.sdrf, listOfOntologyIdsForBlood).get("thymus")
        .intValue());
    assertEquals(3, kama.getCountOfEachTermInExperiment("E-TABM-721", Scope.idf, listOfOntologyIdsForBlood)
        .get("thymus").intValue());
    assertEquals(110, kama
        .getCountOfEachTermInExperiment("E-TABM-721", Scope.both, listOfOntologyIdsForBlood).get("thymus")
        .intValue());
    
    ArrayList<String> accessionIDS = new ArrayList<String>();
    accessionIDS.add("E-TABM-721");
    
    assertEquals(6, kama.getCountMapForListOfAccessions(accessionIDS, Scope.idf, listOfOntologyIdsForBlood)
        .get("E-TABM-721").intValue());
    assertEquals(3, kama.getCountOfEachTermInExperiment("E-TABM-721", Scope.idf, listOfOntologyIdsForBlood)
        .get("spleen").intValue());
    assertEquals(3, kama.getCountOfEachTermInExperiment("E-TABM-721", Scope.idf, listOfOntologyIdsForBlood)
        .get("thymus").intValue());
    
    assertEquals(212, kama
        .getCountMapForListOfAccessions(accessionIDS, Scope.sdrf, listOfOntologyIdsForBlood)
        .get("E-TABM-721").intValue());
    assertEquals(105, kama
        .getCountOfEachTermInExperiment("E-TABM-721", Scope.sdrf, listOfOntologyIdsForBlood).get("spleen")
        .intValue());
    assertEquals(107, kama
        .getCountOfEachTermInExperiment("E-TABM-721", Scope.sdrf, listOfOntologyIdsForBlood).get("thymus")
        .intValue());
  }
  
  @Test
  public void testGetCountHashMapForListOfAccessions() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    ArrayList<String> accessionIDS = new ArrayList<String>();
    accessionIDS.add("E-TABM-721");
    accessionIDS.add("E-GEOD-9171");
    assertEquals(218, kama
        .getCountMapForListOfAccessions(accessionIDS, Scope.both, listOfOntologyIdsForBlood)
        .get("E-TABM-721").intValue());
    assertEquals(0, kama.getCountMapForListOfAccessions(accessionIDS, Scope.both, listOfOntologyIdsForBlood)
        .get("E-GEOD-9171").intValue());
    
  }
  
  @Test
  public void testgetChildrenOfEFOAccessionPlusItself_EFOIsNotFound() throws OntologyServiceException {
    Kama kama = new Kama();
    assertEquals(0, kama.getDictionaryOfTermsFromOntologyIds(listOfOntologyIdsForNull).length);
  }
  
  @Test
  public void testWhatHappensWhenFileDoesNotHaveArrayDataFileColumn() throws OntologyServiceException,
                                                                     MonqException {
    // What happens when a file has does not have a ArrayDataFile
    Kama kama = new Kama();
    Map<String,Boolean> map = kama.getTrueFalseMapForExperimentCELFiles("E-MTAB-502",
      listOfOntologyIdsForBlood);
    assertEquals(0, map.size());
    
  }
  
  @Test
  public void testGetCountOfEachTermInExperimentAsString() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    assertEquals("spleen:108;thymus:110;", kama.getCountOfEachTermInExperimentAsString("E-TABM-721",
      Scope.both, listOfOntologyIdsForBlood));
  }
  
  @Test
  public void testGetCountHashMapForExperimentCELFiles() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    Map<String,Integer> hashMap2 = kama.getCountMapForExperimentCELFiles("E-GEOD-11941",
      listOfOntologyIdsForBlood);
    int sum2 = 0;
    for (String key : hashMap2.keySet()) {
      sum2 += hashMap2.get(key).intValue();
    }
    ArrayList<String> listOfExperimentAccessions = new ArrayList<String>();
    listOfExperimentAccessions.add("E-GEOD-11941");
    assertEquals(sum2, kama.getCountMapForListOfAccessions(listOfExperimentAccessions, Scope.sdrf,
      listOfOntologyIdsForBlood).get("E-GEOD-11941").intValue());
    
    Map<String,Integer> hashMap = kama.getCountMapForExperimentCELFiles("E-TABM-721",
      listOfOntologyIdsForBlood);
    int sum = 0;
    for (String key : hashMap.keySet()) {
      sum += hashMap.get(key).intValue();
    }
    ArrayList<String> listOfExperimentAccessions1 = new ArrayList<String>();
    listOfExperimentAccessions1.add("E-TABM-721");
    assertEquals(sum, kama.getCountMapForListOfAccessions(listOfExperimentAccessions1, Scope.sdrf,
      listOfOntologyIdsForBlood).get("E-TABM-721").intValue());
  }
  
  @Test
  public void testGetCountOfEachTermPerSample() throws OntologyServiceException, MonqException {
    Kama kama = new Kama();
    Map<String,Map<String,Integer>> test = kama.getCountOfEachTermPerSample("E-TABM-721",
      listOfOntologyIdsForBlood);
    for (String sample : test.keySet()) {
      System.out.println(sample);
      Map<String,Integer> row = test.get(sample);
      for (String term : row.keySet()) {
        System.out.print(term + row.get(term).intValue() + ";");
      }
      System.out.println();
    }
    assertEquals(test.get("859_0150_Burt_791_thymuspool35.CEL").get("spleen").intValue(), 1);
    assertEquals(test.get("859_0150_Burt_791_thymuspool35.CEL").get("thymus").intValue(), 2);
  }
  
  @Test
  public void testGetCountOfEachTermPerExperiment() throws OntologyServiceException, MonqException {
    ArrayList<String> listOfOntologyIds = new ArrayList<String>();
    listOfOntologyIds.add("EFO_0000403");
    Kama kama = new Kama();
    Map<String,Integer> hash = kama.getCountOfEachTermInExperiment("E-GEOD-23501", Scope.idf,
      listOfOntologyIds);
    assertEquals(4, hash.get("diffuse large B-cell lymphoma").intValue());
  }
  
  @Test
  public void showThatSumOfCountPerTerm_Equals_SumOfAllTermsFoundPerExperiment() throws OntologyServiceException,
                                                                                MonqException {
    ArrayList<String> listOfOntologyIds = new ArrayList<String>();
    listOfOntologyIds.add("EFO_0000403");
    
    Kama kama = new Kama();
    Map<String,Integer> hash = kama.getCountOfEachTermInExperiment("E-GEOD-23501", Scope.idf,
      listOfOntologyIds);
    int sum = 0;
    for (String key : hash.keySet()) {
      sum += hash.get(key).intValue();
    }
    
    ArrayList<String> listOfExperimentAccessions = new ArrayList<String>();
    listOfExperimentAccessions.add("E-GEOD-23501");
    Map<String,Integer> hashOfAccessionToCount = kama.getCountMapForListOfAccessions(
      listOfExperimentAccessions, Scope.idf, listOfOntologyIds);
    
    assertEquals(sum, hashOfAccessionToCount.get("E-GEOD-23501").intValue());
  }
  
  @Test
  public void showThatAssayCountsAreCorrect() throws OntologyServiceException {
    Kama kama = new Kama();
    
    ArrayList<String> listOfExperimentAccessions = new ArrayList<String>();
    listOfExperimentAccessions.add("E-GEOD-13367");
    listOfExperimentAccessions.add("E-GEOD-23501");
    listOfExperimentAccessions.add("E-TABM-721");
    Map<String,Integer> hash = kama.getCountOfAssaysPerExperiment(listOfExperimentAccessions);
    assertEquals(56, hash.get("E-GEOD-13367").intValue());
    assertEquals(69, hash.get("E-GEOD-23501").intValue());
    assertEquals(71, hash.get("E-TABM-721").intValue());
    
  }
}