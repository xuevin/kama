package uk.ac.ebi.fgpt.kama;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import monq.ie.Term2Re;
import monq.jfa.CompileDfaException;
import monq.jfa.Dfa;
import monq.jfa.DfaRun;
import monq.jfa.Nfa;
import monq.jfa.PrintfFormatter;
import monq.jfa.ReSyntaxException;
import monq.jfa.actions.Copy;
import monq.jfa.ctx.ContextStackProvider;

import org.junit.Test;

import uk.ac.ebi.ontocat.OntologyTerm;

public class MonqTesting {
  
  @Test
  public void testMonq() throws MonqException {
    int i = getCountFromPassage("The bat-wing following batman passage has bat blood in its annotations trust ",
      "bat man","WINGs","wing","wings");
    System.out.println(i);
    
  }
  
  private int getCountFromPassage(String passage,String... terms) throws MonqException {
    
    try {
      Map<String,Integer> map = new HashMap<String,Integer>();
      
      Nfa nfa = new Nfa(Nfa.NOTHING);
      int i= 0;
      for(String item: terms){
        nfa = nfa.or(Term2Re.convert(item),new DoCount(item).setPriority(i));
        i++;
      }
      //Use only complete matches
      nfa = nfa.or("[A-Za-z0-9]+",new Copy(Integer.MIN_VALUE));
      
      // Compile into the Dfa, specify that all text not matching any
      // regular expression shall be copied from input to output
      Dfa dfa = nfa.compile(DfaRun.UNMATCHED_DROP);
      
      // get a machinery (DfaRun) to operate the Dfa
      DfaRun r = new DfaRun(dfa);
      r.clientData = map;
      
      // Get only the filtered text
      r.filter(passage);
      for(String key :map.keySet()){
        System.out.println(key + " " + map.get(key));
      }
      return 0;
    } catch (ReSyntaxException e) {
      throw new MonqException(e);
    } catch (CompileDfaException e) {
      throw new MonqException(e);
    } catch (IOException e) {
      throw new MonqException(e);
    }
  }
  
  
  
  private Set<OntologyTerm> getWing() {
    HashSet<OntologyTerm> set = new HashSet<OntologyTerm>();
    
    set.add(new OntologyTerm("0000", "wing", "wing"));
    return set;
    
  }
  
  private String termsToInputReader(Set<OntologyTerm> allTerms) {
    StringBuilder input = new StringBuilder();
    
    // Print Headers
    input.append("<?xml version='1.0'?>");
    input.append("\n<mwt>");
    input.append("\n<template><span style=\"color:red;text-decoration: overline;\">");
    input.append("\n<a title=\"%1\" label=\"%2\">%0</a>");
    input.append("\n</span></template>");
    
    for (OntologyTerm term : allTerms) {
      input.append("\n");
      input.append("<t p1=");
      input.append("\"" + term.getAccession() + "\"");
      input.append(" p2=\"" + term.getLabel() + "\"");
      input.append(">" + term.getLabel() + "</t>");
      // try {
      // for(String syn:ontoService.getSynonyms(term)){
      // String syn2 = syn.replaceAll("[^a-zA-Z0-9]", "");
      // input.append("\n");
      // input.append("<t p1=");
      // input.append("\""+term.getAccession()+"\"");
      // input.append(" p2=\""+syn2+"\"");
      // input.append(">"+syn2+"</t>");
      // }
      // } catch (OntologyServiceException e) {
      // // TODO Auto-generated catch block
      // e.printStackTrace();
      // }
    }
    // output closing tag
    input.append("\n</mwt>");
    
    return input.toString();
    
  }
  
  private static final class ReadHelper implements ContextStackProvider {
    private List<Object> stack = new ArrayList<Object>();
    
    // used during setup of the mwt filter, records most recently seen
    // <template>
    private PrintfFormatter recentTemplate;
    
    // the dictionary Nfa filled up while reading the mwt file
    private Nfa dict;
    
    // print generated regexps to stderr, one per line if this is true
    private boolean verbose = false;
    
    // callbacks for dictionary terms receive increasing priority in
    // order to not having to deal with ambiguities.
    private int nextPrio = 1;
    
    // a little helper to be reused while reading in the mwt file
    monq.jfa.xml.StdCharEntities helper = new monq.jfa.xml.StdCharEntities();
    
    public List<Object> getStack() {
      return stack;
    }
    
    public ReadHelper(boolean verbose) {
      this.verbose = verbose;
    }
  }
  
}
