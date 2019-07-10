import java.util.*;
import java.lang.*;

public class ClassSt{
    private LinkedHashMap<String, MethodSt> methods;            //K=name, V=MethodSt object
    private LinkedHashMap<String, String> fields;               //K=name, V=type

    ClassSt(){
        methods = new LinkedHashMap<String, MethodSt>();
        fields = new LinkedHashMap<String, String>();
    }

    public void addMethod(String methodName, Signature sig){
        this.methods.put(methodName, new MethodSt(sig));
    }

    public MethodSt getMethod(String methodName){
        return this.methods.get(methodName);
    }

    public Boolean methodExists(String methodName){
        return this.methods.containsKey(methodName);
    }

    public int getMethodCount(){
        return this.methods.size();
    }

    public ArrayList<String> getMethodNames(){
        return new ArrayList<String>(this.methods.keySet());
    }

    public void addField(String name, String type){
        this.fields.put(name, type);
    }

    public String getFieldType(String fieldName){
        return this.fields.get(fieldName);
    }

    public Boolean fieldExists(String fieldName){
        return this.fields.containsKey(fieldName);
    }

    public int getFieldCount(){
        return this.fields.size();
    }

    public void printClass(){
        //print fields
        for(String key: this.fields.keySet()){
            System.out.println("\tField: " + key + ", type: " + this.fields.get(key));
        }

        //print methods
        for(String key: this.methods.keySet()){
            System.out.print("\tMethod: " + key + ", ");
            this.methods.get(key).printMethod();
        }
    }

    public OffsetBlock Offsets(String name, int f, int m, ClassSt parent, String parentName, VTablesImpl vt){
        // method that for each class, makes new vtable_cl object with correct names and offsets
        // and updates the given vtable
        int findex = f, mindex = m;
        vtable_cl vcl = new vtable_cl(name);

        //fields
        for(String key: this.fields.keySet()){
            vcl.vtable_cl_addField(key, findex);         //add in vtable_cl

            String type = this.fields.get(key);         //move findex position
            switch(type){
                case "int":
                    findex += 4; break;
                case "boolean":
                    findex += 1; break;
                default:
                    findex += 8; break;
            }
        }

        if(parent != null){
            //if it has parent, first write all his method with their offsets, and then continue from mindex position
            vtable_cl pcl = vt.vtable_getClass(parentName);
            for(String key: pcl.vtable_cl_getMethodNames())
                vcl.vtable_cl_addMethod(key, pcl.vtable_cl_getMethodOffset(key));
            for(String key: this.methods.keySet()){
                if(!pcl.vtable_cl_MethodExists(key)){
                    vcl.vtable_cl_addMethod(key, mindex);        //add in vtable_cl
                    mindex += 8;
                }
            }
        }
        else{
            //wasn't given a parent, continues writing normally
            for(String key: this.methods.keySet()){
                vcl.vtable_cl_addMethod(key, mindex);        //add in vtable_cl
                mindex += 8;
            }
        }
        //create offset block, will hold last offsets for children, if any.
        OffsetBlock ob = new OffsetBlock(name,findex,mindex);
        vt.vtable_addClass(name, vcl);
        return ob;
    }
}
