package io.compgen.cgpipe.parser.target;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.compgen.cgpipe.exceptions.ASTExecException;
import io.compgen.cgpipe.exceptions.ASTParseException;
import io.compgen.cgpipe.loader.NumberedLine;
import io.compgen.cgpipe.parser.context.RootContext;
import io.compgen.cgpipe.runner.JobDef;
import io.compgen.common.ListBuilder;

public class FileExistsBuildTarget extends BuildTarget {
	static protected Log log = LogFactory.getLog(FileExistsBuildTarget.class);

	public FileExistsBuildTarget(String output) {
		super(new ListBuilder<String>().add(output).list(), null, null, null, null);
	}
	
	@Override
	public JobDef eval(List<NumberedLine> pre, List<NumberedLine> post, RootContext globalRoot) throws ASTParseException, ASTExecException {
		return null;
	}

	@Override
	public boolean isSkippable() {
		return true;
	}
	
}
