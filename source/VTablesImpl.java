import java.util.*;
import java.lang.*;

public class VTablesImpl{
    private LinkedHashMap<String, vtable_cl> classes;

    VTablesImpl(){
        this.classes = new LinkedHashMap<>();
    }

    public void vtable_addClass(String name, vtable_cl cl){
        this.classes.put(name, cl);
    }

    public vtable_cl vtable_getClass(String name){
        return this.classes.get(name);
    }    

    public void print_vtable(){
        for(String key: this.classes.keySet()){
            System.out.println("Class " + key + ": ");
            this.classes.get(key).vtable_cl_printClass();
        }
    }
}
