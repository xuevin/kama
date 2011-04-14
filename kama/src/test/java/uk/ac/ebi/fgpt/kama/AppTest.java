package uk.ac.ebi.fgpt.kama;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest extends TestCase {
	/**
	 * Create the test case
	 * 
	 * @param testName
	 *            name of the test case
	 */
	public AppTest(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(AppTest.class);
	}

	/**
	 * Rigourous Test :-)
	 */
	public void testApp() {
		assertTrue(true);
	}

	public void testOptions() {

		String[] args = new String[1];
		args[0] = "-help";
		// args[1]="some file";

		Options cliOptions = new Options();
		cliOptions.addOption("h", "help", false, "help");
		Option output = OptionBuilder.withArgName("file.txt").hasArg()
				.withDescription("use given file for output").isRequired()
				.create("output");
		Option owlfile = OptionBuilder.withArgName("file.owl").hasArg()
				.withDescription("use given owl file. Defaults to v142")
				.create("owlfile");
		Option efo = OptionBuilder.withArgName("EFO_0000001").hasArg()
				.withDescription("search for children of the given EFO")
				.isRequired().create("efo");
		Option input = OptionBuilder.withArgName("intput.txt").hasArg()
				.withDescription("use given file of accession ids")
				.isRequired().create("input");

		cliOptions.addOption(output);
		cliOptions.addOption(owlfile);
		cliOptions.addOption(efo);
		cliOptions.addOption(input);

		HelpFormatter formatter = new HelpFormatter();

		CommandLineParser parser = new PosixParser();
		try {
			CommandLine cmd = parser.parse(cliOptions, args);
			if (cmd.hasOption("owlfile")) {
				System.out.println(cmd.getOptionValue("owlfile"));
			} else if (cmd.hasOption("w")) {
				System.out.println("Option w");
			} else if (cmd.hasOption("-h")) {
				formatter.printHelp("kama", cliOptions, true);
			}

		} catch (ParseException e) {
			// TODO Auto-generated catch block
			// System.err.println( "Parsing failed.  Reason: " + e.getMessage()
			// );
			formatter.printHelp("kama", cliOptions, true);
		}
	}

	public void testFileReader() {
		File inputFile;
		try {
			inputFile = new File(getClass().getClassLoader().getResource(
					"accessiontest.txt").toURI());
			BufferedReader br = new BufferedReader(new FileReader(inputFile));
			String text;
			while ((text = br.readLine()) != null) {
				System.out.println(text);
			}
		} catch (URISyntaxException e) {
			e.printStackTrace();
			fail();
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
	}

	public void testCreateFileTest() {
		try {
			// Create file
			BufferedWriter output;

			File file = new File("write.txt");
			output = new BufferedWriter(new FileWriter(file));
			output.write("Hello World");
			output.close();
			System.out.println("Your file has been written");
			assertTrue(true);

		} catch (Exception e) {// Catch exception if any
			System.err.println("Error: " + e.getMessage());
			fail();
		}
	}

	public void testMain() {
		String[] args = new String[7];
		args[0] = "-input";
		args[1] = "src/test/resources/accessiontest.txt";
		args[2] = "-output";
		args[3] = "src/test/resources/output.txt";
		args[4] = "-efo";
		args[5] = "src/test/resources/efo.txt";
		args[6] = "-s";
		App.main(args);
	}
	public void testMain2(){
		String[] args2 = new String[6];
		args2[0] = "-input";
		args2[1] = "src/test/resources/accessiontest.txt";
		args2[2] = "-output";
		args2[3] = "src/test/resources/output.txt";
		args2[4] = "-efo";
		args2[5] = "src/test/resources/efo.txt";
		App.main(args2);
	}
	public void testMain3(){
		String[] args2 = new String[7];
		args2[0] = "-input";
		args2[1] = "src/test/resources/accessiontest.txt";
		args2[2] = "-output";
		args2[3] = "src/test/resources/output.txt";
		args2[4] = "-efo";
		args2[5] = "src/test/resources/efo.txt";
		args2[6] = "-x";
		App.main(args2);
	}
}
