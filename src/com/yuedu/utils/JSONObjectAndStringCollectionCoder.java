package com.yuedu.utils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;

/**
 * Created by dong on 13-9-3.
 */
public class JSONObjectAndStringCollectionCoder {

    public static final Collection<String> JSONObjectCollectionToString(@NotNull Collection<JSONObject> jsonObjects) {
        return convertCollection(jsonObjects,new Converter<String, JSONObject>() {
            @Override
            public String convert(JSONObject from) {
                return from.toString();
            }
        });
    }

    public static final Collection<JSONObject> StringionToJSONObjectCollect(@NotNull Collection<String> jsonStrings) {
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

    public static final <E,T> Collection<E> convertCollection(@NotNull Collection<T> from,@NotNull Converter<E,T> converter){
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
