package net.omnialabs.cdt.codan.checkers;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "net.omnialabs.cdt.codan.checkers.messages"; //$NON-NLS-1$

	public static String MemberUsageChecker_PrefPrivate;
	public static String MemberUsageChecker_PrefProtected;
	public static String MemberUsageChecker_PrefPublic;
	public static String MemberUsageChecker_PrefPrivateContructors;
	public static String MemberUsageChecker_PrefPrivateOverloadedOperators;
	
	public static String ReturnStyleChecker_Regex;
	
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}
	
	private Messages() {
	}
}
