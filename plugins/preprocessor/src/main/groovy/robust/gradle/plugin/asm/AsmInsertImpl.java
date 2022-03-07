package robust.gradle.plugin.asm;

import com.tokopedia.stability.ChangeDelegate;
import com.tokopedia.stability.Constants;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipOutputStream;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.bytecode.AccessFlag;
import robust.gradle.plugin.InsertcodeStrategy;


/**
 * Created by zhangmeng on 2017/5/10.
 * <p>
 * insert code using asm
 */

public class AsmInsertImpl extends InsertcodeStrategy {


    public AsmInsertImpl(List<String> hotfixPackageList, List<String> hotfixMethodList, List<String> exceptPackageList, List<String> exceptMethodList, boolean isHotfixMethodLevel, boolean isExceptMethodLevel, boolean isForceInsertLambda) {
        super(hotfixPackageList, hotfixMethodList, exceptPackageList, exceptMethodList, isHotfixMethodLevel, isExceptMethodLevel, isForceInsertLambda);
    }

    @Override
    protected void insertCode(List<CtClass> box, File jarFile) throws IOException, CannotCompileException {
        ZipOutputStream outStream = new JarOutputStream(new FileOutputStream(jarFile));
        //get every class in the box ,ready to insert code
        for (CtClass ctClass : box) {
            if ("META-INF.versions.9.module-info".equals(ctClass.getName())) {
                continue;
            }
            try {
                //change modifier to public ,so all the class in the apk will be public ,you will be able to access it in the patch
                ctClass.setModifiers(AccessFlag.setPublic(ctClass.getModifiers()));
                if (isNeedInsertClass(ctClass.getName()) && !(ctClass.isInterface() || ctClass.getDeclaredMethods().length < 1)) {
                    //only insert code into specific classes
                    zipFile(transformCode(ctClass.toBytecode(), ctClass.getName().replaceAll("\\.", "/")), outStream, ctClass.getName().replaceAll("\\.", "/") + ".class");
                } else {
                    zipFile(ctClass.toBytecode(), outStream, ctClass.getName().replaceAll("\\.", "/") + ".class");
                }
            } catch (Exception e){
                System.out.println("Error change modifier to public or modifying class");
            }
            ctClass.defrost();
        }
        outStream.close();
    }

    private class InsertMethodBodyAdapter extends ClassVisitor implements Opcodes {

        public InsertMethodBodyAdapter() {
            super(Opcodes.ASM9);
        }

        ClassWriter classWriter;
        private String className;
        //this maybe change in the future
        private Map<String, Boolean> methodInstructionTypeMap;

        public InsertMethodBodyAdapter(ClassWriter cw, String className, Map<String, Boolean> methodInstructionTypeMap) {
            super(Opcodes.ASM9, cw);
            this.classWriter = cw;
            this.className = className;
            this.methodInstructionTypeMap = methodInstructionTypeMap;
            //insert the field
            classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, Constants.INSERT_FIELD_NAME, Type.getDescriptor(ChangeDelegate.class), null, null);
        }


        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (isProtect(access)) {
                access = setPublic(access);
            }
            MethodVisitor mv = super.visitMethod(access, name,
                    desc, signature, exceptions);

            if (!isQualifiedMethod(access, name, desc, methodInstructionTypeMap)) {
                return mv;
            }
            StringBuilder parameters = new StringBuilder();
            Type[] types = Type.getArgumentTypes(desc);
            for (Type type : types) {
                parameters.append(type.getClassName()).append(",");
            }
            //remove the last ","
            if (parameters.length() > 0 && parameters.charAt(parameters.length() - 1) == ',') {
                parameters.deleteCharAt(parameters.length() - 1);
            }
            //record method number
            int methodId = generateMethodId().hashCode(); //String.valueOf(insertMethodCount.incrementAndGet());
            methodMap.put(className.replace('/', '.') + "." + name + "(" + parameters.toString() + ")", methodId);
            return new MethodBodyInsertor(mv, className, desc, isStatic(access), methodId, name, access);
        }

        private boolean isProtect(int access) {
            return (access & Opcodes.ACC_PROTECTED) != 0;
        }

        private int setPublic(int access) {
            return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
        }

        private boolean isQualifiedMethod(int access, String name, String desc, Map<String, Boolean> c) {
            //Class Initializer and Constructor Filtering
            if (AsmUtils.CLASS_INITIALIZER.equals(name) || AsmUtils.CONSTRUCTOR.equals(name)) {
                return false;
            }
            //@warn This part of the code, please focus on reviewing it, it will be fatal to judge the wrong condition
            //Please focus on reviewing this part of the code, it will be fatal to judge the wrong condition
            // The synthetic method is temporarily not aop. For example, AsyncTask will generate some synthetic methods with the same name, and code is also inserted into synthetic and private methods, mainly for lambda expressions
            if (!isForceInsertLambda && ((access & Opcodes.ACC_SYNTHETIC) != 0) && ((access & Opcodes.ACC_PRIVATE) == 0)) {
                return false;
            }
            if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                return false;
            }
            if ((access & Opcodes.ACC_NATIVE) != 0) {
                return false;
            }
            if ((access & Opcodes.ACC_INTERFACE) != 0) {
                return false;
            }

            if ((access & Opcodes.ACC_DEPRECATED) != 0) {
                return false;
            }

            //method filtering
            if (isExceptMethodLevel && exceptMethodList != null) {
                for (String item : exceptMethodList) {
                    if (name.matches(item)) {
                        return false;
                    }
                }
            }

            if (isHotfixMethodLevel && hotfixMethodList != null) {
                for (String item : hotfixMethodList) {
                    if (name.matches(item)) {
                        return true;
                    }
                }
            }

            boolean isMethodInvoke = methodInstructionTypeMap.getOrDefault(name + desc, false);
            //Traversal instruction type，
            if (!isMethodInvoke) {
                return false;
            }

            return !isHotfixMethodLevel;

        }

        class MethodBodyInsertor extends GeneratorAdapter implements Opcodes {
            private String className;
            private Type[] argsType;
            private Type returnType;
            List<Type> paramsTypeClass = new ArrayList();
            boolean isStatic;
            //At present, the methodid is of type int, and it may be changed to type of String in the future. A strong conversion has been performed here.
            int methodKey;

            public MethodBodyInsertor(MethodVisitor mv, String className, String desc, boolean isStatic, int methodKey, String name, int access) {
                super(Opcodes.ASM9, mv, access, name, desc);
                this.className = className;
                this.returnType = Type.getReturnType(desc);
                Type[] argsType = Type.getArgumentTypes(desc);
                for (Type type : argsType) {
                    paramsTypeClass.add(type);
                }
                this.isStatic = isStatic;
                this.methodKey = methodKey;
            }


            @Override
            public void visitCode() {
                //insert code here
                RobustAsmUtils.createInsertCode(this, className, paramsTypeClass, returnType, isStatic, methodKey);
            }

        }

        private boolean isStatic(int access) {
            return (access & Opcodes.ACC_STATIC) != 0;
        }

    }

    public byte[] transformCode(byte[] b1, String className) throws IOException {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassReader cr = new ClassReader(b1);
        ClassNode classNode = new ClassNode();
        Map<String, Boolean> methodInstructionTypeMap = new HashMap<>();
        cr.accept(classNode, 0);
        final List<MethodNode> methods = classNode.methods;
        for (MethodNode m : methods) {
            InsnList inList = m.instructions;
            boolean isMethodInvoke = false;
            for (int i = 0; i < inList.size(); i++) {
                if (inList.get(i).getType() == AbstractInsnNode.METHOD_INSN) {
                    isMethodInvoke = true;
                }
            }
            methodInstructionTypeMap.put(m.name + m.desc, isMethodInvoke);
        }
        InsertMethodBodyAdapter insertMethodBodyAdapter = new InsertMethodBodyAdapter(cw, className, methodInstructionTypeMap);
        cr.accept(insertMethodBodyAdapter, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

}
