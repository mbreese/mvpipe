package org.ngsutils.mvpipe.parser.statement;

import org.ngsutils.mvpipe.parser.Eval;
import org.ngsutils.mvpipe.parser.SyntaxException;
import org.ngsutils.mvpipe.parser.Tokens;
import org.ngsutils.mvpipe.parser.context.ExecContext;
import org.ngsutils.mvpipe.parser.context.SubContext;
import org.ngsutils.mvpipe.parser.variable.VarValue;

public class If implements Statement {

	@Override
	public ExecContext eval(ExecContext context, Tokens tokens) throws SyntaxException {
		VarValue test = Eval.evalTokenExpression(context, tokens);
		System.err.println("#IF TEST RESULT: " + test);

		ExecContext nested = new SubContext(context, test.isTrue(), true);
		return nested;		
	}

}
