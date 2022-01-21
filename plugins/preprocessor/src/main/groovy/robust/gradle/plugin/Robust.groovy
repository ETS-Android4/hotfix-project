package robust.gradle.plugin

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.FeaturePlugin
import com.meituan.robust.autopatch.AutoPatchTransform
import org.gradle.api.Plugin
import org.gradle.api.Project;

class Robust implements Plugin<Project> {

    @Override
    void apply(Project project) {

        def hasApp = project.plugins.withType(AppPlugin)
        def hasFeature = project.plugins.withType(FeaturePlugin)

        if (isPatchEnable(project) && hasApp) {
            new AutoPatchTransform(project)
        } else if (!isPatchEnable(project)) {
            new RobustTransform(project)
        }
    }

    private static boolean isPatchEnable(Project project) {
        def isPatch = false
        if (project.hasProperty("com.robust.patch")) {
            isPatch = project.property("com.robust.patch")
        }
        return isPatch
    }
}
