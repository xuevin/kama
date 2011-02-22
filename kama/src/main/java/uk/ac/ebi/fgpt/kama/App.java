package uk.ac.ebi.fgpt.kama;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import uk.ac.ebi.fgpt.kama.Kama.Scope;

/**
 * quicK pAss for Meta Analysis
 *
 */
public class App 
{

	public static void main( String[] args ){
	    boolean displaySummary = false;

    	// Make options
    	Options cliOptions = new Options();
    	cliOptions.addOption("h","help",false,"help");
    	cliOptions.addOption("s","summary",false,"Display summary statistics on IDF/SDRF");

    	Option output = OptionBuilder.withArgName("output.txt").hasArg().withDescription("use given file for output").isRequired().create("output");
    	Option owlfile = OptionBuilder.withArgName("file.owl").hasArg().withDescription("use given owl file. Defaults to v142").create("owlfile");
    	Option efo = OptionBuilder.withArgName("efo.txt").hasArg().withDescription("use the given list of EFO accession ids").isRequired().create("efo");
    	Option input = OptionBuilder.withArgName("input.txt").hasArg().withDescription("use given file of accession ids").isRequired().create("input");

    	cliOptions.addOption(output);
    	cliOptions.addOption(owlfile);
    	cliOptions.addOption(efo);
    	cliOptions.addOption(input);
    	
    	
    	HelpFormatter formatter = new HelpFormatter();
    	
    	// Try to parse options
    	CommandLineParser parser = new PosixParser();
    	try {
			String inputEFOList = null;
			String inputExperimentList = null;
			String outputFileString = null;
			String owlFileString= null;
			
    		CommandLine cmd = parser.parse(cliOptions, args);
			
			if(cmd.hasOption("-h")){
		    	formatter.printHelp( "kama", cliOptions,true);
				return;
			}
			if(cmd.hasOption("owlfile"))
				owlFileString = cmd.getOptionValue("owlfile");
			if(cmd.hasOption("input"))
				inputExperimentList=cmd.getOptionValue("input");
			if(cmd.hasOption("efo"))
				inputEFOList=cmd.getOptionValue("efo");
			if(cmd.hasOption("output"))
				outputFileString=cmd.getOptionValue("output");
			if(cmd.hasOption("s"))
				displaySummary=true;
			
			// Continue only if:
			// -input is not null
			// -output is not null
			// -efo is not null
			if(inputEFOList!=null && inputExperimentList!=null && outputFileString!=null){
				Kama kamaInstance;
				
				//Determine is a custom EFO was entered
				if(owlFileString!=null){
					kamaInstance= new Kama(new File(owlFileString));
				}else{
					kamaInstance= new Kama();
				}

				//Turn files into java objects
				ArrayList<String> listOfExperimentAccessions = FileManipulators.fileToArrayList(new File(inputExperimentList));
				String[] listOfEFOAccessionIds = FileManipulators.fileToArray(new File(inputEFOList));
				
				// Two modes 
				// Summary Mode - display summary on the idf/sdrf level
				// and the default mode which is on the sample level
				if(displaySummary){
					HashMap<String,Integer> idfCount = kamaInstance.getCountHashMapForListOfAccessions(listOfExperimentAccessions, Scope.idf,listOfEFOAccessionIds);
					HashMap<String,Integer> sdrfCount = kamaInstance.getCountHashMapForListOfAccessions(listOfExperimentAccessions, Scope.sdrf,listOfEFOAccessionIds);
					if(idfCount.size()!=sdrfCount.size()){
						System.err.println("There was an error fetching files. SDRF files are not equal to IDF Files");
						return;
					}
					String outString ="";
					outString+=("#AccessionId\tIDF\tSDRF\tTerms");
					for(String accession:listOfExperimentAccessions){
						outString+=("\n");
						outString+=(accession+"\t");
						outString+=(idfCount.get(accession)+"\t");
						outString+=(sdrfCount.get(accession)+"\t");
						outString+=(kamaInstance.getCountOfEachTermInExperimentAsString(accession, Scope.both, listOfEFOAccessionIds));
					}
					//Write the file
					FileManipulators.stringToFile(outputFileString,outString);
				}else{
					// Sample Level Output
					String outString = "";
					outString+=("#AccessionId\tSample");
					for(String efoId:listOfEFOAccessionIds){
						outString+=("\t"+efoId+"_idf");
						outString+=("\t"+efoId+"_sample");
					}
					outString+="\tTerms";

					
					//For each efo class, save the experimentToCount hashmap on the idf scope. 
					HashMap<String,HashMap<String,Integer>> efoToIDFCountHashMap = new HashMap<String, HashMap<String,Integer>>();
					for(String efoId:listOfEFOAccessionIds){
						efoToIDFCountHashMap.put(efoId, kamaInstance.getCountHashMapForListOfAccessions(listOfExperimentAccessions, Scope.idf, efoId));
					}
					
					//For each experiment
					for(String experimentAccession:listOfExperimentAccessions){
						
						HashMap<String,Integer> sampleToCountHash = kamaInstance.getCountHashMapForExperimentCELFiles(experimentAccession, listOfEFOAccessionIds);
						if(sampleToCountHash.size()==0){
							System.out.println(experimentAccession + " is null. May not contain ADF or may not be a valid accession");
							continue;
						}
						
						//For each experiment, and each efo, save the sampleToCount hashMap to save recomputing it at every sample
						HashMap<String,HashMap<String,Integer>> efoToSampleCountHashMap = new HashMap<String, HashMap<String,Integer>>();
						for(String efoId:listOfEFOAccessionIds){
							efoToSampleCountHashMap.put(efoId, kamaInstance.getCountHashMapForExperimentCELFiles(experimentAccession, efoId));
						}
						
						HashMap<String, HashMap<String,Integer>> efoToSampleLevelTerm = kamaInstance.getCountOfEachTermPerSample(experimentAccession, listOfEFOAccessionIds);
						
						//Just iterate through the samples 
						for(String sample :sampleToCountHash.keySet()){
							
							String row = "";
							row+=(experimentAccession+"\t"+sample+"\t");
							//For each EFO id , get the count in the IDF and the count in the specific sample
							for(String efoId:listOfEFOAccessionIds){
								row +=efoToIDFCountHashMap.get(efoId).get(experimentAccession).intValue();
								row +="\t";
								row +=efoToSampleCountHashMap.get(efoId).get(sample).intValue() +"\t";
								
							}
							//Put terms
							HashMap<String,Integer> termsMap =efoToSampleLevelTerm.get(sample); 
							for(String term:termsMap.keySet()){
								row+=term+":"+termsMap.get(term).intValue()+";";
							}	
							
							
							System.out.println(row);
							outString+=row;
						}
					}
					FileManipulators.stringToFile(outputFileString, outString);
				}
			}else{
				
			}
		} catch (ParseException e) {
	        //System.err.println( "Parsing failed.  Reason: " + e.getMessage() );
	    	formatter.printHelp( "kama", cliOptions,true);
		}   
	}
}
