package uk.ac.ebi.fgpt.kama;

import java.util.Map;

import monq.jfa.AbstractFaAction;
import monq.jfa.DfaRun;

public class DoCount extends AbstractFaAction {
  public void invoke(StringBuffer iotext, int start, DfaRun r) {
    String word = iotext.substring(start);
    iotext.setLength(start);
    
    Map m = (Map) r.clientData;
    Integer count = (Integer) m.get(word);
    if (count == null) m.put(word, new Integer(1));
    else m.put(word, new Integer(1 + count.intValue()));
  }
}