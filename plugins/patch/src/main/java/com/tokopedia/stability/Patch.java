package com.tokopedia.stability;

import java.io.File;

/**
 * Created by mivanzhang on 15/7/23.
 * Patch Definition
 */
public class Patch implements Cloneable {
    //The number of the patch, the unique identifier of the patch
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name=name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
    //The path of the original patch file, it is recommended to put it in a private directory
    public String getLocalPath() {
        return localPath + ".jar";
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }
    //md5 of the original patch, to ensure that the original patch file has not been tampered with
    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    private String patchesInfoImplClassFullName;
    /**
     * patch name
     */
    private String name;

    /**
     * Patch download url
     */
    private String url;
    /**
     * Patch local save path
     */
    private String localPath;

    private String tempPath;

    /**
     * 补丁md5值
     */
    private String md5;

    /**
     * app hash值,避免应用内升级导致低版本app的补丁应用到了高版本app上
     */
    private String appHash;

    public boolean isAppliedSuccess() {
        return isAppliedSuccess;
    }

    public void setAppliedSuccess(boolean appliedSuccess) {
        isAppliedSuccess = appliedSuccess;
    }

    /**
     * Whether the patch has been applied success
     */
    private boolean isAppliedSuccess;

    /**
     * Delete Files
     */
    public void delete(String path) {
        File f = new File(path);
        f.delete();
    }

    public String getPatchesInfoImplClassFullName() {
        return patchesInfoImplClassFullName;
    }

    public void setPatchesInfoImplClassFullName(String patchesInfoImplClassFullName) {
        this.patchesInfoImplClassFullName = patchesInfoImplClassFullName;
    }

    public String getAppHash() {
        return appHash;
    }

    public void setAppHash(String appHash) {
        this.appHash = appHash;
    }
    //The decrypted patch file is a patch file that can be run directly. It is recommended to delete it immediately after loading to ensure security.
    public String getTempPath() {
        return tempPath + "_temp" + ".jar";
    }

    public void setTempPath(String tempPath) {
        this.tempPath = tempPath;
    }

    @Override
    public Patch clone() {
        Patch clone = null;
        try {
            clone = (Patch) super.clone();
        } catch (CloneNotSupportedException e) {
//            throw e;
        }
        return clone;
    }
}
