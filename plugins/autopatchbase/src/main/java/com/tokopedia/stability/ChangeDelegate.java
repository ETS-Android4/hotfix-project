package com.tokopedia.stability;

/**
 * Created by c_kunwu on 16/5/10.
 */
public interface ChangeDelegate {
    Object accessDispatch(String methodName, Object[] paramArrayOfObject);

    boolean isSupport(String methodName, Object[] paramArrayOfObject);
}
