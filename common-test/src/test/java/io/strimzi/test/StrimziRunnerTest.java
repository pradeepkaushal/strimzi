/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.test;

import io.strimzi.test.k8s.KubeClient;
import io.strimzi.test.k8s.KubeClusterResource;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class StrimziRunnerTest {

    private static final KubeClient MOCK_KUBE_CLIENT = mock(KubeClient.class);

    static class MockKubeClusterResource extends KubeClusterResource {
        public MockKubeClusterResource() {
            super(null, null);
        }

        @Override
        public void before() {
        }

        @Override
        public void after() {
        }

        @Override
        public KubeClient<?> client() {
            return MOCK_KUBE_CLIENT;
        }
    }

    @Namespace("test")
    @Resources("foo")
    @RunWith(StrimziRunner.class)
    public static class ClsWithClusterResource {

        @ClassRule
        public static KubeClusterResource mockCluster = new MockKubeClusterResource() {

        };

        @Test
        public void test0() {
            System.out.println("Hello");
        }

        @Namespace("different")
        @Resources("moreResources")
        @Test
        public void test1() {
            System.out.println("Hello");
        }
    }

    JUnitCore jUnitCore = new JUnitCore();

    @Test
    public void test0() {
        if (System.getenv(StrimziRunner.NOTEARDOWN) != null) {
            return;
        }
        Result r =  jUnitCore.run(Request.method(ClsWithClusterResource.class, "test0"));
        if (!r.wasSuccessful()) {
            r.getFailures().get(0).getException().printStackTrace();
        }
        assertTrue(r.wasSuccessful());
        verify(MOCK_KUBE_CLIENT).createNamespace(eq("test"));
        verify(MOCK_KUBE_CLIENT).create(eq("foo"));
        verify(MOCK_KUBE_CLIENT).deleteNamespace(eq("test"));
        verify(MOCK_KUBE_CLIENT).delete(eq("foo"));
    }

    @Test
    public void test1() {
        if (System.getenv(StrimziRunner.NOTEARDOWN) != null) {
            return;
        }
        Result r =  jUnitCore.run(Request.method(ClsWithClusterResource.class, "test1"));
        if (!r.wasSuccessful()) {
            r.getFailures().get(0).getException().printStackTrace();
        }
        assertTrue(r.wasSuccessful());
        verify(MOCK_KUBE_CLIENT, times(2)).createNamespace(eq("test"));
        verify(MOCK_KUBE_CLIENT, times(2)).create(eq("foo"));
        verify(MOCK_KUBE_CLIENT, times(2)).deleteNamespace(eq("test"));
        verify(MOCK_KUBE_CLIENT, times(2)).delete(eq("foo"));
    }
}
