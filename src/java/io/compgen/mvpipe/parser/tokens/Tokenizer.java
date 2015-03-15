package io.compgen.mvpipe.parser.tokens;

import io.compgen.mvpipe.exceptions.ASTParseException;
import io.compgen.mvpipe.exceptions.VarTypeException;
import io.compgen.mvpipe.parser.NumberedLine;
import io.compgen.mvpipe.parser.op.Operator;
import io.compgen.mvpipe.parser.statement.Statement;
import io.compgen.mvpipe.parser.variable.VarFloat;
import io.compgen.mvpipe.parser.variable.VarInt;
import io.compgen.mvpipe.parser.variable.VarValue;
import io.compgen.mvpipe.support.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {

	public static TokenList tokenize(NumberedLine line) throws ASTParseException {
		try {
			List<Token> tokens = extractQuotedStrings(line.line, '"', '#');
//			System.err.println("extractQuotedStrings: "+StringUtils.join(";", tokens));
			
			tokens = markSplitLine(tokens);
//			System.err.println("markSplitLine       : "+StringUtils.join(";", tokens));
	
			tokens = delimiterSplit(tokens, ' ');
//			System.err.println("delimiterSplit      : "+StringUtils.join(";", tokens));
	
			tokens = markColons(tokens);
//			System.err.println("markColons          : "+StringUtils.join(";", tokens));
	
			// Look for target definitions here
			// if it exists, then we will skip the next two transformations
			// target definition lines should only be STRING, RAW, or COLON
			// because 1 is a valid filename, (and a valid VarInt), we need
			// to avoid parsing it out...
			
			boolean foundColon = false;
			for (Token tok: tokens) {
				if (tok.isStatement()) {
					// a statement is allowed before a colon (ex: for i:start..end)
					// if you need to use the name of a statement for a filename, 
					// then it can be quoted.
					foundColon = false;
					break;
				}
				if (tok.isColon()) {
					foundColon = true;
				}
			}
			
			if (!foundColon) {
				tokens = markOperators(tokens);
//				System.err.println("markOperators       : "+StringUtils.join(";", tokens));
		
				tokens = parseValues(tokens);
//				System.err.println("parseValues         : "+StringUtils.join(";", tokens));
		
				tokens = correctNegativeNum(tokens);
//				System.err.println("correctNegativeNum  : "+StringUtils.join(";", tokens));
			}
			return new TokenList(tokens, line);
			
		} catch(ASTParseException e) {
			e.setErrorLine(line);
			throw e;
		}
	}
	public static List<Token> markSplitLine(List<Token> tokens) throws ASTParseException {
		if (tokens.size() == 0) {
			return tokens;
		}
		List<Token> out = new ArrayList<Token>();
		out.addAll(tokens.subList(0,  tokens.size()-1));
		
		Token last = tokens.get(tokens.size()-1);
		if (!last.isRaw()) {
			out.add(last);
			return out;
		}

		String s = StringUtils.rstrip(last.getStr());

		if (!s.endsWith("\\")) {
			out.add(last);
			return out;
		}
		
		s = s.substring(0, s.length()-1);
		out.add(Token.raw(s));
		out.add(Token.splitline());
		
		return out;
	}
	
	public static List<Token> markColons(List<Token> tokens) throws ASTParseException {
		if (tokens.size() == 0) {
			return tokens;
		}

		List<Token> out = new ArrayList<Token>();
		for (Token tok: tokens) {
			if (!tok.isRaw()) {
				out.add(tok);
				continue;
			}
			
			String buf = "";
			for (int i=0; i<tok.getStr().length(); i++) {
				if (tok.getStr().charAt(i) == ':') {
					out.add(Token.raw(buf));
					out.add(Token.colon());
					buf = "";
				} else {
					buf += tok.getStr().charAt(i);
				}
			}
			if (!buf.equals("")) {
				out.add(Token.raw(buf));
			}
		}

		return out;
	}

	public static List<Token> correctNegativeNum(List<Token> tokens) throws ASTParseException {
		List<Token> out = new ArrayList<Token>();

		Token twoback = null;
		Token oneback = null;
		for (Token tok: tokens) {
			boolean updated = false;
			if (tok.isValue() && tok.getValue().isNumber()) {
				if (oneback != null && oneback.isOperator() && oneback.getOp().equals(Operator.SUB)) {
					if (twoback == null || (!twoback.isValue() && !twoback.isVariable())) {
						if (tok.getValue().getClass().equals(VarInt.class)) {
							Long num = (Long) tok.getValue().getObject();
							out.remove(out.size()-1);
							out.add(Token.value(new VarInt(num * -1)));
							updated = true;
						} else if (tok.getValue().getClass().equals(VarFloat.class)) {
							Double num = (Double) tok.getValue().getObject();
							out.remove(out.size()-1);
							out.add(Token.value(new VarFloat(num * -1)));
							updated = true;
						}
					}
				}
			}
			
			if (!updated) {
				out.add(tok);
			}
			
			twoback = oneback;
			oneback = tok;
		}
		
		return out;
	}
	

	public static List<Token> parseValues(List<Token> tokens) throws ASTParseException {
		List<Token> out = new ArrayList<Token>();

		for (Token tok: tokens) {
			if (!tok.isRaw()) {
				out.add(tok);
			} else {
				try {
					out.add(Token.value(VarValue.parseString(tok.getStr())));
				} catch (VarTypeException e) {
					out.add(Token.var(tok.getStr()));
				}
			}
		}
		
		return out;
	}
//	
//	public static List<Token> findShell(List<Token> tokens) throws ASTParseException {
//		List<Token> out = new ArrayList<Token>();
//
//		for (Token tok: tokens) {
//			if (tok.getType()!=TokenClass.RAW) {
//				out.add(tok);
//				continue;
//			}
//			
//			boolean inshell = false;
//			
//			String buf = "";
//			for (int i=0; i<tok.getStr().length(); i++ ) {
//				if (inshell) {
//					if (tok.getStr().charAt(i) == ')') {
//						inshell = false;
//						out.add(Token.shell(buf));
//						buf = "";
//					} else {
//						buf += tok.getStr().charAt(i);
//					}
//					
//				} else {
//					if (i < tok.getStr().length()-1 && tok.getStr().substring(i, i+2).equals("$(")) {
//						if (!buf.equals("")) {
//							out.add(Token.raw(buf));
//						}
//						inshell = true;
//						buf = "";
//						i += 1;
//					} else {
//						buf += tok.getStr().charAt(i);
//					}
//				}
//			}
//			if (!buf.equals("")) {
//				out.add(Token.raw(buf));
//			}
//		}
//		
//		return out;
//	}


	public static List<Token> delimiterSplit(List<Token> tokens, char delim) throws ASTParseException {
		List<Token> out = new ArrayList<Token>();

		for (Token tok: tokens) {
			if (tok.getType()!=TokenType.RAW) {
				out.add(tok);
				continue;
			}
			
			String buf = "";
			for (int i=0; i<tok.getStr().length(); i++ ) {
				if (tok.getStr().charAt(i) == delim) {
					if (!buf.equals("")) {
						out.add(Token.raw(buf));
					}
					buf = "";
				} else {
					buf += tok.getStr().charAt(i);
				}
			}
			if (!buf.equals("")) {
				out.add(Token.raw(buf));
			}
		}
		
		return out;
	}

	public static List<Token> markOperators(List<Token> tokens) throws ASTParseException {
		List<Token> out = new ArrayList<Token>();

		for (Token tok: tokens) {
			if (!tok.isRaw()) {
				out.add(tok);
				continue;
			}
			String buf = "";
			int i=0;
			while (i<tok.getStr().length()) {
				boolean found = false;
				for (Statement stmt: Statement.statements) {
					if (tok.getStr().length() - i >= stmt.getName().length()) {
						if (stmt.getName().equals(tok.getStr().substring(i, i+stmt.getName().length()))) {
							if (!buf.equals("")) {
								out.add(Token.raw(buf));
							}
							out.add(Token.statement(stmt));
							buf = "";
							found = true;
							i += stmt.getName().length();
							break;
						}
					}
				}
				if (!found) {
					for (Operator op: Operator.operators) {
						if (tok.getStr().length() >= i+op.getSymbol().length()) {
							if (op.getSymbol().equals(tok.getStr().substring(i,  i+op.getSymbol().length()))) {
								if (!buf.equals("")) {
									out.add(Token.raw(buf));
								}
								out.add(Token.op(op));
								buf = "";
								found = true;
								i += op.getSymbol().length();
								break;
							}
						}
					}
					if (!found) {
						if (tok.getStr().substring(i,i+1).equals("(")) {
							if (!buf.equals("")) {
								out.add(Token.raw(buf));
							}
							out.add(Token.parenOpen());
							buf = "";
							found = true;
							i += 1;
						} else if (tok.getStr().substring(i,i+1).equals(")")) {
							if (!buf.equals("")) {
								out.add(Token.raw(buf));
							}
							out.add(Token.parenClose());
							buf = "";
							found = true;
							i += 1;
						}
					}
				}
				if (!found) {
					buf += tok.getStr().charAt(i);
					i += 1;
				}
			}
			if (!buf.equals("")) {
				out.add(Token.raw(buf));
			}
		}
		
		return out;
	}

	
	public static List<Token> extractQuotedStrings(String line, char quoteChar, char commentChar) throws ASTParseException {
		String buf = "";
		List<Token> tokens = new ArrayList<Token>();
		boolean inquote = false;
		boolean inshell = false;
		for (int i=0; i<line.length(); i++) {
			if (inquote) {
				if (line.charAt(i) == quoteChar && !buf.endsWith("\\")) {
					tokens.add(Token.string(buf));
					inquote = false;
					buf = "";
				} else {
					buf += line.charAt(i);
				}
			} else if (inshell) {
				if (line.charAt(i) == ')' && !buf.endsWith("\\")) {
					tokens.add(Token.shell(buf));
					inshell = false;
					buf = "";
				} else {
					buf += line.charAt(i);
				}
			}  else {
				if (i < line.length()-2 && line.substring(i, i+2).equals("$(") && !buf.endsWith("\\")) {
					if (!buf.equals("")) {
						tokens.add(Token.raw(buf));
					}
					buf = "";
					inshell = true;
					i++;
				} else if (line.charAt(i) == quoteChar && !buf.endsWith("\\")) {
					if (!buf.equals("")) {
						tokens.add(Token.raw(buf));
					}
					buf = "";
					inquote = true;
				} else if (line.charAt(i) == commentChar) {
					if (!buf.equals("")) {
						tokens.add(Token.raw(buf));
					}
					buf = "";
					break;
				} else {
					buf += line.charAt(i);
				}
			}
		}
		
		if (inquote) {
			throw new ASTParseException("Error parsing line - missing quotes: "+line);
		}
		
		if (!buf.equals("")) {
			tokens.add(Token.raw(buf));
		}
		
		return tokens;
	}
	
}
