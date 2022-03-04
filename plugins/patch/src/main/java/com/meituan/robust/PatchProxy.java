package com.meituan.robust;

import android.text.TextUtils;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by c_kunwu on 16/7/5.
 */
public class PatchProxy {

    private static CopyOnWriteArrayList<RobustExtension> registerExtensionList = new CopyOnWriteArrayList<>();
    private static ThreadLocal<RobustExtension> robustExtensionThreadLocal = new ThreadLocal<>();

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
     * 2. Fix a bug. Robust actually supports the real-time offline of a patch by setting ChangeQuickRedirect to null, so the original instrumentation logic has the problem of thread safety.
     * The root cause is that in the original logic, ChangeQuickRedirect is a static variable value that is taken directly every time
     * If ChangeQuickRedirect has a value when isSupport is executed, but ChangeQuickRedirect is set to null when accessDispatch is executed, it means that the patched method will not execute any code this time
     * This will bring a series of unknown problems.
     * After encapsulation, it is guaranteed that the ChangeQuickRedirect read by these two methods is the same.
     */
    public static PatchProxyResult proxy(Object[] paramsArray, Object current, ChangeQuickRedirect changeQuickRedirect, boolean isStatic, int methodNumber, Class[] paramsClassTypes, Class returnType) {
        PatchProxyResult patchProxyResult = new PatchProxyResult();
        if (PatchProxy.isSupport(paramsArray, current, changeQuickRedirect, isStatic, methodNumber, paramsClassTypes, returnType)) {
            patchProxyResult.isSupported = true;
            patchProxyResult.result = PatchProxy.accessDispatch(paramsArray, current, changeQuickRedirect, isStatic, methodNumber, paramsClassTypes, returnType);
        }
        return patchProxyResult;
    }

    public static boolean isSupport(Object[] paramsArray, Object current, ChangeQuickRedirect changeQuickRedirect, boolean isStatic, int methodNumber, Class[] paramsClassTypes, Class returnType) {
        //Robust patches will be executed first, other functions will be later
        if (changeQuickRedirect == null) {
            //Do not execute patches, poll other listeners
            if (registerExtensionList == null || registerExtensionList.isEmpty()) {
                return false;
            }
            for (RobustExtension robustExtension : registerExtensionList) {
                if (robustExtension.isSupport(new RobustArguments(paramsArray, current, isStatic, methodNumber, paramsClassTypes, returnType))) {
                    robustExtensionThreadLocal.set(robustExtension);
                    return true;
                }
            }
            return false;
        }
        String classMethod = getClassMethod(isStatic, methodNumber);
        if (TextUtils.isEmpty(classMethod)) {
            return false;
        }
        Object[] objects = getObjects(paramsArray, current, isStatic);
        try {
            return changeQuickRedirect.isSupport(classMethod, objects);
        } catch (Throwable t) {
            return false;
        }
    }


    public static Object accessDispatch(Object[] paramsArray, Object current, ChangeQuickRedirect changeQuickRedirect, boolean isStatic, int methodNumber, Class[] paramsClassTypes, Class returnType) {

        if (changeQuickRedirect == null) {
            RobustExtension robustExtension = robustExtensionThreadLocal.get();
            robustExtensionThreadLocal.remove();
            if (robustExtension != null) {
                notify(robustExtension.describeSelfFunction());
                return robustExtension.accessDispatch(new RobustArguments(paramsArray, current, isStatic, methodNumber, paramsClassTypes, returnType));
            }
            return null;
        }
        String classMethod = getClassMethod(isStatic, methodNumber);
        if (TextUtils.isEmpty(classMethod)) {
            return null;
        }
        notify(Constants.PATCH_EXECUTE);
        Object[] objects = getObjects(paramsArray, current, isStatic);
        return changeQuickRedirect.accessDispatch(classMethod, objects);
    }

    public static void accessDispatchVoid(Object[] paramsArray, Object current, ChangeQuickRedirect changeQuickRedirect, boolean isStatic, int methodNumber, Class[] paramsClassTypes, Class returnType) {
        if (changeQuickRedirect == null) {
            RobustExtension robustExtension = robustExtensionThreadLocal.get();
            robustExtensionThreadLocal.remove();
            if (robustExtension != null) {
                notify(robustExtension.describeSelfFunction());
                robustExtension.accessDispatch(new RobustArguments(paramsArray, current, isStatic, methodNumber, paramsClassTypes, returnType));
            }
            return;
        }
        notify(Constants.PATCH_EXECUTE);
        String classMethod = getClassMethod(isStatic, methodNumber);
        if (TextUtils.isEmpty(classMethod)) {
            return;
        }
        Object[] objects = getObjects(paramsArray, current, isStatic);
        changeQuickRedirect.accessDispatch(classMethod, objects);
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
     * @param robustExtension
     * Register a RobustExtension listener to notify the current executing program
     * @return
     */
    public synchronized static boolean register(RobustExtension robustExtension) {
        if (registerExtensionList == null) {
            registerExtensionList = new CopyOnWriteArrayList<RobustExtension>();
        }
        return registerExtensionList.addIfAbsent(robustExtension);
    }

    public synchronized static boolean unregister(RobustExtension robustExtension) {
        if (registerExtensionList == null) {
            return false;
        }
        return registerExtensionList.remove(robustExtension);
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
        for (RobustExtension robustExtension : registerExtensionList) {
            robustExtension.notifyListener(info);
        }
    }

}
