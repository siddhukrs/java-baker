import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;


import org.eclipse.jdt.core.dom.ASTVisitor;


import com.google.common.collect.HashMultimap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.json.JSONObject;
import org.neo4j.graphdb.Node;


class revisedASTVisitor extends ASTVisitor
{
	private HashMap<String, ArrayList<Node>> candidateClassNodesCache;
	private HashMap<String, ArrayList<Node>> candidateMethodNodesCache;
	private HashMap<Node, ArrayList<Node>> methodNodesInClassNode;
	private GraphDatabase model;
	private CompilationUnit cu;
	private int cutype;
	private HashMap<String, HashMultimap<ArrayList<Integer>,Node>> methodReturnTypesMap;
	private HashMap<String, HashMultimap<ArrayList<Integer>,Node>> variableTypeMap;//holds variables, fields and method param types
	private HashMultimap<Integer, Node> printtypes;//holds node start loc and possible types
	private HashMultimap<Integer, Node> printmethods;//holds node start posns and possible methods they can be
	private HashMap<String, Integer> printTypesMap;//maps node start loc to variable names
	private HashMap<String, Integer> printMethodsMap;//holds node start locs with method names
	private Set<String> importList = new HashSet<String>();
	private String classname = null;
	private String superclassname=null;
	private ArrayList<Object> interfaces=new ArrayList<Object>();
	private int tolerance = 3;

	private ArrayList<Node> getNewCeList(ArrayList<Node> celist)
	{
		ArrayList<Node> templist = new ArrayList<Node>();
		int flagVar2 = 0;
		int flagVar3 = 0;
		for(Node ce: celist)
		{
			String name = (String) ce.getProperty("id");
			int flagVar1 = 0;
			if(importList.isEmpty() == false)
			{
				for(String importItem : importList)
				{
					if(name.startsWith(importItem) || name.startsWith("java.lang"))
					{
						templist.clear();
						templist.add(ce);
						flagVar1 = 1;
						break;
					}
				}
			}
			if(flagVar1==1)
				break;
			else if(name.startsWith("java."))
			{
				if(flagVar2==0)
				{
					templist.clear();
					flagVar2 =1;
				}
				templist.add(ce);
				flagVar3 = 1;
			}
			else
			{
				if(flagVar3 == 0)
					templist.add(ce);
			}
		}
		return templist;
	}
	
	public void printFields()
	{
		System.out.println("globalmethods"+methodReturnTypesMap);
		System.out.println("globaltypes"+variableTypeMap);
		System.out.println("printtypes"+printtypes);
		System.out.println("printmethods"+printmethods);
		System.out.println("printTypesMap"+printTypesMap);
		System.out.println("printMethodsMap"+printMethodsMap);
		System.out.println("possibleImportList"+importList);
	}
	
	private String checkAndSlice(String string) 
	{
		int loc = string.indexOf('.');
		if(loc==-1)
			return null;
		else
		{
			return(string.substring(0, string.lastIndexOf("."))+".*") ;
		}
	}

	private void initializeMaps()
	{
		variableTypeMap = new HashMap<String, HashMultimap<ArrayList<Integer>,Node>>();
		methodReturnTypesMap = new HashMap<String, HashMultimap<ArrayList<Integer>,Node>>();
		printtypes = HashMultimap.create();
		printmethods = HashMultimap.create();
		printTypesMap = new HashMap<String, Integer>();
		printMethodsMap = new HashMap<String, Integer>();
		importList = new HashSet<String>();
		candidateClassNodesCache = new HashMap<String, ArrayList<Node>>();
		candidateMethodNodesCache = new HashMap<String, ArrayList<Node>>();
		methodNodesInClassNode = new HashMap<Node, ArrayList<Node>>();
	}

	public revisedASTVisitor(GraphDatabase db, CompilationUnit cu, int cutype) 
	{
		this.model=db;
		this.cu=cu;
		this.cutype=cutype;
		initializeMaps();
	}

	private ArrayList<Integer> getScopeArray(ASTNode treeNode)
	{
		ASTNode parentNode;
		ArrayList<Integer> parentList = new ArrayList<Integer>();
		while((parentNode =treeNode.getParent())!=null)
		{
			parentList.add(parentNode.getStartPosition());
			treeNode = parentNode;
		}
		return parentList;
	}

	public void endVisit(VariableDeclarationStatement treeNode)
	{
		ArrayList<Integer> variableScopeArray = getScopeArray(treeNode);
		String treeNodeType = treeNode.getType().toString();
		for(int j=0; j < treeNode.fragments().size(); j++)
		{
			HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
			String variableName = ((VariableDeclarationFragment)treeNode.fragments().get(j)).getName().toString();
			int startPosition = treeNode.getType().getStartPosition();
			if(variableTypeMap.containsKey(variableName))
			{
				candidateAccumulator = variableTypeMap.get(variableName);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}
			ArrayList<Node> candidateClassNodes=model.getCandidateClassNodes(treeNodeType, candidateClassNodesCache);
			
			candidateClassNodes = getNewCeList(candidateClassNodes);
			for(Node candidateClass : candidateClassNodes)
			{
				candidateAccumulator.put(variableScopeArray, candidateClass);
				if(candidateClassNodes.size() < tolerance)
				{
					String possibleImport = checkAndSlice(candidateClass.getProperty("id").toString());
					if(possibleImport!=null)
						importList.add(possibleImport);
				}
				printtypes.put(startPosition, candidateClass);
				printTypesMap.put(variableName, startPosition);
			}
			variableTypeMap.put(variableName, candidateAccumulator);
		}
	}

	public boolean visit(EnhancedForStatement treeNode)
	{
		ArrayList<Integer> variableScopeArray = getScopeArray(treeNode.getParent());
		HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
		String variableType = treeNode.getParameter().getType().toString();
		String variableName = treeNode.getParameter().getName().toString();
		if(variableTypeMap.containsKey(treeNode.getParameter().getName().toString()))
		{
			candidateAccumulator = variableTypeMap.get(variableName);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		ArrayList<Node> candidateClassNodes=model.getCandidateClassNodes(variableType, candidateClassNodesCache);
		candidateClassNodes = getNewCeList(candidateClassNodes);
		for(Node candidateClass : candidateClassNodes)
		{
			int startPosition = treeNode.getParameter().getType().getStartPosition();
			candidateAccumulator.put(variableScopeArray, candidateClass);
			if(candidateClassNodes.size() < tolerance)
			{
				String possibleImport = checkAndSlice(candidateClass.getProperty("id").toString());
				if(possibleImport!=null)
				{
					importList.add(possibleImport);
				}
			}
			printtypes.put(startPosition, candidateClass);
			printTypesMap.put(variableName, startPosition);
		}
		variableTypeMap.put(variableName, candidateAccumulator);
		return true;
	}

	public void endVisit(ForStatement node)
	{
		/*for(int j=0;j<node.initializers().size();j++)
		{
			//System.out.println(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString() + " : "+getScopeArray(node).toString() + "$$$$$");
			ArrayList<Integer> scopeArray = getScopeArray(node);
			HashMultimap<ArrayList<Integer>, Node> temp = null;
			if(globaltypes2.containsKey(((VariableDeclarationFragment)node.initializers().get(j)).getName().toString()))
			{
				temp = globaltypes2.get(((VariableDeclarationFragment)node.initializers().get(j)).getName().toString());
			}
			else
			{
				temp = HashMultimap.create();
			}
			Collection<Node> celist=
getCandidateClassNodes(((VariableDeclarationFragment)node.initializers().get(j)).getType().toString());
			celist = getNewCeList(celist);
			for(Node ce : celist)
			{
						temp.put(scopeArray, ce);
						printtypes.put(((VariableDeclarationFragment)node.initializers().get(j)).getStartPosition(), ce);
						printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
			}
			globaltypes2.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), temp);
		}*/
	}

	public void endVisit(FieldDeclaration treeNode) 
	{
		int startPosition = treeNode.getType().getStartPosition();
		for(int j=0; j < treeNode.fragments().size(); j++)
		{
			String fieldName = ((VariableDeclarationFragment)treeNode.fragments().get(j)).getName().toString();
			ArrayList<Integer> variableScopeArray = getScopeArray(treeNode);
			HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
			if(variableTypeMap.containsKey(fieldName))
			{
				candidateAccumulator = variableTypeMap.get(fieldName);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}
			if(treeNode.getType().getNodeType()==74)
			{
				String treeNodeType = ((ParameterizedType)treeNode.getType()).getType().toString();
				ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(treeNodeType, candidateClassNodesCache);
				candidateClassNodes = getNewCeList(candidateClassNodes);
				for(Node candidateClass : candidateClassNodes)
				{
					candidateAccumulator.put(variableScopeArray, candidateClass);
					if(candidateClassNodes.size() < tolerance)
					{
						String possibleImport = checkAndSlice(candidateClass.getProperty("id").toString());
						if(possibleImport!=null)
						{
							importList.add(possibleImport);
						}
					}
					printtypes.put(startPosition, candidateClass);
					printTypesMap.put(fieldName, startPosition);
				}
			}
			else
			{
				String treeNodeType = treeNode.getType().toString();
				ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(treeNodeType, candidateClassNodesCache);
				candidateClassNodes = getNewCeList(candidateClassNodes);
				for(Node candidateClass : candidateClassNodes)
				{
					candidateAccumulator.put(variableScopeArray, candidateClass);
					if(candidateClassNodes.size() < tolerance)
					{
						String possibleImport = checkAndSlice(candidateClass.getProperty("id").toString());
						if(possibleImport!=null)
						{
							importList.add(possibleImport);
						}
					}
					printtypes.put(startPosition, candidateClass);
					printTypesMap.put(fieldName, startPosition);
				}
			}
			variableTypeMap.put(fieldName, candidateAccumulator);
		}
	}

	public boolean isLocalMethod(String methodName)
	{
		return false;
	}

	public void endVisit(MethodInvocation treeNode)
	{
		long start = System.nanoTime();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		Expression expression=treeNode.getExpression();
		String treeNodeMethodExactName = treeNode.getName().toString();
		String treeNodeString = treeNode.toString();
		int startPosition = treeNode.getStartPosition();
		if(expression==null)
		{
			if(isLocalMethod(treeNodeMethodExactName) == true)
			{
				
			}
			else if(superclassname!=null)
			{	
				/*
				 * Handles inheritance, where methods from Superclasses can be directly called
				 */
				printTypesMap.put(treeNodeString, startPosition);
				printMethodsMap.put(treeNodeString, startPosition);
				HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
				if(methodReturnTypesMap.containsKey(treeNodeString))
				{
					candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
				}
				else
				{
					candidateAccumulator = HashMultimap.create();
				}
				
				ArrayList<Node> candidateSuperClassNodes = model.getCandidateClassNodes(superclassname, candidateClassNodesCache);
				candidateSuperClassNodes = getNewCeList(candidateSuperClassNodes);
				for(Node candidateSuperClass : candidateSuperClassNodes)
				{
					ArrayList<Node> candidateSuperClassMethods = model.getMethodNodes(candidateSuperClass, methodNodesInClassNode);
					for(Node candidateSuperClassMethod : candidateSuperClassMethods)
					{
						String candidateMethodExactName = (String)candidateSuperClassMethod.getProperty("exactName"); 
						if(candidateMethodExactName.equals(treeNodeMethodExactName))
						{
							if(matchParams(candidateSuperClassMethod, treeNode.arguments())==true)
							{
								if(candidateSuperClassNodes.size() < tolerance)
								{
									String possibleImport = checkAndSlice(candidateSuperClass.getProperty("id").toString());
									if(possibleImport!=null)
									{
										importList.add(possibleImport);
									}
								}
								
								printtypes.put(startPosition, candidateSuperClass);
								printmethods.put(startPosition, candidateSuperClassMethod);
								Node retElement = model.getMethodReturn(candidateSuperClassMethod);
								if(retElement!=null)
								{
									candidateAccumulator.put(scopeArray, retElement);
								}
							}
						}
					}
				}
				methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
			}
			else
			{
				/*
				 * Might be user declared helper functions or maybe object reference is assumed to be obvious in the snippet
				 */
				printTypesMap.put(treeNodeString, startPosition);
				printMethodsMap.put(treeNodeString, startPosition);
				HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
				if(methodReturnTypesMap.containsKey(treeNodeString))
				{
					candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
				}
				else
				{
					candidateAccumulator = HashMultimap.create();
				}
				
				ArrayList<Node> candidateMethodNodes = model.getCandidateMethodNodes(treeNode.getName().toString(), candidateMethodNodesCache);
				for(Node candidateMethodNode : candidateMethodNodes)
				{
					if(matchParams(candidateMethodNode, treeNode.arguments())==true)
					{
						Node methodContainerClassNode = model.getMethodContainer(candidateMethodNode);
						if(candidateMethodNodes.size() < tolerance)
						{
							String possibleImport = checkAndSlice(methodContainerClassNode.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
						printtypes.put(startPosition, methodContainerClassNode);
						printmethods.put(startPosition, candidateMethodNode);
						Node retElement = model.getMethodReturn(candidateMethodNode);
						if(retElement!=null)
						{
							candidateAccumulator.put(scopeArray, retElement);
						}
					}
				}
				methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
			}
		}
		else if(expression.toString().contains("System."))
		{

		}
		else if(expression.getNodeType() == 2)
		{
			//System.out.println("array method");
		}
		else if(variableTypeMap.containsKey(expression.toString()))
		{
			printTypesMap.put(treeNodeString, expression.getStartPosition());
			printMethodsMap.put(treeNodeString, expression.getStartPosition());
			
			ArrayList<Node> replacementClassNodesList = new ArrayList<Node>();

			HashMultimap<ArrayList<Integer>, Node> temporaryMap = variableTypeMap.get(expression.toString());
			ArrayList<Integer> rightScopeArray = getNodeSet(temporaryMap, scopeArray);
			if(rightScopeArray == null)
				return;
			
			Set<Node> candidateClassNodes = temporaryMap.get(rightScopeArray);

			HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
			if(methodReturnTypesMap.containsKey(treeNodeString))
			{
				candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}
			
			for(Node candidateClassNode : candidateClassNodes)
			{
				ArrayList<Node> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
				int hasCandidateFlag = 0;
				for(Node candidateMethodNode : candidateMethodNodes)
				{
					String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
					if((candidateMethodExactName).equals(treeNodeMethodExactName))
					{
						if(matchParams(candidateMethodNode, treeNode.arguments())==true)
						{
							printmethods.put(expression.getStartPosition(), candidateMethodNode);
							Node fcname=model.getMethodContainer(candidateMethodNode);
							if(fcname!=null)
								replacementClassNodesList.add(fcname);
							Node retElement = model.getMethodReturn(candidateMethodNode);
							if(retElement!=null)
							{
								candidateAccumulator.put(scopeArray, retElement);
							}
							hasCandidateFlag = 1;
						}
					}
				}
				if(hasCandidateFlag == 0)
				{
					ArrayList<Node> parentNodeList = model.getParents(candidateClassNode);
					parentNodeList = getNewCeList(parentNodeList);
					for(Node parentNode: parentNodeList)
					{
						ArrayList<Node> methodNodes = model.getMethodNodes(parentNode, methodNodesInClassNode);
						for(Node methodNode : methodNodes)
						{
							String candidateMethodExactName = (String)methodNode.getProperty("exactName");
							if(candidateMethodExactName.equals(treeNodeMethodExactName))
							{
								if(matchParams(methodNode, treeNode.arguments())==true)
								{
									printmethods.put(expression.getStartPosition(), methodNode);
									Node fcname=model.getMethodContainer(methodNode);
									if(fcname!=null)
										replacementClassNodesList.add(fcname);
									Node retElement = model.getMethodReturn(methodNode);
									if(retElement!=null)
									{
										candidateAccumulator.put(scopeArray, retElement);
									}
								}
							}
						}
					}
				}
			}
			methodReturnTypesMap.put(treeNodeString, candidateAccumulator);

			if(replacementClassNodesList.isEmpty()==false)
			{
				variableTypeMap.get(expression.toString()).replaceValues(rightScopeArray,replacementClassNodesList);
				printtypes.replaceValues(printTypesMap.get(expression.toString()), replacementClassNodesList);
			}
		}
		else if(expression.toString().matches("[A-Z][a-zA-Z]*"))
		{
			printTypesMap.put(treeNodeString, expression.getStartPosition());
			printMethodsMap.put(treeNodeString, expression.getStartPosition());
			HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
			if(methodReturnTypesMap.containsKey(treeNodeString))
			{
				candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}
			ArrayList <Node> replacementClassNodesList = new ArrayList<Node>();

			ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(expression.toString(), candidateClassNodesCache);
			candidateClassNodes = getNewCeList(candidateClassNodes);
			for(Node candidateClassNode : candidateClassNodes)
			{
				ArrayList<Node> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
				for(Node candidateMethodNode : candidateMethodNodes)
				{
					String candidateMetodExactName = (String)candidateMethodNode.getProperty("exactName");
					if(candidateMetodExactName.equals(treeNodeMethodExactName))
					{
						if(matchParams(candidateMethodNode, treeNode.arguments())==true)
						{
							printmethods.put(expression.getStartPosition(), candidateMethodNode);
							replacementClassNodesList.add(candidateClassNode);
							Node retElement = model.getMethodReturn(candidateMethodNode);
							if(retElement!=null)
							{
								candidateAccumulator.put(scopeArray, retElement);
							}
						}
					}
				}	
			}
			methodReturnTypesMap.put(treeNodeString, candidateAccumulator);

			if(replacementClassNodesList.isEmpty()==false)
			{ 
				if(variableTypeMap.containsKey(expression.toString()))
				{
					variableTypeMap.get(expression.toString()).replaceValues(scopeArray,replacementClassNodesList);
					printtypes.replaceValues(printTypesMap.get(expression.toString()), replacementClassNodesList);
				}
			}
		}
		else if(methodReturnTypesMap.containsKey(expression.toString()))
		{
			
			printTypesMap.put(treeNodeString, expression.getStartPosition());
			printMethodsMap.put(treeNodeString, expression.getStartPosition());
			
			HashMultimap<ArrayList<Integer>, Node> nodeInMap = methodReturnTypesMap.get(expression.toString());
			System.out.println(nodeInMap);
			System.out.println(scopeArray);
			HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
			if(methodReturnTypesMap.containsKey(treeNodeString))
			{
				candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}
			ArrayList<Node> replacementClassNodesList = new ArrayList<Node>();
			
			ArrayList<Integer> newscopeArray = getNodeSet(nodeInMap, scopeArray);
			Set<Node> candidateClassNodes = nodeInMap.get(newscopeArray);
			
			for(Node candidateClassNode : candidateClassNodes)
			{
				System.out.println(candidateClassNode.getProperty("id"));
				String candidateClassExactName = (String) candidateClassNode.getProperty("exactName");
				ArrayList<Node> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
				for(Node candidateMethodNode : candidateMethodNodes)
				{
					String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
					if(candidateMethodExactName.equals(treeNodeMethodExactName))
					{	
						if(matchParams(candidateMethodNode, treeNode.arguments())==true)
						{
							System.out.println(treeNode.getName() + " : " + candidateMethodNode.getProperty("id"));
							printmethods.put(expression.getStartPosition(), candidateMethodNode);
							Node fcname=model.getMethodContainer(candidateMethodNode);
							if(fcname!=null && ((String)fcname.getProperty("exactName")).equals(candidateClassExactName)==true)
								replacementClassNodesList.add(fcname);
							Node retElement = model.getMethodReturn(candidateMethodNode);
							if(retElement!=null)
							{
								candidateAccumulator.put(scopeArray, retElement);
							}
						}

					}
				}

			}
			methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
			
			if(replacementClassNodesList.isEmpty()==false)
			{
				HashMultimap<ArrayList<Integer>, Node> replacer = HashMultimap.create();
				replacer.putAll(scopeArray, replacementClassNodesList);
				methodReturnTypesMap.put(expression.toString(), replacer);
				if(printTypesMap.containsKey(expression.toString())==false)
				{
					printTypesMap.put(expression.toString(), expression.getStartPosition());
				}
				printtypes.replaceValues(treeNode.getExpression().getStartPosition(), replacementClassNodesList);
			}
		}
		else
		{
			ArrayList <Node> replacementClassNodesList= new ArrayList<Node>();
			HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
			if(methodReturnTypesMap.containsKey(treeNodeString))
			{
				candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
			}
			else
			{
				candidateAccumulator = HashMultimap.create();
			}
			printMethodsMap.put(treeNode.toString(), startPosition);
			ArrayList<Node> candidateMethodNodes = model.getCandidateMethodNodes(treeNodeMethodExactName, candidateMethodNodesCache);

			for(Node candidateMethodNode : candidateMethodNodes)
			{
				if(matchParams(candidateMethodNode, treeNode.arguments())==true)
				{
					Node fcname=model.getMethodContainer(candidateMethodNode);
					if(fcname!=null)
					{
						replacementClassNodesList.add(fcname);
					}
					printmethods.put(startPosition, candidateMethodNode);
					Node retElement = model.getMethodReturn(candidateMethodNode);
					if(retElement!=null)
					{
						candidateAccumulator.put(scopeArray, retElement);
					}
				}
			}
			methodReturnTypesMap.put(treeNodeString, candidateAccumulator);

			if(replacementClassNodesList.isEmpty()==false)
			{
				HashMultimap<ArrayList<Integer>, Node> replacer = HashMultimap.create();
				replacer.putAll(scopeArray, replacementClassNodesList);
				variableTypeMap.put(expression.toString(),replacer);
				if(printTypesMap.containsKey(expression.toString())==false)
				{
					printTypesMap.put(expression.toString(), expression.getStartPosition());
				}
				printtypes.replaceValues(treeNode.getExpression().getStartPosition(), replacementClassNodesList);
			}
		}
		long end = System.nanoTime();
		System.out.println(model.getCurrentMethodName() + " - " + treeNode.toString() + " : " + String.valueOf((double)(end-start)/1000000000));
	}

	private ArrayList<Integer> getNodeSet(HashMultimap<ArrayList<Integer>, Node> celist2, ArrayList<Integer> scopeArray) 
	{
		for(ArrayList<Integer> test : celist2.keySet())
		{
			if(isSubset(test, scopeArray))
				return test;
		}
		return null;
	}

	private boolean isSubset(ArrayList<Integer> test,ArrayList<Integer> scopeArray) 
	{
		if(scopeArray.containsAll(test))
			return true;
		else
			return false;
	}

	private boolean matchParams(Node me, List<ASTNode> params) 
	{
		long start = System.nanoTime();
		ArrayList<HashSet<String>> nodeArgs = new ArrayList<HashSet<String>>();
		TreeSet<Node>graphNodes = new TreeSet<Node>(new Comparator<Node>()
		{
			public int compare(Node a, Node b)
			{
				return (Integer)a.getProperty("paramIndex")-(Integer)b.getProperty("paramIndex");
			}
		});
		graphNodes = model.getMethodParams(me);
		if(graphNodes.size() != params.size())
			return false;
		if(params.size()==0 && graphNodes.size()==0)
		{
			return true;
		}
		for(ASTNode param : params)
		{
			HashSet<String> possibleTypes = new HashSet<String>();
			if(param.getNodeType()==34)
			{
				possibleTypes.add("int");
				possibleTypes.add("byte");
				possibleTypes.add("float");
				possibleTypes.add("double");
				possibleTypes.add("long");
				possibleTypes.add("short");
			}
			else if(param.getNodeType()==9)
			{
				possibleTypes.add("boolean");
			}
			else if(param.getNodeType()==13)
			{
				possibleTypes.add("char");
			}
			else if(param.getNodeType()==27)
			{
				InfixExpression tempNode = (InfixExpression) param;
				if(tempNode.getLeftOperand().getNodeType() == 45 || tempNode.getRightOperand().getNodeType() == 45)
					possibleTypes.add("String");
				else if(tempNode.getLeftOperand().getNodeType() == 34 || tempNode.getRightOperand().getNodeType() == 34)
				{
					possibleTypes.add("int");
					possibleTypes.add("byte");
					possibleTypes.add("float");
					possibleTypes.add("double");
					possibleTypes.add("long");
					possibleTypes.add("short");
				}
				else
					possibleTypes.add("UNKNOWN");
			}
			else if(param.getNodeType()==45)
			{
				possibleTypes.add("String");
			}
			else if (param.getNodeType()==42)
			{
				if(variableTypeMap.containsKey(param.toString()))
				{
					HashMultimap<ArrayList<Integer>, Node> celist_temp = variableTypeMap.get(param.toString());
					ArrayList<Integer> intermediate = getNodeSet(celist_temp, getScopeArray(param));
					if(intermediate!=null)
					{
						Set<Node> localTypes = celist_temp.get(intermediate);
						for(Node localType : localTypes)
						{
							possibleTypes.add((String) localType.getProperty("id"));
						}
					}
				}
				else
				{
					possibleTypes.add("UNKNOWN");
					//System.out.println("UNKNOWN");
				}
			}
			else if(param.getNodeType()==32)
			{
				if(methodReturnTypesMap.containsKey(param.toString()))
				{
					HashMultimap<ArrayList<Integer>, Node> temporaryMap = methodReturnTypesMap.get(param.toString());
					ArrayList<Integer> scopeArray = getScopeArray(param);
					ArrayList<Integer> rightScopeArray = getNodeSet(temporaryMap, scopeArray);
					if(rightScopeArray == null)
						return false;
					Set<Node> candidateClassNodes = temporaryMap.get(rightScopeArray);
					for(Node localType : candidateClassNodes)
					{
						possibleTypes.add((String) localType.getProperty("id"));
					}
				}
				else
				{
					possibleTypes.add("UNKNOWN");
					//System.out.println("UNKNOWN");
				}
			}
			else if(param.getNodeType()==14)
			{
				ClassInstanceCreation tempNode = (ClassInstanceCreation) param;
				possibleTypes.add(tempNode.getType().toString());
				//System.out.println("14:  "+tempNode.getType().toString());
			}
			else
			{
				possibleTypes.add("UNKNOWN");
			}
			nodeArgs.add(possibleTypes);
		}
		Iterator<Node> iter1 = graphNodes.iterator();
		Iterator<HashSet<String>> iter2 = nodeArgs.iterator();
		while(iter1.hasNext())
		{
			Node graphParam = iter1.next();
			HashSet<String> args = iter2.next();
			int flag=0;
			for(String arg : args)
			{
				if(graphParam.getProperty("exactName").equals(arg)== true || graphParam.getProperty("id").equals(arg)==true)
				{
					flag=0;
					break;
				}
				else if(arg.equals("UNKNOWN"))
				{
					flag=0;
					break;
				}
				else if(model.checkIfParentNode(graphParam, arg))
				{
					flag=0;
					break;
				}
				else
					flag=1;
			}
			if(flag==1)
				return false;
		}
		//System.out.println("MATCH : "+me.getProperty("id"));
		long end = System.nanoTime();
		System.out.println(model.getCurrentMethodName() + " - " + me.getProperty("id") + " : " + String.valueOf((double)(end-start)/1000000000));
		return true;
	}

	public boolean visit(TypeDeclaration treeNode)
	{
		classname = treeNode.getName().toString();
		if(treeNode.getSuperclassType()!=null)
		{
			if(treeNode.getSuperclassType().getNodeType()==74)
			{
				superclassname = ((ParameterizedType)treeNode.getSuperclassType()).getType().toString();
			}
			else
			{
				superclassname = treeNode.getSuperclassType().toString();
			}
		}

		for(Object ob : treeNode.superInterfaceTypes())
		{	
			interfaces.add(ob);
		}
		return true;
	}

	public boolean visit(MethodDeclaration treeNode)
	{
		@SuppressWarnings("unchecked")
		int startPosition = treeNode.getStartPosition();
		List<SingleVariableDeclaration> param=treeNode.parameters();
		for(int i=0;i<param.size();i++)
		{
			ArrayList<Integer> scopeArray = getScopeArray(treeNode);
			HashMultimap<ArrayList<Integer>,Node> temporaryMap = null;
			if(variableTypeMap.containsKey(param.get(i).getName().toString()))
			{
				temporaryMap = variableTypeMap.get(param.get(i).getName().toString());
			}
			else
			{
				temporaryMap = HashMultimap.create();
			}
			ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(param.get(i).getType().toString(), candidateClassNodesCache);
			candidateClassNodes = getNewCeList(candidateClassNodes);
			for(Node candidateClassNode : candidateClassNodes)
			{
				temporaryMap.put(scopeArray, candidateClassNode);
				if(candidateClassNodes.size() < tolerance)
				{
					String possibleImport = checkAndSlice(candidateClassNode.getProperty("id").toString());
					if(possibleImport!=null)
					{
						importList.add(possibleImport);
					}
				}
				printtypes.put(param.get(i).getType().getStartPosition(),candidateClassNode);
				printTypesMap.put(param.get(i).getName().toString(), param.get(i).getType().getStartPosition());
			}
			variableTypeMap.put(param.get(i).getName().toString(), temporaryMap);
		}

		if(superclassname!=null)
		{
			ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(superclassname, candidateClassNodesCache);
			candidateClassNodes = getNewCeList(candidateClassNodes);
			for(Node candidateClassNode : candidateClassNodes)
			{
				Collection<Node> candidateMethodNodes=model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
				for(Node candidateMethodNode : candidateMethodNodes)
				{
					String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
					if(candidateMethodExactName.equals(treeNode.getName()))
					{
						if(matchParams(candidateMethodNode, treeNode.parameters())==true)
						{
							Node parentNode = model.getMethodContainer(candidateMethodNode);
							if(candidateMethodNodes.size() < tolerance)
							{
								String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
								if(possibleImport!=null)
								{
									importList.add(possibleImport);
								}
							}
							printtypes.put(startPosition,parentNode);
							printmethods.put(startPosition, candidateMethodNode);
						}	
					}
				}
			}
		}

		if(!interfaces.isEmpty())
		{
			for(int i=0;i<interfaces.size();i++)
			{
				ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(interfaces.get(i).toString(), candidateClassNodesCache);
				candidateClassNodes = getNewCeList(candidateClassNodes);
				for(Node candidateClassNode : candidateClassNodes)
				{
					Collection<Node> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
					for(Node candidateMethodNode : candidateMethodNodes)
					{
						String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
						if(candidateMethodExactName.equals(treeNode.getName()))
						{
							if(matchParams(candidateMethodNode, treeNode.parameters())==true)
							{
								Node parentNode = model.getMethodContainer(candidateMethodNode);
								if(candidateMethodNodes.size() < tolerance)
								{
									String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
									if(possibleImport!=null)
									{
										importList.add(possibleImport);
									}
								}
								printtypes.put(startPosition,parentNode);
								printmethods.put(startPosition, candidateMethodNode);
							}
						}
					}
				}
			}
		}
		return true;
	}

	public void endVisit(ConstructorInvocation treeNode)
	{	
		String treeNodeString = treeNode.toString();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		int startPosition = treeNode.getStartPosition();
		ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(classname, candidateClassNodesCache);
		candidateClassNodes = getNewCeList(candidateClassNodes);
		for(Node candidateClassNode : candidateClassNodes)
		{
			ArrayList<Node> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
			for(Node candidateMethodNode : candidateMethodNodes)
			{
				String candidateMethodExactname = (String)candidateMethodNode.getProperty("exactName");
				if(candidateMethodExactname.equals("<init>"))
				{
					if(matchParams(candidateMethodNode, treeNode.arguments())==true)
					{
						printmethods.put(startPosition, candidateMethodNode);
						Node parentNode = model.getMethodContainer(candidateMethodNode);
						if(candidateMethodNodes.size() < tolerance)
						{
							String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
						printtypes.put(startPosition,parentNode);
						Node returnNode = model.getMethodReturn(candidateMethodNode);
						if(returnNode != null)
						{
							candidateAccumulator.put(scopeArray, returnNode);
						}

					}
				}
			}
		}
		methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
	}

	public boolean visit(CatchClause node)
	{
		int startPosition = node.getException().getType().getStartPosition();
		ArrayList<Integer> scopeArray = getScopeArray(node);

		HashMultimap<ArrayList<Integer>, Node> temporaryMap = null;
		if(variableTypeMap.containsKey(node.getException().getName().toString()))
		{
			temporaryMap = variableTypeMap.get(node.getException().getName().toString());
		}
		else
		{
			temporaryMap = HashMultimap.create();
		}
		ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(node.getException().getType().toString(), candidateClassNodesCache);
		candidateClassNodes = getNewCeList(candidateClassNodes);
		for(Node candidateClassNode : candidateClassNodes)
		{
			temporaryMap.put(scopeArray, candidateClassNode);
			if(candidateClassNodes.size() < tolerance)
			{
				String possibleImport = checkAndSlice(candidateClassNode.getProperty("id").toString());
				if(possibleImport!=null)
				{
					importList.add(possibleImport);
				}
			}
			printtypes.put(startPosition, candidateClassNode);
			printTypesMap.put(node.getException().getName().toString(), startPosition);
		}
		variableTypeMap.put(node.getException().getName().toString(), temporaryMap);
		return true;
	}

	public void endVisit(SuperConstructorInvocation treeNode)
	{	
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		int startPosition = treeNode.getStartPosition();
		String treeNodeString = treeNode.toString();
		HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(superclassname, candidateClassNodesCache);
		candidateClassNodes = getNewCeList(candidateClassNodes);
		for(Node candidateClassNode : candidateClassNodes)
		{
			Collection<Node>candidateMethodElements = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
			for(Node candidateMethodElement : candidateMethodElements)
			{
				String candidateMethodExactName = (String)candidateMethodElement.getProperty("exactName");
				if(candidateMethodExactName.equals("<init>"))
				{
					if(matchParams(candidateMethodElement, treeNode.arguments())==true)
					{
						printmethods.put(startPosition,candidateMethodElement);
						Node parentNode = model.getMethodContainer(candidateMethodElement);
						if(candidateMethodElements.size() < tolerance)
						{
							String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
						printtypes.put(startPosition, parentNode);
						Node methodReturnNode = model.getMethodReturn(candidateMethodElement);
						if(methodReturnNode != null)
						{
							candidateAccumulator.put(scopeArray, methodReturnNode);
						}
					}
				}	
			}
		}
		methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
	}

	public void endVisit(SuperMethodInvocation treeNode)
	{
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		int startPosition = treeNode.getStartPosition();
		String treeNodeString = treeNode.toString();
		HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		String treeNodeName = treeNode.getName().toString();
		ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(superclassname, candidateClassNodesCache);
		candidateClassNodes = getNewCeList(candidateClassNodes);
		ArrayList <Node> clist= new ArrayList<Node>();

		printMethodsMap.put(treeNode.toString(),startPosition);

		for(Node candidateClassNode : candidateClassNodes)
		{
			Collection<Node> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
			for(Node candidateMethodNode : candidateMethodNodes)
			{
				String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
				if(candidateMethodExactName.equals(treeNodeName))
				{
					if(matchParams(candidateMethodNode, treeNode.arguments())==true)
					{
						Node fcname=model.getMethodContainer(candidateMethodNode);
						if(fcname!=null)
							clist.add(fcname);
						printmethods.put(startPosition, candidateMethodNode);

						Node methodReturnNode = model.getMethodReturn(candidateMethodNode);
						if(methodReturnNode != null)
						{
							candidateAccumulator.put(scopeArray, methodReturnNode);
						}
					}
				}
			}
			methodReturnTypesMap.put(treeNodeString, candidateAccumulator);

			if(clist.isEmpty()==false)
			{
				printtypes.replaceValues(treeNode.getStartPosition(), clist);
			}
		}
	}

	public boolean visit(final ClassInstanceCreation node)
	{
		ASTNode anon=node.getAnonymousClassDeclaration();
		if(anon!=null)
		{
			anon.accept(new ASTVisitor(){
				public void endVisit(MethodDeclaration md)
				{
					int startPosition = md.getStartPosition();
					ArrayList <Node> candidateClassNodes = model.getCandidateClassNodes(node.getType().toString(), candidateClassNodesCache);
					candidateClassNodes = getNewCeList(candidateClassNodes);
					for(Node candidateClassNode : candidateClassNodes)
					{
						Collection<Node>candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
						for(Node candidateMethodNode : candidateMethodNodes)
						{
							String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
							if(candidateMethodExactName.equals(md.getName().toString()))
							{
								if(matchParams(candidateMethodNode, md.parameters())==true)
								{
									printmethods.put(startPosition,candidateMethodNode);
									Node parentNode = model.getMethodContainer(candidateMethodNode);
									if(candidateMethodNodes.size() < tolerance)
									{
										String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
										if(possibleImport!=null)
										{
											importList.add(possibleImport);
										}
									}
									printtypes.put(startPosition, parentNode);
								}
							}
						}
					}
				}
			});
		}
		else
		{
			int startType = node.getType().getStartPosition();
			ArrayList<Node> candidateClassNodes = model.getCandidateClassNodes(node.getType().toString(), candidateClassNodesCache);
			candidateClassNodes = getNewCeList(candidateClassNodes);
			for(Node candidateClassNode : candidateClassNodes)
			{
				Collection<Node> candidateMethodNodes = model.getMethodNodes(candidateClassNode, methodNodesInClassNode);
				for(Node candidateMethodNode : candidateMethodNodes)
				{
					String candidateMethodExactName = (String)candidateMethodNode.getProperty("exactName");
					if(candidateMethodExactName.equals("<init>"))
					{
						if(matchParams(candidateMethodNode, node.arguments())==true)
						{
							printmethods.put(startType, candidateMethodNode);
						}
					}
				}
			}
		}
		return true;
	}

	public void endVisit(ClassInstanceCreation treeNode)
	{	
		ArrayList<Node> candidateClassNodes=model.getCandidateClassNodes(treeNode.getType().toString(), candidateClassNodesCache);
		candidateClassNodes = getNewCeList(candidateClassNodes);
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		String treeNodeString = treeNode.toString();
		HashMultimap<ArrayList<Integer>, Node> candidateAccumulator = null;
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			candidateAccumulator = methodReturnTypesMap.get(treeNodeString);
		}
		else
		{
			candidateAccumulator = HashMultimap.create();
		}
		for(Node candidateClassNode : candidateClassNodes)
		{
			printTypesMap.put(treeNode.toString(), treeNode.getType().getStartPosition());
			candidateAccumulator.put(scopeArray, candidateClassNode);
		}
		methodReturnTypesMap.put(treeNodeString, candidateAccumulator);
		
	}

	public boolean visit(CastExpression node)
	{
		ArrayList <Node> candidateClassNodes = model.getCandidateClassNodes(node.getType().toString(), candidateClassNodesCache);
		candidateClassNodes = getNewCeList(candidateClassNodes);

		HashMultimap<ArrayList<Integer>, Node> temp1= null;
		HashMultimap<ArrayList<Integer>, Node> temp2= null;

		ArrayList<Integer> scopeArray = getScopeArray(node);
		if(variableTypeMap.containsKey(node.toString()))
		{
			temp1 = variableTypeMap.get(node.toString());
		}
		else
		{
			temp1 = HashMultimap.create();
		}
		if(variableTypeMap.containsKey("("+node.toString()+")"))
		{
			temp2 = variableTypeMap.get("("+node.toString()+")");
		}
		else
		{
			temp2 = HashMultimap.create();
		}
		for(Node candidateClassNode : candidateClassNodes)
		{
			if(candidateClassNode!=null)
			{
				temp1.put(scopeArray, candidateClassNode);
				if(candidateClassNodes.size() < tolerance)
				{
					String possibleImport = checkAndSlice(candidateClassNode.getProperty("id").toString());
					if(possibleImport!=null)
					{
						importList.add(possibleImport);
					}
				}
				printtypes.put(node.getType().getStartPosition(), candidateClassNode);
				temp2.put(scopeArray, candidateClassNode);
			}
		}
		variableTypeMap.put(node.toString(), temp1);
		variableTypeMap.put("("+node.toString()+")", temp2);
		return true;
	}

	public void endVisit(Assignment node)
	{
		String lhs,rhs;
		lhs=node.getLeftHandSide().toString();
		rhs=node.getRightHandSide().toString();

		if(methodReturnTypesMap.containsKey(rhs))
		{
			if(!variableTypeMap.containsKey(lhs))
			{
				methodReturnTypesMap.put(lhs, methodReturnTypesMap.get(rhs));
				methodReturnTypesMap.put(lhs, methodReturnTypesMap.get(rhs));

			}
			else
			{	
				int flag=0;
				Set<Node> temp = new HashSet<Node>();
				HashMultimap<ArrayList<Integer>, Node> celist_temp = variableTypeMap.get(lhs);
				ArrayList<Integer> scopeArray = getNodeSet(celist_temp, getScopeArray(node));
				if(scopeArray!=null)
				{
					Set<Node> localTypes = celist_temp.get(scopeArray);
					for(Node ce : localTypes)
					{
						if(methodReturnTypesMap.get(rhs).values().contains(ce))
						{
							flag=1;
							temp.add(ce);
						}
					}
				}
				if(flag==1)
				{
					variableTypeMap.get(lhs).replaceValues(scopeArray,temp);
				}

			}
		}
	}

	public boolean visit(ImportDeclaration node)
	{

		String importStatement = node.getName().getFullyQualifiedName();
		if(importStatement.endsWith(".*"))
		{
			importStatement= importStatement.substring(0, importStatement.length()-2);
		}
		importList.add(importStatement);
		return true;
	}

	public JSONObject printJson()
	{
		checkForNull();

		//Add to primitive and uncomment to remove unwanted elements
		//String[] primitive = {"int","float","char","long","boolean","String","byte[]","String[]","int[]","float[]","char[]","long[]","byte"};
		String[] primitive={};
		JSONObject main_json=new JSONObject();

		//Collections.sort(printtypes, printtypes.keySet());
		for(Integer key:printtypes.keySet())
		{
			int flag=0;
			String cname=null;
			List<String> namelist = new ArrayList<String>();
			for(Node type_name:printtypes.get(key))
			{
				int isprimitive=0;
				for(String primitive_type : primitive)
				{
					if(((String)type_name.getProperty("id")).equals(primitive_type)==true)
					{
						isprimitive=1;
						break;
					}
				}
				if(isprimitive==0)
				{
					String nameOfClass = (String)type_name.getProperty("id");
					namelist.add("\""+nameOfClass+"\"");
					if(flag==0)
					{
						cname=(String) type_name.getProperty("exactName");
						flag=1;
					}
				}

			}
			if(namelist.isEmpty()==false)
			{
				JSONObject json = new JSONObject();
				json.accumulate("line_number",Integer.toString(cu.getLineNumber(key)-cutype));
				json.accumulate("precision", Integer.toString(namelist.size()));
				json.accumulate("name",cname);
				json.accumulate("elements",namelist);
				json.accumulate("type","api_type");
				json.accumulate("character", Integer.toString(key));
				main_json.accumulate("api_elements", json);
			}

		}
		for(Integer key:printmethods.keySet())
		{
			List<String> namelist = new ArrayList<String>();
			String mname=null;
			for(Node method_name:printmethods.get(key))
			{
				String nameOfMethod = (String)method_name.getProperty("id");
				namelist.add("\""+nameOfMethod+"\"");
				mname=(String) method_name.getProperty("exactName");
			}
			if(namelist.isEmpty()==false)
			{
				JSONObject json = new JSONObject();
				json.accumulate("line_number",Integer.toString(cu.getLineNumber(key)-cutype));
				json.accumulate("precision", Integer.toString(namelist.size()));
				json.accumulate("name",mname);
				json.accumulate("elements",namelist);
				json.accumulate("type","api_method");
				json.accumulate("character", Integer.toString(key));
				main_json.accumulate("api_elements", json);
			}
			//System.out.println(main_json.toString());
		}
		if(main_json.isNull("api_elements"))
		{
			//System.out.println("{\"api_elements\": [{ \"precision\": \"\",\"name\": \"\",\"line_number\": \"\",\"type\": \"\",\"elements\": \"\"}]}" ); 
			//return("{\"api_elements\": [{ \"precision\": \"\",\"name\": \"\",\"line_number\": \"\",\"type\": \"\",\"elements\": \"\"}]}" );
			return null;
		}
		else
		{
			//System.out.println(main_json.toString(3));
			return(main_json);
		}
		//printFields();
	}

	public void checkForNull()
	{
		for(Integer key : printtypes.keySet())
			for(Node type_name:printtypes.get(key))
			{
				if(type_name==null)
					printtypes.remove(key, type_name);
			}
		for(Integer key : printmethods.keySet())
			for(Node method_name:printmethods.get(key))
			{
				if(method_name==null)
					printmethods.remove(key, method_name);
			}
	}


}