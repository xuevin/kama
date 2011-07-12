package uk.ac.ebi.fgpt.kama;

public class MonqException extends Exception {
  
  public MonqException() {
    super();
  }
  
  public MonqException(String message) {
    super(message);
  }
  
  public MonqException(Throwable throwable) {
    super(throwable);
  }
  
  public MonqException(String message, Throwable throwable) {
    super(message, throwable);
  }
  
}
