package com.meituan.robust.autopatch

import com.meituan.robust.Constants
import com.meituan.robust.utils.JavaUtils
import javassist.*
import javassist.bytecode.AccessFlag
import javassist.bytecode.ClassFile
import javassist.bytecode.LocalVariableAttribute
import javassist.bytecode.MethodInfo
import javassist.expr.*

import static com.meituan.robust.utils.JavaUtils.printList

/**
 * Created by zhangmeng on 16/12/2.
 * <p>
 * create patch ctclass
 */
class PatchesFactory {
    private static PatchesFactory patchesFactory = new PatchesFactory();

    private PatchesFactory() {

    }

    /****
     * @param modifiedClass
     * @param isInline
     * @param patchName
     * @param patchMethodSignureSet methods need patch,if patchMethodSignatureSet length is 0,then will patch all methods in modifiedClass
     * @return
     */
    private CtClass createPatchClass(CtClass modifiedClass, boolean isInline, String patchName, Set patchMethodSignureSet, String patchPath) throws CannotCompileException, IOException, NotFoundException {
        List methodNoNeedPatchList = new ArrayList();
        //just keep  methods need patch
        if (patchMethodSignureSet.size() != 0) {
            for (CtMethod method : modifiedClass.getDeclaredMethods()) {
                //The new method needs to be kept in the patch class
                if (!Config.supportProGuard && Config.newlyAddedMethodSet.contains(method.longName)) {
                    continue;
                }
                //Not the way to be patched
                if ((!patchMethodSignureSet.contains(method.getLongName()) ||
                        //Not inline and new method
                        (!isInline && Config.methodMap.get(modifiedClass.getName() + "." + JavaUtils.getJavaMethodSignure(method)) == null))) {
                    methodNoNeedPatchList.add(method);
                } else {
                    //Remove methods in methodNeedPatchSet that require patches，All methods left in the patch class will be processed by default
                    Config.methodNeedPatchSet.remove(method.getLongName());
                }
            }
        }

        CtClass temPatchClass = cloneClass(modifiedClass, patchName, methodNoNeedPatchList);
        if (temPatchClass.getDeclaredMethods().length == 0) {
            printList(patchMethodSignureSet.toList());
            throw new RuntimeException("all methods in patch class are deteted,cannot find patchMethod in class " + temPatchClass.getName());
        }

        JavaUtils.addPatchConstruct(temPatchClass, modifiedClass);
        CtMethod reaLParameterMethod = CtMethod.make(JavaUtils.getRealParamtersBody(), temPatchClass);
        temPatchClass.addMethod(reaLParameterMethod);

        dealWithSuperMethod(temPatchClass, modifiedClass, patchPath);

        if (Config.supportProGuard && ReadMapping.getInstance().getClassMapping(modifiedClass.getName()) == null) {
            throw new RuntimeException(" something wrong with mappingfile ,cannot find  class  " + modifiedClass.getName() + "   in mapping file");
        }
        List<CtMethod> invokeSuperMethodList = Config.invokeSuperMethodMap.getOrDefault(modifiedClass.getName(), new ArrayList<>());

        createPublicMethodForPrivate(temPatchClass);

        for (CtMethod method : temPatchClass.getDeclaredMethods()) {
            //  shit !!too many situations need take into  consideration
            //   methods has methodid   and in  patchMethodSignatureSet
            if (!Config.addedSuperMethodList.contains(method) && reaLParameterMethod != method && !method.getName().startsWith(Constants.ROBUST_PUBLIC_SUFFIX)) {
                method.instrument(
                        new ExprEditor() {
                            public void edit(FieldAccess f) throws CannotCompileException {
                                if (Config.newlyAddedClassNameList.contains(f.getClassName())) {
                                    return;
                                }
                                Map memberMappingInfo = getClassMappingInfo(f.getField().declaringClass.name);
                                try {
                                    if (f.isReader()) {
                                        f.replace(ReflectUtils.getFieldString(f.getField(), memberMappingInfo, temPatchClass.getName(), modifiedClass.getName()));
                                    } else if (f.isWriter()) {
                                        f.replace(ReflectUtils.setFieldString(f.getField(), memberMappingInfo, temPatchClass.getName(), modifiedClass.getName()));
                                    }
                                } catch (NotFoundException e) {
                                    e.printStackTrace();
                                    throw new RuntimeException(e.getMessage());
                                }
                            }


                            @Override
                            void edit(NewExpr e) throws CannotCompileException {
                                //inner class in the patched class ,not all inner class
                                if (Config.newlyAddedClassNameList.contains(e.getClassName()) || Config.noNeedReflectClassSet.contains(e.getClassName())) {
                                    return;
                                }

                                try {
                                    if (!ReflectUtils.isStatic(Config.classPool.get(e.getClassName()).getModifiers()) && JavaUtils.isInnerClassInModifiedClass(e.getClassName(), modifiedClass)) {
                                        e.replace(ReflectUtils.getNewInnerClassString(e.getSignature(), temPatchClass.getName(), ReflectUtils.isStatic(Config.classPool.get(e.getClassName()).getModifiers()), getClassValue(e.getClassName())));
                                        return;
                                    }
                                } catch (NotFoundException e1) {
                                    e1.printStackTrace();
                                }

                                e.replace(ReflectUtils.getCreateClassString(e, getClassValue(e.getClassName()), temPatchClass.getName(), ReflectUtils.isStatic(method.getModifiers())));
                            }

                            @Override
                            void edit(Cast c) throws CannotCompileException {
                                MethodInfo thisMethod = ReflectUtils.readField(c, "thisMethod");
                                CtClass thisClass = ReflectUtils.readField(c, "thisClass");

                                def isStatic = ReflectUtils.isStatic(thisMethod.getAccessFlags());
                                if (!isStatic && !c.type.isArray()) {
                                    //inner class in the patched class ,not all inner class
                                    if (Config.newlyAddedClassNameList.contains(thisClass.getName()) || Config.noNeedReflectClassSet.contains(thisClass.getName())) {
                                        return;
                                    }
                                    // Static functions do not have this directive，will report an error directly。
                                    c.replace(ReflectUtils.getCastString(c, temPatchClass))
                                }
                            }

                            @Override
                            void edit(MethodCall m) throws CannotCompileException {

                                //methods no need reflect
                                if (Config.noNeedReflectClassSet.contains(m.method.declaringClass.name)) {
                                    return;
                                }
                                if (m.getMethodName().contains("lambdaFactory")) {
                                    //method contain modifeid class
                                    m.replace(ReflectUtils.getNewInnerClassString(m.getSignature(), temPatchClass.getName(), ReflectUtils.isStatic(method.getModifiers()), getClassValue(m.getClassName())));
                                    return;
                                }
                                try {
                                    if (!repalceInlineMethod(m, method, false)) {
                                        Map memberMappingInfo = getClassMappingInfo(m.getMethod().getDeclaringClass().getName());
                                        if (invokeSuperMethodList.contains(m.getMethod())) {
                                            /*
                                            It turns out that it is a bug to execute invokeSuperString only if invokeSuperMethodList.contains(m.getMethod()) is true.
                                             The hashcode of CtMethod is implemented with getStringRep(), which is equivalent to matching only according to the function name

                                             In such a situation, the repair code as shown below

                                            @Modify
                                            @Override
                                            public void onBackPressed() {
                                                if (mDispatchTouchEventHook != null && mDispatchTouchEventHook.onBackPressed()) {
                                                    return;
                                                }
                                                postFeedPosition();
                                                checkTaskRoot();
                                                super.onBackPressed();
                                            }

                                            The onBackPressed() method of mDispatchTouchEventHook is also determined to call the super method.
                                             However, activity's onBackPressed() returns void,
                                             The return value of the onBackPressed() method of mDispatchTouchEventHook is boolean, which directly causes the patch to fail.

                                             Even if you can make a patch, replacing the call for mDispatchTouchEventHook with a super call will easily trigger other bugs
                                             */
                                            int index = invokeSuperMethodList.indexOf(m.getMethod());
                                            CtMethod superMethod = invokeSuperMethodList.get(index);
                                            if (superMethod.getLongName() != null && superMethod.getLongName() == m.getMethod().getLongName()) {
                                                String firstVariable = "";
                                                if (ReflectUtils.isStatic(method.getModifiers())) {
                                                    //修复static 方法中含有super的问题，比如Aspectj处理后的方法
                                                    MethodInfo methodInfo = method.getMethodInfo();
                                                    LocalVariableAttribute table = methodInfo.getCodeAttribute().getAttribute(LocalVariableAttribute.tag);
                                                    int numberOfLocalVariables = table.tableLength();
                                                    if (numberOfLocalVariables > 0) {
                                                        int frameWithNameAtConstantPool = table.nameIndex(0);
                                                        firstVariable = methodInfo.getConstPool().getUtf8Info(frameWithNameAtConstantPool)
                                                    }
                                                }
                                                m.replace(ReflectUtils.invokeSuperString(m, firstVariable));
                                                return;
                                            }
                                        }
                                        m.replace(ReflectUtils.getMethodCallString(m, memberMappingInfo, temPatchClass, ReflectUtils.isStatic(method.getModifiers()), isInline));
                                    }
                                } catch (NotFoundException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
            }
        }
        //remove static code block,pay attention to the  class created by cloneClassWithoutFields which construct's
        CtClass patchClass = cloneClassWithoutFields(temPatchClass, patchName, null);
        patchClass = JavaUtils.addPatchConstruct(patchClass, modifiedClass);
        return patchClass;
    }

    /**
     * @param sourceClass
     * @param targetClassName
     * @return targetClass
     * @description targetClassis created by copy methods ,not by name
     */
    private
    static CtClass cloneClassWithoutFields(CtClass sourceClass, String patchName, List<CtMethod> exceptMethodList) throws NotFoundException, CannotCompileException {
        CtClass targetClass = cloneClass(sourceClass, patchName, exceptMethodList);
        targetClass.declaredFields.each { field ->
            targetClass.removeField(field);
        }
        //patch class shouldn`t have super class,we may be unable to initialize super class
        targetClass.setSuperclass(Config.classPool.get("java.lang.Object"));
        return targetClass;
    }

    public
    static CtClass cloneClass(CtClass sourceClass, String patchName, List<CtMethod> exceptMethodList) throws CannotCompileException, NotFoundException {
        CtClass targetClass = Config.classPool.getOrNull(patchName);
        if (targetClass != null) {
            targetClass.defrost();
        }
        targetClass = Config.classPool.makeClass(patchName);
        targetClass.getClassFile().setMajorVersion(ClassFile.JAVA_7);
        //warning 所有的super问题均在assist class来处理,
        targetClass.setSuperclass(sourceClass.getSuperclass());
        for (CtField field : sourceClass.getDeclaredFields()) {
            targetClass.addField(new CtField(field, targetClass));
        }
        ClassMap classMap = new ClassMap();
        classMap.put(patchName, sourceClass.getName());
        classMap.fix(sourceClass);
        for (CtMethod method : sourceClass.getDeclaredMethods()) {
            if (null == exceptMethodList || !exceptMethodList.contains(method)) {
                CtMethod newCtMethod = new CtMethod(method, targetClass, classMap);
                targetClass.addMethod(newCtMethod);
            }
        }
        targetClass.setModifiers(AccessFlag.clear(targetClass.getModifiers(), AccessFlag.ABSTRACT));
        return targetClass;
    }

    private void dealWithSuperMethod(CtClass patchClass, CtClass modifiedClass, String patchPath) throws NotFoundException, CannotCompileException, IOException {
        StringBuilder methodBuilder;
        List<CtMethod> invokeSuperMethodList = Config.invokeSuperMethodMap.getOrDefault(modifiedClass.getName(), new ArrayList());
        for (int index = 0; index < invokeSuperMethodList.size(); index++) {
            methodBuilder = new StringBuilder();
            if (invokeSuperMethodList.get(index).getParameterTypes().length > 0) {
                methodBuilder.append("public  static " + invokeSuperMethodList.get(index).getReturnType().getName() + "  " + ReflectUtils.getStaticSuperMethodName(invokeSuperMethodList.get(index).getName())
                        + "(" + patchClass.getName() + " patchInstance," + modifiedClass.getName() + " modifiedInstance," + JavaUtils.getParameterSignure(invokeSuperMethodList.get(index)) + "){");
            } else {
                methodBuilder.append("public  static  " + invokeSuperMethodList.get(index).getReturnType().getName() + "  " + ReflectUtils.getStaticSuperMethodName(invokeSuperMethodList.get(index).getName())
                        + "(" + patchClass.getName() + " patchInstance," + modifiedClass.getName() + " modifiedInstance){");
            }
            if (Constants.isLogging) {
                methodBuilder.append("android.util.Log.d(\"robust\", \" invoke  " + invokeSuperMethodList.get(index).getLongName() + " staticRobust method \");");
            }
            if (AccessFlag.isPackage(invokeSuperMethodList.get(index).getModifiers())) {
                throw new RuntimeException("autopatch does not support super method with package accessible ");
            }

            CtClass assistClass = PatchesAssistFactory.createAssistClass(modifiedClass, patchClass.getName(), invokeSuperMethodList.get(index));
            assistClass.writeFile(patchPath);

            if (invokeSuperMethodList.get(index).getReturnType().equals(CtClass.voidType)) {
                methodBuilder.append(NameManger.getInstance().getAssistClassName(patchClass.getName()) + "." + ReflectUtils.getStaticSuperMethodName(invokeSuperMethodList.get(index).getName())
                        + "(patchInstance,modifiedInstance");
            } else {
                methodBuilder.append(" return " + NameManger.getInstance().getAssistClassName(patchClass.getName()) + "." + ReflectUtils.getStaticSuperMethodName(invokeSuperMethodList.get(index).getName())
                        + "(patchInstance,modifiedInstance");
            }
            if (invokeSuperMethodList.get(index).getParameterTypes().length > 0) {
                methodBuilder.append(",");
            }
            methodBuilder.append(JavaUtils.getParameterValue(invokeSuperMethodList.get(index).getParameterTypes().length) + ");");
            methodBuilder.append("}");
            CtMethod ctMethod = CtMethod.make(methodBuilder.toString(), patchClass);
            Config.addedSuperMethodList.add(ctMethod);
            patchClass.addMethod(ctMethod);
        }
    }

    private Map getClassMappingInfo(String className) {
        ClassMapping classMapping = ReadMapping.getInstance().getClassMapping(className);
        if (null == classMapping) {
//            logger.warn("getClassMappingInfo~~~~~~~~~~~~~~~~class " + className + "  robust can not find in mapping ")
            classMapping = new ClassMapping();
        }
        return classMapping.getMemberMapping();
    }

    private String getClassValue(String className) {
        ClassMapping classMapping = ReadMapping.getInstance().getClassMappingOrDefault(className);
        if (classMapping.getValueName() == null) {
//            logger.warn("~~~~~~~~~~~~~~~~class " + className + "  robust can not find in mapping ")
            return className;
        } else {
            return classMapping.getValueName();
        }
    }

    public boolean repalceInlineMethod(MethodCall m, CtMethod method, boolean isNewClass) throws NotFoundException, CannotCompileException {
        ClassMapping classMapping = ReadMapping.getInstance().getClassMapping(m.getMethod().getDeclaringClass().getName());
        if (null != classMapping && classMapping.getMemberMapping().get(ReflectUtils.getJavaMethodSignureWithReturnType(m.getMethod())) == null) {
            m.replace(ReflectUtils.getInLineMemberString(m.getMethod(), ReflectUtils.isStatic(method.getModifiers()), isNewClass));
            return true;
        }
        return false;
    }

    public
    static void createPublicMethodForPrivate(CtClass ctClass) throws CannotCompileException, NotFoundException {
        //The inline method is private and needs to be converted to public
        List<CtMethod> privateMethodList = new ArrayList<>();
        for (CtMethod method : ctClass.getDeclaredMethods()) {
            if (AccessFlag.isPrivate(method.getModifiers())) {
                privateMethodList.add(method);
            }
        }
        StringBuilder private2PublicMethod;
        for (CtMethod method : privateMethodList) {
            private2PublicMethod = new StringBuilder();
            private2PublicMethod.append("public  " + getMethodStatic(method) + " " + method.getReturnType().getName() + " " + Constants.ROBUST_PUBLIC_SUFFIX + method.getName() + "(" + JavaUtils.getParameterSignure(method) + "){");
            private2PublicMethod.append("return " + method.getName() + "(" + JavaUtils.getParameterValue(method.getParameterTypes().length) + ");");
            private2PublicMethod.append("}");
            ctClass.addMethod(CtMethod.make(private2PublicMethod.toString(), ctClass));
        }

    }

    private static String getMethodStatic(CtMethod method) {
        //The inline method is private and needs to be converted to public
        if (ReflectUtils.isStatic(method.getModifiers())) {
            return " static ";
        }
        return "";
    }
    /****
     * @param modifiedClass
     * @param isInline
     * @param patchName
     * @param patchMethodSignureSet methods need patch,if patchMethodSignatureSet length is 0,then will patch all methods in modifiedClass
     * @return
     */

    public
    static CtClass createPatch(String patchpath, CtClass modifiedClass, boolean isInline, String patchName, Set patchMethodSignureSet) throws NotFoundException, CannotCompileException, IOException {
        return patchesFactory.createPatchClass(modifiedClass, isInline, patchName, patchMethodSignureSet, patchpath);
    }
}
