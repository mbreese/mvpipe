package io.compgen.mvpipe.parser.tokens;

import io.compgen.mvpipe.parser.NumberedLine;
import io.compgen.mvpipe.support.StringUtils;

import java.util.Iterator;
import java.util.List;

public class TokenList implements Iterable<Token> {
	final private List<Token> tokens;
	final private NumberedLine line;
	
	public TokenList(List<Token> tokens, NumberedLine line) {
		this.tokens = tokens;
		this.line = line;
	}
	
	public Token get(int idx) {
		return tokens.get(idx);
	}
	
	public TokenList subList(int from) {
		return subList(from, tokens.size());
	}
	public TokenList subList(int from, int to) {
		return new TokenList(tokens.subList(from, to), line);
	}
	public int size() {
		return tokens.size();
	}

	@Override
	public Iterator<Token> iterator() {
		return tokens.iterator();
	}

	public NumberedLine getLine() {
		return line;
	}

	public void append(TokenList tokens) {
		this.tokens.addAll(tokens.tokens);
	}
	
	public String toString() {
		return StringUtils.join(";", tokens);
	}
}
