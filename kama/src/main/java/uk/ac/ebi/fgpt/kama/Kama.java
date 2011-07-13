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
import java.util.Map;

import monq.ie.Term2Re;
import monq.jfa.CompileDfaException;
import monq.jfa.Dfa;
import monq.jfa.DfaRun;
import monq.jfa.Nfa;
import monq.jfa.ReSyntaxException;
import monq.jfa.actions.Copy;

import org.apache.commons.net.ftp.FTPClient;

import uk.ac.ebi.ontocat.OntologyServiceException;

/**
 * Kama - quicK pAss for Meta Analysis
 * 
 * @author Vincent Xue
 * @date Tuesday, June 21 2011
 * 
 *       TODO Improve KAMA by including AccessionIds in the dictionary
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
  
  private String arrayExpressFtp = "ftp.ebi.ac.uk";
  private String arrayExpressFtpPath = "/pub/databases/microarray/data/experiment/";
  private Map<String,File> mapOfAccessionFilesForSDRF = new HashMap<String,File>();
  private Map<String,File> mapOfAccessionFilesForIDF = new HashMap<String,File>();
  private Map<String,Integer> mapOfExperimentAccessionToCountOfAssays = new HashMap<String,Integer>();
  private OntologyFunctions ontoFunctions;
  
  /**
   * Users will usually use one listOfOntologyIds consistently. By using this map, we can speed up the
   * retrieval of the dictionary and make sure that the nfa doesn't get created too many times.
   */
  private Map<Integer,String[]> mapOfHashCodeToArray = new HashMap<Integer,String[]>();
  private Map<Integer,Nfa> hashCodeToNfa = new HashMap<Integer,Nfa>();
  
  // Default uses version 142 of EFO as Ontology;
  public Kama() throws OntologyServiceException {
    try {
      ontoFunctions = new OntologyFunctions(this.getClass().getClassLoader().getResource(
        "EFO_inferred_v142.owl").toURI());
    } catch (URISyntaxException e) {
      System.err.println("DEFAULT EFO_inferred_v142.owl IS NOT FOUND");
      e.printStackTrace();
      return;
    }
  }
  
  // Constructor to use a user defined ontology file (Can be OBO or OWL);
  public Kama(File owlFile) throws OntologyServiceException {
    ontoFunctions = new OntologyFunctions(owlFile.toURI());
  }
  
  /**
   * Gets an array of all the class's children, the class itself and all related synonyms for each ontology
   * accession id in the list.
   * 
   * @param listOfOntologyAccessionIds
   *          the list of ontology accession ids for which you want to retrieve the children from.
   * @return an array of the all the class's children, the class itself, and related synonyms.
   */
  public String[] getDictionaryOfTermsFromOntologyIds(String... listOfOntologyAccessionIds) {
    // First check to see if this listOfOntologyAccessionIds has been called before
    if (mapOfHashCodeToArray.containsKey(listOfOntologyAccessionIds.hashCode())) {
      return mapOfHashCodeToArray.get(listOfOntologyAccessionIds.hashCode());
    }
    
    // If the application has reached this far, then this is a "new" dictionary.
    ArrayList<String> returnListOfEFO = new ArrayList<String>();
    for (String efoAccessionID : listOfOntologyAccessionIds) {
      List<String> relatedTerms;
      if ((relatedTerms = ontoFunctions.getChildrenAndRelatedTerms(efoAccessionID)) != null) {
        returnListOfEFO.addAll(relatedTerms);
      }
    }
    String[] returnArray = new String[returnListOfEFO.size()];
    returnListOfEFO.toArray(returnArray);
    
    mapOfHashCodeToArray.put(listOfOntologyAccessionIds.hashCode(), returnArray);
    return returnArray;
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
   * @throws MonqException
   */
  public boolean getIfPassageContainsOntologyTerm(String passage, String... listOfOntologyAccessionIds) throws MonqException {
    String[] dictionary = getDictionaryOfTermsFromOntologyIds(listOfOntologyAccessionIds);
    
    if (getCountFromPassage(passage, getNfa(dictionary)).size() != 0) {
      return true;
    }
    return false;
  }
  
  /**
   * Gets the total count of all related ontology terms in the passage
   * 
   * @param passage
   *          the passage to be checked
   * @param listOfOntologyAccessionIds
   *          the list of ontology accession ids that will be looked for
   * @return the total count all related ontology terms in the passage
   * @throws MonqException
   */
  public int getTotalCountOfRelatedOntologyTermsInPassage(String passage,
                                                          String... listOfOntologyAccessionIds) throws MonqException {
    String[] listOfChildren = getDictionaryOfTermsFromOntologyIds(listOfOntologyAccessionIds);
    int countOfTermsFound = 0;
    Map<String,Integer> countMap = getCountFromPassage(passage, getNfa(listOfChildren));
    for (Integer value : countMap.values()) {
      countOfTermsFound += value.intValue();
    }
    return countOfTermsFound;
  }
  
  /**
   * Gets the 'experiment accession id' to 'boolean' map that is used to determine whether or not a certain
   * experiment has a member of the EFO class.
   * 
   * @param listOfExperimentAccessionIds
   *          the list of experiment accession ids
   * @param scope
   *          the scope of the search (sdrf or idf or both)
   * @param listOfOntologyAccessionIds
   *          a list of Ontology accession ids
   * @return the 'experiment accession id' to 'boolean' map that is used to determine whether a certain
   *         experiment has a word related to the list of ontology accession ids. ie {E-GEOD-1000=>true}
   * @throws MonqException
   */
  public Map<String,Boolean> getTrueFalseMapForListOfAccessions(ArrayList<String> listOfExperimentAccessionIds,
                                                                Scope scope,
                                                                String... listOfOntologyAccessionIds) throws MonqException {
    downloadFilesFromFTP(listOfExperimentAccessionIds);
    
    Map<String,Boolean> returnMap = new HashMap<String,Boolean>();
    
    Map<String,File> mapToUse;
    if (scope == Scope.sdrf) {
      mapToUse = mapOfAccessionFilesForSDRF;
    } else if (scope == Scope.idf) {
      mapToUse = mapOfAccessionFilesForIDF;
    } else if (scope == Scope.both) {
      Map<String,Boolean> sdrfMap = getTrueFalseMapForListOfAccessions(listOfExperimentAccessionIds,
        Scope.idf, listOfOntologyAccessionIds);
      Map<String,Boolean> idfMap = getTrueFalseMapForListOfAccessions(listOfExperimentAccessionIds,
        Scope.sdrf, listOfOntologyAccessionIds);
      for (String accession : listOfExperimentAccessionIds) {
        if ((idfMap.containsKey(accession) && sdrfMap.containsKey(accession))
            && (idfMap.get(accession) == true || sdrfMap.get(accession) == true)) {
          returnMap.put(accession, true);
        } else {
          returnMap.put(accession, false);
        }
      }
      return returnMap;
    } else {
      // Theoretically Never Reached
      return null;
    }
    
    for (String accession : listOfExperimentAccessionIds) {
      File file = mapToUse.get(accession);
      if (file != null) {
        String passage = getPassageFromFile(file);
        if (passage != "") {
          returnMap.put(accession, getIfPassageContainsOntologyTerm(passage, listOfOntologyAccessionIds));
        }
        
      }
    }
    
    return returnMap;
  }
  
  /**
   * Gets the 'CEL file name' to 'boolean' map that is used to find all the unique CEL files in the SDRF and
   * determine whether or not the CEL file (as annotated in the SDRF) mentions a term related to the list Of
   * ontology accessionIds.
   * 
   * @param experimentAccession
   *          the experiment accession
   * @param listOfOntologyAccessionIds
   *          a list of EFO accession ids
   * @return the 'CEL file name' to 'boolean' map for the specified experiment ie {sample.cel=>true}
   * @throws MonqException
   */
  public Map<String,Boolean> getTrueFalseMapForExperimentCELFiles(String experimentAccession,
                                                                  String... listOfOntologyAccessionIds) throws MonqException {
    Map<String,Boolean> returnMap = new HashMap<String,Boolean>();
    
    downloadFileFromFTP(experimentAccession);
    
    File file = mapOfAccessionFilesForSDRF.get(experimentAccession);
    if (file == null) {
      return returnMap;
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
      // Put into map whether or not row contains EFO
      if (hasADFColumn == true) {
        for (int i = 1; i < table.length; i++) {
          // System.out.println(table[i][adfColumn]+ "\t" +getIfPassageContainsEFO(rowString[i],
          // EFOAccessionId));
          // Make sure that CEL Files which have at least one reference to blood are still reported
          if (returnMap.get(table[i][adfColumn]) == null) {
            returnMap.put(table[i][adfColumn], getIfPassageContainsOntologyTerm(rowString[i],
              listOfOntologyAccessionIds));
          } else {
            if (returnMap.get(table[i][adfColumn]) == true) {
              // skip
              // Keep true, true
            } else {
              // Replace False with whether or not the passage contains efo
              returnMap.put(table[i][adfColumn], getIfPassageContainsOntologyTerm(rowString[i],
                listOfOntologyAccessionIds));
            }
          }
        }
      } else {
        System.out.println(experimentAccession + " does not have a ADF column");
      }
    }
    return returnMap;
  }
  
  /**
   * Gets the 'experiment accession id' to 'integer' map that is used to determine how many times a word
   * related to the list of ontology accession ids occurs in the experiment
   * 
   * @param listOfExperimentAccessionIds
   *          the list of experiment accessions
   * @param scope
   *          the filetype. Can count IDF, SDRF, or both
   * @param listOfEFOAccessionIds
   *          the EFO accession ids
   * @return the 'experiment accession id' to 'integer' map for the list of accessions ie {E-GEOD-10000=>10}
   * @throws MonqException
   */
  public Map<String,Integer> getCountMapForListOfAccessions(List<String> listOfExperimentAccessionIds,
                                                            Scope scope,
                                                            String... listOfEFOAccessionIds) throws MonqException {
    downloadFilesFromFTP(listOfExperimentAccessionIds);
    
    Map<String,Integer> returnMap = new HashMap<String,Integer>();
    
    Map<String,File> mapToUse;
    if (scope == Scope.sdrf) {
      mapToUse = mapOfAccessionFilesForSDRF;
    } else if (scope == Scope.idf) {
      mapToUse = mapOfAccessionFilesForIDF;
    } else if (scope == Scope.both) {
      Map<String,Integer> sdrfMap = getCountMapForListOfAccessions(listOfExperimentAccessionIds, Scope.idf,
        listOfEFOAccessionIds);
      Map<String,Integer> idfMap = getCountMapForListOfAccessions(listOfExperimentAccessionIds, Scope.sdrf,
        listOfEFOAccessionIds);
      for (String accession : listOfExperimentAccessionIds) {
        if (idfMap.containsKey(accession) && sdrfMap.containsKey(accession)) {
          returnMap.put(accession, Integer.valueOf(idfMap.get(accession))
                                   + Integer.valueOf(sdrfMap.get(accession)));
        }
      }
      return returnMap;
    } else {
      // Theoretically Never Reached
      return null;
    }
    
    for (String accession : listOfExperimentAccessionIds) {
      File file = mapToUse.get(accession);
      if (file != null) {
        String passage = getPassageFromFile(file);
        if (passage != "") {
          returnMap.put(accession, getTotalCountOfRelatedOntologyTermsInPassage(passage,
            listOfEFOAccessionIds));
        }
      }
    }
    
    return returnMap;
  }
  
  /**
   * Gets the the 'CEL file name' to 'integer' map that is used to determine how many times a word related to
   * a member in the list of ontology accession ids used in the row.
   * 
   * @param experimentAccessionId
   *          the experiment accession id to look up
   * @param listOfOntologyAccessionIds
   *          the list of ontology accession ids
   * @return the 'CEL file name' to 'integer' map for the specified experiment ie {sample.cel=>5}
   * @throws MonqException
   */
  public Map<String,Integer> getCountMapForExperimentCELFiles(String experimentAccessionId,
                                                              String... listOfOntologyAccessionIds) throws MonqException {
    Map<String,Integer> returnMap = new HashMap<String,Integer>();
    downloadFileFromFTP(experimentAccessionId);
    
    File file = mapOfAccessionFilesForSDRF.get(experimentAccessionId);
    
    if (file == null) {
      return returnMap;
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
      // Put into map the count of how many children were found
      if (hasADFColumn == true) {
        for (int i = 1; i < table.length; i++) {
          // System.out.println(table[i][adfColumn]+ "\t"
          // +getIfPassageContainsEFO(rowString[i], EFOAccessionId));
          // Make sure that CEL Files which have at least one
          // reference to EFO are still reported and
          // add the values
          if (returnMap.get(table[i][adfColumn]) == null) {
            returnMap.put(table[i][adfColumn], getTotalCountOfRelatedOntologyTermsInPassage(rowString[i],
              listOfOntologyAccessionIds));
          } else {
            // put the sum of the samples in
            returnMap.put(table[i][adfColumn], returnMap.get(table[i][adfColumn]).intValue()
                                               + +getTotalCountOfRelatedOntologyTermsInPassage(rowString[i],
                                                 listOfOntologyAccessionIds));
          }
        }
      } else {
        System.out.println(experimentAccessionId + " does not have a ADF column");
      }
    }
    return returnMap;
  }
  
  /**
   * Gets a 'CEL file name' to 'map' map. The inner map is an 'ontology term' to 'integer' map. This map is
   * used to determine what words a CEL file has been annotated with and how many.
   * 
   * @param experimentAccessionId
   *          the experiment accesion id
   * @param listOfOntologyAccessionIds
   *          the list of ontology accession ids
   * 
   * @return a Map which relates a 'CEL file name' to the words it has been annotated with and how many.
   *         {Sample.CEL=>{Thymus=>2,Blood=>2}}
   * @throws MonqException
   * 
   */
  public Map<String,Map<String,Integer>> getCountOfEachTermPerSample(String experimentAccessionId,
                                                                     String... listOfOntologyAccessionIds) throws MonqException {
    
    String[] dictionary = getDictionaryOfTermsFromOntologyIds(listOfOntologyAccessionIds);
    downloadFileFromFTP(experimentAccessionId);
    
    Map<String,Map<String,Integer>> returnMap = new HashMap<String,Map<String,Integer>>();
    File file = mapOfAccessionFilesForSDRF.get(experimentAccessionId);
    
    if (file == null) {
      return returnMap;
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
      // Put into map the sample and the terms it found
      if (hasADFColumn == true) {
        for (int i = 1; i < table.length; i++) {
          // System.out.println(table[i][adfColumn]+ "\t"
          // +getIfPassageContainsEFO(rowString[i], EFOAccessionId));
          
          // Make sure that CEL Files which have at least one
          // reference to OntologyTerm are still reported and
          // add the values
          if (returnMap.get(table[i][adfColumn]) == null) {
            returnMap.put(table[i][adfColumn], getCountFromPassage(rowString[i], getNfa(dictionary)));
          } else {
            // Combine maps
            Map<String,Integer> newMap = getCountFromPassage(rowString[i], getNfa(dictionary));
            
            for (String term : newMap.keySet()) {
              if (returnMap.get(table[i][adfColumn]).get(term) != null) {
                int newSum = returnMap.get(table[i][adfColumn]).get(term).intValue()
                             + newMap.get(term).intValue();
                returnMap.get(table[i][adfColumn]).put(term, newSum);
              } else {
                returnMap.get(table[i][adfColumn]).put(term, newMap.get(term));
              }
            }
          }
        }
      } else {
        System.out.println(experimentAccessionId + " does not have a ADF column");
      }
    }
    
    return returnMap;
    
  }
  
  /**
   * Gets the 'OntologyTerm' to 'integer' map that is used to identify how many words related to the list Of
   * ontology accesion ids are mentioned in the experiment.
   * 
   * @param experimentAccession
   *          the experiment accession
   * @param listOfOntologyAccessionIds
   *          the ontology accession ids
   * @param scope
   *          the scope of the search. Can search IDF, SDRF, or both
   * 
   * @return the 'OntologyTerm' to 'integer' map ie {blood=>5}
   * @throws MonqException
   */
  public Map<String,Integer> getCountOfEachTermInExperiment(String experimentAccession,
                                                            Scope scope,
                                                            String... listOfOntologyAccessionIds) throws MonqException {
    // Download Experiment
    downloadFileFromFTP(experimentAccession);
    
    // Get a list of children EFO
    String[] dictionary = getDictionaryOfTermsFromOntologyIds(listOfOntologyAccessionIds);
    
    // Make a map to return
    Map<String,Integer> returnMap = new HashMap<String,Integer>();
    
    Map<String,File> mapToUse;
    if (scope == Scope.sdrf) {
      mapToUse = mapOfAccessionFilesForSDRF;
    } else if (scope == Scope.idf) {
      mapToUse = mapOfAccessionFilesForIDF;
    } else if (scope == Scope.both) {
      Map<String,Integer> sdrfMap = getCountOfEachTermInExperiment(experimentAccession, Scope.idf,
        listOfOntologyAccessionIds);
      Map<String,Integer> idfMap = getCountOfEachTermInExperiment(experimentAccession, Scope.sdrf,
        listOfOntologyAccessionIds);
      
      for (String efoTerm : sdrfMap.keySet()) {
        returnMap.put(efoTerm, sdrfMap.get(efoTerm));
      }
      for (String efoTerm : idfMap.keySet()) {
        if (returnMap.get(efoTerm) != null) {
          returnMap.put(efoTerm, (Integer.valueOf(idfMap.get(efoTerm)) + Integer
              .valueOf(sdrfMap.get(efoTerm))));
        } else {
          // Case where idf has a term that is not mentioned in the sdrf
          returnMap.put(efoTerm, idfMap.get(efoTerm));
        }
      }
      return returnMap;
    } else {
      return null;
    }
    File file = mapToUse.get(experimentAccession);
    if (file != null) {
      String passage = getPassageFromFile(file);
      if (passage != "") {
        return getCountFromPassage(passage, getNfa(dictionary));
      }
    }
    return returnMap;
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
   * @throws MonqException
   */
  public String getCountOfEachTermInExperimentAsString(String experimentAccession,
                                                       Scope filetype,
                                                       String... listOfOntologyAccessionIds) throws MonqException {
    Map<String,Integer> countMap = getCountOfEachTermInExperiment(experimentAccession, filetype,
      listOfOntologyAccessionIds);
    String output = "";
    
    for (String key : countMap.keySet()) {
      output += key + ":" + countMap.get(key) + ";";
    }
    return output;
    
  }
  
  /**
   * Gets the 'experiment accession id' to 'integer' map that is used to determine how many assays there are
   * per experiment
   * 
   * @param listOfExperimentAccessionIds
   *          the list of experiment accession ids
   * @return the 'experiment accession id' to 'integer' map that is used to determine how many assays there
   *         are per experiment
   */
  public Map<String,Integer> getCountOfAssaysPerExperiment(List<String> listOfExperimentAccessionIds) {
    Map<String,Integer> returnMap = new HashMap<String,Integer>();
    
    // Step 1 - Download Files
    downloadFilesFromFTP(listOfExperimentAccessionIds);
    
    // Step 2 - For each file, check to see if mapOfExperimentAccessionToCountOfAssays
    // has the value. If it doesn't then put the value in mapOfExperimentAccessionToCountOfAssays
    
    for (String experimentAccession : listOfExperimentAccessionIds) {
      
      if (mapOfExperimentAccessionToCountOfAssays.get(experimentAccession) == null) {
        // File exists
        if (mapOfAccessionFilesForSDRF.get(experimentAccession) != null) {
          String[][] sdrf2D = stringToTable(getPassageFromFile(mapOfAccessionFilesForSDRF
              .get(experimentAccession)));
          mapOfExperimentAccessionToCountOfAssays.put(experimentAccession, sdrf2D.length - 1);
        }
      } else {
        // Do nothing extra
      }
      returnMap.put(experimentAccession, mapOfExperimentAccessionToCountOfAssays.get(experimentAccession));
    }
    return returnMap;
    
  }
  
  /**
   * Gets the experimentAccession to IDF File map
   * 
   * @return experimentAccession to IDF File map.
   */
  public Map<String,File> getCompleteIDFMap() {
    return mapOfAccessionFilesForIDF;
  }
  
  /**
   * Gets the experimentAccession to SDRF File map
   * 
   * @return experimentAccession to SDRF File map.
   */
  public Map<String,File> getCompleteSDRFMap() {
    return mapOfAccessionFilesForSDRF;
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
        if (mapOfAccessionFilesForSDRF.containsKey(accession)
            && mapOfAccessionFilesForIDF.containsKey(accession)) {
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
            mapOfAccessionFilesForSDRF.put(accession, temp_sdrf);
          } else {
            System.out.println(sdrfFile + "\tFailed");
          }
          
          client.retrieveFile(arrayExpressFtpPath + pipeline + "/" + accession + "/" + idfFile, fos_idf);
          
          if (client.getReplyString().contains("226")) {
            System.out.println(idfFile + "\tFile Received");
            mapOfAccessionFilesForIDF.put(accession, temp_idf);
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
  public void downloadFileFromFTP(String experimentAccessionID) {
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
  private static String[][] stringToTable(String oneLongString) {
    
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
  private static Map<String,Integer> getCountFromPassage(String passage, Nfa nfa) throws MonqException {
    
    try {
      Map<String,Integer> map = new HashMap<String,Integer>();
      
      // Compile into the Dfa, specify that all text not matching any
      // regular expression shall be copied from input to output
      Dfa dfa = nfa.compile(DfaRun.UNMATCHED_DROP);
      
      // get a machinery (DfaRun) to operate the Dfa
      DfaRun r = new DfaRun(dfa);
      r.clientData = map;
      
      // Get only the filtered text
      r.filter(passage);
      return map;
    } catch (CompileDfaException e) {
      throw new MonqException(e);
    } catch (IOException e) {
      throw new MonqException(e);
    }
    
  }
  
  private Nfa getNfa(String... dictionary) throws MonqException {
    if (hashCodeToNfa.containsKey(dictionary.hashCode())) {
      return hashCodeToNfa.get(dictionary.hashCode());
      
    }
    
    try {
      Nfa nfa = new Nfa(Nfa.NOTHING);
      // If there is a clash, it doesn't matter.
      int i = 0;
      for (String item : dictionary) {
        
        nfa = nfa.or(Term2Re.convert(item), new DoCount(item).setPriority(i));
        
        i++;
      }
      // Use only complete matches
      nfa = nfa.or("[A-Za-z0-9]+", new Copy(Integer.MIN_VALUE));
      hashCodeToNfa.put(dictionary.hashCode(), nfa);
      return nfa;
    } catch (ReSyntaxException e) {
      throw new MonqException(e);
    }
  }
}
