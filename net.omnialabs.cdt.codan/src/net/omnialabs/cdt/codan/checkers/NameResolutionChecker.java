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
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTQualifiedName;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTTemplateId;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPFunction;

public class NameResolutionChecker extends AbstractIndexAstChecker {

	static final String ERR_ID = "net.omnialabs.cdt.codan.internal.checkers.NameResolutionChecker"; //$NON-NLS-1$
	
	@Override
	public boolean runInEditor() {
		return true;
	}

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
							
							// Don't report multiple problems with qualified names
							if(parentNode instanceof ICPPASTQualifiedName) {
								if(((ICPPASTQualifiedName)parentNode).resolveBinding() instanceof IProblemBinding)
									return PROCESS_CONTINUE;	
							}
							
							IProblemBinding problemBinding = (IProblemBinding) binding;
							int id = problemBinding.getID();
							
							if(id == IProblemBinding.SEMANTIC_INVALID_OVERLOAD) {
								reportProblem(ERR_ID, name, "Invalid overload of " + name.getRawSignature());
								return PROCESS_CONTINUE;
							}
							
							// For some reason, objects with bad field references are flaged with AMBIGUOUS_LOOKUP 
							boolean notAFieldReferenceOwner = parentNode != null && !parentNode.getPropertyInParent().getName().contains("FIELD_OWNER");
							if(id == IProblemBinding.SEMANTIC_AMBIGUOUS_LOOKUP && notAFieldReferenceOwner) {
								String errorString = name.getRawSignature() + " is ambigious. ";
								errorString += getCandidatesString(problemBinding);
								reportProblem(ERR_ID, name, errorString);
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_CIRCULAR_INHERITANCE) {
								reportProblem(ERR_ID, name, "Circular inheritance encountered in " + name.getRawSignature());
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_INVALID_REDECLARATION) {
								reportProblem(ERR_ID, name, "Invalid redeclaration of " + name.getRawSignature());
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_INVALID_REDEFINITION) {
								reportProblem(ERR_ID, name, "Invalid redefinition of " + name.getRawSignature());
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_MEMBER_DECLARATION_NOT_FOUND) {
								reportProblem(ERR_ID, name, "Member declaration not found.");
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_LABEL_STATEMENT_NOT_FOUND) {
								reportProblem(ERR_ID, name, name.getRawSignature() + " : label not found.");
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_INVALID_TEMPLATE_ARGUMENTS) {
								// We use the templateName since we don't want the whole
								// argument list to be underligned. That way we can see which argument is invalid.
								IASTNode templateName = getTemplateName(name);
								reportProblem(ERR_ID, templateName, "Invalid template arguments.");
								return PROCESS_CONTINUE;
							}
							
							// From this point, we'll deal only with NAME_NOT_FOUND problems. 
							// If it's something else continue because we don't want to give bad messages
							if(id != IProblemBinding.SEMANTIC_NAME_NOT_FOUND) {
								return PROCESS_CONTINUE;
							}
							
							if(isFunctionCall(parentNode)) {
								handleFunctionProblem(name, problemBinding);
							} else if (parentNode instanceof IASTFieldReference) {
								handleMemberProblem(name, parentNode, problemBinding);
							} else if (parentNode instanceof IASTNamedTypeSpecifier) {
								reportProblem(ERR_ID, name, name.getRawSignature() + " type could not be resolved");
							} 
							// Probably a variable
							else {
								handleVariableProblem(name);
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
	
	private void handleFunctionProblem(IASTName name, IProblemBinding problemBinding)
			throws DOMException {
		if(problemBinding.getCandidateBindings().length == 0) {
			reportProblem(ERR_ID, name.getLastName(), name.getRawSignature()+": function could not be resolved");
		} else {
			String problemString = "Invalid arguments. ";
			problemString += getCandidatesString(problemBinding);
			reportProblem(ERR_ID, name.getLastName(), problemString);
		}
	}

	private void handleMemberProblem(IASTName name, IASTNode parentNode,
			IProblemBinding problemBinding) throws DOMException {
		IASTNode parentParentNode = parentNode.getParent();
		if(parentParentNode instanceof IASTFunctionCallExpression) {
			if(problemBinding.getCandidateBindings().length == 0) {
				reportProblem(ERR_ID, name.getLastName(), name.getRawSignature()+": method could not be resolved");
			} else {
				String problemString = "Invalid arguments. " + getCandidatesString(problemBinding);
				reportProblem(ERR_ID, name.getLastName(), problemString);
			}
		} else {
			reportProblem(ERR_ID, name.getLastName(), name.getRawSignature()+": field could not be resolved");
		}
	}

	private void handleVariableProblem(IASTName name) {
		reportProblem(ERR_ID, name, name.getRawSignature() + " could not be resolved");
	}
	
	private boolean isFunctionCall(IASTNode parentNode) {
		if(parentNode instanceof IASTIdExpression) {
			IASTIdExpression expression = (IASTIdExpression)parentNode;
			IASTNode parentParentNode = expression.getParent();
			if(parentParentNode instanceof IASTFunctionCallExpression && expression.getPropertyInParent().getName().contains("FUNCTION_NAME")) {
				return true;
			}
		}
		return false;
	}

	protected IASTNode getTemplateName(IASTName name) {
		IASTName nameToGetTempate = name.getLastName();
		if(nameToGetTempate instanceof ICPPASTTemplateId) {
			return ((ICPPASTTemplateId)nameToGetTempate).getTemplateName();
		}
		
		return nameToGetTempate;
	}

	private String getCandidatesString(IProblemBinding problemBinding) throws DOMException {
		String candidatesString = "Candidates are :\n";
		String lastSignature = "";
		for(IBinding candidateBinding : problemBinding.getCandidateBindings()) {
			if(candidateBinding instanceof ICPPFunction) {
				ICPPFunction functionBinding = (ICPPFunction)candidateBinding;
				String signature = getFunctionSignature(functionBinding);
				if(!signature.equals(lastSignature)) {
					candidatesString += signature + "\n";
					lastSignature = signature;
				}
			} else if(candidateBinding instanceof ICPPClassType) {
				ICPPClassType classType = (ICPPClassType)candidateBinding;
				for(ICPPFunction constructor : classType.getConstructors()) {
					String signature = getFunctionSignature(constructor);
					if(!signature.equals(lastSignature)) {
						candidatesString += signature + "\n";
						lastSignature = signature;
					}
				}
			}
			
		} 
		return candidatesString;
	}
	
	private String getFunctionSignature(ICPPFunction functionBinding) throws DOMException {
		IFunctionType functionType = functionBinding.getType();
		String returnTypeString = ASTTypeUtil.getType(functionBinding.getType().getReturnType())+" ";
		String functionName = functionBinding.getName();
		String parameterTypeString = ASTTypeUtil.getParameterTypeString(functionType);
		return returnTypeString + functionName + parameterTypeString;
	}

}
