package robust.gradle.plugin

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.BaseVariant
import com.google.common.io.Files
import com.tokopedia.stability.Constants
import com.meituan.robust.utils.ZipUsingJavaUtil
import org.gradle.api.Action
import org.gradle.api.Project

class RobustAction implements Action<Project> {

    @Override
    void execute(Project project) {
        def workingDir = "${project.rootProject.buildDir.path}/${Constants.ROBUST_GENERATE_DIRECTORY}/tmp"
        def outputDir = "${project.rootProject.buildDir.path}/${Constants.ROBUST_GENERATE_DIRECTORY}"
        def hasApp = project.plugins.withType(AppPlugin)
        if(hasApp){
            project.android.applicationVariants.each { variant ->
                def task = project.tasks.findByName(getTaskNameForR8(variant))
                if (task == null) {
                    task = project.tasks.findByName(getProguardTaskName(variant))
                }
                if (task == null) {
                    return
                }
                project.logger.quiet("packageTask name ${task.name}")
                task.doLast {
                    project.logger.quiet("===start archive crumb file===")
                    def srcFile = variant.getMappingFile()
                    def destFile = new File(project.rootProject.buildDir.path + Constants.MAPPING_OUT_PATH)
                    Files.copy(srcFile, destFile)
                    def result = archieveFile(workingDir, outputDir)
                    if (result) {
                        deleteDir(new File(workingDir))
                    }
                }
            }
        }
    }

    private void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }

    private boolean archieveFile(String sourceDirPath, outputDir) throws IOException {
        String archivePath = new File(outputDir, "robust_crumb.zip").getAbsolutePath()
        File archiveFile = new File(archivePath)
        if (archiveFile.exists()) {
            archiveFile.delete()
        }
        ZipUsingJavaUtil zipUsingJavaUtil = new ZipUsingJavaUtil()
        def result = zipUsingJavaUtil.zipFiles(sourceDirPath, archivePath, false)
        if(result){
            System.out.println("Robust - archive generated at:" + archivePath)
        }
        return result
    }

    static String getProguardTaskName(BaseVariant variant) {
        return "minify" + variant.name.capitalize() + "WithProguard"
    }

    static String getTaskNameForR8(BaseVariant variant) {
        return "minify" + variant.name.capitalize() + "WithR8"
    }

}
