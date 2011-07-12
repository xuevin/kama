package uk.ac.ebi.fgpt.kama;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FileUtils;

import uk.ac.ebi.fgpt.kama.Kama.Scope;
import uk.ac.ebi.ontocat.OntologyServiceException;

/**
 * KAMA - quicK pAss for Meta Analysis Kama is an application used to quickly annotate SDRF and IDF files. It
 * uses monq data minining libraries and ontocat to quickly describe whether or not a certain ontology term is
 * used in a file.
 * 
 * @author Vincent Xue
 * @date Tuesday, June 21 2011
 */
public class App {
  
  public static void main(String[] args) throws OntologyServiceException, IOException, MonqException {
    boolean displaySummary = false;
    boolean export = false;
    
    // Make options
    Options cliOptions = new Options();
    cliOptions.addOption("h", "help", false, "help");
    cliOptions.addOption("s", "summary", false, "display summary statistics on IDF/SDRF");
    cliOptions.addOption("x", "export", false, "display summary statistics, and save all files to directory");
    
    Option output = OptionBuilder.withArgName("output.txt").hasArg().withDescription(
      "use given file for output").isRequired().create("output");
    Option ontologyFile = OptionBuilder.withArgName("file.owl").hasArg().withDescription(
      "use given ontology file. Defaults to EFOv142").create("owlfile");
    Option accessionIDS = OptionBuilder.withArgName("ontologyAccessions.txt").hasArg().withDescription(
      "use the given list of Ontology accession ids").isRequired().create("ids");
    Option input = OptionBuilder.withArgName("input.txt").hasArg().withDescription(
      "use given file of accession ids").isRequired().create("input");
    
    cliOptions.addOption(output);
    cliOptions.addOption(ontologyFile);
    cliOptions.addOption(accessionIDS);
    cliOptions.addOption(input);
    
    HelpFormatter formatter = new HelpFormatter();
    
    // Try to parse options
    CommandLineParser parser = new PosixParser();
    try {
      String inputOntologyAccessionIds = null;
      String inputExperimentList = null;
      String outputFileString = null;
      String owlFileString = null;
      
      CommandLine cmd = parser.parse(cliOptions, args);
      
      if (cmd.hasOption("-h")) {
        formatter.printHelp("kama", cliOptions, true);
        return;
      }
      if (cmd.hasOption("owlfile")) owlFileString = cmd.getOptionValue("owlfile");
      if (cmd.hasOption("input")) inputExperimentList = cmd.getOptionValue("input");
      if (cmd.hasOption("ids")) inputOntologyAccessionIds = cmd.getOptionValue("ids");
      if (cmd.hasOption("output")) outputFileString = cmd.getOptionValue("output");
      if (cmd.hasOption("s")) displaySummary = true;
      if (cmd.hasOption("x")) {
        displaySummary = true;
        export = true;
      }
      
      // Continue only if:
      // -input is not null
      // -output is not null
      // -ids is not null
      if (inputOntologyAccessionIds != null && inputExperimentList != null && outputFileString != null) {
        Kama kamaInstance;
        
        // Determine is a custom Ontology was entered
        if (owlFileString != null) {
          kamaInstance = new Kama(new File(owlFileString));
        } else {
          kamaInstance = new Kama();
        }
        
        // Turn files into java objects
        List<String> listOfExperimentAccessions = FileManipulators.fileToArrayList(new File(
            inputExperimentList));
        String[] listOfOntologyAccessionIds = FileManipulators
            .fileToArray(new File(inputOntologyAccessionIds));
        
        // Two modes
        // Summary Mode - display summary on the idf/sdrf level
        // and the default mode which is on the sample level
        if (displaySummary) {
          HashMap<String,Integer> idfCount = kamaInstance.getCountHashMapForListOfAccessions(
            listOfExperimentAccessions, Scope.idf, listOfOntologyAccessionIds);
          HashMap<String,Integer> sdrfCount = kamaInstance.getCountHashMapForListOfAccessions(
            listOfExperimentAccessions, Scope.sdrf, listOfOntologyAccessionIds);
          HashMap<String,Integer> assayCount = kamaInstance
              .getCountOfAssaysPerExperiment(listOfExperimentAccessions);
          
          if (idfCount.size() != sdrfCount.size()) {
            System.err.println("There was an error fetching files. SDRF files are not equal to IDF Files");
            System.err.println("Will Print Out Experiments That Have both SDRF and IDFs");
          }
          StringBuilder outString = new StringBuilder();
          
          outString.append("#AccessionId\tAssays\tIDF\tSDRF\tTerms");
          for (String accession : listOfExperimentAccessions) {
            if (idfCount.get(accession) != null && sdrfCount.get(accession) != null) {
              outString.append("\n");
              outString.append(accession + "\t");
              outString.append(assayCount.get(accession).toString() + "\t");
              outString.append(idfCount.get(accession) + "\t");
              outString.append(sdrfCount.get(accession) + "\t");
              outString.append(kamaInstance.getCountOfEachTermInExperimentAsString(accession, Scope.both,
                listOfOntologyAccessionIds));
            } else {
              System.out.println(accession + " does not have both Magetab files");
              continue;
            }
            
          }
          // Write the file
          FileManipulators.stringToFile(outputFileString, outString.toString());
          
          if (export) {
            HashMap<String,File> idfHash = kamaInstance.getCompleteIDFHash();
            HashMap<String,File> sdrfHash = kamaInstance.getCompleteSDRFHash();
            HashMap<String,Integer> bothCount = kamaInstance.getCountHashMapForListOfAccessions(
              listOfExperimentAccessions, Scope.both, listOfOntologyAccessionIds);
            
            File outFile = new File(outputFileString);
            String outDir = outFile.getParent();
            
            // Make top level directories
            System.out.println("Making top level directories...");
            File yesdir = new File(outDir + "/positive");
            yesdir.delete();
            yesdir.mkdir();
            File nodir = new File(outDir + "/negative");
            nodir.delete();
            nodir.mkdir();
            
            for (String experimentAccession : listOfExperimentAccessions) {
              
              if (idfHash.containsKey(experimentAccession) && sdrfHash.containsKey(experimentAccession)) {
                
                // Yes it does contain a member
                
                File dir;
                if (bothCount.get(experimentAccession).intValue() != 0) {
                  dir = new File(yesdir.getAbsolutePath() + "/" + experimentAccession);
                } else {// No it does not
                  dir = new File(nodir.getAbsolutePath() + "/" + experimentAccession);
                }
                dir.delete();
                dir.mkdir();
                File targetIDF = new File(dir.getAbsolutePath() + "/" + experimentAccession + ".idf.txt");
                File targetSDRF = new File(dir.getAbsolutePath() + "/" + experimentAccession + ".sdrf.txt");
                
                FileUtils.copyFile(idfHash.get(experimentAccession), targetIDF);
                FileUtils.copyFile(sdrfHash.get(experimentAccession), targetSDRF);
                System.out.println(experimentAccession + " created");
              }
            }
            
          }
          
        } else {
          // Sample Level Output
          StringBuilder outString = new StringBuilder();
          outString.append("#AccessionId\tSample");
          for (String ontoAccession : listOfOntologyAccessionIds) {
            outString.append("\t" + ontoAccession + "_idf");
            outString.append("\t" + ontoAccession + "_sample");
          }
          outString.append("\tTerms\n");
          
          // For each ontology class, save the experimentToCount hashmap on the idf scope.
          HashMap<String,HashMap<String,Integer>> ontologyAccessionIdsToIDFCountHashMap = new HashMap<String,HashMap<String,Integer>>();
          for (String ontologyAccessionId : listOfOntologyAccessionIds) {
            ontologyAccessionIdsToIDFCountHashMap.put(ontologyAccessionId, kamaInstance
                .getCountHashMapForListOfAccessions(listOfExperimentAccessions, Scope.idf,
                  ontologyAccessionId));
          }
          
          // For each experiment
          for (String experimentAccession : listOfExperimentAccessions) {
            
            HashMap<String,Integer> sampleToCountHash = kamaInstance.getCountHashMapForExperimentCELFiles(
              experimentAccession, listOfOntologyAccessionIds);
            if (sampleToCountHash.size() == 0) {
              System.out.println(experimentAccession
                                 + " is null. May not contain ADF or may not be a valid accession");
              continue;
            }
            
            // For each experiment, and each ontolgyTerm, save the sampleToCount hashMap to save recomputing
            // it at every sample
            HashMap<String,HashMap<String,Integer>> ontologyTermToSampleCountHashMap = new HashMap<String,HashMap<String,Integer>>();
            for (String ontoAccessionID : listOfOntologyAccessionIds) {
              ontologyTermToSampleCountHashMap.put(ontoAccessionID, kamaInstance
                  .getCountHashMapForExperimentCELFiles(experimentAccession, ontoAccessionID));
            }
            
            HashMap<String,HashMap<String,Integer>> ontologyAccessionIdsToSampleLevelTerm = kamaInstance
                .getCountOfEachTermPerSample(experimentAccession, listOfOntologyAccessionIds);
            
            // Just iterate through the samples
            for (String sample : sampleToCountHash.keySet()) {
              
              String row = "";
              row += (experimentAccession + "\t" + sample + "\t");
              // For each ontology accession id , get the count in the IDF and the count in the specific
              // sample
              for (String ontoAccessionID : listOfOntologyAccessionIds) {
                row += ontologyAccessionIdsToIDFCountHashMap.get(ontoAccessionID).get(experimentAccession)
                    .intValue();
                row += "\t";
                row += ontologyTermToSampleCountHashMap.get(ontoAccessionID).get(sample).intValue() + "\t";
                
              }
              // Put terms
              HashMap<String,Integer> termsMap = ontologyAccessionIdsToSampleLevelTerm.get(sample);
              for (String term : termsMap.keySet()) {
                row += term + ":" + termsMap.get(term).intValue() + ";";
              }
              
              System.out.println(row);
              outString.append(row);
              outString.append("\n");
            }
          }
          FileManipulators.stringToFile(outputFileString, outString.toString());
        }
      } else {

      }
    } catch (ParseException e) {
      // System.err.println( "Parsing failed.  Reason: " + e.getMessage() );
      formatter.printHelp("kama", cliOptions, true);
    }
  }
}
