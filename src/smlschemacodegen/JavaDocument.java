package smlschemacodegen;

public class JavaDocument extends IndentedStringBuilder {
	public JavaDocument() {
		super("{", "}");
	}
	
	private boolean isFirstIf = false;
	
	public void beginIf() {
		isFirstIf = true;
	}
	
	public void singleLineIfElse(String condition, String action) {
		String prefix = "else if ";
		if (isFirstIf) {
			isFirstIf = false;
			prefix = "if ";
		}
		appendLine(prefix + "("+condition+") { "+action+" }");
	}
	
	public void singleLineElse(String action) {
		appendLine("else { "+action+" }");
	}
}
