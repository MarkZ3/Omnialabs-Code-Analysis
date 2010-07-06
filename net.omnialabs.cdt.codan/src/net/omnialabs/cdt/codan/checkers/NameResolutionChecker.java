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
package net.omnialabs.cdt.codan.checkers;

import org.eclipse.cdt.codan.core.cxx.model.AbstractIndexAstChecker;
import org.eclipse.cdt.core.dom.ast.ASTTypeUtil;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
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
import org.eclipse.osgi.util.NLS;

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
								reportProblem(ERR_ID, name, NLS.bind(Messages.NameResolutionChecker_OverloadProblem, name.getRawSignature()));
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_AMBIGUOUS_LOOKUP) {
								String errorString = NLS.bind(Messages.NameResolutionChecker_AmbiguousProblem, name.getRawSignature());
								errorString += getCandidatesString(problemBinding);
								reportProblem(ERR_ID, name, errorString);
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_CIRCULAR_INHERITANCE) {
								if(parentNode instanceof IASTFieldReference) {
									IASTExpression ownerExpression = ((IASTFieldReference)parentNode).getFieldOwner();
									String typeString = ASTTypeUtil.getType(ownerExpression.getExpressionType());
									reportProblem(ERR_ID, ownerExpression, NLS.bind(Messages.NameResolutionChecker_CircularReferenceProblem, typeString));
								} else {
									reportProblem(ERR_ID, name, NLS.bind(Messages.NameResolutionChecker_CircularReferenceProblem, name.getRawSignature()));	
								}
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_INVALID_REDECLARATION) {
								reportProblem(ERR_ID, name, NLS.bind(Messages.NameResolutionChecker_RedeclarationProblem, name.getRawSignature()));
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_INVALID_REDEFINITION) {
								reportProblem(ERR_ID, name, NLS.bind(Messages.NameResolutionChecker_RedefinitionProblem, name.getRawSignature()));
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_MEMBER_DECLARATION_NOT_FOUND) {
								reportProblem(ERR_ID, name, Messages.NameResolutionChecker_MemberDeclarationNotFoundProblem);
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_LABEL_STATEMENT_NOT_FOUND) {
								reportProblem(ERR_ID, name, NLS.bind(Messages.NameResolutionChecker_LabelStatementNotFoundProblem, name.getRawSignature()));
								return PROCESS_CONTINUE;
							}
							
							if(id == IProblemBinding.SEMANTIC_INVALID_TEMPLATE_ARGUMENTS) {
								// We use the templateName since we don't want the whole
								// argument list to be underligned. That way we can see which argument is invalid.
								IASTNode templateName = getTemplateName(name);
								reportProblem(ERR_ID, templateName, Messages.NameResolutionChecker_InvalidTemplateArgumentsProblem);
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
								reportProblem(ERR_ID, name, NLS.bind(Messages.NameResolutionChecker_TypeResolutionProblem, name.getRawSignature()));
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
			reportProblem(ERR_ID, name.getLastName(), NLS.bind(Messages.NameResolutionChecker_FunctionResolutionProblem, name.getRawSignature()));
		} else {
			String problemString = Messages.NameResolutionChecker_InvalidArguments;
			problemString += getCandidatesString(problemBinding);
			reportProblem(ERR_ID, name.getLastName(), problemString);
		}
	}

	private void handleMemberProblem(IASTName name, IASTNode parentNode,
			IProblemBinding problemBinding) throws DOMException {
		IASTNode parentParentNode = parentNode.getParent();
		if(parentParentNode instanceof IASTFunctionCallExpression) {
			if(problemBinding.getCandidateBindings().length == 0) {
				reportProblem(ERR_ID, name.getLastName(), NLS.bind(Messages.NameResolutionChecker_MethodResolutionProblem, name.getRawSignature()));
			} else {
				String problemString = Messages.NameResolutionChecker_InvalidArguments + getCandidatesString(problemBinding);
				reportProblem(ERR_ID, name.getLastName(), problemString);
			}
		} else {
			reportProblem(ERR_ID, name.getLastName(), NLS.bind(Messages.NameResolutionChecker_FieldResolutionProblem, name.getRawSignature()));
		}
	}

	private void handleVariableProblem(IASTName name) {
		reportProblem(ERR_ID, name, NLS.bind(Messages.NameResolutionChecker_VariableResolutionProblem, name.getRawSignature()));
	}
	
	private boolean isFunctionCall(IASTNode parentNode) {
		if(parentNode instanceof IASTIdExpression) {
			IASTIdExpression expression = (IASTIdExpression)parentNode;
			IASTNode parentParentNode = expression.getParent();
			if(parentParentNode instanceof IASTFunctionCallExpression && 
					expression.getPropertyInParent().getName().equals(IASTFunctionCallExpression.FUNCTION_NAME.getName())) {
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

	/**
	 * Returns a string of the candidates for the binding
	 * 
	 * @param problemBinding
	 * @return A string of the candidates, one per line
	 * @throws DOMException
	 */
	private String getCandidatesString(IProblemBinding problemBinding) throws DOMException {
		String candidatesString = Messages.NameResolutionChecker_Candidates + "\n"; //$NON-NLS-1$
		String lastSignature = ""; //$NON-NLS-1$
		for(IBinding candidateBinding : problemBinding.getCandidateBindings()) {
			if(candidateBinding instanceof ICPPFunction) {
				ICPPFunction functionBinding = (ICPPFunction)candidateBinding;
				String signature = getFunctionSignature(functionBinding);
				if(!signature.equals(lastSignature)) {
					candidatesString += signature + "\n"; //$NON-NLS-1$
					lastSignature = signature;
				}
			} else if(candidateBinding instanceof ICPPClassType) {
				ICPPClassType classType = (ICPPClassType)candidateBinding;
				for(ICPPFunction constructor : classType.getConstructors()) {
					String signature = getFunctionSignature(constructor);
					if(!signature.equals(lastSignature)) {
						candidatesString += signature + "\n"; //$NON-NLS-1$
						lastSignature = signature;
					}
				}
			}
			
		} 
		return candidatesString;
	}
	
	/**
	 * Returns a string of the function signature : returntype + function + parameters
	 * 
	 * @param functionBinding The function to get the signature
	 * @return A string of the function signature
	 * @throws DOMException
	 */
	private String getFunctionSignature(ICPPFunction functionBinding) throws DOMException {
		IFunctionType functionType = functionBinding.getType();
		String returnTypeString = ASTTypeUtil.getType(functionBinding.getType().getReturnType()) + " "; //$NON-NLS-1$
		String functionName = functionBinding.getName();
		String parameterTypeString = ASTTypeUtil.getParameterTypeString(functionType);
		return returnTypeString + functionName + parameterTypeString;
	}

}
