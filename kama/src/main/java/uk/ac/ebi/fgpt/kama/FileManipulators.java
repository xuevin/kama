package uk.ac.ebi.fgpt.kama;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;


public class FileManipulators {
	public static ArrayList<String> fileToArrayList(File inputFile){
		ArrayList<String> arrayList = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(inputFile));
			String text;
			while((text= br.readLine())!=null){
				if(text.length()>0)
					arrayList.add(text.trim());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return arrayList;
	}
	public static String[] fileToArray(File inputFile){
		ArrayList<String> al = fileToArrayList(inputFile);
		String[] array = new String[al.size()];
		al.toArray(array);
		return array;
	}
	public static String stringToFile(String outputFileString,String outputString){
		try {
			// Create file
			BufferedWriter bufferedWriter;
			File file = new File(outputFileString);
			bufferedWriter = new BufferedWriter(new FileWriter(file));
			bufferedWriter.write(outputString);
			bufferedWriter.close();
			return(file.getAbsolutePath());   					
		} catch (Exception e) {// Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
		return "Error With Output";
	}

}
