package net.omnialabs.cdt.codan.checkers;

import org.eclipse.cdt.codan.core.cxx.model.AbstractIndexAstChecker;
import org.eclipse.cdt.codan.core.model.IProblem;
import org.eclipse.cdt.codan.core.model.IProblemWorkingCopy;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPField;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMember;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMethod;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.index.IIndexName;
import org.eclipse.core.runtime.CoreException;

public class MemberUsageChecker extends AbstractIndexAstChecker {

	final static String ID_METHOD = "org.eclipse.cdt.codan.internal.checkers.MethodUsageProblem"; //$NON-NLS-1$
	final static String ID_FIELD = "org.eclipse.cdt.codan.internal.checkers.FieldUsageProblem"; //$NON-NLS-1$
	final private String PREF_PRIVATE = "private"; //$NON-NLS-1$
	final private String PREF_PROTECTED = "protected"; //$NON-NLS-1$
	final private String PREF_PUBLIC = "public"; //$NON-NLS-1$
	
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
					if(binding instanceof ICPPMember) {
						ICPPMember member = (ICPPMember)binding;
						
						// Don't check destructors
						if(member instanceof ICPPMethod) {
							ICPPMethod method = (ICPPMethod) member;
							if(method.isDestructor())
								return super.visit(name);
						}
						
						try {
							String problemId = member instanceof ICPPField ? ID_FIELD : ID_METHOD;
							int visibility = member.getVisibility();
							if(visibility == ICPPMember.v_private && checkPref(problemId, PREF_PRIVATE) ||
									visibility == ICPPMember.v_protected && checkPref(problemId, PREF_PROTECTED) ||
									visibility == ICPPMember.v_public && checkPref(problemId, PREF_PUBLIC)) {
								if(hasReferences(index, binding)) {
									ICPPClassType classType = member.getClassOwner();
									if(member instanceof ICPPMethod) {
										reportProblem(ID_METHOD, name, member.toString(), classType.toString());	
									} else {
										reportProblem(ID_FIELD, name, member.toString(), classType.toString());
									}
								}
							}
						} catch (DOMException e1) {
						}

					}
					return super.visit(name);
				}
			});
		} catch (Exception e) {
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
	
	boolean checkPref(final String problem, final String param) {
		final IProblem pt = getProblemById(problem, getFile());
		return (Boolean) getPreference(pt, param);
	}

	@Override
	public void initPreferences(IProblemWorkingCopy problem) {
		super.initPreferences(problem);
		addPreference(problem, PREF_PRIVATE,
				Messages.MemberUsageChecker_PrefPrivate, Boolean.TRUE);
		addPreference(problem, PREF_PROTECTED,
				Messages.MemberUsageChecker_PrefProtected, Boolean.FALSE);
		addPreference(problem, PREF_PUBLIC,
				Messages.MemberUsageChecker_PrefPublic, Boolean.FALSE);
	}
	
	

}
