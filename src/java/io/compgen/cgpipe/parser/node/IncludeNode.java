package io.compgen.cgpipe.parser.node;

import io.compgen.cgpipe.exceptions.ASTExecException;
import io.compgen.cgpipe.exceptions.ASTParseException;
import io.compgen.cgpipe.parser.Eval;
import io.compgen.cgpipe.parser.Parser;
import io.compgen.cgpipe.parser.context.ExecContext;
import io.compgen.cgpipe.parser.tokens.TokenList;

public class IncludeNode extends ASTNode {
	public IncludeNode(ASTNode parent, TokenList tokens) {
		super(parent, tokens);
	}

	@Override
	public ASTNode exec(ExecContext context) throws ASTExecException {
		try {
			String filename = Eval.evalTokenExpression(tokens, context).toString();
			
			if (filename == null) {
				throw new ASTExecException("Missing include file", tokens.getLine());
			} else {
				Parser nestedAST = Parser.parseAST(filename, tokens.getLine().getPipeline().getLoader());
				nestedAST.exec(context);
			}
		} catch (ASTParseException e) {
			throw new ASTExecException(e);
		}
		return next;
	}

	@Override
	protected String dumpString() {
		return "[include] "+tokens;
	}
}