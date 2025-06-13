import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

public class Test {
    /*
public static void main(String[] args) throws DamnSON.DamnSONException, NoSuchFieldException, IllegalAccessException, InstantiationException {
}
    ElusiveObject e = new ElusiveObject();
    Field f = e.getClass().getFields()[0];
    Class<?> clazz = f.getType();
    System.out.println(clazz.getName());
    List<Object> ls = new ArrayList<>();
    ls.add(3);
    ls.add("hi");
    ls.add(6);
    f.set(e,ls);
    String json = DamnSON.serialize(e);
    DamnSON.prettyPrint(json);
    ElusiveObject g = new ElusiveObject();
    DamnSON.get(g).parse(json);
    g.ls.remove(0);
    json = DamnSON.serialize(g);
    DamnSON.prettyPrint(json);
     */
    /*
    public static void main(String[] args) throws DamnSONException {
        List<String> objects = new ArrayList<>();
        objects.add("Banana");
        objects.add("Tissue paper");
        objects.add("1kg of eraser dust");
        Table t = new Table(objects);
        String json = DamnSON.serialize(t);
        System.out.println(json);
        DamnSON.prettyPrint(json);

     */
    public static void main(String[] args) {
        Set<String> s = new HashSet<>();
        s.add("Friday");
        s.add("Tuesday");
        s.add("Sunday");
        Food a = new Food("Burger", false, 1.0);
        Food b = new Food("Salad", true, 5.0);
        Food c = new Food("Tomato", false, 0.1);
        Map<Food, Integer> foodmap = new HashMap<>();
        foodmap.put(a, 10);
        foodmap.put(b, 3);
        foodmap.put(c, 999999);
        try {
            Dog d = new Dog("Jimbob", true, new Food(
                    "Pizza", true, 9.81
            ), new Occupation[]{
                    new Occupation("Google", 1000000, "Main Cheerleader", new int[]{}),
                    new Occupation("McDonalds", 10, "Lead Software Engineer", new int[]{0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290, 300, 310, 320, 330, 340, 350, 360})
            },
                    s,foodmap
                    );
            String json = DamnSON.serialize(d);
            DamnSON.prettyPrint(json);
        }
        catch (DamnSON.DamnSONException d){
            System.out.println("womp womp");
        }
    }
}
