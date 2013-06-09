import java.io.IOException;

import org.eclipse.jdt.core.dom.CompilationUnit;

import ca.uwaterloo.cs.se.inconsistency.core.model2.so.ImpreciseModel;

class Main
{
	public static void main(String args[]) throws IOException, NullPointerException, ClassNotFoundException
	{
		String input_oracle="java_final_full.xml";
		String input_snippet="sample.txt";
		Parser parser=new Parser(input_snippet, input_oracle);
		CompilationUnit cu=parser.getCompilationUnit();
		ImpreciseModel model=parser.getModel();
		MyASTVisitor visitor=new MyASTVisitor(model);
		cu.accept(visitor);
		visitor.printFields();
	}
}