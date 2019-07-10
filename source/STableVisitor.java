import syntaxtree.*;
import visitor.GJDepthFirst;

public class STableVisitor extends GJDepthFirst<String, String>{

    String workingClass;            //indicate the current class
    String workingMethod;           //indicate the current method, if we are in method!
    Boolean inMethodFlag;           //flag to verify in we are in method. Used for variables.
    STableImpl st;

    STableVisitor(STableImpl st){
        this.st = st;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    public String visit(MainClass n, String argu) throws Exception {
       String _ret=null;

       //add class
       String mainName = n.f1.accept(this, argu);
       if(st.classExists(mainName))
           throw new Exception("Error, class *" + mainName + "* has already been declared previously.");
       st.setMainClass(mainName);                               //keep main class name
       st.addClass(mainName);                                   //add to class map
       this.workingClass = mainName;                            //set current
       ClassSt wc = st.getClass(this.workingClass);             //get actual object to work with

       //add main method to class
       Signature s = new Signature("void");
       wc.addMethod("main", s);
       this.workingMethod = "main";
       MethodSt wm = wc.getMethod(this.workingMethod);

       //add parameters to method
       String argName = n.f11.accept(this,argu);
       wm.updateSignature("String[]");
       wm.addVariable(argName, "String[]");

       //continue inside body - only var decleration!
       this.inMethodFlag = true;
       n.f14.accept(this,argu);
       return _ret;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    public String visit(ClassDeclaration n, String argu) throws Exception {
       String _ret=null;

       //get class, add to classMap
       String className = n.f1.accept(this, argu);
       if(st.classExists(className))
           throw new Exception("Error, class *" + className + "* has already been declared previously.");
       st.addClass(className);
       this.workingClass = className;
       ClassSt wc = st.getClass(this.workingClass);

       this.inMethodFlag = false;
       n.f3.accept(this, argu);         //visit fields
       n.f4.accept(this, argu);         //visit methods
       return _ret;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    public String visit(ClassExtendsDeclaration n, String argu) throws Exception {
       String _ret=null;

       //get class
       String className = n.f1.accept(this, argu);
       if(st.classExists(className))
           throw new Exception("Error, class *" + className + "* has already been declared previously.");
       String parentName = n.f3.accept(this, argu);
       if(!st.classExists(parentName))
           throw new Exception("Error, class *" + className + "* has not been declared previously.");
       st.addInheritance(className, parentName);        //add to Map scopes this relationship
       st.addClass(className);
       this.workingClass = className;
       ClassSt wc = st.getClass(this.workingClass);

       this.inMethodFlag = false;
       n.f5.accept(this, argu);
       n.f6.accept(this, argu);
       return _ret;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    public String visit(MethodDeclaration n, String argu) throws Exception {
       String _ret=null;
       String methodType = n.f1.accept(this, argu);
       String methodName = n.f2.accept(this, argu);
       ClassSt wc = st.getClass(this.workingClass);
       if(wc.methodExists(methodName))
           throw new Exception("Error, method *" + methodName + "* has already been declared in class *" + this.workingClass + "*.");
       Signature s = new Signature(methodType);
       wc.addMethod(methodName, s);
       this.workingMethod = methodName;
       this.inMethodFlag = true;
       n.f4.accept(this, argu);         //visit parameters

       //in case of working class extending another, check if current method overrides, and if so correctly.
       String parentClass = st.getSuperClass(this.workingClass);
       String currentClass = this.workingClass;
       while(parentClass != null){                                  //if current class extends
           ClassSt pc = st.getClass(parentClass);                   //get parent class
           if(pc.methodExists(this.workingMethod)){                 //if current methods "overrides"
               MethodSt wm = wc.getMethod(this.workingMethod);
               String cs = wm.getSignature().getSigStr();

               MethodSt pm = pc.getMethod(this.workingMethod);
               String ps = pm.getSignature().getSigStr();
               if(!ps.equals(cs)){
                   System.out.println("- Subclass " + currentClass + ": Method " + this.workingMethod + ", signature " + cs);
                   System.out.println("- Superclass " + parentClass + ": Method " + this.workingMethod + ", signature " + ps);
                   throw new Exception("Error, method *" + methodName + "* tries to overload method of class *" + parentClass + "*.");
               }
           }
           //continue loop
           parentClass = st.getSuperClass(parentClass);
       }

       n.f7.accept(this, argu);         //visit variables
       return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    public String visit(FormalParameter n, String argu) throws Exception {
       String _ret=null;
       String parType = n.f0.accept(this, argu);
       String parName = n.f1.accept(this, argu);
       ClassSt wc = st.getClass(this.workingClass);
       MethodSt wm = wc.getMethod(this.workingMethod);
       if(wm.parExists(parName))
           throw new Exception("Error, parameter *" + parName + "* already declared in method *" + this.workingMethod + "*.");
       wm.updateSignature(parType);
       wm.addParameter(parName, parType);
       return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, String argu) throws Exception {
       String _ret=null;
       String varType = n.f0.accept(this, argu);
       String varName = n.f1.accept(this, argu);
       ClassSt wc = st.getClass(this.workingClass);
       if(this.inMethodFlag){
           //we are in method
           MethodSt wm = wc.getMethod(this.workingMethod);
           if(wm.parExists(varName) || wm.varExists(varName))
               throw new Exception("Error, variable *" + varName + "* already declared in method *" + this.workingMethod + "*.");
           wm.addVariable(varName, varType);
       }
       else{
           //we are not in method, variable = class field
           if(wc.fieldExists(varName))
               throw new Exception("Error, field *" + varName + "* already declared in class *" + this.workingClass + "*.");
           wc.addField(varName, varType);
       }

       return _ret;
    }


    //fix identifier + types !!
    /**
     * f0 -> <IDENTIFIER>
     */
    public String visit(Identifier n, String argu) throws Exception {
        return n.f0.toString();
    }

    /**
     * f0 -> "int"
     */
    public String visit(IntegerType n, String argu) throws Exception {
       return "int";
    }

    /**
     * f0 -> "boolean"
     */
    public String visit(BooleanType n, String argu) throws Exception {
       return "boolean";
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
     public String visit(ArrayType n, String argu) throws Exception {
       return "int[]";
    }
}
