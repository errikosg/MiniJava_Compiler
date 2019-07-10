import java.lang.*;

public class OffsetBlock{
    //used to return this "triplet" of information while fixing offsets.
    String className;
    Integer fieldPos;
    Integer methodPos;

    OffsetBlock(String c, Integer f, Integer m){
        this.className = c;
        this.fieldPos = f;
        this.methodPos = m;
    }

    OffsetBlock(){}
}
