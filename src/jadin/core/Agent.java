package jadin.core;

/**
 * Created by pk on 4/24/16.
 */

import com.sun.tools.attach.VirtualMachine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;

public class Agent {
    private static class Transformer implements ClassFileTransformer {
        private final JarFile jarFile;

        Transformer(JarFile jarFile) {
            this.jarFile = jarFile;
        }

        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {

            try {
                InputStream is = jarFile.getInputStream(jarFile.getJarEntry(className + ".class"));
                byte[] data = readInputStream(is);

                System.err.println(String.format("[+] Replaced %s, size=%d", className, data.length));

                return data;
            } catch (IOException e) {
                System.err.println("[!] Error reading class from replacement jar");
                e.printStackTrace();
            }

            return null;
        }

        private byte[] readInputStream(InputStream is) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[16384];
            int nRead;

            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();

            return buffer.toByteArray();
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        try {
            System.err.printf("[+] Loading replacement classes from %s\n", agentArgs);
            JarFile replacement = new JarFile(new File(agentArgs));
            inst.addTransformer(new Transformer(replacement));
        } catch (IOException e) {
            System.err.printf("[!] Error loading replacement jar: %s\n", agentArgs);
            e.printStackTrace();
        }
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        try {
            System.err.printf("[+] Loading replacement classes from /tmp/classes.jar");
            JarFile replacement = new JarFile(new File("/tmp/classes.jar"));
            inst.addTransformer(new Transformer(replacement));
        } catch (IOException e) {
            System.err.println("[!] Error loading replacement jar: /tmp/classes.jar");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 0) {
            System.out.println("Usage: java -jar jadin.jar pid");
            System.exit(1);
        }

        if (new File("/tmp/classes.jar").exists()) {
            System.out.printf("[+] Replacing classes using /tmp/classes.jar for %s\n", args[0]);

            try {
                File agent = new File(Agent.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI().getPath());

                System.out.printf("[+] Agent is running from %s\n", agent.getAbsolutePath());

                VirtualMachine vm = VirtualMachine.attach(args[0]);
                vm.loadAgent(agent.getAbsolutePath());
                vm.detach();
            } catch (Throwable e) {
                System.out.println("[!] Error connecting to virtual machine!");
                e.printStackTrace();
            }

            System.out.println("[+] Done!");

        } else {
            System.out.println("[!] Replacement /tmp/classes.jar is missing. Exiting...");
        }
    }
}

