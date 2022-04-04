package com.tokopedia.stability;

/**
 * Created by zhangmeng on 2017/4/21.
 */

public interface StabilityExtension {
    /**
     *
     * @return Regarding the description of the access party information, please ensure that each business party is independent and different, and the only one remains unchanged.
     * It is recommended to use the package name of each business party + some functional descriptions, such as: com.tokopedia.stability android hot update system
     */
    String describeSelfFunction();

    /**
     * Notify listener information, notify which listener was executed
     * @param msg, msg is the return content in describeSelfFunction
     */
    void notifyListener(String msg);
    /**
     * @param paramsArray The parameter list of the original method
     * @param current The reference of the current object, that is, the this object, if it is a static method, the value is null
     * @param methodNumber The unique number of the method
     * @param paramsTypeArray method parameter type list
     * @param returnType the return value type of the method
     * The code logic executed first in the method body of the method, please note that after this method is executed, other logic in the original method body is not executed
     * @return
     *
     */
    Object accessDispatch(StabilityArguments stabilityArguments);

    /**
     *@param paramsArray parameter list of the original method
     * @param current The reference of the current object, that is, the this object, if it is a static method, the value is null
     * @param methodNumber The unique number of the method
     * @param paramsTypeArray method parameter type list
     * @param returnType the return value type of the method
     *
     * @return return true means do not continue to execute the original method body, only execute the logic of the accessDispatch method, and use the return value of the accessDispatch method as the return value of the original function
     * return false represents the execution of the original method body, but additional logic can be added to the isSupport method, such as recording the current method call stack or log, etc.
     */
    boolean isSupport(StabilityArguments stabilityArguments);

}
