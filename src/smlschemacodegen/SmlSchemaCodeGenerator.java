package smlschemacodegen;

import com.stenway.reliabletxt.ReliableTxtDocument;
import com.stenway.sml.schema.SmlSchema;
import com.stenway.sml.schema.SsAttribute;
import com.stenway.sml.schema.SsChild;
import com.stenway.sml.schema.SsChildAttribute;
import com.stenway.sml.schema.SsChildElement;
import com.stenway.sml.schema.SsChildList;
import com.stenway.sml.schema.SsElement;
import com.stenway.sml.schema.SsItem;
import com.stenway.sml.schema.SsNamespace;
import com.stenway.sml.schema.SsPredefinedValueType;
import com.stenway.sml.schema.SsValueType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

public class SmlSchemaCodeGenerator {
	SmlSchema schema;
	JavaDocument document = new JavaDocument();
	String classPrefix = "";
	String exceptionName;
	String packageName;
	
	public SmlSchemaCodeGenerator(SmlSchema schema) {
		this.schema = schema;
		exceptionName = getElementClassName(schema.RootElement)+"Exception";
	}
		
	private String getElementClassName(SsElement element) {
		return classPrefix + element.Id;
	}
	
	private String getElementName(SsElement element) {
		return element.Id;
	}
	
	private void generateChildElement(SsElement ssElement, SsChildElement childElement, JavaDocument parseMethodDocument, JavaDocument toElementMethodContent) {
		String className = getElementClassName(childElement.Element);
		String elementName = getElementName(childElement.Element);
		
		if (childElement.Occurrence.isOptional()) {
			document.appendLine("public "+className+" "+elementName+";");
			document.appendLine();
			document.open("public boolean has"+elementName+"() ");
			document.appendLine("return this."+elementName+" != null;");
			document.close();
			
			parseMethodDocument.appendLine("SchemaUtils.assureOptionalElement(element, "+javaStr(elementName)+");");
			parseMethodDocument.open("if (element.hasElement("+javaStr(elementName)+")) ");
			parseMethodDocument.appendLine("result."+elementName+" = "+packageName+"."+className+".parse(element.element("+javaStr(elementName)+"));");
			parseMethodDocument.close();
			
			toElementMethodContent.open("if (this.has"+elementName+"()) ");
			toElementMethodContent.appendLine("result.add(this."+elementName+".toElement());");
			toElementMethodContent.close();
		} else if (childElement.Occurrence.isRequired()) {
			document.appendLine("public "+className+" "+elementName+" = new "+className+"();");
			document.appendLine();
			
			parseMethodDocument.appendLine("SchemaUtils.assureRequiredElement(element, "+javaStr(elementName)+");");
			parseMethodDocument.open("");
			parseMethodDocument.appendLine("result."+elementName+" = "+packageName+"."+className+".parse(element.element("+javaStr(elementName)+"));");
			parseMethodDocument.close();
			
			toElementMethodContent.open("if (this."+elementName+" == null) ");
			toElementMethodContent.appendLine(getExceptionStr(javaStr("Field '"+elementName+"' in class '"+ssElement.Id+"' is null")));
			toElementMethodContent.close();
			toElementMethodContent.appendLine("result.add(this."+elementName+".toElement());");
		} else if (childElement.Occurrence.isRepeatStar() || childElement.Occurrence.isRepeatPlus()) {
			document.appendLine("public ArrayList<"+className+"> "+getPlural(elementName)+" = new ArrayList<>();");
			document.appendLine();
			
			if (childElement.Occurrence.isRepeatPlus()) {
				parseMethodDocument.appendLine("SchemaUtils.assureRepeatedPlusElement(element, "+javaStr(elementName)+");");
			}
			parseMethodDocument.open("for (SmlElement childElement: element.elements("+javaStr(elementName)+")) ");
			parseMethodDocument.appendLine("result."+getPlural(elementName)+".add("+className+".parse(childElement));");
			parseMethodDocument.close();
			
			if (childElement.Occurrence.isRepeatPlus()) {
				toElementMethodContent.appendLine("if (this."+getPlural(elementName)+".size() == 0) { "+getExceptionStr(javaStr("'"+getPlural(elementName) +"' must have at least one value"))+" }");
			}
			toElementMethodContent.open("for ("+className+" listValue : this."+getPlural(elementName)+") ");
			toElementMethodContent.appendLine("result.add(listValue.toElement());");
			toElementMethodContent.close();
		} else {
			throw new RuntimeException("Todo");
		}
	}
	
	private String[] getTypeCode(SsAttribute attribute, String attributeName, String thisStr) {
		String className = null;
		String parseLine = null;
		String toAttributeLine = null;
		if (attribute.isArray()) {
			if (attribute.isPredefinedValueType()) {
				if (attribute.PredefinedValueType == SsPredefinedValueType.String) {
					className = "String[]";
					parseLine = "attribute.getValues()";
					toAttributeLine = "("+className+")"+thisStr+attributeName;
				} else if (attribute.PredefinedValueType == SsPredefinedValueType.Bool) {
					className = "boolean[]";
					parseLine = "attribute.getBooleans()";
					toAttributeLine = "("+className+")"+thisStr+attributeName;
				} else {
					throw new RuntimeException("TODO");
				}
			}
		} else {
			if (attribute.isPredefinedValueType()) {
				if (attribute.PredefinedValueType == SsPredefinedValueType.String) {
					className = "String";
					parseLine = "attribute.getString()";
					toAttributeLine = "("+className+")"+thisStr+attributeName;
				} else if (attribute.PredefinedValueType == SsPredefinedValueType.Bool) {
					className = "Boolean";
					parseLine = "attribute.getBoolean()";
					toAttributeLine = "("+className+")"+thisStr+attributeName;
				} else {
					throw new RuntimeException("TODO");
				}
			} else if (attribute.isValueType()) {
				if (attribute.ValueType.isEnumeration()) {
					className = attribute.ValueType.Id;
					parseLine = "ValueTypeUtils.parse"+attribute.ValueType.Id+"(attribute.getString())";
					toAttributeLine = "ValueTypeUtils.get"+attribute.ValueType.Id+"String(("+className+")"+thisStr+attributeName+")";
				}
			}
		}
		if (className == null || parseLine == null || toAttributeLine == null) {
			throw new RuntimeException("Todo");
		}
		return new String[] {className, parseLine, toAttributeLine};
	}
	
	private String getPlural(String name) {
		return name + "List";
	}
	
	private void generateChildAttribute(SsElement ssElement, SsChildAttribute childAttribute, JavaDocument parseMethodDocument, JavaDocument toElementMethodContent) {
		String attributeName = childAttribute.Attribute.Name;
		
		String[] typeCode = getTypeCode(childAttribute.Attribute, attributeName, "this.");
		String className = typeCode[0];
		String parseLine = typeCode[1];
		String toAttributeLine = typeCode[2];
		
		if (childAttribute.Occurrence.isOptional()) {
			document.appendLine("public "+className+" "+attributeName+";");
			document.appendLine();
			document.open("public boolean has"+attributeName+"() ");
			document.appendLine("return this."+attributeName+" != null;");
			document.close();
			
			parseMethodDocument.appendLine("SchemaUtils.assureOptionalAttribute(element, "+javaStr(attributeName)+");");
			parseMethodDocument.open("if (element.hasAttribute("+javaStr(attributeName)+")) ");
			parseMethodDocument.appendLine("SmlAttribute attribute = element.attribute("+javaStr(attributeName)+");");
			parseMethodDocument.appendLine("result."+attributeName+" = "+parseLine+";");
			parseMethodDocument.close();
			
			toElementMethodContent.open("if (this.has"+attributeName+"()) ");
			toElementMethodContent.appendLine("result.add(new SmlAttribute("+javaStr(attributeName)+", "+toAttributeLine+"));");
			toElementMethodContent.close();
		} else if (childAttribute.Occurrence.isRequired()) {
			document.appendLine("public "+className+" "+attributeName+";");
			document.appendLine();
			
			parseMethodDocument.open("");
			parseMethodDocument.appendLine("SchemaUtils.assureRequiredAttribute(element, "+javaStr(attributeName)+");");
			parseMethodDocument.appendLine("SmlAttribute attribute = element.attribute("+javaStr(attributeName)+");");
			parseMethodDocument.appendLine("result."+attributeName+" = "+parseLine+";");
			parseMethodDocument.close();
			
			toElementMethodContent.open("if (this."+attributeName+" == null) ");
			toElementMethodContent.appendLine(getExceptionStr(javaStr("Field '"+attributeName+"' in class '"+ssElement.Id+"' is null")));
			toElementMethodContent.close();
			toElementMethodContent.appendLine("result.add(new SmlAttribute("+javaStr(attributeName)+", "+toAttributeLine+"));");
		} else if (childAttribute.Occurrence.isRepeatStar() || childAttribute.Occurrence.isRepeatPlus()) {
			document.appendLine("public ArrayList<"+className+"> "+getPlural(attributeName)+" = new ArrayList<>();");
			document.appendLine();
			
			if (childAttribute.Occurrence.isRepeatPlus()) {
				parseMethodDocument.appendLine("SchemaUtils.assureRepeatedPlusAttribute(element, "+javaStr(attributeName)+");");
			}
			parseMethodDocument.open("for (SmlAttribute attribute : element.attributes("+javaStr(attributeName)+")) ");
			parseMethodDocument.appendLine("result."+getPlural(attributeName)+".add("+parseLine+");");
			parseMethodDocument.close();
			
			if (childAttribute.Occurrence.isRepeatPlus()) {
				toElementMethodContent.appendLine("if (this."+getPlural(attributeName)+".size() == 0) { "+getExceptionStr(javaStr("'"+getPlural(attributeName) +"' must have at least one value"))+" }");
			}
			toElementMethodContent.open("for ("+className+" listValue : this."+getPlural(attributeName)+") ");
			toElementMethodContent.appendLine("result.add(new SmlAttribute("+javaStr(attributeName)+", listValue));");
			toElementMethodContent.close();
		} else {
			throw new RuntimeException("Todo");
		}
	}
	
	private void generateChildList(SsElement ssElement, SsChildList childList, JavaDocument parseMethodDocument, JavaDocument toElementMethodContent) {
		
		document.appendLine("public ArrayList<Object> "+childList.Name+" = new ArrayList<>();");
		document.appendLine();

		if (childList.Items.size() > 0) {
			parseMethodDocument.open("for (SmlNode childNode : element.Nodes) ");
			parseMethodDocument.open("if (childNode instanceof SmlElement ) ");
			parseMethodDocument.appendLine("SmlElement childElement = (SmlElement)childNode;");
			parseMethodDocument.beginIf();
			for (SsItem childItem : childList.Items) {
				if (childItem instanceof SsElement) {
					SsElement childElement = (SsElement)childItem;
					parseMethodDocument.singleLineIfElse("childElement.hasName("+javaStr(childElement.Name)+")", "result."+childList.Name+".add("+childElement.Name+".parse(childElement));");
				}
			}
			parseMethodDocument.closeOpen("} else if (childNode instanceof SmlAttribute) {");
			parseMethodDocument.appendLine("SmlAttribute attribute = (SmlAttribute)childNode;");
			
			parseMethodDocument.beginIf();
			for (SsItem childItem : childList.Items) {
				if (childItem instanceof SsAttribute) {
					SsAttribute childAttribute = (SsAttribute)childItem;
					
					String[] typeCode = getTypeCode(childAttribute, "attributeObj", "");
					String className = typeCode[0];
					String parseLine = typeCode[1];
					String toAttributeLine = typeCode[2];
					
					parseMethodDocument.singleLineIfElse("attribute.hasName("+javaStr(childAttribute.Name)+")", 
							"result."+childList.Name+".add(new AbstractMap.SimpleEntry<String,Object>("+javaStr(childAttribute.Name)+", "+parseLine+"));");
				}
			}
			
			parseMethodDocument.close();
			parseMethodDocument.close();
			
			
			toElementMethodContent.open("for (Object listValue : this."+childList.Name+") ");
			toElementMethodContent.open("if (listValue instanceof AbstractMap.SimpleEntry) ");
			toElementMethodContent.appendLine("AbstractMap.SimpleEntry entry = (AbstractMap.SimpleEntry)listValue;");
			toElementMethodContent.appendLine("String attributeName = (String)entry.getKey();");
			toElementMethodContent.appendLine("Object attributeObj = entry.getValue();");
			
			toElementMethodContent.beginIf();
			for (SsItem childItem : childList.Items) {
				if (childItem instanceof SsAttribute) {
					SsAttribute childAttribute = (SsAttribute)childItem;
					
					String[] typeCode = getTypeCode(childAttribute, "attributeObj", "");
					String className = typeCode[0];
					String parseLine = typeCode[1];
					String toAttributeLine = typeCode[2];
					
					toElementMethodContent.singleLineIfElse("attributeName.equalsIgnoreCase("+javaStr(childAttribute.Name)+")", 
							"result.add(new SmlAttribute("+javaStr(childAttribute.Name)+", "+toAttributeLine+"));");
				}
			}
			
			toElementMethodContent.closeOpen("} else {");
			toElementMethodContent.beginIf();
			for (SsItem childItem : childList.Items) {
				if (childItem instanceof SsElement) {
					SsElement childElement = (SsElement)childItem;
					toElementMethodContent.singleLineIfElse("listValue instanceof "+childElement.Name, "result.add((("+childElement.Name+")listValue).toElement());");
				}
			}
			toElementMethodContent.singleLineElse(getExceptionStr(javaStr("Unknown child list item type")));
			toElementMethodContent.close();
			
			toElementMethodContent.close();
		}
	}
	
	private String javaStr(String content) {
		content = content.replace("\"", "\\\"");
		return "\""+content+"\"";
	}
	
	private String getExceptionStr(String message) {
		return "throw new "+exceptionName+"("+message+");";
	}
		
	private void generateElementClassParseMethod(SsElement element, JavaDocument parseMethodContent) {
		String className = getElementClassName(element);
		
		document.open("public static "+className+" parse(SmlElement element) ");
		document.open("if (!element.hasName("+javaStr(element.Name)+")) ");
		document.appendLine(getExceptionStr(javaStr("Element with name \""+element.Name+"\" was expected, but found \"")+"+element.getName()+"+javaStr("\"")));
		document.close();
		document.appendLine(className+" result = new "+className+"();");
		
		document.appendLines(parseMethodContent.toString());
		
		document.appendLine("return result;");
		document.close();
		document.appendLine();
	}
	
	private void generateElementClassToElementMethod(SsElement element, JavaDocument toElementMethodContent) {
		document.open("public SmlElement toElement() ");
		document.appendLine("SmlElement result = new SmlElement("+javaStr(element.Name)+");");
		
		document.appendLines(toElementMethodContent.toString());
		
		document.appendLine("return result;");
		document.close();
		document.appendLine();
	}
	
	private void generateElementClass(SsElement element) {
		String className = getElementClassName(element);
		
		SsElement curElement = element;
		
		if (element.Synonym != null) {
			document.open("class "+ className + " extends "+ getElementClassName(element.Synonym));
			curElement = element.Synonym;
		} else {
			document.open("class "+ className + " ");
		}
		
		JavaDocument parseMethodContent = new JavaDocument();
		JavaDocument toElementMethodContent = new JavaDocument();
		for (SsChild child : curElement.UnorderedChildren) {
			if (child instanceof SsChildElement) {
				generateChildElement(curElement, (SsChildElement)child, parseMethodContent, toElementMethodContent);
			} else if (child instanceof SsChildAttribute) {
				generateChildAttribute(curElement, (SsChildAttribute)child, parseMethodContent, toElementMethodContent);
			} else if (child instanceof SsChildList) {
				generateChildList(curElement, (SsChildList)child, parseMethodContent, toElementMethodContent);
			}
			document.appendLine();
		}
		generateElementClassParseMethod(element, parseMethodContent);
		generateElementClassToElementMethod(element, toElementMethodContent);
				
		if (element == schema.RootElement) {
			document.open("public static "+className+" load(String filePath) throws IOException ");
			document.appendLine("SmlDocument document = SmlDocument.load(filePath);");
			document.appendLine("return parse(document.getRoot());");
			document.close();
			document.appendLine();
			
			document.open("public void save(String filePath) throws IOException ");
			document.appendLine("SmlElement rootElement = this.toElement();");
			document.appendLine("SmlDocument document = new SmlDocument(rootElement);");
			document.appendLine("document.save(filePath);");
			document.close();
		}
		
		document.close();
		document.appendLine();
	}
	
	private void generateValueTypeClass(SsValueType valueType) {
		if (valueType.isEnumeration()) {
			String enumName = valueType.Id;
			document.open("enum "+ enumName + " ");
			
			for (int i=0; i<valueType.Enumeration.Values.size(); i++) {
				String enumValue = valueType.Enumeration.Values.get(i);
				String comma = "";
				if (i < valueType.Enumeration.Values.size()-1) {
					comma = ",";
				}
				document.appendLine(enumValue.toUpperCase()+comma);
			}
			
			document.close();
			document.appendLine();
		}
	}
	
	private void generateElementClasses() {
		for (SsNamespace namespace : schema.Namespaces) {
			for (SsElement element : namespace.Elements.getValues()) {
				generateElementClass(element);
			}
			
			for (SsValueType valueType : namespace.ValueTypes.getValues()) {
				generateValueTypeClass(valueType);
			}
		}
	}
	
	private void generateSchemaExceptionClass() {
		document.open("class "+exceptionName+" extends RuntimeException ");
		
		document.open("public "+exceptionName+"(String message) ");
		document.appendLine("super(message);");
		document.close();
		
		document.close();
		document.appendLine();
	}
	
	private void generateSchemaUtilsClass() {
		document.open("class SchemaUtils ");
		
		document.open("public static void assureOptionalElement(SmlElement element, String elementName) ");
		document.appendLine("int count = element.elements(elementName).length;");
		document.open("if (count == 0 || count == 1) ");
		document.appendLine("return;");
		document.close();
		document.appendLine(getExceptionStr(javaStr("Element \"")+"+elementName+"+javaStr("\" is optional but was found ")+"+count+"+javaStr(" times in element \"")+"+element.getName()+"+javaStr("\"")));
		document.close();
		document.appendLine();
		
		document.open("public static void assureRequiredElement(SmlElement element, String elementName) ");
		document.appendLine("int count = element.elements(elementName).length;");
		document.open("if (count == 1) ");
		document.appendLine("return;");
		document.close();
		document.appendLine(getExceptionStr(javaStr("Element \"")+"+elementName+"+javaStr("\" is required but was found ")+"+count+"+javaStr(" times in element \"")+"+element.getName()+"+javaStr("\"")));
		document.close();
		document.appendLine();
		
		document.open("public static void assureRepeatedPlusElement(SmlElement element, String elementName) ");
		document.appendLine("int count = element.elements(elementName).length;");
		document.open("if (count >= 1) ");
		document.appendLine("return;");
		document.close();
		document.appendLine(getExceptionStr(javaStr("Element \"")+"+elementName+"+javaStr("\" is required but was found ")+"+count+"+javaStr(" times in element \"")+"+element.getName()+"+javaStr("\"")));
		document.close();
		document.appendLine();
		
		document.open("public static void assureOptionalAttribute(SmlElement element, String attributeName) ");
		document.appendLine("int count = element.attributes(attributeName).length;");
		document.open("if (count == 0 || count == 1) ");
		document.appendLine("return;");
		document.close();
		document.appendLine(getExceptionStr(javaStr("Attribute \"")+"+attributeName+"+javaStr("\" is optional but was found ")+"+count+"+javaStr(" times in element \"")+"+element.getName()+"+javaStr("\"")));
		document.close();
		document.appendLine();
		
		document.open("public static void assureRequiredAttribute(SmlElement element, String attributeName) ");
		document.appendLine("int count = element.attributes(attributeName).length;");
		document.open("if (count == 1) ");
		document.appendLine("return;");
		document.close();
		document.appendLine(getExceptionStr(javaStr("Attribute \"")+"+attributeName+"+javaStr("\" is required but was found ")+"+count+"+javaStr(" times in element \"")+"+element.getName()+"+javaStr("\"")));
		document.close();
		document.appendLine();
		
		document.open("public static void assureRepeatedPlusAttribute(SmlElement element, String attributeName) ");
		document.appendLine("int count = element.attributes(attributeName).length;");
		document.open("if (count >= 1) ");
		document.appendLine("return;");
		document.close();
		document.appendLine(getExceptionStr(javaStr("Attribute \"")+"+attributeName+"+javaStr("\" is required but was found ")+"+count+"+javaStr(" times in element \"")+"+element.getName()+"+javaStr("\"")));
		document.close();
		document.appendLine();
				
		document.close();
		document.appendLine();
	}
	
	private void generateEnumValueTypeMethods(SsValueType valueType) {
		document.open("public static "+valueType.Id+" parse"+valueType.Id+"(String value) ");
		boolean isFirst = true;
		String compareMethod = "equalsIgnoreCase";
		if (!valueType.Enumeration.IsCaseSensitive) {
			compareMethod = "equals";
		}
		for (String enumValue : valueType.Enumeration.Values) {
			String prefix = "";
			if (isFirst) {
				isFirst = false;
			} else {
				prefix = "else ";
			}
			document.appendLine(prefix + "if (value."+compareMethod+"("+javaStr(enumValue)+")) { return "+valueType.Id+"."+enumValue.toUpperCase()+"; }");
		}
		document.appendLine(getExceptionStr(javaStr("Value \"")+"+value+"+javaStr("\" is not a valid "+valueType.Id)));
		document.close();
		document.appendLine();
		
		
		document.open("public static String get"+valueType.Id+"String("+valueType.Id+" value) ");
		isFirst = true;
		for (String enumValue : valueType.Enumeration.Values) {
			String prefix = "";
			if (isFirst) {
				isFirst = false;
			} else {
				prefix = "else ";
			}
			document.appendLine(prefix + "if (value == "+valueType.Id+"."+enumValue.toUpperCase()+") { return "+javaStr(enumValue)+"; }");
		}
		document.appendLine(getExceptionStr(javaStr("Invalid "+valueType.Id+" argument")));
		document.close();
		document.appendLine();
	}
	
	private void generateValueTypeUtilsClass() {
		document.open("class ValueTypeUtils ");
		
		for (SsNamespace namespace : schema.Namespaces) {
			for (SsValueType valueType : namespace.ValueTypes.getValues()) {
				if (valueType.isEnumeration()) {
					generateEnumValueTypeMethods(valueType);
				}
			}
		}
		
		document.close();
		document.appendLine();
	}
	
	private void generateUtilClasses() {
		generateSchemaExceptionClass();
		generateSchemaUtilsClass();
		generateValueTypeUtilsClass();
	}
	
	public void generate(String packageName, String filePath) throws IOException {
		this.packageName = packageName;
		document.appendLine("package "+packageName+";");
		document.appendLine();
		document.appendLine("import com.stenway.sml.SmlAttribute;");
		document.appendLine("import com.stenway.sml.SmlDocument;");
		document.appendLine("import com.stenway.sml.SmlElement;");
		document.appendLine("import com.stenway.sml.SmlEmptyNode;");
		document.appendLine("import com.stenway.sml.SmlNamedNode;");
		document.appendLine("import com.stenway.sml.SmlNode;");
		document.appendLine("import java.io.IOException;");
		document.appendLine("import java.util.AbstractMap;");
		document.appendLine("import java.util.ArrayList;");
		document.appendLine("import java.io.IOException;");
		
		document.appendLine();
		
		generateUtilClasses();
		
		generateElementClasses();
		document.appendLine();
						
		String strContent = document.toString();
		Files.write( Paths.get(filePath), strContent.getBytes());
	}
}
