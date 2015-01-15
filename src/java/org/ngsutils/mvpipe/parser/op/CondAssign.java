package org.ngsutils.mvpipe.parser.op;

import org.ngsutils.mvpipe.parser.SyntaxException;
import org.ngsutils.mvpipe.parser.context.ExecContext;
import org.ngsutils.mvpipe.parser.variable.VarValue;

public class CondAssign implements Operator {

	@Override
	public VarValue eval(ExecContext context, String lstr, VarValue rval)
			throws SyntaxException {
		if (!context.contains(lstr)) {
			context.set(lstr, rval);
		}
		return context.get(lstr);
	}
}
