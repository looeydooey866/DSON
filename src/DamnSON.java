import TestSuite.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

//TODO all fields in damsonobject should accept fieldname with quote marks e.g.
//TODO "key": value
//Errors should also be documented for the user
public class DamnSON {
    public static String serialize(Object o) throws DamnSONException{
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
                    name = x.getAnnotation(Rename.class).value();
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

    public static class DamnSONException extends Exception{
    }

    public static class DamnSONObject {
        private final Object object;
        private final Class<?> objectClass;
        private final List<Field> classFields = new ArrayList<>();
        private final Map<String,Field> fieldGetter = new HashMap<>();
        private final Map<Class<?>,Class<?>> typeGetter = new HashMap<>();
        private final Map<Class<?>,Class<?>[]> mapTypeGetter = new HashMap<>();
        private Scanner queryParser;

        public DamnSONObject(Object o){
            this.object = o;
            this.objectClass = o.getClass();
            for (Field field : this.objectClass.getFields()){
                if (field.isAnnotationPresent(DoNotSerialize.class))
                    continue;
                classFields.add(field);
                String canonicalizedName = (
                    field.isAnnotationPresent(Rename.class)
                        ? field.getAnnotation(Rename.class).value()
                        : field.getName().toLowerCase()
                );
                fieldGetter.put(canonicalizedName, field);
            }
            //The performance diff when these two are inlined to the
            //loop above can also be examined btw
            getContainerTypes();
            getMapTypes();
        }

        //The result would be something like: List<TestSuite.Apple> -> TestSuite.Apple
        private void getContainerTypes(){
            for (Field field : classFields){
                Class<?> fieldClass = field.getType();
                if (isList(fieldClass) || isSet(fieldClass)){
                    ParameterizedType ptype = (ParameterizedType) field.getGenericType();
                    Class<?> genericClass = (Class<?>) ptype.getActualTypeArguments()[0];
                    typeGetter.put(fieldClass, genericClass);
                }
            }
        }

        //And this one would be something like: Map<Integer, String> -> {Integer, String}
        private void getMapTypes(){
            for (Field field : classFields){
                Class<?> fieldClass = field.getType();
                if (isMap(fieldClass)){
                    ParameterizedType ptype = (ParameterizedType) field.getGenericType();
                    Type[] types = ptype.getActualTypeArguments();
                    Class<?> keyClass = (Class<?>) types[0];
                    Class<?> valueClass = (Class<?>) types[1];
                    mapTypeGetter.put(fieldClass, new Class<?>[]{keyClass,valueClass});
                }
            }
        }

        //If a string is passed in json, it will be \"
        //If a string is passed within json, it will be \\\"
        private static String fixFormat(String s){
            StringBuilder result = new StringBuilder();
            boolean isInsideString = false;
            for (int i = 0; i < s.length(); i++){
                char cur = s.charAt(i);
                if (cur == '"' && (i == 0 || !(s.charAt(i-1) == '\\'))){
                    isInsideString ^= true;
                } else if (!isInsideString){
                    //Avoid handling whitespace between json
                    //Checking if there is a space between field: value
                    //Would be quite wasteful every time
                    if (cur == ' ' || cur == '\n')
                        continue;
                }
                result.append(cur);
            }
            return result.toString();
        }

        public void parse(String query) throws DamnSONException{
            setParser(query);
            parseJSON();
        }

        private void setParser(String query){
            String fixedQuery = fixFormat(query);
            queryParser = new Scanner(fixedQuery);
            queryParser.useDelimiter("");
        }

        private void overrideParser(Scanner parser){
            this.queryParser = parser;
        }

        private char peekOne(){
            queryParser.hasNext(".*");
            return queryParser.match().group(0).charAt(0);
        }

        private boolean endOfLine(){
            return !queryParser.hasNext();
        }

        private void expect(char c) throws DamnSONException{
            if (endOfLine() || peekOne() != c)
                throw new DamnSONException();
        }

        private void advanceOne(){
            queryParser.next();
        }

        private char nextChar(){
            return queryParser.next().charAt(0);
        }

        private String nextCharAsString(){
            return queryParser.next();
        }

        private int parseInt(){
            int result = 0;
            char next = peekOne();
            while (next >= '0' && next <= '9'){
                result = result * 10 + (next - '0');
                advanceOne();
                next = peekOne();
            }
            return result;
        }

        private double parseDouble(){
            double whole = 0, decimal = 0;
            char next = peekOne();
            while (next >= '0' && next <= '9'){
                whole = whole * 10 + (next - '0');
                advanceOne();
                next = peekOne();
            }
            if (next == '.'){
                advanceOne();
                next = peekOne();
                while (next >= '0' && next <= '9'){
                    decimal += (next - '0');
                    decimal /= 10;
                    advanceOne();
                    next = peekOne();
                }
            }
            return whole + decimal;
        }

        private float parseFloat(){
            float whole = 0, decimal = 0;
            char next = peekOne();
            while (next >= '0' && next <= '9'){
                whole = whole * 10 + (next - '0');
                advanceOne();
                next = peekOne();
            }
            if (next == '.'){
                advanceOne();
                next = peekOne();
                while (next >= '0' && next <= '9'){
                    decimal += (next - '0');
                    decimal /= 10;
                    advanceOne();
                    next = peekOne();
                }
            }
            if (next == 'f')
                advanceOne();
            return whole + decimal;
        }

        // Json is case-sensitive
        private boolean parseBoolean() throws DamnSONException {
            StringBuilder result = new StringBuilder();
            char next = peekOne();
            while (next >= 'a' && next <= 'z'){
                result.append(next);
                advanceOne();
                next = peekOne();
            }
            String value = result.toString();
            if (value.equals("true")){
                return true;
            }
            if (value.equals("false")){
                return false;
            }
            throw new DamnSONException();
        }

        private String parseString() throws DamnSONException{
            StringBuilder result = new StringBuilder();
            expect('\"');
            advanceOne();
            while (peekOne() != '\"'){
                result.append(nextCharAsString());
            }
            expect('\"');
            advanceOne();
            return result.toString();
        }

        private char parseChar() throws DamnSONException{
            expect('\'');
            advanceOne();
            char result = nextChar();
            expect('\'');
            advanceOne();
            return result;
        }

        private boolean isPrimitive(Class<?> checkClass){
            return (checkClass.isPrimitive() || checkClass == String.class);
        }

        private Class<?> primitiveToWrapper(Class<?> primitiveClass){
            if (primitiveClass == int.class){
                return Integer.class;
            }
            if (primitiveClass == boolean.class){
                return Boolean.class;
            }
            if (primitiveClass == float.class){
                return Float.class;
            }
            if (primitiveClass == double.class){
                return Double.class;
            }
            if (primitiveClass == char.class){
                return Character.class;
            }
            return primitiveClass;
        }

        private Class<?> wrapperToPrimitive(Class<?> wrapperClass){
            if (wrapperClass == Integer.class){
                return int.class;
            }
            if (wrapperClass == Boolean.class){
                return boolean.class;
            }
            if (wrapperClass == Float.class){
                return float.class;
            }
            if (wrapperClass == Double.class){
                return double.class;
            }
            if (wrapperClass == Character.class){
                return char.class;
            }
            return wrapperClass;
        }

        private Object parsePrimitive(Class<?> primitiveClass) throws DamnSONException{
            if (primitiveClass == Integer.class){
                return parseInt();
            }
            if (primitiveClass == Double.class){
                return parseDouble();
            }
            if (primitiveClass == Float.class){
                return parseFloat();
            }
            if (primitiveClass == String.class){
                return parseString();
            }
            if (primitiveClass == Character.class){
                return parseChar();
            }
            if (primitiveClass == Boolean.class){
                return parseBoolean();
            }
            return null;
        }

        private boolean isTypicalArray(Class<?> arrayClass){
            return arrayClass.isArray();
        }

        private Object parseTypicalArray(Class<?> arrayClass) throws DamnSONException {
            expect('[');
            advanceOne();
            Class<?> underlyingType = arrayClass.getComponentType();
            List<Object> objects = new ArrayList<>();
            char lookahead = peekOne();
            while (lookahead != ']'){
                objects.add(parseObject(underlyingType));
                lookahead = peekOne();
                if (lookahead != ']') {
                    expect(',');
                    advanceOne();
                    lookahead = peekOne();
                }
            }
            expect(']');
            advanceOne();
            Object result = Array.newInstance(underlyingType, objects.size());
            for (int i = 0; i < objects.size(); i++)
                Array.set(result, i, objects.get(i));
            return result;
        }

        private boolean isList(Class<?> listClass){
            return listClass == List.class;
        }

        private List<?> parseList(Class<?> listClass) throws DamnSONException {
            Class<?> underlyingClass = typeGetter.get(listClass);
            expect('[');
            advanceOne();
            char lookahead = peekOne();
            List<Object> result = new ArrayList<>();
            while (lookahead != ']'){
                Object element = parseObject(underlyingClass);
                result.add(element);
                lookahead = peekOne();
                if (lookahead != ']'){
                    expect(',');
                    advanceOne();
                    lookahead = peekOne();
                }
            }
            expect(']');
            advanceOne();
            return result;
        }

        private boolean isSet(Class<?> setClass){
            return setClass == Set.class;
        }

        private Set<?> parseSet(Class<?> setClass) throws DamnSONException{
            Class<?> underlyingClass = typeGetter.get(setClass);
            expect('[');
            advanceOne();
            char lookahead = peekOne();
            Set<Object> result = new HashSet<>();
            while (lookahead != ']'){
                Object element = parseObject(underlyingClass);
                result.add(element);
                lookahead = peekOne();
                if (lookahead != ']'){
                    expect(',');
                    advanceOne();
                    lookahead = peekOne();
                }
            }
            expect(']');
            advanceOne();
            return result;
        }

        private boolean isMap(Class<?> mapClass){
            return mapClass == Map.class;
        }

        private Map<?,?> parseMap(Class<?> mapClass) throws DamnSONException{
            Class<?>[] mapArguments = mapTypeGetter.get(mapClass);
            Class<?> keyClass = mapArguments[0];
            Class<?> valueClass = mapArguments[1];
            expect('[');
            advanceOne();
            char lookahead = peekOne();
            Map<Object,Object> result = new HashMap<>();
            //Key and value can be interchangeable
            StringBuilder commonBuilder = new StringBuilder();
            //Now, we parse every key-value object
            while (lookahead != ']'){
                Object key = null, value = null;
                expect('{');
                advanceOne();
                lookahead = peekOne();
                commonBuilder.setLength(0);
                //Linter doesn't like this but it is arguably more readable
                //I *assume* it will compile to the same bytecode anyway, its just an alias...
                StringBuilder firstArgument = commonBuilder;
                while (lookahead != ':'){
                    firstArgument.append(lookahead);
                    advanceOne();
                    lookahead = peekOne();
                }
                expect(':');
                advanceOne();
                if (firstArgument.toString().equals("key")){
                    key = parseObject(keyClass);
                }
                else if (firstArgument.toString().equals("value")){
                    value = parseObject(valueClass);
                }
                expect(',');
                advanceOne();
                lookahead = peekOne();

                // -- NEXT ITEM --

                commonBuilder.setLength(0);
                StringBuilder secondArgument = commonBuilder;
                while (lookahead != ':'){
                    secondArgument.append(lookahead);
                    advanceOne();
                    lookahead = peekOne();
                }
                expect(':');
                advanceOne();
                if (secondArgument.toString().equals("key")){
                    key = parseObject(keyClass);
                }
                else if (secondArgument.toString().equals("value")){
                    value = parseObject(valueClass);
                }
                expect('}');
                advanceOne();
                lookahead = peekOne();
                if (lookahead != ']'){
                    expect(',');
                    advanceOne();
                    lookahead = peekOne();
                }
                result.put(key, value);
            }
            expect(']');
            advanceOne();
            return result;
        }

        //This is to be used if it is expected that some nested objects
        //are not instantiated by the user. Right now, the user must by default
        //invoke no-arg constructors themselves for initializing their nested objects.
        //Actually, no. I think this design choice has to be made and will be beneficial
        private static Object getClassInstance(Class<?> objectClass) throws DamnSONException {
            try {
                Constructor<?>[] constructors = objectClass.getConstructors();
                for (Constructor<?> c : constructors) {
                    if (c.getParameterCount() == 0) {
                        return c.newInstance();
                    }
                }
            }
            catch (Exception e){
                throw new DamnSONException();
            }
            throw new DamnSONException();
        }

        private Object parseObject(Class<?> objectClass) throws DamnSONException{
            if (isPrimitive(wrapperToPrimitive(objectClass))){
                return parsePrimitive(primitiveToWrapper(objectClass));
            }
            else if (isTypicalArray(objectClass)){
                return parseTypicalArray(objectClass);
            }
            else if (isList(objectClass)){
                return parseList(objectClass);
            }
            else if (isSet(objectClass)){
                return parseSet(objectClass);
            }
            else if (isMap(objectClass)){
                return parseMap(objectClass);
            }
            else{
                Object innerObject = getClassInstance(objectClass);
                DamnSONObject dson = new DamnSONObject(innerObject);
                dson.overrideParser(queryParser);
                dson.parseJSON();
                return innerObject;
            }
        }

        private void parseJSON() throws DamnSONException{
            expect('{');
            advanceOne();
            while (true){
                parseField();
                if (peekOne() == '}')
                    break;
                advanceOne();
            }
            expect('}');
            advanceOne();
        }

        private void parseField() throws DamnSONException{
            StringBuilder fieldName = new StringBuilder();
            while (peekOne() != ':'){
                fieldName.append(nextCharAsString());
            }
            expect(':');
            advanceOne();

            Field field = fieldGetter.get(fieldName.toString());
            Class<?> fieldClass = field.getType();
            Object value = parseObject(fieldClass);
            try {
                field.set(object, value);
            } catch (Exception e){
                throw new DamnSONException();
            }
        }

        public static void main(String[] args) throws DamnSONException {
            DamnSONObject test = new DamnSONObject("");

            //parseInt
            String integerTest = "1024";
            int integerResult = 1024;
            test.setParser(integerTest);
            assert test.parseInt() == integerResult;

            //parseDouble
            String doubleTest = "1024.2048";
            double doubleResult = 1024.2048;
            test.setParser(doubleTest);
            assert Math.abs(test.parseDouble() - doubleResult) <= 0.0000001;

            //parseFloat
            int floatTestCount = 5;
            String[] floatTests = new String[]{"3", "3f", "3.f", ".1f","0.000123"};
            float[] floatResults = new float[]{3.0f, 3.0f, 3.0f, 0.1f, 0.000123f};
            for (int i = 0; i < floatTestCount; i++){
                test.setParser(floatTests[i]);
                assert Math.abs(test.parseFloat() - floatResults[i]) <= 0.000001f;
            }

            //parseBoolean
            int booleanTestCount = 2;
            String[] booleanTests = new String[]{"true,", "false}}"};
            boolean[] booleanResults = new boolean[]{true, false};
            for (int i = 0; i < booleanTestCount; i++){
                test.setParser(booleanTests[i]);
                assert test.parseBoolean() == booleanResults[i];
            }

            //Test on primitive fields
            TestObject testObject1 = new TestObject();
            String testJSON1 = "{name:\"jimbob\",age:255}";
            DamnSONObject obj = new DamnSONObject(testObject1);
            obj.setParser(testJSON1);
            obj.parseJSON();
            String json = serialize(testObject1);
            assert json.equals(testJSON1);

            //Test on simple arrays
            TestObject2 testObject2 = new TestObject2();
            String testJSON2 = """
                    {
                        numbers: [1, 2, 3, 5],
                        strings: ["jim", "bob", "bruhman"]
                    }
                    """;
            DamnSONObject obj2 = new DamnSONObject(testObject2);
            obj2.setParser(testJSON2);
            obj2.parseJSON();
            String json2 = serialize(testObject2);
            assert fixFormat(testJSON2).equals(json2);

            //Test on simple array of ojbects
            TestObject3 testObject3 = new TestObject3();
            String testJSON3 = """
                    {
                        apples: [
                            {
                                weight: 3,
                                tasty: true
                            },
                            {
                                weight:100,
                                tasty: false
                            },
                            {
                                weight: 40,
                                tasty: true
                            }
                        ]
                    }
                    """;
            DamnSONObject obj3 = new DamnSONObject(testObject3);
            obj3.setParser(testJSON3);
            obj3.parseJSON();
            String json3 = serialize(testObject3);
            assert fixFormat(testJSON3).equals(json3);

            //Test on lists and sets
            TestObject4 testObject4 = new TestObject4();
            String testJSON4 = """
                    {
                        apples: [
                            {
                                weight: 3,
                                tasty: true
                            },
                            {
                                weight:   100,
                                tasty: false
                            },
                            {
                                weight: 40,
                                tasty: true
                            }
                        ],
                        numbers: [1, 2, 3, 4, 5]
                    }
                    """;
            DamnSONObject obj4 = new DamnSONObject(testObject4);
            obj4.setParser(testJSON4);
            obj4.parseJSON();
            String json4 = serialize(testObject4);
            assert fixFormat(testJSON4).equals(json4);

            TestObject5 testObject5 = new TestObject5();
            String testJSON5 = """
                    {
                        applemap: [
                        {
                            key: "HEAVY_APPLE_1",
                            value: {
                                weight: 100,
                                tasty: false
                            }
                        },
                        {
                            key: "LIGHT_APPLE_1",
                            value: {
                                weight: 1,
                                tasty: true
                            }
                        },
                        {
                            key: "LIGHT_APPLE_2",
                            value: {
                                weight: 3,
                                tasty: false
                            }
                        }]
                    }
                    """;
            DamnSONObject obj5 = new DamnSONObject(testObject5);
            obj5.setParser(testJSON5);
            obj5.parseJSON();
            String json5 = serialize(testObject5);
            assert fixFormat(testJSON5).equals(json5);
        }
    }
}