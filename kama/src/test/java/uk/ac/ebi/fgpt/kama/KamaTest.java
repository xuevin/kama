package uk.ac.ebi.fgpt.kama;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

import monq.ie.Term2Re;
import monq.jfa.CompileDfaException;
import monq.jfa.Dfa;
import monq.jfa.DfaRun;
import monq.jfa.Nfa;
import monq.jfa.ReSyntaxException;
import monq.jfa.actions.Printf;

import org.apache.commons.net.ftp.FTPClient;
import org.junit.Test;

import uk.ac.ebi.fgpt.kama.Kama.Scope;

public class KamaTest {
	
	
	@Test
	public void testIfKamaCanBeInstantiated(){
		Kama testKama= new Kama();
		assertNotNull(testKama);
	}
	@Test
	public void testIfKamaCanBeInstantiatedFromEFO(){
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
	public void testGetChildrenOfEFOAccession() {
		Kama testKama = new Kama();
		ArrayList<String> listOfChildren = testKama.getChildrenOfEFOAccessionPlusItself("EFO_0000798");
		
		if(!listOfChildren.contains("thymus")){
			fail("Could not find thymus");
		}
	}
	@Test
	public void testIfMonqCanFilterFromString(){
			//It seems to only recognize dict words by those which have a space.
		
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

			    //Print out only the filtered text			    
			    assertEquals("blood Blood blood.",r.filter(inputText));
			    
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
	public void assertPassageIsFromBlood(){
		String passage = "the thymus ran far into blood";
		Kama kama = new Kama();
		ArrayList<String> listOfChildren = kama.getChildrenOfEFOAccessionPlusItself("EFO_0000798");
		
		boolean found = false;
		for(String dictWord : listOfChildren){
			//First make regex
			String regEx = Term2Re.convert(dictWord);
			
			try {
				Nfa nfa = new Nfa(regEx, new Printf("%0"));
			
				// Compile into the Dfa, specify that all text not matching any
			    // regular expression shall be copied from input to output
			    Dfa dfa = nfa.compile(DfaRun.UNMATCHED_DROP);

			    // get a machinery (DfaRun) to operate the Dfa
			    DfaRun r = new DfaRun(dfa);

			    //Print out only the filtered text
			    if(r.filter(passage).length()!=0){
			    	found=true;
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
		if(!found){
			fail();
		}
	}
	@Test
	public void assertPassageIsNotFromBlood(){
		String passage = "the happy chicken ran far";
		Kama kama = new Kama();
		ArrayList<String> listOfChildren = kama.getChildrenOfEFOAccessionPlusItself("EFO_0000798");
		
		boolean found = false;
		for(String dictWord : listOfChildren){
			//First make regex
			String regEx = Term2Re.convert(dictWord);
			
			try {
				Nfa nfa = new Nfa(regEx, new Printf("%0"));
			
				// Compile into the Dfa, specify that all text not matching any
			    // regular expression shall be copied from input to output
			    Dfa dfa = nfa.compile(DfaRun.UNMATCHED_DROP);

			    // get a machinery (DfaRun) to operate the Dfa
			    DfaRun r = new DfaRun(dfa);

			    //Print out only the filtered text
			    if(r.filter(passage).length()!=0){
			    	found=true;
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
		if(found){
			fail("Blood was found in passage when there was no mention of it!");
		}
	}
	@Test
	public void testPassageContainsEFO(){
		Kama kama = new Kama();
		assertEquals(false, kama.getIfPassageContainsEFO("there is no reference to it here", "EFO_0000798"));
		assertEquals(true,kama.getIfPassageContainsEFO("thymus is found", "EFO_0000798"));
		assertEquals(false,kama.getIfPassageContainsEFO("thymus", "EFO_0000798")); //Requires a space or period following
	}
	@Test
	public void testFTP(){
		
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
			if(client.getReplyString().contains("Login successful")){
				System.out.println(client.getReplyString());	
			}else{
				fail("Login Failed");
			}
			
			client.retrieveFile(arrayExpressFtpPath+testGeoFile, fos);
			
			System.out.println(client.getReplyString());


		} catch (SocketException e) {
			e.printStackTrace();
			fail();
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}finally{
			try{
				if(fos!=null){
					fos.close();
					client.disconnect();
					
					if(temp!=null){

						BufferedReader br = new BufferedReader(new FileReader(temp));
						String text;
						while((text= br.readLine())!=null){
							System.out.println(text);
						}
					}else{
						fail();
					}
				}
				else{
					fail();
				}
			}catch (IOException e) {
				e.printStackTrace();
				fail();
			}
		}
		
	}
	@Test
	public void testGetHashMapForListofIDF(){
		ArrayList<String> accessionIDS = new ArrayList<String>();
		
		//Noticed weird error with this accession ID
		
		accessionIDS.add("E-GEOD-24734"); //Title contains thymus but will fail because FTP is broken
		accessionIDS.add("E-TABM-721");  //Title contains thymus 
		accessionIDS.add("E-MEXP-2895"); // Should return false
		Kama kama = new Kama();
		HashMap<String,Boolean> actual = kama.getTrueFalseHashMapForListOfAccessions(accessionIDS,Scope.idf,"EFO_0000798");
		assertNotNull(actual);
		
		
		assertEquals(true, actual.get("E-TABM-721")); // Should pass because title contains thymus
//		assertEquals(true, actual.get("E-GEOD-24734")); // Should return true
		assertEquals(false, actual.get("E-MEXP-2895")); //Should fail because there is no reference to blood 
	}
	@Test
	public void testGetHashMapForListofSDRF(){
		ArrayList<String> accessionIDS = new ArrayList<String>();
		accessionIDS.add("E-GEOD-24734"); //Title contains thymus but will fail because FTP is broken
		accessionIDS.add("E-TABM-721");  //Title contains thymus 
		accessionIDS.add("E-MEXP-2895"); // Should return false
		
		Kama kama = new Kama();
		HashMap<String,Boolean> actual = kama.getTrueFalseHashMapForListOfAccessions(accessionIDS,Scope.sdrf,"EFO_0000798");
		assertNotNull(actual);
		
		
		assertEquals(true, actual.get("E-TABM-721")); // Should pass because sdrf contains thymus
//		assertEquals(false, actual.get("E-GEOD-24734")); //This sample contains nothign about blood... but idf does. it is also broken
		assertEquals(false, actual.get("E-MEXP-2895")); //Should fail because there is no reference to blood
		
	}
	@Test
	public void testIfAppUsesParentTerm(){
		Kama kama = new Kama();
		assertEquals(true, kama.getIfPassageContainsEFO("haemopoietic system is included", "EFO_0000798"));
	}
	@Test
	public void testThreeFieldsForTrueFalseFetches(){
		Kama kama = new Kama();
		ArrayList<String> accessionIDS = new ArrayList<String>();
		accessionIDS.add("E-GEOD-24734"); //Title contains thymus but will fail because FTP is broken
		accessionIDS.add("E-TABM-721");  //Title contains thymus 
		accessionIDS.add("E-MEXP-2895"); // Should return false
		
		HashMap<String,Boolean> sdrf = kama.getTrueFalseHashMapForListOfAccessions(accessionIDS,Scope.sdrf,"EFO_0000798");
		HashMap<String,Boolean> idf = kama.getTrueFalseHashMapForListOfAccessions(accessionIDS,Scope.idf,"EFO_0000798");
		
		assertEquals(true, idf.get("E-TABM-721")); // Should pass because title contains thymus
//		assertEquals(true, idf.get("E-GEOD-24734")); // Should return true
		assertEquals(false, idf.get("E-MEXP-2895")); //Should fail because there is no reference to blood 
		
		assertEquals(true, sdrf.get("E-TABM-721")); // Should pass because sdrf contains thymus
//		assertEquals(false, sdrf.get("E-GEOD-24734")); //This sample contains nothign about blood... but idf does. it is also broken
		assertEquals(false, sdrf.get("E-MEXP-2895")); //Should fail because there is no reference to blood
	}
	
	@Test 
	public void getTrueFalseHashMapForListOfAccessions_both(){
		Kama kama = new Kama();
		ArrayList<String> accessionIDS = new ArrayList<String>();
		accessionIDS.add("E-GEOD-24734"); //Title contains thymus but will fail because FTP is broken
		accessionIDS.add("E-TABM-721");  //Title contains thymus 
		accessionIDS.add("E-MEXP-2895"); // Should return false
		
		HashMap<String,Boolean> both = kama.getTrueFalseHashMapForListOfAccessions(accessionIDS,Scope.both,"EFO_0000798");
		assertEquals(true, both.get("E-TABM-721")); // Should pass because title contains thymus
//		assertEquals(true, both.get("E-GEOD-24734")); // Should return true
		assertEquals(false, both.get("E-MEXP-2895")); //Should fail because there is no reference to blood
	}
	
	@Test
	public void testGetTrueFalseHashMapForExperimentCELFiles(){
		Kama kama = new Kama();
		assertEquals(false, kama.getTrueFalseHashMapForExperimentCELFiles("E-GEOD-26672","EFO_0000798").get("GSM656451.CEL"));
		assertEquals(null, kama.getTrueFalseHashMapForExperimentCELFiles("E-TABM-721","EFO_0000798").get("GSM656451.CEL"));
	}
	@Test
	public void testPassageCountOfEFO(){
		Kama kama = new Kama();
		assertEquals(2, kama.getPassageCountOfEFO("blood blood blood", "EFO_0000798"));
		assertEquals(3, kama.getPassageCountOfEFO("blood blood blood.", "EFO_0000798"));
		assertEquals(4, kama.getPassageCountOfEFO("blood blood blood thymus ", "EFO_0000798"));
		assertEquals(4, kama.getPassageCountOfEFO("blood blood \n blood thymus ", "EFO_0000798"));

	}
	@Test
	public void testGetCountOfEachTermInExperiment(){
		Kama kama = new Kama();
		//In this experiment thymus and spleen are the key words
		//IDF: Spleen:3 Thymus:3
		//SDRF: Thymus: 212, spleen: 105
		
		//To make sure that FieldType.both works
		assertEquals(107, kama.getCountOfEachTermInExperiment("E-TABM-721", Scope.sdrf, "EFO_0000798").get("thymus").intValue());
		assertEquals(3, kama.getCountOfEachTermInExperiment("E-TABM-721", Scope.idf, "EFO_0000798").get("thymus").intValue());
		assertEquals(110, kama.getCountOfEachTermInExperiment("E-TABM-721", Scope.both, "EFO_0000798").get("thymus").intValue());
		
		ArrayList<String> accessionIDS = new ArrayList<String>();
		accessionIDS.add("E-TABM-721");

		assertEquals(6,kama.getCountHashMapForListOfAccessions(accessionIDS, Scope.idf, "EFO_0000798").get("E-TABM-721").intValue());
		assertEquals(3, kama.getCountOfEachTermInExperiment("E-TABM-721", Scope.idf, "EFO_0000798").get("spleen").intValue());
		assertEquals(3, kama.getCountOfEachTermInExperiment("E-TABM-721", Scope.idf, "EFO_0000798").get("thymus").intValue());

		assertEquals(212,kama.getCountHashMapForListOfAccessions(accessionIDS, Scope.sdrf, "EFO_0000798").get("E-TABM-721").intValue());
		assertEquals(105, kama.getCountOfEachTermInExperiment("E-TABM-721", Scope.sdrf, "EFO_0000798").get("spleen").intValue());
		assertEquals(107, kama.getCountOfEachTermInExperiment("E-TABM-721", Scope.sdrf, "EFO_0000798").get("thymus").intValue());
	}
	@Test
	public void testGetCountHashMapForListOfAccessions(){
		Kama kama = new Kama();
		ArrayList<String> accessionIDS = new ArrayList<String>();
		accessionIDS.add("E-TABM-721");
		assertEquals(218,kama.getCountHashMapForListOfAccessions(accessionIDS, Scope.both, "EFO_0000798").get("E-TABM-721").intValue());
		
	}
	@Test
	public void testgetChildrenOfEFOAccessionPlusItself_EFOIsNotFound(){
		Kama kama = new Kama();
		assertNull(kama.getChildrenOfEFOAccessionPlusItself("EFO_NOTFOUND"));
	}
	@Test
	public void testWhatHappensWhenFileDoesNotHaveArrayDataFileColumn(){
		//What happens when a file has does not have a ArrayDataFile
		Kama kama = new Kama();
		kama.getTrueFalseHashMapForExperimentCELFiles("E-MTAB-502", "EFO_0000798");
	}
	@Test 
	public void testGetCountOfEachTermInExperimentAsString(){
		Kama kama = new Kama();
		assertEquals("spleen:108;thymus:110;",
				kama.getCountOfEachTermInExperimentAsString("E-TABM-721", Scope.both, "EFO_0000798"));
	}
	@Test
	public void testGetCountHashMapForExperimentCELFiles(){
		Kama kama = new Kama();
		HashMap<String,Integer> hashMap = kama.getCountHashMapForExperimentCELFiles("E-TABM-721", "EFO_0000798");
		int sum = 0;
		for(String key:hashMap.keySet()){
			sum+= hashMap.get(key).intValue();	
		}
		ArrayList<String> listOfExperimentAccessions = new ArrayList<String>();
		listOfExperimentAccessions.add("E-TABM-721");
		assertEquals(sum, kama.getCountHashMapForListOfAccessions(listOfExperimentAccessions, Scope.sdrf, "EFO_0000798").get("E-TABM-721").intValue());
	}
	@Test
	public void testGetCountOfEachTermPerSample(){
		Kama kama = new Kama();
		HashMap<String,HashMap<String,Integer>> test = kama.getCountOfEachTermPerSample("E-TABM-721", "EFO_0000798");
		for(String sample:test.keySet()){
			HashMap<String,Integer> row = test.get(sample);
			for(String term:row.keySet()){
				System.out.print(term + row.get(term).intValue()+";");
			}
			System.out.println();
		}
	}
}