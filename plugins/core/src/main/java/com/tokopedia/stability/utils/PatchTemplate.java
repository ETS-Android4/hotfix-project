package com.tokopedia.stability.utils;


import com.tokopedia.stability.ChangeDelegate;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by mivanzhang on 16/7/26.
 */
public class PatchTemplate implements ChangeDelegate {
    public static final String MATCH_ALL_PARAMETER = "(\\w*\\.)*\\w*";

    public PatchTemplate() {
    }

    private static final Map<Object, Object> keyToValueRelation = new WeakHashMap<>();

    @Override
    public Object accessDispatch(String methodName, Object[] paramArrayOfObject) {
        return null;
    }

    @Override
    public boolean isSupport(String methodName, Object[] paramArrayOfObject) {
        return true;
    }

    //Solve the problem that boolean is optimized into byte
    private static Object fixObj(Object booleanObj) {
        if (booleanObj instanceof Byte) {
            byte byteValue = (Byte) booleanObj;
            boolean booleanValue = byteValue != 0x00;
            return new Boolean(booleanValue);
        }
        return booleanObj;
    }
}
