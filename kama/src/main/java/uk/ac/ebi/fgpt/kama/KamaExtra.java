package uk.ac.ebi.fgpt.kama;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;


import uk.ac.ebi.ontocat.OntologyServiceException;
import uk.ac.ebi.ontocat.OntologyTerm;

public class KamaExtra extends Kama {
	public KamaExtra(){
		super();
	}
	public KamaExtra(File efoFile){
		super(efoFile);
	}
	public HashMap<String, File> getIDFHash(){
		return hashOfAccessionFilesForIDF;
	}
	public HashMap<String, File> getSDRFHash(){
		return hashOfAccessionFilesForSDRF;
	}
	/**
	 * Gets a list of all the EFO children and the EFO itself for every EFOAccession in this list.
	 *
	 * @param listOfEFOAccessionIds the list of EFO classes for which you want to retrieve the children from.
	 * @return an Arraylist of the all the EFO children of the EFO classes. 
	 */
	@Override
	public ArrayList<String> getChildrenOfEFOAccessionPlusItself(String ...listOfEFOAccessionIds){
		ArrayList<String> returnListOfEFO = new ArrayList<String>();
		for(String efoAccessionID:listOfEFOAccessionIds){
			
			if(hashOfEFOChildrenTerms.get(efoAccessionID)==null){
				try{
					OntologyTerm parent= ontoService.getTerm(efoAccessionID);
					ArrayList<String> listOfChildren;
					if(parent!=null){
						listOfChildren = new ArrayList<String>();
						listOfChildren.add(parent.getLabel()); //Include Synonymns
						listOfChildren.addAll(ontoService.getSynonyms(parent));
						for(OntologyTerm ot : ontoService.getAllChildren(parent)){
							listOfChildren.add(ot.getLabel());
							listOfChildren.addAll(ontoService.getSynonyms(ot));//Include Synonymns
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

}
