package uk.ac.ebi.fgpt.kama;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.net.ftp.FTPClient;

import monq.ie.Term2Re;
import monq.jfa.CompileDfaException;
import monq.jfa.Dfa;
import monq.jfa.DfaRun;
import monq.jfa.Nfa;
import monq.jfa.ReSyntaxException;
import monq.jfa.actions.Printf;

import uk.ac.ebi.ontocat.OntologyServiceException;
import uk.ac.ebi.ontocat.OntologyTerm;
import uk.ac.ebi.ontocat.file.FileOntologyService;

/**
 * Kama - quicK pAss for Meta Analysis
 * @author vincent@ebi.ac.uk
 *
 */
public class Kama {
	
	public enum Scope {
	    sdrf, idf ,both
	}
	
	private FileOntologyService ontoService;
	private String arrayExpressFtp = "ftp.ebi.ac.uk";
	private String arrayExpressFtpPath = "/pub/databases/microarray/data/experiment/";
	private HashMap<String,File> hashOfAccessionFilesForSDRF;
	private HashMap<String,File> hashOfAccessionFilesForIDF;
	
	/** The EFO to ArrayList<String> hashmap that stores children so that an OWL does not need to be 
	 * 	parsed multiple times */
	private HashMap<String,ArrayList<String>> hashOfEFOChildrenTerms;
	

	//Default uses version 142 of EFO;
	public Kama(){
    	try {
			ontoService = new FileOntologyService(this.getClass().getClassLoader().getResource("EFO_inferred_v142.owl").toURI());
			hashOfAccessionFilesForSDRF=new HashMap<String, File>();
			hashOfAccessionFilesForIDF=new HashMap<String, File>();
			hashOfEFOChildrenTerms=new HashMap<String, ArrayList<String>>();
		} catch (URISyntaxException e) {
			System.err.println("DEFAULT EFO_inferred_v142.owl IS NOT FOUND");
			e.printStackTrace();
			return;
		}
	}
	//Custom EFO/OWL file
	public Kama(File owlFile){
    	ontoService = new FileOntologyService(owlFile.toURI());
    	hashOfAccessionFilesForSDRF=new HashMap<String, File>();
		hashOfAccessionFilesForIDF=new HashMap<String, File>();
		hashOfEFOChildrenTerms=new HashMap<String, ArrayList<String>>();

	}
	
	
	/**
	 * Gets a list of all the EFO children and the EFO itself for every EFOAccession in this list.
	 *
	 * @param listOfEFOAccessionIds the list of EFO classes for which you want to retrieve the children from.
	 * @return an Arraylist of the all the EFO children of the EFO classes. 
	 */
	public ArrayList<String> getChildrenOfEFOAccessionPlusItself(String ...listOfEFOAccessionIds){
		ArrayList<String> returnListOfEFO = new ArrayList<String>();
		for(String efoAccessionID:listOfEFOAccessionIds){
			
			if(hashOfEFOChildrenTerms.get(efoAccessionID)==null){
				try{
					OntologyTerm parent= ontoService.getTerm(efoAccessionID);
					ArrayList<String> listOfChildren;
					if(parent!=null){
						listOfChildren = new ArrayList<String>();
						listOfChildren.add(parent.getLabel());
						for(OntologyTerm ot : ontoService.getAllChildren(parent)){
							listOfChildren.add(ot.getLabel());
						}
						hashOfEFOChildrenTerms.put(efoAccessionID, listOfChildren);
					}else{
						throw new OntologyServiceException("OntologyTerm is null");
					}
				}catch (OntologyServiceException e) {
					System.err.println("WARNING: EFO ID DOES NOT EXIST");
					return null;
				}
			}
			returnListOfEFO.addAll(hashOfEFOChildrenTerms.get(efoAccessionID));
		}
		return returnListOfEFO;
	}
	
	/**
	 * Method to determine whether the passage mentions the members of the EFO classes specified.
	 *
	 * @param passage the passage to be filtered
	 * @param listOfEFOAccessionIds a list of EFO accession ids
	 * @return true, if the passage mentions the EFO class specified
	 */
	public boolean getIfPassageContainsEFO(String passage,String ...listOfEFOAccessionIds){
		ArrayList<String> listOfChildren = getChildrenOfEFOAccessionPlusItself(listOfEFOAccessionIds);
		
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
			} catch (CompileDfaException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
		return found;
	}
	
	/**
	 * Gets the count of how many times members of an EFO class is found
	 *
	 * @param passage the passage to be checked
	 * @param listOfEFOAccessionIds a list of EFO accession ids
	 * @return the count of how many times members of the EFO classes are found
	 */
	public int getPassageCountOfEFO(String passage,String ...listOfEFOAccessionIds){
		ArrayList<String> listOfChildren = getChildrenOfEFOAccessionPlusItself(listOfEFOAccessionIds);
		int countOfTermsFound=0;
		
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

			    //Get only the filtered text
			    String filtered = r.filter(passage);
			    
			    if(filtered.length()!=0){
			    	countOfTermsFound+=filtered.split("\\s+").length;
			    }
			} catch (ReSyntaxException e) {
				e.printStackTrace();
			} catch (CompileDfaException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
		return countOfTermsFound;
	}
	

	/**
	 * Gets the accessionID to boolean hashMap which is used to determine whether or not a certain experiment has
	 * a member of the EFO class.
	 *
	 * @param listOfExperimentAccessions the list of experiment accession ids
	 * @param scope the scope of the search (sdrf or idf or both)
	 * @param listOfEFOAccessionIds a list of EFO accession ids
	 * @return the accessionID to boolean hashMap that is used to determine whether a certain experiment has a member of the EFO class ie {E-GEOD-1000=>true}
	 */
	public HashMap<String, Boolean> getTrueFalseHashMapForListOfAccessions(
			ArrayList<String> listOfExperimentAccessions, Scope scope,
			String... listOfEFOAccessionIds) {
		downloadFilesFromFTP(listOfExperimentAccessions);
		
		HashMap<String,Boolean> returnHashMap = new HashMap<String, Boolean>();
		
		
		HashMap<String, File> hashMapToUse;
		if(scope==Scope.sdrf){
			hashMapToUse=hashOfAccessionFilesForSDRF;
		}else if(scope==Scope.idf){
			hashMapToUse=hashOfAccessionFilesForIDF;
		}else if(scope==Scope.both){
			HashMap<String,Boolean> sdrfHash = getTrueFalseHashMapForListOfAccessions(listOfExperimentAccessions,Scope.idf,listOfEFOAccessionIds);
			HashMap<String,Boolean> idfHash = getTrueFalseHashMapForListOfAccessions(listOfExperimentAccessions,Scope.sdrf,listOfEFOAccessionIds);
			for(String accession:listOfExperimentAccessions){
				if((idfHash.get(accession)!=null && sdrfHash.get(accession)!=null)
					&&(idfHash.get(accession)==true || sdrfHash.get(accession)==true)){
					returnHashMap.put(accession, true);
				}else{
					returnHashMap.put(accession, false);
				}
			}
			return returnHashMap;
		}else{
			//Theoretically Never Reached
			return null;
		}

		for(String accession:listOfExperimentAccessions){
			File file = hashMapToUse.get(accession);
			if(file!=null){

				BufferedReader br;
				try {
					br = new BufferedReader(new FileReader(file));
					String text;
					String passage="";
					while((text= br.readLine())!=null){
						passage+=text;
					}
					if(passage!=""){
						returnHashMap.put(accession, getIfPassageContainsEFO(passage, listOfEFOAccessionIds));	
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}
		}
			
		return returnHashMap;
	}
	/**
	 * Gets the experiment sample to boolean hashmap that is used to determine all the unique Array Data Files in a SDRF and whether or not
	 * the row contains a member of the EFO class.
	 *
	 * @param experimentAccession the experiment accession
	 * @param listOfEFOAccessionIds a list of EFO accession ids
	 * @return the sample to boolean hashmap for the specified experiment ie {sample.cel=>true}
	 */
	public HashMap<String,Boolean> getTrueFalseHashMapForExperimentCELFiles(String experimentAccession, String ...listOfEFOAccessionIds){
		HashMap<String, Boolean> returnHash = new HashMap<String, Boolean>();
		
		downloadFilesFromFTP(experimentAccession);
		
		File file = hashOfAccessionFilesForSDRF.get(experimentAccession);
		if(file == null){
			return returnHash;
		}
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(file));
			String text;
			String passage="";
			while((text= br.readLine())!=null){
				passage+=text+"\n";
			}
			
			if(!passage.isEmpty()){
				passage = passage.replaceAll("\\s+$", "");
	
				
				String[] rowString = passage.split("\n");
				String[][] table = stringToTable(passage);
				
				boolean hasADFColumn = false;
				int adfColumn = 0;
				
				//Look for the column named "Array Data File"
				for(int i =0;i<table[0].length;i++){
					if(table[0][i].equals("Array Data File")){
						hasADFColumn=true;
						adfColumn=i;
						break;
					}
				}
				//Put into Hash whether or not row contains EFO
				if(hasADFColumn==true){
					for(int i = 1;i<table.length;i++){
//						System.out.println(table[i][adfColumn]+ "\t" +getIfPassageContainsEFO(rowString[i], EFOAccessionId));
						//Make sure that CEL Files which have at least one reference to blood are still reported
						if(returnHash.get(table[i][adfColumn])==null){
							returnHash.put(table[i][adfColumn],getIfPassageContainsEFO(rowString[i], listOfEFOAccessionIds));	
						}else{
							if(returnHash.get(table[i][adfColumn])==true){
								//skip 
								//Keep true, true	
							}else{
								//Replace False with whether or not the passage contains efo
								returnHash.put(table[i][adfColumn],getIfPassageContainsEFO(rowString[i], listOfEFOAccessionIds));
							}
						}
					}
				}else{
					System.out.println(experimentAccession + " does not have a ADF column");
				}
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return returnHash;
		
	}
	/**
	 * Gets the experiment to count hash map that is used to determine how many times the members of an EFO class occur in a given experiment.
	 *
	 * @param listOfExperimentAccessions the list of experiment accessions
	 * @param scope the filetype. Can count IDF, SDRF, or both
	 * @param listOfEFOAccessions the EFO accession id
	 * @return the experiment to count hash map for the list of accessions ie {E-GEOD-10000=>10}
	 */
	public HashMap<String,Integer> getCountHashMapForListOfAccessions(ArrayList<String> listOfExperimentAccessions,Scope scope,String ...listOfEFOAccessions){
		downloadFilesFromFTP(listOfExperimentAccessions);
		
		HashMap<String,Integer> returnHashMap = new HashMap<String, Integer>();
		
		
		HashMap<String, File> hashMapToUse;
		if(scope==Scope.sdrf){
			hashMapToUse=hashOfAccessionFilesForSDRF;
		}else if(scope==Scope.idf){
			hashMapToUse=hashOfAccessionFilesForIDF;
		}else if(scope==Scope.both){
			HashMap<String,Integer> sdrfHash = getCountHashMapForListOfAccessions(listOfExperimentAccessions,Scope.idf,listOfEFOAccessions);
			HashMap<String,Integer> idfHash = getCountHashMapForListOfAccessions(listOfExperimentAccessions,Scope.sdrf,listOfEFOAccessions);
			for(String accession:listOfExperimentAccessions){
				returnHashMap.put(accession,Integer.valueOf(idfHash.get(accession))+Integer.valueOf(sdrfHash.get(accession)));
			}
			return returnHashMap;
		}else{
			//Theoretically Never Reached
			return null;
		}
	
		for(String accession:listOfExperimentAccessions){
			File file = hashMapToUse.get(accession);
			if(file!=null){
	
				BufferedReader br;
				try {
					br = new BufferedReader(new FileReader(file));
					String text;
					String passage="";
					while((text= br.readLine())!=null){
						passage+=text;
					}
					if(passage!=""){
						returnHashMap.put(accession, getPassageCountOfEFO(passage, listOfEFOAccessions));	
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}
		}
			
		return returnHashMap;
	}
	/**
	 * Get sample to count hashmap that is used to determine how many times a member of the EFO class
	 * occurs in the row.
	 * 
	 * @param experimentAccession the experiment accession id to look up
	 * @param listOfEFOAccessionIds the 
	 * @return a sample to Integer hashmap ie {sample.cel=>5}
	 */
	public HashMap<String,Integer> getCountHashMapForExperimentCELFiles(String experimentAccession, String ...listOfEFOAccessionIds){
		HashMap<String, Integer> returnHash = new HashMap<String, Integer>();
		downloadFilesFromFTP(experimentAccession);
		
		File file = hashOfAccessionFilesForSDRF.get(experimentAccession);
		
		if(file == null){
			return returnHash;
		}
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(file));
			String text;
			String passage="";
			while((text= br.readLine())!=null){
				passage+=text+"\n";
			}
			
			//Read a sdrf file and put it into a 2d array
			if(!passage.isEmpty()){
				passage = passage.replaceAll("\\s+$", "");
	
				
				String[] rowString = passage.split("\n");
				String[][] table = stringToTable(passage);
				
				boolean hasADFColumn = false;
				int adfColumn = 0;
				
				//Look for the column named "Array Data File"
				for(int i =0;i<table[0].length;i++){
					if(table[0][i].equals("Array Data File")){
						hasADFColumn=true;
						adfColumn=i;
						break;
					}
				}
				//Put into Hash the count of how many children were found
				if(hasADFColumn==true){
					for(int i = 1;i<table.length;i++){
//						System.out.println(table[i][adfColumn]+ "\t" +getIfPassageContainsEFO(rowString[i], EFOAccessionId));
						//Make sure that CEL Files which have at least one reference to EFO are still reported and
						//add the values
						if(returnHash.get(table[i][adfColumn])==null){
							returnHash.put(table[i][adfColumn],getPassageCountOfEFO(rowString[i], listOfEFOAccessionIds));	
						}else{
							//put the sum of the samples in
							returnHash.put(table[i][adfColumn], 
									returnHash.get(table[i][adfColumn]).intValue()+
									+getPassageCountOfEFO(rowString[i], listOfEFOAccessionIds)		
							);
						}
					}
				}else{
					System.out.println(experimentAccession + " does not have a ADF column");
				}
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return returnHash;
		
	}
	
	/**
	 * @param experimentAccession
	 * @param listOfEFOAccessionIds
	 * @return a hashMap which relates sample to termCounthashmap. {Sample.CEL=>{Thymus=>2,Blood=>2}}
	 * 
	 */
	public HashMap<String,HashMap<String,Integer>> getCountOfEachTermPerSample(String experimentAccession, String ...listOfEFOAccessionIds){ 
		
		downloadFilesFromFTP(experimentAccession);
		
		HashMap<String,HashMap<String,Integer>> returnHash = new HashMap<String, HashMap<String,Integer>>();
		File file = hashOfAccessionFilesForSDRF.get(experimentAccession);
		
		if(file == null){
			return returnHash;
		}
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(file));
			String text;
			String passage="";
			while((text= br.readLine())!=null){
				passage+=text+"\n";
			}
			
			//Read a sdrf file and put it into a 2d array
			if(!passage.isEmpty()){
				passage = passage.replaceAll("\\s+$", "");
	
				
				String[] rowString = passage.split("\n");
				String[][] table = stringToTable(passage);
				
				boolean hasADFColumn = false;
				int adfColumn = 0;
				
				//Look for the column named "Array Data File"
				for(int i =0;i<table[0].length;i++){
					if(table[0][i].equals("Array Data File")){
						hasADFColumn=true;
						adfColumn=i;
						break;
					}
				}
				//Put into Hash the sample and the terms it found
				if(hasADFColumn==true){
					for(int i = 1;i<table.length;i++){
//						System.out.println(table[i][adfColumn]+ "\t" +getIfPassageContainsEFO(rowString[i], EFOAccessionId));
						//Make sure that CEL Files which have at least one reference to EFO are still reported and
						//add the values
						if(returnHash.get(table[i][adfColumn])==null){
							returnHash.put(table[i][adfColumn],passageToTermHash(rowString[i], listOfEFOAccessionIds));	
						}else{
							//Combine hashes
							HashMap<String, Integer>  newHash = passageToTermHash(rowString[i], listOfEFOAccessionIds);
							
							for(String term: newHash.keySet()){
								if(returnHash.get(table[i][adfColumn]).get(term)!=null){
									int newSum = returnHash.get(table[i][adfColumn]).get(term).intValue()+newHash.get(term).intValue();
									returnHash.get(table[i][adfColumn]).put(term,newSum);
								}else{
									returnHash.get(table[i][adfColumn]).put(term, newHash.get(term));
								}
							}
						}
					}
				}else{
					System.out.println(experimentAccession + " does not have a ADF column");
				}
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return returnHash;
		
	}
	
	/**
	 * Gets the EFOterm to count hashmap which is used to identify which EFO class children are mentioned in a experiment
	 *
	 * @param experimentAccession the experiment accession
	 * @param listOfEFOAccessionIds the eFO accession id
	 * @param scope the scope of the search. Can search IDF, SDRF, or both
	 * @return the EFOterm to count hashmap ie {blood=>5}
	 */
	public HashMap<String,Integer> getCountOfEachTermInExperiment(String experimentAccession,Scope scope,String ...listOfEFOAccessionIds){
		//Download Experiment
		downloadFilesFromFTP(experimentAccession);
		
		//Get a list of children EFO
		ArrayList<String> listOfChildren = getChildrenOfEFOAccessionPlusItself(listOfEFOAccessionIds);
		
		//Make a hashmap to return
		HashMap<String,Integer> returnHashMap = new HashMap<String, Integer>();
		
		
		HashMap<String, File> hashMapToUse;
		if(scope==Scope.sdrf){
			hashMapToUse=hashOfAccessionFilesForSDRF;
		}else if(scope==Scope.idf){
			hashMapToUse=hashOfAccessionFilesForIDF;
		}else if(scope==Scope.both){
			HashMap<String,Integer> sdrfHash = getCountOfEachTermInExperiment(experimentAccession,Scope.idf,listOfEFOAccessionIds);
			HashMap<String,Integer> idfHash = getCountOfEachTermInExperiment(experimentAccession,Scope.sdrf,listOfEFOAccessionIds);
			
			for(String efoTerm:sdrfHash.keySet()){
				returnHashMap.put(efoTerm, sdrfHash.get(efoTerm));
			}
			for(String efoTerm:idfHash.keySet()){
				if(returnHashMap.get(efoTerm)!=null){
					returnHashMap.put(efoTerm, (Integer.valueOf(idfHash.get(efoTerm))+Integer.valueOf(sdrfHash.get(efoTerm))));					
				}else{
					//Case where idf has a term that is not mentioned in the sdrf
					returnHashMap.put(efoTerm, idfHash.get(efoTerm));
				}
			}
			return returnHashMap;
		}else{
			return null;
		}
		File file = hashMapToUse.get(experimentAccession);
		if(file!=null){

			BufferedReader br;
			try {
				br = new BufferedReader(new FileReader(file));
				String text;
				String passage="";
				while((text= br.readLine())!=null){
					passage+=text;
				}
				if(passage!=""){
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

						    //Get only the filtered text
						    String filtered = r.filter(passage);
						    
						    if(filtered.length()!=0){
						    	returnHashMap.put(dictWord,filtered.split("\\s+").length);
						    }
						} catch (ReSyntaxException e) {
							e.printStackTrace();
						} catch (CompileDfaException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}	
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return returnHashMap;		
	}
	public String getCountOfEachTermInExperimentAsString(String experimentAccession,Scope filetype,String ...listOfEFOAccessionIds){
		HashMap<String, Integer> countMap = getCountOfEachTermInExperiment(experimentAccession, filetype, listOfEFOAccessionIds);
		String output="";
		
		for(String key:countMap.keySet()){
			output+=key+":"+countMap.get(key)+";";
		}
		return output;
		
	}
	/**
	 * Download single accession file from ftp. Basically, it wraps an arraylist around the accession and
	 * calls the other download method
	 *
	 * @param experimentAccessionID is the experiment accession ids
	 */
	private void downloadFilesFromFTP(String experimentAccessionID){
		ArrayList<String> temp = new ArrayList<String>();
		temp.add(experimentAccessionID);
		downloadFilesFromFTP(temp);
	}
	/**
	 * Download files from FTP.
	 * If the file exists, it does not attempt to redownload it.
	 *
	 * @param listOfExperimentAccessions the list of experiment accessions
	 */
	private void downloadFilesFromFTP(ArrayList<String> listOfExperimentAccessions){
		
		FTPClient client = new FTPClient();
		FileOutputStream fos_sdrf = null;
		FileOutputStream fos_idf = null;

		File temp_sdrf = null;
		File temp_idf = null;
		
		
		
		try {
			client.connect(arrayExpressFtp);
			client.login("anonymous", "");
			
			for(String accession:listOfExperimentAccessions){
				//Only download the files which are needed
				if(hashOfAccessionFilesForSDRF.containsKey(accession) &&
						hashOfAccessionFilesForIDF.containsKey(accession)){
					continue;
				}
				
				try {
					temp_sdrf = File.createTempFile("kama_", ".tmp");
					temp_sdrf.deleteOnExit();
					
					temp_idf=File.createTempFile("kama_", ".tmp");
					temp_idf.deleteOnExit();
					
					fos_sdrf = new FileOutputStream(temp_sdrf);
					fos_idf = new FileOutputStream(temp_idf);

					String pipeline = accession.substring(2,6);
					String sdrfFile = accession+".sdrf.txt";
					String idfFile = accession+".idf.txt";
					
					client.retrieveFile(arrayExpressFtpPath+
							pipeline+"/"+
							accession+"/"+
							sdrfFile, fos_sdrf);
					
					if(client.getReplyString().contains("226")){
						System.out.println(sdrfFile + "\tFile Received");
						hashOfAccessionFilesForSDRF.put(accession, temp_sdrf);
					}else{
						System.out.println(sdrfFile + "\tFailed");
					}
					

					client.retrieveFile(arrayExpressFtpPath+
							pipeline+"/"+
							accession+"/"+
							idfFile, fos_idf);
					
					if(client.getReplyString().contains("226")){
						System.out.println(idfFile + "\tFile Received");
						hashOfAccessionFilesForIDF.put(accession, temp_idf);
					}else{
						System.out.println(sdrfFile + "\tFailed");
					}
					
				} catch (IOException e) {
					e.printStackTrace();
				}finally{
					try{
						if(fos_sdrf!=null){
							fos_sdrf.close();
							fos_idf.close();							
						}
					}catch (IOException e) {
						e.printStackTrace();
					}
				}	
			}
			client.disconnect();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * String to table method
	 *
	 * @param oneLongString is the input string, It is simply the raw sdrf or raw idf.
	 * @return the string[][] is the 2d array representation of table ([row][column])
	 */
	private String[][] stringToTable(String oneLongString){
		//01_Create Array Of rows by splitting on new line
		String[] rows = oneLongString.split("\\r?\\n");
		
		//02_Create Array of rows that you will return
		int columnsLength = rows[0].split("\\t").length;
		
		String[][] rowArray = new String[rows.length][columnsLength];  
		//03_For each row, split on the tab and put the column into the row array
		for(int i = 0; i<rows.length; i++){
			String[] column = rows[i].split("\\t");
			rowArray[i]=column;
		}
		return rowArray;
	}
	private HashMap<String,Integer> passageToTermHash(String passage, String ...listOfEFOAccessionIds){
		ArrayList<String> listOfChildren = getChildrenOfEFOAccessionPlusItself(listOfEFOAccessionIds);
		HashMap<String,Integer> returnHashMap = new HashMap<String, Integer>();
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

			    //Get only the filtered text
			    String filtered = r.filter(passage);
			    
			    if(filtered.length()!=0){
			    	returnHashMap.put(dictWord,filtered.split("\\s+").length);
			    }
			} catch (ReSyntaxException e) {
				e.printStackTrace();
			} catch (CompileDfaException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return returnHashMap;
	}
}
