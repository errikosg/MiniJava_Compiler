import syntaxtree.*;
import visitor.GJDepthFirst;
import java.io.*;
import java.util.*;

public class LLVMVisitor extends GJDepthFirst<String, String>{

    String workingClass;
    String workingMethod;           //same as STableVisitor
    String filename;                //the .ll file where everytihng is written
    STableImpl st;                  //symbol table;
    VTablesImpl vt;                 //v-tables
    Stack<CustomPair<String,String>> reg_stack;  //stack that holds each register type -- used in MessageSend and AssignmentStatement for register type aquisition
    ListStack stk;                  //ListStack object - it is a stack of lists, used for storing recursively the parameter types in MessageSend (nested calls!!!)
    Counters cnt;                   //Counters object, manages all label and register names

    boolean retRegister;            //flag indicating that return value is a register and not primitive type/identifier...
    boolean loadValue;              //flag for specifying that value must be loaded --> (see primary expression)


    LLVMVisitor(String fl, STableImpl st, VTablesImpl vt){
        //get tables + extract correct name
        this.st = st;
        this.vt = vt;
        String pat = "(.*)(/)(\\w+)(.java)";
        this.filename = fl.replaceAll(pat, "$3");

        this.retRegister = false;
        this.loadValue = false;
        this.reg_stack = new Stack<>();
        this.stk = new ListStack();
        this.cnt = new Counters();

        //make directory "out-llvm" if not exits
        File d = new File("out-llvm");
        if(!d.exists())
            d.mkdir();

        try{
            FileWriter fileWriter = new FileWriter("out-llvm/" + this.filename + ".ll");
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.print("");
        }
        catch(Exception e){
            System.out.println(e.getMessage());
        }
    }

    //my emit method
    private void emit(String text){
        try{
            FileWriter fwrite = new FileWriter("out-llvm/" + this.filename + ".ll", true);
            PrintWriter pwrite = new PrintWriter(fwrite);
            pwrite.print(text);
            pwrite.close();
        }
        catch(IOException e){
            System.out.println(e.getMessage());
        }
    }

    private void emit_vtables(){
        //write at the start of the .ll file all the vtables
        ArrayList<String> classes = st.getClassNames();
        for(String className: classes){
            this.emit("@." + className + "_vtable = global ");
            if(st.isMainClass(className))
                this.emit("[0 x i8*] []\n");
            else{
                ClassSt cl = st.getClass(className);
                vtable_cl vcl = vt.vtable_getClass(className);
                int mcount = st.getClassTotalMethodCount(className);            //get true method count -> # of own + # of inherited
                ArrayList<String> methods = vcl.vtable_cl_getMethodNames();
                this.emit("[" + mcount + " x i8*] [");

                String text = "";
                for(String methodName: methods){
                    //method is inherited by child but not overriden -> search father to write correctly
                    if(!cl.methodExists(methodName)){
                        String parentName = st.getSuperClass(className);
                        while(parentName != null){
                            ClassSt parent = st.getClass(parentName);
                            if(parent.methodExists(methodName)){
                                MethodSt mt = parent.getMethod(methodName);
                                text += "i8* bitcast (" + mt.getSignature().toLLVM() + " @" + parentName + "." + methodName + " to i8*), ";
                                break;
                            }
                            parentName = st.getSuperClass(parentName);
                        }
                    }
                    else{
                        //method is own or overriden
                        MethodSt mt = cl.getMethod(methodName);
                        text += "i8* bitcast (" + mt.getSignature().toLLVM() + " @" + className + "." + methodName + " to i8*), ";
                    }
                }
                //clear output, deleting last coma
                String pat = "(.*)(, $)";
                text = text.replaceAll(pat, "$1");
                this.emit(text + "]\n");
            }
        }
    }


    private void emit_extra(){
        //write below vtables the "helper methods" that were given (printf, calloc...)
        String text = "\n\ndeclare i8* @calloc(i32, i32)\ndeclare i32 @printf(i8*, ...)\ndeclare void @exit(i32)\n\n";
        text += "@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n\n";
        text += "define void @print_int(i32 %i) {\n\t%_str = bitcast [4 x i8]* @_cint to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n\tret void\n}\n\n";
        text += "define void @throw_oob() {\n\t%_str = bitcast [15 x i8]* @_cOOB to i8*\n\tcall i32 (i8*, ...) @printf(i8* %_str)\n\tcall void @exit(i32 1)\n\tret void\n}\n\n";
        this.emit(text);
    }

    private void resetCounters(){
        this.reg_stack.clear();
        this.cnt.resetAll();
    }

    private String getLLType(String type){
        //transform type to llvm.
        switch(type){
            case "int":
                return "i32";
            case "int[]":
                return "i32*";
            case "boolean":
                return "i1";
            default:
                return "i8*";
        }
    }

    private boolean isInt(String expr){
        return (expr.matches("-?[0-9]+"));
    }


    /*------------------------------------------------------------------------*/
    //Visitor pattern below

    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     */
    public String visit(Goal n, String argu) throws Exception {
        this.emit_vtables();
        this.emit_extra();
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return null;
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
        this.workingClass = n.f1.accept(this, argu);                //store current class name!!
        n.f4.accept(this, argu);                                    //need to visit only method declaration
        return null;
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
        this.workingClass = n.f1.accept(this, argu);
        n.f6.accept(this, argu);
        return null;
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
        this.workingClass = n.f1.accept(this, argu);
        this.workingMethod = "main";                    //set working !
        this.emit("define i32 @main() {\n");
        n.f14.accept(this, argu);
        n.f15.accept(this, argu);
        this.emit("\n\tret i32 0\n}\n\n");
        this.resetCounters();                                //reset all label/register/... counters
        return null;
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
        String methodType = n.f1.accept(this, argu);
        this.workingMethod = n.f2.accept(this, argu);
        this.emit("define " + this.getLLType(methodType) + " @" + this.workingClass + "." + this.workingMethod); //have to complete signature
        ClassSt cl = st.getClass(this.workingClass);
        MethodSt mt = cl.getMethod(this.workingMethod);         //get actual object to work with

        //get parameters
        ArrayList<String> param_names = mt.getParamaterNames();
        String text = "(i8* %this, ";
        for(String name: param_names){
            String type = mt.getParType(name);
            text += this.getLLType(type) + " %." + name + ", ";
        }
        String pat = "(.*)(, $)";
        text = text.replaceAll(pat, "$1");
        this.emit(text + ") {\n");

        //initialize parameters - must use "alloca" + "store"...
        for(String name: param_names){
            String type = mt.getParType(name);
            String llvm_type = this.getLLType(type);
            this.emit("\t%" + name + " = alloca " + llvm_type + "\n");
            this.emit("\tstore " + llvm_type + " %." + name + ", " + llvm_type + "* %" + name + "\n");
        }
        n.f7.accept(this, argu);
        n.f8.accept(this, argu);

        this.loadValue = true;
        String expr = n.f10.accept(this, argu);
        this.emit("\tret " + this.getLLType(methodType) + " " + expr + "\n}\n\n");
        this.resetCounters();
        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, String argu) throws Exception {
        String varType = n.f0.accept(this,argu);
        String varName = n.f1.accept(this,argu);
        this.emit("\t%" + varName + " = alloca " + this.getLLType(varType) + "\n");
        return null;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public String visit(AssignmentStatement n, String argu) throws Exception {
        String ret_value;
        this.retRegister = false;
        String id = n.f0.accept(this, argu);

        //check if identifier is class field or method parameter/variable
        ClassSt cl = st.getClass(this.workingClass);
        String id_type = st.idLookup(id, this.workingClass, this.workingMethod);
        String llvm_type = this.getLLType(id_type);

        //check if field of current or parent
        String className = this.workingClass;
        if(!cl.fieldExists(id)){
            String parentName = st.getSuperClass(className);
            while(parentName != null){
                ClassSt parent = st.getClass(parentName);
                if(parent.fieldExists(id)){
                    className = parentName;
                    cl = parent;
                    break;                  //fix class name and object
                }
                else
                    parentName = st.getSuperClass(parentName);
            }
        }

        vtable_cl vcl = vt.vtable_getClass(className);
        if(cl.fieldExists(id)){                     //class field
            String register1 = this.cnt.makeRegister();
            String register2 = this.cnt.makeRegister();
            int offset = vcl.vtable_cl_getFieldOffset(id) + 8;
            this.emit("\t" + register1 + " = getelementptr i8, i8* %this, i32 " + offset + "\n");
            this.emit("\t" + register2 + " = bitcast i8* " + register1 + " to " + llvm_type + "*\n");
            ret_value = register2;
        }
        else
            ret_value = "%" + id;

        this.loadValue = true;
        String expr = n.f2.accept(this, argu);

        //register types here??

        this.emit("\tstore " + llvm_type + " " + expr + ", " + llvm_type + "* " + ret_value + "\n");
        return null;
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
        //as usual, get class - check if id class field or parameter (...)
        String id = n.f0.accept(this, argu);
        String id_type = st.idLookup(id, this.workingClass, this.workingMethod);
        ClassSt cl = st.getClass(this.workingClass);
        String register = this.cnt.makeRegister();

        //check if field of current or parent
        String className = this.workingClass;
        if(!cl.fieldExists(id)){
            String parentName = st.getSuperClass(className);
            while(parentName != null){
                ClassSt parent = st.getClass(parentName);
                if(parent.fieldExists(id)){
                    className = parentName;
                    cl = parent;
                    break;                  //fix class name and object
                }
                else
                    parentName = st.getSuperClass(parentName);          //go up the ladder
            }
        }

        vtable_cl vcl = vt.vtable_getClass(className);
        if(cl.fieldExists(id)){                                         //class field
            int offset = vcl.vtable_cl_getFieldOffset(id) + 8;          //find field offset
            String register1 = this.cnt.makeRegister();
            this.emit("\t" + register1 + " = getelementptr i8, i8* %this, i32 " + offset + "\n");
            String register2 = this.cnt.makeRegister();
            this.emit("\t" + register2 + " = bitcast i8* " + register1 + " to i32**\n");
            String register3 = this.cnt.makeRegister();
            this.emit("\t" + register3 + " = load i32*, i32** " + register2 + "\n");
            register = register3;
        }
        else{                                                           //method variable
            String register1 = this.cnt.makeRegister();
            this.emit("\t" + register1 + " = load i32*, i32** %" + id + "\n");
            register = register1;
        }

        String expr1 = n.f2.accept(this, argu);
        //make 3 bound labels as shown in examples (check for out-of-bounds error)
        String label1 = this.cnt.makeBoundsLabel();
        String label2 = this.cnt.makeBoundsLabel();
        String label3 = this.cnt.makeBoundsLabel();
        String register2 = this.cnt.makeRegister();
        String register3 = this.cnt.makeRegister();     //compare register

        this.emit("\t" + register2 + " = load i32, i32* " + register + "\n");
        this.emit("\t" + register3 + " = icmp ult i32 " + expr1 + ", " + register2 + "\n");        //make oob check ...
        this.emit("\tbr i1 " + register3 + ", label %" + label1 + ", label %" + label2 + "\n");

        this.emit("\n" + label1 + ":\n");
        String expr2 = n.f5.accept(this, argu);
        String register4 = this.cnt.makeRegister();
        String register5 = this.cnt.makeRegister();
        this.emit("\t" + register4 + " = add i32 " + expr1 + ", 1\n");
        this.emit("\t" + register5 + " = getelementptr i32, i32* " + register + ", i32 " + register4 + "\n");
        this.emit("\tstore i32 " + expr2 + ", i32* " + register5 + "\n");
        this.emit("\tbr label %" + label3 + "\n");

        this.emit("\n" + label2 + ":\n");          //error catching
        this.emit("\tcall void  @throw_oob()\n");
        this.emit("\tbr label %" + label3 + "\n");

        this.emit("\n" + label3 + ":\n");
        return register5;
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
        this.loadValue = true;                      //load value first
        String expr = n.f2.accept(this, argu);
        String label1 = this.cnt.makeIfLabel();
        String label2 = this.cnt.makeIfLabel();
        String label3 = this.cnt.makeIfLabel();         //make 3 labels, as shown in examples

        this.emit("\tbr i1 " + expr + ", label %" + label1 + ", label %" + label2 + "\n");
        this.emit("\n" + label1 + ":\n");              // IF
        n.f4.accept(this,argu);                     //visit if expression
        this.emit("\tbr label %" + label3 + "\n");
        this.emit("\n" + label2 + ":\n");              // ELSE
        n.f6.accept(this, argu);                    //visit else expression
        this.emit("\tbr label %" + label3 + "\n");

        this.emit("\n" + label3 + ":\n");
        return null;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, String argu) throws Exception {
        String label1 = this.cnt.makeLoopLabel();
        String label2 = this.cnt.makeLoopLabel();
        String label3 = this.cnt.makeLoopLabel();

        this.emit("\tbr label %" + label1 + "\n");
        this.emit("\n" + label1 + ":\n");
        String expr1 = n.f2.accept(this,argu);
        this.emit("\tbr i1 " + expr1 + ", label %" + label2 + ", label %" + label3 + "\n");      //condition to continue or not loop

        this.emit("\n" + label2 + ":\n");
        String expr2 = n.f4.accept(this,argu);
        this.emit("\tbr label %" + label1 + "\n");
        this.emit("\n" + label3 + ":\n");
        return null;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public String visit(PrintStatement n, String argu) throws Exception {
        this.loadValue = true;              //load value first
        String expr = n.f2.accept(this,argu);
        this.emit("\tcall void (i32) @print_int(i32 " + expr + ")\n");
        return null;
    }


    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    public String visit(AndExpression n, String argu) throws Exception {
        //make 4 labels, as shown in examples!
        String label1 = this.cnt.makeAndLabel();
        String label2 = this.cnt.makeAndLabel();
        String label3 = this.cnt.makeAndLabel();
        String label4 = this.cnt.makeAndLabel();

        this.loadValue = true;                          //must load value stored first
        String expr1 = n.f0.accept(this, argu);
        this.emit("\tbr label %" + label1 + "\n");
        this.emit("\n" + label1 + ":\n");
        this.emit("\tbr i1 " + expr1 + ", label %" + label2 + ", label %" + label4 + "\n");

        this.loadValue = true;
        this.emit("\n" + label2 + ":\n");
        String expr2 = n.f2.accept(this, argu);
        this.emit("\tbr label %" + label3 + "\n");
        this.emit("\n" + label3 + ":\n");
        this.emit("\tbr label %" + label4 + "\n");

        this.emit("\n" + label4 + ":\n");
        //collect results of && using ** phi **
        String phi = this.cnt.makeRegister();
        this.emit("\t" + phi + " = phi i1 [ 0, %" + label1 + " ], [ " + expr2 + ", %" + label3 + " ]\n");      //?
        return phi;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n, String argu) throws Exception {
        //get values (++ load)
        this.loadValue = true;
        String expr1 = n.f0.accept(this, argu);
        this.loadValue = true;
        String expr2 = n.f2.accept(this, argu);

        //emit
        String register = this.cnt.makeRegister();
        this.emit("\t" + register + " = icmp slt i32 " + expr1 + ", " + expr2 + "\n");
        return register;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, String argu) throws Exception {
        this.loadValue = true;
        String expr1 = n.f0.accept(this, argu);
        this.loadValue = true;
        String expr2 = n.f2.accept(this, argu);

        String register = this.cnt.makeRegister();
        this.emit("\t" + register + " = add i32 " + expr1 + ", " + expr2 + "\n");
        return register;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, String argu) throws Exception {
        this.loadValue = true;
        String expr1 = n.f0.accept(this, argu);
        this.loadValue = true;
        String expr2 = n.f2.accept(this, argu);

        String register = this.cnt.makeRegister();
        this.emit("\t" + register + " = sub i32 " + expr1 + ", " + expr2 + "\n");
        return register;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n, String argu) throws Exception {
        this.loadValue = true;
        String expr1 = n.f0.accept(this, argu);
        this.loadValue = true;
        String expr2 = n.f2.accept(this, argu);

        String register = this.cnt.makeRegister();
        this.emit("\t" + register + " = mul i32 " + expr1 + ", " + expr2 + "\n");
        return register;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public String visit(ArrayLookup n, String argu) throws Exception {
        //get 3 out-of-bounds labels, as shown in examples!
        String label1 = this.cnt.makeBoundsLabel();
        String label2 = this.cnt.makeBoundsLabel();
        String label3 = this.cnt.makeBoundsLabel();

        this.loadValue = true;
        String expr1 = n.f0.accept(this, argu);
        String register1 = this.cnt.makeRegister();
        this.emit("\t" + register1 + " = load i32, i32* " + expr1 + "\n");

        this.loadValue = true;
        String expr2 = n.f2.accept(this, argu);
        String compare_register = this.cnt.makeRegister();
        this.emit("\t" + compare_register + " = icmp ult i32 " + expr2 + ", " + register1 + "\n");
        this.emit("\tbr i1 " + compare_register + ", label %" + label1 + ", label %" + label2 + "\n");

        String register2 = this.cnt.makeRegister();
        String register3 = this.cnt.makeRegister();
        String register4 = this.cnt.makeRegister();
        this.emit("\n" + label1 + ":\n");
        this.emit("\t" + register2 + " = add i32 " + expr2 + ", 1\n");
        this.emit("\t" + register3 + " = getelementptr i32, i32* " + expr1 + ", i32 " + register2 + "\n");
        this.emit("\t" + register4 + " = load i32, i32* " + register3 + "\n");
        this.emit("\tbr label %" + label3 + "\n");

        this.emit("\n" + label2 + ":\n");
        this.emit("\tcall void @throw_oob()\n");       //catch out of bound error - call function
        this.emit("\tbr label %" + label3 + "\n");

        this.emit("\n" + label3 + ":\n");
        return register4;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public String visit(ArrayLength n, String argu) throws Exception {
        this.loadValue = true;
        String expr = n.f0.accept(this, argu);

        String register = this.cnt.makeRegister();
        this.emit("\t" + register + " = load i32, i32* " + expr + "\n");
        return register;
    }

    /**
     * f0 -> "!"
     * f1 -> Clause()
     */
    public String visit(NotExpression n, String argu) throws Exception {
        String expr = n.f1.accept(this, argu);
        String register = this.cnt.makeRegister();
        this.emit("\t" + register + " = xor i1 1, " + expr + "\n");
        return register;
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
        String given_reg = n.f0.accept(this, argu);
        String register_type = "";

        //get type of given register!
        if(given_reg == "%this")
            register_type = this.workingClass;
        else if(this.reg_stack.size()==0 || !this.reg_stack.peek().getKey().equals(given_reg) || this.reg_stack.peek().getValue() == null)
            register_type = st.idLookup(given_reg, this.workingClass, this.workingMethod);             //search identifier if info was not given correctly
        else{
            CustomPair<String,String> pair = this.reg_stack.pop();
            register_type = pair.getValue();                             //get register type to aquire class
        }

        //visit method and specify actual class!
        String methodName = n.f2.accept(this, argu);
        String className = register_type;
        ClassSt cl = st.getClass(className);

        if(!cl.methodExists(methodName)){                //in this case i have to check for superclass(es) to find the right offsets - i assume input is semantically correct!
            String parentName = st.getSuperClass(className);
            while(parentName != null){
                ClassSt parent = st.getClass(parentName);
                if(parent.methodExists(methodName)){
                    className = parentName;
                    cl = parent;
                    break;                  //fix class name and object
                }
                else
                    parentName = st.getSuperClass(parentName);
            }
        }

        //get method (using position from vtable_cl) and save to register5
        vtable_cl vcl = vt.vtable_getClass(className);
        MethodSt mt = cl.getMethod(methodName);
        Signature sig = mt.getSignature();

        int methodIndex = vcl.vtable_cl_getMethodIndex(methodName);
        this.emit("\n\t; " + register_type + "." + methodName + " : " + methodIndex + "\n");

        String register1 = this.cnt.makeRegister();
        String register2 = this.cnt.makeRegister();
        this.emit("\t" + register1 + " = bitcast i8* " + given_reg + " to i8***\n");
        this.emit("\t" + register2 + " = load i8**, i8*** " + register1 + "\n");

        String register3 = this.cnt.makeRegister();
        String register4 = this.cnt.makeRegister();
        this.emit("\t" + register3 + " = getelementptr i8*, i8** " + register2 + ", i32 " + methodIndex + "\n");
        this.emit("\t" + register4 + " = load i8*, i8** " + register3 + "\n");

        String register5 = this.cnt.makeRegister();
        String llvm_sig = sig.toLLVM();
        this.emit("\t" + register5 + " = bitcast i8* " + register4 + " to " + llvm_sig + "\n");        //convert method signature to llvm format

        //Visit method parameters - listStack will have pairs of (register,type)
        this.stk.stackNewTop();
        n.f4.accept(this, argu);                            //loadValue flag will be set to true --> values will be loaded by PrimaryExpression
        ArrayList<String> paramsList = this.stk.stackPop();

        int index=0;
        String temp = "";
        for(String s: paramsList){
            String p_type = sig.getParamaterTypes().get(index);
            index += 1;
            temp += ", " + this.getLLType(p_type) + " " + s;
        }

        //write code to call method
        String register6 = this.cnt.makeRegister();
        this.emit("\t" + register6 + " = call " + this.getLLType(mt.getReturnValue()) + " " + register5 + "(i8* " + given_reg + temp + ")\n");

        //save register
        this.reg_stack.push(new CustomPair<String,String>(register6, mt.getReturnValue()));
        return register6;
    }


    /**
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    public String visit(ExpressionList n, String argu) throws Exception {
        this.retRegister = false;
        this.loadValue = true;                  //note - load value!

        String expr = n.f0.accept(this, argu);
        this.stk.stackPush(expr);               //keep in top of stack - just like exercise 2
        n.f1.accept(this,argu);
        return null;
    }

    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    public String visit(ExpressionTerm n, String argu) throws Exception {
        this.loadValue = true;
        String expr = n.f1.accept(this, argu);
        this.stk.stackPush(expr);
        return null;
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
        String expr = n.f0.accept(this, argu);

        if(this.retRegister){                    //if register is returned by child node, return it as it is.
            this.retRegister = false;
            return expr;
        }
        if(this.isInt(expr) || expr.equals("%this"))
            return expr;
        else if(expr.equals("true"))
            return "1";
        else if(expr.equals("false"))
            return "0";
        else{
            //given expression is either a class field or a method variable/parameter
            String expr_type = st.idLookup(expr, this.workingClass, this.workingMethod);
            String llvm_type = this.getLLType(expr_type);
            ClassSt cl = st.getClass(this.workingClass);

            //check if field of current or parent
            String className = this.workingClass;
            if(!cl.fieldExists(expr)){
                String parentName = st.getSuperClass(className);
                while(parentName != null){
                    ClassSt parent = st.getClass(parentName);
                    if(parent.fieldExists(expr)){
                        className = parentName;
                        cl = parent;
                        break;                  //fix class name and object
                    }
                    else
                        parentName = st.getSuperClass(parentName);
                }
            }

            vtable_cl vcl = vt.vtable_getClass(className);              //get corresponding objects from symbol table / vtables
            if(cl.fieldExists(expr)){                                   //if expression is field
                int offs = vcl.vtable_cl_getFieldOffset(expr) + 8;
                String register1 = this.cnt.makeRegister();
                this.emit("\t" + register1 + " = getelementptr i8, i8* %this, i32 " + offs + "\n");
                String register2 = this.cnt.makeRegister();
                this.emit("\t" + register2 + " = bitcast i8* " + register1 + " to " + llvm_type + "*\n");

                if(this.loadValue){                     //load value to register!
                    this.loadValue = false;
                    String register3 = this.cnt.makeRegister();
                    this.emit("\t" + register3 + " = load " + llvm_type + ", " + llvm_type + "* " + register2 + "\n");
                    register2 = register3;
                }

                this.reg_stack.push(new CustomPair<String,String>(register2, expr_type));
                //this.reg_stack.push(expr_type);
                return register2;
            }
            else{                                       //expression is variable/parameter
                String register1 = this.cnt.makeRegister();
                emit("\t" + register1 + " = load " + llvm_type + ", " + llvm_type + "* %" + expr + "\n");

                this.reg_stack.push(new CustomPair<String,String>(register1, expr_type));
                //this.reg_stack.push(expr_type);
                return register1;
            }
        }
    }


    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(ArrayAllocationExpression n, String argu) throws Exception {
        String arrLabel1 = this.cnt.makeArrLabel();
        String arrLabel2 = this.cnt.makeArrLabel();
        String compare_register = this.cnt.makeRegister();

        //we have to use 2 array_alloc labels for error checking -> must check if expression is number < 0
        String ret_register = n.f3.accept(this, argu);
        this.emit("\t" + compare_register + " = icmp slt i32 " + ret_register + ", 0\n");
        this.emit("\tbr i1 " + compare_register + ", label %" + arrLabel1 + ", label %" + arrLabel2 + "\n");
        this.emit(arrLabel1 + ":\n\tcall void @throw_oob()\n\tbr label %" + arrLabel2 + "\n\n");
        this.emit(arrLabel2 + ":\n");
        String register2 = this.cnt.makeRegister();
        this.emit("\t" + register2 + " = add i32 " + ret_register + ", 1\n");
        String register3 = this.cnt.makeRegister();
        this.emit("\t" + register3 + " = call i8* @calloc(i32 4, i32 " + register2 + ")\n");
        String register4 = this.cnt.makeRegister();
        this.emit("\t" + register4 + "  = bitcast i8* " + register3 + " to i32*\n");
        this.emit("\t store i32 " + ret_register + ", i32* " + register4 + "\n");


        this.reg_stack.push(new CustomPair<String,String>(register4, "int[]"));
        //this.reg_stack.push("int[]");
        this.retRegister = true;
        return register4;
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public String visit(AllocationExpression n, String argu) throws Exception {
        String className = n.f1.accept(this, argu);

        //trying to get object size
        vtable_cl vcl = vt.vtable_getClass(className);
        ClassSt cl = st.getClass(className);
        CustomPair<String,Integer> pair = vcl.vtable_cl_getLastField();
        int obj_size = 8;
        if(pair != null){
            obj_size += pair.getValue();
            String parType = cl.getFieldType(pair.getKey());
            switch(parType){
                case "int":
                    obj_size += 4;
                    break;
                case "boolean":
                    obj_size += 1;
                    break;
                default:
                    obj_size += 8;
                    break;
            }
        }
        //calloc obj_size, bitcast to i8***, getelementptr and store value!!
        String register1 = this.cnt.makeRegister();
        this.emit("\t" + register1 + " = call i8* @calloc(i32 1, i32 " + obj_size + ")\n");
        String register2 = this.cnt.makeRegister();
        this.emit("\t" + register2 + " = bitcast i8* " + register1 + " to i8***\n");

        int mcount = st.getClassTotalMethodCount(className);
        //int mcount = cl.getMethodCount();
        String register3 = this.cnt.makeRegister();
        this.emit("\t" + register3 + " = getelementptr [" + mcount + " x i8*], [" + mcount + " x i8*]* @." + className + "_vtable, i32 0, i32 0\n");
        this.emit("\tstore i8** " + register3 + ", i8*** " + register2 + "\n");

        this.reg_stack.push(new CustomPair<String,String>(register1, className));
        //this.reg_stack.push(className);
        this.retRegister = true;
        return register1;
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public String visit(BracketExpression n, String argu) throws Exception {
        String reg = n.f1.accept(this, argu);
        this.retRegister = true;
        return reg;
    }

    /**
     * f0 -> "int"
     */
    public String visit(IntegerType n, String argu) throws Exception {
        return "int";
    }

     /**
     * f0 -> <INTEGER_LITERAL>
     */
    public String visit(IntegerLiteral n, String argu) throws Exception {
        return n.f0.toString();
    }


    /**
     * f0 -> "boolean"
     */
    public String visit(BooleanType n, String argu) throws Exception {
        return "boolean";
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
        return "%this";
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
