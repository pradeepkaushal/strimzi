/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.test.k8s;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Arrays.asList;

public abstract class BaseKubeClient<K extends BaseKubeClient<K>> implements KubeClient<K> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseKubeClient.class);

    public static final String CREATE = "create";
    public static final String DELETE = "delete";

    protected abstract String cmd();

    public K deleteByName(String resourceType, String resourceName) {
        Exec.exec(cmd(), DELETE, resourceType, resourceName);
        return (K) this;
    }

    protected static class Context implements AutoCloseable {
        @Override
        public void close() {

        }
    }

    private static final Context NOOP = new Context();

    @Override
    public abstract K clientWithAdmin();

    protected Context defaultContext() {
        return NOOP;
    }

    protected Context adminContext() {
        return defaultContext();
    }

    @Override
    public boolean clientAvailable() {
        return Exec.isExecutableOnPath(cmd());
    }

    @Override
    public K createRole(String roleName, Permission... permissions) {
        try (Context context = adminContext()) {
            List<String> cmd = new ArrayList<>();
            cmd.addAll(asList(cmd(), CREATE, "role", roleName));
            for (Permission p : permissions) {
                for (String resource : p.resource()) {
                    cmd.add("--resource=" + resource);
                }
                for (int i = 0; i < p.verbs().length; i++) {
                    cmd.add("--verb=" + p.verbs()[i]);
                }
            }
            Exec.exec(cmd);
            return (K) this;
        }

    }

    @Override
    public K createRoleBinding(String bindingName, String roleName, String... user) {
        try (Context context = adminContext()) {
            List<String> cmd = new ArrayList<>();
            cmd.addAll(asList(cmd(), CREATE, "rolebinding", bindingName, "--role=" + roleName));
            for (int i = 0; i < user.length; i++) {
                cmd.add("--user=" + user[i]);
            }
            Exec.exec(cmd);
            return (K) this;
        }
    }

    @Override
    public K deleteRoleBinding(String bindingName) {
        try (Context context = adminContext()) {
            Exec.exec(cmd(), DELETE, "rolebinding", bindingName);
            return (K) this;
        }
    }

    @Override
    public K deleteRole(String roleName) {
        try (Context context = adminContext()) {
            Exec.exec(cmd(), DELETE, "role", roleName);
            return (K) this;
        }
    }


    @Override
    public String get(String resource, String resourceName) {
        return Exec.exec(cmd(), "get", resource, resourceName, "-o", "yaml").out();
    }

    @Override
    public K create(File... files) {
        try (Context context = defaultContext()) {
            KubeClusterException error = execRecursive(CREATE, files, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            if (error != null) {
                throw error;
            }
            return (K) this;
        }
    }

    @Override
    public K delete(File... files) {
        try (Context context = defaultContext()) {
            KubeClusterException error = execRecursive(DELETE, files, (f1, f2) -> f2.getName().compareTo(f1.getName()));
            if (error != null) {
                throw error;
            }
            return (K) this;
        }
    }

    private KubeClusterException execRecursive(String subcommand, File[] files, Comparator<File> cmp) {
        KubeClusterException error = null;
        for (File f : files) {
            if (f.isFile()) {
                if (f.getName().endsWith(".yaml")) {
                    try {
                        Exec.exec(cmd(), subcommand, "-f", f.getAbsolutePath());
                    } catch (KubeClusterException e) {
                        if (error == null) {
                            error = e;
                        }
                    }
                }
            } else {
                File[] s = f.listFiles();
                Arrays.sort(s, cmp);
                KubeClusterException e = execRecursive(subcommand, s, cmp);
                if (error == null) {
                    error = e;
                }
            }
        }
        return error;
    }

    @Override
    public K replace(File... files) {
        try (Context context = defaultContext()) {
            execRecursive("replace", files, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            return (K) this;
        }
    }

    @Override
    public K replaceContent(String yamlContent) {
        try (Context context = defaultContext()) {
            Exec.exec(yamlContent, asList(cmd(), "replace", "-f", "-"));
            return (K) this;
        }
    }

    @Override
    public K createNamespace(String name) {
        try (Context context = adminContext()) {
            Exec.exec(cmd(), CREATE, "namespace", name);
        }
        return (K) this;
    }

    @Override
    public K deleteNamespace(String name) {
        try (Context context = adminContext()) {
            Exec.exec(cmd(), DELETE, "namespace", name);
        }
        return (K) this;
    }

    @Override
    public ProcessResult exec(String pod, String... command) {
        List<String> cmd = new ArrayList<>(asList(cmd(), "exec", pod, "--"));
        cmd.addAll(asList(command));
        return Exec.exec(cmd);
    }

    enum ExType {
        BREAK,
        CONTINUE,
        THROW
    }

    private K waitFor(String resource, String name, Predicate<JsonNode> ready) {
        return waitFor(resource, name, ready, ex -> ex instanceof KubeClusterException.NotFound ? ExType.CONTINUE : ExType.THROW);
    }

    private K waitFor(String resource, String name, Predicate<JsonNode> ready, Function<KubeClusterException, ExType> shouldBreak) {
        LOGGER.debug("Waiting for {} {}", resource, name);
        long timeoutMs = 120_000L;
        long pollMs = 1_000L;
        long t0 = System.currentTimeMillis();
        ObjectMapper mapper = new ObjectMapper();
        outer: while (true) {
            try {
                String jsonString = Exec.exec(cmd(), "get", resource, name, "-o", "json").out();
                LOGGER.trace("{}", jsonString);
                JsonNode actualObj = mapper.readTree(jsonString);

                if (ready.test(actualObj)) {
                    break;
                }

                if (System.currentTimeMillis() - t0 > timeoutMs) {
                    throw new RuntimeException("Timeout waiting for " + resource + " " + name + " to be ready");
                }

                try {
                    Thread.sleep(pollMs);
                } catch (InterruptedException e) {
                    break;
                }
            } catch (KubeClusterException e) {
                /* keep polling till it exists */
                switch (shouldBreak.apply(e)) {
                    case BREAK:
                        break outer;
                    case CONTINUE:
                        continue outer;
                    case THROW:
                        throw e;
                }
                throw e;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return (K) this;
    }

    @Override
    public K waitForDeployment(String name) {
        return waitFor("deployment", name, actualObj -> {
            int rep = actualObj.get("status").get("replicas").asInt();
            JsonNode jsonNode = actualObj.get("status").get("readyReplicas");
            if (jsonNode != null) {
                int readyRep = jsonNode.asInt();
                if (rep == readyRep) {
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public K waitForPod(String name) {
        return waitFor("pod", name,
                actualObj -> "Running".equals(actualObj.get("status").get("phase").asText()));
    }

    @Override
    public K waitForStatefulSet(String name, boolean waitForPods) {
        return waitFor("statefulset", name,
                actualObj -> {
                    int rep = actualObj.get("status").get("replicas").asInt();
                    JsonNode currentReplicas = actualObj.get("status").get("currentReplicas");

                    if (currentReplicas != null && rep == currentReplicas.asInt()) {
                        LOGGER.debug("Waiting for pods of statefulset {}", name);
                        if (waitForPods) {
                            for (int ii = 0; ii < rep; ii++) {
                                waitForPod(name + "-" + ii);
                            }
                        }
                        return true;
                    }
                    return false;
                });
    }

    @Override
    public K waitForResourceDeletion(String resourceType, String resourceName) {
        return waitFor(resourceType, resourceName,
                x -> false,
                ex -> ex instanceof KubeClusterException.NotFound ? ExType.BREAK: ExType.THROW);
    }
}
