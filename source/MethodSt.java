import java.util.*;
import java.lang.*;

public class MethodSt{
    private Signature sig;
    private LinkedHashMap<String, String> parameters;
    private LinkedHashMap<String, String> variables;

    MethodSt(Signature s){
        this.sig = s;
        this.parameters = new LinkedHashMap<String, String>();
        this.variables = new LinkedHashMap<String, String>();
    }

    public void addVariable(String name, String type){
        this.variables.put(name, type);
    }

    public String getVarType(String varName){
        return this.variables.get(varName);
    }

    public Boolean varExists(String varName){
        return this.variables.containsKey(varName);
    }

    public void addParameter(String name, String type){
        this.parameters.put(name, type);
    }

    public String getParType(String parName){
        return this.parameters.get(parName);
    }

    public Boolean parExists(String parName){
        return this.parameters.containsKey(parName);
    }

    public ArrayList<String> getParamaterNames(){
        return new ArrayList<String>(this.parameters.keySet());
    }

    public void updateSignature(String partype){
        this.sig.addParameterType(partype);
    }

    public Signature getSignature(){
        return this.sig;
    }

    public String getReturnValue(){
        return this.sig.getReturnValue();
    }

    public void printMethod(){
        System.out.println("signature: " + this.sig.getSigStr());
        for(String key: this.parameters.keySet()){
            System.out.println("\t\tParameter: " + key + ", type: " + this.parameters.get(key));
        }
        for(String key: this.variables.keySet()){
            System.out.println("\t\tVariable: " + key + ", type: " + this.variables.get(key));
        }
    }
}
