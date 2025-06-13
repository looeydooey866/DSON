public class Apple {
    public int weight;
    public boolean tasty;

    public Apple(){

    }

    public Apple(int weight, boolean tasty){
        this.weight = weight;
        this.tasty = tasty;
    }

    @Override
    public String toString(){
        return String.format("Weight: [%d], Tasty: [%s]", weight, (tasty ? "true" : "false"));
    }
}
