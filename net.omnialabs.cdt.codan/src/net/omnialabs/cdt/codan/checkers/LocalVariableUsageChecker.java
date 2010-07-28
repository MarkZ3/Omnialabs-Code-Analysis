package net.omnialabs.cdt.codan.checkers;

import org.eclipse.cdt.codan.core.cxx.model.AbstractIndexAstChecker;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPParameter;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPVariable;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.index.IIndexName;
import org.eclipse.core.runtime.CoreException;

public class LocalVariableUsageChecker extends AbstractIndexAstChecker {

	final static String ID = "org.eclipse.cdt.codan.internal.checkers.LocalVariableUsageProblem"; //$NON-NLS-1$
	
	private int blah;
	
	@Override
	public void processAst(IASTTranslationUnit ast) {
		final IIndex index = ast.getIndex();
		try {
			index.acquireReadLock();
			ast.accept(new ASTVisitor() {
				{
					shouldVisitNames = true;
				}

				@Override
				public int visit(IASTName name) {
					IBinding binding = name.resolveBinding();
					if(binding instanceof ICPPVariable && !(binding instanceof ICPPParameter)) {
						if(hasReferences(index, binding)) {
						    reportProblem(ID, name, name.getRawSignature());	
						}

					}
					return super.visit(name);
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			index.releaseReadLock();	
		}

	}

	private boolean hasReferences(final IIndex index, IBinding binding) {
		boolean hasReferences = false;
		try {
			IIndexName[] names = index.findReferences(binding);
			hasReferences = names.length == 0;
		} catch (CoreException e) {
			hasReferences = false;
		}
		return hasReferences;
	}

}
