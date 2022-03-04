package com.meituan.robust.autopatch

import com.android.SdkConstants
import com.android.build.api.transform.Context
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.builder.model.AndroidProject
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableSet
import com.meituan.robust.Constants
import com.meituan.robust.utils.JavaUtils
import com.meituan.robust.utils.SmaliTool
import javassist.CannotCompileException
import javassist.CtClass
import javassist.CtMethod
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.StopExecutionException
import org.gradle.internal.service.scopes.Scope

import java.util.regex.Matcher
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Patch extends Transform implements Plugin<Project> {

    private static String dex2SmaliCommand
    private static String smali2DexCommand
    private static String jar2DexCommand
    public static String ROBUST_DIR
    static Logger logger
    private Project project
    def hasApp, hasLib

    def initConfig(Project project) {
        //clear
        logger = project.logger
        this.project = project
        NameManger.init();
        InlineClassFactory.init();
        ReadMapping.init();
        Config.init();
        hasApp = project.plugins.withType(AppPlugin)
        hasLib = project.plugins.withType(LibraryPlugin)
        ROBUST_DIR = "${project.rootDir.path}${File.separator}robust${File.separator}"
        def baksmaliFilePath = "${ROBUST_DIR}${Constants.LIB_NAME_ARRAY[0]}"
        def smaliFilePath = "${ROBUST_DIR}${Constants.LIB_NAME_ARRAY[1]}"
        def dxFilePath = "${ROBUST_DIR}${Constants.LIB_NAME_ARRAY[2]}"
        Config.robustGenerateDirectory = "${project.buildDir}" + File.separator + "$Constants.ROBUST_GENERATE_DIRECTORY" + File.separator;
        dex2SmaliCommand = "  java -jar ${baksmaliFilePath} -o classout" + File.separator + "  $Constants.CLASSES_DEX_NAME";
        smali2DexCommand = "   java -jar ${smaliFilePath} classout" + File.separator + " -o " + Constants.PATACH_DEX_NAME;
        jar2DexCommand = "   java -jar ${dxFilePath} --dex --output=$Constants.CLASSES_DEX_NAME  " + Constants.ZIP_FILE_NAME;
        ReadXML.readXMl(project.rootDir.path);
        Config.methodMap = JavaUtils.readMethodMapFile(project.rootDir.path + Constants.METHOD_MAP_PATH)
    }

    @Override
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
        def startTime = System.currentTimeMillis()
        logger.quiet '================autoPatch start================'
        project.android.bootClasspath.each {
            println "Path : ${(String) it.absolutePath}"
            Config.classPool.appendClassPath((String) it.absolutePath)
        }
        def box = ReflectUtils.toCtClasses(inputs, Config.classPool)
        def cost = (System.currentTimeMillis() - startTime) / 1000
        logger.quiet "check all class cost $cost second, class count: ${box.size()}"
        autoPatch(project, box)
        logger.quiet '================method singure to methodid is printed below================'
        cost = (System.currentTimeMillis() - startTime) / 1000
        logger.quiet "autoPatch cost $cost second"
        logger.quiet("Robust patch generated successfully")
    }

    @Override
    String getName() {
        return "RobustPatchTransform"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_WITH_FEATURES
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void apply(Project project) {
        initConfig(project)
        project.android.registerTransform(this)
        def hasApp = project.plugins.withType(AppPlugin)
        def hasLib = project.plugins.withType(LibraryPlugin)
        def varians
        if (hasApp) {
            varians = project.android.applicationVariants
        }
        if (hasLib) {
            varians = project.android.libraryVariants
        }
        varians.all { variant ->
            def compileTask
            if (variant.hasProperty('javaCompileProvider')) {
                compileTask = variant.javaCompileProvider.get()
            } else {
                compileTask = variant.javaCompiler
            }
            project.task("generate${variant.name.capitalize()}RobustPatch").configure {
//                dependsOn ":${project.name}:${compileTask.name}"
                dependsOn ":${project.name}:build${variant.name.capitalize()}PreBundle"
            }
//                    .doLast {
//                def startTime = System.currentTimeMillis()
//                logger.quiet '================autoPatch start================'
//                project.android.bootClasspath.each {
//                    println "Path : ${(String) it.absolutePath}"
//                    Config.classPool.appendClassPath((String) it.absolutePath)
//                }
//                def box = ReflectUtils.toCtClasses(project, variant, Config.classPool)
//                def cost = (System.currentTimeMillis() - startTime) / 1000
//                logger.quiet "check all class cost $cost second, class count: ${box.size()}"
//                autoPatch(project, box)
//                logger.quiet '================method singure to methodid is printed below================'
//                cost = (System.currentTimeMillis() - startTime) / 1000
//                logger.quiet "autoPatch cost $cost second"
//
//                throw new RuntimeException("auto patch end successfully")
//            }
        }

    }

    def autoPatch(Project project, List<CtClass> box) {
        File buildDir = project.getBuildDir();
        String patchPath = buildDir.getAbsolutePath() + File.separator + Constants.ROBUST_GENERATE_DIRECTORY + File.separator;
        clearPatchPath(patchPath);
        ReadAnnotation.readAnnotation(box, logger);
        if (Config.supportProGuard) {
            ReadMapping.getInstance().initMappingInfo();
        }

        generatPatch(box, patchPath);
//
        zipPatchClassesFile()
        executeCommand(jar2DexCommand)
        executeCommand(dex2SmaliCommand)
        SmaliTool.getInstance().dealObscureInSmali();
        executeCommand(smali2DexCommand)
        //package patch.dex to patch.jar
        packagePatchDex2Jar()
        deleteTmpFiles()
    }

    def deleteTmpFiles() {
        File diretcory = new File(Config.robustGenerateDirectory);
        if (!diretcory.isDirectory()) {
            throw new RuntimeException("patch directry " + Config.robustGenerateDirectory + " dones not exist");
        } else {
            diretcory.listFiles(new FilenameFilter() {
                @Override
                boolean accept(File file, String s) {
                    return !(Constants.PATACH_JAR_NAME.equals(s))
                }
            }).each {
                if (it.isDirectory()) {
                    it.deleteDir()
                } else {
                    it.delete()
                }
            }
        }
    }

    def packagePatchDex2Jar() throws IOException {
        File inputFile = new File(Config.robustGenerateDirectory, Constants.PATACH_DEX_NAME);
        if (!inputFile.exists() || !inputFile.canRead()) {
            throw new RuntimeException("patch.dex is not exists or readable")
        }
        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(new File(Config.robustGenerateDirectory, Constants.PATACH_JAR_NAME)))
        zipOut.setLevel(Deflater.NO_COMPRESSION)
        FileInputStream fis = new FileInputStream(inputFile)
        zipFile(inputFile, zipOut, Constants.CLASSES_DEX_NAME);
        zipOut.close()
    }

    def executeCommand(String commond) {
        println "execute command ${commond}"
        Runtime run = Runtime.getRuntime()
        Process pr = run.exec(commond, null, new File(Config.robustGenerateDirectory))
        pr.waitFor()
        BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        String line = ""
        while ((line = buf.readLine()) != null) {
            System.out.println(line);
        }
    }

    def zipPatchClassesFile() {
        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(Config.robustGenerateDirectory + Constants.ZIP_FILE_NAME));
        zipAllPatchClasses(Config.robustGenerateDirectory + Config.patchPackageName.substring(0, Config.patchPackageName.indexOf(".")), "", zipOut);
        zipOut.close();
    }

    def zipAllPatchClasses(String path, String fullClassName, ZipOutputStream zipOut) {
        File file = new File(path);
        if (file.exists()) {
            fullClassName = fullClassName + file.name;
            if (file.isDirectory()) {
                fullClassName += File.separator;
                File[] files = file.listFiles();
                if (files.length == 0) {
                    return;
                } else {
                    for (File file2 : files) {
                        zipAllPatchClasses(file2.getAbsolutePath(), fullClassName, zipOut);
                    }
                }
            } else {
                //document
                zipFile(file, zipOut, fullClassName);
            }
        } else {
            logger.debug("file does not exist!");
        }
    }

    def zipFile(File inputFile, ZipOutputStream zos, String entryName){
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        FileInputStream fis = new FileInputStream(inputFile)
        byte[] buffer = new byte[4092];
        int byteCount = 0;
        while ((byteCount = fis.read(buffer)) != -1) {
            zos.write(buffer, 0, byteCount);
        }
        fis.close();
        zos.closeEntry();
        zos.flush();
    }

    def generatPatch(List<CtClass> box, String patchPath) {
        if (!Config.isManual) {
            if (Config.patchMethodSignatureSet.size() < 1) {
                throw new RuntimeException(" patch method is empty ,please check your Modify annotation or use RobustModify.modify() to mark modified methods")
            }
            Config.methodNeedPatchSet.addAll(Config.patchMethodSignatureSet)
            InlineClassFactory.dealInLineClass(patchPath, Config.newlyAddedClassNameList)
            initSuperMethodInClass(Config.modifiedClassNameList);
            //auto generate all class
            for (String fullClassName : Config.modifiedClassNameList) {
                CtClass ctClass = Config.classPool.get(fullClassName)
                CtClass patchClass = PatchesFactory.createPatch(patchPath, ctClass, false, NameManger.getInstance().getPatchName(ctClass.name), Config.patchMethodSignatureSet)
                patchClass.writeFile(patchPath)
                patchClass.defrost();
                createControlClass(patchPath, ctClass)
            }
            createPatchesInfoClass(patchPath);
            if (Config.methodNeedPatchSet.size() > 0) {
                throw new RuntimeException(" some methods haven't patched,see unpatched method list : " + Config.methodNeedPatchSet.toListString())
            }
        } else {
            autoPatchManually(box, patchPath);
        }

    }

    def createPatchesInfoClass(String patchPath) {
        PatchesInfoFactory.createPatchesInfo().writeFile(patchPath);
    }

    def createControlClass(String patchPath, CtClass modifiedClass) {
        CtClass controlClass = PatchesControlFactory.createPatchesControl(modifiedClass);
        controlClass.writeFile(patchPath);
        return controlClass;
    }

    def initSuperMethodInClass(List originClassList) {
        CtClass modifiedCtClass;
        for (String modifiedFullClassName : originClassList) {
            List<CtMethod> invokeSuperMethodList = Config.invokeSuperMethodMap.getOrDefault(modifiedFullClassName, new ArrayList());
            //Check the classes used in the currently modified class and add the mapping information
            modifiedCtClass = Config.classPool.get(modifiedFullClassName);
            modifiedCtClass.defrost();
            modifiedCtClass.declaredMethods.findAll {
                return Config.patchMethodSignatureSet.contains(it.longName)||InlineClassFactory.allInLineMethodLongname.contains(it.longName);
            }.each { behavior ->
                behavior.instrument(new ExprEditor() {
                    @Override
                    void edit(MethodCall m) throws CannotCompileException {
                        if (m.isSuper()) {
                            if (!invokeSuperMethodList.contains(m.method)) {
                                invokeSuperMethodList.add(m.method);
                            }
                        }
                    }
                });
            }
            Config.invokeSuperMethodMap.put(modifiedFullClassName, invokeSuperMethodList);
        }
    }

    def clearPatchPath(String patchPath) {
        new File(patchPath).deleteDir();
    }

    static def copyJarToRobust() {
        File targetDir = new File(ROBUST_DIR);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        for (String libName : Constants.LIB_NAME_ARRAY) {
            InputStream inputStream = JavaUtils.class.getResourceAsStream("/libs/" + libName);
            if (inputStream == null) {
                System.out.println("Warning!!!  Did not find " + libName + " ，you must add it to your project's libs ");
                continue;
            }
            File inputFile = new File(ROBUST_DIR + libName);
            try {
                OutputStream inputFileOut = new FileOutputStream(inputFile);
                JavaUtils.copy(inputStream, inputFileOut);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Warning!!! " + libName + " copy error " + e.getMessage());
            }
        }
    }


}