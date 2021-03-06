package com.tokopedia.stability.gradle.plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.NotFoundException;

/**
 * Created by zhangmeng on 2017/5/10.
 */

public abstract class InsertcodeStrategy {
    //packnames need to be insert code List of package names that need to be inserted,
    protected List<String> hotfixPackageList = new ArrayList<>();
    //methods list need to insert code List of methods that require instrumentation
    protected List<String> hotfixMethodList = new ArrayList<>();

    //packnames  don`t need  to be insert code List of package names that do not require instrumentation，
    protected List<String> exceptPackageList = new ArrayList<>();

    //methods list  do not need to insert code List of methods that do not require instrumentation
    protected List<String> exceptMethodList = new ArrayList<>();
    //a switch control whether need to filter method in hotfixMethodList, if false ,hotfixMethodList will be ignored
    protected boolean isHotfixMethodLevel = false;

    //a switch control whether need to filter method in exceptMethodList, if false ,exceptMethodList will be ignored
    protected boolean isExceptMethodLevel = false;

    //a switch control whether need to insert code into lambda function
    protected boolean isForceInsertLambda = false;

    protected AtomicInteger insertMethodCount = new AtomicInteger(0);
    //record every method with unique method number, use LinkedHashMap to keep order for printing
    public HashMap<String, Integer> methodMap = new LinkedHashMap<>();
    protected String moduleName = "";
    protected String generateMethodId() {
        return String.format("%s_%s", moduleName, insertMethodCount.incrementAndGet());
    }

    public InsertcodeStrategy(List<String> hotfixPackageList, List<String> hotfixMethodList, List<String> exceptPackageList, List<String> exceptMethodList, boolean isHotfixMethodLevel, boolean isExceptMethodLevel, boolean isForceInsertLambda) {
        this.hotfixPackageList = hotfixPackageList;
        this.hotfixMethodList = hotfixMethodList;
        this.exceptPackageList = exceptPackageList;
        this.exceptMethodList = exceptMethodList;
        this.isHotfixMethodLevel = isHotfixMethodLevel;
        this.isExceptMethodLevel = isExceptMethodLevel;
        this.isForceInsertLambda = isForceInsertLambda;
        insertMethodCount.set(0);
    }

    /**
     * @param box all classes which will be packed into apk, all classes that need to be packed into apk
     * @param jarFile All instrumented classes will be output jarFile
     * @throws CannotCompileException
     * @throws IOException
     * @throws NotFoundException
     */
    protected abstract void insertCode(List<CtClass> box, File jarFile) throws CannotCompileException, IOException, NotFoundException;

    protected boolean isNeedInsertClass(String className) {

        //In this way, you can cull the specified class when you need to bury the point
        for (String exceptName : exceptPackageList) {
            if (className.startsWith(exceptName)) {
                return false;
            }
        }
        for (String name : hotfixPackageList) {
            if (className.startsWith(name)) {
                return true;
            }
        }
        return false;
    }

    protected void zipFile(byte[] classBytesArray, ZipOutputStream zos, String entryName) {
        try {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(classBytesArray, 0, classBytesArray.length);
            zos.closeEntry();
            zos.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
