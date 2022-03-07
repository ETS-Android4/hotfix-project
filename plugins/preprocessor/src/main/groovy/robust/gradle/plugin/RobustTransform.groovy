package robust.gradle.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.FeaturePlugin
import com.android.build.gradle.internal.pipeline.TransformManager
import com.tokopedia.stability.Constants
import com.meituan.robust.utils.JavaUtils
//import com.robust.plugins.BuildConfig
import javassist.ClassPool
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import robust.gradle.plugin.asm.AsmInsertImpl
import robust.gradle.plugin.javaassist.JavaAssistInsertImpl

import java.util.zip.GZIPOutputStream

/**
 * Created by mivanzhang on 16/11/3.
 *
 * insert code
 *
 */

class RobustTransform extends Transform {
    Project project
    static Logger logger
    private static List<String> hotfixPackageList = new ArrayList<>();
    private static List<String> hotfixMethodList = new ArrayList<>();
    private static List<String> exceptPackageList = new ArrayList<>();
    private static List<String> exceptMethodList = new ArrayList<>();
    private static boolean isHotfixMethodLevel = false;
    private static boolean isExceptMethodLevel = false;
    private static boolean isForceInsert = false;
    private static boolean useASM = true;
    private static boolean isForceInsertLambda = false;

    def robust
    InsertcodeStrategy insertcodeStrategy;

    RobustTransform(Project target) {
        project = target
        robust = new XmlSlurper().parse(new File("${project.rootDir.path}/${Constants.ROBUST_XML}"))
        logger = project.logger
        initConfig()
        if (null != robust.switch.turnOnRobust && !"true".equals(String.valueOf(robust.switch.turnOnRobust))) {
            return;
        }
        //isForceInsert is true to force the insertion
        if (!isForceInsert) {
            def taskNames = project.gradle.startParameter.taskNames
            def isDebugTask = false;
            for (int index = 0; index < taskNames.size(); ++index) {
                def taskName = taskNames[index]
                logger.debug "input start parameter task is ${taskName}"
                //FIXME:AssembleRelease shields Prepare. Because the Task has not yet been executed, it cannot be directly judged by the current BuildType, so the taskname in the current startParameter is directly analyzed.
                //In addition, there is a small pit. The name of the task cannot be an abbreviation and must be the full name. For example, assembleDebug cannot be any form of abbreviation input.
                if (taskName.endsWith("Debug") && taskName.contains("Debug")) {
//                    logger.warn " Don't register robust transform for debug model !!! task is：${taskName}"
                    isDebugTask = true
                    break;
                }
            }
            if (!isDebugTask) {
                project.android.registerTransform(this)
                project.afterEvaluate(new RobustAction())
//                    project.afterEvaluate(new RobustApkHashAction())
                logger.quiet "Register robust transform for ${project.name} successful !!!"
            }
        } else {
            project.android.registerTransform(this)
            project.afterEvaluate(new RobustAction())
//            project.afterEvaluate(new RobustApkHashAction())
        }
    }

    def initConfig() {
        hotfixPackageList = new ArrayList<>()
        hotfixMethodList = new ArrayList<>()
        exceptPackageList = new ArrayList<>()
        exceptMethodList = new ArrayList<>()
        isHotfixMethodLevel = false;
        isExceptMethodLevel = false;
        /*Parse the file*/
        for (name in robust.packname.name) {
            hotfixPackageList.add(name.text());
        }
        for (name in robust.exceptPackname.name) {
            exceptPackageList.add(name.text());
        }
        for (name in robust.hotfixMethod.name) {
            hotfixMethodList.add(name.text());
        }
        for (name in robust.exceptMethod.name) {
            exceptMethodList.add(name.text());
        }

        if (null != robust.switch.filterMethod && "true".equals(String.valueOf(robust.switch.turnOnHotfixMethod.text()))) {
            isHotfixMethodLevel = true;
        }

        if (null != robust.switch.useAsm && "false".equals(String.valueOf(robust.switch.useAsm.text()))) {
            useASM = false;
        } else {
            //Use asm by default
            useASM = true;
        }

        if (null != robust.switch.filterMethod && "true".equals(String.valueOf(robust.switch.turnOnExceptMethod.text()))) {
            isExceptMethodLevel = true;
        }

        if (robust.switch.forceInsert != null && "true".equals(String.valueOf(robust.switch.forceInsert.text())))
            isForceInsert = true
        else
            isForceInsert = false

        if (robust.switch.forceInsertLambda != null && "true".equals(String.valueOf(robust.switch.forceInsertLambda.text())))
            isForceInsertLambda = true;
        else
            isForceInsertLambda = false;
    }

    @Override
    String getName() {
        return "robust"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }


    @Override
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
        logger.quiet "================robust start trasforming for ${project.name}================"
        def startTime = System.currentTimeMillis()
        outputProvider.deleteAll()
        File jarFile = outputProvider.getContentLocation("main", getOutputTypes(), getScopes(),
                Format.JAR);
        if (!jarFile.getParentFile().exists()) {
            jarFile.getParentFile().mkdirs();
        }
        if (jarFile.exists()) {
            jarFile.delete();
        }

        ClassPool classPool = new ClassPool()
        project.android.bootClasspath.each {
            classPool.appendClassPath((String) it.absolutePath)
        }

        def box = ConvertUtils.toCtClasses(inputs, classPool)
        def cost = (System.currentTimeMillis() - startTime) / 1000
        logger.quiet "check all class cost $cost second, class count: ${box.size()}"
        if (useASM) {
            insertcodeStrategy = new AsmInsertImpl(hotfixPackageList, hotfixMethodList, exceptPackageList, exceptMethodList, isHotfixMethodLevel, isExceptMethodLevel, isForceInsertLambda);
        } else {
            insertcodeStrategy = new JavaAssistInsertImpl(hotfixPackageList, hotfixMethodList, exceptPackageList, exceptMethodList, isHotfixMethodLevel, isExceptMethodLevel, isForceInsertLambda);
        }
        insertcodeStrategy.moduleName = project.name
        insertcodeStrategy.insertCode(box, jarFile);
        writeCrumbFile(insertcodeStrategy.methodMap, Constants.METHOD_MAP_OUT_PATH)

        cost = (System.currentTimeMillis() - startTime) / 1000
        logger.quiet "robust cost $cost second"
        logger.quiet '================robust end================'
    }

    private void writeCrumbFile(Map methodMap, String path) {
        File file = new File(project.getRootProject().getBuildDir().path + path);
        if (!file.exists() && (!file.parentFile.mkdirs() || !file.createNewFile())) {
            logger.error(path + " file create error!!")
        }

        try {
            //write to file
            FileWriter writer = new FileWriter(file, true)
            BufferedWriter bw = new BufferedWriter(writer)
            for (String method : methodMap.keySet()) {
                int id = methodMap.get(method);
                bw.write(method + ":" + id)
                bw.newLine()
                bw.flush()
//                logger.quiet("key is   " + method + "  value is    " + id)
            }
            logger.quiet("Robust: Methods hooked  = ${methodMap.size()}")
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    private void writeMap2File(Map map, String path) {
        File file = new File(project.getRootProject().getBuildDir().path + path);
        if (!file.exists() && (!file.parentFile.mkdirs() || !file.createNewFile())) {
            logger.error(path + " file create error!!")
        }

        if (file.exists() && file.length() > 0) {
            map.putAll(JavaUtils.getMapFromZippedFile(file.path))
        }

        FileOutputStream fileOut = new FileOutputStream(file);
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream()
        ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
        objOut.writeObject(map)

        //gzip compression
        GZIPOutputStream gzip = new GZIPOutputStream(fileOut);
        gzip.write(byteOut.toByteArray())
        objOut.close();
        gzip.flush();
        gzip.close();

        fileOut.flush()
        fileOut.close()

    }

}