package com.meituan.test;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.tokopedia.stability.Patch;
import com.tokopedia.stability.StabilityManipulate;
import com.tokopedia.stability.ApkHashUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mivanzhang on 17/2/27.
 *
 * We recommend you rewrite your own PatchManipulate class ,adding your special patch Strategy，in the demo we just load the patch directly
 *
 * <br>
 *   Pay attention to the difference of patch's LocalPath and patch's TempPath
 *
 *     <br>
 *    We recommend LocalPath store the origin patch.jar which may be encrypted,while TempPath is the true runnable jar
 *<br>
 *<br>
 *    我们推荐继承PatchManipulate实现你们App独特的A补丁加载策略，其中setLocalPath设置补丁的原始路径，这个路径存储的补丁是加密过得，setTempPath存储解密之后的补丁，是可以执行的jar文件
 *     <br>
 *     setTempPath设置的补丁加载完毕即刻删除，如果不需要加密和解密补丁，两者没有啥区别
 */

public class StabilityManipulateImp extends StabilityManipulate {
    /***
     * connect to the network ,get the latest patches
     * l联网获取最新的补丁
     * @param context
     *
     * @return
     */
    @Override
    protected List<Patch> fetchPatchList(Context context) {
        //Report the app's own robustApkHash to the server, and the server differentiates each apk build according to robustApkHash to issue patches to the app
        //apkhash is the unique identifier for apk, so you cannnot patch wrong apk.
        String robustApkHash = ApkHashUtils.readRobustApkHash(context);
        Log.w("robust","robustApkHash :" + robustApkHash);
        //connect to network to get patch list on servers
        //Go to the Internet here to get the patch list
        Patch patch = new Patch();
        patch.setName("123");
        //we recommend LocalPath store the origin patch.jar which may be encrypted, while TempPath is the true runnable jar
        //LocalPath is to store the original patch file. This file should be encrypted. TempPath is encrypted. After the patch under TempPath is loaded, it will be deleted to ensure security.
        //Some patch information needs to be set here, mainly patch information obtained from the Internet. Important such as MD5, perform simple verification of the original patch file, and the location of the patch storage. It is recommended to place the storage location of the patch in the private directory of the application to ensure security.
        patch.setLocalPath(Environment.getExternalStorageDirectory().getPath()+ File.separator+"Download"+File.separator + "patch");
        //setPatchesInfoImplClassFullName The setting item of each app can be customized independently. It needs to be ensured that the package name set by setPatchesInfoImplClassFullName is consistent with the xml configuration item patchPackname, and the class name must be: PatchesInfoImpl
        //Please note the settings here
        patch.setPatchesInfoImplClassFullName("com.tokopedia.stability.patch.PatchesInfoImpl");
        List  patches = new ArrayList<Patch>();
        patches.add(patch);
        return patches;
    }

    /**
     *
     * @param context
     * @param patch
     * @return
     *
     * you can verify your patches here
     */
    @Override

    protected boolean verifyPatch(Context context, Patch patch) {
        //do your verification, put the real patch to patch
        //Put it in the app's private directory
        patch.setTempPath(context.getCacheDir()+ File.separator+"robust"+File.separator + "patch");
        //in the sample we just copy the file
        try {
            copy(patch.getLocalPath(), patch.getTempPath());
        }catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException("copy source patch to local patch error, no patch execute in path "+patch.getTempPath());
        }

        return true;
    }
    public void copy(String srcPath,String dstPath) throws IOException {
        Log.d("robust", "path "+srcPath);
        File src=new File(srcPath);
        if(!src.exists()){
            throw new RuntimeException("source patch does not exist ");
        }
        File dst=new File(dstPath);
        if(!dst.getParentFile().exists()){
            dst.getParentFile().mkdirs();
        }
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
    /**
     *
     * @param patch
     * @return
     *
     * you may download your patches here, you can check whether patch is in the phone
     */
    @Override
    protected boolean ensurePatchExist(Patch patch) {
        return true;
    }
}
