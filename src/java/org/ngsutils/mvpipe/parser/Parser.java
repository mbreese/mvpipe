package org.ngsutils.mvpipe.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngsutils.mvpipe.MVPipe;
import org.ngsutils.mvpipe.exceptions.SyntaxException;
import org.ngsutils.mvpipe.parser.context.BuildTarget;
import org.ngsutils.mvpipe.parser.context.ExecContext;
import org.ngsutils.mvpipe.support.StringUtils;

public class Parser {
	private Log log = LogFactory.getLog(Parser.class);

	private ExecContext currentContext;
	private BuildTarget curTarget = null;
	private String curFilename = null;

	public ExecContext getContext() {
		return currentContext;
	}
	
	public Parser(ExecContext context) {
		this.currentContext = context;
	}

	protected File loadFile(String filename) throws IOException {
		// check absolute files first.
		
		File f = new File(filename);
		log.debug("Trying to load filename: " + f.getAbsolutePath());
		if (f.exists()) {
			return f;
		}
		
		// check relative files first.
		ExecContext cxt = currentContext;
		while (cxt != null) {
			String cwd = cxt.getCWD();
			if (cwd != null) {
				f = new File(cwd + File.separator + filename);
				log.debug("Trying to load filename: " + f.getAbsolutePath());
				if (f.exists()) {
					return f;
				}
			}
			cxt = cxt.getParent();
		}

		// check global path
		f = new File(new File(MVPipe.RCFILE).getParent() + File.separator + filename);
		log.debug("Trying to load filename: " + f.getAbsolutePath());
		if (f.exists()) {
			return f;
		}
		

		IOException e = new IOException("File: "+filename+" was not found!");
		log.fatal("File: "+filename+" was not found!", e);
		throw e;
	}
	
	public void parseFile(String filename) throws IOException, SyntaxException {
		File file = loadFile(filename);
		parseFile(file);
	}
	
	public void parseFile(File file) throws IOException, SyntaxException {
		if (file.getParent() != null) {
			currentContext.setCWD(file.getParent());
		} else {
			currentContext.setCWD(new File(new File(".").getAbsolutePath()).getParent());
		}
		curFilename = file.getAbsolutePath();
		parseInputStream(new FileInputStream(file));
	}
	
	public void parseInputStream(InputStream is) throws IOException, SyntaxException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String line;

		String priorLine=null;
		int linenum = 0;
		while ((line = reader.readLine()) != null) {
			line = StringUtils.rstrip(line);

			linenum += 1;
			
			if (priorLine != null) {
				line = priorLine + line;
				priorLine = null;
			}

			if (line.endsWith("\\")) {
				priorLine = line.substring(0, line.length()-1);
				continue;
			}

			// check for a new target (out1 out2 : in1 in2)
			if (StringUtils.strip(line).length() > 0) {
				List<String> targets = StringUtils.quotedSplit(line, ":", true);
				log.trace("target test split: "+StringUtils.join(",", targets));
				if (targets.size() > 1 && targets.get(1).equals(":")) {
					boolean isComment = false;
					List<String> outputs = new ArrayList<String>();
					for (String s: StringUtils.quotedSplit(targets.get(0).replaceAll("\t",  " "), " ")) {
						if (!s.startsWith("#")) {
							outputs.add(s);
						} else {
							isComment = true;
							break;
						}
					}
					
					if (!isComment) {
						List<String> inputs = new ArrayList<String>();
						if (targets.size()==3) {
							for (String s: StringUtils.quotedSplit(targets.get(2).replaceAll("\t",  " "), " ")) {
								if (!s.startsWith("#")) {
									inputs.add(s);
								} else {
									break;
								}
							}
						}
						
						curTarget = new BuildTarget(outputs, inputs, currentContext, curFilename);
						currentContext.addTarget(curTarget);
						continue;
					}
				}
			}
			
			// Try to add this to an existing target
			if (curTarget != null) {
				if (StringUtils.strip(line).length() == 0 || curTarget.getIndentLevel() == -1 || StringUtils.calcIndentLevel(line) >= curTarget.getIndentLevel()) { // not blank
					curTarget.addLine(line, linenum);
					continue;
				}
				// we aren't indented... and have something... must be at the end of the target
				curTarget = null;
			}
			
			// Finally tokenize the line and attempt to execute it
			if (StringUtils.strip(line).length() > 0) {
				Tokens tokens = new Tokens(curFilename, linenum, line);
				log.trace("Parsing tokens: [" +StringUtils.join(", ", tokens.getList())+"]");
				
				try {
					currentContext = currentContext.addTokenizedLine(tokens);
				} catch (SyntaxException e) {
					reader.close();
					e.setErrorLine(curFilename, linenum);
					throw e;
				}
			}
		}
		
		reader.close();
	}
}
