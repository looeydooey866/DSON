import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DamnSON {
    public static int recLevel = 0;
    public static String serialize(Object o) throws DamnSONException{
        assert recLevel <= 500;
        StringBuilder result = new StringBuilder();
        result.append("{");
        try {
            Class<?> c = o.getClass();
            Field[] i = c.getDeclaredFields();
            for (Field x : i){
                if (!Modifier.isPublic(x.getModifiers()))
                    continue;
                if (x.isAnnotationPresent(DoNotSerialize.class))
                    continue;
                String name = x.getName().toLowerCase();
                if (x.isAnnotationPresent(Rename.class)){
                    name = x.getAnnotation(Rename.class).name();
                }
                String value = getValue(x.get(o));
                result.append(name).append(":").append(value).append(",");
            }
            result.deleteCharAt(result.length()-1);
        }
        catch(Exception e){
            e.printStackTrace();
            throw new DamnSONException();
        }
        result.append("}");
        return result.toString();
    }

    public static String getValue(Object o) throws DamnSONException {
        Class<?>[] rawTrivials = {Integer.class, Double.class, Float.class, Boolean.class};
        if (Arrays.stream(rawTrivials).anyMatch(x -> x.isInstance(o))) {
            return o.toString();
        } else if (o instanceof String) {
            return String.format("\"%s\"", o);
        } else if (o instanceof Character) {
            return String.format("'%s'", o);
        } else if (o.getClass().isArray()) {
            StringBuilder result = new StringBuilder();
            result.append("[");
            int up = 0, down = Array.getLength(o);
            while (down > 0) {
                result.append(getValue(Array.get(o, up)));
                if (down != 1)
                    result.append(",");
                else
                    result.append("]");
                up++;
                down--;
            }
            return result.toString();
        } else if (o instanceof List<?> ls){
            return getValue(ls.toArray());
        }else if (o instanceof Set<?> s) {
            return getValue(s.toArray());
        } else if (o instanceof Map<?, ?> mp) {
            StringBuilder result = new StringBuilder();
            result.append('[');
            AtomicInteger down = new AtomicInteger(mp.size());
            mp.forEach((i, j) -> {
                try {
                    result.append('{').append("key:").append(getValue(i)).append(',').append("value:").append(getValue(j)).append('}');
                    if (down.getAndDecrement() != 1){
                        result.append(',');
                    }
                } catch (DamnSONException e) {
                    throw new RuntimeException();
                }
            });
            result.append(']');
            return result.toString();
        } else {
            return serialize(o);
        }
    }

    public static void prettyPrint(String json){
        StringBuilder result = new StringBuilder();
        int tl = 0;
        Stack<Character> st = new Stack<>();
        for (int i=0;i<json.length();i++){
            if (json.charAt(i) == '['){
                st.push('[');
            }
            else if (json.charAt(i) == ']'){
                st.pop();
            }
            if (json.charAt(i) == '}'){
                st.pop();
                result.append('\n').append(getTab(--tl));
            }
            result.append(json.charAt(i));
            if (json.charAt(i) == '{'){
                st.push('{');
                result.append('\n').append(getTab(++tl));
            }
            if (json.charAt(i) == ',' && st.peek() == '{'){
                result.append('\n').append(getTab(tl));
            }
            if (json.charAt(i) == ':'){
                result.append(' ');
            }
        }
        System.out.println(result);
    }

    private static String getTab(int i){
        return " ".repeat(i*4);
    }

    public static DamnSONObject get(Object o){
        return new DamnSONObject(o);
    }
}

//regarding the serialization/deserialization steps of maps/sets
//those types are typically things you need for access at runtime, their intrinsic
//form is more like array and object, but you need the sorting and quick lookup provided
//by these datatypes, so basically storing sets as arrays and maps as entries would be a
//better solution to reflect the "data passing" nature of JSON, as that's its primary use anyway