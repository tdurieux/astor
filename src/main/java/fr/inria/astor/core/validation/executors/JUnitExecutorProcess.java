package fr.inria.astor.core.validation.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import fr.inria.astor.core.setup.ConfigurationProperties;
import fr.inria.astor.core.setup.ProjectConfiguration;
import fr.inria.astor.core.validation.entity.TestResult;
import fr.inria.astor.junitexec.JUnitTestExecutor;

/**
 * Process-based program variant validation
 * 
 * @author Matias Martinez, matias.martinez@inria.fr
 * 
 */
public class JUnitExecutorProcess {

	protected Logger log = Logger.getLogger(Thread.currentThread().getName());

	public JUnitExecutorProcess() {
		super();
	}

	public TestResult execute(URL[] path, List<String> classesToExecute, int waitTime) {
		return execute(urlArrayToString(path), classesToExecute, waitTime);
	}

	public TestResult execute(String path, List<String> classesToExecute, int waitTime) {
		Process p = null;

		if (!ProjectConfiguration.validJDK())
			throw new IllegalArgumentException(
					"jdk folder not found, please configure property jvm4testexecution in the configuration.properties file");

		String javaPath = ConfigurationProperties.getProperty("jvm4testexecution");
		javaPath += File.separator + "java";
		String systemcp = System.getProperty("java.class.path");

		path = systemcp + File.pathSeparator + path;

		List<String> cls = new ArrayList<>(classesToExecute);

		try {

			List<String> command = new ArrayList<String>();
			command.add(javaPath);
			//command.add("-Xmx2048m");
			command.add("-cp");
			command.add(path);
			command.add(JUnitTestExecutor.class.getName());

			command.addAll(cls);

			ProcessBuilder pb = new ProcessBuilder(command.toArray(new String[command.size()]));
			pb.redirectOutput();
			pb.redirectErrorStream(true);
			pb.directory(new File((ConfigurationProperties.getProperty("location"))));
			long t_start = System.currentTimeMillis();
			p = pb.start();

			String commandString = command.toString().replace("[", "").replace("]", "").replace(",", " ");
			int trunk = ConfigurationProperties.getPropertyInt("commandTrunk");
			String commandToPrint = (trunk !=0 && commandString.length() > trunk )? (commandString.substring(0, trunk)+"..AND "+(commandString.length() - trunk)+" CHARS MORE..."):commandString;
			log.debug("Executing process: \n" + commandToPrint);

			WorkerThreadHelper worker = new WorkerThreadHelper(p);
			worker.start();
			worker.join(waitTime);
			long t_end = System.currentTimeMillis();
			
			p.exitValue();
			TestResult tr = getTestResult(p);
			p.destroy();
			log.debug("Execution time " + ((t_end - t_start) / 1000) + " seconds");

			return tr;
		} catch ( IOException |InterruptedException |IllegalThreadStateException  ex) {
			log.info("The Process that runs JUnit test cases had problems: " + ex.getMessage());
			if (p != null)
				p.destroy();
		}
		return null;
	}


	/**
	 * This method analyze the output of the junit executor (i.e.,
	 * {@link JUnitTestExecutor}) and return an entity called TestResult with
	 * the result of the test execution
	 * 
	 * @param p
	 * @return
	 */
	protected TestResult getTestResult(Process p) {
		TestResult tr = new TestResult();
		boolean success = false;
		String out = "";
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				out += line + "\n";
				if (line.startsWith(JUnitTestExecutor.OUTSEP)) {
					String[] s = line.split(JUnitTestExecutor.OUTSEP);
					int nrtc = Integer.valueOf(s[1]);
					tr.casesExecuted = nrtc;
					int failing = Integer.valueOf(s[2]);
					tr.failures = failing;
					if (!"".equals(s[3])) {
						String[] falinglist = s[3].replace("[", "").replace("]", "").split(",");
						for (String string : falinglist) {
							if (!string.trim().isEmpty())
								tr.failTest.add(string.trim());
						}
					}
					success = true;
				}
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (success)
			return tr;
		else {
			log.error("Error reading the validation process\n output: \n"+
			out +" \n error: "+getProcessError(p.getErrorStream()));
			
			return null;
		}
	}

	protected String urlArrayToString(URL[] urls) {
		String s = "";
		for (int i = 0; i < urls.length; i++) {
			URL url = urls[i];
			s += url.getPath() + File.pathSeparator;
		}
		return s;
	}
	private String getProcessError(InputStream str){
		String out = "";
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(str));
			String line;
			while ((line = in.readLine()) != null) {
				out += line + "\n";
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return out;
	}

}
