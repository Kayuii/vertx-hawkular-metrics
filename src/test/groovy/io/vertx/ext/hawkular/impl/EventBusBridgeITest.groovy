package io.vertx.ext.hawkular.impl

import io.vertx.groovy.ext.unit.TestContext
import org.junit.Test

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
class EventBusBridgeITest extends BaseITest {

  def testHost = 'localhost'
  def testPort = getPort(9192)
  def verticleName = 'verticles/metrics_sender.groovy'


  @Test
  void shouldGetCustomMetrics(TestContext context) {
    def instances = 1
    def config = [
            'host': testHost,
            'port': testPort,
            'id': 'my-metric'
    ]
    deployVerticle(verticleName, config, instances, context)
    assertGaugeEquals(1.0, tenantId, "my-metric")
  }

  @Test
  void shouldGetCustomMetricsWithTimestamp(TestContext context) {
    def instances = 1
    def config = [
            'host': testHost,
            'port': testPort,
            'insert-timestamp': true,
            'id': 'my-metric-ts'
    ]
    deployVerticle(verticleName, config, instances, context)
    assertGaugeEquals(1.0, tenantId, "my-metric-ts")
  }

  @Test
  void shouldGetCustomMetricsSentAsCounter(TestContext context) {
    def instances = 1
    def config = [
            'host': testHost,
            'port': testPort,
            'counter' : true,
            'id': 'my-metric-counter'
    ]
    deployVerticle(verticleName, config, instances, context)
    assertCounterEquals(1L, tenantId, "my-metric-counter")
  }
}