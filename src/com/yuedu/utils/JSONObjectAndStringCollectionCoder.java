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
        if (jsonObjects.isEmpty()) {
            return null;
        }
        try {
            Collection<String> result = jsonObjects.getClass().newInstance();
            for (JSONObject json : jsonObjects) {
                result.add(json.toString());
            }
            return result;
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static final Collection<JSONObject> StringionToJSONObjectCollect(@NotNull Collection<String> jsonStrings) {
        if (jsonStrings.isEmpty()) {
            return null;
        }
        try {
            Collection<JSONObject> result = jsonStrings.getClass().newInstance();
            for (String jsonStr : jsonStrings) {
                result.add(new JSONObject(jsonStr));
            }
            return result;
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}
