import java.io.IOException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import ca.uwaterloo.cs.se.inconsistency.core.model2.so.ImpreciseModel;

class Main
{
	public static void main(String args[]) throws IOException, NullPointerException, ClassNotFoundException
	{
		String input_snippet="sample_3.txt";
		
//		String input_oracle="/home/s23subra/workspace/Java Snippet Parser/rt.xml";
		String input_oracle = "/home/s23subra/workspace/model-generator/neo4j-store-new-maven-data_handling_duplicates/";
		Parser parser=new Parser(input_snippet, input_oracle);
		CompilationUnit cu=parser.getCompilationUnit();
		int cutype=parser.getCuType();
//		ImpreciseModel model=parser.getModel();
//		MyASTVisitor visitor=new MyASTVisitor(model,cu,cutype);
		
		GraphDatabase db = parser.getGraph();
		MyNewASTVisitor visitor=new MyNewASTVisitor(db,cu,cutype);
		
		cu.accept(visitor);
		visitor.printJson();
		
		//visitor.printFields();
	}
}
//awk '{printf "%d\t%s\n", NR, $0}' < sample.txt >> print.txt
//shade.org.apache.http.params.CoreConnectionPNames