package io.compgen.mvpipe.parser.node;

import io.compgen.mvpipe.parser.context.ExecContext;



public class JobNoOpNode extends ASTNode {
	public JobNoOpNode(ASTNode parent) {
		super(parent, null);
	}

	@Override
	public ASTNode exec(ExecContext context) {
		return next;
	}

	@Override
	protected String dumpString() {
		return "[job-no-op]";
	}
}
