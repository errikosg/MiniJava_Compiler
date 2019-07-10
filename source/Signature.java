import java.lang.*;
import java.util.*;

public class Signature{
    private ArrayList<String> types;            //parameter types
    private String ret_value;                   //return value

    Signature(String r){
        this.types = new ArrayList<String>();
        this.ret_value = r;
    }

    public void addParameterType(String t){
        this.types.add(t);
    }

    public ArrayList<String> getParamaterTypes(){
        return this.types;
    }

    /*public String getParamaterTypes(){
        String f = "(";
        for(int i=0; i<this.types.size(); i++){
            f += this.types.get(i);
            if(i < this.types.size()-1)
                f += ",";
        }
        f += ")";
        return f;
    }*/

    public String getReturnValue(){
        return this.ret_value;
    }

    public String toLLVM(){
        //!! takes signature and transforms it to llvm form
        String out = "";
        switch(this.ret_value){
            case "int":
                out += "i32";
                break;
            case "int[]":
                out += "i32*";
                break;
            case "boolean":
                out += "i1";
                break;
            default:
                out += "i8*";
                break;
        }
        out += " (i8*, ";
        for(String key: this.types){
            if(key.equals("int"))
                out += "i32, ";
            else if(key.equals("int[]"))
                out += "i32*, ";
            else if(key.equals("boolean"))
                out += "i1, ";
            else
                out += "i8*, ";
        }
        String pat = "(.*)(, $)";
        out = out.replaceAll(pat, "$1");
        out += ")*";
        return out;
    }

    public String getSigStr(){       //formated like: (int,int) --> int
        String f = "(";
        for(int i=0; i<this.types.size(); i++){
            f += this.types.get(i);
            if(i < this.types.size()-1)
                f += ",";
        }
        f += ") --> " + this.ret_value;
        return f;
    }
}
