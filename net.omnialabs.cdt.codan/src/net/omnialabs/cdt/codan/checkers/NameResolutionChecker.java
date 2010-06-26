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
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTFieldReference;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNamedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IProblemBinding;

public class NameResolutionChecker extends AbstractIndexAstChecker {

	static final String ERR_ID = "org.eclipse.cdt.codan.internal.checkers.NameResolutionChecker"; //$NON-NLS-1$
	
	public void processAst(IASTTranslationUnit ast) {
		try {
			ast.accept(new ASTVisitor() {
				{
					shouldVisitNames = true;
				}

				@Override
				public int visit(IASTName name) {
					IBinding binding = name.resolveBinding();
					if (binding instanceof IProblemBinding) {
						int id = ((IProblemBinding) binding).getID();
						if(id == IProblemBinding.SEMANTIC_INVALID_REDECLARATION) {
							reportProblem(ERR_ID, name, "Invalid redeclaration of " + name.getRawSignature());
							return PROCESS_CONTINUE;
						}
						IASTNode parentNode = name.getParent();
						if(parentNode instanceof IASTIdExpression) {
							IASTIdExpression expression = (IASTIdExpression)parentNode;
							IASTNode parentParentNode = expression.getParent();
							if(parentParentNode instanceof IASTFunctionCallExpression && !expression.getPropertyInParent().getName().contains("ARGUMENT")) {
								reportProblem(ERR_ID, name, name.getRawSignature()+": function could not be resolved");
							} else {
								reportProblem(ERR_ID, name, name.getRawSignature() + " could not be resolved");
							}
						} else if (parentNode instanceof IASTFieldReference) {
							IASTNode parentParentNode = parentNode.getParent();
							if(parentParentNode instanceof IASTFunctionCallExpression) {
								reportProblem(ERR_ID, name, name.getRawSignature()+": method could not be resolved");
							} else {
								reportProblem(ERR_ID, name, name.getRawSignature()+": field could not be resolved");
							}
						} else if (parentNode instanceof IASTNamedTypeSpecifier) {
							reportProblem(ERR_ID, name, name.getRawSignature() + " type could not be resolved");
						} else {
							reportProblem(ERR_ID, name, name.getRawSignature() + " could not be resolved");
						}
					}
					return PROCESS_CONTINUE;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	public boolean runInEditor() {
		return true;
	}

}
