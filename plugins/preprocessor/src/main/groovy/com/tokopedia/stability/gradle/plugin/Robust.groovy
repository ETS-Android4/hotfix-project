package com.tokopedia.stability.gradle.plugin

import com.android.build.gradle.AppPlugin
import com.tokopedia.stability.autopatch.AutoPatchTransform
import org.gradle.api.Plugin
import org.gradle.api.Project;

class Robust implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def hasApp = project.plugins.withType(AppPlugin)
        if (patchMode(project) && hasApp) {
            new AutoPatchTransform(project)
        } else if (preprocessorMode(project)) {
            new RobustTransform(project)
        }
    }

    private static boolean isPatchEnable(Project project) {
        if (project.hasProperty("com.robust.patch")) {
            def propertyValue = project.property("com.robust.patch")
            if ("true".equals(propertyValue)) {
                return true
            }
        }
        return false
    }

    private static boolean preprocessorMode(Project project) {
        if (project.hasProperty("com.robust.mode")) {
            def propertyValue = project.property("com.robust.mode")
            if ("preprocessor".equals(propertyValue)) {
                return true
            }
        }
        return false
    }

    private static boolean patchMode(Project project) {
        if (project.hasProperty("com.robust.mode")) {
            def propertyValue = project.property("com.robust.mode")
            if ("patch".equals(propertyValue)) {
                return true
            }
        }
        return false
    }

    private static boolean isRobustEnable(Project project) {
        if (project.hasProperty("enableRobust")) {
            def propertyValue = project.property("enableRobust")
            if ("true".equals(propertyValue)) {
                return true
            }
        }
        return false
    }
}
