import syntaxtree.*;
import visitor.GJDepthFirst;
import java.util.*;

public class TypeCheckVisitor extends GJDepthFirst<String, String>{
    String workingClass;            //indicate the current class
    String workingMethod;           //indicate the current method, if we are in method!
    STableImpl st;                  //symbol table
    boolean allocExpr;              //used so that PrimaryExpression knows if allocation expression was visited.
    ListStack stk;                  //ListStack object - it is a stack of lists, used for storing recursively the parameter types in MessageSend (nested calls!!!)

    TypeCheckVisitor(STableImpl s){
        this.st = s;
        this.allocExpr = false;
        this.stk = new ListStack();
    }

    public boolean isPrimitive(String type){
        return (type=="int" || type=="int[]" || type=="boolean");
    }

    /*--------------------------------------------------------------*/

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
        this.workingClass = n.f1.accept(this, argu);
        this.workingMethod = "main";
        n.f15.accept(this, argu);
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
        this.workingClass = n.f1.accept(this, argu);
        n.f4.accept(this, argu);                     //we need to find expressions and statements, so visit methods
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
        this.workingClass = n.f1.accept(this, argu);
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
        this.workingMethod = n.f2.accept(this, argu);
        ClassSt cl = st.getClass(this.workingClass);
        MethodSt mt = cl.getMethod(this.workingMethod);
        String method_type = mt.getReturnValue();

        //visit statements inside function
        n.f8.accept(this, argu);

        String expr = n.f10.accept(this, argu);
        if(!expr.equals(method_type))
            throw new Exception("Error, invalid return type at method -" + this.workingMethod + "- of class -" + this.workingClass + "-. (-" + expr + "- cannot be converted to -" + method_type + "-)");
        return _ret;
    }

    /*-------------- STATEMENTS ----------------------------------------------*/

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public String visit(AssignmentStatement n, String argu) throws Exception {
        String _ret=null;
        String id = n.f0.accept(this, argu);
        String id_type = st.idLookup(id, this.workingClass, this.workingMethod);
        if(id_type == null)                //means this identifier was never declared
            throw new Exception("Error, can't find symbol: " + id);

        //visit expression, get type and check
        this.allocExpr = false;
        String expr_type = n.f2.accept(this, argu);

        boolean flag = false;
        //if(this.allocExpr){
        if(!isPrimitive(expr_type)){
            //expr_type returned from AllocationExpression, checking correctness
            //this.allocExpr = false;
            if(!expr_type.equals(id_type)){
                //if not of equal type, check inheritance
                String parentClass = st.getSuperClass(expr_type);
                while(parentClass != null){
                    if(parentClass.equals(id_type)){
                        flag=true;
                        break;
                    }
                    parentClass = st.getSuperClass(parentClass);
                }
                if(!flag)          //error if didnt find valid superclass
                    throw new Exception("Error, -" + expr_type + "- cannot be converted to type -" + id_type + "-.");
            }
        }
        else{
            if(!id_type.equals(expr_type))         //error if missmatch occurs in given types
                throw new Exception("Error, assignment of type -" + expr_type + "- to type -" + id_type + "- is not valid");
        }
        return _ret;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    public String visit(ArrayAssignmentStatement n, String argu) throws Exception {
        String _ret=null;
        //check if identifier exists and if = int[]
        String id = n.f0.accept(this, argu);
        String id_type = st.idLookup(id, this.workingClass, this.workingMethod);
        if(id_type == null)
            throw new Exception("Error, can't find symbol: " + id);
        else if(!id_type.equals("int[]"))
            throw new Exception("Error, int array required, but found -" + id_type + "-.");

        //check is expression1 and expression2 are of type int, the only valid options.
        String expr1 = n.f2.accept(this, argu);
        if(!expr1.equals("int"))
            throw new Exception("Error, cannot convert -" + expr1 + "- to int.");
        String expr2 = n.f5.accept(this, argu);
        if(!expr2.equals("int"))
            throw new Exception("Error, cannot convert -" + expr2 + "- to int.");
        return _ret;
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    public String visit(IfStatement n, String argu) throws Exception {
        String _ret=null;
        String expr = n.f2.accept(this, argu);
        if(!expr.equals("boolean"))
            throw new Exception("Error, type -" + expr + "- cannot be converted to boolean. (if-statement requires boolean expression)");
        //just visit rest of statements, type-checking will be done inside
        n.f4.accept(this, argu);
        n.f6.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, String argu) throws Exception {
        String _ret=null;
        String expr = n.f2.accept(this, argu);
        if(!expr.equals("boolean"))
            throw new Exception("Error, type -" + expr + "- cannot be converted to boolean. (while-statement requires boolean expression)");
        n.f4.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public String visit(PrintStatement n, String argu) throws Exception {
        String _ret=null;
        String expr = n.f2.accept(this, argu);
        if(!isPrimitive(expr))                 //only primitive type can be printed
            throw new Exception("Error, cannot print type -" + expr + "-, must be primitive.");
        return _ret;
    }

    /*-------------- EXPRESSIONS ---------------------------------------------*/

    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    public String visit(AndExpression n, String argu) throws Exception {
        String expr1 = n.f0.accept(this, argu);
        if(!expr1.equals("boolean"))
            throw new Exception("Error, bad operand types for operation '&&'. (must be boolean, got -" + expr1 + "-)");
        String expr2 = n.f2.accept(this, argu);
        if(!expr2.equals("boolean"))
            throw new Exception("Error, bad operand types for operation '&&'. (must be boolean, got -" + expr2 + "-)");
        return "boolean";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n, String argu) throws Exception {
        String expr1 = n.f0.accept(this, argu);
        if(!expr1.equals("int"))
            throw new Exception("Error, bad operand types for operation '<'. (must be int, got -" + expr1 + "-)");
        String expr2 = n.f2.accept(this, argu);
        if(!expr2.equals("int"))
            throw new Exception("Error, bad operand types for operation '<'. (must be int, got -" + expr2 + "-)");
        return "boolean";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, String argu) throws Exception {
        String expr1 = n.f0.accept(this, argu);
        if(!expr1.equals("int"))
            throw new Exception("Error, bad operand types for operation '+'. (must be int, got -" + expr1 + "-)");
        String expr2 = n.f2.accept(this, argu);
        if(!expr2.equals("int"))
            throw new Exception("Error, bad operand types for operation '+'. (must be int, got -" + expr2 + "-)");
        return "int";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, String argu) throws Exception {
        String expr1 = n.f0.accept(this, argu);
        if(!expr1.equals("int"))
            throw new Exception("Error, bad operand types for operation '-'. (must be int, got -" + expr1 + "-)");
        String expr2 = n.f2.accept(this, argu);
        if(!expr2.equals("int"))
            throw new Exception("Error, bad operand types for operation '-'. (must be int, got -" + expr2 + "-)");
        return "int";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n, String argu) throws Exception {
        String expr1 = n.f0.accept(this, argu);
        if(!expr1.equals("int"))
            throw new Exception("Error, bad operand types for operation '*'. (must be int, got -" + expr1 + "-)");
        String expr2 = n.f2.accept(this, argu);
        System.out.println("edw: " + expr2);
        if(!expr2.equals("int"))
            throw new Exception("Error, bad operand types for operation '*'. (must be int, got -" + expr2 + "-)");
        return "int";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public String visit(ArrayLookup n, String argu) throws Exception {
        String _ret=null;
        //check 1st primary expression, must be int array
        String type1 = n.f0.accept(this, argu);
        if(!type1.equals("int[]"))
            throw new Exception("Error, int array required but type -" + type1 + "- was found. (can't perform array lookup.)");

        //check second primary expression, must only be an integer
        String type2 = n.f2.accept(this, argu);
        if(!type2.equals("int"))
            throw new Exception("Error, cannot convert -" + type2 + "- to int.");
        return "int";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public String visit(ArrayLength n, String argu) throws Exception {
        String _ret=null;
        String expr = n.f0.accept(this, argu);
        if(!expr.equals("int[]"))
            throw new Exception("Error, type -" + expr + "- cannot be dereferenced (.length can only be applied to -int[]-)");
        return "int";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    public String visit(MessageSend n, String argu) throws Exception {
        String expr = n.f0.accept(this, argu);
        //check if 1st primary expression is a class object
        if(!st.classExists(expr)){
            if(isPrimitive(expr))
                throw new Exception("Error, type -" + expr + "- cannot be dereferenced.");
            else
                throw new Exception("Error, cannot find symbol: " + expr);
        }
        ClassSt cl = st.getClass(expr);

        //get method
        String method = n.f2.accept(this, argu);
        boolean flag = false;                   //flag to check if superclass found
        String correctp = "";                   //will keep name of parent if exists

        if(!cl.methodExists(method)){
            //this class doesn't have this method, check for superclassess
            String parentClass = st.getSuperClass(expr);
            while(parentClass != null){
                ClassSt pc = st.getClass(parentClass);
                if(pc.methodExists(method)){            //if superclass has this particular method, it is valid.
                    flag = true;
                    correctp = parentClass;
                    break;
                }
                parentClass = st.getSuperClass(parentClass);
            }
            if(!flag)
                throw new Exception("Error, class -" + expr + "- does not contain method -" + method + "-.");
        }
        ClassSt pc = flag==true ? st.getClass(correctp) : st.getClass(expr);    //get as current class according to outcome
        MethodSt mt = pc.getMethod(method);

        //accept a string of parameter types
        this.stk.stackNewTop();
        n.f4.accept(this, argu);
        ArrayList<String> paramsList = this.stk.stackPop();

        //now paramsList has all the parameters, can compare
        Signature sig = mt.getSignature();
        ArrayList<String> slist = sig.getParamaterTypes();
        if(slist.size() != paramsList.size())             //if list sizes differ it is invalid
            throw new Exception("Error, parameter list length of method " + method + " differs from given one. Was given size " + paramsList.size() + ", signature is: " + sig.getSigStr());
        int index=0;
        for(String s: paramsList){                         //linear check of every parameter type, must be identical
            if(!s.equals(slist.get(index))){
                flag = false;
                if(!isPrimitive(s)){
                    //consider type of s being a subclass, check superclasses
                    String parentClass = st.getSuperClass(s);
                    while(parentClass != null){
                        if(parentClass.equals(slist.get(index))){       //found it, all valid
                            flag = true;
                            break;
                        }
                        parentClass = st.getSuperClass(parentClass);
                    }
                }
                if(!flag)              //will enter if s is primitive or if was type of class but didnt find valid superclass
                    throw new Exception("Error, parameter type -" + s + "- is not suitable for method: " + method + ". Signature is: " + sig.getSigStr());
            }
            index += 1;
        }
        return sig.getReturnValue();                            //returning method type
   }

   /**
    * f0 -> Expression()
    * f1 -> ExpressionTail()
    */
   public String visit(ExpressionList n, String argu) throws Exception {
        String _ret = null;
        String expr = n.f0.accept(this, argu);
        //this.paramsList.add(expr);
        this.stk.stackPush(expr);
        n.f1.accept(this, argu);
        return _ret;
   }

   /**
    * f0 -> ","
    * f1 -> Expression()
    */
   public String visit(ExpressionTerm n, String argu) throws Exception {
        String _ret = null;
        String expr = n.f1.accept(this, argu);
        //this.paramsList.add(expr);
        this.stk.stackPush(expr);
        return _ret;
   }

    /**
     * f0 -> "!"
     * f1 -> Clause()
     */
    public String visit(NotExpression n, String argu) throws Exception {
        String expr = n.f1.accept(this, argu);
        if(!expr.equals("boolean"))
            throw new Exception("Error, bad operand type -" + expr + "- for unary operator !");
        return "boolean";
    }

    /**
     * f0 -> IntegerLiteral()
     *       | TrueLiteral()
     *       | FalseLiteral()
     *       | Identifier()
     *       | ThisExpression()
     *       | ArrayAllocationExpression()
     *       | AllocationExpression()
     *       | BracketExpression()
     */
    public String visit(PrimaryExpression n, String argu) throws Exception {
        String expr = n.f0.accept(this,argu);
        if(expr == null)
            return null;

        //primitive types
        if(expr.equals("*int_literal*"))
            return "int";
        else if(expr.equals("this"))
            return this.workingClass;
        else if(expr.equals("int[]"))
            return "int[]";
        else if(expr.equals("true") || expr.equals("false"))      //last case for not expression
            return "boolean";
        else if(isPrimitive(expr))
            return expr;
        else if(!this.allocExpr && st.classExists(expr))
            return expr;
        else{
            if(this.allocExpr){
                this.allocExpr = false;
                return expr;
            }
            else{
                //identifier
                String id_type = st.idLookup(expr, this.workingClass, this.workingMethod);
                if(id_type == null)                //means this identifier was never declared
                    throw new Exception("Error, can't find symbol: " + expr);
                return id_type;
            }
        }
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public String visit(IntegerLiteral n, String argu) throws Exception {
        return "*int_literal*";             //something unique
    }

    /**
     * f0 -> "true"
     */
    public String visit(TrueLiteral n, String argu) throws Exception {
        return "true";
    }

    /**
     * f0 -> "false"
     */
    public String visit(FalseLiteral n, String argu) throws Exception {
        return "false";
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public String visit(Identifier n, String argu) throws Exception {
        return n.f0.toString();
    }

    /**
     * f0 -> "this"
     */
    public String visit(ThisExpression n, String argu) throws Exception {
        return "this";
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(ArrayAllocationExpression n, String argu) throws Exception {
        String _ret=null;
        String expr = n.f3.accept(this, argu);
        if(!expr.equals("int")){
            //if it is not an int literal
            throw new Exception("Error, cannot convert -" + expr + "- to int. (only integer array allocation is supported).");
        }
        return "int[]";
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public String visit(AllocationExpression n, String argu) throws Exception {
        String _ret=null;

        String cl = n.f1.accept(this, argu);                 //get class and check for correctness
        if(!st.classExists(cl))
            throw new Exception("Error, can't find symbol: " + cl);
        this.allocExpr = true;
        return cl;
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public String visit(BracketExpression n, String argu) throws Exception {
        String expr = n.f1.accept(this,argu);
        return expr;
    }
}
