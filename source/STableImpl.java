import java.util.*;
import java.lang.*;
import java.io.*;

public class STableImpl{
    private LinkedHashMap<String, ClassSt> classMap;            //K=className, V=ClassSt
    private LinkedHashMap<String, String> scopes;               //K=childScope, V=parentScope (for classes only)
    private String mainClass;

    STableImpl(){
        classMap = new LinkedHashMap<String, ClassSt>();
        scopes = new LinkedHashMap<String, String>();
    }

    public void setMainClass(String main){
        this.mainClass = main;
    }

    public Boolean isMainClass(String name){
        return name==this.mainClass;
    }

    public void addClass(String className){
        this.classMap.put(className, new ClassSt());
    }

    public ClassSt getClass(String className){
        return this.classMap.get(className);
    }

    public ArrayList<String> getClassNames(){
        return new ArrayList<String>(this.classMap.keySet());
    }

    public Boolean classExists(String className){
        return this.classMap.containsKey(className);
    }

    public void addInheritance(String parent,  String child){
        this.scopes.put(parent, child);
    }

    public String getSuperClass(String superName){
        return this.scopes.get(superName);
    }

    public String getSubClass(String subName){
        for(String key: this.scopes.keySet()){
            String value = this.scopes.get(key);
            if(value.equals(subName))
                return key;
        }
        return null;
    }

    public int getClassTotalMethodCount(String className){
        //initially get method count of current class, add those of superclasses that are not currently overriden.
        ClassSt cl = this.getClass(className);
        int mcount = cl.getMethodCount();
        String parentName = this.getSuperClass(className);
        while(parentName != null && !this.isMainClass(parentName)){
            ClassSt parent = this.getClass(parentName);
            ArrayList<String> method_names = parent.getMethodNames();
            for(String name: method_names){
                if(!cl.methodExists(name))
                    mcount+=1;
            }
            parentName = this.getSuperClass(parentName);
        }
        return mcount;
    }

    public String idLookup(String id, String workingClass, String workingMethod){
        //given a certain identifier, he can be declared: 1) in method body, 2) in method parameter list
        //3) as current class field, 4) as superclass field

        ClassSt wc = this.getClass(workingClass);
        MethodSt wm = wc.getMethod(workingMethod);
        if(wm.parExists(id))            //check if in parameter list
            return wm.getParType(id);
        else if(wm.varExists(id))       //check if variable in current method
            return wm.getVarType(id);
        else{
            //doesn't exist inside method, checking class fields
            if(wc.fieldExists(id))
                return wc.getFieldType(id);
            else{
                //check superclass' fields
                String parentClass = this.getSuperClass(workingClass);
                String currentClass = workingClass;
                while(parentClass != null){
                    ClassSt pc = this.getClass(parentClass);
                    if(pc.fieldExists(id))
                        return pc.getFieldType(id);
                    parentClass = this.getSuperClass(parentClass);
                }
                return null;
            }
        }
    }

    public void printAll(){
        for (String key: this.classMap.keySet()){
            System.out.println("Class: " + key);
            String parent = getSuperClass(key);
            if(parent != null){
                System.out.println("Extends " + parent);
            }
            this.classMap.get(key).printClass();
        }
    }

    public void fixOffsets(VTablesImpl vt){
        List<OffsetBlock> lst = new ArrayList<OffsetBlock>();   //list to hold offsets of all classes (for inheritance)

        for(String key: this.classMap.keySet()){
            if(key.equals(this.mainClass))              //skip main class
                continue;
            OffsetBlock ob = new OffsetBlock();

            //check if class extends or not
            String parent = getSuperClass(key);
            if(parent == null || parent.equals(this.mainClass))                     //doesn't extend or parent is main class, offsets start from 0
                ob = this.classMap.get(key).Offsets(key, 0, 0, null, null, vt);
            else{                                                                   //extends, offset start from superclass's indices
                for(OffsetBlock o: lst){
                    if(o.className.equals(parent)){
                        ob = this.classMap.get(key).Offsets(key, o.fieldPos, o.methodPos, this.getClass(parent), parent, vt);  //found superclass, take indices
                        break;
                    }
                }
            }
            //add current ob to list
            lst.add(ob);
        }
    }
}
