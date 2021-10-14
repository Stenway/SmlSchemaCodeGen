package smlschemacodegen;

import com.stenway.reliabletxt.ReliableTxtLines;
import java.util.ArrayList;

public class IndentedStringBuilder {
	StringBuilder sb = new StringBuilder();
	int indentationLevel;
	
	boolean lastWasEmpty = false;
	ArrayList<Boolean> firstElementIndicators = new ArrayList<>();
	
	String openingStr;
	String closingStr;
	
	public IndentedStringBuilder(String openingStr, String closingStr) {
		this.openingStr = openingStr;
		this.closingStr = closingStr;
		
		firstElementIndicators.add(true);
	}
	
	private void setNotFirst() {
		firstElementIndicators.set(firstElementIndicators.size()-1, false);
	}
	
	public void appendLine() {
		if (lastWasEmpty) return;
		if (firstElementIndicators.get(firstElementIndicators.size()-1) == true) {
			return;
		}
		appendLineIndented("");
		lastWasEmpty = true;
	}
	
	private void appendLineIndented(String content) {
		lastWasEmpty = false;
		setNotFirst();
		
		for (int i=0; i<indentationLevel; i++) {
			sb.append("\t");
		}
		sb.append(content);
		sb.append("\n");
	}
	
	public void appendLines(String content) {
		String[] lines = ReliableTxtLines.split(content);
		for (String line : lines) {
			appendLine(line);
		}
	}
	public void appendLine(String content) {
		if (content == null || content.length() == 0) {
			appendLine();
			return;
		}
		appendLineIndented(content);
	}
	
	public void open(String content) {
		appendLine(content+openingStr);
		
		indentationLevel++;
		firstElementIndicators.add(true);
	}
	
	public void close() {
		lastWasEmpty = false;
		indentationLevel--;
		appendLine(closingStr);
		firstElementIndicators.remove(firstElementIndicators.size()-1);
	}
	
	public void closeOpen(String content) {
		lastWasEmpty = false;
		indentationLevel--;
		appendLine(content);
		indentationLevel++;
	}

	@Override
	public String toString() {
		String content = sb.toString();
		content = removeLastEmptyLine(content);
		return content;
	}
	
	private String removeLastEmptyLine(String content) {
		if (content.length() > 0 && content.charAt(content.length()-1) == '\n') {
			return content.substring(0, content.length()-1);
		}
		return content;
	}
}