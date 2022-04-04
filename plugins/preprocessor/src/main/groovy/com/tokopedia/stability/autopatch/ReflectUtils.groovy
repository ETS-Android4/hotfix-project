package com.tokopedia.stability.autopatch

import com.android.SdkConstants
import com.android.build.api.transform.TransformInput
import com.android.build.gradle.api.BaseVariant
import com.android.builder.model.AndroidProject
import com.google.common.base.Joiner
import com.tokopedia.stability.Constants
import com.tokopedia.stability.utils.JavaUtils
import javassist.*
import javassist.bytecode.AccessFlag
import javassist.expr.Cast
import javassist.expr.MethodCall
import javassist.expr.NewExpr
import org.apache.commons.io.FileUtils
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.regex.Matcher

class ReflectUtils {

    public static final Boolean INLINE_R_FILE = true;
    public static int invokeCount = 0;

    static String getCompiledClassesPathForKotlin(BaseVariant variant) {
        String[] directorySegments = variant.getVariantData().getVariantDslInfo().getDirectorySegments()
        StringBuilder stringBuilder = new StringBuilder()
        if (directorySegments != null && directorySegments.length > 0) {
            for (int i = 0; i < directorySegments.length; i++) {
                String directorySegment = directorySegments[i]
                if (i > 0) {
                    directorySegment = directorySegment.substring(0, 1).toUpperCase() + directorySegment.substring(1)
                }
                stringBuilder.append(directorySegment)
            }
        }
        String[] componentList = com.android.utils.StringHelper.toStrings("tmp", "kotlin-classes", stringBuilder.toString())
        String newBuildPath = "/" + Joiner.on(File.separatorChar).join(componentList)
        return newBuildPath.replace("/", File.separator)
    }

    static String getCompiledClassesPathForLibrary(BaseVariant variant) {
        String[] directorySegments = variant.getVariantData().getVariantDslInfo().getDirectorySegments()
        StringBuilder stringBuilder = new StringBuilder()
        if (directorySegments != null && directorySegments.length > 0) {
            for (int i = 0; i < directorySegments.length; i++) {
                String directorySegment = directorySegments[i]
                if (i > 0) {
                    directorySegment = directorySegment.substring(0, 1).toUpperCase() + directorySegment.substring(1)
                }
                stringBuilder.append(directorySegment)
            }
        }
        String[] componentList = com.android.utils.StringHelper.toStrings(AndroidProject.FD_INTERMEDIATES, "javac",
                stringBuilder.toString(), "classes")
        String newBuildPath = "/" + Joiner.on(File.separatorChar).join(componentList)
        return newBuildPath.replace("/", File.separator)
    }

    static String getCompiledClassesPathForAppBundle(String compileTaskName, BaseVariant variant) {
        String[] directorySegments = variant.getVariantData().getVariantDslInfo().getDirectorySegments()
        StringBuilder stringBuilder = new StringBuilder()
        if (directorySegments != null && directorySegments.length > 0) {
            for (int i = 0; i < directorySegments.length; i++) {
                String directorySegment = directorySegments[i]
                if (i > 0) {
                    directorySegment = directorySegment.substring(0, 1).toUpperCase() + directorySegment.substring(1)
                }
                stringBuilder.append(directorySegment)
            }
        }
        String[] componentList = com.android.utils.StringHelper.toStrings(AndroidProject.FD_INTERMEDIATES, "javac",
                stringBuilder.toString(), compileTaskName, "classes")
        String newBuildPath = "/" + Joiner.on(File.separatorChar).join(componentList)
        return newBuildPath.replace("/", File.separator)
    }

    static List<CtClass> toCtClasses(Project project, BaseVariant variant, ClassPool classPool) {
        List<String> classNames = new ArrayList<>()
        List<CtClass> allClass = new ArrayList<>();
        def startTime = System.currentTimeMillis()
        String path = project.buildDir.getPath() + getCompiledClassesPathForLibrary(variant)
        String pathKt = project.buildDir.getPath() + getCompiledClassesPathForKotlin(variant)
        println("path :${path}")
        println("pathKt :${pathKt}")

        List<String> directoryInputs = new ArrayList<>();
        List<String> jarInputs = new ArrayList<>();
        directoryInputs.add(path)
        directoryInputs.add(pathKt)
        directoryInputs.add("/Users/erry.suprayogi/StudioProjects/Robust/plugins/patch/build/intermediates/javac/release/classes")
//        jarInputs.add("/Users/erry.suprayogi/StudioProjects/Robust/plugins/patch/build/intermediates/runtime_library_classes_jar/release/classes.jar")
        jarInputs.add("/Users/erry.suprayogi/StudioProjects/Robust/plugins/autopatchbase/build/libs/autopatchbase-2.0.5.jar")

        directoryInputs.forEach { p ->
            classPool.insertClassPath(p)
            FileUtils.listFiles(new File(p), null, true).each {
                if (it.absolutePath.endsWith(SdkConstants.DOT_CLASS)) {
                    def className = it.absolutePath.substring(p.length() + 1, it.absolutePath.length() - SdkConstants.DOT_CLASS.length()).replaceAll(Matcher.quoteReplacement(File.separator), '.')
                    println("className ${className}")
                    classNames.add(className)
                }
            }
        }

        jarInputs.forEach {p->
            classPool.insertClassPath(p)
            def jarFile = new JarFile(new File(p))
            Enumeration<JarEntry> classes = jarFile.entries();
            while (classes.hasMoreElements()) {
                JarEntry libClass = classes.nextElement();
                String className = libClass.getName();
                if (className.endsWith(SdkConstants.DOT_CLASS)) {
                    className = className.substring(0, className.length() - SdkConstants.DOT_CLASS.length()).replaceAll('/', '.')
                    classNames.add(className)
                }
            }
        }

        def cost = (System.currentTimeMillis() - startTime) / 1000
        println "autopatch read all class file cost $cost second"
        classNames.each {
            try {
                allClass.add(classPool.get(it));
            } catch (NotFoundException e) {
                println "class not found exception class name:  $it "
                e.printStackTrace()

            }
        }
        return allClass;
    }

    static List<CtClass> toCtClasses(Collection<TransformInput> inputs, ClassPool classPool) {
        List<String> classNames = new ArrayList<>()
        List<CtClass> allClass = new ArrayList<>();
        def startTime = System.currentTimeMillis()
        inputs.each {
            it.directoryInputs.each {
                def dirPath = it.file.absolutePath
                classPool.insertClassPath(it.file.absolutePath)
                FileUtils.listFiles(it.file, null, true).each {
                    if (it.absolutePath.endsWith(SdkConstants.DOT_CLASS)) {
                        def className = it.absolutePath.substring(dirPath.length() + 1, it.absolutePath.length() - SdkConstants.DOT_CLASS.length()).replaceAll(Matcher.quoteReplacement(File.separator), '.')
                        classNames.add(className)
                    }
                }
            }
            it.jarInputs.each {
                classPool.insertClassPath(it.file.absolutePath)
                def jarFile = new JarFile(it.file)
                Enumeration<JarEntry> classes = jarFile.entries();
                while (classes.hasMoreElements()) {
                    JarEntry libClass = classes.nextElement();
                    String className = libClass.getName();
                    if (className.endsWith(SdkConstants.DOT_CLASS)) {
                        className = className.substring(0, className.length() - SdkConstants.DOT_CLASS.length()).replaceAll('/', '.')
                        classNames.add(className)
                    }
                }
            }
        }
        def cost = (System.currentTimeMillis() - startTime) / 1000
        println "autopatch read all class file cost $cost second"
        classNames.each {
            try {
                allClass.add(classPool.get(it));
            } catch (NotFoundException e) {
                println "class not found exception class name:  $it "
                e.printStackTrace()

            }
        }
        return allClass;
    }


    public
    static String setFieldString(CtField field, Map memberMappingInfo, String patchClassName, String modifiedClassName) {
        boolean isStatic = isStatic(field.modifiers)
        StringBuilder stringBuilder = new StringBuilder("{");
        if (isStatic) {
            println("setFieldString static field " + field.getName() + "  declaringClass   " + field.declaringClass.name)
            if (AccessFlag.isPublic(field.modifiers)) {
                stringBuilder.append("\$_ = \$proceed(\$\$);");
            } else {
                if (field.declaringClass.name.equals(patchClassName)) {
                    stringBuilder.append(Constants.STABILITY_UTILS_FULL_NAME + ".setStaticFieldValue(\"" + getMappingValue(field.name, memberMappingInfo) + "\"," + modifiedClassName + ".class,\$1);");
                } else {
                    stringBuilder.append(Constants.STABILITY_UTILS_FULL_NAME + ".setStaticFieldValue(\"" + getMappingValue(field.name, memberMappingInfo) + "\"," + field.declaringClass.name + ".class,\$1);");
                }
            }
            if (Constants.isLogging) {
                stringBuilder.append("  android.util.Log.d(\"robust\",\"set static  value is \" +\"" + (field.getName()) + " ${getCoutNumber()}\");");
            }
        } else {
            stringBuilder.append("java.lang.Object instance;");
            stringBuilder.append("java.lang.Class clazz;");
            stringBuilder.append(" if(\$0 instanceof " + patchClassName + "){");
            stringBuilder.append("instance=((" + patchClassName + ")\$0)." + Constants.ORIGINCLASS + ";")
            stringBuilder.append("}else{");
            stringBuilder.append("instance=\$0;");
            stringBuilder.append("}");
            stringBuilder.append(Constants.STABILITY_UTILS_FULL_NAME + ".setFieldValue(\"" + getMappingValue(field.name, memberMappingInfo) + "\",instance,\$1,${field.declaringClass.name}.class);");
            if (Constants.isLogging) {
                stringBuilder.append("  android.util.Log.d(\"robust\",\"set value is \" + \"" + (field.getName()) + "    ${getCoutNumber()}\");");
            }
        }
        stringBuilder.append("}")
//        println field.getName() + "  set  field repalce  by  " + stringBuilder.toString()
        return stringBuilder.toString();
    }

    public
    static String getFieldString(CtField field, Map memberMappingInfo, String patchClassName, String modifiedClassName) {

        boolean isStatic = isStatic(field.modifiers);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");
        if (isStatic) {
            if (AccessFlag.isPublic(field.modifiers)) {
                //deal with android R file
                if (INLINE_R_FILE && isRFile(field.declaringClass.name)) {
                    println("getFieldString static field " + field.getName() + "   is R file macthed   " + field.declaringClass.name)
                    stringBuilder.append("\$_ = " + field.constantValue + ";");
                } else {
                    stringBuilder.append("\$_ = \$proceed(\$\$);");
                }
            } else {

                if (field.declaringClass.name.equals(patchClassName)) {
                    stringBuilder.append("\$_=(\$r) " + Constants.STABILITY_UTILS_FULL_NAME + ".getStaticFieldValue(\"" + getMappingValue(field.name, memberMappingInfo) + "\"," + modifiedClassName + ".class);");

                } else {
                    stringBuilder.append("\$_=(\$r) " + Constants.STABILITY_UTILS_FULL_NAME + ".getStaticFieldValue(\"" + getMappingValue(field.name, memberMappingInfo) + "\"," + field.declaringClass.name + ".class);");
                }
            }
            if (Constants.isLogging) {
                stringBuilder.append("  android.util.Log.d(\"robust\",\"get static  value is \" +\"" + (field.getName()) + "    ${getCoutNumber()}\");");
            }
        } else {
            stringBuilder.append("java.lang.Object instance;");
            stringBuilder.append(" if(\$0 instanceof " + patchClassName + "){");
            stringBuilder.append("instance=((" + patchClassName + ")\$0)." + Constants.ORIGINCLASS + ";")
            stringBuilder.append("}else{");
            stringBuilder.append("instance=\$0;");
            stringBuilder.append("}");

            stringBuilder.append("\$_=(\$r) " + Constants.STABILITY_UTILS_FULL_NAME + ".getFieldValue(\"" + getMappingValue(field.name, memberMappingInfo) + "\",instance,${field.declaringClass.name}.class);");
            if (Constants.isLogging) {
                stringBuilder.append("  android.util.Log.d(\"robust\",\"get value is \" +\"" + (field.getName()) + "    ${getCoutNumber()}\");");
            }
        }
        stringBuilder.append("}");
//        println field.getName() + "  get field repalce  by  " + stringBuilder.toString() + "\n"
        return stringBuilder.toString();
    }

    static boolean isRFile(String s) {
        if (s.lastIndexOf("R") < 0) {
            return false;
        }
        return Constants.RFileClassSet.contains(s.substring(s.indexOf("R")));
    }

    static String getmodifiedClassName(String patchName) {
        return NameManger.getInstance().getPatchNameMap().get(patchName);
    }

    static String getParameterClassSignure(String signature, String pacthClassName) {
        if (signature == null || signature.length() < 1) {
            return "";
        }
        StringBuilder signureBuilder = new StringBuilder();
        String name;
        boolean isArray = false;
        for (int index = 1; index < signature.indexOf(")"); index++) {
            if (Constants.OBJECT_TYPE == signature.charAt(index) && signature.indexOf(Constants.PACKNAME_END) != -1) {
                name = signature.substring(index + 1, signature.indexOf(Constants.PACKNAME_END, index)).replaceAll("/", ".")
                if (name.equals(pacthClassName)) {
                    signureBuilder.append(getmodifiedClassName(pacthClassName));
                } else {
                    signureBuilder.append(name);
                }
                index = signature.indexOf(";", index);
                if (isArray) {
                    signureBuilder.append("[]");
                    isArray = false;
                }
                signureBuilder.append(".class,");
            }
            if (Constants.PRIMITIVE_TYPE.contains(String.valueOf(signature.charAt(index)))) {
                switch (signature.charAt(index)) {
                    case 'Z': signureBuilder.append("boolean"); break;
                    case 'C': signureBuilder.append("char"); break;
                    case 'B': signureBuilder.append("byte"); break;
                    case 'S': signureBuilder.append("short"); break;
                    case 'I': signureBuilder.append("int"); break;
                    case 'J': signureBuilder.append("long"); break;
                    case 'F': signureBuilder.append("float"); break;
                    case 'D': signureBuilder.append("double"); break;
                    default: break;
                }
                if (isArray) {
                    signureBuilder.append("[]");
                    isArray = false;
                }
                signureBuilder.append(".class,");
            }

            if (Constants.ARRAY_TYPE.equals(String.valueOf(signature.charAt(index)))) {
                isArray = true;
            }
        }
        if (signureBuilder.length() > 0 && String.valueOf(signureBuilder.charAt(signureBuilder.length() - 1)).equals(","))
            signureBuilder.deleteCharAt(signureBuilder.length() - 1);
//        println("ggetParameterClassSignure   " + signureBuilder.toString())
        return signureBuilder.toString();
    }

    def
    static String getCreateClassString(NewExpr e, String className, String patchClassName, boolean isStatic) {
        StringBuilder stringBuilder = new StringBuilder();
        if (e.signature == null) {
            return "{\$_=(\$r)\$proceed(\$\$);}";
        }
        String signatureBuilder = getParameterClassSignure(e.signature, patchClassName);
        stringBuilder.append("{");
        if (isStatic) {
            if (signatureBuilder.length() > 1)
                stringBuilder.append("\$_= (\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectConstruct(\"" + className + "\",\$args,new Class[]{" + signatureBuilder + "});");
            else
                stringBuilder.append("\$_=(\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectConstruct(\"" + className + "\",\$args,null);");
        } else {
            if (signatureBuilder.length() > 1) {
                stringBuilder.append("java.lang.Object parameters[]=" + Constants.GET_REAL_PARAMETER + "(\$args);");
                if (Constants.isLogging)
                    stringBuilder.append("  android.util.Log.d(\"robust\",\" parameters[] from method     ${getCoutNumber()} \"+parameters);");

                stringBuilder.append("\$_= (\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectConstruct(\"" + className + "\",parameters,new Class[]{" + signatureBuilder + "});");

            } else
                stringBuilder.append("\$_=(\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectConstruct(\"" + className + "\",\$args,null);");
        }
        stringBuilder.append("}");
//        println("getCreateClassString   " + stringBuilder.toString())
        return stringBuilder.toString();
    }


    def
    static String getNewInnerClassString(String signature, String patchClassName, boolean isStatic, String className) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");
        String signatureBuilder = getParameterClassSignure(signature, patchClassName);
        if (isStatic) {
            if (signatureBuilder.length() > 1) {
                stringBuilder.append("\$_= (\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectConstruct(\"" + className + "\",\$args,new Class[]{" + signatureBuilder + "});");
            } else {
                stringBuilder.append("\$_= (\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectConstruct(\"" + className + "\",\$args,null);");
            }
        } else {
            if (signatureBuilder.length() > 1) {
                if (Constants.isLogging)
                    stringBuilder.append("  android.util.Log.d(\"robust\",\"  inner Class new     ${getCoutNumber()}\");");
                stringBuilder.append("java.lang.Object parameters[]=" + Constants.GET_REAL_PARAMETER + "(\$args);");
                stringBuilder.append("\$_= (\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectConstruct(\"" + className + "\",parameters,new Class[]{" + signatureBuilder + "});");
            } else {
                stringBuilder.append("\$_= (\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectConstruct(\"" + className + "\",\$args,null);");
            }
        }
        stringBuilder.append("}");
//        println("getNewInnerClassString   " + stringBuilder.toString())
        return stringBuilder.toString();
    }


    private static String getParameterClassString(CtClass[] parameters) {
        if (parameters == null || parameters.length < 1) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int index = 0; index < parameters.length; index++) {
            stringBuilder.append(parameters[index].name + ".class")
            if (index != parameters.length - 1) {
                stringBuilder.append(",");
            }
        }
        return stringBuilder.toString();
    }

    def
    static String getMethodCallString(MethodCall methodCall, Map memberMappingInfo, CtClass patchClass, boolean isInStaticMethod, boolean inline) {
        String signatureBuilder = getParameterClassString(methodCall.method.parameterTypes);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");
        // public method or methods in patched classes
//        if (!inline && isPatchClassMethod(methodCall, patchClass)) {
//            //todo Currently reflecting on all methods
////        if (AccessFlag.isPublic(methodCall.method.modifiers) || isPatchClassMethod(methodCall.method, patchClass)) {
//            println("in  getMethodCallString  before     isInStaticMethod is   " + isInStaticMethod + "  methodCall.className  " + methodCall.className + " linenumber " + methodCall.lineNumber)
//            if (isInStaticMethod) {
//                stringBuilder.append("\$_ = \$proceed(\$\$);");
//            } else {
//                stringBuilder.append("java.lang.Object parameters[]=" +  Constants.GET_REAL_PARAMETER + "(\$args);");
//                if (isStatic(methodCall.method.modifiers)) {
//                    stringBuilder.append("\$_ = \$proceed(" + getParameters(methodCall.method.parameterTypes) + ");");
//                } else {
//                    stringBuilder.append("\$_ =(\$r)((" + patchClass.name + ")(\$0) " + ")." + methodCall.methodName + "(" + getParameters(methodCall.method.parameterTypes) + ");");
//                }
//
//            }
//        } else {
        //Here we need to pay attention to the four cases of using static method and non-static method in static method and using static method and non-static method in non-static method
//            stringBuilder.append("java.lang.Object instance;");
        stringBuilder.append(methodCall.method.declaringClass.name + " instance;");
        if (isStatic(methodCall.method.modifiers)) {
            if (isInStaticMethod) {
                //Use static method in static method
                if (AccessFlag.isPublic(methodCall.method.modifiers)) {
                    stringBuilder.append("\$_ = \$proceed(\$\$);");
                } else {
                    if (signatureBuilder.toString().length() > 1) {
                        stringBuilder.append("\$_=(\$r) " + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectStaticMethod(\"" + getMappingValue(getJavaMethodSignureWithReturnType(methodCall.method), memberMappingInfo) + "\"," + methodCall.method.declaringClass.name + ".class,\$args,new Class[]{" + signatureBuilder.toString() + "});");
                    } else
                        stringBuilder.append("\$_=(\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectStaticMethod(\"" + getMappingValue(getJavaMethodSignureWithReturnType(methodCall.method), memberMappingInfo) + "\"," + methodCall.method.declaringClass.name + ".class,\$args,null);");
                }
                if (Constants.isLogging) {
                    stringBuilder.append("  android.util.Log.d(\"robust\",\"invoke static  method is      ${getCoutNumber()}  \" +\"" + methodCall.methodName + "\");");
                }
            } else {
                //Use static method in non-static method
                stringBuilder.append("java.lang.Object parameters[]=" + Constants.GET_REAL_PARAMETER + "(\$args);");
                if (signatureBuilder.toString().length() > 1) {
                    stringBuilder.append("\$_=(\$r) " + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectStaticMethod(\"" + getMappingValue(getJavaMethodSignureWithReturnType(methodCall.method), memberMappingInfo) + "\"," + methodCall.method.declaringClass.name + ".class,parameters,new Class[]{" + signatureBuilder.toString() + "});");
                } else
                    stringBuilder.append("\$_=(\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectStaticMethod(\"" + getMappingValue(getJavaMethodSignureWithReturnType(methodCall.method), memberMappingInfo) + "\"," + methodCall.method.declaringClass.name + ".class,parameters,null);");
            }

        } else {

            if (!isInStaticMethod) {
                //Using a non-static method inside a non-static method
                stringBuilder.append(" if(\$0 == this ){");
                stringBuilder.append("instance=((" + patchClass.getName() + ")\$0)." + Constants.ORIGINCLASS + ";")
                stringBuilder.append("}else{");
                stringBuilder.append("instance=\$0;");
                stringBuilder.append("}");
                if (signatureBuilder.toString().length() > 1) {
                    stringBuilder.append("java.lang.Object parameters[]=" + Constants.GET_REAL_PARAMETER + "(\$args);");
                    stringBuilder.append("\$_=(\$r) " + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectMethod(\"" + getMappingValue(getJavaMethodSignureWithReturnType(methodCall.method), memberMappingInfo) + "\",instance,parameters,new Class[]{" + signatureBuilder.toString() + "},${methodCall.method.declaringClass.name}.class);");
                } else
                    stringBuilder.append("\$_=(\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectMethod(\"" + getMappingValue(getJavaMethodSignureWithReturnType(methodCall.method), memberMappingInfo) + "\",instance,\$args,null,${methodCall.method.declaringClass.name}.class);");
                if (Constants.isLogging) {
                    stringBuilder.append("  android.util.Log.d(\"robust\",\"invoke  method is      ${getCoutNumber()} \" +\"" + methodCall.methodName + "\");");
                }
            } else {
                stringBuilder.append("instance=(" + methodCall.method.declaringClass.name + ")\$0;");
                //Using a non-static method in a static method
                if (signatureBuilder.toString().length() > 1) {
                    stringBuilder.append("\$_=(\$r) " + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectMethod(\"" + getMappingValue(getJavaMethodSignureWithReturnType(methodCall.method), memberMappingInfo) + "\",instance,\$args,new Class[]{" + signatureBuilder.toString() + "},${methodCall.method.declaringClass.name}.class);");
                } else
                    stringBuilder.append("\$_=(\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectMethod(\"" + getMappingValue(getJavaMethodSignureWithReturnType(methodCall.method), memberMappingInfo) + "\",instance,\$args,null,${methodCall.method.declaringClass.name}.class);");

            }
        }
//        }
        stringBuilder.append("}");
//        println("getMethodCallString  " + stringBuilder.toString())
        return stringBuilder.toString();
    }

    def
    static String getCastString(Cast c, CtClass patchClass) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");
        stringBuilder.append(" if(\$1 == this ){");
        stringBuilder.append("\$_=((" + patchClass.getName() + ")\$1)." + Constants.ORIGINCLASS + ";")
        stringBuilder.append("}else{");
        stringBuilder.append("\$_=(\$r)\$1;");
        stringBuilder.append("}");
        stringBuilder.append("}");
    }

    def
    static String getInLineMemberString(CtMethod method, boolean isInStaticMethod, boolean isNewClass) {

        StringBuilder parameterBuilder = new StringBuilder();
        for (int i = 0; i < method.parameterTypes.length; i++) {
            parameterBuilder.append("\$" + (i + 1));
            if (i != method.parameterTypes.length - 1) {
                parameterBuilder.append(",");
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{")

        if (NameManger.getInstance().getPatchNameMap().get(NameManger.getInstance().getInlinePatchNameWithoutRecord(method.declaringClass.name)) != null) {
            if (Constants.isLogging) {
                stringBuilder.append("  android.util.Log.d(\"robust\",\"deal inline in first   ${getCoutNumber()}  \" +\"" + method.name + "\");");
            }
            stringBuilder.append(NameManger.getInstance().getInlinePatchName(method.declaringClass.name) + " instance;");
            if (Constants.isLogging) {
                stringBuilder.append("  android.util.Log.d(\"robust\",\"deal inline method after new instance   ${getCoutNumber()}    \" +\"" + method.name + "\");");
            }
            if (isInStaticMethod || isNewClass) {
                //There is no need to consider the problem of parameter as this in static method
                stringBuilder.append(" instance=new " + NameManger.getInstance().getInlinePatchName(method.declaringClass.name) + "(\$0);")
                if (!isStatic(method.modifiers)) {
                    stringBuilder.append("\$_=(\$r)instance." + getInLineMethodName(method) + "(" + parameterBuilder.toString() + ");")
                } else {
                    stringBuilder.append("\$_ = (\$r)" + NameManger.getInstance().getInlinePatchName(method.declaringClass.name) + "." + getInLineMethodName(method) + "(" + parameterBuilder.toString() + ");");

                }
            } else {
                // todo The newly added class is the method that is called with inline, and the case that contains this is not considered
                String signatureBuilder = getParameterClassString(method.parameterTypes);
                stringBuilder.append("java.lang.Object target[]=" + Constants.GET_REAL_PARAMETER + "(new java.lang.Object[]{\$0});");
                stringBuilder.append(" instance=new " + NameManger.getInstance().getInlinePatchName(method.declaringClass.name) + "(target[0]);")
                //This needs to be handled by reflection, whether the value of each parameter of the processing method is this, or else it needs very disgusting code
                stringBuilder.append("java.lang.Object parameters[]=" + Constants.GET_REAL_PARAMETER + "(\$args);");
                if (Constants.isLogging) {
                    stringBuilder.append("  android.util.Log.d(\"robust\",\"deal inline method after new instance    ${getCoutNumber()}   \" +\"" + method.name + "\");");
                }
                if (!isStatic(method.modifiers)) {
                    if (signatureBuilder.toString().length() > 1) {
                        stringBuilder.append("\$_=(\$r) " + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectMethod(\"" + getInLineMethodName(method) + "\",instance,parameters,new Class[]{" + signatureBuilder.toString() + "},null);");
                    } else
                        stringBuilder.append("\$_=(\$r) " + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectMethod(\"" + getInLineMethodName(method) + "\",instance,parameters,null,null);");

                } else {
                    if (signatureBuilder.toString().length() > 1) {
                        //Reflect inline methods in patch
                        stringBuilder.append("\$_=(\$r) " + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectStaticMethod(\"" + getInLineMethodName(method) + "\"," + NameManger.getInstance().getInlinePatchNameWithoutRecord(method.declaringClass.name) + ".class,parameters,new Class[]{" + signatureBuilder.toString() + "});");
                    } else
                        stringBuilder.append("\$_=(\$r)" + Constants.STABILITY_UTILS_FULL_NAME + ".invokeReflectStaticMethod(\"" + getInLineMethodName(method) + "\"," + NameManger.getInstance().getInlinePatchNameWithoutRecord(method.declaringClass.name) + ".class,parameters,null);");

                }
            }

        } else {
            throw new RuntimeException("getInLineMemberString cannot find inline class ,origin class is  " + method.declaringClass.name)
        }
        if (Constants.isLogging) {
            stringBuilder.append("  android.util.Log.d(\"robust\",\"deal inline method   ${getCoutNumber()}   \" +\"" + method.name + "\");");
        }
        stringBuilder.append("}")
//        println("getInLineMemberString  " + stringBuilder.toString())
        return stringBuilder.toString();
    }

    static getInLineMethodName(CtMethod ctMethod) {
        if (AccessFlag.isPrivate(ctMethod.modifiers)) {
            return Constants.STABILITY_PUBLIC_SUFFIX + ctMethod.name;
        } else {
            return ctMethod.name;
        }
    }


    static boolean isStatic(int modifiers) {
        return (modifiers & AccessFlag.STATIC) != 0;
    }

    @Deprecated
    def static String invokeSuperString(MethodCall m) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("{");
        if (!m.method.returnType.equals(CtClass.voidType)) {
            stringBuilder.append("\$_=(\$r)");
        }
        if (m.method.parameterTypes.length > 0) {
            stringBuilder.append(getStaticSuperMethodName(m.methodName) + "(this," + Constants.ORIGINCLASS + ",\$\$);");
        } else {
            stringBuilder.append(getStaticSuperMethodName(m.methodName) + "(this," + Constants.ORIGINCLASS + ");");
        }

        stringBuilder.append("}");
//        println("invokeSuperString  " + m.methodName + "   " + stringBuilder.toString())
        return stringBuilder.toString();
    }

    def static String invokeSuperString(MethodCall m, String originClass) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("{");
        if (!m.method.returnType.equals(CtClass.voidType)) {
            stringBuilder.append("\$_=(\$r)");
        }
        if (m.method.parameterTypes.length > 0) {
            if (!originClass.isEmpty()) {
                stringBuilder.append(getStaticSuperMethodName(m.methodName) + "(null," + originClass + ",\$\$);");
            } else {
                stringBuilder.append(getStaticSuperMethodName(m.methodName) + "(this," + Constants.ORIGINCLASS + ",\$\$);");
            }
        } else {
            if (!originClass.isEmpty()) {
                stringBuilder.append(getStaticSuperMethodName(m.methodName) + "(null," + originClass + ");");
            } else {
                stringBuilder.append(getStaticSuperMethodName(m.methodName) + "(this," + Constants.ORIGINCLASS + ");");
            }
        }

        stringBuilder.append("}");
//        println("invokeSuperString  " + m.methodName + "   " + stringBuilder.toString())
        return stringBuilder.toString();
    }

    def static String getStaticSuperMethodName(String methodName) {
        return Constants.STATICFLAG + methodName;
    }

    def static getJavaMethodSignureWithReturnType(CtMethod ctMethod) {
        StringBuilder methodSignure = new StringBuilder();
        methodSignure.append(ctMethod.returnType.name)
        methodSignure.append(" ")
        methodSignure.append(ctMethod.name);
        methodSignure.append("(");
        for (int i = 0; i < ctMethod.getParameterTypes().length; i++) {
            methodSignure.append(ctMethod.getParameterTypes()[i].getName());
            if (i != ctMethod.getParameterTypes().length - 1) {
                methodSignure.append(",");
            }
        }
        methodSignure.append(")")
        return methodSignure.toString();
    }


    def static getMappingValue(String name, Map memberMappingInfo) {
        if (Constants.OBSCURE) {
            String value = memberMappingInfo.get(name);
            if (value == null || value.length() < 1) {
                if (name.contains("(")) {
                    name = JavaUtils.eradicatReturnType(name)
                    value = name.substring(0, name.indexOf("("));
                } else {
                    value = name;
                }
                System.err.println("Warning  class name  " + name + "   can not find in mapping !! ")
//                AutoPatchTransform.logger.warn("Warning  class name  " + name + "   can not find in mapping !! ")
//                JavaUtils.printMap(memberMappingInfo)
            }
            return value;
        } else {
            return JavaUtils.eradicatReturnType(name);
        }
    }


    private static String getCoutNumber() {
        return " No:  " + ++invokeCount;
    }

    public static Object readField(Object object, String fieldName) {
        def field = null
        def clazz = object.class;
        while (clazz != null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    break;
                }
                return field;
            } catch (final NoSuchFieldException e) {
                // ignore
            }
            clazz = clazz.superclass;
        }
        if (field != null) {
            return field.get(object);
        }
        return null;
    }

}
