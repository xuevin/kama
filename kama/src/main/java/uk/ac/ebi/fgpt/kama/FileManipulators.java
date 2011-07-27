package uk.ac.ebi.fgpt.kama;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a class of static methods used to manipulate a file.
 * 
 * @author Vincent Xue
 * @date Tuesday, June 21 2011
 * 
 */
public class FileManipulators {
  /**
   * Converts a file into a list of strings. Each line is an element on the list.
   * 
   * @param inputFile
   *          file to be parsed
   * @return list of strings
   */
  public static List<String> fileToArrayList(File inputFile) {
    ArrayList<String> arrayList = new ArrayList<String>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(inputFile));
      String text;
      while ((text = br.readLine()) != null) {
        if (text.length() > 0) arrayList.add(text.trim());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return arrayList;
  }
  
  /**
   * Converts a file to an array of strings. Each element in the array is a line.
   * 
   * @param inputFile
   *          file to be parsed
   * @return an array of strings
   */
  public static String[] fileToArray(File inputFile) {
    List<String> al = fileToArrayList(inputFile);
    String[] array = new String[al.size()];
    al.toArray(array);
    return array;
  }
  
  /**
   * Writes a string to path on the file system.
   * 
   * @param outPath
   *          The destination path on the file system
   * @param outputString
   *          The string to be written into the file
   * @return the path which the file was written to.
   */
  public static String stringToFile(String outPath, String outputString) {
    try {
      System.out.println("Beginning to write file out");
      // Create file
      BufferedWriter bufferedWriter;
      File file = new File(outPath);
      bufferedWriter = new BufferedWriter(new FileWriter(file));
      bufferedWriter.write(outputString);
      bufferedWriter.close();
      return (file.getAbsolutePath());
    } catch (Exception e) {// Catch exception if any
      System.err.println("Error: " + e.getMessage());
    }
    return "Error With Output";
  }
  
}
