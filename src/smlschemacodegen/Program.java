package smlschemacodegen;

import com.stenway.sml.schema.SmlSchema;

public class Program {

	public static void main(String[] args) {
		try {
			String schemaFilePath = "D:\\LspSchemaTest\\Lsp.schema";
			
			SmlSchema schema = SmlSchema.load(schemaFilePath);
			SmlSchemaCodeGenerator codeGenerator = new SmlSchemaCodeGenerator(schema);
			
			codeGenerator.generate("test", "D:\\NetBeans\\SmlSchemaCodeGenTest\\src\\test\\Generated.java");
			
			System.out.println("[SUCCESS]");
			
		} catch (Exception e) {
			System.out.println("[ERROR] "+e.getClass().getName() + ": " + e.getMessage());
		}
	}
	
}
