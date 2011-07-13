package uk.ac.ebi.fgpt.kama;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.ebi.ontocat.OntologyServiceException;
import uk.ac.ebi.ontocat.OntologyTerm;
import uk.ac.ebi.ontocat.file.FileOntologyService;

/**
 * A simple wrapper around OntologyService. This wrapper allows me to cache the children and to get all the
 * related terms without making the KAMA code messy.
 * 
 * @author Vincent Xue
 * 
 */
public class OntologyFunctions {
  /**
   * The 'parent ontology term' to 'ArrayList<String>' map that stores it's children so that an OWL does not
   * need to be parsed multiple times.
   */
  private Map<String,List<String>> mapOfOntologyChildrenTerms = new HashMap<String,List<String>>();
  
  private FileOntologyService ontoService;
  
  public OntologyFunctions(URI uri) throws OntologyServiceException {
    ontoService = new FileOntologyService(uri);
  }
  
  public List<String> getChildrenAndRelatedTerms(String efoAccessionID) {
    // If mapOfOntologyChildrenTerms does not contain the efoAccessionId, then add it to the map
    if (mapOfOntologyChildrenTerms.get(efoAccessionID) != null) {
      return mapOfOntologyChildrenTerms.get(efoAccessionID);
    }
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
        mapOfOntologyChildrenTerms.put(efoAccessionID, listOfChildren);
      } else {
        throw new OntologyServiceException("OntologyTerm is null");
      }
    } catch (OntologyServiceException e) {
      System.err.println("WARNING: ONTOLOGY ACCESSION ID DOES NOT EXIST");
      return null;
    }
    return mapOfOntologyChildrenTerms.get(efoAccessionID);
  }
  
}
