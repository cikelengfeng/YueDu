package com.yuedu.utils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;

/**
 * 用于将一个泛型集合转换成另一种泛型集合，并且两个集合的元素是一一对应的，对应关系由调用者提供的Converter实例决定
 * e.g. cast ArrayList<JSONObject> to ArrayList<String>
 * Created by dong on 13-9-3.
 */
public class JSONObjectAndStringCollectionCoder {

    public static Collection<String> JSONObjectCollectionToString(@NotNull Collection<JSONObject> jsonObjects) {
        return convertCollection(jsonObjects,new Converter<String, JSONObject>() {
            @Override
            public String convert(JSONObject from) {
                return from.toString();
            }
        });
    }

    public static Collection<JSONObject> StringionToJSONObjectCollect(@NotNull Collection<String> jsonStrings) {
        return convertCollection(jsonStrings,new Converter<JSONObject, String>() {
            @Override
            public JSONObject convert(String from) {
                try {
                    return new JSONObject(from);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return null;
            }
        });
    }
    @SuppressWarnings("unchecked")
    public static <E,T> Collection<E> convertCollection(@NotNull Collection<T> from,@NotNull Converter<E,T> converter){
        if (from.isEmpty()) {
            return null;
        }
        try {
            Collection<E> result = from.getClass().newInstance();
            for (T t : from) {
                result.add(converter.convert(t));
            }
            return result;
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static interface Converter<E,T> {
        abstract E convert(T from);
    }
}
