package net.omnialabs.cdt.codan.checkers;
/*******************************************************************************
 * Copyright (c) 2010 Marc-Andre Laperle and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Marc-Andre Laperle - Initial API and implementation
 *******************************************************************************/


import org.eclipse.cdt.codan.core.cxx.model.AbstractIndexAstChecker;
import org.eclipse.cdt.core.dom.ast.ASTTypeUtil;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTFieldReference;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNamedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IFunctionType;
import org.eclipse.cdt.core.dom.ast.IProblemBinding;
import org.eclipse.cdt.core.dom.ast.cpp.CPPASTVisitor;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTQualifiedName;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPFunction;

public class NameResolutionChecker extends AbstractIndexAstChecker {

	static final String ERR_ID = "net.omnialabs.cdt.codan.internal.checkers.NameResolutionChecker"; //$NON-NLS-1$
	
	public void processAst(IASTTranslationUnit ast) {
		try {
			ast.accept(new ASTVisitor() {
				{
					shouldVisitNames = true;
				}

				@Override
				public int visit(IASTName name) {
					try {
						IBinding binding = name.resolveBinding();
						if (binding instanceof IProblemBinding) {
							IASTNode parentNode = name.getParent();
							if(parentNode instanceof ICPPASTQualifiedName) {
								if(((ICPPASTQualifiedName)parentNode).resolveBinding() instanceof IProblemBinding)
									return PROCESS_CONTINUE;	
							}
							
							IProblemBinding problemBinding = (IProblemBinding) binding;
							int id = problemBinding.getID();
							if(id == IProblemBinding.SEMANTIC_INVALID_REDECLARATION) {
								reportProblem(ERR_ID, name, "Invalid redeclaration of " + name.getRawSignature());
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_INVALID_REDEFINITION) {
								reportProblem(ERR_ID, name, "Invalid redefinition of " + name.getRawSignature());
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_AMBIGUOUS_LOOKUP) {
								//TODO: display candidates
								reportProblem(ERR_ID, name, name.getRawSignature() + " is ambigious");
								return PROCESS_CONTINUE;
							}
							
							//TODO: extract function handling
							if(parentNode instanceof IASTIdExpression) {
								IASTIdExpression expression = (IASTIdExpression)parentNode;
								IASTNode parentParentNode = expression.getParent();
								if(parentParentNode instanceof IASTFunctionCallExpression && !expression.getPropertyInParent().getName().contains("ARGUMENT")) {
									if(problemBinding.getCandidateBindings().length == 0) {
										reportProblem(ERR_ID, name, name.getRawSignature()+": function could not be resolved");
									} else if(functionContainsArgumentProblem((IASTFunctionCallExpression)parentParentNode)) {
										String problemString;
										problemString = getInvalidArgumentsErrorString(problemBinding);
										reportProblem(ERR_ID, name, problemString);
									}
								} else {
									reportProblem(ERR_ID, name, name.getRawSignature() + " could not be resolved");
								}
							} else if (parentNode instanceof IASTFieldReference) {
								handleMember(name, parentNode, problemBinding);
							} else if (parentNode instanceof IASTNamedTypeSpecifier) {
								reportProblem(ERR_ID, name, name.getRawSignature() + " type could not be resolved");
							} else {
								reportProblem(ERR_ID, name, name.getRawSignature() + " could not be resolved");
							}
						}
					} catch (DOMException e) {
						e.printStackTrace();
					}
					
					return PROCESS_CONTINUE;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	private boolean functionContainsArgumentProblemResult = false;

	private boolean functionContainsArgumentProblem(IASTFunctionCallExpression functionCall) {
		functionContainsArgumentProblemResult = false;
		functionCall.accept(new CPPASTVisitor() {
			{
				shouldVisitNames = true;
			}

			@Override
			public int visit(IASTName name) {
				if(name.resolveBinding() instanceof IProblemBinding) {
					functionContainsArgumentProblemResult = true;
					return PROCESS_ABORT;	
				}
					
				return PROCESS_CONTINUE;
			}
			
		});
		return functionContainsArgumentProblemResult;
	}

	@Override
	public boolean runInEditor() {
		return true;
	}

	private String getInvalidArgumentsErrorString(IProblemBinding problemBinding) throws DOMException {
		String problemString = "Invalid arguments. Candidates are :\n";
		String lastSignature = "";
		for(IBinding candidateBinding : problemBinding.getCandidateBindings()) {
			if(candidateBinding instanceof ICPPFunction) {
				try {
					ICPPFunction functionBinding = (ICPPFunction)candidateBinding;
					String signature = getFunctionSignature(functionBinding);
					if(!signature.equals(lastSignature)) {
						problemString += signature + "\n";
						lastSignature = signature;
					}
				} catch (DOMException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if(candidateBinding instanceof ICPPClassType) {
				ICPPClassType classType = (ICPPClassType)candidateBinding;
				for(ICPPFunction constructor : classType.getConstructors()) {
					String signature = getFunctionSignature(constructor);
					if(!signature.equals(lastSignature)) {
						problemString += signature + "\n";
						lastSignature = signature;
					}
				}
			}
			
		} 
		return problemString;
	}
	
	private String getFunctionSignature(ICPPFunction functionBinding) throws DOMException {
		IFunctionType functionType = functionBinding.getType();
		String returnTypeString = ASTTypeUtil.getType(functionBinding.getType().getReturnType())+" ";
		String functionName = functionBinding.getName();
		String parameterTypeString = ASTTypeUtil.getParameterTypeString(functionType);
		return returnTypeString + functionName + parameterTypeString;
	}

	private void handleMember(IASTName name, IASTNode parentNode,
			IProblemBinding problemBinding) throws DOMException {
		IASTNode parentParentNode = parentNode.getParent();
		if(parentParentNode instanceof IASTFunctionCallExpression) {
			if(problemBinding.getCandidateBindings().length == 0) {
				reportProblem(ERR_ID, name, name.getRawSignature()+": method could not be resolved");
			} else if(functionContainsArgumentProblem((IASTFunctionCallExpression)parentParentNode)) {
				String problemString = getInvalidArgumentsErrorString(problemBinding);
				reportProblem(ERR_ID, name, problemString);
			}
		} else {
			reportProblem(ERR_ID, name, name.getRawSignature()+": field could not be resolved");
		}
	}

}
