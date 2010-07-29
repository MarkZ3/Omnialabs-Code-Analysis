package net.omnialabs.cdt.codan.checkers;

import org.eclipse.cdt.codan.core.cxx.model.AbstractIndexAstChecker;
import org.eclipse.cdt.codan.core.model.IProblem;
import org.eclipse.cdt.codan.core.model.IProblemWorkingCopy;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTReturnStatement;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;

public class ReturnStyleChecker extends AbstractIndexAstChecker {
	public final String ERR_ID = "org.eclipse.cdt.codan.internal.checkers.returnstyle"; //$NON-NLS-1$
	public final String PREF_REGEX = "regex"; //$NON-NLS-1$
	public final String DEFAULT_REGEX = "return\\s*(\\((\\s*.+\\s*)*\\))?\\s*;"; //$NON-NLS-1$
	
	@Override
	public boolean runInEditor() {
		return true;
	}

	@Override
	public void processAst(IASTTranslationUnit ast) {
		ast.accept(new ASTVisitor() {
			{
				shouldVisitStatements = true;
			}

			@Override
			public int visit(IASTStatement statement) {
				if (statement instanceof IASTReturnStatement) {
					
					String rawSig = statement.getRawSignature();
					
					// It could be a macro, we don't handle this 
					if(rawSig.contains("return")) //$NON-NLS-1$
					{
						IProblem problem = getProblemById(ERR_ID, getFile());
						String regex = (String) getPreference(problem, PREF_REGEX);
						if(!(new String(rawSig).matches(regex))) {
							reportProblem(ERR_ID, statement, (Object)statement.getRawSignature());
						}
					}
				}
				return PROCESS_CONTINUE;
			}
		});
	}
	
	@Override
	public void initPreferences(IProblemWorkingCopy problem) {
		super.initPreferences(problem);
		addPreference(problem, PREF_REGEX, Messages.ReturnStyleChecker_Regex, DEFAULT_REGEX);
	}

}
