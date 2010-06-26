package net.omnialabs.cdt.codan.checkers;

import java.util.ArrayList;

import org.eclipse.cdt.codan.core.cxx.model.AbstractIndexAstChecker;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTCompositeTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTFieldReference;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTQualifiedName;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTVisibilityLabel;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPBase;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPField;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMember;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMethod;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ClassTypeHelper;
import org.eclipse.cdt.internal.core.pdom.indexer.IndexerASTVisitor;
import org.omg.CORBA.FREE_MEM;

public class ClassMemberAccessChecker extends AbstractIndexAstChecker {

	static final String ERR_ID = "org.eclipse.cdt.codan.internal.checkers.ClassMemberAccessChecker"; //$NON-NLS-1$

	public void processAst(IASTTranslationUnit ast) {
		try {
			ast.accept(new ASTVisitor() {
				{
					shouldVisitNames = true;
				}

				@Override
				public int visit(IASTName name) {
					if(!isRelevantAstName(name)) {
						return PROCESS_CONTINUE;
					}
						
					ICPPMember member = (ICPPMember) name.getBinding();
					int visibility;
					try {
						visibility = member.getVisibility();
					} catch (DOMException e) {
						return PROCESS_CONTINUE;
					}

					if (visibility == ICPPASTVisibilityLabel.v_private) {
						if (!isValidPrivateInFriendClass(name, member))
							reportProblem(ERR_ID, name, name.getRawSignature() + ": member is private");
					} else if (visibility == ICPPASTVisibilityLabel.v_protected) {
						if (!isValidPrivateInFriendClass(name, member) && !isValidProtected(name, member))
							reportProblem(ERR_ID, name, name.getRawSignature() + ": member is protected");
					}
					
					return PROCESS_CONTINUE;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	protected boolean isRelevantAstName(IASTName name) {
		IBinding binding = name.getBinding();
		
		// It should be at least a member
		if(!(binding instanceof ICPPMember)) {
			return false;
		}
		
		// Visibility is irrelevant for declarations
		if(name.getParent() instanceof IASTDeclarator) {
			return false;
		}
		
		IASTNode parent = name.getParent();
		if (binding instanceof ICPPMethod && !(parent instanceof ICPPASTFieldReference)) {
			return false;
		}
		
		/*if(name instanceof ICPPASTQualifiedName) {
			ICPPASTQualifiedName funcDefQualifiedName = (ICPPASTQualifiedName)name;
			IASTName[] namesInQualified = funcDefQualifiedName.getNames();
			for(IASTName nameInQualified : namesInQualified) {
				if(nameInQualified.getBinding() instanceof ICPPField) {
					return false;
				}
			}
		}*/
		
		if(parent instanceof ICPPASTQualifiedName) {
			if(parent.getParent() instanceof IASTDeclarator) {
				return false;
			}
		}
		
		return true;
	}

	protected boolean isValidPrivateInFriendClass(IASTName name, ICPPMember member) {
		boolean result = false;
		try {
			
			// .h stuff
			
			ArrayList<IASTCompositeTypeSpecifier> compositeType = new ArrayList<IASTCompositeTypeSpecifier>();
			IASTNode node = name.getParent();
			// Use visitor instead?
			while(node != null) {
				if(node instanceof IASTCompositeTypeSpecifier) {
					compositeType.add((IASTCompositeTypeSpecifier) node);
				}
				node = node.getParent();
			}
			
			// member call in class method
			
			ICPPClassType classOwner = member.getClassOwner();
			for(IASTCompositeTypeSpecifier aCompositeNode : compositeType) {
				IBinding binding = aCompositeNode.getName().getBinding();
				if(binding instanceof ICPPClassType) {
					ICPPClassType classType = (ICPPClassType)binding;
					if(classType.getName().equals(classOwner.getName()))
						return true;
				}
			}
			
			// .cpp stuff
			
			ICPPASTFunctionDefinition functionDefinition = null;
			IASTNode functionDefSearch = name.getParent();
			while(functionDefSearch != null) {
				if(functionDefSearch instanceof ICPPASTFunctionDefinition) {
					functionDefinition = (ICPPASTFunctionDefinition)functionDefSearch;
					break;
				}
				functionDefSearch = functionDefSearch.getParent();
			}
			
			// If friend class or nested class
			
			if(functionDefinition != null) {
				IASTName functionDefName = functionDefinition.getDeclarator().getName();
				if(functionDefName instanceof ICPPASTQualifiedName) {
					ICPPASTQualifiedName funcDefQualifiedName = (ICPPASTQualifiedName)functionDefName;
					IASTName[] namesInQualified = funcDefQualifiedName.getNames();
					for(IASTName nameInQualified : namesInQualified) {
						IBinding binding = nameInQualified.getBinding();
						if(binding instanceof ICPPClassType) {
							ICPPClassType classType = (ICPPClassType)binding;
							if(classType.getName().equals(classOwner.getName())) {
								return true;
							}
							if(isFriend(classOwner, classType)) {
								return true;
							}
						}
					}
				}
			}
			
			// If friend method
			if(functionDefinition != null) {
				IASTName functionDefName = functionDefinition.getDeclarator().getName();
				if(functionDefName instanceof ICPPASTQualifiedName) {
					ICPPASTQualifiedName funcDefQualifiedName = (ICPPASTQualifiedName)functionDefName;
					IASTName[] namesInQualified = funcDefQualifiedName.getNames();
					for(IASTName nameInQualified : namesInQualified) {
						IBinding binding = nameInQualified.getBinding();
						if(binding instanceof ICPPMethod) {
							ICPPMethod method = (ICPPMethod)binding;
							if(ClassTypeHelper.isFriend(method, classOwner))
								return true;
						}
					}
				}
			}
			
		} catch (DOMException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	private boolean isValidProtected(IASTName name, ICPPMember member) {
		boolean result = false;
		try {
			
			ArrayList<IASTCompositeTypeSpecifier> compositeType = new ArrayList<IASTCompositeTypeSpecifier>();
			IASTNode node = name.getParent();
			// Use visitor instead?
			while(node != null) {
				if(node instanceof IASTCompositeTypeSpecifier) {
					compositeType.add((IASTCompositeTypeSpecifier) node);
				}
				node = node.getParent();
			}
			
			// member call in class method
			
			ICPPClassType classOwner = member.getClassOwner();
			for(IASTCompositeTypeSpecifier aCompositeNode : compositeType) {
				IBinding binding = aCompositeNode.getName().getBinding();
				if(binding instanceof ICPPClassType) {
					ICPPClassType classType = (ICPPClassType)binding;
					return isInBase(classType, classOwner);
				}
			}
			
			// .cpp stuff
			
			ICPPASTFunctionDefinition functionDefinition = null;
			IASTNode functionDefSearch = name.getParent();
			while (functionDefSearch != null) {
				if (functionDefSearch instanceof ICPPASTFunctionDefinition) {
					functionDefinition = (ICPPASTFunctionDefinition) functionDefSearch;
					break;
				}
				functionDefSearch = functionDefSearch.getParent();
			}

			if (functionDefinition != null) {
				IASTName functionDefName = functionDefinition.getDeclarator().getName();
				if (functionDefName instanceof ICPPASTQualifiedName) {
					ICPPASTQualifiedName funcDefQualifiedName = (ICPPASTQualifiedName) functionDefName;
					IASTName[] namesInQualified = funcDefQualifiedName.getNames();
					for (IASTName nameInQualified : namesInQualified) {
						IBinding binding = nameInQualified.getBinding();
						if (binding instanceof ICPPClassType) {
							ICPPClassType classType = (ICPPClassType) binding;
							return isInBase(classType, classOwner);
						}
					}
				}
			}
		} catch (DOMException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	boolean isInBase(ICPPClassType baseType, ICPPClassType classOwner) throws DOMException {
		for(ICPPBase base : baseType.getBases()) {
			IBinding baseClassBinding = base.getBaseClass();
			if(baseClassBinding instanceof ICPPClassType) {
				ICPPClassType baseClassType = (ICPPClassType)baseClassBinding;
				if (baseClassType.getName().equals(classOwner.getName()) && 
						(base.getVisibility() == ICPPASTVisibilityLabel.v_protected || 
								base.getVisibility() == ICPPASTVisibilityLabel.v_public )) {
					return true;
				}
				else {
					return isInBase(baseClassType, classOwner);
				}
			}
		}
		return false;
	}

	private boolean isFriend(ICPPClassType classOwner, ICPPClassType otherClass) throws DOMException {
		IBinding[] friendBindings = classOwner.getFriends();
		for(IBinding friendBinding : friendBindings) {
			if(friendBinding instanceof ICPPClassType) {
				ICPPClassType classType = (ICPPClassType)friendBinding;
				if(classType.getName().equals(otherClass.getName()))
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean runInEditor() {
		return true;
	}
}
