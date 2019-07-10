import syntaxtree.*;
import visitor.*;
import java.io.*;

class Main {
    public static void main (String [] args){
        if(args.length == 0){
            System.err.println("Usage: java Driver <inputFile1> <inputFile2> ....");
            System.exit(1);
        }
        FileInputStream fis = null;
        for(String fl: args){
            try{
                fis = new FileInputStream(fl);
                MiniJavaParser parser = new MiniJavaParser(fis);
                System.out.println("\n\n*---------------------------------------------------------*");
                System.out.println("-File " + fl + " parsed successfully.\n");

                STableImpl st = new STableImpl();
                VTablesImpl vt = new VTablesImpl();
                Goal root = parser.Goal();

                try{
                    //visitor for filling symbol tables
                    STableVisitor stVisit = new STableVisitor(st);
                    root.accept(stVisit, null);

					try{
						//visitor for type checking
	                	TypeCheckVisitor tcVisit = new TypeCheckVisitor(st);
	                	root.accept(tcVisit, null);
						System.out.println("-Semantic analysis for file " + fl + " was successfull.\n");

						System.out.println("Producing intermediate code (LLVM)...\n");
                    	st.fixOffsets(vt);                 //print offsets and make vtables

                    	/*// for testing only
                    	System.out.println("VTABLE RESULTLS:");
                   	 	vt.print_vtable();

                   	 	System.out.println("SymolTable RESULTLS:");
                    	st.printAll();*/


                    	//visitor for writing LLVM code
                    	LLVMVisitor llVisit = new LLVMVisitor(fl,st,vt);
                    	root.accept(llVisit, null);
					}
					catch (Exception ex) {
                    	System.out.println(ex.getMessage());
                	}
                }
                catch (Exception ex) {
                    System.out.println(ex.getMessage());
                    ex.printStackTrace();
                }
            }
            catch(ParseException ex){
                System.out.println(ex.getMessage());
            }
            catch(FileNotFoundException ex){
                System.out.println(ex.getMessage());
            }
            finally{
                try{
                    if(fis != null) fis.close();
                }
                catch(IOException ex){
                    System.out.println(ex.getMessage());
                }
            }
        }
    }
}
