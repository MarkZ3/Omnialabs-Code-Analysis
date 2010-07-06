package net.omnialabs.cdt.codan.checkers;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "net.omnialabs.cdt.codan.checkers.messages"; //$NON-NLS-1$
	
	public static String NameResolutionChecker_OverloadProblem;
	public static String NameResolutionChecker_AmbiguousProblem;
	public static String NameResolutionChecker_CircularReferenceProblem;
	public static String NameResolutionChecker_RedeclarationProblem;
	public static String NameResolutionChecker_RedefinitionProblem;
	public static String NameResolutionChecker_MemberDeclarationNotFoundProblem;
	public static String NameResolutionChecker_LabelStatementNotFoundProblem;
	public static String NameResolutionChecker_InvalidTemplateArgumentsProblem;
	public static String NameResolutionChecker_TypeResolutionProblem;
	public static String NameResolutionChecker_FunctionResolutionProblem;
	public static String NameResolutionChecker_InvalidArguments;
	public static String NameResolutionChecker_MethodResolutionProblem;
	public static String NameResolutionChecker_FieldResolutionProblem;
	public static String NameResolutionChecker_VariableResolutionProblem;
	public static String NameResolutionChecker_Candidates;
	
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}
	
	private Messages() {
	}
}
