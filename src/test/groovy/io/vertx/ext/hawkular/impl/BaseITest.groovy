/*
 * Copyright 2015 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.hawkular.impl

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import io.vertx.core.Vertx
import io.vertx.core.impl.VertxImpl
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.Timeout
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.runner.RunWith

import static java.util.concurrent.TimeUnit.*
import static org.junit.Assert.fail

@RunWith(VertxUnitRunner.class)
abstract class BaseITest {
  public static final PORT_OFFSET = Integer.getInteger('test.port.offset', 0);
  public static final SERVER_URL = System.getProperty('test.hawkular.server.base-url') ?:
    'http://127.0.0.1:8080/hawkular/metrics/'
  public static final SERVER_URL_PROPS = new URI(SERVER_URL)
  public static final TENANT_HEADER_NAME = "Hawkular-Tenant"
  public static final METRIC_PREFIX = 'mars01.host13'
  public static final SCHEDULE = MILLISECONDS.convert(2, SECONDS)
  public static final DELTA = 0.001D
  public static final LOOPS = Integer.getInteger('test.hawkular.check.loops', 2);

  private static final Closure<Long> DATAPOINT_COMPARATOR = { p1, p2 -> Long.compare(p2.timestamp, p1.timestamp) }

  protected static RESTClient hawkularMetrics

  @Rule
  public Timeout timeout = new Timeout(1, MINUTES);

  protected def tenantId = TenantGenerator.instance.nextTenantId()
  protected def vertxOptions = createVertxOptions(tenantId)
  protected def vertx = Vertx.vertx(vertxOptions);

  @BeforeClass
  static void createRestClient() {
    hawkularMetrics = new RESTClient(SERVER_URL, ContentType.JSON)
  }


  @Before
  void before(TestContext context) throws Exception {
    setUp(context)
  }

  void setUp(TestContext context) throws Exception {
    def vertxImpl = (VertxImpl) vertx
    def metrics = (VertxMetricsImpl) vertxImpl.metrics
    metrics.metricsReady.setHandler(context.asyncAssertSuccess())
  }

  protected def deployVerticle(String verticleName, Map config, int instances, TestContext testContext) {
    def async = testContext.async()
    vertx.deployVerticle(verticleName, [
      'config'   : config,
      'instances': instances
    ], { res ->
      if (res.succeeded()) {
        async.complete()
      } else {
        testContext.fail(res.cause())
      }
    })
    async.await()
  }

  @After
  void after(TestContext context) throws Exception {
    tearDown(context)
  }

  void tearDown(TestContext context) throws Exception {
    def async = context.async()
    vertx.close({ res ->
      if (res.succeeded()) {
        async.complete()
      } else {
        context.fail(res.cause())
      }
    })
  }

  protected Map createVertxOptions(String tenantId) {
    def vertxOptions = [
      metricsOptions: [
        enabled             : true,
        host                : SERVER_URL_PROPS.host,
        port                : SERVER_URL_PROPS.port,
        tenant              : tenantId,
        prefix              : METRIC_PREFIX,
        schedule            : SECONDS.convert(SCHEDULE, MILLISECONDS),
        // Event bus bridge configuration
        metricsBridgeEnabled: true,
        metricsBridgeAddress: "hawkular.metrics",
        tags                : [dc: 'mars01', host: 'host13']
      ]
    ]
    vertxOptions
  }

  protected static int getPort(int defaultValue) {
    defaultValue + PORT_OFFSET
  }

  protected static void assertMetricsEquals(Set expected, String tenantId, Closure<Boolean> nameFilter,
                                            Closure<String> nameTransformer) {
    long start = System.currentTimeMillis()
    def actual
    while (true) {
      actual = hawkularMetrics.get(path: 'metrics', headers: [(TENANT_HEADER_NAME): tenantId]).data ?: []
      actual = actual.findAll { metric ->
        nameFilter.call(metric.id as String)
      }.collect { metric ->
        nameTransformer.call(metric.id as String)
      } as Set
      if (actual.equals(expected)) return;
      if (System.currentTimeMillis() - start > LOOPS * SCHEDULE) break;
      sleep(SCHEDULE / 10 as long)
    }
    fail("Expected: ${expected.sort()}, actual: ${actual.sort()}")
  }

  protected static void assertGaugeEquals(Double expected, String tenantId, String gauge) {
    long start = System.currentTimeMillis()
    def actual
    while (true) {
      actual = getGaugeValue(tenantId, gauge)
      if (actual != null) {
        if (Double.compare(expected, actual) == 0 || Math.abs(expected - actual) <= DELTA) return
      }
      if (System.currentTimeMillis() - start > LOOPS * SCHEDULE) break;
      sleep(SCHEDULE / 10 as long)
    }
    fail("Expected: ${expected}, actual: ${actual}")
  }

  private static Double getGaugeValue(String tenantId, String gauge) {
    def data = hawkularMetrics.get([
      path   : "gauges/${gauge}/raw",
      headers: [(TENANT_HEADER_NAME): tenantId]
    ]).data ?: []
    data.isEmpty() ? null : data.sort(DATAPOINT_COMPARATOR)[0].value as Double
  }

  protected static void assertCounterEquals(Long expected, String tenantId, String counter) {
    long start = System.currentTimeMillis()
    def actual
    while (true) {
      actual = getCounterValue(tenantId, counter)
      if (expected.equals(actual)) return
      if (System.currentTimeMillis() - start > LOOPS * SCHEDULE) break;
      sleep(SCHEDULE / 10 as long)
    }
    fail("Expected: ${expected}, actual: ${actual}")
  }

  protected static void assertCounterGreaterThan(Long expected, String tenantId, String counter) {
    long start = System.currentTimeMillis()
    def actual
    while (true) {
      actual = getCounterValue(tenantId, counter)
      if (actual != null) {
        if (Long.compare(expected, actual) < 0) return
      }
      if (System.currentTimeMillis() - start > LOOPS * SCHEDULE) break;
      sleep(SCHEDULE / 10 as long)
    }
    fail("Expected ${counter} value ${actual} to be greater than ${expected}")
  }

  private static Long getCounterValue(String tenantId, String counter) {
    def data = hawkularMetrics.get([
      path   : "counters/${counter}/raw",
      headers: [(TENANT_HEADER_NAME): tenantId]
    ]).data ?: []
    data.isEmpty() ? null : data.sort(DATAPOINT_COMPARATOR)[0].value as Long
  }

  protected static void assertAvailabilityEquals(String expected, String tenantId, String availability) {
    long start = System.currentTimeMillis()
    def actual
    while (true) {
      actual = getAvailabilityValue(tenantId, availability)
      if (expected.equals(actual)) return
      if (System.currentTimeMillis() - start > LOOPS * SCHEDULE) break;
      sleep(SCHEDULE / 10 as long)
    }
    fail("Expected: ${expected}, actual: ${actual}")
  }

  private static String getAvailabilityValue(String tenantId, String availability) {
    def data = hawkularMetrics.get([
      path   : "availability/${availability}/raw",
      headers: [(TENANT_HEADER_NAME): tenantId]
    ]).data ?: []
    data.isEmpty() ? null : data.sort(DATAPOINT_COMPARATOR)[0].value as String
  }

  protected static void assertTagsEquals(Map expected, String tenantId, String type, String name) {
    long start = System.currentTimeMillis()
    def actual
    while (true) {
      actual = getTagsValue(tenantId, type, name)
      if (expected == actual) {
        return
      }
      if (System.currentTimeMillis() - start > LOOPS * SCHEDULE) break;
      sleep(SCHEDULE / 10 as long)
    }
    fail("Expected: ${expected}, actual: ${actual}")
  }

  private static Map getTagsValue(String tenantId, String type, String name) {
    hawkularMetrics.get([
      path   : "${type}/${name}/tags",
      headers: [(TENANT_HEADER_NAME): tenantId]
    ]).data ?: [:]
  }
}
