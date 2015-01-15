package org.ngsutils.mvpipe.parser.variable;

public class VarBool extends VarValue {
	
	public static final VarBool TRUE = new VarBool(true);
	public static final VarBool FALSE = new VarBool(false);
	
	private VarBool(boolean val) {
		super(val);
	}
	
	public boolean isTrue() {
		if ((Boolean) obj) {
			return true;
		}
		return false;
	}
}
