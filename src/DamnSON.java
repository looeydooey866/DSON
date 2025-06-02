import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Stack;

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
        Class<?>[] trivials = {Integer.class, Double.class, Float.class, Boolean.class};
        if (Arrays.stream(trivials).anyMatch(x -> x.isInstance(o))){
            return o.toString();
        }
        else if (o instanceof String){
            return String.format("\"%s\"", o);
        }
        else if (o instanceof Character){
            return String.format("'%s'",o);
        }
        else if (o.getClass().isArray()){
            StringBuilder result = new StringBuilder();
            result.append("[");
            int up = 0, down = Array.getLength(o);
            while (down > 0){
                result.append(getValue(Array.get(o, up)));
                if (down != 1)
                    result.append(",");
                else
                    result.append("]");
                up++;down--;
            }
            return result.toString();
        }
        else {
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
}