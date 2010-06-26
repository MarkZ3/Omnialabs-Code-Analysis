package net.omnialabs.cdt.codan.checkers;

import org.eclipse.cdt.codan.core.cxx.model.AbstractIndexAstChecker;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTReturnStatement;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;

public class ReturnParentheses extends AbstractIndexAstChecker {
	public final String ERR_ID = "codantest.returnparentheses"; //$NON-NLS-1$
	
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
					if(!(new String(rawSig).matches("return(\\(.+\\))?;"))) {
						reportProblem(ERR_ID, statement, (Object)statement.getRawSignature());
					}
				}
				return PROCESS_CONTINUE;
			}
			
		});
		
	}

}
