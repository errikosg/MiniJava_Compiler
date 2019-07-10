import java.lang.*;


//class that manages all register and label names
public class Counters{
    private int registerCount;
    private int ifCount;
    private int loopCount;
    private int oobCount;
    private int arrCount;
    private int andClauseCount;             //counters for naming registers and labels

    Counters(){
        this.registerCount = -1;
        this.ifCount = -1;
        this.loopCount = -1;
        this.oobCount = -1;
        this.arrCount = -1;
        this.andClauseCount = -1;
    }

    public String makeRegister(){
        this.registerCount += 1;
        return "%_" + String.valueOf(this.registerCount);
    }

    public String makeIfLabel(){
        this.ifCount += 1;
        return "if" + String.valueOf(this.ifCount);
    }

    public String makeBoundsLabel(){
        this.oobCount += 1;
        return "oob" + String.valueOf(this.oobCount);
    }

    public String makeLoopLabel(){
        this.loopCount += 1;
        return "loop" + String.valueOf(this.loopCount);
    }

    public String makeArrLabel(){
        this.arrCount += 1;
        return "arr_alloc" + String.valueOf(this.arrCount);
    }

    public String makeAndLabel(){
        this.andClauseCount += 1;
        return "andclause" + String.valueOf(this.andClauseCount);
    }

    public void resetAll(){
        this.registerCount = -1;
        this.ifCount = -1;
        this.loopCount = -1;
        this.oobCount = -1;
        this.arrCount = -1;
        this.andClauseCount = -1;
    }
}
