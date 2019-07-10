import java.lang.*;

public class CustomPair<K,V>{
    private K key;
    private V value;

    public CustomPair(K k, V v){
        this.key = k;
        this.value = v;
    }

    public void setKey(K k) {
        this.key = k;
    }

    public void setValue(V v) {
        this.value = v;
    }

    public K getKey() {
        return this.key;
    }

    public V getValue() {
        return this.value;
    }
}
