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
import java.util.List;

import monq.ie.Term2Re;
import monq.jfa.CompileDfaException;
import monq.jfa.Dfa;
import monq.jfa.DfaRun;
import monq.jfa.Nfa;
import monq.jfa.ReSyntaxException;
import monq.jfa.actions.Printf;

import org.apache.commons.net.ftp.FTPClient;

import uk.ac.ebi.ontocat.OntologyServiceException;
import uk.ac.ebi.ontocat.OntologyTerm;
import uk.ac.ebi.ontocat.file.FileOntologyService;

/**
 * Kama - quicK pAss for Meta Analysis
 * 
 * @author Vincent Xue
 * @date Tuesday, June 21 2011
 * 
 */
public class Kama {
  
  /**
   * Scope define the possible levels Kama can function on.
   */
  public enum Scope {
    sdrf,
    idf,
    both
  }
  
  private FileOntologyService ontoService;
  private String arrayExpressFtp = "ftp.ebi.ac.uk";
  private String arrayExpressFtpPath = "/pub/databases/microarray/data/experiment/";
  private HashMap<String,File> hashOfAccessionFilesForSDRF = new HashMap<String,File>();
  private HashMap<String,File> hashOfAccessionFilesForIDF = new HashMap<String,File>();
  private HashMap<String,Integer> hashOfExperimentAccessionToCountOfAssays = new HashMap<String,Integer>();
  
  /**
   * The 'parent ontology term' to 'ArrayList<String>' hashmap that stores it's children so that an OWL does
   * not need to be parsed multiple times.
   */
  protected HashMap<String,ArrayList<String>> hashOfOntologyChildrenTerms = new HashMap<String,ArrayList<String>>();
  
  // Default uses version 142 of EFO as Ontology;
  public Kama() {
    try {
      ontoService = new FileOntologyService(this.getClass().getClassLoader().getResource(
        "EFO_inferred_v142.owl").toURI());
    } catch (URISyntaxException e) {
      System.err.println("DEFAULT EFO_inferred_v142.owl IS NOT FOUND");
      e.printStackTrace();
      return;
    }
  }
  
  // Constructor to use a user defined ontology file (Can be OBO or OWL);
  public Kama(File owlFile) {
    ontoService = new FileOntologyService(owlFile.toURI());
  }
  
  /**
   * Gets a list of all the class's children, the class itself and all related synonyms for each ontology
   * accession id in the list.
   * 
   * @param listOfOntologyAccessionIds
   *          the list of ontology accession ids for which you want to retrieve the children from.
   * @return the list of the all the class's children, the class itself, and related synonyms.
   */
  public List<String> getRelatedTerms(String... listOfOntologyAccessionIds) {
    ArrayList<String> returnListOfEFO = new ArrayList<String>();
    for (String efoAccessionID : listOfOntologyAccessionIds) {
      
      if (hashOfOntologyChildrenTerms.get(efoAccessionID) == null) {
        try {
          OntologyTerm parent = ontoService.getTerm(efoAccessionID);
          ArrayList<String> listOfChildren;
          if (parent != null) {
            listOfChildren = new ArrayList<String>();
            listOfChildren.add(parent.getLabel());
            listOfChildren.addAll(ontoService.getSynonyms(parent)); // Add Synonymns
            for (OntologyTerm ot : ontoService.getAllChildren(parent)) {
              listOfChildren.add(ot.getLabel());
              listOfChildren.addAll(ontoService.getSynonyms(ot));// Include Synonymns
            }
            hashOfOntologyChildrenTerms.put(efoAccessionID, listOfChildren);
          } else {
            throw new OntologyServiceException("OntologyTerm is null");
          }
        } catch (OntologyServiceException e) {
          System.err.println("WARNING: ONTOLOGY ACCESSION ID DOES NOT EXIST");
          return null;
        }
      }
      returnListOfEFO.addAll(hashOfOntologyChildrenTerms.get(efoAccessionID));
    }
    return returnListOfEFO;
  }
  
  /**
   * Method to determine whether the passage mentions any of the terms related to each member in the list Of
   * ontology accession ids.
   * 
   * @param passage
   *          the passage to be filtered
   * @param listOfOntologyAccessionIds
   *          a list of ontology accession ids
   * @return true, if the passage mentions any of the related terms to each member in
   *         listOfOntologyAccessionIds
   */
  public boolean getIfPassageContainsOntologyTerm(String passage, String... listOfOntologyAccessionIds) {
    List<String> listOfChildren = getRelatedTerms(listOfOntologyAccessionIds);
    
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
      } catch (CompileDfaException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return found;
  }
  
  /**
   * Gets the total count of all related ontology terms in the passage
   * 
   * @param passage
   *          the passage to be checked
   * @param listOfOntologyAccessionIds
   *          the list of ontology accession ids that will be looked for
   * @return the total count all related ontology terms in the passage
   */
  public int getTotalCountOfRelatedOntologyTermsInPassage(String passage,
                                                          String... listOfOntologyAccessionIds) {
    List<String> listOfChildren = getRelatedTerms(listOfOntologyAccessionIds);
    int countOfTermsFound = 0;
    
    for (String dictWord : listOfChildren) {
      countOfTermsFound += getCountFromPassage(dictWord, passage);
    }
    return countOfTermsFound;
  }
  
  /**
   * Gets the 'experiment accession id' to 'boolean' hashMap that is used to determine whether or not a
   * certain experiment has a member of the EFO class.
   * 
   * @param listOfExperimentAccessionIds
   *          the list of experiment accession ids
   * @param scope
   *          the scope of the search (sdrf or idf or both)
   * @param listOfOntologyAccessionIds
   *          a list of Ontology accession ids
   * @return the 'experiment accession id' to 'boolean' hashMap that is used to determine whether a certain
   *         experiment has a word related to the list of ontology accession ids. ie {E-GEOD-1000=>true}
   */
  public HashMap<String,Boolean> getTrueFalseHashMapForListOfAccessions(ArrayList<String> listOfExperimentAccessionIds,
                                                                        Scope scope,
                                                                        String... listOfOntologyAccessionIds) {
    downloadFilesFromFTP(listOfExperimentAccessionIds);
    
    HashMap<String,Boolean> returnHashMap = new HashMap<String,Boolean>();
    
    HashMap<String,File> hashMapToUse;
    if (scope == Scope.sdrf) {
      hashMapToUse = hashOfAccessionFilesForSDRF;
    } else if (scope == Scope.idf) {
      hashMapToUse = hashOfAccessionFilesForIDF;
    } else if (scope == Scope.both) {
      HashMap<String,Boolean> sdrfHash = getTrueFalseHashMapForListOfAccessions(listOfExperimentAccessionIds,
        Scope.idf, listOfOntologyAccessionIds);
      HashMap<String,Boolean> idfHash = getTrueFalseHashMapForListOfAccessions(listOfExperimentAccessionIds,
        Scope.sdrf, listOfOntologyAccessionIds);
      for (String accession : listOfExperimentAccessionIds) {
        if ((idfHash.containsKey(accession) && sdrfHash.containsKey(accession))
            && (idfHash.get(accession) == true || sdrfHash.get(accession) == true)) {
          returnHashMap.put(accession, true);
        } else {
          returnHashMap.put(accession, false);
        }
      }
      return returnHashMap;
    } else {
      // Theoretically Never Reached
      return null;
    }
    
    for (String accession : listOfExperimentAccessionIds) {
      File file = hashMapToUse.get(accession);
      if (file != null) {
        String passage = getPassageFromFile(file);
        if (passage != "") {
          returnHashMap.put(accession, getIfPassageContainsOntologyTerm(passage, listOfOntologyAccessionIds));
        }
        
      }
    }
    
    return returnHashMap;
  }
  
  /**
   * Gets the 'CEL file name' to 'boolean' hashmap that is used to find all the unique CEL files in the SDRF
   * and determine whether or not the CEL file (as annotated in the SDRF) mentions a term related to the list
   * Of ontology accessionIds.
   * 
   * @param experimentAccession
   *          the experiment accession
   * @param listOfOntologyAccessionIds
   *          a list of EFO accession ids
   * @return the 'CEL file name' to 'boolean' hashmap for the specified experiment ie {sample.cel=>true}
   */
  public HashMap<String,Boolean> getTrueFalseHashMapForExperimentCELFiles(String experimentAccession,
                                                                          String... listOfOntologyAccessionIds) {
    HashMap<String,Boolean> returnHash = new HashMap<String,Boolean>();
    
    downloadFilesFromFTP(experimentAccession);
    
    File file = hashOfAccessionFilesForSDRF.get(experimentAccession);
    if (file == null) {
      return returnHash;
    }
    String passage = getPassageFromFile(file);
    if (!passage.isEmpty()) {
      passage = passage.replaceAll("\\s+$", "");
      
      String[] rowString = passage.split("\n");
      String[][] table = stringToTable(passage);
      
      boolean hasADFColumn = false;
      int adfColumn = 0;
      
      // Look for the column named "Array Data File"
      for (int i = 0; i < table[0].length; i++) {
        if (table[0][i].equals("Array Data File")) {
          hasADFColumn = true;
          adfColumn = i;
          break;
        }
      }
      // Put into Hash whether or not row contains EFO
      if (hasADFColumn == true) {
        for (int i = 1; i < table.length; i++) {
          // System.out.println(table[i][adfColumn]+ "\t" +getIfPassageContainsEFO(rowString[i],
          // EFOAccessionId));
          // Make sure that CEL Files which have at least one reference to blood are still reported
          if (returnHash.get(table[i][adfColumn]) == null) {
            returnHash.put(table[i][adfColumn], getIfPassageContainsOntologyTerm(rowString[i],
              listOfOntologyAccessionIds));
          } else {
            if (returnHash.get(table[i][adfColumn]) == true) {
              // skip
              // Keep true, true
            } else {
              // Replace False with whether or not the passage contains efo
              returnHash.put(table[i][adfColumn], getIfPassageContainsOntologyTerm(rowString[i],
                listOfOntologyAccessionIds));
            }
          }
        }
      } else {
        System.out.println(experimentAccession + " does not have a ADF column");
      }
    }
    return returnHash;
  }
  
  /**
   * Gets the 'experiment accession id' to 'integer' hash map that is used to determine how many times a word
   * related to the list of ontology accession ids occurs in the experiment
   * 
   * @param listOfExperimentAccessionIds
   *          the list of experiment accessions
   * @param scope
   *          the filetype. Can count IDF, SDRF, or both
   * @param listOfEFOAccessionIds
   *          the EFO accession ids
   * @return the 'experiment accession id' to 'integer' hash map for the list of accessions ie
   *         {E-GEOD-10000=>10}
   */
  public HashMap<String,Integer> getCountHashMapForListOfAccessions(List<String> listOfExperimentAccessionIds,
                                                                    Scope scope,
                                                                    String... listOfEFOAccessionIds) {
    downloadFilesFromFTP(listOfExperimentAccessionIds);
    
    HashMap<String,Integer> returnHashMap = new HashMap<String,Integer>();
    
    HashMap<String,File> hashMapToUse;
    if (scope == Scope.sdrf) {
      hashMapToUse = hashOfAccessionFilesForSDRF;
    } else if (scope == Scope.idf) {
      hashMapToUse = hashOfAccessionFilesForIDF;
    } else if (scope == Scope.both) {
      HashMap<String,Integer> sdrfHash = getCountHashMapForListOfAccessions(listOfExperimentAccessionIds,
        Scope.idf, listOfEFOAccessionIds);
      HashMap<String,Integer> idfHash = getCountHashMapForListOfAccessions(listOfExperimentAccessionIds,
        Scope.sdrf, listOfEFOAccessionIds);
      for (String accession : listOfExperimentAccessionIds) {
        if (idfHash.containsKey(accession) && sdrfHash.containsKey(accession)) {
          returnHashMap.put(accession, Integer.valueOf(idfHash.get(accession))
                                       + Integer.valueOf(sdrfHash.get(accession)));
        }
      }
      return returnHashMap;
    } else {
      // Theoretically Never Reached
      return null;
    }
    
    for (String accession : listOfExperimentAccessionIds) {
      File file = hashMapToUse.get(accession);
      if (file != null) {
        String passage = getPassageFromFile(file);
        if (passage != "") {
          returnHashMap.put(accession, getTotalCountOfRelatedOntologyTermsInPassage(passage,
            listOfEFOAccessionIds));
        }
      }
    }
    
    return returnHashMap;
  }
  
  /**
   * Gets the the 'CEL file name' to 'integer' hashmap that is used to determine how many times a word related
   * to a member in the list of ontology accession ids used in the row.
   * 
   * @param experimentAccessionId
   *          the experiment accession id to look up
   * @param listOfOntologyAccessionIds
   *          the list of ontology accession ids
   * @return the 'CEL file name' to 'integer' hashmap for the specified experiment ie {sample.cel=>5}
   */
  public HashMap<String,Integer> getCountHashMapForExperimentCELFiles(String experimentAccessionId,
                                                                      String... listOfOntologyAccessionIds) {
    HashMap<String,Integer> returnHash = new HashMap<String,Integer>();
    downloadFilesFromFTP(experimentAccessionId);
    
    File file = hashOfAccessionFilesForSDRF.get(experimentAccessionId);
    
    if (file == null) {
      return returnHash;
    }
    String passage = getPassageFromFile(file);
    
    // Read a sdrf file and put it into a 2d array
    if (!passage.isEmpty()) {
      passage = passage.replaceAll("\\s+$", "");
      
      String[] rowString = passage.split("\n");
      String[][] table = stringToTable(passage);
      
      boolean hasADFColumn = false;
      int adfColumn = 0;
      
      // Look for the column named "Array Data File"
      for (int i = 0; i < table[0].length; i++) {
        if (table[0][i].equals("Array Data File")) {
          hasADFColumn = true;
          adfColumn = i;
          break;
        }
      }
      // Put into Hash the count of how many children were found
      if (hasADFColumn == true) {
        for (int i = 1; i < table.length; i++) {
          // System.out.println(table[i][adfColumn]+ "\t"
          // +getIfPassageContainsEFO(rowString[i], EFOAccessionId));
          // Make sure that CEL Files which have at least one
          // reference to EFO are still reported and
          // add the values
          if (returnHash.get(table[i][adfColumn]) == null) {
            returnHash.put(table[i][adfColumn], getTotalCountOfRelatedOntologyTermsInPassage(rowString[i],
              listOfOntologyAccessionIds));
          } else {
            // put the sum of the samples in
            returnHash.put(table[i][adfColumn], returnHash.get(table[i][adfColumn]).intValue()
                                                + +getTotalCountOfRelatedOntologyTermsInPassage(rowString[i],
                                                  listOfOntologyAccessionIds));
          }
        }
      } else {
        System.out.println(experimentAccessionId + " does not have a ADF column");
      }
    }
    return returnHash;
  }
  
  /**
   * Gets a 'CEL file name' to 'hashmap' hashmap. The inner hashmap is an 'ontology term' to 'integer'
   * hashMap. This hash map is used to determine what words a CEL file has been annotated with and how many.
   * 
   * @param experimentAccessionId
   *          the experiment accesion id
   * @param listOfOntologyAccessionIds
   *          the list of ontology accession ids
   * 
   * @return a hashMap which relates a 'CEL file name' to the words it has been annotated with and how many.
   *         {Sample.CEL=>{Thymus=>2,Blood=>2}}
   * 
   */
  public HashMap<String,HashMap<String,Integer>> getCountOfEachTermPerSample(String experimentAccessionId,
                                                                             String... listOfOntologyAccessionIds) {
    
    downloadFilesFromFTP(experimentAccessionId);
    
    HashMap<String,HashMap<String,Integer>> returnHash = new HashMap<String,HashMap<String,Integer>>();
    File file = hashOfAccessionFilesForSDRF.get(experimentAccessionId);
    
    if (file == null) {
      return returnHash;
    }
    String passage = getPassageFromFile(file);
    
    // Read a sdrf file and put it into a 2d array
    if (!passage.isEmpty()) {
      passage = passage.replaceAll("\\s+$", "");
      
      String[] rowString = passage.split("\n");
      String[][] table = stringToTable(passage);
      
      boolean hasADFColumn = false;
      int adfColumn = 0;
      
      // Look for the column named "Array Data File"
      for (int i = 0; i < table[0].length; i++) {
        if (table[0][i].equals("Array Data File")) {
          hasADFColumn = true;
          adfColumn = i;
          break;
        }
      }
      // Put into Hash the sample and the terms it found
      if (hasADFColumn == true) {
        for (int i = 1; i < table.length; i++) {
          // System.out.println(table[i][adfColumn]+ "\t"
          // +getIfPassageContainsEFO(rowString[i], EFOAccessionId));
          
          // Make sure that CEL Files which have at least one
          // reference to OntologyTerm are still reported and
          // add the values
          if (returnHash.get(table[i][adfColumn]) == null) {
            returnHash.put(table[i][adfColumn], passageToTermHash(rowString[i], listOfOntologyAccessionIds));
          } else {
            // Combine hashes
            HashMap<String,Integer> newHash = passageToTermHash(rowString[i], listOfOntologyAccessionIds);
            
            for (String term : newHash.keySet()) {
              if (returnHash.get(table[i][adfColumn]).get(term) != null) {
                int newSum = returnHash.get(table[i][adfColumn]).get(term).intValue()
                             + newHash.get(term).intValue();
                returnHash.get(table[i][adfColumn]).put(term, newSum);
              } else {
                returnHash.get(table[i][adfColumn]).put(term, newHash.get(term));
              }
            }
          }
        }
      } else {
        System.out.println(experimentAccessionId + " does not have a ADF column");
      }
    }
    
    return returnHash;
    
  }
  
  /**
   * Gets the 'OntologyTerm' to 'integer' hashmap that is used to identify how many words related to the list
   * Of ontology accesion ids are mentioned in the experiment.
   * 
   * @param experimentAccession
   *          the experiment accession
   * @param listOfOntologyAccessionIds
   *          the ontology accession ids
   * @param scope
   *          the scope of the search. Can search IDF, SDRF, or both
   * 
   * @return the 'OntologyTerm' to 'integer' hashmap ie {blood=>5}
   */
  public HashMap<String,Integer> getCountOfEachTermInExperiment(String experimentAccession,
                                                                Scope scope,
                                                                String... listOfOntologyAccessionIds) {
    // Download Experiment
    downloadFilesFromFTP(experimentAccession);
    
    // Get a list of children EFO
    List<String> listOfChildren = getRelatedTerms(listOfOntologyAccessionIds);
    
    // Make a hashmap to return
    HashMap<String,Integer> returnHashMap = new HashMap<String,Integer>();
    
    HashMap<String,File> hashMapToUse;
    if (scope == Scope.sdrf) {
      hashMapToUse = hashOfAccessionFilesForSDRF;
    } else if (scope == Scope.idf) {
      hashMapToUse = hashOfAccessionFilesForIDF;
    } else if (scope == Scope.both) {
      HashMap<String,Integer> sdrfHash = getCountOfEachTermInExperiment(experimentAccession, Scope.idf,
        listOfOntologyAccessionIds);
      HashMap<String,Integer> idfHash = getCountOfEachTermInExperiment(experimentAccession, Scope.sdrf,
        listOfOntologyAccessionIds);
      
      for (String efoTerm : sdrfHash.keySet()) {
        returnHashMap.put(efoTerm, sdrfHash.get(efoTerm));
      }
      for (String efoTerm : idfHash.keySet()) {
        if (returnHashMap.get(efoTerm) != null) {
          returnHashMap.put(efoTerm, (Integer.valueOf(idfHash.get(efoTerm)) + Integer.valueOf(sdrfHash
              .get(efoTerm))));
        } else {
          // Case where idf has a term that is not mentioned in the sdrf
          returnHashMap.put(efoTerm, idfHash.get(efoTerm));
        }
      }
      return returnHashMap;
    } else {
      return null;
    }
    File file = hashMapToUse.get(experimentAccession);
    if (file != null) {
      String passage = getPassageFromFile(file);
      if (passage != "") {
        for (String dictWord : listOfChildren) {
          int count = getCountFromPassage(dictWord, passage);
          if (count != 0) {
            returnHashMap.put(dictWord, count);
          }
        }
      }
    }
    return returnHashMap;
  }
  
  /**
   * A string representation of getCountOfEachTermInExperiment
   * 
   * @param experimentAccession
   *          the experiment accession
   * @param listOfOntologyAccessionIds
   *          the ontology accession ids
   * @param scope
   *          the scope of the search. Can search IDF, SDRF, or both
   * @return A string representation of getCountOfEachTermInExperiment
   */
  public String getCountOfEachTermInExperimentAsString(String experimentAccession,
                                                       Scope filetype,
                                                       String... listOfOntologyAccessionIds) {
    HashMap<String,Integer> countMap = getCountOfEachTermInExperiment(experimentAccession, filetype,
      listOfOntologyAccessionIds);
    String output = "";
    
    for (String key : countMap.keySet()) {
      output += key + ":" + countMap.get(key) + ";";
    }
    return output;
    
  }
  
  /**
   * Gets the 'experiment accession id' to 'integer' hashmap that is used to determine how many assays there
   * are per experiment
   * 
   * @param listOfExperimentAccessionIds
   *          the list of experiment accession ids
   * @return the 'experiment accession id' to 'integer' hashmap that is used to determine how many assays
   *         there are per experiment
   */
  public HashMap<String,Integer> getCountOfAssaysPerExperiment(List<String> listOfExperimentAccessionIds) {
    HashMap<String,Integer> returnHash = new HashMap<String,Integer>();
    
    downloadFilesFromFTP(listOfExperimentAccessionIds);
    
    for (String experimentAccession : listOfExperimentAccessionIds) {
      
      if (hashOfExperimentAccessionToCountOfAssays.get(experimentAccession) == null) {
        // File exists
        if (hashOfAccessionFilesForSDRF.get(experimentAccession) != null) {
          String[][] sdrf2D = stringToTable(getPassageFromFile(hashOfAccessionFilesForSDRF
              .get(experimentAccession)));
          hashOfExperimentAccessionToCountOfAssays.put(experimentAccession, sdrf2D.length - 1);
        }
      } else {
        // Do nothing extra
      }
      returnHash.put(experimentAccession, hashOfExperimentAccessionToCountOfAssays.get(experimentAccession));
    }
    return returnHash;
    
  }
  
  /**
   * Gets the experimentAccession to IDF File hash
   * 
   * @return experimentAccession to IDF File hash.
   */
  public HashMap<String,File> getCompleteIDFHash() {
    return hashOfAccessionFilesForIDF;
  }
  
  /**
   * Gets the experimentAccession to SDRF File hash
   * 
   * @return experimentAccession to SDRF File hash.
   */
  public HashMap<String,File> getCompleteSDRFHash() {
    return hashOfAccessionFilesForSDRF;
  }
  
  /**
   * Download files from FTP. If the file exists, it does not attempt to redownload it.
   * 
   * @param listOfExperimentAccessions
   *          the list of experiment accessions
   */
  public void downloadFilesFromFTP(List<String> listOfExperimentAccessions) {
    
    FTPClient client = new FTPClient();
    FileOutputStream fos_sdrf = null;
    FileOutputStream fos_idf = null;
    
    File temp_sdrf = null;
    File temp_idf = null;
    
    try {
      client.connect(arrayExpressFtp);
      client.login("anonymous", "");
      
      for (String accession : listOfExperimentAccessions) {
        // Only download the files which are needed
        if (hashOfAccessionFilesForSDRF.containsKey(accession)
            && hashOfAccessionFilesForIDF.containsKey(accession)) {
          continue;
        }
        
        try {
          temp_sdrf = File.createTempFile("kama_", ".tmp");
          // temp_sdrf.deleteOnExit();
          
          temp_idf = File.createTempFile("kama_", ".tmp");
          // temp_idf.deleteOnExit();
          
          fos_sdrf = new FileOutputStream(temp_sdrf);
          fos_idf = new FileOutputStream(temp_idf);
          
          String pipeline = accession.substring(2, 6);
          String sdrfFile = accession + ".sdrf.txt";
          String idfFile = accession + ".idf.txt";
          
          client.retrieveFile(arrayExpressFtpPath + pipeline + "/" + accession + "/" + sdrfFile, fos_sdrf);
          
          if (client.getReplyString().contains("226")) {
            System.out.println(sdrfFile + "\tFile Received");
            hashOfAccessionFilesForSDRF.put(accession, temp_sdrf);
          } else {
            System.out.println(sdrfFile + "\tFailed");
          }
          
          client.retrieveFile(arrayExpressFtpPath + pipeline + "/" + accession + "/" + idfFile, fos_idf);
          
          if (client.getReplyString().contains("226")) {
            System.out.println(idfFile + "\tFile Received");
            hashOfAccessionFilesForIDF.put(accession, temp_idf);
          } else {
            System.out.println(idfFile + "\tFailed");
          }
          
        } catch (IOException e) {
          e.printStackTrace();
        } finally {
          try {
            if (fos_sdrf != null) {
              fos_sdrf.close();
              fos_idf.close();
            }
          } catch (IOException e) {
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
   * Download single accession file from ftp. Basically, it wraps an arraylist around the accession and calls
   * the other download method
   * 
   * @param experimentAccessionID
   *          is the experiment accession ids
   */
  public void downloadFilesFromFTP(String experimentAccessionID) {
    ArrayList<String> temp = new ArrayList<String>();
    temp.add(experimentAccessionID);
    downloadFilesFromFTP(temp);
  }
  
  /**
   * String to table method
   * 
   * @param oneLongString
   *          is the input string, It is simply the raw sdrf or raw idf.
   * @return the string[][] is the 2d array representation of table ([row][column])
   */
  private String[][] stringToTable(String oneLongString) {
    
    // 01_Create Array Of rows by splitting on new line
    String[] rows = oneLongString.split("\\r?\\n");
    
    // 02_Create Array of rows that you will return
    int columnsLength = rows[0].split("\\t").length;
    
    String[][] rowArray = new String[rows.length][columnsLength];
    // 03_For each row, split on the tab and put the column into the row array
    for (int i = 0; i < rows.length; i++) {
      String[] column = rows[i].split("\\t");
      if (column.length < columnsLength) {
        for (int k = 0; k < columnsLength; k++) {
          if (k < column.length) {
            rowArray[i][k] = column[k];
          } else {
            rowArray[i][k] = "";
          }
        }
        
      } else if (column.length == columnsLength) {
        rowArray[i] = column;
      } else {
        System.err.println("There is a row that is longer than the first row.");
      }
    }
    return rowArray;
  }
  
  private HashMap<String,Integer> passageToTermHash(String passage, String... listOfEFOAccessionIds) {
    List<String> listOfChildren = getRelatedTerms(listOfEFOAccessionIds);
    HashMap<String,Integer> returnHashMap = new HashMap<String,Integer>();
    for (String dictWord : listOfChildren) {
      int count = getCountFromPassage(dictWord, passage);
      if (count != 0) {
        returnHashMap.put(dictWord, count);
      }
    }
    return returnHashMap;
  }
  
  private String getPassageFromFile(File file) {
    BufferedReader br;
    try {
      br = new BufferedReader(new FileReader(file));
      String text;
      StringBuilder passage = new StringBuilder();
      while ((text = br.readLine()) != null) {
        passage.append(text + " \n"); // Space is because monq does not recognize line break!
      }
      return passage.toString().trim();
      
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return "";
  }
  
  /**
   * Counts how many times the term appears in the passage
   * 
   * @param term
   *          the term to be looked for
   * @param passage
   *          the passage
   * @return a count of the number times the term appears in the passage
   */
  private int getCountFromPassage(String term, String passage) {
    
    // First make regex
    String regEx = Term2Re.convert(term);
    
    try {
      Nfa nfa = new Nfa(regEx, new Printf("%0"));
      
      // Compile into the Dfa, specify that all text not matching any
      // regular expression shall be copied from input to output
      Dfa dfa = nfa.compile(DfaRun.UNMATCHED_DROP);
      
      // get a machinery (DfaRun) to operate the Dfa
      DfaRun r = new DfaRun(dfa);
      
      // Get only the filtered text
      String filtered = r.filter(passage);
      
      if (filtered.length() != 0) {
        return (filtered.split("[-\\s+]").length) / (term.split("[-\\s+]").length);
      }
    } catch (ReSyntaxException e) {
      e.printStackTrace();
    } catch (CompileDfaException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    return 0;
    
  }
}
