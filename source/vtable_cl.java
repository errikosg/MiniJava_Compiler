import java.util.*;
import java.lang.*;

public class vtable_cl{
    private String className;
    private LinkedHashMap<String, Integer> methods;     //method name / offset
    private LinkedHashMap<String, Integer> fields;      //field name / offset

    vtable_cl(String name){
        this.className = name;
        this.methods = new LinkedHashMap<>();
        this.fields = new LinkedHashMap<>();
    }

    //methods
    public void vtable_cl_addMethod(String name, int ofs){
        this.methods.put(name, ofs);
    }

    public int vtable_cl_getMethodOffset(String name){
        return this.methods.get(name);
    }

    public int vtable_cl_getMethodIndex(String name){
        return this.methods.get(name) / 8;              //!!
    }

    public boolean vtable_cl_MethodExists(String name){
        return this.methods.containsKey(name);
    }

    public ArrayList<String> vtable_cl_getMethodNames(){
        return new ArrayList<>(this.methods.keySet());
    }

    //fields
    public void vtable_cl_addField(String name, int ofs){
        this.fields.put(name, ofs);
    }

    public int vtable_cl_getFieldOffset(String name){
        return this.fields.get(name);
    }

    public String vtable_cl_getClassName(){
        return this.className;
    }

    public CustomPair<String, Integer> vtable_cl_getLastField(){                //get last field name and offset!! - for calloc
        String k = "";
        for(String key: this.fields.keySet())
            k = key;
        if(!k.equals(""))
            return new CustomPair<String, Integer>(k, this.fields.get(k));
        else
            return null;
    }

    public void vtable_cl_printClass(){
        System.out.println("\tFields:");
        for(String key: this.fields.keySet()){
            System.out.println("\t\t" + key + ": " + this.fields.get(key));
        }
        System.out.println("\tMethods:");
        for(String key: this.methods.keySet()){
            System.out.println("\t\t" + key + ": " + this.methods.get(key));
        }
    }
}
