import java.util.*;
import java.lang.*;

public class ListStack{
    Stack<ArrayList<String>> stack;

    ListStack(){
        this.stack = new Stack<ArrayList<String>>();
    }

    public void stackNewTop(){
        this.stack.push(new ArrayList<String>());
    }

    public void stackPush(String expr){
        this.stack.peek().add(expr);
    }

    public ArrayList<String> stackPop(){
        return stack.pop();
    }
}
