/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.controller.cluster.resources;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.strimzi.controller.cluster.ResourceUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

public class TopicControllerTest {

    private final String namespace = "test";
    private final String cluster = "foo";
    private final int replicas = 3;
    private final String image = "my-image:latest";
    private final int healthDelay = 120;
    private final int healthTimeout = 30;
    private final String metricsCmJson = "{\"animal\":\"wombat\"}";
    private final String storageJson = "{\"type\": \"ephemeral\"}";

    private final String tcNamespace = "my-topic-namespace";
    private final String tcImage = "my-topic-controller-image";
    private final String tcReconciliationInterval = "900000";
    private final String tcZookeeperSessionTimeout = "20000";

    private final String topicControllerJson = "{ " +
            "\"namespace\":\"" + tcNamespace + "\", " +
            "\"image\":\"" + tcImage + "\", " +
            "\"reconciliationInterval\":\"" + tcReconciliationInterval + "\", " +
            "\"zookeeperSessionTimeout\":\"" + tcZookeeperSessionTimeout + "\"" +
            " }";

    private final ConfigMap cm = ResourceUtils.createKafkaClusterConfigMap(namespace, cluster, replicas, image, healthDelay, healthTimeout, metricsCmJson, storageJson, topicControllerJson);
    private final TopicController tc = TopicController.fromConfigMap(cm);

    private List<EnvVar> getExpectedEnvVars() {
        List<EnvVar> expected = new ArrayList<>();
        expected.add(new EnvVarBuilder().withName(TopicController.KEY_CONFIGMAP_LABELS).withValue(TopicController.defaultTopicConfigMapLabels(cluster)).build());
        expected.add(new EnvVarBuilder().withName(TopicController.KEY_KAFKA_BOOTSTRAP_SERVERS).withValue(TopicController.defaultBootstrapServers(cluster)).build());
        expected.add(new EnvVarBuilder().withName(TopicController.KEY_ZOOKEEPER_CONNECT).withValue(TopicController.defaultZookeeperConnect(cluster)).build());
        expected.add(new EnvVarBuilder().withName(TopicController.KEY_NAMESPACE).withValue(tcNamespace).build());
        expected.add(new EnvVarBuilder().withName(TopicController.KEY_FULL_RECONCILIATION_INTERVAL_MS).withValue(tcReconciliationInterval).build());
        expected.add(new EnvVarBuilder().withName(TopicController.KEY_ZOOKEEPER_SESSION_TIMEOUT_MS).withValue(tcZookeeperSessionTimeout).build());

        return expected;
    }

    @Test
    public void testFromConfigMapNoConfig() {

        ConfigMap cm = ResourceUtils.createKafkaClusterConfigMap(namespace, cluster, replicas, image, healthDelay, healthTimeout, metricsCmJson);
        TopicController tc = TopicController.fromConfigMap(cm);

        assertNull(tc);
    }

    @Test
    public void testFromConfigMapDefaultConfig() {

        ConfigMap cm = ResourceUtils.createKafkaClusterConfigMap(namespace, cluster, replicas, image, healthDelay, healthTimeout, metricsCmJson, storageJson, "{ }");
        TopicController tc = TopicController.fromConfigMap(cm);

        assertEquals(TopicController.DEFAULT_IMAGE, tc.getImage());
        assertEquals(namespace, tc.getTopicNamespace());
        assertEquals(TopicController.DEFAULT_FULL_RECONCILIATION_INTERVAL_MS, tc.getReconciliationIntervalMs());
        assertEquals(TopicController.DEFAULT_ZOOKEEPER_SESSION_TIMEOUT_MS, tc.getZookeeperSessionTimeoutMs());
        assertEquals(TopicController.defaultBootstrapServers(cluster), tc.getKafkaBootstrapServers());
        assertEquals(TopicController.defaultZookeeperConnect(cluster), tc.getZookeeperConnect());
        assertEquals(TopicController.defaultTopicConfigMapLabels(cluster), tc.getTopicConfigMapLabels());
    }

    @Test
    public void testFromConfigMap() {

        assertEquals(namespace, tc.namespace);
        assertEquals(cluster, tc.cluster);
        assertEquals(tcImage, tc.image);
        assertEquals(TopicController.DEFAULT_REPLICAS, tc.replicas);
        assertEquals(TopicController.DEFAULT_HEALTHCHECK_DELAY, tc.healthCheckInitialDelay);
        assertEquals(TopicController.DEFAULT_HEALTHCHECK_TIMEOUT, tc.healthCheckTimeout);
        assertEquals(tcImage, tc.getImage());
        assertEquals(tcNamespace, tc.getTopicNamespace());
        assertEquals(tcReconciliationInterval, tc.getReconciliationIntervalMs());
        assertEquals(tcZookeeperSessionTimeout, tc.getZookeeperSessionTimeoutMs());
        assertEquals(TopicController.defaultBootstrapServers(cluster), tc.getKafkaBootstrapServers());
        assertEquals(TopicController.defaultZookeeperConnect(cluster), tc.getZookeeperConnect());
        assertEquals(TopicController.defaultTopicConfigMapLabels(cluster), tc.getTopicConfigMapLabels());
    }

    @Test
    public void testFromDeployment() {

        TopicController tcFromDep = TopicController.fromDeployment(namespace, cluster, tc.generateDeployment());

        assertEquals(tc.namespace, tcFromDep.namespace);
        assertEquals(tc.cluster, tcFromDep.cluster);
        assertEquals(tc.image, tcFromDep.image);
        assertEquals(tc.replicas, tcFromDep.replicas);
        assertEquals(tc.healthCheckInitialDelay, tcFromDep.healthCheckInitialDelay);
        assertEquals(tc.healthCheckTimeout, tcFromDep.healthCheckTimeout);
        assertEquals(tc.getImage(), tcFromDep.getImage());
        assertEquals(tc.getTopicNamespace(), tcFromDep.getTopicNamespace());
        assertEquals(tc.getReconciliationIntervalMs(), tcFromDep.getReconciliationIntervalMs());
        assertEquals(tc.getZookeeperSessionTimeoutMs(), tcFromDep.getZookeeperSessionTimeoutMs());
        assertEquals(tc.getKafkaBootstrapServers(), tcFromDep.getKafkaBootstrapServers());
        assertEquals(tc.getZookeeperConnect(), tcFromDep.getZookeeperConnect());
        assertEquals(tc.getTopicConfigMapLabels(), tcFromDep.getTopicConfigMapLabels());
    }

    @Test
    public void testGenerateDeployment() {

        Deployment dep = tc.generateDeployment();

        assertEquals(tc.topicControllerName(cluster), dep.getMetadata().getName());
        assertEquals(namespace, dep.getMetadata().getNamespace());
        assertEquals(new Integer(TopicController.DEFAULT_REPLICAS), dep.getSpec().getReplicas());
        assertEquals(1, dep.getSpec().getTemplate().getSpec().getContainers().size());
        assertEquals(tc.topicControllerName(cluster), dep.getSpec().getTemplate().getSpec().getContainers().get(0).getName());
        assertEquals(tc.image, dep.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        assertEquals(getExpectedEnvVars(), dep.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
        assertEquals(new Integer(TopicController.DEFAULT_HEALTHCHECK_DELAY), dep.getSpec().getTemplate().getSpec().getContainers().get(0).getLivenessProbe().getInitialDelaySeconds());
        assertEquals(new Integer(TopicController.DEFAULT_HEALTHCHECK_TIMEOUT), dep.getSpec().getTemplate().getSpec().getContainers().get(0).getLivenessProbe().getTimeoutSeconds());
        assertEquals(new Integer(TopicController.DEFAULT_HEALTHCHECK_DELAY), dep.getSpec().getTemplate().getSpec().getContainers().get(0).getReadinessProbe().getInitialDelaySeconds());
        assertEquals(new Integer(TopicController.DEFAULT_HEALTHCHECK_TIMEOUT), dep.getSpec().getTemplate().getSpec().getContainers().get(0).getReadinessProbe().getTimeoutSeconds());
        assertEquals(1, dep.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().size());
        assertEquals(new Integer(TopicController.HEALTHCHECK_PORT), dep.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getContainerPort());
        assertEquals(TopicController.HEALTHCHECK_PORT_NAME, dep.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getName());
        assertEquals("TCP", dep.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getProtocol());
        assertEquals("Recreate", dep.getSpec().getStrategy().getType());
    }

    @Test
    public void testEnvVars()   {
        assertEquals(getExpectedEnvVars(), tc.getEnvVars());
    }

    @Test
    public void testDiffNoDiffs() {

        ClusterDiffResult diff = tc.diff(tc.generateDeployment());

        assertFalse(diff.isDifferent());
    }

    @Test
    public void testDiffImage() {

        Deployment dep = tc.generateDeployment();
        dep.getSpec().getTemplate().getSpec().getContainers().get(0).setImage("diff-image");

        ClusterDiffResult diff = tc.diff(dep);

        assertTrue(diff.isDifferent());
    }

    @Test
    public void testDiffNamespace() {

        testDiffEnvVar(TopicController.KEY_NAMESPACE, "diff-tc-namespace");
    }

    @Test
    public void testDiffReconciliationInterval() {

        testDiffEnvVar(TopicController.KEY_FULL_RECONCILIATION_INTERVAL_MS, "1200000");
    }

    @Test
    public void testDiffZookeeperSessionTimeout() {

        testDiffEnvVar(TopicController.KEY_ZOOKEEPER_SESSION_TIMEOUT_MS, "10000");
    }

    private void testDiffEnvVar(String name, String value) {

        Deployment dep = tc.generateDeployment();

        List<EnvVar> newEnvVars = dep.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv()
                .stream().map(envVar -> {

                    if (envVar.getName().equals(name)) {
                        return new EnvVarBuilder().withName(name).withValue(value).build();
                    } else {
                        return envVar;
                    }

                }).collect(Collectors.toList());

        dep.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(newEnvVars);

        ClusterDiffResult diff = tc.diff(dep);
        assertTrue(diff.isDifferent());
    }
}
