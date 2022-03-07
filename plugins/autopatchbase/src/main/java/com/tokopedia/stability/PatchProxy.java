package com.tokopedia.stability;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by c_kunwu on 16/7/5.
 */
public class PatchProxy {

    private static CopyOnWriteArrayList<StabilityExtension> registerExtensionList = new CopyOnWriteArrayList<>();
    private static ThreadLocal<StabilityExtension> robustExtensionThreadLocal = new ThreadLocal<>();

    /**
     * The original instrumentation logic is as follows:
     * <pre>
     * if (PatchProxy.isSupport()) {
     * return PatchProxy.accessDispatch();
     * }
     * <pre/>
     * Encapsulate it into the following code
     * <pre>
     * PatchProxyResult patchProxyResult = PatchProxy.proxy();
     * if (patchProxyResult.isSupported) {
     * return patchProxyResult.result;
     * }
     * <pre/>
     * There are two advantages to doing this:
     * 1. Reduced package size. No kidding, although the latter code looks complicated, it actually produces fewer instructions.
     * The previous two function calls need to load 7 parameters to the stack each time. These 7 parameters are not simple basic types, which means that there are several more instructions than the latter.
     * The data shows that under the instrumentation of 5W methods, the latter can save 200KB compared to the former
     *
     * 2. Fix a bug. Robust actually supports the real-time offline of a patch by setting ChangeDelegate to null, so the original instrumentation logic has the problem of thread safety.
     * The root cause is that in the original logic, ChangeDelegate is a static variable value that is taken directly every time
     * If ChangeDelegate has a value when isSupport is executed, but ChangeDelegate is set to null when accessDispatch is executed, it means that the patched method will not execute any code this time
     * This will bring a series of unknown problems.
     * After encapsulation, it is guaranteed that the ChangeDelegate read by these two methods is the same.
     */
    public static PatchProxyResult proxy(Object[] paramsArray, Object current, ChangeDelegate changeDelegate, boolean isStatic, int methodNumber, Class[] paramsClassTypes, Class returnType) {
        PatchProxyResult patchProxyResult = new PatchProxyResult();
        if (PatchProxy.isSupport(paramsArray, current, changeDelegate, isStatic, methodNumber, paramsClassTypes, returnType)) {
            patchProxyResult.isSupported = true;
            patchProxyResult.result = PatchProxy.accessDispatch(paramsArray, current, changeDelegate, isStatic, methodNumber, paramsClassTypes, returnType);
        }
        return patchProxyResult;
    }

    public static boolean isSupport(Object[] paramsArray, Object current, ChangeDelegate changeDelegate, boolean isStatic, int methodNumber, Class[] paramsClassTypes, Class returnType) {
        //Robust patches will be executed first, other functions will be later
        if (changeDelegate == null) {
            //Do not execute patches, poll other listeners
            if (registerExtensionList == null || registerExtensionList.isEmpty()) {
                return false;
            }
            for (StabilityExtension stabilityExtension : registerExtensionList) {
                if (stabilityExtension.isSupport(new StabilityArguments(paramsArray, current, isStatic, methodNumber, paramsClassTypes, returnType))) {
                    robustExtensionThreadLocal.set(stabilityExtension);
                    return true;
                }
            }
            return false;
        }
        String classMethod = getClassMethod(isStatic, methodNumber);
        if (classMethod.isEmpty()) {
            return false;
        }
        Object[] objects = getObjects(paramsArray, current, isStatic);
        try {
            return changeDelegate.isSupport(classMethod, objects);
        } catch (Throwable t) {
            return false;
        }
    }


    public static Object accessDispatch(Object[] paramsArray, Object current, ChangeDelegate changeDelegate, boolean isStatic, int methodNumber, Class[] paramsClassTypes, Class returnType) {

        if (changeDelegate == null) {
            StabilityExtension stabilityExtension = robustExtensionThreadLocal.get();
            robustExtensionThreadLocal.remove();
            if (stabilityExtension != null) {
                notify(stabilityExtension.describeSelfFunction());
                return stabilityExtension.accessDispatch(new StabilityArguments(paramsArray, current, isStatic, methodNumber, paramsClassTypes, returnType));
            }
            return null;
        }
        String classMethod = getClassMethod(isStatic, methodNumber);
        if (classMethod.isEmpty()) {
            return null;
        }
        notify(Constants.PATCH_EXECUTE);
        Object[] objects = getObjects(paramsArray, current, isStatic);
        return changeDelegate.accessDispatch(classMethod, objects);
    }

    public static void accessDispatchVoid(Object[] paramsArray, Object current, ChangeDelegate changeDelegate, boolean isStatic, int methodNumber, Class[] paramsClassTypes, Class returnType) {
        if (changeDelegate == null) {
            StabilityExtension stabilityExtension = robustExtensionThreadLocal.get();
            robustExtensionThreadLocal.remove();
            if (stabilityExtension != null) {
                notify(stabilityExtension.describeSelfFunction());
                stabilityExtension.accessDispatch(new StabilityArguments(paramsArray, current, isStatic, methodNumber, paramsClassTypes, returnType));
            }
            return;
        }
        notify(Constants.PATCH_EXECUTE);
        String classMethod = getClassMethod(isStatic, methodNumber);
        if (classMethod.isEmpty()) {
            return;
        }
        Object[] objects = getObjects(paramsArray, current, isStatic);
        changeDelegate.accessDispatch(classMethod, objects);
    }


    private static Object[] getObjects(Object[] arrayOfObject, Object current, boolean isStatic) {
        Object[] objects;
        if (arrayOfObject == null) {
            return null;
        }
        int argNum = arrayOfObject.length;
        if (isStatic) {
            objects = new Object[argNum];
        } else {
            objects = new Object[argNum + 1];
        }
        int x = 0;
        for (; x < argNum; x++) {
            objects[x] = arrayOfObject[x];
        }
        if (!(isStatic)) {
            objects[x] = current;
        }
        return objects;
    }

    private static String getClassMethod(boolean isStatic, int methodNumber) {
        String classMethod = "";
        try {
            //It may be too time-consuming, this part needs to call the function yourself
//            java.lang.StackTraceElement stackTraceElement = (new java.lang.Throwable()).getStackTrace()[2];
//            String methodName = stackTraceElement.getMethodName();
//            String className = stackTraceElement.getClassName();
            String methodName = "";
            String className = "";
            classMethod = className + ":" + methodName + ":" + isStatic + ":" + methodNumber;
        } catch (Exception e) {

        }
        return classMethod;
    }

    private static String[] getClassMethodName() {
        java.lang.StackTraceElement stackTraceElement = (new java.lang.Throwable()).getStackTrace()[2];
        String[] classMethodname = new String[2];
        classMethodname[0] = stackTraceElement.getClassName();
        classMethodname[1] = stackTraceElement.getMethodName();
        return classMethodname;
    }

    /***
     *
     * @param stabilityExtension
     * Register a RobustExtension listener to notify the current executing program
     * @return
     */
    public synchronized static boolean register(StabilityExtension stabilityExtension) {
        if (registerExtensionList == null) {
            registerExtensionList = new CopyOnWriteArrayList<StabilityExtension>();
        }
        return registerExtensionList.addIfAbsent(stabilityExtension);
    }

    public synchronized static boolean unregister(StabilityExtension stabilityExtension) {
        if (registerExtensionList == null) {
            return false;
        }
        return registerExtensionList.remove(stabilityExtension);
    }

    /**
     * clear registerExtensionList and executing robustExtension
     */
    public static void reset() {
        registerExtensionList = new CopyOnWriteArrayList<>();
        robustExtensionThreadLocal = new ThreadLocal<>();
    }

    private static void notify(String info) {
        if (registerExtensionList == null) {
            return;
        }
        for (StabilityExtension stabilityExtension : registerExtensionList) {
            stabilityExtension.notifyListener(info);
        }
    }

}
